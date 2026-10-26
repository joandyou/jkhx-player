package com.jkhx.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppDimens(
    // Spacing tokens
    val space2: Dp = 2.dp,
    val space4: Dp = 4.dp,
    val space6: Dp = 6.dp,
    val space8: Dp = 8.dp,
    val space10: Dp = 10.dp,
    val space12: Dp = 12.dp,
    val space14: Dp = 14.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space28: Dp = 28.dp,
    val space32: Dp = 32.dp,

    // Component Dimensions
    val cardWidthMobile: Dp = 125.dp,
    val cardWidthTablet: Dp = 160.dp,
    val heroHeightMobile: Dp = 340.dp,
    val heroHeightTablet: Dp = 420.dp,
    val topBarHeight: Dp = 46.dp,
    val buttonHeightSmall: Dp = 38.dp,
    val buttonHeightMedium: Dp = 48.dp,
    val iconSizeSmall: Dp = 16.dp,
    val iconSizeMedium: Dp = 20.dp,
    val iconSizeNormal: Dp = 24.dp,
    val iconSizeLarge: Dp = 38.dp
)

val DefaultAppDimens = AppDimens()

val LocalAppDimens = staticCompositionLocalOf { DefaultAppDimens }

