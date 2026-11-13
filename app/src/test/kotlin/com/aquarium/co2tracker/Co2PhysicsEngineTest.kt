package com.aquarium.co2tracker

import com.aquarium.co2tracker.domain.*
import org.junit.Assert.*
import org.junit.Test

class Co2PhysicsEngineTest {

  @Test
  fun testAtmosphericPressureCalculation() {
    // Johannesburg at 1,700m should yield ~83.0 kPa
    val jhbPressure = Co2PhysicsEngine.calculateAtmosphericPressureKpa(1700.0)
    assertEquals(83.0, jhbPressure, 0.5)

    // Sea level (0m) should yield ~101.3 kPa
    val seaLevelPressure = Co2PhysicsEngine.calculateAtmosphericPressureKpa(0.0)
    assertEquals(101.3, seaLevelPressure, 1.0)
  }

  @Test
  fun testActiveSolenoidHoursCalculation() {
    // 10:30 (630 min) to 18:00 (1080 min) = 450 min = 7.5 hrs
    val hours = Co2PhysicsEngine.calculateActiveSolenoidHoursPerDay(10 * 60 + 30, 18 * 60)
    assertEquals(7.5, hours, 0.001)

    // Midnight crossover: 22:00 (1320 min) to 02:00 (120 min) = 4.0 hrs
    val overnightHours = Co2PhysicsEngine.calculateActiveSolenoidHoursPerDay(22 * 60, 2 * 60)
    assertEquals(4.0, overnightHours, 0.001)
  }

  @Test
  fun testTheoreticalGramsPerBubble() {
    val waterCoeff = Co2PhysicsEngine.calculateTheoreticalGramsPerBubble(
      workingPressureBar = 2.5,
      atmosphericPressureBar = 0.83,
      fluidType = FluidType.WATER
    )
    // Should be around ~0.000091g per bubble at default 2.5 Bar + 0.83 Bar
    assertEquals(0.000091, waterCoeff, 0.000005)

    // Mineral oil (1.35x multiplier)
    val oilCoeff = Co2PhysicsEngine.calculateTheoreticalGramsPerBubble(
      workingPressureBar = 2.5,
      atmosphericPressureBar = 0.83,
      fluidType = FluidType.MINERAL_OIL
    )
    assertEquals(waterCoeff * 1.35, oilCoeff, 0.000001)
  }

  @Test
  fun testBurnRateCalculation() {
    val coeff = 0.000091
    val bps = 1.5
    val activeHours = 7.5

    // Hourly burn = 1.5 * 3600 * 0.000091 = 0.4914 g/hr
    val hourly = Co2PhysicsEngine.calculateHourlyBurnRateGrams(bps, coeff)
    assertEquals(0.4914, hourly, 0.005)

    // Daily burn = 0.4914 * 7.5 = 3.6855 g/day
    val daily = Co2PhysicsEngine.calculateDailyBurnRateGrams(bps, coeff, activeHours)
    assertEquals(3.6855, daily, 0.02)
  }

  @Test
  fun testTwoPointCalibrationOnWeightCheckIn() {
    val setupTime = 1_000_000_000L
    val config = CylinderConfig(
      tareWeightGrams = 3350.0,
      fullGrossWeightGrams = 4850.0, // 1500g net CO2
      setupTimestampMillis = setupTime,
      solenoidStartMinutes = 10 * 60,
      solenoidEndMinutes = 18 * 60, // 8 hrs/day
      startingBps = 1.0
    )

    // Simulate 10 days of elapsed time = 80 active hours = 80 * 3600 = 288,000 bubbles
    val tenDaysMillis = setupTime + (10 * 86_400_000L)
    val initialEvents = listOf(
      LedgerEvent(
        id = 1L,
        cylinderId = config.id,
        timestampMillis = setupTime,
        eventType = LedgerEventType.SETUP,
        bps = 1.0,
        solenoidStartMinutes = 600,
        solenoidEndMinutes = 1080
      )
    )

    // Suppose scale check-in on day 10 measures 4820g (30g consumed)
    val scaleEvent = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = tenDaysMillis,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      scaleWeightGrams = 4820.0,
      note = "Scale check-in on kitchen scale"
    )

    val projection = Co2PhysicsEngine.evaluateLedger(
      config = config,
      rawEvents = initialEvents + scaleEvent,
      nowMillis = tenDaysMillis
    )

