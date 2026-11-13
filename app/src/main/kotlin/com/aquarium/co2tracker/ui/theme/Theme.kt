package com.aquarium.co2tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AquariumDarkColorScheme = darkColorScheme(
  primary = NeonCyanPrimary,
  onPrimary = NeonCyanOnPrimary,
  primaryContainer = NeonCyanPrimaryDim,
  onPrimaryContainer = TextPrimaryDark,
  secondary = AquamarineSecondary,
  onSecondary = AquamarineOnSecondary,
  secondaryContainer = AquamarineSecondaryDim,
  onSecondaryContainer = TextPrimaryDark,
  background = CarbonBackground,
  onBackground = TextPrimaryDark,
  surface = DarkSurfaceCard,
  onSurface = TextPrimaryDark,
  surfaceVariant = DarkSurfaceCardElevated,
  onSurfaceVariant = TextSecondaryDark,
  outline = DarkSurfaceBorder,
  error = StatusCritical,
  onError = TextPrimaryDark
)

@Composable
fun CO2CylinderTrackerTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = AquariumDarkColorScheme,
    typography = Typography,
    content = content
  )
}
