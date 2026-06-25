package com.apk.claw.android.ui.fpv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.tool.ToolRegistry
import com.apk.claw.android.tool.impl.fpv.FPVToolBridge
import org.json.JSONObject

/**
 * FPV 飞行游戏 - 全屏 WebView
 *
 * 加载本地 assets/web/fpv/index.html 中的 3D 飞行探索游戏。
 * 通过 JavaScript Bridge 将 ApkClaw 的原生能力桥接到游戏中。
 */
class FPVGameActivity : BaseActivity() {

    companion object {
        private const val TAG = "FPVGameActivity"

        fun start(context: Context) {
            val intent = Intent(context, FPVGameActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive mode for the game
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        setContentView(R.layout.activity_fpv_game)

        loadingOverlay = findViewById(R.id.fpvLoadingOverlay)
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = findViewById(R.id.fpvWebView)
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
            }

            // JavaScript Bridge for native-Web communication
            addJavascriptInterface(FPVBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request == null) return null
                    val url = request.url.toString()

                    // Intercept file:// scheme requests for our game assets
                    if (url.startsWith("file:///android_asset/web/fpv/")) {
                        val assetPath = url.removePrefix("file:///android_asset/")
                        try {
                            val mimeType = guessMimeType(assetPath)
                            val inputStream = assets.open(assetPath)
                            return WebResourceResponse(mimeType, "UTF-8", inputStream)
                        } catch (e: Exception) {
                            // Asset not found, let WebView handle it
                        }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Register WebView with FPVToolBridge for native Agent tools
                    FPVToolBridge.registerWebView(this@apply)
                    // Hide loading overlay with fade animation
                    loadingOverlay.animate().alpha(0f).setDuration(500).withEndAction {
                        loadingOverlay.visibility = View.GONE
                    }.start()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    android.util.Log.d("FPV-Web", message?.message() ?: "")
                    return true
                }
            }

            // Load the game from local assets
            loadUrl("file:///android_asset/web/fpv/index.html")
        }
    }

    private fun guessMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".woff") || path.endsWith(".woff2") -> "font/woff"
            path.endsWith(".txt") -> "text/plain"
            path.endsWith(".xml") -> "text/xml"
            else -> "application/octet-stream"
        }
    }

    /**
     * JavaScript Bridge - 暴露给 WebView 的 Java 对象
     * 游戏中的 AI 建造功能可以通过 window.AndroidBridge 访问原生能力
     */
    inner class FPVBridge {

        /**
         * 游戏内 AI 建造 - 调用 LLM 生成工具调用
         * 注意: 同步 JS Bridge 不适合耗时操作
         * 游戏应通过 window.__FPV_CONFIG__.buildApiUrl 配置 API 地址
         * @param prompt 用户输入的建造指令
         * @param historyJson 历史对话 JSON 字符串
         * @return LLM 返回的 JSON 结果
         */
        @JavascriptInterface
        fun callLLM(prompt: String, historyJson: String): String {
            return """{"text":"请在设置中配置 AI 建造 API 地址 (window.__FPV_CONFIG__.buildApiUrl)","toolCalls":[]}"""
        }

        /**
         * 获取设备信息
         */
        @JavascriptInterface
        fun getDeviceInfo(): String {
            return JSONObject().apply {
                put("platform", "android")
                put("model", Build.MODEL)
                put("sdk", Build.VERSION.SDK_INT)
            }.toString()
        }

        /**
         * 退出游戏
         */
        @JavascriptInterface
        fun exitGame() {
            runOnUiThread { finish() }
        }

        /**
         * 震动反馈
         */
        @JavascriptInterface
        fun vibrate(duration: Long) {
            try {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            android.os.VibrationEffect.createOneShot(
                                duration,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // Game runs in fullscreen - no status bar padding
    override fun applyPaddingToRootView(rootView: View) { }
    override fun isApplyStatusBarPadding(): Boolean = false

    override fun onResume() {
        super.onResume()
        webView.onResume()
        FPVToolBridge.registerWebView(webView)
        // Register FPV building tools so the Agent can control the 3D world
        ToolRegistry.getInstance().registerFPVTools()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        FPVToolBridge.unregisterWebView()
        // Note: FPV tools stay registered for background Agent tasks
    }

    override fun onDestroy() {
        FPVToolBridge.unregisterWebView()
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }
}