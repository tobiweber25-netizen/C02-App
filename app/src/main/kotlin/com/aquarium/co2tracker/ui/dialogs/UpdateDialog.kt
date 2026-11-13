package com.aquarium.co2tracker.ui.dialogs

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(DarkSurfaceCard)
                .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.lg))
                .padding(Spacing.cardPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
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
    }
}

@Composable
private fun CheckingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.normal),
        modifier = Modifier.padding(vertical = Spacing.normal)
    ) {
        CircularProgressIndicator(
            color = NeonCyanPrimary,
            modifier = Modifier.size(Spacing.xxl)
        )
        Text(
            text = "Checking GitHub for updates...",
            fontSize = FontSizeTokens.body,
            color = TextPrimaryDark
        )
    }
}

@Composable
private fun UpToDateContent(
    currentVersion: String,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "✓",
            fontSize = FontSizeTokens.display,
            color = StatusCalibrated,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "AquaCO2 Pulse is Up to Date",
            fontSize = FontSizeTokens.titleSm,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark
        )
        Text(
            text = "Version $currentVersion is currently the latest release.",
            fontSize = FontSizeTokens.bodySm,
            textAlign = TextAlign.Center,
            color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(Radius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkSurfaceCardElevated,
                contentColor = TextPrimaryDark
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "OK", fontSize = FontSizeTokens.body, fontWeight = FontWeight.SemiBold)
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "🚀",
            fontSize = FontSizeTokens.display
        )
        Text(
            text = "OTA Update Available",
            fontSize = FontSizeTokens.titleSm,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(DarkSurfaceCardElevated)
                .border(1.dp, NeonCyanPrimaryDim, RoundedCornerShape(Radius.sm))
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
        ) {
            Text(
                text = "v${update.currentVersion}  ➔  v${update.newVersion}" + if (formattedSize.isNotBlank()) "  ($formattedSize)" else "",
                fontSize = FontSizeTokens.caption,
                fontWeight = FontWeight.Bold,
                color = NeonCyanPrimary
            )
        }

        if (update.releaseNotes.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(DarkSurfaceCardElevated)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.sm))
                    .padding(Spacing.sm)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = update.releaseNotes,
                    fontSize = FontSizeTokens.caption,
                    color = TextSecondaryDark
                )
            }
        }

        if (!canInstallUnknownApps) {
            OutlinedButton(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarning),
                border = BorderStroke(1.dp, StatusWarning),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Grant Install Unknown Apps Permission", fontSize = FontSizeTokens.bodySm)
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondaryDark),
                border = BorderStroke(1.dp, DarkSurfaceBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Later", fontSize = FontSizeTokens.body)
            }
            Button(
                onClick = onStartDownload,
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyanPrimary,
                    contentColor = NeonCyanOnPrimary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Download", fontSize = FontSizeTokens.body, fontWeight = FontWeight.Bold)
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(vertical = Spacing.sm)
    ) {
        Text(
            text = "⏳",
            fontSize = FontSizeTokens.display
        )
        Text(
            text = "Downloading Wireless Update...",
            fontSize = FontSizeTokens.titleSm,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark
        )
        LinearProgressIndicator(
            progress = { downloadState.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.sm)
                .clip(RoundedCornerShape(Radius.xs)),
            color = NeonCyanPrimary,
            trackColor = DarkSurfaceCardElevated
        )
        Text(
            text = if (downloadState.totalBytes > 0) {
                String.format(Locale.US, "%d%% (%.1f / %.1f MB)", progressPercent, downloadedMb, totalMb)
            } else {
                String.format(Locale.US, "%.1f MB downloaded", downloadedMb)
            },
            fontSize = FontSizeTokens.caption,
            color = TextSecondaryDark
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "📦",
            fontSize = FontSizeTokens.display
        )
        Text(
            text = "Update Package Ready",
            fontSize = FontSizeTokens.titleSm,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark
        )
        Text(
            text = "The update is downloaded and ready to install over-the-air.",
            fontSize = FontSizeTokens.bodySm,
            textAlign = TextAlign.Center,
            color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        if (!canInstallUnknownApps) {
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusWarning,
                    contentColor = DarkSurfaceCard
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
                    containerColor = NeonCyanPrimary,
                    contentColor = NeonCyanOnPrimary
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "⚠️",
            fontSize = FontSizeTokens.display
        )
        Text(
            text = title,
            fontSize = FontSizeTokens.titleSm,
            fontWeight = FontWeight.Bold,
            color = StatusWarning
        )
        Text(
            text = message,
            fontSize = FontSizeTokens.bodySm,
            textAlign = TextAlign.Center,
            color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(Radius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkSurfaceCardElevated,
                contentColor = TextPrimaryDark
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Dismiss", fontSize = FontSizeTokens.body)
        }
    }
}
