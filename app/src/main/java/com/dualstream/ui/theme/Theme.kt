package com.dualstream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    secondary = PurpleAccent,
    background = DarkBackground,
    surface = SurfaceColor,
    onBackground = OnSurfaceColor,
    onSurface = OnSurfaceColor,
    error = ErrorRed
)

@Composable
fun DualStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
