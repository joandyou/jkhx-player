package com.jkhx.player.ui.screens.player.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jkhx.player.R
import com.jkhx.player.ui.screens.player.HudIndicatorType
import com.jkhx.player.ui.screens.player.formatTime
import com.jkhx.player.ui.theme.ShapeTokens

/**
 * Tổng hợp các HUD Overlays và Loading/Buffering states trong Player
 */
@Composable
fun BoxScope.PlayerHudOverlays(
    isWebViewMode: Boolean,
    errorMessage: String?,
    isPlaybackStartedEffective: Boolean,
    isLoading: Boolean,
    isPlayerBuffering: Boolean,
    movieThumb: String?,
    movieName: String,
    currentEpisodeName: String,
    activeServerName: String,
    isLandscape: Boolean,
    isLongPressing2x: Boolean,
    isTranslatingWithGemini: Boolean,
    geminiTranslateProgress: Float,
    hudType: HudIndicatorType,
    hudValue: Float,
    seekGestureDeltaMs: Long,
    seekGestureTargetMs: Long,
    effectiveTotalDuration: Long,
    isLocked: Boolean,
    isPipMode: Boolean,
    showControls: Boolean,
    onUnlock: () -> Unit
) {
    // Hiệu ứng chữ "Đang tải bộ đệm..." nhấp nháy êm dịu (pulsing alpha) chuẩn phong cách streaming cao cấp Nuvio
    val infiniteTransition = rememberInfiniteTransition(label = "buffering_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffering_alpha"
    )

    // 1. Nuvio-style Opening Overlay (Màn hình đầu phim khi chưa bắt đầu phát) & Mid-stream Buffering Overlay
    if (!isWebViewMode && errorMessage == null && !isPlaybackStartedEffective) {
        // Lớp phủ đầu phim: Backdrop poster làm mờ + Gradient tối + Thông tin phim & Capsule nhấp nháy đồng bộ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!movieThumb.isNullOrBlank()) {
                AsyncImage(
                    model = movieThumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(8.dp),
                    alpha = 0.72f
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.58f)
                            )
                        )
                    )
            )

            BufferingInfoContent(
                movieName = movieName,
                currentEpisodeName = currentEpisodeName,
                activeServerName = activeServerName,
                isLandscape = isLandscape,
                pulseAlpha = pulseAlpha
            )
        }
    } else if (!isWebViewMode && errorMessage == null && isPlaybackStartedEffective && (isLoading || isPlayerBuffering) && !showControls) {
        // Buffer mid-stream sau khi phim đã bắt đầu phát (chỉ hiển thị khi Controls ẩn để triệt tiêu hoàn toàn trùng lặp 2 vòng xoay)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            BufferingInfoContent(
                movieName = movieName,
                currentEpisodeName = currentEpisodeName,
                activeServerName = activeServerName,
                isLandscape = isLandscape,
                pulseAlpha = pulseAlpha
            )
        }
    }

    // 2. Badge hiển thị khi đang giữ ngón tay tăng tốc 2X
    if (isLongPressing2x) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
            shape = ShapeTokens.Card,
            color = Color.Black.copy(alpha = 0.75f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.player_fast_forward_2x), color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    // 3. HUD hiển thị % tiến độ dịch phụ đề Gemini AI
    if (isTranslatingWithGemini) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 36.dp else 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    progress = { geminiTranslateProgress.coerceIn(0.05f, 1f) },
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "Gemini AI đang dịch phụ đề: ${(geminiTranslateProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // 4. HUD Âm lượng (Rìa phải màn hình - Vertical Pill)
    AnimatedVisibility(
        visible = hudType == HudIndicatorType.VOLUME,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .width(46.dp)
                .height(175.dp)
                .background(Color.Black.copy(alpha = 0.75f), ShapeTokens.LargeChip)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                if (hudValue <= 0.01f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            // Thanh trượt đứng
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(95.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(hudValue.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }

            Text(
                text = "${(hudValue * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // 5. HUD Độ sáng màn hình (Rìa trái màn hình - Vertical Pill)
    AnimatedVisibility(
        visible = hudType == HudIndicatorType.BRIGHTNESS,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .width(46.dp)
                .height(175.dp)
                .background(Color.Black.copy(alpha = 0.75f), ShapeTokens.LargeChip)
                .padding(vertical = 12.dp)
                .clickable { /* Chặn touch event lọt qua */ },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                if (hudValue < 0.5f) Icons.Default.BrightnessLow else Icons.Default.BrightnessMedium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            // Thanh trượt đứng
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(95.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(hudValue.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }

            Text(
                text = "${(hudValue * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // 6. HUD Double-Tap Tua 10s (Center Pill)
    AnimatedVisibility(
        visible = hudType == HudIndicatorType.SEEK_FORWARD || hudType == HudIndicatorType.SEEK_REWIND,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                if (hudType == HudIndicatorType.SEEK_FORWARD) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Forward10, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Replay10, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // 7. HUD Kéo trượt tua ngang (Swipe to Seek Preview)
    AnimatedVisibility(
        visible = hudType == HudIndicatorType.SEEK_GESTURE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.85f), ShapeTokens.Card)
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isForward = seekGestureDeltaMs >= 0
                Icon(
                    if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    val deltaSec = seekGestureDeltaMs / 1000
                    val deltaSign = if (isForward) "+" else ""
                    Text(
                        text = "$deltaSign${deltaSec}s",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${formatTime(seekGestureTargetMs)} / ${formatTime(effectiveTotalDuration)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 8. Nút Mở khóa khi đang bật Khóa màn hình (Screen Lock)
    if (isLocked && !isPipMode) {
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        ) {
            IconButton(
                onClick = onUnlock,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.player_unlock_controls), tint = Color.White)
            }
        }
    }
}

/**
 * Khối thông tin phim và capsule chữ "Đang tải bộ đệm..." nhấp nháy đồng bộ dùng chung
 * cho cả lớp phủ mở màn đầu phim (Opening Overlay) và lớp phủ đệm giữa phim (Mid-stream Buffering).
 */
@Composable
private fun BufferingInfoContent(
    movieName: String,
    currentEpisodeName: String,
    activeServerName: String,
    isLandscape: Boolean,
    pulseAlpha: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(24.dp)
    ) {
        Text(
            text = movieName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (isLandscape) 18.sp else 16.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        val epAndServer = buildString {
            append(currentEpisodeName)
            if (activeServerName.isNotBlank()) {
                append(" • ")
                append(activeServerName)
            }
        }
        Text(
            text = epAndServer,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (isLandscape) 13.sp else 12.sp
            ),
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f * pulseAlpha)),
            shadowElevation = 6.dp,
            modifier = Modifier.graphicsLayer { alpha = pulseAlpha }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.player_buffering_title),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.player_buffering_hint),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}


