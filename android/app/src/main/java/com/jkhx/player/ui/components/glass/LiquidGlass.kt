package com.jkhx.player.ui.components.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<VideoFrameBackdrop?> { null }

val PlayerGlassSurfaceTint = Color(0xFF14171E).copy(alpha = 0.58f)
val PlayerGlassFallback = Color(0xFF0F1115).copy(alpha = 0.85f)
val PlayerGlassRimGradient = Brush.linearGradient(
    0.0f to Color.White.copy(alpha = 0.38f),
    0.5f to Color.White.copy(alpha = 0.12f),
    1.0f to Color.White.copy(alpha = 0.25f),
    start = Offset(0f, 0f),
    end = Offset(300f, 300f)
)

/**
 * Hiệu ứng Kính lỏng (Liquid Glass / Frosted Glassmorphism) cho Player Controls.
 * Tự động trích xuất màu sắc và chuyển động từ khung hình ExoPlayer đang phát bên dưới,
 * kết hợp phủ kính bán trong suốt và viền kim loại ánh gương phản quang.
 */
@Composable
fun Modifier.playerLiquidGlass(
    shape: Shape,
    tint: Color = PlayerGlassSurfaceTint,
    fallback: Color = PlayerGlassFallback,
    rim: Boolean = true,
    elevation: Dp = 6.dp,
): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current
    if (backdrop != null) {
        DisposableEffect(backdrop) {
            backdrop.acquire()
            onDispose { backdrop.release() }
        }
    }

    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val ready = backdrop != null && backdrop.isReady

    val baseModifier = this
        .shadow(elevation = elevation, shape = shape, spotColor = Color.Black.copy(alpha = 0.5f))
        .clip(shape)
        .onGloballyPositioned { coordinates = it }
        .drawWithContent {
            if (ready) {
                backdrop?.drawTo(this, coordinates)
                drawRect(color = tint)
            } else {
                drawRect(color = fallback)
            }
            drawContent()
        }

    return if (rim) {
        baseModifier.border(0.85.dp, PlayerGlassRimGradient, shape)
    } else {
        baseModifier
    }
}

/**
 * Hiệu ứng Kính mờ (Frosted Glass) cho Floating Bottom Bar trên Home Screen.
 * Tạo cảm giác thanh thỏi thuốc nổi (Pill) trong suốt với viền gương phát sáng.
 */
fun Modifier.frostedGlassSurface(
    shape: Shape,
    containerColor: Color = Color(0xFF121418).copy(alpha = 0.82f),
    elevation: Dp = 16.dp,
    rimWidth: Dp = 0.85.dp,
): Modifier {
    return this
        .shadow(elevation = elevation, shape = shape, spotColor = Color.Black.copy(alpha = 0.6f))
        .clip(shape)
        .background(containerColor)
        .border(
            width = rimWidth,
            brush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.32f),
                0.6f to Color.White.copy(alpha = 0.08f),
                1.0f to Color.White.copy(alpha = 0.18f),
            ),
            shape = shape
        )
}
