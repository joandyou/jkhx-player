package com.jkhx.player.ui.screens.home

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.ui.theme.tvFocusableItem

/**
 * Tính toán chuỗi hiển thị thời lượng còn lại của phim đang xem dở
 */
private fun formatRemainingTime(positionMs: Long, durationMs: Long, context: Context): String {
    if (durationMs <= 0L || positionMs <= 0L) return ""
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    val totalMinutes = (remainingMs / 60000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> context.getString(R.string.cw_remaining_hours_minutes, hours, minutes)
        totalMinutes > 0 -> context.getString(R.string.cw_remaining_minutes, totalMinutes)
        else -> context.getString(R.string.cw_remaining_seconds)
    }
}

/**
 * Popup Context Menu khi nhấn giữ (long press) vào card Xem tiếp trên Home Screen.
 * Thiết kế chuẩn phong cách Nuvio với Preview 16:9 và 4 tùy chọn hành động,
 * màu sắc tuân thủ Theme động hiện tại của ứng dụng.
 */
@Composable
fun ContinueWatchingActionModal(
    history: HistoryEntity?,
    onDismissRequest: () -> Unit,
    onOpenDetails: (HistoryEntity) -> Unit,
    onSelectStream: (HistoryEntity) -> Unit,
    onRestartFromBeginning: (HistoryEntity) -> Unit,
    onDelete: (HistoryEntity) -> Unit
) {
    if (history == null) return
    val context = LocalContext.current

    val remainingLabel = remember(history.positionMs, history.durationMs) {
        formatRemainingTime(history.positionMs, history.durationMs, context)
    }

    val progressFraction = remember(history.positionMs, history.durationMs) {
        if (history.durationMs > 0L) {
            (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 380.dp)
                    .padding(horizontal = 20.dp)
                    .clickable(enabled = false) {}, // Chặn click xuyên qua nền modal
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // =====================================================================
                // 1. CARD PREVIEW NỔI 16:9
                // =====================================================================
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        // Poster / Backdrop
                        AsyncImage(
                            model = remember(history.thumbUrl) {
                                ImageRequest.Builder(context)
                                    .data(history.thumbUrl)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = history.movieName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Lớp gradient tối ở chân và đỉnh để nổi bật chữ
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )

                        // Badge góc trên bên phải: Thời lượng còn lại (Ví dụ: "Còn 37ph")
                        if (remainingLabel.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.72f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = remainingLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Tiêu đề phim ở góc dưới bên trái của ảnh thumbnail
                        Text(
                            text = history.movieName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )

                        // Thanh tiến trình màu Theme ở đáy thumbnail
                        if (progressFraction > 0f) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .align(Alignment.BottomCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Black.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tiêu đề & Phụ đề thể loại dưới Card
                Text(
                    text = history.movieName,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subLabel = history.lastEpisodeName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.cw_tag_movie)
                Text(
                    text = subLabel,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                // =====================================================================
                // 2. KHUNG CHỨA 4 HÀNG MENU HÀNH ĐỘNG
                // =====================================================================
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF161922).copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. Xem chi tiết
                        ActionMenuItemRow(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.cw_action_view_details),
                            onClick = {
                                onDismissRequest()
                                onOpenDetails(history)
                            }
                        )

                        HorizontalDivider(
                            color = DividerDefaults.color.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )

                        // 2. Chọn nguồn phát
                        ActionMenuItemRow(
                            icon = Icons.Default.PlayArrow,
                            title = stringResource(R.string.cw_action_select_stream),
                            onClick = {
                                onDismissRequest()
                                onSelectStream(history)
                            }
                        )

                        HorizontalDivider(
                            color = DividerDefaults.color.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )

                        // 3. Xem từ đầu
                        ActionMenuItemRow(
                            icon = Icons.Default.Replay,
                            title = stringResource(R.string.cw_action_restart),
                            onClick = {
                                onDismissRequest()
                                onRestartFromBeginning(history)
                            }
                        )

                        HorizontalDivider(
                            color = DividerDefaults.color.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )

                        // 4. Xóa (Màu đỏ cảnh báo)
                        ActionMenuItemRow(
                            icon = Icons.Default.DeleteOutline,
                            title = stringResource(R.string.cw_action_delete),
                            textColor = MaterialTheme.colorScheme.error,
                            iconColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                onDismissRequest()
                                onDelete(history)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Một hàng chức năng hành động trong Action Menu
 */
@Composable
private fun ActionMenuItemRow(
    icon: ImageVector,
    title: String,
    textColor: Color = Color.White.copy(alpha = 0.92f),
    iconColor: Color = Color.White.copy(alpha = 0.82f),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

