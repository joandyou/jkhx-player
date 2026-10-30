package com.jkhx.player.ui.screens.player.controls

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.player.ExoPlayerManager
import com.jkhx.player.ui.components.glass.LocalLiquidGlassBackdrop
import com.jkhx.player.ui.components.glass.VideoFrameBackdrop
import com.jkhx.player.ui.components.glass.playerLiquidGlass
import com.jkhx.player.ui.screens.player.HudIndicatorType
import com.jkhx.player.ui.screens.player.components.RepeatingIconButton
import com.jkhx.player.ui.screens.player.formatTime
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Giao diện điều khiển Player Compose Controls cho Native Player
 * Bao gồm: Top Bar, Center Controls (Play/Pause, Tua, Buffer hint), Bottom Bar (Slider Seekbar, Capsule Pill Bar Liquid Glass)
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.PlayerControlsOverlay(
    showControls: Boolean,
    videoBackdrop: VideoFrameBackdrop?,
    movieName: String,
    currentEpisode: EpisodeData,
    activeServerName: String,
    isLandscape: Boolean,
    onRotateScreen: () -> Unit,
    onBack: () -> Unit,
    onLockScreen: () -> Unit,
    activity: Activity?,
    onEnterPip: () -> Unit,
    activeEpisodes: List<EpisodeData>,
    currentEpisodeIndex: Int,
    onSelectEpisodeIndex: (Int) -> Unit,
    showEpisodesDrawer: Boolean,
    onOpenEpisodesDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    showSourceSelectorDrawer: Boolean,
    onOpenSourceDrawer: () -> Unit,
    embedFallback: String?,
    onSwitchToWebView: () -> Unit,
    isPlaybackStartedEffective: Boolean,
    isLoading: Boolean,
    isPlayerBuffering: Boolean,
    hudType: HudIndicatorType,
    isDraggingGesture: Boolean,
    isPlaying: Boolean,
    exoPlayerManager: ExoPlayerManager,
    currentPosition: Long,
    onCurrentPositionChange: (Long) -> Unit,
    effectiveTotalDuration: Long,
    isDraggingSlider: Boolean,
    onDraggingSliderChange: (Boolean) -> Unit,
    sliderDragRatio: Float,
    onSliderDragRatioChange: (Float) -> Unit,
    isSeeking: Boolean,
    onSeekingChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    resizeModes: List<Pair<Int, String>>,
    resizeModeIndex: Int,
    onResizeModeIndexChange: (Int) -> Unit,
    activePlayerView: PlayerView?,
    capsulePillFocusRequester: FocusRequester,
    speeds: List<Float>,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    selectedAudioTrackId: String?,
    showAudioSheet: Boolean,
    onOpenAudioSheet: () -> Unit,
    selectedSubtitleTrackId: String?,
    showSubtitleSheet: Boolean,
    onOpenSubtitleSheet: () -> Unit,
    isAiDubbingEnabled: Boolean,
    onToggleAiDubbing: () -> Unit,
    onUserInteraction: () -> Unit
) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalLiquidGlassBackdrop provides videoBackdrop) {
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // --- TOP BAR: Back, Tiêu đề phim & tập, Nút Khóa, Nút Tỉ lệ, Nút PiP, Nút Cài đặt ---
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .playerLiquidGlass(CircleShape, elevation = 4.dp)
                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = movieName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentEpisode.name,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nút Khóa màn hình
                        IconButton(
                            onClick = onLockScreen,
                            modifier = Modifier
                                .size(40.dp)
                                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.player_lock_controls), tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Nút Xoay màn hình (Ngang <-> Dọc)
                        IconButton(
                            onClick = onRotateScreen,
                            modifier = Modifier
                                .size(40.dp)
                                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                        ) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = stringResource(R.string.player_rotate),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Nút Picture-in-Picture (PiP)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(
                                onClick = onEnterPip,
                                modifier = Modifier
                                    .size(40.dp)
                                    .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                    .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                            ) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = stringResource(R.string.player_pip), tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }

                        // Nút Danh sách tập (cho phim bộ)
                        if (activeEpisodes.size > 1) {
                            IconButton(
                                onClick = onOpenEpisodesDrawer,
                                modifier = Modifier
                                    .size(40.dp)
                                    .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                    .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                            ) {
                                Icon(
                                    Icons.Default.VideoLibrary,
                                    contentDescription = stringResource(R.string.player_episodes_sheet_title),
                                    tint = if (showEpisodesDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Nút Cài đặt (Tốc độ, Chất lượng HLS)
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.player_settings_title), tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Nút chuyển đổi nhanh WebView <-> ExoPlayer
                        if (!embedFallback.isNullOrEmpty()) {
                            IconButton(
                                onClick = onSwitchToWebView,
                                modifier = Modifier
                                    .size(40.dp)
                                    .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                    .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = stringResource(R.string.player_switch_to_webview),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // --- CENTER CONTROLS: Buffering HUD khi chưa phát, hoặc [Tua lùi 10s, Tập trước, Play/Pause/Buffer, Tập sau, Tua tới 10s] ---
                val isCenterHudActive = hudType == HudIndicatorType.SEEK_FORWARD ||
                        hudType == HudIndicatorType.SEEK_REWIND ||
                        hudType == HudIndicatorType.SEEK_GESTURE
                val isBufferingActive = isLoading || isPlayerBuffering

                if (!isPlaybackStartedEffective) {
                    // Tích hợp Buffering HUD vào chính giữa vùng điều khiển theo Theme Kính Lỏng
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.85f)
                            .padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(46.dp),
                            strokeWidth = 3.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
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
                            append(currentEpisode.name)
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
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier.playerLiquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                tint = Color(0xFF10141C).copy(alpha = 0.65f),
                                elevation = 6.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
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
                } else {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 36.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nút Tua lùi 10 giây (Nhấn liên tục để tua dồn dập)
                        RepeatingIconButton(
                            onClick = {
                                onUserInteraction()
                                val target = (currentPosition - 10_000L).coerceAtLeast(0L)
                                onCurrentPositionChange(target)
                                exoPlayerManager.seekTo(target)
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = stringResource(R.string.player_rewind_10s),
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Nút Tập trước
                        IconButton(
                            onClick = {
                                onUserInteraction()
                                if (currentEpisodeIndex > 0) {
                                    val prevIdx = currentEpisodeIndex - 1
                                    onSelectEpisodeIndex(prevIdx)
                                    ActivePlayerSession.currentEpisodeIndex = prevIdx
                                    ActivePlayerSession.currentEpisode = activeEpisodes[prevIdx]
                                }
                            },
                            enabled = currentEpisodeIndex > 0,
                            modifier = Modifier
                                .size(46.dp)
                                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.player_prev_episode),
                                tint = if (currentEpisodeIndex > 0) Color.White else Color.Gray,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Nút Play/Pause chính giữa (Nuvio-style Circular Indicator)
                        if (isBufferingActive) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .playerLiquidGlass(CircleShape, elevation = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (!isCenterHudActive && !isDraggingGesture && !isDraggingSlider) {
                            IconButton(
                                onClick = {
                                    onUserInteraction()
                                    if (isPlaying) exoPlayerManager.pause() else exoPlayerManager.play()
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .playerLiquidGlass(
                                        shape = CircleShape,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        elevation = 8.dp
                                    )
                                    .tvFocusableItem(cornerRadius = 36.dp, focusedScale = 1.15f)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(72.dp))
                        }

                        // Nút Tập tiếp theo
                        IconButton(
                            onClick = {
                                onUserInteraction()
                                if (activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1) {
                                    val nextIdx = currentEpisodeIndex + 1
                                    onSelectEpisodeIndex(nextIdx)
                                    ActivePlayerSession.currentEpisodeIndex = nextIdx
                                    ActivePlayerSession.currentEpisode = activeEpisodes[nextIdx]
                                }
                            },
                            enabled = activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1,
                            modifier = Modifier
                                .size(46.dp)
                                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.player_next_episode),
                                tint = if (activeEpisodes.isNotEmpty() && currentEpisodeIndex < activeEpisodes.size - 1) Color.White else Color.Gray,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Nút Tua tới 10 giây (Nhấn liên tục để tua dồn dập)
                        RepeatingIconButton(
                            onClick = {
                                onUserInteraction()
                                val target = (currentPosition + 10_000L).coerceAtMost(effectiveTotalDuration)
                                onCurrentPositionChange(target)
                                exoPlayerManager.seekTo(target)
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = stringResource(R.string.player_forward_10s),
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // --- BOTTOM BAR (Nuvio Style): Seekbar Slider, Timeline (Current / Total), Centered Pill Actions ---
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    val displayPosition = if (isDraggingSlider) (sliderDragRatio * effectiveTotalDuration).toLong() else currentPosition

                    // 1. Seekbar Timeline
                    Slider(
                        value = if (isDraggingSlider) sliderDragRatio else if (effectiveTotalDuration > 0) (currentPosition.toFloat() / effectiveTotalDuration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { ratio ->
                            onDraggingSliderChange(true)
                            onSliderDragRatioChange(ratio)
                            onUserInteraction()
                        },
                        onValueChangeFinished = {
                            val targetMs = (sliderDragRatio * effectiveTotalDuration).toLong()
                            onCurrentPositionChange(targetMs)
                            onSeekingChange(true)
                            exoPlayerManager.seekTo(targetMs)
                            onDraggingSliderChange(false)
                            onUserInteraction()
                            coroutineScope.launch {
                                delay(800)
                                onSeekingChange(false)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    // 2. Timeline Numbers: Left = Nút Play/Stop nhỏ + Current Time, Right = Total Duration & Volume Mute Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    onUserInteraction()
                                    if (isPlaying) exoPlayerManager.pause() else exoPlayerManager.play()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = formatTime(displayPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = formatTime(effectiveTotalDuration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = onMuteToggle,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.player_audio_pill_label),
                                    tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Dàn nút điều khiển Capsule nằm phía dưới Seekbar chuẩn Nuvio Liquid Glass (Kích thước to rõ, Icon + Label)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .playerLiquidGlass(
                                    shape = RoundedCornerShape(26.dp),
                                    tint = Color(0xFF10141C).copy(alpha = 0.60f),
                                    elevation = 8.dp
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            val btnSize = if (isLandscape) 40.dp else 36.dp
                            val iconSize = if (isLandscape) 20.dp else 18.dp
                            val pillSpacing = if (isLandscape) 6.dp else 4.dp

                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .focusGroup(),
                                horizontalArrangement = Arrangement.spacedBy(pillSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 3.1. Tỉ lệ khung hình (Resize Mode - Ẩn tiêu đề, chỉ icon)
                                val isResizeCustom = resizeModeIndex != 0
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isResizeCustom) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                        )
                                        .then(
                                            if (isResizeCustom) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                        )
                                        .focusRequester(capsulePillFocusRequester)
                                        .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                        .clickable {
                                            onUserInteraction()
                                            val nextModeIdx = (resizeModeIndex + 1) % resizeModes.size
                                            onResizeModeIndexChange(nextModeIdx)
                                            val (mode, label) = resizeModes[nextModeIdx]
                                            activePlayerView?.resizeMode = mode
                                            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = resizeModes[resizeModeIndex].second,
                                        tint = if (isResizeCustom) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }

                                // 3.2. Tốc độ phát (Speed - Hiển thị số 1.0x / 1.5x hoặc icon)
                                val isSpeedCustom = currentSpeed != 1.0f
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSpeedCustom) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                        )
                                        .then(
                                            if (isSpeedCustom) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                        )
                                        .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                        .clickable {
                                            onUserInteraction()
                                            val nextIdx = (speeds.indexOf(currentSpeed) + 1) % speeds.size
                                            val nextSpeed = speeds[nextIdx]
                                            onSpeedChange(nextSpeed)
                                            exoPlayerManager.setPlaybackSpeed(nextSpeed)
                                            Toast.makeText(context, context.getString(R.string.player_speed_toast, "${nextSpeed}x"), Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSpeedCustom) {
                                        Text(
                                            text = "${currentSpeed}x",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = stringResource(R.string.player_speed_title),
                                            tint = Color.White,
                                            modifier = Modifier.size(iconSize)
                                        )
                                    }
                                }

                                // 3.3. Âm thanh (Audio Tracks - Ẩn tiêu đề)
                                val isAudioActive = selectedAudioTrackId != null
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAudioActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                        )
                                        .then(
                                            if (isAudioActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                        )
                                        .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                        .clickable {
                                            onUserInteraction()
                                            onOpenAudioSheet()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = stringResource(R.string.player_audio_pill_label),
                                        tint = if (isAudioActive) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }

                                // 3.4. Phụ đề (Subtitles Panel - Ẩn tiêu đề)
                                val isSubActive = selectedSubtitleTrackId != null
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSubActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                        )
                                        .then(
                                            if (isSubActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                        )
                                        .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                        .clickable {
                                            onUserInteraction()
                                            onOpenSubtitleSheet()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = stringResource(R.string.player_subtitles_pill_label),
                                        tint = if (isSubActive) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }

                                // 3.5. Thuyết minh AI (TTS - Ẩn tiêu đề)
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAiDubbingEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                        )
                                        .then(
                                            if (isAiDubbingEnabled) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                        )
                                        .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                        .clickable {
                                            onUserInteraction()
                                            onToggleAiDubbing()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = stringResource(R.string.player_tts_pill_label),
                                        tint = if (isAiDubbingEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }

                                // 3.6. Danh sách tập (Episodes Drawer - Ẩn tiêu đề)
                                if (activeEpisodes.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .size(btnSize)
                                            .clip(CircleShape)
                                            .background(
                                                if (showEpisodesDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                            )
                                            .then(
                                                if (showEpisodesDrawer) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                            )
                                            .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                            .clickable {
                                                onUserInteraction()
                                                onOpenEpisodesDrawer()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideoLibrary,
                                            contentDescription = stringResource(R.string.player_episodes_sheet_title),
                                            tint = if (showEpisodesDrawer) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.size(iconSize)
                                        )
                                    }
                                }

                                // 3.7. Nguồn Phát & Server (Sources Selector - Ẩn tiêu đề)
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (showSourceSelectorDrawer) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                                        )
                                        .then(
                                            if (showSourceSelectorDrawer) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), CircleShape) else Modifier
                                        )
                                        .tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                                        .clickable {
                                            onUserInteraction()
                                            onOpenSourceDrawer()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = stringResource(R.string.player_source_pill_label),
                                        tint = if (showSourceSelectorDrawer) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
