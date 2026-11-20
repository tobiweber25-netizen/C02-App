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
import androidx.compose.ui.graphics.Color
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
    verticalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    AquaSectionHeader(
      title = "Regulator Controls",
      trailingLabel = "IMMUTABLE LEDGER"
    )

    // 2x2 Precision Aquascaping Control Deck
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      ControlDeckButton(
        iconType = AquaIconType.BUBBLE_VALVE,
        title = "Bubble Rate",
        subtitle = "Tune BPS flow",
        accentColor = PearlEmerald,
        onClick = onBpsClick,
        modifier = Modifier.weight(1f)
      )

      ControlDeckButton(
        iconType = AquaIconType.PHOTOPERIOD_ARC,
        title = "Solenoid Hours",
        subtitle = "Adjust daily timer",
        accentColor = StarphireCyan,
        onClick = onScheduleClick,
        modifier = Modifier.weight(1f)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      ControlDeckButton(
        iconType = AquaIconType.DOWNTIME_PAUSE,
        title = "Log Downtime",
        subtitle = "Outage or trim",
        accentColor = DropCheckerAmber,
        onClick = onDowntimeClick,
        modifier = Modifier.weight(1f)
      )

      ControlDeckButton(
        iconType = AquaIconType.SCALE_BALANCE,
        title = "Scale Check-In",
        subtitle = "Weight calibration",
        accentColor = DropCheckerOptimal,
        onClick = onScaleClick,
        modifier = Modifier.weight(1f)
      )
    }

    // Refined Full-Width Cylinder Refill & Reset Bar
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      cornerRadius = Radius.md,
      elevated = true,
      accentRim = PearlEmerald,
      onClick = onRefillResetClick,
      contentPadding = PaddingValues(horizontal = Spacing.normal, vertical = Spacing.md)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
          Box(
            modifier = Modifier
              .size(IconSize.badge)
              .clip(RoundedCornerShape(Radius.sm))
              .background(PearlEmerald.copy(alpha = AlphaTokens.subtleTint))
              .border(
                StrokeTokens.hairline,
                PearlEmerald.copy(alpha = AlphaTokens.rimSoft),
                RoundedCornerShape(Radius.sm)
              ),
            contentAlignment = Alignment.Center
          ) {
            AquaIcon(
              type = AquaIconType.CYLINDER_REFILL,
              tint = PearlEmerald,
              size = IconSize.md
            )
          }

          Column {
            Text(
              text = "New Refill / Reset Cylinder",
              style = AquaTextStyles.bodySm,
              color = InkPrimary
            )
            Text(
              text = "Start a fresh bottle cycle and archive ledger",
              style = AquaTextStyles.caption,
              color = InkSecondary
            )
          }
        }

        AquaIcon(
          type = AquaIconType.CHEVRON_RIGHT,
          tint = PearlEmerald,
          size = IconSize.sm
        )
      }
    }
  }
}

@Composable
private fun ControlDeckButton(
  iconType: AquaIconType,
  title: String,
  subtitle: String,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  RimlessGlassSurface(
    modifier = modifier,
    cornerRadius = Radius.md,
    onClick = onClick,
    contentPadding = PaddingValues(Spacing.md)
  ) {
    Box(
      modifier = Modifier
        .size(IconSize.badge)
        .clip(RoundedCornerShape(Radius.sm))
        .background(accentColor.copy(alpha = AlphaTokens.subtleTint))
        .border(
          StrokeTokens.hairline,
          accentColor.copy(alpha = AlphaTokens.rimSoft),
          RoundedCornerShape(Radius.sm)
        ),
      contentAlignment = Alignment.Center
    ) {
      AquaIcon(
        type = iconType,
        tint = accentColor,
        size = IconSize.md
      )
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    Text(
      text = title,
      style = AquaTextStyles.bodySm,
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
