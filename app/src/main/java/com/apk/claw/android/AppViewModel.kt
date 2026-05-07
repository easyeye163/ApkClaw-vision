package com.apk.claw.android

import android.os.PowerManager
import androidx.lifecycle.ViewModel
import com.apk.claw.android.ClawApplication.Companion.appViewModelInstance
import com.apk.claw.android.agent.AgentConfig
import com.apk.claw.android.channel.Channel
import com.apk.claw.android.channel.ChannelManager
import com.apk.claw.android.channel.ChannelSetup
import com.apk.claw.android.service.ForegroundService
import com.apk.claw.android.floating.FloatingCircleManager
import com.apk.claw.android.server.ConfigServerManager
import com.apk.claw.android.service.KeepAliveJobService
import com.apk.claw.android.ui.chat.ChatActivity
import com.apk.claw.android.ui.home.HomeActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.agent.AgentCallback
import com.apk.claw.android.tool.ToolResult

class AppViewModel : ViewModel() {

    companion object {
        private const val TAG = "AppViewModel"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    private var _commonInitialized = false

    /** 本地对话的进度回调，任务执行过程中实时更新 ChatActivity UI */
    @Volatile
    var chatCallback: ChatActivity.ChatCallback? = null

    val taskOrchestrator = TaskOrchestrator(
        agentConfigProvider = { getAgentConfig() },
        onTaskFinished = {
            // 任务结束后清理 chatCallback
            chatCallback = null
        },
        chatCallbackProvider = { chatCallback }
    )

    private val channelSetup = ChannelSetup(taskOrchestrator = taskOrchestrator)

    val inProgressTaskMessageId: String get() = taskOrchestrator.inProgressTaskMessageId
    val inProgressTaskChannel: Channel? get() = taskOrchestrator.inProgressTaskChannel

    fun init() {
        initCommon()
        initAgent()
    }

    fun initCommon() {
        if (_commonInitialized) return
        _commonInitialized = true
    }

    fun initAgent() {
        if (!KVUtils.hasLlmConfig()) return
        taskOrchestrator.initAgent()
    }

    fun getAgentConfig(): AgentConfig {
        var baseUrl = KVUtils.getLlmBaseUrl().trim()
        if (baseUrl.isEmpty()) baseUrl = "https://api.openai.com/v1"
        return AgentConfig.Builder()
            .apiKey(KVUtils.getLlmApiKey())
            .baseUrl(baseUrl)
            .modelName(KVUtils.getLlmModelName())
            .temperature(0.1)
            .maxIterations(60)
            .build()
    }

    fun updateAgentConfig(): Boolean = taskOrchestrator.updateAgentConfig()

    fun afterInit() {
        acquireScreenWakeLock()
        ForegroundService.start(ClawApplication.instance)
        KeepAliveJobService.schedule(ClawApplication.instance)
        ConfigServerManager.autoStartIfNeeded(ClawApplication.instance)
        if (android.provider.Settings.canDrawOverlays(ClawApplication.instance)) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                appViewModelInstance.showFloatingCircle()
            }
        }
        channelSetup.setup()
    }


    /**
     * 获取亮屏锁，防止息屏后无障碍服务无法操作
     */
    private fun acquireScreenWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = ClawApplication.instance.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
            ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ApkClaw::ScreenWakeLock"
        ).apply {
            acquire()
        }
        XLog.i(TAG, "亮屏锁已获取")
    }

    /**
     * 释放亮屏锁
     */
    private fun releaseScreenWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                XLog.i(TAG, "亮屏锁已释放")
            }
        }
        wakeLock = null
    }

    /**
     * 显示圆形悬浮窗
     */
    fun showFloatingCircle() {
        try {
            FloatingCircleManager.show(ClawApplication.instance)
            // 设置外部点击回调（非 RUNNING 状态时回到 App 前台）
            // RUNNING 状态时默认行为是切换推理面板（见 FloatingCircleManager.onFloatClick）
            FloatingCircleManager.externalClickCallback = {
                XLog.d(TAG, "Floating circle clicked → bring app to foreground")
                bringAppToForeground()
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to show floating circle: ${e.message}")
        }
    }

    /**
     * 将应用带回前台
     */
    private fun bringAppToForeground() {
        val context = ClawApplication.instance
        val intent = android.content.Intent(context, HomeActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    fun isTaskRunning(): Boolean = taskOrchestrator.isTaskRunning()

    fun cancelCurrentTask() = taskOrchestrator.cancelCurrentTask()

    fun startNewTask(channel: Channel, task: String, messageID: String) =
        taskOrchestrator.startNewTask(channel, task, messageID)

    fun startChatTask(task: String, imageData: ByteArray?, callback: ChatActivity.ChatCallback) {
        if (!KVUtils.hasLlmConfig()) {
            callback.onError("LLM not configured")
            return
        }
        if (isTaskRunning()) {
            callback.onError("Task already running")
            return
        }

        chatCallback = callback

        val fakeMessageId = "chat_${System.currentTimeMillis()}"
        val fakeChannel = Channel.CHAT

        // 将图片转为 base64 传递给 Agent（作为多模态内容发送给 LLM）
        var imageBase64: String? = null
        if (imageData != null) {
            try {
                imageBase64 = java.util.Base64.getEncoder().encodeToString(imageData)
                val file = java.io.File(ClawApplication.instance.cacheDir, "chat_image_${System.currentTimeMillis()}.png")
                file.writeBytes(imageData)
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to process chat image", e)
            }
        }

        val taskWithContext = if (imageData != null) {
            task
        } else {
            task
        }

        taskOrchestrator.startNewTask(fakeChannel, taskWithContext, fakeMessageId, imageBase64)
    }

    private fun trySendScreenshot(channel: Channel, filePath: String, messageID: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                XLog.w(TAG, "截图文件不存在: $filePath")
                return
            }
            val imageBytes = file.readBytes()
            ChannelManager.sendImage(channel, imageBytes, messageID)
        } catch (e: Exception) {
            XLog.e(TAG, "发送截图失败", e)
        }
    }
}
