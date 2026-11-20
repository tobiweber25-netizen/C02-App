package com.aquarium.co2tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.aquarium.co2tracker.ui.theme.*
import java.util.Calendar

enum class AquaIconType {
  BRAND_DROPLET,
  BUBBLE_VALVE,
  PHOTOPERIOD_ARC,
  DOWNTIME_PAUSE,
  SCALE_BALANCE,
  CYLINDER_REFILL,
  TAB_VESSEL,
  TAB_LEDGER,
  TAB_SETTINGS,
  OTA_UPDATE,
  ALERT_BELL,
  TRASH,
  CHECK_SHIELD,
  WARNING,
  CHEVRON_RIGHT
}

fun currentLocalMinuteOfDay(): Int {
  val cal = Calendar.getInstance()
  return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

fun isSolenoidActiveAtMinute(
  minuteOfDay: Int,
  startMinutes: Int,
  endMinutes: Int
): Boolean {
  val now = ((minuteOfDay % 1440) + 1440) % 1440
  val start = ((startMinutes % 1440) + 1440) % 1440
  val end = ((endMinutes % 1440) + 1440) % 1440
  return when {
    start == end -> true
    end > start -> now in start until end
    else -> now >= start || now < end
  }
}

fun formatMinuteOfDay(minutes: Int): String {
  val normalized = ((minutes % 1440) + 1440) % 1440
  val h = normalized / 60
  val m = normalized % 60
  return String.format("%02d:%02d", h, m)
}

@Composable
fun AquaAmbientBackdrop(
  modifier: Modifier = Modifier,
  accentGlow: Color = OverheadLightGlow,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(AbyssalCanvas)
      .drawBehind {
        // Overhead WRGB aquarium pendant light wash radiating softly downward
        drawRect(
          brush = Brush.radialGradient(
            colorStops = arrayOf(
              0.0f to accentGlow.copy(alpha = 0.72f),
              0.42f to OverheadLightMist.copy(alpha = 0.45f),
              1.0f to AbyssalCanvas
            ),
            center = Offset(size.width * 0.5f, size.height * 0.04f),
            radius = size.width * 1.15f
          )
        )
        // Subtle lower depth shadow for abyssal contrast behind the floating dock
        drawRect(
          brush = Brush.verticalGradient(
            colorStops = arrayOf(
              0.0f to Color.Transparent,
              0.78f to Color.Transparent,
              1.0f to AbyssalCanvasDeep.copy(alpha = 0.92f)
            )
          )
        )
      },
    content = content
  )
}

@Composable
fun RimlessGlassSurface(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = Radius.lg,
  elevated: Boolean = false,
  accentRim: Color? = null,
  onClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(Spacing.cardPadding),
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(cornerRadius)
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (onClick != null && isPressed) AlphaTokens.pressScale else 1f,
    animationSpec = tween(durationMillis = 120),
    label = "glassPressScale"
  )

  val surfaceGradient = if (elevated) {
    Brush.verticalGradient(listOf(GlassSurfaceElevatedTop, GlassSurfaceElevated))
  } else {
    Brush.verticalGradient(listOf(GlassSurfaceTop, GlassSurface, GlassSurfaceBottom))
  }

  val topRimColor = accentRim?.copy(alpha = AlphaTokens.rimMedium) ?: StarphireRimBright
  val midRimColor = accentRim?.copy(alpha = AlphaTokens.rimSoft) ?: StarphireRimMid
  val rimBrush = Brush.verticalGradient(
    colorStops = arrayOf(
      0.0f to topRimColor,
      0.32f to midRimColor,
      1.0f to StarphireRimSubtle
    )
  )

  val clickableModifier = if (onClick != null) {
    Modifier.clickable(
      interactionSource = interactionSource,
      indication = ripple(color = PearlEmerald.copy(alpha = AlphaTokens.mediumTint)),
      onClick = onClick
    )
  } else {
    Modifier
  }

  Column(
    modifier = modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(shape)
      .background(surfaceGradient)
      .border(StrokeTokens.hairline, rimBrush, shape)
      .then(clickableModifier)
      .padding(contentPadding),
    content = content
  )
}

