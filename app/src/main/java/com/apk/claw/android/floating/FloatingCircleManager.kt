package com.apk.claw.android.floating

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.blankj.utilcode.util.ThreadUtils
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.channel.Channel
import com.apk.claw.android.floating.reasoning.FloatingReasoningPanel
import com.apk.claw.android.service.ClawAccessibilityService
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.webrtc.FloatingAvatarManager
import com.blankj.utilcode.util.BarUtils
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import com.lzf.easyfloat.interfaces.OnFloatCallbacks
import com.lzf.easyfloat.utils.DisplayUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues

/**
 * 圆形悬浮窗管理器
 * 使用 EasyFloat 实现可拖动、记录位置的圆形悬浮窗
 * 支持多种状态：等待任务(IDLE)、任务执行中(RUNNING)、任务成功(SUCCESS)、任务失败(ERROR)
 */
object FloatingCircleManager {

    private const val TAG = "FloatingCircle"
    private const val FLOAT_TAG = "circle_float"
    private const val KEY_FLOAT_X = "floating_circle_x"
    private const val KEY_FLOAT_Y = "floating_circle_y"
    private const val AUTO_RESET_DELAY_MS = 5000L // 5秒后自动重置

    /**
     * 悬浮窗状态
     */
    enum class State {
        IDLE,           // 等待任务（默认）
        TASK_NOTIFY,    // 收到任务通知（胶囊展开）
        RUNNING,        // 任务执行中
        SUCCESS,        // 任务完成
        ERROR           // 任务失败
    }

    private var isShowing = false
    @Volatile
    private var currentState: State = State.IDLE
    private var currentRound: Int = 0
    private var currentChannel: Channel? = null

    private const val TASK_NOTIFY_DURATION_MS = 3000L // 任务通知显示 3 秒后收回

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoResetRunnable: Runnable? = null
    private var notifyCollapseRunnable: Runnable? = null
    private var pendingTaskText: String = ""

    private var appRef: Application? = null

    /** 长按检测相关 */
    private var longPressStartX = 0f
    private var longPressStartY = 0f
    private var hasMoved = false

