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
fun DowntimeOverrideDialog(
  projection: CylinderProjection,
  onDismiss: () -> Unit,
  onConfirm: (hoursDelta: Double, note: String) -> Unit
) {
  var hoursText by remember { mutableStateOf("") }
  var noteText by remember { mutableStateOf("") }

  val hoursDelta = hoursText.toDoubleOrNull() ?: 0.0
  val isHoursValid = hoursText.toDoubleOrNull() != null && hoursText.toDoubleOrNull() != 0.0
  val gramEquivalent = hoursDelta * projection.hourlyBurnRateGrams

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
          text = "Log Downtime / Runtime Override",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
          text = "Use negative values (e.g. -4.0h) if CO2 was switched off for tank maintenance or outages. Use positive for extra runtime.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // Quick presets
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          FilledTonalButton(
            onClick = {
              hoursText = "-2.0"
              noteText = "Maintenance / water change"
            },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceCardElevated, contentColor = StatusWarning)
          ) {
            Text("-2 hrs", fontWeight = FontWeight.Bold)
          }

          FilledTonalButton(
            onClick = {
              hoursText = "-4.0"
              noteText = "Power outage / Solenoid off"
            },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceCardElevated, contentColor = StatusWarning)
          ) {
            Text("-4 hrs", fontWeight = FontWeight.Bold)
          }

          FilledTonalButton(
            onClick = {
              hoursText = "-8.0"
              noteText = "Full day outage"
            },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceCardElevated, contentColor = StatusWarning)
          ) {
            Text("-8 hrs", fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        OutlinedTextField(
          value = hoursText,
          onValueChange = { hoursText = it },
          label = { Text("Hours Delta (- for off, + for on)") },
          placeholder = { Text("e.g. -4.0") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        OutlinedTextField(
          value = noteText,
          onValueChange = { noteText = it },
          label = { Text("Reason / Note") },
          placeholder = { Text("e.g. Tank maintenance") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        // Offset summary box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(DarkSurfaceCardElevated)
            .padding(Spacing.md)
        ) {
          Column {
            Text(
              text = "CO2 Offset Equivalent:",
              fontSize = FontSizeTokens.caption,
              color = TextSecondaryDark
            )
            val effectDesc = if (hoursDelta <= 0) "Saved: ~${(-gramEquivalent).toInt()}g (Depletion date pushed later)" else "Consumed extra: ~${gramEquivalent.toInt()}g"
            Text(
              text = String.format("%.2f hours = %.2fg CO2 (%s)", hoursDelta, gramEquivalent, effectDesc),
              fontSize = FontSizeTokens.bodySm,
              fontWeight = FontWeight.Bold,
              color = StatusWarning
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
              val h = hoursText.toDoubleOrNull()
              if (h != null && h != 0.0) {
                onConfirm(h, noteText.trim())
              }
            },
            enabled = isHoursValid,
            colors = ButtonDefaults.buttonColors(containerColor = StatusWarning, contentColor = CarbonBackground)
          ) {
            Text("Log Override", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
