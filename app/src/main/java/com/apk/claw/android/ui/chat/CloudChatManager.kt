package com.apk.claw.android.ui.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.webrtc.DirectWebRTCManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * 云端对话管理器 — 支持 VoiceLLM 和 OpenClaw 两种模式
 *
 * VoiceLLM 模式:
 * - 复用 DirectWebRTCManager 的 WebSocket（CyberVerse 信令通道）
 * - 发送 {type: "text_input", text} → 接收 {type: "llm_token"} 和 {type: "transcript"}
 * - 无需独立 WebSocket 连接
 *
 * OpenClaw 模式:
 * - 独立连接 OpenClaw WebSocket（ws://7110f985.r21.cpolar.top）
 * - 发送 {type: "text", session_id: "client-request", text} → 接收 {type: "text/llm/end/push"}
 * - LLM 回复通过 DirectWebRTCManager.sendAssistantText() 桥接到 CyberVerse 进行 TTS/数字人驱动
 *
 * 两种模式共享 WebRTC 视频流（数字人形象）。
 */
object CloudChatManager {

    private const val TAG = "CloudChat"
    private const val CHANNEL_ID = "cloud_chat_push"
    private const val CHANNEL_NAME = "云端推送消息"
    private const val NOTIFICATION_ID_PUSH = 10001

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    // OpenClaw mode WebSocket
    private var openClawWebSocket: WebSocket? = null
    private var isOpenClawConnected = false

    private var currentCallback: ChatActivity.ChatCallback? = null
    private var pushListener: PushListener? = null

    // 自动重连（指数退避：2s→4s→8s→...→60s，最多 10 次）
    private var shouldReconnect = false
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_DELAY = 60000L
    private val MAX_RECONNECT_ATTEMPTS = 10
    private val BASE_RECONNECT_DELAY = 2000L

    // VoiceLLM 模式的回复超时
    private var voiceLlmReplyTimer: Runnable? = null
    private val voiceLlmReplyHandler = Handler(Looper.getMainLooper())
    private var currentLlmAccumulated = StringBuilder()
    private var hasVoiceLlmResponse = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * 推送消息监听器
     */
    interface PushListener {
        fun onPushMessage(text: String)
    }

    fun setPushListener(listener: PushListener?) {
        pushListener = listener
    }

    /**
     * 获取当前聊天模式显示名称
     */
    fun getModeDisplayName(): String {
        return if (KVUtils.isOpenClawMode()) "OpenClaw" else "VoiceLLM"
    }

    /**
     * 进入聊天界面时初始化连接
     * VoiceLLM 模式: 注册 DirectWebRTCManager 的文本监听器
     * OpenClaw 模式: 建立 OpenClaw WebSocket 连接
     */
    fun connectForPush() {
        shouldReconnect = true
        reconnectAttempts = 0

        if (KVUtils.isOpenClawMode()) {
            // OpenClaw 模式: 连接 OpenClaw WebSocket
            connectOpenClaw()
        } else {
            // VoiceLLM 模式: 注册 DirectWebRTCManager 文本监听器
            registerVoiceLlmListener()
        }
    }

    /**
     * 发送文本消息（根据当前模式走不同通道）
     */
    fun sendMessage(text: String, callback: ChatActivity.ChatCallback) {
        currentCallback = callback

        if (KVUtils.isOpenClawMode()) {
            sendOpenClawMessage(text)
        } else {
            sendVoiceLlmMessage(text)
        }
    }

    // ==================== VoiceLLM 模式 ====================

    /**
     * 注册 DirectWebRTCManager 的文本响应监听器
     * VoiceLLM 模式下，LLM 回复通过 CyberVerse WebSocket 的 llm_token 和 transcript 事件传递
     */
    private fun registerVoiceLlmListener() {
        XLog.i(TAG, "VoiceLLM mode: registering text response listener")
        DirectWebRTCManager.setTextResponseListener(object : DirectWebRTCManager.TextResponseListener {
            override fun onLlmToken(accumulated: String, isFinal: Boolean) {
                // 文本管道：流式累积文本
                XLog.d(TAG, "VoiceLLM llm_token: ${accumulated.take(50)}... isFinal=$isFinal")
                mainHandler.post {
                    hasVoiceLlmResponse = true
                    cancelReplyTimer()

                    currentCallback?.onProgress(accumulated)
                    if (isFinal) {
                        currentCallback?.onComplete(accumulated)
                        currentCallback = null
                        // 也发送给 push listener
                        pushListener?.onPushMessage(accumulated)
                    }
                }
            }

            override fun onTextResponse(text: String, isFinal: Boolean) {
                // 语音管道：语音转录文本（assistant 的语音转文字）
                XLog.d(TAG, "VoiceLLM transcript: text=$text isFinal=$isFinal")
                if (!isFinal) return  // 只处理最终结果

                // 如果 llm_token 已经有回复了，跳过 transcript 的重复内容
                if (hasVoiceLlmResponse) return

                mainHandler.post {
                    hasVoiceLlmResponse = true
                    cancelReplyTimer()

                    currentCallback?.onComplete(text)
                    currentCallback = null
                }
            }
        })
    }

