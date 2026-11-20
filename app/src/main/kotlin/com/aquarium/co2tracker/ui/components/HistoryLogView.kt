package com.aquarium.co2tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.aquarium.co2tracker.domain.DropCheckerStatus
import com.aquarium.co2tracker.domain.EvaluatedLedgerSegment
import com.aquarium.co2tracker.domain.LedgerEvent
import com.aquarium.co2tracker.domain.LedgerEventType
import com.aquarium.co2tracker.domain.RefillArchiveEntry
import com.aquarium.co2tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class HistorySection {
  LEDGER,
  ARCHIVE
}

@Composable
fun HistoryLogView(
  events: List<LedgerEvent>,
  onDeleteEvent: (Long) -> Unit,
  modifier: Modifier = Modifier,
  evaluatedSegments: List<EvaluatedLedgerSegment> = emptyList(),
  refillArchive: List<RefillArchiveEntry> = emptyList(),
  onDeleteArchiveEntry: ((Long) -> Unit)? = null
) {
  var selectedSection by remember { mutableStateOf(HistorySection.LEDGER) }
  var eventToDelete by remember { mutableStateOf<LedgerEvent?>(null) }
  val segmentByEventId = remember(evaluatedSegments) {
    evaluatedSegments.associateBy { it.event.id }
  }

  if (eventToDelete != null) {
    val evt = eventToDelete!!
    AlertDialog(
      onDismissRequest = { eventToDelete = null },
      shape = RoundedCornerShape(Radius.xl),
      title = {
        Text(
          text = "Delete Ledger Event?",
          style = AquaTextStyles.cardTitle,
          color = InkPrimary
        )
      },
      text = {
        Text(
          text = "Removing '${evt.eventType.displayTitle}' will automatically recalculate all past and future CO2 burn projections.",
          style = AquaTextStyles.bodySm,
          color = InkSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteEvent(evt.id)
            eventToDelete = null
          },
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.buttonColors(
            containerColor = DropCheckerCoral,
            contentColor = OnDropCheckerCritical
          )
        ) {
          Text(
            text = "Delete & Recalculate",
            fontWeight = FontWeight.SemiBold
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { eventToDelete = null }) {
          Text(text = "Cancel", color = InkSecondary)
        }
      },
      containerColor = GlassSurfaceElevated,
      titleContentColor = InkPrimary,
      textContentColor = InkSecondary
    )
  }

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    // Clean Segmented Switcher: Current Ledger | Past Refills Archive
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      FilterChip(
        selected = selectedSection == HistorySection.LEDGER,
        onClick = { selectedSection = HistorySection.LEDGER },
        label = { Text("Current Ledger (${events.size})", style = AquaTextStyles.caption) },
        colors = aquaChipColors(PearlEmerald),
        modifier = Modifier.weight(1f)
      )
      FilterChip(
        selected = selectedSection == HistorySection.ARCHIVE,
        onClick = { selectedSection = HistorySection.ARCHIVE },
        label = { Text("Past Refills (${refillArchive.size})", style = AquaTextStyles.caption) },
        colors = aquaChipColors(StarphireCyan),
        modifier = Modifier.weight(1f)
      )
    }

    if (selectedSection == HistorySection.LEDGER) {
      AquaSectionHeader(
        title = "Immutable Event Ledger",
        trailingLabel = "${events.size} RECORDS",
        trailingColor = PearlEmerald
      )

      if (events.isEmpty()) {
        EmptyHistoryCard(
          title = "No Ledger Entries Yet",
          subtitle = "Every bubble-rate tweak, timer update, downtime pause, and scale weigh-in is logged here automatically."
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = Spacing.dockClearance)
        ) {
          itemsIndexed(events, key = { _, item -> item.id }) { index, event ->
            LedgerTimelineRow(
              event = event,
              segment = segmentByEventId[event.id],
              isFirst = index == 0,
              isLast = index == events.lastIndex,
              onDeleteClick = { eventToDelete = event }
            )
          }
        }
      }
    } else {
      AquaSectionHeader(
        title = "Completed Refill Archive",
        trailingLabel = "${refillArchive.size} CYCLES",
        trailingColor = StarphireCyan
      )

      if (refillArchive.isEmpty()) {
        EmptyHistoryCard(
          title = "No Archived Refills Yet",
          subtitle = "When you refill a cylinder or swap in a spare, your finished fill automatically archives here with total days lasted and cost per month."
        )
      } else {
        val avgDays = refillArchive.map { it.durationDays }.average()
        val monthlyCosts = refillArchive.mapNotNull { it.costPerMonth }
        val avgMonthlyCost = if (monthlyCosts.isNotEmpty()) monthlyCosts.average() else null

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(Spacing.md),
          contentPadding = PaddingValues(bottom = Spacing.dockClearance)
        ) {
          item {
            GlassInsetWell(
              modifier = Modifier.fillMaxWidth(),
              borderColor = StarphireCyan.copy(alpha = AlphaTokens.rimSoft),
              contentPadding = PaddingValues(Spacing.md)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column {
                  Text("AVG FILL LIFESPAN", style = AquaTextStyles.monoMicro, color = InkTertiary)
                  Spacer(modifier = Modifier.height(Spacing.xxs))
                  Text(
                    text = String.format(Locale.US, "%.0f days", avgDays),
                    style = AquaTextStyles.monoLarge,
                    color = PearlEmerald
                  )
                }
                if (avgMonthlyCost != null) {
                  Column(horizontalAlignment = Alignment.End) {
                    Text("AVG COST / MONTH", style = AquaTextStyles.monoMicro, color = InkTertiary)
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                      text = String.format(Locale.US, "%.1f / mo", avgMonthlyCost),
                      style = AquaTextStyles.monoLarge,
                      color = StarphireCyan
                    )
                  }
                }
              }
            }
          }

          items(refillArchive, key = { it.id }) { entry ->
            RefillArchiveCard(
              entry = entry,
              onDelete = onDeleteArchiveEntry?.let { cb -> { cb(entry.id) } }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ColumnScope.EmptyHistoryCard(
  title: String,
  subtitle: String
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .weight(1f),
    contentAlignment = Alignment.Center
  ) {
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(Spacing.xl)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
      ) {
        Box(
          modifier = Modifier
            .size(IconSize.xl)
            .clip(CircleShape)
            .background(PearlEmerald.copy(alpha = AlphaTokens.subtleTint))
            .border(
              StrokeTokens.hairline,
              PearlEmerald.copy(alpha = AlphaTokens.rimSoft),
              CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          AquaIcon(type = AquaIconType.TAB_LEDGER, tint = PearlEmerald, size = IconSize.md)
        }
        Text(
          text = title,
          style = AquaTextStyles.cardTitle,
          color = InkPrimary
        )
        Text(
          text = subtitle,
          style = AquaTextStyles.bodySm,
          color = InkSecondary
        )
      }
    }
  }
}

