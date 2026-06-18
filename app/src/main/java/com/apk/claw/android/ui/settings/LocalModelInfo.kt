package com.apk.claw.android.ui.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 本地模型信息数据类
 *
 * 包含 GGUF 模型的元数据（名称、文件、下载源等），
 * 用于模型选择列表和下载管理。
 */
data class LocalModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val ggufFileName: String,
    val mmprojFileName: String? = null,
    val hfRepo: String? = null,
    val msRepo: String? = null,
    val directGgufUrl: String? = null,
    val directMmprojUrl: String? = null,
    val modelSize: String,
    val isCustom: Boolean = false
) {
    /** 模型在本地存储的目录 */
    fun modelDir(baseDir: File): File = File(baseDir, "local_models/$id")

    /** GGUF 模型文件路径 */
    fun ggufPath(baseDir: File): File = File(modelDir(baseDir), ggufFileName)

    /** mmproj 文件路径（仅多模态模型） */
    fun mmprojPath(baseDir: File): File? {
        if (mmprojFileName != null) return File(modelDir(baseDir), mmprojFileName)
        return null
    }

    /** GGUF 文件是否已下载 */
    fun isDownloaded(baseDir: File): Boolean = ggufPath(baseDir).exists()

    /** 已下载文件的总大小 */
    fun downloadedSize(baseDir: File): Long {
        val ggufSize = if (ggufPath(baseDir).exists()) ggufPath(baseDir).length() else 0L
        val mmproj = mmprojPath(baseDir)
        val mmprojSize = if (mmproj != null && mmproj.exists()) mmproj.length() else 0L
        return ggufSize + mmprojSize
    }

    /** 序列化为 JSON */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("description", description)
        put("ggufFileName", ggufFileName)
        put("mmprojFileName", mmprojFileName)
        put("hfRepo", hfRepo)
        put("msRepo", msRepo)
        put("directGgufUrl", directGgufUrl)
        put("directMmprojUrl", directMmprojUrl)
        put("modelSize", modelSize)
        put("isCustom", true)
    }

    companion object {
        /** 预设的可用模型列表 */
        val BUILTIN_MODELS: List<LocalModelInfo> = listOf(
            LocalModelInfo(
                id = "qwen2.5-1.5b-q4",
                displayName = "Qwen2.5-1.5B (Q4_K_M)",
                description = "通义千问轻量级模型，纯文本对话 (1.5B)",
                ggufFileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                hfRepo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
                msRepo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
                modelSize = "~1.0 GB"
            ),
            LocalModelInfo(
                id = "qwen2.5-3b-q4",
                displayName = "Qwen2.5-3B (Q4_K_M)",
                description = "通义千问轻量级模型，纯文本对话 (3B)",
                ggufFileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
                hfRepo = "Qwen/Qwen2.5-3B-Instruct-GGUF",
                msRepo = "Qwen/Qwen2.5-3B-Instruct-GGUF",
                modelSize = "~1.9 GB"
            ),
            LocalModelInfo(
                id = "minicpm-v-4.6-q4",
                displayName = "MiniCPM-V-4.6 (Q4_K_M)",
                description = "面壁智能多模态模型，支持图文理解 (1.2B)",
                ggufFileName = "MiniCPM-V-4_6-Q4_K_M.gguf",
                mmprojFileName = "mmproj-model-f16.gguf",
                hfRepo = "openbmb/MiniCPM-V-4.6-gguf",
                msRepo = "OpenBMB/MiniCPM-V-4.6-gguf",
                modelSize = "~3.5 GB"
            ),
            LocalModelInfo(
                id = "minicpm-v-4-q4",
                displayName = "MiniCPM-V-4 (Q4_K_M)",
                description = "面壁智能多模态模型，支持图文理解 (4.1B)",
                ggufFileName = "ggml-model-Q4_K_M.gguf",
                mmprojFileName = "mmproj-model-f16.gguf",
                hfRepo = "openbmb/MiniCPM-V-4-gguf",
                msRepo = "OpenBMB/MiniCPM-V-4-gguf",
                modelSize = "~3.5 GB"
            ),
            LocalModelInfo(
                id = "llama-3.2-1b-q4",
                displayName = "Llama-3.2-1B (Q4_K_M)",
                description = "Meta 轻量级模型，纯文本对话 (1B)",
                ggufFileName = "llama-3.2-1b-instruct-q4_k_m.gguf",
                hfRepo = "hugging-quants/Llama-3.2-1B-Instruct-GGUF",
                msRepo = "AI-ModelScope/Llama-3.2-1B-Instruct-GGUF",
                modelSize = "~0.8 GB"
            ),
            LocalModelInfo(
                id = "llama-3.2-3b-q4",
                displayName = "Llama-3.2-3B (Q4_K_M)",
                description = "Meta 轻量级模型，纯文本对话 (3B)",
                ggufFileName = "llama-3.2-3b-instruct-Q4_K_M.gguf",
                hfRepo = "hugging-quants/Llama-3.2-3B-Instruct-GGUF",
                msRepo = "AI-ModelScope/Llama-3.2-3B-Instruct-GGUF",
                modelSize = "~1.9 GB"
            )
        )

        /** 默认选中模型 */
        val DEFAULT_MODEL: LocalModelInfo = BUILTIN_MODELS.first()

        private const val PREFS_CUSTOM_MODELS = "custom_local_models"
        private const val KEY_CUSTOM_MODELS_LIST = "custom_models_json"

        /** 从 JSON 反序列化 */
        fun fromJson(json: JSONObject): LocalModelInfo = LocalModelInfo(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            description = json.optString("description", ""),
            ggufFileName = json.getString("ggufFileName"),
            mmprojFileName = json.optString("mmprojFileName", null).ifBlank { null },
            hfRepo = json.optString("hfRepo", null).ifBlank { null },
            msRepo = json.optString("msRepo", null).ifBlank { null },
            directGgufUrl = json.optString("directGgufUrl", null).ifBlank { null },
            directMmprojUrl = json.optString("directMmprojUrl", null).ifBlank { null },
            modelSize = json.getString("modelSize"),
            isCustom = true
        )

        /** 保存自定义模型列表到 SharedPreferences */
        fun saveCustomModels(context: Context, models: List<LocalModelInfo>) {
            val arr = JSONArray()
            models.filter { it.isCustom }.forEach { arr.put(it.toJson()) }
            context.getSharedPreferences(PREFS_CUSTOM_MODELS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM_MODELS_LIST, arr.toString()).apply()
        }

        /** 读取自定义模型列表 */
        fun loadCustomModels(context: Context): List<LocalModelInfo> {
            val json = context.getSharedPreferences(PREFS_CUSTOM_MODELS, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_MODELS_LIST, null) ?: return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) { emptyList() }
        }

        /** 获取完整模型列表（预设 + 自定义） */
        fun getAllModels(context: Context): List<LocalModelInfo> {
            return BUILTIN_MODELS + loadCustomModels(context)
        }

        /** 添加自定义模型 */
        fun addCustomModel(context: Context, model: LocalModelInfo) {
            val existing = loadCustomModels(context).toMutableList()
            // Replace if same id exists
            val idx = existing.indexOfFirst { it.id == model.id }
            if (idx >= 0) existing[idx] = model else existing.add(model)
            saveCustomModels(context, existing)
        }

        /** 删除自定义模型 */
        fun removeCustomModel(context: Context, modelId: String) {
            val filtered = loadCustomModels(context).filter { it.id != modelId }
            saveCustomModels(context, filtered)
        }
    }
}