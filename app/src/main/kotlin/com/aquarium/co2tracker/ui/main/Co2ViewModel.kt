package com.aquarium.co2tracker.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler
import com.aquarium.co2tracker.data.Co2Repository
import com.aquarium.co2tracker.domain.CylinderConfig
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.domain.LedgerEvent
import com.aquarium.co2tracker.domain.LedgerEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class DashboardTab {
  DASHBOARD,
  HISTORY,
  SETTINGS
}

data class Co2UiState(
  val isLoading: Boolean = true,
  val projection: CylinderProjection? = null,
  val events: List<LedgerEvent> = emptyList(),
  val activeTab: DashboardTab = DashboardTab.DASHBOARD,
  val showBpsDialog: Boolean = false,
  val showScheduleDialog: Boolean = false,
  val showDowntimeDialog: Boolean = false,
  val showScaleCalibrationDialog: Boolean = false,
  val showSetupDialog: Boolean = false,
  val showSettingsDialog: Boolean = false,
  val infoMessage: String? = null
)

class Co2ViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = Co2Repository(application)

  private val _uiState = MutableStateFlow(Co2UiState())
  val uiState: StateFlow<Co2UiState> = _uiState.asStateFlow()

  init {
    loadData()
  }

  fun loadData() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      repository.initialize()
      val projection = repository.refreshProjection()
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          isLoading = false,
          projection = projection,
          events = sortEventsForHistory(events)
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), projection)
    }
  }

  /**
   * Silently refreshes live CO2 consumption and projection timestamps without triggering
   * a full-screen loading indicator. Safe to invoke on ON_RESUME and periodic foreground ticks.
   */
  fun refreshLiveProjection() {
    viewModelScope.launch {
      val projection = repository.refreshProjection()
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          isLoading = false,
          projection = projection,
          events = sortEventsForHistory(events)
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), projection)
    }
  }

  fun setActiveTab(tab: DashboardTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun showBpsDialog(show: Boolean) = _uiState.update { it.copy(showBpsDialog = show) }
  fun showScheduleDialog(show: Boolean) = _uiState.update { it.copy(showScheduleDialog = show) }
  fun showDowntimeDialog(show: Boolean) = _uiState.update { it.copy(showDowntimeDialog = show) }
  fun showScaleCalibrationDialog(show: Boolean) = _uiState.update { it.copy(showScaleCalibrationDialog = show) }
  fun showSetupDialog(show: Boolean) = _uiState.update { it.copy(showSetupDialog = show) }
  fun showSettingsDialog(show: Boolean) = _uiState.update { it.copy(showSettingsDialog = show) }
  fun clearInfoMessage() = _uiState.update { it.copy(infoMessage = null) }

  fun updateBps(newBps: Double) {
    viewModelScope.launch {
      val curProj = repository.refreshProjection() ?: _uiState.value.projection ?: return@launch
      val safeBps = newBps.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: return@launch
      val eventTs = monotonicTimestamp(curProj)
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.BPS_CHANGE,
        bps = safeBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        note = "Adjusted BPS from ${curProj.currentBps} to $safeBps",
        timestampMillis = eventTs
      )
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = sortEventsForHistory(events),
          showBpsDialog = false,
          infoMessage = "Bubble rate updated to $safeBps BPS"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun updateSchedule(startMinutes: Int, endMinutes: Int) {
    viewModelScope.launch {
      val curProj = repository.refreshProjection() ?: _uiState.value.projection ?: return@launch
      val eventTs = monotonicTimestamp(curProj)
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.SCHEDULE_CHANGE,
        bps = curProj.currentBps,
        solenoidStartMinutes = startMinutes,
        solenoidEndMinutes = endMinutes,
        note = "Schedule changed to ${formatMinutes(startMinutes)} - ${formatMinutes(endMinutes)}",
        timestampMillis = eventTs
      )
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = sortEventsForHistory(events),
          showScheduleDialog = false,
          infoMessage = "Solenoid schedule updated"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun logDowntime(hoursDelta: Double, note: String) {
    viewModelScope.launch {
      val curProj = repository.refreshProjection() ?: _uiState.value.projection ?: return@launch
      val safeHours = hoursDelta.takeIf { it.isFinite() && it != 0.0 } ?: return@launch
      val eventTs = monotonicTimestamp(curProj)
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.DOWNTIME_OVERRIDE,
        bps = curProj.currentBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        downtimeHoursDelta = safeHours,
        note = if (note.isNotBlank()) note else "Runtime override: $safeHours hrs",
        timestampMillis = eventTs
      )
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = sortEventsForHistory(events),
          showDowntimeDialog = false,
          infoMessage = "Logged $safeHours hrs override"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun logScaleCalibration(currentScaleWeightGrams: Double) {
    viewModelScope.launch {
      val curProj = repository.refreshProjection() ?: _uiState.value.projection ?: return@launch
      val safeWeight = currentScaleWeightGrams.takeIf { it.isFinite() && it >= 0.0 } ?: return@launch
      val eventTs = monotonicTimestamp(curProj)
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.SCALE_CALIBRATION,
        bps = curProj.currentBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        scaleWeightGrams = safeWeight,
        note = "Scale check-in: ${safeWeight.roundToInt()}g",
        timestampMillis = eventTs
      )
      val events = repository.getEvents()
      val latestFeedback = updatedProjection?.latestCalibrationFeedback
      val feedbackMsg = when {
        updatedProjection?.possibleLeakDetected == true ->
          "Calibrated, but CO2 loss is abnormally high—check regulator & tubing for leaks!"
        !latestFeedback.isNullOrBlank() ->
          latestFeedback
        else ->
          "Scale check-in logged (${safeWeight.roundToInt()}g)"
      }
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = sortEventsForHistory(events),
          showScaleCalibrationDialog = false,
          infoMessage = feedbackMsg
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  /**
   * Updates cylinder physics & alert settings in-place WITHOUT clearing ledger history
   * or resetting the cylinder to 100% full.
   */
  fun updateCylinderSettings(updatedConfig: CylinderConfig) {
    viewModelScope.launch {
      val existing = repository.getConfig()
      val preservedConfig = if (existing != null) {
        updatedConfig.copy(
          id = existing.id,
          setupTimestampMillis = existing.setupTimestampMillis
        )
      } else {
        updatedConfig
      }
      val updatedProjection = repository.saveConfig(preservedConfig)
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = sortEventsForHistory(events),
          showSetupDialog = false,
          infoMessage = "Cylinder settings updated (history preserved)"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun resetOrSetupCylinder(config: CylinderConfig) {
    viewModelScope.launch {
      Co2AlarmScheduler.clearDeliveryState(getApplication())
      val updatedProjection = repository.resetCylinder(config)
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = sortEventsForHistory(events),
          showSetupDialog = false,
          infoMessage = "Cylinder initialized with ${config.initialNetCo2Grams.roundToInt()}g net CO2"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun deleteEvent(eventId: Long) {
    viewModelScope.launch {
      val updatedProjection = repository.deleteEvent(eventId)
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = sortEventsForHistory(events),
          infoMessage = "Ledger event removed. Recalculated timeline."
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun triggerTestNotification() {
    val app = getApplication<Application>()
    if (!Co2AlarmScheduler.areNotificationsEnabled(app)) {
      _uiState.update {
        it.copy(infoMessage = "Notifications are disabled in Android Settings—enable them to receive CO2 alerts.")
      }
      return
    }
    Co2AlarmScheduler.triggerTestNotification(app)
    _uiState.update { it.copy(infoMessage = "Test notification sent") }
  }

  private fun sortEventsForHistory(events: List<LedgerEvent>): List<LedgerEvent> =
    events.sortedWith(
      compareByDescending<LedgerEvent> { it.timestampMillis }.thenByDescending { it.id }
    )

  private fun monotonicTimestamp(projection: CylinderProjection): Long {
    val lastTs = projection.evaluatedSegments.lastOrNull()?.event?.timestampMillis ?: 0L
    return max(System.currentTimeMillis(), lastTs)
  }

  private fun formatMinutes(min: Int): String {
    val normalized = ((min % 1440) + 1440) % 1440
    val h = normalized / 60
    val m = normalized % 60
    return String.format(Locale.US, "%02d:%02d", h, m)
  }
}
