package com.jkhx.player.ui.screens.stream.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun StreamBadgeChip(
    badge: StreamBadge,
    modifier: Modifier = Modifier,
    height: Dp = 17.dp
) {
    val tagColorArgb = if (badge.tagStyle.equals("filled", ignoreCase = true)) {
        badge.tagColor.toBadgeColorArgbOrNull()
    } else null
    val borderColorArgb = badge.borderColor.toBadgeColorArgbOrNull()
    val shape = RoundedCornerShape(4.dp)

    var boxModifier = modifier.height(height)
    if (tagColorArgb != null) {
        boxModifier = boxModifier.background(Color(tagColorArgb), shape)
    }
    if (borderColorArgb != null && borderColorArgb != 0L) {
        boxModifier = boxModifier.border(1.dp, Color(borderColorArgb), shape)
    }

    Box(
        modifier = boxModifier
            .clip(shape)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        if (badge.imageURL.isNotBlank()) {
            AsyncImage(
                model = badge.imageURL,
                contentDescription = badge.name,
                modifier = Modifier
                    .height(height - 2.dp)
                    .widthIn(min = 24.dp, max = 80.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            val textColorArgb = badge.textColor.toBadgeColorArgbOrNull() ?: 0xFFFFFFFF
            Text(
                text = badge.name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(textColorArgb)
            )
        }
    }
}

@Composable
fun StreamFileSizeBadge(
    fileSize: String,
    modifier: Modifier = Modifier,
    height: Dp = 17.dp
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = fileSize,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun String.toBadgeColorArgbOrNull(): Long? {
    val hex = trim().removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return argb.toLongOrNull(16)
}

