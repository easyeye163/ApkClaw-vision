package com.apk.claw.android.floating.reasoning

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog

/**
 * AI 实时推理悬浮面板管理器
 *
 * 功能：在手机屏幕上以半透明悬浮窗形式实时展示 Agent 的推理过程和操作步骤。
 * 设计灵感来自 LobsterHUD Pro 的实时可视化要求：
 *   "App操控手机时，悬浮面板实时显示AI的推理过程和操作步骤"
 *
 * 使用方式：
 *   1. 通过 FloatingReasoningPanel.getInstance() 获取单例
 *   2. 调用 show() 展示面板
 *   3. 通过 onLoopStart/onContent/onToolCall/onToolResult/onComplete/onError 推送步骤
 *   4. 调用 hide() 隐藏面板
 */
class FloatingReasoningPanel private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FloatingReasoningPanel"

        /** EasyFloat tag（如果用 EasyFloat 的话） */
        const val FLOAT_TAG = "reasoning_panel"

        /** 面板宽度 */
        private const val PANEL_WIDTH_DP = 260

        /** 面板高度 */
        private const val PANEL_HEIGHT_DP = 360

        /** 触摸区域扩大（用于防止误触关闭） */
        private const val TOUCH_SLOP = 10

        @Volatile
        private var instance: FloatingReasoningPanel? = null

        fun getInstance(context: Context): FloatingReasoningPanel {
            return instance ?: synchronized(this) {
                instance ?: FloatingReasoningPanel(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var panelView: View? = null
    @Volatile
    private var isShowing = false

    // Views
    private var rvSteps: RecyclerView? = null
    private var tvStepCount: TextView? = null
    private var tvTaskName: TextView? = null
    private var tvStatusText: TextView? = null
    private var pulseDot: View? = null
    private var layoutStatusBar: LinearLayout? = null
    private var btnCancel: TextView? = null
    private var btnClear: TextView? = null
    private var btnMinimize: ImageView? = null

    private val adapter = ReasoningAdapter()
    private var cancelCallback: (() -> Unit)? = null

    // 拖拽相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // 脉冲动画
    private var pulseAnimator: ValueAnimator? = null

    /**
     * 显示推理面板
     */
    fun show() {
        if (isShowing) return
        mainHandler.post {
            try {
                if (isShowing) return@post
                createPanelView()
                addPanelToWindow()
                isShowing = true
            } catch (e: Exception) {
                XLog.e(TAG, "Error showing panel", e)
            }
        }
    }

    /**
     * 隐藏推理面板
     */
    fun hide() {
        if (!isShowing) return
        mainHandler.post {
            try {
                removePanelFromWindow()
                isShowing = false
                stopPulseAnimation()
            } catch (e: Exception) {
                XLog.e(TAG, "Error hiding panel", e)
            }
        }
    }

    /**
     * 面板是否正在显示
     */
    fun isVisible(): Boolean = isShowing

    /**
     * 设置任务名称
     */
    fun setTaskName(name: String) {
        mainHandler.post {
            tvTaskName?.text = name
            tvTaskName?.visibility = View.VISIBLE
        }
    }

    /**
     * 设置取消回调
     */
    fun setCancelCallback(callback: () -> Unit) {
        cancelCallback = callback
    }

    // ==================== Agent 事件推送 ====================

    fun onLoopStart(round: Int) {
        mainHandler.post {
            try {
                addStep(ReasoningStep.thinking(round, "Starting round $round..."))
                updateStatus(ReasoningStep.StepType.THINKING)
            } catch (e: Exception) {
                XLog.e(TAG, "Error in onLoopStart", e)
            }
        }
    }

    fun onContent(round: Int, content: String) {
        mainHandler.post {
            try {
                addStep(ReasoningStep.content(round, content))
            } catch (e: Exception) {
                XLog.e(TAG, "Error in onContent", e)
            }
        }
    }

    fun onToolCall(round: Int, toolId: String, toolName: String, parameters: String) {
        mainHandler.post {
            try {
                addStep(ReasoningStep.toolCall(round, toolId, toolName, parameters))
                updateStatus(ReasoningStep.StepType.TOOL_CALL)
            } catch (e: Exception) {
                XLog.e(TAG, "Error in onToolCall", e)
            }
        }
    }

    fun onToolResult(
        round: Int,
        toolId: String,
        toolName: String,
        parameters: String,
        isSuccess: Boolean
    ) {
        mainHandler.post {
            try {
                addStep(ReasoningStep.toolResult(round, toolId, toolName, parameters, isSuccess))
                updateStatus(ReasoningStep.StepType.TOOL_RESULT)
            } catch (e: Exception) {
                XLog.e(TAG, "Error in onToolResult", e)
            }
        }
    }

    fun onComplete(round: Int, finalAnswer: String) {
        mainHandler.post {
            try {
                addStep(ReasoningStep.completion(round, finalAnswer))
                updateStatus(ReasoningStep.StepType.COMPLETION)
                stopPulseAnimation()
            } catch (e: Exception) {
                XLog.e(TAG, "Error in onComplete", e)
            }
        }
    }

    fun onError(round: Int, error: String) {
        mainHandler.post {
            try {
                addStep(ReasoningStep.error(round, error))
                updateStatus(ReasoningStep.StepType.ERROR)
                stopPulseAnimation()
            } catch (e: Exception) {
                XLog.e(TAG, "Error in onError", e)
            }
        }
    }

    fun clearSteps() {
        mainHandler.post {
            try {
                adapter.clear()
                tvStepCount?.text = context.getString(R.string.reasoning_step_count, 0)
            } catch (e: Exception) {
                XLog.e(TAG, "Error in clearSteps", e)
            }
        }
    }

    // ==================== 内部方法 ====================

    private fun createPanelView() {
        val inflater = LayoutInflater.from(context)
        panelView = inflater.inflate(R.layout.layout_floating_reasoning_panel, null)

        rvSteps = panelView!!.findViewById(R.id.rvReasoningSteps)
        tvStepCount = panelView!!.findViewById(R.id.tvStepCount)
        tvTaskName = panelView!!.findViewById(R.id.tvTaskName)
        tvStatusText = panelView!!.findViewById(R.id.tvStatusText)
        pulseDot = panelView!!.findViewById(R.id.pulseDot)
        layoutStatusBar = panelView!!.findViewById(R.id.layoutStatusBar)
        btnCancel = panelView!!.findViewById(R.id.btnCancel)
        btnClear = panelView!!.findViewById(R.id.btnClear)
        btnMinimize = panelView!!.findViewById(R.id.btnMinimize)

        // 设置 RecyclerView
        rvSteps?.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        rvSteps?.adapter = adapter
        rvSteps?.setItemAnimator(null) // 禁用动画以提升性能

        // 状态栏显示
        layoutStatusBar?.visibility = View.VISIBLE

        // 最小化按钮
        btnMinimize?.setOnClickListener {
            hide()
        }

        // 取消按钮
        btnCancel?.setOnClickListener {
            cancelCallback?.invoke()
        }

        // 清空按钮
        btnClear?.setOnClickListener {
            adapter.clear()
            tvStepCount?.text = context.getString(R.string.reasoning_step_count, 0)
        }

        // 拖拽功能
        setupDrag(panelView!!)

        // 加载保存的位置
        restorePosition()
    }

    private fun addStep(step: ReasoningStep) {
        adapter.addStep(step)
        tvStepCount?.text = context.getString(R.string.reasoning_step_count, adapter.getStepCount())

        // 自动滚动到底部
        rvSteps?.post {
            val lastPos = adapter.getLastPosition()
            if (lastPos >= 0) {
                rvSteps?.smoothScrollToPosition(lastPos)
            }
        }
    }

    private fun updateStatus(type: ReasoningStep.StepType) {
        when (type) {
            ReasoningStep.StepType.THINKING -> {
                tvStatusText?.text = context.getString(R.string.reasoning_status_thinking)
                pulseDot?.setBackgroundColor(0xFF5856D6.toInt())
                startPulseAnimation()
            }
            ReasoningStep.StepType.TOOL_CALL -> {
                tvStatusText?.text = context.getString(R.string.reasoning_status_tool_exec)
                pulseDot?.setBackgroundColor(0xFF1AAFFF.toInt())
                startPulseAnimation()
            }
            ReasoningStep.StepType.TOOL_RESULT -> {
                // 短暂显示后恢复 thinking 状态
                pulseDot?.setBackgroundColor(0xFF2BA471.toInt())
                mainHandler.postDelayed({
                    if (isShowing) {
                        tvStatusText?.text = context.getString(R.string.reasoning_status_running)
                        pulseDot?.setBackgroundColor(0xFF5856D6.toInt())
                    }
                }, 800)
            }
            ReasoningStep.StepType.COMPLETION -> {
                tvStatusText?.text = context.getString(R.string.reasoning_status_completed)
                pulseDot?.setBackgroundColor(0xFF2BA471.toInt())
            }
            ReasoningStep.StepType.ERROR -> {
                tvStatusText?.text = context.getString(R.string.reasoning_status_error)
                pulseDot?.setBackgroundColor(0xFFF6685D.toInt())
            }
            else -> {
                tvStatusText?.text = context.getString(R.string.reasoning_status_running)
                pulseDot?.setBackgroundColor(0xFF5856D6.toInt())
            }
        }
    }

    private fun addPanelToWindow() {
        val displayMetrics = context.resources.displayMetrics
        val widthPx = (PANEL_WIDTH_DP * displayMetrics.density).toInt()
        val heightPx = (PANEL_HEIGHT_DP * displayMetrics.density).toInt()

        // 读取保存的位置，默认在右上角
        val savedX = KVUtils.getInt("reasoning_panel_x", displayMetrics.widthPixels - widthPx - 16)
        val savedY = KVUtils.getInt("reasoning_panel_y", 120)

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = savedX
        params.y = savedY

        try {
            windowManager.addView(panelView, params)
            // 入场动画
            panelView?.alpha = 0f
            val fadeIn = AlphaAnimation(0f, 1f)
            fadeIn.duration = 250
            fadeIn.fillAfter = true
            panelView?.startAnimation(fadeIn)
        } catch (e: Exception) {
            // 悬浮窗权限可能未授予
        }
    }

    private fun removePanelFromWindow() {
        panelView?.let { view ->
            val fadeOut = AlphaAnimation(1f, 0f)
            fadeOut.duration = 200
            fadeOut.fillAfter = true
            view.startAnimation(fadeOut)
            mainHandler.postDelayed({
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    // view may already be removed
                }
                panelView = null
            }, 200)
        }
    }

    private fun setupDrag(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY

                        if (!isDragging && (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP)) {
                            isDragging = true
                        }

                        if (isDragging) {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            // 限制在屏幕范围内
                            val displayMetrics = context.resources.displayMetrics
                            val maxWidth = displayMetrics.widthPixels - view.width
                            val maxHeight = displayMetrics.heightPixels - view.height
                            params.x = params.x.coerceIn(0, maxWidth)
                            params.y = params.y.coerceIn(0, maxHeight)
                            windowManager.updateViewLayout(view, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            // 保存位置
                            KVUtils.putInt("reasoning_panel_x", params.x)
                            KVUtils.putInt("reasoning_panel_y", params.y)
                        }
                        return !isDragging
                    }
                }
                return false
            }
        })
    }

    private fun restorePosition() {
        // 位置在 addPanelToWindow 中通过 KVUtils 读取
    }

    private fun startPulseAnimation() {
        if (pulseAnimator?.isRunning == true) return
        val dot = pulseDot ?: return
        pulseAnimator = ValueAnimator.ofFloat(0.3f, 1f, 0.3f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                dot.alpha = anim.animatedValue as Float
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseDot?.alpha = 1f
    }
}
