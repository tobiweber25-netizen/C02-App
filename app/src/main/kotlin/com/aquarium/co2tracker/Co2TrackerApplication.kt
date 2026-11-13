package com.aquarium.co2tracker

import android.app.Application
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler

class Co2TrackerApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    Co2AlarmScheduler.createNotificationChannel(this)
    Co2AlarmScheduler.enqueueDailyWatchdogWork(this)
  }
}
