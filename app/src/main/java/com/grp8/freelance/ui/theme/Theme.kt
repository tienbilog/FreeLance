package com.grp8.freelance.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = GreenAccent,
    onPrimary = SurfaceWhite,
    primaryContainer = GreenAccentLight,
    onPrimaryContainer = TextCharcoal,
    secondary = GreenAccent,
    onSecondary = SurfaceWhite,
    secondaryContainer = GreenAccentLight,
    onSecondaryContainer = TextCharcoal,
    background = BackgroundSoftGray,
    onBackground = TextCharcoal,
    surface = SurfaceWhite,
    onSurface = TextCharcoal,
    surfaceVariant = SurfaceVariantSoft,
    onSurfaceVariant = TextNeutralGray,
    error = ErrorRed,
    onError = SurfaceWhite,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRed,
    outline = OutlineLight
)

@Composable
fun FreelanceTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = LightColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography, // Ensure you have Type.kt properly mapped
        content = content
    )
}