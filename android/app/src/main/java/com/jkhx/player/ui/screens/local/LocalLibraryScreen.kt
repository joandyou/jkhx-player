package com.jkhx.player.ui.screens.local

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.LocalLibraryEntity
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.local.LocalComicParser
import com.jkhx.player.local.LocalFileResolver
import com.jkhx.player.local.LocalNovelParser
import com.jkhx.player.local.TmdbLocalScraper
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLibraryScreen(
    onBack: () -> Unit,
    onMovieClick: (providerId: String, slug: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localLibraryDao = remember { JkhxApplication.instance.database.localLibraryDao() }

    val allItems by localLibraryDao.getAll().collectAsState(initial = emptyList())
    var selectedTab by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<LocalLibraryEntity?>(null) }
    var selectedItemForMenu by remember { mutableStateOf<LocalLibraryEntity?>(null) }
    var itemForFixMatch by remember { mutableStateOf<LocalLibraryEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showRemoteServersDialog by remember { mutableStateOf(false) }

    val json = remember { Json { ignoreUnknownKeys = true } }

    fun saveLocalEntity(entity: LocalLibraryEntity) {
        scope.launch(Dispatchers.IO) {
            localLibraryDao.insert(entity)
            if (entity.type == "movie" || entity.type == "series") {
                TmdbLocalScraper.scrapeAndEnrich(context, entity.id, entity.title, entity.type)
            }
            withContext(Dispatchers.Main) {
                isProcessing = false
                Toast.makeText(
                    context,
                    context.getString(R.string.local_library_add_success, entity.title),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 1. Video file launcher
    val videoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            processingMessage = context.getString(R.string.local_library_parsing)
            scope.launch(Dispatchers.IO) {
                LocalFileResolver.takePersistablePermission(context, uri)
                val fileName = LocalFileResolver.getFileName(context, uri)
                val hashId = LocalFileResolver.hashUri(uri)
                val episode = LocalFileResolver.createSingleVideoEpisode(fileName, uri)
                val entity = LocalLibraryEntity(
                    id = hashId,
                    title = fileName.substringBeforeLast('.'),
                    type = "movie",
                    uri = uri.toString(),
                    chapterCount = 1,
                    episodesJson = json.encodeToString(listOf(episode))
                )
                saveLocalEntity(entity)
            }
        }
    }

    fun importFolder(uri: Uri, forceSplitMovies: Boolean = false) {
        isProcessing = true
        processingMessage = context.getString(R.string.local_library_parsing)
        scope.launch(Dispatchers.IO) {
            LocalFileResolver.takePersistablePermission(context, uri)
            val analysis = LocalFileResolver.analyzeFolder(context, uri)
            val hashId = LocalFileResolver.hashUri(uri)
            var importedAny = false
            val addedTitles = mutableListOf<String>()

            // 1. Phim / Video
            if (analysis.videoEpisodes.isNotEmpty()) {
                val isSeries = !forceSplitMovies && LocalFileResolver.isSeriesFolder(analysis.videoEpisodes)
                if (isSeries) {
                    val videoEntity = LocalLibraryEntity(
                        id = hashId,
                        title = analysis.folderName,
                        type = "series",
                        uri = uri.toString(),
                        coverUrl = analysis.coverUrl,
                        chapterCount = analysis.videoEpisodes.size,
                        episodesJson = json.encodeToString(analysis.videoEpisodes)
                    )
                    localLibraryDao.insert(videoEntity)
                    scope.launch(Dispatchers.IO) {
                        TmdbLocalScraper.scrapeAndEnrich(context, videoEntity.id, videoEntity.title, "series")
                    }
                    importedAny = true
                    val epCountStr = context.getString(R.string.local_library_episodes_count, analysis.videoEpisodes.size)
                    addedTitles.add("${analysis.folderName} ($epCountStr)")
                } else {
                    for (ep in analysis.videoEpisodes) {
                        val itemHash = "mov_${hashId}_${ep.name.hashCode()}"
                        val movieEntity = LocalLibraryEntity(
                            id = itemHash,
                            title = LocalFileResolver.cleanMovieTitle(ep.name),
                            type = "movie",
                            uri = uri.toString(),
                            coverUrl = analysis.coverUrl,
                            chapterCount = 1,
                            episodesJson = json.encodeToString(listOf(ep.copy(name = ep.name)))
                        )
                        localLibraryDao.insert(movieEntity)
                        scope.launch(Dispatchers.IO) {
                            TmdbLocalScraper.scrapeAndEnrich(context, movieEntity.id, movieEntity.title, "movie")
                        }
                        importedAny = true
                        addedTitles.add(movieEntity.title)
                    }
                }
            }

            // 2. Truyện tranh (ảnh)
            if (analysis.comicMetadata != null && analysis.comicMetadata.chapters.isNotEmpty()) {
                val comicId = if (analysis.videoEpisodes.isNotEmpty()) "${hashId}_comic" else hashId
                val comicEntity = LocalLibraryEntity(
                    id = comicId,
                    title = analysis.comicMetadata.title,
                    type = "comic",
                    uri = uri.toString(),
                    coverUrl = analysis.comicMetadata.coverUrl ?: analysis.coverUrl,
                    chapterCount = analysis.comicMetadata.chapters.size,
                    episodesJson = json.encodeToString(analysis.comicMetadata.chapters)
                )
                localLibraryDao.insert(comicEntity)
                importedAny = true
                val chapCountStr = context.getString(R.string.local_library_chapters_count, analysis.comicMetadata.chapters.size)
                addedTitles.add("${analysis.comicMetadata.title} ($chapCountStr)")
            }

            // 3. Truyện chữ (.epub, .txt)
            if (analysis.novelFiles.isNotEmpty()) {
                for (novelFile in analysis.novelFiles) {
                    val nUri = novelFile.uri
                    val nName = novelFile.name ?: "Truyện chữ"
                    val nHash = LocalFileResolver.hashUri(nUri)
                    val ext = nName.substringAfterLast('.', "").lowercase()
                    val meta = if (ext == "epub") {
                        LocalNovelParser.parseEpubNovel(context, nUri, nName, nHash)
                    } else {
                        LocalNovelParser.parseTxtNovel(context, nUri, nName)
                    }
                    if (meta.chapters.isNotEmpty()) {
                        val novelEntity = LocalLibraryEntity(
                            id = nHash,
                            title = meta.title,
                            type = "novel",
                            uri = nUri.toString(),
                            author = meta.author,
                            description = meta.description,
                            coverUrl = meta.coverUrl,
                            chapterCount = meta.chapters.size,
                            episodesJson = json.encodeToString(meta.chapters)
                        )
                        localLibraryDao.insert(novelEntity)
                        importedAny = true
                        addedTitles.add(meta.title)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                isProcessing = false
                if (importedAny) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.local_library_add_success, addedTitles.joinToString(", ")),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.local_library_no_media_found,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // 2. Video folder launcher (Phim bộ / Series)
    val videoFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importFolder(uri, forceSplitMovies = false)
        }
    }

    // 2b. Video movies folder launcher (Phim lẻ bóc tách riêng)
    val videoMoviesFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importFolder(uri, forceSplitMovies = true)
        }
    }

    // 3. Novel file launcher (.txt, .epub)
    val novelFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            processingMessage = context.getString(R.string.local_library_parsing)
            scope.launch(Dispatchers.IO) {
                LocalFileResolver.takePersistablePermission(context, uri)
                val fileName = LocalFileResolver.getFileName(context, uri)
                val hashId = LocalFileResolver.hashUri(uri)
                val ext = fileName.substringAfterLast('.', "").lowercase()

                val meta = if (ext == "epub") {
                    LocalNovelParser.parseEpubNovel(context, uri, fileName, hashId)
                } else {
                    LocalNovelParser.parseTxtNovel(context, uri, fileName)
                }

                val entity = LocalLibraryEntity(
                    id = hashId,
                    title = meta.title,
                    type = "novel",
                    uri = uri.toString(),
                    author = meta.author,
                    description = meta.description,
                    coverUrl = meta.coverUrl,
                    chapterCount = meta.chapters.size,
                    episodesJson = json.encodeToString(meta.chapters)
                )
                saveLocalEntity(entity)
            }
        }
    }

    // 4. Comic file launcher (.cbz, .zip)
    val comicFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            processingMessage = context.getString(R.string.local_library_parsing)
            scope.launch(Dispatchers.IO) {
                LocalFileResolver.takePersistablePermission(context, uri)
                val fileName = LocalFileResolver.getFileName(context, uri)
                val hashId = LocalFileResolver.hashUri(uri)
                val meta = LocalComicParser.parseZipComic(context, uri, fileName, hashId)

                val entity = LocalLibraryEntity(
                    id = hashId,
                    title = meta.title,
                    type = "comic",
                    uri = uri.toString(),
                    coverUrl = meta.coverUrl,
                    chapterCount = meta.chapters.size,
                    episodesJson = json.encodeToString(meta.chapters)
                )
                saveLocalEntity(entity)
            }
        }
    }

    // 5. Comic folder launcher
    val comicFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importFolder(uri)
        }
    }

    // 6. Smart folder launcher
    val smartFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importFolder(uri)
        }
    }

    val filteredItems = remember(allItems, selectedTab, searchQuery) {
        var list = when (selectedTab) {
            "movie" -> allItems.filter { it.type == "movie" || it.type == "series" }
            "novel" -> allItems.filter { it.type == "novel" }
            "comic" -> allItems.filter { it.type == "comic" }
            else -> allItems
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) || (it.author?.lowercase()?.contains(q) == true)
            }
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.tvFocusableItem()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.local_library_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (allItems.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.tvFocusableItem()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.local_library_clear_all),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                            )
                        }
                    }

                    IconButton(
                        onClick = { isSearching = !isSearching },
                        modifier = Modifier.tvFocusableItem()
                    ) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }

                    Box {
                        FilledTonalButton(
                            onClick = { showAddMenu = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.tvFocusableItem()
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.common_add), fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.local_library_video_single)) },
                                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    videoFileLauncher.launch(arrayOf("video/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.local_library_video_folder_series)) },
                                leadingIcon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    videoFolderLauncher.launch(null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.local_library_video_folder_movies)) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    videoMoviesFolderLauncher.launch(null)
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.local_library_novel_txt_epub)) },
                                leadingIcon = { Icon(Icons.Default.Book, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    novelFileLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.local_library_comic_cbz_zip)) },
                                leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    comicFileLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.local_library_comic_folder)) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    comicFolderLauncher.launch(null)
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.local_library_smart_folder)) },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    smartFolderLauncher.launch(null)
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remote_menu_item)) },
                                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showAddMenu = false
                                    showRemoteServersDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Search Bar
            if (isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.local_library_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = ShapeTokens.Card,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )
            }

            // Tab Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    "all" to stringResource(R.string.local_library_tab_all),
                    "movie" to stringResource(R.string.local_library_tab_movie),
                    "novel" to stringResource(R.string.local_library_tab_novel),
                    "comic" to stringResource(R.string.local_library_tab_comic)
                )

                tabs.forEach { (tabKey, tabLabel) ->
                    val isSelected = selectedTab == tabKey
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.05f)
                            .clickable { selectedTab = tabKey }
                    ) {
                        Text(
                            text = tabLabel,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Content Grid
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.local_library_empty),
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        LocalMediaCard(
                            item = item,
                            onClick = {
                                onMovieClick("local", item.id)
                            },
                            onLongClick = {
                                selectedItemForMenu = item
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }

        // Processing Overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = ShapeTokens.Card,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = processingMessage, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // Context Action Menu Dialog (khi nhấn giữ vào phim)
        selectedItemForMenu?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedItemForMenu = null },
                title = {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.type == "movie" || item.type == "series") {
                            // Fix Match
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val target = item
                                        selectedItemForMenu = null
                                        itemForFixMatch = target
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.fix_match_title),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }

                            // Auto Re-scrape
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val target = item
                                        selectedItemForMenu = null
                                        scope.launch(Dispatchers.IO) {
                                            val ok = TmdbLocalScraper.scrapeAndEnrich(context, target.id, target.title, target.type)
                                            withContext(Dispatchers.Main) {
                                                val msg = if (ok) {
                                                    context.getString(R.string.local_library_rescrape_success)
                                                } else {
                                                    context.getString(R.string.fix_match_no_results)
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.local_library_rescrape),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Xóa
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val target = item
                                    selectedItemForMenu = null
                                    itemToDelete = target
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.common_delete),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedItemForMenu = null }) {
                        Text(stringResource(R.string.common_cancel), color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Fix Match TMDb Dialog
        itemForFixMatch?.let { targetItem ->
            FixMatchDialog(
                initialTitle = targetItem.title,
                currentType = targetItem.type,
                onDismiss = { itemForFixMatch = null },
                onMatchSelected = { matchResult ->
                    scope.launch(Dispatchers.IO) {
                        val success = TmdbLocalScraper.applyTmdbMatch(
                            context = context,
                            entityId = targetItem.id,
                            result = matchResult,
                            type = targetItem.type
                        )
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, R.string.fix_match_apply_success, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    itemForFixMatch = null
                }
            )
        }

        // Delete Confirmation Dialog
        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text(stringResource(R.string.local_library_delete_confirm)) },
                text = { Text(stringResource(R.string.local_library_delete_msg)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = item.id
                            itemToDelete = null
                            scope.launch(Dispatchers.IO) {
                                localLibraryDao.delete(id)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.local_library_item_deleted), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text(stringResource(R.string.common_cancel), color = Color.White)
                    }
                }
            )
        }

        // Bulk Clear Dialog
        if (showClearDialog) {
            val hasMovies = allItems.any { it.type == "movie" || it.type == "series" }
            val hasNovels = allItems.any { it.type == "novel" }
            val hasComics = allItems.any { it.type == "comic" }

            val message = when (selectedTab) {
                "movie" -> stringResource(R.string.local_library_clear_movies_msg)
                "novel" -> stringResource(R.string.local_library_clear_novels_msg)
                "comic" -> stringResource(R.string.local_library_clear_comics_msg)
                else -> {
                    if (hasMovies && !hasNovels && !hasComics) {
                        stringResource(R.string.local_library_clear_movies_msg)
                    } else {
                        stringResource(R.string.local_library_clear_everything_msg)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text(stringResource(R.string.local_library_clear_confirm_title)) },
                text = {
                    Text(text = message)
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Nếu đang ở tab All và có cả phim lẫn truyện, cho phép tùy chọn xóa riêng phim
                        if (selectedTab == "all" && hasMovies && (hasNovels || hasComics)) {
                            TextButton(
                                onClick = {
                                    showClearDialog = false
                                    scope.launch(Dispatchers.IO) {
                                        localLibraryDao.deleteAllVideos()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, R.string.local_library_clear_success, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.local_library_clear_action_movies),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                showClearDialog = false
                                scope.launch(Dispatchers.IO) {
                                    when (selectedTab) {
                                        "movie" -> localLibraryDao.deleteAllVideos()
                                        "novel" -> localLibraryDao.deleteByType("novel")
                                        "comic" -> localLibraryDao.deleteByType("comic")
                                        else -> localLibraryDao.clearAll()
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, R.string.local_library_clear_success, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            val btnText = if (selectedTab == "movie") {
                                stringResource(R.string.local_library_clear_action_movies)
                            } else {
                                stringResource(R.string.local_library_clear_action_all)
                            }
                            Text(
                                text = btnText,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(R.string.common_cancel), color = Color.White)
                    }
                }
            )
        }

        // Remote Servers Dialog
        if (showRemoteServersDialog) {
            com.jkhx.player.ui.screens.local.remote.RemoteServersDialog(
                onDismiss = { showRemoteServersDialog = false },
                onPlayEpisode = { providerId, movieSlug ->
                    onMovieClick(providerId, movieSlug)
                },
                onSaveEntity = { entity ->
                    scope.launch(Dispatchers.IO) {
                        localLibraryDao.insert(entity)
                        if (entity.type == "movie" || entity.type == "series") {
                            TmdbLocalScraper.scrapeAndEnrich(context, entity.id, entity.title, entity.type)
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalMediaCard(
    item: LocalLibraryEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val typeIcon = when (item.type) {
        "novel" -> Icons.Default.Book
        "comic" -> Icons.Default.Collections
        else -> Icons.Default.Movie
    }

    val typeLabel = when (item.type) {
        "novel" -> stringResource(R.string.local_library_tab_novel)
        "comic" -> stringResource(R.string.local_library_tab_comic)
        else -> stringResource(R.string.local_library_tab_movie)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.05f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.71f)
                .clip(ShapeTokens.Card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2C3E50), Color(0xFF000000))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Type Badge Top-Left
            Surface(
                shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = typeLabel,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            // Delete Action Button Top-Right
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Rating Badge Bottom-Left (từ TMDb)
            val itemRating = item.rating
            if (itemRating != null && itemRating > 0.0) {
                Surface(
                    shape = RoundedCornerShape(topEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", itemRating),
                            color = Color(0xFFFFC107),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Episode / Chapter Count Bottom-Right
            if (item.chapterCount > 1) {
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    val countText = if (item.type == "novel" || item.type == "comic") {
                        stringResource(R.string.local_library_chapters_count, item.chapterCount)
                    } else {
                        stringResource(R.string.local_library_episodes_count, item.chapterCount)
                    }
                    Text(
                        text = countText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val itemYear = item.year
        if (!itemYear.isNullOrBlank()) {
            Text(
                text = itemYear,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}
