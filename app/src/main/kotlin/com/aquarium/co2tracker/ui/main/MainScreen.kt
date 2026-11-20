package com.aquarium.co2tracker.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aquarium.co2tracker.ui.components.*
import com.aquarium.co2tracker.ui.dialogs.*
import com.aquarium.co2tracker.ui.theme.*
import com.aquarium.co2tracker.update.AppUpdateManager
import com.aquarium.co2tracker.update.UpdateCheckState
import com.aquarium.co2tracker.update.UpdateDownloadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
  var isEditSettingsMode by remember { mutableStateOf(false) }
  var resumeTick by remember { mutableIntStateOf(0) }
  var liveMinuteOfDay by remember { mutableIntStateOf(currentLocalMinuteOfDay()) }

  // Refresh live CO2 consumption, local clock, and OS permission status on resume
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        resumeTick++
        liveMinuteOfDay = currentLocalMinuteOfDay()
        viewModel.refreshLiveProjection()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Keep live projection & photoperiod beacon fresh every 60s while foregrounded
  LaunchedEffect(Unit) {
    while (true) {
      delay(60_000L)
      liveMinuteOfDay = currentLocalMinuteOfDay()
      viewModel.refreshLiveProjection()
    }
  }

  fun triggerUpdateCheck() {
    showUpdateDialog = true
    updateCheckState = UpdateCheckState.Checking
    updateDownloadState = UpdateDownloadState.Idle
    coroutineScope.launch {
      updateCheckState = updateManager.checkForUpdates()
    }
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { /* User can manage notifications anytime from Settings */ }
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

  MainScreenContent(
    uiState = uiState,
    snackbarHostState = snackbarHostState,
    onSelectTab = viewModel::setActiveTab,
    onTriggerUpdateCheck = { triggerUpdateCheck() },
    onShowBpsSheet = { viewModel.showBpsDialog(true) },
    onShowScheduleSheet = { viewModel.showScheduleDialog(true) },
    onShowDowntimeSheet = { viewModel.showDowntimeDialog(true) },
    onShowScaleSheet = { viewModel.showScaleCalibrationDialog(true) },
    onShowSetupSheet = {
      isEditSettingsMode = false
      viewModel.showSetupDialog(true)
    },
    onShowEditSettingsSheet = {
      isEditSettingsMode = true
      viewModel.showSetupDialog(true)
    },
    onDeleteEvent = viewModel::deleteEvent,
    onTriggerTestNotification = viewModel::triggerTestNotification,
    nowMinuteOfDay = liveMinuteOfDay,
    modifier = modifier
  )

  // Slide-up Modal Sheets
  val projection = uiState.projection
  if (projection != null) {
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

  if (uiState.showSetupDialog) {
    val editMode = isEditSettingsMode && uiState.projection != null
    InitialCylinderSetupDialog(
      currentConfig = uiState.projection?.config,
      isEditSettingsMode = editMode,
      onDismiss = { viewModel.showSetupDialog(false) },
      onConfirm = { newConfig ->
        if (editMode) {
          viewModel.updateCylinderSettings(newConfig)
        } else {
          viewModel.resetOrSetupCylinder(newConfig)
        }
      }
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
    val canInstall = remember(resumeTick, showUpdateDialog) { updateManager.canInstallUnknownApps() }
    UpdateDialog(
      checkState = updateCheckState,
      downloadState = updateDownloadState,
      canInstallUnknownApps = canInstall,
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
      onRequestInstallPermission = { updateManager.openUnknownAppInstallSettings() },
      onInstallApk = { downloadedApkFile?.let { updateManager.installApk(it) } }
    )
  }
}

@Composable
internal fun MainScreenContent(
  uiState: Co2UiState,
  snackbarHostState: SnackbarHostState,
  onSelectTab: (DashboardTab) -> Unit,
  onTriggerUpdateCheck: () -> Unit,
  onShowBpsSheet: () -> Unit,
  onShowScheduleSheet: () -> Unit,
  onShowDowntimeSheet: () -> Unit,
  onShowScaleSheet: () -> Unit,
  onShowSetupSheet: () -> Unit,
  onDeleteEvent: (Long) -> Unit,
  onTriggerTestNotification: () -> Unit,
  modifier: Modifier = Modifier,
  onShowEditSettingsSheet: () -> Unit = onShowSetupSheet,
  nowMinuteOfDay: Int = currentLocalMinuteOfDay()
) {
  val ambientGlow = when {
    uiState.projection?.isBelowDangerThreshold == true -> VesselLiquidCoralDeep
    uiState.projection?.isInRefillWindow == true -> VesselLiquidAmberDeep
    else -> OverheadLightGlow
  }

  AquaAmbientBackdrop(
    modifier = modifier,
    accentGlow = ambientGlow
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
    ) {
      // Minimal Gallery Top Header
      AquaTopHeader(
        onUpdateClick = onTriggerUpdateCheck,
        onAlertsClick = { onSelectTab(DashboardTab.SETTINGS) }
      )

      // Primary Workspace Body
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = Spacing.screenPadding)
      ) {
        if (uiState.isLoading) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              color = PearlEmerald,
              trackColor = GlassWell
            )
          }
        } else {
          Crossfade(
            targetState = uiState.activeTab,
            animationSpec = tween(durationMillis = 220),
            label = "tabCrossfade"
          ) { tab ->
            when (tab) {
              DashboardTab.DASHBOARD -> {
                if (uiState.projection == null) {
                  UnconfiguredVesselShowcase(
                    onConfigureClick = onShowSetupSheet
                  )
                } else {
                  val projection = uiState.projection
                  Column(
                    modifier = Modifier
                      .fillMaxSize()
                      .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                  ) {
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // 1. Living Illuminated CO2 Vessel Hero
                    VisualCylinderGauge(
                      projection = projection,
                      nowMinuteOfDay = nowMinuteOfDay
                    )

                    // 2. Refill Horizon & Drop-Checker Status
                    RefillWindowCard(projection = projection)

                    // 3. 24-Hour Photoperiod & Burn Telemetry Bento
                    ActiveSettingsSummaryCard(
                      projection = projection,
                      nowMinuteOfDay = nowMinuteOfDay,
                      onBpsClick = onShowBpsSheet,
                      onScheduleClick = onShowScheduleSheet,
                      onScaleClick = onShowScaleSheet
                    )

                    // 4. Precision Regulator Control Deck
                    QuickActionsRow(
                      onBpsClick = onShowBpsSheet,
                      onScheduleClick = onShowScheduleSheet,
                      onDowntimeClick = onShowDowntimeSheet,
                      onScaleClick = onShowScaleSheet,
                      onRefillResetClick = onShowSetupSheet
                    )

                    Spacer(modifier = Modifier.height(Spacing.dockClearance))
                  }
                }
              }

              DashboardTab.HISTORY -> {
                HistoryLogView(
                  events = uiState.events,
                  evaluatedSegments = uiState.projection?.evaluatedSegments.orEmpty(),
                  onDeleteEvent = onDeleteEvent,
                  modifier = Modifier.padding(top = Spacing.xs)
                )
              }

              DashboardTab.SETTINGS -> {
                SettingsView(
                  projection = uiState.projection,
                  onCheckForUpdates = onTriggerUpdateCheck,
                  onTriggerTestNotification = onTriggerTestNotification,
                  onConfigureCylinder = onShowSetupSheet,
                  onEditSettings = onShowEditSettingsSheet,
                  modifier = Modifier.padding(top = Spacing.xs)
                )
              }
            }
          }
        }
      }
    }

    // Floating Snackbar positioned cleanly above the glass dock
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = VesselTokens.dockHeight + Spacing.xl, start = Spacing.screenPadding, end = Spacing.screenPadding)
    ) { data ->
      Snackbar(
        snackbarData = data,
        shape = RoundedCornerShape(Radius.md),
        containerColor = GlassSurfaceElevated,
        contentColor = InkPrimary,
        actionColor = PearlEmerald
      )
    }

    // Floating Starphire Glass Bottom Dock
    FloatingGlassDock(
      activeTab = uiState.activeTab,
      historyCount = uiState.events.size,
      onSelectTab = onSelectTab,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(
          start = VesselTokens.dockHorizontalInset,
          end = VesselTokens.dockHorizontalInset,
          bottom = VesselTokens.dockBottomInset
        )
    )
  }
}

