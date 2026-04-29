package com.apk.claw.android.integration

import android.content.Context
import android.util.Log
import com.apk.claw.android.brain.LocalBrain
import com.apk.claw.android.floating.reasoning.FloatingReasoningPanel
import com.apk.claw.android.relay.RelayRoomSystem
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.timeline.TaskTimeline

/**
 * 功能集成管理器
 * 
 * 统一管理 LobsterHUD Pro 功能模块的初始化、生命周期和交互。
 * 所有新功能通过此管理器与现有 ApkClaw 系统对接。
 */
class FeatureIntegrationManager(private val context: Context) {

    companion object {
        private const val TAG = "FeatureIntegration"

        @Volatile
        private var instance: FeatureIntegrationManager? = null

        fun getInstance(context: Context): FeatureIntegrationManager {
            return instance ?: synchronized(this) {
                instance ?: FeatureIntegrationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    val localBrain: LocalBrain by lazy { LocalBrain.getInstance(context) }
    val taskTimeline: TaskTimeline by lazy { TaskTimeline.getInstance(context) }
    val skillSystem: SkillSystem by lazy { SkillSystem.getInstance(context) }
    val relayRoom: RelayRoomSystem by lazy { RelayRoomSystem.getInstance(context) }
    val reasoningPanel: FloatingReasoningPanel by lazy { FloatingReasoningPanel.getInstance(context) }

    var isInitialized = false
        private set

    fun initialize() {
        if (isInitialized) return
        Log.d(TAG, "Initializing feature modules...")
        skillSystem.initBuiltInSkills()
        isInitialized = true
        Log.d(TAG, "All feature modules initialized")
        logModuleStatus()
    }

    /**
     * 根据用户消息增强 Agent System Prompt
     * 1. 从 LocalBrain 检索相关记忆
     * 2. 匹配技能，注入技能上下文
     */
    fun buildEnhancedSystemPrompt(userMessage: String, baseSystemPrompt: String): String {
        val sb = StringBuilder()
        sb.append(baseSystemPrompt)

        val brainContext = localBrain.buildContextInjection(userMessage)
        if (brainContext.isNotBlank()) {
            sb.append("\n\n").append(brainContext)
        }

        val matchedSkill = skillSystem.matchSkill(userMessage)
        if (matchedSkill != null) {
            sb.append("\n\n[技能激活: ${matchedSkill.name}]\n")
            sb.append("${matchedSkill.description}\n")
            if (matchedSkill.systemPrompt != null) {
                sb.append("技能专用指令: ${matchedSkill.systemPrompt}\n")
            }
            sb.append("提示词模板: ${skillSystem.buildSkillPrompt(matchedSkill, userMessage)}\n")
            Log.d(TAG, "Skill matched: ${matchedSkill.name}")
        }

        val preferences = localBrain.getUserPreferences()
        if (preferences.isNotEmpty()) {
            sb.append("\n\n[用户偏好设置]\n")
            preferences.take(5).forEach { pref -> sb.append("- ${pref.content}\n") }
        }

        return sb.toString()
    }

    fun onTaskStarted(channelType: String, userId: String, userMessage: String): String {
        val record = taskTimeline.createTask(channelType, userId, userMessage)
        return record.id
    }

    fun onTaskStep(recordId: String, stepType: String, content: String, metadata: Map<String, String> = emptyMap()) {
        val step = TaskTimeline.TaskStep(
            round = metadata["round"]?.toIntOrNull() ?: 0,
            type = try { TaskTimeline.StepType.valueOf(stepType) } catch (_: Exception) { TaskTimeline.StepType.SYSTEM_EVENT },
            content = content,
            metadata = metadata
        )
        taskTimeline.addStep(recordId, step)
    }

    fun onTaskCompleted(recordId: String, success: Boolean, summary: String = "", totalRounds: Int = 0, tokensUsed: Int = 0) {
        if (success) {
            taskTimeline.completeTask(recordId, summary, totalRounds, tokensUsed)
        } else {
            taskTimeline.failTask(recordId, summary.ifEmpty { "Unknown error" })
        }
        val record = taskTimeline.getTask(recordId)
        if (record != null && success) {
            localBrain.saveSummary(
                LocalBrain.ConversationSummary(
                    channelType = record.channelType,
                    userId = record.userId,
                    startTime = record.createdAt,
                    endTime = record.completedAt ?: System.currentTimeMillis(),
                    taskDescription = record.userMessage,
                    summary = summary,
                    toolUsageStats = record.toolCallStats.toMap(),
                    success = success
                )
            )
        }
    }

    fun getFeatureOverview(): Map<String, Any> {
        return mapOf(
            "localBrain" to mapOf("entriesCount" to localBrain.getAllEntries().size, "stats" to localBrain.getStats()),
            "taskTimeline" to mapOf("totalTasks" to taskTimeline.getStats().totalTasks, "todaySummary" to taskTimeline.getTodaySummary()),
            "skillSystem" to mapOf("totalSkills" to skillSystem.getAllSkills().size, "enabledSkills" to skillSystem.getEnabledSkills().size, "stats" to skillSystem.getStats()),
            "relayRoom" to mapOf("myRooms" to relayRoom.getMyRooms().map { mapOf("id" to it.roomId, "name" to it.roomName, "devices" to it.devices.size) }, "deviceId" to relayRoom.config.myDeviceId),
            "reasoningPanel" to mapOf("isShowing" to reasoningPanel.isVisible(), "stepCount" to 0)
        )
    }

    private fun logModuleStatus() {
        Log.d(TAG, "=== Feature Module Status ===")
        Log.d(TAG, "LocalBrain: ${localBrain.getAllEntries().size} entries")
        Log.d(TAG, "TaskTimeline: ${taskTimeline.getStats().totalTasks} tasks")
        Log.d(TAG, "SkillSystem: ${skillSystem.getAllSkills().size} skills (${skillSystem.getEnabledSkills().size} enabled)")
        Log.d(TAG, "RelayRoom: device ${relayRoom.config.myDeviceId}, ${relayRoom.getMyRooms().size} rooms")
        Log.d(TAG, "ReasoningPanel: ${if (reasoningPanel.isVisible()) "visible" else "hidden"}")
    }

    fun shutdown() {
        reasoningPanel.hide()
        relayRoom.shutdown()
        isInitialized = false
    }
}
