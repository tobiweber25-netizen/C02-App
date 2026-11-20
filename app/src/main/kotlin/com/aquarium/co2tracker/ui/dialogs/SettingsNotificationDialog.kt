package com.aquarium.co2tracker.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*

@Composable
fun SettingsNotificationDialog(
  projection: CylinderProjection?,
  onDismiss: () -> Unit,
  onTriggerTestNotification: () -> Unit,
  onCheckForUpdates: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val canExact = Co2AlarmScheduler.canScheduleExactAlarms(context)
  val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
  } else {
    true
  }

  AquaModalSheet(
    title = "Alerts & Device Reliability",
    subtitle = "Notification health check for Android 15+ and Doze Mode",
    iconType = AquaIconType.ALERT_BELL,
    accentColor = if (canExact && isIgnoringBatteryOptimizations) DropCheckerOptimal else DropCheckerAmber,
    onDismiss = onDismiss
  ) {
    val exactAccent = if (canExact) DropCheckerOptimal else DropCheckerAmber
    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      borderColor = exactAccent.copy(alpha = AlphaTokens.rimSoft)
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "Exact Alarm Permission", style = AquaTextStyles.bodySm, color = InkPrimary)
          AquaStatusPill(
            label = if (canExact) "ACTIVE" else "REQUIRED",
            accentColor = exactAccent
          )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
          text = if (canExact) {
            "Exact alarms are armed and allowed while idle."
          } else {
            "Allow exact alarms in system settings so refill reminders fire on time."
          },
          style = AquaTextStyles.caption,
          color = InkSecondary
        )
      }
    }

    if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Spacer(modifier = Modifier.height(Spacing.sm))
      OutlinedButton(
        onClick = {
          try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
              data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
          } catch (_: Exception) {}
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md)
      ) {
        Text(text = "Open Exact Alarm Settings", style = AquaTextStyles.bodySm)
      }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    val batteryAccent = if (isIgnoringBatteryOptimizations) DropCheckerOptimal else DropCheckerAmber
    GlassInsetWell(
      modifier = Modifier.fillMaxWidth(),
      borderColor = batteryAccent.copy(alpha = AlphaTokens.rimSoft)
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "Battery Deep-Sleep Bypass", style = AquaTextStyles.bodySm, color = InkPrimary)
          AquaStatusPill(
            label = if (isIgnoringBatteryOptimizations) "UNRESTRICTED" else "OPTIMIZED",
            accentColor = batteryAccent
          )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
          text = if (isIgnoringBatteryOptimizations) {
            "Exempt from deep sleep. Daily watchdog runs uninterrupted."
          } else {
            "Allow unrestricted battery access so long-cycle reminders never sleep."
          },
          style = AquaTextStyles.caption,
          color = InkSecondary
        )
      }
    }

    if (!isIgnoringBatteryOptimizations && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Spacer(modifier = Modifier.height(Spacing.sm))
      OutlinedButton(
        onClick = {
          try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
              data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
          } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallbackIntent)
          }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md)
      ) {
        Text(text = "Allow Unrestricted Battery", style = AquaTextStyles.bodySm)
      }
    }

    Spacer(modifier = Modifier.height(Spacing.normal))

    Button(
      onClick = onTriggerTestNotification,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Radius.md),
      colors = ButtonDefaults.buttonColors(
        containerColor = PearlEmerald,
        contentColor = OnPearlEmerald
      )
    ) {
      Text(text = "Trigger Test Notification", style = AquaTextStyles.bodySm, fontWeight = FontWeight.Bold)
    }

    if (onCheckForUpdates != null) {
      Spacer(modifier = Modifier.height(Spacing.sm))
      OutlinedButton(
        onClick = onCheckForUpdates,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = StarphireCyan),
        border = BorderStroke(StrokeTokens.hairline, StarphireCyan.copy(alpha = AlphaTokens.rimMedium))
      ) {
        Text(text = "Check for Wireless Update", style = AquaTextStyles.bodySm, fontWeight = FontWeight.SemiBold)
      }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    TextButton(
      onClick = onDismiss,
      modifier = Modifier.align(Alignment.End)
    ) {
      Text(text = "Done", color = InkSecondary)
    }
  }
}
