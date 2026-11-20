package com.aquarium.co2tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aquarium.co2tracker.R

val PlusJakartaSansFamily = FontFamily(
  Font(R.font.plus_jakarta_sans_light, FontWeight.Light),
  Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
  Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
  Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
  Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
  Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
  Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
  Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

private const val TABULAR_NUMS_FEATURE = "tnum"

object AquaTextStyles {
  val heroPercent = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Light,
    fontSize = FontSizeTokens.heroNumeral,
    lineHeight = 60.sp,
    letterSpacing = TrackingTokens.tightHero,
    fontFeatureSettings = TABULAR_NUMS_FEATURE
  )

  val displayNumber = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = FontSizeTokens.displaySm,
    lineHeight = 32.sp,
    letterSpacing = TrackingTokens.tightTitle,
    fontFeatureSettings = TABULAR_NUMS_FEATURE
  )

  val brandPrimary = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Bold,
    fontSize = FontSizeTokens.title,
    lineHeight = 24.sp,
    letterSpacing = TrackingTokens.tightTitle
  )

  val brandSecondary = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Light,
    fontSize = FontSizeTokens.title,
    lineHeight = 24.sp,
    letterSpacing = TrackingTokens.normal
  )

  val sectionOverline = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = FontSizeTokens.micro,
    lineHeight = 14.sp,
    letterSpacing = TrackingTokens.microWide
  )

  val cardTitle = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = FontSizeTokens.titleSm,
    lineHeight = 22.sp,
    letterSpacing = TrackingTokens.tightTitle
  )

  val monoLarge = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = FontSizeTokens.titleSm,
    lineHeight = 22.sp,
    fontFeatureSettings = TABULAR_NUMS_FEATURE
  )

  val monoBody = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = FontSizeTokens.bodySm,
    lineHeight = 18.sp,
    fontFeatureSettings = TABULAR_NUMS_FEATURE
  )

  val monoCaption = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = FontSizeTokens.caption,
    lineHeight = 16.sp,
    fontFeatureSettings = TABULAR_NUMS_FEATURE
  )

  val monoMicro = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = FontSizeTokens.micro,
    lineHeight = 13.sp,
    letterSpacing = TrackingTokens.labelWide,
    fontFeatureSettings = TABULAR_NUMS_FEATURE
  )

  val body = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = FontSizeTokens.body,
    lineHeight = 22.sp
  )

  val bodySm = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = FontSizeTokens.bodySm,
    lineHeight = 19.sp
  )

  val caption = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Medium,
    fontSize = FontSizeTokens.caption,
    lineHeight = 16.sp
  )
}

val Typography = Typography(
  displayLarge = AquaTextStyles.heroPercent,
  headlineSmall = AquaTextStyles.displayNumber,
  titleLarge = AquaTextStyles.brandPrimary,
  titleMedium = AquaTextStyles.cardTitle,
  bodyLarge = AquaTextStyles.body,
  bodyMedium = AquaTextStyles.bodySm,
  labelLarge = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = FontSizeTokens.bodySm,
    lineHeight = 18.sp
  ),
  labelMedium = AquaTextStyles.caption,
  labelSmall = AquaTextStyles.sectionOverline
)
