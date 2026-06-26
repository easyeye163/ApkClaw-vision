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
import com.apk.claw.android.base.BaseApp
import com.apk.claw.android.floating.voice.VoiceInteractionFloatWindow
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonStringSchema
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

    private fun tool(name: String, desc: String, vararg params: Pair<String, dev.langchain4j.model.chat.request.json.JsonSchemaElement>): ToolSpecification {
        val map = linkedMapOf<String, dev.langchain4j.model.chat.request.json.JsonSchemaElement>()
        val required = mutableListOf<String>()
        for ((pname, schema) in params) {
            map[pname] = schema
            required.add(pname)
        }
        return if (map.isEmpty()) {
            ToolSpecification.builder().name(name).description(desc).build()
        } else {
            ToolSpecification.builder().name(name).description(desc)
                .parameters(JsonObjectSchema.builder().addProperties(map).required(required).build())
                .build()
        }
    }

    private val TOOL_SPECS: List<ToolSpecification> by lazy {
        listOf(
            tool("addTree", "在坐标(x,z)添加一棵树",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "height" to JsonIntegerSchema.builder().description("树高(可选,默认4)").build(),
                "color" to JsonStringSchema.builder().description("颜色(可选,如GREEN/BROWN)").build()
            ),
            tool("addHouseBody", "在坐标添加房屋",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "width" to JsonIntegerSchema.builder().description("宽度").build(),
                "height" to JsonIntegerSchema.builder().description("高度").build(),
                "depth" to JsonIntegerSchema.builder().description("深度").build(),
                "color" to JsonStringSchema.builder().description("颜色").build()
            ),
            tool("addRock", "在坐标添加石头",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "scale" to JsonNumberSchema.builder().description("大小").build(),
                "color" to JsonStringSchema.builder().description("颜色").build()
            ),
            tool("addCloud", "在坐标添加云朵",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "y" to JsonIntegerSchema.builder().description("高度").build(),
                "scale" to JsonNumberSchema.builder().description("缩放").build()
            ),
            tool("addFlower", "在坐标添加花",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "color" to JsonStringSchema.builder().description("颜色").build()
            ),
            tool("addCrate", "在坐标添加箱子",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "size" to JsonNumberSchema.builder().description("大小").build(),
                "color" to JsonStringSchema.builder().description("颜色").build()
            ),
            tool("addSign", "在坐标添加告示牌",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "text" to JsonStringSchema.builder().description("文字").build()
            ),
            tool("addLamp", "在坐标添加路灯(带光源)",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build()
            ),
            tool("executeCode", "执行Three.js代码。可用:box(x,y,z,w,h,d,color),sphere(x,y,z,r,color),cylinder(x,y,z,rt,rb,h,color),cone(x,y,z,r,h,color),torus(x,y,z,r,tube,color)。常量:PI,RED,GREEN,BLUE,YELLOW,ORANGE,PURPLE,PINK,CYAN,WHITE,GRAY,BROWN,GOLD,SILVER",
                "code" to JsonStringSchema.builder().description("JavaScript代码").build()
            ),
            tool("removeDynamic", "删除指定ID的动态物体",
                "id" to JsonStringSchema.builder().description("物体ID").build()
            ),
            tool("clearDynamicObjects", "清除所有AI添加的动态物体")
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

        // 复用语音悬浮框：语音识别结果直接发送到 FPV 聊天面板
        VoiceInteractionFloatWindow.onVoiceResultCallback = { voiceText ->
            webView.evaluateJavascript(
                "if(window.__fpv_voiceInput)window.__fpv_voiceInput('${voiceText.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","")}');",
                null
            )
        }
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
    override fun onDestroy() { super.onDestroy(); VoiceInteractionFloatWindow.onVoiceResultCallback = null; localServer?.stop(); webView.destroy() }
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