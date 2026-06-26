package com.apk.claw.android.local.llm

import android.util.Log
import java.io.File
import kotlin.text.Regex

/**
 * CPU 特性检测工具
 *
 * 读取 /proc/cpuinfo 中的 Features 行，检测 ARM CPU 是否支持
 * bf16、dotprod (asimddp)、fp16 (fphp)、i8mm、sve2 等指令集扩展，
 * 以便选择最优的 ggml-cpu 变体。
 */
object CpuFeatures {

    private const val TAG = "CpuFeatures"

    private val features: Set<String> by lazy { readFeatures() }

    val hasDotprod: Boolean get() = features.contains("asimddp")
    val hasFp16: Boolean get() = features.contains("fphp")
    val hasI8mm: Boolean get() = features.contains("i8mm")
    val hasBf16: Boolean get() = features.contains("bf16")
    val hasSve2: Boolean get() = features.contains("sve2")

    /**
     * 选择最优的 ggml-cpu 动态库变体。
     *
     * 当前仅支持 v86（需要 i8mm + bf16），其余情况返回 null 表示使用 baseline。
     */
    fun bestGgmlCpuVariant(): String? {
        return if (hasI8mm && hasBf16) "v86" else null
    }

    /**
     * 生成 CPU 特性摘要文本（用于日志）
     */
    fun summary(): String {
        val sb = StringBuilder()
        sb.appendLine("CPU features: ${features.joinToString()}")
        sb.appendLine("dotprod=$hasDotprod fp16=$hasFp16 i8mm=$hasI8mm bf16=$hasBf16 sve2=$hasSve2")
        val variant = bestGgmlCpuVariant() ?: "baseline"
        sb.append("Selected ggml-cpu variant: $variant")
        return sb.toString()
    }

    /**
     * 读取 /proc/cpuinfo 中的 Features 行并解析为 Set
     */
    private fun readFeatures(): Set<String> {
        return try {
            val lines = File("/proc/cpuinfo").readLines()
            val featureLines = lines.filter { it.startsWith("Features", ignoreCase = true) }
            val featuresLine = featureLines.firstOrNull()
            if (featuresLine != null) {
                val afterColon = featuresLine.substringAfter(":", "").trim()
                afterColon.split(Regex("\\s+")).toSet()
            } else {
                Log.w(TAG, "No Features line in /proc/cpuinfo")
                emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read /proc/cpuinfo", e)
            emptySet()
        }
    }
}
