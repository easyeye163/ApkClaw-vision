package com.apk.claw.android.service.monitor

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.apk.claw.android.R
import com.apk.claw.android.floating.voice.VoiceStreamFloatWindow
import com.apk.claw.android.local.llm.LlamaEngine
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.vision.VisionFrameBuffer
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 视频流监控控制器（单例）
 *
 * 定期从 VisionFrameBuffer 获取帧画面，发送到 LLM Vision API 进行分析。
 * 支持本地 LLM（通过 LlamaEngine）和 HTTP API 两种分析方式。
 * 检测到目标后触发通知、振动、悬浮窗弹窗提醒。
 */
object StreamMonitorController {

    private const val TAG = "StreamMonitor"
    private const val CHANNEL_ID = "stream_monitor_channel"
    private const val CHANNEL_NAME = "视频流监控"
    private const val NOTIFICATION_ID = 2001
    private const val MIN_INTERVAL_MS = 5000L
    private const val DEFAULT_INTERVAL_MS = 10000L
    private const val MIN_NOTIFY_INTERVAL_MS = 30000L

    @Volatile
    private var isRunning = false

    @Volatile
    private var monitorTask = ""

    @Volatile
    var intervalMs: Long = DEFAULT_INTERVAL_MS
        private set

    @Volatile
    private var lastNotifyTimeMs = 0L

    private var monitorJob: Job? = null
    private var monitorScope: CoroutineScope? = null

    val running: Boolean get() = isRunning

