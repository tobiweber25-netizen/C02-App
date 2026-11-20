package com.aquarium.co2tracker.ui.preview

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.aquarium.co2tracker.domain.*
import com.aquarium.co2tracker.ui.main.Co2UiState
import com.aquarium.co2tracker.ui.main.DashboardTab
import com.aquarium.co2tracker.ui.main.MainScreenContent
import com.aquarium.co2tracker.ui.theme.CO2CylinderTrackerTheme

private fun sampleConfig() = CylinderConfig(
  id = 1L,
  cylinderName = "ADA 90P Rimless CO2",
  tareWeightGrams = 3350.0,
  fullGrossWeightGrams = 4850.0,
  workingPressureValue = 2.5,
  workingPressureUnit = PressureUnit.BAR,
  fluidType = FluidType.WATER,
  elevationMeters = 1700.0,
  atmosphericPressureKpa = 83.0,
  solenoidStartMinutes = 10 * 60 + 30,
  solenoidEndMinutes = 18 * 60,
  startingBps = 2.0,
  dangerThresholdPercent = 10.0,
  notificationLeadTimeDays = 5,
  setupTimestampMillis = 1_760_000_000_000L
)

private fun sampleProjection(
  remainingPercent: Double = 74.0,
  isScaleCalibrated: Boolean = true
): CylinderProjection {
  val cfg = sampleConfig()
  val remainingGrams = cfg.initialNetCo2Grams * (remainingPercent / 100.0)
  val usable = (remainingGrams - cfg.dangerThresholdGrams).coerceAtLeast(0.0)
  val dailyBurn = 4.91
  val daysToDanger = usable / dailyBurn
  val daysToRefill = daysToDanger - cfg.notificationLeadTimeDays
  val now = 1_765_000_000_000L
  val depletionMs = now + (daysToDanger * Co2PhysicsEngine.MILLIS_PER_DAY).toLong()
  val alertMs = depletionMs - (cfg.notificationLeadTimeDays * Co2PhysicsEngine.MILLIS_PER_DAY).toLong()

  return CylinderProjection(
    config = cfg,
    evaluatedSegments = emptyList(),
    currentBps = 2.0,
    currentStartMinutes = cfg.solenoidStartMinutes,
    currentEndMinutes = cfg.solenoidEndMinutes,
    activeSolenoidHoursPerDay = 7.5,
    theoreticalGramsPerBubble = 0.000091,
    activeGramsPerBubble = if (isScaleCalibrated) 0.000089 else 0.000091,
    isScaleCalibrated = isScaleCalibrated,
    hourlyBurnRateGrams = 0.65,
    dailyBurnRateGrams = dailyBurn,
    bankedConsumedGrams = cfg.initialNetCo2Grams - remainingGrams,
    liveSegmentConsumedGrams = 14.2,
    totalConsumedGrams = cfg.initialNetCo2Grams - remainingGrams,
    remainingCo2Grams = remainingGrams,
    remainingTotalPercent = remainingPercent,
    usableRemainingGrams = usable,
    cumulativeBubblesFired = 4_380_000.0,
    daysUntilDangerThreshold = daysToDanger,
    daysUntilRefillWindow = daysToRefill,
    depletionTimestampMillis = depletionMs,
    alertTimestampMillis = alertMs,
    evaluatedAtMillis = now
  )
}

private fun sampleEvents(): List<LedgerEvent> = listOf(
  LedgerEvent(
    id = 4L,
    timestampMillis = 1_764_800_000_000L,
    eventType = LedgerEventType.SCALE_CALIBRATION,
    bps = 2.0,
    solenoidStartMinutes = 630,
    solenoidEndMinutes = 1080,
    scaleWeightGrams = 4460.0,
    note = "Mid-cycle digital scale check-in"
  ),
  LedgerEvent(
    id = 3L,
    timestampMillis = 1_763_600_000_000L,
    eventType = LedgerEventType.DOWNTIME_OVERRIDE,
    bps = 2.0,
    solenoidStartMinutes = 630,
    solenoidEndMinutes = 1080,
    downtimeHoursDelta = -3.5,
    note = "Carpet trim and 50% water change"
  ),
  LedgerEvent(
    id = 2L,
    timestampMillis = 1_762_100_000_000L,
    eventType = LedgerEventType.BPS_CHANGE,
    bps = 2.0,
    solenoidStartMinutes = 630,
    solenoidEndMinutes = 1080,
    note = "Increased flow for Rotala pearling"
  ),
  LedgerEvent(
    id = 1L,
    timestampMillis = 1_760_000_000_000L,
    eventType = LedgerEventType.SETUP,
    bps = 1.5,
    solenoidStartMinutes = 630,
    solenoidEndMinutes = 1080,
    note = "Initial cylinder fill (1,500g net CO2)"
  )
)

