package com.dualstream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary            = iOSBlue,
    secondary          = iOSPurple,
    background         = iOSBlack,
    surface            = iOSGrayBg,
    surfaceVariant     = iOSLightGrayBg,
    onBackground       = iOSWhite,
    onSurface          = iOSWhite,
    onSurfaceVariant   = iOSSecondary,
    error              = iOSRed,
    onPrimary          = iOSWhite,
    outline            = iOSSeparator
)

@Composable
fun DualStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
