package com.testapp.scombz_widgets

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RelativeLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.testapp.scombz_widgets.data.network.ScombzClient

/**
 * ScombZ SSO ログイン用 Activity (V4)
 *
 * 修正内容：
 * - window.chrome などのブラウザ偽装スクリプトを addDocumentStartJavaScript() で
 *   ページスクリプト実行**前**に注入する（V3 は onPageFinished = 遅すぎた）
 * - navigator.webdriver = false で WebView 検出を回避
 * - navigator.userAgentData を Chrome 形式で提供
 */
class LoginActivity : ComponentActivity() {

    companion object {
        const val RESULT_COOKIES = "result_cookies"
        fun createIntent(context: Context) = Intent(context, LoginActivity::class.java)
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    /** ページ読み込み開始前に注入するブラウザ偽装スクリプト */
    private val BROWSER_SPOOF_SCRIPT = """
        (function() {
            // 1. Chrome ランタイムオブジェクト (Microsoft SSO の必須チェック)
            if (!window.chrome) {
                Object.defineProperty(window, 'chrome', {
                    value: {
                        runtime: {
                            id: undefined,
                            connect: function() { return { onMessage: { addListener: function(){} }, postMessage: function(){}, disconnect: function(){} }; },
                            sendMessage: function() {},
                            onConnect: { addListener: function(){} },
                            onMessage: { addListener: function(){} },
                            onDisconnect: { addListener: function(){} },
                            getManifest: function() { return {}; },
                            getURL: function(p) { return p; },
                            reload: function() {}
                        },
                        loadTimes: function() { return null; },
                        csi: function() { return { startE: Date.now(), onloadT: Date.now(), pageT: 0, tran: 15 }; },
                        app: {
                            getDetails: function() { return null; },
                            getIsInstalled: function() { return false; },
                            isInstalled: false,
                            InstallState: { DISABLED:'disabled', INSTALLED:'installed', NOT_INSTALLED:'not_installed' },
                            RunningState: { CANNOT_RUN:'cannot_run', READY_TO_RUN:'ready_to_run', RUNNING:'running' }
                        },
                        webstore: {
                            onInstallStageChanged: { addListener: function(){} },
                            onDownloadProgress: { addListener: function(){} }
                        }
                    },
                    writable: false,
                    configurable: true,
                    enumerable: true
                });
            }

            // 2. navigator.webdriver = false (WebView は true になる場合がある)
            try {
                Object.defineProperty(navigator, 'webdriver', {
                    get: function() { return false; },
                    configurable: true
                });
            } catch(e) {}

            // 3. navigator.userAgentData (Chrome 89+ の機能、ADFS が確認する場合あり)
            if (!navigator.userAgentData) {
                try {
                    Object.defineProperty(navigator, 'userAgentData', {
                        get: function() {
                            return {
                                brands: [
                                    { brand: 'Not)A;Brand', version: '24' },
                                    { brand: 'Chromium', version: '129' },
                                    { brand: 'Google Chrome', version: '129' }
                                ],
                                mobile: true,
                                platform: 'Android',
                                getHighEntropyValues: function(hints) {
                                    return Promise.resolve({
                                        platform: 'Android',
                                        platformVersion: '14.0.0',
                                        architecture: '',
                                        model: 'Pixel 8',
                                        uaFullVersion: '129.0.6668.100',
                                        bitness: '',
                                        fullVersionList: [
                                            { brand: 'Not)A;Brand', version: '24.0.0.0' },
                                            { brand: 'Chromium', version: '129.0.6668.100' },
                                            { brand: 'Google Chrome', version: '129.0.6668.100' }
                                        ]
                                    });
                                },
                                toJSON: function() {
                                    return { brands: this.brands, mobile: this.mobile, platform: this.platform };
                                }
                            };
                        },
                        configurable: true
                    });
                } catch(e) {}
            }

            // 4. navigator.standalone (iOS 由来だが一部のスクリプトが参照)
            if (typeof navigator.standalone === 'undefined') {
                try {
                    Object.defineProperty(navigator, 'standalone', {
                        get: function() { return false; },
                        configurable: true
                    });
                } catch(e) {}
            }

            // 5. window.external (古い IE 検出回避)
            if (!window.external) {
                try {
                    window.external = { AddFavorite: function(){} };
                } catch(e) {}
            }

            // 6. Permissions API (Chrome は持つ)
            if (!navigator.permissions) {
                try {
                    Object.defineProperty(navigator, 'permissions', {
                        value: {
                            query: function(desc) {
                                return Promise.resolve({ state: 'prompt', addEventListener: function(){} });
                            }
                        },
                        configurable: true
                    });
                } catch(e) {}
            }
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cookie 初期化
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
            setAcceptCookie(true)
        }

        // レイアウト構築
        val root = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = View.generateViewId()
            isIndeterminate = false
            max = 100
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                12
            ).also { it.addRule(RelativeLayout.ALIGN_PARENT_TOP) }
        }

        webView = WebView(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        root.addView(progressBar)
        root.addView(webView)
        setContentView(root)

        configureWebView()

        // ── ポイント: addDocumentStartJavaScript でページスクリプト実行前に注入 ──
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, BROWSER_SPOOF_SCRIPT, setOf("*"))
        }

        webView.loadUrl(ScombzClient.LOGIN_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val cookieManager = CookieManager.getInstance()

        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
            loadsImagesAutomatically = true

            // "wv" マーカーを含まない Chrome UA (Microsoft の UA 検出対策)
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240905.003) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/129.0.6668.100 Mobile Safari/537.36"
        }

        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // ── WebChromeClient ──────────────────────────────────────────────────
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            // window.open() ポップアップ (Microsoft 認証が別ウィンドウを開く場合)
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val popupWebView = WebView(this@LoginActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = webView.settings.userAgentString
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // ポップアップにも document-start スクリプトを注入
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(
                            this, BROWSER_SPOOF_SCRIPT, setOf("*")
                        )
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?, req: WebResourceRequest?
                        ) = false

                        override fun onPageFinished(v: WebView?, url: String?) {
                            cookieManager.flush()
                            if (url != null && isLoggedIn(url)) finishWithCookies(url)
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            v: WebView?, handler: SslErrorHandler?, error: SslError?
                        ) {
                            if (error?.url?.contains("shibaura-it.ac.jp") == true) handler?.proceed()
                            else handler?.cancel()
                        }
                    }
                }
                (resultMsg?.obj as? WebView.WebViewTransport)?.webView = popupWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        // ── WebViewClient ────────────────────────────────────────────────────
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ) = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE

                // addDocumentStartJavaScript 非対応端末のフォールバック
                // (onPageStarted は初期 HTML パース前の場合が多い)
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view?.evaluateJavascript(BROWSER_SPOOF_SCRIPT, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null) return
                CookieManager.getInstance().flush()

                // addDocumentStartJavaScript 非対応端末の追加フォールバック
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view?.evaluateJavascript(BROWSER_SPOOF_SCRIPT, null)
                }

                if (isLoggedIn(url)) finishWithCookies(url)
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: SslError?
            ) {
                if (error?.url?.contains("shibaura-it.ac.jp") == true) handler?.proceed()
                else handler?.cancel()
            }
        }
    }

    /**
     * ScombZ にログイン済みと判定できるか
     */
    private fun isLoggedIn(url: String): Boolean {
        if (!url.contains("scombz.shibaura-it.ac.jp")) return false
        if (url.contains("/login")) return false
        return url.contains("/portal/") ||
               url.contains("/lms/") ||
               url.contains("/home") ||
               Uri.parse(url).path.let { it == "/" || it.isNullOrEmpty() }
    }

    /**
     * Cookie を収集して Activity を終了
     */
    private fun finishWithCookies(url: String) {
        CookieManager.getInstance().flush()
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!cookies.isNullOrBlank()) {
            setResult(RESULT_OK, Intent().apply {
                putExtra(RESULT_COOKIES, cookies)
            })
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.apply {
            stopLoading()
            clearHistory()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
    }
}
