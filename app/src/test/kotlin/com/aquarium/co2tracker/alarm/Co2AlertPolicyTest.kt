package com.aquarium.co2tracker.alarm

import com.aquarium.co2tracker.domain.Co2PhysicsEngine
import com.aquarium.co2tracker.domain.CylinderConfig
import com.aquarium.co2tracker.domain.LedgerEvent
import com.aquarium.co2tracker.domain.LedgerEventType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class Co2AlertPolicyTest {

  private val fixedZone: ZoneId = ZoneId.of("Africa/Johannesburg")

  private fun epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
    ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), fixedZone).toInstant().toEpochMilli()

  private fun baseConfig(setupTime: Long, leadDays: Int = 5): CylinderConfig = CylinderConfig(
    tareWeightGrams = 3350.0,
    fullGrossWeightGrams = 4850.0, // 1500g net, 150g danger cutoff (10%)
    workingPressureValue = 2.5,
    atmosphericPressureKpa = 83.0,
    solenoidStartMinutes = 10 * 60 + 30,
    solenoidEndMinutes = 18 * 60, // 7.5 hrs/day
    startingBps = 1.5,
    dangerThresholdPercent = 10.0,
    notificationLeadTimeDays = leadDays,
    setupTimestampMillis = setupTime
  )

  @Test
  fun testFullLifecycleMilestoneProgressionWithoutDuplicateLoops() {
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val config = baseConfig(setupTime)
    var state = AlertDeliveryState(setupTimestampMillis = setupTime)

    // 1. Right after setup (uncalibrated): next alarm is CHECK_IN
    val projDay0 = Co2PhysicsEngine.evaluateLedger(config, emptyList(), setupTime, fixedZone)
    val plan0 = Co2AlertPolicy.computeNextAlarmPlan(projDay0, state, setupTime)
    assertNotNull(plan0)
    assertEquals(AlertStage.CHECK_IN, plan0!!.stage)

    // 2. Wakeup 50ms EARLY for CHECK_IN -> must fire CHECK_IN and NOT loop!
    val checkInWake = plan0.triggerAtMillis - 50L
    val projAtCheckIn = Co2PhysicsEngine.evaluateLedger(config, emptyList(), checkInWake, fixedZone)
    val dueCheckIn = Co2AlertPolicy.evaluateDueNotification(projAtCheckIn, state, checkInWake)
    assertEquals(AlertStage.CHECK_IN, dueCheckIn)

    state = Co2AlertPolicy.recordNotificationDelivered(projAtCheckIn, state, dueCheckIn!!, checkInWake)
    // Immediate re-evaluation must NOT double-fire
    assertNull(Co2AlertPolicy.evaluateDueNotification(projAtCheckIn, state, checkInWake + 100L))

    // Next alarm advances to REFILL_WARNING (not CHECK_IN again!)
    val planAfterCheckIn = Co2AlertPolicy.computeNextAlarmPlan(projAtCheckIn, state, checkInWake)
    assertNotNull(planAfterCheckIn)
    assertEquals(AlertStage.REFILL_WARNING, planAfterCheckIn!!.stage)
    assertTrue(planAfterCheckIn.triggerAtMillis > checkInWake)

    // 3. Wakeup at REFILL_WARNING (even 5s early) -> fires REFILL_WARNING, next alarm is DANGER_CUTOFF
    val refillWake = planAfterCheckIn.triggerAtMillis - 5_000L
    val projAtRefill = Co2PhysicsEngine.evaluateLedger(config, emptyList(), refillWake, fixedZone)
    val dueRefill = Co2AlertPolicy.evaluateDueNotification(projAtRefill, state, refillWake)
    assertEquals(AlertStage.REFILL_WARNING, dueRefill)

    state = Co2AlertPolicy.recordNotificationDelivered(projAtRefill, state, dueRefill!!, refillWake)
    // Watchdog running 2 hours later must NOT spam duplicate
    val twoHoursLater = refillWake + 2L * 3_600_000L
    val projTwoHoursLater = Co2PhysicsEngine.evaluateLedger(config, emptyList(), twoHoursLater, fixedZone)
    assertNull(Co2AlertPolicy.evaluateDueNotification(projTwoHoursLater, state, twoHoursLater))

    // Next exact alarm is DANGER_CUTOFF
    val planAfterRefill = Co2AlertPolicy.computeNextAlarmPlan(projAtRefill, state, refillWake)
    assertNotNull(planAfterRefill)
    assertEquals(AlertStage.DANGER_CUTOFF, planAfterRefill!!.stage)

    // 4. Wakeup at DANGER_CUTOFF -> escalates immediately even if within 23h of refill warning
    val dangerWake = planAfterRefill.triggerAtMillis
    val projAtDanger = Co2PhysicsEngine.evaluateLedger(config, emptyList(), dangerWake, fixedZone)
    val dueDanger = Co2AlertPolicy.evaluateDueNotification(projAtDanger, state, dangerWake)
    assertEquals(AlertStage.DANGER_CUTOFF, dueDanger)

    state = Co2AlertPolicy.recordNotificationDelivered(projAtDanger, state, dueDanger!!, dangerWake)
    // Next exact alarm is EMPTY (0g) — NOT cancelled!
    val planAfterDanger = Co2AlertPolicy.computeNextAlarmPlan(projAtDanger, state, dangerWake)
    assertNotNull(planAfterDanger)
    assertEquals(AlertStage.EMPTY, planAfterDanger!!.stage)
    assertTrue(planAfterDanger.triggerAtMillis > dangerWake)

    // 5. Wakeup at EMPTY (0g) -> fires EMPTY and still arms 24h daily follow-up!
    val emptyWake = planAfterDanger.triggerAtMillis
    val projAtEmpty = Co2PhysicsEngine.evaluateLedger(config, emptyList(), emptyWake, fixedZone)
    val dueEmpty = Co2AlertPolicy.evaluateDueNotification(projAtEmpty, state, emptyWake)
    assertEquals(AlertStage.EMPTY, dueEmpty)

    state = Co2AlertPolicy.recordNotificationDelivered(projAtEmpty, state, dueEmpty!!, emptyWake)
    val followUpPlan = Co2AlertPolicy.computeNextAlarmPlan(projAtEmpty, state, emptyWake)
    assertNotNull("Must keep daily follow-up armed after empty until user refills", followUpPlan)
    assertEquals(AlertStage.EMPTY, followUpPlan!!.stage)
    assertEquals(emptyWake + Co2AlertPolicy.DAILY_FOLLOWUP_MILLIS, followUpPlan.triggerAtMillis)
  }

  @Test
  fun testScaleCheckInPushingRefillBackIntoFutureResetsDeliveredWarning() {
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val config = baseConfig(setupTime)

    // Advance into conservative refill window (uncalibrated) and deliver REFILL_WARNING
    val initialProj = Co2PhysicsEngine.evaluateLedger(config, emptyList(), setupTime, fixedZone)
    val refillMoment = initialProj.alertTimestampMillis + 3_600_000L
    val projInWindow = Co2PhysicsEngine.evaluateLedger(config, emptyList(), refillMoment, fixedZone)

    var state = AlertDeliveryState(setupTimestampMillis = setupTime, checkInNotified = true)
    assertEquals(AlertStage.REFILL_WARNING, Co2AlertPolicy.evaluateDueNotification(projInWindow, state, refillMoment))
    state = Co2AlertPolicy.recordNotificationDelivered(projInWindow, state, AlertStage.REFILL_WARNING, refillMoment)

    // User weighs bottle and discovers 900g CO2 still left! Refill date moves months ahead.
    val scaleEvent = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = refillMoment + 60_000L,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.5,
      solenoidStartMinutes = config.solenoidStartMinutes,
      solenoidEndMinutes = config.solenoidEndMinutes,
      scaleWeightGrams = 3350.0 + 900.0
    )
    val afterWeigh = refillMoment + 60_000L
    val recalibratedProj = Co2PhysicsEngine.evaluateLedger(config, listOf(scaleEvent), afterWeigh, fixedZone)
    assertFalse("Should no longer be in refill window after finding 900g left", recalibratedProj.isInRefillWindow)

    // Policy MUST reset stale REFILL_WARNING flag and re-arm REFILL_WARNING for the new future date!
    val nextPlan = Co2AlertPolicy.computeNextAlarmPlan(recalibratedProj, state, afterWeigh)
    assertNotNull(nextPlan)
    assertEquals(AlertStage.REFILL_WARNING, nextPlan!!.stage)
    assertEquals(recalibratedProj.alertTimestampMillis, nextPlan.triggerAtMillis)
  }

  @Test
  fun testPausedCylinderArmsNoAlarm() {
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val pausedConfig = baseConfig(setupTime).copy(startingBps = 0.0)
    val proj = Co2PhysicsEngine.evaluateLedger(pausedConfig, emptyList(), setupTime, fixedZone)
    val plan = Co2AlertPolicy.computeNextAlarmPlan(proj, AlertDeliveryState(setupTime), setupTime)
    assertNull("0 BPS paused cylinder must not schedule bogus 10-year alarm", plan)
  }

  @Test
  fun testNotificationCopyReflectsActualRemainingDaysNotHardcodedLeadTime() {
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val config = baseConfig(setupTime, leadDays = 5)
    // Scale-calibrate on day 10 so we have exact calibrated timing
    val day10 = setupTime + 10L * 86_400_000L
    val scaleEvent = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = day10,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.5,
      solenoidStartMinutes = config.solenoidStartMinutes,
      solenoidEndMinutes = config.solenoidEndMinutes,
      scaleWeightGrams = 4750.0 // 100g burned in 10 days = 10g/day
    )
    val calibrated = Co2PhysicsEngine.evaluateLedger(config, listOf(scaleEvent), day10, fixedZone)

    // Jump to ~1.5 days before cutoff (should NOT say "5 days remaining"!)
    val nearCutoff = calibrated.depletionTimestampMillis - (36L * 3_600_000L)
    val projNear = Co2PhysicsEngine.evaluateLedger(config, listOf(scaleEvent), nearCutoff, fixedZone)
    val copy = Co2AlertPolicy.buildNotificationCopy(AlertStage.REFILL_WARNING, projNear, "Thu, 17 Sep 2026", "Fri, 02 Oct 2026")

    assertFalse("Title must not hardcode lead time 5 days when <2 days left: ${copy.title}", copy.title.contains("5"))
    assertTrue("Title should reflect ~2 days left: ${copy.title}", copy.title.contains("2 days"))
    assertTrue("Body should include empty date: ${copy.body}", copy.body.contains("Fri, 02 Oct 2026"))
  }
}
