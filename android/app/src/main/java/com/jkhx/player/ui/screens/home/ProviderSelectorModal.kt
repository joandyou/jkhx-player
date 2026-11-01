package com.jkhx.player.ui.screens.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jkhx.player.R
import com.jkhx.player.plugin.manager.InstalledPluginRecord
import com.jkhx.player.ui.theme.PluginCategoryAdult
import com.jkhx.player.ui.theme.PluginCategoryComic
import com.jkhx.player.ui.theme.PluginCategoryLive
import com.jkhx.player.ui.theme.PluginCategoryMovie
import com.jkhx.player.ui.theme.PluginCategoryNovel
import com.jkhx.player.ui.theme.tvFocusableItem

/**
 * Các Tab lọc danh mục nguồn nội dung
 */
private enum class ProviderCategoryTab {
    ALL,
    MOVIE,
    STREMIO,
    COMIC_NOVEL,
    LIVE,
    ADULT;

    fun matches(categoryType: String?): Boolean {
        val norm = categoryType?.lowercase()?.trim() ?: "movie"
        return when (this) {
            ALL -> true
            MOVIE -> norm == "movie" || norm.isBlank()
            STREMIO -> norm == "stremio"
            COMIC_NOVEL -> norm == "comic" || norm == "novel"
            LIVE -> norm == "live"
            ADULT -> norm == "adult"
        }
    }

    @Composable
    fun getLabel(): String {
        return when (this) {
            ALL -> stringResource(R.string.home_provider_tab_all)
            MOVIE -> stringResource(R.string.home_tag_movie_badge)
            STREMIO -> stringResource(R.string.plugins_stremio_badge)
            COMIC_NOVEL -> stringResource(R.string.home_tag_comic)
            LIVE -> stringResource(R.string.home_tag_live_badge)
            ADULT -> stringResource(R.string.home_tag_adult)
        }
    }
}

/**
 * Bảng chọn Provider nguồn nội dung Responsive (Modal Bottom Sheet trên Mobile, Centered Dialog trên TV / Màn hình ngang)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectorModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    enabledPlugins: List<InstalledPluginRecord>,
    selectedProviderId: String?,
    onSelectProvider: (InstalledPluginRecord) -> Unit,
    onOpenPluginsClick: () -> Unit
) {
    if (!visible) return

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isWideScreen) {
        // Giao diện Android TV / Tablet Landscape: Hộp thoại kính mờ trung tâm tối ưu Remote D-Pad
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 560.dp, max = 840.dp)
                        .heightIn(min = 420.dp, max = 580.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF121620).copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    shadowElevation = 18.dp
                ) {
                    ProviderSelectorContent(
                        isWideScreen = true,
                        enabledPlugins = enabledPlugins,
                        selectedProviderId = selectedProviderId,
                        onSelectProvider = { record ->
                            onSelectProvider(record)
                            onDismissRequest()
                        },
                        onOpenPluginsClick = {
                            onDismissRequest()
                            onOpenPluginsClick()
                        },
                        onClose = onDismissRequest
                    )
                }
            }
        }
    } else {
        // Giao diện Điện thoại Portrait: Modal Bottom Sheet kính mờ vuốt từ đáy màn hình
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF14171F).copy(alpha = 0.96f),
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
            ProviderSelectorContent(
                isWideScreen = false,
                enabledPlugins = enabledPlugins,
                selectedProviderId = selectedProviderId,
                onSelectProvider = { record ->
                    onSelectProvider(record)
                    onDismissRequest()
                },
                onOpenPluginsClick = {
                    onDismissRequest()
                    onOpenPluginsClick()
                },
                onClose = onDismissRequest
            )
        }
    }
}

/**
 * Nội dung bảng chọn Provider dùng chung
 */
