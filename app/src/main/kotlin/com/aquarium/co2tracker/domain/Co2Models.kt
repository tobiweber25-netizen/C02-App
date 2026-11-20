package com.aquarium.co2tracker.domain

enum class PressureUnit(val symbol: String) {
  BAR("Bar"),
  PSI("PSI");

  fun toBar(value: Double): Double = when (this) {
    BAR -> value
    PSI -> value / PSI_PER_BAR
  }

  companion object {
    const val PSI_PER_BAR = 14.5037738
  }
}

enum class FluidType(
  val displayName: String,
  val volumeMultiplier: Double
) {
  WATER("Water (1.0x)", 1.0),
  MINERAL_OIL("Mineral Oil (1.35x)", 1.35)
}

enum class LedgerEventType(val displayTitle: String) {
  SETUP("Initial Cylinder Setup"),
  BPS_CHANGE("Bubble Rate (BPS) Update"),
  SCHEDULE_CHANGE("Solenoid Schedule Update"),
  DOWNTIME_OVERRIDE("Downtime / Runtime Override"),
  SCALE_CALIBRATION("Scale Weight Check-In")
}

enum class BottleStatus(val label: String) {
  ACTIVE("Active on Tank"),
  SPARE_FULL("Full Spare Ready"),
  NEEDS_REFILL("Needs Refill")
}

enum class DropCheckerStatus(val label: String, val shortLabel: String) {
  LOW_BLUE("Low CO2 (Blue)", "BLUE"),
  OPTIMAL_GREEN("Optimal ~30ppm (Green)", "GREEN"),
  HIGH_YELLOW("High CO2 (Yellow)", "YELLOW")
}

data class CylinderConfig(
  val id: Long = 1L,
  val cylinderName: String = "Primary CO2 Cylinder",
  val tareWeightGrams: Double = 0.0,
  val fullGrossWeightGrams: Double = 0.0,
  val includeRegulatorWeight: Boolean = false,
  val regulatorWeightGrams: Double = 0.0,
  val workingPressureValue: Double = 2.5,
  val workingPressureUnit: PressureUnit = PressureUnit.BAR,
  val fluidType: FluidType = FluidType.WATER,
  val elevationMeters: Double = 0.0,
  val atmosphericPressureKpa: Double = 101.3,
  val solenoidStartMinutes: Int = 10 * 60 + 30, // 10:30
  val solenoidEndMinutes: Int = 18 * 60,        // 18:00 (7.5 hrs/day)
  val startingBps: Double = 1.5,
  val dangerThresholdPercent: Double = 10.0,
  val notificationLeadTimeDays: Int = 5,
  val setupTimestampMillis: Long = System.currentTimeMillis(),
  val bottleStatus: BottleStatus = BottleStatus.ACTIVE,
  val isSelected: Boolean = true,
  val pausedSinceMillis: Long? = null,
  val carriedGramsPerBubble: Double? = null,
  val refillShopDaysMask: Int = ALL_DAYS_MASK,
  val refillShopName: String = "",
  val lastRefillCost: Double? = null
) {
  val isConfigured: Boolean
    get() = tareWeightGrams > 0.0 && fullGrossWeightGrams > effectiveTareWeightGrams

  val effectiveTareWeightGrams: Double
    get() = tareWeightGrams.coerceAtLeast(0.0) + if (includeRegulatorWeight) regulatorWeightGrams.coerceAtLeast(0.0) else 0.0

  val initialNetCo2Grams: Double
    get() = (fullGrossWeightGrams - effectiveTareWeightGrams).coerceAtLeast(0.0)

  val workingPressureBar: Double
    get() = workingPressureUnit.toBar(workingPressureValue).coerceAtLeast(0.0)

  val atmosphericPressureBar: Double
    get() = (atmosphericPressureKpa / 100.0).coerceAtLeast(0.1)

  val safeLeadTimeDays: Int
    get() = notificationLeadTimeDays.coerceIn(0, 365)

  val dangerThresholdGrams: Double
    get() = initialNetCo2Grams * (dangerThresholdPercent.coerceIn(0.0, 95.0) / 100.0)

  val isPaused: Boolean
    get() = pausedSinceMillis != null

  companion object {
    // 7-bit mask: bit 0 = Mon ... bit 6 = Sun
    const val ALL_DAYS_MASK = 0b1111111 // 127
    const val WEEKDAYS_MASK = 0b0011111 // 31 (Mon–Fri)
    const val MON_TO_SAT_MASK = 0b0111111 // 63 (Mon–Sat)
  }
}

