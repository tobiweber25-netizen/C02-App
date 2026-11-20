package com.aquarium.co2tracker.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aquarium.co2tracker.MainActivity
import com.aquarium.co2tracker.R
import com.aquarium.co2tracker.domain.CylinderProjection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object Co2AlarmScheduler {
  const val CHANNEL_ID = "co2_cylinder_refill_channel"
  const val CHANNEL_NAME = "CO2 Refill Warning Alerts"
  const val ACTION_EXACT_ALARM = "com.aquarium.co2tracker.ACTION_CO2_REFILL_ALARM"
  const val ALARM_REQUEST_CODE = 4200
  const val NOTIFICATION_ID_REFILL = 4201
  const val NOTIFICATION_ID_TEST = 4202
  const val NOTIFICATION_ID_CHECK_IN = 4203

  private const val PREFS_NAME = "co2_alert_delivery_state"
  private const val KEY_SETUP_TS = "setup_timestamp_millis"
  private const val KEY_CHECK_IN_NOTIFIED = "check_in_notified"
  private const val KEY_LAST_STAGE = "last_notified_stage"
  private const val KEY_LAST_NOTIFIED_AT = "last_notified_at_millis"

  fun enqueueDailyWatchdogWork(context: Context) {
    val watchdogRequest = PeriodicWorkRequestBuilder<Co2DailyWatchdogWorker>(
      24, TimeUnit.HOURS,
      2, TimeUnit.HOURS
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      Co2DailyWatchdogWorker.WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      watchdogRequest
    )
  }

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Exact-time high priority alarms when aquarium CO2 cylinder requires checking or refilling"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 450, 200, 450)
      }
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  fun canScheduleExactAlarms(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      alarmManager.canScheduleExactAlarms()
    } else {
      true
    }
  }

  fun areNotificationsEnabled(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  private fun loadDeliveryState(context: Context): AlertDeliveryState {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val stageName = prefs.getString(KEY_LAST_STAGE, null)
    val stage = stageName?.let { runCatching { AlertStage.valueOf(it) }.getOrNull() }
    return AlertDeliveryState(
      setupTimestampMillis = prefs.getLong(KEY_SETUP_TS, 0L),
      checkInNotified = prefs.getBoolean(KEY_CHECK_IN_NOTIFIED, false),
      lastNotifiedStage = stage,
      lastNotifiedAtMillis = prefs.getLong(KEY_LAST_NOTIFIED_AT, 0L)
    )
  }

  private fun saveDeliveryState(context: Context, state: AlertDeliveryState) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putLong(KEY_SETUP_TS, state.setupTimestampMillis)
      .putBoolean(KEY_CHECK_IN_NOTIFIED, state.checkInNotified)
      .putString(KEY_LAST_STAGE, state.lastNotifiedStage?.name)
      .putLong(KEY_LAST_NOTIFIED_AT, state.lastNotifiedAtMillis)
      .apply()
  }

  fun clearDeliveryState(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
  }

  /**
   * Evaluates whether any milestone notification is due right now (catching up missed alarms
   * across reboots/Doze while preventing duplicate spam) and re-arms the next exact alarm.
   */
  fun evaluateAndNotifyIfDue(
    context: Context,
    projection: CylinderProjection?,
    nowMillis: Long = System.currentTimeMillis()
  ): AlertStage? {
    createNotificationChannel(context)
    if (projection == null) {
      cancelAlarm(context)
      return null
    }

    val rawState = loadDeliveryState(context)
    val normalizedState = Co2AlertPolicy.normalizeStateForProjection(projection, rawState, nowMillis)
    if (normalizedState != rawState) {
      saveDeliveryState(context, normalizedState)
    }

    val dueStage = Co2AlertPolicy.evaluateDueNotification(
      projection = projection,
      rawState = normalizedState,
      nowMillis = nowMillis
    )

    val finalState = if (dueStage != null) {
      showStageNotification(context, dueStage, projection)
      val updated = Co2AlertPolicy.recordNotificationDelivered(
        projection = projection,
        rawState = normalizedState,
        stage = dueStage,
        deliveredAtMillis = nowMillis
      )
      saveDeliveryState(context, updated)
      updated
    } else {
      normalizedState
    }

    armNextAlarm(context, projection, finalState, nowMillis)
    return dueStage
  }

  fun scheduleExactDepletionAlarm(context: Context, projection: CylinderProjection?) {
    createNotificationChannel(context)

    if (projection == null) {
      cancelAlarm(context)
      return
    }

    val now = System.currentTimeMillis()
    val rawState = loadDeliveryState(context)
    val normalizedState = Co2AlertPolicy.normalizeStateForProjection(projection, rawState, now)
    if (normalizedState != rawState) {
      saveDeliveryState(context, normalizedState)
    }

    armNextAlarm(context, projection, normalizedState, now)
  }

  private fun armNextAlarm(
    context: Context,
    projection: CylinderProjection,
    state: AlertDeliveryState,
    nowMillis: Long
  ) {
    val plan = Co2AlertPolicy.computeNextAlarmPlan(projection, state, nowMillis)
    if (plan == null) {
      // Only cancel when burn is paused (0 BPS) and bottle is not low
      cancelPendingAlarmOnly(context)
      if (!projection.hasActiveBurn && !projection.isBelowDangerThreshold && !projection.isEmpty) {
        WorkManager.getInstance(context).cancelUniqueWork(Co2DailyWatchdogWorker.WORK_NAME)
      }
      return
    }

    // Keep daily watchdog alive as long as a cylinder is actively tracked or low
    enqueueDailyWatchdogWork(context)

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, Co2AlarmReceiver::class.java).apply {
      action = ACTION_EXACT_ALARM
      putExtra("ALERT_STAGE", plan.stage.name)
      putExtra("REMAINING_GRAMS", projection.remainingCo2Grams)
      putExtra("DEPLETION_MILLIS", projection.depletionTimestampMillis)
      putExtra("EMPTY_MILLIS", projection.emptyTimestampMillis)
      putExtra("ALERT_MILLIS", projection.alertTimestampMillis)
      putExtra("LEAD_TIME_DAYS", projection.config.safeLeadTimeDays)
      putExtra("IS_CALIBRATED", projection.isScaleCalibrated)
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        // Doze-safe inexact wakeup fallback when exact alarm permission is not granted
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          plan.triggerAtMillis,
          pendingIntent
        )
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          plan.triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.setExact(
          AlarmManager.RTC_WAKEUP,
          plan.triggerAtMillis,
          pendingIntent
        )
      }
    } catch (_: SecurityException) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          plan.triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.set(
          AlarmManager.RTC_WAKEUP,
          plan.triggerAtMillis,
          pendingIntent
        )
      }
    }
  }

  private fun cancelPendingAlarmOnly(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, Co2AlarmReceiver::class.java).apply {
      action = ACTION_EXACT_ALARM
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    if (pendingIntent != null) {
      alarmManager.cancel(pendingIntent)
      pendingIntent.cancel()
    }
  }

  fun cancelAlarm(context: Context) {
    cancelPendingAlarmOnly(context)
    WorkManager.getInstance(context).cancelUniqueWork(Co2DailyWatchdogWorker.WORK_NAME)
  }

  fun triggerTestNotification(context: Context) {
    createNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      NOTIFICATION_ID_TEST,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val canExact = canScheduleExactAlarms(context)
    val exactStatus = if (canExact) "Exact Alarms Active (Doze Bypass OK)" else "Standard Alarms (Allow Exact in Settings)"

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_co2_cylinder)
      .setContentTitle("CO2 Alert System Test: Verified")
      .setContentText("Alarm system online. Status: $exactStatus")
      .setStyle(
        NotificationCompat.BigTextStyle().bigText(
          "Test alert successfully delivered! Status: $exactStatus. Check-in, refill warning, safety cutoff, and empty alerts are armed with a daily watchdog backup."
        )
      )
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID_TEST, builder.build())
  }

  fun showStageNotification(
    context: Context,
    stage: AlertStage,
    projection: CylinderProjection
  ) {
    createNotificationChannel(context)

    val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val effectiveCutoffMillis = if (projection.isScaleCalibrated) {
      projection.depletionTimestampMillis
    } else {
      projection.conservativeDepletionTimestampMillis
    }
    val cutoffDateStr = dateFormatter.format(Date(effectiveCutoffMillis))
    val emptyDateStr = dateFormatter.format(Date(projection.emptyTimestampMillis))

    val copy = Co2AlertPolicy.buildNotificationCopy(
      stage = stage,
      projection = projection,
      cutoffDateStr = cutoffDateStr,
      emptyDateStr = emptyDateStr
    )

    val notificationId = if (stage == AlertStage.CHECK_IN) NOTIFICATION_ID_CHECK_IN else NOTIFICATION_ID_REFILL
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_co2_cylinder)
      .setContentTitle(copy.title)
      .setContentText(copy.body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
      .setPriority(if (copy.isUrgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
      .setCategory(if (copy.isUrgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
      .setVibrate(
        if (copy.isUrgent) longArrayOf(0, 500, 200, 500, 200, 500) else longArrayOf(0, 350, 150, 350)
      )
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(notificationId, builder.build())
  }

  /**
   * Backward-compatible fallback notification method used if database cannot be read during wakeup.
   */
  fun showRefillNotification(
    context: Context,
    remainingGrams: Double,
    depletionMillis: Long,
    leadTimeDays: Int,
    isUrgentEmpty: Boolean
  ) {
    createNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      NOTIFICATION_ID_REFILL,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val depletionDateStr = dateFormatter.format(Date(depletionMillis))

    val remainingDaysApprox = ((depletionMillis - System.currentTimeMillis()) / 86_400_000.0).coerceAtLeast(0.0)
    val daysStr = when {
      remainingDaysApprox < 1.0 -> "< 1 day"
      else -> "~${kotlin.math.ceil(remainingDaysApprox).toInt()} days"
    }

    val title = if (isUrgentEmpty) {
      "CRITICAL: Aquarium CO2 Cylinder At Safety Cutoff!"
    } else {
      "CO2 Refill Warning: $daysStr Remaining"
    }

    val safeGrams = remainingGrams.coerceAtLeast(0.0).roundToInt()
    val content = if (isUrgentEmpty) {
      "Remaining CO2 is at or below safety cutoff (~${safeGrams}g remaining). Check canister weight and prepare refill immediately."
    } else {
      "Estimated CO2: ~${safeGrams}g. Safety cutoff expected around $depletionDateStr ($leadTimeDays d lead). Time to check and arrange a refill."
    }

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_co2_cylinder)
      .setContentTitle(title)
      .setContentText(content)
      .setStyle(NotificationCompat.BigTextStyle().bigText(content))
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID_REFILL, builder.build())
  }
}
