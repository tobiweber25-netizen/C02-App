package com.aquarium.co2tracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.aquarium.co2tracker.domain.BottleStatus
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private data class BubbleSeed(
  val xFraction: Float,
  val phaseOffset: Float,
  val speedFactor: Float,
  val radiusScale: Float,
  val swayAmplitude: Float
)

private val VesselBubbleSeeds = listOf(
  BubbleSeed(0.18f, 0.05f, 0.90f, 0.70f, 0.015f),
  BubbleSeed(0.28f, 0.42f, 1.15f, 1.00f, 0.020f),
  BubbleSeed(0.36f, 0.77f, 0.80f, 0.55f, 0.012f),
  BubbleSeed(0.45f, 0.21f, 1.25f, 1.15f, 0.018f),
  BubbleSeed(0.52f, 0.63f, 0.95f, 0.75f, 0.016f),
  BubbleSeed(0.61f, 0.11f, 1.10f, 0.90f, 0.022f),
  BubbleSeed(0.68f, 0.54f, 0.85f, 0.60f, 0.014f),
  BubbleSeed(0.74f, 0.89f, 1.30f, 1.05f, 0.019f),
  BubbleSeed(0.81f, 0.33f, 0.92f, 0.65f, 0.013f),
  BubbleSeed(0.41f, 0.94f, 1.05f, 0.50f, 0.010f),
  BubbleSeed(0.58f, 0.47f, 1.20f, 0.80f, 0.017f),
  BubbleSeed(0.24f, 0.68f, 0.88f, 0.55f, 0.012f)
)