    /**
     * 启动监控
     *
     * @param app Application 实例
     * @param task 监控任务描述（如 "检测是否有人进入画面"）
     * @param intervalSec 检测间隔（秒），最小 5 秒，默认 10 秒
     */
    fun start(app: Application, task: String, intervalSec: Int = 10) {
        if (isRunning) {
            XLog.w(TAG, "Monitor already running")
            return
        }

        monitorTask = task
        intervalMs = maxOf(MIN_INTERVAL_MS, intervalSec * 1000L)
        isRunning = true
        lastNotifyTimeMs = 0L

        createNotificationChannel(app)
        app.startForegroundService(Intent(app, StreamMonitorService::class.java))

        monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        monitorJob = monitorScope?.launch {
            while (isRunning) {
                delay(intervalMs)
                if (!isRunning) break
                try {
                    analyzeFrame(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    XLog.w(TAG, "analyzeFrame error", e)
                }
            }
        }

        XLog.i(TAG, "Stream monitor started")
    }

    /**
     * 使用已保存的 task 启动监控
     */
    fun start(app: Application) {
        if (monitorTask.isEmpty()) {
            XLog.w(TAG, "No monitor task set")
        } else {
            start(app, monitorTask)
        }
    }

    /**
     * 停止监控
     */
    fun stop() {
        isRunning = false
        monitorJob?.cancel()
        monitorScope?.cancel(null)
        monitorJob = null
        monitorScope = null
        XLog.i(TAG, "Stream monitor stopped")
    }

    // ───────────────────────────────────────────────────────────────────────
    // 帧分析
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 分析一帧画面
     *
     * 如果本地模型已激活，优先使用本地 LLM 分析（失败则回退到 HTTP）；
     * 否则直接走 HTTP API。
     */
    private suspend fun analyzeFrame(app: Application) {
        val frameEntry = VisionFrameBuffer.latestFrame ?: run {
            XLog.w(TAG, "No frame available, skip analysis")
            return
        }

        // 优先使用本地模型
        if (KVUtils.isLocalModelChatActive()) {
            try {
                val result = analyzeFrameLocal(app, frameEntry)
                if (result != null) return  // 本地分析成功
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                XLog.w(TAG, "Local model analysis failed, falling back to HTTP", e)
            }
        }

        // HTTP API 分析
        analyzeFrameHttp(app, frameEntry)
    }

    /**
     * 使用本地 LlamaEngine 分析帧画面
     *
     * 现在可以直接调用 Kotlin suspend 函数，无需反射。
     *
     * @return 分析结果文本，分析失败返回 null
     */
    private suspend fun analyzeFrameLocal(
        app: Application,
        frameEntry: VisionFrameBuffer.FrameEntry
    ): String? {
        val engine = LlamaEngine.getInstance(app)
        if (!engine.isModelLoaded || !engine._mmprojLoaded) {
            XLog.w(TAG, "Local model not ready for vision analysis")
            return null
        }

        return try {
            val systemPrompt = """
                你是一个视频流监控助手。你的任务是持续监控摄像头画面，检测用户关注的事件。

                用户设定的监控任务: $monitorTask

                分析规则:
                1. 仔细观察画面中的内容
                2. 判断画面中是否出现了用户关注的目标/事件
                3. 如果检测到 → 回复 "DETECTED: [简要描述你看到的内容]"
                4. 如果未检测到 → 回复 "CLEAR: [描述你看到的内容，1句话]"

                只做判断，不要提供建议或执行操作。
            """.trimIndent()

            // 直接调用 Kotlin suspend 函数
            engine.fullReset()
            engine.setSystemPrompt(systemPrompt)
            engine.prefillImage(frameEntry.jpegBytes)

            val userPrompt = "请分析当前摄像头画面，判断是否满足监控任务条件。"
            val resultBuilder = StringBuilder()
            engine.sendUserPrompt(userPrompt, 200).collect { token ->
                resultBuilder.append(token)
            }
            val result = resultBuilder.toString().trim()

            XLog.i(TAG, "Monitor local analysis: $result")

            if (result.startsWith("DETECTED:", ignoreCase = true)) {
                val description = result
                    .removePrefix("DETECTED:")
                    .removePrefix("DETECTED：")
                    .trim()
                handleDetection(app, description)
            }

            result
        } catch (e: Exception) {
            XLog.w(TAG, "Local model analysis error", e)
            null
        }
    }

    /**
     * 使用 HTTP API（OpenAI 兼容格式）分析帧画面
     */
    private suspend fun analyzeFrameHttp(
        app: Application,
        frameEntry: VisionFrameBuffer.FrameEntry
    ) {
        val base64Image = Base64.encodeToString(frameEntry.jpegBytes, Base64.NO_WRAP)
        val baseUrl = KVUtils.getLlmBaseUrl().trimEnd('/')
        val apiKey = KVUtils.getLlmApiKey()
        var modelName = KVUtils.getLlmModelName()

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            XLog.w(TAG, "LLM not configured, skip analysis")
            return
        }

        if (modelName.isEmpty()) modelName = "gpt-4o"

        // 构建 OpenAI 兼容的 chat completion 请求体
        val messages = listOf(
            mapOf(
                "role" to "system",
                "content" to """
                    你是一个视频流监控助手。你的任务是持续监控摄像头画面，检测用户关注的事件。

                    用户设定的监控任务: $monitorTask

                    分析规则:
                    1. 仔细观察画面中的内容
                    2. 判断画面中是否出现了用户关注的目标/事件
                    3. 如果检测到 → 回复 "DETECTED: [简要描述你看到的内容]"
                    4. 如果未检测到 → 回复 "CLEAR: [描述你看到的内容，1句话]"

                    只做判断，不要提供建议或执行操作。
                """.trimIndent()
            ),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "text", "text" to "请分析当前摄像头画面，判断是否满足监控任务条件。"),
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64Image")
                    )
                )
            )
        )

        val bodyMap = mapOf(
            "model" to modelName,
            "messages" to messages,
            "max_tokens" to 200,
            "temperature" to 0.1
        )

        // 智能拼接 API URL
        val url = when {
            baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
            baseUrl.contains("/v1/") -> "$baseUrl/chat/completions"
            else -> "$baseUrl/v1/chat/completions"
        }

        val json = Gson().toJson(bodyMap)

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        // 在 IO 线程执行同步 HTTP 请求
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (responseBody == null) return@withContext

                if (!response.isSuccessful) {
                    XLog.e(TAG, "Monitor API error ${response.code}")
                    return@withContext
                }

                try {
                    val jsonResp = JSONObject(responseBody)
                    val choices = jsonResp.getJSONArray("choices")
                    val message = choices.optJSONObject(0)?.getJSONObject("message")
                    val content = message?.optString("content", "") ?: return@withContext

                    XLog.i(TAG, "Monitor analysis: $content")

                    val trimmed = content.trim()
                    if (trimmed.startsWith("DETECTED:", ignoreCase = true)) {
                        val description = trimmed
                            .removePrefix("DETECTED:")
                            .removePrefix("DETECTED：")
                            .trim()
                        handleDetection(app, description)
                    }
                } catch (e: Exception) {
                    XLog.e(TAG, "Failed to parse monitor API response", e)
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 检测处理 & 通知
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 处理检测到的目标事件
     *
     * 显示悬浮窗弹窗 → 发送通知 → 振动提醒
     */
    private fun handleDetection(app: Application, description: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyTimeMs < MIN_NOTIFY_INTERVAL_MS) {
            XLog.w(TAG, "Too frequent notification, skipping")
            return
        }

        lastNotifyTimeMs = now
        XLog.i(TAG, "DETECTED: $description")

        VoiceStreamFloatWindow.showMonitorResult("检测到: $description")
        sendNotification(app, "监控提醒", description)

        try {
            val vibrator = app.getSystemService("vibrator") as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
            // 忽略振动权限或设备不支持的情况
        }
    }

    /**
     * 发送系统通知
     */
    private fun sendNotification(app: Application, title: String, content: String) {
        val notificationManager = app.getSystemService("notification") as NotificationManager
        val launchIntent = app.packageManager.getLaunchIntentForPackage(app.packageName)

        val notification: Notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    app, 0, launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel(app: Application) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "视频流监控检测通知"
            enableVibration(true)
        }
        val notificationManager = app.getSystemService("notification") as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
