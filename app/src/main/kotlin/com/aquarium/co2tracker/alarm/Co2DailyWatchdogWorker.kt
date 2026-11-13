package com.aquarium.co2tracker.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aquarium.co2tracker.data.Co2Repository

/**
 * Redundant daily safety net watchdog for long-range alert reliability (months into future).
 * Recalculates depletion projection from SQLite ledger, re-arms exact AlarmManager alarms,
 * and triggers refill notifications directly if the alert threshold has arrived.
 */
class Co2DailyWatchdogWorker(
  context: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val repository = Co2Repository(applicationContext)
      val projection = repository.refreshProjection()
      if (projection == null) {
        Co2AlarmScheduler.cancelAlarm(applicationContext)
        return Result.success()
      }

      // 1. Re-arm AlarmManager exact alarm
      Co2AlarmScheduler.scheduleExactDepletionAlarm(applicationContext, projection)

      // 2. Direct safety net check
      val now = System.currentTimeMillis()
      if (projection.alertTimestampMillis <= now || projection.isInRefillWindow || projection.isBelowDangerThreshold) {
        Co2AlarmScheduler.showRefillNotification(
          context = applicationContext,
          remainingGrams = projection.remainingCo2Grams,
          depletionMillis = projection.depletionTimestampMillis,
          leadTimeDays = projection.config.notificationLeadTimeDays,
          isUrgentEmpty = projection.isBelowDangerThreshold
        )
      }

      Result.success()
    } catch (_: Exception) {
      Result.retry()
    }
  }

  companion object {
    const val WORK_NAME = "co2_daily_watchdog_work"
  }
}
