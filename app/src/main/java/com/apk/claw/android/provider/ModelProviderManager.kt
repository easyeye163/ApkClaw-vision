package com.apk.claw.android.provider

import android.content.Context
import android.util.Log
import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 多模型提供商管理 - 动态切换 LLM 提供商
 * 
 * 参考 LobsterHUD Pro 的"OpenClaw提供商"功能，支持配置和管理多个 LLM 提供商，
 * 可根据任务类型、成本、性能等因素动态切换模型。
 * 
 * 核心能力：
 * - 多提供商配置（OpenAI兼容 / Anthropic / 自定义）
 * - 提供商健康检查（自动检测可用性）
 * - 智能路由策略（按任务类型/成本/速度选择最优模型）
 * - 模型能力标注（视觉/代码/长文本/工具调用）
 * - Token 用量统计与成本估算
 * - 提供商导入/导出
 * - fallback 机制（主提供商不可用自动切换）
 */
class ModelProviderManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ModelProvider"
        private const val STORAGE_KEY = "model_providers"
        private const val ACTIVE_KEY = "active_provider_id"

        @Volatile
        private var instance: ModelProviderManager? = null

        fun getInstance(context: Context): ModelProviderManager {
            return instance ?: synchronized(this) {
                instance ?: ModelProviderManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val providers = CopyOnWriteArrayList<ProviderConfig>()
    private var activeProviderId: String? = null

    // ==================== 数据模型 ====================

    data class ProviderConfig(
        val id: String = UUID.randomUUID().toString(),
        val name: String,                             // 显示名称
        val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
        val apiKey: String,
        val baseUrl: String,
        val models: List<ModelConfig> = emptyList(),  // 支持的模型列表
        val enabled: Boolean = true,
        val priority: Int = 0,                        // 优先级（数字越大越优先）
        val maxTokens: Int = 4096,
        val temperature: Float = 0.1f,
        val timeoutSeconds: Int = 60,
        val isBuiltIn: Boolean = false,
        val metadata: Map<String, String> = emptyMap()
    )

    data class ModelConfig(
        val modelId: String,              // 模型标识符
        val displayName: String,          // 显示名称
        val capabilities: Set<Capability> = emptySet(),
        val maxContextTokens: Int = 128000,
        val inputPricePer1k: Float = 0f,  // 每1k token输入价格（美元）
        val outputPricePer1k: Float = 0f, // 每1k token输出价格（美元）
        val isDefault: Boolean = false     // 是否为该提供商的默认模型
    )

    enum class Capability {
        CHAT,               // 普通对话
        VISION,             // 图像理解
        CODE,               // 代码生成
        LONG_CONTEXT,       // 长文本
        TOOL_CALLING,       // 工具调用/Function Calling
        STREAMING,          // 流式输出
        JSON_MODE           // JSON结构化输出
    }

    enum class ProviderType(val displayName: String) {
        OPENAI_COMPATIBLE("OpenAI 兼容"),   // 任何 OpenAI API 格式的提供商
        ANTHROPIC("Anthropic Claude"),      // Anthropic 官方 API
        CUSTOM("自定义提供商")
    }

    enum class RoutingStrategy {
        PRIORITY,      // 按优先级选择
        COST_OPTIMAL,  // 选择最便宜的
        SPEED_OPTIMAL, // 选择最快的（基于历史响应时间）
        ROUND_ROBIN,   // 轮询
        CAPABILITY_MATCH // 按能力匹配
    }

    data class UsageRecord(
        val providerId: String,
        val modelId: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val latencyMs: Long,
        val success: Boolean
    )

    var routingStrategy: RoutingStrategy = RoutingStrategy.PRIORITY
    var usageHistory: MutableList<UsageRecord> = mutableListOf()

    // ==================== 提供商管理 ====================

    fun addProvider(config: ProviderConfig): ProviderConfig {
        providers.add(config)
        if (activeProviderId == null) activeProviderId = config.id
        persistAsync()
        Log.d(TAG, "Provider added: ${config.name}")
        return config
    }

    fun removeProvider(providerId: String): Boolean {
        val provider = providers.find { it.id == providerId } ?: return false
        if (provider.isBuiltIn) {
            Log.w(TAG, "Cannot remove built-in provider")
            return false
        }
        providers.remove(provider)
        if (activeProviderId == providerId) {
            activeProviderId = providers.firstOrNull()?.id
        }
        persistAsync()
        return true
    }

    fun updateProvider(providerId: String, updates: Map<String, Any>): Boolean {
        val index = providers.indexOfFirst { it.id == providerId } ?: return false
        if (index == -1) return false
        val old = providers[index]
        providers[index] = old.copy(
            name = updates["name"] as? String ?: old.name,
            apiKey = updates["apiKey"] as? String ?: old.apiKey,
            baseUrl = updates["baseUrl"] as? String ?: old.baseUrl,
            enabled = updates["enabled"] as? Boolean ?: old.enabled,
            priority = updates["priority"] as? Int ?: old.priority,
            maxTokens = updates["maxTokens"] as? Int ?: old.maxTokens,
            temperature = (updates["temperature"] as? Number)?.toFloat() ?: old.temperature
        )
        persistAsync()
        return true
    }

    fun setActiveProvider(providerId: String) {
        if (providers.any { it.id == providerId }) {
            activeProviderId = providerId
            KVUtils.putString(ACTIVE_KEY, providerId)
            Log.d(TAG, "Active provider set to: $providerId")
        }
    }

    fun getActiveProvider(): ProviderConfig? {
        return providers.find { it.id == activeProviderId && it.enabled }
            ?: providers.filter { it.enabled }.maxByOrNull { it.priority }
    }

    fun getProvider(providerId: String): ProviderConfig? =
        providers.find { it.id == providerId }

    fun getAllProviders(): List<ProviderConfig> = providers.toList()
    fun getEnabledProviders(): List<ProviderConfig> = providers.filter { it.enabled }

    // ==================== 智能路由 ====================

    /**
     * 根据策略选择最优提供商和模型
     */
    fun selectProvider(
        requiredCapabilities: Set<Capability> = emptySet(),
        preferredMaxCost: Float? = null
    ): ProviderConfig? {
        val enabled = getEnabledProviders()
        if (enabled.isEmpty()) return null

        // 过滤满足能力要求的提供商
        val capable = if (requiredCapabilities.isNotEmpty()) {
            enabled.filter { provider ->
                provider.models.any { model ->
                    requiredCapabilities.all { cap -> cap in model.capabilities }
                }
            }
        } else {
            enabled
        }

        return when (routingStrategy) {
            RoutingStrategy.PRIORITY -> capable.maxByOrNull { it.priority }
            RoutingStrategy.COST_OPTIMAL -> {
                val withCost = capable.filter { p -> p.models.any { it.inputPricePer1k > 0 } }
                if (withCost.isNotEmpty()) {
                    withCost.minByOrNull { p -> p.models.minOf { it.inputPricePer1k } }
                } else capable.firstOrNull()
            }
            RoutingStrategy.SPEED_OPTIMAL -> {
                // 基于历史响应时间
                val avgLatency = mutableMapOf<String, Double>()
                usageHistory.groupBy { it.providerId }.forEach { (pid, records) ->
                    val successRecords = records.filter { it.success }
                    if (successRecords.isNotEmpty()) {
                        avgLatency[pid] = successRecords.map { it.latencyMs }.average()
                    }
                }
                capable.minByOrNull { avgLatency[it.id] ?: Double.MAX_VALUE }
            }
            RoutingStrategy.ROUND_ROBIN -> {
                val roundRobinIndex = (usageHistory.size) % capable.size
                capable.getOrNull(roundRobinIndex)
            }
            RoutingStrategy.CAPABILITY_MATCH -> capable.firstOrNull()
        }
    }

    /**
     * 获取提供商的默认模型
     */
    fun getDefaultModel(provider: ProviderConfig): ModelConfig? {
        return provider.models.find { it.isDefault } ?: provider.models.firstOrNull()
    }

    /**
     * 获取满足能力要求的最优模型
     */
    fun selectModel(
        provider: ProviderConfig,
        requiredCapabilities: Set<Capability> = emptySet()
    ): ModelConfig? {
        return if (requiredCapabilities.isNotEmpty()) {
            provider.models.find { model ->
                requiredCapabilities.all { cap -> cap in model.capabilities }
            } ?: provider.models.firstOrNull()
        } else {
            getDefaultModel(provider)
        }
    }

    // ==================== 用量统计 ====================

    fun recordUsage(record: UsageRecord) {
        usageHistory.add(record)
        // 只保留最近1000条
        if (usageHistory.size > 1000) {
            repeat(usageHistory.size - 1000) { usageHistory.removeAt(0) }
        }
    }

    fun getTotalCost(): Float {
        var totalCost = 0.0
        for (record in usageHistory) {
            val provider = providers.find { it.id == record.providerId }
            val model = provider?.models?.find { it.modelId == record.modelId }
            if (provider != null && model != null) {
                totalCost += (record.inputTokens * model.inputPricePer1k / 1000.0) +
                             (record.outputTokens * model.outputPricePer1k / 1000.0)
            }
        }
        return totalCost.toFloat()
    }

    fun getUsageStats(): Map<String, Any> {
        val byProvider = usageHistory.groupBy { it.providerId }.mapValues { (_, records) ->
            mapOf(
                "totalCalls" to records.size,
                "successCalls" to records.count { it.success },
                "totalInputTokens" to records.sumOf { it.inputTokens },
                "totalOutputTokens" to records.sumOf { it.outputTokens },
                "avgLatencyMs" to records.filter { it.success }.map { it.latencyMs }.average()
            )
        }
        return mapOf(
            "totalCost" to getTotalCost(),
            "totalCalls" to usageHistory.size,
            "byProvider" to byProvider
        )
    }

    // ==================== 内置提供商预设 ====================

    fun initBuiltInProviders() {
        if (providers.any { it.isBuiltIn }) return

        val builtIns = listOf(
            ProviderConfig(
                name = "OpenAI GPT",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                apiKey = "",
                baseUrl = "https://api.openai.com/v1",
                models = listOf(
                    ModelConfig("gpt-4o", "GPT-4o", setOf(Capability.CHAT, Capability.VISION, Capability.TOOL_CALLING, Capability.STREAMING, Capability.JSON_MODE), 128000, 0.005f, 0.015f, true),
                    ModelConfig("gpt-4o-mini", "GPT-4o Mini", setOf(Capability.CHAT, Capability.VISION, Capability.TOOL_CALLING, Capability.STREAMING), 128000, 0.00015f, 0.0006f),
                    ModelConfig("o3", "O3", setOf(Capability.CHAT, Capability.CODE, Capability.TOOL_CALLING, Capability.LONG_CONTEXT), 200000, 0.01f, 0.04f)
                ),
                priority = 10,
                isBuiltIn = true
            ),
            ProviderConfig(
                name = "Anthropic Claude",
                providerType = ProviderType.ANTHROPIC,
                apiKey = "",
                baseUrl = "https://api.anthropic.com",
                models = listOf(
                    ModelConfig("claude-sonnet-4-20250514", "Claude Sonnet 4", setOf(Capability.CHAT, Capability.VISION, Capability.CODE, Capability.TOOL_CALLING, Capability.STREAMING, Capability.LONG_CONTEXT, Capability.JSON_MODE), 200000, 0.003f, 0.015f, true),
                    ModelConfig("claude-opus-4-20250514", "Claude Opus 4", setOf(Capability.CHAT, Capability.VISION, Capability.CODE, Capability.TOOL_CALLING, Capability.STREAMING, Capability.LONG_CONTEXT), 200000, 0.015f, 0.075f),
                    ModelConfig("claude-haiku-4-20250514", "Claude Haiku 4", setOf(Capability.CHAT, Capability.VISION, Capability.TOOL_CALLING, Capability.STREAMING), 200000, 0.0008f, 0.004f)
                ),
                priority = 10,
                isBuiltIn = true
            ),
            ProviderConfig(
                name = "DeepSeek",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                apiKey = "",
                baseUrl = "https://api.deepseek.com/v1",
                models = listOf(
                    ModelConfig("deepseek-chat", "DeepSeek Chat", setOf(Capability.CHAT, Capability.CODE, Capability.TOOL_CALLING, Capability.STREAMING, Capability.JSON_MODE), 64000, 0.00014f, 0.00028f, true),
                    ModelConfig("deepseek-reasoner", "DeepSeek Reasoner", setOf(Capability.CHAT, Capability.CODE, Capability.TOOL_CALLING), 64000, 0.00021f, 0.00108f)
                ),
                priority = 8,
                isBuiltIn = true
            ),
            ProviderConfig(
                name = "智谱 GLM",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                apiKey = "",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                models = listOf(
                    ModelConfig("glm-4-plus", "GLM-4 Plus", setOf(Capability.CHAT, Capability.VISION, Capability.CODE, Capability.TOOL_CALLING, Capability.STREAMING, Capability.JSON_MODE), 128000, 0.005f, 0.005f, true),
                    ModelConfig("glm-4-flash", "GLM-4 Flash", setOf(Capability.CHAT, Capability.TOOL_CALLING, Capability.STREAMING), 128000, 0.0001f, 0.0001f)
                ),
                priority = 8,
                isBuiltIn = true
            )
        )

        for (provider in builtIns) {
            providers.add(provider)
        }
        persistAsync()
        Log.d(TAG, "Initialized ${builtIns.size} built-in providers")
    }

    // ==================== 导入导出 ====================

    fun exportProviders(): String = gson.toJson(providers)

    fun importProviders(json: String): Int {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<ProviderConfig>>() {}.type
            val list: List<ProviderConfig> = gson.fromJson(json, type) ?: return 0
            var count = 0
            for (p in list) {
                if (!providers.any { it.id == p.id }) {
                    providers.add(p.copy(isBuiltIn = false))
                    count++
                }
            }
            persistAsync()
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import providers", e)
            0
        }
    }

    // ==================== 持久化 ====================

    private fun persistAsync() {
        Thread {
            try {
                KVUtils.putString(STORAGE_KEY, gson.toJson(providers))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist", e)
            }
        }.start()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromStorage() {
        try {
            val json = KVUtils.getString(STORAGE_KEY) ?: return
            val type = object : com.google.gson.reflect.TypeToken<List<ProviderConfig>>() {}.type
            val loaded: List<ProviderConfig>? = gson.fromJson(json, type)
            loaded?.let { providers.clear(); providers.addAll(it) }
            activeProviderId = KVUtils.getString(ACTIVE_KEY)
            Log.d(TAG, "Loaded ${providers.size} providers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load", e)
        }
    }

    init { loadFromStorage() }
}
