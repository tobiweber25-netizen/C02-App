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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aquarium.co2tracker.domain.*
import com.aquarium.co2tracker.ui.theme.*

@Composable
fun InitialCylinderSetupDialog(
  currentConfig: CylinderConfig? = null,
  onDismiss: () -> Unit,
  onConfirm: (CylinderConfig) -> Unit
) {
  var tareWeightText by remember {
    mutableStateOf(if (currentConfig != null && currentConfig.tareWeightGrams > 0.0) currentConfig.tareWeightGrams.toInt().toString() else "")
  }
  var fullGrossText by remember {
    mutableStateOf(if (currentConfig != null && currentConfig.fullGrossWeightGrams > 0.0) currentConfig.fullGrossWeightGrams.toInt().toString() else "")
  }
  var includeRegulator by remember { mutableStateOf(currentConfig?.includeRegulatorWeight ?: false) }
  var regulatorWeightText by remember {
    mutableStateOf(if (currentConfig != null && currentConfig.regulatorWeightGrams > 0.0) currentConfig.regulatorWeightGrams.toInt().toString() else "")
  }

  var workingPressureText by remember { mutableStateOf(currentConfig?.workingPressureValue?.toString() ?: "2.5") }
  var pressureUnit by remember { mutableStateOf(currentConfig?.workingPressureUnit ?: PressureUnit.BAR) }
  var fluidType by remember { mutableStateOf(currentConfig?.fluidType ?: FluidType.WATER) }
  var elevationText by remember {
    mutableStateOf(if (currentConfig != null) currentConfig.elevationMeters.toInt().toString() else "0")
  }

  val curStartH = (currentConfig?.solenoidStartMinutes ?: (10 * 60 + 30)) / 60
  val curStartM = (currentConfig?.solenoidStartMinutes ?: (10 * 60 + 30)) % 60
  val curEndH = (currentConfig?.solenoidEndMinutes ?: (18 * 60)) / 60
  val curEndM = (currentConfig?.solenoidEndMinutes ?: (18 * 60)) % 60

  var startHourText by remember { mutableStateOf(String.format("%02d", curStartH)) }
  var startMinText by remember { mutableStateOf(String.format("%02d", curStartM)) }
  var endHourText by remember { mutableStateOf(String.format("%02d", curEndH)) }
  var endMinText by remember { mutableStateOf(String.format("%02d", curEndM)) }

  var startingBpsText by remember { mutableStateOf((currentConfig?.startingBps ?: 1.5).toString()) }
  var dangerThresholdText by remember { mutableStateOf((currentConfig?.dangerThresholdPercent ?: 10.0).toInt().toString()) }
  var leadTimeDaysText by remember { mutableStateOf((currentConfig?.notificationLeadTimeDays ?: 5).toString()) }

  val tareGrams = tareWeightText.toDoubleOrNull()
  val grossGrams = fullGrossText.toDoubleOrNull()
  val regGrams = if (includeRegulator) (regulatorWeightText.toDoubleOrNull() ?: 0.0) else 0.0
  val effectiveTare = (tareGrams ?: 0.0) + regGrams
  val isTareValid = tareGrams != null && tareGrams > 0.0
  val isGrossValid = grossGrams != null && grossGrams > effectiveTare
  val isValid = isTareValid && isGrossValid
  val calculatedNetCo2 = if (isValid && grossGrams != null) (grossGrams - effectiveTare).coerceAtLeast(0.0) else 0.0

  val elevationMeters = elevationText.toDoubleOrNull() ?: 0.0
  val calculatedAtmPressureKpa = Co2PhysicsEngine.calculateAtmosphericPressureKpa(elevationMeters)

  val sH = (startHourText.toIntOrNull() ?: curStartH).coerceIn(0, 23)
  val sM = (startMinText.toIntOrNull() ?: curStartM).coerceIn(0, 59)
  val eH = (endHourText.toIntOrNull() ?: curEndH).coerceIn(0, 23)
  val eM = (endMinText.toIntOrNull() ?: curEndM).coerceIn(0, 59)
  val startTotalMinutes = sH * 60 + sM
  val endTotalMinutes = eH * 60 + eM
  val activeHoursPerDay = Co2PhysicsEngine.calculateActiveSolenoidHoursPerDay(startTotalMinutes, endTotalMinutes)

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.92f)
        .clip(RoundedCornerShape(Radius.lg))
        .background(DarkSurfaceCard)
        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg))
        .padding(Spacing.cardPadding)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = "Cylinder Setup / New Refill",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
          text = "Initialize cylinder refill parameters, physics calibration baseline, and alert thresholds.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // 1. Tare & Gross weights
        Text(text = "BOTTLE WEIGHT & GAS MASS", fontSize = FontSizeTokens.caption, fontWeight = FontWeight.Bold, color = NeonCyanPrimary)
        Spacer(modifier = Modifier.height(Spacing.xs))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          OutlinedTextField(
            value = tareWeightText,
            onValueChange = { tareWeightText = it },
            label = { Text("Tare Weight (g)") },
            placeholder = { Text("e.g. 3350") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = tareWeightText.isNotBlank() && !isTareValid,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = fullGrossText,
            onValueChange = { fullGrossText = it },
            label = { Text("Full Gross (g)") },
            placeholder = { Text("e.g. 4850") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = fullGrossText.isNotBlank() && !isGrossValid,
            modifier = Modifier.weight(1f)
          )
        }

        if (tareWeightText.isNotBlank() && !isTareValid) {
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = "Tare weight must be greater than 0g",
            color = StatusCritical,
            fontSize = FontSizeTokens.caption
          )
        } else if (fullGrossText.isNotBlank() && !isGrossValid) {
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = "Full gross weight must exceed tare (${effectiveTare.toInt()}g)",
            color = StatusCritical,
            fontSize = FontSizeTokens.caption
          )
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Regulator Weight Toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = includeRegulator,
            onCheckedChange = { includeRegulator = it }
          )
          Text(
            text = "Bottle weighed with regulator attached",
            fontSize = FontSizeTokens.bodySm,
            color = TextPrimaryDark
          )
        }

        if (includeRegulator) {
          OutlinedTextField(
            value = regulatorWeightText,
            onValueChange = { regulatorWeightText = it },
            label = { Text("Regulator Weight (grams)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(Spacing.xs))
        }

        // Auto-calculated Net CO2 Banner
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(DarkSurfaceCardElevated)
            .border(1.dp, NeonCyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(Radius.sm))
            .padding(Spacing.sm)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(text = "Calculated Net CO2 Mass:", fontSize = FontSizeTokens.bodySm, color = TextSecondaryDark)
            Text(
              text = "${calculatedNetCo2.toInt()}g CO2",
              fontSize = FontSizeTokens.body,
              fontWeight = FontWeight.ExtraBold,
              color = NeonCyanPrimary
            )
          }
        }

        Spacer(modifier = Modifier.height(Spacing.normal))

        // 2. Pressure & Fluid
        Text(text = "PRESSURE & BUBBLE FLUID", fontSize = FontSizeTokens.caption, fontWeight = FontWeight.Bold, color = NeonCyanPrimary)
        Spacer(modifier = Modifier.height(Spacing.xs))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = workingPressureText,
            onValueChange = { workingPressureText = it },
            label = { Text("Working Pressure") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.2f)
          )

          // Bar / PSI Toggle buttons
          Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            FilterChip(
              selected = pressureUnit == PressureUnit.BAR,
              onClick = { pressureUnit = PressureUnit.BAR },
              label = { Text("Bar") }
            )
            FilterChip(
              selected = pressureUnit == PressureUnit.PSI,
              onClick = { pressureUnit = PressureUnit.PSI },
              label = { Text("PSI") }
            )
          }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Bubble Counter Fluid Type Dropdown / Toggle
        Text(text = "Bubble Counter Fluid Type:", fontSize = FontSizeTokens.caption, color = TextSecondaryDark)
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          FilterChip(
            selected = fluidType == FluidType.WATER,
            onClick = { fluidType = FluidType.WATER },
            label = { Text(FluidType.WATER.displayName) },
            modifier = Modifier.weight(1f)
          )
          FilterChip(
            selected = fluidType == FluidType.MINERAL_OIL,
            onClick = { fluidType = FluidType.MINERAL_OIL },
            label = { Text(FluidType.MINERAL_OIL.displayName) },
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(Spacing.normal))

        // 3. Altitude / Atmospheric Baseline
        Text(text = "ATMOSPHERIC BASELINE", fontSize = FontSizeTokens.caption, fontWeight = FontWeight.Bold, color = NeonCyanPrimary)
        Spacer(modifier = Modifier.height(Spacing.xs))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = elevationText,
            onValueChange = { elevationText = it },
            label = { Text("Elevation (meters)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )

          FilledTonalButton(
            onClick = { elevationText = "0" },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceCardElevated, contentColor = NeonCyanPrimary)
          ) {
            Text("Sea (0m)")
          }

          FilledTonalButton(
            onClick = { elevationText = "1700" },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceCardElevated, contentColor = NeonCyanPrimary)
          ) {
            Text("Alt (1700m)")
          }
        }
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = "Calculated atmospheric pressure: $calculatedAtmPressureKpa kPa (~${String.format("%.2f", calculatedAtmPressureKpa / 100.0)} Bar)",
          fontSize = FontSizeTokens.caption,
          color = TextTertiaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // 4. Daily Solenoid Schedule & BPS
        Text(text = "SOLENOID SCHEDULE & BUBBLE RATE", fontSize = FontSizeTokens.caption, fontWeight = FontWeight.Bold, color = NeonCyanPrimary)
        Spacer(modifier = Modifier.height(Spacing.xs))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = startHourText,
            onValueChange = { if (it.length <= 2) startHourText = it },
            label = { Text("ON (Hr)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = startMinText,
            onValueChange = { if (it.length <= 2) startMinText = it },
            label = { Text("ON (Min)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = endHourText,
            onValueChange = { if (it.length <= 2) endHourText = it },
            label = { Text("OFF (Hr)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = endMinText,
            onValueChange = { if (it.length <= 2) endMinText = it },
            label = { Text("OFF (Min)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
        }
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = "Active solenoid runtime: ${String.format("%.1f", activeHoursPerDay)} hours/day",
          fontSize = FontSizeTokens.caption,
          color = AquamarineSecondary
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        OutlinedTextField(
          value = startingBpsText,
          onValueChange = { startingBpsText = it },
          label = { Text("Starting Bubble Rate (BPS)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // 5. Safety Danger Threshold & Lead Time Days
        Text(text = "SAFETY ALERT THRESHOLDS", fontSize = FontSizeTokens.caption, fontWeight = FontWeight.Bold, color = NeonCyanPrimary)
        Spacer(modifier = Modifier.height(Spacing.xs))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          OutlinedTextField(
            value = dangerThresholdText,
            onValueChange = { dangerThresholdText = it },
            label = { Text("Danger Cutoff (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = leadTimeDaysText,
            onValueChange = { leadTimeDaysText = it },
            label = { Text("Alert Lead (Days)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextSecondaryDark)
          }

          Spacer(modifier = Modifier.width(Spacing.sm))

          Button(
            onClick = {
              val finalTare = tareGrams
              val finalGross = grossGrams
              if (isValid && finalTare != null && finalGross != null) {
                val newCfg = CylinderConfig(
                  id = currentConfig?.id ?: 1L,
                  cylinderName = "Primary CO2 Cylinder",
                  tareWeightGrams = finalTare,
                  fullGrossWeightGrams = finalGross,
                  includeRegulatorWeight = includeRegulator,
                  regulatorWeightGrams = regGrams,
                  workingPressureValue = workingPressureText.toDoubleOrNull() ?: 2.5,
                  workingPressureUnit = pressureUnit,
                  fluidType = fluidType,
                  elevationMeters = elevationMeters,
                  atmosphericPressureKpa = calculatedAtmPressureKpa,
                  solenoidStartMinutes = startTotalMinutes,
                  solenoidEndMinutes = endTotalMinutes,
                  startingBps = startingBpsText.toDoubleOrNull() ?: 1.5,
                  dangerThresholdPercent = dangerThresholdText.toDoubleOrNull() ?: 10.0,
                  notificationLeadTimeDays = leadTimeDaysText.toIntOrNull() ?: 5,
                  setupTimestampMillis = System.currentTimeMillis()
                )
                onConfirm(newCfg)
              }
            },
            enabled = isValid,
            colors = ButtonDefaults.buttonColors(
              containerColor = NeonCyanPrimary,
              contentColor = NeonCyanOnPrimary,
              disabledContainerColor = DarkSurfaceCardElevated,
              disabledContentColor = TextTertiaryDark
            )
          ) {
            Text("Initialize Cylinder", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
