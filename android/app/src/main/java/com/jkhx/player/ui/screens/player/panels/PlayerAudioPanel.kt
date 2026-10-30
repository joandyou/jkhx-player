package com.jkhx.player.ui.screens.player.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.delay

@Composable
internal fun PlayerAudioPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    isLandscape: Boolean,
    initialFocusRequester: FocusRequester,
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
                .background(Color.Black.copy(alpha = 0.52f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (isLandscape) {
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.55f)
                                .fillMaxHeight(0.70f)
                                .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        } else {
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(0.55f)
                                .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        }
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                LaunchedEffect(visible) {
                    if (visible) {
                        delay(60)
                        try {
                            initialFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header: Tiêu đề "Luồng Âm Thanh" & Nút đóng
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.player_audio_title),
                                color = Color.White,
                                fontSize = if (isLandscape) 20.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (audioTracks.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${audioTracks.size}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .focusRequester(initialFocusRequester)
                                .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.12f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    if (audioTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.player_audio_auto),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.player_audio_auto_desc),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Danh sách các luồng âm thanh chi tiết
                            items(audioTracks) { track ->
                                val isTrackSelected = if (selectedAudioTrackId != null) selectedAudioTrackId == track.id else track.isSelected
                                val specs = formatAudioSpecs(track)
                                val isDubbed = (track.roleFlags and androidx.media3.common.C.ROLE_FLAG_DUB) != 0
                                val isOriginal = (track.roleFlags and androidx.media3.common.C.ROLE_FLAG_MAIN) != 0

                                Surface(
                                    onClick = { onAudioTrackSelected(track) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isTrackSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color(0xFF1E222B),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isTrackSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.03f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = track.label,
                                                    color = if (isTrackSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                    fontWeight = if (isTrackSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 14.5.sp
                                                )
                                                if (isDubbed) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        color = Color(0xFFFF9800).copy(alpha = 0.22f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.player_audio_dubbed_badge),
                                                            color = Color(0xFFFFB74D),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                } else if (isOriginal) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        color = Color(0xFF4CAF50).copy(alpha = 0.22f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.player_audio_original_badge),
                                                            color = Color(0xFF81C784),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (specs.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = specs,
                                                    color = Color.White.copy(alpha = 0.65f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        if (isTrackSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
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
    }
}