@Composable
private fun AquaTopHeader(
  onUpdateClick: () -> Unit,
  onAlertsClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.screenPadding, vertical = Spacing.md),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
      AquaIcon(
        type = AquaIconType.BRAND_DROPLET,
        tint = PearlEmerald,
        size = IconSize.emblem
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
      ) {
        Text(
          text = "AquaCO2",
          style = AquaTextStyles.brandPrimary,
          color = InkPrimary
        )
        Text(
          text = "Pulse",
          style = AquaTextStyles.brandSecondary,
          color = PearlEmerald
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HeaderGlassIconButton(
        iconType = AquaIconType.OTA_UPDATE,
        tint = StarphireCyan,
        onClick = onUpdateClick
      )
      HeaderGlassIconButton(
        iconType = AquaIconType.ALERT_BELL,
        tint = InkSecondary,
        onClick = onAlertsClick
      )
    }
  }
}

@Composable
private fun HeaderGlassIconButton(
  iconType: AquaIconType,
  tint: Color,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(IconSize.headerButton)
      .clip(CircleShape)
      .background(GlassSurface.copy(alpha = 0.85f))
      .border(StrokeTokens.hairline, StarphireRimMid, CircleShape)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    AquaIcon(type = iconType, tint = tint, size = IconSize.sm)
  }
}