@Composable
private fun RefillArchiveCard(
  entry: RefillArchiveEntry,
  onDelete: (() -> Unit)?
) {
  val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
  val rangeStr = "${dateFmt.format(Date(entry.startedAtMillis))} → ${dateFmt.format(Date(entry.endedAtMillis))}"

  RimlessGlassSurface(
    modifier = Modifier.fillMaxWidth(),
    cornerRadius = Radius.md,
    accentRim = if (entry.wasCalibrated) DropCheckerOptimal else StarphireCyan,
    contentPadding = PaddingValues(Spacing.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.cylinderName,
          style = AquaTextStyles.bodySm,
          fontWeight = FontWeight.SemiBold,
          color = InkPrimary
        )
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = rangeStr,
          style = AquaTextStyles.monoCaption,
          color = InkTertiary
        )
      }
      if (onDelete != null) {
        Box(
          modifier = Modifier
            .size(IconSize.lg)
            .clip(RoundedCornerShape(Radius.xs))
            .background(DropCheckerCoral.copy(alpha = AlphaTokens.subtleTint))
            .clickable(onClick = onDelete),
          contentAlignment = Alignment.Center
        ) {
          AquaIcon(type = AquaIconType.TRASH, tint = DropCheckerCoral, size = IconSize.xs)
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      cornerRadius = Radius.sm,
      contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text("LASTED", style = AquaTextStyles.monoMicro, color = InkTertiary)
          Text(
            text = String.format(Locale.US, "%.0f days", entry.durationDays),
            style = AquaTextStyles.monoBody,
            color = PearlEmerald
          )
        }
        Column {
          Text("CO2 USED", style = AquaTextStyles.monoMicro, color = InkTertiary)
          Text(
            text = String.format(Locale.US, "%,dg", entry.consumedGrams.roundToInt()),
            style = AquaTextStyles.monoBody,
            color = InkPrimary
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text("AVG BURN", style = AquaTextStyles.monoMicro, color = InkTertiary)
          Text(
            text = String.format(Locale.US, "%.1f g/d", entry.avgDailyBurnGrams),
            style = AquaTextStyles.monoBody,
            color = StarphireCyan
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.xs))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = if (entry.wasCalibrated && entry.calibratedGramsPerBubble != null) {
          String.format(Locale.US, "Scale locked: %.5fg/bubble", entry.calibratedGramsPerBubble)
        } else {
          "Theoretical baseline"
        },
        style = AquaTextStyles.caption,
        color = InkSecondary
      )
      entry.costPerMonth?.let { perMo ->
        Text(
          text = String.format(Locale.US, "%.1f/mo", perMo),
          style = AquaTextStyles.monoCaption,
          color = StarphireCyan
        )
      }
    }
  }
}

