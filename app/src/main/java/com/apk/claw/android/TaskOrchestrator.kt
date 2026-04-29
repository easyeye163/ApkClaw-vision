package com.apk.claw.android

import com.apk.claw.android.agent.AgentCallback
import com.apk.claw.android.agent.AgentConfig
import com.apk.claw.android.agent.AgentService
import com.apk.claw.android.agent.AgentServiceFactory
import com.apk.claw.android.channel.Channel
import com.apk.claw.android.channel.ChannelManager
import com.apk.claw.android.floating.FloatingCircleManager
import com.apk.claw.android.floating.reasoning.FloatingReasoningPanel
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.service.ClawAccessibilityService
import com.apk.claw.android.tool.ToolResult
import com.apk.claw.android.utils.XLog

class TaskOrchestrator(
    private val agentConfigProvider: () -> AgentConfig,
    private val onTaskFinished: () -> Unit
) {

    companion object {
        private const val TAG = "TaskOrchestrator"
    }

    private lateinit var agentService: AgentService

    private val taskLock = Any()
    @Volatile
    var inProgressTaskMessageId: String = ""
        private set
    @Volatile
    var inProgressTaskChannel: Channel? = null
        private set

    fun initAgent() {
        agentService = AgentServiceFactory.create()
        try {
            agentService.initialize(agentConfigProvider())
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to initialize AgentService", e)
        }
    }

    fun updateAgentConfig(): Boolean {
        return try {
            val config = agentConfigProvider()
            if (::agentService.isInitialized) {
                agentService.updateConfig(config)
                XLog.d(TAG, "Agent config updated: model=${config.modelName}, temp=${config.temperature}")
                true
            } else {
                XLog.w(TAG, "AgentService not initialized, initializing with new config")
                agentService = AgentServiceFactory.create()
                agentService.initialize(config)
                true
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to update agent config", e)
            false
        }
    }

    fun tryAcquireTask(messageId: String, channel: Channel): Boolean {
        synchronized(taskLock) {
            if (inProgressTaskMessageId.isNotEmpty()) return false
            inProgressTaskMessageId = messageId
            inProgressTaskChannel = channel
            return true
        }
    }

    private fun releaseTask(): Pair<Channel?, String> {
        synchronized(taskLock) {
            val ch = inProgressTaskChannel
            val id = inProgressTaskMessageId
            inProgressTaskMessageId = ""
            inProgressTaskChannel = null
            return ch to id
        }
    }

    fun isTaskRunning(): Boolean {
        synchronized(taskLock) {
            return inProgressTaskMessageId.isNotEmpty()
        }
    }

    private var currentTimelineRecordId: String = ""

    fun cancelCurrentTask() {
        if (!isTaskRunning()) return
        try {
            if (::agentService.isInitialized) {
                agentService.cancel()
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Error cancelling agent", e)
        }
        
        val (channel, messageId) = releaseTask()
        try {
            if (channel != null && messageId.isNotEmpty()) {
                ChannelManager.sendMessage(channel, ClawApplication.instance.getString(R.string.channel_msg_task_cancelled), messageId)
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Error sending cancel message", e)
        }
        
        try {
            FloatingCircleManager.setErrorState()
        } catch (e: Exception) {
            XLog.e(TAG, "Error setting floating circle state", e)
        }
        
        try {
            if (currentTimelineRecordId.isNotEmpty()) {
                FeatureIntegrationManager.getInstance(ClawApplication.instance).onTaskCompleted(currentTimelineRecordId, false, "User cancelled")
                currentTimelineRecordId = ""
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Error updating timeline", e)
        }
        
        try {
            onTaskFinished()
        } catch (e: Exception) {
            XLog.e(TAG, "Error in onTaskFinished callback", e)
        }
        
        XLog.d(TAG, "Current task cancelled by user")
    }

    fun startNewTask(channel: Channel, task: String, messageID: String) {
        try {
            if (!::agentService.isInitialized) {
                XLog.e(TAG, "AgentService not initialized, attempting to initialize")
                try {
                    agentService = AgentServiceFactory.create()
                    agentService.initialize(agentConfigProvider())
                } catch (e: Exception) {
                    XLog.e(TAG, "Failed to initialize AgentService", e)
                    releaseTask()
                    ChannelManager.sendMessage(channel, ClawApplication.instance.getString(R.string.channel_msg_service_not_ready), messageID)
                    return
                }
            }

            ClawAccessibilityService.getInstance()?.pressHome()
        } catch (e: Exception) {
            XLog.e(TAG, "Error in pre-task setup", e)
        }

        try {
            FloatingCircleManager.showTaskNotify(task, channel)
        } catch (e: Exception) {
            XLog.e(TAG, "Error showing task notify", e)
        }

        val integration = FeatureIntegrationManager.getInstance(ClawApplication.instance)
        try {
            currentTimelineRecordId = integration.onTaskStarted(channel.name.lowercase(), messageID, task)
        } catch (e: Exception) {
            XLog.e(TAG, "Error starting timeline record", e)
            currentTimelineRecordId = ""
        }

        val reasoningPanel = FloatingReasoningPanel.getInstance(ClawApplication.instance)
        try {
            reasoningPanel.clearSteps()
            reasoningPanel.setTaskName(task)
            reasoningPanel.setCancelCallback { cancelCurrentTask() }
            reasoningPanel.show()
        } catch (e: Exception) {
            XLog.e(TAG, "Error initializing reasoning panel, continuing without it", e)
        }

        val roundBuffer = StringBuilder()

        fun flushRoundBuffer() {
            if (roundBuffer.isNotEmpty()) {
                try {
                    ChannelManager.sendMessage(channel, roundBuffer.toString().trim(), messageID)
                } catch (e: Exception) {
                    XLog.e(TAG, "Error flushing round buffer", e)
                }
                roundBuffer.clear()
            }
        }

        agentService.executeTask(task, object : AgentCallback {
            override fun onLoopStart(round: Int) {
                try {
                    flushRoundBuffer()
                    FloatingCircleManager.setRunningState(round, channel)
                    reasoningPanel.onLoopStart(round)
                    integration.onTaskStep(currentTimelineRecordId, "SYSTEM_EVENT", "Round $round started", mapOf("round" to round.toString()))
                } catch (e: Exception) {
                    XLog.e(TAG, "Error in onLoopStart callback", e)
                }
            }

            override fun onContent(round: Int, content: String) {
                try {
                    if (content.isNotEmpty()) {
                        roundBuffer.append(content)
                        reasoningPanel.onContent(round, content)
                        integration.onTaskStep(currentTimelineRecordId, "THINKING", content, mapOf("round" to round.toString()))
                    }
                } catch (e: Exception) {
                    XLog.e(TAG, "Error in onContent callback", e)
                }
            }

            override fun onToolCall(round: Int, toolId: String, toolName: String, parameters: String) {
                try {
                    XLog.d(TAG, "onToolCall: $toolId($toolName), $parameters")
                    reasoningPanel.onToolCall(round, toolId, toolName, parameters)
                    integration.onTaskStep(currentTimelineRecordId, "TOOL_CALL", "$toolName: $parameters", mapOf("round" to round.toString(), "toolId" to toolId))
                } catch (e: Exception) {
                    XLog.e(TAG, "Error in onToolCall callback", e)
                }
            }

            override fun onToolResult(round: Int, toolId: String, toolName: String, parameters: String, result: ToolResult) {
                try {
                    val app = ClawApplication.instance
                    val status = if (result.isSuccess) app.getString(R.string.channel_msg_tool_success) else app.getString(R.string.channel_msg_tool_failure)
                    var data = if (result.isSuccess) result.data else result.error
                    if (data != null && data.length > 300) {
                        data = data.substring(0, 300) + "...(truncated)"
                    }
                    if (!result.isSuccess) {
                        XLog.e(TAG, "!!!!!!!!!!Fail: $toolName, $parameters $data")
                    }
                    XLog.e(TAG, "onToolResult: $toolName, $status $data")
                    reasoningPanel.onToolResult(round, toolId, toolName, if (result.isSuccess) data ?: "OK" else result.error ?: "Failed", result.isSuccess)
                    integration.onTaskStep(currentTimelineRecordId, "TOOL_RESULT", "$toolName: ${if (result.isSuccess) "成功" else "失败"}", mapOf("round" to round.toString(), "toolId" to toolId, "success" to result.isSuccess.toString()))
                    if (toolId == "finish" && (result.data?.isNotEmpty() ?: false)) {
                        flushRoundBuffer()
                        ChannelManager.sendMessage(channel, result.data ?: "", messageID)
                    } else {
                        if (roundBuffer.isNotEmpty()) roundBuffer.append("\n")
                        roundBuffer.append(
                            app.getString(R.string.channel_msg_tool_execution, toolName + parameters, status)
                        )
                    }
                } catch (e: Exception) {
                    XLog.e(TAG, "Error in onToolResult callback", e)
                }
            }

            override fun onComplete(round: Int, finalAnswer: String, totalTokens: Int) {
                try {
                    XLog.i(TAG, "onComplete: 轮数=$round, totalTokens=$totalTokens, answer=$finalAnswer")
                    flushRoundBuffer()
                    releaseTask()
                    ChannelManager.flushMessages(channel)
                    FloatingCircleManager.setSuccessState()
                    reasoningPanel.onComplete(round, finalAnswer)
                    integration.onTaskCompleted(currentTimelineRecordId, true, finalAnswer, round, totalTokens)
                    currentTimelineRecordId = ""
                    onTaskFinished()
                } catch (e: Exception) {
                    XLog.e(TAG, "Error in onComplete callback", e)
                    releaseTask()
                    try { onTaskFinished() } catch (_: Exception) {}
                }
            }

            override fun onError(round: Int, error: Exception, totalTokens: Int) {
                try {
                    XLog.e(TAG, "onError: ${error.message}, totalTokens=$totalTokens", error)
                    flushRoundBuffer()
                    releaseTask()
                    ChannelManager.sendMessage(channel, ClawApplication.instance.getString(R.string.channel_msg_task_error, error.message), messageID)
                    ChannelManager.flushMessages(channel)
                    FloatingCircleManager.setErrorState()
                    reasoningPanel.onError(round, error.message ?: "Unknown error")
                    integration.onTaskCompleted(currentTimelineRecordId, false, error.message ?: "Unknown error", round, totalTokens)
                    currentTimelineRecordId = ""
                    onTaskFinished()
                } catch (e: Exception) {
                    XLog.e(TAG, "Error in onError callback", e)
                    releaseTask()
                    try { onTaskFinished() } catch (_: Exception) {}
                }
            }

            override fun onSystemDialogBlocked(round: Int, totalTokens: Int) {
                try {
                    XLog.w(TAG, "onSystemDialogBlocked: round=$round, totalTokens=$totalTokens")
                    flushRoundBuffer()
                    releaseTask()
                    ChannelManager.sendMessage(channel, ClawApplication.instance.getString(R.string.channel_msg_system_dialog_blocked), messageID)
                    try {
                        val service = ClawAccessibilityService.getInstance()
                        val bitmap = service?.takeScreenshot(5000)
                        if (bitmap != null) {
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, stream)
                            bitmap.recycle()
                            ChannelManager.sendImage(channel, stream.toByteArray(), messageID)
                        }
                    } catch (e: Exception) {
                        XLog.e(TAG, "Failed to send screenshot for system dialog", e)
                    }
                    FloatingCircleManager.setErrorState()
                    integration.onTaskCompleted(currentTimelineRecordId, false, "System dialog blocked", round, totalTokens)
                    currentTimelineRecordId = ""
                    onTaskFinished()
                } catch (e: Exception) {
                    XLog.e(TAG, "Error in onSystemDialogBlocked callback", e)
                    releaseTask()
                    try { onTaskFinished() } catch (_: Exception) {}
                }
            }
        })
    }
}