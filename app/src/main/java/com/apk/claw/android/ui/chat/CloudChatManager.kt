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
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * 云端对话模式 WebSocket 管理器
 *
 * 智能模式：
 * - 如果 DirectWebRTCManager 已连接（CyberVerse 数字人），复用其 WebSocket，
 *   通过 TextResponseListener 接收 transcript 流式响应，实现文字+视频同步。
 * - 如果未连接，自动创建独立 session（REST API → WebSocket）连接 CyberVerse 服务器。
 *
 * 发送: { "type": "text_input", "text": "..." }
 * 接收:
 *   - transcript: 流式 token，{ "speaker": "assistant", "text": "...", "is_final": bool }
 *   - llm_token: CyberVerse LLM 流式响应（兼容）
 *   - avatar_status: 数字人状态
 *   - text / llm: 完整响应（兼容旧协议）
 *   - push: 主动推送
 *   - error: 错误
 */
object CloudChatManager {

    private const val TAG = "CloudChat"
    private const val CHANNEL_ID = "cloud_chat_push"
    private const val CHANNEL_NAME = "云端推送消息"
    private const val NOTIFICATION_ID_PUSH = 10001

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    // DirectWebRTCManager shared mode
    private var usingSharedConnection = false
    private val sharedListener = object : DirectWebRTCManager.TextResponseListener {
        override fun onTranscriptToken(text: String, isFinal: Boolean) {
            XLog.d(TAG, "Shared transcript: ${text.take(50)}... isFinal=$isFinal")
            mainHandler.post {
                currentCallback?.onProgress(text)
                if (isFinal) {
                    currentCallback?.onComplete(text)
                    currentCallback = null
                }
            }
        }
        override fun onTextError(error: String) {
            mainHandler.post {
                currentCallback?.onError(error)
                currentCallback = null
            }
        }
    }

    // Standalone WebSocket mode (fallback when WebRTC not connected)
    private var webSocket: WebSocket? = null
    private var isWsConnected = false

    private var currentCallback: ChatActivity.ChatCallback? = null
    private var pushListener: PushListener? = null

    // Accumulated LLM response for standalone mode streaming
    private var accumulatedText = StringBuilder()

    // Auto-reconnect (exponential backoff: 2s→4s→8s→...→60s, max 10)
    private var shouldReconnect = false
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_DELAY = 60000L
    private val MAX_RECONNECT_ATTEMPTS = 10
    private val BASE_RECONNECT_DELAY = 2000L

    private val httpClient = OkHttpClient.Builder()
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
     * Check if we have an active connection (shared or standalone)
     */
    fun hasActiveConnection(): Boolean {
        return usingSharedConnection || isWsConnected
    }

    /**
     * Enter chat activity - establish connection.
     * Prefer DirectWebRTCManager's shared connection; fall back to standalone.
     */
    fun connectForPush() {
        // If WebRTC is connected, just use it (register listener)
        if (DirectWebRTCManager.isConnectedAndReady()) {
            switchToSharedMode()
            return
        }
        // Otherwise, create standalone connection
        if (isWsConnected) return
        val wsUrl = buildStandaloneWsUrl()
        if (wsUrl.isEmpty()) return

        shouldReconnect = true
        reconnectAttempts = 0
        XLog.i(TAG, "connectForPush (standalone): $wsUrl")
        doConnectStandalone(wsUrl, null)
    }

    /**
     * Switch to shared mode - use DirectWebRTCManager's WebSocket.
     */
    private fun switchToSharedMode() {
        if (usingSharedConnection) return
        // Disconnect standalone if connected
        disconnectStandalone()
        usingSharedConnection = true
        DirectWebRTCManager.addTextResponseListener(sharedListener)
        XLog.i(TAG, "Switched to shared WebRTC connection mode")
    }

    /**
     * Switch to standalone mode - create our own session + WebSocket.
     */
    private fun switchToStandaloneMode() {
        if (!usingSharedConnection) return
        usingSharedConnection = false
        DirectWebRTCManager.removeTextResponseListener(sharedListener)
        XLog.i(TAG, "Switched to standalone connection mode")
    }

    /**
     * Send text message to cloud.
     * Smart routing: use shared connection if WebRTC is connected, otherwise standalone.
     */
    fun sendMessage(text: String, callback: ChatActivity.ChatCallback) {
        currentCallback = callback
        accumulatedText.clear()

        // Prefer shared connection (WebRTC connected)
        if (DirectWebRTCManager.isConnectedAndReady()) {
            if (!usingSharedConnection) {
                switchToSharedMode()
            }
            XLog.d(TAG, "Sending via shared WebRTC connection: ${text.take(50)}")
            DirectWebRTCManager.sendTextMessage(text)
            return
        }

        // Fallback: standalone mode
        if (usingSharedConnection) {
            switchToStandaloneMode()
        }

        val wsUrl = buildStandaloneWsUrl()
        if (wsUrl.isEmpty()) {
            // Try to create session first via REST API
            createSessionAndConnect(text, callback)
            return
        }

        if (isWsConnected) {
            sendStandaloneTextMessage(text)
        } else {
            shouldReconnect = true
            XLog.i(TAG, "Connecting standalone for message: $wsUrl")
            doConnectStandalone(wsUrl, text)
        }
    }

