package com.aquarium.co2tracker.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*

import android.os.PowerManager

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

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.lg))
        .background(DarkSurfaceCard)
        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg))
        .padding(Spacing.cardPadding)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Alerts & Device Reliability",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
          text = "Samsung Galaxy S25 / Android 15+ notification diagnostic engine.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // 1. Exact Alarm Permission Diagnostic Badge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(DarkSurfaceCardElevated)
            .border(
              1.dp,
              if (canExact) StatusCalibrated.copy(alpha = 0.5f) else StatusWarning.copy(alpha = 0.5f),
              RoundedCornerShape(Radius.md)
            )
            .padding(Spacing.md)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Exact Alarm Permission:",
                fontSize = FontSizeTokens.caption,
                color = TextSecondaryDark
              )
              Text(
                text = if (canExact) "ACTIVE (GRANTED)" else "ACTION REQUIRED",
                fontSize = FontSizeTokens.caption,
                fontWeight = FontWeight.Bold,
                color = if (canExact) StatusCalibrated else StatusWarning
              )
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))

            Text(
              text = if (canExact) {
                "AlarmManager.setExactAndAllowWhileIdle() is armed. Samsung OneUI Doze Mode will NOT delay alerts."
              } else {
                "Tap below to allow exact alarms in system settings so alerts fire at the exact minute."
              },
              fontSize = FontSizeTokens.caption,
              color = TextPrimaryDark
            )
          }
        }

        if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          Spacer(modifier = Modifier.height(Spacing.xs))
          OutlinedButton(
            onClick = {
              try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
              } catch (_: Exception) {
                // Fallback
              }
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Open Samsung Exact Alarm Settings")
          }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // 2. Battery Optimization / Deep Sleep Protection Badge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(DarkSurfaceCardElevated)
            .border(
              1.dp,
              if (isIgnoringBatteryOptimizations) StatusCalibrated.copy(alpha = 0.5f) else StatusWarning.copy(alpha = 0.5f),
              RoundedCornerShape(Radius.md)
            )
            .padding(Spacing.md)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Samsung Deep Sleep Bypass:",
                fontSize = FontSizeTokens.caption,
                color = TextSecondaryDark
              )
              Text(
                text = if (isIgnoringBatteryOptimizations) "UNRESTRICTED" else "OPTIMIZED",
                fontSize = FontSizeTokens.caption,
                fontWeight = FontWeight.Bold,
                color = if (isIgnoringBatteryOptimizations) StatusCalibrated else StatusWarning
              )
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))

            Text(
              text = if (isIgnoringBatteryOptimizations) {
                "App is exempt from battery optimization. Samsung OneUI will not put the daily watchdog to sleep."
              } else {
                "Samsung OneUI may put inactive apps into deep sleep after months. Request unrestricted access below."
              },
              fontSize = FontSizeTokens.caption,
              color = TextPrimaryDark
            )
          }
        }

        if (!isIgnoringBatteryOptimizations && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          Spacer(modifier = Modifier.height(Spacing.xs))
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
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Allow Unrestricted Battery (No Deep Sleep)")
          }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Test Notification Action
        Button(
          onClick = onTriggerTestNotification,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = NeonCyanPrimary, contentColor = NeonCyanOnPrimary)
        ) {
          Text("🔔  Trigger Test Notification Now", fontWeight = FontWeight.Bold)
        }

        if (onCheckForUpdates != null) {
          Spacer(modifier = Modifier.height(Spacing.sm))
          OutlinedButton(
            onClick = onCheckForUpdates,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyanPrimary),
            border = BorderStroke(1.dp, NeonCyanPrimaryDim)
          ) {
            Text("🚀  Check for Wireless Update (GitHub OTA)", fontWeight = FontWeight.SemiBold)
          }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Technical Specs Box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(DarkSurfaceCardElevated)
            .padding(Spacing.md)
        ) {
          Column {
            Text(
              text = "Architecture Verification:",
              fontSize = FontSizeTokens.caption,
              fontWeight = FontWeight.Bold,
              color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
              text = "• Receiver: BootReceiver (BOOT_COMPLETED + TIME_SET + TIMEZONE)\n" +
                "• Exact Alarm: USE_EXACT_ALARM + SCHEDULE_EXACT_ALARM\n" +
                "• Doze Bypass: RTC_WAKEUP via setExactAndAllowWhileIdle()\n" +
                "• Redundant Watchdog: WorkManager PeriodicWorkRequest (Daily)\n" +
                "• Local Persistence: SQLite DB (co2_tracker.db)",
              fontSize = FontSizeTokens.caption,
              color = TextTertiaryDark
            )
          }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCardElevated, contentColor = TextPrimaryDark)
          ) {
            Text("Done")
          }
        }
      }
    }
  }
}