@Composable
private fun FloatingGlassDock(
  activeTab: DashboardTab,
  historyCount: Int,
  onSelectTab: (DashboardTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val dockShape = RoundedCornerShape(Radius.pill)
  val rimBrush = Brush.verticalGradient(
    listOf(StarphireRimBright, StarphireRimMid, StarphireRimSubtle)
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(VesselTokens.dockHeight)
      .shadow(elevation = Elevation.dock, shape = dockShape, clip = false)
      .clip(dockShape)
      .background(GlassDockBackdrop)
      .border(StrokeTokens.hairline, rimBrush, dockShape)
      .padding(Spacing.xs),
    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    verticalAlignment = Alignment.CenterVertically
  ) {
    DockTabItem(
      label = "Vessel",
      iconType = AquaIconType.TAB_VESSEL,
      selected = activeTab == DashboardTab.DASHBOARD,
      badgeText = null,
      onClick = { onSelectTab(DashboardTab.DASHBOARD) },
      modifier = Modifier.weight(1f)
    )
    DockTabItem(
      label = "Ledger",
      iconType = AquaIconType.TAB_LEDGER,
      selected = activeTab == DashboardTab.HISTORY,
      badgeText = if (historyCount > 0) historyCount.toString() else null,
      onClick = { onSelectTab(DashboardTab.HISTORY) },
      modifier = Modifier.weight(1f)
    )
    DockTabItem(
      label = "System",
      iconType = AquaIconType.TAB_SETTINGS,
      selected = activeTab == DashboardTab.SETTINGS,
      badgeText = null,
      onClick = { onSelectTab(DashboardTab.SETTINGS) },
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun DockTabItem(
  label: String,
  iconType: AquaIconType,
  selected: Boolean,
  badgeText: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val pillShape = RoundedCornerShape(Radius.pill)
  val tint by animateColorAsState(
    targetValue = if (selected) PearlEmerald else InkSecondary,
    animationSpec = tween(durationMillis = 200),
    label = "dockItemTint"
  )
  val bgColor by animateColorAsState(
    targetValue = if (selected) PearlEmerald.copy(alpha = AlphaTokens.mediumTint) else Color.Transparent,
    animationSpec = tween(durationMillis = 200),
    label = "dockItemBg"
  )
  val borderColor by animateColorAsState(
    targetValue = if (selected) PearlEmerald.copy(alpha = AlphaTokens.rimSoft) else Color.Transparent,
    animationSpec = tween(durationMillis = 200),
    label = "dockItemBorder"
  )

  Row(
    modifier = modifier
      .fillMaxHeight()
      .clip(pillShape)
      .background(bgColor)
      .border(StrokeTokens.hairline, borderColor, pillShape)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      ),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    AquaIcon(type = iconType, tint = tint, size = IconSize.sm)
    Spacer(modifier = Modifier.width(Spacing.xs))
    Text(
      text = label,
      style = AquaTextStyles.caption,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      color = tint,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis
    )
    if (badgeText != null) {
      Spacer(modifier = Modifier.width(Spacing.xs))
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(Radius.pill))
          .background(if (selected) PearlEmerald.copy(alpha = AlphaTokens.strongTint) else GlassWell)
          .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
      ) {
        Text(
          text = badgeText,
          style = AquaTextStyles.monoMicro,
          color = if (selected) PearlMintGlow else InkTertiary
        )
      }
    }
  }
}

@Composable
private fun UnconfiguredVesselShowcase(
  onConfigureClick: () -> Unit
) {
  val infinite = rememberInfiniteTransition(label = "onboardingAmbient")
  val drift by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 5400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "onboardingBubbleDrift"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(bottom = Spacing.dockClearance),
    contentAlignment = Alignment.Center
  ) {
    RimlessGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      cornerRadius = Radius.hero,
      accentRim = PearlEmerald,
      contentPadding = PaddingValues(Spacing.xl)
    ) {
      // Sculptural Glass CO2 Vessel & Rising Micro-Bubbles Artwork
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(VesselTokens.onboardingVisualHeight)
      ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f

        // Soft emerald backlight behind glass cylinder
        drawCircle(
          brush = Brush.radialGradient(
            listOf(PearlEmerald.copy(alpha = AlphaTokens.glowSoft), Color.Transparent),
            center = Offset(cx, h * 0.52f),
            radius = h * 0.62f
          ),
          radius = h * 0.62f,
          center = Offset(cx, h * 0.52f)
        )

        // Minimalist pressurized CO2 vessel outline
        val bottleW = h * 0.42f
        val bottleH = h * 0.64f
        val bottleTop = h * 0.26f
        val bottleLeft = cx - bottleW / 2f

        drawRoundRect(
          brush = Brush.verticalGradient(
            listOf(GlassSurfaceElevatedTop.copy(alpha = 0.8f), VesselLiquidDeep.copy(alpha = 0.45f))
          ),
          topLeft = Offset(bottleLeft, bottleTop),
          size = Size(bottleW, bottleH),
          cornerRadius = CornerRadius(bottleW * 0.34f, bottleW * 0.34f)
        )
        drawRoundRect(
          color = StarphireRimBright,
          topLeft = Offset(bottleLeft, bottleTop),
          size = Size(bottleW, bottleH),
          cornerRadius = CornerRadius(bottleW * 0.34f, bottleW * 0.34f),
          style = Stroke(width = StrokeTokens.medium.toPx())
        )

        // Brass/titanium valve neck & wheel
        drawLine(
          color = TitaniumSilver,
          start = Offset(cx, h * 0.14f),
          end = Offset(cx, bottleTop),
          strokeWidth = StrokeTokens.bold.toPx(),
          cap = StrokeCap.Round
        )
        drawLine(
          color = PearlEmerald,
          start = Offset(cx - bottleW * 0.28f, h * 0.14f),
          end = Offset(cx + bottleW * 0.28f, h * 0.14f),
          strokeWidth = StrokeTokens.bold.toPx(),
          cap = StrokeCap.Round
        )

        // Rising ambient bubbles inside the cylinder silhouette
        listOf(
          Triple(0.0f, -0.14f, 4.5f),
          Triple(0.32f, 0.10f, 3.5f),
          Triple(0.64f, -0.04f, 5.2f),
          Triple(0.84f, 0.16f, 3.0f)
        ).forEach { (offset, xShift, radiusDp) ->
          val p = (drift + offset) % 1f
          val y = (bottleTop + bottleH * 0.88f) - p * (bottleH * 0.72f)
          val x = cx + bottleW * xShift
          val alpha = (1f - p).coerceIn(0f, 1f) * (p * 4f).coerceIn(0f, 1f) * 0.85f
          drawCircle(
            color = PearlMintGlow.copy(alpha = alpha),
            radius = radiusDp,
            center = Offset(x, y)
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.md))

      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        AquaStatusPill(
          label = "ZERO DEMO DATA",
          accentColor = StarphireCyan,
          showPulseDot = false
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
          text = "Calibrate Your CO2 Vessel",
          style = AquaTextStyles.displayNumber,
          textAlign = TextAlign.Center,
          color = InkPrimary
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
          text = "Enter your bottle's tare and full weights to activate live gas tracking, daily photoperiod monitoring, and early refill alerts.",
          style = AquaTextStyles.bodySm,
          textAlign = TextAlign.Center,
          color = InkSecondary
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        Button(
          onClick = onConfigureClick,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(Radius.md),
          contentPadding = PaddingValues(vertical = Spacing.md),
          colors = ButtonDefaults.buttonColors(
            containerColor = PearlEmerald,
            contentColor = OnPearlEmerald
          )
        ) {
          Text(
            text = "Configure Cylinder Now",
            style = AquaTextStyles.body,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
