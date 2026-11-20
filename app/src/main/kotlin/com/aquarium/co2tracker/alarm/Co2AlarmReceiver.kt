package com.aquarium.co2tracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aquarium.co2tracker.data.Co2Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Co2AlarmReceiver : BroadcastReceiver() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onReceive(context: Context, intent: Intent?) {
    val pendingResult = goAsync()
    scope.launch {
      try {
        val repository = Co2Repository(context)
        val projection = repository.refreshProjection()
        Co2AlarmScheduler.evaluateAndNotifyIfDue(context, projection)
      } catch (_: Exception) {
        // Fallback to intent extras only if database could not be read
        if (intent != null && intent.hasExtra("DEPLETION_MILLIS")) {
          val remainingGrams = intent.getDoubleExtra("REMAINING_GRAMS", 0.0).coerceAtLeast(0.0)
          val depletionMillis = intent.getLongExtra("DEPLETION_MILLIS", System.currentTimeMillis())
          val leadTimeDays = intent.getIntExtra("LEAD_TIME_DAYS", 5)

          Co2AlarmScheduler.showRefillNotification(
            context = context,
            remainingGrams = remainingGrams,
            depletionMillis = depletionMillis,
            leadTimeDays = leadTimeDays,
            isUrgentEmpty = remainingGrams <= 0.5 || depletionMillis <= System.currentTimeMillis()
          )
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
