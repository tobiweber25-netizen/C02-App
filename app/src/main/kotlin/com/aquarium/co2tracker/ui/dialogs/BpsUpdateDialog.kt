package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.aquarium.co2tracker.domain.Co2PhysicsEngine
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*
import java.util.Locale

@Composable
fun BpsUpdateDialog(
  projection: CylinderProjection,
  onDismiss: () -> Unit,
  onConfirm: (Double) -> Unit
) {
  var bpsText by remember { mutableStateOf(String.format(Locale.US, "%.1f", projection.currentBps)) }
  val parsedBps = Co2PhysicsEngine.parseDecimalInput(bpsText)
  val currentBps = parsedBps?.coerceAtLeast(0.0) ?: projection.currentBps

  val previewDailyBurn = Co2PhysicsEngine.calculateDailyBurnRateGrams(
    bps = currentBps,
    gramsPerBubbleCoefficient = projection.activeGramsPerBubble,
    activeSolenoidHoursPerDay = projection.activeSolenoidHoursPerDay
  )
  val isValid = parsedBps != null && parsedBps >= 0.0

  AquaModalSheet(
    title = "Tune Bubble Rate",
    subtitle = "Currently ${projection.currentBps} BPS. Gas consumed up to this minute is banked automatically.",
    iconType = AquaIconType.BUBBLE_VALVE,
    accentColor = PearlEmerald,
    onDismiss = onDismiss
  ) {
    // Tactile Needle-Valve Stepper Pills
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      listOf(-0.2, -0.1, 0.1, 0.2).forEach { delta ->
        val label = if (delta > 0) String.format(Locale.US, "+%.1f", delta) else String.format(Locale.US, "%.1f", delta)
        FilledTonalButton(
          onClick = {
            val cur = Co2PhysicsEngine.parseDecimalInput(bpsText) ?: projection.currentBps
            val next = (cur + delta).coerceAtLeast(0.0)
            bpsText = String.format(Locale.US, "%.1f", next)
          },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(Radius.md),
          contentPadding = PaddingValues(vertical = Spacing.sm),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = GlassWell,
            contentColor = PearlEmerald
          )
        ) {
          Text(text = label, style = AquaTextStyles.monoBody, fontWeight = FontWeight.SemiBold)
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    OutlinedTextField(
      value = bpsText,
      onValueChange = { bpsText = it },
      label = { Text("Bubbles Per Second (BPS)") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      singleLine = true,
      shape = RoundedCornerShape(Radius.md),
      modifier = Modifier.fillMaxWidth(),
      colors = aquaTextFieldColors(PearlEmerald)
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    // Live Mini Glass Bubble Counter Preview + Projected Daily Burn
    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      borderColor = PearlEmerald.copy(alpha = AlphaTokens.rimSoft),
      contentPadding = PaddingValues(Spacing.md)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.normal)
      ) {
        LiveBubbleCounterTube(bps = currentBps)

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "LIVE FLOW PREVIEW",
            style = AquaTextStyles.monoMicro,
            color = InkTertiary
          )
          Spacer(modifier = Modifier.height(Spacing.xs))
          Text(
            text = String.format(Locale.US, "%.2f g / day", previewDailyBurn),
            style = AquaTextStyles.monoLarge,
            color = PearlEmerald
          )
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = String.format(
              Locale.US,
              "At %.1f BPS over %.1f active hrs/day",
              currentBps,
              projection.activeSolenoidHoursPerDay
            ),
            style = AquaTextStyles.caption,
            color = InkSecondary
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
          val v = Co2PhysicsEngine.parseDecimalInput(bpsText)
          if (v != null && v >= 0.0) onConfirm(v)
        },
        enabled = isValid,
        modifier = Modifier.weight(1.4f),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = PearlEmerald,
          contentColor = OnPearlEmerald,
          disabledContainerColor = GlassWell,
          disabledContentColor = InkTertiary
        )
      ) {
        Text(
          text = "Bank & Update",
          style = AquaTextStyles.bodySm,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun LiveBubbleCounterTube(bps: Double) {
  val safeBps = bps.coerceIn(0.2, 8.0)
  // Exact physical rhythm: 1 bubble rises per (1000 / bps) milliseconds
  val periodMs = (1000.0 / safeBps).toInt().coerceIn(140, 3200)
  val infinite = rememberInfiniteTransition(label = "miniBubbleCounter")
  val phase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = periodMs, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "miniBubblePhase"
  )

  Canvas(
    modifier = Modifier.size(
      width = VesselTokens.miniBubbleTubeWidth,
      height = VesselTokens.miniBubbleTubeHeight
    )
  ) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * 0.42f, w * 0.42f)

    // Liquid body inside borosilicate tube
    drawRoundRect(
      brush = Brush.verticalGradient(
        listOf(VesselLiquidBright.copy(alpha = 0.22f), VesselLiquidDeep.copy(alpha = 0.50f))
      ),
      topLeft = Offset(w * 0.14f, h * 0.12f),
      size = Size(w * 0.72f, h * 0.78f),
      cornerRadius = corner
    )

    // Glass rim contour
    drawRoundRect(
      color = StarphireRimBright,
      topLeft = Offset(w * 0.12f, h * 0.08f),
      size = Size(w * 0.76f, h * 0.84f),
      cornerRadius = corner,
      style = Stroke(width = StrokeTokens.medium.toPx())
    )

    // Specular side highlight along glass cylinder
    drawLine(
      color = InkPrimary.copy(alpha = 0.28f),
      start = Offset(w * 0.26f, h * 0.20f),
      end = Offset(w * 0.26f, h * 0.76f),
      strokeWidth = StrokeTokens.hairline.toPx(),
      cap = StrokeCap.Round
    )

    // Two staggered pearls rising smoothly at the exact BPS cadence
    listOf(0f, 0.5f).forEach { offset ->
      val p = (phase + offset) % 1f
      val y = h * 0.84f - p * (h * 0.64f)
      val r = w * 0.11f * (0.85f + 0.25f * p)
      val alpha = (1f - p * 0.7f).coerceIn(0.2f, 0.95f)
      drawCircle(
        color = PearlEmerald.copy(alpha = alpha * 0.55f),
        radius = r * 1.45f,
        center = Offset(w * 0.50f, y)
      )
      drawCircle(
        color = VesselBubbleHighlight.copy(alpha = alpha),
        radius = r,
        center = Offset(w * 0.50f, y)
      )
    }
  }
}
