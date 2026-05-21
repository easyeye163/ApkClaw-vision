package com.apk.claw.android.ui.camera

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.floating.voice.VoiceInteractionFloatWindow
import com.apk.claw.android.floating.voice.VoiceStreamFloatWindow
import com.apk.claw.android.service.monitor.StreamMonitorController
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.vision.CameraFramePusher
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

class CameraStreamActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraStreamActivity"
        private const val REQUEST_CAMERA_AUDIO = 1001
        private const val LENS_FACING_FRONT = 0
        private const val LENS_FACING_BACK = 1
        private const val MONITOR_INTERVAL_MS = 5000L // 自动监控间隔5秒
    }

    private lateinit var previewView: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var tvMonitorStatus: TextView
    private lateinit var btnSwitchCamera: Button
    private lateinit var btnToggleMonitor: Button
    private lateinit var btnVoiceFloat: Button
    private lateinit var btnCloseCamera: ImageButton

    private var ttsManager: com.apk.claw.android.floating.voice.TtsManager? = null
    private var cameraFramePusher: CameraFramePusher? = null
    private var isMonitoring = false
    private var lensFacing = LENS_FACING_BACK

    // 自动监控循环
    private var monitorScope: CoroutineScope? = null
    private var monitorJob: Job? = null

    // 监控提示词（可被语音介入更新）
    @Volatile
    private var monitorPrompt: String = "请分析当前摄像头画面，描述你看到的内容。"

    // 监控轮次计数
    private var monitorRound: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFullscreen()
        setContentView(R.layout.activity_camera_stream)

        previewView = findViewById(R.id.preview_view)
        tvStatus = findViewById(R.id.tv_camera_status)
        tvMonitorStatus = findViewById(R.id.tv_monitor_status)
        btnSwitchCamera = findViewById(R.id.btn_switch_camera)
        btnToggleMonitor = findViewById(R.id.btn_toggle_monitor)
        btnVoiceFloat = findViewById(R.id.btn_voice_float)
        btnCloseCamera = findViewById(R.id.btn_close_camera)

        checkPermissionsAndStart()
        bindButtons()
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

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_CAMERA_AUDIO
            )
        } else {
            startCameraStream()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_AUDIO) {
            for (result in grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要摄像头和麦克风权限", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
            }
            startCameraStream()
        }
    }

    private fun startCameraStream() {
        try {
            VisionFrameBuffer.start()

            val pusher = CameraFramePusher(this, previewView.surfaceProvider)
            pusher.lensFacing = lensFacing
            pusher.fps = 2
            pusher.callback = object : CameraFramePusher.Callback {
                override fun onCameraError(message: String) {
                    XLog.e(TAG, "Camera error: $message")
                }
            }
            pusher.start()
            cameraFramePusher = pusher

            val label = if (lensFacing == LENS_FACING_FRONT) "前置" else "后置"
            tvStatus.text = "${label}摄像头已启动"
            XLog.i(TAG, "Camera stream started with frame pusher (lensFacing=$lensFacing)")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to start camera", e)
            tvStatus.text = "摄像头启动失败: ${e.message}"
        }
    }

    private fun bindButtons() {
        btnCloseCamera.setOnClickListener {
            finish()
        }

        btnSwitchCamera.setOnClickListener {
            cameraFramePusher?.switchCamera()
            lensFacing = cameraFramePusher?.lensFacing ?: lensFacing
            val label = if (lensFacing == LENS_FACING_FRONT) "前置" else "后置"
            Toast.makeText(this, "切换到${label}摄像头", Toast.LENGTH_LONG).show()
            tvStatus.text = "${label}摄像头已启动"
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
                    // 在悬浮窗内展示用户语音
                    showResultMessage("你: $text")

                    // 如果正在监控，语音介入：更新监控提示词
                    if (isMonitoring) {
                        monitorPrompt = text
                        showResultMessage("助手: 已更新监控任务: $text")
                        XLog.i(TAG, "Monitor prompt updated by voice: $text")
                    }

                    // 发送到 LLM（带当前画面）
                    sendToLlmWithFrame(text)
                }
                VoiceInteractionFloatWindow.show(application as ClawApplication)
                btnVoiceFloat.text = "隐藏语音"
            }
        }
    }

    /**
     * 开始监控：启动自动循环
     * 流程：发送提示词 → LLM回复 → 等5秒 → 发送画面 → LLM回复 → 等5秒 → 循环
     */
    private fun startMonitoring() {
        if (isMonitoring) return

        // 确保语音悬浮窗已打开
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
        tvMonitorStatus.text = "监控运行中..."
        tvMonitorStatus.visibility = View.VISIBLE

        showResultMessage("监控已启动，任务: $monitorPrompt")

        monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        monitorJob = monitorScope?.launch {
            while (isActive && isMonitoring) {
                try {
                    monitorRound++
                    sendMonitorFrame()
                } catch (e: Exception) {
                    XLog.e(TAG, "Monitor loop error at round $monitorRound", e)
                }
                // 等待指定间隔
                delay(MONITOR_INTERVAL_MS)
            }
        }

        XLog.i(TAG, "Auto monitoring started, prompt: $monitorPrompt")
    }

    /**
     * 停止监控
     */
    private fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorScope?.cancel()
        monitorJob = null
        monitorScope = null

        btnToggleMonitor.text = "开始监控"
        tvMonitorStatus.text = "监控已停止"

        showResultMessage("监控已停止 (共${monitorRound}轮)")

        XLog.i(TAG, "Auto monitoring stopped after $monitorRound rounds")
    }

    /**
     * 发送一帧画面到 LLM 进行监控分析
     */
    private suspend fun sendMonitorFrame() {
        val frameEntry = VisionFrameBuffer.latestFrame
        if (frameEntry == null) {
            XLog.w(TAG, "Monitor round $monitorRound: no frame available, skip")
            return
        }

        val currentPrompt = monitorPrompt
        XLog.i(TAG, "Monitor round $monitorRound: analyzing frame, prompt=$currentPrompt")

        val reply = callLlmVision(
            systemPrompt = "你是一个视频流监控助手。用户会给你摄像头画面和监控任务。请根据任务要求分析画面内容，用简洁的语言描述你看到的情况。如果检测到用户关注的目标或事件，请明确提醒。",
            userText = currentPrompt,
            frameJpegBytes = frameEntry.jpegBytes
        )

        if (reply != null) {
            val displayText = "[${monitorRound}] 助手: $reply"
            showResultMessage(displayText)

            // TTS 播报每轮回复（先停止上一轮避免重叠）
            if (KVUtils.isTtsEnabled()) {
                ttsManager?.stop()
                speakReply(reply)
            }
        }
    }

    /**
     * 在 VoiceStreamFloatWindow 悬浮窗中展示对话结果
     */
    private fun showResultMessage(text: String) {
        VoiceStreamFloatWindow.showMonitorResult(text)
    }

    /**
     * 将用户语音文本 + 当前摄像头画面一起发送到 LLM（vision多模态）
     */
    private fun sendToLlmWithFrame(userText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val frameEntry = VisionFrameBuffer.latestFrame
                if (frameEntry != null) {
                    // 有画面：使用 vision 模式，同时发送文字和画面
                    showResultMessage("助手: 思考中（含画面分析）...")
                    val reply = callLlmVision(
                        systemPrompt = "你是一个简洁有用的语音助手。用户会给你语音内容和摄像头画面，请结合两者回答。用简短的语言回答。",
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
                    // 无画面：纯文本模式
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

    /**
     * 调用 LLM Vision API（带图片 + 文字）
     * @return 回复文本，失败返回 null
     */
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

    /**
     * 调用 LLM 纯文本 API（无图片）
     * @return 回复文本，失败返回 null
     */
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

    /**
     * 执行 LLM HTTP 请求（统一入口）
     * @return 回复文本，失败返回 null
     */
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

    /**
     * 智能拼接 API URL
     */
    private fun buildApiUrl(baseUrl: String): String {
        return when {
            baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
            baseUrl.contains("/v1/") -> "$baseUrl/chat/completions"
            else -> "$baseUrl/v1/chat/completions"
        }
    }

    /**
     * 使用 TTS 语音播报回复内容
     */
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

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
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
        cameraFramePusher?.stop()
        cameraFramePusher = null
        VisionFrameBuffer.stop()
    }
}
