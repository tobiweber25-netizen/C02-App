package com.aquarium.co2tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*
import java.util.Locale

@Composable
fun ActiveSettingsSummaryCard(
  projection: CylinderProjection,
  modifier: Modifier = Modifier,
  nowMinuteOfDay: Int = currentLocalMinuteOfDay(),
  onBpsClick: (() -> Unit)? = null,
  onScheduleClick: (() -> Unit)? = null,
  onScaleClick: (() -> Unit)? = null
) {
  val scheduleStr = "${formatMinuteOfDay(projection.currentStartMinutes)} – ${formatMinuteOfDay(projection.currentEndMinutes)}"
  val isInjectingNow = projection.hasActiveBurn && !projection.isEmpty && isSolenoidActiveAtMinute(
    minuteOfDay = nowMinuteOfDay,
    startMinutes = projection.currentStartMinutes,
    endMinutes = projection.currentEndMinutes
  )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    AquaSectionHeader(
      title = "Photoperiod & Burn Telemetry",
      trailingLabel = String.format(Locale.US, "%.1f HRS/DAY", projection.activeSolenoidHoursPerDay),
      trailingColor = StarphireCyan
    )

    // 1. 24-Hour Solenoid Photoperiod Strip Card (Tappable to adjust schedule)
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      onClick = onScheduleClick,
      accentRim = if (isInjectingNow) PearlEmerald else null
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
          AquaIcon(
            type = AquaIconType.PHOTOPERIOD_ARC,
            tint = StarphireCyan,
            size = IconSize.sm
          )
          Column {
            Text(
              text = "Daily CO2 Injection Window",
              style = AquaTextStyles.caption,
              color = InkSecondary
            )
            Text(
              text = scheduleStr,
              style = AquaTextStyles.monoLarge,
              color = InkPrimary
            )
          }
        }

        AquaStatusPill(
          label = if (isInjectingNow) "INJECTING" else "OFF-CYCLE",
          accentColor = if (isInjectingNow) PearlEmerald else InkTertiary,
          showPulseDot = isInjectingNow
        )
      }

      Spacer(modifier = Modifier.height(Spacing.md))

      PhotoperiodTimelineBar(
        startMinutes = projection.currentStartMinutes,
        endMinutes = projection.currentEndMinutes,
        nowMinuteOfDay = nowMinuteOfDay
      )
    }

    // 2. Asymmetric Bento Telemetry Grid (3 key live physics metrics)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      TelemetryTile(
        label = "BUBBLE RATE",
        value = String.format(Locale.US, "%.1f", projection.currentBps),
        unit = "BPS",
        detail = String.format(Locale.US, "%.5fg / bubble", projection.activeGramsPerBubble),
        accentColor = PearlEmerald,
        iconType = AquaIconType.BUBBLE_VALVE,
        onClick = onBpsClick,
        modifier = Modifier.weight(1f)
      )

      TelemetryTile(
        label = "DAILY BURN",
        value = String.format(Locale.US, "%.2f", projection.dailyBurnRateGrams),
        unit = "g/day",
        detail = String.format(Locale.US, "%.2f g/hr active", projection.hourlyBurnRateGrams),
        accentColor = StarphireCyan,
        iconType = AquaIconType.BRAND_DROPLET,
        onClick = null,
        modifier = Modifier.weight(1f)
      )
    }

    // 3. Calibration Model + Hardware Baseline Bar
    val calibColor = when {
      projection.possibleLeakDetected -> DropCheckerCoral
      projection.isScaleCalibrated -> DropCheckerOptimal
      else -> DropCheckerAqua
    }
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      onClick = onScaleClick,
      accentRim = calibColor,
      contentPadding = PaddingValues(Spacing.md)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.md),
          modifier = Modifier.weight(1f)
        ) {
          AquaIcon(
            type = AquaIconType.SCALE_BALANCE,
            tint = calibColor,
            size = IconSize.md
          )
          Column {
            Text(
              text = when {
                projection.possibleLeakDetected -> "High Loss Rate (Possible Leak)"
                projection.isScaleCalibrated -> "Scale-Calibrated Burn Rate"
                else -> "Ideal Gas Law Estimate"
              },
              style = AquaTextStyles.bodySm,
              color = InkPrimary
            )
            Text(
              text = when {
                projection.possibleLeakDetected -> "Measured CO2 drop exceeds normal bubble rate • check fittings"
                projection.isScaleCalibrated -> "Locked to real cylinder weight • tap to re-weigh"
                else -> "Uncalibrated baseline • weigh once to lock exact rate"
              },
              style = AquaTextStyles.caption,
              color = if (projection.possibleLeakDetected) DropCheckerCoral else InkSecondary
            )
          }
        }

        if (onScaleClick != null) {
          AquaIcon(
            type = AquaIconType.CHEVRON_RIGHT,
            tint = InkTertiary,
            size = IconSize.sm
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.md))

      // Atmospheric & Regulator Hardware Baseline Strip
      val fluidShortName = if (projection.config.fluidType == com.aquarium.co2tracker.domain.FluidType.WATER) {
        "Water"
      } else {
        "Mineral Oil"
      }
      GlassInsetWell(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.sm,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${projection.config.workingPressureValue} ${projection.config.workingPressureUnit.symbol} · $fluidShortName",
            style = AquaTextStyles.monoCaption,
            color = InkSecondary,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = "${projection.config.elevationMeters.toInt()}m (${projection.config.atmosphericPressureKpa} kPa)",
            style = AquaTextStyles.monoCaption,
            color = InkTertiary
          )
        }
      }
    }
  }
}

