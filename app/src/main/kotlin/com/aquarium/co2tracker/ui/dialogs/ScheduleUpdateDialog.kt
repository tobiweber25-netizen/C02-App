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
fun ScheduleUpdateDialog(
  projection: CylinderProjection,
  onDismiss: () -> Unit,
  onConfirm: (startMin: Int, endMin: Int) -> Unit
) {
  val curStartH = projection.currentStartMinutes / 60
  val curStartM = projection.currentStartMinutes % 60
  val curEndH = projection.currentEndMinutes / 60
  val curEndM = projection.currentEndMinutes % 60

  var startHourText by remember { mutableStateOf(String.format("%02d", curStartH)) }
  var startMinText by remember { mutableStateOf(String.format("%02d", curStartM)) }
  var endHourText by remember { mutableStateOf(String.format("%02d", curEndH)) }
  var endMinText by remember { mutableStateOf(String.format("%02d", curEndM)) }

  val sH = (startHourText.toIntOrNull() ?: curStartH).coerceIn(0, 23)
  val sM = (startMinText.toIntOrNull() ?: curStartM).coerceIn(0, 59)
  val eH = (endHourText.toIntOrNull() ?: curEndH).coerceIn(0, 23)
  val eM = (endMinText.toIntOrNull() ?: curEndM).coerceIn(0, 59)

  val startTotalMinutes = sH * 60 + sM
  val endTotalMinutes = eH * 60 + eM
  val previewActiveHours = Co2PhysicsEngine.calculateActiveSolenoidHoursPerDay(startTotalMinutes, endTotalMinutes)

  val previewDailyBurn = Co2PhysicsEngine.calculateDailyBurnRateGrams(
    bps = projection.currentBps,
    gramsPerBubbleCoefficient = projection.activeGramsPerBubble,
    activeSolenoidHoursPerDay = previewActiveHours
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
          text = "Change Solenoid Schedule",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
          text = "Past consumption will be banked. Future depletion rate will recalculate based on new daily runtime.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // Solenoid Start Time
        Text(
          text = "Solenoid ON Time (24h)",
          fontSize = FontSizeTokens.caption,
          fontWeight = FontWeight.SemiBold,
          color = TextSecondaryDark,
          modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = startHourText,
            onValueChange = { if (it.length <= 2) startHourText = it },
            label = { Text("Hour (0-23)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
          Text(text = ":", fontSize = FontSizeTokens.title, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
          OutlinedTextField(
            value = startMinText,
            onValueChange = { if (it.length <= 2) startMinText = it },
            label = { Text("Minute (0-59)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Solenoid End Time
        Text(
          text = "Solenoid OFF Time (24h)",
          fontSize = FontSizeTokens.caption,
          fontWeight = FontWeight.SemiBold,
          color = TextSecondaryDark,
          modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = endHourText,
            onValueChange = { if (it.length <= 2) endHourText = it },
            label = { Text("Hour (0-23)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
          Text(text = ":", fontSize = FontSizeTokens.title, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
          OutlinedTextField(
            value = endMinText,
            onValueChange = { if (it.length <= 2) endMinText = it },
            label = { Text("Minute (0-59)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Active summary
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(DarkSurfaceCardElevated)
            .padding(Spacing.md)
        ) {
          Column {
            Text(
              text = "Calculated Daily Runtime:",
              fontSize = FontSizeTokens.caption,
              color = TextSecondaryDark
            )
            Text(
              text = String.format("%.2f hours / day  (Burn: %.2f g/day)", previewActiveHours, previewDailyBurn),
              fontSize = FontSizeTokens.bodySm,
              fontWeight = FontWeight.Bold,
              color = AquamarineSecondary
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
              onConfirm(startTotalMinutes, endTotalMinutes)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AquamarineSecondary, contentColor = AquamarineOnSecondary)
          ) {
            Text("Save & Recalculate", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
