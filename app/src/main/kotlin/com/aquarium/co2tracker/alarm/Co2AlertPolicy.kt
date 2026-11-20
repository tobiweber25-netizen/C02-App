package com.aquarium.co2tracker.alarm

import com.aquarium.co2tracker.domain.BottleStatus
import com.aquarium.co2tracker.domain.CylinderProjection
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class AlertStage(val severity: Int) {
  PAUSED_REMINDER(0),
  CHECK_IN(1),
  REFILL_WARNING(2),
  DANGER_CUTOFF(3),
  EMPTY(4)
}

data class AlertDeliveryState(
  val setupTimestampMillis: Long = 0L,
  val checkInNotified: Boolean = false,
  val lastNotifiedStage: AlertStage? = null,
  val lastNotifiedAtMillis: Long = 0L,
  val pausedReminderForMillis: Long = 0L
)

data class NextAlarmPlan(
  val stage: AlertStage,
  val triggerAtMillis: Long,
  val cylinderId: Long = 1L
)

data class AlertNotificationCopy(
  val title: String,
  val body: String,
  val isUrgent: Boolean
)

/**
 * Pure-Kotlin decision engine for CO2 alert timing, deduplication, escalation, pause reminders,
 * and catch-up across single or multiple active bottles.
 */
object Co2AlertPolicy {
  const val ALARM_WAKEUP_GRACE_MILLIS = 60_000L
  const val REPEAT_REMINDER_INTERVAL_MILLIS = 23L * 3_600_000L
  const val DAILY_FOLLOWUP_MILLIS = 24L * 3_600_000L
  const val FUTURE_RESET_MARGIN_MILLIS = 5L * 60_000L
  const val PAUSED_REMINDER_DELAY_MILLIS = 18L * 3_600_000L

  fun normalizeStateForProjection(
    projection: CylinderProjection,
    rawState: AlertDeliveryState,
    nowMillis: Long
  ): AlertDeliveryState {
    if (rawState.setupTimestampMillis != projection.config.setupTimestampMillis) {
      return AlertDeliveryState(setupTimestampMillis = projection.config.setupTimestampMillis)
    }

    var state = rawState
    if (projection.isScaleCalibrated && !state.checkInNotified) {
      state = state.copy(checkInNotified = true)
    }

    if (!projection.isPaused && state.pausedReminderForMillis != 0L) {
      state = state.copy(pausedReminderForMillis = 0L)
    }

    val lastStage = state.lastNotifiedStage
    if (lastStage != null) {
      val stillInStage = when (lastStage) {
        AlertStage.PAUSED_REMINDER -> projection.isPaused
        AlertStage.CHECK_IN -> true
        AlertStage.REFILL_WARNING -> projection.isInRefillWindow || projection.alertTimestampMillis <= nowMillis + FUTURE_RESET_MARGIN_MILLIS
        AlertStage.DANGER_CUTOFF -> projection.isBelowDangerThreshold || projection.depletionTimestampMillis <= nowMillis + FUTURE_RESET_MARGIN_MILLIS
        AlertStage.EMPTY -> projection.isEmpty || projection.emptyTimestampMillis <= nowMillis + FUTURE_RESET_MARGIN_MILLIS
      }
      if (!stillInStage) {
        state = state.copy(lastNotifiedStage = null, lastNotifiedAtMillis = 0L)
      }
    }

    return state
  }

  fun evaluateDueNotification(
    projection: CylinderProjection,
    rawState: AlertDeliveryState,
    nowMillis: Long,
    graceMillis: Long = ALARM_WAKEUP_GRACE_MILLIS
  ): AlertStage? {
    // Only actively connected tank cylinders should fire depletion/pause alerts
    if (projection.config.bottleStatus != BottleStatus.ACTIVE) return null

    val state = normalizeStateForProjection(projection, rawState, nowMillis)
    val nowWithGrace = nowMillis + graceMillis

    // Forgotten live-pause reminder (once per pause session after 18h)
    val pausedSince = projection.pausedSinceMillis
    if (projection.isPaused && pausedSince != null) {
      val remindAt = pausedSince + PAUSED_REMINDER_DELAY_MILLIS
      return if (nowWithGrace >= remindAt && state.pausedReminderForMillis != pausedSince) {
        AlertStage.PAUSED_REMINDER
      } else {
        null
      }
    }

    val dueStage: AlertStage? = when {
      projection.isEmpty || (projection.hasActiveBurn && projection.emptyTimestampMillis <= nowWithGrace) ->
        AlertStage.EMPTY
      projection.isBelowDangerThreshold || (projection.hasActiveBurn && projection.depletionTimestampMillis <= nowWithGrace) ->
        AlertStage.DANGER_CUTOFF
      projection.hasActiveBurn && (projection.isInRefillWindow || projection.alertTimestampMillis <= nowWithGrace) ->
        AlertStage.REFILL_WARNING
      !projection.isScaleCalibrated &&
        projection.hasActiveBurn &&
        projection.recommendedCheckInTimestampMillis != null &&
        projection.recommendedCheckInTimestampMillis <= nowWithGrace ->
        AlertStage.CHECK_IN
      else -> null
    }

    if (dueStage == null) return null

    if (dueStage == AlertStage.CHECK_IN) {
      return if (!state.checkInNotified) AlertStage.CHECK_IN else null
    }

    val prevStage = state.lastNotifiedStage
    if (prevStage == null || dueStage.severity > prevStage.severity) {
      return dueStage
    }

    val elapsedSinceLast = nowMillis - state.lastNotifiedAtMillis
    return if (elapsedSinceLast >= REPEAT_REMINDER_INTERVAL_MILLIS) dueStage else null
  }

