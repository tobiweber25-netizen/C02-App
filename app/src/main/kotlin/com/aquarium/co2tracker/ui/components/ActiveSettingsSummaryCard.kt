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

@Composable
fun ActiveSettingsSummaryCard(
  projection: CylinderProjection,
  modifier: Modifier = Modifier
) {
  val startH = projection.currentStartMinutes / 60
  val startM = projection.currentStartMinutes % 60
  val endH = projection.currentEndMinutes / 60
  val endM = projection.currentEndMinutes % 60
  val scheduleStr = String.format("%02d:%02d - %02d:%02d", startH, startM, endH, endM)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.lg))
      .background(DarkSurfaceCard)
      .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg))
      .padding(Spacing.cardPadding)
  ) {
    Text(
      text = "Active Physics & Burn Settings",
      fontSize = FontSizeTokens.body,
      fontWeight = FontWeight.SemiBold,
      color = TextPrimaryDark
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      MetricTile(
        title = "BUBBLE RATE",
        value = "${projection.currentBps} BPS",
        subtext = String.format("%.5fg / bubble", projection.activeGramsPerBubble),
        modifier = Modifier.weight(1f)
      )

      MetricTile(
        title = "DAILY SOLENOID",
        value = String.format("%.1f hrs/d", projection.activeSolenoidHoursPerDay),
        subtext = scheduleStr,
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      MetricTile(
        title = "DAILY BURN RATE",
        value = String.format("%.2f g/day", projection.dailyBurnRateGrams),
        subtext = String.format("%.2f g/hr active", projection.hourlyBurnRateGrams),
        modifier = Modifier.weight(1f)
      )

      val calibTitle = if (projection.isScaleCalibrated) "CALIBRATED COEFF" else "THEORETICAL COEFF"
      val calibDesc = if (projection.isScaleCalibrated) "Two-point scale locked" else "Ideal Gas Law @ ${projection.config.atmosphericPressureKpa.toInt()}kPa"
      val calibColor = if (projection.isScaleCalibrated) StatusCalibrated else StatusTheoretical

      MetricTile(
        title = calibTitle,
        value = if (projection.isScaleCalibrated) "Scale Verified" else "Standard Gas Law",
        subtext = calibDesc,
        accentColor = calibColor,
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    // Atmospheric & Fluid Details Line
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.sm))
        .background(DarkSurfaceCardElevated)
        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Elevation: ${projection.config.elevationMeters.toInt()}m (${projection.config.atmosphericPressureKpa} kPa)",
        fontSize = FontSizeTokens.caption,
        color = TextSecondaryDark
      )
      Text(
        text = "${projection.config.workingPressureValue} ${projection.config.workingPressureUnit.symbol} • ${projection.config.fluidType.displayName}",
        fontSize = FontSizeTokens.caption,
        color = TextSecondaryDark
      )
    }
  }
}

@Composable
private fun MetricTile(
  title: String,
  value: String,
  subtext: String,
  modifier: Modifier = Modifier,
  accentColor: androidx.compose.ui.graphics.Color = NeonCyanPrimary
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(Radius.md))
      .background(DarkSurfaceCardElevated)
      .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.md))
      .padding(Spacing.md)
  ) {
    Column {
      Text(
        text = title,
        fontSize = FontSizeTokens.caption,
        fontWeight = FontWeight.Bold,
        color = TextTertiaryDark
      )
      Spacer(modifier = Modifier.height(Spacing.xxs))
      Text(
        text = value,
        fontSize = FontSizeTokens.titleSm,
        fontWeight = FontWeight.Bold,
        color = accentColor
      )
      Spacer(modifier = Modifier.height(Spacing.xxs))
      Text(
        text = subtext,
        fontSize = FontSizeTokens.caption,
        color = TextSecondaryDark
      )
    }
  }
}
