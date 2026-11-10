package com.jkhx.player.ui.screens.search

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.StremioAddonEntity
import com.jkhx.player.core.model.stremio.StremioManifest
import com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem

/**
 * Bảng chọn Addon Stremio ở Màn hình Khám phá Responsive
 * (Modal Bottom Sheet trên Mobile, Centered Dialog trên TV / Màn hình ngang)
 * Đồng bộ với phong cách ProviderSelectorModal của màn hình Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StremioAddonSelectorModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    stremioAddons: List<Pair<StremioAddonEntity, StremioManifest>>,
    selectedAddonUrl: String?,
    onSelectAddon: (index: Int, pair: Pair<StremioAddonEntity, StremioManifest>) -> Unit,
    onOpenAddonsClick: () -> Unit
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
                        .heightIn(min = 400.dp, max = 560.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF121620).copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    shadowElevation = 18.dp
                ) {
                    StremioAddonSelectorContent(
                        isWideScreen = true,
                        stremioAddons = stremioAddons,
                        selectedAddonUrl = selectedAddonUrl,
                        onSelectAddon = { index, pair ->
                            onSelectAddon(index, pair)
                            onDismissRequest()
                        },
                        onOpenAddonsClick = {
                            onDismissRequest()
                            onOpenAddonsClick()
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
            StremioAddonSelectorContent(
                isWideScreen = false,
                stremioAddons = stremioAddons,
                selectedAddonUrl = selectedAddonUrl,
                onSelectAddon = { index, pair ->
                    onSelectAddon(index, pair)
                    onDismissRequest()
                },
                onOpenAddonsClick = {
                    onDismissRequest()
                    onOpenAddonsClick()
                },
                onClose = onDismissRequest
            )
        }
    }
}

/**
 * Nội dung bảng chọn Addon Stremio dùng chung
 */
@Composable
private fun StremioAddonSelectorContent(
    isWideScreen: Boolean,
    stremioAddons: List<Pair<StremioAddonEntity, StremioManifest>>,
    selectedAddonUrl: String?,
    onSelectAddon: (index: Int, pair: Pair<StremioAddonEntity, StremioManifest>) -> Unit,
    onOpenAddonsClick: () -> Unit,
    onClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredAddons = remember(stremioAddons, searchQuery) {
        if (searchQuery.isBlank()) {
            stremioAddons.mapIndexed { idx, pair -> Triple(idx, pair.first, pair.second) }
        } else {
            val q = searchQuery.trim().lowercase()
            stremioAddons.mapIndexedNotNull { idx, pair ->
                val name = pair.second.name.lowercase()
                val desc = pair.second.description.orEmpty().lowercase()
                val id = pair.second.id.lowercase()
                if (name.contains(q) || desc.contains(q) || id.contains(q)) {
                    Triple(idx, pair.first, pair.second)
                } else null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isWideScreen) 24.dp else 16.dp)
            .padding(bottom = if (isWideScreen) 16.dp else 24.dp)
    ) {
        // 1. Header Bar: Tiêu đề + Số lượng addon + Nút Đóng (cho TV)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.search_addon_modal_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isWideScreen) 18.sp else 16.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "${filteredAddons.size}/${stremioAddons.size} addon sẵn sàng",
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

        // 2. Ô tìm kiếm addon nhanh
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_addon_search_placeholder),
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
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
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

        // 3. Lưới Card Addon (2 cột Mobile, 3 cột TV)
        if (filteredAddons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_addon_empty_filter),
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
                itemsIndexed(
                    items = filteredAddons,
                    key = { _, triple -> triple.second.url }
                ) { _, (origIdx, addonEntity, manifest) ->
                    val isSelected = addonEntity.url == selectedAddonUrl
                    val cleanName = StremioPluginWrapper.cleanCatalogTitle(manifest.name)
                    val iconUrl = addonEntity.iconUrl ?: manifest.effectiveIconUrl

                    Surface(
                        onClick = { onSelectAddon(origIdx, Pair(addonEntity, manifest)) },
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
                            // Icon Addon hoặc Avatar chữ cái đầu
                            if (!iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = iconUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF2A2340))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = cleanName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    // Badge Stremio
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        shape = ShapeTokens.Badge
                                    ) {
                                        Text(
                                            text = stringResource(R.string.plugins_stremio_badge),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                val versionAndDesc = remember(manifest.version, manifest.description) {
                                    val v = manifest.version?.let { "v$it" }.orEmpty()
                                    val d = manifest.description?.takeIf { it.isNotBlank() }.orEmpty()
                                    when {
                                        v.isNotBlank() && d.isNotBlank() -> "$v • $d"
                                        v.isNotBlank() -> v
                                        else -> d
                                    }
                                }

                                if (versionAndDesc.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = versionAndDesc,
                                        fontSize = 10.5.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
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

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Nút chuyển nhanh sang Quản lý Addon trong Kho Tiện Ích
        OutlinedButton(
            onClick = onOpenAddonsClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusableItem(cornerRadius = 14.dp, focusedScale = 1.02f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.search_addon_manage_btn),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
