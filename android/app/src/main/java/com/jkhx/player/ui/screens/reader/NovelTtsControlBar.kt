package com.jkhx.player.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.R
import com.jkhx.player.core.player.tts.model.TtsEngineType
import com.jkhx.player.core.player.tts.model.TtsVoices
import com.jkhx.player.core.player.tts.novel.NovelTtsManager
import com.jkhx.player.settings.SettingsManager
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelTtsControlBar(
    novelTtsManager: NovelTtsManager,
    settingsManager: SettingsManager? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by novelTtsManager.isPlaying.collectAsState()
    val isLoading by novelTtsManager.isLoading.collectAsState()
    val currentParagraphIndex by novelTtsManager.currentParagraphIndex.collectAsState()
    val isReadingHeader by novelTtsManager.isReadingHeader.collectAsState()
    val paragraphs by novelTtsManager.paragraphs.collectAsState()
    val speechRate by novelTtsManager.speechRate.collectAsState()
    val sleepTimerMinutes by novelTtsManager.sleepTimerMinutes.collectAsState()
    val sleepTimerSecondsRemaining by novelTtsManager.sleepTimerSecondsRemaining.collectAsState()

    var showVoiceSettingsSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.space12, vertical = AppTheme.dimens.space8),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF181A20).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.space14, vertical = AppTheme.dimens.space10)
        ) {
            // Hàng 1: Đoạn X/Y + Thanh Slider tua nhanh đoạn văn
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isReadingHeader || currentParagraphIndex < 0) {
                        stringResource(R.string.reader_tts_reading_title)
                    } else {
                        stringResource(
                            R.string.reader_tts_paragraph_label,
                            currentParagraphIndex + 1,
                            maxOf(1, paragraphs.size)
                        )
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                // Chỉ báo hẹn giờ ngủ nếu đang chạy
                if (sleepTimerMinutes != NovelTtsManager.SLEEP_TIMER_OFF) {
                    val timerText = if (sleepTimerMinutes == NovelTtsManager.SLEEP_TIMER_END_OF_CHAPTER) {
                        stringResource(R.string.reader_tts_timer_end_chapter)
                    } else {
                        val mins = sleepTimerSecondsRemaining / 60
                        val secs = sleepTimerSecondsRemaining % 60
                        "%02d:%02d".format(mins, secs)
                    }
                    Surface(
                        shape = ShapeTokens.Chip,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable { showSleepTimerSheet = true }
                            .padding(end = AppTheme.dimens.space6)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = AppTheme.dimens.space8, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = timerText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Nút đóng TTS bar
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.reader_tts_stop),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Thanh trượt tua đoạn văn
            if (paragraphs.size > 1) {
                Slider(
                    value = if (isReadingHeader || currentParagraphIndex < 0) 0f else currentParagraphIndex.coerceAtLeast(0).toFloat(),
                    onValueChange = { novelTtsManager.seekToParagraph(it.toInt()) },
                    valueRange = 0f..maxOf(1f, (paragraphs.size - 1).toFloat()),
                    steps = if (paragraphs.size > 2) paragraphs.size - 2 else 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            } else {
                Spacer(modifier = Modifier.height(AppTheme.dimens.space6))
            }

            // Hàng 2: Bộ phím điều khiển phát & cài đặt nhanh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tùy chọn tốc độ đọc (0.95x -> 1.25x) - Tự động lưu vào SettingsManager
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.clickable {
                        val nextSpeed = when {
                            speechRate < 0.98f -> 1.0f
                            speechRate < 1.05f -> 1.1f
                            speechRate < 1.13f -> 1.15f
                            speechRate < 1.20f -> 1.25f
                            else -> 0.95f
                        }
                        novelTtsManager.setSpeechRate(nextSpeed)
                        settingsManager?.setTtsNovelSpeechRate(nextSpeed)
                    }
                ) {
                    Text(
                        text = formatSpeedLabel(speechRate),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = AppTheme.dimens.space8, vertical = AppTheme.dimens.space6)
                    )
                }

                // Cụm điều khiển chính: [Lùi đoạn] - [Play/Pause] - [Tiến đoạn]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space14)
                ) {
                    val canGoPrev = currentParagraphIndex > 0 || !isReadingHeader
                    IconButton(
                        onClick = { novelTtsManager.previousParagraph() },
                        enabled = canGoPrev,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.reader_tts_prev_paragraph),
                            tint = if (canGoPrev) Color.White else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play / Pause Button with Loading state
                    FilledIconButton(
                        onClick = { novelTtsManager.togglePlayPause() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(46.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { novelTtsManager.nextParagraph() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.reader_tts_next_paragraph),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Cụm bên phải: Hẹn giờ & Cài đặt giọng đọc
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSleepTimerSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = stringResource(R.string.reader_tts_timer),
                            tint = if (sleepTimerMinutes != NovelTtsManager.SLEEP_TIMER_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showVoiceSettingsSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.reader_tts_settings_title),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal BottomSheet: Cài đặt Giọng đọc & Engine
    if (showVoiceSettingsSheet && settingsManager != null) {
        NovelTtsSettingsSheet(
            novelTtsManager = novelTtsManager,
            settingsManager = settingsManager,
            isPlaying = isPlaying,
            onStartPlay = {
                novelTtsManager.togglePlayPause()
                showVoiceSettingsSheet = false
            },
            onDismissRequest = { showVoiceSettingsSheet = false }
        )
    }

    // Modal BottomSheet: Hẹn giờ tắt (Sleep Timer)
    if (showSleepTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            containerColor = Color(0xFF1E2026),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.dimens.space20, vertical = AppTheme.dimens.space8)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = stringResource(R.string.reader_tts_timer),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = AppTheme.dimens.space14)
                )

                val timerOptions = listOf(
                    NovelTtsManager.SLEEP_TIMER_OFF to stringResource(R.string.reader_tts_timer_off),
                    15 to stringResource(R.string.reader_tts_timer_15),
                    30 to stringResource(R.string.reader_tts_timer_30),
                    45 to stringResource(R.string.reader_tts_timer_45),
                    60 to stringResource(R.string.reader_tts_timer_60),
                    NovelTtsManager.SLEEP_TIMER_END_OF_CHAPTER to stringResource(R.string.reader_tts_timer_end_chapter)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(timerOptions) { (mins, label) ->
                        val isSelected = sleepTimerMinutes == mins
                        Surface(
                            onClick = {
                                novelTtsManager.setSleepTimer(mins)
                                showSleepTimerSheet = false
                            },
                            shape = ShapeTokens.Card,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.06f),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppTheme.dimens.space14, vertical = AppTheme.dimens.space12),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppTheme.dimens.space20))
            }
        }
    }
}

