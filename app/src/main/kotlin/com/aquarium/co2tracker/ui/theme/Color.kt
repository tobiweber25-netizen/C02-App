package com.aquarium.co2tracker.ui.theme

import androidx.compose.ui.graphics.Color

// Semantic Theme Palette - Dark-Mode-First Lush Forest Aquarium Aesthetic
// Deep forest canopy & mossy substrates
val ForestDeepBackground = Color(0xFF08130B)
val ForestSurfaceCard = Color(0xFF0F2215)
val ForestSurfaceCardElevated = Color(0xFF17301F)
val ForestSurfaceBorder = Color(0xFF23462E)

// Backward compatibility references for existing components
val CarbonBackground = ForestDeepBackground
val DarkSurfaceCard = ForestSurfaceCard
val DarkSurfaceCardElevated = ForestSurfaceCardElevated
val DarkSurfaceBorder = ForestSurfaceBorder

// Primary: Vibrant Lush Forest Sprout Emerald
val ForestEmeraldPrimary = Color(0xFF2EE576)
val ForestEmeraldPrimaryDim = Color(0xFF136334)
val ForestEmeraldOnPrimary = Color(0xFF02210D)

// Backward compatibility references
val NeonCyanPrimary = ForestEmeraldPrimary
val NeonCyanPrimaryDim = ForestEmeraldPrimaryDim
val NeonCyanOnPrimary = ForestEmeraldOnPrimary

// Secondary: Sunlit Canopy & Fern Lime
val ForestFernSecondary = Color(0xFF7CE778)
val ForestFernSecondaryDim = Color(0xFF255D21)
val ForestFernOnSecondary = Color(0xFF062505)

// Backward compatibility references
val AquamarineSecondary = ForestFernSecondary
val AquamarineSecondaryDim = ForestFernSecondaryDim
val AquamarineOnSecondary = ForestFernOnSecondary

// Gauge & Status Indicators
val GaugeTrack = Color(0xFF192D1F)
val GaugeFillActive = ForestEmeraldPrimary
val GaugeFillWarning = Color(0xFFFBBF24)
val GaugeFillCritical = Color(0xFFF87171)

val StatusCalibrated = ForestEmeraldPrimary
val StatusTheoretical = Color(0xFF86EFAC)
val StatusWarning = Color(0xFFFBBF24)
val StatusCritical = Color(0xFFF87171)

// Forest Mist Typography & Subtle Accents
val TextPrimaryDark = Color(0xFFF0FDF4)
val TextSecondaryDark = Color(0xFFA2BDA9)
val TextTertiaryDark = Color(0xFF62846B)
val IconSubtle = Color(0xFF7D9E85)

// Backward compatibility references for MaterialTheme
val primaryDark = ForestEmeraldPrimary
val primaryContainerDark = ForestEmeraldPrimaryDim
val secondaryDark = ForestFernSecondary
val tertiaryDark = StatusTheoretical
val backgroundDark = ForestDeepBackground
val surfaceDark = ForestSurfaceCard

val primaryLight = Color(0xFF156E37)
val primaryContainerLight = Color(0xFF90F6AF)
val secondaryLight = Color(0xFF2C6B1C)
val tertiaryLight = Color(0xFF21684B)
