package com.jkhx.player.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeData
import com.jkhx.player.ui.theme.tvFocusableItem
import kotlinx.coroutines.launch

/**
 * Thứ tự sắp xếp danh sách tập phim
 */
enum class EpisodeSortOrder {
    FirstToLast,
    LastToFirst;

    fun labelRes(): Int = when (this) {
        FirstToLast -> R.string.episode_navigator_sort_first_to_last
        LastToFirst -> R.string.episode_navigator_sort_last_to_first
    }
}

/**
 * Kiểm tra xem tập phim có khớp với chuỗi tìm kiếm không
 * Hỗ trợ khớp theo số tập, tiêu đề tập hoặc nội dung tóm tắt
 */
fun EpisodeData.matchesEpisodeQuery(query: String, defaultIndex: Int): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true

    val numberQuery = trimmed.toIntOrNull()
    if (numberQuery != null) {
        val epNum = this.number ?: (defaultIndex + 1)
        if (epNum == numberQuery) return true
    }

    if (this.name.contains(trimmed, ignoreCase = true)) return true
    if (this.overview?.contains(trimmed, ignoreCase = true) == true) return true

    // Khớp mẫu "01-03", "tập 12", v.v.
    val rawNameDigits = this.name.filter { it.isDigit() }
    val queryDigits = trimmed.filter { it.isDigit() }
    if (queryDigits.isNotEmpty() && rawNameDigits.contains(queryDigits)) return true

    return false
}

/**
 * Thanh điều hướng và tìm kiếm tập phim chuẩn Nuvio
 */
@Composable
fun EpisodeNavigatorBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortOrder: EpisodeSortOrder,
    onSortOrderChange: (EpisodeSortOrder) -> Unit,
    shownCount: Int,
    totalCount: Int,
    seasonLabel: String,
    episodes: List<EpisodeData>,
    isWatched: (EpisodeData) -> Boolean,
    upNextEpisodeId: String?,
    onGoToEpisode: (EpisodeData) -> Unit,
    modifier: Modifier = Modifier
) {
    var showGoToSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    val fieldShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hàng 1: Ô tìm kiếm + Nút "Đi đến" + Nút "Sắp xếp"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ô tìm kiếm dạng Pill bo tròn 14dp
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(fieldShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), fieldShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.episode_navigator_search_hint),
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.5.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.episode_navigator_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }

            // Nút "Đi đến" (Go to)
            EpisodeNavigatorChip(
                text = stringResource(R.string.episode_navigator_go_to),
                onClick = { showGoToSheet = true }
            )

            // Nút "Sắp xếp" (Sort)
            EpisodeNavigatorChip(
                text = stringResource(R.string.episode_navigator_sort),
                onClick = { showSortSheet = true }
            )
        }

        // Hàng 2: Số lượng tập (X / Y tập) & Thứ tự sắp xếp hiện tại
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.episode_navigator_count, shownCount, totalCount),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(sortOrder.labelRes()),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Modal BottomSheet "Đi đến tập phim"
    if (showGoToSheet) {
        EpisodeGoToSheet(
            seasonLabel = seasonLabel,
            episodes = episodes,
            isWatched = isWatched,
            upNextEpisodeId = upNextEpisodeId,
            onPick = { episode ->
                onGoToEpisode(episode)
                showGoToSheet = false
            },
            onDismiss = { showGoToSheet = false }
        )
    }

    // Modal BottomSheet "Sắp xếp tập phim"
    if (showSortSheet) {
        EpisodeSortSheet(
            current = sortOrder,
            onSelect = { newOrder ->
                onSortOrderChange(newOrder)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }
}

/**
 * Chip nút bấm bo góc 14dp chuẩn Nuvio
 */
@Composable
private fun EpisodeNavigatorChip(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .tvFocusableItem(cornerRadius = 14.dp, focusedScale = 1.05f)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Giao diện khi tìm kiếm không có tập nào khớp
 */
@Composable
fun EpisodeNavigatorEmptyState(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = stringResource(R.string.episode_navigator_no_match, query.trim()),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(
            onClick = onClear,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Text(
                text = stringResource(R.string.episode_navigator_clear_search),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * BottomSheet "Đi đến tập phim" với bàn phím số và danh sách chọn nhanh
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeGoToSheet(
    seasonLabel: String,
    episodes: List<EpisodeData>,
    isWatched: (EpisodeData) -> Boolean,
    upNextEpisodeId: String?,
    onPick: (EpisodeData) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var outOfRange by remember { mutableStateOf(false) }

    val firstNumber = remember(episodes) {
        episodes.mapNotNull { it.number }.minOrNull() ?: 1
    }
    val lastNumber = remember(episodes) {
        episodes.mapNotNull { it.number }.maxOrNull() ?: episodes.size
    }

    fun close(then: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            then()
        }
    }

    fun submit() {
        val number = input.trim().toIntOrNull() ?: return
        val target = episodes.firstOrNull { it.number == number }
            ?: episodes.getOrNull(number - 1)
        if (target == null) {
            outOfRange = true
        } else {
            close { onPick(target) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = sheetState,
        containerColor = Color(0xFF14171F).copy(alpha = 0.98f),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(Modifier.size(width = 38.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.episode_navigator_go_to_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Ô nhập số tập + Nút "Đi"
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val fieldShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(fieldShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), fieldShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(R.string.episode_navigator_episode_number),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { value ->
                            input = value.filter(Char::isDigit).take(5)
                            outOfRange = false
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = { submit() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.episode_navigator_go),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Gợi ý hoặc thông báo ngoài phạm vi
            Text(
                text = if (outOfRange) {
                    stringResource(R.string.episode_navigator_out_of_range, seasonLabel, firstNumber, lastNumber)
                } else {
                    stringResource(R.string.episode_navigator_go_to_help)
                },
                fontSize = 12.sp,
                color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Lưới các ô số tập để chọn nhanh 1 chạm
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                episodes.forEachIndexed { index, episode ->
                    val watched = isWatched(episode)
                    val upNext = episode.id == upNextEpisodeId
                    val shape = RoundedCornerShape(12.dp)
                    val epNumber = episode.number ?: (index + 1)

                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(44.dp)
                            .clip(shape)
                            .background(
                                if (upNext) primaryColor.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .border(
                                width = if (upNext) 1.8.dp else 1.dp,
                                color = if (upNext) primaryColor else Color.White.copy(alpha = 0.12f),
                                shape = shape
                            )
                            .clickable { close { onPick(episode) } }
                            .drawBehind {
                                if (watched) {
                                    val y = size.height - 5.dp.toPx()
                                    drawLine(
                                        color = primaryColor,
                                        start = Offset(size.width * 0.25f, y),
                                        end = Offset(size.width * 0.75f, y),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = epNumber.toString(),
                            fontSize = 13.5.sp,
                            fontWeight = if (upNext) FontWeight.Bold else FontWeight.Medium,
                            color = if (watched) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * BottomSheet "Sắp xếp tập phim"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSortSheet(
    current: EpisodeSortOrder,
    onSelect: (EpisodeSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14171F).copy(alpha = 0.98f),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(Modifier.size(width = 38.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.episode_navigator_sort_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            EpisodeSortOrder.entries.forEach { order ->
                val isSelected = order == current
                Surface(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onSelect(order)
                        }
                    },
                    color = if (isSelected) Color.White.copy(alpha = 0.07f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(order.labelRes()),
                            fontSize = 14.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