    /**
     * VoiceLLM 模式发送消息
     * 通过 DirectWebRTCManager 的 WebSocket 发送 text_input
     */
    private fun sendVoiceLlmMessage(text: String) {
        hasVoiceLlmResponse = false
        currentLlmAccumulated.clear()

        // 检查 DirectWebRTCManager 是否已连接
        if (DirectWebRTCManager.connectionState.value != DirectWebRTCManager.ConnectionState.CONNECTED) {
            mainHandler.post {
                currentCallback?.onError("数字人未连接，请等待连接建立后再试")
                currentCallback = null
            }
            return
        }

        // 通过 DirectWebRTCManager 发送 text_input
        DirectWebRTCManager.sendTextMessage(text)

        // 设置超时：如果 30 秒内没有收到回复，提示超时
        cancelReplyTimer()
        voiceLlmReplyTimer = Runnable {
            if (!hasVoiceLlmResponse && currentCallback != null) {
                currentCallback?.onError("回复超时，请检查网络连接")
                currentCallback = null
                hasVoiceLlmResponse = true
            }
        }
        voiceLlmReplyHandler.postDelayed(voiceLlmReplyTimer!!, 30000)

        XLog.i(TAG, "VoiceLLM: sent text_input via DirectWebRTCManager")
    }

    private fun cancelReplyTimer() {
        voiceLlmReplyTimer?.let { voiceLlmReplyHandler.removeCallbacks(it) }
        voiceLlmReplyTimer = null
    }

    // ==================== OpenClaw 模式 ====================

    /**
     * 建立 OpenClaw WebSocket 连接
     */
    private fun connectOpenClaw() {
        val wsUrl = KVUtils.getOpenClawWsUrl().trim()
        if (wsUrl.isEmpty()) {
            XLog.w(TAG, "OpenClaw WS URL not configured")
            return
        }
        if (isOpenClawConnected) return

        XLog.i(TAG, "OpenClaw mode: connecting to $wsUrl")
        doConnectOpenClaw(wsUrl)
    }

