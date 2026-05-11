package com.apk.claw.android.webrtc

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.utils.KVUtils
import org.webrtc.SurfaceViewRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Floating avatar window manager (singleton).
 *
 * Two-layer video system (same as Vue frontend):
 * - **Standby layer**: Loops server-provided idle videos (TextureView + MediaPlayer)
 * - **WebRTC layer**: Live avatar video (SurfaceViewRenderer)
 *
 * Display mode switching logic (mirrors Vue SessionPage.vue):
 * - Idle + has idle video → show standby (avatar keeps moving)
 * - Speaking + fresh WebRTC frames → show WebRTC
 * - Speaking but no fresh frames yet → show standby (fallback)
 * - No idle video + no WebRTC → show placeholder icon
 */
object FloatingAvatarManager {

    private const val TAG = "FloatingAvatarManager"
    private const val PREF_X = "floating_avatar_x"
    private const val PREF_Y = "floating_avatar_y"
    private const val PREF_SIZE = "floating_avatar_size"
    private val DEFAULT_SIZE_DP = 120
    private val MIN_SIZE_DP = 60
    private val MAX_SIZE_DP = 300

    // Display modes (mirrors Vue: webrtc | standby | placeholder)
    private enum class DisplayMode { STANDBY, WEBRTC, PLACEHOLDER }

    private var windowManager: WindowManager? = null
    private var avatarView: View? = null
    private var renderer: SurfaceViewRenderer? = null
    private var standbyTextureView: TextureView? = null
    private var standbyMediaPlayer: MediaPlayer? = null
    private var statusIndicator: ImageView? = null
    private var speakingIndicator: View? = null
    private var resizeHandle: View? = null

    private var isDragging = false
    private var isResizing = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialSize = 0

    private var currentSizeDp = DEFAULT_SIZE_DP

    // Standby idle video state
    private var idleVideoUrls: List<String> = emptyList()
    private var currentIdleVideoIndex = 0
    private var standbySurfaceReady = false
    private var standbyPrepared = false

    // WebRTC video flow tracking
    private var hasVideoFlow = false
    private var lastWebRTCFrameTime = 0L  // System.currentTimeMillis of last confirmed frame
    private val FRESH_FRAME_TIMEOUT_MS = 3000L

    private var appContext: Context? = null

    // Coroutine jobs
    private var observeConnectionJob: Job? = null
    private var videoFlowCheckJob: Job? = null
    private var displayModeJob: Job? = null

