package com.aquarium.co2tracker.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aquarium.co2tracker.BuildConfig
import com.aquarium.co2tracker.alarm.Co2AlarmScheduler
import com.aquarium.co2tracker.domain.CylinderProjection
import com.aquarium.co2tracker.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsView(
  projection: CylinderProjection?,
  onCheckForUpdates: () -> Unit,
  onTriggerTestNotification: () -> Unit,
  onConfigureCylinder: () -> Unit,
  modifier: Modifier = Modifier,
  onEditSettings: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var permissionRefreshTick by remember { mutableIntStateOf(0) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        permissionRefreshTick++
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val notificationsEnabled = remember(permissionRefreshTick) {
    Co2AlarmScheduler.areNotificationsEnabled(context)
  }
  val canExact = remember(permissionRefreshTick) {
    Co2AlarmScheduler.canScheduleExactAlarms(context)
  }
  val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  val isIgnoringBatteryOptimizations = remember(permissionRefreshTick) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
      true
    }
  }
  val allDiagnosticsHealthy = notificationsEnabled && canExact && isIgnoringBatteryOptimizations

  var showSpecs by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(Spacing.normal)
  ) {
    AquaSectionHeader(
      title = "System & Reliability",
      trailingLabel = "v${BuildConfig.VERSION_NAME}",
      trailingColor = PearlEmerald
    )

    // 1. Cylinder Hardware Setup Card
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      accentRim = if (projection != null) PearlEmerald else DropCheckerAmber
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
      ) {
        SettingsIconBadge(
          iconType = AquaIconType.CYLINDER_REFILL,
          accentColor = if (projection != null) PearlEmerald else DropCheckerAmber
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Cylinder Hardware",
            style = AquaTextStyles.cardTitle,
            color = InkPrimary
          )
          Text(
            text = if (projection != null) projection.config.cylinderName else "No active CO2 vessel configured",
            style = AquaTextStyles.caption,
            color = if (projection != null) InkSecondary else DropCheckerAmber
          )
        }
      }

      if (projection != null) {
        Spacer(modifier = Modifier.height(Spacing.md))
        GlassInsetWell(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            SettingsMetricColumn(
              label = "TARE",
              value = String.format(Locale.US, "%,dg", projection.config.tareWeightGrams.roundToInt())
            )
            SettingsMetricColumn(
              label = "FULL GROSS",
              value = String.format(Locale.US, "%,dg", projection.config.fullGrossWeightGrams.roundToInt())
            )
            SettingsMetricColumn(
              label = "NET CO2",
              value = String.format(Locale.US, "%,dg", projection.config.initialNetCo2Grams.roundToInt()),
              accent = PearlEmerald
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.normal))

      if (projection != null && onEditSettings != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
          OutlinedButton(
            onClick = onEditSettings,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(Radius.md),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PearlEmerald),
            border = BorderStroke(StrokeTokens.hairline, PearlEmerald.copy(alpha = AlphaTokens.rimMedium))
          ) {
            Text(
              text = "Edit Settings",
              style = AquaTextStyles.bodySm,
              fontWeight = FontWeight.SemiBold
            )
          }
          OutlinedButton(
            onClick = onConfigureCylinder,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(Radius.md),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = InkPrimary),
            border = BorderStroke(StrokeTokens.hairline, StarphireRimBright)
          ) {
            Text(
              text = "New Refill Reset",
              style = AquaTextStyles.bodySm,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      } else {
        OutlinedButton(
          onClick = onConfigureCylinder,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = InkPrimary),
          border = BorderStroke(StrokeTokens.hairline, StarphireRimBright)
        ) {
          Text(
            text = if (projection != null) "Reconfigure Cylinder" else "Configure Cylinder Now",
            style = AquaTextStyles.bodySm,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // 2. Alerts & Device Reliability Diagnostics Card
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      accentRim = if (allDiagnosticsHealthy) DropCheckerOptimal else DropCheckerAmber
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
      ) {
        SettingsIconBadge(
          iconType = AquaIconType.ALERT_BELL,
          accentColor = if (allDiagnosticsHealthy) DropCheckerOptimal else DropCheckerAmber
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Alerts & Device Reliability",
            style = AquaTextStyles.cardTitle,
            color = InkPrimary
          )
          Text(
            text = "Exact-minute refill alert checks for Android 15+ and Doze Mode",
            style = AquaTextStyles.caption,
            color = InkSecondary
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.normal))

      // Notification Permission Well
      DiagnosticStatusWell(
        title = "Notification Permission",
        badgeLabel = if (notificationsEnabled) "ALLOWED" else "BLOCKED",
        description = if (notificationsEnabled) {
          "Notifications are allowed and ready to alert you."
        } else {
          "Android is blocking alerts from this app. Enable notifications so warnings appear."
        },
        isHealthy = notificationsEnabled
      )

      if (!notificationsEnabled) {
        Spacer(modifier = Modifier.height(Spacing.sm))
        OutlinedButton(
          onClick = {
            try {
              val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
              }
              context.startActivity(intent)
            } catch (_: Exception) {
              val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
              }
              context.startActivity(fallback)
            }
          },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = DropCheckerAmber),
          border = BorderStroke(StrokeTokens.hairline, DropCheckerAmber.copy(alpha = AlphaTokens.rimMedium))
        ) {
          Text(text = "Open Notification Settings", style = AquaTextStyles.bodySm)
        }
      }

      Spacer(modifier = Modifier.height(Spacing.sm))

      // Exact Alarm Permission Well
      DiagnosticStatusWell(
        title = "Exact Alarm Permission",
        badgeLabel = if (canExact) "ACTIVE" else "ACTION NEEDED",
        description = if (canExact) {
          "Exact background wake-up alarms are armed and allowed."
        } else {
          "Enable exact alarms in Android settings so refill alerts fire on time."
        },
        isHealthy = canExact
      )

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
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = DropCheckerAmber),
          border = BorderStroke(StrokeTokens.hairline, DropCheckerAmber.copy(alpha = AlphaTokens.rimMedium))
        ) {
          Text(text = "Open Exact Alarm Settings", style = AquaTextStyles.bodySm)
        }
      }

      Spacer(modifier = Modifier.height(Spacing.sm))

      // Battery Optimization Bypass Well
      DiagnosticStatusWell(
        title = "Battery Deep-Sleep Protection",
        badgeLabel = if (isIgnoringBatteryOptimizations) "UNRESTRICTED" else "OPTIMIZED",
        description = if (isIgnoringBatteryOptimizations) {
          "Exempt from battery sleep. Daily watchdog runs uninterrupted."
        } else {
          "System may sleep inactive apps over long cycles. Allow unrestricted access."
        },
        isHealthy = isIgnoringBatteryOptimizations
      )

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
          shape = RoundedCornerShape(Radius.md),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = DropCheckerAmber),
          border = BorderStroke(StrokeTokens.hairline, DropCheckerAmber.copy(alpha = AlphaTokens.rimMedium))
        ) {
          Text(text = "Allow Unrestricted Battery", style = AquaTextStyles.bodySm)
        }
      }

      Spacer(modifier = Modifier.height(Spacing.normal))

      OutlinedButton(
        onClick = onTriggerTestNotification,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PearlEmerald),
        border = BorderStroke(StrokeTokens.hairline, PearlEmerald.copy(alpha = AlphaTokens.rimMedium))
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
          AquaIcon(type = AquaIconType.ALERT_BELL, tint = PearlEmerald, size = IconSize.xs)
          Text(
            text = "Trigger Test Notification",
            style = AquaTextStyles.bodySm,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // 3. Wireless App Updates (GitHub OTA)
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      accentRim = StarphireCyan
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
          SettingsIconBadge(
            iconType = AquaIconType.OTA_UPDATE,
            accentColor = StarphireCyan
          )
          Column {
            Text(
              text = "Wireless Updates",
              style = AquaTextStyles.cardTitle,
              color = InkPrimary
            )
            Text(
              text = "AquaCO2 Pulse v${BuildConfig.VERSION_NAME}",
              style = AquaTextStyles.monoCaption,
              color = StarphireCyan
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(Spacing.sm))

      Text(
        text = "Check GitHub Releases for the latest wireless build, UI upgrades, and calibration refinements.",
        style = AquaTextStyles.caption,
        color = InkSecondary
      )

      Spacer(modifier = Modifier.height(Spacing.normal))

      Button(
        onClick = onCheckForUpdates,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = PearlEmerald,
          contentColor = OnPearlEmerald
        )
      ) {
        Text(
          text = "Check for Updates",
          style = AquaTextStyles.body,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // 4. Compact Expandable Architecture Diagnostics
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      cornerRadius = Radius.md,
      onClick = { showSpecs = !showSpecs },
      contentPadding = PaddingValues(Spacing.md)
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
          AquaIcon(type = AquaIconType.CHECK_SHIELD, tint = InkTertiary, size = IconSize.sm)
          Text(
            text = "Background Engine Diagnostics",
            style = AquaTextStyles.caption,
            color = InkSecondary
          )
        }
        Text(
          text = if (showSpecs) "HIDE" else "DETAILS",
          style = AquaTextStyles.monoMicro,
          color = InkTertiary
        )
      }

      AnimatedVisibility(visible = showSpecs) {
        Column(modifier = Modifier.padding(top = Spacing.md)) {
          GlassInsetWell(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Radius.sm
          ) {
            Text(
              text = "• Boot Persistence: BootReceiver (Boot + Time + Timezone)\n" +
                "• Exact Alarm: USE_EXACT_ALARM + SCHEDULE_EXACT_ALARM\n" +
                "• Doze Bypass: RTC_WAKEUP via setExactAndAllowWhileIdle()\n" +
                "• Redundant Watchdog: Daily WorkManager Periodic Check\n" +
                "• Local Storage: On-device SQLite Ledger (co2_tracker.db)",
              style = AquaTextStyles.monoCaption,
              color = InkTertiary
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(Spacing.dockClearance))
  }
}

@Composable
private fun SettingsIconBadge(
  iconType: AquaIconType,
  accentColor: androidx.compose.ui.graphics.Color
) {
  Box(
    modifier = Modifier
      .size(IconSize.badge)
      .clip(RoundedCornerShape(Radius.md))
      .background(accentColor.copy(alpha = AlphaTokens.subtleTint))
      .border(
        StrokeTokens.hairline,
        accentColor.copy(alpha = AlphaTokens.rimSoft),
        RoundedCornerShape(Radius.md)
      ),
    contentAlignment = Alignment.Center
  ) {
    AquaIcon(type = iconType, tint = accentColor, size = IconSize.md)
  }
}

@Composable
private fun SettingsMetricColumn(
  label: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color = InkPrimary
) {
  Column {
    Text(
      text = label,
      style = AquaTextStyles.monoMicro,
      color = InkTertiary
    )
    Spacer(modifier = Modifier.height(Spacing.xxs))
    Text(
      text = value,
      style = AquaTextStyles.monoBody,
      fontWeight = FontWeight.SemiBold,
      color = accent
    )
  }
}

@Composable
private fun DiagnosticStatusWell(
  title: String,
  badgeLabel: String,
  description: String,
  isHealthy: Boolean
) {
  val accent = if (isHealthy) DropCheckerOptimal else DropCheckerAmber
  GlassInsetWell(
    modifier = Modifier.fillMaxWidth(),
    borderColor = accent.copy(alpha = AlphaTokens.rimSoft)
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = AquaTextStyles.bodySm,
          color = InkPrimary
        )
        AquaStatusPill(
          label = badgeLabel,
          accentColor = accent,
          showPulseDot = true
        )
      }
      Spacer(modifier = Modifier.height(Spacing.xs))
      Text(
        text = description,
        style = AquaTextStyles.caption,
        color = InkSecondary
      )
    }
  }
}
