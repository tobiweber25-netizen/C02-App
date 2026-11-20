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
  val setupTimestampMillis: Long = System.currentTimeMillis()
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
  val note: String = ""
)

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
  val latestCalibrationFeedback: String? = null
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
