package com.apk.claw.android.webrtc

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
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
import kotlinx.coroutines.launch

/**
 * Floating avatar window manager (singleton).
 * Shows a small draggable overlay (120dp circle) with the WebRTC video renderer for digital avatar.
 * Auto-shows when WebRTC is enabled and has config, auto-hides when disabled.
 */
object FloatingAvatarManager {

    private const val TAG = "FloatingAvatarManager"
    private const val PREF_X = "floating_avatar_x"
    private const val PREF_Y = "floating_avatar_y"
    private val AVATAR_SIZE_DP = 120

    private var windowManager: WindowManager? = null
    private var avatarView: View? = null
    private var renderer: SurfaceViewRenderer? = null
    private var statusIndicator: ImageView? = null
    private var speakingIndicator: View? = null

    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var observeConnectionJob: Job? = null
    private var observeAvatarStatusJob: Job? = null

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

        // Inflate the avatar overlay layout
        val inflater = LayoutInflater.from(ctx)
        avatarView = inflater.inflate(R.layout.layout_floating_avatar, null).also { view ->
            val container = view.findViewById<FrameLayout>(R.id.avatarVideoContainer)
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

        // Bind the renderer to DirectWebRTCManager
        renderer?.let { DirectWebRTCManager.bindRenderer(it) }

        // Observe connection state and avatar status
        observeState()

        // Trigger WebRTC connection if not already connected
        if (DirectWebRTCManager.connectionState.value == DirectWebRTCManager.ConnectionState.DISCONNECTED) {
            Log.d(TAG, "Triggering WebRTC connection...")
            DirectWebRTCManager.connect()
        }

        Log.d(TAG, "Floating avatar shown")
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
                    }
                    DirectWebRTCManager.ConnectionState.CONNECTED -> {
                        statusIndicator?.visibility = View.GONE
                    }
                    DirectWebRTCManager.ConnectionState.ERROR -> {
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_error)
                        statusIndicator?.visibility = View.VISIBLE
                    }
                    DirectWebRTCManager.ConnectionState.DISCONNECTED -> {
                        statusIndicator?.visibility = View.VISIBLE
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_disconnected)
                    }
                }
            }
        }

        observeAvatarStatusJob = CoroutineScope(Dispatchers.Main).launch {
            DirectWebRTCManager.avatarStatus.collect { status ->
                when (status) {
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
            }
        }
    }
}