@Composable
private fun LedgerTimelineRow(
  event: LedgerEvent,
  segment: EvaluatedLedgerSegment?,
  isFirst: Boolean,
  isLast: Boolean,
  onDeleteClick: () -> Unit
) {
  val dateFormatter = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault())
  val dateStr = dateFormatter.format(Date(event.timestampMillis))

  val (accentColor, iconType) = when (event.eventType) {
    LedgerEventType.SETUP -> PearlEmerald to AquaIconType.CYLINDER_REFILL
    LedgerEventType.BPS_CHANGE -> PearlEmerald to AquaIconType.BUBBLE_VALVE
    LedgerEventType.SCHEDULE_CHANGE -> StarphireCyan to AquaIconType.PHOTOPERIOD_ARC
    LedgerEventType.DOWNTIME_OVERRIDE -> DropCheckerAmber to AquaIconType.DOWNTIME_PAUSE
    LedgerEventType.SCALE_CALIBRATION -> DropCheckerOptimal to AquaIconType.SCALE_BALANCE
  }

  val detailValue = when (event.eventType) {
    LedgerEventType.SETUP -> "Initial fill @ ${event.bps} BPS"
    LedgerEventType.BPS_CHANGE -> "Flow set to ${event.bps} BPS"
    LedgerEventType.SCHEDULE_CHANGE ->
      "Window ${formatMinuteOfDay(event.solenoidStartMinutes)} – ${formatMinuteOfDay(event.solenoidEndMinutes)}"
    LedgerEventType.DOWNTIME_OVERRIDE -> {
      val sign = if (event.downtimeHoursDelta >= 0) "+" else ""
      "Override $sign${event.downtimeHoursDelta} hrs"
    }
    LedgerEventType.SCALE_CALIBRATION -> {
      val grams = event.scaleWeightGrams?.roundToInt() ?: 0
      val statusTag = when {
        segment == null -> "Scale check-in"
        segment.didRecalibrateInSegment -> "Rate calibrated"
        else -> "Weight updated"
      }
      String.format(Locale.US, "%s • %,dg", statusTag, grams)
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    Column(
      modifier = Modifier
        .width(VesselTokens.timelineColumnWidth)
        .fillMaxHeight(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .width(StrokeTokens.hairline)
          .height(Spacing.md)
          .background(if (isFirst) Color.Transparent else StarphireRimMid)
      )

      Box(
        modifier = Modifier
          .size(VesselTokens.timelineNodeSize)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = AlphaTokens.mediumTint))
          .border(
            StrokeTokens.hairline,
            accentColor.copy(alpha = AlphaTokens.rimMedium),
            CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        AquaIcon(
          type = iconType,
          tint = accentColor,
          size = IconSize.xs
        )
      }

      Box(
        modifier = Modifier
          .width(StrokeTokens.hairline)
          .weight(1f)
          .background(if (isLast) Color.Transparent else StarphireRimMid)
      )
    }

    Box(
      modifier = Modifier
        .weight(1f)
        .padding(bottom = Spacing.md)
    ) {
      RimlessGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.md,
        accentRim = if (isFirst) accentColor else null,
        contentPadding = PaddingValues(Spacing.md)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = event.eventType.displayTitle,
              style = AquaTextStyles.bodySm,
              fontWeight = FontWeight.SemiBold,
              color = InkPrimary
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
              text = dateStr,
              style = AquaTextStyles.monoCaption,
              color = InkTertiary
            )
          }

          if (event.eventType != LedgerEventType.SETUP) {
            Box(
              modifier = Modifier
                .size(IconSize.lg)
                .clip(RoundedCornerShape(Radius.xs))
                .background(DropCheckerCoral.copy(alpha = AlphaTokens.subtleTint))
                .clickable(onClick = onDeleteClick),
              contentAlignment = Alignment.Center
            ) {
              AquaIcon(
                type = AquaIconType.TRASH,
                tint = DropCheckerCoral,
                size = IconSize.xs
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        GlassInsetWell(
          modifier = Modifier.fillMaxWidth(),
          cornerRadius = Radius.sm,
          borderColor = accentColor.copy(alpha = AlphaTokens.rimSoft),
          contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = detailValue,
              style = AquaTextStyles.monoBody,
              color = accentColor
            )
            event.dropCheckerStatus?.let { dc ->
              val dcColor = when (dc) {
                DropCheckerStatus.LOW_BLUE -> DropCheckerAqua
                DropCheckerStatus.OPTIMAL_GREEN -> DropCheckerOptimal
                DropCheckerStatus.HIGH_YELLOW -> DropCheckerAmber
              }
              AquaStatusPill(
                label = "DC: ${dc.shortLabel}",
                accentColor = dcColor,
                showPulseDot = true
              )
            }
          }
        }

        if (event.note.isNotBlank()) {
          Spacer(modifier = Modifier.height(Spacing.sm))
          Text(
            text = event.note,
            style = AquaTextStyles.caption,
            color = InkSecondary
          )
        }
      }
    }
  }
}
