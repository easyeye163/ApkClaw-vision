package com.apk.claw.android.ui.fpv

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    /** 进入游戏前预申请录音权限 */
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun preRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val SYSTEM_PROMPT = """你是一个3D世界的AI建造助手，用户用自然语言描述想建造的物体，你用工具函数在3D世界中创建。

## 可用工具

快捷工具（简单物体）：
- addTree(x,z), addHouseBody(x,z), addRock(x,z), addCloud(x,z), addFlower(x,z), addCrate(x,z), addSign(x,z,text), addLamp(x,z)
- clearDynamicObjects() — 清除所有AI添加的物体

复合建造工具（核心）：
- addCompositeObject(x,z,rotationY?,parts) — 用基础形状组合创建任意复杂物体。parts是部件数组，每个部件：
  {type,ox,oy,oz,w,h,d,r,rt,rb,h,tube,color,roughness?,metalness?,emissive?,transparent?,opacity?,rx?,ry?,rz?,sx?,sy?,sz?}
  type可选: box/sphere/cylinder/cone/torus/plane
  ox/oy/oz是相对偏移。box用w/h/d, sphere用r, cylinder用rt/rb/h, cone用r/h, torus用r/tube
  color可用RED/GREEN/BLUE/YELLOW/GOLD/ORANGE/PURPLE/PINK/WHITE/BLACK/BROWN/WOOD/SILVER/STEEL/BRONZE等40+颜色名或#hex

## 建造规则
1. 优先使用addCompositeObject来建造复杂物体（车辆、建筑、动物、家具、武器等），用多个部件组合
2. 简单物体（树/石头/房子/云/花/灯）可用快捷工具
3. 坐标范围-200到200，高度自动适配地形
4. 用中文回复用户
5. 尽量一次调用完成建造

## 示例思路

建造红色汽车：
addCompositeObject → parts: [{type:box,ox:0,oy:0.5,oz:0,w:4,h:1.2,d:2,color:RED}, {type:box,ox:-0.3,oy:1.4,oz:0,w:2,h:0.8,d:1.8,color:RED}, {type:sphere,ox:-1.8,oy:0.5,oz:0.9,r:0.45,color:BLACK}, {type:sphere,ox:-1.8,oy:0.5,oz:-0.9,r:0.45,color:BLACK}, {type:sphere,ox:1.8,oy:0.5,oz:0.9,r:0.45,color:BLACK}, {type:sphere,ox:1.8,oy:0.5,oz:-0.9,r:0.45,color:BLACK}, {type:box,ox:-0.8,oy:1.6,oz:0.5,w:1,h:0.5,d:0.8,color:CYAN,transparent:true,opacity:0.5}, {type:box,ox:0.8,oy:1.6,oz:0.5,w:1,h:0.5,d:0.8,color:CYAN,transparent:true,opacity:0.5}, {type:sphere,ox:2.2,oy:0.6,oz:0,r:0.3,color:YELLOW,emissive:YELLOW}]

建造灯塔：
addCompositeObject → parts: [{type:cylinder,ox:0,oy:5,oz:0,rt:0.8,rb:1.5,h:10,color:WHITE}, {type:cylinder,ox:0,oy:10.5,oz:0,rt:1.8,rb:0.8,h:1,color:RED}, {type:sphere,ox:0,oy:11.2,oz:0,r:1.2,color:YELLOW,emissive:YELLOW,emissiveIntensity:1.0}]

建造飞机：
addCompositeObject → parts: [{type:box,ox:0,oy:0,oz:0,w:6,h:1,d:1.2,color:SILVER}, {type:box,ox:0,oy:0.5,oz:0,w:1.5,h:0.8,d:5,color:SILVER}, {type:box,ox:0,oy:0,oz:-1.5,w:8,h:0.2,d:1.5,color:SILVER}, {type:cylinder,ox:0,oy:0.3,oz:2.5,rt:0.1,rb:0.4,h:2,color:GRAY}, {type:cone,ox:0,oy:0,oz:-3.5,r:0.6,h:1.5,color:RED}]

建造桥：
addCompositeObject → parts: [{type:box,ox:0,oy:2,oz:0,w:20,h:0.5,d:3,color:GRAY}, {type:box,ox:-9,oy:1,oz:0,w:1,h:4,d:1,color:BROWN}, {type:box,ox:9,oy:1,oz:0,w:1,h:4,d:1,color:BROWN}, {type:cylinder,ox:-5,oy:2,oz:1.2,rt:0.1,rb:0.1,h:0.5,color:GRAY}, {type:cylinder,ox:0,oy:2,oz:1.2,rt:0.1,rb:0.1,h:0.5,color:GRAY}, {type:cylinder,ox:5,oy:2,oz:1.2,rt:0.1,rb:0.1,h:0.5,color:GRAY}]""".trimIndent()

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
                "z" to JsonIntegerSchema.builder().description("Z坐标").build()
            ),
            tool("addHouseBody", "在坐标添加房屋",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build()
            ),
            tool("addRock", "在坐标添加石头",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build()
            ),
            tool("addCloud", "在坐标添加云朵",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build()
            ),
            tool("addFlower", "在坐标添加花",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build()
            ),
            tool("addCrate", "在坐标添加箱子",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build()
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
            tool("executeCode", "执行Three.js代码",
                "code" to JsonStringSchema.builder().description("JavaScript代码").build()
            ),
            tool("removeDynamic", "删除指定ID的动态物体",
                "id" to JsonStringSchema.builder().description("物体ID").build()
            ),
            tool("clearDynamicObjects", "清除所有AI添加的动态物体"),
            tool("addCompositeObject", "用基础形状组合创建任意复杂物体(车辆/建筑/动物/家具/武器等)。parts数组中每个部件: {type,ox,oy,oz,w,h,d,r,rt,rb,h,tube,color,roughness?,metalness?,emissive?,transparent?,opacity?,rx?,ry?,rz?,sx?,sy?,sz?}。type可选box/sphere/cylinder/cone/torus/plane。ox/oy/oz是相对偏移。box用w/h/d,sphere用r,cylinder用rt/rb/h,cone用r/h,torus用r/tube。color可用RED/GREEN/BLUE等名称或#hex",
                "x" to JsonIntegerSchema.builder().description("X坐标").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标").build(),
                "rotationY" to JsonIntegerSchema.builder().description("整体Y轴旋转角度(可选)").build(),
                "parts" to JsonStringSchema.builder().description("部件数组JSON字符串").build()
            )
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

        // 语音悬浮框回调：识别结果发送到FPV聊天面板
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
        // 进入游戏前预申请录音权限，避免游戏中申请导致黑屏
        preRequestAudioPermission()
    }

    override fun isApplyStatusBarPadding() = false
    override fun getDesignWidth() = 1080
    override fun onDestroy() {
        super.onDestroy()
        VoiceInteractionFloatWindow.onVoiceResultCallback = null
        try { VoiceInteractionFloatWindow.dismiss() } catch (_: Exception) {}
        localServer?.stop()
        webView.destroy()
    }
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

        /**
         * 弹出/关闭语音悬浮框（权限已在onCreate中预申请）
         */
        @JavascriptInterface
        fun showVoiceFloat() {
            runOnUiThread { doShowVoiceFloat() }
        }

        private fun doShowVoiceFloat() {
            try {
                if (VoiceInteractionFloatWindow.isShowing()) {
                    VoiceInteractionFloatWindow.dismiss()
                } else {
                    VoiceInteractionFloatWindow.onVoiceResultCallback = { voiceText ->
                        webView.evaluateJavascript(
                            "if(window.__fpv_voiceInput)window.__fpv_voiceInput('${voiceText.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","")}');",
                            null
                        )
                    }
                    VoiceInteractionFloatWindow.show(application as BaseApp)
                }
            } catch (e: Exception) {
                XLog.e(TAG, "doShowVoiceFloat: ${e.message}")
            }
        }
    }

    private fun callJs(id: String, data: String) {
        val encoded = android.util.Base64.encodeToString(data.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        runOnUiThread { webView.evaluateJavascript("window.__fpv_llmCallback('$id', '$encoded');", null) }
    }
}