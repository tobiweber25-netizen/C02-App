package com.aquarium.co2tracker.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

object Co2PhysicsEngine {
  // Standard bubble volume in water = 0.05 mL (5.0e-5 Liters)
  const val STANDARD_BUBBLE_VOLUME_ML = 0.05
  const val STANDARD_BUBBLE_VOLUME_LITERS = 0.00005

  // Ideal Gas Law constants for CO2 at 22°C (295.15 K)
  const val DEFAULT_TEMPERATURE_CELSIUS = 22.0
  const val KELVIN_OFFSET = 273.15
  const val CO2_MOLAR_MASS_G_PER_MOL = 44.01
  const val IDEAL_GAS_CONSTANT_L_BAR_K_MOL = 0.083144626

  // Default reference pressures: 2.5 Bar working + 0.83 Bar atmospheric (Johannesburg ~1,700m)
  const val DEFAULT_WORKING_PRESSURE_BAR = 2.5
  const val DEFAULT_JOHANNESBURG_ATM_BAR = 0.83

  // Downstream bubble counter sits between the needle valve and diffuser.
  // Typical ceramic diffusers operate around ~65% of regulator working pressure;
  // full working pressure (1.0) represents the conservative upper-bound density.
  const val TYPICAL_DIFFUSER_BACKPRESSURE_RATIO = 0.65
  const val CONSERVATIVE_FULL_PRESSURE_RATIO = 1.0

  // Safety thresholds to prevent kitchen-scale quantization noise from corrupting calibration
  const val MIN_CALIBRATION_GRAMS_FROM_SETUP = 10.0
  const val MIN_CALIBRATION_ACTIVE_HOURS_SETUP = 4.0
  const val MIN_CALIBRATION_BUBBLES_SETUP = 3_600.0

  // Higher thresholds when calibrating from an intermediate scale check-in anchor
  const val MIN_CALIBRATION_GRAMS_INTERVAL = 25.0
  const val MIN_CALIBRATION_ACTIVE_HOURS_INTERVAL = 12.0
  const val MIN_CALIBRATION_BUBBLES_INTERVAL = 10_000.0

  const val MIN_CALIBRATED_GRAMS_PER_BUBBLE = 1e-5
  const val MAX_CALIBRATED_GRAMS_PER_BUBBLE = 0.01

  // If scale-calibrated loss exceeds 1.6x full-working-pressure Ideal Gas Law density,
  // gas is likely escaping outside the bubble counter (e.g., slow washer/fitting leak).
  const val LEAK_DETECTION_MULTIPLIER = 1.6

  const val SECONDS_PER_HOUR = 3600.0
  const val HOURS_PER_DAY = 24.0
  const val MILLIS_PER_HOUR = 3_600_000.0
  const val MILLIS_PER_DAY = 86_400_000.0
  const val MAX_PROJECTION_DAYS = 3650.0
  private const val MAX_CALENDAR_ITERATION_DAYS = 4000L

  private data class WeightAnchor(
    val timestampMillis: Long,
    val remainingGrams: Double,
    val cumulativeBubbles: Double,
    val cumulativeActiveHours: Double
  )

  /**
   * Locale-safe decimal parser accepting both '.' and ',' separators.
   */
  fun parseDecimalInput(raw: String): Double? {
    val normalized = raw.trim().replace(',', '.')
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
  }

  /**
   * Barometric formula converting elevation (meters) to atmospheric pressure (kPa).
   * At 1,700m (Johannesburg), returns ~83.0 kPa (0.83 Bar).
   * At 0m (Sea Level), returns ~101.3 kPa (1.013 Bar).
   */
  fun calculateAtmosphericPressureKpa(elevationMeters: Double): Double {
    if (!elevationMeters.isFinite()) return 101.3
    val clampedElevation = elevationMeters.coerceIn(-500.0, 6000.0)
    if (abs(clampedElevation - 1700.0) < 0.5) {
      return 83.0
    }
    val kpa = 101.325 * (1.0 - 2.25577e-5 * clampedElevation).pow(5.25588)
    return (Math.round(kpa * 10.0) / 10.0).coerceAtLeast(30.0)
  }

  /**
   * Calculates active solenoid hours per day from start and end minutes (0..1439).
   * Example: 10:30 (630) to 18:00 (1080) = 450 minutes = 7.5 hours/day.
   */
  fun calculateActiveSolenoidHoursPerDay(startMinutes: Int, endMinutes: Int): Double {
    val normalizedStart = normalizeMinuteOfDay(startMinutes)
    val normalizedEnd = normalizeMinuteOfDay(endMinutes)
    val activeMinutes = when {
      normalizedEnd > normalizedStart -> normalizedEnd - normalizedStart
      normalizedEnd < normalizedStart -> (1440 - normalizedStart) + normalizedEnd
      else -> 1440 // 24h continuous if start == end
    }
    return activeMinutes / 60.0
  }