data class LedgerEvent(
  val id: Long = 0L,
  val cylinderId: Long = 1L,
  val timestampMillis: Long,
  val eventType: LedgerEventType,
  val bps: Double,
  val solenoidStartMinutes: Int,
  val solenoidEndMinutes: Int,
  val downtimeHoursDelta: Double = 0.0,
  val scaleWeightGrams: Double? = null,
  val note: String = "",
  val dropCheckerStatus: DropCheckerStatus? = null
)

data class RefillArchiveEntry(
  val id: Long = 0L,
  val cylinderId: Long = 1L,
  val cylinderName: String,
  val startedAtMillis: Long,
  val endedAtMillis: Long,
  val initialNetGrams: Double,
  val consumedGrams: Double,
  val durationDays: Double,
  val avgDailyBurnGrams: Double,
  val calibratedGramsPerBubble: Double? = null,
  val wasCalibrated: Boolean = false,
  val refillCost: Double? = null,
  val note: String = ""
) {
  val costPerMonth: Double?
    get() = if (refillCost != null && refillCost > 0.0 && durationDays >= 1.0) {
      refillCost / (durationDays / 30.4375)
    } else {
      null
    }
}

data class EvaluatedLedgerSegment(
  val event: LedgerEvent,
  val segmentElapsedHours: Double,
  val segmentActiveSolenoidHours: Double,
  val bankedConsumedGramsInSegment: Double,
  val remainingCo2GramsAfter: Double,
  val cumulativeBubblesAfter: Double,
  val activeGramsPerBubbleAfter: Double,
  val isCalibratedAfter: Boolean,
  val didRecalibrateInSegment: Boolean = false,
  val calibrationFeedback: String? = null
)

data class CylinderProjection(
  val config: CylinderConfig,
  val evaluatedSegments: List<EvaluatedLedgerSegment>,
  val currentBps: Double,
  val currentStartMinutes: Int,
  val currentEndMinutes: Int,
  val activeSolenoidHoursPerDay: Double,
  val theoreticalGramsPerBubble: Double,
  val activeGramsPerBubble: Double,
  val isScaleCalibrated: Boolean,
  val hourlyBurnRateGrams: Double,
  val dailyBurnRateGrams: Double,
  val bankedConsumedGrams: Double,
  val liveSegmentConsumedGrams: Double,
  val totalConsumedGrams: Double,
  val remainingCo2Grams: Double,
  val remainingTotalPercent: Double,
  val usableRemainingGrams: Double,
  val cumulativeBubblesFired: Double,
  val daysUntilDangerThreshold: Double,
  val daysUntilRefillWindow: Double,
  val depletionTimestampMillis: Long,
  val alertTimestampMillis: Long,
  val evaluatedAtMillis: Long,
  val hasActiveBurn: Boolean = dailyBurnRateGrams > 1e-6,
  val daysUntilEmpty: Double = daysUntilDangerThreshold,
  val emptyTimestampMillis: Long = depletionTimestampMillis,
  val conservativeDaysUntilDanger: Double = daysUntilDangerThreshold,
  val conservativeDepletionTimestampMillis: Long = depletionTimestampMillis,
  val recommendedCheckInTimestampMillis: Long? = null,
  val possibleLeakDetected: Boolean = false,
  val latestCalibrationFeedback: String? = null,
  val isPaused: Boolean = config.pausedSinceMillis != null,
  val pausedSinceMillis: Long? = config.pausedSinceMillis,
  val pausedMissedSolenoidHours: Double = 0.0,
  val isUsingCarriedCalibration: Boolean = false,
  val alertShiftedForShopDays: Boolean = false
) {
  val isBelowDangerThreshold: Boolean
    get() = remainingCo2Grams <= config.dangerThresholdGrams + 0.01

  val isEmpty: Boolean
    get() = remainingCo2Grams <= 0.5

  val isInRefillWindow: Boolean
    get() = isBelowDangerThreshold || (hasActiveBurn && daysUntilRefillWindow <= 0.0)
}

data class ScaleCheckInPreview(
  val exactConsumedGrams: Double,
  val remainingAfterGrams: Double,
  val resultingGramsPerBubble: Double,
  val willRecalibrateRate: Boolean,
  val feedbackMessage: String,
  val possibleLeakDetected: Boolean,
  val projectedDepletionMillis: Long,
  val projectedEmptyMillis: Long
)

data class WhatIfPreview(
  val newDailyBurnGrams: Double,
  val currentCutoffMillis: Long,
  val newCutoffMillis: Long,
  val cutoffDeltaDays: Double,
  val currentEmptyMillis: Long,
  val newEmptyMillis: Long,
  val emptyDeltaDays: Double
)

data class TapBpsResult(
  val bps: Double,
  val tapCount: Int,
  val isSteady: Boolean
)
