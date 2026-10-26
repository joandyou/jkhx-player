package com.jkhx.player.settings

import android.content.Context
import android.content.SharedPreferences
import com.jkhx.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

enum class ProxyType(val key: String, val title: String) {
    SOCKS("socks", "SOCKS5"),
    HTTP("http", "HTTP");

    companion object {
        fun fromKey(key: String?): ProxyType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SOCKS
        }
    }
}

enum class AppThemeMode(val key: String, val titleRes: Int) {
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark),
    NIGHT_BLUE("night_blue", R.string.theme_neon),
    CINEMA_WARM("cinema_warm", R.string.theme_cinema);

    val title: String get() = when (this) {
        LIGHT -> "Sáng"
        DARK -> "Tối"
        NIGHT_BLUE -> "Neon"
        CINEMA_WARM -> "Cinema"
    }

    companion object {
        fun fromKey(key: String?): AppThemeMode {
            return entries.firstOrNull { it.key == key } ?: DARK
        }
    }
}

enum class DeviceDisplayMode(val key: String, val titleRes: Int) {
    MOBILE("mobile", R.string.display_mobile),
    TABLET("tablet", R.string.display_tablet);

    val title: String get() = when (this) {
        MOBILE -> "Điện thoại"
        TABLET -> "Máy tính bảng"
    }

    companion object {
        fun fromKey(key: String?): DeviceDisplayMode {
            return entries.firstOrNull { it.key == key } ?: MOBILE
        }
    }
}

enum class AppLanguage(val code: String, val titleRes: Int) {
    VIETNAMESE("vi", R.string.language_vietnamese),
    ENGLISH("en", R.string.language_english);

