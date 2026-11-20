package com.aquarium.co2tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.aquarium.co2tracker.MainActivity
import com.aquarium.co2tracker.R
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler
import com.aquarium.co2tracker.data.Co2Repository
import com.aquarium.co2tracker.domain.BottleStatus
import com.aquarium.co2tracker.ui.components.currentLocalMinuteOfDay
import com.aquarium.co2tracker.ui.components.isSolenoidActiveAtMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class Co2WidgetProvider : AppWidgetProvider() {

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    val pendingResult = goAsync()
    scope.launch {
      try {
        renderWidgets(context, appWidgetManager, appWidgetIds)
      } finally {
        pendingResult.finish()
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_WIDGET_TOGGLE_PAUSE) {
      val pendingResult = goAsync()
      scope.launch {
        try {
          val repo = Co2Repository(context)
          repo.toggleLivePause()
          val allProjections = repo.getAllProjections()
          Co2AlarmScheduler.scheduleForAllActive(context, allProjections)
          val manager = AppWidgetManager.getInstance(context)
          val ids = manager.getAppWidgetIds(ComponentName(context, Co2WidgetProvider::class.java))
          if (ids.isNotEmpty()) {
            renderWidgets(context, manager, ids)
          }
        } finally {
          pendingResult.finish()
        }
      }
    }
  }

  companion object {
    const val ACTION_WIDGET_TOGGLE_PAUSE = "com.aquarium.co2tracker.ACTION_WIDGET_TOGGLE_PAUSE"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAllWidgets(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, Co2WidgetProvider::class.java))
      if (ids.isEmpty()) return
      scope.launch {
        runCatching { renderWidgets(context, manager, ids) }
      }
    }

    private suspend fun renderWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray
    ) {
      val repo = Co2Repository(context)
      val projection = repo.refreshProjection()
      val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

      val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val openAppPending = PendingIntent.getActivity(
        context,
        5100,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val togglePauseIntent = Intent(context, Co2WidgetProvider::class.java).apply {
        action = ACTION_WIDGET_TOGGLE_PAUSE
      }
      val togglePausePending = PendingIntent.getBroadcast(
        context,
        5101,
        togglePauseIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val emerald = ContextCompat.getColor(context, R.color.widget_emerald)
      val cyan = ContextCompat.getColor(context, R.color.widget_cyan)
      val amber = ContextCompat.getColor(context, R.color.widget_amber)
      val coral = ContextCompat.getColor(context, R.color.widget_coral)

      for (widgetId in appWidgetIds) {
        val views = RemoteViews(context.packageName, R.layout.widget_co2_status)
        views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

        if (projection == null) {
          views.setTextViewText(R.id.widget_cylinder_name, "AquaCO2 Pulse")
          views.setTextViewText(R.id.widget_status_badge, "SETUP")
          views.setTextColor(R.id.widget_status_badge, cyan)
          views.setTextViewText(R.id.widget_percent, "--%")
          views.setTextColor(R.id.widget_percent, emerald)
          views.setTextViewText(R.id.widget_grams, "Tap to configure cylinder")
          views.setTextViewText(R.id.widget_runway, "Weight & schedule needed")
          views.setTextViewText(R.id.widget_empty_date, "No bottle active")
          views.setViewVisibility(R.id.widget_pause_button, View.GONE)
        } else {
          val isDosingNow = projection.hasActiveBurn && !projection.isEmpty && !projection.isBelowDangerThreshold &&
            isSolenoidActiveAtMinute(
              currentLocalMinuteOfDay(),
              projection.currentStartMinutes,
              projection.currentEndMinutes
            )

          val (badgeText, badgeColor) = when {
            projection.config.bottleStatus == BottleStatus.NEEDS_REFILL || projection.isEmpty -> "EMPTY" to coral
            projection.config.bottleStatus == BottleStatus.SPARE_FULL -> "SPARE" to cyan
            projection.isPaused -> "PAUSED" to amber
            projection.isBelowDangerThreshold -> "CUTOFF" to coral
            projection.isInRefillWindow -> "REFILL" to amber
            isDosingNow -> "DOSING" to emerald
            else -> "STANDBY" to cyan
          }

          val percentColor = when {
            projection.isBelowDangerThreshold || projection.isEmpty -> coral
            projection.isInRefillWindow || projection.isPaused -> amber
            else -> emerald
          }
          val percentText = when {
            projection.remainingCo2Grams > 0.5 && projection.remainingTotalPercent < 1.0 -> "<1%"
            else -> "${projection.remainingTotalPercent.roundToInt()}%"
          }

          val runwayText = when {
            projection.config.bottleStatus == BottleStatus.SPARE_FULL -> "Full spare ready to swap"
            projection.config.bottleStatus == BottleStatus.NEEDS_REFILL || projection.isEmpty -> "Refill needed now"
            projection.isPaused -> String.format(Locale.US, "Paused • %.1fh missed", projection.pausedMissedSolenoidHours)
            !projection.hasActiveBurn -> "Tracking paused (0 BPS)"
            projection.isBelowDangerThreshold -> "Below safety cutoff!"
            projection.isInRefillWindow -> "Refill window open now"
            else -> {
              val days = ceil(projection.daysUntilRefillWindow.coerceAtLeast(0.0)).toInt()
              if (days <= 1) "Refill window in <1 day" else "Refill in $days days"
            }
          }

          val emptyLabel = when {
            projection.isEmpty -> "Empty: Now"
            !projection.hasActiveBurn -> "Empty: Paused"
            else -> "Empty: ${dateFmt.format(Date(projection.emptyTimestampMillis))}"
          }

          views.setTextViewText(R.id.widget_cylinder_name, projection.config.cylinderName)
          views.setTextViewText(R.id.widget_status_badge, badgeText)
          views.setTextColor(R.id.widget_status_badge, badgeColor)
          views.setTextViewText(R.id.widget_percent, percentText)
          views.setTextColor(R.id.widget_percent, percentColor)
          views.setTextViewText(
            R.id.widget_grams,
            String.format(
              Locale.US,
              "%,dg / %,dg CO2",
              projection.remainingCo2Grams.roundToInt(),
              projection.config.initialNetCo2Grams.roundToInt()
            )
          )
          views.setTextViewText(R.id.widget_runway, runwayText)
          views.setTextViewText(R.id.widget_empty_date, emptyLabel)

          if (projection.config.bottleStatus == BottleStatus.ACTIVE) {
            views.setViewVisibility(R.id.widget_pause_button, View.VISIBLE)
            views.setTextViewText(
              R.id.widget_pause_button,
              if (projection.isPaused) "Resume" else "Pause"
            )
            views.setTextColor(
              R.id.widget_pause_button,
              if (projection.isPaused) emerald else amber
            )
            views.setOnClickPendingIntent(R.id.widget_pause_button, togglePausePending)
          } else {
            views.setViewVisibility(R.id.widget_pause_button, View.GONE)
          }
        }

        appWidgetManager.updateAppWidget(widgetId, views)
      }
    }
  }
}
