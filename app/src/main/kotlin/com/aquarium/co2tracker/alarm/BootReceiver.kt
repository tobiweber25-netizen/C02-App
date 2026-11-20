package com.aquarium.co2tracker.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aquarium.co2tracker.data.Co2Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val isPermissionStateChange =
      action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
    val isSystemWakeupEvent =
      action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
        action == Intent.ACTION_TIME_CHANGED ||
        action == Intent.ACTION_TIMEZONE_CHANGED ||
        action == Intent.ACTION_DATE_CHANGED ||
        action == "android.intent.action.TIME_SET" ||
        action == "android.intent.action.DATE_CHANGED" ||
        action == "android.intent.action.QUICKBOOT_POWERON" ||
        action == "com.htc.intent.action.QUICKBOOT_POWERON"

    if (isSystemWakeupEvent || isPermissionStateChange) {
      val pendingResult = goAsync()
      scope.launch {
        try {
          val repository = Co2Repository(context)
          val allProjections = repository.getAllProjections()
          if (isPermissionStateChange) {
            Co2AlarmScheduler.scheduleForAllActive(context, allProjections)
          } else {
            Co2AlarmScheduler.evaluateAndNotifyAllActive(context, allProjections)
          }
        } catch (_: Exception) {
          // Graceful catch for direct-boot / credential-encrypted storage before unlock
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
