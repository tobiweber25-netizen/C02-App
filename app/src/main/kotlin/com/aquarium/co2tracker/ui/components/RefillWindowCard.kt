package com.aquarium.co2tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RefillWindowCard(
  projection: CylinderProjection,
  modifier: Modifier = Modifier
) {
  val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
  val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

  val depletionDateStr = dateFormatter.format(Date(projection.depletionTimestampMillis))
  val alertDateStr = dateFormatter.format(Date(projection.alertTimestampMillis))
  val alertTimeStr = timeFormatter.format(Date(projection.alertTimestampMillis))

  val daysToRefill = projection.daysUntilRefillWindow
  val daysToDepletion = projection.daysUntilDangerThreshold

  val (badgeBg, badgeBorder, badgeText, badgeSubtext) = when {
    projection.isBelowDangerThreshold -> {
      Quad(
        StatusCritical.copy(alpha = 0.2f),
        StatusCritical,
        "DANGER: CYLINDER DEPLETED",
        "CO2 is below the ${projection.config.dangerThresholdPercent.toInt()}% safety margin (${projection.config.dangerThresholdGrams.toInt()}g)"
      )
    }
    daysToRefill <= 0.0 -> {
      val daysLeftStr = if (daysToDepletion < 1.0) "< 1 day" else "${daysToDepletion.toInt()} days"
      Quad(
        StatusWarning.copy(alpha = 0.2f),
        StatusWarning,
        "REFILL WINDOW OPEN NOW",
        "$daysLeftStr until empty. Alarm armed."
      )
    }
    else -> {
      val days = daysToRefill.toInt()
      Quad(
        NeonCyanPrimary.copy(alpha = 0.12f),
        NeonCyanPrimary.copy(alpha = 0.4f),
        "$days Days to Refill Window",
        "Alert will sound ${projection.config.notificationLeadTimeDays} days before tank reaches ${projection.config.dangerThresholdPercent.toInt()}% cutoff"
      )
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.lg))
      .background(DarkSurfaceCard)
      .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg))
      .padding(Spacing.cardPadding)
  ) {
    // Primary Badge
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.md))
        .background(badgeBg)
        .border(1.dp, badgeBorder, RoundedCornerShape(Radius.md))
        .padding(Spacing.md),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = badgeText,
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = badgeBorder
        )
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = badgeSubtext,
          fontSize = FontSizeTokens.caption,
          color = TextPrimaryDark
        )
      }
    }

    Spacer(modifier = Modifier.height(Spacing.normal))

    // Key Projection Dates Grid
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Depletion Date Column
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Depletion Cutoff Date",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = depletionDateStr,
          fontSize = FontSizeTokens.body,
          fontWeight = FontWeight.SemiBold,
          color = TextPrimaryDark
        )
        Text(
          text = "At ${projection.config.dangerThresholdPercent.toInt()}% (${projection.config.dangerThresholdGrams.toInt()}g net)",
          fontSize = FontSizeTokens.caption,
          color = TextTertiaryDark
        )
      }

      Spacer(modifier = Modifier.width(Spacing.md))

      // Exact Alert Date Column
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Exact Alert Alarm",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = alertDateStr,
          fontSize = FontSizeTokens.body,
          fontWeight = FontWeight.SemiBold,
          color = NeonCyanPrimary
        )
        Text(
          text = "At $alertTimeStr (${projection.config.notificationLeadTimeDays}d lead time)",
          fontSize = FontSizeTokens.caption,
          color = TextTertiaryDark
        )
      }
    }
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
