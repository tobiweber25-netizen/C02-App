package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.aquarium.co2tracker.domain.Co2PhysicsEngine
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.domain.DropCheckerStatus
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class BpsInputMode(val label: String) {
  MANUAL("Manual"),
  TAP_MEASURE("Tap to Count"),
  PACE_GUIDE("Pace Guide")
}

@Composable
fun BpsUpdateDialog(
  projection: CylinderProjection,
  onDismiss: () -> Unit,
  onConfirm: (Double, DropCheckerStatus?) -> Unit
) {
  var bpsText by remember { mutableStateOf(String.format(Locale.US, "%.1f", projection.currentBps)) }
  var inputMode by remember { mutableStateOf(BpsInputMode.MANUAL) }
  var tapTimestamps by remember { mutableStateOf<List<Long>>(emptyList()) }
  var selectedDropChecker by remember { mutableStateOf<DropCheckerStatus?>(null) }
  var pacePulseOn by remember { mutableStateOf(false) }

  val haptic = LocalHapticFeedback.current
  val parsedBps = Co2PhysicsEngine.parseDecimalInput(bpsText)
  val currentBps = parsedBps?.coerceAtLeast(0.0) ?: projection.currentBps
  val tapResult = remember(tapTimestamps) {
    Co2PhysicsEngine.calculateBpsFromTapTimestamps(tapTimestamps)
  }

  val whatIf = remember(projection, currentBps) {
    Co2PhysicsEngine.previewWhatIfChange(
      projection = projection,
      newBps = currentBps
    )
  }
  val isValid = parsedBps != null && parsedBps >= 0.0
  val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

  // Visual + tactile pace guide metronome at the exact selected BPS
  LaunchedEffect(inputMode, currentBps) {
    if (inputMode == BpsInputMode.PACE_GUIDE && currentBps >= 0.2) {
      val periodMs = (1000.0 / currentBps.coerceIn(0.2, 8.0)).toLong().coerceIn(125L, 5000L)
      while (true) {
        pacePulseOn = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        delay((periodMs / 3L).coerceIn(50L, 180L))
        pacePulseOn = false
        delay((periodMs - (periodMs / 3L).coerceIn(50L, 180L)).coerceAtLeast(60L))
      }
    } else {
      pacePulseOn = false
    }
  }

  AquaModalSheet(
    title = "Tune Bubble Rate",
    subtitle = "Currently ${projection.currentBps} BPS. Gas consumed up to this minute is banked automatically.",
    iconType = AquaIconType.BUBBLE_VALVE,
    accentColor = PearlEmerald,
    onDismiss = onDismiss
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
      // Mode Switcher: Manual | Tap to Count | Pace Guide
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
      ) {
        BpsInputMode.entries.forEach { mode ->
          FilterChip(
            selected = inputMode == mode,
            onClick = { inputMode = mode },
            label = { Text(mode.label, style = AquaTextStyles.caption) },
            colors = aquaChipColors(PearlEmerald),
            modifier = Modifier.weight(1f)
          )
        }
      }

      when (inputMode) {
        BpsInputMode.MANUAL -> {
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
        }

        BpsInputMode.TAP_MEASURE -> {
          // Musician-style Tap-to-Measure Bubble Counter Pad
          val tapShape = RoundedCornerShape(Radius.lg)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(tapShape)
              .background(
                Brush.verticalGradient(
                  listOf(
                    PearlEmerald.copy(alpha = AlphaTokens.mediumTint),
                    GlassWell
                  )
                )
              )
              .border(
                StrokeTokens.medium,
                if (tapResult?.isSteady == true) PearlEmerald else StarphireRimBright,
                tapShape
              )
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val now = System.currentTimeMillis()
                val updated = (tapTimestamps + now).takeLast(14)
                tapTimestamps = updated
                Co2PhysicsEngine.calculateBpsFromTapTimestamps(updated)?.let { measured ->
                  bpsText = String.format(Locale.US, "%.1f", measured.bps)
                }
              }
              .padding(vertical = Spacing.xl, horizontal = Spacing.normal),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              AquaStatusPill(
                label = when {
                  tapResult == null -> "TAP EACH BUBBLE"
                  tapResult.isSteady -> "STEADY RHYTHM (${tapResult.tapCount} TAPS)"
                  else -> "KEEP TAPPING (${tapResult.tapCount} TAPS)"
                },
                accentColor = if (tapResult?.isSteady == true) DropCheckerOptimal else StarphireCyan,
                showPulseDot = tapResult != null
              )
              Spacer(modifier = Modifier.height(Spacing.sm))
              Text(
                text = if (tapResult != null) String.format(Locale.US, "%.1f BPS", tapResult.bps) else "Tap Here",
                style = AquaTextStyles.displayNumber,
                color = InkPrimary
              )
              Spacer(modifier = Modifier.height(Spacing.xxs))
              Text(
                text = "Tap in time with 5–8 rising bubbles to measure automatically",
                style = AquaTextStyles.caption,
                color = InkSecondary
              )
            }
          }

          if (tapTimestamps.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              TextButton(onClick = { tapTimestamps = emptyList() }) {
                Text("Reset Taps", color = InkSecondary, style = AquaTextStyles.caption)
              }
            }
          }
        }

        BpsInputMode.PACE_GUIDE -> {
          // Visual + Haptic Metronome to dial needle valve to match target BPS
          GlassInsetWell(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (pacePulseOn) PearlEmerald else StarphireRimMid,
            contentPadding = PaddingValues(Spacing.normal)
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              AquaStatusPill(
                label = if (pacePulseOn) "BUBBLE PULSE" else "MATCH NEEDLE VALVE",
                accentColor = if (pacePulseOn) PearlEmerald else StarphireCyan,
                showPulseDot = pacePulseOn
              )
              Spacer(modifier = Modifier.height(Spacing.sm))
              Text(
                text = String.format(Locale.US, "%.1f BPS Target", currentBps),
                style = AquaTextStyles.displayNumber,
                color = if (pacePulseOn) PearlEmerald else InkPrimary
              )
              Spacer(modifier = Modifier.height(Spacing.xxs))
              Text(
                text = "Turn your needle valve until real bubbles match the pulse beat",
                style = AquaTextStyles.caption,
                color = InkSecondary
              )
              Spacer(modifier = Modifier.height(Spacing.md))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
              ) {
                listOf(-0.2, -0.1, 0.1, 0.2).forEach { delta ->
                  val label = if (delta > 0) String.format(Locale.US, "+%.1f", delta) else String.format(Locale.US, "%.1f", delta)
                  FilledTonalButton(
                    onClick = {
                      val cur = Co2PhysicsEngine.parseDecimalInput(bpsText) ?: projection.currentBps
                      val next = (cur + delta).coerceAtLeast(0.2)
                      bpsText = String.format(Locale.US, "%.1f", next)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.md),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                    colors = ButtonDefaults.filledTonalButtonColors(
                      containerColor = GlassSurfaceElevated,
                      contentColor = PearlEmerald
                    )
                  ) {
                    Text(text = label, style = AquaTextStyles.monoCaption)
                  }
                }
              }
            }
          }
        }
      }

      // Live Mini Glass Bubble Counter + What-If Refill Date Impact Preview
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

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
              text = "WHAT-IF RUNWAY PREVIEW",
              style = AquaTextStyles.monoMicro,
              color = InkTertiary
            )
            Text(
              text = String.format(Locale.US, "%.2f g / day", whatIf.newDailyBurnGrams),
              style = AquaTextStyles.monoLarge,
              color = PearlEmerald
            )
            if (currentBps > 0.0 && !projection.isBelowDangerThreshold) {
              val deltaDays = whatIf.cutoffDeltaDays.roundToInt()
              val deltaTag = when {
                abs(deltaDays) == 0 -> "no change"
                deltaDays > 0 -> "+${deltaDays}d longer"
                else -> "${deltaDays}d earlier"
              }
              val deltaColor = when {
                deltaDays < 0 -> DropCheckerAmber
                deltaDays > 0 -> DropCheckerOptimal
                else -> InkSecondary
              }
              Text(
                text = "Cutoff: ${dateFormatter.format(Date(whatIf.newCutoffMillis))} ($deltaTag)",
                style = AquaTextStyles.caption,
                color = deltaColor
              )
              Text(
                text = "Empty (0g): ${dateFormatter.format(Date(whatIf.newEmptyMillis))}",
                style = AquaTextStyles.caption,
                color = InkSecondary
              )
            } else if (currentBps == 0.0) {
              Text(
                text = "Sets tracking to paused (0 BPS)",
                style = AquaTextStyles.caption,
                color = StarphireCyan
              )
            }
          }
        }
      }

      // Optional Drop-Checker Tuning Note
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
          text = "DROP CHECKER READING (OPTIONAL)",
          style = AquaTextStyles.monoMicro,
          color = InkTertiary
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
          DropCheckerStatus.entries.forEach { status ->
            val accent = when (status) {
              DropCheckerStatus.LOW_BLUE -> DropCheckerAqua
              DropCheckerStatus.OPTIMAL_GREEN -> DropCheckerOptimal
              DropCheckerStatus.HIGH_YELLOW -> DropCheckerAmber
            }
            val selected = selectedDropChecker == status
            FilterChip(
              selected = selected,
              onClick = {
                selectedDropChecker = if (selected) null else status
              },
              label = { Text(status.shortLabel, style = AquaTextStyles.monoMicro) },
              colors = aquaChipColors(accent),
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.lg))

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
          if (v != null && v >= 0.0) onConfirm(v, selectedDropChecker)
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

    drawRoundRect(
      brush = Brush.verticalGradient(
        listOf(VesselLiquidBright.copy(alpha = 0.22f), VesselLiquidDeep.copy(alpha = 0.50f))
      ),
      topLeft = Offset(w * 0.14f, h * 0.12f),
      size = Size(w * 0.72f, h * 0.78f),
      cornerRadius = corner
    )

    drawRoundRect(
      color = StarphireRimBright,
      topLeft = Offset(w * 0.12f, h * 0.08f),
      size = Size(w * 0.76f, h * 0.84f),
      cornerRadius = corner,
      style = Stroke(width = StrokeTokens.medium.toPx())
    )

    drawLine(
      color = InkPrimary.copy(alpha = 0.28f),
      start = Offset(w * 0.26f, h * 0.20f),
      end = Offset(w * 0.26f, h * 0.76f),
      strokeWidth = StrokeTokens.hairline.toPx(),
      cap = StrokeCap.Round
    )

    if (bps > 0.0) {
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
}
