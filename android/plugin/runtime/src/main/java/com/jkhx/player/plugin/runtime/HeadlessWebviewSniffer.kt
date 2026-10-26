package com.jkhx.player.plugin.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream

/**
 * Generic Headless WebView Sniffer Service cho tầng Plugins.
 * Chạy WebView hoàn toàn ngầm (không hiển thị UI, vô hình),
 * hỗ trợ tiêm JS hook để bóc tách luồng phát (m3u8, blob, decrypt) từ các host web phức tạp,
 * đồng thời triệt tiêu 100% popup quảng cáo và chuyển hướng.
 */
class HeadlessWebviewSniffer(private val context: Context) {

    suspend fun sniff(
        url: String,
        injectJs: String,
        timeoutMs: Long = 10000L,
        headers: Map<String, String> = emptyMap()
    ): String? {
        val deferred = CompletableDeferred<String?>()
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            createAndStartWebView(url, injectJs, deferred, headers)
        }

        return withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
    }

    private fun findActiveActivity(): android.app.Activity? {
        if (context is android.app.Activity) return context
        return runCatching {
            val appClass = Class.forName("com.jkhx.player.JkhxApplication")
            val field = appClass.getDeclaredField("currentActivity")
            field.isAccessible = true
            field.get(null) as? android.app.Activity
        }.getOrNull()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createAndStartWebView(
        url: String,
        injectJs: String,
        deferred: CompletableDeferred<String?>,
        headers: Map<String, String>
    ) {
        var webView: WebView? = null
        var isFinished = false
        val activeActivity = findActiveActivity()
        val decorView = activeActivity?.window?.decorView as? android.view.ViewGroup
        val targetContext = activeActivity ?: context

        println("[HeadlessSniffer] Starting sniff for: $url (attached: ${decorView != null})")

        fun cleanup() {
            if (!isFinished) {
                isFinished = true
                try {
                    decorView?.removeView(webView)
                    webView?.apply {
                        removeJavascriptInterface("__sniffer_bridge")
                        loadUrl("about:blank")
                        stopLoading()
                        destroy()
                    }
                } catch (_: Exception) {}
                webView = null
            }
        }

        try {
            webView = WebView(targetContext).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                alpha = 0.01f
                visibility = android.view.View.INVISIBLE
                layout(0, 0, 1280, 720)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowContentAccess = true
                    allowFileAccess = true
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = headers["User-Agent"]
                        ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                }

                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // Cầu nối giao tiếp giữa script tiêm vào WebView và Native
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(resultJson: String) {
                        println("[HeadlessSniffer] postMessage received: $resultJson")
                        if (!deferred.isCompleted) {
                            var finalJson = resultJson
                            try {
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                val cookies = cookieManager.getCookie(url) ?: ""
                                if (cookies.isNotBlank()) {
                                    val jsonObj = org.json.JSONObject(resultJson)
                                    if (!jsonObj.has("cookies")) {
                                        jsonObj.put("cookies", cookies)
                                        finalJson = jsonObj.toString()
                                    }
                                }
                            } catch (_: Exception) {}
                            deferred.complete(finalJson)
                            Handler(Looper.getMainLooper()).post { cleanup() }
                        }
                    }
                }, "__sniffer_bridge")

                webChromeClient = object : WebChromeClient() {
                    // Vô hiệu hóa và chặn 100% cửa sổ popup (window.open)
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message?
                    ): Boolean = false

                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        println("[HeadlessSniffer Console] ${consoleMessage?.message()}")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    // Cho phép chuyển hướng HTTP/HTTPS bình thường, chỉ chặn scheme ngoài (intent, market, v.v.)
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val targetUrl = request?.url?.toString() ?: return false
                        return !targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")
                    }

                    // Chặn quảng cáo rác từ tầng socket mạng của các mạng ad bên thứ ba
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        val lower = reqUrl.lowercase()
                        if (lower.contains("doubleclick.net") || lower.contains("google-analytics.com") ||
                            lower.contains("googlesyndication.com") || lower.contains("adservice.google") ||
                            lower.contains("popads.net") || lower.contains("popcash.net") ||
                            lower.contains("propellerads") || lower.contains("exoclick")) {
                            return WebResourceResponse("text/javascript", "UTF-8", ByteArrayInputStream("".toByteArray()))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // Tiêm hook càng sớm càng tốt khi trang bắt đầu tải
                        if (injectJs.isNotBlank()) {
                            view?.evaluateJavascript(injectJs, null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Đảm bảo tiêm lại khi trang tải xong DOM
                        if (injectJs.isNotBlank()) {
                            view?.evaluateJavascript(injectJs, null)
                        }
                    }
                }

                // Tiêm hook định kỳ trong 6 giây đầu để bắt kịp mọi script tạo động
                val reapplyRunnable = object : Runnable {
                    var count = 0
                    override fun run() {
                        if (!isFinished && count++ < 20) {
                            if (injectJs.isNotBlank()) {
                                webView?.evaluateJavascript(injectJs, null)
                            }
                            handler.postDelayed(this, 300)
                        }
                    }
                }
                handler.postDelayed(reapplyRunnable, 300)

                val customHeaders = HashMap<String, String>()
                headers.forEach { (k, v) -> customHeaders[k] = v }
                decorView?.addView(this)
                loadUrl(url, customHeaders)
            }
        } catch (_: Exception) {
            deferred.complete(null)
            cleanup()
        }
    }
}
