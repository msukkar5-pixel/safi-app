package com.mohamed.safi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Brand = Color(0xFF0F6E5C)
val BrandLight = Color(0xFFE3F2EE)
val Gold = Color(0xFFF2C14E)
val Danger = Color(0xFFC6423A)
val Positive = Color(0xFF1E8E5A)
val Warn = Color(0xFFD98A1C)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandLight,
    onPrimaryContainer = Color(0xFF06372E),
    secondary = Color(0xFF3F6158),
    secondaryContainer = Color(0xFFDCE8E4),
    tertiary = Color(0xFF8A6A12),
    tertiaryContainer = Color(0xFFFFF0C8),
    background = Color(0xFFF6F8F7),
    surface = Color(0xFFF6F8F7),
    surfaceVariant = Color(0xFFE6ECEA),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F4F2),
    error = Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3BF),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0D4F43),
    onPrimaryContainer = Color(0xFFCDEFE6),
    secondary = Color(0xFFB2CCC4),
    secondaryContainer = Color(0xFF2F4A43),
    tertiary = Color(0xFFE9C46A),
    tertiaryContainer = Color(0xFF4A3B10),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF26302D),
    surfaceContainerLow = Color(0xFF171E1C),
    surfaceContainer = Color(0xFF1B2321),
    error = Color(0xFFFF8A80),
)

@Composable
fun SafiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
