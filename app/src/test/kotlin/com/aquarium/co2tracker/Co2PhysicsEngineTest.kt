package com.aquarium.co2tracker

import com.aquarium.co2tracker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class Co2PhysicsEngineTest {

  private val fixedZone: ZoneId = ZoneId.of("Africa/Johannesburg") // UTC+2, no DST

  private fun epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, zone: ZoneId = fixedZone): Long =
    ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), zone).toInstant().toEpochMilli()

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

    // Continuous 24h when start == end
    val continuous = Co2PhysicsEngine.calculateActiveSolenoidHoursPerDay(600, 600)
    assertEquals(24.0, continuous, 0.001)
  }

  @Test
  fun testExactSolenoidWindowIntegration_NoOffHoursBurn() {
    // Solenoid runs 10:30 to 18:00 (7.5 hrs/day)
    val startMin = 10 * 60 + 30
    val endMin = 18 * 60

    // Overnight while solenoid is OFF: 18:30 -> next day 08:30 = 0.0 active hours!
    val evening = epochMillis(2026, 5, 1, 18, 30)
    val nextMorning = epochMillis(2026, 5, 2, 8, 30)
    val offHours = Co2PhysicsEngine.calculateActiveSolenoidHoursBetween(evening, nextMorning, startMin, endMin, fixedZone)
    assertEquals(0.0, offHours, 1e-6)

    // Full active window same day: 10:30 -> 18:00 = 7.5 active hours!
    val activeStart = epochMillis(2026, 5, 1, 10, 30)
    val activeEnd = epochMillis(2026, 5, 1, 18, 0)
    val fullDay = Co2PhysicsEngine.calculateActiveSolenoidHoursBetween(activeStart, activeEnd, startMin, endMin, fixedZone)
    assertEquals(7.5, fullDay, 1e-6)

    // Overnight solenoid schedule: 22:00 -> 02:00, queried 00:30 -> 01:30 = 1.0 hour
    val midStart = epochMillis(2026, 5, 2, 0, 30)
    val midEnd = epochMillis(2026, 5, 2, 1, 30)
    val overnightSlice = Co2PhysicsEngine.calculateActiveSolenoidHoursBetween(midStart, midEnd, 22 * 60, 2 * 60, fixedZone)
    assertEquals(1.0, overnightSlice, 1e-6)
  }

  @Test
  fun testProjectTimestampAfterActiveHoursLandsInsideActiveWindow() {
    // Solenoid 10:30 - 18:00; currently Monday 20:00 (OFF), 1.0 active hour left -> Tuesday 11:30
    val mondayEvening = epochMillis(2026, 5, 4, 20, 0)
    val expected = epochMillis(2026, 5, 5, 11, 30)
    val projected = Co2PhysicsEngine.projectTimestampAfterActiveHours(
      fromMillis = mondayEvening,
      requiredActiveHours = 1.0,
      startMinutes = 10 * 60 + 30,
      endMinutes = 18 * 60,
      zoneId = fixedZone
    )
    assertEquals(expected, projected)

    // Currently Monday 17:30 (0.5h left today) + 1.0h needed -> Tuesday 11:00
    val mondayLate = epochMillis(2026, 5, 4, 17, 30)
    val expectedNextDay = epochMillis(2026, 5, 5, 11, 0)
    val projectedSplit = Co2PhysicsEngine.projectTimestampAfterActiveHours(
      fromMillis = mondayLate,
      requiredActiveHours = 1.0,
      startMinutes = 10 * 60 + 30,
      endMinutes = 18 * 60,
      zoneId = fixedZone
    )
    assertEquals(expectedNextDay, projectedSplit)
  }

  @Test
  fun testTheoreticalGramsPerBubble_IdealGasLaw() {
    // At sea level (1.01325 bar) with 0 bar gauge (atmospheric reactor), 0.05mL CO2 @ 22C = 0.00009086g
    val atmOnlyCoeff = Co2PhysicsEngine.calculateTheoreticalGramsPerBubble(
      workingPressureBar = 0.0,
      atmosphericPressureBar = 1.01325,
      fluidType = FluidType.WATER
    )
    assertEquals(0.00009086, atmOnlyCoeff, 0.000001)

    // At 2.5 Bar working + 0.83 Bar atmospheric (Johannesburg) with typical ceramic diffuser backpressure (0.65x):
    // P_abs = 0.83 + 2.5 * 0.65 = 2.455 bar -> ~0.000220g/bubble
    val waterCoeff = Co2PhysicsEngine.calculateTheoreticalGramsPerBubble(
      workingPressureBar = 2.5,
      atmosphericPressureBar = 0.83,
      fluidType = FluidType.WATER
    )
    assertEquals(0.0002201, waterCoeff, 0.000005)

    // Conservative full working pressure upper bound (3.33 bar absolute) -> ~0.0002986g/bubble
    val fullPressureCoeff = Co2PhysicsEngine.calculateTheoreticalGramsPerBubble(
      workingPressureBar = 2.5,
      atmosphericPressureBar = 0.83,
      fluidType = FluidType.WATER,
      backpressureRatio = Co2PhysicsEngine.CONSERVATIVE_FULL_PRESSURE_RATIO
    )
    assertEquals(0.0002986, fullPressureCoeff, 0.000005)
    assertTrue("Conservative bound must exceed typical estimate", fullPressureCoeff > waterCoeff)

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
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val config = CylinderConfig(
      tareWeightGrams = 3350.0,
      fullGrossWeightGrams = 4850.0, // 1500g net CO2
      setupTimestampMillis = setupTime,
      solenoidStartMinutes = 10 * 60,
      solenoidEndMinutes = 18 * 60, // 8 hrs/day
      startingBps = 1.0
    )

    // Simulate 10 full days = 80 active hours = 80 * 3600 = 288,000 bubbles
    val tenDaysMillis = setupTime + (10L * 86_400_000L)
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

    // Scale check-in on day 10 measures 4820g (30g consumed)
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
      nowMillis = tenDaysMillis,
      zoneId = fixedZone
    )

    assertTrue("Should be calibrated", projection.isScaleCalibrated)
    assertEquals(1470.0, projection.remainingCo2Grams, 0.1) // 1500 - 30g = 1470g
    // 30g / 288,000 bubbles = ~0.00010416 g/bubble
    assertEquals(30.0 / 288000.0, projection.activeGramsPerBubble, 0.000001)
    assertFalse("Normal burn should not trigger leak warning", projection.possibleLeakDetected)
    assertTrue("Empty date must be strictly after 10% safety cutoff date", projection.emptyTimestampMillis > projection.depletionTimestampMillis)
    assertNull("No initial calibration check-in reminder needed once calibrated", projection.recommendedCheckInTimestampMillis)
  }

  @Test
  fun testPrematureScaleCheckInDoesNotCorruptBurnCoefficient() {
    val setupTime = epochMillis(2026, 5, 1, 10, 30)
    val config = CylinderConfig(
      tareWeightGrams = 3350.0,
      fullGrossWeightGrams = 4850.0,
      setupTimestampMillis = setupTime,
      solenoidStartMinutes = 10 * 60 + 30,
      solenoidEndMinutes = 18 * 60,
      startingBps = 1.5
    )

    // User weighs bottle just 10 minutes after setup and scale drifts by 3g
    val tenMinsLater = setupTime + 600_000L
    val earlyCheckIn = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = tenMinsLater,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.5,
      solenoidStartMinutes = 10 * 60 + 30,
      solenoidEndMinutes = 18 * 60,
      scaleWeightGrams = 4847.0
    )

    val projection = Co2PhysicsEngine.evaluateLedger(
      config = config,
      rawEvents = listOf(earlyCheckIn),
      nowMillis = tenMinsLater,
      zoneId = fixedZone
    )

    // Remaining mass snaps to scale reading (1497g), but burn coefficient MUST remain theoretical
    assertEquals(1497.0, projection.remainingCo2Grams, 0.01)
    assertFalse("Premature 3g check-in must NOT mark burn rate calibrated", projection.isScaleCalibrated)
    assertEquals(projection.theoreticalGramsPerBubble, projection.activeGramsPerBubble, 1e-9)
    assertFalse(projection.evaluatedSegments.last().didRecalibrateInSegment)
  }

  @Test
  fun testMultiPointCalibrationAndLeakDetection() {
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val config = CylinderConfig(
      tareWeightGrams = 3350.0,
      fullGrossWeightGrams = 4850.0, // 1500g net
      workingPressureValue = 2.5,
      atmosphericPressureKpa = 83.0,
      setupTimestampMillis = setupTime,
      solenoidStartMinutes = 10 * 60,
      solenoidEndMinutes = 18 * 60, // 8 hrs/day
      startingBps = 1.0
    )

    // Day 10 (80 active hrs = 288,000 bubbles): normal loss of 60g -> ~0.000208 g/bubble
    val day10 = setupTime + 10L * 86_400_000L
    val scale1 = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = day10,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      scaleWeightGrams = 4790.0
    )

    // Day 20 (+80 active hrs = +288,000 bubbles): sudden leak loses 250g in 10 days -> ~0.000868 g/bubble!
    val day20 = setupTime + 20L * 86_400_000L
    val scale2 = LedgerEvent(
      id = 3L,
      cylinderId = config.id,
      timestampMillis = day20,
      eventType = LedgerEventType.SCALE_CALIBRATION,
      bps = 1.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      scaleWeightGrams = 4540.0
    )

    val projAfter1 = Co2PhysicsEngine.evaluateLedger(config, listOf(scale1), day10, fixedZone)
    assertTrue(projAfter1.isScaleCalibrated)
    assertFalse(projAfter1.possibleLeakDetected)
    assertEquals(60.0 / 288_000.0, projAfter1.activeGramsPerBubble, 1e-6)

    val projAfter2 = Co2PhysicsEngine.evaluateLedger(config, listOf(scale1, scale2), day20, fixedZone)
    assertTrue(projAfter2.isScaleCalibrated)
    // Uses recent interval (250g / 288,000 bubbles) to capture the developing leak promptly!
    assertEquals(250.0 / 288_000.0, projAfter2.activeGramsPerBubble, 1e-6)
    assertTrue("Abnormally fast CO2 loss must trigger leak warning", projAfter2.possibleLeakDetected)
  }

  @Test
  fun testNegativeDowntimeOverrideLoggedAfterRecentEventIsNotLost() {
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val config = CylinderConfig(
      tareWeightGrams = 3000.0,
      fullGrossWeightGrams = 4500.0,
      setupTimestampMillis = setupTime,
      solenoidStartMinutes = 10 * 60,
      solenoidEndMinutes = 18 * 60, // 8 hrs/day
      startingBps = 2.0
    )

    // Day 4 at 12:00: 3 full days (24h) + 2h = 26 active hours elapsed
    val day4Noon = epochMillis(2026, 5, 4, 12, 0)
    val bpsEvent = LedgerEvent(
      id = 2L,
      cylinderId = config.id,
      timestampMillis = day4Noon,
      eventType = LedgerEventType.BPS_CHANGE,
      bps = 2.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080
    )

    // 5 minutes later (12:05), user logs -4.0h downtime from a previous power outage
    val day4Noon05 = epochMillis(2026, 5, 4, 12, 5)
    val downtimeEvent = LedgerEvent(
      id = 3L,
      cylinderId = config.id,
      timestampMillis = day4Noon05,
      eventType = LedgerEventType.DOWNTIME_OVERRIDE,
      bps = 2.0,
      solenoidStartMinutes = 600,
      solenoidEndMinutes = 1080,
      downtimeHoursDelta = -4.0
    )

    val withoutOverride = Co2PhysicsEngine.evaluateLedger(config, listOf(bpsEvent), day4Noon05, fixedZone)
    val withOverride = Co2PhysicsEngine.evaluateLedger(config, listOf(bpsEvent, downtimeEvent), day4Noon05, fixedZone)

    val expectedSavedGrams = 4.0 * withoutOverride.hourlyBurnRateGrams
    assertEquals(
      expectedSavedGrams,
      withOverride.remainingCo2Grams - withoutOverride.remainingCo2Grams,
      0.05
    )
  }

  @Test
  fun testUncalibratedConservativeAlertPrecedesExpectedCutoff() {
    val setupTime = epochMillis(2026, 5, 1, 9, 0)
    val config = CylinderConfig(
      tareWeightGrams = 3350.0,
      fullGrossWeightGrams = 4850.0, // 1500g net
      workingPressureValue = 2.5,
      atmosphericPressureKpa = 83.0,
      solenoidStartMinutes = 10 * 60 + 30,
      solenoidEndMinutes = 18 * 60,
      startingBps = 1.5,
      dangerThresholdPercent = 10.0,
      notificationLeadTimeDays = 5,
      setupTimestampMillis = setupTime
    )

    val proj = Co2PhysicsEngine.evaluateLedger(config, emptyList(), setupTime, fixedZone)
    assertFalse(proj.isScaleCalibrated)
    assertTrue(
      "Conservative cutoff must be earlier than expected cutoff while uncalibrated",
      proj.conservativeDepletionTimestampMillis < proj.depletionTimestampMillis
    )
    assertTrue(
      "Alert timestamp must precede conservative cutoff when uncalibrated",
      proj.alertTimestampMillis < proj.conservativeDepletionTimestampMillis
    )
    assertTrue(
      "Empty timestamp (0g) must be later than 10% safety cutoff",
      proj.emptyTimestampMillis > proj.depletionTimestampMillis
    )
    assertNotNull("Uncalibrated setup should recommend a first scale check-in date", proj.recommendedCheckInTimestampMillis)
    assertTrue(
      "Recommended check-in must come before refill alert",
      proj.recommendedCheckInTimestampMillis!! <= proj.alertTimestampMillis
    )
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
    val proj = Co2PhysicsEngine.evaluateLedger(config, emptyList(), nowMillis = 1000L, zoneId = fixedZone)
    assertFalse("0 BPS should report hasActiveBurn = false", proj.hasActiveBurn)
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
      nowMillis = setupTime + 2000L,
      zoneId = fixedZone
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
    val setupTime = epochMillis(2026, 5, 1, 10, 0)
    val config = CylinderConfig(
      tareWeightGrams = 3000.0,
      fullGrossWeightGrams = 4000.0,
      setupTimestampMillis = setupTime,
      startingBps = 1.0
    )

    // Measured scale weight below tare (physically impossible / empty bottle)
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
      nowMillis = setupTime + 200_000L,
      zoneId = fixedZone
    )
    assertEquals(0.0, projBelowTare.remainingCo2Grams, 0.0)
    assertTrue("Below tare should be treated as empty", projBelowTare.isBelowDangerThreshold)
    assertTrue("Below tare should set isEmpty = true", projBelowTare.isEmpty)
    assertFalse("Empty check-in must NOT corrupt calibration state", projBelowTare.isScaleCalibrated)

    // Measured scale weight above full gross weight
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
      nowMillis = setupTime + 200_000L,
      zoneId = fixedZone
    )
    assertEquals(1000.0, projAboveGross.remainingCo2Grams, 0.05)
    assertFalse("Above-gross check-in must NOT mark calibrated", projAboveGross.isScaleCalibrated)
  }

  @Test
  fun testParseDecimalInputSupportsCommaAndDot() {
    assertEquals(1.5, Co2PhysicsEngine.parseDecimalInput("1.5")!!, 1e-9)
    assertEquals(1.5, Co2PhysicsEngine.parseDecimalInput("1,5")!!, 1e-9)
    assertEquals(-4.25, Co2PhysicsEngine.parseDecimalInput(" -4,25 ")!!, 1e-9)
    assertNull(Co2PhysicsEngine.parseDecimalInput("abc"))
    assertNull(Co2PhysicsEngine.parseDecimalInput("NaN"))
  }
}
