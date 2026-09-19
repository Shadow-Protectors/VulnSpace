package com.vulnspace.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VulnSpaceLightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = WhiteSurface,
    primaryContainer = LightBlue,
    onPrimaryContainer = DarkBlue,
    secondary = DarkBlue,
    onSecondary = WhiteSurface,
    secondaryContainer = LightBlue,
    onSecondaryContainer = DarkBlue,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = WhiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppBackground,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = WhiteSurface,
    errorContainer = DangerBackground,
    onErrorContainer = DangerRed,
    outline = BlueBorder,
    outlineVariant = BlueBorder,
    inverseSurface = TextPrimary,
    inverseOnSurface = WhiteSurface
)

@Composable
fun VulnSpaceTheme(content: @Composable () -> Unit) {
    val colorScheme = VulnSpaceLightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = WhiteSurface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VulnSpaceTypography,
        content = content
    )
}
