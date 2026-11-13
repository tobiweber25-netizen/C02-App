package com.aquarium.co2tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aquarium.co2tracker.ui.theme.*

@Composable
fun QuickActionsRow(
  onBpsClick: () -> Unit,
  onScheduleClick: () -> Unit,
  onDowntimeClick: () -> Unit,
  onScaleClick: () -> Unit,
  onRefillResetClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
  ) {
    Text(
      text = "Quick Actions (Immutable Ledger)",
      fontSize = FontSizeTokens.body,
      fontWeight = FontWeight.SemiBold,
      color = TextPrimaryDark
    )

    // 2x2 Action Buttons Grid
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      QuickActionButton(
        iconText = "⏱",
        title = "± BPS Rate",
        subtitle = "Change bubble flow",
        accentColor = NeonCyanPrimary,
        onClick = onBpsClick,
        modifier = Modifier.weight(1f)
      )

      QuickActionButton(
        iconText = "💡",
        title = "Solenoid Hours",
        subtitle = "Adjust active timer",
        accentColor = AquamarineSecondary,
        onClick = onScheduleClick,
        modifier = Modifier.weight(1f)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      QuickActionButton(
        iconText = "⏸",
        title = "Log Downtime",
        subtitle = "Outage / maintenance",
        accentColor = StatusWarning,
        onClick = onDowntimeClick,
        modifier = Modifier.weight(1f)
      )

      QuickActionButton(
        iconText = "⚖️",
        title = "Scale Check-In",
        subtitle = "Two-point calibration",
        accentColor = StatusCalibrated,
        onClick = onScaleClick,
        modifier = Modifier.weight(1f)
      )
    }

    // New Refill / Reset Full Bottle Banner
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.md))
        .background(DarkSurfaceCardElevated)
        .border(1.dp, NeonCyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(Radius.md))
        .clickable { onRefillResetClick() }
        .padding(Spacing.md),
      contentAlignment = Alignment.Center
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(
          text = "🔄  New Refill / Cylinder Reset",
          fontSize = FontSizeTokens.bodySm,
          fontWeight = FontWeight.Bold,
          color = NeonCyanPrimary
        )
      }
    }
  }
}

@Composable
private fun QuickActionButton(
  iconText: String,
  title: String,
  subtitle: String,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(Radius.md))
      .background(DarkSurfaceCard)
      .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.md))
      .clickable { onClick() }
      .padding(Spacing.md)
  ) {
    Column {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
      ) {
        Text(text = iconText, fontSize = FontSizeTokens.body)
        Text(
          text = title,
          fontSize = FontSizeTokens.bodySm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )
      }
      Spacer(modifier = Modifier.height(Spacing.xxs))
      Text(
        text = subtitle,
        fontSize = FontSizeTokens.caption,
        color = TextSecondaryDark
      )
    }
  }
}
