package com.jkhx.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jkhx.player.R
import com.jkhx.player.ui.theme.tvFocusableItem

private data class DownloadOptionEntry(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

@Composable
fun DownloadOptionsModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    qualityBadge: String? = null,
    onPlayExternal: (() -> Unit)? = null,
    onDownloadInApp: () -> Unit,
    onDownloadExternal: () -> Unit,
    onCopyLink: () -> Unit
) {
    if (!visible) return

    val configuration = LocalConfiguration.current
    val isCompactLandscape = configuration.screenWidthDp > configuration.screenHeightDp &&
        configuration.screenHeightDp < 540
    val maxDialogHeight = (configuration.screenHeightDp * 0.90f).dp

    val options = buildList {
        if (onPlayExternal != null) {
            add(
                DownloadOptionEntry(
                    icon = Icons.Default.PlayArrow,
                    title = stringResource(R.string.stream_action_play_external),
                    description = stringResource(R.string.stream_action_play_external_desc),
                    onClick = {
                        onDismiss()
                        onPlayExternal()
                    }
                )
            )
        }
        add(
            DownloadOptionEntry(
                icon = Icons.Default.Download,
                title = stringResource(R.string.stream_download_in_app),
                description = stringResource(R.string.stream_download_in_app_desc),
                onClick = {
                    onDismiss()
                    onDownloadInApp()
                }
            )
        )
        add(
            DownloadOptionEntry(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = stringResource(R.string.stream_download_external),
                description = stringResource(R.string.stream_download_external_desc),
                onClick = {
                    onDismiss()
                    onDownloadExternal()
                }
            )
        )
        add(
            DownloadOptionEntry(
                icon = Icons.Default.ContentCopy,
                title = stringResource(R.string.stream_download_copy_link),
                description = stringResource(R.string.stream_download_copy_link_desc),
                onClick = {
                    onDismiss()
                    onCopyLink()
                }
            )
        )
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
                .widthIn(max = if (isCompactLandscape) 600.dp else 440.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = maxDialogHeight)
                .padding(vertical = if (isCompactLandscape) 8.dp else 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompactLandscape) 18.dp else 22.dp)
            ) {
                // 1. Header: Icon + Title + Quality Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stream_download_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = title,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!qualityBadge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = qualityBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactLandscape) 12.dp else 16.dp))

                // 2. Danh sách tùy chọn (tự chuyển lưới 2 cột ở màn ngang + hỗ trợ cuộn dọc chống tràn)
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 8.dp else 10.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isCompactLandscape) {
                        options.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { option ->
                                    DownloadOptionItem(
                                        icon = option.icon,
                                        title = option.title,
                                        description = option.description,
                                        compact = true,
                                        onClick = option.onClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        options.forEach { option ->
                            DownloadOptionItem(
                                icon = option.icon,
                                title = option.title,
                                description = option.description,
                                compact = false,
                                onClick = option.onClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactLandscape) 10.dp else 14.dp))

                // 3. Nút Đóng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.tvFocusableItem(cornerRadius = 20.dp, focusedScale = 1.05f)
                    ) {
                        Text(
                            text = stringResource(R.string.common_close),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadOptionItem(
    icon: ImageVector,
    title: String,
    description: String,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier.tvFocusableItem(cornerRadius = 14.dp, focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 12.dp else 14.dp,
                    vertical = if (compact) 10.dp else 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 32.dp else 36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = if (compact) 13.sp else 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
