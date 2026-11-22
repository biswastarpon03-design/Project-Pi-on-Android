package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = IvoryText,
    onPrimary = InkBlack,
    primaryContainer = ObsidianSurfaceElevated,
    onPrimaryContainer = IvoryText,
    secondary = MutedInkDark,
    onSecondary = InkBlack,
    secondaryContainer = ObsidianSurfaceElevated,
    onSecondaryContainer = IvoryText,
    tertiary = TealAccentDark,
    onTertiary = InkBlack,
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = TealAccentDark,
    background = ObsidianBg,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = MutedInkDark,
    outline = SlateBorderDark,
    outlineVariant = SlateBorderDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = InkBlack,
    onPrimary = PureWhite,
    primaryContainer = SurfaceVariantLight,
    onPrimaryContainer = InkBlack,
    secondary = MutedInkLight,
    onSecondary = PureWhite,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = InkBlack,
    tertiary = TealAccentLight,
    onTertiary = PureWhite,
    tertiaryContainer = TealContainerLight,
    onTertiaryContainer = TealAccentLight,
    background = PaperWhite,
    onBackground = InkBlack,
    surface = PureWhite,
    onSurface = InkBlack,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = MutedInkLight,
    outline = SlateBorderLight,
    outlineVariant = SlateBorderLight,
  )

@Immutable
data class PlatformAccent(
  val brand: Color,
  val foreground: Color,
  val container: Color,
)

@Immutable
data class PlatformColorPalette(
  val whatsapp: PlatformAccent,
  val facebook: PlatformAccent,
  val telegram: PlatformAccent,
  val isDark: Boolean,
)

private val LightPlatformPalette =
  PlatformColorPalette(
    whatsapp =
      PlatformAccent(
        brand = WhatsAppBrand,
        foreground = WhatsAppOnLight,
        container = WhatsAppContainerLight,
      ),
    facebook =
      PlatformAccent(
        brand = FacebookBrand,
        foreground = FacebookOnLight,
        container = FacebookContainerLight,
      ),
    telegram =
      PlatformAccent(
        brand = TelegramBrand,
        foreground = TelegramOnLight,
        container = TelegramContainerLight,
      ),
    isDark = false,
  )

private val DarkPlatformPalette =
  PlatformColorPalette(
    whatsapp =
      PlatformAccent(
        brand = WhatsAppBrand,
        foreground = WhatsAppBrand,
        container = WhatsAppContainerDark,
      ),
    facebook =
      PlatformAccent(
        brand = FacebookBrand,
        foreground = Color(0xFF60A5FA),
        container = FacebookContainerDark,
      ),
    telegram =
      PlatformAccent(
        brand = TelegramBrand,
        foreground = Color(0xFF38BDF8),
        container = TelegramContainerDark,
      ),
    isDark = true,
  )

val LocalPlatformColors = staticCompositionLocalOf { LightPlatformPalette }

val ProjectPiShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun ProjectPiTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val platformPalette = if (darkTheme) DarkPlatformPalette else LightPlatformPalette

  CompositionLocalProvider(LocalPlatformColors provides platformPalette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = ProjectPiShapes,
      content = content,
    )
  }
}
