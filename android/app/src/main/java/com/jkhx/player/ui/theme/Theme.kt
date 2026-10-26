package com.jkhx.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.jkhx.player.settings.AppThemeMode
import com.jkhx.player.settings.DeviceDisplayMode

val LocalDisplayMode = staticCompositionLocalOf { DeviceDisplayMode.MOBILE }

object AppTheme {
    val colors: AppSemanticColors
        @Composable
        get() = LocalAppSemanticColors.current

    val dimens: AppDimens
        @Composable
        get() = LocalAppDimens.current

    val typography: Typography
        @Composable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable
        get() = MaterialTheme.shapes
}

@Composable
fun JkhxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.NIGHT_BLUE -> NightBlueColorScheme
        AppThemeMode.CINEMA_WARM -> CinemaWarmColorScheme
    }

    val semanticColors = when (themeMode) {
        AppThemeMode.LIGHT -> LightSemanticColors
        AppThemeMode.DARK -> DarkSemanticColors
        AppThemeMode.NIGHT_BLUE -> NightBlueSemanticColors
        AppThemeMode.CINEMA_WARM -> CinemaWarmSemanticColors
    }

    CompositionLocalProvider(
        LocalAppSemanticColors provides semanticColors,
        LocalAppDimens provides DefaultAppDimens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
