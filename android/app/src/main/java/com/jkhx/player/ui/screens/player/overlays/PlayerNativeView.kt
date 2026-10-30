package com.jkhx.player.ui.screens.player.overlays

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.jkhx.player.core.player.ExoPlayerManager
import com.jkhx.player.ui.screens.player.applySubtitleAppearance

/**
 * AndroidView bao bọc Media3 PlayerView cho trình phát Native
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerNativeView(
    exoPlayerManager: ExoPlayerManager,
    resizeMode: Int,
    subtitleTextSize: Float,
    subtitleIsBold: Boolean,
    subtitleTextColor: Int,
    subtitleTextOpacity: Int,
    subtitleIsOutline: Boolean,
    subtitleOutlineColor: Int,
    subtitleBottomMargin: Int,
    isLandscape: Boolean,
    onActivePlayerViewChange: (PlayerView) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                onActivePlayerViewChange(this)
                player = exoPlayerManager.player
                keepScreenOn = true
                useController = false // Dùng toàn bộ custom Jetpack Compose Player Controls
                this.resizeMode = resizeMode
                subtitleView?.apply {
                    applySubtitleAppearance(
                        this,
                        subtitleTextSize,
                        subtitleIsBold,
                        subtitleTextColor,
                        subtitleTextOpacity,
                        subtitleIsOutline,
                        subtitleOutlineColor,
                        subtitleBottomMargin,
                        isLandscape
                    )
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { pv ->
            pv.resizeMode = resizeMode
        },
        modifier = modifier
    )
}

