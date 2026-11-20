package com.aquarium.co2tracker.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.theme.*
import com.aquarium.co2tracker.update.UpdateCheckState
import com.aquarium.co2tracker.update.UpdateDownloadState
import java.util.Locale

@Composable
fun UpdateDialog(
  checkState: UpdateCheckState,
  downloadState: UpdateDownloadState,
  canInstallUnknownApps: Boolean,
  onDismiss: () -> Unit,
  onStartDownload: (String) -> Unit,
  onRequestInstallPermission: () -> Unit,
  onInstallApk: () -> Unit
) {
  AquaModalSheet(
    title = "Wireless App Update",
    subtitle = "Direct GitHub Release delivery for AquaCO2 Pulse",
    iconType = AquaIconType.OTA_UPDATE,
    accentColor = StarphireCyan,
    onDismiss = onDismiss
  ) {
    when {
      downloadState is UpdateDownloadState.Downloading -> {
        DownloadingContent(downloadState = downloadState)
      }
      downloadState is UpdateDownloadState.ReadyToInstall -> {
        ReadyToInstallContent(
          canInstallUnknownApps = canInstallUnknownApps,
          onRequestPermission = onRequestInstallPermission,
          onInstall = onInstallApk
        )
      }
      downloadState is UpdateDownloadState.Error -> {
        ErrorContent(
          title = "Download Failed",
          message = downloadState.message,
          onDismiss = onDismiss
        )
      }
      checkState is UpdateCheckState.Checking -> {
        CheckingContent()
      }
      checkState is UpdateCheckState.UpdateAvailable -> {
        UpdateAvailableContent(
          update = checkState,
          canInstallUnknownApps = canInstallUnknownApps,
          onStartDownload = { onStartDownload(checkState.downloadUrl) },
          onRequestPermission = onRequestInstallPermission,
          onDismiss = onDismiss
        )
      }
      checkState is UpdateCheckState.UpToDate -> {
        UpToDateContent(
          currentVersion = checkState.currentVersion,
          onDismiss = onDismiss
        )
      }
      checkState is UpdateCheckState.Error -> {
        ErrorContent(
          title = "Update Check Failed",
          message = checkState.message,
          onDismiss = onDismiss
        )
      }
      else -> {
        CheckingContent()
      }
    }
  }
}

@Composable
private fun UpdateStateMedallion(
  iconType: AquaIconType,
  accent: Color
) {
  Box(
    modifier = Modifier
      .size(IconSize.xl)
      .clip(CircleShape)
      .background(accent.copy(alpha = AlphaTokens.subtleTint))
      .border(StrokeTokens.hairline, accent.copy(alpha = AlphaTokens.rimMedium), CircleShape),
    contentAlignment = Alignment.Center
  ) {
    AquaIcon(type = iconType, tint = accent, size = IconSize.md)
  }
}

@Composable
private fun CheckingContent() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.lg),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.normal)
  ) {
    CircularProgressIndicator(
      color = PearlEmerald,
      trackColor = GlassWell,
      modifier = Modifier.size(IconSize.xl)
    )
    Text(
      text = "Checking GitHub Releases...",
      style = AquaTextStyles.body,
      color = InkPrimary
    )
  }
}

