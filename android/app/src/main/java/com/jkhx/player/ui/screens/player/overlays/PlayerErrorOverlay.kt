package com.jkhx.player.ui.screens.player.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.R
import com.jkhx.player.ui.components.glass.LocalLiquidGlassBackdrop
import com.jkhx.player.ui.components.glass.VideoFrameBackdrop
import com.jkhx.player.ui.components.glass.frostedGlassSurface
import com.jkhx.player.ui.components.glass.playerLiquidGlass
import com.jkhx.player.ui.theme.tvFocusableItem

/**
 * Màn hình thông báo lỗi và tùy chọn thử lại hoặc chuyển sang Web Player (Phong cách Nuvio Liquid Glass)
 */
@Composable
fun BoxScope.PlayerErrorOverlay(
    errorMessage: String,
    embedFallback: String?,
    onSwitchToWebView: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    videoBackdrop: VideoFrameBackdrop? = null
) {
    CompositionLocalProvider(LocalLiquidGlassBackdrop provides videoBackdrop) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .frostedGlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    containerColor = Color(0xFF14171E).copy(alpha = 0.88f),
                    elevation = 16.dp,
                    rimWidth = 1.dp
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Nút chuyển sang Web Player (Fallback WebView) với hiệu ứng Liquid Glass và viền gương phản quang
                if (!embedFallback.isNullOrEmpty()) {
                    Button(
                        onClick = onSwitchToWebView,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        modifier = Modifier
                            .playerLiquidGlass(
                                shape = RoundedCornerShape(24.dp),
                                tint = Color(0xFF1E3A5F).copy(alpha = 0.55f),
                                fallback = Color(0xFF16253B).copy(alpha = 0.90f),
                                elevation = 6.dp
                            )
                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.08f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF64B5F6)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.player_switch_to_webview),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier
                            .playerLiquidGlass(
                                shape = RoundedCornerShape(24.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                fallback = Color(0xFF1C2230).copy(alpha = 0.90f),
                                elevation = 4.dp
                            )
                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.08f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.common_retry),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier
                            .playerLiquidGlass(
                                shape = RoundedCornerShape(24.dp),
                                tint = Color.White.copy(alpha = 0.12f),
                                fallback = Color(0xFF181B22).copy(alpha = 0.85f),
                                elevation = 4.dp
                            )
                            .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.08f)
                    ) {
                        Text(
                            text = stringResource(R.string.common_back),
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}


