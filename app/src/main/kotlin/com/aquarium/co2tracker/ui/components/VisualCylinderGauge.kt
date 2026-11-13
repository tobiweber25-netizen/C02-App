package com.aquarium.co2tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*

@Composable
fun VisualCylinderGauge(
  projection: CylinderProjection,
  modifier: Modifier = Modifier
) {
  val targetFill = (projection.remainingTotalPercent / 100f).toFloat().coerceIn(0f, 1f)
  val animatedFill by animateFloatAsState(
    targetValue = targetFill,
    animationSpec = tween(durationMillis = 800),
    label = "cylinderFillAnimation"
  )

  val statusColor = when {
    projection.remainingTotalPercent <= projection.config.dangerThresholdPercent -> StatusCritical
    projection.remainingTotalPercent <= 25.0 -> StatusWarning
    else -> NeonCyanPrimary
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.lg))
      .background(DarkSurfaceCard)
      .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg))
      .padding(Spacing.cardPadding),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = projection.config.cylinderName,
        fontSize = FontSizeTokens.body,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimaryDark
      )

      val calibrationText = if (projection.isScaleCalibrated) "SCALE CALIBRATED" else "THEORETICAL"
      val calibrationColor = if (projection.isScaleCalibrated) StatusCalibrated else StatusTheoretical

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(Radius.xs))
          .background(calibrationColor.copy(alpha = 0.15f))
          .border(1.dp, calibrationColor.copy(alpha = 0.5f), RoundedCornerShape(Radius.xs))
          .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
      ) {
        Text(
          text = calibrationText,
          fontSize = FontSizeTokens.caption,
          fontWeight = FontWeight.Bold,
          color = calibrationColor
        )
      }
    }

    Spacer(modifier = Modifier.height(Spacing.normal))

    // Visual Cylinder + Circular Gauge Container
    Box(
      modifier = Modifier
        .size(GaugeTokens.diameter)
        .padding(Spacing.xs),
      contentAlignment = Alignment.Center
    ) {
      // Circular track and progress arc
      Canvas(modifier = Modifier.fillMaxSize()) {
        val strokePx = GaugeTokens.strokeWidth.toPx()
        val arcSize = size.width - strokePx
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)

        // Background Track Arc
        drawArc(
          color = GaugeTrack,
          startAngle = 135f,
          sweepAngle = 270f,
          useCenter = false,
          topLeft = topLeft,
          size = Size(arcSize, arcSize),
          style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Progress Arc
        drawArc(
          brush = Brush.sweepGradient(
            0.0f to statusColor,
            1.0f to statusColor.copy(alpha = 0.8f)
          ),
          startAngle = 135f,
          sweepAngle = 270f * animatedFill,
          useCenter = false,
          topLeft = topLeft,
          size = Size(arcSize, arcSize),
          style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
      }

      // Cylinder Core Stats inside Arc
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = "${projection.remainingTotalPercent.toInt()}%",
          fontSize = FontSizeTokens.display,
          fontWeight = FontWeight.ExtraBold,
          color = statusColor
        )

        Spacer(modifier = Modifier.height(Spacing.xxs))

        Text(
          text = "${projection.remainingCo2Grams.toInt()}g / ${projection.config.initialNetCo2Grams.toInt()}g",
          fontSize = FontSizeTokens.bodySm,
          fontWeight = FontWeight.Medium,
          color = TextPrimaryDark
        )

        Text(
          text = "Net CO2 Remaining",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Danger Cutoff Reference
        Text(
          text = "Safety cutoff: ${projection.config.dangerThresholdPercent.toInt()}% (${projection.config.dangerThresholdGrams.toInt()}g)",
          fontSize = FontSizeTokens.caption,
          color = if (projection.isBelowDangerThreshold) StatusCritical else TextTertiaryDark
        )
      }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Total gross scale weight snapshot
    val currentEstimatedGrossWeight = projection.config.effectiveTareWeightGrams + projection.remainingCo2Grams
    Text(
      text = "Current Scale Weight: ~${currentEstimatedGrossWeight.toInt()}g (Tare: ${projection.config.effectiveTareWeightGrams.toInt()}g)",
      fontSize = FontSizeTokens.bodySm,
      color = TextSecondaryDark
    )
  }
}
