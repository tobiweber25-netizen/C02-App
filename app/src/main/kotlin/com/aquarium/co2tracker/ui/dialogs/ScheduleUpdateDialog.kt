package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.aquarium.co2tracker.domain.Co2PhysicsEngine
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*
import java.util.Locale

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

  var startHourText by remember { mutableStateOf(String.format(Locale.US, "%02d", curStartH)) }
  var startMinText by remember { mutableStateOf(String.format(Locale.US, "%02d", curStartM)) }
  var endHourText by remember { mutableStateOf(String.format(Locale.US, "%02d", curEndH)) }
  var endMinText by remember { mutableStateOf(String.format(Locale.US, "%02d", curEndM)) }

  val parsedSH = startHourText.trim().toIntOrNull()
  val parsedSM = startMinText.trim().toIntOrNull()
  val parsedEH = endHourText.trim().toIntOrNull()
  val parsedEM = endMinText.trim().toIntOrNull()
  val isTimeValid =
    parsedSH != null && parsedSH in 0..23 &&
      parsedSM != null && parsedSM in 0..59 &&
      parsedEH != null && parsedEH in 0..23 &&
      parsedEM != null && parsedEM in 0..59

  val sH = (parsedSH ?: curStartH).coerceIn(0, 23)
  val sM = (parsedSM ?: curStartM).coerceIn(0, 59)
  val eH = (parsedEH ?: curEndH).coerceIn(0, 23)
  val eM = (parsedEM ?: curEndM).coerceIn(0, 59)

  val startTotalMinutes = sH * 60 + sM
  val endTotalMinutes = eH * 60 + eM
  val previewActiveHours = Co2PhysicsEngine.calculateActiveSolenoidHoursPerDay(startTotalMinutes, endTotalMinutes)
  val previewDailyBurn = Co2PhysicsEngine.calculateDailyBurnRateGrams(
    bps = projection.currentBps,
    gramsPerBubbleCoefficient = projection.activeGramsPerBubble,
    activeSolenoidHoursPerDay = previewActiveHours
  )

  AquaModalSheet(
    title = "Solenoid Photoperiod",
    subtitle = "Past gas consumption is banked. Future depletion updates with the new daily window.",
    iconType = AquaIconType.PHOTOPERIOD_ARC,
    accentColor = StarphireCyan,
    onDismiss = onDismiss
  ) {
    Text(
      text = "SOLENOID ON TIME (24H)",
      style = AquaTextStyles.monoMicro,
      color = InkTertiary
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
        singleLine = true,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(StarphireCyan),
        modifier = Modifier.weight(1f)
      )
      Text(text = ":", style = AquaTextStyles.displayNumber, color = InkSecondary)
      OutlinedTextField(
        value = startMinText,
        onValueChange = { if (it.length <= 2) startMinText = it },
        label = { Text("Min (0-59)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(StarphireCyan),
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    Text(
      text = "SOLENOID OFF TIME (24H)",
      style = AquaTextStyles.monoMicro,
      color = InkTertiary
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
        singleLine = true,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(StarphireCyan),
        modifier = Modifier.weight(1f)
      )
      Text(text = ":", style = AquaTextStyles.displayNumber, color = InkSecondary)
      OutlinedTextField(
        value = endMinText,
        onValueChange = { if (it.length <= 2) endMinText = it },
        label = { Text("Min (0-59)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(Radius.md),
        colors = aquaTextFieldColors(StarphireCyan),
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    // Live 24-Hour Photoperiod Visual Preview
    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      borderColor = StarphireCyan.copy(alpha = AlphaTokens.rimSoft),
      contentPadding = PaddingValues(Spacing.md)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = String.format(Locale.US, "%.2f hrs / day", previewActiveHours),
            style = AquaTextStyles.monoBody,
            fontWeight = FontWeight.SemiBold,
            color = StarphireCyan
          )
          Text(
            text = String.format(Locale.US, "Burn %.2f g/day", previewDailyBurn),
            style = AquaTextStyles.monoCaption,
            color = InkSecondary
          )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        PhotoperiodTimelineBar(
          startMinutes = startTotalMinutes,
          endMinutes = endTotalMinutes,
          nowMinuteOfDay = currentLocalMinuteOfDay()
        )
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
        onClick = { onConfirm(startTotalMinutes, endTotalMinutes) },
        enabled = isTimeValid,
        modifier = Modifier.weight(1.4f),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = StarphireCyan,
          contentColor = OnStarphireCyan,
          disabledContainerColor = GlassWell,
          disabledContentColor = InkTertiary
        )
      ) {
        Text(
          text = "Save & Recalculate",
          style = AquaTextStyles.bodySm,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