  fun recordNotificationDelivered(
    projection: CylinderProjection,
    rawState: AlertDeliveryState,
    stage: AlertStage,
    deliveredAtMillis: Long
  ): AlertDeliveryState {
    val state = normalizeStateForProjection(projection, rawState, deliveredAtMillis)
    return when (stage) {
      AlertStage.PAUSED_REMINDER -> state.copy(
        pausedReminderForMillis = projection.pausedSinceMillis ?: deliveredAtMillis
      )
      AlertStage.CHECK_IN -> state.copy(checkInNotified = true)
      else -> state.copy(
        lastNotifiedStage = stage,
        lastNotifiedAtMillis = deliveredAtMillis
      )
    }
  }

  fun computeNextAlarmPlan(
    projection: CylinderProjection,
    rawState: AlertDeliveryState,
    nowMillis: Long
  ): NextAlarmPlan? {
    if (projection.config.bottleStatus != BottleStatus.ACTIVE) return null

    val state = normalizeStateForProjection(projection, rawState, nowMillis)
    val cid = projection.config.id

    // Live-paused bottle: schedule forgotten-pause nudge at +18h
    val pausedSince = projection.pausedSinceMillis
    if (projection.isPaused && pausedSince != null) {
      if (state.pausedReminderForMillis == pausedSince) return null
      val remindAt = pausedSince + PAUSED_REMINDER_DELAY_MILLIS
      return if (remindAt > nowMillis + ALARM_WAKEUP_GRACE_MILLIS) {
        NextAlarmPlan(AlertStage.PAUSED_REMINDER, remindAt, cid)
      } else {
        null
      }
    }

    if (!projection.hasActiveBurn && !projection.isBelowDangerThreshold && !projection.isEmpty) {
      return null
    }

    // 1. Recommended first scale check-in (only when uncalibrated and still before refill alert)
    val checkInTs = projection.recommendedCheckInTimestampMillis
    if (
      !projection.isScaleCalibrated &&
      !state.checkInNotified &&
      checkInTs != null &&
      checkInTs > nowMillis + ALARM_WAKEUP_GRACE_MILLIS &&
      checkInTs < projection.alertTimestampMillis
    ) {
      return NextAlarmPlan(AlertStage.CHECK_IN, checkInTs, cid)
    }

    val prevSeverity = state.lastNotifiedStage?.severity ?: 0

    // 2. Advance refill warning
    if (
      projection.hasActiveBurn &&
      prevSeverity < AlertStage.REFILL_WARNING.severity &&
      projection.alertTimestampMillis > nowMillis + ALARM_WAKEUP_GRACE_MILLIS
    ) {
      return NextAlarmPlan(AlertStage.REFILL_WARNING, projection.alertTimestampMillis, cid)
    }

    // 3. Safety cutoff (danger threshold)
    if (
      projection.hasActiveBurn &&
      !projection.isBelowDangerThreshold &&
      prevSeverity < AlertStage.DANGER_CUTOFF.severity &&
      projection.depletionTimestampMillis > nowMillis + ALARM_WAKEUP_GRACE_MILLIS
    ) {
      return NextAlarmPlan(AlertStage.DANGER_CUTOFF, projection.depletionTimestampMillis, cid)
    }

    // 4. Completely empty (0g)
    if (
      projection.hasActiveBurn &&
      !projection.isEmpty &&
      prevSeverity < AlertStage.EMPTY.severity &&
      projection.emptyTimestampMillis > nowMillis + ALARM_WAKEUP_GRACE_MILLIS
    ) {
      return NextAlarmPlan(AlertStage.EMPTY, projection.emptyTimestampMillis, cid)
    }

    // 5. Already at/past active alert window or critical cutoff: keep daily follow-up armed
    if (projection.isInRefillWindow || projection.isBelowDangerThreshold || projection.isEmpty) {
      val stage = when {
        projection.isEmpty -> AlertStage.EMPTY
        projection.isBelowDangerThreshold -> AlertStage.DANGER_CUTOFF
        else -> AlertStage.REFILL_WARNING
      }
      val nextFollowUp = max(
        nowMillis + DAILY_FOLLOWUP_MILLIS,
        state.lastNotifiedAtMillis + DAILY_FOLLOWUP_MILLIS
      )
      return NextAlarmPlan(stage, nextFollowUp, cid)
    }

    return null
  }

