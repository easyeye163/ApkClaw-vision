package com.apk.claw.android.ui.settings

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
    val modelSize: String
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

    /** 构建所有可用的下载 URL 列表 */
    fun buildDownloadUrls(): List<Pair<String, String>> {
        val urls = mutableListOf<Pair<String, String>>()
        hfRepo?.let { hf ->
            urls.add("HuggingFace" to "https://huggingface.co/$hf/resolve/main/$ggufFileName")
            mmprojFileName?.let { mm ->
                urls.add("HuggingFace-mmproj" to "https://huggingface.co/$hf/resolve/main/$mm")
            }
        }
        msRepo?.let { ms ->
            urls.add("ModelScope" to "https://modelscope.cn/models/$ms/resolve/master/$ggufFileName")
            mmprojFileName?.let { mm ->
                urls.add("ModelScope-mmproj" to "https://modelscope.cn/models/$ms/resolve/master/$mm")
            }
        }
        directGgufUrl?.let { urls.add("Direct" to it) }
        directMmprojUrl?.let { urls.add("Direct-mmproj" to it) }
        return urls
    }

    companion object {
        /** 预设的可用模型列表 */
        val AVAILABLE_MODELS: List<LocalModelInfo> = listOf(
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
                ggufFileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                hfRepo = "hugging-quants/Llama-3.2-3B-Instruct-GGUF",
                msRepo = "AI-ModelScope/Llama-3.2-3B-Instruct-GGUF",
                modelSize = "~1.9 GB"
            )
        )

        /** 默认选中模型 */
        val DEFAULT_MODEL: LocalModelInfo = AVAILABLE_MODELS.first()
    }
}
