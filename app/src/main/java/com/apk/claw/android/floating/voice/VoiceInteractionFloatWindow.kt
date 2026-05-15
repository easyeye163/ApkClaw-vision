package com.apk.claw.android.floating.voice

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.service.ClawAccessibilityService
import com.apk.claw.android.ui.chat.ChatActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.voice.VoiceInputController
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 语音交互悬浮窗
 * 参考 X-OmniClaw 的 ScreenCompanionFloatWindow 实现。
 *
 * 功能：
 * 1. 可拖动悬浮在任意界面（包括摄像头流）
 * 2. 按住语音按钮说话，松开后自动识别
 * 3. 自动截屏并通过 AccessibilityService 发送到 ChatActivity 分析
 * 4. 在悬浮窗中展示对话结果
 * 5. 关闭按钮可收起悬浮窗
 */
object VoiceInteractionFloatWindow {

    private const val TAG = "VoiceFloatWindow"
    private const val FLOAT_TAG = "voice_interaction_float"
    private const val KEY_FLOAT_X = "voice_float_x"
    private const val KEY_FLOAT_Y = "voice_float_y"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appRef: Application? = null

    // UI views
    private var statusTextView: TextView? = null
    private var messageTextView: TextView? = null
    private var voiceButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var resultsRecyclerView: RecyclerView? = null
    private var resultAdapter: ResultAdapter? = null

    // Voice controller
    private var voiceController: VoiceInputController? = null

    // Voice listener (separated to avoid lambda capture issues)
    private val voiceListener = object : VoiceInputController.Listener {
        override fun onListeningStarted() {
            isListening = true
            isProcessing = false
            updateStatus("松开结束")
        }

        override fun onPartialResults(text: String) {
            showMessage(text)
        }

        override fun onFinalResult(text: String) {
            isListening = false
            isProcessing = true
            updateStatus("正在分析...")
            showResultMessage("你: $text")
            captureAndAnalyze(text)
        }

        override fun onError(errorCode: Int, message: String) {
            isListening = false
            isProcessing = false
            updateStatus("按住说话")
            showMessage(message)
        }
    }

    // State
    @Volatile
    private var isActive = false
    private var isListening = false
    private var isProcessing = false
    private val resultsList = mutableListOf<String>()
    private var messageClearRunnable: Runnable? = null