@Composable
fun VisualCylinderGauge(
  projection: CylinderProjection,
  modifier: Modifier = Modifier,
  nowMinuteOfDay: Int = currentLocalMinuteOfDay(),
  bottleCount: Int = 1,
  needsRefillCount: Int = 0,
  onOpenFleetClick: (() -> Unit)? = null
) {
  val targetFill = (projection.remainingTotalPercent / 100.0).toFloat().coerceIn(0f, 1f)
  val animatedFill by animateFloatAsState(
    targetValue = targetFill,
    animationSpec = tween(durationMillis = 900),
    label = "vesselFillLevel"
  )

  val isDosingNow = projection.hasActiveBurn && isSolenoidActiveAtMinute(
    minuteOfDay = nowMinuteOfDay,
    startMinutes = projection.currentStartMinutes,
    endMinutes = projection.currentEndMinutes
  ) && !projection.isBelowDangerThreshold && !projection.isEmpty

  // Pace bubble cycle smoothly by live BPS (higher BPS = brisker pearling stream)
  val bpsFactor = projection.currentBps.coerceIn(0.3, 5.0)
  val cycleDurationMs = if (isDosingNow) {
    (5600.0 / (0.65 + bpsFactor * 0.35)).toInt().coerceIn(2200, 6200)
  } else {
    9000
  }

  val infiniteTransition = rememberInfiniteTransition(label = "livingVesselAmbient")
  val bubbleProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = cycleDurationMs, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "pearlingStream"
  )
  val wavePhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2.0 * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 5200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "meniscusWave"
  )

  val (statusAccent, liquidBright, liquidDeep) = when {
    projection.isBelowDangerThreshold -> Triple(DropCheckerCoral, VesselLiquidCoralBright, VesselLiquidCoralDeep)
    projection.isInRefillWindow || projection.remainingTotalPercent <= 25.0 ->
      Triple(DropCheckerAmber, VesselLiquidAmberBright, VesselLiquidAmberDeep)
    else -> Triple(PearlEmerald, VesselLiquidBright, VesselLiquidDeep)
  }

  val shape = RoundedCornerShape(Radius.hero)
  val rimBrush = Brush.verticalGradient(
    colorStops = arrayOf(
      0.0f to statusAccent.copy(alpha = AlphaTokens.rimMedium),
      0.28f to StarphireRimBright,
      1.0f to StarphireRimSubtle
    )
  )

  val estimatedGrossGrams = projection.config.effectiveTareWeightGrams + projection.remainingCo2Grams

  Box(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = VesselTokens.heroMinHeight)
      .clip(shape)
      .background(Brush.verticalGradient(listOf(GlassSurfaceTop, GlassSurface, AbyssalCanvasDeep)))
      .border(StrokeTokens.hairline, rimBrush, shape)
  ) {
    // Full-bleed Living Illuminated Rimless Vessel Canvas
    Canvas(modifier = Modifier.matchParentSize()) {
      val w = size.width
      val h = size.height

      // 1. Overhead WRGB LED light shafts refracting softly from top rim
      val leftRay = Path().apply {
        moveTo(w * 0.16f, 0f)
        lineTo(w * 0.40f, 0f)
        lineTo(w * 0.24f, h * 0.85f)
        lineTo(w * 0.02f, h * 0.85f)
        close()
      }
      val centerRay = Path().apply {
        moveTo(w * 0.44f, 0f)
        lineTo(w * 0.72f, 0f)
        lineTo(w * 0.86f, h * 0.90f)
        lineTo(w * 0.56f, h * 0.90f)
        close()
      }
      val rayBrush = Brush.verticalGradient(
        listOf(statusAccent.copy(alpha = AlphaTokens.rayGhost), Color.Transparent)
      )
      drawPath(leftRay, brush = rayBrush)
      drawPath(centerRay, brush = rayBrush)

      // 2. Pressurized liquid/CO2 fill column mapped to the visible viewing window above bottom well
      val tankBottomY = h * 0.78f
      val tankTopY = h * 0.14f
      val tankSpan = tankBottomY - tankTopY
      val surfaceBaseY = tankBottomY - (tankSpan * animatedFill.coerceIn(0.03f, 0.98f))
      val waveAmp = if (isDosingNow) h * 0.014f else h * 0.008f

      val liquidPath = Path().apply {
        moveTo(0f, h)
        lineTo(0f, surfaceBaseY)
        val steps = 36
        for (i in 0..steps) {
          val fraction = i / steps.toFloat()
          val x = fraction * w
          val waveY = surfaceBaseY +
            sin(fraction * 2.0 * PI + wavePhase).toFloat() * waveAmp +
            sin(fraction * 4.0 * PI - wavePhase * 1.4f).toFloat() * (waveAmp * 0.45f)
          lineTo(x, waveY)
        }
        lineTo(w, h)
        close()
      }

      drawPath(
        path = liquidPath,
        brush = Brush.verticalGradient(
          colorStops = arrayOf(
            0.0f to liquidBright.copy(alpha = AlphaTokens.liquidTop),
            0.35f to liquidBright.copy(alpha = AlphaTokens.liquidMid),
            1.0f to liquidDeep.copy(alpha = AlphaTokens.liquidDeep)
          ),
          startY = surfaceBaseY,
          endY = h
        )
      )

      // Subtle dark contrast vignette behind left hero numerals for guaranteed AAA legibility
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            AbyssalCanvasDeep.copy(alpha = 0.48f),
            Color.Transparent
          ),
          center = Offset(w * 0.34f, h * 0.48f),
          radius = w * 0.52f
        ),
        radius = w * 0.52f,
        center = Offset(w * 0.34f, h * 0.48f)
      )

      // Luminous meniscus hairline along the top wave
      val meniscusPath = Path().apply {
        val steps = 36
        for (i in 0..steps) {
          val fraction = i / steps.toFloat()
          val x = fraction * w
          val waveY = surfaceBaseY +
            sin(fraction * 2.0 * PI + wavePhase).toFloat() * waveAmp +
            sin(fraction * 4.0 * PI - wavePhase * 1.4f).toFloat() * (waveAmp * 0.45f)
          if (i == 0) moveTo(x, waveY) else lineTo(x, waveY)
        }
      }
      drawPath(
        path = meniscusPath,
        brush = Brush.horizontalGradient(
          listOf(
            statusAccent.copy(alpha = 0.25f),
            PearlMintGlow.copy(alpha = 0.78f),
            statusAccent.copy(alpha = 0.30f)
          )
        ),
        style = Stroke(width = StrokeTokens.medium.toPx(), cap = StrokeCap.Round)
      )

      // 3. Delicate ghosted Vallisneria / aquascape stem silhouettes along vessel floor
      val frondBaseColor = VesselSilhouetteFrond.copy(alpha = AlphaTokens.frondGhost)
      val frondCoords = listOf(
        Triple(0.08f, 0.14f, 0.56f),
        Triple(0.14f, 0.20f, 0.64f),
        Triple(0.84f, 0.78f, 0.58f),
        Triple(0.91f, 0.87f, 0.67f)
      )
      frondCoords.forEach { (startX, tipX, tipY) ->
        val frond = Path().apply {
          moveTo(w * startX, h)
          quadraticTo(
            w * ((startX + tipX) * 0.5f + 0.03f),
            h * (tipY + 0.16f),
            w * tipX,
            h * tipY
          )
        }
        drawPath(
          path = frond,
          color = frondBaseColor,
          style = Stroke(width = StrokeTokens.bold.toPx(), cap = StrokeCap.Round)
        )
      }

      // 4. Rising CO2 Micro-Bubbles ("Pearling Stream") inside the fluid volume
      val liquidHeight = (tankBottomY - surfaceBaseY).coerceAtLeast(h * 0.10f)
      val visibleBubbles = if (isDosingNow) VesselBubbleSeeds.size else 6
      for (i in 0 until visibleBubbles) {
        val seed = VesselBubbleSeeds[i]
        val localProgress = (bubbleProgress * seed.speedFactor + seed.phaseOffset) % 1f
        val bubbleY = tankBottomY - localProgress * liquidHeight
        val swayX = sin(localProgress * 3.0 * PI + seed.phaseOffset * 6.0).toFloat() * w * seed.swayAmplitude
        val bubbleX = w * seed.xFraction + swayX
        val baseRadius = (size.minDimension * 0.011f) * seed.radiusScale
        val fadeEdge = (1f - localProgress).coerceIn(0f, 1f) * (localProgress * 5f).coerceIn(0f, 1f)
        val alpha = (if (isDosingNow) 0.58f else 0.26f) * fadeEdge

        if (alpha > 0.02f && bubbleY > surfaceBaseY + baseRadius) {
          drawCircle(
            color = statusAccent.copy(alpha = alpha * 0.45f),
            radius = baseRadius * 1.45f,
            center = Offset(bubbleX, bubbleY)
          )
          drawCircle(
            color = VesselBubbleHighlight.copy(alpha = alpha),
            radius = baseRadius,
            center = Offset(bubbleX, bubbleY),
            style = Stroke(width = StrokeTokens.hairline.toPx())
          )
          drawCircle(
            color = InkPrimary.copy(alpha = alpha * 0.9f),
            radius = baseRadius * 0.32f,
            center = Offset(bubbleX - baseRadius * 0.3f, bubbleY - baseRadius * 0.3f)
          )
        }
      }

      // 5. Etched Safety Cutoff (EOTD) Horizon Line positioned cleanly above the bottom scale bar
      val cutoffFraction = (projection.config.dangerThresholdPercent / 100.0).toFloat().coerceIn(0.05f, 0.45f)
      val cutoffY = tankBottomY - (tankSpan * cutoffFraction)
      drawLine(
        color = DropCheckerCoral.copy(alpha = 0.55f),
        start = Offset(Spacing.lg.toPx(), cutoffY),
        end = Offset(w - Spacing.xxl.toPx(), cutoffY),
        strokeWidth = StrokeTokens.hairline.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
      )

      // 6. Right-edge graduated borosilicate glass tick marks (100 / 75 / 50 / 25 / cutoff)
      val ticks = listOf(1.0f, 0.75f, 0.50f, 0.25f, cutoffFraction)
      val tickRight = w - Spacing.md.toPx()
      ticks.forEach { level ->
        val y = tankBottomY - (tankSpan * level)
        val isCutoffTick = level == cutoffFraction
        val tickLen = if (level == 0.50f || isCutoffTick) Spacing.md.toPx() else Spacing.sm.toPx()
        drawLine(
          color = if (isCutoffTick) DropCheckerCoral.copy(alpha = 0.90f) else TitaniumSilver.copy(alpha = 0.34f),
          start = Offset(tickRight - tickLen, y),
          end = Offset(tickRight, y),
          strokeWidth = StrokeTokens.hairline.toPx(),
          cap = StrokeCap.Round
        )
      }
    }

    // Foreground Sculptural Telemetry Layer
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.lg),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top Instrument Bar: Cylinder Name + Live Dosing & Calibration Pills
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .then(
              if (onOpenFleetClick != null) Modifier.clickable(onClick = onOpenFleetClick) else Modifier
            )
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
          ) {
            Text(
              text = projection.config.cylinderName,
              style = AquaTextStyles.cardTitle,
              color = InkPrimary
            )
            if (onOpenFleetClick != null) {
              AquaStatusPill(
                label = when {
                  needsRefillCount > 0 -> "$bottleCount • $needsRefillCount REFILL"
                  bottleCount > 1 -> "$bottleCount BOTTLES"
                  else -> "FLEET"
                },
                accentColor = if (needsRefillCount > 0) DropCheckerAmber else StarphireCyan,
                showPulseDot = needsRefillCount > 0
              )
            }
          }
          Spacer(modifier = Modifier.height(Spacing.xxs))
          val pauseTimeStr = projection.pausedSinceMillis?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
          }
          Text(
            text = when {
              projection.config.bottleStatus == BottleStatus.SPARE_FULL -> "Full spare ready • tap fleet to swap in"
              projection.config.bottleStatus == BottleStatus.NEEDS_REFILL -> "Marked empty • awaiting refill"
              projection.isPaused && pauseTimeStr != null ->
                String.format(Locale.US, "Paused since %s • %.1fh missed", pauseTimeStr, projection.pausedMissedSolenoidHours)
              !projection.hasActiveBurn -> "Paused • 0 BPS"
              projection.isEmpty -> "Empty • weigh canister now"
              isDosingNow -> "Active • ${projection.currentBps} BPS"
              else -> "Standby • ON ${formatMinuteOfDay(projection.currentStartMinutes)}"
            },
            style = AquaTextStyles.caption,
            color = if (projection.isPaused) DropCheckerAmber else InkSecondary
          )
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
          AquaStatusPill(
            label = when {
              projection.config.bottleStatus == BottleStatus.SPARE_FULL -> "SPARE"
              projection.config.bottleStatus == BottleStatus.NEEDS_REFILL -> "REFILL"
              projection.isPaused -> "PAUSED"
              !projection.hasActiveBurn -> "0 BPS"
              isDosingNow -> "DOSING"
              else -> "STANDBY"
            },
            accentColor = when {
              projection.config.bottleStatus == BottleStatus.SPARE_FULL -> StarphireCyan
              projection.config.bottleStatus == BottleStatus.NEEDS_REFILL -> DropCheckerCoral
              projection.isPaused -> DropCheckerAmber
              !projection.hasActiveBurn -> DropCheckerAqua
              isDosingNow -> PearlEmerald
              else -> StarphireCyan
            }
          )
          AquaStatusPill(
            label = when {
              projection.isUsingCarriedCalibration -> "CARRIED RATE"
              projection.isScaleCalibrated -> "SCALE LOCKED"
              else -> "GAS LAW"
            },
            accentColor = if (projection.isScaleCalibrated) DropCheckerOptimal else DropCheckerAqua,
            showPulseDot = false
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      // Sculptural Hero Metric Readout
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = Spacing.xl)
      ) {
        Text(
          text = "CO2 RESERVE",
          style = AquaTextStyles.sectionOverline,
          color = InkSecondary
        )
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Row(
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
          val percentDisplay = when {
            projection.remainingCo2Grams > 0.5 && projection.remainingTotalPercent < 1.0 -> "<1"
            else -> "${projection.remainingTotalPercent.roundToInt()}"
          }
          Text(
            text = percentDisplay,
            style = AquaTextStyles.heroPercent,
            color = InkPrimary
          )
          Text(
            text = "%",
            style = AquaTextStyles.displayNumber,
            color = statusAccent,
            modifier = Modifier.padding(bottom = Spacing.sm)
          )
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
          Text(
            text = String.format(
              Locale.US,
              "%,dg",
              projection.remainingCo2Grams.roundToInt()
            ),
            style = AquaTextStyles.monoLarge,
            color = InkPrimary
          )
          Text(
            text = String.format(
              Locale.US,
              "/ %,dg net CO2",
              projection.config.initialNetCo2Grams.roundToInt()
            ),
            style = AquaTextStyles.monoBody,
            color = InkSecondary
          )
        }

        Spacer(modifier = Modifier.height(Spacing.xxs))

        Text(
          text = String.format(
            Locale.US,
            "Usable above %d%% cutoff: %,dg",
            projection.config.dangerThresholdPercent.roundToInt(),
            projection.usableRemainingGrams.roundToInt()
          ),
          style = AquaTextStyles.caption,
          color = if (projection.isBelowDangerThreshold) DropCheckerCoral else InkTertiary
        )
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      // Recessed Bottom Scale Weight Snapshot Well
      GlassInsetWell(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.md,
        borderColor = StarphireRimSubtle,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          HeroFootMetric(
            label = "EST. SCALE",
            value = String.format(Locale.US, "~%,dg", estimatedGrossGrams.roundToInt()),
            valueColor = InkPrimary
          )
          Box(
            modifier = Modifier
              .width(StrokeTokens.hairline)
              .height(Spacing.xl)
              .background(StarphireRimSubtle)
          )
          HeroFootMetric(
            label = "TARE BASE",
            value = String.format(Locale.US, "%,dg", projection.config.effectiveTareWeightGrams.roundToInt()),
            valueColor = InkSecondary
          )
          Box(
            modifier = Modifier
              .width(StrokeTokens.hairline)
              .height(Spacing.xl)
              .background(StarphireRimSubtle)
          )
          HeroFootMetric(
            label = "EOTD FLOOR",
            value = String.format(Locale.US, "%,dg", projection.config.dangerThresholdGrams.toInt()),
            valueColor = if (projection.isBelowDangerThreshold) DropCheckerCoral else DropCheckerAmber
          )
        }
      }
    }
  }
}

@Composable
private fun HeroFootMetric(
  label: String,
  value: String,
  valueColor: Color
) {
  Column(horizontalAlignment = Alignment.Start) {
    Text(
      text = label,
      style = AquaTextStyles.monoMicro,
      color = InkTertiary
    )
    Spacer(modifier = Modifier.height(Spacing.xxs))
    Text(
      text = value,
      style = AquaTextStyles.monoBody,
      fontWeight = FontWeight.SemiBold,
      color = valueColor
    )
  }
}
