package com.jkhx.player.ui.screens.player.overlays

import android.app.Activity
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jkhx.player.R
import com.jkhx.player.core.model.EpisodeStream
import com.jkhx.player.plugin.manager.stremio.StremioPluginWrapper
import com.jkhx.player.ui.components.glass.playerLiquidGlass
import com.jkhx.player.ui.screens.player.DEFAULT_WEB_UA
import com.jkhx.player.ui.screens.player.buildHtml5HlsPlayerPage
import com.jkhx.player.ui.screens.player.embedHeadersFor
import com.jkhx.player.ui.theme.tvFocusableItem
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * Trình phát WebView nhúng fallback (hỗ trợ giải mã m3u8 proxy, chặn quảng cáo, tiêm script CSS)
 */
@Composable
fun PlayerWebViewPlayer(
    webViewUrl: String,
    webViewHeaders: Map<String, String>,
    resolvedStream: EpisodeStream?,
    activity: Activity?,
    onActiveWebViewChange: (WebView?) -> Unit,
    webCustomView: View?,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback?) -> Unit,
    onHideCustomView: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    key(webViewUrl) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    onActiveWebViewChange(this)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = false
                        useWideViewPort = false
                        allowContentAccess = true
                        allowFileAccess = true
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        userAgentString = webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA
                    }

                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    val refererForPage = webViewHeaders["Referer"]
                        ?: embedHeadersFor(webViewUrl)["Referer"]
                        ?: ""

                    val referrerJs = """
                        (function() {
                            try {
                                if ('$refererForPage') {
                                    Object.defineProperty(document, 'referrer', {
                                        get: function() { return '$refererForPage'; },
                                        configurable: true
                                    });
                                }
                            } catch(e) {}
                            try {
                                window.open = function() { return null; };
                            } catch(e) {}
                            try {
                                var style = document.createElement('style');
                                style.textContent = '* { box-sizing: border-box !important; } html, body { overflow: hidden !important; margin: 0 !important; padding: 0 !important; width: 100% !important; height: 100% !important; background: #000 !important; } .player, #hrm-player, .jwplayer, #player, #v-player-wrapper { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; } video, iframe { width: 100% !important; height: 100% !important; object-fit: contain !important; border: none !important; margin: 0 !important; padding: 0 !important; }';
                                (document.head || document.documentElement).appendChild(style);
                            } catch(e) {}
                        })();
                    """.trimIndent()

                    val currentUrl = webViewUrl
                    val currentUrlHost = try { Uri.parse(currentUrl).host?.lowercase() } catch (_: Exception) { null }
                    val inferredStreamType = StremioPluginWrapper.inferStreamType(
                        streamUrl = currentUrl,
                        rawType = if (currentUrl == resolvedStream?.url) resolvedStream?.type else null,
                        metaText = ""
                    )
                    val isDirectMediaStream = !currentUrl.contains("/embed", ignoreCase = true) &&
                        !currentUrl.contains("player.php", ignoreCase = true) &&
                        (inferredStreamType in setOf("m3u8", "hls", "mp4", "mkv", "webm", "mov", "avi") ||
                            (currentUrl == resolvedStream?.url && resolvedStream?.type != "embed"))

                    val embedHttpClient = OkHttpClient.Builder()
                        .dns(object : okhttp3.Dns {
                            override fun lookup(hostname: String): List<java.net.InetAddress> =
                                okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
                        })
                        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString() ?: return false
                            // Chặn toàn bộ scheme không phải HTTP/HTTPS (intent, market, tel...)
                            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                return true
                            }

                            // Chỉ chặn redirect của main frame nếu cố tình nhảy sang web quảng cáo / cá độ khác
                            if (request.isForMainFrame) {
                                val currentUri = Uri.parse(webViewUrl)
                                val targetUri = request.url
                                val currentHost = currentUri.host?.lowercase()
                                val targetHost = targetUri?.host?.lowercase()
                                val refererHost = try {
                                     if (refererForPage.isNotBlank()) Uri.parse(refererForPage).host?.lowercase() else null
                                } catch (_: Exception) { null }

                                if (currentHost != null && targetHost != null) {
                                    val isAllowed = targetHost == currentHost ||
                                            targetHost.endsWith(".$currentHost") ||
                                            currentHost.endsWith(".$targetHost") ||
                                            (refererHost != null && (targetHost == refererHost || targetHost.endsWith(".$refererHost") || refererHost.endsWith(".$targetHost")))

                                    if (!isAllowed) {
                                        println("[PlayerScreen WebView] Chặn redirect quảng cáo ngoại vi: $targetUrl")
                                        return true
                                    }
                                }
                            }

                            return false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null

                            // 0. HLS / DIRECT MEDIA STREAM PROXY INTERCEPTION:
                            // Khi WebView đang đóng vai trò trình phát HTML5 + hls.js cho luồng HLS/Direct video,
                            // tự động đính kèm User-Agent, Referer, Origin từ webViewHeaders và cấp header CORS
                            val reqHost = request.url?.host?.lowercase()
                            val isMediaSubRequest = isDirectMediaStream && !request.isForMainFrame && (
                                reqUrl == currentUrl ||
                                (currentUrlHost != null && reqHost != null && (reqHost == currentUrlHost || reqHost.endsWith(".$currentUrlHost"))) ||
                                reqUrl.contains(".m3u8", ignoreCase = true) ||
                                reqUrl.contains(".ts", ignoreCase = true) ||
                                reqUrl.contains(".m4s", ignoreCase = true) ||
                                reqUrl.contains("/m/") ||
                                reqUrl.contains("/s/") ||
                                reqUrl.contains("/hls")
                            ) && !reqUrl.endsWith(".js", ignoreCase = true) && !reqUrl.endsWith(".css", ignoreCase = true)

                            if (isMediaSubRequest) {
                                if (request.method.equals("OPTIONS", ignoreCase = true)) {
                                    val corsHeaders = mapOf(
                                        "Access-Control-Allow-Origin" to "*",
                                        "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                                        "Access-Control-Allow-Headers" to "*",
                                        "Access-Control-Max-Age" to "86400"
                                    )
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        200,
                                        "OK",
                                        corsHeaders,
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                try {
                                    val reqBuilder = Request.Builder()
                                        .url(reqUrl)
                                        .header("User-Agent", webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA)

                                    if (refererForPage.isNotBlank()) {
                                        reqBuilder.header("Referer", refererForPage)
                                        val originHeader = webViewHeaders["Origin"] ?: try {
                                            Uri.parse(refererForPage).let { "${it.scheme}://${it.host}" }
                                        } catch (_: Exception) { refererForPage }
                                        reqBuilder.header("Origin", originHeader)
                                    }

                                    request.requestHeaders["Range"]?.let { rangeVal ->
                                        reqBuilder.header("Range", rangeVal)
                                    }

                                    val mediaRes = embedHttpClient.newCall(reqBuilder.build()).execute()
                                    val rawContentType = mediaRes.header("Content-Type") ?: "application/octet-stream"
                                    val mimeType = rawContentType.substringBefore(";").trim().ifBlank { "application/octet-stream" }
                                    val charset = rawContentType.substringAfter("charset=", "").substringBefore(";").trim().ifBlank { null }

                                    val respHeaders = mutableMapOf<String, String>()
                                    for (i in 0 until mediaRes.headers.size) {
                                        val hName = mediaRes.headers.name(i)
                                        if (!hName.equals("content-security-policy", ignoreCase = true) &&
                                            !hName.equals("x-frame-options", ignoreCase = true) &&
                                            !hName.equals("access-control-allow-origin", ignoreCase = true)
                                        ) {
                                            respHeaders[hName] = mediaRes.headers.value(i)
                                        }
                                    }
                                    respHeaders["Access-Control-Allow-Origin"] = "*"
                                    respHeaders["Access-Control-Expose-Headers"] = "*"

                                    return WebResourceResponse(
                                        mimeType,
                                        charset,
                                        mediaRes.code,
                                        mediaRes.message.ifBlank { "OK" },
                                        respHeaders,
                                        mediaRes.body?.byteStream()
                                    )
                                } catch (e: Exception) {
                                    println("[PlayerScreen WebView] Media proxy intercept error: ${e.message}")
                                }
                            }

                            // 1. MAIN FRAME INTERCEPTION: Nạp và chuẩn hóa HTML5 Viewport chuẩn cho trang Embed
                            if (request.isForMainFrame && !isDirectMediaStream && refererForPage.isNotBlank() &&
                                (reqUrl == webViewUrl || reqUrl.contains("/embed") || reqUrl.contains("player.php"))
                            ) {
                                try {
                                    val okReq = Request.Builder()
                                        .url(reqUrl)
                                        .header("Referer", refererForPage)
                                        .header("Origin", try { Uri.parse(refererForPage).let { "${it.scheme}://${it.host}" } } catch (_: Exception) { refererForPage })
                                        .header("User-Agent", webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA)
                                        .build()

                                    val okRes = embedHttpClient.newCall(okReq).execute()
                                    if (okRes.isSuccessful) {
                                        val rawHtml = okRes.body?.string() ?: ""
                                        val resContentType = okRes.header("Content-Type")?.lowercase() ?: ""
                                        val isRawM3u8 = rawHtml.trimStart().startsWith("#EXTM3U") ||
                                            resContentType.contains("mpegurl") ||
                                            resContentType.contains("application/x-mpegurl")

                                        val isFullDoc = rawHtml.contains("<html", ignoreCase = true) || rawHtml.contains("<!DOCTYPE", ignoreCase = true)

                                        val modifiedHtml = if (isRawM3u8) {
                                            buildHtml5HlsPlayerPage(reqUrl, refererForPage)
                                        } else if (isFullDoc) {
                                            val injectHeader = """
                                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                                                <style>
                                                    * { margin: 0 !important; padding: 0 !important; box-sizing: border-box !important; }
                                                    html, body { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; }
                                                    #v-player-wrapper, .player, #hrm-player, .jwplayer, #player { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; }
                                                    video, iframe { width: 100% !important; height: 100% !important; object-fit: contain !important; border: none !important; margin: 0 !important; padding: 0 !important; }
                                                </style>
                                                <script>
                                                    try { Object.defineProperty(document, 'referrer', { get: function() { return '$refererForPage'; }, configurable: true }); } catch(e) {}
                                                    try { window.open = function() { return null; }; } catch(e) {}
                                                </script>
                                            """.trimIndent()

                                            if (rawHtml.contains("<head>", ignoreCase = true)) {
                                                rawHtml.replaceFirst("<head>", "<head>$injectHeader", ignoreCase = true)
                                            } else {
                                                rawHtml.replaceFirst("<html>", "<html><head>$injectHeader</head>", ignoreCase = true)
                                            }
                                        } else {
                                            // Trường hợp player.php chỉ trả về raw <iframe>: bọc thành tài liệu HTML5 chuẩn
                                            """
                                            <!DOCTYPE html>
                                            <html lang="vi">
                                            <head>
                                                <meta charset="utf-8">
                                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                                                <style>
                                                    * { margin: 0 !important; padding: 0 !important; box-sizing: border-box !important; }
                                                    html, body { width: 100vw !important; height: 100vh !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; }
                                                    #v-player-wrapper { position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; background: #000 !important; }
                                                    iframe { position: absolute !important; top: 0 !important; left: 0 !important; width: 100% !important; height: 100% !important; border: none !important; margin: 0 !important; padding: 0 !important; }
                                                </style>
                                                <script>
                                                    try { Object.defineProperty(document, 'referrer', { get: function() { return '$refererForPage'; }, configurable: true }); } catch(e) {}
                                                    try { window.open = function() { return null; }; } catch(e) {}
                                                </script>
                                            </head>
                                            <body style="margin: 0; padding: 0; background: #000; overflow: hidden;">
                                                <div id="v-player-wrapper">
                                                    $rawHtml
                                                </div>
                                            </body>
                                            </html>
                                            """.trimIndent()
                                        }

                                        val respHeaders = mutableMapOf<String, String>()
                                        for (i in 0 until okRes.headers.size) {
                                            val hName = okRes.headers.name(i)
                                            if (!hName.equals("content-security-policy", ignoreCase = true) &&
                                                !hName.equals("x-frame-options", ignoreCase = true)) {
                                                respHeaders[hName] = okRes.headers.value(i)
                                            }
                                        }

                                        return WebResourceResponse(
                                            "text/html",
                                            "utf-8",
                                            okRes.code,
                                            okRes.message.ifBlank { "OK" },
                                            respHeaders,
                                            ByteArrayInputStream(modifiedHtml.toByteArray(Charsets.UTF_8))
                                        )
                                    }
                                } catch (e: Exception) {
                                    println("[PlayerScreen] shouldInterceptRequest main frame error: ${e.message}")
                                }
                            }

                            // 2. SUBFRAME INTERCEPTION (Iframe Player con như streamfree.vip):
                            // Bóc tách và tiêm CSS Lớp 2 để triệt tiêu padding-bottom 56.25% gây tràn & lẹm đáy video
                            val isSubframeCandidate = !request.isForMainFrame &&
                                    (reqUrl.contains("streamfree.vip") || reqUrl.contains("/embed/") || reqUrl.contains("player")) &&
                                    (request.requestHeaders["Accept"]?.contains("text/html") == true || reqUrl.contains("/embed/v/"))

                            if (isSubframeCandidate) {
                                try {
                                    val subReferer = webViewUrl.ifBlank { refererForPage }
                                    val subReq = Request.Builder()
                                        .url(reqUrl)
                                        .header("Referer", subReferer)
                                        .header("User-Agent", webViewHeaders["User-Agent"] ?: DEFAULT_WEB_UA)
                                        .build()

                                    val subRes = embedHttpClient.newCall(subReq).execute()
                                    if (subRes.isSuccessful) {
                                        val subHtml = subRes.body?.string() ?: ""
                                        val subInject = """
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                                            <style>
                                                * { box-sizing: border-box !important; }
                                                html, body { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; }
                                                .player, #hrm-player, .jwplayer, #player, #v-player-wrapper { width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; }
                                                video, iframe { width: 100% !important; height: 100% !important; object-fit: contain !important; border: none !important; margin: 0 !important; padding: 0 !important; }
                                            </style>
                                        """.trimIndent()

                                        val modifiedSubHtml = if (subHtml.contains("<head>", ignoreCase = true)) {
                                            subHtml.replaceFirst("<head>", "<head>$subInject", ignoreCase = true)
                                        } else if (subHtml.contains("<html>", ignoreCase = true)) {
                                            subHtml.replaceFirst("<html>", "<html><head>$subInject</head>", ignoreCase = true)
                                        } else {
                                            subInject + subHtml
                                        }

                                        val respHeaders = mutableMapOf<String, String>()
                                        for (i in 0 until subRes.headers.size) {
                                            val hName = subRes.headers.name(i)
                                            if (!hName.equals("content-security-policy", ignoreCase = true) &&
                                                !hName.equals("x-frame-options", ignoreCase = true)) {
                                                respHeaders[hName] = subRes.headers.value(i)
                                            }
                                        }

                                        return WebResourceResponse(
                                            "text/html",
                                            "utf-8",
                                            subRes.code,
                                            subRes.message.ifBlank { "OK" },
                                            respHeaders,
                                            ByteArrayInputStream(modifiedSubHtml.toByteArray(Charsets.UTF_8))
                                        )
                                    }
                                } catch (e: Exception) {
                                    println("[PlayerScreen] shouldInterceptRequest subframe error: ${e.message}")
                                }
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            view?.evaluateJavascript(referrerJs, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(referrerJs, null)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        // Chặn 100% lệnh mở cửa sổ mới từ mã quảng cáo (window.open, target="_blank")
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            return false
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view == null) return
                            if (webCustomView != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            val decor = activity?.window?.decorView as? ViewGroup ?: return
                            view.setBackgroundColor(android.graphics.Color.BLACK)
                            decor.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            onShowCustomView(view, callback)
                        }

                        override fun onHideCustomView() {
                            onHideCustomView()
                        }
                    }

                    val referer = webViewHeaders["Referer"]
                        ?: embedHeadersFor(currentUrl)["Referer"]

                    val finalHeaders = webViewHeaders.toMutableMap()
                    if (!finalHeaders.containsKey("Referer") && referer != null) {
                        finalHeaders["Referer"] = referer
                    }
                    if (!finalHeaders.containsKey("User-Agent")) {
                        finalHeaders["User-Agent"] = DEFAULT_WEB_UA
                    }

                    tag = currentUrl
                    if (isDirectMediaStream) {
                        val baseOrigin = try {
                            if (refererForPage.isNotBlank()) {
                                val u = Uri.parse(refererForPage)
                                if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}/" else refererForPage
                            } else {
                                val u = Uri.parse(currentUrl)
                                if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}/" else "https://localhost/"
                            }
                        } catch (_: Exception) {
                            "https://localhost/"
                        }
                        loadDataWithBaseURL(
                            baseOrigin,
                            buildHtml5HlsPlayerPage(currentUrl, refererForPage),
                            "text/html",
                            "utf-8",
                            null
                        )
                    } else {
                        loadUrl(currentUrl, finalHeaders)
                    }
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Thanh điều khiển cố định ở đỉnh màn hình cho WebView Player Fallback
 */
