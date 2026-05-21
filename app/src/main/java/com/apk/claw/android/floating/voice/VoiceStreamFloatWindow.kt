package com.apk.claw.android.floating.voice

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
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

    // 监控模式下的消息记录（保留最近N条）
    private val monitorMessages = mutableListOf<String>()
    private val MAX_MONITOR_MESSAGES = 20

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
        monitorMessages.clear()

        try {
            val topOffset = getStatusBarHeight(application) + 16.dpToPx()
            EasyFloat.with(application)
                .setTag(FLOAT_TAG)
                .setLayout(R.layout.layout_voice_interaction_float) { view ->
                    statusTextView = view.findViewById(R.id.tv_voice_status)
                    messageTextView = view.findViewById(R.id.tv_voice_message)
                    closeButton = view.findViewById(R.id.btn_voice_close)

                    // 监控模式下允许更多行显示
                    messageTextView?.maxLines = 12

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
        messageClearRunnable = null
        statusTextView = null
        messageTextView = null
        closeButton = null
        monitorMessages.clear()

        try {
            if (EasyFloat.isShow(FLOAT_TAG)) {
                EasyFloat.dismiss(FLOAT_TAG)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing", e)
        }
    }

    /**
     * 显示监控检测结果 - 供 StreamMonitorController 和 CameraStreamActivity 调用
     * 监控模式下保留最近的消息记录，滚动显示
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

            // 添加到消息记录
            monitorMessages.add(message)
            if (monitorMessages.size > MAX_MONITOR_MESSAGES) {
                monitorMessages.removeAt(0)
            }

            // 拼接最近几条消息显示
            val displayCount = minOf(monitorMessages.size, 6)
            val recentMessages = monitorMessages.takeLast(displayCount)
            val displayText = recentMessages.joinToString("\n")

            showMessage(displayText)
            updateStatus("监控检测 (${monitorMessages.size})")
        }
    }

    /**
     * 重置监控消息记录（停止监控时调用）
     */
    fun clearMonitorMessages() {
        mainHandler.post {
            monitorMessages.clear()
            messageTextView?.text = ""
            messageTextView?.visibility = View.INVISIBLE
            messageClearRunnable?.let { mainHandler.removeCallbacks(it) }
            messageClearRunnable = null
        }
    }

    private fun updateStatus(text: String) {
        statusTextView?.text = text
    }

    private fun showMessage(text: String) {
        messageTextView?.text = text
        messageTextView?.visibility = View.VISIBLE
        // 监控消息不自动清除（由外部调用 clearMonitorMessages 或 dismiss 处理）
        messageClearRunnable?.let { mainHandler.removeCallbacks(it) }
        messageClearRunnable = Runnable {
            messageTextView?.text = ""
            messageTextView?.visibility = View.INVISIBLE
        }
        // 60秒后自动清除（比原来30秒长，适合监控场景）
        mainHandler.postDelayed(messageClearRunnable!!, 60000L)
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }
}