/**
 * Modal BottomSheet: Cài đặt Giọng đọc & Engine TTS
 * Có thể mở từ TopBar (không tự động phát) hoặc từ thanh Media Control Bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelTtsSettingsSheet(
    novelTtsManager: NovelTtsManager,
    settingsManager: SettingsManager,
    isPlaying: Boolean,
    onStartPlay: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val config by novelTtsManager.config.collectAsState()
    val speechRate by novelTtsManager.speechRate.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1E2026),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.space20, vertical = AppTheme.dimens.space8)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppTheme.dimens.space14),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.reader_tts_settings_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Nút bắt đầu nghe / tạm dừng nổi bật
            Button(
                onClick = onStartPlay,
                shape = ShapeTokens.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (!isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = if (!isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppTheme.dimens.space8))
                Text(
                    text = if (!isPlaying) stringResource(R.string.reader_tts_start) else stringResource(R.string.player_pause),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.dimens.space16))

            // 1. Chọn Engine: Offline System vs OpenAI TTS Cloud
            Text(
                text = stringResource(R.string.player_tts_engine_header),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AppTheme.dimens.space8)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space10)
            ) {
                val isSystem = config.engineType == TtsEngineType.OFFLINE_SYSTEM
                Surface(
                    onClick = {
                        val newConf = config.copy(engineType = TtsEngineType.OFFLINE_SYSTEM)
                        novelTtsManager.updateConfig(newConf)
                        settingsManager.setTtsEngine(TtsEngineType.OFFLINE_SYSTEM.id)
                    },
                    shape = ShapeTokens.Card,
                    color = if (isSystem) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                    border = if (isSystem) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(AppTheme.dimens.space10)) {
                        Text(
                            text = "Google TTS (Offline)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (isSystem) MaterialTheme.colorScheme.primary else Color.White
                        )
                        Text(
                            text = "Tích hợp máy • Miễn phí",
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                val isCloud = config.engineType != TtsEngineType.OFFLINE_SYSTEM
                Surface(
                    onClick = {
                        val newConf = config.copy(engineType = TtsEngineType.OPENAI_TTS)
                        novelTtsManager.updateConfig(newConf)
                        settingsManager.setTtsEngine(TtsEngineType.OPENAI_TTS.id)
                    },
                    shape = ShapeTokens.Card,
                    color = if (isCloud) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                    border = if (isCloud) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(AppTheme.dimens.space10)) {
                        Text(
                            text = "OpenAI TTS (Cloud)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (isCloud) MaterialTheme.colorScheme.primary else Color.White
                        )
                        Text(
                            text = "Gateway • /v1/audio/speech",
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.dimens.space16))

            // 2. Chọn Tốc độ đọc (0.95x, 1.0x, 1.1x, 1.15x, 1.25x)
            Text(
                text = "${stringResource(R.string.player_tts_speech_rate_header)}: ${formatSpeedLabel(speechRate)}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AppTheme.dimens.space8)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space8)
            ) {
                val speeds = listOf(0.95f, 1.0f, 1.1f, 1.15f, 1.25f)
                speeds.forEach { rate ->
                    val isSpeedSelected = kotlin.math.abs(speechRate - rate) < 0.03f
                    Surface(
                        onClick = {
                            novelTtsManager.setSpeechRate(rate)
                            settingsManager.setTtsNovelSpeechRate(rate)
                        },
                        shape = ShapeTokens.Chip,
                        color = if (isSpeedSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = AppTheme.dimens.space8),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatSpeedLabel(rate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSpeedSelected) MaterialTheme.colorScheme.onPrimary else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.dimens.space16))

            // 3. Chọn Giọng đọc tiếng Việt
            Text(
                text = stringResource(R.string.player_tts_voice_header),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AppTheme.dimens.space8)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space6),
                modifier = Modifier.heightIn(max = 220.dp)
            ) {
                items(TtsVoices.ALL) { voice ->
                    val isSelected = config.voiceId.equals(voice.id, ignoreCase = true)
                    Surface(
                        onClick = {
                            val newConf = config.copy(voiceId = voice.id)
                            novelTtsManager.updateConfig(newConf)
                            settingsManager.setTtsVoice(voice.id)
                        },
                        shape = ShapeTokens.Card,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.06f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppTheme.dimens.space14, vertical = AppTheme.dimens.space10),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                )
                                Text(
                                    text = voice.tag,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
        }
    }
}

private fun formatSpeedLabel(rate: Float): String {
    return when {
        kotlin.math.abs(rate - 0.95f) < 0.01f -> "0.95x"
        kotlin.math.abs(rate - 1.0f) < 0.01f -> "1.0x"
        kotlin.math.abs(rate - 1.1f) < 0.01f -> "1.1x"
        kotlin.math.abs(rate - 1.15f) < 0.01f -> "1.15x"
        kotlin.math.abs(rate - 1.25f) < 0.01f -> "1.25x"
        else -> "%.2fx".format(rate)
    }
}
