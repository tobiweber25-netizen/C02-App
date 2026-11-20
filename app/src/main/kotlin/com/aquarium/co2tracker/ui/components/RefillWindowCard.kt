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
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class RefillBannerState(
  val accent: Color,
  val headline: String,
  val subtext: String,
  val reagentTag: String
)

private fun formatDaysLabel(days: Double): String {
  if (!days.isFinite() || days <= 0.0) return "Under 1 day"
  if (days < 1.0) return "Under 1 day"
  val count = ceil(days).toInt()
  return if (count == 1) "1 day" else "$count days"
}

@Composable
fun RefillWindowCard(
  projection: CylinderProjection,
  modifier: Modifier = Modifier,
  historicalSealDriftPercent: Int? = null
) {
  val dateFormatter = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
  val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())
  val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

  val depletionDateStr = dateFormatter.format(Date(projection.depletionTimestampMillis))
  val depletionYearStr = yearFormatter.format(Date(projection.depletionTimestampMillis))
  val emptyDateStr = dateFormatter.format(Date(projection.emptyTimestampMillis))
  val emptyYearStr = yearFormatter.format(Date(projection.emptyTimestampMillis))
  val alertDateStr = dateFormatter.format(Date(projection.alertTimestampMillis))
  val alertTimeStr = timeFormatter.format(Date(projection.alertTimestampMillis))
  val conservativeDateStr = dateFormatter.format(Date(projection.conservativeDepletionTimestampMillis))

  val daysToRefill = projection.daysUntilRefillWindow
  val effectiveCutoffDays = if (projection.isScaleCalibrated) {
    projection.daysUntilDangerThreshold
  } else {
    projection.conservativeDaysUntilDanger
  }
  val cutoffPercentInt = projection.config.dangerThresholdPercent.roundToInt()

  val state = when {
    projection.isEmpty -> RefillBannerState(
      accent = DropCheckerCoral,
      headline = "Cylinder likely empty",
      subtext = "Estimated 0g left. Weigh canister and refill immediately.",
      reagentTag = "EMPTY"
    )
    projection.isBelowDangerThreshold -> RefillBannerState(
      accent = DropCheckerCoral,
      headline = "Below safety floor",
      subtext = "End-of-tank dump risk. Est. empty around $emptyDateStr (${formatDaysLabel(projection.daysUntilEmpty)}).",
      reagentTag = "CRITICAL"
    )
    projection.isPaused -> RefillBannerState(
      accent = DropCheckerAmber,
      headline = "Live CO2 pause active",
      subtext = String.format(
        Locale.US,
        "%.2f active solenoid hrs missed so far. Tap Resume when CO2 is back on.",
        projection.pausedMissedSolenoidHours
      ),
      reagentTag = "PAUSED"
    )
    !projection.hasActiveBurn -> RefillBannerState(
      accent = DropCheckerAqua,
      headline = "Tracking paused (0 BPS)",
      subtext = "Bubble rate is set to 0 BPS. Set active BPS to resume alerts.",
      reagentTag = "PAUSED"
    )
    projection.isInRefillWindow -> RefillBannerState(
      accent = DropCheckerAmber,
      headline = "Refill window open",
      subtext = if (projection.isScaleCalibrated) {
        "${formatDaysLabel(effectiveCutoffDays)} to $cutoffPercentInt% cutoff • empty around $emptyDateStr."
      } else {
        "${formatDaysLabel(effectiveCutoffDays)} to earliest cutoff — weigh bottle to confirm."
      },
      reagentTag = "REFILL NOW"
    )
    else -> {
      val leadDays = projection.config.safeLeadTimeDays
      RefillBannerState(
        accent = DropCheckerOptimal,
        headline = "${formatDaysLabel(daysToRefill)} to refill window",
        subtext = if (projection.isScaleCalibrated) {
          "Scale-locked alert armed $leadDays d before $cutoffPercentInt% cutoff."
        } else {
          "Uncalibrated alert armed $leadDays d before earliest cutoff."
        },
        reagentTag = "OPTIMAL"
      )
    }
  }

  // Fractions along [0..1] representing total cylinder capacity
  val totalNet = projection.config.initialNetCo2Grams.coerceAtLeast(1.0)
  val cutoffFraction = (projection.config.dangerThresholdGrams / totalNet).toFloat().coerceIn(0.05f, 0.50f)
  val alertGrams = projection.config.dangerThresholdGrams +
    (projection.config.safeLeadTimeDays * projection.dailyBurnRateGrams)
  val alertFraction = (alertGrams / totalNet).toFloat().coerceIn(cutoffFraction + 0.03f, 0.92f)
  val currentFraction = (projection.remainingTotalPercent / 100.0).toFloat().coerceIn(0f, 1f)

  RimlessGlassSurface(
    modifier = modifier.fillMaxWidth(),
    accentRim = if (projection.possibleLeakDetected) DropCheckerCoral else state.accent
  ) {
    // Top Drop Checker Status Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
      DropCheckerGlyph(reagentColor = state.accent)

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = state.headline,
            style = AquaTextStyles.cardTitle,
            color = InkPrimary
          )
          AquaStatusPill(
            label = state.reagentTag,
            accentColor = state.accent,
            showPulseDot = false
          )
        }
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = state.subtext,
          style = AquaTextStyles.caption,
          color = InkSecondary
        )
      }
    }

    // Possible CO2 Leak Warning (when calibrated burn dramatically exceeds physical bubble maximum)
    if (projection.possibleLeakDetected) {
      Spacer(modifier = Modifier.height(Spacing.md))
      GlassInsetWell(
        modifier = Modifier.fillMaxWidth(),
        borderColor = DropCheckerCoral.copy(alpha = AlphaTokens.rimMedium),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
          Text(
            text = "POSSIBLE CO2 LEAK DETECTED",
            style = AquaTextStyles.monoMicro,
            color = DropCheckerCoral
          )
          Text(
            text = "Measured weight loss is much faster than normal for your BPS & pressure. Inspect regulator washer, solenoid, and tubing.",
            style = AquaTextStyles.caption,
            color = InkPrimary
          )
        }
      }
    }

    // Historical Seal-Wear / Drift Warning across Refill Cycles
    if (!projection.possibleLeakDetected && historicalSealDriftPercent != null && historicalSealDriftPercent >= 25) {
      Spacer(modifier = Modifier.height(Spacing.md))
      GlassInsetWell(
        modifier = Modifier.fillMaxWidth(),
        borderColor = DropCheckerAmber.copy(alpha = AlphaTokens.rimMedium),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
          Text(
            text = "SEAL WEAR NOTICE (+$historicalSealDriftPercent% VS PAST FILLS)",
            style = AquaTextStyles.monoMicro,
            color = DropCheckerAmber
          )
          Text(
            text = "Gas use per bubble is higher than your archived refills. Check your regulator washer and O-rings.",
            style = AquaTextStyles.caption,
            color = InkSecondary
          )
        }
      }
    }

    // Recommended Scale Check-In Date (when still uncalibrated)
    val checkInTs = projection.recommendedCheckInTimestampMillis
    if (!projection.isScaleCalibrated && projection.hasActiveBurn && !projection.isBelowDangerThreshold && checkInTs != null) {
      val checkInNow = checkInTs <= projection.evaluatedAtMillis
      val checkInDateStr = dateFormatter.format(Date(checkInTs))
      Spacer(modifier = Modifier.height(Spacing.md))
      GlassInsetWell(
        modifier = Modifier.fillMaxWidth(),
        borderColor = DropCheckerAqua.copy(alpha = AlphaTokens.rimMedium),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
          Text(
            text = if (checkInNow) "SCALE CHECK-IN READY NOW" else "RECOMMENDED SCALE CHECK-IN",
            style = AquaTextStyles.monoMicro,
            color = DropCheckerAqua
          )
          Text(
            text = if (checkInNow) {
              "Weigh your canister now to lock in your exact burn rate. Earliest uncalibrated cutoff: $conservativeDateStr."
            } else {
              "Weigh bottle around $checkInDateStr to lock in real burn rate. Earliest uncalibrated cutoff: $conservativeDateStr."
            },
            style = AquaTextStyles.caption,
            color = InkSecondary
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.normal))

    // Visual Depletion Horizon Progress Track
    Column(modifier = Modifier.fillMaxWidth()) {
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(VesselTokens.horizonBarHeight)
      ) {
        val barH = size.height
        val barRadius = CornerRadius(barH / 2f, barH / 2f)

        // Base recessed track
        drawRoundRect(
          color = GlassWell,
          size = size,
          cornerRadius = barRadius
        )

        // EOTD Danger Zone Band [0 .. cutoffFraction]
        drawRoundRect(
          color = DropCheckerCoral.copy(alpha = 0.28f),
          size = Size(size.width * cutoffFraction, barH),
          cornerRadius = barRadius
        )

        // Refill Lead Warning Zone [cutoffFraction .. alertFraction]
        val warnStart = size.width * cutoffFraction
        val warnWidth = (size.width * (alertFraction - cutoffFraction)).coerceAtLeast(0f)
        drawRect(
          color = DropCheckerAmber.copy(alpha = 0.24f),
          topLeft = Offset(warnStart, 0f),
          size = Size(warnWidth, barH)
        )

        // Active Remaining Reserve Fill [0 .. currentFraction]
        val fillW = (size.width * currentFraction).coerceAtLeast(barH)
        drawRoundRect(
          brush = Brush.horizontalGradient(
            colors = listOf(state.accent.copy(alpha = 0.65f), state.accent)
          ),
          size = Size(fillW.coerceAtMost(size.width), barH),
          cornerRadius = barRadius
        )

        // Alert Marker Notch
        val alertX = size.width * alertFraction
        drawLine(
          color = DropCheckerAmber,
          start = Offset(alertX, 0f),
          end = Offset(alertX, barH),
          strokeWidth = StrokeTokens.bold.toPx()
        )

        // Cutoff Marker Notch
        val cutoffX = size.width * cutoffFraction
        drawLine(
          color = DropCheckerCoral,
          start = Offset(cutoffX, 0f),
          end = Offset(cutoffX, barH),
          strokeWidth = StrokeTokens.bold.toPx()
        )

        // Luminous Current Position Beacon
        val markerX = (size.width * currentFraction).coerceIn(barH / 2f, size.width - barH / 2f)
        drawCircle(
          color = InkPrimary,
          radius = barH * 0.45f,
          center = Offset(markerX, barH / 2f)
        )
      }

      Spacer(modifier = Modifier.height(Spacing.xs))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "CUTOFF $cutoffPercentInt%",
          style = AquaTextStyles.monoMicro,
          color = DropCheckerCoral.copy(alpha = 0.85f)
        )
        Text(
          text = "ALERT -${projection.config.safeLeadTimeDays}D",
          style = AquaTextStyles.monoMicro,
          color = DropCheckerAmber.copy(alpha = 0.9f)
        )
        Text(
          text = "FULL 100%",
          style = AquaTextStyles.monoMicro,
          color = InkTertiary
        )
      }
    }

    Spacer(modifier = Modifier.height(Spacing.normal))

    // Dual Recessed Date Wells: Refill Alarm & Safety Cutoff
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      GlassInsetWell(
        modifier = Modifier.weight(1f),
        borderColor = state.accent.copy(alpha = AlphaTokens.rimSoft)
      ) {
        Column {
          Text(
            text = "REFILL ALARM",
            style = AquaTextStyles.monoMicro,
            color = InkTertiary
          )
          Spacer(modifier = Modifier.height(Spacing.xs))
          Text(
            text = if (projection.hasActiveBurn || projection.isBelowDangerThreshold) alertDateStr else "Paused",
            style = AquaTextStyles.monoBody,
            color = state.accent
          )
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = when {
              projection.isPaused -> "Paused — resume to arm"
              projection.hasActiveBurn && projection.alertShiftedForShopDays ->
                "At $alertTimeStr • shifted for shop days"
              projection.hasActiveBurn ->
                "At $alertTimeStr • ${projection.config.safeLeadTimeDays}d lead"
              else -> "0 BPS active"
            },
            style = AquaTextStyles.caption,
            color = if (projection.alertShiftedForShopDays) StarphireCyan else InkSecondary
          )
        }
      }

      GlassInsetWell(
        modifier = Modifier.weight(1f),
        borderColor = GlassWellBorder
      ) {
        Column {
          Text(
            text = "SAFETY CUTOFF",
            style = AquaTextStyles.monoMicro,
            color = InkTertiary
          )
          Spacer(modifier = Modifier.height(Spacing.xs))
          Text(
            text = if (projection.hasActiveBurn || projection.isBelowDangerThreshold) {
              "$depletionDateStr $depletionYearStr"
            } else {
              "Paused"
            },
            style = AquaTextStyles.monoBody,
            color = InkPrimary
          )
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = "At $cutoffPercentInt% (${projection.config.dangerThresholdGrams.roundToInt()}g)",
            style = AquaTextStyles.caption,
            color = InkSecondary
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Full Empty (0g) Estimate Well
    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      borderColor = if (projection.isEmpty) DropCheckerCoral.copy(alpha = AlphaTokens.rimMedium) else GlassWellBorder,
      contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "ESTIMATED EMPTY (0g)",
            style = AquaTextStyles.monoMicro,
            color = InkTertiary
          )
          Spacer(modifier = Modifier.height(Spacing.xxs))
          Text(
            text = when {
              projection.isEmpty -> "Now — check canister immediately"
              !projection.hasActiveBurn -> "Paused (0 BPS)"
              else -> "$emptyDateStr $emptyYearStr"
            },
            style = AquaTextStyles.monoBody,
            color = if (projection.isEmpty || projection.isBelowDangerThreshold) DropCheckerCoral else InkPrimary
          )
        }
        if (projection.hasActiveBurn && !projection.isEmpty) {
          Text(
            text = formatDaysLabel(projection.daysUntilEmpty),
            style = AquaTextStyles.monoCaption,
            color = InkSecondary
          )
        }
      }
    }
  }
}