    /**
     * 对话结果列表适配器
     */
    private class ResultAdapter : RecyclerView.Adapter<ResultAdapter.ViewHolder>() {
        private val items = mutableListOf<String>()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.tv_voice_result_item)
        }

        fun setItems(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun addItem(item: String) {
            items.add(item)
            if (items.size > 20) items.removeAt(0)
            notifyItemInserted(items.size - 1)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_voice_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = items[position]
        }

        override fun getItemCount() = items.size
    }

    /**
     * 显示悬浮窗
     */
    fun show(application: Application) {
        if (EasyFloat.isShow(FLOAT_TAG)) {
            return
        }
        appRef = application
        isActive = true

        // 创建 VoiceInputController
        voiceController?.destroy()
        val controller = VoiceInputController(application.applicationContext)
        controller.listener = voiceListener
        voiceController = controller

        try {
            val topOffset = getStatusBarHeight(application) + 16.dpToPx()
            EasyFloat.with(application)
                .setTag(FLOAT_TAG)
                .setLayout(R.layout.layout_voice_interaction_float) { view ->
                    bindViews(view)
                    bindInteractions(application)
                    updateStatus("按住说话")
                }
                .setGravity(Gravity.TOP or Gravity.END, 8.dpToPx(), topOffset)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setDragEnable(true)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show voice float window", e)
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun dismiss() {
        isActive = false
        isListening = false
        isProcessing = false
        voiceController?.destroy()
        voiceController = null
        messageClearRunnable?.let { mainHandler.removeCallbacks(it) }
        statusTextView = null
        messageTextView = null
        voiceButton = null
        closeButton = null
        resultsRecyclerView = null
        resultAdapter = null
        resultsList.clear()

        try {
            if (EasyFloat.isShow(FLOAT_TAG)) {
                EasyFloat.dismiss(FLOAT_TAG)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing", e)
        }
    }

    fun isShowing(): Boolean = EasyFloat.isShow(FLOAT_TAG)

    @SuppressLint("ClickableViewAccessibility")
    private fun bindViews(root: View) {
        statusTextView = root.findViewById(R.id.tv_voice_status)
        messageTextView = root.findViewById(R.id.tv_voice_message)
        voiceButton = root.findViewById(R.id.btn_voice_mic)
        closeButton = root.findViewById(R.id.btn_voice_close)
        resultsRecyclerView = root.findViewById(R.id.rv_voice_results)

        resultAdapter = ResultAdapter()
        resultsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(root.context)
            adapter = resultAdapter
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindInteractions(application: Application) {
        // 按住说话按钮
        voiceButton?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isProcessing) {
                        showMessage("正在处理中，请稍候")
                        return@setOnTouchListener true
                    }
                    voiceController?.startListening()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isListening) {
                        voiceController?.stopListening()
                    }
                    true
                }
                else -> false
            }
        }

        // 关闭按钮
        closeButton?.setOnClickListener {
            dismiss()
        }
    }

    /**
     * 截屏并发送到 ChatActivity 分析
     */
    private fun captureAndAnalyze(voiceText: String) {
        val app = appRef ?: return

        // 先隐藏悬浮窗，截屏后再恢复
        val floatView = EasyFloat.getFloatView(FLOAT_TAG)
        floatView?.visibility = View.GONE

        Thread {
            try {
                Thread.sleep(500)

                val a11yService = ClawAccessibilityService.getInstance()
                if (a11yService == null) {
                    mainHandler.post {
                        floatView?.visibility = View.VISIBLE
                        isProcessing = false
                        updateStatus("按住说话")
                        showMessage("无障碍服务未运行，无法截屏分析")
                        // 没有截屏时，直接把语音文本发送到 ChatActivity
                        sendToChatWithoutImage(app, voiceText)
                    }
                    return@Thread
                }

                val bitmap = a11yService.takeScreenshot(5000)
                if (bitmap == null) {
                    mainHandler.post {
                        floatView?.visibility = View.VISIBLE
                        isProcessing = false
                        updateStatus("按住说话")
                        showMessage("截屏失败，请重试")
                        // 直接发送文本
                        sendToChatWithoutImage(app, voiceText)
                    }
                    return@Thread
                }

                val softBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                bitmap.recycle()

                // 保存截图
                val dir = File(app.cacheDir, "screenshots")
                if (!dir.exists()) dir.mkdirs()
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "screenshot_$timeStamp.png")

                FileOutputStream(file).use { out ->
                    softBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                softBitmap.recycle()

                // 发送到 ChatActivity 分析
                mainHandler.post {
                    floatView?.visibility = View.VISIBLE
                    isProcessing = false
                    updateStatus("按住说话")

                    try {
                        val intent = Intent(app, ChatActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(
                                com.apk.claw.android.ui.chat.ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH,
                                file.absolutePath
                            )
                            putExtra("voice_text", voiceText)
                        }
                        app.startActivity(intent)
                        showMessage("已发送分析请求")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening ChatActivity", e)
                        showMessage("发送失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureAndAnalyze failed", e)
                mainHandler.post {
                    floatView?.visibility = View.VISIBLE
                    isProcessing = false
                    updateStatus("按住说话")
                    showMessage("处理失败: ${e.message}")
                }
            }
        }.start()
    }

    /**
     * 无截屏时直接发送文本到 ChatActivity
     */
    private fun sendToChatWithoutImage(app: Application, text: String) {
        try {
            val intent = Intent(app, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("voice_text", text)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text to ChatActivity", e)
        }
    }

    private fun updateStatus(text: String) {
        statusTextView?.text = text
        // 更新语音按钮颜色
        voiceButton?.let { btn ->
            val bgColor = when {
                isListening -> Color.parseColor("#E53935")
                isProcessing -> Color.parseColor("#555555")
                else -> Color.parseColor("#1E88E5")
            }
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            btn.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            btn.isEnabled = !isProcessing || isListening
            btn.alpha = if (btn.isEnabled) 1.0f else 0.6f
        }
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

    private fun showResultMessage(text: String) {
        resultsList.add(text)
        if (resultsList.size > 20) resultsList.removeAt(0)
        resultAdapter?.setItems(resultsList)
        resultsRecyclerView?.visibility = View.VISIBLE
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }
}
