package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioGold,
    onPrimary = StudioOnGold,
    primaryContainer = StudioGoldContainer,
    onPrimaryContainer = StudioGold,
    secondary = StudioCyan,
    onSecondary = StudioOnCyan,
    secondaryContainer = StudioCyanContainer,
    onSecondaryContainer = StudioCyan,
    tertiary = StudioEmerald,
    onTertiary = Color(0xFF002116),
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioBorder,
    outlineVariant = StudioBorder.copy(alpha = 0.6f),
    error = StudioCoral,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LightGoldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightGoldContainer,
    onPrimaryContainer = Color(0xFF451A03),
    secondary = LightCyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceHighest,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.6f),
    error = Color(0xFFE11D48),
    onError = Color.White
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ScriptFlowTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScriptFlowTypography,
        shapes = StudioShapes,
        content = content
    )
}
