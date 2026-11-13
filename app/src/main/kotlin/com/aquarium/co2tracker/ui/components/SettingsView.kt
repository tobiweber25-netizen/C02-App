package com.aquarium.co2tracker.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aquarium.co2tracker.BuildConfig
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*

@Composable
fun SettingsView(
  projection: CylinderProjection?,
  onCheckForUpdates: () -> Unit,
  onTriggerTestNotification: () -> Unit,
  onConfigureCylinder: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val canExact = Co2AlarmScheduler.canScheduleExactAlarms(context)
  val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
  } else {
    true
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(Spacing.normal)
  ) {
    // 1. App Updates Card (Main Check For Updates Action)
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.lg))
        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg)),
      colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(Spacing.cardPadding)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "App Updates",
              fontSize = FontSizeTokens.titleSm,
              fontWeight = FontWeight.Bold,
              color = TextPrimaryDark
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
              text = "AquaCO2 Pulse v${BuildConfig.VERSION_NAME}",
              fontSize = FontSizeTokens.bodySm,
              color = NeonCyanPrimary
            )
          }
          Text(
            text = "🚀",
            fontSize = FontSizeTokens.displaySm
          )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
          text = "Check GitHub Releases for the latest APK build, bug fixes, and physics engine improvements.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        Button(
          onClick = onCheckForUpdates,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyanPrimary,
            contentColor = NeonCyanOnPrimary
          ),
          shape = RoundedCornerShape(Radius.md)
        ) {
          Text(
            text = "Check for Updates",
            fontWeight = FontWeight.Bold,
            fontSize = FontSizeTokens.body
          )
        }
      }
    }

    // 2. Cylinder Configuration Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.lg))
        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg)),
      colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(Spacing.cardPadding)
      ) {
        Text(
          text = "Cylinder Setup",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        if (projection != null) {
          Text(
            text = "Tare: ${projection.config.tareWeightGrams.toInt()}g • Gross: ${projection.config.fullGrossWeightGrams.toInt()}g • Net CO2: ${projection.config.initialNetCo2Grams.toInt()}g",
            fontSize = FontSizeTokens.bodySm,
            color = TextSecondaryDark
          )
        } else {
          Text(
            text = "No cylinder currently configured.",
            fontSize = FontSizeTokens.bodySm,
            color = StatusWarning
          )
        }

        Spacer(modifier = Modifier.height(Spacing.normal))

        OutlinedButton(
          onClick = onConfigureCylinder,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
          border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
          Text(
            text = if (projection != null) "Reconfigure Cylinder" else "Configure Cylinder Now",
            fontSize = FontSizeTokens.bodySm,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // 3. Alerts & Reliability Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.lg))
        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg)),
      colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(Spacing.cardPadding)
      ) {
        Text(
          text = "Alerts & Device Reliability",
          fontSize = FontSizeTokens.titleSm,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
          text = "Diagnostic status for Android 15+ & Samsung OneUI Doze Mode.",
          fontSize = FontSizeTokens.caption,
          color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(Spacing.normal))

        // Exact Alarm Diagnostic
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
                "AlarmManager.setExactAndAllowWhileIdle() is armed."
              } else {
                "Tap below to allow exact alarms in system settings."
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
              } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md)
          ) {
            Text("Open Exact Alarm Settings", fontSize = FontSizeTokens.bodySm)
          }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Battery Optimization Diagnostic
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
                text = "Battery Optimization Bypass:",
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
                "App is exempt from battery optimization. Watchdog will not sleep."
              } else {
                "System may put inactive apps into deep sleep. Request unrestricted access below."
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md)
          ) {
            Text("Allow Unrestricted Battery", fontSize = FontSizeTokens.bodySm)
          }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        OutlinedButton(
          onClick = onTriggerTestNotification,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyanPrimary),
          border = BorderStroke(1.dp, NeonCyanPrimaryDim)
        ) {
          Text("🔔  Trigger Test Notification Now", fontWeight = FontWeight.SemiBold, fontSize = FontSizeTokens.bodySm)
        }
      }
    }

    // 4. Architecture Verification Card
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.md))
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

    Spacer(modifier = Modifier.height(Spacing.xl))
  }
}
