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
import com.apk.claw.android.R
import com.apk.claw.android.utils.KVUtils
import io.livekit.android.renderer.SurfaceViewRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Floating avatar window manager.
 * Shows a small draggable overlay with the LiveKit video renderer for digital avatar.
 */
class FloatingAvatarManager(private val context: Context) {

    companion object {
        private const val TAG = "FloatingAvatarManager"
        private const val PREF_X = "floating_avatar_x"
        private const val PREF_Y = "floating_avatar_y"
        private val AVATAR_SIZE_DP = 120
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var avatarView: View? = null
    private var renderer: SurfaceViewRenderer? = null
    private var statusIndicator: ImageView? = null

    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var observeJob: Job? = null

    /**
     * Show the floating avatar window.
     */
    fun show() {
        if (avatarView != null) return

        val sizePx = (AVATAR_SIZE_DP * context.resources.displayMetrics.density).toInt()

        // Create the video renderer
        renderer = SurfaceViewRenderer(context).apply {
            setZOrderMediaOverlay(true)
        }

        // Create container layout
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        }

        // Inflate the avatar overlay layout
        val inflater = LayoutInflater.from(context)
        avatarView = inflater.inflate(R.layout.layout_floating_avatar, container, false).also { view ->
            view.findViewById<FrameLayout>(R.id.avatarVideoContainer)?.addView(renderer)
            statusIndicator = view.findViewById(R.id.ivAvatarStatus)
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
                    windowManager.updateViewLayout(avatarView, params)
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

        windowManager.addView(avatarView, params)

        // Bind the renderer to LiveKit
        renderer?.let { LiveKitRoomManager.bindRenderer(it) }

        // Observe connection state
        observeState()

        Log.d(TAG, "Floating avatar shown")
    }

    /**
     * Hide the floating avatar window.
     */
    fun hide() {
        observeJob?.cancel()
        observeJob = null

        LiveKitRoomManager.unbindRenderer()

        avatarView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing avatar view", e)
            }
        }
        avatarView = null
        renderer = null
        statusIndicator = null
        Log.d(TAG, "Floating avatar hidden")
    }

    /**
     * Check if avatar is currently showing.
     */
    fun isShowing(): Boolean = avatarView != null

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
            y = 200
        }
    }

    private fun observeState() {
        observeJob?.cancel()
        observeJob = CoroutineScope(Dispatchers.Main).launch {
            LiveKitRoomManager.connectionState.collect { state ->
                when (state) {
                    LiveKitRoomManager.ConnectionState.CONNECTING -> {
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_connecting)
                        statusIndicator?.visibility = View.VISIBLE
                    }
                    LiveKitRoomManager.ConnectionState.CONNECTED -> {
                        statusIndicator?.visibility = View.GONE
                    }
                    LiveKitRoomManager.ConnectionState.ERROR -> {
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_error)
                        statusIndicator?.visibility = View.VISIBLE
                    }
                    LiveKitRoomManager.ConnectionState.DISCONNECTED -> {
                        statusIndicator?.visibility = View.VISIBLE
                        statusIndicator?.setImageResource(R.drawable.ic_avatar_disconnected)
                    }
                }
            }
        }
    }
}