  /**
   * Integrates exact active solenoid hours between [startMillis] and [endMillis]
   * respecting the real local-clock ON/OFF window, overnight rollovers, and DST transitions.
   */
  fun calculateActiveSolenoidHoursBetween(
    startMillis: Long,
    endMillis: Long,
    startMinutes: Int,
    endMinutes: Int,
    zoneId: ZoneId = ZoneId.systemDefault()
  ): Double {
    if (endMillis <= startMillis) return 0.0

    val normStart = normalizeMinuteOfDay(startMinutes)
    val normEnd = normalizeMinuteOfDay(endMinutes)
    if (normStart == normEnd) {
      return (endMillis - startMillis) / MILLIS_PER_HOUR
    }

    val startDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
    val endDate = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalDate()
    val spanDays = ChronoUnit.DAYS.between(startDate, endDate)

    if (spanDays > MAX_CALENDAR_ITERATION_DAYS) {
      val elapsedDays = (endMillis - startMillis) / MILLIS_PER_DAY
      return elapsedDays * calculateActiveSolenoidHoursPerDay(normStart, normEnd)
    }

    val startTime = LocalTime.of(normStart / 60, normStart % 60)
    val endTime = LocalTime.of(normEnd / 60, normEnd % 60)
    val isOvernight = normEnd < normStart

    var totalOverlapMillis = 0L
    var date = startDate.minusDays(1)
    while (!date.isAfter(endDate)) {
      val windowStart = ZonedDateTime.of(date, startTime, zoneId).toInstant().toEpochMilli()
      val windowEndDate = if (isOvernight) date.plusDays(1) else date
      val windowEnd = ZonedDateTime.of(windowEndDate, endTime, zoneId).toInstant().toEpochMilli()

      if (windowEnd > windowStart) {
        val overlapStart = max(startMillis, windowStart)
        val overlapEnd = min(endMillis, windowEnd)
        if (overlapEnd > overlapStart) {
          totalOverlapMillis += (overlapEnd - overlapStart)
        }
      }
      date = date.plusDays(1)
    }

    return totalOverlapMillis / MILLIS_PER_HOUR
  }

  /**
   * Projects forward from [fromMillis] along real local solenoid active windows until
   * [requiredActiveHours] of active CO2 injection have elapsed.
   */
  fun projectTimestampAfterActiveHours(
    fromMillis: Long,
    requiredActiveHours: Double,
    startMinutes: Int,
    endMinutes: Int,
    zoneId: ZoneId = ZoneId.systemDefault()
  ): Long {
    if (!requiredActiveHours.isFinite() || requiredActiveHours <= 0.0) return fromMillis

    val maxTargetMillis = fromMillis + (MAX_PROJECTION_DAYS * MILLIS_PER_DAY).roundToLong()
    val normStart = normalizeMinuteOfDay(startMinutes)
    val normEnd = normalizeMinuteOfDay(endMinutes)
    if (normStart == normEnd) {
      val addedMillis = (requiredActiveHours * MILLIS_PER_HOUR).roundToLong()
      return min(fromMillis + addedMillis, maxTargetMillis)
    }

    val hoursPerDay = calculateActiveSolenoidHoursPerDay(normStart, normEnd)
    if (hoursPerDay <= 0.0 || requiredActiveHours / hoursPerDay >= MAX_PROJECTION_DAYS) {
      return maxTargetMillis
    }

    var remainingMillis = (requiredActiveHours * MILLIS_PER_HOUR).roundToLong().coerceAtLeast(1L)
    val startTime = LocalTime.of(normStart / 60, normStart % 60)
    val endTime = LocalTime.of(normEnd / 60, normEnd % 60)
    val isOvernight = normEnd < normStart

    var date = Instant.ofEpochMilli(fromMillis).atZone(zoneId).toLocalDate().minusDays(1)
    var daysChecked = 0L
    while (daysChecked <= MAX_CALENDAR_ITERATION_DAYS) {
      val windowStart = ZonedDateTime.of(date, startTime, zoneId).toInstant().toEpochMilli()
      val windowEndDate = if (isOvernight) date.plusDays(1) else date
      val windowEnd = ZonedDateTime.of(windowEndDate, endTime, zoneId).toInstant().toEpochMilli()

      if (windowEnd > windowStart) {
        val segStart = max(fromMillis, windowStart)
        if (windowEnd > segStart) {
          val segDuration = windowEnd - segStart
          if (remainingMillis <= segDuration) {
            return min(segStart + remainingMillis, maxTargetMillis)
          }
          remainingMillis -= segDuration
        }
      }

      date = date.plusDays(1)
      daysChecked++
    }

    return maxTargetMillis
  }

