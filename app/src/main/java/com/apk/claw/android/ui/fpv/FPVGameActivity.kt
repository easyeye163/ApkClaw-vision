package com.apk.claw.android.ui.fpv

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.apk.claw.android.agent.langchain.http.OkHttpClientBuilderAdapter
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.server.LocalWebServer
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException

class FPVGameActivity : BaseActivity() {

    companion object {
        private const val TAG = "FPVGame"
        private const val SERVER_PORT = 18080
    }

    private lateinit var webView: WebView
    private var localServer: LocalWebServer? = null
    private var chatModel: dev.langchain4j.model.chat.ChatModel? = null

    private val SYSTEM_PROMPT = """你是一个3D世界的AI建造助手。用户会描述他们想要建造的场景，你需要使用可用的工具函数来在3D世界中创建物体。
可用工具：addTree(x,z,height?,color?), addHouseBody(x,z,width?,height?,depth?,color?), addRock(x,z,scale?,color?), addCloud(x,z,y?,scale?), addFlower(x,z,color?), addCrate(x,z,size?,color?), addSign(x,z,text), addLamp(x,z), executeCode(code), removeDynamic(id), clearDynamicObjects()。
规则：1.只使用上面列出的工具 2.合理分布物体位置 3.颜色可用RED/GREEN/BLUE等名称或#hex 4.坐标范围-200到200 5.用中文回复 6.尽量一次调用多个工具""".trimIndent()

    private val TOOL_SPECS: List<ToolSpecification> by lazy {
        listOf(
            ToolSpecification.builder().name("addTree").description("在坐标添加树").addParameter("x","integer","X").addParameter("z","integer","Z").addParameter("height","integer","树高").addParameter("color","string","颜色").build(),
            ToolSpecification.builder().name("addHouseBody").description("添加房屋").addParameter("x","integer","X").addParameter("z","integer","Z").addParameter("width","integer","宽").addParameter("height","integer","高").addParameter("depth","integer","深").addParameter("color","string","颜色").build(),
            ToolSpecification.builder().name("addRock").description("添加石头").addParameter("x","integer","X").addParameter("z","integer","Z").addParameter("scale","number","大小").addParameter("color","string","颜色").build(),
            ToolSpecification.builder().name("addCloud").description("添加云朵").addParameter("x","integer","X").addParameter("z","integer","Z").addParameter("y","integer","高度").addParameter("scale","number","缩放").build(),
            ToolSpecification.builder().name("addFlower").description("添加花").addParameter("x","integer","X").addParameter("z","integer","Z").addParameter("color","string","颜色").build(),
            ToolSpecification.builder().name("addCrate").description("添加箱子").addParameter("x","integer","X").addParameter("z","integer","Z").addParameter("size","number","大小").addParameter("color","string","颜色").build(),
            ToolSpecification.builder().name("addSign").description("添加告示牌").addParameter("x","integer","X").addParameter("z","integer","Z").addParameter("text","string","文字").build(),
            ToolSpecification.builder().name("addLamp").description("添加路灯").addParameter("x","integer","X").addParameter("z","integer","Z").build(),
            ToolSpecification.builder().name("executeCode").description("执行Three.js代码。可用:box(x,y,z,w,h,d,color),sphere(x,y,z,r,color),cylinder(x,y,z,rt,rb,h,color),cone(x,y,z,r,h,color),torus(x,y,z,r,tube,color)。常量:PI,RED,GREEN,BLUE,YELLOW,ORANGE,PURPLE,PINK,CYAN,WHITE,GRAY,BROWN,GOLD,SILVER").addParameter("code","string","JS代码").build(),
            ToolSpecification.builder().name("removeDynamic").description("删除动态物体").addParameter("id","string","物体ID").build(),
            ToolSpecification.builder().name("clearDynamicObjects").description("清除所有动态物体").build()
        )
    }

