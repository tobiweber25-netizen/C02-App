package com.aquarium.co2tracker.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler
import com.aquarium.co2tracker.data.Co2Repository
import com.aquarium.co2tracker.domain.Co2PhysicsEngine
import com.aquarium.co2tracker.domain.CylinderConfig
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.domain.DropCheckerStatus
import com.aquarium.co2tracker.domain.LedgerEvent
import com.aquarium.co2tracker.domain.LedgerEventType
import com.aquarium.co2tracker.domain.RefillArchiveEntry
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
  val infoMessage: String? = null,
  val allCylinders: List<CylinderProjection> = emptyList(),
  val refillArchive: List<RefillArchiveEntry> = emptyList(),
  val historicalSealDriftPercent: Int? = null,
  val showBottleFleetSheet: Boolean = false
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
      syncFullState(clearLoading = true)
    }
  }

  fun refreshLiveProjection() {
    viewModelScope.launch {
      syncFullState(clearLoading = true)
    }
  }

  private suspend fun syncFullState(
    clearLoading: Boolean = false,
    infoMessage: String? = null,
    closeDialogs: (Co2UiState) -> Co2UiState = { it }
  ) {
    val projection = repository.refreshProjection()
    val events = repository.getEvents()
    val allCylinders = repository.getAllProjections()
    val archive = repository.getRefillArchive()
    val driftPct = projection?.let {
      Co2PhysicsEngine.detectHistoricalSealDriftPercent(it, archive)
    }

    _uiState.update { current ->
      val updated = closeDialogs(current).copy(
        isLoading = if (clearLoading) false else current.isLoading,
        projection = projection,
        events = sortEventsForHistory(events),
        allCylinders = allCylinders,
        refillArchive = archive,
        historicalSealDriftPercent = driftPct,
        infoMessage = infoMessage ?: current.infoMessage
      )
      updated
    }

    Co2AlarmScheduler.scheduleForAllActive(getApplication(), allCylinders)
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
  fun showBottleFleetSheet(show: Boolean) = _uiState.update { it.copy(showBottleFleetSheet = show) }
  fun clearInfoMessage() = _uiState.update { it.copy(infoMessage = null) }

  fun updateBps(newBps: Double, dropCheckerStatus: DropCheckerStatus? = null) {
    viewModelScope.launch {
      val curProj = repository.refreshProjection() ?: _uiState.value.projection ?: return@launch
      val safeBps = newBps.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: return@launch
      val eventTs = monotonicTimestamp(curProj)
      val dcNote = dropCheckerStatus?.let { " • Drop checker: ${it.label}" }.orEmpty()
      repository.addEvent(
        eventType = LedgerEventType.BPS_CHANGE,
        bps = safeBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        note = "Adjusted BPS from ${curProj.currentBps} to $safeBps$dcNote",
        timestampMillis = eventTs,
        dropCheckerStatus = dropCheckerStatus
      )
      syncFullState(
        infoMessage = "Bubble rate updated to $safeBps BPS",
        closeDialogs = { it.copy(showBpsDialog = false) }
      )
    }
  }

  fun updateSchedule(startMinutes: Int, endMinutes: Int) {
    viewModelScope.launch {
      val curProj = repository.refreshProjection() ?: _uiState.value.projection ?: return@launch
      val eventTs = monotonicTimestamp(curProj)
      repository.addEvent(
        eventType = LedgerEventType.SCHEDULE_CHANGE,
        bps = curProj.currentBps,
        solenoidStartMinutes = startMinutes,
        solenoidEndMinutes = endMinutes,
        note = "Schedule changed to ${formatMinutes(startMinutes)} - ${formatMinutes(endMinutes)}",
        timestampMillis = eventTs
      )
      syncFullState(
        infoMessage = "Solenoid schedule updated",
        closeDialogs = { it.copy(showScheduleDialog = false) }
      )
    }
  }

  fun logDowntime(hoursDelta: Double, note: String) {
    viewModelScope.launch {
      val curProj = repository.refreshProjection() ?: _uiState.value.projection ?: return@launch
      val safeHours = hoursDelta.takeIf { it.isFinite() && it != 0.0 } ?: return@launch
      val eventTs = monotonicTimestamp(curProj)
      repository.addEvent(
        eventType = LedgerEventType.DOWNTIME_OVERRIDE,
        bps = curProj.currentBps,
        solenoidStartMinutes = curProj.currentStartMinutes,
        solenoidEndMinutes = curProj.currentEndMinutes,
        downtimeHoursDelta = safeHours,
        note = if (note.isNotBlank()) note else "Runtime override: $safeHours hrs",
        timestampMillis = eventTs
      )
      syncFullState(
        infoMessage = "Logged $safeHours hrs override",
        closeDialogs = { it.copy(showDowntimeDialog = false) }
      )
    }
  }

  fun toggleLivePause() {
    viewModelScope.launch {
      val (_, message) = repository.toggleLivePause()
      syncFullState(infoMessage = message)
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
      val latestFeedback = updatedProjection?.latestCalibrationFeedback
      val feedbackMsg = when {
        updatedProjection?.possibleLeakDetected == true ->
          "Calibrated, but CO2 loss is abnormally high—check regulator & tubing for leaks!"
        !latestFeedback.isNullOrBlank() ->
          latestFeedback
        else ->
          "Scale check-in logged (${safeWeight.roundToInt()}g)"
      }
      syncFullState(
        infoMessage = feedbackMsg,
        closeDialogs = { it.copy(showScaleCalibrationDialog = false) }
      )
    }
  }

  fun updateCylinderSettings(updatedConfig: CylinderConfig) {
    viewModelScope.launch {
      val existing = repository.getConfig()
      val preservedConfig = if (existing != null) {
        updatedConfig.copy(
          id = existing.id,
          setupTimestampMillis = existing.setupTimestampMillis,
          bottleStatus = existing.bottleStatus,
          isSelected = existing.isSelected,
          pausedSinceMillis = existing.pausedSinceMillis,
          carriedGramsPerBubble = existing.carriedGramsPerBubble
        )
      } else {
        updatedConfig
      }
      repository.saveConfig(preservedConfig)
      syncFullState(
        infoMessage = "Cylinder settings updated (history preserved)",
        closeDialogs = { it.copy(showSetupDialog = false) }
      )
    }
  }

  fun resetOrSetupCylinder(config: CylinderConfig) {
    viewModelScope.launch {
      val existing = repository.getConfig()
      Co2AlarmScheduler.clearDeliveryState(getApplication(), existing?.id ?: config.id)
      repository.resetCylinder(config, archivePrevious = true)
      val carriedNote = if (config.carriedGramsPerBubble != null) " (calibration carried over)" else ""
      syncFullState(
        infoMessage = "Cylinder initialized with ${config.initialNetCo2Grams.roundToInt()}g net CO2$carriedNote",
        closeDialogs = { it.copy(showSetupDialog = false) }
      )
    }
  }

  fun addNewCylinder(config: CylinderConfig, selectImmediately: Boolean = true) {
    viewModelScope.launch {
      repository.addNewCylinder(config, selectImmediately = selectImmediately)
      syncFullState(
        infoMessage = "Added '${config.cylinderName}' (${config.bottleStatus.label})",
        closeDialogs = { it.copy(showSetupDialog = false, showBottleFleetSheet = false) }
      )
    }
  }

  fun selectCylinder(cylinderId: Long) {
    viewModelScope.launch {
      val selected = repository.selectCylinder(cylinderId)
      syncFullState(
        infoMessage = selected?.let { "Switched to ${it.config.cylinderName}" },
        closeDialogs = { it.copy(showBottleFleetSheet = false) }
      )
    }
  }

  fun swapToSpareBottle(spareCylinderId: Long, carryCalibration: Boolean = true) {
    viewModelScope.launch {
      Co2AlarmScheduler.clearDeliveryState(getApplication(), spareCylinderId)
      val swapped = repository.swapToSpareBottle(spareCylinderId, carryCalibrationFromCurrent = carryCalibration)
      syncFullState(
        infoMessage = swapped?.let { "Swapped in ${it.config.cylinderName} • previous bottle marked Needs Refill" },
        closeDialogs = { it.copy(showBottleFleetSheet = false) }
      )
    }
  }

  fun markSpareRefilled(cylinderId: Long, newFullGrossGrams: Double? = null, refillCost: Double? = null) {
    viewModelScope.launch {
      repository.markBottleRefilledAsSpare(cylinderId, newFullGrossGrams, refillCost)
      syncFullState(infoMessage = "Spare bottle marked full & ready")
    }
  }

  fun deleteCylinder(cylinderId: Long) {
    viewModelScope.launch {
      Co2AlarmScheduler.clearDeliveryState(getApplication(), cylinderId)
      repository.deleteCylinder(cylinderId)
      syncFullState(infoMessage = "Bottle removed from fleet")
    }
  }

  fun deleteEvent(eventId: Long) {
    viewModelScope.launch {
      repository.deleteEvent(eventId)
      syncFullState(infoMessage = "Ledger event removed. Recalculated timeline.")
    }
  }

  fun deleteArchiveEntry(entryId: Long) {
    viewModelScope.launch {
      repository.deleteArchiveEntry(entryId)
      syncFullState(infoMessage = "Archived refill record removed")
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
