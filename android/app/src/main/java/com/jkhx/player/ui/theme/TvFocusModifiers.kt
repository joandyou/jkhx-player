package com.jkhx.player.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Modifier hỗ trợ hiển thị Focus Ring phát sáng theo Theme + Scale mượt mà khi điều hướng
 * bằng Remote D-Pad trên Android TV / Google TV / Android Box.
 * Đặt TRƯỚC `.clickable { ... }` hoặc `.combinedClickable { ... }` để tự động bắt trạng thái Focus.
 */
fun Modifier.tvFocusableItem(
    cornerRadius: Dp = 14.dp,
    focusedScale: Float = 1.05f,
    borderWidth: Dp = 2.5.dp,
    focusColor: Color? = null,
    fillTintAlpha: Float = 0.08f
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val resolvedColor = focusColor ?: MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tvFocusScale"
    )

    this
        .zIndex(if (isFocused) 10f else 0f)
        .onFocusChanged { state ->
            isFocused = state.isFocused
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (isFocused) {
                val strokePx = borderWidth.toPx()
                val radiusPx = cornerRadius.toPx()
                val halfStroke = strokePx / 2f
                if (fillTintAlpha > 0f) {
                    drawRoundRect(
                        color = resolvedColor.copy(alpha = fillTintAlpha),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                    )
                }
                // Glow halo nhẹ bên ngoài
                drawRoundRect(
                    color = resolvedColor.copy(alpha = 0.35f),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx * 1.8f)
                )
                // Viền chính sắc nét
                drawRoundRect(
                    color = resolvedColor,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(
                        width = (size.width - strokePx).coerceAtLeast(0f),
                        height = (size.height - strokePx).coerceAtLeast(0f)
                    ),
                    cornerRadius = CornerRadius(
                        (radiusPx - halfStroke).coerceAtLeast(0f),
                        (radiusPx - halfStroke).coerceAtLeast(0f)
                    ),
                    style = Stroke(width = strokePx)
                )
            }
        }
}

