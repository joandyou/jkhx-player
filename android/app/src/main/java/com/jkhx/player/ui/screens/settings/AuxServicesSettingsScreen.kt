package com.jkhx.player.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.ui.screens.stream.badge.*
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private enum class AuxServiceDialogType {
    OPEN_SUBTITLES,
    SUBDL,
    SUBSOURCE,
    TMDB,
    GEMINI_AI,
    TTS_GATEWAY,
    SUBTITLE_LANGUAGES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuxServicesSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = JkhxApplication.instance.settingsManager
    val subtitleManager = JkhxApplication.instance.subtitleManager
    val tmdbRepo = JkhxApplication.instance.tmdbRepository

    // State bindings from SettingsManager
    val savedTmdbKey by settingsManager.tmdbApiKey.collectAsState()
    val isCustomTmdbKey = savedTmdbKey.isNotBlank()
    val savedOpenSubKey by settingsManager.openSubtitlesApiKey.collectAsState()
    val savedSubdlKey by settingsManager.subdlApiKey.collectAsState()
    val savedSubsourceKey by settingsManager.subsourceApiKey.collectAsState()
    val savedGeminiKey by settingsManager.geminiApiKey.collectAsState()
    val savedGeminiModel by settingsManager.geminiModel.collectAsState()
    val savedTtsEndpoint by settingsManager.ttsApiEndpoint.collectAsState()
    val savedTtsAudioFormat by settingsManager.ttsAudioFormat.collectAsState()
    val savedTtsKey by settingsManager.ttsApiKey.collectAsState()

    // Fusion Badges & Stream Display settings
    val streamBadgeRulesJson by settingsManager.streamBadgeRulesJson.collectAsState()
    val streamBadgePlacement by settingsManager.streamBadgePlacement.collectAsState()
    val showStreamSizeBadges by settingsManager.showStreamSizeBadges.collectAsState()

    val streamBadgeRules = remember(streamBadgeRulesJson) {
        StreamBadgeJsonHelper.decodeRules(streamBadgeRulesJson)
    }

    var showBadgeImportDialog by remember { mutableStateOf(false) }
    var showBadgePositionDialog by remember { mutableStateOf(false) }
    var previewImportItem by remember { mutableStateOf<StreamBadgeImport?>(null) }

    var activeDialog by remember { mutableStateOf<AuxServiceDialogType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_aux_services_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space12)
        ) {
            // Header Description
            Text(
                text = stringResource(R.string.settings_aux_services_desc),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ========================================================
            // NHÓM: KIỂU FUSION
            // ========================================================
            Text(
                text = stringResource(R.string.settings_stream_fusion_section),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Nhãn dung lượng
                    FusionSwitchRow(
                        title = stringResource(R.string.settings_stream_size_badges_title),
                        subtitle = stringResource(R.string.settings_stream_size_badges_desc),
                        checked = showStreamSizeBadges,
                        onCheckedChange = { settingsManager.setShowStreamSizeBadges(it) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // Vị trí nhãn
                    FusionNavigationRow(
                        title = stringResource(R.string.settings_stream_badge_position_title),
                        subtitle = if (streamBadgePlacement == StreamBadgePlacement.BOTTOM)
                            stringResource(R.string.settings_stream_badge_position_bottom)
                        else
                            stringResource(R.string.settings_stream_badge_position_top),
                        onClick = { showBadgePositionDialog = true }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // URL nhãn Fusion
                    FusionNavigationRow(
                        title = stringResource(R.string.settings_stream_badge_urls_title),
                        subtitle = stringResource(
                            R.string.settings_stream_badge_urls_summary,
                            streamBadgeRules.imports.size,
                            streamBadgeRules.enabledFilterCount
                        ),
                        onClick = { showBadgeImportDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========================================================
            // NHÓM 1: DỊCH VỤ PHỤ ĐỀ & SIÊU DỮ LIỆU
            // ========================================================
            Text(
                text = stringResource(R.string.settings_group_subtitles_metadata),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // OpenSubtitles
                    ServiceRowItem(
                        icon = Icons.Default.Subtitles,
                        title = "OpenSubtitles.com",
                        subtitle = if (savedOpenSubKey.isNotBlank()) stringResource(R.string.settings_status_key_configured) else stringResource(R.string.settings_status_key_not_set),
                        isConfigured = savedOpenSubKey.isNotBlank(),
                        onClick = { activeDialog = AuxServiceDialogType.OPEN_SUBTITLES }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // SubDL
                    ServiceRowItem(
                        icon = Icons.Default.Key,
                        title = "SubDL",
                        subtitle = if (savedSubdlKey.isNotBlank()) stringResource(R.string.settings_status_key_configured) else stringResource(R.string.settings_status_key_not_set),
                        isConfigured = savedSubdlKey.isNotBlank(),
                        onClick = { activeDialog = AuxServiceDialogType.SUBDL }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // Subsource
                    ServiceRowItem(
                        icon = Icons.Default.Key,
                        title = "SubSource",
                        subtitle = if (savedSubsourceKey.isNotBlank()) stringResource(R.string.settings_status_key_configured) else stringResource(R.string.settings_status_key_not_set),
                        isConfigured = savedSubsourceKey.isNotBlank(),
                        onClick = { activeDialog = AuxServiceDialogType.SUBSOURCE }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // TMDB
                    ServiceRowItem(
                        icon = Icons.Default.Movie,
                        title = "TMDB (The Movie Database)",
                        subtitle = if (isCustomTmdbKey) stringResource(R.string.settings_status_tmdb_custom) else stringResource(R.string.settings_status_key_not_set),
                        isConfigured = isCustomTmdbKey,
                        onClick = { activeDialog = AuxServiceDialogType.TMDB }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // Ngôn ngữ tìm kiếm phụ đề
                    val savedSubLangs by settingsManager.subtitleSearchLanguages.collectAsState()
                    val subLangSummary = remember(savedSubLangs) {
                        savedSubLangs.map { com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.getDisplayName(it) }.joinToString(", ")
                    }
                    ServiceRowItem(
                        icon = Icons.Default.Translate,
                        title = stringResource(R.string.settings_subtitle_languages_title),
                        subtitle = subLangSummary.ifBlank { "Tiếng Việt, Tiếng Anh" },
                        isConfigured = true,
                        onClick = { activeDialog = AuxServiceDialogType.SUBTITLE_LANGUAGES }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========================================================
            // NHÓM 2: TRÍ TUỆ NHÂN TẠO & THUYẾT MINH AI
            // ========================================================
            Text(
                text = stringResource(R.string.settings_group_ai_tts),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Google Gemini AI
                    ServiceRowItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "Google Gemini AI",
                        subtitle = if (savedGeminiKey.isNotBlank()) "Đã cấu hình • $savedGeminiModel" else stringResource(R.string.settings_status_key_not_set),
                        isConfigured = savedGeminiKey.isNotBlank(),
                        onClick = { activeDialog = AuxServiceDialogType.GEMINI_AI }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // AI Voiceover & TTS Gateway
                    ServiceRowItem(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Thuyết Minh AI & TTS Gateway",
                        subtitle = if (savedTtsEndpoint.isNotBlank()) "Gateway: $savedTtsEndpoint" else stringResource(R.string.settings_status_tts_default),
                        isConfigured = savedTtsEndpoint.isNotBlank(),
                        onClick = { activeDialog = AuxServiceDialogType.TTS_GATEWAY }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // ========================================================
    // DIALOGS CHO TỪNG DỊCH VỤ
    // ========================================================

    // 1. OpenSubtitles Dialog
    if (activeDialog == AuxServiceDialogType.OPEN_SUBTITLES) {
        var inputKey by remember { mutableStateOf(savedOpenSubKey) }
        var isTesting by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        BaseServiceDialog(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.settings_opensubtitles_title),
            description = stringResource(R.string.settings_opensubtitles_desc),
            linkText = "Lấy OpenSubtitles API Key tại opensubtitles.com",
            linkUrl = "https://www.opensubtitles.com/en/consumers",
            isTesting = isTesting,
            testResult = testResult,
            onTest = {
                coroutineScope.launch {
                    isTesting = true
                    testResult = null
                    val res = subtitleManager.openSubtitlesProvider.testConnection(inputKey.trim())
                    testResult = res
                    isTesting = false
                }
            },
            onDismiss = { activeDialog = null },
            onSave = {
                val clean = inputKey.trim()
                settingsManager.setOpenSubtitlesApiKey(clean)
                Toast.makeText(context, context.getString(R.string.settings_opensubtitles_saved_toast), Toast.LENGTH_SHORT).show()
                activeDialog = null
            }
        ) {
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text(stringResource(R.string.settings_opensubtitles_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_opensubtitles_key_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputKey.isNotEmpty()) {
                        IconButton(onClick = { inputKey = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    }

    // 2. SubDL Dialog
    if (activeDialog == AuxServiceDialogType.SUBDL) {
        var inputKey by remember { mutableStateOf(savedSubdlKey) }
        var isTesting by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        BaseServiceDialog(
            icon = Icons.Default.Key,
            title = stringResource(R.string.settings_subdl_title),
            description = stringResource(R.string.settings_subdl_desc),
            linkText = "Lấy SubDL API Key tại subdl.com",
            linkUrl = "https://subdl.com/",
            isTesting = isTesting,
            testResult = testResult,
            onTest = {
                coroutineScope.launch {
                    isTesting = true
                    testResult = null
                    val res = subtitleManager.subdlProvider.testConnection(inputKey.trim())
                    testResult = res
                    isTesting = false
                }
            },
            onDismiss = { activeDialog = null },
            onSave = {
                val clean = inputKey.trim()
                settingsManager.setSubdlApiKey(clean)
                Toast.makeText(context, context.getString(R.string.settings_subdl_saved_toast), Toast.LENGTH_SHORT).show()
                activeDialog = null
            }
        ) {
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text(stringResource(R.string.settings_subdl_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_subdl_key_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputKey.isNotEmpty()) {
                        IconButton(onClick = { inputKey = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    }

    // 3. Subsource Dialog
    if (activeDialog == AuxServiceDialogType.SUBSOURCE) {
        var inputKey by remember { mutableStateOf(savedSubsourceKey) }
        var isTesting by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        BaseServiceDialog(
            icon = Icons.Default.Key,
            title = stringResource(R.string.settings_subsource_title),
            description = stringResource(R.string.settings_subsource_desc),
            linkText = stringResource(R.string.settings_subsource_get_key_link),
            linkUrl = "https://subsource.net/",
            isTesting = isTesting,
            testResult = testResult,
            onTest = {
                coroutineScope.launch {
                    isTesting = true
                    testResult = null
                    val res = subtitleManager.subsourceProvider.testConnection(inputKey.trim())
                    testResult = res
                    isTesting = false
                }
            },
            onDismiss = { activeDialog = null },
            onSave = {
                val clean = inputKey.trim()
                settingsManager.setSubsourceApiKey(clean)
                Toast.makeText(context, context.getString(R.string.settings_subsource_saved_toast), Toast.LENGTH_SHORT).show()
                activeDialog = null
            }
        ) {
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text(stringResource(R.string.settings_subsource_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_subsource_key_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputKey.isNotEmpty()) {
                        IconButton(onClick = { inputKey = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    }

    // 4. TMDB Dialog
    if (activeDialog == AuxServiceDialogType.TMDB) {
        var inputKey by remember { mutableStateOf(savedTmdbKey) }
        var isTesting by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        BaseServiceDialog(
            icon = Icons.Default.Movie,
            title = stringResource(R.string.settings_tmdb_section),
            description = stringResource(R.string.settings_tmdb_desc),
            linkText = "Lấy API Key TMDB tại themoviedb.org",
            linkUrl = "https://www.themoviedb.org/settings/api",
            isTesting = isTesting,
            testResult = testResult,
            onTest = {
                coroutineScope.launch {
                    isTesting = true
                    testResult = null
                    val res = tmdbRepo.testApiKey(inputKey.trim())
                    testResult = res
                    isTesting = false
                }
            },
            onDismiss = { activeDialog = null },
            onSave = {
                val clean = inputKey.trim()
                settingsManager.setTmdbApiKey(clean)
                Toast.makeText(context, context.getString(R.string.settings_tmdb_saved_toast), Toast.LENGTH_SHORT).show()
                activeDialog = null
            }
        ) {
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text(stringResource(R.string.settings_tmdb_label)) },
                placeholder = { Text(stringResource(R.string.settings_tmdb_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputKey.isNotEmpty()) {
                        IconButton(onClick = { inputKey = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )

            if (inputKey != com.jkhx.player.core.network.tmdb.TmdbConfig.DEFAULT_API_KEY) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        inputKey = com.jkhx.player.core.network.tmdb.TmdbConfig.DEFAULT_API_KEY
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_dialog_reset_default), fontSize = 12.sp)
                }
            }
        }
    }

    // 5. Google Gemini AI Dialog
    if (activeDialog == AuxServiceDialogType.GEMINI_AI) {
        var inputKey by remember { mutableStateOf(savedGeminiKey) }
        var inputModel by remember { mutableStateOf(savedGeminiModel) }
        var isTesting by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        BaseServiceDialog(
            icon = Icons.Default.AutoAwesome,
            title = stringResource(R.string.settings_gemini_title),
            description = stringResource(R.string.settings_gemini_desc),
            linkText = stringResource(R.string.settings_gemini_get_key_link),
            linkUrl = "https://aistudio.google.com/app/apikey",
            isTesting = isTesting,
            testResult = testResult,
            onTest = {
                coroutineScope.launch {
                    isTesting = true
                    testResult = null
                    val res = subtitleManager.geminiTranslator.testConnection(
                        apiKey = inputKey.trim(),
                        model = inputModel
                    )
                    testResult = res
                    isTesting = false
                }
            },
            onDismiss = { activeDialog = null },
            onSave = {
                val cleanKey = inputKey.trim()
                settingsManager.setGeminiApiKey(cleanKey)
                settingsManager.setGeminiModel(inputModel)
                Toast.makeText(context, context.getString(R.string.settings_gemini_saved_toast), Toast.LENGTH_SHORT).show()
                activeDialog = null
            }
        ) {
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text(stringResource(R.string.settings_gemini_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_gemini_key_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputKey.isNotEmpty()) {
                        IconButton(onClick = { inputKey = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.settings_gemini_model_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            val geminiModels = listOf(
                "gemini-3.5-flash-lite" to "gemini-3.5-flash-lite (Khuyên dùng)",
                "gemini-3.1-flash-lite" to "gemini-3.1-flash-lite",
                "gemini-2.0-flash" to "gemini-2.0-flash"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                geminiModels.forEach { (modelId, modelLabel) ->
                    val isSelected = inputModel == modelId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        onClick = {
                            inputModel = modelId
                            testResult = null
                        }
                    ) {
                        Text(
                            text = modelLabel,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }

    // 6. TTS Gateway Dialog
    if (activeDialog == AuxServiceDialogType.TTS_GATEWAY) {
        var inputEndpoint by remember { mutableStateOf(savedTtsEndpoint) }
        var inputKey by remember { mutableStateOf(savedTtsKey) }
        var inputFormat by remember { mutableStateOf(savedTtsAudioFormat) }
        var isTesting by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        BaseServiceDialog(
            icon = Icons.Default.RecordVoiceOver,
            title = stringResource(R.string.settings_tts_section_title),
            description = "Kết nối máy chủ OpenAI-compatible TTS Gateway (như travisvn/openai-edge-tts, OneAPI, Cloudflare AI Gateway) hoặc Gemini Studio qua endpoint /v1/audio/speech.",
            linkText = null,
            linkUrl = null,
            isTesting = isTesting,
            testResult = testResult,
            testButtonLabel = stringResource(R.string.settings_tts_test_btn),
            onTest = {
                coroutineScope.launch {
                    isTesting = true
                    testResult = null
                    val testEngine = com.jkhx.player.core.player.tts.engine.GeminiTtsEngine(
                        okHttpClient = JkhxApplication.instance.okHttpClient,
                        engineId = "gemini-3.8-flash-tts",
                        apiKeyProvider = { inputKey.trim() },
                        apiEndpointProvider = { inputEndpoint.trim() },
                        audioFormatProvider = { inputFormat }
                    )
                    val start = System.currentTimeMillis()
                    val audioBytes = testEngine.synthesize("Xin chào, đây là giọng đọc thử nghiệm.", "bao_nam", 1.0f)
                    val elapsed = System.currentTimeMillis() - start
                    if (audioBytes != null && audioBytes.isNotEmpty()) {
                        val fmtLabel = if (audioBytes.size >= 4 && audioBytes[0] == 0x1A.toByte() && audioBytes[1] == 0x45.toByte()) "Opus" else "MP3"
                        testResult = true to "Kết nối AI TTS thành công ($fmtLabel)! Nhận được ${audioBytes.size} bytes (${elapsed}ms)"
                    } else {
                        testResult = false to "Không nhận được phản hồi âm thanh từ model TTS (${elapsed}ms). Vui lòng kiểm tra lại Gateway / Key."
                    }
                    isTesting = false
                }
            },
            onDismiss = { activeDialog = null },
            onSave = {
                val cleanEndpoint = inputEndpoint.trim()
                val cleanKey = inputKey.trim()
                settingsManager.setTtsApiEndpoint(cleanEndpoint)
                settingsManager.setTtsApiKey(cleanKey)
                settingsManager.setTtsAudioFormat(inputFormat)
                Toast.makeText(context, context.getString(R.string.settings_tts_saved_toast), Toast.LENGTH_SHORT).show()
                activeDialog = null
            }
        ) {
            OutlinedTextField(
                value = inputEndpoint,
                onValueChange = { inputEndpoint = it },
                label = { Text(stringResource(R.string.settings_tts_endpoint_label)) },
                placeholder = { Text(stringResource(R.string.settings_tts_endpoint_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputEndpoint.isNotEmpty()) {
                        IconButton(onClick = { inputEndpoint = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text(stringResource(R.string.settings_tts_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_tts_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputKey.isNotEmpty()) {
                        IconButton(onClick = { inputKey = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Định dạng âm thanh stream:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "opus" to "Opus (Khuyên dùng - Siêu nhẹ)",
                    "mp3" to "MP3 (Tương thích)"
                ).forEach { (formatKey, label) ->
                    val isSelected = inputFormat == formatKey
                    Surface(
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        onClick = {
                            inputFormat = formatKey
                            testResult = null
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // 7. Subtitle Search Languages Dialog
    if (activeDialog == AuxServiceDialogType.SUBTITLE_LANGUAGES) {
        val currentLangs by settingsManager.subtitleSearchLanguages.collectAsState()
        var tempSelectedLangs by remember { mutableStateOf(currentLangs) }

        AlertDialog(
            shape = ShapeTokens.Card,
            onDismissRequest = { activeDialog = null },
            icon = {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.settings_subtitle_languages_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.settings_subtitle_languages_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    com.jkhx.player.core.player.subtitle.model.SubtitleSupportedLanguages.ALL.forEach { langOpt ->
                        val isChecked = tempSelectedLangs.contains(langOpt.code)
                        val isEn = langOpt.code == "en"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isEn) {
                                    tempSelectedLangs = if (isChecked) {
                                        tempSelectedLangs - langOpt.code
                                    } else {
                                        tempSelectedLangs + langOpt.code
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (!isEn) {
                                        tempSelectedLangs = if (checked) {
                                            tempSelectedLangs + langOpt.code
                                        } else {
                                            tempSelectedLangs - langOpt.code
                                        }
                                    }
                                },
                                enabled = !isEn
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = langOpt.nameVi,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${langOpt.nativeName})",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isEn) {
                                    Text(
                                        text = stringResource(R.string.settings_subtitle_languages_mandatory),
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = ShapeTokens.Button,
                    onClick = {
                        settingsManager.setSubtitleSearchLanguages(tempSelectedLangs)
                        Toast.makeText(context, context.getString(R.string.settings_subtitle_languages_saved), Toast.LENGTH_SHORT).show()
                        activeDialog = null
                    }
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(
                    shape = ShapeTokens.Button,
                    onClick = { activeDialog = null }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Dialogs cho Kiểu Fusion
    if (showBadgePositionDialog) {
        BadgePositionDialog(
            currentPlacement = streamBadgePlacement,
            onDismiss = { showBadgePositionDialog = false },
            onSelect = {
                settingsManager.setStreamBadgePlacement(it)
                showBadgePositionDialog = false
            }
        )
    }

    if (showBadgeImportDialog) {
        BadgeUrlManagerDialog(
            currentRules = streamBadgeRules,
            onDismiss = { showBadgeImportDialog = false },
            onActivate = { url ->
                val nextRules = streamBadgeRules.setActiveSource(url)
                settingsManager.setStreamBadgeRulesJson(StreamBadgeJsonHelper.encodeRules(nextRules))
                Toast.makeText(context, "Đã kích hoạt nguồn nhãn Fusion", Toast.LENGTH_SHORT).show()
            },
            onDelete = { url ->
                val nextRules = streamBadgeRules.removeSource(url)
                settingsManager.setStreamBadgeRulesJson(StreamBadgeJsonHelper.encodeRules(nextRules))
                Toast.makeText(context, "Đã xóa URL nhãn Fusion", Toast.LENGTH_SHORT).show()
            },
            onImportUrl = { url ->
                withContext(Dispatchers.IO) {
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = JkhxApplication.instance.okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful) {
                            return@withContext Pair(false, "Lỗi HTTP: ${response.code}")
                        }
                        val body = response.body?.string() ?: return@withContext Pair(false, "Phản hồi rỗng")
                        val imported = StreamBadgeJsonHelper.parsePayload(url, body)
                        val nextRules = streamBadgeRules.upsert(imported, activate = true)
                        settingsManager.setStreamBadgeRulesJson(StreamBadgeJsonHelper.encodeRules(nextRules))
                        Pair(true, "")
                    } catch (e: Exception) {
                        Pair(false, e.message ?: "Lỗi tải URL")
                    }
                }
            },
            onPreview = { importItem ->
                previewImportItem = importItem
            }
        )
    }

    previewImportItem?.let { importItem ->
        BadgePreviewDialog(
            importItem = importItem,
            onDismiss = { previewImportItem = null }
        )
    }
}

@Composable
private fun FusionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun FusionNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun BadgePositionDialog(
    currentPlacement: StreamBadgePlacement,
    onDismiss: () -> Unit,
    onSelect: (StreamBadgePlacement) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.settings_stream_badge_position_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(StreamBadgePlacement.BOTTOM) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentPlacement == StreamBadgePlacement.BOTTOM,
                        onClick = { onSelect(StreamBadgePlacement.BOTTOM) }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_stream_badge_position_bottom),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(StreamBadgePlacement.TOP) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentPlacement == StreamBadgePlacement.TOP,
                        onClick = { onSelect(StreamBadgePlacement.TOP) }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_stream_badge_position_top),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeUrlManagerDialog(
    currentRules: StreamBadgeRules,
    onDismiss: () -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImportUrl: suspend (String) -> Pair<Boolean, String>,
    onPreview: (StreamBadgeImport) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var draftUrl by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val imports = currentRules.imports

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_stream_badge_urls_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_stream_badge_urls_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = {
                        draftUrl = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_fusion_badge_url_label)) },
                    placeholder = { Text("https://raw.githubusercontent.com/.../badges.json") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_fusion_badge_urls_imported, imports.size),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val cleanUrl = draftUrl.trim()
                            if (cleanUrl.isBlank()) {
                                errorMessage = context.getString(R.string.settings_fusion_badge_enter_url)
                                return@Button
                            }
                            if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                                errorMessage = context.getString(R.string.settings_fusion_badge_url_invalid)
                                return@Button
                            }
                            if (imports.size >= STREAM_BADGE_IMPORT_LIMIT && !imports.any { it.sourceUrl.equals(cleanUrl, true) }) {
                                errorMessage = context.getString(R.string.settings_fusion_badge_limit_reached)
                                return@Button
                            }
                            coroutineScope.launch {
                                isImporting = true
                                errorMessage = null
                                val (success, msg) = onImportUrl(cleanUrl)
                                isImporting = false
                                if (success) {
                                    draftUrl = ""
                                    Toast.makeText(context, "Nhập nhãn Fusion thành công!", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = msg
                                }
                            }
                        },
                        enabled = !isImporting && draftUrl.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(stringResource(R.string.settings_fusion_badge_action_import))
                    }
                }

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = msg, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (imports.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có URL nhãn Fusion nào được thêm.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = imports, key = { it.sourceUrl }) { importItem ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (importItem.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onActivate(importItem.sourceUrl) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    RadioButton(
                                        selected = importItem.isActive,
                                        onClick = { onActivate(importItem.sourceUrl) },
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = importItem.sourceUrl,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (importItem.isActive)
                                                stringResource(
                                                    R.string.settings_fusion_badge_active_status,
                                                    importItem.enabledFilterCount,
                                                    importItem.groups.size
                                                )
                                            else
                                                stringResource(
                                                    R.string.settings_fusion_badge_inactive_status,
                                                    importItem.enabledFilterCount,
                                                    importItem.groups.size
                                                ),
                                            fontSize = 11.sp,
                                            color = if (importItem.isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { onPreview(importItem) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    stringResource(R.string.settings_fusion_badge_preview_action),
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { onDelete(importItem.sourceUrl) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Xóa",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgePreviewDialog(
    importItem: StreamBadgeImport,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_fusion_badge_preview_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_fusion_badge_preview_count, importItem.filters.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                val groupsMap = remember(importItem) {
                    val grouped = mutableMapOf<String, MutableList<StreamBadgeFilter>>()
                    importItem.filters.forEach { filter ->
                        val gId = filter.groupId.ifBlank { "other" }
                        grouped.getOrPut(gId) { mutableListOf() }.add(filter)
                    }
                    grouped
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    groupsMap.forEach { (groupId, filters) ->
                        val groupObj = importItem.groups.firstOrNull { it.id == groupId }
                        val groupTitle = groupObj?.name?.ifBlank { groupId } ?: if (groupId == "other") "Khác" else groupId
                        item(key = groupId) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    val groupColorArgb = groupObj?.color?.toBadgeColorArgbOrNull()
                                    if (groupColorArgb != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(Color(groupColorArgb), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "$groupTitle (${filters.size})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filters.forEach { f ->
                                        val badge = StreamBadge(
                                            name = f.name,
                                            imageURL = f.imageURL,
                                            tagColor = f.tagColor,
                                            tagStyle = f.tagStyle,
                                            textColor = f.textColor,
                                            borderColor = f.borderColor
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            StreamBadgeChip(badge = badge, height = 20.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = f.name,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isConfigured) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun BaseServiceDialog(
    icon: ImageVector,
    title: String,
    description: String,
    linkText: String? = null,
    linkUrl: String? = null,
    isTesting: Boolean = false,
    testResult: Pair<Boolean, String>? = null,
    testButtonLabel: String = "Kiểm tra kết nối",
    onTest: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header (Icon + Title)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Description
                Text(
                    text = description,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Input Form
                content()

                // Link to get API Key (if provided)
                if (!linkText.isNullOrBlank() && !linkUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = linkText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Test Connection Button
                if (onTest != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isTesting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.settings_dialog_testing),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                testButtonLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Test Result Banner
                testResult?.let { (success, msg) ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (success) Color(0xFF1B5E20).copy(alpha = 0.15f) else Color(0xFFB71C1C).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (success) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFF44336).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = msg,
                            fontSize = 11.5.sp,
                            color = if (success) Color(0xFF81C784) else Color(0xFFE57373),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Buttons (Hủy & Lưu)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = stringResource(R.string.common_save),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
