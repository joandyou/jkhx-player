package com.jkhx.player.ui.screens.plugins

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jkhx.player.R
import com.jkhx.player.ui.screens.player.DEFAULT_WEB_UA
import com.jkhx.player.ui.theme.ShapeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * JS Bridge Interface để Webpage có thể truyền token trực tiếp sang Native
 */
private class PluginAuthBridge(private val onTokenReceived: (String) -> Unit) {
    @JavascriptInterface
    fun postToken(token: String?) {
        val clean = token?.trim()
        if (!clean.isNullOrBlank() && clean != "null" && clean != "undefined" && clean.length > 8) {
            onTokenReceived(clean)
        }
    }
}

/**
 * Cửa sổ đăng nhập Webview nhúng (In-App WebView Login) cho Plugin
 * Tự động bắt Token (cravi_access_token, mofic_access_token, token...) trong localStorage, Cookie hoặc Deep Link
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginWebviewLoginDialog(
    pluginId: String,
    pluginName: String,
    targetUrl: String,
    onDismiss: () -> Unit,
    onTokenCaptured: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var isCaptured by remember { mutableStateOf(false) }

    fun handleToken(rawToken: String) {
        val clean = rawToken.trim('"', ' ', '\'', '\\')
        if (clean.isBlank() || clean == "null" || clean == "undefined" || clean.length < 8) return
        coroutineScope.launch(Dispatchers.Main) {
            if (!isCaptured) {
                isCaptured = true
                onTokenCaptured(clean)
                onDismiss()
            }
        }
    }

    // Coroutine thăm dò định kỳ kiểm tra localStorage khi trang đã nạp xong
    LaunchedEffect(Unit) {
        while (isActive && !isCaptured) {
            delay(2000)
            if (!isLoading) {
                webViewInstance?.let { wv ->
                    try {
                        wv.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    var t = localStorage.getItem('cravi_access_token') ||
                                            localStorage.getItem('mofic_access_token') ||
                                            localStorage.getItem('access_token') ||
                                            localStorage.getItem('token') ||
                                            sessionStorage.getItem('cravi_access_token') ||
                                            sessionStorage.getItem('access_token');
                                    if (t && t.length > 8) return t;
                                } catch(e) {}
                                return '';
                            })();
                            """.trimIndent()
                        ) { result ->
                            if (!isCaptured && !result.isNullOrBlank()) {
                                val clean = result.trim('"', ' ', '\'', '\\')
                                if (clean.isNotBlank() && clean != "null" && clean.length > 8) {
                                    handleToken(clean)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewInstance?.stopLoading()
                webViewInstance?.loadUrl("about:blank")
                webViewInstance?.destroy()
                webViewInstance = null
            } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 16.dp),
            shape = ShapeTokens.Card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header TopBar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = stringResource(R.string.plugin_login_title, pluginName),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Thanh tiến trình tải trang (Loading bar)
                if (isLoading || webProgress < 100) {
                    LinearProgressIndicator(
                        progress = { (webProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.5.dp))
                }

                // Banner hướng dẫn
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.plugin_login_hint),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF141414))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this

                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.parseColor("#141414"))

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    @Suppress("DEPRECATION")
                                    databaseEnabled = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    allowContentAccess = true
                                    allowFileAccess = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    // Giữ tất cả luồng đăng nhập trong cùng 1 cửa sổ, không kích hoạt popup gây treo WebView
                                    setSupportMultipleWindows(false)
                                    javaScriptCanOpenWindowsAutomatically = true
                                    // User-Agent chuẩn Chrome Android không chứa dấu hiệu WebView
                                    userAgentString = DEFAULT_WEB_UA
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                addJavascriptInterface(PluginAuthBridge { token ->
                                    handleToken(token)
                                }, "__jkhx_bridge")

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        webProgress = newProgress
                                        isLoading = newProgress < 100
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val uri = request.url

                                        // 1. Bắt Deep link jkhx://plugin/auth
                                        if (uri.scheme == "jkhx" && uri.host == "plugin" && uri.path?.startsWith("/auth") == true) {
                                            val token = uri.getQueryParameter("token") ?: uri.getQueryParameter("auth_token")
                                            if (!token.isNullOrBlank()) {
                                                handleToken(token)
                                                return true
                                            }
                                        }

                                        // 2. Bắt Token truyền qua query parameter nếu có
                                        val tokenParam = uri.getQueryParameter("cravi_access_token")
                                            ?: uri.getQueryParameter("access_token")
                                            ?: uri.getQueryParameter("token")
                                        if (!tokenParam.isNullOrBlank() && tokenParam.length > 8) {
                                            handleToken(tokenParam)
                                        }

                                        // Tiếp tục tải các URL (Google OAuth, callback...) trong cùng WebView
                                        return false
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?
                                    ) {
                                        handler?.proceed()
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        canGoBack = view?.canGoBack() == true

                                        // Kiểm tra cookies cho domain hiện tại
                                        if (!url.isNullOrBlank()) {
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            if (!cookies.isNullOrBlank()) {
                                                val parts = cookies.split(";")
                                                for (part in parts) {
                                                    val pair = part.trim().split("=", limit = 2)
                                                    if (pair.size == 2) {
                                                        val key = pair[0].trim()
                                                        val value = pair[1].trim()
                                                        if ((key.equals("cravi_access_token", ignoreCase = true) ||
                                                             key.equals("access_token", ignoreCase = true)) && value.length > 8) {
                                                            handleToken(value)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Tiêm script kiểm tra localStorage
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                try {
                                                    var t = localStorage.getItem('cravi_access_token') ||
                                                            localStorage.getItem('mofic_access_token') ||
                                                            localStorage.getItem('access_token') ||
                                                            localStorage.getItem('token');
                                                    if (t && t.length > 8) {
                                                        if (window.__jkhx_bridge && window.__jkhx_bridge.postToken) {
                                                            window.__jkhx_bridge.postToken(t);
                                                        }
                                                        return t;
                                                    }
                                                } catch(e) {}
                                                return '';
                                            })();
                                            """.trimIndent()
                                        ) { res ->
                                            if (!isCaptured && !res.isNullOrBlank()) {
                                                val clean = res.trim('"', ' ', '\'', '\\')
                                                if (clean.isNotBlank() && clean != "null" && clean.length > 8) {
                                                    handleToken(clean)
                                                }
                                            }
                                        }
                                    }
                                }

                                val initialUrl = targetUrl.ifBlank { "about:blank" }
                                loadUrl(initialUrl)
                            }
                        }
                    )
                }
            }
        }
    }
}
