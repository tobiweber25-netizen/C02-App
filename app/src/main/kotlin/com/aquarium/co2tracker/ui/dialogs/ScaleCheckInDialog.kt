package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.aquarium.co2tracker.domain.Co2PhysicsEngine
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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

  // Allow up to 50g below tare for real-world kitchen scale tolerance on an empty canister
  val minAllowedWeight = (tare - 50.0).coerceAtLeast(0.0)
  val inputWeight = Co2PhysicsEngine.parseDecimalInput(weightText)
  val isAboveMin = inputWeight != null && inputWeight >= minAllowedWeight
  val isNotExceedingGross = inputWeight != null && inputWeight <= fullGross
  val isValid = isAboveMin && isNotExceedingGross
  val isAtOrBelowTare = inputWeight != null && inputWeight <= tare

  val preview = remember(projection, inputWeight, isValid) {
    if (isValid) {
      Co2PhysicsEngine.previewScaleCheckIn(
        projection = projection,
        measuredScaleWeightGrams = inputWeight
      )
    } else {
      null
    }
  }

  val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

  AquaModalSheet(
    title = "Scale Check-In Calibration",
    subtitle = "Weigh your cylinder on a scale to snap remaining CO2 to reality and calibrate your real burn rate.",
    iconType = AquaIconType.SCALE_BALANCE,
    accentColor = DropCheckerOptimal,
    onDismiss = onDismiss
  ) {
    OutlinedTextField(
      value = weightText,
      onValueChange = { weightText = it },
      label = { Text("Current Scale Weight (grams)") },
      placeholder = { Text("Estimated ~${estimatedGross.roundToInt()}g") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      singleLine = true,
      isError = weightText.isNotBlank() && !isValid,
      shape = RoundedCornerShape(Radius.md),
      colors = aquaTextFieldColors(DropCheckerOptimal),
      modifier = Modifier.fillMaxWidth()
    )

    if (inputWeight != null && !isAboveMin) {
      Spacer(modifier = Modifier.height(Spacing.xs))
      Text(
        text = "Scale reading is far below tare weight (${tare.roundToInt()}g). Check whether regulator weight is included.",
        style = AquaTextStyles.caption,
        color = DropCheckerCoral
      )
    } else if (inputWeight != null && !isNotExceedingGross) {
      Spacer(modifier = Modifier.height(Spacing.xs))
      Text(
        text = "Scale weight cannot exceed full gross weight (${fullGross.roundToInt()}g).",
        style = AquaTextStyles.caption,
        color = DropCheckerAmber
      )
    } else if (isValid && isAtOrBelowTare) {
      Spacer(modifier = Modifier.height(Spacing.xs))
      Text(
        text = "At or below tare (${tare.roundToInt()}g) — will record 0g CO2 remaining (empty cylinder).",
        style = AquaTextStyles.caption,
        color = DropCheckerCoral
      )
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    val previewAccent = when {
      preview?.possibleLeakDetected == true -> DropCheckerCoral
      preview?.willRecalibrateRate == true -> DropCheckerOptimal
      preview != null -> DropCheckerAmber
      else -> InkTertiary
    }

    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      borderColor = if (preview != null) previewAccent.copy(alpha = AlphaTokens.rimMedium) else GlassWellBorder,
      contentPadding = PaddingValues(Spacing.md)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
          text = when {
            preview == null -> "ENTER SCALE READING TO PREVIEW"
            preview.possibleLeakDetected -> "CALIBRATION + POSSIBLE LEAK WARNING"
            preview.willRecalibrateRate -> "BURN RATE CALIBRATION LOCK"
            else -> "REMAINING WEIGHT SNAP ONLY"
          },
          style = AquaTextStyles.monoMicro,
          color = previewAccent
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(text = "Total CO2 Consumed", style = AquaTextStyles.caption, color = InkSecondary)
          Text(
            text = String.format(Locale.US, "%,dg", (preview?.exactConsumedGrams ?: 0.0).roundToInt()),
            style = AquaTextStyles.monoBody,
            color = InkPrimary
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(text = "Snap Remaining Net CO2", style = AquaTextStyles.caption, color = InkSecondary)
          Text(
            text = String.format(
              Locale.US,
              "%,dg / %,dg",
              (preview?.remainingAfterGrams ?: 0.0).roundToInt(),
              projection.config.initialNetCo2Grams.roundToInt()
            ),
            style = AquaTextStyles.monoBody,
            color = if (preview != null) previewAccent else InkSecondary
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(text = "Resulting Mass / Bubble", style = AquaTextStyles.caption, color = InkSecondary)
          Text(
            text = String.format(
              Locale.US,
              "%.5fg",
              preview?.resultingGramsPerBubble ?: projection.activeGramsPerBubble
            ),
            style = AquaTextStyles.monoCaption,
            color = if (preview?.willRecalibrateRate == true) PearlEmerald else InkTertiary
          )
        }

        if (preview != null && projection.hasActiveBurn && preview.remainingAfterGrams > 0.5) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = "Updated Empty Est. (0g)", style = AquaTextStyles.caption, color = InkSecondary)
            Text(
              text = dateFormatter.format(Date(preview.projectedEmptyMillis)),
              style = AquaTextStyles.monoCaption,
              color = InkPrimary
            )
          }
        }

        if (preview != null) {
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = if (preview.possibleLeakDetected) {
              "Warning: CO2 drop is much faster than normal for your BPS & pressure—check for slow leaks."
            } else {
              preview.feedbackMessage
            },
            style = AquaTextStyles.caption,
            color = if (preview.possibleLeakDetected) DropCheckerCoral else InkSecondary
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.xl))

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
        onClick = {
          if (isValid) onConfirm(inputWeight)
        },
        enabled = isValid,
        modifier = Modifier.weight(1.4f),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = DropCheckerOptimal,
          contentColor = OnPearlEmerald,
          disabledContainerColor = GlassWell,
          disabledContentColor = InkTertiary
        )
      ) {
        Text(
          text = if (preview?.willRecalibrateRate == true) "Calibrate & Lock" else "Save Weight",
          style = AquaTextStyles.bodySm,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
