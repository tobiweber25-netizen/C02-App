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
import java.util.Locale

@Composable
fun DowntimeOverrideDialog(
  projection: CylinderProjection,
  onDismiss: () -> Unit,
  onConfirm: (hoursDelta: Double, note: String) -> Unit
) {
  var hoursText by remember { mutableStateOf("") }
  var noteText by remember { mutableStateOf("") }

  val parsedHours = Co2PhysicsEngine.parseDecimalInput(hoursText)
  val hoursDelta = parsedHours ?: 0.0
  val isHoursValid = parsedHours != null && parsedHours != 0.0
  val gramEquivalent = hoursDelta * projection.hourlyBurnRateGrams

  AquaModalSheet(
    title = "Log Downtime Override",
    subtitle = "Use negative hours (-4.0) if CO2 was off for maintenance or outages, or positive for extra runtime.",
    iconType = AquaIconType.DOWNTIME_PAUSE,
    accentColor = DropCheckerAmber,
    onDismiss = onDismiss
  ) {
    // Quick Outage / Maintenance Presets
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      val presets = listOf(
        Triple("-2 hrs", "-2.0", "Maintenance / water change"),
        Triple("-4 hrs", "-4.0", "Power outage / solenoid off"),
        Triple("-8 hrs", "-8.0", "Full day outage")
      )
      presets.forEach { (label, value, defaultNote) ->
        FilledTonalButton(
          onClick = {
            hoursText = value
            noteText = defaultNote
          },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = GlassWell,
            contentColor = DropCheckerAmber
          )
        ) {
          Text(text = label, style = AquaTextStyles.monoBody, fontWeight = FontWeight.SemiBold)
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    OutlinedTextField(
      value = hoursText,
      onValueChange = { hoursText = it },
      label = { Text("Hours Delta (- off, + extra on)") },
      placeholder = { Text("e.g. -4.0") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
      singleLine = true,
      shape = RoundedCornerShape(Radius.md),
      colors = aquaTextFieldColors(DropCheckerAmber),
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(Spacing.sm))

    OutlinedTextField(
      value = noteText,
      onValueChange = { noteText = it },
      label = { Text("Reason / Note") },
      placeholder = { Text("e.g. Aquascape trim & water change") },
      singleLine = true,
      shape = RoundedCornerShape(Radius.md),
      colors = aquaTextFieldColors(DropCheckerAmber),
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      borderColor = DropCheckerAmber.copy(alpha = AlphaTokens.rimSoft),
      contentPadding = PaddingValues(Spacing.md)
    ) {
      Column {
        Text(
          text = "CO2 MASS OFFSET",
          style = AquaTextStyles.monoMicro,
          color = InkTertiary
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        val effectDesc = if (hoursDelta <= 0.0) {
          String.format(Locale.US, "Saved ~%.1fg (extends refill window)", -gramEquivalent)
        } else {
          String.format(Locale.US, "Consumed extra ~%.1fg", gramEquivalent)
        }
        Text(
          text = String.format(Locale.US, "%.2f hrs  •  %.2fg CO2", hoursDelta, gramEquivalent),
          style = AquaTextStyles.monoBody,
          fontWeight = FontWeight.SemiBold,
          color = DropCheckerAmber
        )
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = effectDesc,
          style = AquaTextStyles.caption,
          color = InkSecondary
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
        onClick = {
          val h = parsedHours
          if (h != null && h != 0.0) onConfirm(h, noteText.trim())
        },
        enabled = isHoursValid,
        modifier = Modifier.weight(1.4f),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = DropCheckerAmber,
          contentColor = OnDropCheckerWarning,
          disabledContainerColor = GlassWell,
          disabledContentColor = InkTertiary
        )
      ) {
        Text(
          text = "Log Override",
          style = AquaTextStyles.bodySm,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
