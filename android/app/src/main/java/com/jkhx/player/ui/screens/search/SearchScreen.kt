package com.jkhx.player.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.ui.screens.home.MovieCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onMovieClick: (providerId: String, slug: String) -> Unit
) {
    val pluginManager = JkhxApplication.instance.pluginManager
    var query by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<MovieItem>() }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun triggerSearch(keyword: String) {
        searchJob?.cancel()
        if (keyword.isBlank()) {
            searchResults.clear()
            isSearching = false
            return
        }

        searchJob = coroutineScope.launch {
            delay(400) // debounce
            searchResults.clear()
            isSearching = true
            pluginManager.searchAll(keyword).collect { items ->
                searchResults.addAll(items)
            }
            isSearching = false
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        triggerSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tìm kiếm phim trên toàn bộ plugins...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                triggerSearch("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isSearching && searchResults.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (searchResults.isEmpty() && query.isNotBlank() && !isSearching) {
                Text(
                    text = "Không tìm thấy kết quả nào cho '$query'",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults) { movie ->
                        MovieCard(movie = movie, onClick = {
                            onMovieClick(movie.providerId ?: "unknown", movie.slug)
                        })
                    }
                }
            }
        }
    }
}
