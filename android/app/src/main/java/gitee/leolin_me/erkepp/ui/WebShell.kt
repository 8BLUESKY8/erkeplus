package gitee.leolin_me.erkepp.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import gitee.leolin_me.erkepp.AppConfig
import gitee.leolin_me.erkepp.UrlPolicy
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * 外壳页面：一个受控的 WebView。
 *
 * 约束：
 * - 仅允许在 [AppConfig.ALLOWED_HOST] 及其子域内跳转，其余链接交给系统浏览器
 * - 禁止混合内容、禁止本地文件访问
 * - Cookie 与 DOM Storage 保持持久化，登录取自官方站点自身
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebShell(
    onPickFiles: (ValueCallback<Array<Uri>>) -> Unit,
    modifier: Modifier = Modifier,
    url: String = AppConfig.HOME_URL,
    prefill: Map<String, String> = emptyMap(),
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val targetUrl = url
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorUrl by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val refreshLayout = remember { mutableStateOf<SwipeRefreshLayout?>(null) }
    // 网页详情依赖官方站点的 localStorage.Token；注入一次后重载，让页面启动时就能读到
    val seeded = remember { mutableStateOf(false) }
    // 注入期间先遮住 WebView，避免用户看到首页闪一下再跳详情
    var ready by remember { mutableStateOf(prefill.isEmpty()) }
    var awaitingTarget by remember { mutableStateOf(false) }
    val targetFragment = remember(targetUrl) { targetUrl.substringAfter('#', "") }

    // 跳转失败或超时也要放行，避免一直挡着加载层
    LaunchedEffect(awaitingTarget) {
        if (awaitingTarget) {
            delay(8_000)
            if (awaitingTarget) {
                awaitingTarget = false
                ready = true
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "$userAgentString Erkepp/${AppConfig.VERSION}"
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url
                    if (UrlPolicy.isAllowedHost(uri.host)) return false
                    openExternal(context, uri)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    errorUrl = null
                    loading = true
                    progress = 0
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                    progress = 100
                    canGoBack = view.canGoBack()
                    refreshLayout.value?.isRefreshing = false
                    if (awaitingTarget && url.contains(targetFragment)) {
                        awaitingTarget = false
                        ready = true
                    }
                    if (!seeded.value && prefill.isNotEmpty()) {
                        seeded.value = true
                        view.evaluateJavascript(storageScript(prefill)) {
                            awaitingTarget = true
                            if (targetUrl.trimEnd('/') == AppConfig.HOME_URL.trimEnd('/')) {
                                view.reload()
                            } else {
                                // 只改 hash 不会重新加载文档，必须再 reload 一次让 SPA 带着登录态重启
                                view.evaluateJavascript(
                                    "location.replace(${JSONObject.quote(targetUrl)});location.reload();",
                                    null,
                                )
                            }
                        }
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        errorUrl = request.url.toString()
                        loading = false
                        refreshLayout.value?.isRefreshing = false
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                        errorUrl = request.url.toString()
                        loading = false
                        refreshLayout.value?.isRefreshing = false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onShowFileChooser(
                    view: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams,
                ): Boolean {
                    onPickFiles(callback)
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    if (UrlPolicy.isAllowedHost(request.origin.host)) {
                        request.grant(request.resources)
                    } else {
                        request.deny()
                    }
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                enqueueDownload(context, url, userAgent, contentDisposition, mimeType)
            }

            // 需要注入登录态时，先停在同源首页，注入完成后再跳目标页
            loadUrl(if (prefill.isNotEmpty()) AppConfig.HOME_URL else targetUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SwipeRefreshLayout(ctx).apply {
                    refreshLayout.value = this
                    setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
                    setOnRefreshListener { webView.reload() }
                    addView(
                        webView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
        )

        if (loading && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        errorUrl?.let { failed ->
            ErrorPane(
                onRetry = {
                    errorUrl = null
                    webView.reload()
                },
                onOpenInBrowser = { openExternal(context, Uri.parse(failed)) },
            )
        }

        if (!ready) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        onClose?.let { close ->
            TextButton(
                onClick = close,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun ErrorPane(onRetry: () -> Unit, onOpenInBrowser: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "页面没加载出来",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "检查一下网络连接，或者到浏览器里打开看看。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("重试") }
            Button(onClick = onOpenInBrowser) { Text("用浏览器打开") }
        }
    }
}

private fun isInternal(uri: Uri): Boolean = UrlPolicy.isAllowedHost(uri.host)

/** 把登录态写进官方站点的 localStorage，使网页详情免登录。 */
private fun storageScript(values: Map<String, String>): String {
    val body = values.entries.joinToString(separator = "") { (key, value) ->
        "localStorage.setItem(${JSONObject.quote(key)},${JSONObject.quote(value)});"
    }
    return "(function(){$body})()"
}

private fun openExternal(context: Context, uri: Uri) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // 设备上没有能打开该链接的应用，忽略即可
    }
}

private fun enqueueDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
) {
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setMimeType(mimeType)
        addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            URLUtil.guessFileName(url, contentDisposition, mimeType),
        )
    }
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}
