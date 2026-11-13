package com.aquarium.co2tracker.domain

import kotlin.math.pow

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
  const val DEFAULT_TOTAL_ABSOLUTE_BAR = DEFAULT_WORKING_PRESSURE_BAR + DEFAULT_JOHANNESBURG_ATM_BAR

  // Baseline mass per bubble at 1 BPS in water at reference conditions (~0.000091g per bubble)
  const val DEFAULT_BASELINE_GRAMS_PER_BUBBLE = 0.000091

  const val SECONDS_PER_HOUR = 3600.0
  const val HOURS_PER_DAY = 24.0
  const val MILLIS_PER_HOUR = 3_600_000.0
  const val MILLIS_PER_DAY = 86_400_000.0

  /**
   * Barometric formula converting elevation (meters) to atmospheric pressure (kPa).
   * At 1,700m (Johannesburg), returns ~83.0 kPa (0.83 Bar).
   * At 0m (Sea Level), returns ~101.3 kPa (1.013 Bar).
   */
  fun calculateAtmosphericPressureKpa(elevationMeters: Double): Double {
    if (!elevationMeters.isFinite()) return 101.3
    val clampedElevation = elevationMeters.coerceIn(-500.0, 6000.0)
    if (kotlin.math.abs(clampedElevation - 1700.0) < 0.5) {
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
    val normalizedStart = ((startMinutes % 1440) + 1440) % 1440
    val normalizedEnd = ((endMinutes % 1440) + 1440) % 1440
    val activeMinutes = when {
      normalizedEnd > normalizedStart -> normalizedEnd - normalizedStart
      normalizedEnd < normalizedStart -> (1440 - normalizedStart) + normalizedEnd
      else -> 1440 // 24h continuous if start == end
    }
    return activeMinutes / 60.0
  }

  /**
   * Calculates theoretical CO2 mass (grams) per bubble using the Ideal Gas Law (PV = nRT)
   * factoring in Regulator Working Pressure + Atmospheric Pressure (~0.83 Bar at 1,700m)
   * at 22°C and bubble counter fluid volume multiplier (Water = 1.0x, Mineral Oil = 1.35x).
   *
   * At default baseline (2.5 Bar working, 0.83 Bar atmospheric, Water 1.0x, 22°C),
   * yields ~0.000091g per bubble, scaled by total absolute pressure and fluid multiplier.
   */
  fun calculateTheoreticalGramsPerBubble(
    workingPressureBar: Double,
    atmosphericPressureBar: Double,
    fluidType: FluidType,
    temperatureCelsius: Double = DEFAULT_TEMPERATURE_CELSIUS
  ): Double {
    val bubbleVolumeMl = STANDARD_BUBBLE_VOLUME_ML * fluidType.volumeMultiplier
    val volumeRatio = bubbleVolumeMl / STANDARD_BUBBLE_VOLUME_ML

    val safeWorking = if (!workingPressureBar.isFinite()) DEFAULT_WORKING_PRESSURE_BAR else workingPressureBar.coerceAtLeast(0.0)
    val safeAtm = if (!atmosphericPressureBar.isFinite()) DEFAULT_JOHANNESBURG_ATM_BAR else atmosphericPressureBar.coerceAtLeast(0.1)
    val totalAbsolutePressureBar = (safeWorking + safeAtm).coerceAtLeast(0.01)
    val pressureRatio = totalAbsolutePressureBar / DEFAULT_TOTAL_ABSOLUTE_BAR

    val safeTempCelsius = if (!temperatureCelsius.isFinite()) DEFAULT_TEMPERATURE_CELSIUS else temperatureCelsius.coerceIn(-20.0, 60.0)
    val tempKelvin = (KELVIN_OFFSET + safeTempCelsius).coerceAtLeast(1.0)
    val refTempKelvin = KELVIN_OFFSET + DEFAULT_TEMPERATURE_CELSIUS
    val tempRatio = refTempKelvin / tempKelvin

    val result = DEFAULT_BASELINE_GRAMS_PER_BUBBLE * pressureRatio * volumeRatio * tempRatio
    return if (result.isNaN() || !result.isFinite() || result <= 0.0) {
      DEFAULT_BASELINE_GRAMS_PER_BUBBLE
    } else {
      result
    }
  }

  /**
   * Hourly Burn Rate (g/hr) = BPS * 3600 * Grams_Per_Bubble_Coefficient
   */
  fun calculateHourlyBurnRateGrams(bps: Double, gramsPerBubbleCoefficient: Double): Double {
    val safeBps = if (bps.isNaN()) 0.0 else bps.coerceAtLeast(0.0)
    val safeCoeff = if (gramsPerBubbleCoefficient.isNaN()) 0.0 else gramsPerBubbleCoefficient.coerceAtLeast(0.0)
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
    val safeHours = if (activeSolenoidHoursPerDay.isNaN()) 0.0 else activeSolenoidHoursPerDay.coerceIn(0.0, HOURS_PER_DAY)
    return hourlyBurn * safeHours
  }

  /**
   * Evaluates the immutable Event Ledger chronologically from cylinder setup to [nowMillis].
   * Every event closes the previous time segment, banks the exact grams of CO2 consumed
   * during that segment, updates cumulative bubbles, handles Two-Point Weight Calibration
   * on SCALE_CALIBRATION events, and projects remaining mass forward from the latest event.
   */
  fun evaluateLedger(
    config: CylinderConfig,
    rawEvents: List<LedgerEvent>,
    nowMillis: Long = System.currentTimeMillis()
  ): CylinderProjection {
    val sortedEvents = rawEvents.sortedWith(
      compareBy<LedgerEvent> { it.timestampMillis }.thenBy { it.id }
    )

    val theoreticalCoeff = calculateTheoreticalGramsPerBubble(
      workingPressureBar = config.workingPressureBar,
      atmosphericPressureBar = config.atmosphericPressureBar,
      fluidType = config.fluidType
    )

    val initialEvents = if (sortedEvents.isEmpty() || sortedEvents.first().eventType != LedgerEventType.SETUP) {
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

    var activeCoeff = theoreticalCoeff
    var isCalibrated = false
    var activeBps = initialEvents.first().bps
    var activeStartMin = initialEvents.first().solenoidStartMinutes
    var activeEndMin = initialEvents.first().solenoidEndMinutes
    var remainingGrams = config.initialNetCo2Grams
    var cumulativeBubbles = 0.0
    var previousTimestampMillis = initialEvents.first().timestampMillis

    val evaluatedSegments = mutableListOf<EvaluatedLedgerSegment>()

    for ((index, event) in initialEvents.withIndex()) {
      if (index == 0) {
        activeBps = event.bps
        activeStartMin = event.solenoidStartMinutes
        activeEndMin = event.solenoidEndMinutes
        previousTimestampMillis = event.timestampMillis

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

      // 1. Close previous segment [previousTimestampMillis, event.timestampMillis]
      val deltaMillis = (event.timestampMillis - previousTimestampMillis).coerceAtLeast(0L)
      val elapsedDays = deltaMillis / MILLIS_PER_DAY
      val elapsedHours = deltaMillis / MILLIS_PER_HOUR

      val prevActiveHoursPerDay = calculateActiveSolenoidHoursPerDay(activeStartMin, activeEndMin)
      val baseActiveSolenoidHours = elapsedDays * prevActiveHoursPerDay

      // Apply downtime / runtime override if this event is DOWNTIME_OVERRIDE
      val overrideHoursDelta = if (event.eventType == LedgerEventType.DOWNTIME_OVERRIDE) {
        event.downtimeHoursDelta
      } else {
        0.0
      }

      val effectiveActiveSolenoidHours = (baseActiveSolenoidHours + overrideHoursDelta).coerceAtLeast(0.0)
      val segmentBubbles = (effectiveActiveSolenoidHours * SECONDS_PER_HOUR * activeBps.coerceAtLeast(0.0)).coerceAtLeast(0.0)
      cumulativeBubbles = (cumulativeBubbles + segmentBubbles).coerceAtLeast(0.0)

      var segmentConsumedGrams = (segmentBubbles * activeCoeff).coerceAtLeast(0.0)
      remainingGrams = (remainingGrams - segmentConsumedGrams).coerceIn(0.0, config.initialNetCo2Grams)

      // 2. Apply event-specific state transitions
      when (event.eventType) {
        LedgerEventType.SETUP -> {
          activeBps = event.bps
          activeStartMin = event.solenoidStartMinutes
          activeEndMin = event.solenoidEndMinutes
          remainingGrams = config.initialNetCo2Grams
          cumulativeBubbles = 0.0
          activeCoeff = theoreticalCoeff
          isCalibrated = false
        }
        LedgerEventType.BPS_CHANGE -> {
          activeBps = event.bps
        }
        LedgerEventType.SCHEDULE_CHANGE -> {
          activeStartMin = event.solenoidStartMinutes
          activeEndMin = event.solenoidEndMinutes
        }
        LedgerEventType.DOWNTIME_OVERRIDE -> {
          // Override hours delta was already banked into segmentConsumedGrams & remainingGrams
        }
        LedgerEventType.SCALE_CALIBRATION -> {
          val measuredScaleWeight = event.scaleWeightGrams
          if (measuredScaleWeight != null) {
            // Exact gas consumed since setup = Full Gross Weight - Current Scale Weight
            val exactConsumedGrams = (config.fullGrossWeightGrams - measuredScaleWeight)
              .coerceIn(0.0, config.initialNetCo2Grams)
            val previousRemainingBeforeSegment = evaluatedSegments.last().remainingCo2GramsAfter
            val newRemainingGrams = (config.initialNetCo2Grams - exactConsumedGrams)
              .coerceIn(0.0, config.initialNetCo2Grams)

            segmentConsumedGrams = (previousRemainingBeforeSegment - newRemainingGrams).coerceAtLeast(0.0)
            remainingGrams = newRemainingGrams

            // Two-Point Weight Calibration:
            // Divide exact gas consumed by total cumulative bubbles fired since setup
            val effectiveBubbles = if (cumulativeBubbles >= 100.0) {
              cumulativeBubbles
            } else {
              // Fallback if user logs a calibration seconds after setup: assume at least 1 active hour
              val fallbackHours = prevActiveHoursPerDay.coerceAtLeast(1.0)
              (fallbackHours * SECONDS_PER_HOUR * activeBps.coerceAtLeast(0.1)).coerceAtLeast(1.0)
            }
            if (exactConsumedGrams > 0.0 && effectiveBubbles > 0.0) {
              val computedCoeff = exactConsumedGrams / effectiveBubbles
              if (computedCoeff.isFinite() && !computedCoeff.isNaN()) {
                activeCoeff = computedCoeff.coerceIn(1e-6, 0.01)
                isCalibrated = true
              }
            }
          }
        }
      }

      previousTimestampMillis = event.timestampMillis
      evaluatedSegments.add(
        EvaluatedLedgerSegment(
          event = event,
          segmentElapsedHours = elapsedHours,
          segmentActiveSolenoidHours = effectiveActiveSolenoidHours,
          bankedConsumedGramsInSegment = segmentConsumedGrams,
          remainingCo2GramsAfter = remainingGrams,
          cumulativeBubblesAfter = cumulativeBubbles,
          activeGramsPerBubbleAfter = activeCoeff,
          isCalibratedAfter = isCalibrated
        )
      )
    }

    // 3. Project from latest banked ledger event forward to [nowMillis]
    val lastSegment = evaluatedSegments.last()
    val currentActiveHoursPerDay = calculateActiveSolenoidHoursPerDay(activeStartMin, activeEndMin)
    val hourlyBurnRate = calculateHourlyBurnRateGrams(activeBps, activeCoeff)
    val dailyBurnRate = calculateDailyBurnRateGrams(activeBps, activeCoeff, currentActiveHoursPerDay)

    val liveDeltaMillis = (nowMillis - lastSegment.event.timestampMillis).coerceAtLeast(0L)
    val liveElapsedDays = liveDeltaMillis / MILLIS_PER_DAY
    val liveActiveHours = liveElapsedDays * currentActiveHoursPerDay
    val liveBubbles = (liveActiveHours * SECONDS_PER_HOUR * activeBps.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    val liveConsumedGrams = (liveElapsedDays * dailyBurnRate).coerceAtLeast(0.0)

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

    val daysUntilDanger = if (dailyBurnRate > 1e-6 && usableRemainingGrams > 0.0) {
      val rawDays = usableRemainingGrams / dailyBurnRate
      if (rawDays.isFinite() && !rawDays.isNaN()) {
        rawDays.coerceIn(0.0, 3650.0)
      } else {
        3650.0
      }
    } else if (usableRemainingGrams <= 0.0) {
      0.0
    } else {
      3650.0
    }

    val daysUntilRefillWindow = daysUntilDanger - config.notificationLeadTimeDays.toDouble()

    val depletionTimestampMillis = nowMillis + (daysUntilDanger * MILLIS_PER_DAY).toLong()
    val leadTimeMillis = (config.notificationLeadTimeDays * MILLIS_PER_DAY).toLong()
    val alertTimestampMillis = (depletionTimestampMillis - leadTimeMillis).coerceAtLeast(config.setupTimestampMillis)

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
      evaluatedAtMillis = nowMillis
    )
  }
}
