package com.aquarium.co2tracker.ui.theme

import androidx.compose.ui.graphics.Color

// Semantic Theme Palette - Dark-Mode-First Aquarium Aesthetic
val CarbonBackground = Color(0xFF0A0F14)
val DarkSurfaceCard = Color(0xFF131B24)
val DarkSurfaceCardElevated = Color(0xFF1B2633)
val DarkSurfaceBorder = Color(0xFF233547)

val NeonCyanPrimary = Color(0xFF00E5FF)
val NeonCyanPrimaryDim = Color(0xFF00838F)
val NeonCyanOnPrimary = Color(0xFF002026)

val AquamarineSecondary = Color(0xFF00E676)
val AquamarineSecondaryDim = Color(0xFF005B32)
val AquamarineOnSecondary = Color(0xFF002812)

val GaugeTrack = Color(0xFF1E2C3A)
val GaugeFillActive = Color(0xFF00E5FF)
val GaugeFillWarning = Color(0xFFFFB300)
val GaugeFillCritical = Color(0xFFFF3D00)

val StatusCalibrated = Color(0xFF00E676)
val StatusTheoretical = Color(0xFF64B5F6)
val StatusWarning = Color(0xFFFFB300)
val StatusCritical = Color(0xFFFF3D00)

val TextPrimaryDark = Color(0xFFF0F4F8)
val TextSecondaryDark = Color(0xFF90A4AE)
val TextTertiaryDark = Color(0xFF546E7A)
val IconSubtle = Color(0xFF78909C)

// Backward compatibility references for MaterialTheme
val primaryDark = NeonCyanPrimary
val primaryContainerDark = NeonCyanPrimaryDim
val secondaryDark = AquamarineSecondary
val tertiaryDark = StatusTheoretical
val backgroundDark = CarbonBackground
val surfaceDark = DarkSurfaceCard

val primaryLight = Color(0xFF006877)
val primaryContainerLight = Color(0xFF9EEFFF)
val secondaryLight = Color(0xFF006D38)
val tertiaryLight = Color(0xFF006399)
