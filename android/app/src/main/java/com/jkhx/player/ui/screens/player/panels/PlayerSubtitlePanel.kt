package com.jkhx.player.ui.screens.player.panels

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.R
import com.jkhx.player.core.player.SubtitleTrackOption
import com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem
import com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages
import com.jkhx.player.core.player.tts.model.TtsVoices
import com.jkhx.player.settings.SettingsManager
import com.jkhx.player.ui.screens.player.components.RepeatingIconButton
import com.jkhx.player.ui.screens.player.isEnglishTrack
import com.jkhx.player.ui.screens.player.isVietnameseTrack
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.delay

@Composable
internal fun PlayerSubtitlePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    isLandscape: Boolean,
    initialFocusRequester: FocusRequester,
    // Column 1: Languages
    configuredSubLanguages: Set<String>,
    selectedLangGroup: String,
    onSelectLangGroup: (String) -> Unit,
    onSelectNoneTrack: () -> Unit,
    // Column 2: Subtitle Tracks
    subtitleTracks: List<SubtitleTrackOption>,
    selectedSubtitleTrackId: String?,
    activeOnlineSubId: String?,
    stremioAddonSubsMap: Map<String, List<OnlineSubtitleItem>>,
    onlineSubsMap: Map<String, List<OnlineSubtitleItem>>,
    loadingSubsMap: Map<String, Boolean>,
    isStremioSubsLoading: Boolean,
    customSubSearchQuery: String?,
    onSearchQuerySubmit: (String) -> Unit,
    onRefreshOnlineSubs: (String, Boolean) -> Unit,
    onSelectStreamTrack: (SubtitleTrackOption) -> Unit,
    downloadingSubItemId: String?,
    translatedEnglishSubIds: Collection<String>,
    isTranslatingWithGemini: Boolean,
    onApplyOnlineSub: (OnlineSubtitleItem) -> Unit,
    onTranslateWithGemini: (OnlineSubtitleItem) -> Unit,
    // Column 3: Offset & AI Sync
    currentSubCues: List<com.jkhx.player.core.player.subtitle.model.SubtitleCue>,
    currentSubOffsetMs: Long,
    onUpdateSubOffset: (Long) -> Unit,
    isAiSyncing: Boolean,
    onAiSync: () -> Unit,
    // Column 3: TTS
    isTtsEnabled: Boolean,
    ttsVoice: String,
    ttsSpeechRate: Float,
    ttsDuckingVolume: Float,
    ttsApiKey: String,
    // Column 3: Style
    subtitleTextSize: Float,
    subtitleIsBold: Boolean,
    subtitleTextColor: Int,
    subtitleTextOpacity: Int,
    subtitleIsOutline: Boolean,
    subtitleOutlineColor: Int,
    subtitleBottomMargin: Int,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    var isEditingSubSearchQuery by remember { mutableStateOf(false) }
    var tempSubSearchQuery by remember { mutableStateOf("") }

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
                                    .fillMaxWidth(0.78f)
                                    .fillMaxHeight(0.78f)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            } else {
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.72f)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
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
                        // Header: Tiêu đề "Phụ đề" & Nút đóng
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (isLandscape) 12.dp else 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.player_subtitles_pill_label),
                                color = Color.White,
                                fontSize = if (isLandscape) 22.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onDismiss() },
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

                        // 3 Cột: Ngôn ngữ | Phụ đề | Kiểu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 16.dp else 8.dp)
                        ) {
                            // CỘT 1: Ngôn ngữ
                            Column(
                                modifier = Modifier
                                    .weight(if (isLandscape) 1f else 0.95f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = stringResource(R.string.player_subtitles_language_header),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = if (isLandscape) 13.sp else 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = if (isLandscape) 10.dp else 6.dp)
                                )

                                val allActiveLangCodes = remember(configuredSubLanguages, subtitleTracks) {
                                    val list = mutableListOf<String>()
                                    if (configuredSubLanguages.contains("vi")) list.add("vi")
                                    if (configuredSubLanguages.contains("en")) list.add("en")
                                    configuredSubLanguages.filter { it != "vi" && it != "en" }.sorted().forEach { list.add(it) }
                                    // Chỉ bổ sung thêm các ngôn ngữ nhúng sẵn trong track stream của video đang phát
                                    subtitleTracks.mapNotNull { it.language?.lowercase()?.trim() }
                                        .filter { it.isNotBlank() && !list.contains(it) && it != "und" }
                                        .distinct()
                                        .forEach { list.add(it) }
                                    list
                                }

                                val col1ItemHeight = if (isLandscape) 42.dp else 36.dp
                                val col1PaddingH = if (isLandscape) 14.dp else 8.dp
                                val col1FontSize = if (isLandscape) 13.5.sp else 12.sp
                                val col1BadgeSize = if (isLandscape) 22.dp else 18.dp
                                val col1BadgeFontSize = if (isLandscape) 11.sp else 9.5.sp

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 6.dp)
                                ) {
                                    // 1. "Không có"
                                    item {
                                        val isNoneSelected = selectedLangGroup == "none" || selectedSubtitleTrackId == "off"
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(col1ItemHeight)
                                                .tvFocusableItem(cornerRadius = col1ItemHeight / 2, focusedScale = 1.05f),
                                            shape = RoundedCornerShape(col1ItemHeight / 2),
                                            color = if (isNoneSelected && selectedSubtitleTrackId == "off") MaterialTheme.colorScheme.primary else Color.Transparent,
                                            onClick = onSelectNoneTrack
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = col1PaddingH),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.player_subtitles_none),
                                                    color = Color.White,
                                                    fontSize = col1FontSize,
                                                    fontWeight = if (isNoneSelected && selectedSubtitleTrackId == "off") FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // 2. Danh sách các ngôn ngữ (Được cấu hình + có sẵn trong stream)
                                    items(allActiveLangCodes) { langCode ->
                                        val isLangSelected = selectedLangGroup == langCode
                                        val streamCount = subtitleTracks.count {
                                            when (langCode) {
                                                "vi" -> isVietnameseTrack(it)
                                                "en" -> isEnglishTrack(it)
                                                else -> it.language?.equals(langCode, true) == true
                                            }
                                        }
                                        val onlineCount = ((stremioAddonSubsMap[langCode] ?: emptyList()) + (onlineSubsMap[langCode] ?: emptyList()))
                                            .distinctBy { it.downloadUrl ?: it.id }
                                            .size
                                        val totalCount = streamCount + onlineCount
                                        val displayName = com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.getDisplayName(langCode)

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(col1ItemHeight)
                                                .tvFocusableItem(cornerRadius = col1ItemHeight / 2, focusedScale = 1.05f),
                                            shape = RoundedCornerShape(col1ItemHeight / 2),
                                            color = if (isLangSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            onClick = {
                                                onSelectLangGroup(langCode)
                                                if (onlineSubsMap[langCode] == null && loadingSubsMap[langCode] != true) {
                                                    onRefreshOnlineSubs(langCode, false)
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = col1PaddingH),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    color = Color.White,
                                                    fontSize = col1FontSize,
                                                    fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (totalCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(col1BadgeSize)
                                                            .background(if (isLangSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$totalCount",
                                                            color = Color.White,
                                                            fontSize = col1BadgeFontSize,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // CỘT 2: Phụ đề (Unified Stream + Online Subtitles)
                            Column(
                                modifier = Modifier
                                    .weight(if (isLandscape) 1.35f else 1.3f)
                                    .fillMaxHeight()
                            ) {
                                // Header: Tiêu đề theo nhóm ngôn ngữ + Nút Sửa từ khóa + Nút Làm mới
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (isLandscape) 8.dp else 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (selectedLangGroup) {
                                            "vi" -> stringResource(R.string.player_subtitles_vi_header)
                                            "en" -> stringResource(R.string.player_subtitles_en_header)
                                            "none" -> stringResource(R.string.player_subtitles_none)
                                            else -> stringResource(R.string.player_subtitles_stream_header, selectedLangGroup.uppercase())
                                        },
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = if (isLandscape) 13.sp else 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (selectedLangGroup == "vi" || selectedLangGroup == "en") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            // Nút sửa từ khóa
                                            IconButton(
                                                onClick = {
                                                    isEditingSubSearchQuery = !isEditingSubSearchQuery
                                                    if (isEditingSubSearchQuery) {
                                                        tempSubSearchQuery = customSubSearchQuery ?: ""
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .tvFocusableItem(cornerRadius = 15.dp, focusedScale = 1.15f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.player_subtitles_edit_query),
                                                    tint = if (isEditingSubSearchQuery) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Nút làm mới danh sách (force refresh online)
                                            val isLangLoading = (loadingSubsMap[selectedLangGroup] == true) || isStremioSubsLoading
                                            IconButton(
                                                onClick = {
                                                    if (!isLangLoading) {
                                                        if (selectedLangGroup != "none") {
                                                            onRefreshOnlineSubs(selectedLangGroup, true)
                                                        }
                                                    }
                                                },
                                                enabled = !isLangLoading,
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .tvFocusableItem(cornerRadius = 15.dp, focusedScale = 1.15f)
                                            ) {
                                                if (isLangLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = stringResource(R.string.player_subtitles_refresh),
                                                        tint = Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Ô sửa từ khóa tìm kiếm (khi bật Edit)
                                if (isEditingSubSearchQuery && selectedLangGroup != "none") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        BasicTextField(
                                            value = tempSubSearchQuery,
                                            onValueChange = { tempSubSearchQuery = it },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            decorationBox = { innerTextField ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0xFF161920), RoundedCornerShape(8.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Search,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                                        if (tempSubSearchQuery.isEmpty()) {
                                                            Text(
                                                                stringResource(R.string.player_subtitles_edit_search_hint),
                                                                fontSize = 11.5.sp,
                                                                color = Color.White.copy(alpha = 0.35f)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    if (tempSubSearchQuery.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = { tempSubSearchQuery = "" },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Clear",
                                                                tint = Color.White.copy(alpha = 0.5f),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        Button(
                                            onClick = {
                                                onSearchQuerySubmit(tempSubSearchQuery.trim())
                                                isEditingSubSearchQuery = false
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.06f)
                                        ) {
                                            Text(stringResource(R.string.player_subtitles_search_action), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                // Danh sách các bản phụ đề
                                val isLangLoading = (loadingSubsMap[selectedLangGroup] == true) || isStremioSubsLoading
                                val onlineItems = ((stremioAddonSubsMap[selectedLangGroup] ?: emptyList()) + (onlineSubsMap[selectedLangGroup] ?: emptyList()))
                                    .distinctBy { it.downloadUrl ?: it.id }
                                val streamItems = remember(subtitleTracks, selectedLangGroup) {
                                    when (selectedLangGroup) {
                                        "vi" -> subtitleTracks.filter { isVietnameseTrack(it) }
                                        "en" -> subtitleTracks.filter { isEnglishTrack(it) }
                                        "none" -> emptyList()
                                        else -> subtitleTracks.filter { it.language?.equals(selectedLangGroup, true) == true }
                                    }
                                }

                                if (selectedLangGroup == "none") {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.player_subtitle_off),
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 13.sp
                                        )
                                    }
                                } else if (isLangLoading && streamItems.isEmpty() && onlineItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = stringResource(R.string.player_subtitles_loading_online),
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                } else if (streamItems.isEmpty() && onlineItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.player_subtitles_no_results),
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.5.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            TextButton(
                                                onClick = {
                                                            onRefreshOnlineSubs(selectedLangGroup, true)
                                                }
                                            ) {
                                                Text(stringResource(R.string.player_subtitles_refresh), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. Phụ đề có sẵn từ luồng phát (Stream / Plugin / Custom)
                                        items(streamItems) { track ->
                                            val isSelected = selectedSubtitleTrackId == track.id
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.03f),
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E222B),
                                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                                onClick = { onSelectStreamTrack(track) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        val badgeText = when {
                                                            track.label.contains("Gemini", true) -> "✨ [JT-SUBTITLES] Gemini AI"
                                                            track.label.contains("OpenSubtitles", true) -> "[OpenSubtitles]"
                                                            track.label.contains("SubDL", true) -> "[SubDL]"
                                                            track.label.contains("Subsource", true) -> "[Subsource]"
                                                            else -> stringResource(R.string.player_subtitles_stream_badge)
                                                        }
                                                        Text(
                                                            text = badgeText,
                                                            color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier
                                                                .background(
                                                                    if (isSelected) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                                    RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = track.label,
                                                            color = Color.White,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Phụ đề trực tuyến (Online Subtitles: OpenSubtitles, SubDL, Subsource)
                                        items(onlineItems) { item ->
                                            val customItemId = "online_${item.source}_${item.id}"
                                            val isSelected = (selectedSubtitleTrackId == customItemId) || (activeOnlineSubId == item.id)
                                            val isDownloadingThis = downloadingSubItemId == item.id

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.03f),
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E222B),
                                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                                onClick = {
                                                    if (!isDownloadingThis && !isTranslatingWithGemini) {
                                                        onApplyOnlineSub(item)
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "[${item.source}]",
                                                                color = if (isSelected) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.primary,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier
                                                                    .background(
                                                                        if (isSelected) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                                            )
                                                            val dlCount = item.downloadCount
                                                            if (dlCount != null && dlCount > 0) {
                                                                Text(
                                                                    text = "⬇ $dlCount",
                                                                    color = Color.White.copy(alpha = 0.6f),
                                                                    fontSize = 10.5.sp
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = item.name,
                                                            color = Color.White,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        // Nút Dịch AI trực tiếp sang Tiếng Việt cho các bản sub ngoại ngữ
                                                        if (selectedLangGroup != "vi" && selectedLangGroup != "none") {
                                                            val isAlreadyTranslated = translatedEnglishSubIds.contains(item.id)
                                                            if (isAlreadyTranslated) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(0xFF2E7D32),
                                                                    modifier = Modifier.tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                                    onClick = {
                                                                        onSelectLangGroup("vi")
                                                                    }
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Check,
                                                                            contentDescription = null,
                                                                            tint = Color.White,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = "Đã dịch AI",
                                                                            color = Color.White,
                                                                            fontSize = 10.5.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(0xFF1565C0),
                                                                    modifier = Modifier.tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                                    onClick = {
                                                                        if (!isDownloadingThis && !isTranslatingWithGemini) {
                                                                            onTranslateWithGemini(item)
                                                                        }
                                                                    }
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.AutoAwesome,
                                                                            contentDescription = null,
                                                                            tint = Color.Yellow,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = stringResource(R.string.player_subtitles_translate_ai),
                                                                            color = Color.White,
                                                                            fontSize = 10.5.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (isDownloadingThis) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                strokeWidth = 2.dp,
                                                                color = Color.White
                                                            )
                                                        } else if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        // CỘT 3: Kiểu
                        Column(
                            modifier = Modifier
                                .weight(if (isLandscape) 1.2f else 1.15f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = stringResource(R.string.player_subtitles_style_header),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = if (isLandscape) 13.sp else 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = if (isLandscape) 10.dp else 6.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 8.dp)
                            ) {
                                // 1. Độ trễ phụ đề (Offset)
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_delay_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Nút Giảm (-) có hỗ trợ nhấn giữ tăng tốc
                                        RepeatingIconButton(
                                            onClick = { onUpdateSubOffset(currentSubOffsetMs - 500L) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF262A34))
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${if (currentSubOffsetMs >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.3f", currentSubOffsetMs / 1000f)}s",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }

                                        // Nút Tăng (+) có hỗ trợ nhấn giữ tăng tốc
                                        RepeatingIconButton(
                                            onClick = { onUpdateSubOffset(currentSubOffsetMs + 500L) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF262A34))
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(5.dp))

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(30.dp)
                                            .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.04f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF262A34),
                                        onClick = { onUpdateSubOffset(0L) }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = stringResource(R.string.player_subtitles_offset_reset),
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // 1.2. Tự đồng bộ bằng AI (AI Auto-Sync độc lập)
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.04f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAiSyncing) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                                        onClick = onAiSync
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isAiSyncing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.player_subtitles_ai_sync_loading),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFD54F),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.player_subtitles_ai_sync_btn),
                                                    color = Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                // 1.3. Thuyết minh AI (TTS / Voiceover)
                                item {
                                    Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.player_tts_title),
                                                color = if (isTtsEnabled) Color.White else Color.White.copy(alpha = 0.8f),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Switch(
                                            checked = isTtsEnabled,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    if (currentSubCues.isEmpty()) {
                                                        Toast.makeText(context, context.getString(R.string.player_subtitles_no_cues_to_sync), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                settingsManager.setTtsEnabled(enabled)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                                uncheckedTrackColor = Color(0xFF262A34)
                                            ),
                                            modifier = Modifier
                                                .scale(0.8f)
                                                .tvFocusableItem(cornerRadius = 14.dp, focusedScale = 1.15f)
                                        )
                                    }

                                    if (isTtsEnabled) {
                                        Spacer(modifier = Modifier.height(6.dp))


                                        // Giọng đọc (Voice)
                                        Text(
                                            text = stringResource(R.string.player_tts_voice_header),
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val voiceOptions = com.jkhx.player.core.player.tts.model.TtsVoices.ALL
                                        val activeNormVoice = com.jkhx.player.core.player.tts.model.TtsVoices.normalizeVoiceId(ttsVoice)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            voiceOptions.take(3).forEach { opt ->
                                                val isSelected = activeNormVoice == opt.id
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                        .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = {
                                                        settingsManager.setTtsVoice(opt.id)
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = opt.name,
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            voiceOptions.drop(3).forEach { opt ->
                                                val isSelected = activeNormVoice == opt.id
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                        .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = {
                                                        settingsManager.setTtsVoice(opt.id)
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = opt.name,
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Âm lượng phim nền (Ducking)
                                        Text(
                                            text = "${stringResource(R.string.player_tts_ducking_header)}: ${(ttsDuckingVolume * 100).toInt()}%",
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(0.20f to "20%", 0.35f to "35%", 0.50f to "50%").forEach { (vol, label) ->
                                                val isSelected = kotlin.math.abs(ttsDuckingVolume - vol) < 0.05f
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                        .tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = { settingsManager.setTtsDuckingVolume(vol) }
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = label,
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Tốc độ đọc (Speech rate)
                                        Text(
                                            text = "${stringResource(R.string.player_tts_speech_rate_header)}: ${ttsSpeechRate}x",
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1.0f to "1.0x", 1.15f to "1.15x", 1.25f to "1.25x", 1.4f to "1.4x").forEach { (rate, label) ->
                                                val isSelected = kotlin.math.abs(ttsSpeechRate - rate) < 0.05f
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                        .tvFocusableItem(cornerRadius = 6.dp, focusedScale = 1.08f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                                    onClick = { settingsManager.setTtsSpeechRate(rate) }
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = label,
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                }

                                // 2. Cỡ chữ
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_font_size_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newSize = (subtitleTextSize - 2f).coerceAtLeast(12f)
                                                settingsManager.setSubtitleTextSize(newSize)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${subtitleTextSize.toInt()}sp",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newSize = (subtitleTextSize + 2f).coerceAtMost(36f)
                                                settingsManager.setSubtitleTextSize(newSize)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // 3. In đậm
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_bold_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .tvFocusableItem(cornerRadius = 17.dp, focusedScale = 1.06f),
                                        shape = RoundedCornerShape(17.dp),
                                        color = if (subtitleIsBold) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                        onClick = { settingsManager.setSubtitleIsBold(!subtitleIsBold) }
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (subtitleIsBold) stringResource(R.string.player_subtitles_btn_on) else stringResource(R.string.player_subtitles_btn_off),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // 4. Màu sắc
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_color_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val textColors = listOf(
                                        0xFFFFFFFF.toInt(),
                                        0xFFFFEB3B.toInt(),
                                        0xFF00E5FF.toInt(),
                                        0xFFFF5252.toInt(),
                                        0xFF00E676.toInt(),
                                        0xFFE040FB.toInt(),
                                        0xFFFF9100.toInt()
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        textColors.forEach { col ->
                                            val isSelected = subtitleTextColor == col
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .background(Color(col), CircleShape)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                                                        shape = CircleShape
                                                    )
                                                    .tvFocusableItem(cornerRadius = 13.dp, focusedScale = 1.25f)
                                                    .clickable { settingsManager.setSubtitleTextColor(col) }
                                            )
                                        }
                                    }
                                }

                                // 5. Độ mờ chữ
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_opacity_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newOp = (subtitleTextOpacity - 10).coerceAtLeast(20)
                                                settingsManager.setSubtitleTextOpacity(newOp)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${subtitleTextOpacity}%",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newOp = (subtitleTextOpacity + 10).coerceAtMost(100)
                                                settingsManager.setSubtitleTextOpacity(newOp)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // 6. Viền chữ
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_outline_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .tvFocusableItem(cornerRadius = 17.dp, focusedScale = 1.06f),
                                        shape = RoundedCornerShape(17.dp),
                                        color = if (subtitleIsOutline) MaterialTheme.colorScheme.primary else Color(0xFF262A34),
                                        onClick = { settingsManager.setSubtitleIsOutline(!subtitleIsOutline) }
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (subtitleIsOutline) stringResource(R.string.player_subtitles_btn_on) else stringResource(R.string.player_subtitles_btn_off),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // 7. Màu viền
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_outline_color_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val outlineColors = listOf(
                                        0xFF000000.toInt(),
                                        0xFFFFFFFF.toInt(),
                                        0xFF00E5FF.toInt(),
                                        0xFFFF5252.toInt()
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        outlineColors.forEach { col ->
                                            val isSelected = subtitleOutlineColor == col
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .background(Color(col), CircleShape)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                                        shape = CircleShape
                                                    )
                                                    .tvFocusableItem(cornerRadius = 13.dp, focusedScale = 1.25f)
                                                    .clickable { settingsManager.setSubtitleOutlineColor(col) }
                                            )
                                        }
                                    }
                                }

                                // 8. Khoảng cách lề dưới (Bottom Margin - càng tăng càng đẩy lên cao)
                                item {
                                    Text(
                                        text = stringResource(R.string.player_subtitles_bottom_margin_label),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.player_subtitles_bottom_margin_hint),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newMargin = (subtitleBottomMargin - 5).coerceAtLeast(0)
                                                settingsManager.setSubtitleBottomMargin(newMargin)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$subtitleBottomMargin",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF262A34),
                                            onClick = {
                                                val newMargin = (subtitleBottomMargin + 5).coerceAtMost(100)
                                                settingsManager.setSubtitleBottomMargin(newMargin)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // 9. Đặt lại mặc định
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { settingsManager.resetSubtitleStyleDefaults() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF262A34),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.04f)
                                    ) {
                                        Text(stringResource(R.string.player_subtitles_reset_defaults), fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