    private fun doConnectOpenClaw(wsUrl: String) {
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        openClawWebSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                XLog.i(TAG, "OpenClaw WebSocket connected")
                isOpenClawConnected = true
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleOpenClawMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                XLog.i(TAG, "OpenClaw WebSocket closing: $code $reason")
                webSocket.close(1000, null)
                isOpenClawConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                XLog.i(TAG, "OpenClaw WebSocket closed: $code $reason")
                isOpenClawConnected = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                XLog.e(TAG, "OpenClaw WebSocket failure: ${response?.code} ${t.message}", t)
                isOpenClawConnected = false

                mainHandler.post {
                    currentCallback?.onError("连接OpenClaw服务失败: ${t.message}")
                    currentCallback = null
                }

                scheduleReconnect()
            }
        })
    }

    /**
     * OpenClaw 模式发送消息
     */
    private fun sendOpenClawMessage(text: String) {
        val wsUrl = KVUtils.getOpenClawWsUrl().trim()
        if (wsUrl.isEmpty()) {
            mainHandler.post {
                currentCallback?.onError("未配置 OpenClaw WebSocket 地址")
                currentCallback = null
            }
            return
        }

        if (isOpenClawConnected) {
            doSendOpenClawText(text)
        } else {
            // 未连接时先连接，连接成功后发送
            XLog.i(TAG, "OpenClaw not connected, connecting first...")
            val pendingText = text
            val request = Request.Builder().url(wsUrl).build()
            openClawWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    XLog.i(TAG, "OpenClaw WebSocket connected (pending send)")
                    isOpenClawConnected = true
                    reconnectAttempts = 0
                    doSendOpenClawText(pendingText)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleOpenClawMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    isOpenClawConnected = false
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isOpenClawConnected = false
                    scheduleReconnect()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    XLog.e(TAG, "OpenClaw connect+send failure: ${t.message}", t)
                    isOpenClawConnected = false
                    mainHandler.post {
                        currentCallback?.onError("连接OpenClaw服务失败: ${t.message}")
                        currentCallback = null
                    }
                    scheduleReconnect()
                }
            })
        }
    }

    private fun doSendOpenClawText(text: String) {
        val message = JsonObject().apply {
            addProperty("type", "text")
            addProperty("session_id", "client-request")
            addProperty("text", text.trim())
        }

        val json = gson.toJson(message)
        XLog.d(TAG, "OpenClaw sending: $json")

        val sent = openClawWebSocket?.send(json) ?: false
        if (!sent) {
            mainHandler.post {
                currentCallback?.onError("消息发送失败，请重试")
                currentCallback = null
            }
        }
    }

    /**
     * 处理 OpenClaw WebSocket 消息
     */
    private fun handleOpenClawMessage(text: String) {
        XLog.d(TAG, "OpenClaw received: $text")

        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: ""

            when (type) {
                "text", "llm" -> {
                    // OpenClaw LLM 回复（完整文本，非流式）
                    val responseText = json.get("text")?.asString
                        ?: json.get("content")?.asString
                        ?: json.get("answer")?.asString ?: ""
                    if (responseText.isNotEmpty()) {
                        mainHandler.post {
                            currentCallback?.onProgress(responseText)
                            currentCallback?.onComplete(responseText)
                            currentCallback = null
                        }
                        // 桥接到 CyberVerse 进行 TTS/数字人驱动
                        bridgeToCyberVerse(responseText)
                    }
                }
                "end" -> {
                    // OpenClaw 结束标记
                    XLog.i(TAG, "OpenClaw end signal received")
                }
                "push" -> {
                    // 服务端主动推送
                    val pushText = json.get("text")?.asString ?: ""
                    if (pushText.isNotEmpty()) {
                        XLog.i(TAG, "OpenClaw push: $pushText")
                        mainHandler.post {
                            pushListener?.onPushMessage(pushText)
                        }
                        showPushNotification(pushText)
                        // 推送消息也桥接到 CyberVerse 进行 TTS
                        bridgeToCyberVerse(pushText)
                    }
                }
                "error" -> {
                    val errorMsg = json.get("message")?.asString
                        ?: json.get("error")?.asString
                        ?: "未知错误"
                    mainHandler.post {
                        currentCallback?.onError(errorMsg)
                        currentCallback = null
                    }
                }
                else -> {
                    XLog.d(TAG, "OpenClaw ignoring message type: $type")
                }
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to parse OpenClaw message", e)
        }
    }

    /**
     * 将 OpenClaw 的 LLM 回复桥接到 CyberVerse WebSocket 进行 TTS/数字人驱动
     * 对应 Vue 项目中 useOpenClawChat 的 onAssistantText 回调
     */
    private fun bridgeToCyberVerse(text: String) {
        try {
            DirectWebRTCManager.sendAssistantText(text)
            XLog.d(TAG, "Bridged to CyberVerse for TTS: ${text.take(30)}...")
        } catch (e: Exception) {
            XLog.w(TAG, "Failed to bridge to CyberVerse", e)
        }
    }

    // ==================== 自动重连 ====================

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            XLog.w(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
            shouldReconnect = false
            return
        }

        val delay = minOf(BASE_RECONNECT_DELAY * (1L shl reconnectAttempts), MAX_RECONNECT_DELAY)
        reconnectAttempts++

        XLog.i(TAG, "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        reconnectHandler.postDelayed({
            if (shouldReconnect) {
                if (KVUtils.isOpenClawMode()) {
                    if (!isOpenClawConnected) {
                        val wsUrl = KVUtils.getOpenClawWsUrl().trim()
                        if (wsUrl.isNotEmpty()) {
                            doConnectOpenClaw(wsUrl)
                        }
                    }
                }
                // VoiceLLM 模式不需要重连 - 使用 DirectWebRTCManager 的连接
            }
        }, delay)
    }

    // ==================== 推送通知 ====================

    private fun showPushNotification(text: String) {
        val appContext = ClawApplication.instance ?: return

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "云端对话推送消息通知"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(appContext, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChatActivity.EXTRA_PUSH_TEXT, text)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = if (text.length > 100) text.substring(0, 100) + "..." else text

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("云端推送")
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PUSH, notification)
    }

    // ==================== 生命周期 ====================

    fun disconnect() {
        shouldReconnect = false
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectAttempts = 0
        cancelReplyTimer()

        // 关闭 OpenClaw WebSocket
        try {
            openClawWebSocket?.close(1000, "User disconnect")
        } catch (_: Exception) {}
        openClawWebSocket = null
        isOpenClawConnected = false

        // 注销 VoiceLLM 监听器
        DirectWebRTCManager.setTextResponseListener(null)

        currentCallback = null
        pushListener = null
        hasVoiceLlmResponse = false
    }

    fun isConnected(): Boolean {
        return if (KVUtils.isOpenClawMode()) {
            isOpenClawConnected
        } else {
            DirectWebRTCManager.connectionState.value == DirectWebRTCManager.ConnectionState.CONNECTED
        }
    }
}
