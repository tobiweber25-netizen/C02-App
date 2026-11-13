package com.aquarium.co2tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aquarium.co2tracker.domain.LedgerEvent
import com.aquarium.co2tracker.domain.LedgerEventType
import com.aquarium.co2tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryLogView(
  events: List<LedgerEvent>,
  onDeleteEvent: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  var eventToDelete by remember { mutableStateOf<LedgerEvent?>(null) }

  if (eventToDelete != null) {
    val evt = eventToDelete!!
    AlertDialog(
      onDismissRequest = { eventToDelete = null },
      title = { Text("Delete Ledger Event?") },
      text = {
        Text("Removing '${evt.eventType.displayTitle}' will re-evaluate all past and future gas burn calculations automatically.")
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteEvent(evt.id)
            eventToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = StatusCritical, contentColor = TextPrimaryDark)
        ) {
          Text("Delete & Recalculate")
        }
      },
      dismissButton = {
        TextButton(onClick = { eventToDelete = null }) {
          Text("Cancel", color = TextSecondaryDark)
        }
      },
      containerColor = DarkSurfaceCard,
      titleContentColor = TextPrimaryDark,
      textContentColor = TextSecondaryDark
    )
  }

  Column(modifier = modifier.fillMaxSize()) {
    Text(
      text = "Immutable Event Ledger (${events.size} records)",
      fontSize = FontSizeTokens.body,
      fontWeight = FontWeight.SemiBold,
      color = TextPrimaryDark,
      modifier = Modifier.padding(bottom = Spacing.sm)
    )

    if (events.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(Spacing.xl),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No ledger entries recorded.",
          fontSize = FontSizeTokens.bodySm,
          color = TextSecondaryDark
        )
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize()
      ) {
        items(events, key = { it.id }) { event ->
          LedgerEventCard(
            event = event,
            onDeleteClick = { eventToDelete = event }
          )
        }
      }
    }
  }
}

@Composable
private fun LedgerEventCard(
  event: LedgerEvent,
  onDeleteClick: () -> Unit
) {
  val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
  val dateStr = dateFormatter.format(Date(event.timestampMillis))

  val (accentColor, iconStr) = when (event.eventType) {
    LedgerEventType.SETUP -> NeonCyanPrimary to "🚀"
    LedgerEventType.BPS_CHANGE -> NeonCyanPrimary to "⏱"
    LedgerEventType.SCHEDULE_CHANGE -> AquamarineSecondary to "💡"
    LedgerEventType.DOWNTIME_OVERRIDE -> StatusWarning to "⏸"
    LedgerEventType.SCALE_CALIBRATION -> StatusCalibrated to "⚖️"
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(DarkSurfaceCard)
      .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.md))
      .padding(Spacing.md)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
          Text(text = iconStr, fontSize = FontSizeTokens.body)
          Text(
            text = event.eventType.displayTitle,
            fontSize = FontSizeTokens.bodySm,
            fontWeight = FontWeight.Bold,
            color = accentColor
          )
        }

        // Only allow deleting non-setup entries, or confirm delete
        if (event.eventType != LedgerEventType.SETUP) {
          Text(
            text = "Delete",
            fontSize = FontSizeTokens.caption,
            fontWeight = FontWeight.Bold,
            color = StatusCritical,
            modifier = Modifier
              .clickable { onDeleteClick() }
              .padding(Spacing.xs)
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xxs))

      Text(
        text = dateStr,
        fontSize = FontSizeTokens.caption,
        color = TextTertiaryDark
      )

      Spacer(modifier = Modifier.height(Spacing.xs))

      val detailText = when (event.eventType) {
        LedgerEventType.SETUP -> "Initialized cylinder fill @ ${event.bps} BPS"
        LedgerEventType.BPS_CHANGE -> "Rate updated to ${event.bps} BPS"
        LedgerEventType.SCHEDULE_CHANGE -> {
          val sH = event.solenoidStartMinutes / 60
          val sM = event.solenoidStartMinutes % 60
          val eH = event.solenoidEndMinutes / 60
          val eM = event.solenoidEndMinutes % 60
          String.format("Solenoid: %02d:%02d - %02d:%02d", sH, sM, eH, eM)
        }
        LedgerEventType.DOWNTIME_OVERRIDE -> {
          val sign = if (event.downtimeHoursDelta >= 0) "+" else ""
          "Override: $sign${event.downtimeHoursDelta} hours"
        }
        LedgerEventType.SCALE_CALIBRATION -> {
          "Scale Weight: ${event.scaleWeightGrams?.toInt() ?: 0}g (Calibrated)"
        }
      }

      Text(
        text = detailText,
        fontSize = FontSizeTokens.bodySm,
        fontWeight = FontWeight.Medium,
        color = TextPrimaryDark
      )

      if (event.note.isNotBlank()) {
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
          text = "Note: ${event.note}",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )
      }
    }
  }
}
