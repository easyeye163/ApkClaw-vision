package com.apk.claw.android.agent

import com.apk.claw.android.utils.XLog
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured JSON Lines event logger for agent execution.
 * Each event is written as a single JSON line to a .jsonl file in the cache directory.
 */
class AgentEventLogger(private val cacheDir: File) {

    companion object {
        private const val TAG = "AgentEventLogger"
        private const val EVENT_DIR_NAME = "agent_events"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private var writer: BufferedWriter? = null
    private var eventFile: File? = null

    /**
     * Initialize the logger by creating the event file and opening a writer.
     */
    fun start() {
        try {
            val eventDir = File(cacheDir, EVENT_DIR_NAME)
            if (!eventDir.exists()) {
                eventDir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            eventFile = File(eventDir, "${timestamp}.jsonl")
            writer = BufferedWriter(FileWriter(eventFile!!, true))
            XLog.i(TAG, "Event logger started: ${eventFile!!.absolutePath}")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to start event logger", e)
        }
    }

    private fun currentTimestamp(): String = dateFormat.format(Date())

    private fun logEvent(eventMap: MutableMap<String, Any>) {
        try {
            val w = writer ?: return
            val json = buildJsonString(eventMap)
            w.write(json)
            w.newLine()
            w.flush()
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to log event", e)
        }
    }

    /**
     * Simple JSON string builder to avoid dependency on Gson in this logger.
     */
    private fun buildJsonString(map: Map<String, Any>): String {
        val sb = StringBuilder("{")
        val entries = map.entries.toList()
        for ((i, entry) in entries.withIndex()) {
            if (i > 0) sb.append(",")
            sb.append("\"").append(escapeJson(entry.key)).append("\":")
            sb.append(valueToJson(entry.value))
        }
        sb.append("}")
        return sb.toString()
    }

    private fun valueToJson(value: Any): String {
        return when (value) {
            is String -> "\"${escapeJson(value)}\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> {
                val sb = StringBuilder("[")
                value.forEachIndexed { i, item ->
                    if (i > 0) sb.append(",")
                    if (item != null) sb.append(valueToJson(item))
                }
                sb.append("]")
                sb.toString()
            }
            is Map<*, *> -> {
                val sb = StringBuilder("{")
                val entries = value.entries.toList()
                entries.forEachIndexed { i, entry ->
                    if (i > 0) sb.append(",")
                    sb.append("\"").append(escapeJson(entry.key.toString())).append("\":")
                    val v = entry.value
                    if (v != null) sb.append(valueToJson(v))
                }
                sb.append("}")
                sb.toString()
            }
            else -> "\"${escapeJson(value.toString())}\""
        }
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 32) {
                    sb.append("\\u${c.code.toString(16).padStart(4, '0')}")
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    // ===== Event Methods =====

    fun logLoopStart(round: Int) {
        logEvent(mutableMapOf(
            "type" to "loop_start",
            "round" to round,
            "timestamp" to currentTimestamp()
        ))
    }

    fun logScreenshot(round: Int, size: String, base64Length: Int) {
        logEvent(mutableMapOf(
            "type" to "screenshot",
            "round" to round,
            "size" to size,
            "base64_length" to base64Length
        ))
    }

    fun logLlmRequest(round: Int, messageCount: Int) {
        logEvent(mutableMapOf(
            "type" to "llm_request",
            "round" to round,
            "message_count" to messageCount
        ))
    }

    fun logLlmResponse(round: Int, toolCalls: List<String>, tokenUsage: Int) {
        logEvent(mutableMapOf(
            "type" to "llm_response",
            "round" to round,
            "tool_calls" to toolCalls,
            "token_usage" to tokenUsage
        ))
    }

    fun logToolCall(round: Int, tool: String, params: String) {
        logEvent(mutableMapOf(
            "type" to "tool_call",
            "round" to round,
            "tool" to tool,
            "params" to params
        ))
    }

    fun logToolResult(round: Int, tool: String, success: Boolean) {
        logEvent(mutableMapOf(
            "type" to "tool_result",
            "round" to round,
            "tool" to tool,
            "success" to success
        ))
    }

    fun logActionHistory(round: Int, history: List<String>) {
        logEvent(mutableMapOf(
            "type" to "action_history",
            "round" to round,
            "history" to history
        ))
    }

    fun logErrorRecovery(round: Int, recoveryType: String, staleCount: Int = 0, detail: String = "") {
        val map = mutableMapOf<String, Any>(
            "type" to "error_recovery",
            "round" to round,
            "type" to recoveryType
        )
        if (recoveryType == "stale_action") {
            map["stale_count"] = staleCount
        }
        if (detail.isNotEmpty()) {
            map["detail"] = detail
        }
        logEvent(map)
    }

    fun logComplete(round: Int, totalTokens: Int, summary: String) {
        logEvent(mutableMapOf(
            "type" to "complete",
            "round" to round,
            "total_tokens" to totalTokens,
            "summary" to summary
        ))
    }

    fun logError(round: Int, error: String) {
        logEvent(mutableMapOf(
            "type" to "error",
            "round" to round,
            "error" to error
        ))
    }

    /**
     * Close the writer and release resources.
     */
    fun close() {
        try {
            writer?.close()
            XLog.i(TAG, "Event logger closed: ${eventFile?.absolutePath}")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to close event logger", e)
        }
        writer = null
        eventFile = null
    }
}
