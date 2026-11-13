package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*

@Composable
fun ScaleCheckInDialog(
  projection: CylinderProjection,
  onDismiss: () -> Unit,
  onConfirm: (scaleWeightGrams: Double) -> Unit
) {
  val tare = projection.config.effectiveTareWeightGrams
  val fullGross = projection.config.fullGrossWeightGrams
  val estimatedGross = tare + projection.remainingCo2Grams
  var weightText by remember { mutableStateOf("") }

  val inputWeight = weightText.toDoubleOrNull()
  val isGreaterThanTare = inputWeight != null && inputWeight > tare
  val isNotExceedingGross = inputWeight != null && inputWeight <= fullGross
  val isValid = isGreaterThanTare && isNotExceedingGross

  val exactConsumedGrams = if (isValid && inputWeight != null) {
    (fullGross - inputWeight).coerceIn(0.0, projection.config.initialNetCo2Grams)
  } else {
    0.0
  }

  val remainingSnapGrams = if (isValid && inputWeight != null) {
    (projection.config.initialNetCo2Grams - exactConsumedGrams).coerceIn(0.0, projection.config.initialNetCo2Grams)
  } else {
    0.0
  }

  val bubbles = projection.cumulativeBubblesFired.coerceAtLeast(100.0)
  val calibratedCoeff = if (exactConsumedGrams > 0.0 && bubbles > 0.0) {
    exactConsumedGrams / bubbles
  } else {
    projection.theoreticalGramsPerBubble
  }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.lg))
        .background(DarkSurfaceCard)
        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg))
        .padding(Spacing.cardPadding)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Scale Weight Check-In (Calibration)",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
          text = "Place bottle on a scale mid-cycle. Snaps remaining CO2 mass to reality and recalibrates grams-per-bubble coefficient.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        OutlinedTextField(
          value = weightText,
          onValueChange = { weightText = it },
          label = { Text("Current Scale Weight (grams)") },
          placeholder = { Text("e.g. ${estimatedGross.toInt()}") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          isError = weightText.isNotBlank() && !isValid,
          modifier = Modifier.fillMaxWidth()
        )

        if (inputWeight != null && !isGreaterThanTare) {
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = "Scale weight must be greater than tare weight (${tare.toInt()}g).",
            color = StatusCritical,
            fontSize = FontSizeTokens.caption,
            fontWeight = FontWeight.SemiBold
          )
        } else if (inputWeight != null && !isNotExceedingGross) {
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = "Scale weight cannot exceed initial full gross weight (${fullGross.toInt()}g).",
            color = StatusWarning,
            fontSize = FontSizeTokens.caption,
            fontWeight = FontWeight.SemiBold
          )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Calibration Preview Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(DarkSurfaceCardElevated)
            .border(
              1.dp,
              if (isValid) StatusCalibrated.copy(alpha = 0.3f) else DarkSurfaceBorder,
              RoundedCornerShape(Radius.sm)
            )
            .padding(Spacing.md)
        ) {
          Column {
            Text(
              text = if (isValid) "Two-Point Calibration Preview:" else "Two-Point Calibration Preview (Pending valid weight):",
              fontSize = FontSizeTokens.caption,
              fontWeight = FontWeight.Bold,
              color = if (isValid) StatusCalibrated else TextTertiaryDark
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
              text = "Exact Gas Consumed: ${exactConsumedGrams.toInt()}g",
              fontSize = FontSizeTokens.bodySm,
              color = TextPrimaryDark
            )
            Text(
              text = "Snap Remaining Net CO2: ${remainingSnapGrams.toInt()}g / ${projection.config.initialNetCo2Grams.toInt()}g",
              fontSize = FontSizeTokens.bodySm,
              color = TextPrimaryDark
            )
            Text(
              text = String.format("New Calibrated Coeff: %.6fg / bubble", calibratedCoeff),
              fontSize = FontSizeTokens.caption,
              color = if (isValid) StatusCalibrated else TextTertiaryDark
            )
            Text(
              text = "Cumulative Bubbles Fired: ${bubbles.toInt()}",
              fontSize = FontSizeTokens.caption,
              color = TextTertiaryDark
            )
          }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

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
              val w = inputWeight
              if (isValid && w != null) {
                onConfirm(w)
              }
            },
            enabled = isValid,
            colors = ButtonDefaults.buttonColors(
              containerColor = StatusCalibrated,
              contentColor = CarbonBackground,
              disabledContainerColor = DarkSurfaceCardElevated,
              disabledContentColor = TextTertiaryDark
            )
          ) {
            Text("Calibrate & Lock", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
