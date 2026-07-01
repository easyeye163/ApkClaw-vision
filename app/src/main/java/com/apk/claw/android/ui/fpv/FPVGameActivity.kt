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
import com.apk.claw.android.voice.VoiceInputController
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
    private var fpvVoiceController: VoiceInputController? = null

    private val SYSTEM_PROMPT = """你是一个3D世界的AI自由建造助手。用户会用自然语言描述想建造的任何物体或场景，你需要用工具在3D世界中创建它们。

## 可用工具

**快捷预制物体（适合简单需求）：**
addTree(x,z,height?,color?), addHouseBody(x,z,width?,height?,depth?,color?), addRock(x,z,scale?,color?), addCloud(x,z,y?,scale?), addFlower(x,z,color?), addCrate(x,z,size?,color?), addSign(x,z,text), addLamp(x,z)

**自由建造（核心工具，可建造任意物体）：**
addCompositeObject(x,z,rotationY?,parts) - 用基础形状组合创建任意复杂物体。parts是部件数组，每个部件：
  {type:"box/sphere/cylinder/cone/torus/plane", ox,oy,oz, w,h,d, r,rt,rb,h,tube, color, roughness?,metalness?,emissive?,transparent?,opacity?,rx?,ry?,rz?,sx?,sy?,sz?}
  - type: 形状类型（默认box）
  - ox,oy,oz: 相对于物体中心的偏移坐标
  - box参数: w(宽) h(高) d(深)
  - sphere参数: r(半径)
  - cylinder参数: rt(顶半径) rb(底半径) h(高)
  - cone参数: r(半径) h(高)
  - torus参数: r(环半径) tube(管半径)
  - plane参数: w(宽) h(高)
  - color: RED/GREEN/BLUE/YELLOW/ORANGE/PURPLE/PINK/CYAN/WHITE/GRAY/BLACK/BROWN/GOLD/SILVER/DARK_RED/DARK_BLUE/LIGHT_BLUE/LIGHT_GREEN/SKY_BLUE/CREAM/WOOD/STONE/BRICK/SAND/TURQUOISE/CORAL/LIME/NAVY/MAROON/TEAL/OLIVE/AQUA/SALMON/KHAKI/IVORY/CHOCOLATE/FIRE_RED/ICE_BLUE/FOREST_GREEN/ROSE/VIOLET/INDIGO 或 #hex
  - rx,ry,rz: 旋转角度(度)
  - sx,sy,sz: 缩放

**其他：**
executeCode(code), removeDynamic(id), clearDynamicObjects()

## 建造规则
1. 优先使用addCompositeObject来建造复杂物体（车辆、建筑、动物、家具、武器等），用多个部件组合
2. 简单的自然物体可用快捷工具（树、石头、花等）
3. 合理分布物体位置，坐标范围-200到200
4. 注意部件的偏移坐标(ox,oy,oz)让物体各部分正确拼合
5. 用中文回复用户，简短描述你建造了什么
6. 尽量一次调用多个工具来建造完整场景
7. 物体默认放在相机前方附近，无需指定坐标时传x:0,z:0即可自动放置

## 建造示例思路
- 红色汽车：车体(box)+车顶(box)+4个轮子(cylinder)+车窗(box,transparent)+车灯(sphere,emissive)
- 塔楼：底座(box)+多层墙体(box)+窗户(box)+尖顶(cone)+旗帜(plane)
- 桥梁：桥面(box)+桥墩(cylinder×2)+栏杆(box)
- 飞机：机身(cylinder)+机翼(box×2)+尾翼(box)+引擎(cylinder×2)""".trimIndent()

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
            tool("addCompositeObject", "用基础形状组合创建任意复杂物体(车辆/建筑/动物/家具/武器等)。parts数组中每个部件: {type,ox,oy,oz,w,h,d,r,rt,rb,h,tube,color,roughness?,metalness?,emissive?,transparent?,opacity?,rx?,ry?,rz?,sx?,sy?,sz?}。type可选box/sphere/cylinder/cone/torus/plane。ox/oy/oz是相对偏移。box用w/h/d,sphere用r,cylinder用rt/rb/h,cone用r/h,torus用r/tube。color可用RED/GREEN/BLUE等名称或#hex",
                "x" to JsonIntegerSchema.builder().description("X坐标(0=自动放置在相机前)").build(),
                "z" to JsonIntegerSchema.builder().description("Z坐标(0=自动放置在相机前)").build(),
                "rotationY" to JsonIntegerSchema.builder().description("整体旋转角度(可选,默认0)").build(),
                "parts" to JsonStringSchema.builder().description("部件JSON数组,如[{type:'box',ox:0,oy:1,w:4,h:2,d:2,color:'RED'},{type:'cylinder',ox:-1.5,oy:0,oz:1,r:0.5,h:0.3,color:'BLACK'}]").build()
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
    override fun onDestroy() { super.onDestroy(); VoiceInteractionFloatWindow.onVoiceResultCallback = null; try { VoiceInteractionFloatWindow.dismiss() } catch (_: Exception) {}; fpvVoiceController?.destroy(); fpvVoiceController = null; localServer?.stop(); webView.destroy() }
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
        @JavascriptInterface
        fun startStt() {
            runOnUiThread {
                try {
                    fpvVoiceController?.destroy()
                    val controller = VoiceInputController(applicationContext)
                    controller.listener = object : VoiceInputController.Listener {
                        override fun onListeningStarted() {
                            webView.evaluateJavascript("if(window.__fpv_sttState)window.__fpv_sttState('listening')", null)
                        }
                        override fun onTranscribing() {
                            webView.evaluateJavascript("if(window.__fpv_sttState)window.__fpv_sttState('transcribing')", null)
                        }
                        override fun onFinalResult(text: String) {
                            val escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
                            webView.evaluateJavascript("if(window.__fpv_sttResult)window.__fpv_sttResult('$escaped')", null)
                            fpvVoiceController = null
                        }
                        override fun onError(errorCode: Int, message: String) {
                            val em = message.replace("'", "\\'")
                            webView.evaluateJavascript("if(window.__fpv_sttError)window.__fpv_sttError('$em')", null)
                            fpvVoiceController = null
                        }
                    }
                    fpvVoiceController = controller
                    controller.startListening()
                } catch (e: Exception) {
                    XLog.e(TAG, "startStt: ${e.message}")
                    val em = (e.message ?: "unknown").replace("'", "\\'")
                    webView.evaluateJavascript("if(window.__fpv_sttError)window.__fpv_sttError('$em')", null)
                }
            }
        }
        @JavascriptInterface
        fun stopStt() {
            runOnUiThread {
                fpvVoiceController?.stopListening()
            }
        }
    }

    private fun callJs(id: String, data: String) {
        val encoded = android.util.Base64.encodeToString(data.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        runOnUiThread { webView.evaluateJavascript("window.__fpv_llmCallback('$id', atob('$encoded'));", null) }
    }
}