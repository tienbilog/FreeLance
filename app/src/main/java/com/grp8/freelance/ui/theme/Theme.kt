package com.grp8.freelance.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Warm white + slate palette — professional, not bland, not loud
val White         = Color(0xFFFFFFFF)
val SlateLight    = Color(0xFFEEF0F3)
val SlateMid      = Color(0xFFD0D4DA)
val SlateCard     = Color(0xFFE2E5EA)
val SlateDeep     = Color(0xFF8A9099)
val Ink           = Color(0xFF1C1F26)
val InkSoft       = Color(0xFF3D4351)
val AccentBlue    = Color(0xFF3B6FD4)
val AccentBlueSoft= Color(0xFFDDE6FA)
val AccentRed     = Color(0xFFD94F4F)
val AccentGreen   = Color(0xFF2E7D5E)

private val LightColors = lightColorScheme(
    primary          = AccentBlue,
    onPrimary        = White,
    primaryContainer = AccentBlueSoft,
    onPrimaryContainer = AccentBlue,
    background       = White,
    onBackground     = Ink,
    surface          = White,
    onSurface        = Ink,
    surfaceVariant   = SlateCard,
    onSurfaceVariant = InkSoft,
    outline          = SlateMid,
    error            = AccentRed,
    onError          = White,
)

@Composable
fun FreelanceTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(colorScheme = LightColors, typography = Typography, content = content)
}