    assertTrue("Should be calibrated", projection.isScaleCalibrated)
    assertEquals(1470.0, projection.remainingCo2Grams, 0.1) // 1500 - 30g = 1470g
    // 30g / 288,000 bubbles = ~0.00010416 g/bubble
    assertEquals(30.0 / 288000.0, projection.activeGramsPerBubble, 0.00001)
  }

  @Test
  fun testZeroDivisionSafety() {
    // 0 BPS burn rate
    val hourlyZeroBps = Co2PhysicsEngine.calculateHourlyBurnRateGrams(0.0, 0.000091)
    assertEquals(0.0, hourlyZeroBps, 0.0)

    val dailyZeroBps = Co2PhysicsEngine.calculateDailyBurnRateGrams(0.0, 0.000091, 0.0)
    assertEquals(0.0, dailyZeroBps, 0.0)

    // NaN elevation
    val nanElevationPressure = Co2PhysicsEngine.calculateAtmosphericPressureKpa(Double.NaN)
    assertTrue("Should handle NaN elevation", nanElevationPressure.isFinite())

    // Zero / NaN pressures in theoretical grams per bubble
    val zeroPressureCoeff = Co2PhysicsEngine.calculateTheoreticalGramsPerBubble(0.0, 0.0, FluidType.WATER)
    assertTrue("Should be positive finite", zeroPressureCoeff > 0.0 && zeroPressureCoeff.isFinite())

    // Projection when burn rate is 0: daysUntilDanger should safely clamp without division by zero
    val config = CylinderConfig(
      tareWeightGrams = 3000.0,
      fullGrossWeightGrams = 4000.0,
      setupTimestampMillis = 1000L,
      startingBps = 0.0
    )
    val proj = Co2PhysicsEngine.evaluateLedger(config, emptyList(), nowMillis = 1000L)
    assertTrue("Days until danger should be finite", proj.daysUntilDangerThreshold.isFinite())
    assertEquals(3650.0, proj.daysUntilDangerThreshold, 0.01)
  }

  @Test
  fun testNegativeOverrideAndDurationGracefulHandling() {
    val setupTime = 10_000_000L
    val config = CylinderConfig(
      tareWeightGrams = 3000.0,
      fullGrossWeightGrams = 4000.0,
      setupTimestampMillis = setupTime,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      startingBps = 1.0
    )

    // Negative downtime override (e.g. user enters -500 hours)
    val negativeOverrideEvent = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = setupTime + 1000L,
      eventType = LedgerEventType.DOWNTIME_OVERRIDE,
      bps = 1.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      downtimeHoursDelta = -500.0
    )

    val projection = Co2PhysicsEngine.evaluateLedger(
      config = config,
      rawEvents = listOf(negativeOverrideEvent),
      nowMillis = setupTime + 2000L
    )

    assertTrue("Remaining CO2 must not exceed initial capacity", projection.remainingCo2Grams <= 1000.0)
    assertTrue("Remaining CO2 must not be negative", projection.remainingCo2Grams >= 0.0)
    assertTrue("Cumulative bubbles must not be negative", projection.cumulativeBubblesFired >= 0.0)
  }

  @Test
  fun testUnconfiguredCylinderModel() {
    val defaultCfg = CylinderConfig()
    assertFalse("Default cylinder config must not be configured", defaultCfg.isConfigured)
    assertEquals(0.0, defaultCfg.tareWeightGrams, 0.0)
    assertEquals(0.0, defaultCfg.fullGrossWeightGrams, 0.0)
    assertEquals(0.0, defaultCfg.initialNetCo2Grams, 0.0)

    val validCfg = CylinderConfig(tareWeightGrams = 3000.0, fullGrossWeightGrams = 4500.0)
    assertTrue("Configured cylinder should be valid", validCfg.isConfigured)
    assertEquals(1500.0, validCfg.initialNetCo2Grams, 0.0)
  }

  @Test
  fun testInfiniteValuesHandling() {
    val infElev = Co2PhysicsEngine.calculateAtmosphericPressureKpa(Double.POSITIVE_INFINITY)
    assertEquals(101.3, infElev, 0.001)

    val negInfElev = Co2PhysicsEngine.calculateAtmosphericPressureKpa(Double.NEGATIVE_INFINITY)
    assertEquals(101.3, negInfElev, 0.001)

    val infCoeff = Co2PhysicsEngine.calculateTheoreticalGramsPerBubble(
      workingPressureBar = Double.POSITIVE_INFINITY,
      atmosphericPressureBar = Double.POSITIVE_INFINITY,
      fluidType = FluidType.WATER,
      temperatureCelsius = Double.POSITIVE_INFINITY
    )
    assertTrue("Should produce positive finite coefficient", infCoeff > 0.0 && infCoeff.isFinite())
  }

  @Test
  fun testScaleWeightOutOfBoundsInPhysicsEngine() {
    val setupTime = 1_000_000L
    val config = CylinderConfig(
      tareWeightGrams = 3000.0,
      fullGrossWeightGrams = 4000.0,
      setupTimestampMillis = setupTime,
      startingBps = 1.0
    )

    // Measured scale weight below tare (physically impossible scale error)
    val belowTareEvent = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = setupTime + 100_000L,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      scaleWeightGrams = 2500.0 // Tare is 3000g!
    )

    val projBelowTare = Co2PhysicsEngine.evaluateLedger(
      config = config,
      rawEvents = listOf(belowTareEvent),
      nowMillis = setupTime + 200_000L
    )
    assertEquals(0.0, projBelowTare.remainingCo2Grams, 0.0)
    assertTrue("Below tare should be treated as empty", projBelowTare.isBelowDangerThreshold)

    // Measured scale weight above full gross weight (user placed something extra on scale)
    val aboveGrossEvent = LedgerEvent(
      id = 3L,
      cylinderId = config.id,
      timestampMillis = setupTime + 100_000L,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      scaleWeightGrams = 5000.0 // Full gross is 4000g!
    )

    val projAboveGross = Co2PhysicsEngine.evaluateLedger(
      config = config,
      rawEvents = listOf(aboveGrossEvent),
      nowMillis = setupTime + 200_000L
    )
    assertEquals(1000.0, projAboveGross.remainingCo2Grams, 0.01)
  }
}
