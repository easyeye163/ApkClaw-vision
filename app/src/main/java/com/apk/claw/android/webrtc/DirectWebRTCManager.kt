package com.apk.claw.android.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.webrtc.*
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Direct WebRTC manager - connects to CyberVerse server without LiveKit.
 *
 * Signaling flow:
 * 1. POST /api/v1/sessions → session_id
 * 2. WebSocket /ws/chat/{session_id} → signaling channel
 * 3. Send {type:"webrtc_ready"} → receive webrtc_config + webrtc_offer
 * 4. Create PeerConnection, set remote offer, create answer, send back
 * 5. ICE candidates exchanged over the same WebSocket
 * 6. Remote video/audio tracks rendered via SurfaceViewRenderer
 */
object DirectWebRTCManager {

    private const val TAG = "DirectWebRTC"

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    enum class AvatarStatus {
        IDLE, SPEAKING, PROCESSING
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _avatarStatus = MutableStateFlow(AvatarStatus.IDLE)
    val avatarStatus: StateFlow<AvatarStatus> = _avatarStatus

    // Idle video URLs from server (for standby animation)
    private val _idleVideoUrls = MutableStateFlow<List<String>>(emptyList())
    val idleVideoUrls: StateFlow<List<String>> = _idleVideoUrls
    val hasIdleVideo: Boolean get() = _idleVideoUrls.value.isNotEmpty()

    private var videoRenderer: SurfaceViewRenderer? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var webSocket: WebSocket? = null
    private var sessionId: String? = null
    private var eglBase: EglBase? = null
    private var eglBaseReleased = false  // Track if EglBase was released (app-level cleanup)

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Serialized signaling chain - ensures operations happen in order
    private var signalingReady = false
    private val pendingIceCandidates = mutableListOf<JsonObject>()
    @Volatile private var iceServers: List<PeerConnection.IceServer> = emptyList()
    @Volatile private var configReceived = false  // Whether webrtc_config was received
    @Volatile private var apiBaseUrlOrigin = ""  // e.g. "http://host" for resolving relative idle URLs

    // Timeout for webrtc_config - if server doesn't send it, proceed with fallback STUN
    private var configTimeoutRunnable: Runnable? = null
    private val CONFIG_TIMEOUT_MS = 3000L  // 3 seconds

    // ICE restart/retry tracking
    @Volatile private var iceRestartAttempts = 0
    private val maxIceRestartAttempts = 3
    private var iceRestartRunnable: Runnable? = null

    // Diagnostic log collection
    private val diagnosticLogs = mutableListOf<String>()
    @Volatile private var lastConnectionError: String = "unknown"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Initialize PeerConnectionFactory. Call once from Application.onCreate().
     */
    fun init(context: Context) {
        try {
            val initializationOptions = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initializationOptions)

            // Create EglBase early - shared by decoder factory and renderers
            eglBase = EglBase.create()
            val eglBaseContext = eglBase!!.eglBaseContext

            // Video decoder/encoder factories are REQUIRED for receiving/sending video
            val videoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
            val videoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, false, true)

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoDecoderFactory(videoDecoderFactory)
                .setVideoEncoderFactory(videoEncoderFactory)
                .createPeerConnectionFactory()

            Log.d(TAG, "DirectWebRTCManager initialized (with video decoder/encoder)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init WebRTC", e)
        }
    }

    /**
     * Connect to CyberVerse server using saved config.
     * 1. Create session via REST API
     * 2. Open WebSocket for signaling
     * 3. Negotiate WebRTC peer connection
     *
     * @param isRetry true if this is an ICE failure retry (don't reset retry counter)
     */
    fun connect(isRetry: Boolean = false): Boolean {
        val apiBase = KVUtils.getCyberVerseApiBase().trim()
        val wsBase = KVUtils.getCyberVerseWsBase().trim()
        val characterId = KVUtils.getCyberVerseCharacterId().trim()

        if (apiBase.isEmpty() || wsBase.isEmpty() || characterId.isEmpty()) {
            Log.w(TAG, "CyberVerse config incomplete, skipping connect")
            return false
        }

        if (!KVUtils.isWebRTCEnabled()) {
            Log.d(TAG, "CyberVerse is disabled in settings")
            return false
        }

        // Use lightweight reset - do NOT destroy renderer or EglBase
        resetConnection(isRetry)
        _connectionState.value = ConnectionState.CONNECTING
        diagnosticLogs.clear()
        addDiagnostic("connect(isRetry=$isRetry): api=$apiBase, ws=$wsBase, char=$characterId")
        Log.d(TAG, "Connecting to CyberVerse (isRetry=$isRetry): api=$apiBase, ws=$wsBase, char=$characterId")

        Thread({
            try {
                // Step 1: Create session
                val sid = createSession(apiBase, characterId)
                if (sid == null) {
                    _connectionState.value = ConnectionState.ERROR
                    return@Thread
                }
                sessionId = sid
                Log.d(TAG, "Session created: $sid")

                // Step 2: Connect WebSocket for signaling
                val wsUrl = buildWsUrl(wsBase, sid)
                connectWebSocket(wsUrl)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect CyberVerse", e)
                _connectionState.value = ConnectionState.ERROR
            }
        }, "cyberverse-direct-connect").start()

        return true
    }

    /**
     * Create a session via REST API.
     */
    private fun createSession(apiBase: String, characterId: String): String? {
        try {
            val url = if (apiBase.endsWith("/")) "${apiBase}sessions" else "$apiBase/sessions"
            val body = gson.toJson(mapOf("character_id" to characterId, "mode" to "omni"))

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Create session failed: ${response.code}")
                return null
            }

            val respStr = response.body?.string() ?: return null
            val json = JsonParser.parseString(respStr).asJsonObject
            val sid = json.get("session_id")?.asString
            Log.d(TAG, "Session response: streaming_mode=${json.get("streaming_mode")?.asString}")
            Log.d(TAG, "Session response body keys: ${json.keySet().joinToString(", ")}")

            // Parse idle video URLs from session response
            // Server may return relative paths like /api/v1/characters/.../idle-videos/...
            // which must be resolved to full URLs for Android MediaPlayer.
            apiBaseUrlOrigin = try {
                val parsed = URL(apiBase)
                "${parsed.protocol}://${parsed.host}"
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse apiBase origin: $apiBase")
                ""
            }
            val idleUrls = mutableListOf<String>()
            val idleVideoUrl = json.get("idle_video_url")?.asString
            val idleVideoUrlsArray = json.getAsJsonArray("idle_video_urls")
            if (idleVideoUrlsArray != null && idleVideoUrlsArray.size() > 0) {
                idleUrls.addAll(idleVideoUrlsArray.map { it.asString })
            } else if (!idleVideoUrl.isNullOrEmpty()) {
                idleUrls.add(idleVideoUrl)
            }
            // Resolve relative URLs to absolute
            if (apiBaseUrlOrigin.isNotEmpty() && idleUrls.any { it.startsWith("/") }) {
                val resolved = idleUrls.map { url ->
                    if (url.startsWith("http://") || url.startsWith("https://")) url
                    else "$apiBaseUrlOrigin$url"
                }
                idleUrls.clear()
                idleUrls.addAll(resolved)
                Log.d(TAG, "Resolved relative idle URLs with base origin: $apiBaseUrlOrigin")
            }
            if (idleUrls.isNotEmpty()) {
                _idleVideoUrls.value = idleUrls
                addDiagnostic("Session returned ${idleUrls.size} idle video URL(s)")
                Log.d(TAG, "Idle video URLs from session: ${idleUrls.size} urls, first=${idleUrls.firstOrNull()?.take(80)}")
            } else {
                Log.d(TAG, "No idle video URLs in session response")
            }

            return sid
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session", e)
            return null
        }
    }

    /**
     * Build WebSocket URL from base and session ID.
     */
    private fun buildWsUrl(wsBase: String, sid: String): String {
        val base = wsBase.trimEnd('/')
        return "$base/ws/chat/$sid"
    }

    /**
     * Connect WebSocket for signaling.
     */
    private fun connectWebSocket(wsUrl: String) {
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                signalingReady = false
                pendingIceCandidates.clear()
                iceServers = emptyList()
                configReceived = false
                pendingOffer = null
                configTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                configTimeoutRunnable = null

                addDiagnostic("WebSocket opened, sending webrtc_ready")
                // Send webrtc_ready to trigger negotiation
                sendWsMessage(mapOf("type" to "webrtc_ready"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JsonParser.parseString(text).asJsonObject
                    val type = json.get("type")?.asString ?: return
                    handleMessage(type, json)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing WS message", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary messages not expected
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                mainHandler.post {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _avatarStatus.value = AvatarStatus.IDLE
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                mainHandler.post {
                    if (_connectionState.value == ConnectionState.CONNECTING) {
                        _connectionState.value = ConnectionState.ERROR
                    } else {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        })
    }

    /**
     * Handle incoming WebSocket signaling messages.
     */
    private fun handleMessage(type: String, json: JsonObject) {
        when (type) {
            "webrtc_config" -> handleWebrtcConfig(json)
            "webrtc_offer" -> handleWebrtcOffer(json)
            "ice_candidate" -> handleIceCandidate(json)
            "avatar_status" -> handleAvatarStatus(json)
            "idle_video_ready" -> handleIdleVideoReady(json)
            "transcript" -> handleTranscript(json)
            "llm_token" -> handleLlmToken(json)
            else -> Log.d(TAG, "Unhandled message type: $type")
        }
    }

    /**
     * Handle webrtc_config - store ICE servers.
     */
    private fun handleWebrtcConfig(json: JsonObject) {
        try {
            val serversArray = json.getAsJsonArray("ice_servers")
            if (serversArray != null && serversArray.size() > 0) {
                iceServers = serversArray.map { server ->
                    val s = server.asJsonObject
                    val urls = if (s.has("urls")) {
                        val el = s.get("urls")
                        if (el.isJsonArray) {
                            el.asJsonArray.map { it.asString }
                        } else {
                            listOf(el.asString)
                        }
                    } else {
                        emptyList()
                    }
                    PeerConnection.IceServer.builder(urls)
                        .setUsername(s.get("username")?.asString ?: "")
                        .setPassword(s.get("credential")?.asString ?: "")
                        .createIceServer()
                }
                configReceived = true
                // Cancel the config timeout since we received config
                configTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                configTimeoutRunnable = null
                addDiagnostic("webrtc_config: ${iceServers.size} ICE servers received")
                Log.d(TAG, "ICE servers configured: ${iceServers.size} servers")
                // Log each ICE server type for debugging
                iceServers.forEachIndexed { i, server ->
                    val serverUrls = server.urls.joinToString(", ")
                    val hasTurn = serverUrls.contains("turn")
                    val hasStun = serverUrls.contains("stun")
                    val hasTcp = serverUrls.contains("?transport=tcp") || serverUrls.contains("tcp:")
                    addDiagnostic("  ICE[$i]: ${if (hasTurn) "TURN" else if (hasStun) "STUN" else "?"} $serverUrls auth=${server.username.isNotEmpty()}")
                    Log.d(TAG, "  ICE server[$i]: type=${if (hasTurn) "TURN" else if (hasStun) "STUN" else "UNKNOWN"}, urls=$serverUrls, hasAuth=${server.username.isNotEmpty()}, tcp=$hasTcp")
                }
                // If no TURN server, add warning - mobile NAT traversal likely needs TURN
                val hasTurn = iceServers.any { it.urls.any { u -> u.contains("turn") } }
                if (!hasTurn) {
                    addDiagnostic("WARNING: No TURN server! Mobile NAT may block ICE connection.")
                    Log.w(TAG, "WARNING: No TURN server configured! Mobile NAT may block P2P connection.")
                }

                // Process pending offer if it arrived before config
                pendingOffer?.let { offer ->
                    addDiagnostic("Processing pending offer (arrived before config)")
                    pendingOffer = null
                    handleWebrtcOffer(offer)
                }
            } else {
                addDiagnostic("webrtc_config: no ice_servers array or empty, proceeding with fallback STUN")
                Log.w(TAG, "webrtc_config received but no ice_servers - using fallback STUN")
                configReceived = true
                // Process pending offer even without ICE servers
                pendingOffer?.let { offer ->
                    addDiagnostic("Processing pending offer (config had no ice_servers)")
                    pendingOffer = null
                    handleWebrtcOffer(offer)
                }
            }
        } catch (e: Exception) {
            addDiagnostic("webrtc_config parse error: ${e.message}")
            Log.w(TAG, "Error parsing ICE config", e)
        }
    }

    /**
     * Handle webrtc_offer - create PeerConnection, set remote, create answer.
     * CRITICAL: Must wait for setRemoteDescription to complete before createAnswer,
     * otherwise the answer will be malformed and no video track will be negotiated.
     */
    private var pendingOffer: JsonObject? = null

    private fun handleWebrtcOffer(json: JsonObject) {
        val sdpStr = json.get("sdp")?.asString ?: return
        Log.d(TAG, "Received WebRTC offer (${sdpStr.length} chars)")

        // Debug: log offer SDP details
        val hasVideoMline = sdpStr.contains("m=video")
        val hasAudioMline = sdpStr.contains("m=audio")
        val offerVideoDir = extractSdpDirection(sdpStr, "video")
        val offerAudioDir = extractSdpDirection(sdpStr, "audio")
        addDiagnostic("webrtc_offer: ${sdpStr.length} chars, audio=$hasAudioMline($offerAudioDir), video=$hasVideoMline($offerVideoDir)")
        Log.d(TAG, "Offer SDP (${sdpStr.length} chars): audio=$hasAudioMline($offerAudioDir), video=$hasVideoMline($offerVideoDir)")
        Log.d(TAG, "Offer SDP (first 500 chars): ${sdpStr.take(500)}")
        if (!hasVideoMline) {
            addDiagnostic("CRITICAL: Offer has NO m=video line!")
            Log.w(TAG, "WARNING: SDP offer does NOT contain video media line! Server may not support video.")
        }

        // Check if ICE candidates from offer's SDP (non-trickle)
        val candidateLines = sdpStr.lines().filter { it.startsWith("a=candidate") }.size
        if (candidateLines > 0) {
            addDiagnostic("Offer contains $candidateLines embedded ICE candidates (non-trickle)")
        }

        if (!configReceived) {
            addDiagnostic("webrtc_offer arrived BEFORE webrtc_config! Starting 3s timeout, will proceed with fallback STUN if no config...")
            Log.w(TAG, "Offer arrived before config - starting ${CONFIG_TIMEOUT_MS}ms timeout")
            pendingOffer = json
            // Cancel any previous timeout
            configTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            // Start timeout - if no config arrives, proceed with fallback STUN servers
            val timeoutRunnable = Runnable {
                if (!configReceived && pendingOffer != null) {
                    addDiagnostic("Timeout waiting for webrtc_config, proceeding with fallback STUN servers")
                    Log.w(TAG, "Timeout waiting for webrtc_config, proceeding with fallback STUN servers")
                    configReceived = true  // Mark as received to allow processing
                    val offer = pendingOffer
                    pendingOffer = null
                    configTimeoutRunnable = null
                    handleWebrtcOffer(offer!!)
                }
            }
            configTimeoutRunnable = timeoutRunnable
            mainHandler.postDelayed(timeoutRunnable, CONFIG_TIMEOUT_MS)
            return
        }

        mainHandler.post {
            try {
                createPeerConnection()

                // Set remote description (offer) - MUST complete before creating answer
                val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "setRemoteDescription completed successfully")
                        // CRITICAL: Only mark signaling ready and flush ICE candidates
                        // AFTER setRemoteDescription has completed.
                        // Adding ICE candidates before remote desc is set causes them
                        // to be silently dropped, breaking the ICE connection.
                        signalingReady = true
                        val pending = pendingIceCandidates.toList()
                        pendingIceCandidates.clear()
                        if (pending.isNotEmpty()) {
                            Log.d(TAG, "Flushing ${pending.size} pending ICE candidates after setRemoteSuccess")
                            for (candidate in pending) {
                                addIceCandidateFromJson(candidate)
                            }
                        }
                        // Only create answer AFTER setRemote is done
                        createAndSendAnswer()
                    }
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "setRemoteDescription onCreateFailure: $error")
                    }
                    override fun onSetFailure(error: String?) {
                        lastConnectionError = "setRemoteDescription: $error"
                        addDiagnostic("setRemoteDescription FAILED: $error")
                        Log.e(TAG, "setRemoteDescription FAILED: $error")
                        _connectionState.value = ConnectionState.ERROR
                    }
                }, sdp)

            } catch (e: Exception) {
                Log.e(TAG, "Error handling WebRTC offer", e)
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    /**
     * Create SDP answer and send it to the server.
     * Must be called AFTER setRemoteDescription has completed.
     */
    private fun createAndSendAnswer() {
        try {
            val constraints = MediaConstraints()
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        Log.e(TAG, "createAnswer returned null SDP")
                        _connectionState.value = ConnectionState.ERROR
                        return
                    }
                    // Debug: log answer content details
                    val desc = sdp.description ?: ""
                    val answerHasVideo = desc.contains("m=video")
                    val answerHasAudio = desc.contains("m=audio")
                    val answerVideoDirection = extractSdpDirection(desc, "video")
                    val answerAudioDirection = extractSdpDirection(desc, "audio")
                    Log.d(TAG, "Answer created: video=$answerHasVideo($answerVideoDirection), audio=$answerHasAudio($answerAudioDirection)")
                    Log.d(TAG, "Answer SDP (first 500 chars): ${desc.take(500)}")

                    if (!answerHasVideo) {
                        Log.e(TAG, "CRITICAL: Answer does NOT contain m=video! Video will not work.")
                    }

                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "setLocalDescription completed")
                        }
                        override fun onCreateFailure(error: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "setLocalDescription FAILED: $error")
                        }
                    }, sdp)
                    sendWsMessage(mapOf("type" to "webrtc_answer", "sdp" to sdp.description))
                    Log.d(TAG, "WebRTC answer sent")
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create answer failed: $error")
                    _connectionState.value = ConnectionState.ERROR
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating answer", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Create PeerConnection with current ICE servers.
     */
    private fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return

        // Build effective ICE server list: server-provided + fallback STUN servers
        val fallbackStunServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer()
        )
        val effectiveIceServers = if (iceServers.isEmpty()) {
            addDiagnostic("No server ICE servers, using ${fallbackStunServers.size} fallback STUN servers")
            fallbackStunServers
        } else {
            addDiagnostic("Using ${iceServers.size} server ICE servers + ${fallbackStunServers.size} fallback STUN")
            iceServers + fallbackStunServers
        }

        val rtcConfig = PeerConnection.RTCConfiguration(effectiveIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Keep gathering ICE candidates continuously (better for mobile networks)
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            // Pre-gather ICE candidates to speed up connection
            iceCandidatePoolSize = 10
            // Try all network interfaces (WiFi, cellular, VPN)
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            // Bundle all media on single transport (reduces ICE pairs needed)
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            // Multiplex RTCP over RTP transport (simplifies ICE)
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        addDiagnostic("RTC config: ${effectiveIceServers.size} ICE servers, MAXBUNDLE, RTCP_MUX_REQUIRE")
        Log.d(TAG, "RTC config: ${effectiveIceServers.size} ICE servers, continualGathering, MAXBUNDLE, rtcpMuxRequire, candidatePool=10")

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _connectionState.value = ConnectionState.CONNECTED
                        startStatsMonitor()
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        lastConnectionError = "ICE_FAILED"
                        addDiagnostic("ICE FAILED (attempt ${iceRestartAttempts + 1}/$maxIceRestartAttempts)")
                        Log.e(TAG, "ICE connection FAILED (attempt ${iceRestartAttempts + 1}/$maxIceRestartAttempts)")
                        statsMonitorJob?.cancel()
                        statsMonitorJob = null

                        if (iceRestartAttempts < maxIceRestartAttempts) {
                            iceRestartAttempts++
                            addDiagnostic("Scheduling retry $iceRestartAttempts/$maxIceRestartAttempts in 3s...")
                            Log.w(TAG, "ICE FAILED, will retry connection in 3s (attempt $iceRestartAttempts/$maxIceRestartAttempts)")
                            // Cancel any pending restart
                            iceRestartRunnable?.let { mainHandler.removeCallbacks(it) }
                            iceRestartRunnable = Runnable {
                                Log.d(TAG, "ICE restart attempt $iceRestartAttempts - reconnecting...")
                                connect(isRetry = true)
                            }
                            mainHandler.postDelayed(iceRestartRunnable!!, 3000)
                        } else {
                            addDiagnostic("ICE FAILED after all retries. DIAGNOSTIC: ${getDiagnosticSummary()}")
                            Log.e(TAG, "ICE FAILED after $maxIceRestartAttempts retries, giving up")
                            _connectionState.value = ConnectionState.ERROR
                        }
                    }
                    // DISCONNECTED is often transient (network glitch), don't treat as ERROR.
                    // Only log it; if it recovers to CONNECTED the state will update.
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "ICE connection DISCONNECTED (may recover)")
                    }
                    PeerConnection.IceConnectionState.NEW,
                    PeerConnection.IceConnectionState.CHECKING -> {
                        if (_connectionState.value != ConnectionState.CONNECTED) {
                            _connectionState.value = ConnectionState.CONNECTING
                        }
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "Local ICE candidate: mid=${candidate.sdpMid}, idx=${candidate.sdpMLineIndex}, sdp=${candidate.sdp.take(60)}")
                    sendWsMessage(mapOf(
                        "type" to "ice_candidate",
                        "candidate" to candidate.sdp,
                        "sdp_mid" to (candidate.sdpMid ?: ""),
                        "sdp_mline_index" to candidate.sdpMLineIndex
                    ))
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack called: receiver=$receiver, streams count=${streams?.size ?: 0}")
                // In Unified Plan, streams may be empty - get track from receiver
                val track = receiver?.track()
                Log.d(TAG, "onAddTrack track: $track (type=${track?.kind()})")
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    Log.d(TAG, "Remote video track received via receiver (enabled=${track.enabled()})")
                    mainHandler.post {
                        attachRenderer()
                        // Notify avatar that video is flowing
                        _avatarStatus.value = AvatarStatus.PROCESSING
                    }
                    return
                }
                if (track is org.webrtc.AudioTrack) {
                    Log.d(TAG, "Remote audio track received via receiver")
                    return
                }
                // Fallback: check streams array (Plan B compatibility)
                streams?.forEach { stream ->
                    stream.videoTracks?.forEach { vt ->
                        if (vt is VideoTrack && remoteVideoTrack == null) {
                            remoteVideoTrack = vt
                            Log.d(TAG, "Remote video track received via stream (enabled=${vt.enabled()})")
                            mainHandler.post {
                                attachRenderer()
                                _avatarStatus.value = AvatarStatus.PROCESSING
                            }
                        }
                    }
                }
            }
        })

        // Add explicit video transceiver for receiving remote avatar video.
        // In Unified Plan, this is more reliable than OfferToReceiveVideo constraint.
        // Must be called BEFORE setRemoteDescription so it matches the offer's m=video.
        try {
            val videoTransceiver = peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                )
            )
            Log.d(TAG, "Video RECV_ONLY transceiver added: $videoTransceiver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add video transceiver", e)
        }

        // Add local audio track (microphone)
        try {
            val audioConstraints = MediaConstraints()
            val audioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("mic", audioSource)
            peerConnection?.addTrack(localAudioTrack)
            Log.d(TAG, "Local audio track added")
        } catch (e: Exception) {
            Log.w(TAG, "Could not add local audio track (mic may be denied)", e)
        }
    }

    /**
     * Handle incoming ICE candidate.
     */
    private fun handleIceCandidate(json: JsonObject) {
        val candidate = json.get("candidate")?.asString ?: return
        if (candidate.isEmpty()) return

        val sdpMid = json.get("sdp_mid")?.asString
        val sdpMLineIndex = json.get("sdp_mline_index")?.asInt
        Log.d(TAG, "Remote ICE candidate: mid=$sdpMid, idx=$sdpMLineIndex, sdp=${candidate.take(60)}")

        if (signalingReady && peerConnection != null) {
            addIceCandidateFromJson(json)
        } else {
            // Queue for later processing
            pendingIceCandidates.add(json)
            Log.d(TAG, "Queued ICE candidate (signalingReady=$signalingReady, pc=${peerConnection != null}), queue=${pendingIceCandidates.size}")
        }
    }

    private fun addIceCandidateFromJson(json: JsonObject) {
        try {
            val candidate = IceCandidate(
                json.get("sdp_mid")?.asString ?: "0",
                json.get("sdp_mline_index")?.asInt ?: 0,
                json.get("candidate")?.asString ?: ""
            )
            peerConnection?.addIceCandidate(candidate)
        } catch (e: Exception) {
            Log.w(TAG, "Error adding ICE candidate", e)
        }
    }

    /**
     * Handle avatar_status message.
     */
    private fun handleAvatarStatus(json: JsonObject) {
        val status = json.get("status")?.asString ?: return
        mainHandler.post {
            _avatarStatus.value = when (status) {
                "speaking" -> AvatarStatus.SPEAKING
                "processing" -> AvatarStatus.PROCESSING
                else -> AvatarStatus.IDLE
            }
        }
    }

    /**
     * Handle idle_video_ready - server sends idle video URLs dynamically.
     */
    private fun handleIdleVideoReady(json: JsonObject) {
        val urlsArray = json.getAsJsonArray("urls")
        val singleUrl = json.get("url")?.asString
        val rawUrls: List<String> = if (urlsArray != null && urlsArray.size() > 0) {
            urlsArray.map { it.asString }
        } else if (!singleUrl.isNullOrEmpty()) {
            listOf(singleUrl)
        } else {
            return
        }

        // Resolve relative URLs to absolute using stored apiBaseUrlOrigin
        val resolvedUrls = if (apiBaseUrlOrigin.isNotEmpty()) {
            rawUrls.map { url ->
                if (url.startsWith("http://") || url.startsWith("https://")) url
                else "$apiBaseUrlOrigin$url"
            }
        } else {
            rawUrls
        }

        _idleVideoUrls.value = resolvedUrls
        addDiagnostic("idle_video_ready: ${resolvedUrls.size} URLs received")
        Log.d(TAG, "Idle video ready: ${resolvedUrls.size} URLs, first=${resolvedUrls.firstOrNull()?.take(100)}")
    }

    /**
     * Handle transcript message (voice pipeline).
     */
    private fun handleTranscript(json: JsonObject) {
        val speaker = json.get("speaker")?.asString ?: return
        val text = json.get("text")?.asString ?: return
        val isFinal = json.get("is_final")?.asBoolean ?: false
        Log.d(TAG, "Transcript [$speaker${if (isFinal) "" else " (partial)"}]: $text")

        // Forward to text response listener for VoiceLLM mode
        if (speaker == "assistant") {
            mainHandler.post {
                textResponseListener?.onTextResponse(text, isFinal)
            }
        }
    }

    /**
     * Handle llm_token message (text pipeline - streaming).
     */
    private fun handleLlmToken(json: JsonObject) {
        val accumulated = json.get("accumulated")?.asString ?: return
        val isFinal = json.get("is_final")?.asBoolean ?: false
        Log.d(TAG, "LLM token [${if (isFinal) "final" else "streaming"}]: ${accumulated.take(50)}...")

        mainHandler.post {
            textResponseListener?.onLlmToken(accumulated, isFinal)
        }
    }

    /**
     * Listener for text/LLM responses (used by VoiceLLM chat mode).
     */
    interface TextResponseListener {
        fun onLlmToken(accumulated: String, isFinal: Boolean)
        fun onTextResponse(text: String, isFinal: Boolean)
    }

    private var textResponseListener: TextResponseListener? = null

    fun setTextResponseListener(listener: TextResponseListener?) {
        textResponseListener = listener
    }

    /**
     * Get the shared EglBase instance (created during init).
     * EglBase has app-level lifetime - never released during disconnect/reconnect.
     */
    fun getEglBase(): EglBase? {
        if (eglBase == null && !eglBaseReleased) {
            try {
                eglBase = EglBase.create()
                Log.w(TAG, "EglBase created lazily (should have been created in init)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create EglBase", e)
            }
        }
        return eglBase
    }

    /**
     * Bind a SurfaceViewRenderer to display the remote avatar video.
     */
    fun bindRenderer(renderer: SurfaceViewRenderer) {
        videoRenderer = renderer
        try {
            val egl = getEglBase()
            if (egl != null) {
                renderer.init(egl.eglBaseContext, null)
                renderer.setMirror(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing renderer", e)
        }
        attachRenderer()
    }

    /**
     * Unbind and clean up the video renderer.
     */
    fun unbindRenderer() {
        videoRenderer?.let {
            try {
                (it as? SurfaceViewRenderer)?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing renderer", e)
            }
        }
        videoRenderer = null
    }

    private fun attachRenderer() {
        val renderer = videoRenderer ?: return
        val track = remoteVideoTrack ?: return
        try {
            track.addSink(renderer)
            Log.d(TAG, "Remote video track attached to renderer")
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching video track to renderer", e)
        }
    }

    /**
     * Send text message to the avatar via WebSocket.
     */
    fun sendTextMessage(text: String) {
        if (webSocket == null) {
            Log.w(TAG, "Cannot send text - not connected")
            return
        }
        sendWsMessage(mapOf("type" to "text_input", "text" to text))
    }

    /**
     * Interrupt current avatar speech.
     */
    fun interrupt() {
        sendWsMessage(mapOf("type" to "interrupt"))
    }

    /**
     * Lightweight connection reset - closes WS + PeerConnection but preserves
     * renderer and EglBase (they have app-level lifetime).
     */
    private fun resetConnection(isRetry: Boolean = false) {
        try {
            webSocket?.close(1000, "reset")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket", e)
        }
        webSocket = null
        sessionId = null
        signalingReady = false
        pendingIceCandidates.clear()
        pendingOffer = null
        configReceived = false
        configTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        configTimeoutRunnable = null
        // NOTE: Do NOT reset iceRestartAttempts here - only reset on explicit user disconnect
        iceRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        iceRestartRunnable = null

        // Cancel stats monitor
        statsMonitorJob?.cancel()
        statsMonitorJob = null

        // Remove sink but do NOT unbind/release the renderer
        try {
            remoteVideoTrack?.removeSink(videoRenderer)
        } catch (_: Exception) {}
        remoteVideoTrack = null

        try {
            localAudioTrack?.dispose()
        } catch (_: Exception) {}
        localAudioTrack = null

        try {
            peerConnection?.close()
        } catch (_: Exception) {}
        peerConnection = null

        // Don't change connectionState here - let the caller decide
        // (connect() sets it to CONNECTING, disconnect() sets it to DISCONNECTED)
        if (peerConnection == null && webSocket == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        _avatarStatus.value = AvatarStatus.IDLE
    }

    /**
     * Reset ICE retry counter (call on user-initiated disconnect only).
     */
    private fun resetRetryCounter() {
        iceRestartAttempts = 0
        iceRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        iceRestartRunnable = null
    }

    /**
     * Disconnect and clean up connection resources.
     * Does NOT destroy renderer or EglBase - those have app-level lifetime.
     */
    fun disconnect() {
        resetRetryCounter()  // User explicitly disconnected, reset retry counter
        resetConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Full cleanup - releases all resources including EglBase and PeerConnectionFactory.
     * Only call when the app is being destroyed.
     */
    fun destroy() {
        resetRetryCounter()  // Reset on app destroy
        disconnect()
        unbindRenderer()

        try {
            eglBase?.release()
        } catch (_: Exception) {}
        eglBase = null
        eglBaseReleased = true

        try {
            peerConnectionFactory?.dispose()
        } catch (_: Exception) {}
        peerConnectionFactory = null
    }

    /**
     * Send a JSON message over WebSocket.
     */
    private fun sendWsMessage(data: Map<String, Any?>) {
        try {
            val json = gson.toJson(data)
            webSocket?.send(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WS message", e)
        }
    }

    /**
     * Check if a remote video track has been received.
     */
    fun hasRemoteVideoTrack(): Boolean = remoteVideoTrack != null

    /**
     * Get current session ID.
     */
    fun getSessionId(): String? = sessionId

    /**
     * Send assistant text to CyberVerse for TTS/avatar (used by OpenClaw mode bridge).
     */
    fun sendAssistantText(text: String) {
        if (webSocket == null) {
            Log.w(TAG, "Cannot send assistant text - not connected")
            return
        }
        sendWsMessage(mapOf("type" to "assistant_text", "text" to text))
    }

    /**
     * Periodically log ICE/track stats for debugging video flow.
     */
    private fun startStatsMonitor() {
        statsMonitorJob?.cancel()
        statsMonitorJob = CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(10000)
            while (true) {
                try {
                    val pc = peerConnection ?: break
                    pc.getStats { report ->
                        var videoBytes = 0L
                        var audioBytes = 0L
                        for (stat in report.statsMap.values) {
                            if (stat.type == "inbound-rtp") {
                                val kind = stat.members["kind"]?.toString()
                                val bytes = stat.members["bytesReceived"]?.toString()?.toLongOrNull() ?: 0L
                                if (kind == "video") videoBytes += bytes
                                else if (kind == "audio") audioBytes += bytes
                            }
                        }
                        if (videoBytes > 0 || audioBytes > 0) {
                            Log.d(TAG, "Stats: video bytes=$videoBytes, audio bytes=$audioBytes")
                        }
                        if (videoBytes == 0L && _connectionState.value == ConnectionState.CONNECTED) {
                            Log.w(TAG, "Stats: NO video bytes received despite being connected")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting stats", e)
                }
                kotlinx.coroutines.delay(10000)
            }
        }
    }

    private var statsMonitorJob: kotlinx.coroutines.Job? = null

    /**
     * Extract the direction attribute for a specific media type from SDP.
     * e.g., for video: finds "a=sendrecv" or "a=recvonly" etc. after m=video line.
     */
    private fun extractSdpDirection(sdp: String, mediaType: String): String {
        val lines = sdp.lines()
        var inTargetMedia = false
        for (line in lines) {
            if (line.startsWith("m=$mediaType")) {
                inTargetMedia = true
                continue
            }
            if (inTargetMedia && line.startsWith("m=")) {
                break // moved to next media section
            }
            if (inTargetMedia && line.startsWith("a=")) {
                val attr = line.substring(2)
                if (attr == "sendrecv" || attr == "sendonly" || attr == "recvonly" || attr == "inactive") {
                    return attr
                }
            }
        }
        return "unknown"
    }

    /**
     * Add a diagnostic log entry.
     */
    private fun addDiagnostic(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        diagnosticLogs.add("[$timestamp] $msg")
        // Keep last 50 entries
        while (diagnosticLogs.size > 50) diagnosticLogs.removeAt(0)
    }

    /**
     * Get diagnostic summary for debugging.
     */
    fun getDiagnosticSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== WebRTC Diagnostic ===")
        sb.appendLine("Connection: ${_connectionState.value}")
        sb.appendLine("Last error: $lastConnectionError")
        sb.appendLine("ICE restart attempts: $iceRestartAttempts")
        sb.appendLine("Config received: $configReceived")
        sb.appendLine("Signaling ready: $signalingReady")
        sb.appendLine("Remote video track: ${remoteVideoTrack != null}")
        sb.appendLine("Pending ICE candidates: ${pendingIceCandidates.size}")
        sb.appendLine("ICE servers: ${iceServers.size}")
        sb.appendLine()
        sb.appendLine("--- Log ---")
        for (log in diagnosticLogs) {
            sb.appendLine(log)
        }
        return sb.toString()
    }

    /**
     * Get last connection error reason.
     */
    fun getLastConnectionError(): String = lastConnectionError

    /**
     * Simple SdpObserver with logging callbacks.
     */
    private class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e("SdpObserver", "onCreateFailure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e("SdpObserver", "onSetFailure: $error")
        }
    }
}
