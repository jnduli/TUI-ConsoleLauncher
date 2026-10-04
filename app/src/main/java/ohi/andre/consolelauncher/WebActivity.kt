package ohi.andre.consolelauncher

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.security.KeyChain
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.ConsoleMessage
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Mobile-friendly defaults: without wide viewport support, WebView renders a fixed 980px desktop
 * viewport, so mobile-only elements (hamburger menus, responsive nav bars) driven by CSS media
 * queries never activate. */
fun WebView.applyWebLauncherSettings() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    // Blocks http:// sub-resources from loading inside an https:// page.
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    // Menu items that open links via window.open()/target="_blank" are silently dropped
    // without multi-window support - LauncherWebChromeClient.onCreateWindow handles the rest.
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(true)

    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
}

/** Routes download links (which WebView can't handle itself) through the system DownloadManager. */
fun createLauncherDownloadListener(context: Context): DownloadListener =
    DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("User-Agent", userAgent)
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(context, R.string.web_download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, R.string.web_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

/** Routes non-http(s) links (tel:, mailto:, geo:, intent://, market://, ...) - which WebView can't
 * render itself - out to the system, and reports page-load state back to the caller. */
class LauncherWebViewClient(
    private val context: Context,
    private val onPageStarted: () -> Unit,
    private val onMainFrameError: (String?) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (url.scheme == "http" || url.scheme == "https") return false

        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.web_no_app_for_link, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        onPageStarted.invoke()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onMainFrameError(error.description?.toString())
    }

    /** WebView never consults the system KeyChain on its own (the default implementation cancels),
     * so sites that require a client certificate fail unless we let the user pick an installed one. */
    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        val activity = context as? Activity ?: return request.cancel()
        KeyChain.choosePrivateKeyAlias(
            activity,
            { alias ->
                if (alias == null) return@choosePrivateKeyAlias request.cancel()
                // The callback runs on a background thread, so the blocking KeyChain calls are fine here
                try {
                    val key = KeyChain.getPrivateKey(activity, alias)
                    val chain = KeyChain.getCertificateChain(activity, alias)
                    if (key != null && chain != null) request.proceed(key, chain) else request.cancel()
                } catch (e: Exception) {
                    request.cancel()
                }
            },
            request.keyTypes, request.principals, request.host, request.port, null,
        )
    }
}

/** Handles the browser-chrome side of WebView: load progress/title, links that open via
 * window.open()/target="_blank" (dropped without multi-window support), and file-input pickers. */
class LauncherWebChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String) -> Unit,
    private val onShowFileChooserRequested: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgress.invoke(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!title.isNullOrBlank()) onTitle.invoke(title)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        val popup = WebView(view.context)
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }
        }
        (resultMsg.obj as WebView.WebViewTransport).webView = popup
        resultMsg.sendToTarget()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        callback: ValueCallback<Array<Uri>>,
        params: FileChooserParams,
    ): Boolean = onShowFileChooserRequested(callback, params)

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val level = when (consoleMessage.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
            else -> Log.DEBUG
        }
        Log.println(level, "WebConsole", "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
        return true
    }
}

/** The in-app browser screen: the WebView itself, a top loading bar while a page is in flight,
 * and a full-screen error state (with retry) when the main frame fails to load. */
@Composable
fun WebScreen(
    factory: (Context) -> WebView,
    progress: Int,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = factory)

        if (progress in 1..99 && errorMessage == null) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        errorMessage?.let { message ->
            WebLoadErrorScreen(message = message, onRetry = onRetry)
        }
    }
}

@Composable
private fun WebLoadErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.web_load_error_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.web_retry))
        }
    }
}

class WebActivity : AppCompatActivity() {
    companion object {

        const val URL_EXTRA = "EXTRA_URL"
    }

    private lateinit var myWebView: WebView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null

            val uris = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { WebChromeClient.FileChooserParams.parseResult(result.resultCode, it) }
            } else null
            callback?.onReceiveValue(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var loadProgress by remember { mutableStateOf(0) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            WebScreen(
                factory = { context ->
                    createWebView(
                        context = context,
                        restoreFrom = savedInstanceState,
                        onProgress = { loadProgress = it },
                        onTitle = { title = it },
                        onPageStarted = { errorMessage = null },
                        onMainFrameError = { errorMessage = it },
                    )
                },
                progress = loadProgress,
                errorMessage = errorMessage,
                onRetry = {
                    errorMessage = null
                    myWebView.reload()
                },
            )
        }

        setUpBackNavigation()
    }

    private fun createWebView(
        context: Context,
        restoreFrom: Bundle?,
        onProgress: (Int) -> Unit,
        onTitle: (String) -> Unit,
        onPageStarted: () -> Unit,
        onMainFrameError: (String?) -> Unit,
    ): WebView {
        return WebView(context).also { webView ->
            myWebView = webView
            // Without an explicit size hint, vh-based layouts can render against a stale,
            // near-zero viewport before AndroidView finishes measuring this WebView.
            webView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            webView.applyWebLauncherSettings()

            if (restoreFrom != null) webView.restoreState(restoreFrom)

            webView.webViewClient = LauncherWebViewClient(context, onPageStarted, onMainFrameError)
            webView.webChromeClient = LauncherWebChromeClient(
                onProgress = onProgress,
                onTitle = onTitle,
                onShowFileChooserRequested = ::launchFileChooser,
            )
            webView.setDownloadListener(createLauncherDownloadListener(context))

            if (restoreFrom == null) loadUrlFromIntent(intent)
        }
    }

    private fun launchFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        return try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            filePathCallback = null
            false
        }
    }

    private fun setUpBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (myWebView.canGoBack()) {
                    myWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.web_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.refresh_page) {
            myWebView.reload()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        loadUrlFromIntent(intent)
    }

    private fun loadUrlFromIntent(intent: android.content.Intent) {
        val url = intent.getStringExtra(URL_EXTRA) ?: "https://google.com"
        myWebView.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::myWebView.isInitialized) myWebView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        if (::myWebView.isInitialized) myWebView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::myWebView.isInitialized) myWebView.onResume()
    }

    override fun onDestroy() {
        if (::myWebView.isInitialized) {
            (myWebView.parent as? ViewGroup)?.removeView(myWebView)
            myWebView.destroy()
        }
        super.onDestroy()
    }
}
