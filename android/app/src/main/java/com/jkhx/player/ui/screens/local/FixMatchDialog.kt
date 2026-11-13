package com.jkhx.player.ui.screens.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.core.network.tmdb.TmdbConfig
import com.jkhx.player.core.network.tmdb.TmdbError
import com.jkhx.player.core.network.tmdb.TmdbItemResult
import com.jkhx.player.local.TmdbLocalScraper
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FixMatchDialog(
    initialTitle: String,
    currentType: String? = null,
    onDismiss: () -> Unit,
    onMatchSelected: (TmdbItemResult) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val (cleanedTitle, _) = remember(initialTitle) {
        TmdbLocalScraper.extractTitleAndYear(initialTitle)
    }

    var searchQuery by remember { mutableStateOf(cleanedTitle.ifBlank { initialTitle }) }
    var selectedFilter by remember {
        mutableStateOf(
            when (currentType) {
                "series", "tv" -> "tv"
                "movie" -> "movie"
                else -> null
            }
        )
    }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TmdbItemResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    fun doSearch() {
        val query = searchQuery.trim()
        if (query.isBlank()) return
        focusManager.clearFocus()
        isSearching = true
        hasSearched = true
        errorMessage = null
        coroutineScope.launch {
            val tmdbRepo = JkhxApplication.instance.tmdbRepository
            val language = TmdbConfig.getAppLanguage()
            val res = tmdbRepo.searchTmdbDetailed(query, type = selectedFilter, language = language)
            results = res.items
            errorMessage = when (val err = res.error) {
                is TmdbError.MissingApiKey -> context.getString(R.string.fix_match_error_missing_key)
                is TmdbError.InvalidApiKey -> context.getString(R.string.fix_match_error_invalid_key)
                is TmdbError.HttpError -> context.getString(R.string.fix_match_error_http, err.code)
                is TmdbError.NetworkError -> context.getString(R.string.fix_match_error_network, err.detail)
                is TmdbError.ParseError -> context.getString(R.string.fix_match_error_parse)
                null -> null
            }
            isSearching = false
        }
    }

    // Tự động tìm kiếm lần đầu khi mở dialog
    LaunchedEffect(Unit) {
        doSearch()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.fix_match_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_cancel),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.fix_match_search_hint),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { doSearch() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter chips: Tất cả / Phim lẻ / Phim bộ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        null to stringResource(R.string.fix_match_all),
                        "movie" to stringResource(R.string.fix_match_movie),
                        "tv" to stringResource(R.string.fix_match_tv)
                    )

                    filters.forEach { (typeKey, typeLabel) ->
                        val isSelected = selectedFilter == typeKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFilter = typeKey
                                doSearch()
                            },
                            label = {
                                Text(
                                    text = typeLabel,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Results list / Loading / Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isSearching) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.fix_match_searching),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    } else if (errorMessage != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (results.isEmpty() && hasSearched) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.fix_match_no_results),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(results, key = { it.id }) { item ->
                                FixMatchResultItem(
                                    item = item,
                                    onClick = { onMatchSelected(item) }
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
fun FixMatchResultItem(
    item: TmdbItemResult,
    onClick: () -> Unit
) {
    val displayTitle = item.title ?: item.name ?: "Chưa có tên"
    val originalTitle = item.originalTitle ?: item.originalName
    val year = (item.releaseDate ?: item.firstAirDate)?.take(4)
    val rating = item.voteAverage
    val posterUrl = TmdbConfig.imageUrl(item.posterPath, "w185")
    val isTv = item.mediaType == "tv" || (!item.name.isNullOrBlank() && item.title.isNullOrBlank())

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .width(55.dp)
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!originalTitle.isNullOrBlank() && originalTitle != displayTitle) {
                    Text(
                        text = originalTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Type Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = if (isTv) stringResource(R.string.fix_match_tv) else stringResource(R.string.fix_match_movie),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Year
                    if (!year.isNullOrBlank()) {
                        Text(
                            text = year,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }

                    // Rating
                    if (rating != null && rating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f", rating),
                                color = Color(0xFFFFC107),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val itemOverview = item.overview
                if (!itemOverview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = itemOverview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.CheckCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
