package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
  onDismiss: () -> Unit,
  onConfirm: (CylinderConfig) -> Unit
) {
  var cylinderNameText by remember {
    mutableStateOf(currentConfig?.cylinderName ?: "Primary CO2 Cylinder")
  }
  var tareWeightText by remember {
    mutableStateOf(if (currentConfig != null && currentConfig.tareWeightGrams > 0.0) currentConfig.tareWeightGrams.roundToInt().toString() else "")
  }
  var fullGrossText by remember {
    mutableStateOf(if (currentConfig != null && currentConfig.fullGrossWeightGrams > 0.0) currentConfig.fullGrossWeightGrams.roundToInt().toString() else "")
  }
  var includeRegulator by remember { mutableStateOf(currentConfig?.includeRegulatorWeight ?: false) }
  var regulatorWeightText by remember {
    mutableStateOf(if (currentConfig != null && currentConfig.regulatorWeightGrams > 0.0) currentConfig.regulatorWeightGrams.roundToInt().toString() else "")
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

  val isTareValid = tareGrams != null && tareGrams > 0.0
  val isGrossValid = grossGrams != null && grossGrams > effectiveTare
  val isValid = isTareValid && isGrossValid && isRegValid && isPressureValid && isBpsValid && isDangerValid && isLeadValid
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

  AquaModalSheet(
    title = if (isEditSettingsMode) "Edit Cylinder Settings" else "Cylinder Setup & Refill",
    subtitle = if (isEditSettingsMode) {
      "Update weights, pressure, elevation, or alert margins while keeping your ledger history intact."
    } else {
      "Configure bottle weights, gas baseline, daily solenoid window, and refill thresholds."
    },
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
          text = "Full gross weight must exceed tare (${effectiveTare.toInt()}g).",
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
            text = String.format(Locale.US, "%,dg CO2", calculatedNetCo2.toInt()),
            style = AquaTextStyles.monoLarge,
            color = PearlEmerald
          )
        }
      }

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
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

      // 5. Safety Alert Thresholds
      AquaSectionHeader(title = "Safety Alert Thresholds")

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

      Spacer(modifier = Modifier.height(Spacing.sm))
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    val buildConfig: () -> CylinderConfig? = {
      if (isValid) {
        val cleanName = cylinderNameText.trim().ifBlank { "Primary CO2 Cylinder" }
        CylinderConfig(
          id = currentConfig?.id ?: 1L,
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
          }
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
          text = if (isEditSettingsMode) "Save Settings" else "Initialize Cylinder",
          style = AquaTextStyles.bodySm,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