@Composable
private fun UpToDateContent(
  currentVersion: String,
  onDismiss: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
  ) {
    UpdateStateMedallion(iconType = AquaIconType.CHECK_SHIELD, accent = DropCheckerOptimal)
    Text(
      text = "You're Up to Date",
      style = AquaTextStyles.cardTitle,
      color = InkPrimary
    )
    Text(
      text = "Version $currentVersion is the latest release.",
      style = AquaTextStyles.bodySm,
      textAlign = TextAlign.Center,
      color = InkSecondary
    )
    Spacer(modifier = Modifier.height(Spacing.sm))
    Button(
      onClick = onDismiss,
      shape = RoundedCornerShape(Radius.md),
      colors = ButtonDefaults.buttonColors(
        containerColor = GlassWell,
        contentColor = InkPrimary
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(text = "Done", style = AquaTextStyles.bodySm, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun UpdateAvailableContent(
  update: UpdateCheckState.UpdateAvailable,
  canInstallUnknownApps: Boolean,
  onStartDownload: () -> Unit,
  onRequestPermission: () -> Unit,
  onDismiss: () -> Unit
) {
  val scrollState = rememberScrollState()
  val formattedSize = remember(update.assetSize) {
    if (update.assetSize > 0) {
      String.format(Locale.US, "%.1f MB", update.assetSize / (1024.0 * 1024.0))
    } else ""
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
  ) {
    AquaStatusPill(
      label = "v${update.currentVersion}  →  v${update.newVersion}" +
        if (formattedSize.isNotBlank()) "  ($formattedSize)" else "",
      accentColor = PearlEmerald,
      showPulseDot = true
    )

    if (update.releaseNotes.isNotBlank()) {
      GlassInsetWell(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = VesselTokens.releaseNotesMaxHeight),
        cornerRadius = Radius.md
      ) {
        Text(
          text = update.releaseNotes,
          style = AquaTextStyles.caption,
          color = InkSecondary,
          modifier = Modifier.verticalScroll(scrollState)
        )
      }
    }

    if (!canInstallUnknownApps) {
      OutlinedButton(
        onClick = onRequestPermission,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DropCheckerAmber),
        border = BorderStroke(StrokeTokens.hairline, DropCheckerAmber),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(text = "Grant Install Permission", style = AquaTextStyles.bodySm)
      }
    }

    Spacer(modifier = Modifier.height(Spacing.xs))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      OutlinedButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = InkSecondary),
        border = BorderStroke(StrokeTokens.hairline, StarphireRimMid),
        modifier = Modifier.weight(1f)
      ) {
        Text(text = "Later", style = AquaTextStyles.bodySm)
      }
      Button(
        onClick = onStartDownload,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = PearlEmerald,
          contentColor = OnPearlEmerald
        ),
        modifier = Modifier.weight(1.3f)
      ) {
        Text(text = "Download Update", style = AquaTextStyles.bodySm, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun DownloadingContent(downloadState: UpdateDownloadState.Downloading) {
  val progressPercent = (downloadState.progress * 100).toInt().coerceIn(0, 100)
  val downloadedMb = downloadState.downloadedBytes / (1024.0 * 1024.0)
  val totalMb = downloadState.totalBytes / (1024.0 * 1024.0)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.md)
  ) {
    Text(
      text = "Downloading Release Package...",
      style = AquaTextStyles.cardTitle,
      color = InkPrimary
    )
    LinearProgressIndicator(
      progress = { downloadState.progress.coerceIn(0f, 1f) },
      modifier = Modifier
        .fillMaxWidth()
        .height(Spacing.sm)
        .clip(RoundedCornerShape(Radius.pill)),
      color = PearlEmerald,
      trackColor = GlassWell
    )
    Text(
      text = if (downloadState.totalBytes > 0) {
        String.format(Locale.US, "%d%%  •  %.1f / %.1f MB", progressPercent, downloadedMb, totalMb)
      } else {
        String.format(Locale.US, "%.1f MB downloaded", downloadedMb)
      },
      style = AquaTextStyles.monoCaption,
      color = InkSecondary
    )
  }
}

@Composable
private fun ReadyToInstallContent(
  canInstallUnknownApps: Boolean,
  onRequestPermission: () -> Unit,
  onInstall: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
  ) {
    UpdateStateMedallion(iconType = AquaIconType.CHECK_SHIELD, accent = PearlEmerald)
    Text(
      text = "Update Ready to Install",
      style = AquaTextStyles.cardTitle,
      color = InkPrimary
    )
    Text(
      text = "Your new release package is downloaded and verified.",
      style = AquaTextStyles.bodySm,
      textAlign = TextAlign.Center,
      color = InkSecondary
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    if (!canInstallUnknownApps) {
      Button(
        onClick = onRequestPermission,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = DropCheckerAmber,
          contentColor = OnDropCheckerWarning
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(text = "Grant Permission to Install", fontWeight = FontWeight.Bold)
      }
    } else {
      Button(
        onClick = onInstall,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
          containerColor = PearlEmerald,
          contentColor = OnPearlEmerald
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(text = "Install Now", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ErrorContent(
  title: String,
  message: String,
  onDismiss: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
  ) {
    UpdateStateMedallion(iconType = AquaIconType.WARNING, accent = DropCheckerAmber)
    Text(
      text = title,
      style = AquaTextStyles.cardTitle,
      color = DropCheckerAmber
    )
    Text(
      text = message,
      style = AquaTextStyles.bodySm,
      textAlign = TextAlign.Center,
      color = InkSecondary
    )
    Spacer(modifier = Modifier.height(Spacing.sm))
    Button(
      onClick = onDismiss,
      shape = RoundedCornerShape(Radius.md),
      colors = ButtonDefaults.buttonColors(
        containerColor = GlassWell,
        contentColor = InkPrimary
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(text = "Dismiss", style = AquaTextStyles.bodySm)
    }
  }
}
