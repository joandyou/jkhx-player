package com.jkhx.player.ui.screens.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.database.entity.RepositoryEntity
import com.jkhx.player.core.model.PluginManifest
import com.jkhx.player.core.model.RepositoryManifest
import com.jkhx.player.plugin.manager.InstalledPluginRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val AccentRed = Color(0xFFE50914)
private val CardBackground = Color(0xFF161616)
private val CardBorderColor = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen() {
    val pluginManager = JkhxApplication.instance.pluginManager
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var installedRecords by remember { mutableStateOf(listOf<InstalledPluginRecord>()) }
    var repositoriesWithManifests by remember { mutableStateOf(listOf<Pair<RepositoryEntity, RepositoryManifest>>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for adding repository
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var newRepoUrl by remember { mutableStateOf("") }
    var isAddingRepo by remember { mutableStateOf(false) }
    var addRepoError by remember { mutableStateOf<String?>(null) }

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

    suspend fun reloadData() {
        isLoading = true
        withContext(Dispatchers.IO) {
            installedRecords = pluginManager.getInstalledRecords()
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

    LaunchedEffect(Unit) {
        reloadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý Plugin", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch { reloadData() }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                    containerColor = AccentRed,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm nguồn Plugin")
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
            // TABS (Đã cài đặt | Kho Plugin)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = AccentRed,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = if (selectedTabIndex == 0) AccentRed else Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Đã cài đặt",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTabIndex == 0) Color.White else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (selectedTabIndex == 0) AccentRed else Color.DarkGray,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${installedRecords.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
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
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = if (selectedTabIndex == 1) AccentRed else Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Kho Plugin",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTabIndex == 1) Color.White else Color.Gray
                            )
                        }
                    }
                )
            }

            // Status message snack
            if (statusMessage != null) {
                Surface(
                    color = AccentRed.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
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
            if (selectedTabIndex == 0) {
                // TAB 1: ĐÃ CÀI ĐẶT
                InstalledPluginsTab(
                    installedRecords = installedRecords,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    domainInputMap = domainInputMap,
                    onToggleEnabled = { id, enabled ->
                        installedRecords = installedRecords.map {
                            if (it.manifest.id == id) it.copy(isEnabled = enabled) else it
                        }
                        coroutineScope.launch {
                            try {
                                if (enabled) pluginManager.enablePlugin(id) else pluginManager.disablePlugin(id)
                            } catch (e: Exception) {
                                statusMessage = "Lỗi: ${e.message}"
                            }
                            reloadData()
                        }
                    },
                    onSaveCustomDomain = { id, domain ->
                        coroutineScope.launch {
                            pluginManager.updateCustomDomain(id, domain.ifBlank { null })
                            statusMessage = "Đã cập nhật domain cho $id"
                            reloadData()
                        }
                    },
                    onUninstall = { id ->
                        coroutineScope.launch {
                            pluginManager.uninstallPlugin(id)
                            statusMessage = "Đã gỡ cài đặt $id"
                            reloadData()
                        }
                    },
                    onOpenAddRepo = { selectedTabIndex = 1 }
                )
            } else {
                // TAB 2: KHO PLUGIN
                RepositoryStoreTab(
                    repositories = repositoriesWithManifests,
                    installedRecords = installedRecords,
                    installingMap = installingMap,
                    onInstallScraper = { repoUrl, scraper ->
                        coroutineScope.launch {
                            installingMap[scraper.id] = true
                            try {
                                pluginManager.installScraper(repoUrl, scraper)
                                statusMessage = "Đã cài đặt thành công ${scraper.name}"
                                reloadData()
                            } catch (e: Exception) {
                                statusMessage = "Lỗi cài đặt: ${e.message}"
                            } finally {
                                installingMap[scraper.id] = false
                            }
                        }
                    },
                    onUninstallScraper = { scraperId ->
                        coroutineScope.launch {
                            pluginManager.uninstallPlugin(scraperId)
                            statusMessage = "Đã gỡ cài đặt $scraperId"
                            reloadData()
                        }
                    },
                    onRemoveRepo = { repoUrl ->
                        coroutineScope.launch {
                            pluginManager.removeRepository(repoUrl)
                            statusMessage = "Đã xóa kho nguồn"
                            reloadData()
                        }
                    },
                    onRefreshRepo = { repoUrl ->
                        coroutineScope.launch {
                            try {
                                pluginManager.addRepository(repoUrl)
                                statusMessage = "Đã cập nhật kho nguồn"
                                reloadData()
                            } catch (e: Exception) {
                                statusMessage = "Lỗi làm mới: ${e.message}"
                            }
                        }
                    },
                    onOpenAddRepo = { showAddRepoDialog = true }
                )
            }
        }
    }

    // DIALOG THÊM NGUỒN PLUGIN
    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isAddingRepo) showAddRepoDialog = false
            },
            title = {
                Text("Thêm Nguồn Plugin (Manifest)", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Nhập liên kết manifest.json (GitHub raw, Pastebin, hoặc URL nguồn riêng):",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = {
                            newRepoUrl = it
                            addRepoError = null
                        },
                        placeholder = { Text("https://raw.githubusercontent.com/.../manifest.json", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
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
                                showAddRepoDialog = false
                                selectedTabIndex = 1 // Chuyển ngay sang tab Kho Plugin
                                statusMessage = "Đã thêm kho nguồn thành công!"
                                reloadData()
                            } catch (e: Exception) {
                                addRepoError = "Lỗi nạp manifest: ${e.message}"
                            } finally {
                                isAddingRepo = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    enabled = !isAddingRepo
                ) {
                    if (isAddingRepo) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đang tải...")
                    } else {
                        Text("Thêm nguồn")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddRepoDialog = false },
                    enabled = !isAddingRepo
                ) {
                    Text("Hủy", color = Color.Gray)
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    domainInputMap: MutableMap<String, String>,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onSaveCustomDomain: (id: String, domain: String) -> Unit,
    onUninstall: (id: String) -> Unit,
    onOpenAddRepo: () -> Unit
) {
    val filteredRecords = remember(installedRecords, searchQuery) {
        if (searchQuery.isBlank()) installedRecords
        else installedRecords.filter {
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
            placeholder = { Text("Tìm kiếm plugin đã cài...", fontSize = 13.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedBorderColor = AccentRed,
                unfocusedBorderColor = CardBorderColor
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
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (installedRecords.isEmpty()) "Chưa có plugin nào được cài đặt" else "Không tìm thấy plugin phù hợp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Chuyển sang tab 'Kho Plugin' để khám phá và cài đặt plugins.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onOpenAddRepo,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đi đến Kho Plugin")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredRecords, key = { it.manifest.id }) { record ->
                    InstalledPluginCard(
                        record = record,
                        domainInputMap = domainInputMap,
                        onToggleEnabled = { onToggleEnabled(record.manifest.id, it) },
                        onSaveCustomDomain = { onSaveCustomDomain(record.manifest.id, it) },
                        onUninstall = { onUninstall(record.manifest.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledPluginCard(
    record: InstalledPluginRecord,
    domainInputMap: MutableMap<String, String>,
    onToggleEnabled: (Boolean) -> Unit,
    onSaveCustomDomain: (String) -> Unit,
    onUninstall: () -> Unit
) {
    val manifest = record.manifest
    val currentInput = domainInputMap[manifest.id] ?: (record.customDomain ?: "")

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Logo, Name, Version, Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Logo
                if (!manifest.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = manifest.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        manifest.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "v${manifest.version} • ${manifest.supportedTypes.joinToString(", ").uppercase()}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Switch Toggle (Enabled / Disabled)
                var isChecked by remember(record.manifest.id, record.isEnabled) { mutableStateOf(record.isEnabled) }
                Switch(
                    checked = isChecked,
                    onCheckedChange = { newState ->
                        isChecked = newState
                        onToggleEnabled(newState)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentRed,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Domain gốc
            Text(
                "Domain gốc",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentRed
            )
            Text(
                manifest.defaultDomain ?: "Mặc định",
                fontSize = 13.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Domain tùy chỉnh
            Text(
                "Domain tùy chỉnh",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentRed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = currentInput,
                    onValueChange = { domainInputMap[manifest.id] = it },
                    placeholder = { Text("Nhập domain mới (vd: https://phim.moinhat.com)", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = CardBorderColor
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { onSaveCustomDomain(currentInput) },
                    modifier = Modifier
                        .size(42.dp)
                        .background(AccentRed, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Lưu domain", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons row (Đăng nhập / Xóa)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onUninstall,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Xóa", fontSize = 13.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: KHO PLUGIN (REPOSITORIES & SCRAPERS)
// -------------------------------------------------------------
@Composable
private fun RepositoryStoreTab(
    repositories: List<Pair<RepositoryEntity, RepositoryManifest>>,
    installedRecords: List<InstalledPluginRecord>,
    installingMap: Map<String, Boolean>,
    onInstallScraper: (repoUrl: String, scraper: PluginManifest) -> Unit,
    onUninstallScraper: (scraperId: String) -> Unit,
    onRemoveRepo: (repoUrl: String) -> Unit,
    onRefreshRepo: (repoUrl: String) -> Unit,
    onOpenAddRepo: () -> Unit
) {
    val installedMap = remember(installedRecords) {
        installedRecords.associateBy { it.manifest.id }
    }

    if (repositories.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Chưa có kho nguồn plugin nào",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Thêm liên kết manifest.json từ GitHub hoặc server riêng để xem và cài đặt plugins.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenAddRepo,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thêm kho nguồn")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(repositories, key = { it.first.url }) { (entity, manifest) ->
                RepositorySectionCard(
                    repoEntity = entity,
                    manifest = manifest,
                    installedMap = installedMap,
                    installingMap = installingMap,
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
    installingMap: Map<String, Boolean>,
    onInstallScraper: (scraper: PluginManifest) -> Unit,
    onUninstallScraper: (scraperId: String) -> Unit,
    onRefreshRepo: () -> Unit,
    onRemoveRepo: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Repo Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = AccentRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        manifest.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        "v${manifest.version} • ${manifest.allScrapers.size} plugins",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                IconButton(onClick = onRefreshRepo) {
                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới kho", tint = Color.LightGray)
                }
                IconButton(onClick = onRemoveRepo) {
                    Icon(Icons.Default.Delete, contentDescription = "Xóa kho nguồn", tint = Color.Gray)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorderColor)

            // Scrapers list
            if (manifest.allScrapers.isEmpty()) {
                Text(
                    "Không có scraper nào trong kho này",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    manifest.allScrapers.forEach { scraper ->
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
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F0F0F),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scraper Logo
            if (!scraper.logo.isNullOrBlank()) {
                AsyncImage(
                    model = scraper.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scraper.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "v${scraper.version} • ${scraper.supportedTypes.joinToString(", ").uppercase()}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                val desc = scraper.description
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        desc,
                        fontSize = 11.sp,
                        color = Color.LightGray,
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
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Cập nhật", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else if (isInstalled) {
                OutlinedButton(
                    onClick = onUninstall,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Gỡ", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Cài đặt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
