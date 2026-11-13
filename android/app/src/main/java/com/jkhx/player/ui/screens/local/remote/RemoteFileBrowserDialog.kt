package com.jkhx.player.ui.screens.local.remote

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.LocalLibraryEntity
import com.jkhx.player.core.database.entity.RemoteServerEntity
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.core.remote.model.RemoteFileItem
import com.jkhx.player.local.LocalComicParser
import com.jkhx.player.local.LocalFileResolver
import com.jkhx.player.local.RemoteFolderScanner
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteFileBrowserDialog(
    server: RemoteServerEntity,
    onDismiss: () -> Unit,
    onPlayEpisode: (providerId: String, movieSlug: String) -> Unit,
    onSaveEntity: (LocalLibraryEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }

    var currentPath by remember { mutableStateOf("") }
    var pathHistory by remember { mutableStateOf(listOf("")) }
    var fileItems by remember { mutableStateOf<List<RemoteFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadPath(path: String) {
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            val client = RemoteClientFactory.getClient(server)
            val result = client.listFiles(path)
            withContext(Dispatchers.Main) {
                isLoading = false
                if (result.isSuccess) {
                    val rawList = result.getOrDefault(emptyList())
                    // Phân loại: Thư mục lên trước, sắp xếp theo tên tự nhiên
                    val dirs = rawList.filter { it.isDirectory }.sortedWith { a, b ->
                        LocalFileResolver.naturalOrderComparator.compare(a.name, b.name)
                    }
                    val files = rawList.filter { !it.isDirectory }.sortedWith { a, b ->
                        LocalFileResolver.naturalOrderComparator.compare(a.name, b.name)
                    }
                    fileItems = dirs + files
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Lỗi tải tệp"
                }
            }
        }
    }

    LaunchedEffect(currentPath) {
        loadPath(currentPath)
    }

    BackHandler {
        if (pathHistory.size > 1) {
            val newHistory = pathHistory.dropLast(1)
            pathHistory = newHistory
            currentPath = newHistory.last()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxSize(0.92f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header: Tên server và Breadcrumb
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (pathHistory.size > 1) {
                                val newHistory = pathHistory.dropLast(1)
                                pathHistory = newHistory
                                currentPath = newHistory.last()
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.tvFocusableItem()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = if (pathHistory.size > 1) stringResource(R.string.common_back) else stringResource(R.string.remote_server_back_to_list),
                            tint = Color.White
                        )
                    }

                    Icon(
                        imageVector = when (server.protocol) {
                            "smb" -> Icons.Default.FolderShared
                            "sftp" -> Icons.Default.Terminal
                            else -> Icons.Default.Cloud
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val displayPath = if (currentPath.isBlank()) "/" else "/$currentPath"
                        Text(
                            text = displayPath,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Nút quay về danh sách máy chủ nhanh
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.tvFocusableItem()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = stringResource(R.string.remote_server_back_to_list),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.tvFocusableItem()) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Danh sách Files & Folders
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                        }
                    } else if (errorMessage != null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(text = errorMessage ?: "", color = Color.White, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(onClick = { loadPath(currentPath) }, modifier = Modifier.tvFocusableItem()) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                        }
                    } else if (fileItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Thư mục trống",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(fileItems, key = { it.path }) { item ->
                                val isVideo = !item.isDirectory && LocalFileResolver.isVideoFile(item.name)
                                val isImage = !item.isDirectory && LocalComicParser.isImageFile(item.name)
                                val isSub = !item.isDirectory && LocalFileResolver.isSubtitleFile(item.name)
                                val isComicArchive = !item.isDirectory && RemoteFolderScanner.isComicArchive(item.name)
                                val isNovel = !item.isDirectory && RemoteFolderScanner.isNovelFile(item.name)

                                val itemIcon = when {
                                    item.isDirectory -> Icons.Default.Folder
                                    isVideo -> Icons.Default.Movie
                                    isComicArchive -> Icons.Default.MenuBook
                                    isNovel -> Icons.Default.Book
                                    isImage -> Icons.Default.Image
                                    isSub -> Icons.Default.Subtitles
                                    else -> Icons.Default.InsertDriveFile
                                }

                                val iconTint = when {
                                    item.isDirectory -> Color(0xFFFFCA28)
                                    isVideo -> MaterialTheme.colorScheme.primary
                                    isComicArchive -> Color(0xFFFF7043)
                                    isNovel -> Color(0xFF42A5F5)
                                    isImage -> Color(0xFF26A69A)
                                    isSub -> Color(0xFFAB47BC)
                                    else -> Color.White.copy(alpha = 0.5f)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableItem(cornerRadius = 8.dp)
                                        .clickable {
                                            if (isImporting) return@clickable
                                            if (item.isDirectory) {
                                                pathHistory = pathHistory + item.path
                                                currentPath = item.path
                                            } else if (isVideo) {
                                                // Phát video trực tiếp
                                                val hashId = "remote_${server.id}_${item.path.hashCode()}"
                                                val epDataJson = buildString {
                                                    append("{\"type\":\"remote\",")
                                                    append("\"protocol\":\"").append(server.protocol).append("\",")
                                                    append("\"serverId\":\"").append(server.id).append("\",")
                                                    append("\"path\":\"").append(item.path).append("\"}")
                                                }
                                                val singleEpisode = EpisodeData(
                                                    id = "ep_1",
                                                    name = item.name.substringBeforeLast('.'),
                                                    slug = "ep_1",
                                                    number = 1,
                                                    data = Json.parseToJsonElement(epDataJson)
                                                )
                                                val entity = LocalLibraryEntity(
                                                    id = hashId,
                                                    title = item.name.substringBeforeLast('.'),
                                                    type = "movie",
                                                    uri = "remote://${server.id}/${item.path}",
                                                    chapterCount = 1,
                                                    episodesJson = json.encodeToString(listOf(singleEpisode))
                                                )
                                                onSaveEntity(entity)
                                                onDismiss()
                                                onPlayEpisode("local", hashId)
                                            } else if (isComicArchive || isNovel) {
                                                isImporting = true
                                                scope.launch(Dispatchers.IO) {
                                                    val importResult = RemoteFolderScanner.importSingleRemoteFile(
                                                        context = context,
                                                        server = server,
                                                        item = item
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        isImporting = false
                                                        if (importResult.isSuccess) {
                                                            val entity = importResult.getOrThrow()
                                                            onSaveEntity(entity)
                                                            onDismiss()
                                                            onPlayEpisode("local", entity.id)
                                                        } else {
                                                            val msg = importResult.exceptionOrNull()?.message ?: "Không thể mở tệp"
                                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = itemIcon,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontSize = 14.sp,
                                                fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!item.isDirectory && item.size > 0L) {
                                                Text(
                                                    text = formatFileSize(item.size),
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                        if (item.isDirectory) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Thanh công cụ dưới: Nạp thư mục này vào Thư viện
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${fileItems.size} mục",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Button(
                        onClick = {
                            isImporting = true
                            scope.launch(Dispatchers.IO) {
                                val scanResult = RemoteFolderScanner.scanRemoteFolder(context, server, currentPath)
                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    if (scanResult.isSuccess) {
                                        val res = scanResult.getOrThrow()
                                        val summaryParts = mutableListOf<String>()

                                        if (res.episodes.isNotEmpty()) {
                                            val isSeries = LocalFileResolver.isSeriesFolder(res.episodes)
                                            if (isSeries) {
                                                val hashId = "remote_${server.id}_${currentPath.hashCode()}"
                                                val entity = LocalLibraryEntity(
                                                    id = hashId,
                                                    title = res.folderName,
                                                    type = "series",
                                                    uri = "remote://${server.id}/$currentPath",
                                                    coverUrl = res.coverUrl,
                                                    chapterCount = res.episodes.size,
                                                    episodesJson = json.encodeToString(res.episodes)
                                                )
                                                onSaveEntity(entity)
                                                summaryParts.add("${res.folderName} (${res.episodes.size} tập)")
                                            } else {
                                                for (ep in res.episodes) {
                                                    val movieHashId = "remote_${server.id}_${currentPath.hashCode()}_${ep.name.hashCode()}"
                                                    val movieEntity = LocalLibraryEntity(
                                                        id = movieHashId,
                                                        title = LocalFileResolver.cleanMovieTitle(ep.name),
                                                        type = "movie",
                                                        uri = "remote://${server.id}/$currentPath",
                                                        coverUrl = res.coverUrl,
                                                        chapterCount = 1,
                                                        episodesJson = json.encodeToString(listOf(ep.copy(name = ep.name)))
                                                    )
                                                    onSaveEntity(movieEntity)
                                                }
                                                summaryParts.add("${res.episodes.size} phim lẻ")
                                            }
                                        }

                                        for (comic in res.comics) {
                                            onSaveEntity(comic)
                                        }
                                        if (res.comics.isNotEmpty()) {
                                            summaryParts.add("${res.comics.size} truyện tranh")
                                        }

                                        for (novel in res.novels) {
                                            onSaveEntity(novel)
                                        }
                                        if (res.novels.isNotEmpty()) {
                                            summaryParts.add("${res.novels.size} tiểu thuyết")
                                        }

                                        Toast.makeText(
                                            context,
                                            "Đã nạp thành công: ${summaryParts.joinToString(", ")}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onDismiss()
                                    } else {
                                        val msg = scanResult.exceptionOrNull()?.message ?: "Lỗi quét thư mục"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isImporting && !isLoading,
                        modifier = Modifier.tvFocusableItem()
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.remote_browser_scanning))
                        } else {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.remote_browser_add_folder))
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
