package com.aquarium.co2tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ForestDarkColorScheme = darkColorScheme(
  primary = ForestEmeraldPrimary,
  onPrimary = ForestEmeraldOnPrimary,
  primaryContainer = ForestEmeraldPrimaryDim,
  onPrimaryContainer = TextPrimaryDark,
  secondary = ForestFernSecondary,
  onSecondary = ForestFernOnSecondary,
  secondaryContainer = ForestFernSecondaryDim,
  onSecondaryContainer = TextPrimaryDark,
  background = ForestDeepBackground,
  onBackground = TextPrimaryDark,
  surface = ForestSurfaceCard,
  onSurface = TextPrimaryDark,
  surfaceVariant = ForestSurfaceCardElevated,
  onSurfaceVariant = TextSecondaryDark,
  outline = ForestSurfaceBorder,
  error = StatusCritical,
  onError = TextPrimaryDark
)

@Composable
fun CO2CylinderTrackerTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = ForestDarkColorScheme,
    typography = Typography,
    content = content
  )
}
