package com.aquarium.co2tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RimlessTankDarkColorScheme = darkColorScheme(
  primary = PearlEmerald,
  onPrimary = OnPearlEmerald,
  primaryContainer = PearlEmeraldContainer,
  onPrimaryContainer = InkPrimary,
  secondary = StarphireCyan,
  onSecondary = OnStarphireCyan,
  secondaryContainer = StarphireCyanContainer,
  onSecondaryContainer = InkPrimary,
  tertiary = DropCheckerAqua,
  onTertiary = OnStarphireCyan,
  background = AbyssalCanvas,
  onBackground = InkPrimary,
  surface = GlassSurface,
  onSurface = InkPrimary,
  surfaceVariant = GlassSurfaceElevated,
  onSurfaceVariant = InkSecondary,
  outline = StarphireRimMid,
  outlineVariant = StarphireRimSubtle,
  error = DropCheckerCoral,
  onError = OnDropCheckerCritical
)

@Composable
fun CO2CylinderTrackerTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = RimlessTankDarkColorScheme,
    typography = Typography,
    content = content
  )
}
