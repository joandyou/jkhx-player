package com.jkhx.player.ui.screens.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.core.player.ExoPlayerManager
import com.jkhx.player.core.player.GenericHtml5EmbedResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(
    providerId: String,
    episode: EpisodeData,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pluginManager = JkhxApplication.instance.pluginManager
    val exoPlayerManager = remember { ExoPlayerManager(context) }
    val embedResolver = remember { GenericHtml5EmbedResolver() }

    var resolvedStream by remember { mutableStateOf<EpisodeStream?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(providerId, episode.id) {
        isLoading = true
        errorMessage = null
        try {
            val plugin = pluginManager.getPlugin(providerId)
                ?: throw Exception("Plugin [$providerId] không khả dụng")

            var stream = plugin.getStream(episode)

            // Nếu trả về embed -> bóc tách sang direct m3u8/mp4 qua EmbedResolver
            if (stream.type == "embed") {
                try {
                    stream = embedResolver.resolve(stream.url)
                } catch (e: Exception) {
                    println("[PlayerScreen] Embed resolver skipped: ${e.message}")
                }
            }

            resolvedStream = stream
            withContext(Dispatchers.Main) {
                exoPlayerManager.prepareStream(stream)
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Lỗi tải luồng phát video"
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayerManager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (resolvedStream != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayerManager.player
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Quay Lại")
                }
            }
        }

        // Back button overlay
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Thoát",
                tint = Color.White
            )
        }
    }
}
