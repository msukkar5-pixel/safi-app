package com.mohamed.safi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mohamed.safi.ui.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp

// "Khushu" palette: deep emerald, antique gold, warm parchment / quiet night
val Brand = Color(0xFF0E5A4A)
val BrandDeep = Color(0xFF073B31)
val BrandLight = Color(0xFFDCEDE6)
val Gold = Color(0xFFC9A24B)
val GoldSoft = Color(0xFFE9D7A6)
val Danger = Color(0xFFB4473C)
val Positive = Color(0xFF2E7D5B)
val Warn = Color(0xFFC08A2E)

/** Classical Arabic face for titles and reading. */
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))

/** Lightweight in-memory signal used when the user changes visual accessibility settings. */
object UiAccessibility {
    val version = mutableIntStateOf(0)
    fun refresh() { version.intValue++ }
}

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandLight,
    onPrimaryContainer = Color(0xFF052E26),
    secondary = Color(0xFF5B6F66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EEE8),
    onSecondaryContainer = Color(0xFF1B2B25),
    tertiary = Color(0xFF8F6E22),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5EAD0),
    onTertiaryContainer = Color(0xFF3A2C08),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFF7F3EA),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFECE6D8),
    onSurfaceVariant = Color(0xFF4D4839),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFCF6),
    surfaceContainer = Color(0xFFF2EDE1),
    surfaceContainerHigh = Color(0xFFEDE7D9),
    outline = Color(0xFF8A8372),
    outlineVariant = Color(0xFFD9D1BF),
    error = Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD1BB),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF12443A),
    onPrimaryContainer = Color(0xFFCFEDE2),
    secondary = Color(0xFFB4C9BF),
    secondaryContainer = Color(0xFF233631),
    onSecondaryContainer = Color(0xFFD7E6DE),
    tertiary = Color(0xFFDDBB66),
    onTertiary = Color(0xFF3A2C00),
    tertiaryContainer = Color(0xFF3B3116),
    onTertiaryContainer = Color(0xFFF3E2B5),
    background = Color(0xFF0A1210),
    onBackground = Color(0xFFE9E5DA),
    surface = Color(0xFF0A1210),
    onSurface = Color(0xFFE9E5DA),
    surfaceVariant = Color(0xFF243029),
    onSurfaceVariant = Color(0xFFC3C9C2),
    surfaceContainerLowest = Color(0xFF070D0B),
    surfaceContainerLow = Color(0xFF111B18),
    surfaceContainer = Color(0xFF15211D),
    surfaceContainerHigh = Color(0xFF1B2824),
    outline = Color(0xFF8E978F),
    outlineVariant = Color(0xFF2E3B36),
    error = Color(0xFFFF8A80),
)

@Composable
fun SafiTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val accessibilityVersion = UiAccessibility.version.intValue
    val density = LocalDensity.current
    val preferences = SafiApp.prefs
    val base = Typography()
    val type = base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold),
    )
    val original = if (isSystemInDarkTheme()) Dark else Light
    val colors = if (preferences.highContrast) original.copy(
        outline = if (isSystemInDarkTheme()) Color(0xFFE9E5DA) else Color(0xFF2A2925),
        outlineVariant = if (isSystemInDarkTheme()) Color(0xFFBFC8C1) else Color(0xFF666157),
    ) else original
    CompositionLocalProvider(LocalDensity provides Density(density.density, if (preferences.largeText) 1.20f else density.fontScale)) {
        MaterialTheme(
            colorScheme = colors,
            typography = type,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
            ),
            content = content,
        )
    }
}
