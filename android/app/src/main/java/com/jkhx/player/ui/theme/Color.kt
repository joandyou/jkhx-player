package com.jkhx.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Primitive Brand & Palette Colors
val RedPrimary = Color(0xFFE50914)
val RedPrimaryLight = Color(0xFFDC2626)
val DarkBackground = Color(0xFF101012)
val DarkSurface = Color(0xFF1A1A1E)
val DarkSurfaceVariant = Color(0xFF282830)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFFAAAAAA)

val RatingGold = Color(0xFFFFD700)
val SuccessGreen = Color(0xFF4CAF50)

// Plugin Category Badge Colors (Phân nhóm Plugin nhận diện rõ rệt)
val PluginCategoryMovie = Color(0xFF2563EB)   // Xanh dương rạp chiếu (Movie / TV)
val PluginCategoryAdult = Color(0xFFDC2626)   // Đỏ cảnh báo (18+ / Adult)
val PluginCategoryLive = Color(0xFF059669)    // Xanh lục phát sóng trực tiếp (Live)
val PluginCategoryComic = Color(0xFF8B5CF6)   // Tím truyện tranh (Comic)
val PluginCategoryNovel = Color(0xFFD97706)   // Vàng hổ phách / Sepia sách (Novel / Tiểu thuyết)

// 1. Sáng (Light Color Scheme)
val LightColorScheme = lightColorScheme(
    primary = RedPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEE2E2),
    onPrimaryContainer = Color(0xFF991B1B),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

// 2. Tối (Dark Color Scheme)
val DarkColorScheme = darkColorScheme(
    primary = RedPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A181D),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

// 3. Neon / Night-blue Scheme
val NightBlueColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF132F4C),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF818CF8),
    onSecondary = Color.Black,
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF111D36),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF1E2B4A),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF2D436D),
    outlineVariant = Color(0xFF1E2B4A),
    error = Color(0xFFF87171),
    onError = Color.Black
)

// 4. Cinema-warm Scheme
val CinemaWarmColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF432814),
    onPrimaryContainer = Color(0xFFFED7AA),
    secondary = Color(0xFFFB923C),
    onSecondary = Color.Black,
    background = Color(0xFF14110E),
    onBackground = Color(0xFFFAF5EE),
    surface = Color(0xFF1F1A15),
    onSurface = Color(0xFFFAF5EE),
    surfaceVariant = Color(0xFF2E2620),
    onSurfaceVariant = Color(0xFFBAAFA2),
    outline = Color(0xFF4D3F34),
    outlineVariant = Color(0xFF352B24),
    error = Color(0xFFEF4444),
    onError = Color.Black
)

/**
 * Semantic custom tokens to prevent contrast issues across Light / Dark / Night / Cinema themes.
 */
@Immutable
data class AppSemanticColors(
    val badgeRed: Color,
    val badgeDark: Color,
    val ratingStar: Color,
    val subText: Color,
    val glassBackground: Color,
    val glassBorder: Color,
    val cardBorder: Color,
    val heroGlow: Color,
    val heroOverlayTop: Color,
    val heroOverlayBottom: Color,
    val warning: Color = ratingStar,
    val onSurfaceHighEmphasis: Color = Color.White,
    val brandGradient: List<Color> = listOf(badgeRed, ratingStar)
)

val LightSemanticColors = AppSemanticColors(
    badgeRed = RedPrimaryLight,
    badgeDark = Color(0xCC0F172A),
    ratingStar = Color(0xFFD97706),
    subText = Color(0xFF64748B),
    glassBackground = Color(0x1F000000),
    glassBorder = Color(0x1A000000),
    cardBorder = Color(0xFFE2E8F0),
    heroGlow = RedPrimaryLight.copy(alpha = 0.25f),
    heroOverlayTop = Color(0x66FFFFFF),
    heroOverlayBottom = Color(0xFFF8FAFC),
    warning = Color(0xFFD97706),
    onSurfaceHighEmphasis = Color(0xFF0F172A),
    brandGradient = listOf(Color(0xFFDC2626), Color(0xFFF97316))
)

val DarkSemanticColors = AppSemanticColors(
    badgeRed = RedPrimary,
    badgeDark = Color(0xB3141414),
    ratingStar = RatingGold,
    subText = Color(0xFF9E9E9E),
    glassBackground = Color(0x28FFFFFF),
    glassBorder = Color(0x1FFFFFFF),
    cardBorder = Color(0xFF3F3F46).copy(alpha = 0.35f),
    heroGlow = RedPrimary.copy(alpha = 0.40f),
    heroOverlayTop = Color(0x88000000),
    heroOverlayBottom = DarkBackground,
    warning = RatingGold,
    onSurfaceHighEmphasis = Color.White,
    brandGradient = listOf(Color(0xFFE50914), Color(0xFFFF5252))
)

val NightBlueSemanticColors = AppSemanticColors(
    badgeRed = Color(0xFF38BDF8),
    badgeDark = Color(0xB30B1120),
    ratingStar = Color(0xFF38BDF8),
    subText = Color(0xFF94A3B8),
    glassBackground = Color(0x28FFFFFF),
    glassBorder = Color(0x2D436D),
    cardBorder = Color(0xFF2D436D).copy(alpha = 0.40f),
    heroGlow = Color(0xFF38BDF8).copy(alpha = 0.40f),
    heroOverlayTop = Color(0x88000000),
    heroOverlayBottom = Color(0xFF0B1120),
    warning = Color(0xFF38BDF8),
    onSurfaceHighEmphasis = Color(0xFFF8FAFC),
    brandGradient = listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
)

val CinemaWarmSemanticColors = AppSemanticColors(
    badgeRed = Color(0xFFF59E0B),
    badgeDark = Color(0xB314110E),
    ratingStar = Color(0xFFF59E0B),
    subText = Color(0xFFBAAFA2),
    glassBackground = Color(0x28FFFFFF),
    glassBorder = Color(0x4D3F34),
    cardBorder = Color(0xFF4D3F34).copy(alpha = 0.40f),
    heroGlow = Color(0xFFF59E0B).copy(alpha = 0.40f),
    heroOverlayTop = Color(0x88000000),
    heroOverlayBottom = Color(0xFF14110E),
    warning = Color(0xFFF59E0B),
    onSurfaceHighEmphasis = Color(0xFFFAF5EE),
    brandGradient = listOf(Color(0xFFF59E0B), Color(0xFFFB923C))
)

val LocalAppSemanticColors = staticCompositionLocalOf { DarkSemanticColors }