    /**
     * 显示悬浮窗
     * @param application Application 实例
     * @param x 初始位置 X（可选，默认屏幕右边偏中心）
     * @param y 初始位置 Y（可选，默认屏幕中心）
     */
    fun show(
        application: Application,
        x: Int? = null,
        y: Int? = null
    ) {
        if (isShowing) {
            return
        }
        appRef = application

        // 计算默认位置：屏幕中心的右边
        val screenWidth = DisplayUtils.getScreenWidth(application)
        val screenHeight = DisplayUtils.getScreenHeight(application)
        val defaultX = 0
        val defaultY = screenHeight / 2

        // 从本地读取保存的位置
        val savedX = getSavedX() ?: x ?: defaultX
        val savedY = getSavedY() ?: y ?: defaultY

        EasyFloat.with(application)
            .setLayout(R.layout.layout_floating_circle)
            .setShowPattern(ShowPattern.ALL_TIME)
            .setSidePattern(SidePattern.DEFAULT)
            .setGravity(android.view.Gravity.START or android.view.Gravity.TOP, savedX, savedY)
            .setDragEnable(true)
            .hasEditText(false)
            .setTag(FLOAT_TAG)
            .registerCallbacks(object : OnFloatCallbacks {

                override fun createdResult(
                    isCreated: Boolean,
                    msg: String?,
                    view: View?
                ) {
                    // 缓存圆形原始宽度（必须在任何 setFloatRootWidth 之前）
                    view?.findViewById<View>(R.id.floatRoot)?.let { root ->
                        if (circleWidthPx <= 0) {
                            circleWidthPx = root.layoutParams?.width ?: -1
                        }
                    }
                    // 点击事件
                    view?.setOnClickListener {
                        onFloatClick()
                    }
                    // 长按弹出二级菜单，拖拽后不触发
                    view?.setOnLongClickListener { v ->
                        if (!hasMoved) {
                            try {
                                showPopupMenu(v)
                            } catch (e: Exception) {
                                XLog.e(TAG, "Error showing popup menu on long press", e)
                            }
                        }
                        true
                    }
                    // 初始化状态
                    updateStateView(view, currentState)
                    // 布局完成后检测位置，防止圆球卡在屏幕外
                    view?.post {
                        ensureFloatInBounds(view)
                    }
                }

                override fun dismiss() {
                    isShowing = false
                }

                override fun drag(view: View, event: MotionEvent) {
                }

                override fun dragEnd(view: View) {
                    ensureFloatInBounds(view)
                }

                override fun hide(view: View) {
                    isShowing = false
                }

                override fun show(view: View) {
                    isShowing = true
                }

                override fun touchEvent(view: View, event: MotionEvent) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            hasMoved = false
                            longPressStartX = event.rawX
                            longPressStartY = event.rawY
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!hasMoved) {
                                val dx = Math.abs(event.rawX - longPressStartX)
                                val dy = Math.abs(event.rawY - longPressStartY)
                                if (dx > 10f || dy > 10f) {
                                    hasMoved = true
                                }
                            }
                        }
                    }
                }
            })
            .show()
    }

    /** WindowManager for overlay popup menu */
    private var popupWindowManager: WindowManager? = null
    private var popupMenuView: View? = null
    private var popupDismissRunnable: Runnable? = null

    /**
     * 显示悬浮按钮长按弹出菜单（使用 WindowManager overlay，与悬浮按钮同坐标系统）
     */
    private fun showPopupMenu(anchorView: View) {
        dismissPopupMenu()

        val app = appRef ?: ClawApplication.instance
        val inflater = android.view.LayoutInflater.from(app)
        val popupView = inflater.inflate(R.layout.popup_floating_menu, null)

        popupMenuView = popupView
        popupWindowManager = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

        // "本地对话" option
        popupView.findViewById<TextView>(R.id.tvMenuChat)?.setOnClickListener {
            dismissPopupMenu()
            try {
                val intent = android.content.Intent(
                    app,
                    com.apk.claw.android.ui.chat.ChatActivity::class.java
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                app.startActivity(intent)
            } catch (e: Exception) {
                XLog.e(TAG, "Error opening chat", e)
            }
        }

        // "分析屏幕" option
        popupView.findViewById<TextView>(R.id.tvMenuAnalyzeScreen)?.setOnClickListener {
            dismissPopupMenu()
            takeScreenshotAndAnalyze()
        }

        // "数字人" toggle option
        popupView.findViewById<TextView>(R.id.tvMenuToggleAvatar)?.setOnClickListener {
            dismissPopupMenu()
            try {
                FloatingAvatarManager.toggle()
            } catch (e: Exception) {
                XLog.e(TAG, "Error toggling avatar", e)
            }
        }

        // Measure popup to get dimensions
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupW = popupView.measuredWidth
        val popupH = popupView.measuredHeight

        // Get floating button's screen position via its WindowManager.LayoutParams
        val floatView = EasyFloat.getFloatView(FLOAT_TAG)
        var anchorX = 0
        var anchorY = 0
        var anchorW = 56.dpToPx()
        var anchorH = 56.dpToPx()
        if (floatView != null) {
            // Walk up to find WindowManager.LayoutParams (same coordinate space)
            var wmView: View? = floatView
            while (wmView != null) {
                val lp = wmView.layoutParams
                if (lp is WindowManager.LayoutParams) {
                    anchorX = lp.x
                    anchorY = lp.y
                    anchorW = floatView.width
                    anchorH = floatView.height
                    break
                }
                wmView = wmView.parent as? View
            }
        }

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val gap = 8.dpToPx()

        val anchorCenterX = anchorX + anchorW / 2
        val anchorCenterY = anchorY + anchorH / 2

        // Decide left or right based on anchor position
        val safeY = (anchorCenterY - popupH / 2).coerceIn(16.dpToPx(), screenHeight - popupH - 16.dpToPx())
        val showX = if (anchorCenterX < screenWidth / 2) {
            anchorX + anchorW + gap
        } else {
            (anchorX - popupW - gap).coerceAtLeast(8.dpToPx())
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = showX
        params.y = safeY

        // Touch outside popup area → dismiss
        popupView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismissPopupMenu()
                true
            } else false
        }

        try {
            popupWindowManager?.addView(popupView, params)
            // Auto dismiss after 8s if no interaction
            popupDismissRunnable = Runnable { dismissPopupMenu() }
            mainHandler.postDelayed(popupDismissRunnable!!, 8000L)
        } catch (e: Exception) {
            XLog.e(TAG, "Error showing popup overlay", e)
            popupMenuView = null
        }
    }

    /**
     * 关闭弹出菜单 overlay
     */
    private fun dismissPopupMenu() {
        popupDismissRunnable?.let {
            mainHandler.removeCallbacks(it)
            popupDismissRunnable = null
        }
        popupMenuView?.let {
            try {
                popupWindowManager?.removeView(it)
            } catch (e: Exception) {
                XLog.e(TAG, "Error dismissing popup", e)
            }
            popupMenuView = null
        }
    }

    /**
     * 保存截图到公共相册（通过 MediaStore，Android 10+ 兼容）
     */
    private fun saveToGallery(app: Application, bitmap: android.graphics.Bitmap, fileName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }

    /**
     * 截图并分析
     * 使用 AccessibilityService 静默截图（无弹窗、不切换屏幕）。
     * 截图完成后直接打开 ChatActivity 并传入文件路径。
     */
    private fun takeScreenshotAndAnalyze() {
        val app = appRef ?: ClawApplication.instance

        val a11yService = ClawAccessibilityService.getInstance()
        if (a11yService == null) {
            Toast.makeText(app, R.string.accessibility_not_running, Toast.LENGTH_SHORT).show()
            return
        }

        // 先隐藏悬浮按钮，截图完成后再恢复
        val floatView = EasyFloat.getFloatView(FLOAT_TAG)
        floatView?.visibility = View.GONE

        // 延迟 500ms 确保悬浮菜单和按钮完全消失后再截图
        Thread {
            try {
                Thread.sleep(500)
                val bitmap = a11yService.takeScreenshot(5000)
                if (bitmap == null) {
                    mainHandler.post {
                        floatView?.visibility = View.VISIBLE
                        Toast.makeText(app, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                // 转为软件位图以便保存
                val softBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                bitmap.recycle()

                // 保存截图到 cache/screenshots 目录（供 ChatActivity 使用）
                val dir = File(app.cacheDir, "screenshots")
                if (!dir.exists()) dir.mkdirs()
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "screenshot_$timeStamp.png")

                FileOutputStream(file).use { out ->
                    softBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }

                // 同时保存到公共相册 DCIM/Screenshots/
                try {
                    saveToGallery(app, softBitmap, "ApkClaw_$timeStamp")
                } catch (e: Exception) {
                    XLog.e(TAG, "Failed to save screenshot to gallery", e)
                }

                softBitmap.recycle()

                // 截图成功，恢复悬浮按钮并打开 ChatActivity
                mainHandler.post {
                    floatView?.visibility = View.VISIBLE
                    try {
                        val intent = android.content.Intent(
                            app,
                            com.apk.claw.android.ui.chat.ChatActivity::class.java
                        ).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(
                                com.apk.claw.android.ui.chat.ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH,
                                file.absolutePath
                            )
                        }
                        app.startActivity(intent)
                    } catch (e: Exception) {
                        XLog.e(TAG, "Error opening ChatActivity", e)
                        Toast.makeText(app, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                XLog.e(TAG, "Error taking screenshot", e)
                mainHandler.post {
                    floatView?.visibility = View.VISIBLE
                    Toast.makeText(app, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        if (isShowing) {
            EasyFloat.dismiss(FLOAT_TAG)
            isShowing = false
        }
    }

    /**
     * 判断是否显示中
     */
    @JvmStatic
    fun isShowing(): Boolean = isShowing

    /**
     * 切换到等待任务状态（默认）
     */
    fun setIdleState() {
        ThreadUtils.runOnUiThread {
            setState(State.IDLE)
        }
    }

    /**
     * 显示任务通知：悬浮窗展开为胶囊，显示任务内容，3 秒后自动收回进入 RUNNING 状态。
     * @param taskText 任务文本（会截断显示）
     * @param channel 消息来源渠道
     */
    fun showTaskNotify(taskText: String, channel: Channel) {
        ThreadUtils.runOnUiThread {
            try {
                pendingTaskText = taskText
                currentChannel = channel
                cancelNotifyCollapse()
                setState(State.TASK_NOTIFY)
                notifyCollapseRunnable = Runnable {
                    try { setState(State.RUNNING) } catch (_: Exception) {}
                }
                mainHandler.postDelayed(notifyCollapseRunnable!!, TASK_NOTIFY_DURATION_MS)
            } catch (e: Exception) {
                XLog.e(TAG, "Error in showTaskNotify", e)
            }
        }
    }

    private fun cancelNotifyCollapse() {
        notifyCollapseRunnable?.let {
            mainHandler.removeCallbacks(it)
            notifyCollapseRunnable = null
        }
    }

    /**
     * 切换到任务执行中状态
     * @param round 当前轮数
     * @param channel 消息来源渠道
     */
    fun setRunningState(round: Int, channel: Channel) {
        ThreadUtils.runOnUiThread {
            try {
                currentRound = round
                currentChannel = channel
                if (currentState == State.TASK_NOTIFY) {
                    return@runOnUiThread
                }
                setState(State.RUNNING)
            } catch (e: Exception) {
                XLog.e(TAG, "Error in setRunningState", e)
            }
        }
    }

    /**
     * 切换到任务完成状态（5秒后自动回到 IDLE）
     */
    fun setSuccessState() {
        ThreadUtils.runOnUiThread {
            try {
                setState(State.SUCCESS)
                scheduleAutoReset()
            } catch (e: Exception) {
                XLog.e(TAG, "Error in setSuccessState", e)
            }
        }
    }

    fun setErrorState() {
        ThreadUtils.runOnUiThread {
            try {
                setState(State.ERROR)
                scheduleAutoReset()
            } catch (e: Exception) {
                XLog.e(TAG, "Error in setErrorState", e)
            }
        }
    }

    /**
     * 设置状态
     */
    private fun setState(state: State) {
        currentState = state
        try {
            val view = EasyFloat.getFloatView(FLOAT_TAG)
            view?.let { updateStateView(it, state) }
        } catch (e: Exception) {
            XLog.e(TAG, "Error getting float view in setState", e)
        }
    }

    /**
     * 更新视图状态
     */
    private fun updateStateView(view: View?, state: State) {
        if (view == null) return

        val cardIdle = view.findViewById<View>(R.id.cardIdle)
        val cardTaskNotify = view.findViewById<View>(R.id.cardTaskNotify)
        val cardRunning = view.findViewById<View>(R.id.cardRunning)
        val cardSuccess = view.findViewById<View>(R.id.cardSuccess)
        val cardError = view.findViewById<View>(R.id.cardError)

        // 隐藏所有状态
        cardIdle?.visibility = View.GONE
        cardTaskNotify?.visibility = View.GONE
        cardRunning?.visibility = View.GONE
        cardSuccess?.visibility = View.GONE
        cardError?.visibility = View.GONE

        // 取消之前的自动重置
        cancelAutoReset()

        // 显示对应状态
        when (state) {
            State.IDLE -> {
                cardIdle?.visibility = View.VISIBLE
                setFloatRootWidth(view, getCircleWidth(view))
            }
            State.TASK_NOTIFY -> {
                cardTaskNotify?.visibility = View.VISIBLE
                val tvNotify = view.findViewById<TextView>(R.id.tvTaskNotify)
                val app = appRef ?: return
                val displayText = if (pendingTaskText.length > 40) {
                    pendingTaskText.substring(0, 40) + "…"
                } else {
                    pendingTaskText
                }
                tvNotify?.text = app.getString(R.string.floating_task_received, displayText)
                val ivLogo = view.findViewById<ImageView>(R.id.ivNotifyChannelLogo)
                ivLogo?.setImageResource(getChannelIcon(currentChannel))
                // 展开为 wrap_content
                setFloatRootWidth(view, WindowManager.LayoutParams.WRAP_CONTENT)
            }
            State.RUNNING -> {
                cancelNotifyCollapse()
                // 收回为固定圆形
                setFloatRootWidth(view, getCircleWidth(view))
                cardRunning?.visibility = View.VISIBLE
                // 更新轮数显示
                val tvRound = view.findViewById<TextView>(R.id.tvRound)
                tvRound?.text = currentRound.toString()
                // 更新渠道 Logo
                val ivChannelLogo = view.findViewById<ImageView>(R.id.ivChannelLogo)
                ivChannelLogo?.setImageResource(getChannelIcon(currentChannel))
            }
            State.SUCCESS -> {
                cancelNotifyCollapse()
                cardSuccess?.visibility = View.VISIBLE
                setFloatRootWidth(view, getCircleWidth(view))
            }
            State.ERROR -> {
                cancelNotifyCollapse()
                cardError?.visibility = View.VISIBLE
                setFloatRootWidth(view, getCircleWidth(view))
            }
        }
    }

    /**
     * 获取渠道对应的图标
     */
    @DrawableRes
    private fun getChannelIcon(channel: Channel?): Int {
        return when (channel) {
            Channel.DINGTALK -> R.drawable.ic_channel_dingtalk
            Channel.FEISHU -> R.drawable.ic_channel_feishu
            Channel.QQ -> R.drawable.ic_channel_qq
            Channel.DISCORD -> R.drawable.ic_channel_discord
            Channel.TELEGRAM -> R.drawable.ic_channel_telegram
            Channel.WECHAT -> R.drawable.ic_channel_wechat
            else -> R.drawable.ic_launcher
        }
    }

    /**
     * 5秒后自动重置到 IDLE 状态
     */
    private fun scheduleAutoReset() {
        cancelAutoReset()
        autoResetRunnable = Runnable {
            try { setIdleState() } catch (_: Exception) {}
        }
        mainHandler.postDelayed(autoResetRunnable!!, AUTO_RESET_DELAY_MS)
    }

    /**
     * 取消自动重置
     */
    private fun cancelAutoReset() {
        autoResetRunnable?.let {
            mainHandler.removeCallbacks(it)
            autoResetRunnable = null
        }
    }

    /**
     * 确保悬浮窗在屏幕可见范围内，超出则修正
     */
    private fun ensureFloatInBounds(view: View) {
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        // 获取导航栏高度，确保圆球不会被导航栏遮挡
        val navBarHeight = getNavigationBarHeight()

        // 方式1：尝试从 view 层级找到 WindowManager.LayoutParams
        var wmParams: WindowManager.LayoutParams? = null
        var wmView: View? = view
        while (wmView != null) {
            val lp = wmView.layoutParams
            if (lp is WindowManager.LayoutParams) {
                wmParams = lp
                break
            }
            wmView = wmView.parent as? View
        }

        if (wmParams != null) {
            val floatHeight = (wmView ?: view).height
            val floatWidth = (wmView ?: view).width
            val maxX = (screenWidth - floatWidth).coerceAtLeast(0)
            // 减去导航栏高度和额外安全边距
            val maxY = (screenHeight - floatHeight - navBarHeight - 50).coerceAtLeast(0)
            val clampedX = wmParams.x.coerceIn(0, maxX)
            val clampedY = wmParams.y.coerceIn(0, maxY)
            if (clampedX != wmParams.x || clampedY != wmParams.y) {
                EasyFloat.updateFloat(FLOAT_TAG, clampedX, clampedY)
            }
            savePosition(clampedX, clampedY)
            return
        }

        // 兜底：用 getLocationOnScreen 检测，updateFloat 修正
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewBottom = location[1] + view.height
        if (viewBottom > screenHeight - navBarHeight || location[1] < 0) {
            val safeY = screenHeight / 3
            EasyFloat.updateFloat(FLOAT_TAG, location[0].coerceIn(0, screenWidth), safeY)
            savePosition(location[0].coerceIn(0, screenWidth), safeY)
        } else {
            savePosition(location[0], location[1])
        }
    }

    private fun getNavigationBarHeight(): Int = BarUtils.getNavBarHeight()

    /** 圆形状态的原始宽度（首次从 layout 读取并缓存） */
    private var circleWidthPx: Int = -1

    /** 动态修改悬浮窗根布局宽度（展开胶囊 / 收回圆形） */
    private fun setFloatRootWidth(view: View, widthPx: Int) {
        val root = view.findViewById<View>(R.id.floatRoot) ?: return
        val lp = root.layoutParams
        if (lp != null && lp.width != widthPx) {
            lp.width = widthPx
            root.layoutParams = lp
        }
    }

    /** 获取圆形状态的宽度（createdResult 时缓存，确保与 XML 定义一致） */
    private fun getCircleWidth(@Suppress("UNUSED_PARAMETER") view: View): Int {
        return if (circleWidthPx > 0) circleWidthPx else WindowManager.LayoutParams.WRAP_CONTENT
    }


    /**
     * 保存位置
     */
    private fun savePosition(x: Int, y: Int) {
        KVUtils.putInt(KEY_FLOAT_X, x)
        KVUtils.putInt(KEY_FLOAT_Y, y)
    }

    /**
     * 获取保存的 X 坐标
     */
    private fun getSavedX(): Int? {
        val x = KVUtils.getInt(KEY_FLOAT_X, -1)
        return if (x == -1) null else x
    }

    /**
     * 获取保存的 Y 坐标
     */
    private fun getSavedY(): Int? {
        val y = KVUtils.getInt(KEY_FLOAT_Y, -1)
        return if (y == -1) null else y
    }

    /**
     * 点击回调，可以在外部设置
     * 默认行为：RUNNING 状态时切换推理面板，其他状态时调用外部回调
     */
    var onFloatClick: () -> Unit = {
        try {
            if (currentState == State.RUNNING) {
                val panel = FloatingReasoningPanel.getInstance(ClawApplication.instance)
                if (panel.isVisible()) {
                    panel.hide()
                } else {
                    panel.show()
                }
            } else {
                externalClickCallback?.invoke()
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Error in onFloatClick", e)
        }
    }

    /** 外部点击回调（非 RUNNING 状态时的行为，如回到 App 前台） */
    var externalClickCallback: (() -> Unit)? = null
}
