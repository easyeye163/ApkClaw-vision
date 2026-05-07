package com.apk.claw.android.skill

import android.content.Context
import android.util.Log
import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 技能系统 - 可复用操作链管理
 * 
 * 参考 LobsterHUD Pro 的"技能"功能，支持将自然语言指令转化为可复用的操作链。
 * 技能可被 Agent 自动调用，也可由用户手动触发。
 * 
 * 核心能力：
 * - 技能 CRUD（创建/编辑/删除/启用/禁用）
 * - 自然语言定义技能触发条件和执行步骤
 * - 技能分类管理（自动回复/手机操控/定时任务/跨应用）
 * - 内置技能模板（开箱即用）
 * - 技能运行状态监控
 * - 技能导入/导出（JSON）
 * - 与 LocalBrain 联动（技能执行结果自动沉淀）
 */
class SkillSystem private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SkillSystem"
        private const val STORAGE_KEY = "skill_system_skills"
        private const val MAX_SKILLS = 200

        @Volatile
        private var instance: SkillSystem? = null

        fun getInstance(context: Context): SkillSystem {
            return instance ?: synchronized(this) {
                instance ?: SkillSystem(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor()
    private val skills = CopyOnWriteArrayList<Skill>()
    private val listeners = CopyOnWriteArrayList<SkillListener>()

    interface SkillListener {
        fun onSkillTriggered(skill: Skill, triggerMessage: String) {}
        fun onSkillStarted(skill: Skill) {}
        fun onSkillStepCompleted(skill: Skill, stepIndex: Int, result: String) {}
        fun onSkillCompleted(skill: Skill, success: Boolean, summary: String) {}
    }

    // ==================== 数据模型 ====================

    data class Skill(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val description: String,
        val category: SkillCategory = SkillCategory.CUSTOM,
        val triggerType: TriggerType = TriggerType.MANUAL,
        val triggerKeywords: List<String> = emptyList(),
        val triggerRegex: String? = null,
        val promptTemplate: String,           // 发送给 LLM 的提示词模板
        val systemPrompt: String? = null,     // 技能专用 System Prompt（覆盖全局）
        val offlineSteps: String? = null,     // 离线执行步骤 JSON（工具调用序列），非null时可直接离线执行
        val maxIterations: Int = 20,
        val enabled: Boolean = true,
        val isBuiltIn: Boolean = false,
        val version: Int = 1,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val runCount: Int = 0,
        val successCount: Int = 0,
        val lastRunAt: Long? = null,
        val metadata: Map<String, String> = emptyMap()
    ) {
        /** 是否可离线执行（有预定义步骤且不依赖 LLM） */
        val isOfflineExecutable: Boolean get() = !offlineSteps.isNullOrBlank()
    }

    enum class SkillCategory(val displayName: String) {
        AUTO_REPLY("自动回复"),       // 消息弹窗自动回复
        PHONE_CONTROL("手机操控"),    // 手机操作链
        SCHEDULED("定时任务"),        // 定时执行
        CROSS_APP("跨应用"),          // 跨应用协作
        CUSTOM("自定义")              // 用户自定义
    }

    enum class TriggerType(val displayName: String) {
        KEYWORD("关键词触发"),
        REGEX("正则匹配"),
        MANUAL("手动触发"),
        SCHEDULED("定时触发"),
        EVENT("事件触发")
    }

    data class SkillRunContext(
        val skill: Skill,
        val triggerMessage: String,
        val channelType: String,
        val userId: String,
        val startTime: Long = System.currentTimeMillis()
    )

    // ==================== 技能管理 ====================

    fun createSkill(
        name: String,
        description: String,
        promptTemplate: String,
        category: SkillCategory = SkillCategory.CUSTOM,
        triggerType: TriggerType = TriggerType.MANUAL,
        triggerKeywords: List<String> = emptyList(),
        triggerRegex: String? = null,
        systemPrompt: String? = null,
        offlineSteps: String? = null,
        maxIterations: Int = 20
    ): Skill {
        val skill = Skill(
            name = name,
            description = description,
            promptTemplate = promptTemplate,
            category = category,
            triggerType = triggerType,
            triggerKeywords = triggerKeywords,
            triggerRegex = triggerRegex,
            systemPrompt = systemPrompt,
            offlineSteps = offlineSteps,
            maxIterations = maxIterations
        )
        skills.add(skill)
        persistAsync()
        Log.d(TAG, "Skill created: ${skill.name}")
        return skill
    }

    fun updateSkill(skillId: String, updates: Map<String, Any>): Boolean {
        val index = skills.indexOfFirst { it.id == skillId } ?: return false
        if (index == -1) return false

        val old = skills[index]
        val updated = old.copy(
            name = updates["name"] as? String ?: old.name,
            description = updates["description"] as? String ?: old.description,
            promptTemplate = updates["promptTemplate"] as? String ?: old.promptTemplate,
            category = updates["category"] as? SkillCategory ?: old.category,
            triggerType = updates["triggerType"] as? TriggerType ?: old.triggerType,
            triggerKeywords = updates["triggerKeywords"] as? List<String> ?: old.triggerKeywords,
            triggerRegex = updates["triggerRegex"] as? String ?: old.triggerRegex,
            systemPrompt = updates["systemPrompt"] as? String ?: old.systemPrompt,
            offlineSteps = updates["offlineSteps"] as? String ?: old.offlineSteps,
            maxIterations = updates["maxIterations"] as? Int ?: old.maxIterations,
            enabled = updates["enabled"] as? Boolean ?: old.enabled,
            updatedAt = System.currentTimeMillis(),
            version = old.version + 1
        )
        skills[index] = updated
        persistAsync()
        return true
    }

    fun deleteSkill(skillId: String): Boolean {
        val skill = skills.find { it.id == skillId } ?: return false
        if (skill.isBuiltIn) {
            Log.w(TAG, "Cannot delete built-in skill: ${skill.name}")
            return false
        }
        skills.remove(skill)
        persistAsync()
        return true
    }

    fun toggleSkill(skillId: String, enabled: Boolean): Boolean {
        val index = skills.indexOfFirst { it.id == skillId } ?: return false
        if (index == -1) return false
        skills[index] = skills[index].copy(enabled = enabled, updatedAt = System.currentTimeMillis())
        persistAsync()
        return true
    }

    fun getSkill(skillId: String): Skill? = skills.find { it.id == skillId }

    fun getAllSkills(): List<Skill> = skills.toList()

    fun getEnabledSkills(): List<Skill> = skills.filter { it.enabled }

    fun getSkillsByCategory(category: SkillCategory): List<Skill> =
        skills.filter { it.category == category }

    fun getBuiltInSkills(): List<Skill> = skills.filter { it.isBuiltIn }

    // ==================== 技能匹配与触发 ====================

    /**
     * 根据用户消息匹配技能
     * 返回第一个匹配的技能（优先级：正则 > 关键词）
     */
    fun matchSkill(userMessage: String): Skill? {
        val enabledSkills = getEnabledSkills().filter { it.triggerType != TriggerType.MANUAL }

        // 1. 先尝试正则匹配
        for (skill in enabledSkills) {
            if (skill.triggerRegex != null) {
                try {
                    if (Regex(skill.triggerRegex, RegexOption.IGNORE_CASE).containsMatchIn(userMessage)) {
                        return skill
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid regex in skill ${skill.name}: ${skill.triggerRegex}")
                }
            }
        }

        // 2. 再尝试关键词匹配
        for (skill in enabledSkills) {
            if (skill.triggerKeywords.isNotEmpty()) {
                val msgLower = userMessage.lowercase()
                val matchCount = skill.triggerKeywords.count { keyword ->
                    msgLower.contains(keyword.lowercase())
                }
                // 至少匹配一个关键词
                if (matchCount > 0) {
                    return skill
                }
            }
        }

        return null
    }

    /**
     * 获取技能的完整提示词（模板 + 用户消息填充）
     */
    fun buildSkillPrompt(skill: Skill, userMessage: String, extraContext: String = ""): String {
        var prompt = skill.promptTemplate
            .replace("\$USER_MESSAGE", userMessage)
            .replace("\$MESSAGE", userMessage)
            .replace("\$CURRENT_TIME", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            .replace("\$EXTRA_CONTEXT", extraContext)

        if (skill.systemPrompt != null) {
            prompt = "[技能指令]\n${skill.systemPrompt}\n\n[用户消息]\n$userMessage\n\n[执行要求]\n$prompt"
        } else if (userMessage.isNotBlank() && !skill.promptTemplate.contains("\$USER_MESSAGE") && !skill.promptTemplate.contains("\$MESSAGE")) {
            // 当模板中没有使用 $USER_MESSAGE 占位符时，将用户原始消息追加到提示词末尾
            prompt = "$prompt\n\n[用户原始消息]\n$userMessage"
        }

        return prompt
    }

    // ==================== 内置技能模板 ====================

    /**
     * 初始化：先加载持久化的技能，再补充内置技能模板
     */
    fun initBuiltInSkills() {
        // 先从存储加载已有技能（包括自定义技能）
        loadFromStorage()
        // 再检查是否需要添加内置技能
        if (getBuiltInSkills().isNotEmpty()) return

        val builtIns = listOf(
            Skill(
                name = "微信消息自动回复",
                description = "当收到微信消息时，自动分析内容并生成合适的回复",
                category = SkillCategory.AUTO_REPLY,
                triggerType = TriggerType.EVENT,
                triggerKeywords = listOf("微信", "消息"),
                promptTemplate = "你是一个贴心的消息助手。请根据以下收到的消息内容，生成一个自然、友好的回复。\n\n收到消息: \$USER_MESSAGE\n\n回复要求:\n1. 语气自然，像真人回复\n2. 根据消息内容判断是否需要认真回复还是轻松回复\n3. 回复简洁有度，不要过于冗长\n4. 如果是工作消息，保持专业但不冷漠",
                isBuiltIn = true
            ),
            Skill(
                name = "抖音刷视频",
                description = "自动打开抖音并刷视频，可指定刷多少个或刷多长时间",
                category = SkillCategory.PHONE_CONTROL,
                triggerType = TriggerType.KEYWORD,
                triggerKeywords = listOf("刷抖音", "看抖音", "抖音"),
                promptTemplate = "请帮我打开抖音应用，然后自动刷视频。\n\n用户要求: \$USER_MESSAGE\n\n执行步骤:\n1. 使用 open_app 打开抖音\n2. 等待3秒让应用加载\n3. 使用 swipe 从下往上滑动来刷视频\n4. 每个视频停留5-15秒（随机）\n5. 偶尔点赞感兴趣的\n6. 重复滑动，直到满足用户要求的时间或数量\n\n注意: 使用 wait 工具控制每个视频的停留时间，使用 tap 工具点赞",
                maxIterations = 30,
                isBuiltIn = true
            ),
            Skill(
                name = "每日签到打卡",
                description = "自动打开指定应用完成签到打卡操作",
                category = SkillCategory.SCHEDULED,
                triggerType = TriggerType.SCHEDULED,
                triggerKeywords = listOf("签到", "打卡", "签到打卡"),
                promptTemplate = "请帮我完成每日签到打卡任务。\n\n用户要求: \$USER_MESSAGE\n\n执行步骤:\n1. 分析用户要签到哪个应用\n2. 使用 open_app 打开目标应用\n3. 等待应用加载完成\n4. 使用 get_screen_info 查看页面内容\n5. 找到签到/打卡按钮\n6. 使用 tap 点击签到按钮\n7. 等待并确认签到结果\n8. 使用 finish 工具报告结果",
                maxIterations = 15,
                isBuiltIn = true
            ),
            Skill(
                name = "跨应用信息查询",
                description = "在一个应用中获取信息后，到另一个应用中使用（如从备忘录读取信息发送给微信联系人）",
                category = SkillCategory.CROSS_APP,
                triggerType = TriggerType.KEYWORD,
                triggerKeywords = listOf("跨应用", "从", "发送到", "转发到"),
                promptTemplate = "请帮我完成跨应用操作。\n\n用户要求: \$USER_MESSAGE\n\n执行原则:\n1. 先理解用户要从哪个应用获取什么信息\n2. 打开源应用，获取所需信息\n3. 打开目标应用，输入或发送信息\n4. 跨应用切换时使用 open_app 工具\n5. 确认操作完成后报告结果",
                maxIterations = 25,
                isBuiltIn = true
            ),
            Skill(
                name = "手机清理优化",
                description = "自动清理手机缓存、关闭后台应用、释放内存",
                category = SkillCategory.PHONE_CONTROL,
                triggerType = TriggerType.KEYWORD,
                triggerKeywords = listOf("清理", "优化", "加速", "释放内存"),
                promptTemplate = "请帮我优化手机性能。\n\n用户要求: \$USER_MESSAGE\n\n执行步骤:\n1. 查看当前运行的应用列表\n2. 返回桌面\n3. 查看后台应用情况\n4. 清理不必要的后台应用\n5. 如有系统清理工具，执行清理操作\n6. 使用 finish 报告优化结果",
                maxIterations = 15,
                isBuiltIn = true
            )
        )

        for (skill in builtIns) {
            skills.add(skill)
        }
        persistAsync()
        Log.d(TAG, "Initialized ${builtIns.size} built-in skills")
    }

    // ==================== 技能导入导出 ====================

    fun exportSkill(skillId: String): String? {
        val skill = getSkill(skillId) ?: return null
        return gson.toJson(skill)
    }

    fun exportAllSkills(): String {
        return gson.toJson(getAllSkills())
    }

    fun importSkill(json: String): Skill? {
        return try {
            val skill: Skill = gson.fromJson(json, Skill::class.java)
            // 生成新ID避免冲突
            val imported = skill.copy(
                id = UUID.randomUUID().toString(),
                isBuiltIn = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            skills.add(imported)
            persistAsync()
            Log.d(TAG, "Imported skill: ${imported.name}")
            imported
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import skill", e)
            null
        }
    }

    fun importSkills(jsonArray: String): Int {
        return try {
            val type = object : TypeToken<List<Skill>>() {}.type
            val skillsList: List<Skill> = gson.fromJson(jsonArray, type) ?: return 0
            var count = 0
            for (skill in skillsList) {
                val imported = skill.copy(
                    id = UUID.randomUUID().toString(),
                    isBuiltIn = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                skills.add(imported)
                count++
            }
            persistAsync()
            Log.d(TAG, "Imported $count skills")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import skills", e)
            0
        }
    }

    // ==================== 统计 ====================

    fun getStats(): Map<String, Any> {
        val categoryStats = skills.groupingBy { it.category }.eachCount()
        val enabledCount = skills.count { it.enabled }
        val builtInCount = skills.count { it.isBuiltIn }
        val totalRuns = skills.sumOf { it.runCount }
        val totalSuccess = skills.sumOf { it.successCount }
        val topSkills = skills.sortedByDescending { it.runCount }.take(5).map {
            mapOf("name" to it.name, "runs" to it.runCount, "success" to it.successCount)
        }

        return mapOf(
            "total" to skills.size,
            "enabled" to enabledCount,
            "builtIn" to builtInCount,
            "custom" to (skills.size - builtInCount),
            "totalRuns" to totalRuns,
            "totalSuccess" to totalSuccess,
            "successRate" to if (totalRuns > 0) totalSuccess.toFloat() / totalRuns else 0f,
            "categoryStats" to categoryStats,
            "topSkills" to topSkills
        )
    }

    fun addListener(listener: SkillListener) { listeners.add(listener) }
    fun removeListener(listener: SkillListener) { listeners.remove(listener) }

    // ==================== 持久化 ====================

    private fun persistAsync() {
        executor.execute {
            try {
                KVUtils.putString(STORAGE_KEY, gson.toJson(skills))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist skills", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromStorage() {
        try {
            val json = KVUtils.getString(STORAGE_KEY) ?: return
            val type = object : TypeToken<List<Skill>>() {}.type
            val loaded: List<Skill>? = gson.fromJson(json, type)
            loaded?.let { skills.clear(); skills.addAll(it) }
            Log.d(TAG, "Loaded ${skills.size} skills")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load skills", e)
        }
    }

    fun clearAll() {
        // 只清除非内置技能
        skills.removeAll { !it.isBuiltIn }
        persistAsync()
    }
}