@Preview(name = "Dashboard - Optimal Dosing", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun DashboardOptimalPreview() {
  CO2CylinderTrackerTheme {
    MainScreenContent(
      uiState = Co2UiState(
        isLoading = false,
        projection = sampleProjection(remainingPercent = 74.0, isScaleCalibrated = true),
        events = sampleEvents(),
        activeTab = DashboardTab.DASHBOARD
      ),
      snackbarHostState = remember { SnackbarHostState() },
      onSelectTab = {},
      onTriggerUpdateCheck = {},
      onShowBpsSheet = {},
      onShowScheduleSheet = {},
      onShowDowntimeSheet = {},
      onShowScaleSheet = {},
      onShowSetupSheet = {},
      onDeleteEvent = {},
      onTriggerTestNotification = {},
      nowMinuteOfDay = 14 * 60 + 15 // 14:15 — inside dosing window
    )
  }
}

@Preview(name = "Dashboard - Refill Window Amber", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun DashboardRefillWarningPreview() {
  CO2CylinderTrackerTheme {
    MainScreenContent(
      uiState = Co2UiState(
        isLoading = false,
        projection = sampleProjection(remainingPercent = 11.2, isScaleCalibrated = false),
        events = sampleEvents(),
        activeTab = DashboardTab.DASHBOARD
      ),
      snackbarHostState = remember { SnackbarHostState() },
      onSelectTab = {},
      onTriggerUpdateCheck = {},
      onShowBpsSheet = {},
      onShowScheduleSheet = {},
      onShowDowntimeSheet = {},
      onShowScaleSheet = {},
      onShowSetupSheet = {},
      onDeleteEvent = {},
      onTriggerTestNotification = {},
      nowMinuteOfDay = 20 * 60 + 30 // 20:30 — standby off-cycle
    )
  }
}

@Preview(name = "Dashboard - First Run Onboarding", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun DashboardUnconfiguredPreview() {
  CO2CylinderTrackerTheme {
    MainScreenContent(
      uiState = Co2UiState(
        isLoading = false,
        projection = null,
        events = emptyList(),
        activeTab = DashboardTab.DASHBOARD
      ),
      snackbarHostState = remember { SnackbarHostState() },
      onSelectTab = {},
      onTriggerUpdateCheck = {},
      onShowBpsSheet = {},
      onShowScheduleSheet = {},
      onShowDowntimeSheet = {},
      onShowScaleSheet = {},
      onShowSetupSheet = {},
      onDeleteEvent = {},
      onTriggerTestNotification = {}
    )
  }
}

@Preview(name = "Ledger Timeline", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun HistoryTimelinePreview() {
  CO2CylinderTrackerTheme {
    MainScreenContent(
      uiState = Co2UiState(
        isLoading = false,
        projection = sampleProjection(),
        events = sampleEvents(),
        activeTab = DashboardTab.HISTORY
      ),
      snackbarHostState = remember { SnackbarHostState() },
      onSelectTab = {},
      onTriggerUpdateCheck = {},
      onShowBpsSheet = {},
      onShowScheduleSheet = {},
      onShowDowntimeSheet = {},
      onShowScaleSheet = {},
      onShowSetupSheet = {},
      onDeleteEvent = {},
      onTriggerTestNotification = {}
    )
  }
}

@Preview(name = "System & Reliability", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun SettingsSuitePreview() {
  CO2CylinderTrackerTheme {
    MainScreenContent(
      uiState = Co2UiState(
        isLoading = false,
        projection = sampleProjection(),
        events = sampleEvents(),
        activeTab = DashboardTab.SETTINGS
      ),
      snackbarHostState = remember { SnackbarHostState() },
      onSelectTab = {},
      onTriggerUpdateCheck = {},
      onShowBpsSheet = {},
      onShowScheduleSheet = {},
      onShowDowntimeSheet = {},
      onShowScaleSheet = {},
      onShowSetupSheet = {},
      onDeleteEvent = {},
      onTriggerTestNotification = {}
    )
  }
}