  /**
   * Calculates theoretical CO2 mass (grams) per bubble using the Ideal Gas Law:
   * m = (P_abs * V * M) / (R * T)
   *
   * Because the bubble counter sits downstream of the needle valve and upstream of the diffuser,
   * P_abs = atmosphericPressureBar + workingPressureBar * backpressureRatio.
   * - backpressureRatio = 0.0 -> atmospheric / zero-backpressure reactor (~0.000091g at sea level)
   * - backpressureRatio = 0.65 -> typical ceramic diffuser backpressure (~0.00022g at 2.5 Bar + 0.83 Bar)
   * - backpressureRatio = 1.0 -> full working pressure upper bound (~0.00030g at 2.5 Bar + 0.83 Bar)
   */
  fun calculateTheoreticalGramsPerBubble(
    workingPressureBar: Double,
    atmosphericPressureBar: Double,
    fluidType: FluidType,
    temperatureCelsius: Double = DEFAULT_TEMPERATURE_CELSIUS,
    backpressureRatio: Double = TYPICAL_DIFFUSER_BACKPRESSURE_RATIO
  ): Double {
    val safeWorking = if (!workingPressureBar.isFinite()) DEFAULT_WORKING_PRESSURE_BAR else workingPressureBar.coerceAtLeast(0.0)
    val safeAtm = if (!atmosphericPressureBar.isFinite()) DEFAULT_JOHANNESBURG_ATM_BAR else atmosphericPressureBar.coerceAtLeast(0.1)
    val safeRatio = if (!backpressureRatio.isFinite()) TYPICAL_DIFFUSER_BACKPRESSURE_RATIO else backpressureRatio.coerceIn(0.0, 1.0)

    val counterAbsolutePressureBar = (safeAtm + safeWorking * safeRatio).coerceAtLeast(0.1)
    val bubbleVolumeLiters = STANDARD_BUBBLE_VOLUME_LITERS * fluidType.volumeMultiplier

    val safeTempCelsius = if (!temperatureCelsius.isFinite()) DEFAULT_TEMPERATURE_CELSIUS else temperatureCelsius.coerceIn(-20.0, 60.0)
    val tempKelvin = (KELVIN_OFFSET + safeTempCelsius).coerceAtLeast(1.0)

    val massGrams = (counterAbsolutePressureBar * bubbleVolumeLiters * CO2_MOLAR_MASS_G_PER_MOL) /
      (IDEAL_GAS_CONSTANT_L_BAR_K_MOL * tempKelvin)

    return if (massGrams.isNaN() || !massGrams.isFinite() || massGrams <= 0.0) {
      0.00022
    } else {
      massGrams
    }
  }

  /**
   * Hourly Burn Rate (g/hr) = BPS * 3600 * Grams_Per_Bubble_Coefficient
   */
  fun calculateHourlyBurnRateGrams(bps: Double, gramsPerBubbleCoefficient: Double): Double {
    val safeBps = if (!bps.isFinite()) 0.0 else bps.coerceAtLeast(0.0)
    val safeCoeff = if (!gramsPerBubbleCoefficient.isFinite()) 0.0 else gramsPerBubbleCoefficient.coerceAtLeast(0.0)
    return safeBps * SECONDS_PER_HOUR * safeCoeff
  }

  /**
   * Daily Burn Rate (g/day) = Hourly Burn Rate * Active_Solenoid_Hours_Per_Day
   */
  fun calculateDailyBurnRateGrams(
    bps: Double,
    gramsPerBubbleCoefficient: Double,
    activeSolenoidHoursPerDay: Double
  ): Double {
    val hourlyBurn = calculateHourlyBurnRateGrams(bps, gramsPerBubbleCoefficient)
    val safeHours = if (!activeSolenoidHoursPerDay.isFinite()) 0.0 else activeSolenoidHoursPerDay.coerceIn(0.0, HOURS_PER_DAY)
    return hourlyBurn * safeHours
  }

  /**
   * Exact single-source-of-truth preview for a prospective scale check-in.
   */
  fun previewScaleCheckIn(
    projection: CylinderProjection,
    measuredScaleWeightGrams: Double,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
  ): ScaleCheckInPreview {
    val existingEvents = projection.evaluatedSegments.map { it.event }
    val syntheticCheckIn = LedgerEvent(
      id = Long.MAX_VALUE,
      cylinderId = projection.config.id,
      timestampMillis = max(nowMillis, existingEvents.lastOrNull()?.timestampMillis ?: nowMillis),
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = projection.currentBps,
      solenoidStartMinutes = projection.currentStartMinutes,
      solenoidEndMinutes = projection.currentEndMinutes,
      scaleWeightGrams = measuredScaleWeightGrams
    )
    val simulated = evaluateLedger(
      config = projection.config,
      rawEvents = existingEvents + syntheticCheckIn,
      nowMillis = syntheticCheckIn.timestampMillis,
      zoneId = zoneId
    )
    val lastSeg = simulated.evaluatedSegments.last()
    return ScaleCheckInPreview(
      exactConsumedGrams = simulated.totalConsumedGrams,
      remainingAfterGrams = simulated.remainingCo2Grams,
      resultingGramsPerBubble = simulated.activeGramsPerBubble,
      willRecalibrateRate = lastSeg.didRecalibrateInSegment,
      feedbackMessage = lastSeg.calibrationFeedback ?: "Scale weight applied",
      possibleLeakDetected = simulated.possibleLeakDetected,
      projectedDepletionMillis = simulated.depletionTimestampMillis,
      projectedEmptyMillis = simulated.emptyTimestampMillis
    )
  }

