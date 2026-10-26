package com.jkhx.player.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.components.ContinueWatchingCard
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.LocalDisplayMode
import com.jkhx.player.ui.theme.ShapeTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit,
    onMovieClick: (providerId: String, slug: String) -> Unit,
    onResumePlayback: ((HistoryEntity) -> Unit)? = null,
    onSelectStream: ((HistoryEntity) -> Unit)? = null
) {
    val historyDao = remember { JkhxApplication.instance.database.historyDao() }
    val pluginManager = JkhxApplication.instance.pluginManager
    val settingsManager = JkhxApplication.instance.settingsManager
    val isAdultEnabled by settingsManager.isAdultEnabled.collectAsState()
    val isComicEnabled by settingsManager.isComicEnabled.collectAsState()
    val isNovelEnabled by settingsManager.isNovelEnabled.collectAsState()
    val isLiveEnabled by settingsManager.isLiveEnabled.collectAsState()
    val updateCount by pluginManager.pluginsUpdateCounter.collectAsState()
    val rawHistory by historyDao.getAllHistory().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET

    var showClearDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Lọc bỏ phim 18+, truyện tranh, truyện chữ, live nếu phân nhóm tương ứng bị tắt trong Cài đặt và gộp trùng phim khác nguồn (Universal Deduplication)
    val displayHistory = remember(rawHistory, isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled, updateCount) {
        val seen = mutableSetOf<String>()
        rawHistory.filter { item ->
            val isVisible = pluginManager.isProviderVisible(
                pluginId = item.providerId,
                isAdultEnabled = isAdultEnabled,
                isComicEnabled = isComicEnabled,
                isNovelEnabled = isNovelEnabled,
                isLiveEnabled = isLiveEnabled
            )
            if (!isVisible) return@filter false

            val isComicOrNovel = pluginManager.isComicProvider(item.providerId) || pluginManager.isNovelProvider(item.providerId)
            val isLive = pluginManager.isLiveProvider(item.providerId)
            if (isComicOrNovel || isLive) {
                return@filter seen.add(item.id)
            }

            // Gộp trùng thông minh tuyệt đối cho Movie / TV
            val imdbMatch = Regex("""(tt\d+)""").find(item.id)?.groupValues?.getOrNull(1)
                ?: Regex("""(tt\d+)""").find(item.movieSlug)?.groupValues?.getOrNull(1)
            val rawRoot = item.movieName.substringBefore(":").substringBefore("-").trim()
            val cleanRoot = rawRoot.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
            val fullCleanTitle = item.movieName.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

            val primaryKey = if (!imdbMatch.isNullOrBlank()) "imdb:$imdbMatch" else null
            val rootKey = if (cleanRoot.length >= 3) "root_title:$cleanRoot" else null
            val titleKey = if (fullCleanTitle.isNotBlank()) "title:$fullCleanTitle" else null

            if (primaryKey != null && seen.contains(primaryKey)) return@filter false
            if (rootKey != null && seen.contains(rootKey)) return@filter false
            if (titleKey != null && seen.contains(titleKey)) return@filter false
            if (seen.contains(item.id)) return@filter false

            if (primaryKey != null) seen.add(primaryKey)
            if (rootKey != null) seen.add(rootKey)
            if (titleKey != null) seen.add(titleKey)
            seen.add(item.id)
            true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.history_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = stringResource(R.string.history_subtitle, displayHistory.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (displayHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.history_clear_all),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (displayHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(AppTheme.dimens.space24),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space6))
                    Text(
                        text = stringResource(R.string.history_empty_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space20))
                    Button(
                        onClick = onBack,
                        shape = ShapeTokens.Button,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.history_explore_now))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 5 else 3),
                    contentPadding = PaddingValues(
                        start = AppTheme.dimens.space14,
                        end = AppTheme.dimens.space14,
                        top = AppTheme.dimens.space14,
                        bottom = 48.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space10),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space14),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = displayHistory,
                        key = { it.id },
                        contentType = { "continue_watching_card" }
                    ) { historyItem ->
                        val providerName = remember(historyItem.providerId) {
                            pluginManager.getPluginName(historyItem.providerId)
                        }
                        ContinueWatchingCard(
                            history = historyItem,
                            sourceBadge = providerName,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (onResumePlayback != null) {
                                    onResumePlayback(historyItem)
                                } else {
                                    onMovieClick(historyItem.providerId, historyItem.movieSlug)
                                }
                            },
                            onLongClick = {
                                if (onSelectStream != null) {
                                    onSelectStream(historyItem)
                                } else {
                                    onMovieClick(historyItem.providerId, historyItem.movieSlug)
                                }
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    historyDao.delete(historyItem.id)
                                    val isComicOrNovel = pluginManager.isComicProvider(historyItem.providerId) || pluginManager.isNovelProvider(historyItem.providerId)
                                    val isLive = pluginManager.isLiveProvider(historyItem.providerId)
                                    if (!isComicOrNovel && !isLive && historyItem.movieName.isNotBlank()) {
                                        historyDao.deleteByName(historyItem.movieName)
                                    }
                                    com.jkhx.player.sync.SyncManager.getInstance(context).onHistoryDeleted(historyItem.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_dialog_clear_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.history_dialog_clear_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            historyDao.clearAll()
                            com.jkhx.player.sync.SyncManager.getInstance(context).onAllHistoryCleared()
                            showClearDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.history_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
