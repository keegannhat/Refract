package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CosmicDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = PurpleGlow,
    tertiary = AcidGreen,
    background = SlateGrayBg,
    surface = CardDark,
    onPrimary = SlateGrayBg,
    onSecondary = SlateGrayBg,
    onBackground = IceWhite,
    onSurface = IceWhite,
    outline = SurfaceBorder,
    error = RedAlert
)

private val CosmicLightColorScheme = lightColorScheme(
    primary = CyberCyan,
    secondary = PurpleGlow,
    tertiary = AcidGreen,
    background = SlateGrayBg, // We commit to a consistent immersive dark mode standard
    surface = CardDark,
    onPrimary = SlateGrayBg,
    onSecondary = SlateGrayBg,
    onBackground = IceWhite,
    onSurface = IceWhite,
    outline = SurfaceBorder,
    error = RedAlert
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // We enforce our signature Cosmic Dark palette for an immersive audio experience
    val colorScheme = CosmicDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