  /**
   * Evaluates the immutable Event Ledger chronologically from cylinder setup to [nowMillis].
   * Every event closes the previous time segment using exact solenoid schedule integration,
   * handles cumulative downtime/runtime overrides cleanly, applies noise-guarded Two-Point /
   * Multi-Point scale calibration, detects abnormal gas loss (possible leaks), and projects
   * safety cutoff, empty (0g), conservative cutoff, check-in, and alert timestamps.
   */
  fun evaluateLedger(
    config: CylinderConfig,
    rawEvents: List<LedgerEvent>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
  ): CylinderProjection {
    val sortedEvents = rawEvents.sortedWith(
      compareBy<LedgerEvent> { it.timestampMillis }.thenBy { it.id }
    )

    val theoreticalCoeff = calculateTheoreticalGramsPerBubble(
      workingPressureBar = config.workingPressureBar,
      atmosphericPressureBar = config.atmosphericPressureBar,
      fluidType = config.fluidType
    )
    val maxPressureCoeff = calculateTheoreticalGramsPerBubble(
      workingPressureBar = config.workingPressureBar,
      atmosphericPressureBar = config.atmosphericPressureBar,
      fluidType = config.fluidType,
      backpressureRatio = CONSERVATIVE_FULL_PRESSURE_RATIO
    )

    val initialWithSetup = if (sortedEvents.isEmpty() || sortedEvents.first().eventType != LedgerEventType.SETUP) {
      listOf(
        LedgerEvent(
          id = 0L,
          cylinderId = config.id,
          timestampMillis = config.setupTimestampMillis,
          eventType = LedgerEventType.SETUP,
          bps = config.startingBps,
          solenoidStartMinutes = config.solenoidStartMinutes,
          solenoidEndMinutes = config.solenoidEndMinutes,
          note = "Initial cylinder fill (${config.initialNetCo2Grams.toInt()}g net CO2)"
        )
      ) + sortedEvents
    } else {
      sortedEvents
    }

    // Ignore events strictly in the future relative to nowMillis (protects against clock rollback)
    val initialEvents = initialWithSetup.filterIndexed { index, event ->
      index == 0 || event.timestampMillis <= nowMillis
    }

    val setupEvent = initialEvents.first()
    var activeCoeff = theoreticalCoeff
    var isCalibrated = false
    var activeBps = setupEvent.bps.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    var activeStartMin = setupEvent.solenoidStartMinutes
    var activeEndMin = setupEvent.solenoidEndMinutes
    var remainingGrams = config.initialNetCo2Grams
    var maxAllowableRemainingGrams = config.initialNetCo2Grams
    var cumulativeBubbles = 0.0
    var cumulativeActiveHours = 0.0
    var previousTimestampMillis = setupEvent.timestampMillis
    var latestCalibrationFeedback: String? = null

    val weightAnchors = mutableListOf(
      WeightAnchor(
        timestampMillis = setupEvent.timestampMillis,
        remainingGrams = config.initialNetCo2Grams,
        cumulativeBubbles = 0.0,
        cumulativeActiveHours = 0.0
      )
    )

    val evaluatedSegments = mutableListOf<EvaluatedLedgerSegment>()

    for ((index, event) in initialEvents.withIndex()) {
      if (index == 0) {
        evaluatedSegments.add(
          EvaluatedLedgerSegment(
            event = event,
            segmentElapsedHours = 0.0,
            segmentActiveSolenoidHours = 0.0,
            bankedConsumedGramsInSegment = 0.0,
            remainingCo2GramsAfter = remainingGrams,
            cumulativeBubblesAfter = 0.0,
            activeGramsPerBubbleAfter = activeCoeff,
            isCalibratedAfter = isCalibrated
          )
        )
        continue
      }

      // 1. Close previous segment [previousTimestampMillis, event.timestampMillis] using real solenoid schedule
      val deltaMillis = (event.timestampMillis - previousTimestampMillis).coerceAtLeast(0L)
      val elapsedHours = deltaMillis / MILLIS_PER_HOUR

      val baseActiveSolenoidHours = calculateActiveSolenoidHoursBetween(
        startMillis = previousTimestampMillis,
        endMillis = event.timestampMillis,
        startMinutes = activeStartMin,
        endMinutes = activeEndMin,
        zoneId = zoneId
      )

      val overrideHoursDelta = if (event.eventType == LedgerEventType.DOWNTIME_OVERRIDE) {
        event.downtimeHoursDelta.takeIf { it.isFinite() } ?: 0.0
      } else {
        0.0
      }

      // Apply override against cumulative timeline so negative overrides logged after recent events are never lost
      val rawSegmentActiveHours = baseActiveSolenoidHours + overrideHoursDelta
      val rawSegmentBubbles = rawSegmentActiveHours * SECONDS_PER_HOUR * activeBps
      val newCumulativeBubbles = (cumulativeBubbles + rawSegmentBubbles).coerceAtLeast(0.0)
      val actualBubblesDelta = newCumulativeBubbles - cumulativeBubbles
      cumulativeBubbles = newCumulativeBubbles
      cumulativeActiveHours = (cumulativeActiveHours + rawSegmentActiveHours).coerceAtLeast(0.0)

      val previousRemaining = remainingGrams
      val rawConsumedDelta = actualBubblesDelta * activeCoeff
      remainingGrams = (remainingGrams - rawConsumedDelta).coerceIn(0.0, maxAllowableRemainingGrams)
      var segmentConsumedGrams = (previousRemaining - remainingGrams).coerceAtLeast(0.0)
      var didRecalibrate = false
      var segmentFeedback: String? = null

      // 2. Apply event-specific state transitions
      when (event.eventType) {
        LedgerEventType.SETUP -> {
          activeBps = event.bps.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
          activeStartMin = event.solenoidStartMinutes
          activeEndMin = event.solenoidEndMinutes
          remainingGrams = config.initialNetCo2Grams
          maxAllowableRemainingGrams = config.initialNetCo2Grams
          cumulativeBubbles = 0.0
          cumulativeActiveHours = 0.0
          activeCoeff = theoreticalCoeff
          isCalibrated = false
          latestCalibrationFeedback = null
          weightAnchors.clear()
          weightAnchors.add(
            WeightAnchor(
              timestampMillis = event.timestampMillis,
              remainingGrams = config.initialNetCo2Grams,
              cumulativeBubbles = 0.0,
              cumulativeActiveHours = 0.0
            )
          )
        }
        LedgerEventType.BPS_CHANGE -> {
          activeBps = event.bps.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: activeBps
        }
        LedgerEventType.SCHEDULE_CHANGE -> {
          activeStartMin = event.solenoidStartMinutes
          activeEndMin = event.solenoidEndMinutes
        }
        LedgerEventType.DOWNTIME_OVERRIDE -> {
          // Override hours delta was already applied above
        }
        LedgerEventType.SCALE_CALIBRATION -> {
          val measuredScaleWeight = event.scaleWeightGrams
          if (measuredScaleWeight != null && measuredScaleWeight.isFinite()) {
            val measuredNetRemaining = (measuredScaleWeight - config.effectiveTareWeightGrams)
              .coerceIn(0.0, config.initialNetCo2Grams)
            val previousRemainingBeforeSegment = evaluatedSegments.last().remainingCo2GramsAfter

            segmentConsumedGrams = (previousRemainingBeforeSegment - measuredNetRemaining).coerceAtLeast(0.0)
            remainingGrams = measuredNetRemaining
            maxAllowableRemainingGrams = measuredNetRemaining

            if (measuredNetRemaining <= 0.0) {
              // Never calibrate burn rate from an empty reading because exact dry-out time is unknown
              segmentFeedback = "Bottle recorded empty (0g remaining); burn rate kept unchanged"
            } else {
              // Select the most recent weight anchor with sufficient gas drop & active runtime
              var selectedAnchor: WeightAnchor? = null
              for (i in weightAnchors.indices.reversed()) {
                val anchor = weightAnchors[i]
                val deltaGrams = anchor.remainingGrams - measuredNetRemaining
                val deltaBubbles = cumulativeBubbles - anchor.cumulativeBubbles
                val deltaActiveHours = cumulativeActiveHours - anchor.cumulativeActiveHours
                val isSetupAnchor = (i == 0)

                val minGrams = if (isSetupAnchor) MIN_CALIBRATION_GRAMS_FROM_SETUP else MIN_CALIBRATION_GRAMS_INTERVAL
                val minHours = if (isSetupAnchor) MIN_CALIBRATION_ACTIVE_HOURS_SETUP else MIN_CALIBRATION_ACTIVE_HOURS_INTERVAL
                val minBubbles = if (isSetupAnchor) MIN_CALIBRATION_BUBBLES_SETUP else MIN_CALIBRATION_BUBBLES_INTERVAL

                if (deltaGrams >= minGrams && deltaActiveHours >= minHours && deltaBubbles >= minBubbles) {
                  selectedAnchor = anchor
                  break
                }
              }

              if (selectedAnchor != null) {
                val deltaGrams = selectedAnchor.remainingGrams - measuredNetRemaining
                val deltaBubbles = cumulativeBubbles - selectedAnchor.cumulativeBubbles
                val computedCoeff = deltaGrams / deltaBubbles
                if (computedCoeff.isFinite() && computedCoeff > 0.0) {
                  activeCoeff = computedCoeff.coerceIn(MIN_CALIBRATED_GRAMS_PER_BUBBLE, MAX_CALIBRATED_GRAMS_PER_BUBBLE)
                  isCalibrated = true
                  didRecalibrate = true
                  segmentFeedback = "Burn rate locked at ${String.format(Locale.US, "%.5f", activeCoeff)} g/bubble"
                }
              } else {
                val totalLossFromSetup = config.initialNetCo2Grams - measuredNetRemaining
                segmentFeedback = if (totalLossFromSetup < MIN_CALIBRATION_GRAMS_FROM_SETUP) {
                  "Remaining weight updated. Need at least ${MIN_CALIBRATION_GRAMS_FROM_SETUP.toInt()}g used to calibrate burn rate."
                } else {
                  "Remaining weight updated. Need at least ${MIN_CALIBRATION_ACTIVE_HOURS_SETUP.toInt()} active solenoid hours to calibrate burn rate."
                }
              }

              weightAnchors.add(
                WeightAnchor(
                  timestampMillis = event.timestampMillis,
                  remainingGrams = measuredNetRemaining,
                  cumulativeBubbles = cumulativeBubbles,
                  cumulativeActiveHours = cumulativeActiveHours
                )
              )
            }
            latestCalibrationFeedback = segmentFeedback
          }
        }
      }

      previousTimestampMillis = event.timestampMillis
      evaluatedSegments.add(
        EvaluatedLedgerSegment(
          event = event,
          segmentElapsedHours = elapsedHours,
          segmentActiveSolenoidHours = rawSegmentActiveHours.coerceAtLeast(0.0),
          bankedConsumedGramsInSegment = segmentConsumedGrams,
          remainingCo2GramsAfter = remainingGrams,
          cumulativeBubblesAfter = cumulativeBubbles,
          activeGramsPerBubbleAfter = activeCoeff,
          isCalibratedAfter = isCalibrated,
          didRecalibrateInSegment = didRecalibrate,
          calibrationFeedback = segmentFeedback
        )
      )
    }

    // 3. Project from latest banked ledger event forward to [nowMillis] using real solenoid schedule
    val lastSegment = evaluatedSegments.last()
    val currentActiveHoursPerDay = calculateActiveSolenoidHoursPerDay(activeStartMin, activeEndMin)
    val hourlyBurnRate = calculateHourlyBurnRateGrams(activeBps, activeCoeff)
    val dailyBurnRate = calculateDailyBurnRateGrams(activeBps, activeCoeff, currentActiveHoursPerDay)
    val hasActiveBurn = hourlyBurnRate > 1e-6 && currentActiveHoursPerDay > 0.0

    val liveActiveHours = calculateActiveSolenoidHoursBetween(
      startMillis = lastSegment.event.timestampMillis,
      endMillis = nowMillis,
      startMinutes = activeStartMin,
      endMinutes = activeEndMin,
      zoneId = zoneId
    )
    val liveBubbles = (liveActiveHours * SECONDS_PER_HOUR * activeBps).coerceAtLeast(0.0)
    val liveConsumedGrams = (liveBubbles * activeCoeff).coerceAtLeast(0.0)

    val currentRemainingGrams = (lastSegment.remainingCo2GramsAfter - liveConsumedGrams)
      .coerceIn(0.0, config.initialNetCo2Grams)
    val totalConsumedGrams = (config.initialNetCo2Grams - currentRemainingGrams)
      .coerceIn(0.0, config.initialNetCo2Grams)
    val bankedConsumedGrams = (config.initialNetCo2Grams - lastSegment.remainingCo2GramsAfter)
      .coerceIn(0.0, config.initialNetCo2Grams)

    val remainingPercent = if (config.initialNetCo2Grams > 0.0) {
      ((currentRemainingGrams / config.initialNetCo2Grams) * 100.0).coerceIn(0.0, 100.0)
    } else {
      0.0
    }

    val usableRemainingGrams = (currentRemainingGrams - config.dangerThresholdGrams).coerceAtLeast(0.0)
    val isBelowDanger = currentRemainingGrams <= config.dangerThresholdGrams + 0.01

    // 4. Exact projection to Safety Cutoff (dangerThresholdGrams)
    val depletionTimestampMillis: Long
    val daysUntilDanger: Double
    if (isBelowDanger || usableRemainingGrams <= 0.0) {
      depletionTimestampMillis = nowMillis
      daysUntilDanger = 0.0
    } else if (!hasActiveBurn) {
      daysUntilDanger = MAX_PROJECTION_DAYS
      depletionTimestampMillis = nowMillis + (MAX_PROJECTION_DAYS * MILLIS_PER_DAY).roundToLong()
    } else {
      val requiredActiveHoursToDanger = usableRemainingGrams / hourlyBurnRate
      depletionTimestampMillis = projectTimestampAfterActiveHours(
        fromMillis = nowMillis,
        requiredActiveHours = requiredActiveHoursToDanger,
        startMinutes = activeStartMin,
        endMinutes = activeEndMin,
        zoneId = zoneId
      )
      daysUntilDanger = ((depletionTimestampMillis - nowMillis) / MILLIS_PER_DAY)
        .coerceIn(0.0, MAX_PROJECTION_DAYS)
    }

    // 5. Exact projection to Completely Empty (0g)
    val emptyTimestampMillis: Long
    val daysUntilEmpty: Double
    if (currentRemainingGrams <= 0.5) {
      emptyTimestampMillis = nowMillis
      daysUntilEmpty = 0.0
    } else if (!hasActiveBurn) {
      daysUntilEmpty = MAX_PROJECTION_DAYS
      emptyTimestampMillis = nowMillis + (MAX_PROJECTION_DAYS * MILLIS_PER_DAY).roundToLong()
    } else {
      val requiredActiveHoursToEmpty = currentRemainingGrams / hourlyBurnRate
      emptyTimestampMillis = projectTimestampAfterActiveHours(
        fromMillis = nowMillis,
        requiredActiveHours = requiredActiveHoursToEmpty,
        startMinutes = activeStartMin,
        endMinutes = activeEndMin,
        zoneId = zoneId
      )
      daysUntilEmpty = ((emptyTimestampMillis - nowMillis) / MILLIS_PER_DAY)
        .coerceIn(0.0, MAX_PROJECTION_DAYS)
    }

    // 6. Conservative (earliest) safety cutoff when uncalibrated (assumes full working pressure inside counter).
    // IMPORTANT: To avoid time-drift (Zeno effect), integrate conservative burn from the latest
    // physical weight anchor (setup or last scale reading) using full-pressure mass per bubble.
    val conservativeDepletionTimestampMillis: Long
    val conservativeDaysUntilDanger: Double
    if (isCalibrated || isBelowDanger || !hasActiveBurn) {
      conservativeDepletionTimestampMillis = depletionTimestampMillis
      conservativeDaysUntilDanger = daysUntilDanger
    } else {
      val conservativeHourlyBurn = calculateHourlyBurnRateGrams(activeBps, maxPressureCoeff)
      val latestAnchor = weightAnchors.last()
      val totalBubblesNow = lastSegment.cumulativeBubblesAfter + liveBubbles
      val bubblesSinceAnchor = (totalBubblesNow - latestAnchor.cumulativeBubbles).coerceAtLeast(0.0)
      val conservativeRemainingGrams = (latestAnchor.remainingGrams - bubblesSinceAnchor * maxPressureCoeff)
        .coerceIn(0.0, config.initialNetCo2Grams)
      val conservativeUsableGrams = (conservativeRemainingGrams - config.dangerThresholdGrams).coerceAtLeast(0.0)

      if (conservativeUsableGrams <= 0.0) {
        conservativeDepletionTimestampMillis = nowMillis
        conservativeDaysUntilDanger = 0.0
      } else if (conservativeHourlyBurn > 1e-6) {
        val requiredConservativeHours = conservativeUsableGrams / conservativeHourlyBurn
        conservativeDepletionTimestampMillis = min(
          depletionTimestampMillis,
          projectTimestampAfterActiveHours(
            fromMillis = nowMillis,
            requiredActiveHours = requiredConservativeHours,
            startMinutes = activeStartMin,
            endMinutes = activeEndMin,
            zoneId = zoneId
          )
        )
        conservativeDaysUntilDanger = ((conservativeDepletionTimestampMillis - nowMillis) / MILLIS_PER_DAY)
          .coerceIn(0.0, MAX_PROJECTION_DAYS)
      } else {
        conservativeDepletionTimestampMillis = depletionTimestampMillis
        conservativeDaysUntilDanger = daysUntilDanger
      }
    }

    // 7. Advance Refill Alert timestamp:
    // Use conservative cutoff when uncalibrated so the user is warned before the earliest possible dry-out.
    val safeLeadDays = config.safeLeadTimeDays
    val leadTimeMillis = safeLeadDays * MILLIS_PER_DAY.toLong()
    val effectiveCutoffForAlert = if (isCalibrated) depletionTimestampMillis else conservativeDepletionTimestampMillis
    val rawAlertMillis = (effectiveCutoffForAlert - leadTimeMillis).coerceAtLeast(config.setupTimestampMillis)
    val alertTimestampMillis = if (safeLeadDays > 0 && rawAlertMillis > nowMillis && hasActiveBurn) {
      adjustAdvanceAlertToWakingHour(rawAlertMillis, effectiveCutoffForAlert, nowMillis, zoneId)
    } else {
      rawAlertMillis
    }

    val daysUntilRefillWindow = if (!hasActiveBurn && !isBelowDanger) {
      MAX_PROJECTION_DAYS
    } else {
      (alertTimestampMillis - nowMillis) / MILLIS_PER_DAY
    }

    // 8. Recommended First Scale Check-In (when uncalibrated)
    val recommendedCheckInTimestampMillis: Long? = if (!isCalibrated && hasActiveBurn && !isBelowDanger && currentRemainingGrams > 0.0) {
      val targetLossGrams = min(80.0, max(25.0, dailyBurnRate * 10.0))
        .coerceAtMost(config.initialNetCo2Grams * 0.15)
        .coerceAtLeast(MIN_CALIBRATION_GRAMS_FROM_SETUP)
      val remainingGramsForCheckIn = (targetLossGrams - totalConsumedGrams).coerceAtLeast(0.0)
      val totalActiveHoursSoFar = cumulativeActiveHours + liveActiveHours
      val remainingHoursForCheckIn = max(
        if (hourlyBurnRate > 1e-6) remainingGramsForCheckIn / hourlyBurnRate else 0.0,
        (MIN_CALIBRATION_ACTIVE_HOURS_SETUP - totalActiveHoursSoFar).coerceAtLeast(0.0)
      )
      val rawCheckIn = if (remainingHoursForCheckIn <= 0.0) {
        nowMillis
      } else {
        projectTimestampAfterActiveHours(
          fromMillis = nowMillis,
          requiredActiveHours = remainingHoursForCheckIn,
          startMinutes = activeStartMin,
          endMinutes = activeEndMin,
          zoneId = zoneId
        )
      }
      val boundedCheckIn = min(rawCheckIn, alertTimestampMillis)
      if (boundedCheckIn > nowMillis) {
        adjustAdvanceAlertToWakingHour(boundedCheckIn, alertTimestampMillis, nowMillis, zoneId)
      } else {
        boundedCheckIn
      }
    } else {
      null
    }

    // 9. Leak detection: calibrated mass/bubble significantly exceeds physical full-pressure upper bound
    val possibleLeakDetected = isCalibrated && activeCoeff > (maxPressureCoeff * LEAK_DETECTION_MULTIPLIER)

    return CylinderProjection(
      config = config,
      evaluatedSegments = evaluatedSegments,
      currentBps = activeBps,
      currentStartMinutes = activeStartMin,
      currentEndMinutes = activeEndMin,
      activeSolenoidHoursPerDay = currentActiveHoursPerDay,
      theoreticalGramsPerBubble = theoreticalCoeff,
      activeGramsPerBubble = activeCoeff,
      isScaleCalibrated = isCalibrated,
      hourlyBurnRateGrams = hourlyBurnRate,
      dailyBurnRateGrams = dailyBurnRate,
      bankedConsumedGrams = bankedConsumedGrams,
      liveSegmentConsumedGrams = liveConsumedGrams,
      totalConsumedGrams = totalConsumedGrams,
      remainingCo2Grams = currentRemainingGrams,
      remainingTotalPercent = remainingPercent,
      usableRemainingGrams = usableRemainingGrams,
      cumulativeBubblesFired = lastSegment.cumulativeBubblesAfter + liveBubbles,
      daysUntilDangerThreshold = daysUntilDanger,
      daysUntilRefillWindow = daysUntilRefillWindow,
      depletionTimestampMillis = depletionTimestampMillis,
      alertTimestampMillis = alertTimestampMillis,
      evaluatedAtMillis = nowMillis,
      hasActiveBurn = hasActiveBurn,
      daysUntilEmpty = daysUntilEmpty,
      emptyTimestampMillis = emptyTimestampMillis,
      conservativeDaysUntilDanger = conservativeDaysUntilDanger,
      conservativeDepletionTimestampMillis = conservativeDepletionTimestampMillis,
      recommendedCheckInTimestampMillis = recommendedCheckInTimestampMillis,
      possibleLeakDetected = possibleLeakDetected,
      latestCalibrationFeedback = latestCalibrationFeedback
    )
  }

  private fun normalizeMinuteOfDay(minutes: Int): Int = ((minutes % 1440) + 1440) % 1440

  /**
   * Keeps non-urgent advance notifications out of overnight quiet hours (21:00–07:59)
   * without ever delaying past the deadline.
   */
  private fun adjustAdvanceAlertToWakingHour(
    candidateMillis: Long,
    deadlineMillis: Long,
    nowMillis: Long,
    zoneId: ZoneId
  ): Long {
    val zdt = Instant.ofEpochMilli(candidateMillis).atZone(zoneId)
    val hour = zdt.hour
    val adjusted = when {
      hour < 8 -> {
        val morning = zdt.toLocalDate().atTime(8, 30).atZone(zoneId).toInstant().toEpochMilli()
        if (morning <= deadlineMillis - (12L * MILLIS_PER_HOUR.toLong())) morning else candidateMillis
      }
      hour >= 21 -> {
        val evening = zdt.toLocalDate().atTime(19, 0).atZone(zoneId).toInstant().toEpochMilli()
        if (evening > nowMillis) evening else candidateMillis
      }
      else -> candidateMillis
    }
    return adjusted.coerceAtMost(deadlineMillis)
  }
}
