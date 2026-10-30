package com.jkhx.player.ui.screens.player.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.R
import com.jkhx.player.core.player.AudioTrackOption
import com.jkhx.player.core.player.VideoTrackOption
import com.jkhx.player.ui.screens.player.components.SettingsOptionCard
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.delay

@Composable
internal fun PlayerSettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    initialFocusRequester: FocusRequester,
    currentSpeed: Float,
    speeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    onSpeedSelected: (Float) -> Unit,
    videoTracks: List<VideoTrackOption>,
    selectedVideoTrackId: String?,
    onVideoTrackSelected: (VideoTrackOption?) -> Unit,
    audioTracks: List<AudioTrackOption>,
    selectedAudioTrackId: String?,
    formatAudioSpecs: (AudioTrackOption) -> String,
    onAudioTrackSelected: (AudioTrackOption) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        ) {
            // Panel trượt từ bên phải
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(
                        Color.Black.copy(alpha = 0.65f),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
                    .pointerInput(Unit) {
                        // Chặn chạm xuyên qua ra màn hình chính
                        detectTapGestures { }
                    }
                    .padding(20.dp)
            ) {
                LaunchedEffect(visible) {
                    if (visible) {
                        delay(60)
                        try {
                            initialFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.player_settings_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .focusRequester(initialFocusRequester)
                                .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.12f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Mục 1: Tốc độ phát video
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_speed_title).uppercase(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "${currentSpeed}x",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                // Lưới nút tốc độ phát chia đều 3 cột x 2 hàng
                                val speedChunks = speeds.chunked(3)
                                speedChunks.forEach { rowSpeeds ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowSpeeds.forEach { sp ->
                                            val isSelected = currentSpeed == sp
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp)
                                                    .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.08f),
                                                shape = ShapeTokens.Button,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                onClick = { onSpeedSelected(sp) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${sp}x",
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        // Mục 2: Chất lượng Video HLS
                        item {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = stringResource(R.string.player_quality_title).uppercase(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            SettingsOptionCard(
                                title = stringResource(R.string.player_quality_auto),
                                subtitle = stringResource(R.string.player_quality_auto_desc),
                                isSelected = selectedVideoTrackId == "auto",
                                onClick = { onVideoTrackSelected(null) }
                            )
                        }

                        items(videoTracks) { track ->
                            SettingsOptionCard(
                                title = track.label,
                                subtitle = if (track.width > 0 && track.height > 0) "${track.width}x${track.height}" else null,
                                isSelected = selectedVideoTrackId == track.id,
                                onClick = { onVideoTrackSelected(track) }
                            )
                        }

                        // Mục 3: Luồng âm thanh (Audio Tracks)
                        if (audioTracks.isNotEmpty()) {
                            item {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = stringResource(R.string.player_audio_title).uppercase(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            items(audioTracks) { track ->
                                val specs = formatAudioSpecs(track)
                                val isSelected = if (selectedAudioTrackId != null) selectedAudioTrackId == track.id else track.isSelected
                                SettingsOptionCard(
                                    title = track.label,
                                    subtitle = specs.ifBlank { null },
                                    isSelected = isSelected,
                                    onClick = { onAudioTrackSelected(track) }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}