@Composable
fun GlassInsetWell(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = Radius.md,
  borderColor: Color = GlassWellBorder,
  contentPadding: PaddingValues = PaddingValues(Spacing.md),
  content: @Composable BoxScope.() -> Unit
) {
  val shape = RoundedCornerShape(cornerRadius)
  Box(
    modifier = modifier
      .clip(shape)
      .background(GlassWell)
      .border(StrokeTokens.hairline, borderColor, shape)
      .padding(contentPadding),
    content = content
  )
}

@Composable
fun AquaSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  trailingLabel: String? = null,
  trailingColor: Color = InkTertiary
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      Box(
        modifier = Modifier
          .size(width = Spacing.sm, height = Spacing.xxs)
          .clip(RoundedCornerShape(Radius.pill))
          .background(PearlEmerald.copy(alpha = AlphaTokens.rimBright))
      )
      Text(
        text = title.uppercase(),
        style = AquaTextStyles.sectionOverline,
        color = InkSecondary
      )
    }
    if (trailingLabel != null) {
      Text(
        text = trailingLabel,
        style = AquaTextStyles.monoMicro,
        color = trailingColor
      )
    }
  }
}

@Composable
fun AquaStatusPill(
  label: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
  showPulseDot: Boolean = true
) {
  val shape = RoundedCornerShape(Radius.pill)
  Row(
    modifier = modifier
      .clip(shape)
      .background(accentColor.copy(alpha = AlphaTokens.subtleTint))
      .border(StrokeTokens.hairline, accentColor.copy(alpha = AlphaTokens.rimSoft), shape)
      .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
  ) {
    if (showPulseDot) {
      Box(
        modifier = Modifier
          .size(IconSize.statusDot)
          .clip(CircleShape)
          .background(accentColor)
      )
    }
    Text(
      text = label,
      style = AquaTextStyles.monoMicro,
      color = accentColor
    )
  }
}

/**
 * Hand-blown ADA-style Glass Drop Checker showing live reagent status color.
 */
@Composable
fun DropCheckerGlyph(
  reagentColor: Color,
  modifier: Modifier = Modifier
) {
  Canvas(
    modifier = modifier.size(
      width = IconSize.dropCheckerWidth,
      height = IconSize.dropCheckerHeight
    )
  ) {
    val w = size.width
    val h = size.height
    val bulbCenter = Offset(w * 0.56f, h * 0.64f)
    val bulbRadius = w * 0.36f

    // Soft reagent luminescence
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          reagentColor.copy(alpha = AlphaTokens.glowAmbient),
          Color.Transparent
        ),
        center = bulbCenter,
        radius = bulbRadius * 1.9f
      ),
      radius = bulbRadius * 1.9f,
      center = bulbCenter
    )

    // Glass J-hook neck curving over rim
    val hookPath = Path().apply {
      moveTo(w * 0.18f, h * 0.42f)
      cubicTo(w * 0.14f, h * 0.08f, w * 0.62f, h * 0.08f, bulbCenter.x, h * 0.36f)
    }
    drawPath(
      path = hookPath,
      color = TitaniumSilver.copy(alpha = 0.72f),
      style = Stroke(width = StrokeTokens.medium.toPx(), cap = StrokeCap.Round)
    )

    // Reagent liquid lower dome inside glass sphere
    drawArc(
      color = reagentColor,
      startAngle = 10f,
      sweepAngle = 160f,
      useCenter = false,
      topLeft = Offset(bulbCenter.x - bulbRadius * 0.78f, bulbCenter.y - bulbRadius * 0.78f),
      size = Size(bulbRadius * 1.56f, bulbRadius * 1.56f)
    )
    drawCircle(
      brush = Brush.verticalGradient(
        colors = listOf(reagentColor.copy(alpha = 0.75f), reagentColor),
        startY = bulbCenter.y - bulbRadius * 0.15f,
        endY = bulbCenter.y + bulbRadius * 0.85f
      ),
      radius = bulbRadius * 0.72f,
      center = Offset(bulbCenter.x, bulbCenter.y + bulbRadius * 0.12f)
    )

    // Outer crystal glass bulb contour
    drawCircle(
      color = TitaniumSilver.copy(alpha = 0.8f),
      radius = bulbRadius,
      center = bulbCenter,
      style = Stroke(width = StrokeTokens.hairline.toPx())
    )

    // Specular starphire highlight dot
    drawCircle(
      color = InkPrimary.copy(alpha = 0.85f),
      radius = bulbRadius * 0.16f,
      center = Offset(bulbCenter.x - bulbRadius * 0.36f, bulbCenter.y - bulbRadius * 0.32f)
    )
  }
}