    private fun ensureChatModel(): dev.langchain4j.model.chat.ChatModel? {
        if (chatModel != null) return chatModel
        val apiKey = KVUtils.getLlmApiKey()
        if (apiKey.isEmpty()) return null
        return try {
            val b = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(KVUtils.getLlmModelName().ifEmpty { "gpt-4o-mini" })
                .temperature(0.7)
                .httpClientBuilder(OkHttpClientBuilderAdapter())
            val baseUrl = KVUtils.getLlmBaseUrl()
            if (baseUrl.isNotEmpty()) b.baseUrl(baseUrl)
            // 不设 maxTokens，用模型默认最大值
            chatModel = b.build()
            chatModel
        } catch (e: Exception) { XLog.e(TAG, "ChatModel: ${e.message}"); null }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        webView = WebView(this)
        setContentView(webView)
        try { localServer = LocalWebServer(this, SERVER_PORT); localServer?.start() } catch (e: Exception) { XLog.e(TAG, "Server: ${e.message}") }
        webView.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false; allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE; mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true; loadWithOverviewMode = true
        }
        webView.addJavascriptInterface(FPVBridge(), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, r: android.webkit.WebResourceRequest?) = true
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean { XLog.d(TAG, "JS: ${msg?.message()}"); return true }
        }
        webView.loadUrl("http://127.0.0.1:$SERVER_PORT/")
    }

    override fun isApplyStatusBarPadding() = false
    override fun getDesignWidth() = 1080
    override fun onDestroy() { super.onDestroy(); localServer?.stop(); webView.destroy() }
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() }

    inner class FPVBridge {
        @JavascriptInterface
        fun callLLM(userMessage: String, historyJson: String, callbackId: String) {
            lifecycleScope.launch {
                try {
                    val model = withContext(Dispatchers.IO) { ensureChatModel() }
                    if (model == null) { callJs(callbackId, """{"error":"未配置LLM API"}"""); return@launch }
                    val result = withContext(Dispatchers.IO) {
                        val msgs = mutableListOf<ChatMessage>()
                        msgs.add(SystemMessage(SYSTEM_PROMPT))
                        try { val h = JSONArray(historyJson); for (i in 0 until h.length()) { val m = h.getJSONObject(i); val r = m.getString("role"); val c = m.getString("content"); if (r=="user") msgs.add(UserMessage.from(c)) else if (r=="assistant") msgs.add(AiMessage.from(c)) } } catch (_: Exception) {}
                        msgs.add(UserMessage.from(userMessage))
                        model.chat(ChatRequest.builder().messages(msgs).toolSpecifications(TOOL_SPECS).build())
                    }
                    val json = JSONObject(); json.put("text", result.aiMessage().text() ?: "")
                    val tc = result.aiMessage().toolExecutionRequests()
                    if (tc != null && tc.isNotEmpty()) { val arr = JSONArray(); for (t in tc) { val o = JSONObject(); o.put("name", t.name()); o.put("args", t.arguments()); arr.put(o) }; json.put("toolCalls", arr) }
                    callJs(callbackId, json.toString())
                } catch (e: SocketTimeoutException) { callJs(callbackId, """{"error":"请求超时"}""") }
                catch (e: ConnectException) { callJs(callbackId, """{"error":"无法连接LLM"}""") }
                catch (e: Exception) { callJs(callbackId, """{"error":"${e.message?.replace("\"","'")}"}""") }
            }
        }
        @JavascriptInterface fun getDeviceInfo() = JSONObject().apply { put("model",Build.MODEL); put("sdk",Build.VERSION.SDK_INT); put("width",resources.displayMetrics.widthPixels); put("height",resources.displayMetrics.heightPixels) }.toString()
        @JavascriptInterface fun exitGame() { runOnUiThread { finish() } }
        @JavascriptInterface fun vibrate(ms: Long) { try { val v = getSystemService(Vibrator::class.java); if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(ms) } catch (_:Exception) {} }
    }

    private fun callJs(id: String, data: String) {
        runOnUiThread { webView.evaluateJavascript("window.__fpv_llmCallback('$id','${data.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","")}');", null) }
    }
}