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
import kotlinx.coroutines.launch

/**
 * Floating avatar window manager (singleton).
 * Shows a small draggable overlay (120dp circle) with the WebRTC video renderer for digital avatar.
 * Falls back to a local test MP4 when WebRTC video is not available.
 */
object FloatingAvatarManager {

    private const val TAG = "FloatingAvatarManager"
    private const val PREF_X = "floating_avatar_x"
    private const val PREF_Y = "floating_avatar_y"
    private val AVATAR_SIZE_DP = 120

    private var windowManager: WindowManager? = null
    private var avatarView: View? = null
    private var renderer: SurfaceViewRenderer? = null
    private var fallbackTextureView: TextureView? = null
    private var fallbackMediaPlayer: MediaPlayer? = null
    private var statusIndicator: ImageView? = null
    private var speakingIndicator: View? = null

    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var observeConnectionJob: Job? = null
    private var observeAvatarStatusJob: Job? = null
    private var videoFlowCheckJob: Job? = null

    private var hasVideoFlow = false  // Track if WebRTC video is actually rendering frames
    private var appContext: Context? = null

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

        val sizePx = (AVATAR_SIZE_DP * ctx.resources.displayMetrics.density).toInt()

        // Create the video renderer (org.webrtc.SurfaceViewRenderer)
        renderer = SurfaceViewRenderer(ctx).apply {
            setZOrderMediaOverlay(true)
        }

        // Create fallback TextureView for MP4 playback
        fallbackTextureView = TextureView(ctx)

        // Inflate the avatar overlay layout
        val inflater = LayoutInflater.from(ctx)
        avatarView = inflater.inflate(R.layout.layout_floating_avatar, null).also { view ->
            val container = view.findViewById<FrameLayout>(R.id.avatarVideoContainer)
            // Add fallback first (behind), then renderer on top
            fallbackTextureView?.let { container?.addView(it, 0) }
            container?.addView(renderer)
            statusIndicator = view.findViewById(R.id.ivAvatarStatus)
            speakingIndicator = view.findViewById(R.id.speakingIndicator)
        }

        val params = createWindowParams(sizePx)

        // Restore saved position
        val savedX = KVUtils.getInt(PREF_X, -1)
        val savedY = KVUtils.getInt(PREF_Y, -1)
        if (savedX >= 0 && savedY >= 0) {
            params.x = savedX
            params.y = savedY
        }

