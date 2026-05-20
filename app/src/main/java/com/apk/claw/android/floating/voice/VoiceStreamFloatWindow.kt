package com.apk.claw.android.floating.voice

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.apk.claw.android.R
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import android.content.res.Resources

/**
 * 语音流式悬浮窗 - 用于视频监控结果展示和语音交互入口
 * 
 * 功能：
 * 1. 显示监控检测结果 (showMonitorResult)
 * 2. 作为 CameraStreamActivity 中的语音助手入口
 * 3. 可拖动悬浮在任意界面
 */
object VoiceStreamFloatWindow {

    private const val TAG = "VoiceStreamFloatWindow"
    private const val FLOAT_TAG = "voice_stream_float"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appRef: Application? = null

    // UI views
    private var statusTextView: TextView? = null
    private var messageTextView: TextView? = null
    private var closeButton: ImageButton? = null

    // State
    @Volatile
    private var isActive = false
    private var messageClearRunnable: Runnable? = null

    val isShowing: Boolean
        get() = try {
            EasyFloat.isShow(FLOAT_TAG)
        } catch (e: Exception) {
            false
        }

    /**
     * 显示悬浮窗
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(application: Application) {
        if (EasyFloat.isShow(FLOAT_TAG)) {
            return
        }
        appRef = application
        isActive = true

        try {
            val topOffset = getStatusBarHeight(application) + 16.dpToPx()
            EasyFloat.with(application)
                .setTag(FLOAT_TAG)
                .setLayout(R.layout.layout_voice_interaction_float) { view ->
                    statusTextView = view.findViewById(R.id.tv_voice_status)
                    messageTextView = view.findViewById(R.id.tv_voice_message)
                    closeButton = view.findViewById(R.id.btn_voice_close)

                    updateStatus("语音助手")

                    // 关闭按钮
                    closeButton?.setOnClickListener {
                        dismiss()
                    }
                }
                .setGravity(Gravity.TOP or Gravity.END, 8.dpToPx(), topOffset)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setDragEnable(true)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show voice stream float window", e)
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun dismiss() {
        isActive = false
        messageClearRunnable?.let { mainHandler.removeCallbacks(it) }
        statusTextView = null
        messageTextView = null
        closeButton = null

        try {
            if (EasyFloat.isShow(FLOAT_TAG)) {
                EasyFloat.dismiss(FLOAT_TAG)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing", e)
        }
    }

    /**
     * 显示监控检测结果 - 供 StreamMonitorController 调用
     */
    fun showMonitorResult(message: String) {
        mainHandler.post {
            if (!EasyFloat.isShow(FLOAT_TAG)) {
                // 如果悬浮窗未显示，使用 Toast 作为后备
                appRef?.let {
                    android.widget.Toast.makeText(it, message, android.widget.Toast.LENGTH_LONG).show()
                }
                return@post
            }
            showMessage(message)
            updateStatus("监控检测")
        }
    }

    private fun updateStatus(text: String) {
        statusTextView?.text = text
    }

    private fun showMessage(text: String) {
        messageTextView?.text = text
        messageTextView?.visibility = View.VISIBLE
        // 30秒后自动清除
        messageClearRunnable?.let { mainHandler.removeCallbacks(it) }
        messageClearRunnable = Runnable {
            messageTextView?.text = ""
            messageTextView?.visibility = View.INVISIBLE
        }
        mainHandler.postDelayed(messageClearRunnable!!, 30000L)
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }
}
