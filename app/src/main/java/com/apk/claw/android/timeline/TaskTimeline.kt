package com.apk.claw.android.timeline

import android.content.Context
import android.util.Log
import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 任务时间线 - 任务历史记录与可视化系统
 * 
 * 参考 LobsterHUD Pro 的"任务时间线"功能，记录每个 Agent 任务的完整生命周期，
 * 包括执行步骤、工具调用、LLM 交互、截图快照等，支持回溯与统计分析。
 * 
 * 核心能力：
 * - 任务全生命周期记录（创建→执行→完成/失败）
 * - 逐步骤记录（思考→工具调用→结果→下一步）
 * - 工具调用统计与分析
 * - 任务成功率与耗时统计
 * - 按日期/渠道/状态筛选
 * - 截图快照关联
 * - 持久化存储（MMKV）
 */
class TaskTimeline private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TaskTimeline"
        private const val STORAGE_KEY = "task_timeline_records"
        private const val STATS_KEY = "task_timeline_stats"
        private const val MAX_RECORDS = 500

        @Volatile
        private var instance: TaskTimeline? = null

        fun getInstance(context: Context): TaskTimeline {
            return instance ?: synchronized(this) {
                instance ?: TaskTimeline(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor()
    private val records = CopyOnWriteArrayList<TaskRecord>()
    private val stats = TimelineStats()

    // 任务状态监听
    private val listeners = CopyOnWriteArrayList<TimelineListener>()

    interface TimelineListener {
        fun onTaskStarted(record: TaskRecord) {}
        fun onStepAdded(record: TaskRecord, step: TaskStep) {}
        fun onTaskCompleted(record: TaskRecord) {}
        fun onTaskFailed(record: TaskRecord, error: String) {}
    }

    /**
     * 任务记录
     */
    data class TaskRecord(
        val id: String = UUID.randomUUID().toString(),
        val channelType: String,        // wechat / telegram / feishu / discord / qq / dingtalk / relay
        val userId: String = "",
        val userMessage: String,         // 用户原始消息
        var status: TaskStatus = TaskStatus.RUNNING,
        val createdAt: Long = System.currentTimeMillis(),
        var completedAt: Long? = null,
        var durationMs: Long? = null,
        val steps: MutableList<TaskStep> = mutableListOf(),
        val toolCallStats: MutableMap<String, Int> = mutableMapOf(),
        var totalRounds: Int = 0,
        var llmTokensUsed: Int = 0,
        val screenshots: MutableList<ScreenshotSnapshot> = mutableListOf(),
        var errorMessage: String? = null,
        val metadata: MutableMap<String, String> = mutableMapOf()
    )

    /**
     * 任务步骤
     */
    data class TaskStep(
        val round: Int,
        val type: StepType,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val durationMs: Long? = null,
        val metadata: Map<String, String> = emptyMap()
    )

    enum class StepType {
        THINKING,       // LLM 思考
        TOOL_CALL,      // 工具调用
        TOOL_RESULT,    // 工具结果
        USER_INPUT,     // 用户输入
        SYSTEM_EVENT,   // 系统事件（截图、弹窗等）
        ERROR,          // 错误
        COMPLETION      // 任务完成
    }

    enum class TaskStatus {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }

    data class ScreenshotSnapshot(
        val round: Int,
        val filePath: String,
        val timestamp: Long = System.currentTimeMillis(),
        val description: String? = null
    )

    data class TimelineStats(
        var totalTasks: Int = 0,
        var completedTasks: Int = 0,
        var failedTasks: Int = 0,
        var cancelledTasks: Int = 0,
        var totalDurationMs: Long = 0,
        var totalRounds: Int = 0,
        var totalTokensUsed: Int = 0,
        val toolUsageCount: MutableMap<String, Int> = mutableMapOf(),
        val channelStats: MutableMap<String, ChannelStat> = mutableMapOf()
    )

    data class ChannelStat(
        var total: Int = 0,
        var completed: Int = 0,
        var failed: Int = 0
    )

    init {
        loadFromStorage()
        loadStats()
    }

    // ==================== 任务记录管理 ====================

    /**
     * 创建新任务记录
     */
    fun createTask(
        channelType: String,
        userId: String = "",
        userMessage: String,
        metadata: Map<String, String> = emptyMap()
    ): TaskRecord {
        val record = TaskRecord(
            channelType = channelType,
            userId = userId,
            userMessage = userMessage,
            metadata = metadata.toMutableMap()
        )
        records.add(record)
        stats.totalTasks++
        stats.channelStats.getOrPut(channelType) { ChannelStat() }.total++

        listeners.forEach { it.onTaskStarted(record) }
        persistAsync()
        Log.d(TAG, "Task created: ${record.id} from $channelType")
        return record
    }

    /**
     * 添加任务步骤
     */
    fun addStep(recordId: String, step: TaskStep) {
        val record = records.find { it.id == recordId } ?: return
        record.steps.add(step)

        // 统计工具调用
        if (step.type == StepType.TOOL_CALL) {
            val toolName = step.metadata["tool_name"] ?: "unknown"
            record.toolCallStats[toolName] = (record.toolCallStats[toolName] ?: 0) + 1
            stats.toolUsageCount[toolName] = (stats.toolUsageCount[toolName] ?: 0) + 1
        }

        listeners.forEach { it.onStepAdded(record, step) }
        persistAsync()
    }

    /**
     * 记录截图快照
     */
    fun addScreenshot(recordId: String, round: Int, filePath: String, description: String? = null) {
        val record = records.find { it.id == recordId } ?: return
        record.screenshots.add(ScreenshotSnapshot(round, filePath, description = description))
    }

    /**
     * 标记任务完成
     */
    fun completeTask(recordId: String, summary: String? = null, totalRounds: Int = 0, tokensUsed: Int = 0) {
        val record = records.find { it.id == recordId } ?: return
        record.status = TaskStatus.COMPLETED
        record.completedAt = System.currentTimeMillis()
        record.durationMs = (record.completedAt ?: 0L) - record.createdAt
        record.totalRounds = totalRounds
        record.llmTokensUsed = tokensUsed

        if (summary != null) {
            addStep(recordId, TaskStep(
                round = totalRounds,
                type = StepType.COMPLETION,
                content = summary
            ))
        }

        // 更新统计
        stats.completedTasks++
        stats.totalDurationMs += record.durationMs ?: 0
        stats.totalRounds += totalRounds
        stats.totalTokensUsed += tokensUsed
        stats.channelStats[record.channelType]?.completed = 
            (stats.channelStats[record.channelType]?.completed ?: 0) + 1

        listeners.forEach { it.onTaskCompleted(record) }
        evictIfNeeded()
        persistAsync()
        persistStatsAsync()
        Log.d(TAG, "Task completed: ${record.id} in ${record.durationMs}ms")
    }

    /**
     * 标记任务失败
     */
    fun failTask(recordId: String, error: String) {
        val record = records.find { it.id == recordId } ?: return
        record.status = TaskStatus.FAILED
        record.completedAt = System.currentTimeMillis()
        record.durationMs = (record.completedAt ?: 0L) - record.createdAt
        record.errorMessage = error

        stats.failedTasks++
        stats.channelStats[record.channelType]?.failed = 
            (stats.channelStats[record.channelType]?.failed ?: 0) + 1

        listeners.forEach { it.onTaskFailed(record, error) }
        evictIfNeeded()
        persistAsync()
        persistStatsAsync()
        Log.d(TAG, "Task failed: ${record.id} - $error")
    }

    /**
     * 标记任务取消
     */
    fun cancelTask(recordId: String) {
        val record = records.find { it.id == recordId } ?: return
        record.status = TaskStatus.CANCELLED
        record.completedAt = System.currentTimeMillis()
        record.durationMs = (record.completedAt ?: 0L) - record.createdAt

        stats.cancelledTasks++
        evictIfNeeded()
        persistAsync()
        persistStatsAsync()
    }

    // ==================== 查询接口 ====================

    fun getTask(recordId: String): TaskRecord? = records.find { it.id == recordId }

    fun getAllTasks(): List<TaskRecord> = records.toList()

    fun getRunningTask(): TaskRecord? = records.findLast { it.status == TaskStatus.RUNNING }

    fun getTasksByChannel(channelType: String): List<TaskRecord> =
        records.filter { it.channelType == channelType }

    fun getTasksByStatus(status: TaskStatus): List<TaskRecord> =
        records.filter { it.status == status }

    fun getRecentTasks(limit: Int = 20): List<TaskRecord> =
        records.takeLast(limit).reversed()

    fun getTasksByDateRange(startMs: Long, endMs: Long): List<TaskRecord> =
        records.filter { it.createdAt in startMs..endMs }

    /**
     * 获取今日统计摘要
     */
    fun getTodaySummary(): Map<String, Any> {
        val todayStart = getTodayStartMs()
        val todayTasks = records.filter { it.createdAt >= todayStart }

        val completed = todayTasks.count { it.status == TaskStatus.COMPLETED }
        val failed = todayTasks.count { it.status == TaskStatus.FAILED }
        val avgDuration = if (completed > 0) {
            todayTasks.filter { it.status == TaskStatus.COMPLETED }
                .mapNotNull { it.durationMs }.average()
        } else 0.0

        return mapOf(
            "total" to todayTasks.size,
            "completed" to completed,
            "failed" to failed,
            "successRate" to if (todayTasks.isNotEmpty()) completed.toFloat() / todayTasks.size else 0f,
            "avgDurationMs" to avgDuration,
            "topTools" to getTopTools(5)
        )
    }

    /**
     * 获取最常用工具 TOP N
     */
    fun getTopTools(limit: Int = 5): List<Pair<String, Int>> {
        return stats.toolUsageCount.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    fun getStats(): TimelineStats = stats

    // ==================== 监听器 ====================

    fun addListener(listener: TimelineListener) { listeners.add(listener) }
    fun removeListener(listener: TimelineListener) { listeners.remove(listener) }

    // ==================== 内部方法 ====================

    private fun getTodayStartMs(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun evictIfNeeded() {
        if (records.size <= MAX_RECORDS) return
        val toRemove = records.take(records.size - MAX_RECORDS)
        records.removeAll(toRemove)
    }

    private fun persistAsync() {
        executor.execute {
            try {
                KVUtils.putString(STORAGE_KEY, gson.toJson(records))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist records", e)
            }
        }
    }

    private fun persistStatsAsync() {
        executor.execute {
            try {
                KVUtils.putString(STATS_KEY, gson.toJson(stats))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist stats", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromStorage() {
        try {
            val json = KVUtils.getString(STORAGE_KEY) ?: return
            val type = object : TypeToken<List<TaskRecord>>() {}.type
            val loaded: List<TaskRecord>? = gson.fromJson(json, type)
            loaded?.let { records.clear(); records.addAll(it) }
            Log.d(TAG, "Loaded ${records.size} records")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load records", e)
        }
    }

    private fun loadStats() {
        try {
            val json = KVUtils.getString(STATS_KEY) ?: return
            val loaded = gson.fromJson(json, TimelineStats::class.java) ?: return
            stats.totalTasks = loaded.totalTasks
            stats.completedTasks = loaded.completedTasks
            stats.failedTasks = loaded.failedTasks
            stats.cancelledTasks = loaded.cancelledTasks
            stats.totalDurationMs = loaded.totalDurationMs
            stats.totalRounds = loaded.totalRounds
            stats.totalTokensUsed = loaded.totalTokensUsed
            stats.toolUsageCount.putAll(loaded.toolUsageCount)
            stats.channelStats.putAll(loaded.channelStats)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load stats", e)
        }
    }

    fun clearAll() {
        records.clear()
        stats.totalTasks = 0; stats.completedTasks = 0; stats.failedTasks = 0; stats.cancelledTasks = 0
        stats.totalDurationMs = 0; stats.totalRounds = 0; stats.totalTokensUsed = 0
        stats.toolUsageCount.clear(); stats.channelStats.clear()
        KVUtils.remove(STORAGE_KEY)
        KVUtils.remove(STATS_KEY)
    }
}
