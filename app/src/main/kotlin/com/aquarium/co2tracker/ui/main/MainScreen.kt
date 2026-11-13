package com.aquarium.co2tracker.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.dialogs.*
import com.aquarium.co2tracker.ui.theme.*
import com.aquarium.co2tracker.update.AppUpdateManager
import com.aquarium.co2tracker.update.UpdateCheckState
import com.aquarium.co2tracker.update.UpdateDownloadState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  viewModel: Co2ViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  val updateManager = remember { AppUpdateManager(context) }
  var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
  var updateDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
  var showUpdateDialog by remember { mutableStateOf(false) }
  var downloadedApkFile by remember { mutableStateOf<java.io.File?>(null) }

  fun triggerUpdateCheck() {
    showUpdateDialog = true
    updateCheckState = UpdateCheckState.Checking
    updateDownloadState = UpdateDownloadState.Idle
    coroutineScope.launch {
      updateCheckState = updateManager.checkForUpdates()
    }
  }

  // Handle Android 13+ / 15+ notification permission request
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { isGranted ->
      if (!isGranted) {
        // Notification permission denied - user can still use exact alarms or re-enable
      }
    }
  )

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!hasPermission) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  LaunchedEffect(uiState.infoMessage) {
    uiState.infoMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearInfoMessage()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CarbonBackground,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
          ) {
            Text(
              text = "AquaCO2",
              fontSize = FontSizeTokens.title,
              fontWeight = FontWeight.ExtraBold,
              color = NeonCyanPrimary
            )
            Text(
              text = "Pulse",
              fontSize = FontSizeTokens.title,
              fontWeight = FontWeight.Light,
              color = TextPrimaryDark
            )
          }
        },
        actions = {
          // Wireless GitHub OTA Update Action
          IconButton(onClick = { triggerUpdateCheck() }) {
            Text(text = "🚀", fontSize = FontSizeTokens.titleSm)
          }

          // Notification & Device Reliability Diagnostics Button
          IconButton(onClick = { viewModel.showSettingsDialog(true) }) {
            Text(text = "🔔", fontSize = FontSizeTokens.titleSm)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = CarbonBackground,
          titleContentColor = NeonCyanPrimary
        )
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = NeonCyanPrimary)
        }
      } else if (uiState.projection == null) {
        // Clean unconfigured / first run onboarding prompt
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
          contentAlignment = Alignment.Center
        ) {
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
                .padding(Spacing.xl),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "⚡",
                fontSize = FontSizeTokens.display
              )

              Spacer(modifier = Modifier.height(Spacing.sm))

              Text(
                text = "No Cylinder Configured",
                fontSize = FontSizeTokens.title,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryDark
              )

              Spacer(modifier = Modifier.height(Spacing.xs))

              Text(
                text = "AquaCO2 Pulse is running without demo or filler data. Configure your cylinder's tare and gross weights to activate physics depletion tracking and advance refill alerts.",
                fontSize = FontSizeTokens.bodySm,
                color = TextSecondaryDark
              )

              Spacer(modifier = Modifier.height(Spacing.lg))

              Button(
                onClick = { viewModel.showSetupDialog(true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                  containerColor = NeonCyanPrimary,
                  contentColor = NeonCyanOnPrimary
                ),
                shape = RoundedCornerShape(Radius.md)
              ) {
                Text(
                  text = "Configure Cylinder Now",
                  fontWeight = FontWeight.Bold,
                  fontSize = FontSizeTokens.body
                )
              }
            }
          }
        }
      } else {
        val projection = uiState.projection!!

        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenPadding)
        ) {
          // Navigation Tab Row
          SecondaryTabRow(
            selectedTabIndex = if (uiState.activeTab == DashboardTab.DASHBOARD) 0 else 1,
            containerColor = DarkSurfaceCard,
            contentColor = NeonCyanPrimary,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(Radius.md))
              .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(Radius.md))
          ) {
            Tab(
              selected = uiState.activeTab == DashboardTab.DASHBOARD,
              onClick = { viewModel.setActiveTab(DashboardTab.DASHBOARD) },
              text = {
                Text(
                  text = "Dashboard",
                  fontWeight = FontWeight.Bold,
                  color = if (uiState.activeTab == DashboardTab.DASHBOARD) NeonCyanPrimary else TextSecondaryDark
                )
              }
            )
            Tab(
              selected = uiState.activeTab == DashboardTab.HISTORY,
              onClick = { viewModel.setActiveTab(DashboardTab.HISTORY) },
              text = {
                Text(
                  text = "History Ledger (${uiState.events.size})",
                  fontWeight = FontWeight.Bold,
                  color = if (uiState.activeTab == DashboardTab.HISTORY) NeonCyanPrimary else TextSecondaryDark
                )
              }
            )
          }

          Spacer(modifier = Modifier.height(Spacing.normal))

          when (uiState.activeTab) {
            DashboardTab.DASHBOARD -> {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.normal)
              ) {
                // 1. Visual Gauge & CO2 Net Mass Card
                VisualCylinderGauge(projection = projection)

                // 2. Refill Window & Alert Countdown Badge
                RefillWindowCard(projection = projection)

                // 3. Active Settings & Burn-down Physics Summary
                ActiveSettingsSummaryCard(projection = projection)

                // 4. Quick Actions Row (BPS, Schedule, Downtime, Scale Check-In, Reset)
                QuickActionsRow(
                  onBpsClick = { viewModel.showBpsDialog(true) },
                  onScheduleClick = { viewModel.showScheduleDialog(true) },
                  onDowntimeClick = { viewModel.showDowntimeDialog(true) },
                  onScaleClick = { viewModel.showScaleCalibrationDialog(true) },
                  onRefillResetClick = { viewModel.showSetupDialog(true) }
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
              }
            }

            DashboardTab.HISTORY -> {
              HistoryLogView(
                events = uiState.events,
                onDeleteEvent = { eventId -> viewModel.deleteEvent(eventId) }
              )
            }
          }
        }

        // Projection-dependent Dialogs
        if (uiState.showBpsDialog) {
          BpsUpdateDialog(
            projection = projection,
            onDismiss = { viewModel.showBpsDialog(false) },
            onConfirm = { newBps -> viewModel.updateBps(newBps) }
          )
        }

        if (uiState.showScheduleDialog) {
          ScheduleUpdateDialog(
            projection = projection,
            onDismiss = { viewModel.showScheduleDialog(false) },
            onConfirm = { sMin, eMin -> viewModel.updateSchedule(sMin, eMin) }
          )
        }

        if (uiState.showDowntimeDialog) {
          DowntimeOverrideDialog(
            projection = projection,
            onDismiss = { viewModel.showDowntimeDialog(false) },
            onConfirm = { hoursDelta, note -> viewModel.logDowntime(hoursDelta, note) }
          )
        }

        if (uiState.showScaleCalibrationDialog) {
          ScaleCheckInDialog(
            projection = projection,
            onDismiss = { viewModel.showScaleCalibrationDialog(false) },
            onConfirm = { scaleWeight -> viewModel.logScaleCalibration(scaleWeight) }
          )
        }
      }

      // Dialogs accessible whether configured or unconfigured
      if (uiState.showSetupDialog) {
        InitialCylinderSetupDialog(
          currentConfig = uiState.projection?.config,
          onDismiss = { viewModel.showSetupDialog(false) },
          onConfirm = { newConfig -> viewModel.resetOrSetupCylinder(newConfig) }
        )
      }

      if (uiState.showSettingsDialog) {
        SettingsNotificationDialog(
          projection = uiState.projection,
          onDismiss = { viewModel.showSettingsDialog(false) },
          onTriggerTestNotification = { viewModel.triggerTestNotification() },
          onCheckForUpdates = {
            viewModel.showSettingsDialog(false)
            triggerUpdateCheck()
          }
        )
      }

      if (showUpdateDialog) {
        UpdateDialog(
          checkState = updateCheckState,
          downloadState = updateDownloadState,
          canInstallUnknownApps = updateManager.canInstallUnknownApps(),
          onDismiss = {
            showUpdateDialog = false
            updateDownloadState = UpdateDownloadState.Idle
          },
          onStartDownload = { url ->
            coroutineScope.launch {
              updateDownloadState = UpdateDownloadState.Downloading(0f, 0L, 0L)
              val result = updateManager.downloadApk(url) { progress, read, total ->
                updateDownloadState = UpdateDownloadState.Downloading(progress, read, total)
              }
              result.onSuccess { file ->
                downloadedApkFile = file
                updateDownloadState = UpdateDownloadState.ReadyToInstall(file.absolutePath)
              }.onFailure { err ->
                updateDownloadState = UpdateDownloadState.Error(err.message ?: "Download failed")
              }
            }
          },
          onRequestInstallPermission = {
            updateManager.openUnknownAppInstallSettings()
          },
          onInstallApk = {
            downloadedApkFile?.let { updateManager.installApk(it) }
          }
        )
      }
    }
  }
}
