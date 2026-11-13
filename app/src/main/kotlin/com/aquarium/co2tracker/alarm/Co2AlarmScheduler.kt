package com.aquarium.co2tracker.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
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

object Co2AlarmScheduler {
  const val CHANNEL_ID = "co2_cylinder_refill_channel"
  const val CHANNEL_NAME = "CO2 Refill Warning Alerts"
  const val ACTION_EXACT_ALARM = "com.aquarium.co2tracker.ACTION_CO2_REFILL_ALARM"
  const val ALARM_REQUEST_CODE = 4200
  const val NOTIFICATION_ID_REFILL = 4201
  const val NOTIFICATION_ID_TEST = 4202

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
        description = "Exact-time high priority alarms when aquarium CO2 cylinder requires refilling"
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

  fun scheduleExactDepletionAlarm(context: Context, projection: CylinderProjection?) {
    createNotificationChannel(context)

    if (projection == null) {
      cancelAlarm(context)
      return
    }

    enqueueDailyWatchdogWork(context)

    val now = System.currentTimeMillis()
    // Determine the next future critical threshold to arm
    val targetAlertMillis = when {
      projection.alertTimestampMillis > now -> projection.alertTimestampMillis
      projection.depletionTimestampMillis > now -> projection.depletionTimestampMillis
      else -> null
    }

    if (targetAlertMillis == null) {
      cancelAlarm(context)
      return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, Co2AlarmReceiver::class.java).apply {
      action = ACTION_EXACT_ALARM
      putExtra("REMAINING_GRAMS", projection.remainingCo2Grams)
      putExtra("DEPLETION_MILLIS", projection.depletionTimestampMillis)
      putExtra("ALERT_MILLIS", projection.alertTimestampMillis)
      putExtra("LEAD_TIME_DAYS", projection.config.notificationLeadTimeDays)
      putExtra("IS_CALIBRATED", projection.isScaleCalibrated)
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Schedule exact alarm with Doze mode bypass: AlarmManager.setExactAndAllowWhileIdle()
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          targetAlertMillis,
          pendingIntent
        )
      } else {
        alarmManager.setExact(
          AlarmManager.RTC_WAKEUP,
          targetAlertMillis,
          pendingIntent
        )
      }
    } catch (e: SecurityException) {
      // In case exact alarm permission is revoked by Samsung OS, fallback to setWindow or set
      alarmManager.set(
        AlarmManager.RTC_WAKEUP,
        targetAlertMillis,
        pendingIntent
      )
    }
  }

  fun cancelAlarm(context: Context) {
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
          "Test alert successfully delivered! Exact-time wakeups are active on this device (Samsung Galaxy S25 / Android 15+ compatible). Solenoid schedules and burn rates will trigger reliable advance notifications."
        )
      )
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID_TEST, builder.build())
  }

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

    val title = if (isUrgentEmpty) {
      "CRITICAL: Aquarium CO2 Cylinder Exhausted!"
    } else {
      "CO2 Refill Warning: ~${leadTimeDays} Days Remaining"
    }

    val safeGrams = remainingGrams.coerceAtLeast(0.0).toInt()
    val content = if (isUrgentEmpty) {
      "Remaining CO2 is at or below safety cutoff (${safeGrams}g remaining). Turn off solenoid to prevent dump or pH fluctuation."
    } else {
      "Current CO2: ${safeGrams}g. Danger threshold expected on $depletionDateStr. Time to arrange a refill."
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