    /**
     * Create a session via REST API and then connect WebSocket.
     * Used when no WebSocket URL is pre-configured.
     */
    private fun createSessionAndConnect(pendingText: String, callback: ChatActivity.ChatCallback) {
        val apiBase = KVUtils.getCyberVerseApiBase().trim()
        val characterId = KVUtils.getCyberVerseCharacterId().trim()

        if (apiBase.isEmpty() || characterId.isEmpty()) {
            mainHandler.post {
                callback.onError("未配置云端对话服务地址，请在设置中配置 CyberVerse 或云端对话地址")
            }
            return
        }

        Thread({
            try {
                val url = if (apiBase.endsWith("/")) "${apiBase}sessions" else "$apiBase/sessions"
                val body = gson.toJson(mapOf("character_id" to characterId, "mode" to "omni"))

                val request = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                XLog.i(TAG, "Creating session via REST API: $url")
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    mainHandler.post {
                        callback.onError("创建会话失败: HTTP ${response.code}")
                    }
                    return@Thread
                }

                val respStr = response.body?.string()
                val json = JsonParser.parseString(respStr).asJsonObject
                val sid = json.get("session_id")?.asString
                if (sid == null) {
                    mainHandler.post {
                        callback.onError("创建会话失败: 无 session_id")
                    }
                    return@Thread
                }

                XLog.i(TAG, "Session created: $sid")
                val wsBase = KVUtils.getCyberVerseWsBase().trim()
                if (wsBase.isEmpty()) {
                    mainHandler.post {
                        callback.onError("未配置 CyberVerse WebSocket 地址")
                    }
                    return@Thread
                }

                val wsUrl = "${wsBase.trimEnd('/')}/ws/chat/$sid"
                shouldReconnect = true
                reconnectAttempts = 0
                doConnectStandalone(wsUrl, pendingText)

            } catch (e: Exception) {
                XLog.e(TAG, "Error creating session", e)
                mainHandler.post {
                    callback.onError("连接云端服务失败: ${e.message}")
                }
            }
        }, "cloud-chat-session-create").start()
    }

    /**
     * Build standalone WebSocket URL.
     * Uses CyberVerse config if available, falls back to cloud chat WS URL.
     */
    private fun buildStandaloneWsUrl(): String {
        // Prefer CyberVerse WS base (same server as WebRTC)
        val cvWsBase = KVUtils.getCyberVerseWsBase().trim()
        val cvApiBase = KVUtils.getCyberVerseApiBase().trim()
        val cvCharId = KVUtils.getCyberVerseCharacterId().trim()

        if (cvWsBase.isNotEmpty() && cvApiBase.isNotEmpty() && cvCharId.isNotEmpty()) {
            // Will create session via REST API, return empty to trigger that flow
            return ""
        }

        // Fallback: use cloud chat WS URL directly (for OpenClaw brain or other servers)
        val baseUrl = KVUtils.getCloudChatWsUrl().trim()
        if (baseUrl.isEmpty()) return ""

        val sessionId = KVUtils.getCloudChatSessionId().ifEmpty {
            "android_${System.currentTimeMillis()}".also {
                KVUtils.setCloudChatSessionId(it)
                XLog.i(TAG, "Generated new session ID: $it")
            }
        }

        // If URL already has a path, use as-is
        if (baseUrl.contains("/ws/")) return baseUrl

        // Append CyberVerse-style path
        val base = baseUrl.trimEnd('/')
        return "$base/ws/chat/$sessionId"
    }

    /**
     * Connect standalone WebSocket.
     */
    private fun doConnectStandalone(wsUrl: String, pendingText: String?) {
        XLog.i(TAG, "Connecting standalone to $wsUrl ...")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                XLog.i(TAG, "Standalone WebSocket connected")
                isWsConnected = true
                reconnectAttempts = 0

                // Send hello handshake for OpenClaw brain compatibility
                try {
                    val hello = gson.toJson(mapOf("type" to "hello"))
                    webSocket.send(hello)
                    XLog.d(TAG, "Sent hello handshake")
                } catch (e: Exception) {
                    XLog.w(TAG, "Failed to send hello", e)
                }

                if (pendingText != null) {
                    // Small delay after hello before sending text
                    mainHandler.postDelayed({
                        sendStandaloneTextMessage(pendingText)
                    }, 500)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                XLog.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
                isWsConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                XLog.i(TAG, "WebSocket closed: $code $reason")
                isWsConnected = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                XLog.e(TAG, "WebSocket failure", t)
                isWsConnected = false

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
     * Auto-reconnect (exponential backoff)
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        if (usingSharedConnection) return // Don't reconnect standalone if in shared mode
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            XLog.w(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
            shouldReconnect = false
            return
        }

        val delay = minOf(BASE_RECONNECT_DELAY * (1L shl reconnectAttempts), MAX_RECONNECT_DELAY)
        reconnectAttempts++

        XLog.i(TAG, "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        reconnectHandler.postDelayed({
            if (shouldReconnect && !isWsConnected && !usingSharedConnection) {
                // Re-check if WebRTC is now available
                if (DirectWebRTCManager.isConnectedAndReady()) {
                    switchToSharedMode()
                    return@postDelayed
                }
                // Try to create new session via REST API
                createSessionAndConnect("", object : ChatActivity.ChatCallback {
                    override fun onProgress(step: String) {}
                    override fun onComplete(answer: String) {}
                    override fun onError(error: String) {
                        XLog.e(TAG, "Reconnect error: $error")
                    }
                })
            }
        }, delay)
    }

    /**
     * Send text message in standalone mode.
     */
    private fun sendStandaloneTextMessage(text: String) {
        val message = JsonObject().apply {
            addProperty("type", "text_input")
            addProperty("text", text)
        }

        val json = gson.toJson(message)
        XLog.d(TAG, "Sending standalone: $json")

        val sent = webSocket?.send(json) ?: false
        if (!sent) {
            mainHandler.post {
                currentCallback?.onError("消息发送失败，请重试")
                currentCallback = null
            }
        }
    }

    /**
     * Handle incoming WebSocket messages (standalone mode).
     */
    private fun handleMessage(text: String) {
        XLog.d(TAG, "Received (standalone): $text")

        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: ""

            when (type) {
                // Hello handshake response (OpenClaw brain)
                "hello" -> {
                    val sessionId = json.get("session_id")?.asString
                    XLog.i(TAG, "Hello handshake OK, session_id=$sessionId")
                    // Store the session ID for potential reconnection
                    if (sessionId != null && sessionId.isNotEmpty()) {
                        KVUtils.setCloudChatSessionId(sessionId)
                    }
                }
                // CyberVerse streaming transcript (primary for CyberVerse server)
                "transcript" -> handleTranscript(json)
                // CyberVerse streaming LLM token (compat)
                "llm_token" -> handleLlmToken(json)
                // CyberVerse avatar status
                "avatar_status" -> handleAvatarStatus(json)
                // Legacy: complete text response
                "text", "llm" -> handleTextResponse(json)
                // Push notification from server
                "push" -> handlePush(json)
                // Error
                "error" -> handleError(json)
                // WebRTC signaling messages (ignore in standalone mode)
                "webrtc_config", "webrtc_offer", "ice_candidate", "webrtc_ready" -> {
                    XLog.d(TAG, "Ignoring signaling message: $type")
                }
                else -> {
                    XLog.d(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to parse message", e)
        }
    }

    /**
     * Handle transcript: streaming token-by-token response from CyberVerse server.
     * { "type": "transcript", "speaker": "assistant", "text": "token", "is_final": false }
     */
    private fun handleTranscript(json: JsonObject) {
        val speaker = json.get("speaker")?.asString ?: ""
        val text = json.get("text")?.asString ?: ""
        val isFinal = json.get("is_final")?.asBoolean ?: false

        if (speaker != "assistant" || text.isEmpty()) return

        accumulatedText.append(text)
        val accumulated = accumulatedText.toString()

        mainHandler.post {
            currentCallback?.onProgress(accumulated)
            if (isFinal) {
                currentCallback?.onComplete(accumulated)
                currentCallback = null
                accumulatedText.clear()
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
            mainHandler.post {
                currentCallback?.onProgress(accumulated)
                if (isFinal) {
                    currentCallback?.onComplete(accumulated)
                    currentCallback = null
                }
            }
        } else if (token.isNotEmpty()) {
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
     */
    private fun handleAvatarStatus(json: JsonObject) {
        val status = json.get("status")?.asString ?: ""
        XLog.d(TAG, "Avatar status: $status")
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
     * Disconnect standalone WebSocket only.
     */
    private fun disconnectStandalone() {
        try {
            webSocket?.close(1000, "User disconnect")
        } catch (_: Exception) {}
        webSocket = null
        isWsConnected = false
    }

    /**
     * Full disconnect - both shared and standalone.
     */
    fun disconnect() {
        shouldReconnect = false
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectAttempts = 0
        accumulatedText.clear()

        // Unregister from shared connection
        if (usingSharedConnection) {
            DirectWebRTCManager.removeTextResponseListener(sharedListener)
            usingSharedConnection = false
        }

        disconnectStandalone()
        currentCallback = null
        pushListener = null
    }

    fun isConnected(): Boolean = usingSharedConnection || isWsConnected
}