@Composable
fun BoxScope.PlayerWebViewTopBar(
    movieName: String,
    episodeName: String,
    onRotateScreen: () -> Unit,
    hasMultipleEpisodes: Boolean,
    isEpisodesDrawerOpen: Boolean,
    onOpenEpisodesDrawer: () -> Unit,
    isSourceDrawerOpen: Boolean,
    onOpenSourceDrawer: () -> Unit,
    canSwitchToNative: Boolean,
    onSwitchToNative: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = movieName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$episodeName • Web Player",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        // Nút Xoay màn hình (Ngang <-> Dọc)
        IconButton(
            onClick = onRotateScreen,
            modifier = Modifier
                .size(40.dp)
                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
        ) {
            Icon(
                Icons.Default.ScreenRotation,
                contentDescription = stringResource(R.string.player_rotate),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Nút Danh sách tập (cho phim bộ trong Web Player)
        if (hasMultipleEpisodes) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onOpenEpisodesDrawer,
                modifier = Modifier
                    .size(40.dp)
                    .playerLiquidGlass(CircleShape, elevation = 4.dp)
                    .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
            ) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = stringResource(R.string.player_episodes_sheet_title),
                    tint = if (isEpisodesDrawerOpen) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Nút Chọn Nguồn Phát & Server (Multi-source Selector)
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onOpenSourceDrawer,
            modifier = Modifier
                .size(40.dp)
                .playerLiquidGlass(CircleShape, elevation = 4.dp)
                .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
        ) {
            Icon(
                Icons.Default.Dns,
                contentDescription = stringResource(R.string.player_sources_btn),
                tint = if (isSourceDrawerOpen) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Nút chuyển đổi nhanh WebView -> ExoPlayer Native
        if (canSwitchToNative) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSwitchToNative,
                modifier = Modifier
                    .size(40.dp)
                    .playerLiquidGlass(CircleShape, elevation = 4.dp)
                    .tvFocusableItem(cornerRadius = 24.dp, focusedScale = 1.12f)
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = stringResource(R.string.player_switch_to_native),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

