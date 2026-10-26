package com.jkhx.player.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FavoriteBorder
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
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.components.FavoriteMovieCard
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.LocalDisplayMode
import com.jkhx.player.ui.theme.ShapeTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onMovieClick: (providerId: String, slug: String) -> Unit,
    onBack: (() -> Unit)? = null,
    onExploreClick: (() -> Unit)? = null
) {
    val favoriteDao = remember { JkhxApplication.instance.database.favoriteDao() }
    val pluginManager = JkhxApplication.instance.pluginManager
    val settingsManager = JkhxApplication.instance.settingsManager
    val isAdultEnabled by settingsManager.isAdultEnabled.collectAsState()
    val isComicEnabled by settingsManager.isComicEnabled.collectAsState()
    val isNovelEnabled by settingsManager.isNovelEnabled.collectAsState()
    val isLiveEnabled by settingsManager.isLiveEnabled.collectAsState()
    val updateCount by pluginManager.pluginsUpdateCounter.collectAsState()
    val rawFavorites by favoriteDao.getAllFavorites().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET

    var showClearDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Lọc bỏ phim 18+, truyện tranh, truyện chữ, live nếu phân nhóm tương ứng bị tắt trong Cài đặt
    val displayFavorites = remember(rawFavorites, isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled, updateCount) {
        rawFavorites.filter { item ->
            pluginManager.isProviderVisible(
                pluginId = item.providerId,
                isAdultEnabled = isAdultEnabled,
                isComicEnabled = isComicEnabled,
                isNovelEnabled = isNovelEnabled,
                isLiveEnabled = isLiveEnabled
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.favorites_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (displayFavorites.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.favorites_subtitle, displayFavorites.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                },
                actions = {
                    if (displayFavorites.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.favorites_clear_all),
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
            if (displayFavorites.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(AppTheme.dimens.space24),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                    Text(
                        text = stringResource(R.string.favorites_empty_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space6))
                    Text(
                        text = stringResource(R.string.favorites_empty_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (onExploreClick != null) {
                        Spacer(modifier = Modifier.height(AppTheme.dimens.space20))
                        Button(
                            onClick = onExploreClick,
                            shape = ShapeTokens.Button,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.history_explore_now))
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 5 else 3),
                    contentPadding = PaddingValues(
                        start = AppTheme.dimens.space14,
                        end = AppTheme.dimens.space14,
                        top = AppTheme.dimens.space14,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space10),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space14),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = displayFavorites,
                        key = { it.id },
                        contentType = { "favorite_card" }
                    ) { favItem ->
                        val providerName = remember(favItem.providerId) {
                            pluginManager.getPluginName(favItem.providerId)
                        }
                        FavoriteMovieCard(
                            favorite = favItem,
                            sourceBadge = providerName,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onMovieClick(favItem.providerId, favItem.movieSlug)
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    favoriteDao.delete(favItem.id)
                                    com.jkhx.player.sync.SyncManager.getInstance(context).onFavoriteDeleted(favItem.id)
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
            title = { Text(stringResource(R.string.favorites_dialog_clear_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.favorites_dialog_clear_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            favoriteDao.clearAll()
                            com.jkhx.player.sync.SyncManager.getInstance(context).onAllFavoritesCleared()
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