    val title: String get() = when (this) {
        VIETNAMESE -> "Tiếng Việt"
        ENGLISH -> "English"
    }

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: VIETNAMESE
        }
    }
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jkhx_settings_pref", Context.MODE_PRIVATE)

    private val _isAdultEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_IS_ADULT_ENABLED, false)
    )
    val isAdultEnabled: StateFlow<Boolean> = _isAdultEnabled.asStateFlow()

    fun setAdultEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ADULT_ENABLED, enabled).apply()
        _isAdultEnabled.value = enabled
    }

    private val _isComicEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_IS_COMIC_ENABLED, true)
    )
    val isComicEnabled: StateFlow<Boolean> = _isComicEnabled.asStateFlow()

    fun setComicEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_COMIC_ENABLED, enabled).apply()
        _isComicEnabled.value = enabled
    }

    private val _isNovelEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_IS_NOVEL_ENABLED, true)
    )
    val isNovelEnabled: StateFlow<Boolean> = _isNovelEnabled.asStateFlow()

    fun setNovelEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_NOVEL_ENABLED, enabled).apply()
        _isNovelEnabled.value = enabled
    }

    private val _isLiveEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_IS_LIVE_ENABLED, true)
    )
    val isLiveEnabled: StateFlow<Boolean> = _isLiveEnabled.asStateFlow()

    fun setLiveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LIVE_ENABLED, enabled).apply()
        _isLiveEnabled.value = enabled
    }

    private val _themeMode = MutableStateFlow(
        AppThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.key))
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
        _themeMode.value = mode
    }

    private val _displayMode = MutableStateFlow(
        DeviceDisplayMode.fromKey(prefs.getString(KEY_DISPLAY_MODE, DeviceDisplayMode.MOBILE.key))
    )
    val displayMode: StateFlow<DeviceDisplayMode> = _displayMode.asStateFlow()

    fun setDisplayMode(mode: DeviceDisplayMode) {
        prefs.edit().putString(KEY_DISPLAY_MODE, mode.key).apply()
        _displayMode.value = mode
    }

    private val _language = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, AppLanguage.VIETNAMESE.code))
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _language.value = language
    }

    private val _lastSelectedProvider = MutableStateFlow(
        prefs.getString(KEY_LAST_SELECTED_PROVIDER, null)
    )
    val lastSelectedProvider: StateFlow<String?> = _lastSelectedProvider.asStateFlow()

    fun setLastSelectedProvider(providerId: String?) {
        prefs.edit().putString(KEY_LAST_SELECTED_PROVIDER, providerId).apply()
        _lastSelectedProvider.value = providerId
    }

    private val _lastSelectedStremioAddonUrl = MutableStateFlow(
        prefs.getString(KEY_LAST_SELECTED_STREMIO_ADDON_URL, null)
    )
    val lastSelectedStremioAddonUrl: StateFlow<String?> = _lastSelectedStremioAddonUrl.asStateFlow()

    fun setLastSelectedStremioAddonUrl(url: String?) {
        prefs.edit().putString(KEY_LAST_SELECTED_STREMIO_ADDON_URL, url).apply()
        _lastSelectedStremioAddonUrl.value = url
    }

    private val _lastSelectedStremioType = MutableStateFlow(
        prefs.getString(KEY_LAST_SELECTED_STREMIO_TYPE, null)
    )
    val lastSelectedStremioType: StateFlow<String?> = _lastSelectedStremioType.asStateFlow()

    fun setLastSelectedStremioType(type: String?) {
        prefs.edit().putString(KEY_LAST_SELECTED_STREMIO_TYPE, type).apply()
        _lastSelectedStremioType.value = type
    }

    private val _lastSelectedStremioCatalogId = MutableStateFlow(
        prefs.getString(KEY_LAST_SELECTED_STREMIO_CATALOG_ID, null)
    )
    val lastSelectedStremioCatalogId: StateFlow<String?> = _lastSelectedStremioCatalogId.asStateFlow()

    fun setLastSelectedStremioCatalogId(catalogId: String?) {
        prefs.edit().putString(KEY_LAST_SELECTED_STREMIO_CATALOG_ID, catalogId).apply()
        _lastSelectedStremioCatalogId.value = catalogId
    }

    private val _lastSelectedStremioFilter = MutableStateFlow(
        prefs.getString(KEY_LAST_SELECTED_STREMIO_FILTER, null)
    )
    val lastSelectedStremioFilter: StateFlow<String?> = _lastSelectedStremioFilter.asStateFlow()

    fun setLastSelectedStremioFilter(filter: String?) {
        prefs.edit().putString(KEY_LAST_SELECTED_STREMIO_FILTER, filter).apply()
        _lastSelectedStremioFilter.value = filter
    }

    fun clearStremioDiscoverSelection() {
        prefs.edit()
            .remove(KEY_LAST_SELECTED_STREMIO_ADDON_URL)
            .remove(KEY_LAST_SELECTED_STREMIO_TYPE)
            .remove(KEY_LAST_SELECTED_STREMIO_CATALOG_ID)
            .remove(KEY_LAST_SELECTED_STREMIO_FILTER)
            .apply()
        _lastSelectedStremioAddonUrl.value = null
        _lastSelectedStremioType.value = null
        _lastSelectedStremioCatalogId.value = null
        _lastSelectedStremioFilter.value = null
    }

    // --- Player Orientation Persistence ---
    private val _isPlayerLandscape = MutableStateFlow(
        prefs.getBoolean(KEY_PLAYER_IS_LANDSCAPE, true)
    )
    val isPlayerLandscape: StateFlow<Boolean> = _isPlayerLandscape.asStateFlow()

    fun setPlayerLandscape(isLandscape: Boolean) {
        prefs.edit().putBoolean(KEY_PLAYER_IS_LANDSCAPE, isLandscape).apply()
        _isPlayerLandscape.value = isLandscape
    }

    // --- Episode / Chapter Sort Order Persistence (Mặc định: true - Mới nhất trước) ---
    private val _isMovieEpisodeSortDescending = MutableStateFlow(
        prefs.getBoolean(KEY_MOVIE_EPISODE_SORT_DESCENDING, true)
    )
    val isMovieEpisodeSortDescending: StateFlow<Boolean> = _isMovieEpisodeSortDescending.asStateFlow()

    fun setMovieEpisodeSortDescending(descending: Boolean) {
        prefs.edit().putBoolean(KEY_MOVIE_EPISODE_SORT_DESCENDING, descending).apply()
        _isMovieEpisodeSortDescending.value = descending
    }

    private val _isComicEpisodeSortDescending = MutableStateFlow(
        prefs.getBoolean(KEY_COMIC_EPISODE_SORT_DESCENDING, true)
    )
    val isComicEpisodeSortDescending: StateFlow<Boolean> = _isComicEpisodeSortDescending.asStateFlow()

    fun setComicEpisodeSortDescending(descending: Boolean) {
        prefs.edit().putBoolean(KEY_COMIC_EPISODE_SORT_DESCENDING, descending).apply()
        _isComicEpisodeSortDescending.value = descending
    }

    // --- Subtitle Appearance Settings ---
    private val _subtitleTextSize = MutableStateFlow(
        prefs.getFloat(KEY_SUBTITLE_TEXT_SIZE, 20f).let {
            if (it < 16f) 20f else it
        }
    )
    val subtitleTextSize: StateFlow<Float> = _subtitleTextSize.asStateFlow()

    fun setSubtitleTextSize(size: Float) {
        prefs.edit().putFloat(KEY_SUBTITLE_TEXT_SIZE, size).apply()
        _subtitleTextSize.value = size
    }

    private val _subtitleStyleKey = MutableStateFlow(
        prefs.getString(KEY_SUBTITLE_STYLE_KEY, "white_outline") ?: "white_outline"
    )
    val subtitleStyleKey: StateFlow<String> = _subtitleStyleKey.asStateFlow()

    fun setSubtitleStyleKey(key: String) {
        prefs.edit().putString(KEY_SUBTITLE_STYLE_KEY, key).apply()
        _subtitleStyleKey.value = key
    }

    private val _subtitlePositionKey = MutableStateFlow(
        prefs.getString(KEY_SUBTITLE_POSITION_KEY, "bottom") ?: "bottom"
    )
    val subtitlePositionKey: StateFlow<String> = _subtitlePositionKey.asStateFlow()

    fun setSubtitlePositionKey(key: String) {
        prefs.edit().putString(KEY_SUBTITLE_POSITION_KEY, key).apply()
        _subtitlePositionKey.value = key
    }

    // --- Nuvio Subtitle appearance preferences ---
    private val _subtitleIsBold = MutableStateFlow(prefs.getBoolean(KEY_SUBTITLE_IS_BOLD, true))
    val subtitleIsBold: StateFlow<Boolean> = _subtitleIsBold.asStateFlow()

    fun setSubtitleIsBold(bold: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLE_IS_BOLD, bold).apply()
        _subtitleIsBold.value = bold
    }

    private val _subtitleTextColor = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_TEXT_COLOR, android.graphics.Color.WHITE))
    val subtitleTextColor: StateFlow<Int> = _subtitleTextColor.asStateFlow()

    fun setSubtitleTextColor(color: Int) {
        prefs.edit().putInt(KEY_SUBTITLE_TEXT_COLOR, color).apply()
        _subtitleTextColor.value = color
    }

    private val _subtitleTextOpacity = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_TEXT_OPACITY, 100))
    val subtitleTextOpacity: StateFlow<Int> = _subtitleTextOpacity.asStateFlow()

    fun setSubtitleTextOpacity(opacity: Int) {
        val clamped = opacity.coerceIn(10, 100)
        prefs.edit().putInt(KEY_SUBTITLE_TEXT_OPACITY, clamped).apply()
        _subtitleTextOpacity.value = clamped
    }

    private val _subtitleIsOutline = MutableStateFlow(prefs.getBoolean(KEY_SUBTITLE_IS_OUTLINE, true))
    val subtitleIsOutline: StateFlow<Boolean> = _subtitleIsOutline.asStateFlow()

    fun setSubtitleIsOutline(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLE_IS_OUTLINE, enabled).apply()
        _subtitleIsOutline.value = enabled
    }

    private val _subtitleOutlineColor = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_OUTLINE_COLOR, android.graphics.Color.BLACK))
    val subtitleOutlineColor: StateFlow<Int> = _subtitleOutlineColor.asStateFlow()

    fun setSubtitleOutlineColor(color: Int) {
        prefs.edit().putInt(KEY_SUBTITLE_OUTLINE_COLOR, color).apply()
        _subtitleOutlineColor.value = color
    }

    private val _subtitleBottomMargin = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_BOTTOM_MARGIN, 20))
    val subtitleBottomMargin: StateFlow<Int> = _subtitleBottomMargin.asStateFlow()

    fun setSubtitleBottomMargin(margin: Int) {
        val clamped = margin.coerceIn(0, 100)
        prefs.edit().putInt(KEY_SUBTITLE_BOTTOM_MARGIN, clamped).apply()
        _subtitleBottomMargin.value = clamped
    }

    fun resetSubtitleStyleDefaults() {
        setSubtitleTextSize(20f)
        setSubtitleIsBold(true)
        setSubtitleTextColor(android.graphics.Color.WHITE)
        setSubtitleTextOpacity(100)
        setSubtitleIsOutline(true)
        setSubtitleOutlineColor(android.graphics.Color.BLACK)
        setSubtitleBottomMargin(20)
    }

    // --- TMDB API Key Setting ---
    private val _tmdbApiKey = MutableStateFlow(
        prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
    )
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    init {
        com.jkhx.player.core.network.tmdb.TmdbConfig.apiKey = _tmdbApiKey.value
    }

    fun setTmdbApiKey(key: String) {
        val cleanKey = key.trim()
        prefs.edit().putString(KEY_TMDB_API_KEY, cleanKey).apply()
        _tmdbApiKey.value = cleanKey
        com.jkhx.player.core.network.tmdb.TmdbConfig.apiKey = cleanKey
    }

    // --- Auxiliary Subtitle & AI Services (OpenSubtitles, SubDL, Gemini AI) ---
    private val _openSubtitlesApiKey = MutableStateFlow(
        prefs.getString(KEY_OPENSUBTITLES_API_KEY, "") ?: ""
    )
    val openSubtitlesApiKey: StateFlow<String> = _openSubtitlesApiKey.asStateFlow()

    fun setOpenSubtitlesApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_OPENSUBTITLES_API_KEY, clean).apply()
        _openSubtitlesApiKey.value = clean
    }

    private val _subdlApiKey = MutableStateFlow(
        prefs.getString(KEY_SUBDL_API_KEY, "") ?: ""
    )
    val subdlApiKey: StateFlow<String> = _subdlApiKey.asStateFlow()

    fun setSubdlApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_SUBDL_API_KEY, clean).apply()
        _subdlApiKey.value = clean
    }

    private val _subsourceApiKey = MutableStateFlow(
        prefs.getString(KEY_SUBSOURCE_API_KEY, "") ?: ""
    )
    val subsourceApiKey: StateFlow<String> = _subsourceApiKey.asStateFlow()

    fun setSubsourceApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_SUBSOURCE_API_KEY, clean).apply()
        _subsourceApiKey.value = clean
    }

    private val _geminiApiKey = MutableStateFlow(
        prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    )
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun setGeminiApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_GEMINI_API_KEY, clean).apply()
        _geminiApiKey.value = clean
    }

    private val _geminiModel = MutableStateFlow(
        run {
            val saved = prefs.getString(KEY_GEMINI_MODEL, "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite"
            if (saved.contains("1.5") || saved.contains("2.0") || saved == "gemini-2.5-flash") {
                "gemini-3.5-flash-lite"
            } else {
                saved
            }
        }
    )
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

    fun setGeminiModel(model: String) {
        val clean = model.trim().ifBlank { "gemini-3.5-flash-lite" }
        prefs.edit().putString(KEY_GEMINI_MODEL, clean).apply()
        _geminiModel.value = clean
    }

    private val _autoSearchOnlineSubtitles = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_SEARCH_ONLINE_SUBTITLES, true)
    )
    val autoSearchOnlineSubtitles: StateFlow<Boolean> = _autoSearchOnlineSubtitles.asStateFlow()

    fun setAutoSearchOnlineSubtitles(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SEARCH_ONLINE_SUBTITLES, enabled).apply()
        _autoSearchOnlineSubtitles.value = enabled
    }

    // --- Subtitle Search Languages ---
    private val _subtitleSearchLanguages = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_SUBTITLE_SEARCH_LANGUAGES, setOf("vi", "en"))?.let {
            if (it.isEmpty()) setOf("vi", "en") else (it + "en")
        } ?: setOf("vi", "en")
    )
    val subtitleSearchLanguages: StateFlow<Set<String>> = _subtitleSearchLanguages.asStateFlow()

    fun setSubtitleSearchLanguages(languages: Set<String>) {
        // Luôn đảm bảo Tiếng Anh ("en") có trong danh sách để hỗ trợ Gemini AI dịch
        val finalSet = languages + "en"
        prefs.edit().putStringSet(KEY_SUBTITLE_SEARCH_LANGUAGES, finalSet).apply()
        _subtitleSearchLanguages.value = finalSet
    }

    // --- Custom Proxy Settings (Warp / SOCKS5 / HTTP) ---
    private val _isCustomProxyEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_CUSTOM_PROXY_ENABLED, false)
    )
    val isCustomProxyEnabled: StateFlow<Boolean> = _isCustomProxyEnabled.asStateFlow()

    fun setCustomProxyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CUSTOM_PROXY_ENABLED, enabled).apply()
        _isCustomProxyEnabled.value = enabled
    }

    private val _customProxyType = MutableStateFlow(
        ProxyType.fromKey(prefs.getString(KEY_CUSTOM_PROXY_TYPE, ProxyType.SOCKS.key))
    )
    val customProxyType: StateFlow<ProxyType> = _customProxyType.asStateFlow()

    fun setCustomProxyType(type: ProxyType) {
        prefs.edit().putString(KEY_CUSTOM_PROXY_TYPE, type.key).apply()
        _customProxyType.value = type
    }

    private val _customProxyHost = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_PROXY_HOST, "") ?: ""
    )
    val customProxyHost: StateFlow<String> = _customProxyHost.asStateFlow()

    fun setCustomProxyHost(host: String) {
        val clean = host.trim()
        prefs.edit().putString(KEY_CUSTOM_PROXY_HOST, clean).apply()
        _customProxyHost.value = clean
    }

    private val _customProxyPort = MutableStateFlow(
        prefs.getInt(KEY_CUSTOM_PROXY_PORT, 1080)
    )
    val customProxyPort: StateFlow<Int> = _customProxyPort.asStateFlow()

    fun setCustomProxyPort(port: Int) {
        prefs.edit().putInt(KEY_CUSTOM_PROXY_PORT, port).apply()
        _customProxyPort.value = port
    }

    fun getProxy(): Proxy? {
        if (!_isCustomProxyEnabled.value) return null
        val host = _customProxyHost.value.trim()
        val port = _customProxyPort.value
        if (host.isBlank() || port <= 0 || port > 65535) return null
        return try {
            val type = if (_customProxyType.value == ProxyType.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
            Proxy(type, InetSocketAddress.createUnresolved(host, port))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun testProxyConnection(host: String, port: Int, type: ProxyType): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) return@withContext false to "Host/IP không được để trống"
        if (port <= 0 || port > 65535) return@withContext false to "Port không hợp lệ (1-65535)"

        val proxyType = if (type == ProxyType.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
        val proxy = Proxy(proxyType, InetSocketAddress.createUnresolved(cleanHost, port))

        val testClient = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url("https://cloudflare.com/cdn-cgi/trace")
                .header("User-Agent", "curl/8.5.0")
                .build()
            testClient.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val ip = body.lines().firstOrNull { it.startsWith("ip=") }?.substringAfter("ip=") ?: "OK"
                    val warp = body.lines().firstOrNull { it.startsWith("warp=") }?.substringAfter("warp=")
                    val warpInfo = if (warp == "on") " [WARP ON]" else ""
                    true to "Kết nối thành công (${duration}ms) - IP: $ip$warpInfo"
                } else {
                    false to "Proxy phản hồi HTTP ${resp.code} (${duration}ms)"
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            false to "Lỗi kết nối proxy (${duration}ms): ${e.message ?: "Timeout/Error"}"
        }
    }

    // --- AI TTS / Voiceover Settings ---
    private val _isTtsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_TTS_ENABLED, false)
    )
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
        _isTtsEnabled.value = enabled
    }

    private val _ttsEngine = MutableStateFlow(
        prefs.getString(KEY_TTS_ENGINE, "gemini-3.8-flash-tts") ?: "gemini-3.8-flash-tts"
    )
    val ttsEngine: StateFlow<String> = _ttsEngine.asStateFlow()

    fun setTtsEngine(engineId: String) {
        val clean = engineId.trim().ifBlank { "gemini-3.8-flash-tts" }
        prefs.edit().putString(KEY_TTS_ENGINE, clean).apply()
        _ttsEngine.value = clean
    }

    private val _ttsVoice = MutableStateFlow(
        run {
            val raw = prefs.getString(KEY_TTS_VOICE, "bao_nam") ?: "bao_nam"
            com.jkhx.player.core.player.tts.model.TtsVoices.normalizeVoiceId(raw)
        }
    )
    val ttsVoice: StateFlow<String> = _ttsVoice.asStateFlow()

    fun setTtsVoice(voice: String) {
        val clean = com.jkhx.player.core.player.tts.model.TtsVoices.normalizeVoiceId(voice)
        prefs.edit().putString(KEY_TTS_VOICE, clean).apply()
        _ttsVoice.value = clean
    }

    private val _ttsSpeechRate = MutableStateFlow(
        prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.15f)
    )
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()

    fun setTtsSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, clamped).apply()
        _ttsSpeechRate.value = clamped
    }

    private val _ttsDuckingVolume = MutableStateFlow(
        prefs.getFloat(KEY_TTS_DUCKING_VOLUME, 0.35f)
    )
    val ttsDuckingVolume: StateFlow<Float> = _ttsDuckingVolume.asStateFlow()

    fun setTtsDuckingVolume(vol: Float) {
        val clamped = vol.coerceIn(0.1f, 0.8f)
        prefs.edit().putFloat(KEY_TTS_DUCKING_VOLUME, clamped).apply()
        _ttsDuckingVolume.value = clamped
    }

    private val _ttsApiEndpoint = MutableStateFlow(
        prefs.getString(KEY_TTS_API_ENDPOINT, "") ?: ""
    )
    val ttsApiEndpoint: StateFlow<String> = _ttsApiEndpoint.asStateFlow()

    fun setTtsApiEndpoint(endpoint: String) {
        val clean = endpoint.trim()
        prefs.edit().putString(KEY_TTS_API_ENDPOINT, clean).apply()
        _ttsApiEndpoint.value = clean
    }

    private val _ttsAudioFormat = MutableStateFlow(
        prefs.getString(KEY_TTS_AUDIO_FORMAT, "opus") ?: "opus"
    )
    val ttsAudioFormat: StateFlow<String> = _ttsAudioFormat.asStateFlow()

    fun setTtsAudioFormat(format: String) {
        val clean = format.trim().lowercase().ifBlank { "opus" }
        prefs.edit().putString(KEY_TTS_AUDIO_FORMAT, clean).apply()
        _ttsAudioFormat.value = clean
    }

    private val _ttsApiKey = MutableStateFlow(
        prefs.getString(KEY_TTS_API_KEY, "") ?: ""
    )
    val ttsApiKey: StateFlow<String> = _ttsApiKey.asStateFlow()

    fun setTtsApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_TTS_API_KEY, clean).apply()
        _ttsApiKey.value = clean
    }

    fun getEffectiveTtsApiKey(): String {
        return _ttsApiKey.value.trim()
    }

    companion object {
        private const val KEY_IS_ADULT_ENABLED = "pref_is_adult_enabled"
        private const val KEY_IS_COMIC_ENABLED = "pref_is_comic_enabled"
        private const val KEY_IS_NOVEL_ENABLED = "pref_is_novel_enabled"
        private const val KEY_IS_LIVE_ENABLED = "pref_is_live_enabled"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_DISPLAY_MODE = "pref_display_mode"
        private const val KEY_LANGUAGE = "pref_app_language"
        private const val KEY_LAST_SELECTED_PROVIDER = "pref_last_selected_provider"
        private const val KEY_LAST_SELECTED_STREMIO_ADDON_URL = "pref_last_selected_stremio_addon_url"
        private const val KEY_LAST_SELECTED_STREMIO_TYPE = "pref_last_selected_stremio_type"
        private const val KEY_LAST_SELECTED_STREMIO_CATALOG_ID = "pref_last_selected_stremio_catalog_id"
        private const val KEY_LAST_SELECTED_STREMIO_FILTER = "pref_last_selected_stremio_filter"
        private const val KEY_PLAYER_IS_LANDSCAPE = "pref_player_is_landscape"
        private const val KEY_MOVIE_EPISODE_SORT_DESCENDING = "pref_movie_episode_sort_descending"
        private const val KEY_COMIC_EPISODE_SORT_DESCENDING = "pref_comic_episode_sort_descending"
        private const val KEY_NOVEL_EPISODE_SORT_DESCENDING = "pref_novel_episode_sort_descending"
        private const val KEY_SUBTITLE_TEXT_SIZE = "pref_subtitle_text_size"
        private const val KEY_SUBTITLE_STYLE_KEY = "pref_subtitle_style_key"
        private const val KEY_SUBTITLE_POSITION_KEY = "pref_subtitle_position_key"
        private const val KEY_SUBTITLE_IS_BOLD = "pref_subtitle_is_bold"
        private const val KEY_SUBTITLE_TEXT_COLOR = "pref_subtitle_text_color"
        private const val KEY_SUBTITLE_TEXT_OPACITY = "pref_subtitle_text_opacity"
        private const val KEY_SUBTITLE_IS_OUTLINE = "pref_subtitle_is_outline"
        private const val KEY_SUBTITLE_OUTLINE_COLOR = "pref_subtitle_outline_color"
        private const val KEY_SUBTITLE_BOTTOM_MARGIN = "pref_subtitle_bottom_margin"
        private const val KEY_TMDB_API_KEY = "pref_tmdb_api_key"
        private const val KEY_OPENSUBTITLES_API_KEY = "pref_opensubtitles_api_key"
        private const val KEY_SUBDL_API_KEY = "pref_subdl_api_key"
        private const val KEY_SUBSOURCE_API_KEY = "pref_subsource_api_key"
        private const val KEY_GEMINI_API_KEY = "pref_gemini_api_key"
        private const val KEY_GEMINI_MODEL = "pref_gemini_model"
        private const val KEY_AUTO_SEARCH_ONLINE_SUBTITLES = "pref_auto_search_online_subtitles"
        private const val KEY_SUBTITLE_SEARCH_LANGUAGES = "pref_subtitle_search_languages"
        private const val KEY_CUSTOM_PROXY_ENABLED = "pref_custom_proxy_enabled"
        private const val KEY_CUSTOM_PROXY_TYPE = "pref_custom_proxy_type"
        private const val KEY_CUSTOM_PROXY_HOST = "pref_custom_proxy_host"
        private const val KEY_CUSTOM_PROXY_PORT = "pref_custom_proxy_port"
        private const val KEY_GOOGLE_ACCOUNT_EMAIL = "pref_google_account_email"
        private const val KEY_GOOGLE_ACCOUNT_NAME = "pref_google_account_name"
        private const val KEY_GOOGLE_ACCOUNT_PHOTO = "pref_google_account_photo"
        private const val KEY_AUTO_SYNC_ENABLED = "pref_auto_sync_enabled"
        private const val KEY_LAST_SYNC_TIMESTAMP = "pref_last_sync_timestamp"
        private const val KEY_TTS_ENABLED = "pref_tts_enabled"
        private const val KEY_TTS_ENGINE = "pref_tts_engine"
        private const val KEY_TTS_VOICE = "pref_tts_voice"
        private const val KEY_TTS_SPEECH_RATE = "pref_tts_speech_rate"
        private const val KEY_TTS_DUCKING_VOLUME = "pref_tts_ducking_volume"
        private const val KEY_TTS_API_ENDPOINT = "pref_tts_api_endpoint"
        private const val KEY_TTS_API_KEY = "pref_tts_api_key"
        private const val KEY_TTS_AUDIO_FORMAT = "pref_tts_audio_format"
        private const val KEY_STREAM_BADGE_RULES = "pref_stream_badge_rules"
        private const val KEY_STREAM_BADGE_PLACEMENT = "pref_stream_badge_placement"
        private const val KEY_SHOW_STREAM_SIZE_BADGES = "pref_show_stream_size_badges"
        private const val KEY_SHOW_ADDON_ICON = "pref_show_addon_icon"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _googleAccountEmail = MutableStateFlow(
        prefs.getString(KEY_GOOGLE_ACCOUNT_EMAIL, null)
    )
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _googleAccountName = MutableStateFlow(
        prefs.getString(KEY_GOOGLE_ACCOUNT_NAME, null)
    )
    val googleAccountName: StateFlow<String?> = _googleAccountName.asStateFlow()

    private val _googleAccountPhoto = MutableStateFlow(
        prefs.getString(KEY_GOOGLE_ACCOUNT_PHOTO, null)
    )
    val googleAccountPhoto: StateFlow<String?> = _googleAccountPhoto.asStateFlow()

    private val _isAutoSyncEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
    )
    val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(
        prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    )
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    fun setGoogleAccount(email: String?, name: String?, photoUrl: String?) {
        prefs.edit()
            .putString(KEY_GOOGLE_ACCOUNT_EMAIL, email)
            .putString(KEY_GOOGLE_ACCOUNT_NAME, name)
            .putString(KEY_GOOGLE_ACCOUNT_PHOTO, photoUrl)
            .apply()
        _googleAccountEmail.value = email
        _googleAccountName.value = name
        _googleAccountPhoto.value = photoUrl
    }

    fun clearGoogleAccount() {
        setGoogleAccount(null, null, null)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
        _isAutoSyncEnabled.value = enabled
    }

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()
        _lastSyncTimestamp.value = timestamp
    }

    // --- Stream Badges & Fusion Customization ---
    private val _streamBadgeRulesJson = MutableStateFlow(
        prefs.getString(KEY_STREAM_BADGE_RULES, "") ?: ""
    )
    val streamBadgeRulesJson: StateFlow<String> = _streamBadgeRulesJson.asStateFlow()

    fun setStreamBadgeRulesJson(json: String) {
        prefs.edit().putString(KEY_STREAM_BADGE_RULES, json).apply()
        _streamBadgeRulesJson.value = json
    }

    private val _streamBadgePlacement = MutableStateFlow(
        com.jkhx.player.ui.screens.stream.badge.StreamBadgePlacement.fromKey(
            prefs.getString(KEY_STREAM_BADGE_PLACEMENT, "bottom")
        )
    )
    val streamBadgePlacement: StateFlow<com.jkhx.player.ui.screens.stream.badge.StreamBadgePlacement> =
        _streamBadgePlacement.asStateFlow()

    fun setStreamBadgePlacement(placement: com.jkhx.player.ui.screens.stream.badge.StreamBadgePlacement) {
        prefs.edit().putString(KEY_STREAM_BADGE_PLACEMENT, placement.key).apply()
        _streamBadgePlacement.value = placement
    }

    private val _showStreamSizeBadges = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_STREAM_SIZE_BADGES, true)
    )
    val showStreamSizeBadges: StateFlow<Boolean> = _showStreamSizeBadges.asStateFlow()

    fun setShowStreamSizeBadges(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_STREAM_SIZE_BADGES, enabled).apply()
        _showStreamSizeBadges.value = enabled
    }

    private val _showAddonIcon = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_ADDON_ICON, true)
    )
    val showAddonIcon: StateFlow<Boolean> = _showAddonIcon.asStateFlow()

    fun setShowAddonIcon(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ADDON_ICON, enabled).apply()
        _showAddonIcon.value = enabled
    }
}

