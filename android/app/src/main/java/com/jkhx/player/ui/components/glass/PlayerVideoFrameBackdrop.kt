package com.jkhx.player.ui.components.glass

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val VideoFrameCaptureWidth = 480
private const val VideoFrameCaptureIntervalMs = 33L // ~30fps
private const val VideoFrameBufferCount = 3
private const val BlackFrameLimit = 15

@Stable
class VideoFrameBackdrop {

    private var frame: ImageBitmap? by mutableStateOf(null, neverEqualPolicy())
    private var frameBounds: Rect = Rect.Zero
    private var ready: Boolean by mutableStateOf(false)

    val isReady: Boolean
        get() = ready

    internal var consumers by mutableIntStateOf(0)
        private set

    fun acquire() {
        consumers += 1
    }

    fun release() {
        consumers = (consumers - 1).coerceAtLeast(0)
    }

    internal fun publishFrame(image: ImageBitmap, boundsOnScreen: Rect) {
        frameBounds = boundsOnScreen
        frame = image
        if (!ready) ready = true
    }

    internal fun clearFrame() {
        frame = null
        frameBounds = Rect.Zero
        if (ready) ready = false
    }

    fun drawTo(drawScope: DrawScope, coordinates: LayoutCoordinates?) {
        val target = coordinates ?: return
        if (!target.isAttached) return
        val image = frame ?: return
        val bounds = frameBounds
        if (bounds.isEmpty) return
        val onScreen = target.positionOnScreen()
        val origin = if (onScreen.x.isFinite() && onScreen.y.isFinite()) onScreen else target.positionInWindow()
        drawScope.drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(
                (bounds.left - origin.x).roundToInt(),
                (bounds.top - origin.y).roundToInt(),
            ),
            dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * Capture frame video trực tiếp từ SurfaceView của PlayerView bằng PixelCopy.
 * Tự động dừng vòng lặp khi controls ẩn để tiết kiệm 100% CPU/GPU.
 */
@Composable
fun rememberVideoFrameBackdrop(enabled: Boolean): VideoFrameBackdrop? {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val view = LocalView.current
    val backdrop = remember { VideoFrameBackdrop() }

    LaunchedEffect(backdrop, view, enabled) {
        val handler = Handler(Looper.getMainLooper())
        val buffers = arrayOfNulls<Bitmap>(VideoFrameBufferCount)
        var nextBuffer = 0
        var blackFrames = 0

        snapshotFlow { backdrop.consumers > 0 }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (!active) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    val surfaceView = view.rootView.findVideoSurfaceView()
                    if (surfaceView != null && surfaceView.width > 0 && surfaceView.height > 0) {
                        val width = VideoFrameCaptureWidth
                        val height = (width * surfaceView.height / surfaceView.width.toFloat())
                            .roundToInt()
                            .coerceIn(1, width * 4)
                        val bitmap = buffers[nextBuffer]?.takeIf { it.width == width && it.height == height }
                            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                buffers[nextBuffer] = it
                            }
                        val copied = surfaceView.copyFrameInto(bitmap, handler)
                        if (copied && bitmap.isBlankCapture()) {
                            blackFrames += 1
                            if (blackFrames >= BlackFrameLimit) backdrop.clearFrame()
                        } else if (copied) {
                            blackFrames = 0
                            val location = IntArray(2)
                            surfaceView.getLocationOnScreen(location)
                            backdrop.publishFrame(
                                image = bitmap.asImageBitmap(),
                                boundsOnScreen = Rect(
                                    left = location[0].toFloat(),
                                    top = location[1].toFloat(),
                                    right = (location[0] + surfaceView.width).toFloat(),
                                    bottom = (location[1] + surfaceView.height).toFloat(),
                                ),
                            )
                            nextBuffer = (nextBuffer + 1) % buffers.size
                        }
                    }
                    delay(VideoFrameCaptureIntervalMs)
                }
            }
    }

    DisposableEffect(backdrop) {
        onDispose { backdrop.clearFrame() }
    }
    return backdrop
}

private fun View.findVideoSurfaceView(): SurfaceView? {
    var best: SurfaceView? = null
    var bestArea = 0
    var fromPlayerView: SurfaceView? = null

    fun SurfaceView.isUsable(): Boolean =
        isShown && width > 0 && height > 0 && holder.surface?.isValid == true

    fun visit(candidate: View) {
        if (fromPlayerView != null) return
        if (candidate.visibility != View.VISIBLE) return
        if (candidate is PlayerView) {
            val videoSurface = candidate.videoSurfaceView as? SurfaceView
            if (videoSurface != null && videoSurface.isUsable()) {
                fromPlayerView = videoSurface
                return
            }
        }
        if (candidate is SurfaceView) {
            val area = candidate.width * candidate.height
            if (area > bestArea && candidate.isUsable()) {
                best = candidate
                bestArea = area
            }
        } else if (candidate is ViewGroup) {
            for (index in 0 until candidate.childCount) {
                visit(candidate.getChildAt(index))
            }
        }
    }
    visit(this)
    return fromPlayerView ?: best
}

private fun Bitmap.isBlankCapture(): Boolean {
    val stepX = (width / 8).coerceAtLeast(1)
    val stepY = (height / 8).coerceAtLeast(1)
    var y = stepY / 2
    while (y < height) {
        var x = stepX / 2
        while (x < width) {
            if (getPixel(x, y) and 0x00FFFFFF != 0) return false
            x += stepX
        }
        y += stepY
    }
    return true
}

private suspend fun SurfaceView.copyFrameInto(bitmap: Bitmap, handler: Handler): Boolean {
    if (width <= 0 || height <= 0 || holder.surface?.isValid != true) return false
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                this,
                bitmap,
                { result ->
                    if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS)
                },
                handler,
            )
        } catch (_: IllegalArgumentException) {
            if (continuation.isActive) continuation.resume(false)
        }
    }
}