@Composable
fun AquaIcon(
  type: AquaIconType,
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = IconSize.md
) {
  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val stroke = Stroke(
      width = StrokeTokens.icon.toPx(),
      cap = StrokeCap.Round,
      join = StrokeJoin.Round
    )
    val fineStroke = Stroke(
      width = StrokeTokens.hairline.toPx(),
      cap = StrokeCap.Round,
      join = StrokeJoin.Round
    )

    when (type) {
      AquaIconType.BRAND_DROPLET -> {
        val dropPath = Path().apply {
          moveTo(w * 0.50f, h * 0.08f)
          cubicTo(w * 0.78f, h * 0.40f, w * 0.88f, h * 0.58f, w * 0.84f, h * 0.74f)
          cubicTo(w * 0.80f, h * 0.88f, w * 0.66f, h * 0.94f, w * 0.50f, h * 0.94f)
          cubicTo(w * 0.34f, h * 0.94f, w * 0.20f, h * 0.88f, w * 0.16f, h * 0.74f)
          cubicTo(w * 0.12f, h * 0.58f, w * 0.22f, h * 0.40f, w * 0.50f, h * 0.08f)
          close()
        }
        drawPath(
          path = dropPath,
          brush = Brush.verticalGradient(
            listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.08f))
          )
        )
        drawPath(path = dropPath, color = tint, style = stroke)
        // Rising pearl micro-bubbles inside the droplet
        drawCircle(color = PearlMintGlow, radius = w * 0.085f, center = Offset(w * 0.52f, h * 0.70f))
        drawCircle(color = tint.copy(alpha = 0.85f), radius = w * 0.055f, center = Offset(w * 0.42f, h * 0.54f))
        drawCircle(color = InkPrimary.copy(alpha = 0.9f), radius = w * 0.038f, center = Offset(w * 0.55f, h * 0.40f))
      }

      AquaIconType.BUBBLE_VALVE -> {
        // Vertical glass bubble counter tube
        drawRoundRect(
          color = tint,
          topLeft = Offset(w * 0.28f, h * 0.16f),
          size = Size(w * 0.44f, h * 0.68f),
          cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
          style = stroke
        )
        // Top & bottom tubing ports
        drawLine(tint, Offset(w * 0.50f, h * 0.06f), Offset(w * 0.50f, h * 0.16f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.84f), Offset(w * 0.50f, h * 0.94f), stroke.width, StrokeCap.Round)
        // Rising bubbles inside glass chamber
        drawCircle(color = tint, radius = w * 0.075f, center = Offset(w * 0.50f, h * 0.66f))
        drawCircle(color = tint.copy(alpha = 0.85f), radius = w * 0.058f, center = Offset(w * 0.45f, h * 0.48f))
        drawCircle(color = tint.copy(alpha = 0.65f), radius = w * 0.045f, center = Offset(w * 0.54f, h * 0.33f))
      }

      AquaIconType.PHOTOPERIOD_ARC -> {
        // Horizon base line
        drawLine(tint.copy(alpha = 0.55f), Offset(w * 0.10f, h * 0.74f), Offset(w * 0.90f, h * 0.74f), stroke.width, StrokeCap.Round)
        // Sunlit photoperiod arc
        drawArc(
          color = tint,
          startAngle = 180f,
          sweepAngle = 180f,
          useCenter = false,
          topLeft = Offset(w * 0.18f, h * 0.36f),
          size = Size(w * 0.64f, h * 0.74f),
          style = stroke
        )
        // Glowing meridian sun/LED bead at zenith
        drawCircle(color = tint, radius = w * 0.09f, center = Offset(w * 0.50f, h * 0.36f))
        // Subtle light rays
        drawLine(tint.copy(alpha = 0.7f), Offset(w * 0.50f, h * 0.12f), Offset(w * 0.50f, h * 0.20f), fineStroke.width, StrokeCap.Round)
        drawLine(tint.copy(alpha = 0.7f), Offset(w * 0.24f, h * 0.22f), Offset(w * 0.30f, h * 0.28f), fineStroke.width, StrokeCap.Round)
        drawLine(tint.copy(alpha = 0.7f), Offset(w * 0.76f, h * 0.22f), Offset(w * 0.70f, h * 0.28f), fineStroke.width, StrokeCap.Round)
      }

      AquaIconType.DOWNTIME_PAUSE -> {
        drawCircle(
          color = tint,
          radius = w * 0.39f,
          center = Offset(w * 0.50f, h * 0.50f),
          style = stroke
        )
        drawLine(tint, Offset(w * 0.40f, h * 0.35f), Offset(w * 0.40f, h * 0.65f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.60f, h * 0.35f), Offset(w * 0.60f, h * 0.65f), stroke.width, StrokeCap.Round)
      }

      AquaIconType.SCALE_BALANCE -> {
        // Precision digital laboratory scale silhouette
        drawRoundRect(
          color = tint,
          topLeft = Offset(w * 0.14f, h * 0.32f),
          size = Size(w * 0.72f, h * 0.54f),
          cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
          style = stroke
        )
        // Weighing platter above body
        drawLine(tint, Offset(w * 0.24f, h * 0.18f), Offset(w * 0.76f, h * 0.18f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.32f), stroke.width, StrokeCap.Round)
        // Calibration dial / digital readout window
        drawRoundRect(
          color = tint.copy(alpha = 0.30f),
          topLeft = Offset(w * 0.30f, h * 0.48f),
          size = Size(w * 0.40f, h * 0.20f),
          cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
        drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.58f))
      }

      AquaIconType.CYLINDER_REFILL -> {
        // Pressurized steel CO2 cylinder outline
        drawRoundRect(
          color = tint,
          topLeft = Offset(w * 0.30f, h * 0.30f),
          size = Size(w * 0.40f, h * 0.60f),
          cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
          style = stroke
        )
        // Brass neck & valve handwheel
        drawLine(tint, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.30f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.36f, h * 0.14f), Offset(w * 0.64f, h * 0.14f), stroke.width, StrokeCap.Round)
        // Fill meniscus inside bottle
        drawLine(tint.copy(alpha = 0.6f), Offset(w * 0.36f, h * 0.58f), Offset(w * 0.64f, h * 0.58f), fineStroke.width, StrokeCap.Round)
      }

      AquaIconType.TAB_VESSEL -> {
        // Rimless aquarium glass vessel with gentle water wave inside
        val tankPath = Path().apply {
          moveTo(w * 0.14f, h * 0.22f)
          lineTo(w * 0.14f, h * 0.78f)
          quadraticTo(w * 0.14f, h * 0.86f, w * 0.22f, h * 0.86f)
          lineTo(w * 0.78f, h * 0.86f)
          quadraticTo(w * 0.86f, h * 0.86f, w * 0.86f, h * 0.78f)
          lineTo(w * 0.86f, h * 0.22f)
        }
        drawPath(tankPath, color = tint, style = stroke)
        // Water level wave
        val wavePath = Path().apply {
          moveTo(w * 0.22f, h * 0.48f)
          quadraticTo(w * 0.38f, h * 0.40f, w * 0.52f, h * 0.48f)
          quadraticTo(w * 0.66f, h * 0.56f, w * 0.78f, h * 0.48f)
        }
        drawPath(wavePath, color = tint, style = stroke)
        drawCircle(color = tint, radius = w * 0.055f, center = Offset(w * 0.44f, h * 0.68f))
        drawCircle(color = tint.copy(alpha = 0.75f), radius = w * 0.04f, center = Offset(w * 0.62f, h * 0.62f))
      }

      AquaIconType.TAB_LEDGER -> {
        // Vertical timeline nodes & lines
        drawLine(tint.copy(alpha = 0.45f), Offset(w * 0.24f, h * 0.20f), Offset(w * 0.24f, h * 0.80f), fineStroke.width, StrokeCap.Round)
        val ys = listOf(0.24f, 0.50f, 0.76f)
        ys.forEachIndexed { idx, y ->
          drawCircle(color = tint, radius = w * 0.08f, center = Offset(w * 0.24f, h * y))
          val endX = if (idx == 1) 0.86f else 0.74f
          drawLine(tint, Offset(w * 0.44f, h * y), Offset(w * endX, h * y), stroke.width, StrokeCap.Round)
        }
      }

      AquaIconType.TAB_SETTINGS -> {
        // Precision dual-slider regulator controls
        drawLine(tint.copy(alpha = 0.55f), Offset(w * 0.16f, h * 0.30f), Offset(w * 0.84f, h * 0.30f), stroke.width, StrokeCap.Round)
        drawLine(tint.copy(alpha = 0.55f), Offset(w * 0.16f, h * 0.70f), Offset(w * 0.84f, h * 0.70f), stroke.width, StrokeCap.Round)
        drawCircle(color = AbyssalCanvas, radius = w * 0.12f, center = Offset(w * 0.36f, h * 0.30f))
        drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.36f, h * 0.30f), style = stroke)
        drawCircle(color = AbyssalCanvas, radius = w * 0.12f, center = Offset(w * 0.66f, h * 0.70f))
        drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.66f, h * 0.70f), style = stroke)
      }

      AquaIconType.OTA_UPDATE -> {
        // Circular sync arc + upward arrow
        drawArc(
          color = tint,
          startAngle = -45f,
          sweepAngle = 270f,
          useCenter = false,
          topLeft = Offset(w * 0.16f, h * 0.16f),
          size = Size(w * 0.68f, h * 0.68f),
          style = stroke
        )
        val arrowPath = Path().apply {
          moveTo(w * 0.50f, h * 0.66f)
          lineTo(w * 0.50f, h * 0.34f)
          moveTo(w * 0.37f, h * 0.46f)
          lineTo(w * 0.50f, h * 0.33f)
          lineTo(w * 0.63f, h * 0.46f)
        }
        drawPath(arrowPath, color = tint, style = stroke)
      }

      AquaIconType.ALERT_BELL -> {
        val bellPath = Path().apply {
          moveTo(w * 0.24f, h * 0.68f)
          lineTo(w * 0.76f, h * 0.68f)
          cubicTo(w * 0.72f, h * 0.60f, w * 0.72f, h * 0.38f, w * 0.66f, h * 0.28f)
          cubicTo(w * 0.60f, h * 0.18f, w * 0.40f, h * 0.18f, w * 0.34f, h * 0.28f)
          cubicTo(w * 0.28f, h * 0.38f, w * 0.28f, h * 0.60f, w * 0.24f, h * 0.68f)
          close()
        }
        drawPath(bellPath, color = tint, style = stroke)
        drawLine(tint, Offset(w * 0.42f, h * 0.80f), Offset(w * 0.58f, h * 0.80f), stroke.width, StrokeCap.Round)
      }

      AquaIconType.TRASH -> {
        drawLine(tint, Offset(w * 0.20f, h * 0.28f), Offset(w * 0.80f, h * 0.28f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.18f), Offset(w * 0.62f, h * 0.18f), stroke.width, StrokeCap.Round)
        val binPath = Path().apply {
          moveTo(w * 0.28f, h * 0.28f)
          lineTo(w * 0.32f, h * 0.82f)
          lineTo(w * 0.68f, h * 0.82f)
          lineTo(w * 0.72f, h * 0.28f)
        }
        drawPath(binPath, color = tint, style = stroke)
      }

      AquaIconType.CHECK_SHIELD -> {
        drawCircle(
          color = tint,
          radius = w * 0.39f,
          center = Offset(w * 0.50f, h * 0.50f),
          style = stroke
        )
        val check = Path().apply {
          moveTo(w * 0.33f, h * 0.52f)
          lineTo(w * 0.45f, h * 0.64f)
          lineTo(w * 0.68f, h * 0.38f)
        }
        drawPath(check, color = tint, style = stroke)
      }

      AquaIconType.WARNING -> {
        val tri = Path().apply {
          moveTo(w * 0.50f, h * 0.16f)
          lineTo(w * 0.86f, h * 0.82f)
          lineTo(w * 0.14f, h * 0.82f)
          close()
        }
        drawPath(tri, color = tint, style = stroke)
        drawLine(tint, Offset(w * 0.50f, h * 0.42f), Offset(w * 0.50f, h * 0.60f), stroke.width, StrokeCap.Round)
        drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.71f))
      }

      AquaIconType.CHEVRON_RIGHT -> {
        val chev = Path().apply {
          moveTo(w * 0.36f, h * 0.24f)
          lineTo(w * 0.66f, h * 0.50f)
          lineTo(w * 0.36f, h * 0.76f)
        }
        drawPath(chev, color = tint, style = stroke)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AquaModalSheet(
  title: String,
  subtitle: String,
  iconType: AquaIconType,
  accentColor: Color = PearlEmerald,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = GlassSurfaceElevated,
    contentColor = InkPrimary,
    scrimColor = GlassScrim,
    shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    dragHandle = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = Spacing.md, bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(width = VesselTokens.sheetHandleWidth, height = VesselTokens.sheetHandleHeight)
            .clip(RoundedCornerShape(Radius.pill))
            .background(StarphireRimBright)
        )
      }
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = Spacing.sheetPadding)
        .padding(bottom = Spacing.xl)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
      ) {
        Box(
          modifier = Modifier
            .size(IconSize.badge)
            .clip(RoundedCornerShape(Radius.md))
            .background(accentColor.copy(alpha = AlphaTokens.subtleTint))
            .border(
              StrokeTokens.hairline,
              accentColor.copy(alpha = AlphaTokens.rimSoft),
              RoundedCornerShape(Radius.md)
            ),
          contentAlignment = Alignment.Center
        ) {
          AquaIcon(type = iconType, tint = accentColor, size = IconSize.md)
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = AquaTextStyles.cardTitle,
            color = InkPrimary
          )
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = subtitle,
            style = AquaTextStyles.caption,
            color = InkSecondary
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.lg))

      content()
    }
  }
}

@Composable
fun aquaTextFieldColors(accentColor: Color = PearlEmerald): TextFieldColors =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = InkPrimary,
    unfocusedTextColor = InkPrimary,
    focusedContainerColor = GlassWell,
    unfocusedContainerColor = GlassWell,
    errorContainerColor = GlassWell,
    focusedBorderColor = accentColor,
    unfocusedBorderColor = StarphireRimMid,
    errorBorderColor = DropCheckerCoral,
    focusedLabelColor = accentColor,
    unfocusedLabelColor = InkSecondary,
    errorLabelColor = DropCheckerCoral,
    cursorColor = accentColor,
    focusedPlaceholderColor = InkTertiary,
    unfocusedPlaceholderColor = InkTertiary
  )

@Composable
fun aquaChipColors(accentColor: Color = PearlEmerald): SelectableChipColors =
  FilterChipDefaults.filterChipColors(
    containerColor = GlassWell,
    labelColor = InkSecondary,
    selectedContainerColor = accentColor.copy(alpha = AlphaTokens.mediumTint),
    selectedLabelColor = accentColor
  )