        // Touch handling for dragging
        avatarView?.setOnTouchListener { _, event ->
            when (event.action) {
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
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(avatarView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating avatar position", e)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Save position
                        KVUtils.putInt(PREF_X, params.x)
                        KVUtils.putInt(PREF_Y, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(avatarView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding avatar view to WindowManager", e)
            avatarView = null
            return
        }

        // Set up fallback video playback
        setupFallbackVideo(ctx)

        // Initially: show fallback, hide WebRTC renderer
        showFallbackVideo()

        // Bind the renderer to DirectWebRTCManager
        renderer?.let { DirectWebRTCManager.bindRenderer(it) }

        // Observe connection state and avatar status
        observeState()

        // Trigger WebRTC connection if not already connected or in error state
        val connState = DirectWebRTCManager.connectionState.value
        if (connState == DirectWebRTCManager.ConnectionState.DISCONNECTED ||
            connState == DirectWebRTCManager.ConnectionState.ERROR) {
            Log.d(TAG, "Triggering WebRTC connection (state=$connState)...")
            DirectWebRTCManager.connect()
        }

        Log.d(TAG, "Floating avatar shown")
    }

    /**
     * Set up the fallback MP4 video player.
     */
    private fun setupFallbackVideo(ctx: Context) {
        fallbackTextureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                try {
                    val surface = Surface(surfaceTexture)
                    if (fallbackMediaPlayer == null) {
                        fallbackMediaPlayer = MediaPlayer().apply {
                            val resId = ctx.resources.getIdentifier(
                                "test_avatar", "raw", ctx.packageName
                            )
                            if (resId != 0) {
                                setDataSource(ctx, 
                                    android.net.Uri.parse("android.resource://${ctx.packageName}/$resId"))
                            }
                            setSurface(surface)
                            isLooping = true
                            setVolume(0f, 0f)  // Mute
                            prepareAsync()
                            setOnPreparedListener { mp ->
                                mp.start()
                                Log.d(TAG, "Fallback video started playing")
                            }
                            setOnErrorListener { _, what, extra ->
                                Log.e(TAG, "Fallback video error: what=$what extra=$extra")
                                false
                            }
                        }
                    } else {
                        fallbackMediaPlayer?.setSurface(surface)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up fallback video", e)
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                stopFallbackVideo()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    /**
     * Show the fallback test video and hide the WebRTC renderer.
     */
    private fun showFallbackVideo() {
        hasVideoFlow = false
        if (renderer != null) {
            renderer?.visibility = View.GONE
        }
        if (fallbackTextureView != null) {
            fallbackTextureView?.visibility = View.VISIBLE
        }
    }

    /**
     * Show the WebRTC renderer and hide the fallback video.
     * Called when WebRTC video frames are confirmed flowing.
     */
    fun showWebRTCVideo() {
        if (!hasVideoFlow) {
            hasVideoFlow = true
            Log.d(TAG, "Switching to WebRTC video (frames confirmed)")
            stopFallbackVideo()
            if (renderer != null) {
                renderer?.visibility = View.VISIBLE
            }
            if (fallbackTextureView != null) {
                fallbackTextureView?.visibility = View.GONE
            }
        }
    }

    /**
     * Stop fallback video playback.
     */
    private fun stopFallbackVideo() {
        try {
            fallbackMediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            fallbackMediaPlayer?.release()
        } catch (_: Exception) {}
        fallbackMediaPlayer = null
    }

    /**
     * Hide the floating avatar window.
     */
    @JvmStatic
    fun hide() {
        observeConnectionJob?.cancel()
        observeConnectionJob = null
        observeAvatarStatusJob?.cancel()
        observeAvatarStatusJob = null
        videoFlowCheckJob?.cancel()
        videoFlowCheckJob = null

        stopFallbackVideo()
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
        fallbackTextureView = null
        statusIndicator = null
        speakingIndicator = null
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

    private fun observeState() {
        observeConnectionJob = CoroutineScope(Dispatchers.Main).launch {
            DirectWebRTCManager.connectionState.collect { state ->
                when (state) {
                    DirectWebRTCManager.ConnectionState.CONNECTING -> {
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_connecting)
                        statusIndicator?.visibility = View.VISIBLE
                        // Center the icon during connecting
                        centerStatusIcon()
                    }
                    DirectWebRTCManager.ConnectionState.CONNECTED -> {
                        statusIndicator?.visibility = View.GONE
                        // After connecting, wait a few seconds for video frames to arrive
                        // If no frames, keep showing fallback
                        startVideoFlowCheck()
                    }
                    DirectWebRTCManager.ConnectionState.ERROR -> {
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_error)
                        statusIndicator?.visibility = View.VISIBLE
                        // Move to corner so it doesn't block the fallback video
                        moveStatusToCorner()
                        videoFlowCheckJob?.cancel()
                        showFallbackVideo()
                    }
                    DirectWebRTCManager.ConnectionState.DISCONNECTED -> {
                        statusIndicator?.visibility = View.VISIBLE
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_disconnected)
                        centerStatusIcon()
                        videoFlowCheckJob?.cancel()
                        showFallbackVideo()
                    }
                }
            }
        }

        observeAvatarStatusJob = CoroutineScope(Dispatchers.Main).launch {
            DirectWebRTCManager.avatarStatus.collect { status ->
                when (status) {
                    DirectWebRTCManager.AvatarStatus.SPEAKING -> {
                        speakingIndicator?.visibility = View.VISIBLE
                        // Speaking means video is definitely flowing
                        if (DirectWebRTCManager.connectionState.value == DirectWebRTCManager.ConnectionState.CONNECTED) {
                            showWebRTCVideo()
                        }
                    }
                    DirectWebRTCManager.AvatarStatus.PROCESSING -> {
                        speakingIndicator?.visibility = View.VISIBLE
                    }
                    DirectWebRTCManager.AvatarStatus.IDLE -> {
                        speakingIndicator?.visibility = View.GONE
                    }
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
                    Log.d(TAG, "Remote video track found after ${checks * 3}s, switching to WebRTC renderer")
                    showWebRTCVideo()
                    statusIndicator?.visibility = View.GONE
                    break
                }
                Log.d(TAG, "No remote video track yet, check $checks/$maxChecks")
            }
            if (!hasVideoFlow && checks >= maxChecks) {
                Log.w(TAG, "No video track after ${maxChecks * 3}s total, keeping fallback")
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
