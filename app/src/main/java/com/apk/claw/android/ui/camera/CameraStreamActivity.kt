package com.apk.claw.android.ui.camera

import android.os.Bundle
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
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class CameraStreamActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraStreamActivity"
        private const val REQUEST_CAMERA_AUDIO = 1001
        private const val LENS_FACING_FRONT = 0
        private const val LENS_FACING_BACK = 1
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
                    // 在悬浮窗内展示用户语音，并通过 LLM 获取回复
                    showResultMessage("你: $text")
                    sendToLlmAndReply(text)
                }
                VoiceInteractionFloatWindow.show(application as ClawApplication)
                btnVoiceFloat.text = "隐藏语音"
            }
        }
    }

    private fun startMonitoring() {
        if (StreamMonitorController.running) return

        StreamMonitorController.start(application as ClawApplication)
        isMonitoring = true

        btnToggleMonitor.text = "停止监控"
        tvMonitorStatus.text = "监控运行中..."
        tvMonitorStatus.visibility = View.VISIBLE
    }

    private fun stopMonitoring() {
        StreamMonitorController.stop()
        isMonitoring = false

        btnToggleMonitor.text = "开始监控"
        tvMonitorStatus.text = "监控已停止"
    }

    /**
     * 在 VoiceStreamFloatWindow 悬浮窗中展示对话结果
     */
    private fun showResultMessage(text: String) {
        VoiceStreamFloatWindow.showMonitorResult(text)
    }

    /**
     * 将用户语音文本发送到 LLM 获取回复，结果展示在悬浮窗中（文字+TTS语音）
     */
    private fun sendToLlmAndReply(userText: String) {
        val app = application as ClawApplication
        val baseUrl = KVUtils.getLlmBaseUrl().trimEnd('/')
        val apiKey = KVUtils.getLlmApiKey()
        var modelName = KVUtils.getLlmModelName()

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            showResultMessage("助手: 请先配置 LLM（设置 > 模型 > LLM 配置）")
            return
        }
        if (modelName.isEmpty()) modelName = "gpt-4o"

        showResultMessage("助手: 思考中...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = when {
                    baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
                    baseUrl.contains("/v1/") -> "$baseUrl/chat/completions"
                    else -> "$baseUrl/v1/chat/completions"
                }

                val messages = listOf(
                    mapOf("role" to "system", "content" to "你是一个简洁有用的语音助手，用简短的语言回答问题。"),
                    mapOf("role" to "user", "content" to userText)
                )

                val bodyMap = mapOf(
                    "model" to modelName,
                    "messages" to messages,
                    "max_tokens" to 300,
                    "temperature" to 0.7
                )

                val json = com.google.gson.Gson().toJson(bodyMap)
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
                        return@use
                    }
                    if (!response.isSuccessful) {
                        showResultMessage("助手: 请求失败(HTTP ${response.code})")
                        return@use
                    }
                    val jsonResp = org.json.JSONObject(responseBody)
                    val content = jsonResp.getJSONArray("choices")
                        .optJSONObject(0)?.getJSONObject("message")
                        ?.optString("content", "") ?: "无回复"

                    val reply = content.trim()
                    showResultMessage("助手: $reply")

                    // TTS 语音播报回复
                    if (com.apk.claw.android.utils.KVUtils.isTtsEnabled()) {
                        speakReply(reply)
                    }
                }
            } catch (e: Exception) {
                XLog.e(TAG, "LLM request failed", e)
                showResultMessage("助手: 请求失败: ${e.message}")
            }
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
            StreamMonitorController.stop()
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