  fun buildNotificationCopy(
    stage: AlertStage,
    projection: CylinderProjection,
    cutoffDateStr: String,
    emptyDateStr: String,
    includeCylinderName: Boolean = false
  ): AlertNotificationCopy {
    val namePrefix = if (includeCylinderName) "[${projection.config.cylinderName}] " else ""
    val remainingGrams = projection.remainingCo2Grams.coerceAtLeast(0.0).roundToInt()
    val cutoffDays = projection.daysUntilDangerThreshold.coerceAtLeast(0.0)
    val emptyDays = projection.daysUntilEmpty.coerceAtLeast(0.0)
    val effectiveCutoffDays = if (projection.isScaleCalibrated) {
      cutoffDays
    } else {
      projection.conservativeDaysUntilDanger.coerceAtLeast(0.0)
    }

    val daysLabel = formatDaysPhrase(effectiveCutoffDays)
    val emptyDaysLabel = formatDaysPhrase(emptyDays)
    val leakNote = if (projection.possibleLeakDetected) {
      " Note: Higher-than-normal CO2 loss detected—check for leaks."
    } else {
      ""
    }
    val shopNote = if (projection.alertShiftedForShopDays && projection.config.refillShopName.isNotBlank()) {
      " Shifted ahead of closed days for ${projection.config.refillShopName}."
    } else if (projection.alertShiftedForShopDays) {
      " Shifted ahead of your refill shop's closed days."
    } else {
      ""
    }

    return when (stage) {
      AlertStage.PAUSED_REMINDER -> AlertNotificationCopy(
        title = "${namePrefix}CO2 Tracking Is Still Paused",
        body = "Your CO2 has been paused for over 18 hours. Tap Resume if your solenoid is back on so remaining gas stays accurate.",
        isUrgent = false
      )
      AlertStage.CHECK_IN -> AlertNotificationCopy(
        title = "${namePrefix}CO2 Check-In: Weigh Canister to Calibrate",
        body = "Weigh your cylinder on a scale and log a check-in to lock in your real burn rate. Current estimate: ~${remainingGrams}g left (empty around $emptyDateStr).",
        isUrgent = false
      )
      AlertStage.REFILL_WARNING -> {
        val cutoffLabel = if (projection.isScaleCalibrated) {
          "Safety cutoff (${projection.config.dangerThresholdPercent.roundToInt()}%) around $cutoffDateStr; empty around $emptyDateStr."
        } else {
          "Uncalibrated estimate—weigh canister now to lock in accuracy. Earliest cutoff around $cutoffDateStr; expected empty around $emptyDateStr."
        }
        AlertNotificationCopy(
          title = "${namePrefix}CO2 Refill Window: $daysLabel Until Safety Cutoff",
          body = "~${remainingGrams}g left. $cutoffLabel$shopNote$leakNote",
          isUrgent = false
        )
      }
      AlertStage.DANGER_CUTOFF -> AlertNotificationCopy(
        title = "${namePrefix}URGENT: CO2 At ${projection.config.dangerThresholdPercent.roundToInt()}% Safety Cutoff",
        body = "~${remainingGrams}g CO2 left. Liquid CO2 is nearly gone—refill now to avoid end-of-tank dump. Estimated completely empty in $emptyDaysLabel ($emptyDateStr).$leakNote",
        isUrgent = true
      )
      AlertStage.EMPTY -> AlertNotificationCopy(
        title = "${namePrefix}CRITICAL: Aquarium CO2 Cylinder Likely Empty",
        body = "Estimated ${remainingGrams}g CO2 remaining. Check your canister weight and close the valve or refill immediately to protect livestock.$leakNote",
        isUrgent = true
      )
    }
  }

  private fun formatDaysPhrase(days: Double): String {
    if (!days.isFinite() || days <= 0.0) return "< 1 day"
    if (days < 1.0) return "< 1 day"
    val rounded = ceil(days).toInt()
    return if (rounded == 1) "1 day" else "~$rounded days"
  }
}
