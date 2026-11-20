package com.aquarium.co2tracker.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aquarium.co2tracker.data.Co2Repository

/**
 * Redundant daily safety net watchdog for long-range alert reliability across all active cylinders.
 */
class Co2DailyWatchdogWorker(
  context: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val repository = Co2Repository(applicationContext)
      val allProjections = repository.getAllProjections()
      Co2AlarmScheduler.evaluateAndNotifyAllActive(applicationContext, allProjections)
      Result.success()
    } catch (_: Exception) {
      Result.retry()
    }
  }

  companion object {
    const val WORK_NAME = "co2_daily_watchdog_work"
  }
}
