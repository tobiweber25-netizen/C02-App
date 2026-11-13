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
import com.aquarium.co2tracker.domain.Co2PhysicsEngine
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*

@Composable
fun BpsUpdateDialog(
  projection: CylinderProjection,
  onDismiss: () -> Unit,
  onConfirm: (Double) -> Unit
) {
  var bpsText by remember { mutableStateOf(projection.currentBps.toString()) }
  val currentBps = bpsText.toDoubleOrNull() ?: projection.currentBps

  val previewDailyBurn = Co2PhysicsEngine.calculateDailyBurnRateGrams(
    bps = currentBps,
    gramsPerBubbleCoefficient = projection.activeGramsPerBubble,
    activeSolenoidHoursPerDay = projection.activeSolenoidHoursPerDay
  )

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
          text = "Update Bubble Rate (BPS)",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
          text = "Current: ${projection.currentBps} BPS. Gas used up to this exact minute will be banked in the ledger.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // Quick Stepper Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledTonalButton(
            onClick = {
              val cur = bpsText.toDoubleOrNull() ?: 1.5
              val next = (cur - 0.2).coerceAtLeast(0.1)
              bpsText = String.format("%.1f", next)
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = DarkSurfaceCardElevated,
              contentColor = NeonCyanPrimary
            )
          ) {
            Text("-0.2", fontWeight = FontWeight.Bold)
          }

          FilledTonalButton(
            onClick = {
              val cur = bpsText.toDoubleOrNull() ?: 1.5
              val next = (cur - 0.1).coerceAtLeast(0.1)
              bpsText = String.format("%.1f", next)
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = DarkSurfaceCardElevated,
              contentColor = NeonCyanPrimary
            )
          ) {
            Text("-0.1", fontWeight = FontWeight.Bold)
          }

          FilledTonalButton(
            onClick = {
              val cur = bpsText.toDoubleOrNull() ?: 1.5
              val next = cur + 0.1
              bpsText = String.format("%.1f", next)
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = DarkSurfaceCardElevated,
              contentColor = NeonCyanPrimary
            )
          ) {
            Text("+0.1", fontWeight = FontWeight.Bold)
          }

          FilledTonalButton(
            onClick = {
              val cur = bpsText.toDoubleOrNull() ?: 1.5
              val next = cur + 0.2
              bpsText = String.format("%.1f", next)
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = DarkSurfaceCardElevated,
              contentColor = NeonCyanPrimary
            )
          ) {
            Text("+0.2", fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        OutlinedTextField(
          value = bpsText,
          onValueChange = { bpsText = it },
          label = { Text("Bubbles Per Second (BPS)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimaryDark,
            unfocusedTextColor = TextPrimaryDark,
            focusedBorderColor = NeonCyanPrimary,
            unfocusedBorderColor = DarkSurfaceBorder,
            focusedLabelColor = NeonCyanPrimary,
            unfocusedLabelColor = TextSecondaryDark
          )
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        // Burn preview box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(DarkSurfaceCardElevated)
            .padding(Spacing.md)
        ) {
          Column {
            Text(
              text = "Projected Daily Burn:",
              fontSize = FontSizeTokens.caption,
              color = TextSecondaryDark
            )
            Text(
              text = String.format("%.2f g/day (Active: %.1f hrs/day)", previewDailyBurn, projection.activeSolenoidHoursPerDay),
              fontSize = FontSizeTokens.bodySm,
              fontWeight = FontWeight.Bold,
              color = NeonCyanPrimary
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
              val v = bpsText.toDoubleOrNull()
              if (v != null && v > 0.0) {
                onConfirm(v)
              }
            },
            enabled = (bpsText.toDoubleOrNull() ?: 0.0) > 0.0,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyanPrimary, contentColor = NeonCyanOnPrimary)
          ) {
            Text("Bank & Update", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
