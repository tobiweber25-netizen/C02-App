package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.aquarium.co2tracker.domain.*
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InitialCylinderSetupDialog(
  currentConfig: CylinderConfig? = null,
  isEditSettingsMode: Boolean = false,
  isAddBottleMode: Boolean = false,
  previousCalibratedCoeff: Double? = null,
  onDismiss: () -> Unit,
  onConfirm: (CylinderConfig) -> Unit
) {
  val defaultName = when {
    isAddBottleMode -> "Spare CO2 Cylinder"
    else -> currentConfig?.cylinderName ?: "Primary CO2 Cylinder"
  }
  var cylinderNameText by remember { mutableStateOf(defaultName) }
  var bottleStatus by remember {
    mutableStateOf(
      when {
        isAddBottleMode -> BottleStatus.SPARE_FULL
        else -> currentConfig?.bottleStatus ?: BottleStatus.ACTIVE
      }
    )
  }

  var tareWeightText by remember {
    mutableStateOf(
      if (!isAddBottleMode && currentConfig != null && currentConfig.tareWeightGrams > 0.0) {
        currentConfig.tareWeightGrams.roundToInt().toString()
      } else {
        ""
      }
    )
  }
  var fullGrossText by remember {
    mutableStateOf(
      if (!isAddBottleMode && currentConfig != null && currentConfig.fullGrossWeightGrams > 0.0) {
        currentConfig.fullGrossWeightGrams.roundToInt().toString()
      } else {
        ""
      }
    )
  }
  var includeRegulator by remember { mutableStateOf(currentConfig?.includeRegulatorWeight ?: false) }
  var regulatorWeightText by remember {
    mutableStateOf(
      if (currentConfig != null && currentConfig.regulatorWeightGrams > 0.0) {
        currentConfig.regulatorWeightGrams.roundToInt().toString()
      } else {
        ""
      }
    )
  }

  var workingPressureText by remember { mutableStateOf(currentConfig?.workingPressureValue?.toString() ?: "2.5") }
  var pressureUnit by remember { mutableStateOf(currentConfig?.workingPressureUnit ?: PressureUnit.BAR) }
  var fluidType by remember { mutableStateOf(currentConfig?.fluidType ?: FluidType.WATER) }
  var elevationText by remember {
    mutableStateOf(if (currentConfig != null) currentConfig.elevationMeters.roundToInt().toString() else "0")
  }

  val curStartH = (currentConfig?.solenoidStartMinutes ?: (10 * 60 + 30)) / 60
  val curStartM = (currentConfig?.solenoidStartMinutes ?: (10 * 60 + 30)) % 60
  val curEndH = (currentConfig?.solenoidEndMinutes ?: (18 * 60)) / 60
  val curEndM = (currentConfig?.solenoidEndMinutes ?: (18 * 60)) % 60

  var startHourText by remember { mutableStateOf(String.format(Locale.US, "%02d", curStartH)) }
  var startMinText by remember { mutableStateOf(String.format(Locale.US, "%02d", curStartM)) }
  var endHourText by remember { mutableStateOf(String.format(Locale.US, "%02d", curEndH)) }
  var endMinText by remember { mutableStateOf(String.format(Locale.US, "%02d", curEndM)) }

  var startingBpsText by remember { mutableStateOf((currentConfig?.startingBps ?: 1.5).toString()) }
  var dangerThresholdText by remember { mutableStateOf((currentConfig?.dangerThresholdPercent ?: 10.0).roundToInt().toString()) }
  var leadTimeDaysText by remember { mutableStateOf((currentConfig?.notificationLeadTimeDays ?: 5).toString()) }

  // Upgrade #3: Carry-over calibration & Refill Cost
  val canCarryCalibration = !isEditSettingsMode && !isAddBottleMode && previousCalibratedCoeff != null
  var carryCalibration by remember { mutableStateOf(canCarryCalibration) }
  var refillCostText by remember {
    mutableStateOf(currentConfig?.lastRefillCost?.takeIf { it > 0.0 }?.toString().orEmpty())
  }

  // Upgrade #5: Refill Shop Open Days & Reconnect Leak Checklist
  var shopDaysMask by remember { mutableIntStateOf(currentConfig?.refillShopDaysMask ?: CylinderConfig.ALL_DAYS_MASK) }
  var shopNameText by remember { mutableStateOf(currentConfig?.refillShopName.orEmpty()) }
  var checkWasher by remember { mutableStateOf(false) }
  var checkNutTight by remember { mutableStateOf(false) }
  var checkSoapTest by remember { mutableStateOf(false) }

  val tareGrams = Co2PhysicsEngine.parseDecimalInput(tareWeightText)
  val grossGrams = Co2PhysicsEngine.parseDecimalInput(fullGrossText)
  val parsedRegGrams = Co2PhysicsEngine.parseDecimalInput(regulatorWeightText)
  val isRegValid = !includeRegulator || (parsedRegGrams != null && parsedRegGrams >= 0.0)
  val regGrams = if (includeRegulator) (parsedRegGrams ?: 0.0).coerceAtLeast(0.0) else 0.0
  val effectiveTare = (tareGrams ?: 0.0) + regGrams

  val parsedWorkingPressure = Co2PhysicsEngine.parseDecimalInput(workingPressureText)
  val isPressureValid = parsedWorkingPressure != null && parsedWorkingPressure >= 0.0
  val parsedBps = Co2PhysicsEngine.parseDecimalInput(startingBpsText)
  val isBpsValid = parsedBps != null && parsedBps >= 0.0
  val parsedDangerPct = Co2PhysicsEngine.parseDecimalInput(dangerThresholdText)
  val isDangerValid = parsedDangerPct != null && parsedDangerPct in 0.0..95.0
  val parsedLeadDays = leadTimeDaysText.trim().toIntOrNull()
  val isLeadValid = parsedLeadDays != null && parsedLeadDays in 0..365
  val parsedCost = if (refillCostText.isBlank()) null else Co2PhysicsEngine.parseDecimalInput(refillCostText)
  val isCostValid = refillCostText.isBlank() || (parsedCost != null && parsedCost >= 0.0)

  val isTareValid = tareGrams != null && tareGrams > 0.0
  val isGrossValid = grossGrams != null && grossGrams > effectiveTare
  val isValid = isTareValid && isGrossValid && isRegValid && isPressureValid && isBpsValid && isDangerValid && isLeadValid && isCostValid
  val calculatedNetCo2 = if (isTareValid && isGrossValid) (grossGrams - effectiveTare).coerceAtLeast(0.0) else 0.0

  val elevationMeters = Co2PhysicsEngine.parseDecimalInput(elevationText) ?: 0.0
  val calculatedAtmPressureKpa = Co2PhysicsEngine.calculateAtmosphericPressureKpa(elevationMeters)

  val sH = (startHourText.trim().toIntOrNull() ?: curStartH).coerceIn(0, 23)
  val sM = (startMinText.trim().toIntOrNull() ?: curStartM).coerceIn(0, 59)
  val eH = (endHourText.trim().toIntOrNull() ?: curEndH).coerceIn(0, 23)
  val eM = (endMinText.trim().toIntOrNull() ?: curEndM).coerceIn(0, 59)
  val startTotalMinutes = sH * 60 + sM
  val endTotalMinutes = eH * 60 + eM
  val activeHoursPerDay = Co2PhysicsEngine.calculateActiveSolenoidHoursPerDay(startTotalMinutes, endTotalMinutes)

  val sheetTitle = when {
    isAddBottleMode -> "Add Spare Bottle or Tank"
    isEditSettingsMode -> "Edit Cylinder Settings"
    else -> "Cylinder Setup & Refill"
  }
  val sheetSubtitle = when {
    isAddBottleMode -> "Save another cylinder with its empty weight for quick swaps or multi-tank tracking."
    isEditSettingsMode -> "Update pressure, weights, shop days, or alert margins without erasing your ledger."
    else -> "Configure bottle weights, refill shop days, and optional carry-over calibration."
  }

  AquaModalSheet(
    title = sheetTitle,
    subtitle = sheetSubtitle,
    iconType = AquaIconType.CYLINDER_REFILL,
    accentColor = PearlEmerald,
    onDismiss = onDismiss
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
      // Optional Bottle Role picker when adding a new cylinder to fleet
      if (isAddBottleMode) {
        AquaSectionHeader(title = "Bottle Role")
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
          FilterChip(
            selected = bottleStatus == BottleStatus.SPARE_FULL,
            onClick = { bottleStatus = BottleStatus.SPARE_FULL },
            label = { Text("Full Spare Ready", style = AquaTextStyles.caption) },
            colors = aquaChipColors(StarphireCyan),
            modifier = Modifier.weight(1f)
          )
          FilterChip(
            selected = bottleStatus == BottleStatus.ACTIVE,
            onClick = { bottleStatus = BottleStatus.ACTIVE },
            label = { Text("Active on Tank", style = AquaTextStyles.caption) },
            colors = aquaChipColors(PearlEmerald),
            modifier = Modifier.weight(1f)
          )
        }
      }

      // 1. Cylinder Identity & Weights
      AquaSectionHeader(title = "Bottle Weight & Gas Mass")

      OutlinedTextField(
        value = cylinderNameText,
        onValueChange = { cylinderNameText = it },
        label = { Text("Tank / Cylinder Name") },
        placeholder = { Text("e.g. ADA 90P Rimless CO2") },
        singleLine = true,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(PearlEmerald),
        modifier = Modifier.fillMaxWidth()
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
      ) {
        OutlinedTextField(
          value = tareWeightText,
          onValueChange = { tareWeightText = it },
          label = { Text("Tare Weight (g)") },
          placeholder = { Text("3350") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          isError = tareWeightText.isNotBlank() && !isTareValid,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = fullGrossText,
          onValueChange = { fullGrossText = it },
          label = { Text("Full Gross (g)") },
          placeholder = { Text("4850") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          isError = fullGrossText.isNotBlank() && !isGrossValid,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
      }

      if (tareWeightText.isNotBlank() && !isTareValid) {
        Text(
          text = "Tare weight must be greater than 0g.",
          style = AquaTextStyles.caption,
          color = DropCheckerCoral
        )
      } else if (fullGrossText.isNotBlank() && !isGrossValid) {
        Text(
          text = "Full gross weight must exceed tare (${effectiveTare.roundToInt()}g).",
          style = AquaTextStyles.caption,
          color = DropCheckerCoral
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = includeRegulator,
          onCheckedChange = { includeRegulator = it },
          colors = CheckboxDefaults.colors(
            checkedColor = PearlEmerald,
            checkmarkColor = OnPearlEmerald,
            uncheckedColor = StarphireRimBright
          )
        )
        Text(
          text = "Weighed with regulator attached",
          style = AquaTextStyles.bodySm,
          color = InkPrimary
        )
      }

      if (includeRegulator) {
        OutlinedTextField(
          value = regulatorWeightText,
          onValueChange = { regulatorWeightText = it },
          label = { Text("Regulator Weight (grams)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          isError = regulatorWeightText.isNotBlank() && !isRegValid,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(PearlEmerald),
          modifier = Modifier.fillMaxWidth()
        )
      }

      GlassInsetWell(
        modifier = Modifier.fillMaxWidth(),
        borderColor = PearlEmerald.copy(alpha = AlphaTokens.rimMedium),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "NET CO2 FILL MASS",
            style = AquaTextStyles.monoMicro,
            color = InkSecondary
          )
          Text(
            text = String.format(Locale.US, "%,dg CO2", calculatedNetCo2.roundToInt()),
            style = AquaTextStyles.monoLarge,
            color = PearlEmerald
          )
        }
      }

      // Carry-Over Calibration Toggle (Upgrade #3)
      if (canCarryCalibration) {
        val carriedValue = previousCalibratedCoeff
        GlassInsetWell(
          modifier = Modifier.fillMaxWidth(),
          borderColor = DropCheckerOptimal.copy(alpha = AlphaTokens.rimSoft),
          contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = carryCalibration,
              onCheckedChange = { carryCalibration = it },
              colors = CheckboxDefaults.colors(
                checkedColor = DropCheckerOptimal,
                checkmarkColor = OnPearlEmerald,
                uncheckedColor = StarphireRimBright
              )
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Carry over proven burn calibration",
                style = AquaTextStyles.bodySm,
                color = InkPrimary
              )
              Text(
                text = String.format(
                  Locale.US,
                  "Keeps %.5fg/bubble from your previous fill for Day-1 accuracy",
                  carriedValue
                ),
                style = AquaTextStyles.caption,
                color = InkSecondary
              )
            }
          }
        }
      }

      // Optional Refill Cost field for Refill Archive tracking
      OutlinedTextField(
        value = refillCostText,
        onValueChange = { refillCostText = it },
        label = { Text("Refill Cost (Optional)") },
        placeholder = { Text("e.g. 150 (tracks monthly cost in archive)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = !isCostValid,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(StarphireCyan),
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(Spacing.xs))

      // 2. Pressure & Bubble Counter Fluid
      AquaSectionHeader(title = "Regulator Pressure & Fluid")

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = workingPressureText,
          onValueChange = { workingPressureText = it },
          label = { Text("Working Pressure") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          isError = workingPressureText.isNotBlank() && !isPressureValid,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(StarphireCyan),
          modifier = Modifier.weight(1.2f)
        )

        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
          FilterChip(
            selected = pressureUnit == PressureUnit.BAR,
            onClick = { pressureUnit = PressureUnit.BAR },
            label = { Text("Bar", style = AquaTextStyles.monoCaption) },
            colors = aquaChipColors(StarphireCyan),
            modifier = Modifier.weight(1f)
          )
          FilterChip(
            selected = pressureUnit == PressureUnit.PSI,
            onClick = { pressureUnit = PressureUnit.PSI },
            label = { Text("PSI", style = AquaTextStyles.monoCaption) },
            colors = aquaChipColors(StarphireCyan),
            modifier = Modifier.weight(1f)
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
      ) {
        FilterChip(
          selected = fluidType == FluidType.WATER,
          onClick = { fluidType = FluidType.WATER },
          label = { Text(FluidType.WATER.displayName, style = AquaTextStyles.caption) },
          colors = aquaChipColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
        FilterChip(
          selected = fluidType == FluidType.MINERAL_OIL,
          onClick = { fluidType = FluidType.MINERAL_OIL },
          label = { Text(FluidType.MINERAL_OIL.displayName, style = AquaTextStyles.caption) },
          colors = aquaChipColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(Spacing.xs))

      // 3. Elevation & Atmospheric Baseline
      AquaSectionHeader(
        title = "Atmospheric Elevation",
        trailingLabel = String.format(Locale.US, "%.1f kPa", calculatedAtmPressureKpa),
        trailingColor = StarphireCyan
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = elevationText,
          onValueChange = { elevationText = it },
          label = { Text("Elevation (m)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(StarphireCyan),
          modifier = Modifier.weight(1f)
        )

        FilledTonalButton(
          onClick = { elevationText = "0" },
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = GlassWell,
            contentColor = StarphireCyan
          )
        ) {
          Text("Sea 0m", style = AquaTextStyles.monoCaption)
        }

        FilledTonalButton(
          onClick = { elevationText = "1700" },
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = GlassWell,
            contentColor = StarphireCyan
          )
        ) {
          Text("1700m", style = AquaTextStyles.monoCaption)
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xs))

      // 4. Solenoid Photoperiod & Starting BPS
      AquaSectionHeader(
        title = "Solenoid Schedule & Flow",
        trailingLabel = String.format(Locale.US, "%.1f HRS/DAY", activeHoursPerDay),
        trailingColor = PearlEmerald
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
      ) {
        OutlinedTextField(
          value = startHourText,
          onValueChange = { if (it.length <= 2) startHourText = it },
          label = { Text("ON Hr") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = startMinText,
          onValueChange = { if (it.length <= 2) startMinText = it },
          label = { Text("ON Min") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = endHourText,
          onValueChange = { if (it.length <= 2) endHourText = it },
          label = { Text("OFF Hr") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = endMinText,
          onValueChange = { if (it.length <= 2) endMinText = it },
          label = { Text("OFF Min") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(PearlEmerald),
          modifier = Modifier.weight(1f)
        )
      }

      OutlinedTextField(
        value = startingBpsText,
        onValueChange = { startingBpsText = it },
        label = { Text("Starting Bubble Rate (BPS)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = startingBpsText.isNotBlank() && !isBpsValid,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(PearlEmerald),
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(Spacing.xs))

      // 5. Safety Alert Thresholds & Refill Shop Open Days (Upgrade #5)
      AquaSectionHeader(title = "Safety Alerts & Shop Days")

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
      ) {
        OutlinedTextField(
          value = dangerThresholdText,
          onValueChange = { dangerThresholdText = it },
          label = { Text("Danger Cutoff (%)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          isError = dangerThresholdText.isNotBlank() && !isDangerValid,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(DropCheckerAmber),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = leadTimeDaysText,
          onValueChange = { leadTimeDaysText = it },
          label = { Text("Alert Lead (Days)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          isError = leadTimeDaysText.isNotBlank() && !isLeadValid,
          shape = RoundedCornerShape(Radius.md),
          colors = aquaTextFieldColors(DropCheckerAmber),
          modifier = Modifier.weight(1f)
        )
      }

      if (!isDangerValid || !isLeadValid) {
        Text(
          text = "Danger cutoff must be 0–95% and alert lead time must be 0–365 days.",
          style = AquaTextStyles.caption,
          color = DropCheckerCoral
        )
      }

      // 7-day Refill Shop Open Days Toggle Strip
      Text(
        text = "REFILL SHOP OPEN DAYS (SHIFTS ALERTS AHEAD OF CLOSED DAYS)",
        style = AquaTextStyles.monoMicro,
        color = InkTertiary
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
      ) {
        val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
        dayLetters.forEachIndexed { idx, letter ->
          val bit = 1 shl idx
          val isOpen = (shopDaysMask and bit) != 0
          val chipShape = RoundedCornerShape(Radius.sm)
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(chipShape)
              .background(if (isOpen) PearlEmerald.copy(alpha = AlphaTokens.mediumTint) else GlassWell)
              .border(
                StrokeTokens.hairline,
                if (isOpen) PearlEmerald.copy(alpha = AlphaTokens.rimMedium) else StarphireRimSubtle,
                chipShape
              )
              .clickable {
                val toggled = shopDaysMask xor bit
                // Keep at least 1 open day selected
                if (toggled != 0) shopDaysMask = toggled
              }
              .padding(vertical = Spacing.sm),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = letter,
              style = AquaTextStyles.monoCaption,
              fontWeight = if (isOpen) FontWeight.Bold else FontWeight.Normal,
              color = if (isOpen) PearlEmerald else InkTertiary
            )
          }
        }
      }

      OutlinedTextField(
        value = shopNameText,
        onValueChange = { shopNameText = it },
        label = { Text("Local Refill Shop / Notes (Optional)") },
        placeholder = { Text("e.g. Fire Safety Depot • Mon–Fri 8–4") },
        singleLine = true,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(DropCheckerAmber),
        modifier = Modifier.fillMaxWidth()
      )

      // 6. Reconnect Leak-Prevention Checklist (on New Refill only — Upgrade #5)
      if (!isEditSettingsMode && !isAddBottleMode) {
        Spacer(modifier = Modifier.height(Spacing.xs))
        GlassInsetWell(
          modifier = Modifier.fillMaxWidth(),
          borderColor = StarphireCyan.copy(alpha = AlphaTokens.rimSoft),
          contentPadding = PaddingValues(Spacing.md)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
              text = "RECONNECT LEAK-CHECK LIST",
              style = AquaTextStyles.monoMicro,
              color = StarphireCyan
            )
            ChecklistRow("Fresh nylon/PTFE washer seated in regulator nut", checkWasher) { checkWasher = it }
            ChecklistRow("Regulator nut tightened snugly with wrench", checkNutTight) { checkNutTight = it }
            ChecklistRow("Soapy-water spray test showed zero bubbles", checkSoapTest) { checkSoapTest = it }
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.sm))
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    val buildConfig: () -> CylinderConfig? = {
      if (isValid) {
        val cleanName = cylinderNameText.trim().ifBlank { "Primary CO2 Cylinder" }
        CylinderConfig(
          id = if (isAddBottleMode) 0L else (currentConfig?.id ?: 1L),
          cylinderName = cleanName,
          tareWeightGrams = tareGrams,
          fullGrossWeightGrams = grossGrams,
          includeRegulatorWeight = includeRegulator,
          regulatorWeightGrams = regGrams,
          workingPressureValue = parsedWorkingPressure,
          workingPressureUnit = pressureUnit,
          fluidType = fluidType,
          elevationMeters = elevationMeters,
          atmosphericPressureKpa = calculatedAtmPressureKpa,
          solenoidStartMinutes = startTotalMinutes,
          solenoidEndMinutes = endTotalMinutes,
          startingBps = parsedBps,
          dangerThresholdPercent = parsedDangerPct.coerceIn(0.0, 95.0),
          notificationLeadTimeDays = parsedLeadDays.coerceIn(0, 365),
          setupTimestampMillis = if (isEditSettingsMode && currentConfig != null) {
            currentConfig.setupTimestampMillis
          } else {
            System.currentTimeMillis()
          },
          bottleStatus = if (isAddBottleMode) bottleStatus else (currentConfig?.bottleStatus ?: BottleStatus.ACTIVE),
          isSelected = true,
          pausedSinceMillis = if (isEditSettingsMode) currentConfig?.pausedSinceMillis else null,
          carriedGramsPerBubble = when {
            isEditSettingsMode -> currentConfig?.carriedGramsPerBubble
            canCarryCalibration && carryCalibration -> previousCalibratedCoeff
            else -> null
          },
          refillShopDaysMask = shopDaysMask,
          refillShopName = shopNameText.trim(),
          lastRefillCost = parsedCost
        )
      } else {
        null
      }
    }

    // Sticky Footer Action Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = InkSecondary)
      ) {
        Text(text = "Cancel", style = AquaTextStyles.bodySm)
      }

      Button(
        onClick = { buildConfig()?.let(onConfirm) },
        enabled = isValid,
        modifier = Modifier.weight(1.5f),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = PearlEmerald,
          contentColor = OnPearlEmerald,
          disabledContainerColor = GlassWell,
          disabledContentColor = InkTertiary
        )
      ) {
        Text(
          text = when {
            isAddBottleMode -> "Add Bottle"
            isEditSettingsMode -> "Save Settings"
            else -> "Initialize Cylinder"
          },
          style = AquaTextStyles.bodySm,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun ChecklistRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCheckedChange(!checked) },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = CheckboxDefaults.colors(
        checkedColor = PearlEmerald,
        checkmarkColor = OnPearlEmerald,
        uncheckedColor = StarphireRimBright
      )
    )
    Text(
      text = label,
      style = AquaTextStyles.caption,
      color = if (checked) InkPrimary else InkSecondary
    )
  }
}