@Composable
private fun ProviderSelectorContent(
    isWideScreen: Boolean,
    enabledPlugins: List<InstalledPluginRecord>,
    selectedProviderId: String?,
    onSelectProvider: (InstalledPluginRecord) -> Unit,
    onOpenPluginsClick: () -> Unit,
    onClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ProviderCategoryTab.ALL) }

    val availableTabs = remember(enabledPlugins) {
        ProviderCategoryTab.values().filter { tab ->
            tab == ProviderCategoryTab.ALL || enabledPlugins.any { tab.matches(it.manifest.categoryType) }
        }
    }

    val filteredPlugins = remember(enabledPlugins, selectedTab, searchQuery) {
        enabledPlugins.filter { record ->
            val matchTab = selectedTab.matches(record.manifest.categoryType)
            val matchSearch = searchQuery.isBlank() ||
                    record.manifest.name.contains(searchQuery.trim(), ignoreCase = true) ||
                    record.manifest.id.contains(searchQuery.trim(), ignoreCase = true)
            matchTab && matchSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isWideScreen) 24.dp else 16.dp)
            .padding(bottom = if (isWideScreen) 16.dp else 24.dp)
    ) {
        // 1. Header Bar: Tiêu đề + Đóng (cho TV)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_provider_modal_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isWideScreen) 18.sp else 16.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "${filteredPlugins.size}/${enabledPlugins.size} nguồn sẵn sàng",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.5.sp
                )
            }

            if (isWideScreen) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.15f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        // 2. Ô tìm kiếm nhanh (Tối ưu cho Mobile, TV có thể ẩn bớt bàn phím ảo phiền phức)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.home_search_provider_placeholder),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1F2C).copy(alpha = 0.6f),
                unfocusedContainerColor = Color(0xFF161A24).copy(alpha = 0.4f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f)
        )

        // 3. Dải Tab phân loại danh mục cuộn ngang (Category Pills - D-Pad Trái/Phải di chuyển cực mượt)
        if (availableTabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableTabs.forEach { tab ->
                    val isTabActive = tab == selectedTab
                    val tabCount = if (tab == ProviderCategoryTab.ALL) {
                        enabledPlugins.size
                    } else {
                        enabledPlugins.count { tab.matches(it.manifest.categoryType) }
                    }

                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isTabActive) MaterialTheme.colorScheme.primaryContainer else Color(0xFF1E2433).copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (isTabActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.08f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab.getLabel(),
                                fontSize = 12.sp,
                                fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTabActive) MaterialTheme.colorScheme.onPrimaryContainer else Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (isTabActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = tabCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTabActive) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Lưới Card Provider (2 cột Mobile, 3 cột TV)
        if (filteredPlugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.home_provider_empty_filter),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isWideScreen) 3 else 2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = if (isWideScreen) 340.dp else 420.dp)
            ) {
                items(filteredPlugins, key = { it.manifest.id }) { record ->
                    val isSelected = record.manifest.id == selectedProviderId
                    val (badgeBg, badgeText) = resolveCategoryBadge(record.manifest.categoryType)

                    Surface(
                        onClick = { onSelectProvider(record) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF1B202D).copy(alpha = 0.75f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusableItem(cornerRadius = 14.dp, focusedScale = 1.05f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            // Avatar chữ cái đầu với màu nhận diện danh mục
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(badgeBg.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                                    .border(1.dp, badgeBg.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = record.manifest.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Black,
                                    color = badgeBg,
                                    fontSize = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.manifest.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = badgeBg,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• Đang dùng",
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Nút Quản lý Plugins & Addons ở chân trang
        OutlinedButton(
            onClick = onOpenPluginsClick,
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusableItem(cornerRadius = 14.dp, focusedScale = 1.03f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_manage_plugins_btn),
                fontSize = 12.5.sp,
                color = Color.White
            )
        }
    }
}

/**
 * Trả về màu nền và nhãn danh mục cho badge
 */
@Composable
private fun resolveCategoryBadge(categoryType: String?): Pair<Color, String> {
    return when (categoryType?.lowercase()?.trim()) {
        "stremio" -> MaterialTheme.colorScheme.primary to stringResource(R.string.plugins_stremio_badge)
        "adult" -> PluginCategoryAdult to stringResource(R.string.home_tag_adult)
        "comic" -> PluginCategoryComic to stringResource(R.string.home_tag_comic)
        "novel" -> PluginCategoryNovel to stringResource(R.string.home_tag_novel_badge)
        "live" -> PluginCategoryLive to stringResource(R.string.home_tag_live_badge)
        else -> PluginCategoryMovie to stringResource(R.string.home_tag_movie_badge)
    }
}