    /**
     * Show the floating avatar window.
     * Must be called from main thread.
     */
    @JvmStatic
    fun show() {
        val ctx = appContext ?: ClawApplication.instance
        appContext = ctx

        if (avatarView != null) return

        if (!KVUtils.hasCyberVerseConfig()) {
            Log.d(TAG, "No CyberVerse config, skip showing avatar")
            return
        }

        // Ensure WebRTC is enabled when user explicitly opens avatar
        if (!KVUtils.isWebRTCEnabled()) {
            KVUtils.setWebRTCEnabled(true)
        }

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // Restore saved size or use default
        currentSizeDp = KVUtils.getInt(PREF_SIZE, DEFAULT_SIZE_DP)
        val sizePx = (currentSizeDp * ctx.resources.displayMetrics.density).toInt()

        // Create the video renderer (org.webrtc.SurfaceViewRenderer)
        renderer = SurfaceViewRenderer(ctx).apply {
            setZOrderMediaOverlay(true)
        }

        // Create standby TextureView for idle video playback
        standbyTextureView = TextureView(ctx)

        // Inflate the avatar overlay layout
        val inflater = LayoutInflater.from(ctx)
        avatarView = inflater.inflate(R.layout.layout_floating_avatar, null).also { view ->
            val container = view.findViewById<FrameLayout>(R.id.avatarVideoContainer)
            // Add standby first (behind), then renderer on top
            standbyTextureView?.let { container?.addView(it, 0) }
            container?.addView(renderer)
            statusIndicator = view.findViewById(R.id.ivAvatarStatus)
            speakingIndicator = view.findViewById(R.id.speakingIndicator)
            resizeHandle = view.findViewById(R.id.resizeHandle)
        }

        val params = createWindowParams(sizePx)

        // Restore saved position
        val savedX = KVUtils.getInt(PREF_X, -1)
        val savedY = KVUtils.getInt(PREF_Y, -1)
        if (savedX >= 0 && savedY >= 0) {
            params.x = savedX
            params.y = savedY
        }

        // Touch handling for dragging (on the root view)
        avatarView?.setOnTouchListener { _, event ->
            handleTouch(event, params)
        }

        // Separate touch handling for resize handle
        resizeHandle?.setOnTouchListener { _, event ->
            handleResizeTouch(event, params)
        }

        try {
            wm.addView(avatarView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding avatar view to WindowManager", e)
            avatarView = null
            return
        }

        // Set up standby video player (surface listener)
        setupStandbyVideo(ctx)

        // Check if server already provided idle video URLs
        val existingUrls = DirectWebRTCManager.idleVideoUrls.value
        if (existingUrls.isNotEmpty()) {
            onIdleVideoUrlsUpdated(existingUrls)
        }

        // Initially show placeholder (status indicator) - standby will take over once URLs arrive
        applyDisplayMode(DisplayMode.PLACEHOLDER)

        // Bind the renderer to DirectWebRTCManager
        renderer?.let { DirectWebRTCManager.bindRenderer(it) }

        // Observe all state flows
        observeState()

        // Trigger WebRTC connection if not already connected or in error state
        val connState = DirectWebRTCManager.connectionState.value
        if (connState == DirectWebRTCManager.ConnectionState.DISCONNECTED ||
            connState == DirectWebRTCManager.ConnectionState.ERROR) {
            Log.d(TAG, "Triggering WebRTC connection (state=$connState)...")
            DirectWebRTCManager.connect()
        }

        Log.d(TAG, "Floating avatar shown (size=${currentSizeDp}dp)")
    }

    /**
     * Handle touch events for dragging the floating avatar.
     */
    private fun handleTouch(event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && !isResizing) {
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                        resizeHandle?.visibility = View.VISIBLE
                        resizeHandle?.alpha = 0.6f
                    }
                }
                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(avatarView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating avatar position", e)
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    KVUtils.putInt(PREF_X, params.x)
                    KVUtils.putInt(PREF_Y, params.y)
                }
                if (!isResizing) {
                    resizeHandle?.postDelayed({
                        if (!isDragging && !isResizing) {
                            resizeHandle?.animate()?.alpha(0f)?.withEndAction {
                                resizeHandle?.visibility = View.GONE
                            }?.start()
                        }
                    }, 2000)
                }
                isDragging = false
                true
            }
            else -> false
        }
    }

    /**
     * Handle touch events for resizing the floating avatar (bottom-right corner).
     * Resizes proportionally (maintains square aspect ratio).
     */
    private fun handleResizeTouch(event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        val ctx = appContext ?: return false
        val density = ctx.resources.displayMetrics.density

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isResizing = true
                isDragging = false
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialSize = (params.width / density).toInt()
                resizeHandle?.visibility = View.VISIBLE
                resizeHandle?.alpha = 0.8f
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val deltaDp = (Math.max(dx, dy) / density).toInt()

                var newSizeDp = initialSize + deltaDp
                newSizeDp = newSizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)

                val newSizePx = (newSizeDp * density).toInt()
                if (newSizePx != params.width) {
                    params.width = newSizePx
                    params.height = newSizePx
                    currentSizeDp = newSizeDp
                    try {
                        windowManager?.updateViewLayout(avatarView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error resizing avatar", e)
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                isResizing = false
                KVUtils.putInt(PREF_SIZE, currentSizeDp)
                KVUtils.putInt(PREF_X, params.x)
                KVUtils.putInt(PREF_Y, params.y)
                Log.d(TAG, "Avatar resized to ${currentSizeDp}dp, saved")
                resizeHandle?.postDelayed({
                    if (!isDragging && !isResizing) {
                        resizeHandle?.animate()?.alpha(0f)?.withEndAction {
                            resizeHandle?.visibility = View.GONE
                        }?.start()
                    }
                }, 2000)
                true
            }
            else -> false
        }
    }

    /**
     * Set up the standby idle video player using TextureView.
     */
    private fun setupStandbyVideo(ctx: Context) {
        standbyTextureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                standbySurfaceReady = true
                Log.d(TAG, "Standby TextureView surface ready")
                // If we already have URLs, start playing immediately
                if (idleVideoUrls.isNotEmpty()) {
                    playIdleVideo(ctx)
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                standbySurfaceReady = false
                stopStandbyVideo()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    /**
     * Called when idle video URLs are received from server (session response or WS message).
     */
    private fun onIdleVideoUrlsUpdated(urls: List<String>) {
        val newUrls = urls.filter { it.isNotBlank() }
        if (newUrls.isEmpty()) return

        val hadUrls = idleVideoUrls.isNotEmpty()
        idleVideoUrls = newUrls
        currentIdleVideoIndex = 0
        Log.d(TAG, "Idle video URLs updated: ${newUrls.size} urls, first=${newUrls.firstOrNull()?.take(100)}")
        // Warn if any URL looks like a relative path (should have been resolved already)
        newUrls.forEach { url ->
            if (url.startsWith("/") && !url.startsWith("//")) {
                Log.w(TAG, "WARNING: Idle video URL looks like a relative path: $url")
            }
        }

        val ctx = appContext ?: return
        if (standbySurfaceReady) {
            playIdleVideo(ctx)
        }

        // If this is the first time we get URLs, the display mode should update
        if (!hadUrls) {
            // Trigger display mode re-evaluation will happen via observeState
        }
    }

    /**
     * Play current idle video (from idleVideoUrls[currentIdleVideoIndex]).
     */
    private fun playIdleVideo(ctx: Context) {
        if (idleVideoUrls.isEmpty() || !standbySurfaceReady) return

        stopStandbyVideo()

        try {
            val url = idleVideoUrls[currentIdleVideoIndex]
            val surfaceTexture = standbyTextureView?.surfaceTexture ?: return
            val surface = Surface(surfaceTexture)

            standbyMediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setSurface(surface)
                isLooping = (idleVideoUrls.size == 1)  // Native loop if only 1 video
                setVolume(0f, 0f)  // Mute
                prepareAsync()
                setOnPreparedListener { mp ->
                    standbyPrepared = true
                    mp.start()
                    Log.d(TAG, "Standby video playing: index=$currentIdleVideoIndex, url=${url.take(60)}")
                }
                setOnCompletionListener {
                    // If multiple videos, cycle to next one
                    if (idleVideoUrls.size > 1) {
                        currentIdleVideoIndex = (currentIdleVideoIndex + 1) % idleVideoUrls.size
                        Log.d(TAG, "Standby video completed, cycling to index $currentIdleVideoIndex")
                        playIdleVideo(ctx)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Standby video error: what=$what extra=$extra, url=${url.take(120)}")
                    // If URL looks like a relative path, log helpful hint
                    if (url.startsWith("/")) {
                        Log.e(TAG, "ERROR: Idle video URL is a relative path (starts with '/')! " +
                                "MediaPlayer requires absolute URL (http://...). " +
                                "Check DirectWebRTCManager.createSession() URL resolution.")
                    }
                    // Try next video if available
                    if (idleVideoUrls.size > 1) {
                        currentIdleVideoIndex = (currentIdleVideoIndex + 1) % idleVideoUrls.size
                        playIdleVideo(ctx)
                    }
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing idle video", e)
        }
    }

    /**
     * Stop standby video playback.
     */
    private fun stopStandbyVideo() {
        standbyPrepared = false
        try {
            standbyMediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            standbyMediaPlayer?.release()
        } catch (_: Exception) {}
        standbyMediaPlayer = null
    }

    /**
     * Switch to WebRTC video display mode.
     */
    private fun switchToWebRTC() {
        if (!hasVideoFlow) {
            hasVideoFlow = true
            lastWebRTCFrameTime = System.currentTimeMillis()
            Log.d(TAG, "Switching to WebRTC display mode")
        }
        renderer?.visibility = View.VISIBLE
        standbyTextureView?.visibility = View.GONE
        // Stop standby to save bandwidth when WebRTC is active
        if (standbyPrepared) {
            stopStandbyVideo()
        }
    }

    /**
     * Switch to standby idle video display mode.
     */
    private fun switchToStandby() {
        hasVideoFlow = false
        renderer?.visibility = View.GONE
        standbyTextureView?.visibility = View.VISIBLE

        // Restart standby if it was stopped and we have URLs
        if (standbyMediaPlayer == null && idleVideoUrls.isNotEmpty() && standbySurfaceReady) {
            val ctx = appContext ?: return
            playIdleVideo(ctx)
        }
        Log.d(TAG, "Switching to standby display mode")
    }

    /**
     * Switch to placeholder (status icon, no video).
     */
    private fun switchToPlaceholder() {
        hasVideoFlow = false
        renderer?.visibility = View.GONE
        standbyTextureView?.visibility = View.GONE
        stopStandbyVideo()
        Log.d(TAG, "Switching to placeholder display mode")
    }

    /**
     * Apply a display mode change.
     */
    private fun applyDisplayMode(mode: DisplayMode) {
        when (mode) {
            DisplayMode.WEBRTC -> switchToWebRTC()
            DisplayMode.STANDBY -> switchToStandby()
            DisplayMode.PLACEHOLDER -> switchToPlaceholder()
        }
    }

    /**
     * Show WebRTC video (called when video frames confirmed flowing).
     * Public API for DirectWebRTCManager integration.
     */
    fun showWebRTCVideo() {
        lastWebRTCFrameTime = System.currentTimeMillis()
        switchToWebRTC()
    }

    /**
     * Hide the floating avatar window.
     */
    @JvmStatic
    fun hide() {
        observeConnectionJob?.cancel()
        observeConnectionJob = null
        displayModeJob?.cancel()
        displayModeJob = null
        videoFlowCheckJob?.cancel()
        videoFlowCheckJob = null

        stopStandbyVideo()
        hasVideoFlow = false

        DirectWebRTCManager.unbindRenderer()

        avatarView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing avatar view", e)
            }
        }
        avatarView = null
        renderer = null
        standbyTextureView = null
        statusIndicator = null
        speakingIndicator = null
        resizeHandle = null
        isDragging = false
        isResizing = false
        idleVideoUrls = emptyList()
        currentIdleVideoIndex = 0
        Log.d(TAG, "Floating avatar hidden")
    }

    /**
     * Check if avatar is currently showing.
     */
    @JvmStatic
    fun isShowing(): Boolean = avatarView != null

    /**
     * Toggle avatar visibility.
     */
    @JvmStatic
    fun toggle() {
        if (isShowing()) {
            hide()
        } else {
            show()
        }
    }

    private fun createWindowParams(sizePx: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }
    }

    /**
     * Observe all state flows and manage display mode switching.
     * Uses a single combined flow to avoid race conditions between
     * connection state, avatar status, and idle video URLs.
     * Mirrors the Vue SessionPage.vue displayMode computed property.
     */
    private fun observeState() {
        // Single combined flow: recomputes display mode whenever any input changes
        displayModeJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                DirectWebRTCManager.connectionState,
                DirectWebRTCManager.avatarStatus,
                DirectWebRTCManager.idleVideoUrls
            ) { connState, avatarStatus, idleUrls ->
                Triple(connState, avatarStatus, idleUrls)
            }.collect { (connState, avatarStatus, idleUrls) ->
                // Update idle video URLs and start playing if new
                val newUrls = idleUrls.filter { it.isNotBlank() }
                if (newUrls != idleVideoUrls) {
                    if (newUrls.isNotEmpty()) {
                        onIdleVideoUrlsUpdated(newUrls)
                    }
                }

                // Update speaking indicator
                when (avatarStatus) {
                    DirectWebRTCManager.AvatarStatus.SPEAKING -> {
                        speakingIndicator?.visibility = View.VISIBLE
                    }
                    DirectWebRTCManager.AvatarStatus.PROCESSING -> {
                        speakingIndicator?.visibility = View.VISIBLE
                    }
                    DirectWebRTCManager.AvatarStatus.IDLE -> {
                        speakingIndicator?.visibility = View.GONE
                    }
                }

                // Update status indicator based on connection state
                when (connState) {
                    DirectWebRTCManager.ConnectionState.CONNECTING -> {
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_connecting)
                        statusIndicator?.visibility = View.VISIBLE
                        centerStatusIcon()
                        switchToPlaceholder()
                    }
                    DirectWebRTCManager.ConnectionState.CONNECTED -> {
                        // Hide status indicator on successful connection
                        statusIndicator?.visibility = View.GONE

                        // Display mode logic (mirrors Vue exactly):
                        // 1. Idle + has idle video → standby
                        // 2. Speaking → WebRTC (force switch)
                        // 3. Idle + no idle video → WebRTC (let it show whatever the server sends)
                        // 4. Processing → keep current mode
                        when (avatarStatus) {
                            DirectWebRTCManager.AvatarStatus.SPEAKING -> {
                                lastWebRTCFrameTime = System.currentTimeMillis()
                                switchToWebRTC()
                            }
                            DirectWebRTCManager.AvatarStatus.PROCESSING -> {
                                // Keep current mode during processing
                                if (!hasVideoFlow && idleVideoUrls.isNotEmpty()) {
                                    switchToStandby()
                                }
                            }
                            DirectWebRTCManager.AvatarStatus.IDLE -> {
                                if (idleVideoUrls.isNotEmpty()) {
                                    switchToStandby()
                                } else {
                                    // No idle videos - show WebRTC layer so server video is visible
                                    switchToWebRTC()
                                }
                            }
                        }
                    }
                    DirectWebRTCManager.ConnectionState.ERROR -> {
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_error)
                        statusIndicator?.visibility = View.VISIBLE
                        moveStatusToCorner()
                        videoFlowCheckJob?.cancel()
                        if (idleVideoUrls.isNotEmpty()) {
                            switchToStandby()
                        } else {
                            switchToPlaceholder()
                        }
                    }
                    DirectWebRTCManager.ConnectionState.DISCONNECTED -> {
                        statusIndicator?.visibility = View.VISIBLE
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_disconnected)
                        centerStatusIcon()
                        videoFlowCheckJob?.cancel()
                        if (idleVideoUrls.isNotEmpty()) {
                            switchToStandby()
                        } else {
                            switchToPlaceholder()
                        }
                    }
                }
            }
        }

        // Separate job: start video flow check when connection becomes CONNECTED
        observeConnectionJob = CoroutineScope(Dispatchers.Main).launch {
            DirectWebRTCManager.connectionState.collect { state ->
                if (state == DirectWebRTCManager.ConnectionState.CONNECTED) {
                    startVideoFlowCheck()
                }
            }
        }
    }

    /**
     * Start checking for actual video flow after WebRTC connection.
     * Polls every 3 seconds for up to 30 seconds.
     */
    private fun startVideoFlowCheck() {
        videoFlowCheckJob?.cancel()
        videoFlowCheckJob = CoroutineScope(Dispatchers.Main).launch {
            var checks = 0
            val maxChecks = 10  // 10 * 3s = 30 seconds
            while (checks < maxChecks) {
                delay(3000)
                checks++
                if (DirectWebRTCManager.connectionState.value != DirectWebRTCManager.ConnectionState.CONNECTED) {
                    Log.d(TAG, "Connection lost during video check, stopping")
                    break
                }
                if (DirectWebRTCManager.hasRemoteVideoTrack()) {
                    Log.d(TAG, "Remote video track found after ${checks * 3}s")
                    lastWebRTCFrameTime = System.currentTimeMillis()
                    // Don't auto-switch to WebRTC here - let avatar_status:SPEAKING trigger it
                    // Just note that we have a track available
                    break
                }
                Log.d(TAG, "No remote video track yet, check $checks/$maxChecks")
            }
            if (!hasVideoFlow && checks >= maxChecks) {
                Log.w(TAG, "No video track after ${maxChecks * 3}s total, keeping current mode")
            }
        }
    }

    /**
     * Move status icon to top-right corner (error state - don't block video).
     */
    private fun moveStatusToCorner() {
        statusIndicator?.let { iv ->
            val params = iv.layoutParams as? FrameLayout.LayoutParams
            if (params != null) {
                params.gravity = Gravity.TOP or Gravity.END
                params.width = 18
                params.height = 18
                iv.layoutParams = params
                iv.alpha = 0.7f
            }
        }
    }

    /**
     * Center the status icon (connecting/disconnected state).
     */
    private fun centerStatusIcon() {
        statusIndicator?.let { iv ->
            val params = iv.layoutParams as? FrameLayout.LayoutParams
            if (params != null) {
                params.gravity = Gravity.CENTER
                params.width = 24
                params.height = 24
                iv.layoutParams = params
                iv.alpha = 1.0f
            }
        }
    }
}
