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
 * 云端对话模式 WebSocket 管理器（兼容 CyberVerse 协议）
 *
 * 发送: { "type": "text_input", "text": "..." }
 * 接收:
 *   - llm_token: 流式 LLM 响应，{ "accumulated": "...", "is_final": bool }
 *   - avatar_status: 数字人状态，{ "status": "idle"|"speaking"|"processing" }
 *   - text / llm: 完整响应（兼容旧协议）
 *   - push: 主动推送
 *   - error: 错误
 *
 * WebSocket URL 格式: baseUrl/ws/chat/{sessionId}（CyberVerse 格式）
 */
object CloudChatManager {

    private const val TAG = "CloudChat"
    private const val CHANNEL_ID = "cloud_chat_push"
    private const val CHANNEL_NAME = "云端推送消息"
    private const val NOTIFICATION_ID_PUSH = 10001

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var currentCallback: ChatActivity.ChatCallback? = null
    private var pushListener: PushListener? = null

    // Accumulated LLM response for streaming
    private var accumulatedText = StringBuilder()

    // 自动重连（指数退避：2s→4s→8s→...→60s，最多 10 次）
    private var shouldReconnect = false
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_DELAY = 60000L
    private val MAX_RECONNECT_ATTEMPTS = 10
    private val BASE_RECONNECT_DELAY = 2000L

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
     * Build full WebSocket URL from base URL + session ID (CyberVerse format).
     * Base: ws://7110f985.r21.cpolar.top → ws://7110f985.r21.cpolar.top/ws/chat/{sessionId}
     */
    private fun buildWsUrl(): String {
        val baseUrl = KVUtils.getCloudChatWsUrl().trim()
        if (baseUrl.isEmpty()) return ""

        val sessionId = KVUtils.getCloudChatSessionId().ifEmpty {
            "android_${System.currentTimeMillis()}".also {
                KVUtils.setCloudChatSessionId(it)
                XLog.i(TAG, "Generated new session ID: $it")
            }
        }

        // If URL already has a path (e.g. ws://host/ws/chat/xxx), use as-is
        if (baseUrl.contains("/ws/")) return baseUrl

        // Append CyberVerse-style path: /ws/chat/{sessionId}
        val base = baseUrl.trimEnd('/')
        return "$base/ws/chat/$sessionId"
    }

    /**
     * 仅建立 WebSocket 长连接（不发送消息）
     */
    fun connect(context: Context) {
        if (isConnected) return
        val wsUrl = buildWsUrl()
        if (wsUrl.isEmpty()) return

        shouldReconnect = true
        reconnectAttempts = 0
        doConnect(wsUrl, null)
    }

    /**
     * 进入聊天界面时自动建立长连接
     */
    fun connectForPush() {
        if (isConnected) return
        val wsUrl = buildWsUrl()
        if (wsUrl.isEmpty()) return

        shouldReconnect = true
        reconnectAttempts = 0
        XLog.i(TAG, "connectForPush: $wsUrl")
        doConnect(wsUrl, null)
    }

    /**
     * 发送文本消息到云端 WebSocket
     */
    fun sendMessage(text: String, callback: ChatActivity.ChatCallback) {
        val wsUrl = buildWsUrl()
        if (wsUrl.isEmpty()) {
            callback.onError("未配置云端对话 WebSocket 地址")
            return
        }

        currentCallback = callback
        accumulatedText.clear()

        if (isConnected) {
            sendTextMessage(text)
        } else {
            shouldReconnect = true
            doConnect(wsUrl, text)
        }
    }

