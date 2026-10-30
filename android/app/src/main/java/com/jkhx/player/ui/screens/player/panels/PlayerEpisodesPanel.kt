package com.jkhx.player.ui.screens.player.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.core.model.EpisodeServer
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.delay

@Composable
internal fun PlayerEpisodesPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    initialFocusRequester: FocusRequester,
    movieName: String,
    activeEpisodes: List<EpisodeData>,
    currentEpisodeIndex: Int,
    availableServers: List<EpisodeServer>,
    activeServerName: String,
    onServerSelect: (EpisodeServer) -> Unit,
    onEpisodeSelect: (Int, EpisodeData) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(
                        Color.Black.copy(alpha = 0.75f),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .padding(20.dp)
            ) {
                LaunchedEffect(visible) {
                    if (visible) {
                        delay(60)
                        try {
                            initialFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header: Title + Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.player_episodes_sheet_title),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .focusRequester(initialFocusRequester)
                                .tvFocusableItem(cornerRadius = 18.dp, focusedScale = 1.12f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White.copy(alpha = 0.8f))
                        }
                    }

                    // Info row: Movie Name & Episode Count
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = movieName,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.player_episodes_count, activeEpisodes.size),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Server tabs (nếu có nhiều server trong availableServers)
                    if (availableServers.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableServers) { srv ->
                                val isSelected = srv.serverName == activeServerName
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier
                                        .tvFocusableItem(cornerRadius = 8.dp, focusedScale = 1.06f)
                                        .clickable {
                                            if (srv.serverName != activeServerName) {
                                                onServerSelect(srv)
                                            }
                                        }
                                ) {
                                    Text(
                                        text = srv.serverName,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(bottom = 12.dp))

                    // Grid danh sách các tập phim
                    val episodeListState = rememberLazyGridState()
                    LaunchedEffect(visible) {
                        if (visible && currentEpisodeIndex >= 0) {
                            try {
                                episodeListState.scrollToItem(currentEpisodeIndex.coerceAtLeast(0))
                            } catch (_: Exception) {}
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        state = episodeListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(activeEpisodes, key = { idx, ep -> "${ep.id}_$idx" }) { idx, ep ->
                            val isCurrent = idx == currentEpisodeIndex
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusableItem(cornerRadius = 10.dp, focusedScale = 1.08f)
                                    .clickable {
                                        if (idx != currentEpisodeIndex) {
                                            onEpisodeSelect(idx, ep)
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = ep.name.ifBlank { "Tập ${idx + 1}" },
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.player_episodes_playing),
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

