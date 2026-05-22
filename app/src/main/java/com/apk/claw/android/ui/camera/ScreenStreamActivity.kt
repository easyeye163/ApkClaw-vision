package com.apk.claw.android.ui.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
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
import com.apk.claw.android.floating.voice.VoiceStreamFloatWindow
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.vision.ScreenCapturePusher
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

/**
 * 屏幕流 Activity
 *
 * 功能与 CameraStreamActivity 完全一致，只是输入源从摄像头改为屏幕捕获。
 * 支持自动监控循环、语音介入更新任务、TTS 语音播报。
 */
class ScreenStreamActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenStreamActivity"
        private const val REQUEST_MEDIA_PROJECTION = 2001
        private const val MONITOR_INTERVAL_MS = 5000L
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvMonitorStatus: TextView
    private lateinit var btnToggleMonitor: Button
    private lateinit var btnVoiceFloat: Button
    private lateinit var btnCloseScreen: ImageButton

    private var ttsManager: com.apk.claw.android.floating.voice.TtsManager? = null
    private var screenCapturePusher: ScreenCapturePusher? = null
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
        requestScreenCapture()
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
     * 请求屏幕录制权限
     */
    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startScreenCapture(resultCode, data)
            } else {
                Toast.makeText(this, "需要屏幕录制权限才能使用屏幕流", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = projectionManager.getMediaProjection(resultCode, data) ?: run {
                Toast.makeText(this, "获取屏幕录制权限失败", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            VisionFrameBuffer.start()

            val pusher = ScreenCapturePusher(mediaProjection)
            pusher.fps = 2
            pusher.start(windowManager)
            screenCapturePusher = pusher

            tvStatus.text = "屏幕流已启动"
            XLog.i(TAG, "Screen capture started")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to start screen capture", e)
            tvStatus.text = "屏幕捕获启动失败: ${e.message}"
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
                VoiceStreamFloatWindow.dismiss()
                btnVoiceFloat.text = "语音助手"
            } else {
                VoiceStreamFloatWindow.show(application as ClawApplication)
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
     * 开始监控：启动自动循环
     */
    private fun startMonitoring() {
        if (isMonitoring) return

        if (!VoiceInteractionFloatWindow.isShowing()) {
            VoiceStreamFloatWindow.show(application as ClawApplication)
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
        val frameEntry = VisionFrameBuffer.latestFrame
        if (frameEntry == null) {
            XLog.w(TAG, "Monitor round $monitorRound: no frame available, skip")
            return
        }

        val currentPrompt = monitorPrompt
        XLog.i(TAG, "Monitor round $monitorRound: analyzing screen frame, prompt=$currentPrompt")

        val reply = callLlmVision(
            systemPrompt = "你是一个屏幕监控助手。用户会给你屏幕画面和监控任务。请根据任务要求分析屏幕内容，用简洁的语言描述你看到的情况。如果检测到用户关注的内容或变化，请明确提醒。",
            userText = currentPrompt,
            frameJpegBytes = frameEntry.jpegBytes
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
        VoiceStreamFloatWindow.showMonitorResult(text)
    }

    private fun sendToLlmWithFrame(userText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val frameEntry = VisionFrameBuffer.latestFrame
                if (frameEntry != null) {
                    showResultMessage("助手: 思考中（含屏幕分析）...")
                    val reply = callLlmVision(
                        systemPrompt = "你是一个简洁有用的语音助手。用户会给你语音内容和屏幕画面，请结合两者回答。用简短的语言回答。",
                        userText = userText,
                        frameJpegBytes = frameEntry.jpegBytes
                    )
                    if (reply != null) {
                        showResultMessage("助手: $reply")
                        if (KVUtils.isTtsEnabled()) {
                            speakReply(reply)
                        }
                    }
                } else {
                    showResultMessage("助手: 思考中...")
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
        VoiceInteractionFloatWindow.dismiss()
        VoiceStreamFloatWindow.dismiss()
        ttsManager?.shutdown()
        ttsManager = null
        screenCapturePusher?.stop()
        screenCapturePusher = null
        VisionFrameBuffer.stop()
    }
}