    private fun doConnect(wsUrl: String, pendingText: String?) {
        XLog.i(TAG, "Connecting to $wsUrl ...")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                XLog.i(TAG, "WebSocket connected")
                isConnected = true
                reconnectAttempts = 0

                if (pendingText != null) {
                    sendTextMessage(pendingText)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                XLog.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                XLog.i(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                XLog.e(TAG, "WebSocket failure", t)
                isConnected = false

                if (pendingText != null) {
                    mainHandler.post {
                        currentCallback?.onError("连接云端服务失败: ${t.message}")
                        currentCallback = null
                    }
                }

                scheduleReconnect()
            }
        })
    }

    /**
     * 自动重连（指数退避：2s→4s→8s→...→60s，最多 10 次）
     */
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
            if (shouldReconnect && !isConnected) {
                val wsUrl = buildWsUrl()
                if (wsUrl.isNotEmpty()) {
                    doConnect(wsUrl, null)
                }
            }
        }, delay)
    }

    /**
     * 发送文本消息（CyberVerse text_input 格式）
     */
    private fun sendTextMessage(text: String) {
        val message = JsonObject().apply {
            addProperty("type", "text_input")
            addProperty("text", text)
        }

        val json = gson.toJson(message)
        XLog.d(TAG, "Sending: $json")

        val sent = webSocket?.send(json) ?: false
        if (!sent) {
            mainHandler.post {
                currentCallback?.onError("消息发送失败，请重试")
                currentCallback = null
            }
        }
    }

    private fun handleMessage(text: String) {
        XLog.d(TAG, "Received: $text")

        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: ""

            when (type) {
                // CyberVerse streaming LLM token
                "llm_token" -> handleLlmToken(json)
                // CyberVerse avatar status
                "avatar_status" -> handleAvatarStatus(json)
                // Legacy: complete text response
                "text", "llm" -> handleTextResponse(json)
                // Push notification from server
                "push" -> handlePush(json)
                // Error
                "error" -> handleError(json)
                // WebRTC signaling messages (ignore in chat manager)
                "webrtc_config", "webrtc_offer", "ice_candidate" -> {
                    XLog.d(TAG, "Ignoring signaling message: $type")
                }
                else -> {
                    // Fallback: treat unknown messages as plain text response
                    XLog.d(TAG, "Unknown message type: $type, treating as text")
                    mainHandler.post {
                        currentCallback?.onProgress(text)
                        currentCallback?.onComplete(text)
                        currentCallback = null
                    }
                }
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to parse message", e)
            // Fallback: treat raw text as response
            mainHandler.post {
                currentCallback?.onProgress(text)
                currentCallback?.onComplete(text)
                currentCallback = null
            }
        }
    }

    /**
     * Handle llm_token: CyberVerse streaming LLM response.
     * { "type": "llm_token", "accumulated": "full text so far", "is_final": false }
     */
    private fun handleLlmToken(json: JsonObject) {
        val accumulated = json.get("accumulated")?.asString ?: ""
        val isFinal = json.get("is_final")?.asBoolean ?: false
        val token = json.get("token")?.asString ?: ""

        if (accumulated.isNotEmpty()) {
            // Use accumulated text for display
            mainHandler.post {
                currentCallback?.onProgress(accumulated)
                if (isFinal) {
                    currentCallback?.onComplete(accumulated)
                    currentCallback = null
                }
            }
        } else if (token.isNotEmpty()) {
            // Append token to buffer, show accumulated
            accumulatedText.append(token)
            mainHandler.post {
                currentCallback?.onProgress(accumulatedText.toString())
                if (isFinal) {
                    currentCallback?.onComplete(accumulatedText.toString())
                    currentCallback = null
                    accumulatedText.clear()
                }
            }
        }
    }

    /**
     * Handle avatar_status: CyberVerse digital avatar state update.
     * { "type": "avatar_status", "status": "idle"|"speaking"|"processing" }
     */
    private fun handleAvatarStatus(json: JsonObject) {
        val status = json.get("status")?.asString ?: ""
        XLog.d(TAG, "Avatar status: $status")
        // Forward to DirectWebRTCManager if needed
        // For now, just log — the floating avatar observes its own connection state
    }

    /**
     * Handle legacy text/llm response.
     */
    private fun handleTextResponse(json: JsonObject) {
        val responseText = json.get("text")?.asString ?: ""
        val answer = json.get("answer")?.asString ?: ""
        val content = responseText.ifEmpty { answer }
        if (content.isNotEmpty()) {
            mainHandler.post {
                currentCallback?.onProgress(content)
                currentCallback?.onComplete(content)
                currentCallback = null
            }
        }
    }

    /**
     * Handle push notification.
     */
    private fun handlePush(json: JsonObject) {
        val pushText = json.get("text")?.asString ?: ""
        if (pushText.isNotEmpty()) {
            XLog.i(TAG, "Push received: $pushText")
            mainHandler.post {
                pushListener?.onPushMessage(pushText)
            }
            showPushNotification(pushText)
        }
    }

    /**
     * Handle error response.
     */
    private fun handleError(json: JsonObject) {
        val errorMsg = json.get("message")?.asString
            ?: json.get("error")?.asString
            ?: "未知错误"
        mainHandler.post {
            currentCallback?.onError(errorMsg)
            currentCallback = null
        }
    }

    /**
     * 显示推送通知
     */
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

    fun disconnect() {
        shouldReconnect = false
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectAttempts = 0
        accumulatedText.clear()
        try {
            webSocket?.close(1000, "User disconnect")
        } catch (_: Exception) {}
        webSocket = null
        isConnected = false
        currentCallback = null
        pushListener = null
    }

    fun isConnected(): Boolean = isConnected
}
