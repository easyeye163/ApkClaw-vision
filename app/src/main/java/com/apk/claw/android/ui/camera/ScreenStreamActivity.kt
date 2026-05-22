package com.apk.claw.android.ui.camera

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.floating.voice.VoiceInteractionFloatWindow
import com.apk.claw.android.service.ClawAccessibilityService
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.vision.VisionFrameBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * 屏幕流 Activity
 *
 * 使用无障碍服务截图功能捕获屏幕画面，发送到 LLM Vision API 进行分析。
 * 支持：
 * - 自动监控循环（每5秒截图→发LLM→TTS播报）
 * - 语音介入更新监控任务
 * - 不需要 MediaProjection 权限，不会显示"共享中"
 */
class ScreenStreamActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenStreamActivity"
        private const val MONITOR_INTERVAL_MS = 5000L
        private const val SCREENSHOT_TIMEOUT_MS = 5000L
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvMonitorStatus: TextView
    private lateinit var btnToggleMonitor: Button
    private lateinit var btnVoiceFloat: Button
    private lateinit var btnCloseScreen: ImageButton

    private var ttsManager: com.apk.claw.android.floating.voice.TtsManager? = null
    private var isMonitoring = false

    // 自动监控循环
    private var monitorScope: CoroutineScope? = null
    private var monitorJob: Job? = null

    // 监控提示词（可被语音介入更新）
    @Volatile
    private var monitorPrompt: String = "请分析当前屏幕画面，描述你看到的内容。"

    // 监控轮次计数
    private var monitorRound: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFullscreen()
        setContentView(R.layout.activity_screen_stream)

        tvStatus = findViewById(R.id.tv_screen_status)
        tvMonitorStatus = findViewById(R.id.tv_monitor_status)
        btnToggleMonitor = findViewById(R.id.btn_toggle_monitor)
        btnVoiceFloat = findViewById(R.id.btn_voice_float)
        btnCloseScreen = findViewById(R.id.btn_close_screen)

        bindButtons()
        checkAccessibilityService()
    }

    private fun setupFullscreen() {
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    /**
     * 检查无障碍服务是否已开启
     */
    private fun checkAccessibilityService() {
        val a11yService = ClawAccessibilityService.getInstance()
        if (a11yService != null) {
            tvStatus.text = "屏幕监控就绪（无障碍截图）"
            XLog.i(TAG, "Accessibility service available, screen monitoring ready")
        } else {
            tvStatus.text = "⚠️ 请先开启无障碍服务"
            XLog.w(TAG, "Accessibility service not running, screen monitoring unavailable")
            Toast.makeText(this, "屏幕监控需要无障碍服务，请先在设置中开启", Toast.LENGTH_LONG).show()
        }
    }

    private fun bindButtons() {
        btnCloseScreen.setOnClickListener {
            finish()
        }

        btnToggleMonitor.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }

        btnVoiceFloat.setOnClickListener {
            if (VoiceInteractionFloatWindow.isShowing()) {
                VoiceInteractionFloatWindow.dismiss()
                btnVoiceFloat.text = "语音助手"
            } else {
                VoiceInteractionFloatWindow.onVoiceResultCallback = { text ->
                    showResultMessage("你: $text")

                    if (isMonitoring) {
                        monitorPrompt = text
                        showResultMessage("助手: 已更新监控任务: $text")
                        XLog.i(TAG, "Monitor prompt updated by voice: $text")
                    }

                    sendToLlmWithFrame(text)
                }
                VoiceInteractionFloatWindow.show(application as ClawApplication)
                btnVoiceFloat.text = "隐藏语音"
            }
        }
    }

    /**
     * 使用无障碍服务截取当前屏幕
     * @return JPEG 字节数组，失败返回 null
     */
    private fun captureScreenJpeg(): ByteArray? {
        val a11yService = ClawAccessibilityService.getInstance()
        if (a11yService == null) {
            XLog.w(TAG, "Accessibility service not available for screenshot")
            return null
        }

        try {
            val bitmap = a11yService.takeScreenshot(SCREENSHOT_TIMEOUT_MS) ?: run {
                XLog.w(TAG, "Screenshot returned null")
                return null
            }

            // 缩小图片以减少发送大小（最大宽度720px）
            val scaledBitmap = if (bitmap.width > 720) {
                val scale = 720f / bitmap.width
                Bitmap.createScaledBitmap(bitmap, 720, (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }

            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val jpegBytes = stream.toByteArray()

            // 回收 bitmap
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            XLog.i(TAG, "Screen captured: ${jpegBytes.size / 1024}KB")
            return jpegBytes
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to capture screen", e)
            return null
        }
    }

    /**
     * 开始监控：启动自动循环
     */
    private fun startMonitoring() {
        if (isMonitoring) return

        val a11yService = ClawAccessibilityService.getInstance()
        if (a11yService == null) {
            Toast.makeText(this, "屏幕监控需要无障碍服务，请先开启", Toast.LENGTH_SHORT).show()
            return
        }

        if (!VoiceInteractionFloatWindow.isShowing()) {
            VoiceInteractionFloatWindow.onVoiceResultCallback = { text ->
                showResultMessage("你: $text")
                monitorPrompt = text
                showResultMessage("助手: 已更新监控任务: $text")
                XLog.i(TAG, "Monitor prompt updated by voice: $text")
                sendToLlmWithFrame(text)
            }
            VoiceInteractionFloatWindow.show(application as ClawApplication)
            btnVoiceFloat.text = "隐藏语音"
        }

        isMonitoring = true
        monitorRound = 0

        btnToggleMonitor.text = "停止监控"
        tvMonitorStatus.text = "屏幕监控运行中..."
        tvMonitorStatus.visibility = View.VISIBLE

        showResultMessage("屏幕监控已启动，任务: $monitorPrompt")

        monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        monitorJob = monitorScope?.launch {
            while (isActive && isMonitoring) {
                try {
                    monitorRound++
                    sendMonitorFrame()
                } catch (e: Exception) {
                    XLog.e(TAG, "Monitor loop error at round $monitorRound", e)
                }
                delay(MONITOR_INTERVAL_MS)
            }
        }

        XLog.i(TAG, "Screen auto monitoring started, prompt: $monitorPrompt")
    }

    private fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorScope?.cancel()
        monitorJob = null
        monitorScope = null

        btnToggleMonitor.text = "开始监控"
        tvMonitorStatus.text = "监控已停止"

        showResultMessage("屏幕监控已停止 (共${monitorRound}轮)")
        XLog.i(TAG, "Screen auto monitoring stopped after $monitorRound rounds")
    }

    private suspend fun sendMonitorFrame() {
        // 使用无障碍截图获取当前屏幕
        val jpegBytes = withContext(Dispatchers.IO) {
            captureScreenJpeg()
        }

        if (jpegBytes == null) {
            XLog.w(TAG, "Monitor round $monitorRound: screenshot failed, skip")
            showResultMessage("[${monitorRound}] 截图失败，跳过本轮")
            return
        }

        val currentPrompt = monitorPrompt
        XLog.i(TAG, "Monitor round $monitorRound: analyzing screen (${jpegBytes.size / 1024}KB), prompt=$currentPrompt")

        val reply = callLlmVision(
            systemPrompt = "你是一个屏幕监控助手。用户会给你屏幕画面和监控任务。请根据任务要求分析屏幕内容，用简洁的语言描述你看到的情况。如果检测到用户关注的内容或变化，请明确提醒。",
            userText = currentPrompt,
            frameJpegBytes = jpegBytes
        )

        if (reply != null) {
            val displayText = "[${monitorRound}] 助手: $reply"
            showResultMessage(displayText)

            if (KVUtils.isTtsEnabled()) {
                ttsManager?.stop()
                speakReply(reply)
            }
        }
    }

    private fun showResultMessage(text: String) {
        VoiceInteractionFloatWindow.showMonitorResult(text)
    }

    private fun sendToLlmWithFrame(userText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 先隐藏悬浮窗避免截图包含悬浮窗内容
                val floatView = com.lzf.easyfloat.EasyFloat.getFloatView("voice_interaction_float")
                floatView?.visibility = View.GONE

                // 等待 UI 刷新
                kotlinx.coroutines.delay(300)

                val jpegBytes = captureScreenJpeg()

                // 恢复悬浮窗
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    floatView?.visibility = View.VISIBLE
                }

                if (jpegBytes != null) {
                    showResultMessage("助手: 思考中（含屏幕分析）...")
                    val reply = callLlmVision(
                        systemPrompt = "你是一个简洁有用的语音助手。用户会给你语音内容和屏幕画面，请结合两者回答。用简短的语言回答。",
                        userText = userText,
                        frameJpegBytes = jpegBytes
                    )
                    if (reply != null) {
                        showResultMessage("助手: $reply")
                        if (KVUtils.isTtsEnabled()) {
                            speakReply(reply)
                        }
                    }
                } else {
                    showResultMessage("助手: 截图失败，仅文本回复...")
                    val reply = callLlmTextOnly(userText)
                    if (reply != null) {
                        showResultMessage("助手: $reply")
                        if (KVUtils.isTtsEnabled()) {
                            speakReply(reply)
                        }
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                val modelName = KVUtils.getLlmModelName().ifEmpty { "gpt-4o" }
                XLog.e(TAG, "LLM request timeout: model=$modelName", e)
                showResultMessage("助手: LLM请求超时，请检查模型[$modelName]是否支持或网络是否畅通")
            } catch (e: Exception) {
                XLog.e(TAG, "LLM request failed", e)
                showResultMessage("助手: 请求失败: ${e.message}")
            }
        }
    }

    private suspend fun callLlmVision(
        systemPrompt: String,
        userText: String,
        frameJpegBytes: ByteArray
    ): String? {
        val baseUrl = KVUtils.getLlmBaseUrl().trimEnd('/')
        val apiKey = KVUtils.getLlmApiKey()
        var modelName = KVUtils.getLlmModelName()

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            showResultMessage("助手: 请先配置 LLM（设置 > 模型 > LLM 配置）")
            return null
        }
        if (modelName.isEmpty()) modelName = "gpt-4o"

        val base64Image = Base64.encodeToString(frameJpegBytes, Base64.NO_WRAP)
        val url = buildApiUrl(baseUrl)
        XLog.i(TAG, "callLlmVision: model=$modelName, url=$url, imageSize=${frameJpegBytes.size / 1024}KB")

        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "text", "text" to userText),
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64Image")
                    )
                )
            )
        )

        return withContext(Dispatchers.IO) {
            executeLlmRequest(url, apiKey, modelName, messages)
        }
    }

    private suspend fun callLlmTextOnly(userText: String): String? {
        val baseUrl = KVUtils.getLlmBaseUrl().trimEnd('/')
        val apiKey = KVUtils.getLlmApiKey()
        var modelName = KVUtils.getLlmModelName()

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            showResultMessage("助手: 请先配置 LLM（设置 > 模型 > LLM 配置）")
            return null
        }
        if (modelName.isEmpty()) modelName = "gpt-4o"

        val url = buildApiUrl(baseUrl)
        XLog.i(TAG, "callLlmTextOnly: model=$modelName, url=$url")

        val messages = listOf(
            mapOf("role" to "system", "content" to "你是一个简洁有用的语音助手，用简短的语言回答问题。"),
            mapOf("role" to "user", "content" to userText)
        )

        return withContext(Dispatchers.IO) {
            executeLlmRequest(url, apiKey, modelName, messages)
        }
    }

    private fun executeLlmRequest(
        url: String,
        apiKey: String,
        modelName: String,
        messages: List<Map<String, Any>>
    ): String? {
        val bodyMap = mapOf(
            "model" to modelName,
            "messages" to messages,
            "max_tokens" to 300,
            "temperature" to 0.7
        )

        val json = com.google.gson.Gson().toJson(bodyMap)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (responseBody == null) {
                showResultMessage("助手: 请求失败，无响应")
                return null
            }
            if (!response.isSuccessful) {
                XLog.e(TAG, "LLM API error: HTTP ${response.code}, body=$responseBody")
                showResultMessage("助手: 请求失败(HTTP ${response.code})")
                return null
            }
            val jsonResp = org.json.JSONObject(responseBody)
            val content = jsonResp.getJSONArray("choices")
                .optJSONObject(0)?.getJSONObject("message")
                ?.optString("content", "") ?: "无回复"
            return content.trim()
        }
    }

    private fun buildApiUrl(baseUrl: String): String {
        return when {
            baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
            baseUrl.contains("/v1/") -> "$baseUrl/chat/completions"
            else -> "$baseUrl/v1/chat/completions"
        }
    }

    private fun speakReply(text: String) {
        try {
            if (ttsManager == null) {
                ttsManager = com.apk.claw.android.floating.voice.TtsManager(application)
            }
            ttsManager?.speak(text)
        } catch (e: Exception) {
            XLog.e(TAG, "TTS speak failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMonitoring) {
            stopMonitoring()
        }
        VoiceInteractionFloatWindow.clearMonitorResults()
        VoiceInteractionFloatWindow.dismiss()
        ttsManager?.shutdown()
        ttsManager = null
    }
}
