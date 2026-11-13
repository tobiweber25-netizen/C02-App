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
          events = events.sortedByDescending { e -> e.timestampMillis }
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
      val curProj = _uiState.value.projection ?: return@launch
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.BPS_CHANGE,
        bps = newBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        note = "Adjusted BPS from ${curProj.currentBps} to $newBps"
      )
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = events.sortedByDescending { e -> e.timestampMillis },
          showBpsDialog = false,
          infoMessage = "Bubble rate updated to $newBps BPS"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun updateSchedule(startMinutes: Int, endMinutes: Int) {
    viewModelScope.launch {
      val curProj = _uiState.value.projection ?: return@launch
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.SCHEDULE_CHANGE,
        bps = curProj.currentBps,
        solenoidStartMinutes = startMinutes,
        solenoidEndMinutes = endMinutes,
        note = "Schedule changed to ${formatMinutes(startMinutes)} - ${formatMinutes(endMinutes)}"
      )
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = events.sortedByDescending { e -> e.timestampMillis },
          showScheduleDialog = false,
          infoMessage = "Solenoid schedule updated"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun logDowntime(hoursDelta: Double, note: String) {
    viewModelScope.launch {
      val curProj = _uiState.value.projection ?: return@launch
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.DOWNTIME_OVERRIDE,
        bps = curProj.currentBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        downtimeHoursDelta = hoursDelta,
        note = if (note.isNotBlank()) note else "Runtime override: $hoursDelta hrs"
      )
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = events.sortedByDescending { e -> e.timestampMillis },
          showDowntimeDialog = false,
          infoMessage = "Logged $hoursDelta hrs override"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun logScaleCalibration(currentScaleWeightGrams: Double) {
    viewModelScope.launch {
      val curProj = _uiState.value.projection ?: return@launch
      val updatedProjection = repository.addEvent(
        eventType = LedgerEventType.SCALE_CALIBRATION,
        bps = curProj.currentBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        scaleWeightGrams = currentScaleWeightGrams,
        note = "Scale check-in: ${currentScaleWeightGrams.toInt()}g"
      )
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = events.sortedByDescending { e -> e.timestampMillis },
          showScaleCalibrationDialog = false,
          infoMessage = "Scale calibration complete. Burn coefficient updated!"
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun resetOrSetupCylinder(config: CylinderConfig) {
    viewModelScope.launch {
      val updatedProjection = repository.resetCylinder(config)
      val events = repository.getEvents()
      _uiState.update {
        it.copy(
          projection = updatedProjection,
          events = events.sortedByDescending { e -> e.timestampMillis },
          showSetupDialog = false,
          infoMessage = "Cylinder initialized with ${config.initialNetCo2Grams.toInt()}g net CO2"
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
          events = events.sortedByDescending { e -> e.timestampMillis },
          infoMessage = "Ledger event removed. Recalculated timeline."
        )
      }
      Co2AlarmScheduler.scheduleExactDepletionAlarm(getApplication(), updatedProjection)
    }
  }

  fun triggerTestNotification() {
    Co2AlarmScheduler.triggerTestNotification(getApplication())
    _uiState.update { it.copy(infoMessage = "Test notification sent") }
  }

  private fun formatMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return String.format("%02d:%02d", h, m)
  }
}
