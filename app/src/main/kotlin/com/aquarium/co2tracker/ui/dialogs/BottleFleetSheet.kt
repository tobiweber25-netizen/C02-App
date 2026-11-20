package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aquarium.co2tracker.domain.BottleStatus
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BottleFleetSheet(
  allCylinders: List<CylinderProjection>,
  activeProjection: CylinderProjection?,
  onDismiss: () -> Unit,
  onSelectCylinder: (Long) -> Unit,
  onSwapInSpare: (spareId: Long, carryCalibration: Boolean) -> Unit,
  onMarkSpareRefilled: (Long) -> Unit,
  onDeleteCylinder: (Long) -> Unit,
  onAddNewBottle: () -> Unit
) {
  var carryCalibrationOnSwap by remember { mutableStateOf(activeProjection?.isScaleCalibrated == true) }
  var bottleToDelete by remember { mutableStateOf<CylinderProjection?>(null) }

  if (bottleToDelete != null) {
    val target = bottleToDelete!!
    AlertDialog(
      onDismissRequest = { bottleToDelete = null },
      shape = RoundedCornerShape(Radius.xl),
      title = { Text("Remove Bottle?", style = AquaTextStyles.cardTitle, color = InkPrimary) },
      text = {
        Text(
          text = "Remove '${target.config.cylinderName}' and its ledger history from your bottle fleet?",
          style = AquaTextStyles.bodySm,
          color = InkSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteCylinder(target.config.id)
            bottleToDelete = null
          },
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.buttonColors(containerColor = DropCheckerCoral, contentColor = OnDropCheckerCritical)
        ) {
          Text("Remove", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { bottleToDelete = null }) {
          Text("Cancel", color = InkSecondary)
        }
      },
      containerColor = GlassSurfaceElevated
    )
  }

  val needsRefillCount = allCylinders.count { it.config.bottleStatus == BottleStatus.NEEDS_REFILL }
  val hasSpareReady = allCylinders.any { it.config.bottleStatus == BottleStatus.SPARE_FULL }

  AquaModalSheet(
    title = "CO2 Bottle Fleet & Spares",
    subtitle = "Switch between tanks or one-tap swap in a full spare cylinder.",
    iconType = AquaIconType.CYLINDER_REFILL,
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
      if (needsRefillCount > 0) {
        GlassInsetWell(
          modifier = Modifier.fillMaxWidth(),
          borderColor = DropCheckerAmber.copy(alpha = AlphaTokens.rimMedium),
          contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
          Text(
            text = if (needsRefillCount == 1) {
              "1 spare cylinder is waiting to be refilled at the shop."
            } else {
              "$needsRefillCount spare cylinders are waiting to be refilled at the shop."
            },
            style = AquaTextStyles.caption,
            color = DropCheckerAmber
          )
        }
      }

      if (hasSpareReady && activeProjection?.isScaleCalibrated == true) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = carryCalibrationOnSwap,
            onCheckedChange = { carryCalibrationOnSwap = it },
            colors = CheckboxDefaults.colors(
              checkedColor = PearlEmerald,
              checkmarkColor = OnPearlEmerald,
              uncheckedColor = StarphireRimBright
            )
          )
          Text(
            text = "Carry calibrated burn rate when swapping in a spare",
            style = AquaTextStyles.caption,
            color = InkPrimary
          )
        }
      }

      allCylinders.forEach { item ->
        val isCurrent = activeProjection?.config?.id == item.config.id
        val statusAccent = when (item.config.bottleStatus) {
          BottleStatus.ACTIVE -> if (item.isBelowDangerThreshold) DropCheckerCoral else PearlEmerald
          BottleStatus.SPARE_FULL -> StarphireCyan
          BottleStatus.NEEDS_REFILL -> DropCheckerAmber
        }

        RimlessGlassSurface(
          modifier = Modifier.fillMaxWidth(),
          cornerRadius = Radius.md,
          accentRim = if (isCurrent) PearlEmerald else statusAccent,
          onClick = { onSelectCylinder(item.config.id) },
          contentPadding = PaddingValues(Spacing.md)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.config.cylinderName + if (isCurrent) " (Viewing)" else "",
                style = AquaTextStyles.bodySm,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary
              )
              Spacer(modifier = Modifier.height(Spacing.xxs))
              Text(
                text = String.format(
                  Locale.US,
                  "Tare %,dg • %,dg / %,dg CO2 (%d%%)",
                  item.config.effectiveTareWeightGrams.roundToInt(),
                  item.remainingCo2Grams.roundToInt(),
                  item.config.initialNetCo2Grams.roundToInt(),
                  item.remainingTotalPercent.roundToInt()
                ),
                style = AquaTextStyles.monoCaption,
                color = InkSecondary
              )
            }
            AquaStatusPill(
              label = when (item.config.bottleStatus) {
                BottleStatus.ACTIVE -> "ACTIVE"
                BottleStatus.SPARE_FULL -> "SPARE"
                BottleStatus.NEEDS_REFILL -> "REFILL"
              },
              accentColor = statusAccent,
              showPulseDot = item.config.bottleStatus == BottleStatus.ACTIVE
            )
          }

          Spacer(modifier = Modifier.height(Spacing.sm))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
          ) {
            when (item.config.bottleStatus) {
              BottleStatus.SPARE_FULL -> {
                Button(
                  onClick = { onSwapInSpare(item.config.id, carryCalibrationOnSwap) },
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(Radius.sm),
                  contentPadding = PaddingValues(vertical = Spacing.xs),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = PearlEmerald,
                    contentColor = OnPearlEmerald
                  )
                ) {
                  Text("Swap Onto Tank", style = AquaTextStyles.caption, fontWeight = FontWeight.Bold)
                }
              }
              BottleStatus.NEEDS_REFILL -> {
                OutlinedButton(
                  onClick = { onMarkSpareRefilled(item.config.id) },
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(Radius.sm),
                  contentPadding = PaddingValues(vertical = Spacing.xs),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = StarphireCyan),
                  border = BorderStroke(StrokeTokens.hairline, StarphireCyan.copy(alpha = AlphaTokens.rimMedium))
                ) {
                  Text("Mark Refilled (Full Spare)", style = AquaTextStyles.caption, fontWeight = FontWeight.SemiBold)
                }
              }
              BottleStatus.ACTIVE -> {
                if (!isCurrent) {
                  OutlinedButton(
                    onClick = { onSelectCylinder(item.config.id) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.sm),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PearlEmerald),
                    border = BorderStroke(StrokeTokens.hairline, PearlEmerald.copy(alpha = AlphaTokens.rimSoft))
                  ) {
                    Text("Switch to Tank", style = AquaTextStyles.caption, fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }

            if (allCylinders.size > 1 && !isCurrent) {
              OutlinedButton(
                onClick = { bottleToDelete = item },
                shape = RoundedCornerShape(Radius.sm),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DropCheckerCoral),
                border = BorderStroke(StrokeTokens.hairline, DropCheckerCoral.copy(alpha = AlphaTokens.rimSoft))
              ) {
                Text("Remove", style = AquaTextStyles.caption)
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.lg))

    Button(
      onClick = onAddNewBottle,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Radius.md),
      colors = ButtonDefaults.buttonColors(
        containerColor = PearlEmerald,
        contentColor = OnPearlEmerald
      )
    ) {
      Text(
        text = "+ Add Spare Bottle or Tank",
        style = AquaTextStyles.bodySm,
        fontWeight = FontWeight.Bold
      )
    }
  }
}
