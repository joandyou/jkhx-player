package com.jkhx.player.ui.screens.plugins

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.ui.theme.LocalDisplayMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jkhx.player.JkhxApplication
import com.jkhx.player.sync.SyncManager
import com.jkhx.player.core.database.entity.RepositoryEntity
import com.jkhx.player.core.database.entity.StremioAddonEntity
import com.jkhx.player.core.model.stremio.StremioManifest
import com.jkhx.player.core.model.PluginManifest
import com.jkhx.player.core.model.PluginSettingItem
import com.jkhx.player.core.model.RepositoryManifest
import com.jkhx.player.plugin.manager.InstalledPluginRecord
import com.jkhx.player.plugin.manager.PluginCheckResult
import com.jkhx.player.ui.screens.search.ActiveSearchHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.compose.ui.res.stringResource
import com.jkhx.player.R
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.PluginCategoryAdult
import com.jkhx.player.ui.theme.PluginCategoryComic
import com.jkhx.player.ui.theme.PluginCategoryLive
import com.jkhx.player.ui.theme.PluginCategoryMovie
import com.jkhx.player.ui.theme.PluginCategoryNovel

private val AccentRed: Color
    @Composable get() = MaterialTheme.colorScheme.primary

private val CardBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surface

private val CardBorderColor: Color
    @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen() {
    val context = LocalContext.current
    val pluginManager = JkhxApplication.instance.pluginManager
    val settingsManager = JkhxApplication.instance.settingsManager
    val isAdultEnabled by settingsManager.isAdultEnabled.collectAsState()
    val isComicEnabled by settingsManager.isComicEnabled.collectAsState()
    val isNovelEnabled by settingsManager.isNovelEnabled.collectAsState()
    val isLiveEnabled by settingsManager.isLiveEnabled.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val displayMode = LocalDisplayMode.current
    val isTablet = displayMode == DeviceDisplayMode.TABLET

    var selectedTabIndex by remember { mutableStateOf(0) }
    var installedRecords by remember { mutableStateOf(listOf<InstalledPluginRecord>()) }
    var stremioAddons by remember { mutableStateOf<List<StremioAddonEntity>>(emptyList()) }
    var isAddingStremioAddon by remember { mutableStateOf(false) }
    val stremioPingMap = remember { mutableStateMapOf<String, Pair<Boolean, Long>?>() }
    var repositoriesWithManifests by remember { mutableStateOf(listOf<Pair<RepositoryEntity, RepositoryManifest>>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for adding repository
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var newRepoUrl by remember { mutableStateOf("") }
    var isAddingRepo by remember { mutableStateOf(false) }
    var addRepoError by remember { mutableStateOf<String?>(null) }

    // Dialog state for adding Stremio / Nuvio addon
    var showAddStremioDialog by remember { mutableStateOf(false) }
    var newStremioUrl by remember { mutableStateOf("") }
    var addStremioError by remember { mutableStateOf<String?>(null) }

    // Installing map to track loading spinner per scraper
    val installingMap = remember { mutableStateMapOf<String, Boolean>() }
    // Custom domain edit map
    val domainInputMap = remember { mutableStateMapOf<String, String>() }

    val json = remember {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    val refreshingRepoUrls = remember { mutableStateMapOf<String, Boolean>() }

    val infiniteTransition = rememberInfiniteTransition(label = "pluginsSpin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    suspend fun reloadData() {
        isLoading = true
        withContext(Dispatchers.IO) {
            installedRecords = pluginManager.getInstalledRecords()
            stremioAddons = pluginManager.getStremioAddons()
            val repoEntities = pluginManager.getRepositories()
            val list = mutableListOf<Pair<RepositoryEntity, RepositoryManifest>>()
            for (repo in repoEntities) {
                try {
                    val m = json.decodeFromString<RepositoryManifest>(repo.manifestJson)
                    list.add(Pair(repo, m))
                } catch (e: Exception) {
                    // Try fetch fresh
                    try {
                        val m = pluginManager.fetchRepository(repo.url)
                        list.add(Pair(repo, m))
                    } catch (e2: Exception) {
                        println("[PluginsScreen] Error parsing repo ${repo.url}: ${e2.message}")
                    }
                }
            }
            repositoriesWithManifests = list
        }
        isLoading = false
    }

    var isRefreshingAll by remember { mutableStateOf(false) }

    suspend fun refreshAll() {
        if (isRefreshingAll) return
        isRefreshingAll = true
        withContext(Dispatchers.IO) {
            try {
                val repoEntities = pluginManager.getRepositories()
                for (repo in repoEntities) {
                    try {
                        pluginManager.addRepository(repo.url)
                    } catch (e: Exception) {
                        println("[PluginsScreen] Refresh repo ${repo.url} failed: ${e.message}")
                    }
                }
                pluginManager.loadInstalledPlugins()
            } catch (e: Exception) {
                println("[PluginsScreen] Global refresh error: ${e.message}")
            }
        }
        reloadData()
        isRefreshingAll = false
        statusMessage = context.getString(R.string.plugins_refresh_all_done)
    }

    val pluginsUpdateCount by pluginManager.pluginsUpdateCounter.collectAsState()
    LaunchedEffect(pluginsUpdateCount) {
        reloadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.plugins_title), 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ) 
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch { refreshAll() }
                        },
                        enabled = !isRefreshingAll
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.plugins_refresh_all),
                            tint = if (isRefreshingAll) AccentRed else MaterialTheme.colorScheme.onBackground,
                            modifier = if (isRefreshingAll) Modifier.rotate(rotationAngle) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 1) {
                FloatingActionButton(
                    onClick = {
                        newRepoUrl = ""
                        addRepoError = null
                        showAddRepoDialog = true
                    },
                    modifier = Modifier.padding(bottom = if (isTablet) 16.dp else 84.dp),
                    containerColor = AccentRed,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.plugins_add_repo_btn))
                }
            } else if (selectedTabIndex == 2) {
                FloatingActionButton(
                    onClick = {
                        newStremioUrl = ""
                        addStremioError = null
                        showAddStremioDialog = true
                    },
                    modifier = Modifier.padding(bottom = if (isTablet) 16.dp else 84.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.plugins_stremio_add_title))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isRefreshingAll || isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentRed
                )
            }

            // TABS (Đã cài đặt | Kho Plugin)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = AccentRed,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = AccentRed,
                        height = 3.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PushPin, 
                                contentDescription = null, 
                                tint = if (selectedTabIndex == 0) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant, 
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.plugins_tab_installed),
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTabIndex == 0) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (selectedTabIndex == 0) AccentRed else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${installedRecords.count { !it.manifest.isStremioAddon }}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTabIndex == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Storefront, 
                                contentDescription = null, 
                                tint = if (selectedTabIndex == 1) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant, 
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.plugins_tab_store),
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTabIndex == 1) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudQueue, 
                                contentDescription = null, 
                                tint = if (selectedTabIndex == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, 
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.plugins_tab_stremio),
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTabIndex == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (selectedTabIndex == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${stremioAddons.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTabIndex == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Status message snack
            if (statusMessage != null) {
                Surface(
                    color = AccentRed.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.dimens.space16, vertical = AppTheme.dimens.space6),
                    shape = ShapeTokens.Chip
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(statusMessage!!, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            // CONTENT
            when (selectedTabIndex) {
                0 -> {
                    // TAB 0: ĐÃ CÀI ĐẶT
                    InstalledPluginsTab(
                        installedRecords = installedRecords,
                        isAdultEnabled = isAdultEnabled,
                        isComicEnabled = isComicEnabled,
                        isNovelEnabled = isNovelEnabled,
                        isLiveEnabled = isLiveEnabled,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        domainInputMap = domainInputMap,
                        onMoveOrder = { id, moveUp ->
                            if (pluginManager.movePluginOrder(id, moveUp)) {
                                installedRecords = pluginManager.getInstalledRecords()
                            }
                        },
                        onToggleEnabled = { id, enabled ->
                            installedRecords = installedRecords.map {
                                if (it.manifest.id == id) it.copy(isEnabled = enabled) else it
                            }
                            coroutineScope.launch {
                                try {
                                    if (enabled) pluginManager.enablePlugin(id) else pluginManager.disablePlugin(id)
                                    if (id.startsWith("stremio_")) {
                                        settingsManager.clearStremioDiscoverSelection()
                                        ActiveSearchHolder.clearDiscoverCache()
                                    }
                                } catch (e: Exception) {
                                    statusMessage = context.getString(R.string.common_error_prefix, e.message ?: "")
                                }
                                reloadData()
                            }
                        },
                        onSaveCustomDomain = { id, domain ->
                            coroutineScope.launch {
                                pluginManager.updateCustomDomain(id, domain.ifBlank { null })
                                statusMessage = context.getString(R.string.plugins_domain_updated, id)
                                reloadData()
                            }
                        },
                        onUninstall = { id ->
                            coroutineScope.launch {
                                val addonRepoUrl = installedRecords.firstOrNull { it.manifest.id == id }?.manifest?.repoUrl
                                pluginManager.uninstallPlugin(id)
                                if (id.startsWith("stremio_")) {
                                    settingsManager.clearStremioDiscoverSelection()
                                    ActiveSearchHolder.clearDiscoverCache()
                                    if (!addonRepoUrl.isNullOrBlank()) {
                                        SyncManager.getInstance(context).onStremioAddonDeleted(addonRepoUrl)
                                    }
                                } else {
                                    SyncManager.getInstance(context).onPluginDeleted(id)
                                }
                                statusMessage = context.getString(R.string.plugins_uninstalled_toast, id)
                                reloadData()
                            }
                        },
                        onOpenAddRepo = { selectedTabIndex = 1 }
                    )
                }
                1 -> {
                    // TAB 1: KHO PLUGIN
                    RepositoryStoreTab(
                        repositories = repositoriesWithManifests,
                        installedRecords = installedRecords,
                        isAdultEnabled = isAdultEnabled,
                        isComicEnabled = isComicEnabled,
                        isNovelEnabled = isNovelEnabled,
                        isLiveEnabled = isLiveEnabled,
                        installingMap = installingMap,
                        onInstallScraper = { repoUrl, scraper ->
                            coroutineScope.launch {
                                installingMap[scraper.id] = true
                                try {
                                    pluginManager.installScraper(repoUrl, scraper)
                                    SyncManager.getInstance(context).onPluginInstalled(scraper.id)
                                    statusMessage = context.getString(R.string.plugins_installed_success, scraper.name)
                                    reloadData()
                                } catch (e: Exception) {
                                    statusMessage = context.getString(R.string.plugins_install_error, e.message ?: "")
                                } finally {
                                    installingMap[scraper.id] = false
                                }
                            }
                        },
                        onUninstallScraper = { scraperId ->
                            coroutineScope.launch {
                                pluginManager.uninstallPlugin(scraperId)
                                SyncManager.getInstance(context).onPluginDeleted(scraperId)
                                statusMessage = context.getString(R.string.plugins_uninstalled_toast, scraperId)
                                reloadData()
                            }
                        },
                        onRemoveRepo = { repoUrl ->
                            coroutineScope.launch {
                                pluginManager.removeRepository(repoUrl)
                                SyncManager.getInstance(context).onRepositoryDeleted(repoUrl)
                                statusMessage = context.getString(R.string.plugins_delete_repo_toast)
                                reloadData()
                            }
                        },
                        onRefreshRepo = { repoUrl ->
                            coroutineScope.launch {
                                refreshingRepoUrls[repoUrl] = true
                                try {
                                    pluginManager.addRepository(repoUrl)
                                    statusMessage = context.getString(R.string.plugins_refresh_repo_toast)
                                    reloadData()
                                } catch (e: Exception) {
                                    statusMessage = context.getString(R.string.common_error_prefix, e.message ?: "")
                                } finally {
                                    refreshingRepoUrls[repoUrl] = false
                                }
                            }
                        },
                        refreshingRepoUrls = refreshingRepoUrls,
                        isRefreshingAll = isRefreshingAll,
                        rotationAngle = rotationAngle,
                        onOpenAddRepo = {
                            newRepoUrl = ""
                            addRepoError = null
                            showAddRepoDialog = true
                        }
                    )
                }
                else -> {
                    // TAB 2: STREMIO ADDONS
                    StremioAddonsTab(
                        stremioAddons = stremioAddons,
                        onOpenAddAddon = {
                            newStremioUrl = ""
                            addStremioError = null
                            showAddStremioDialog = true
                        },
                        onToggleEnabled = { url, enabled ->
                            coroutineScope.launch {
                                pluginManager.toggleStremioAddon(url, enabled)
                                settingsManager.clearStremioDiscoverSelection()
                                ActiveSearchHolder.clearDiscoverCache()
                                SyncManager.getInstance(context).onStremioAddonToggled(url, enabled)
                                reloadData()
                            }
                        },
                        onDeleteAddon = { url ->
                            coroutineScope.launch {
                                pluginManager.removeStremioAddon(url)
                                settingsManager.clearStremioDiscoverSelection()
                                ActiveSearchHolder.clearDiscoverCache()
                                SyncManager.getInstance(context).onStremioAddonDeleted(url)
                                statusMessage = context.getString(R.string.plugins_stremio_deleted_toast, url)
                                reloadData()
                            }
                        },
                        onPingTest = { url ->
                            coroutineScope.launch {
                                val client = pluginManager.getStremioClient()
                                val res = client.checkHealth(url)
                                stremioPingMap[url] = res
                            }
                        },
                        pingResults = stremioPingMap
                    )
                }
            }
        }
    }

    // DIALOG THÊM NGUỒN PLUGIN
    if (showAddRepoDialog) {
        AlertDialog(
            shape = ShapeTokens.Card,
            onDismissRequest = {
                if (!isAddingRepo) showAddRepoDialog = false
            },
            title = {
                Text(stringResource(R.string.plugins_add_repo_dialog_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.plugins_add_repo_dialog_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space10))
                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = {
                            newRepoUrl = it
                            addRepoError = null
                        },
                        placeholder = { Text(stringResource(R.string.plugins_add_repo_input_placeholder), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        shape = ShapeTokens.Button,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (addRepoError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(addRepoError!!, color = AccentRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUrl = newRepoUrl.trim()
                        if (cleanUrl.isBlank()) {
                            addRepoError = "Vui lòng nhập đường dẫn URL hợp lệ"
                            return@Button
                        }
                        isAddingRepo = true
                        addRepoError = null
                        coroutineScope.launch {
                            try {
                                pluginManager.addRepository(cleanUrl)
                                SyncManager.getInstance(context).onRepositoryAdded(cleanUrl)
                                showAddRepoDialog = false
                                selectedTabIndex = 1 // Chuyển ngay sang tab Kho Plugin
                                statusMessage = context.getString(R.string.plugins_add_repo_success)
                                reloadData()
                            } catch (e: Exception) {
                                addRepoError = context.getString(R.string.plugins_add_repo_error, e.message ?: "")
                            } finally {
                                isAddingRepo = false
                            }
                        }
                    },
                    shape = ShapeTokens.Button,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    enabled = !isAddingRepo
                ) {
                    if (isAddingRepo) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_loading), color = Color.White)
                    } else {
                        Text(stringResource(R.string.plugins_add_repo_confirm), color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddRepoDialog = false },
                    enabled = !isAddingRepo
                ) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // DIALOG THÊM ADDON STREMIO / NUVIO
    if (showAddStremioDialog) {
        AlertDialog(
            shape = ShapeTokens.Card,
            onDismissRequest = {
                if (!isAddingStremioAddon) showAddStremioDialog = false
            },
            title = {
                Text(stringResource(R.string.plugins_stremio_add_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.plugins_stremio_add_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(AppTheme.dimens.space10))
                    OutlinedTextField(
                        value = newStremioUrl,
                        onValueChange = {
                            newStremioUrl = it
                            addStremioError = null
                        },
                        placeholder = { Text(stringResource(R.string.plugins_stremio_url_placeholder), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        shape = ShapeTokens.Button,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (addStremioError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(addStremioError!!, color = AccentRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUrl = newStremioUrl.trim()
                        if (cleanUrl.isBlank()) {
                            addStremioError = "Vui lòng nhập đường dẫn URL hợp lệ"
                            return@Button
                        }
                        isAddingStremioAddon = true
                        addStremioError = null
                        coroutineScope.launch {
                            try {
                                val entity = pluginManager.installStremioAddon(cleanUrl)
                                ActiveSearchHolder.cachedStremioAddons = emptyList()
                                SyncManager.getInstance(context).onStremioAddonInstalled(entity.url)
                                showAddStremioDialog = false
                                statusMessage = context.getString(R.string.plugins_stremio_added_success, entity.name)
                                reloadData()
                            } catch (e: Exception) {
                                addStremioError = context.getString(R.string.plugins_stremio_add_error, e.message ?: "")
                            } finally {
                                isAddingStremioAddon = false
                            }
                        }
                    },
                    shape = ShapeTokens.Button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = !isAddingStremioAddon
                ) {
                    if (isAddingStremioAddon) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_loading))
                    } else {
                        Text(stringResource(R.string.plugins_stremio_add_btn))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddStremioDialog = false },
                    enabled = !isAddingStremioAddon
                ) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 1: DANH SÁCH ĐÃ CÀI ĐẶT
// -------------------------------------------------------------
@Composable
private fun InstalledPluginsTab(
    installedRecords: List<InstalledPluginRecord>,
    isAdultEnabled: Boolean,
    isComicEnabled: Boolean,
    isNovelEnabled: Boolean,
    isLiveEnabled: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    domainInputMap: MutableMap<String, String>,
    onMoveOrder: (id: String, moveUp: Boolean) -> Unit,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onSaveCustomDomain: (id: String, domain: String) -> Unit,
    onUninstall: (id: String) -> Unit,
    onOpenAddRepo: () -> Unit
) {
    val visibleRecords = remember(installedRecords, isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled) {
        installedRecords.filter {
            !it.manifest.isStremioAddon &&
            (isAdultEnabled || (!it.manifest.isAdult && it.manifest.categoryType != "adult")) &&
            (isComicEnabled || (!it.manifest.isComic && it.manifest.categoryType != "comic")) &&
            (isNovelEnabled || (!it.manifest.isNovel && it.manifest.categoryType != "novel")) &&
            (isLiveEnabled || (!it.manifest.isLive && it.manifest.categoryType != "live"))
        }
    }
    val filteredRecords = remember(visibleRecords, searchQuery) {
        if (searchQuery.isBlank()) visibleRecords
        else visibleRecords.filter {
            it.manifest.name.contains(searchQuery, ignoreCase = true) ||
            it.manifest.id.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.plugins_search_installed_placeholder), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = ShapeTokens.Card,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = AccentRed,
                unfocusedBorderColor = CardBorderColor,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true
        )

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ExtensionOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (installedRecords.isEmpty()) stringResource(R.string.plugins_empty_installed) else stringResource(R.string.plugins_empty_store),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.plugins_empty_installed_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onOpenAddRepo,
                        shape = ShapeTokens.Button,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.plugins_explore_store_btn), color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(filteredRecords, key = { _, it -> it.manifest.id }) { index, record ->
                    InstalledPluginCard(
                        record = record,
                        canMoveUp = index > 0,
                        canMoveDown = index < filteredRecords.size - 1,
                        onMoveUp = { onMoveOrder(record.manifest.id, true) },
                        onMoveDown = { onMoveOrder(record.manifest.id, false) },
                        onToggleEnabled = { onToggleEnabled(record.manifest.id, it) },
                        onUninstall = { onUninstall(record.manifest.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginTypeBadge(categoryType: String) {
    val (badgeBg, badgeText) = when (categoryType) {
        "adult" -> PluginCategoryAdult to stringResource(R.string.home_tag_adult)
        "comic" -> PluginCategoryComic to stringResource(R.string.plugins_tag_comic)
        "novel" -> PluginCategoryNovel to stringResource(R.string.plugins_tag_novel)
        "live" -> PluginCategoryLive to stringResource(R.string.plugins_tag_live)
        else -> PluginCategoryMovie to stringResource(R.string.plugins_tag_movie)
    }
    Surface(
        color = badgeBg,
        shape = ShapeTokens.Badge
    ) {
        Text(
            text = badgeText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun InstalledPluginCard(
    record: InstalledPluginRecord,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    val manifest = record.manifest
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val pluginManager = JkhxApplication.instance.pluginManager
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isCheckingDomain by remember { mutableStateOf(false) }
    var domainCheckResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Card(
        shape = ShapeTokens.Card,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderColor, ShapeTokens.Card)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Logo, Thông tin, Cụm Nút Di chuyển, Nút Xóa & Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Logo / Icon nhỏ gọn
                val iconUrl = manifest.effectiveIconUrl
                if (!iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(ShapeTokens.Chip)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(ShapeTokens.Chip)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Extension, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name & Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = manifest.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (manifest.isStremioAddon) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = ShapeTokens.Badge
                            ) {
                                Text(
                                    text = stringResource(R.string.plugins_stremio_badge),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = Color(0xFF00897B),
                                shape = ShapeTokens.Badge
                            ) {
                                Text(
                                    text = stringResource(R.string.plugins_quickjs_badge),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        PluginTypeBadge(categoryType = manifest.categoryType)
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "v${manifest.version} • ${manifest.displayTypeLabel}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Nút di chuyển lên (↑)
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "↑",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (canMoveUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Nút di chuyển xuống (↓)
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "↓",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (canMoveDown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Nút Check Nguồn Plugin (Check Health)
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isCheckingDomain = true
                            domainCheckResult = null
                            val res = pluginManager.checkPluginHealth(manifest.id, null)
                            val msg = res.toLocalizedMessage(context)
                            domainCheckResult = res.isAlive to msg
                            isCheckingDomain = false

                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.plugins_health_toast_format, manifest.name, msg),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !isCheckingDomain,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isCheckingDomain) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AccentRed
                        )
                    } else {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = stringResource(R.string.plugins_check_health_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Nút Cài Đặt Plugin (Settings)
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.plugins_settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Cụm Nút Xóa
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.plugins_uninstall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                var isChecked by remember(record.manifest.id, record.isEnabled) { mutableStateOf(record.isEnabled) }
                Switch(
                    checked = isChecked,
                    onCheckedChange = { newState ->
                        isChecked = newState
                        onToggleEnabled(newState)
                    },
                    modifier = Modifier.padding(start = 2.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentRed,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // Dòng hiển thị Domain đầy đủ chiều rộng
            val activeDomain = record.customDomain?.takeIf { it.isNotBlank() }
                ?: manifest.defaultDomain?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.plugins_health_no_domain)

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeTokens.Chip)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = if (record.customDomain != null) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.plugins_domain_label, activeDomain),
                    fontSize = 11.5.sp,
                    color = if (record.customDomain != null) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            domainCheckResult?.let { (isAlive, msg) ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isAlive) "✓ $msg" else "✕ $msg",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAlive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            shape = ShapeTokens.Card,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.plugins_uninstall_dialog_title, manifest.name), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.plugins_uninstall_dialog_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onUninstall()
                    },
                    shape = ShapeTokens.Button,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.plugins_uninstall_confirm), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showSettingsDialog) {
        PluginSettingsDialog(
            pluginId = manifest.id,
            pluginName = manifest.name,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun PluginSettingsDialog(
    pluginId: String,
    pluginName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pluginManager = JkhxApplication.instance.pluginManager
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var isLoading by remember { mutableStateOf(true) }
    var settingItems by remember { mutableStateOf<List<PluginSettingItem>>(emptyList()) }
    val settingValues = remember { mutableStateMapOf<String, String>() }

    fun saveSetting(key: String, value: String) {
        settingValues[key] = value
        pluginManager.setPluginSetting(pluginId, key, value)
        if (key.equals("base_url", ignoreCase = true) || key.equals("domain", ignoreCase = true)) {
            coroutineScope.launch {
                pluginManager.updateCustomDomain(pluginId, value.ifBlank { null })
            }
        }
    }

    LaunchedEffect(pluginId) {
        isLoading = true
        val items = pluginManager.getPluginSettings(pluginId) ?: emptyList()
        settingItems = items
        items.forEach { item ->
            settingValues[item.key] = item.currentValue ?: item.defaultValue
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${stringResource(R.string.plugins_settings_title)}: $pluginName",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (settingItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.plugins_settings_empty),
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(settingItems) { item ->
                        val currentVal = settingValues[item.key] ?: item.defaultValue
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), ShapeTokens.Card)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val desc = item.description
                            if (!desc.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            when (item.type.lowercase()) {
                                "boolean" -> {
                                    val isChecked = currentVal == "true" || currentVal == "1"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isChecked) "Bật" else "Tắt",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = isChecked,
                                            onCheckedChange = { newState ->
                                                val strVal = newState.toString()
                                                saveSetting(item.key, strVal)
                                            }
                                        )
                                    }
                                }
                                "select" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        item.options.forEach { opt ->
                                            val isOptSelected = currentVal == opt.slug
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(ShapeTokens.Chip)
                                                    .clickable {
                                                        saveSetting(item.key, opt.slug)
                                                    }
                                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                                            ) {
                                                RadioButton(
                                                    selected = isOptSelected,
                                                    onClick = {
                                                        saveSetting(item.key, opt.slug)
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = opt.name,
                                                    fontSize = 13.sp,
                                                    color = if (isOptSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    var textInput by remember(currentVal) { mutableStateOf(currentVal) }
                                    OutlinedTextField(
                                        value = textInput,
                                        onValueChange = {
                                            textInput = it
                                            settingValues[item.key] = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 13.sp),
                                        shape = ShapeTokens.Card
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Góc trái: Nút Đặt lại mặc định
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        coroutineScope.launch {
                            settingItems.forEach { item ->
                                settingValues[item.key] = item.defaultValue
                                pluginManager.setPluginSetting(pluginId, item.key, item.defaultValue)
                            }
                            pluginManager.updateCustomDomain(pluginId, null)
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.plugins_settings_reset_success, pluginName),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.plugins_settings_reset_domain),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Góc phải: Cụm Nút Hủy và Lưu cài đặt
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            coroutineScope.launch {
                                settingValues.forEach { (k, v) ->
                                    pluginManager.setPluginSetting(pluginId, k, v)
                                    if (k.equals("base_url", ignoreCase = true) || k.equals("domain", ignoreCase = true)) {
                                        pluginManager.updateCustomDomain(pluginId, v.ifBlank { null })
                                    }
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.plugins_settings_saved, pluginName),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        },
                        shape = ShapeTokens.Button,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.plugins_settings_save_btn),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ShapeTokens.Card
    )
}

// -------------------------------------------------------------
// TAB 2: KHO PLUGIN (REPOSITORIES & SCRAPERS)
// -------------------------------------------------------------
@Composable
private fun RepositoryStoreTab(
    repositories: List<Pair<RepositoryEntity, RepositoryManifest>>,
    installedRecords: List<InstalledPluginRecord>,
    isAdultEnabled: Boolean,
    isComicEnabled: Boolean,
    isNovelEnabled: Boolean,
    isLiveEnabled: Boolean,
    installingMap: Map<String, Boolean>,
    onInstallScraper: (repoUrl: String, scraper: PluginManifest) -> Unit,
    onUninstallScraper: (scraperId: String) -> Unit,
    onRemoveRepo: (repoUrl: String) -> Unit,
    onRefreshRepo: (repoUrl: String) -> Unit,
    refreshingRepoUrls: Map<String, Boolean> = emptyMap(),
    isRefreshingAll: Boolean = false,
    rotationAngle: Float = 0f,
    onOpenAddRepo: () -> Unit
) {
    val installedMap = remember(installedRecords) {
        installedRecords.associateBy { it.manifest.id }
    }

    if (repositories.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.dimens.space24),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                Text(
                    stringResource(R.string.plugins_no_repos),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(AppTheme.dimens.space8))
                Text(
                    stringResource(R.string.plugins_no_repos_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(AppTheme.dimens.space16))
                Button(
                    onClick = onOpenAddRepo,
                    shape = ShapeTokens.Button,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.plugins_add_repo_btn), color = Color.White)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(repositories, key = { it.first.url }) { (entity, manifest) ->
                val isRepoRefreshing = (refreshingRepoUrls[entity.url] == true) || isRefreshingAll
                RepositorySectionCard(
                    repoEntity = entity,
                    manifest = manifest,
                    installedMap = installedMap,
                    isAdultEnabled = isAdultEnabled,
                    isComicEnabled = isComicEnabled,
                    isNovelEnabled = isNovelEnabled,
                    isLiveEnabled = isLiveEnabled,
                    installingMap = installingMap,
                    isRefreshing = isRepoRefreshing,
                    rotationAngle = rotationAngle,
                    onInstallScraper = { scraper -> onInstallScraper(entity.url, scraper) },
                    onUninstallScraper = onUninstallScraper,
                    onRefreshRepo = { onRefreshRepo(entity.url) },
                    onRemoveRepo = { onRemoveRepo(entity.url) }
                )
            }
        }
    }
}

@Composable
private fun RepositorySectionCard(
    repoEntity: RepositoryEntity,
    manifest: RepositoryManifest,
    installedMap: Map<String, InstalledPluginRecord>,
    isAdultEnabled: Boolean,
    isComicEnabled: Boolean,
    isNovelEnabled: Boolean,
    isLiveEnabled: Boolean,
    installingMap: Map<String, Boolean>,
    isRefreshing: Boolean = false,
    rotationAngle: Float = 0f,
    onInstallScraper: (scraper: PluginManifest) -> Unit,
    onUninstallScraper: (scraperId: String) -> Unit,
    onRefreshRepo: () -> Unit,
    onRemoveRepo: () -> Unit
) {
    var isExpanded by rememberSaveable(repoEntity.url) { mutableStateOf(true) }

    Card(
        shape = ShapeTokens.Card,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderColor, ShapeTokens.Card)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Repo Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = AccentRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { isExpanded = !isExpanded }
                ) {
                    Text(
                        manifest.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "v${manifest.version} • ${manifest.allScrapers.size} plugins",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefreshRepo,
                    enabled = !isRefreshing
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.plugins_refresh_repo),
                        tint = if (isRefreshing) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (isRefreshing) Modifier.rotate(rotationAngle) else Modifier
                    )
                }
                IconButton(onClick = onRemoveRepo) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.plugins_delete_repo), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Nút thu hẹp / mở rộng danh sách card plugin trong source (▲ ▼)
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpanded) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorderColor)

                    val displayScrapers = remember(manifest, isAdultEnabled, isComicEnabled, isNovelEnabled, isLiveEnabled) {
                        manifest.allScrapers.filter {
                            (isAdultEnabled || (!it.isAdult && it.categoryType != "adult")) &&
                            (isComicEnabled || (!it.isComic && it.categoryType != "comic")) &&
                            (isNovelEnabled || (!it.isNovel && it.categoryType != "novel")) &&
                            (isLiveEnabled || (!it.isLive && it.categoryType != "live"))
                        }
                    }

                    // Scrapers list
                    if (displayScrapers.isEmpty()) {
                        Text(
                            if (manifest.allScrapers.isEmpty()) stringResource(R.string.plugins_no_scrapers)
                            else stringResource(R.string.plugins_no_scrapers_safe),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            displayScrapers.forEach { scraper ->
                                val installedRecord = installedMap[scraper.id]
                                val isInstalled = installedRecord != null
                                val isInstalling = installingMap[scraper.id] == true
                                val hasUpdate = isInstalled && installedRecord.manifest.version != scraper.version

                                ScraperStoreItem(
                                    scraper = scraper,
                                    isInstalled = isInstalled,
                                    isInstalling = isInstalling,
                                    hasUpdate = hasUpdate,
                                    onInstall = { onInstallScraper(scraper) },
                                    onUninstall = { onUninstallScraper(scraper.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScraperStoreItem(
    scraper: PluginManifest,
    isInstalled: Boolean,
    isInstalling: Boolean,
    hasUpdate: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Surface(
        shape = ShapeTokens.Chip,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scraper Logo / Icon
            val iconUrl = scraper.effectiveIconUrl
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(ShapeTokens.Chip)
                        .background(MaterialTheme.colorScheme.surface)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(ShapeTokens.Chip)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Extension, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        scraper.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    PluginTypeBadge(categoryType = scraper.categoryType)
                }
                Text(
                    "v${scraper.version} • ${scraper.displayTypeLabel}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val desc = scraper.description
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        desc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Button
            if (isInstalling) {
                CircularProgressIndicator(
                    color = AccentRed,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else if (hasUpdate) {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = ShapeTokens.Chip
                ) {
                    Text(stringResource(R.string.plugins_update_btn), fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else if (isInstalled) {
                OutlinedButton(
                    onClick = onUninstall,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    border = BorderStroke(1.dp, CardBorderColor),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = ShapeTokens.Chip
                ) {
                    Text(stringResource(R.string.plugins_uninstall_short), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = ShapeTokens.Chip
                ) {
                    Text(stringResource(R.string.plugins_install), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun PluginCheckResult.toLocalizedMessage(context: android.content.Context): String = when (statusType) {
    PluginCheckResult.StatusType.OK -> {
        val latencyText = if (latencyMs != null) " (${latencyMs}ms)" else ""
        (customMessage ?: context.getString(R.string.plugins_health_ok)) + latencyText
    }
    PluginCheckResult.StatusType.CUSTOM_STATUS -> {
        val latencyText = if (latencyMs != null) " (${latencyMs}ms)" else ""
        (customMessage ?: context.getString(R.string.plugins_health_status, rawStatus ?: "")) + latencyText
    }
    PluginCheckResult.StatusType.DOMAIN_ACTIVE -> {
        context.getString(R.string.plugins_health_active, httpCode ?: 200, latencyMs ?: 0L)
    }
    PluginCheckResult.StatusType.DOMAIN_HTTP_ERROR -> {
        context.getString(R.string.plugins_health_http_error, httpCode ?: 0, latencyMs ?: 0L)
    }
    PluginCheckResult.StatusType.DOMAIN_EMPTY -> {
        context.getString(R.string.plugins_health_domain_empty)
    }
    PluginCheckResult.StatusType.CANNOT_CONNECT -> {
        customMessage ?: context.getString(R.string.plugins_health_cannot_connect, latencyMs ?: 0L)
    }
    PluginCheckResult.StatusType.NO_DOMAIN_CONFIGURED -> {
        context.getString(R.string.plugins_health_no_domain)
    }
    PluginCheckResult.StatusType.SERVER_ERROR -> {
        customMessage ?: context.getString(R.string.plugins_health_error)
    }
}

@Composable
private fun StremioAddonsTab(
    stremioAddons: List<StremioAddonEntity>,
    onOpenAddAddon: () -> Unit,
    onToggleEnabled: (url: String, enabled: Boolean) -> Unit,
    onDeleteAddon: (url: String) -> Unit,
    onPingTest: (url: String) -> Unit,
    pingResults: Map<String, Pair<Boolean, Long>?>
) {
    val context = LocalContext.current
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Danh sách Addon đã cài đặt hoặc Empty State
        if (stremioAddons.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.plugins_stremio_empty),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.plugins_stremio_empty_desc),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenAddAddon,
                            shape = ShapeTokens.Button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.plugins_stremio_add_action_btn))
                        }
                    }
                }
            }
        } else {
            items(stremioAddons, key = { it.url }) { addon ->
                val sManifest = remember(addon.manifestJson) {
                    try {
                        json.decodeFromString<StremioManifest>(addon.manifestJson)
                    } catch (_: Exception) {
                        null
                    }
                }
                var showDeleteConfirm by remember { mutableStateOf(false) }

                Card(
                    shape = ShapeTokens.Card,
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorderColor, ShapeTokens.Card)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Hàng 1: Icon + Tên Addon (chiếm trọn dòng với Ellipsis) + Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val iconUrl = addon.iconUrl ?: sManifest?.effectiveIconUrl
                            if (!iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = iconUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(ShapeTokens.Chip)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            } else {
                                Surface(
                                    shape = ShapeTokens.Chip,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.CloudQueue,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = addon.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = addon.isEnabled,
                                onCheckedChange = { onToggleEnabled(addon.url, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Hàng 2: URL manifest
                        Text(
                            text = addon.url,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Hàng 3: Mô tả (nếu có)
                        val desc = addon.description
                        if (!desc.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Hàng 4: Resource & Catalog chips + Phiên bản
                        if (sManifest != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sManifest.catalogs.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = ShapeTokens.Badge
                                    ) {
                                        Text(
                                            stringResource(R.string.plugins_stremio_catalogs_count, sManifest.catalogs.size),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (sManifest.hasResource("stream")) {
                                    Surface(
                                        color = Color(0xFF00897B).copy(alpha = 0.15f),
                                        shape = ShapeTokens.Badge
                                    ) {
                                        Text(
                                            stringResource(R.string.plugins_stremio_streams_support),
                                            fontSize = 10.sp,
                                            color = Color(0xFF00897B),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (sManifest.hasResource("subtitles")) {
                                    Surface(
                                        color = Color(0xFF7B1FA2).copy(alpha = 0.15f),
                                        shape = ShapeTokens.Badge
                                    ) {
                                        Text(
                                            stringResource(R.string.plugins_stremio_subtitles_support),
                                            fontSize = 10.sp,
                                            color = Color(0xFFBA68C8),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (!addon.version.isNullOrBlank()) {
                                    Text(
                                        "v${addon.version}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(6.dp))

                        val configureUrl = remember(sManifest, addon.url) {
                            sManifest?.getConfigureUrl(addon.url)
                        }

                        // Actions Row: Ping result, Configure button & Delete button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val pingResult = pingResults[addon.url]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(ShapeTokens.Chip)
                                        .clickable { onPingTest(addon.url) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (pingResult?.first == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        when {
                                            pingResult == null -> stringResource(R.string.plugins_stremio_ping_test)
                                            pingResult.first -> "${pingResult.second}ms"
                                            else -> stringResource(R.string.common_status_error)
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (pingResult?.first == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!configureUrl.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(ShapeTokens.Chip)
                                            .clickable {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(configureUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Không thể mở trình duyệt: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = stringResource(R.string.plugins_addon_configure),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.plugins_addon_configure),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        shape = ShapeTokens.Card,
                        onDismissRequest = { showDeleteConfirm = false },
                        title = {
                            Text(stringResource(R.string.plugins_stremio_delete_title, addon.name), fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Text(stringResource(R.string.plugins_stremio_delete_desc), fontSize = 14.sp)
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteConfirm = false
                                    onDeleteAddon(addon.url)
                                }
                            ) {
                                Text(stringResource(R.string.plugins_stremio_delete_confirm), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}