@Composable
fun PhotoperiodTimelineBar(
  startMinutes: Int,
  endMinutes: Int,
  nowMinuteOfDay: Int,
  modifier: Modifier = Modifier
) {
  val startFrac = (((startMinutes % 1440) + 1440) % 1440) / 1440f
  val endFrac = (((endMinutes % 1440) + 1440) % 1440) / 1440f
  val nowFrac = (((nowMinuteOfDay % 1440) + 1440) % 1440) / 1440f

  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(VesselTokens.photoperiodBarHeight)
    ) {
      val trackTop = size.height * 0.28f
      val trackHeight = StrokeTokens.timelineTrack.toPx()
      val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

      // Recessed 24-hour glass channel
      drawRoundRect(
        color = GlassWell,
        topLeft = Offset(0f, trackTop),
        size = Size(size.width, trackHeight),
        cornerRadius = corner
      )

      // Subtle 6-hour tick divisions along bottom
      listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
        val x = (size.width * frac).coerceIn(StrokeTokens.hairline.toPx(), size.width - StrokeTokens.hairline.toPx())
        drawLine(
          color = StarphireRimMid,
          start = Offset(x, trackTop + trackHeight + Spacing.xs.toPx()),
          end = Offset(x, size.height),
          strokeWidth = StrokeTokens.hairline.toPx(),
          cap = StrokeCap.Round
        )
      }

      // Active CO2 Injection Ribbon (handles standard day & overnight wrap-around)
      val activeBrush = Brush.horizontalGradient(
        listOf(StarphireCyan, PearlEmerald, PearlMintGlow)
      )
      when {
        startMinutes == endMinutes -> {
          drawRoundRect(
            brush = activeBrush,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = corner
          )
        }
        endFrac > startFrac -> {
          val x1 = size.width * startFrac
          val width = (size.width * (endFrac - startFrac)).coerceAtLeast(trackHeight)
          drawRoundRect(
            brush = activeBrush,
            topLeft = Offset(x1, trackTop),
            size = Size(width, trackHeight),
            cornerRadius = corner
          )
        }
        else -> {
          val x1 = size.width * startFrac
          drawRoundRect(
            brush = activeBrush,
            topLeft = Offset(x1, trackTop),
            size = Size(size.width - x1, trackHeight),
            cornerRadius = corner
          )
          drawRoundRect(
            brush = activeBrush,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width * endFrac, trackHeight),
            cornerRadius = corner
          )
        }
      }

      // Live "Now" Beacon Pin
      val nowX = (size.width * nowFrac).coerceIn(trackHeight, size.width - trackHeight)
      val beaconCenter = Offset(nowX, trackTop + trackHeight / 2f)
      drawCircle(
        color = PearlEmerald.copy(alpha = AlphaTokens.glowAmbient),
        radius = trackHeight * 1.45f,
        center = beaconCenter
      )
      drawCircle(
        color = InkPrimary,
        radius = trackHeight * 0.62f,
        center = beaconCenter
      )
    }

    Spacer(modifier = Modifier.height(Spacing.xxs))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      listOf("00h", "06h", "12h", "18h", "24h").forEach { hourMark ->
        Text(
          text = hourMark,
          style = AquaTextStyles.monoMicro,
          color = InkTertiary
        )
      }
    }
  }
}

@Composable
private fun TelemetryTile(
  label: String,
  value: String,
  unit: String,
  detail: String,
  accentColor: Color,
  iconType: AquaIconType,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  RimlessGlassSurface(
    modifier = modifier,
    onClick = onClick,
    contentPadding = PaddingValues(Spacing.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        style = AquaTextStyles.monoMicro,
        color = InkTertiary
      )
      AquaIcon(type = iconType, tint = accentColor, size = IconSize.sm)
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
      Text(
        text = value,
        style = AquaTextStyles.displayNumber,
        color = InkPrimary
      )
      Text(
        text = unit,
        style = AquaTextStyles.monoCaption,
        color = accentColor,
        modifier = Modifier.padding(bottom = Spacing.xs)
      )
    }

    Spacer(modifier = Modifier.height(Spacing.xs))

    Text(
      text = detail,
      style = AquaTextStyles.monoCaption,
      color = InkSecondary
    )
  }
}
