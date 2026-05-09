package com.apk.claw.android.brain

import android.content.Context
import android.util.Log
import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.ln

/**
 * 本地大脑 - 知识库与记忆管理系统
 * 
 * 参考 LobsterHUD Pro 的"本地大脑"功能，为 Agent 提供持久化知识存储和语义检索能力。
 * 支持按命名空间组织知识条目，并通过 TF-IDF 简易语义相似度进行检索。
 * 
 * 核心能力：
 * - 知识条目 CRUD（增删改查）
 * - 命名空间隔离（skill/memory/preference/context）
 * - 语义关键词检索（TF-IDF 相似度）
 * - 任务上下文注入（自动将相关记忆注入 Agent System Prompt）
 * - 对话历史摘要管理
 * - 持久化存储（MMKV）
 */
class LocalBrain private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocalBrain"
        private const val STORAGE_KEY = "local_brain_entries"
        private const val SUMMARY_KEY = "local_brain_summaries"
        private const val CONFIG_KEY = "local_brain_config"

        @Volatile
        private var instance: LocalBrain? = null

        fun getInstance(context: Context): LocalBrain {
            return instance ?: synchronized(this) {
                instance ?: LocalBrain(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor()

    // 内存缓存
    private val entriesCache = CopyOnWriteArrayList<KnowledgeEntry>()
    private val invertedIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val docTermFreq = ConcurrentHashMap<String, Int>()
    private var totalDocs = 0

    // 配置
    var config: BrainConfig
        private set

    data class BrainConfig(
        val maxEntries: Int = 5000,
        val maxContextInjection: Int = 10,
        val autoExtractEnabled: Boolean = true,
        val similarityThreshold: Float = 0.15f,
        val summaryMaxTokens: Int = 500,
        val namespacesEnabled: List<String> = listOf("memory", "preference", "context", "skill")
    )

    data class KnowledgeEntry(
        val id: String = UUID.randomUUID().toString(),
        val namespace: String = "memory",
        val title: String = "",
        val content: String,
        val tags: List<String> = emptyList(),
        val source: String = "manual", // manual / auto_extract / skill / task
        val importance: Float = 0.5f, // 0.0 ~ 1.0
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val accessCount: Int = 0,
        val metadata: Map<String, String> = emptyMap()
    )

    data class ConversationSummary(
        val id: String = UUID.randomUUID().toString(),
        val channelType: String,
        val userId: String,
        val startTime: Long,
        val endTime: Long,
        val taskDescription: String,
        val summary: String,
        val keyDecisions: List<String> = emptyList(),
        val toolUsageStats: Map<String, Int> = emptyMap(),
        val success: Boolean = true
    )

    data class SearchResult(
        val entry: KnowledgeEntry,
        val score: Float
    )

    init {
        config = KVUtils.getString(CONFIG_KEY)?.let {
            try { gson.fromJson(it, BrainConfig::class.java) } catch (_: Exception) { null }
        } ?: BrainConfig()
        loadFromStorage()
    }

    // ==================== 知识条目管理 ====================

    /**
     * 添加知识条目
     */
    fun addEntry(
        content: String,
        namespace: String = "memory",
        title: String = "",
        tags: List<String> = emptyList(),
        source: String = "manual",
        importance: Float = 0.5f,
        metadata: Map<String, String> = emptyMap()
    ): KnowledgeEntry {
        val entry = KnowledgeEntry(
            namespace = namespace,
            title = title.ifEmpty { content.take(30) + "..." },
            content = content,
            tags = tags,
            source = source,
            importance = importance.coerceIn(0f, 1f),
            metadata = metadata
        )
        entriesCache.add(entry)
        indexEntry(entry)
        evictIfNeeded()
        persistEntries()
        Log.d(TAG, "Entry added: [${entry.namespace}] ${entry.title}")
        return entry
    }

    /**
     * 更新知识条目
     */
    fun updateEntry(entryId: String, content: String? = null, tags: List<String>? = null, importance: Float? = null): Boolean {
        val index = entriesCache.indexOfFirst { it.id == entryId }
        if (index == -1) return false

        val old = entriesCache[index]
        // 从索引中移除旧条目
        removeFromIndex(old)

        val updated = old.copy(
            content = content ?: old.content,
            tags = tags ?: old.tags,
            importance = importance?.coerceIn(0f, 1f) ?: old.importance,
            updatedAt = System.currentTimeMillis()
        )
        entriesCache[index] = updated
        indexEntry(updated)
        persistEntries()
        return true
    }

    /**
     * 删除知识条目
     */
    fun deleteEntry(entryId: String): Boolean {
        val entry = entriesCache.find { it.id == entryId } ?: return false
        entriesCache.remove(entry)
        removeFromIndex(entry)
        persistEntries()
        return true
    }

    /**
     * 根据ID获取条目
     */
    fun getEntry(entryId: String): KnowledgeEntry? {
        return entriesCache.find { it.id == entryId }
    }

    /**
     * 按命名空间获取所有条目
     */
    fun getEntriesByNamespace(namespace: String): List<KnowledgeEntry> {
        return entriesCache.filter { it.namespace == namespace }.sortedByDescending { it.updatedAt }
    }

    /**
     * 按标签获取条目
     */
    fun getEntriesByTag(tag: String): List<KnowledgeEntry> {
        return entriesCache.filter { tag in it.tags }.sortedByDescending { it.updatedAt }
    }

    /**
     * 获取全部条目
     */
    fun getAllEntries(): List<KnowledgeEntry> = entriesCache.toList()

    /**
     * 获取统计信息
     */
    fun getStats(): Map<String, Any> {
        val namespaceStats = entriesCache.groupingBy { it.namespace }.eachCount()
        val sourceStats = entriesCache.groupingBy { it.source }.eachCount()
        return mapOf(
            "totalEntries" to entriesCache.size,
            "totalIndexTerms" to invertedIndex.size,
            "namespaceStats" to namespaceStats,
            "sourceStats" to sourceStats
        )
    }

    // ==================== 语义检索 ====================

    /**
     * 语义搜索 - 基于关键词的 TF-IDF 相似度
     * 
     * @param query 搜索查询
     * @param namespace 限定命名空间（null 表示全部）
     * @param limit 返回结果数量
     * @return 按相似度排序的搜索结果
     */
    fun search(query: String, namespace: String? = null, limit: Int = 10): List<SearchResult> {
        val queryTerms = tokenize(query).filter { it.length > 1 }
        if (queryTerms.isEmpty()) return emptyList()

        val candidates = if (namespace != null) {
            entriesCache.filter { it.namespace == namespace }
        } else {
            entriesCache
        }

        val scored = candidates.map { entry ->
            val score = computeSimilarity(queryTerms, entry)
            SearchResult(entry, score)
        }.filter { it.score >= config.similarityThreshold }

        return scored.sortedByDescending { it.score }
            .take(limit)
            .onEach { result ->
                // 更新访问计数
                val idx = entriesCache.indexOfFirst { it.id == result.entry.id }
                if (idx >= 0) {
                    entriesCache[idx] = entriesCache[idx].copy(accessCount = entriesCache[idx].accessCount + 1)
                }
            }
    }

    /**
     * 计算查询与条目之间的相似度
     */
    private fun computeSimilarity(queryTerms: List<String>, entry: KnowledgeEntry): Float {
        val docTerms = tokenize("${entry.title} ${entry.content} ${entry.tags.joinToString(" ")}")
            .filter { it.length > 1 }

        if (docTerms.isEmpty() || queryTerms.isEmpty()) return 0f

        // 计算 TF-IDF
        val queryTf = queryTerms.groupingBy { it }.eachCount().mapValues { (_, count) ->
            count.toFloat() / queryTerms.size
        }

        val docTf = docTerms.groupingBy { it }.eachCount().mapValues { (_, count) ->
            count.toFloat() / docTerms.size
        }

        // IDF
        val idfMap = mutableMapOf<String, Float>()
        val allTerms = (queryTerms + docTerms).toSet()
        for (term in allTerms) {
            val docsWithTerm = invertedIndex[term]?.size ?: 0
            idfMap[term] = if (docsWithTerm > 0) {
                (ln((totalDocs + 1.0) / (docsWithTerm + 1.0)) + 1).toFloat()
            } else {
                1f
            }
        }

        // 余弦相似度
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (term in queryTerms.toSet()) {
            val queryWeight = queryTf.getOrDefault(term, 0f) * idfMap.getOrDefault(term, 1f)
            val docWeight = docTf.getOrDefault(term, 0f) * idfMap.getOrDefault(term, 1f)
            dotProduct += queryWeight * docWeight
            normA += queryWeight * queryWeight
        }
        for (term in docTerms.toSet()) {
            val docWeight = docTf.getOrDefault(term, 0f) * idfMap.getOrDefault(term, 1f)
            normB += docWeight * docWeight
        }

        val norm = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (norm > 0) dotProduct / norm else 0f
    }

    // ==================== 上下文注入 ====================

    /**
     * 根据当前任务上下文，检索相关记忆并生成上下文注入文本
     * 
     * @param taskDescription 当前任务描述
     * @param namespace 限定命名空间
     * @return 格式化的上下文文本，可直接拼接到 System Prompt
     */
    fun buildContextInjection(taskDescription: String, namespace: String? = null): String {
        val results = search(taskDescription, namespace, config.maxContextInjection)
        if (results.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("[本地大脑 · 相关记忆]\n")

        for ((i, result) in results.withIndex()) {
            val entry = result.entry
            sb.append("${i + 1}. [${entry.namespace}] ${entry.title}\n")
            sb.append("   ${entry.content}\n")
            if (entry.tags.isNotEmpty()) {
                sb.append("   标签: ${entry.tags.joinToString(", ")}\n")
            }
            sb.append("   (相关度: ${"%.0f".format(result.score * 100)}%)\n\n")
        }

        return sb.toString()
    }

    /**
     * 获取用户偏好信息
     */
    fun getUserPreferences(): List<KnowledgeEntry> {
        return entriesCache.filter { it.namespace == "preference" }
            .sortedByDescending { it.importance }
    }

    // ==================== 对话摘要管理 ====================

    /**
     * 保存对话摘要
     */
    fun saveSummary(summary: ConversationSummary) {
        val summaries = loadSummaries().toMutableList()
        summaries.add(summary)
        // 只保留最近100条
        if (summaries.size > 100) {
            repeat(summaries.size - 100) { summaries.removeAt(0) }
        }
        KVUtils.putString(SUMMARY_KEY, gson.toJson(summaries))

        // 自动提取关键信息为知识条目
        if (config.autoExtractEnabled && summary.success) {
            extractKnowledgeFromSummary(summary)
        }
    }

    /**
     * 获取最近的对话摘要
     */
    fun getRecentSummaries(limit: Int = 10): List<ConversationSummary> {
        return loadSummaries().takeLast(limit).reversed()
    }

    /**
     * 从对话摘要中自动提取知识
     */
    private fun extractKnowledgeFromSummary(summary: ConversationSummary) {
        // 提取关键决策作为知识
        for (decision in summary.keyDecisions) {
            addEntry(
                content = decision,
                namespace = "memory",
                tags = listOf("auto", "decision", summary.channelType),
                source = "auto_extract",
                importance = 0.6f,
                metadata = mapOf("summaryId" to summary.id, "task" to summary.taskDescription)
            )
        }

        // 提取任务描述作为上下文知识
        if (summary.summary.isNotBlank()) {
            addEntry(
                content = "任务: ${summary.taskDescription}\n结果: ${summary.summary}",
                namespace = "context",
                tags = listOf("auto", "task_result", summary.channelType),
                source = "auto_extract",
                importance = 0.4f,
                metadata = mapOf("summaryId" to summary.id)
            )
        }
    }

    // ==================== 索引管理 ====================

    private fun tokenize(text: String): List<String> {
        // 中文按字符分词，英文按空格分词，统一小写
        val tokens = mutableListOf<String>()
        val englishWords = text.lowercase()
            .replace(Regex("[^a-z0-9\\s\\u4e00-\\u9fff]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        for (word in englishWords) {
            if (word.all { it in 'a'..'z' || it in '0'..'9' }) {
                if (word.length > 1) tokens.add(word)
            } else {
                // 中文字符，生成 bigram
                val chars = word.filter { it in '\u4e00'..'\u9fff' }.toList()
                for (i in chars.indices) {
                    tokens.add(chars[i].toString())
                    if (i < chars.size - 1) {
                        tokens.add("${chars[i]}${chars[i + 1]}")
                    }
                    if (i < chars.size - 2) {
                        tokens.add("${chars[i]}${chars[i + 1]}${chars[i + 2]}")
                    }
                }
            }
        }
        return tokens
    }

    private fun indexEntry(entry: KnowledgeEntry) {
        val text = "${entry.title} ${entry.content} ${entry.tags.joinToString(" ")}"
        val terms = tokenize(text)
        for (term in terms) {
            invertedIndex.getOrPut(term) { mutableSetOf() }.add(entry.id)
        }
        docTermFreq[entry.id] = terms.size
        totalDocs = entriesCache.size
    }

    private fun removeFromIndex(entry: KnowledgeEntry) {
        val text = "${entry.title} ${entry.content} ${entry.tags.joinToString(" ")}"
        val terms = tokenize(text)
        for (term in terms) {
            invertedIndex[term]?.remove(entry.id)
        }
        docTermFreq.remove(entry.id)
        totalDocs = entriesCache.size
    }

    // ==================== 持久化 ====================

    private fun persistEntries() {
        executor.execute {
            try {
                val json = gson.toJson(entriesCache)
                KVUtils.putString(STORAGE_KEY, json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist entries", e)
            }
        }
    }

    private fun loadFromStorage() {
        try {
            val json = KVUtils.getString(STORAGE_KEY) ?: return
            val type = object : TypeToken<List<KnowledgeEntry>>() {}.type
            val loaded: List<KnowledgeEntry> = gson.fromJson(json, type) ?: return
            entriesCache.clear()
            entriesCache.addAll(loaded)
            // 重建索引
            rebuildIndex()
            Log.d(TAG, "Loaded ${entriesCache.size} entries from storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load entries", e)
        }
    }

    private fun loadSummaries(): List<ConversationSummary> {
        return try {
            val json = KVUtils.getString(SUMMARY_KEY) ?: return emptyList()
            val type = object : TypeToken<List<ConversationSummary>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load summaries", e)
            emptyList()
        }
    }

    private fun rebuildIndex() {
        invertedIndex.clear()
        docTermFreq.clear()
        for (entry in entriesCache) {
            indexEntry(entry)
        }
    }

    private fun evictIfNeeded() {
        if (entriesCache.size <= config.maxEntries) return
        // 按重要性 + 访问计数 + 时间衰减 排序，淘汰最不重要的
        val now = System.currentTimeMillis()
        val sorted = entriesCache.sortedBy { entry ->
            val ageDecay = (now - entry.updatedAt).toFloat() / (30 * 24 * 3600 * 1000f)
            val score = entry.importance * 2 + entry.accessCount * 0.1f - ageDecay
            score
        }
        val toRemove = sorted.take(sorted.size - config.maxEntries)
        for (entry in toRemove) {
            entriesCache.remove(entry)
            removeFromIndex(entry)
        }
        Log.d(TAG, "Evicted ${toRemove.size} entries, remaining ${entriesCache.size}")
    }

    /**
     * 清空所有数据
     */
    fun clearAll() {
        entriesCache.clear()
        invertedIndex.clear()
        docTermFreq.clear()
        totalDocs = 0
        KVUtils.remove(STORAGE_KEY)
        KVUtils.remove(SUMMARY_KEY)
    }

    /**
     * 按命名空间清空
     */
    fun clearNamespace(namespace: String) {
        val toRemove = entriesCache.filter { it.namespace == namespace }
        for (entry in toRemove) {
            entriesCache.remove(entry)
            removeFromIndex(entry)
        }
        persistEntries()
    }
}
