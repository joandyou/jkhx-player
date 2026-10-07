package com.jkhx.player.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.MovieDetail
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    providerId: String,
    slug: String,
    onBack: () -> Unit,
    onPlayEpisode: (episode: EpisodeData) -> Unit
) {
    val pluginManager = JkhxApplication.instance.pluginManager
    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var selectedServerIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(providerId, slug) {
        isLoading = true
        errorMessage = null
        try {
            val plugin = pluginManager.getPlugin(providerId)
                ?: throw Exception("Plugin [$providerId] chưa được bật hoặc chưa cài đặt")
            movieDetail = plugin.getDetail(slug)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Lỗi khi tải chi tiết phim"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(movieDetail?.name ?: "Chi Tiết Phim", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Quay Lại") }
                }
            } else if (movieDetail != null) {
                val detail = movieDetail!!
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // 1. Backdrop / Poster Header
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            AsyncImage(
                                model = detail.backdropUrl ?: detail.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            listOf(
                                                androidx.compose.ui.graphics.Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // 2. Info Section
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = detail.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (!detail.originName.isNullOrEmpty()) {
                                Text(
                                    text = detail.originName ?: "",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (detail.year != null) {
                                    AssistChip(onClick = {}, label = { Text(detail.year.toString()) })
                                }
                                if (!detail.quality.isNullOrEmpty()) {
                                    AssistChip(onClick = {}, label = { Text(detail.quality ?: "") })
                                }
                                if (!detail.language.isNullOrEmpty()) {
                                    AssistChip(onClick = {}, label = { Text(detail.language ?: "") })
                                }
                            }
                            if (!detail.description.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = detail.description ?: "",
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    // 3. Server Tabs
                    if (detail.episodes.isNotEmpty()) {
                        item {
                            Text(
                                text = "Chọn Server",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(detail.episodes) { index, server ->
                                    FilterChip(
                                        selected = selectedServerIndex == index,
                                        onClick = { selectedServerIndex = index },
                                        label = { Text(server.serverName) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 4. Episode Grid
                        val currentServer = detail.episodes.getOrNull(selectedServerIndex)
                        if (currentServer != null) {
                            item {
                                Text(
                                    text = "Danh Sách Tập (${currentServer.episodes.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            item {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    currentServer.episodes.forEach { episode ->
                                        FilledTonalButton(
                                            onClick = { onPlayEpisode(episode) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(episode.name, fontSize = 13.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(40.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
