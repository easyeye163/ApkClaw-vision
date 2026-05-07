package com.apk.claw.android.skill

import android.util.Log
import com.apk.claw.android.agent.AgentCallback
import com.apk.claw.android.tool.ToolRegistry
import com.apk.claw.android.tool.ToolResult
import com.apk.claw.android.utils.XLog
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

/**
 * 离线技能执行器
 *
 * 解析技能的 offlineSteps JSON，依次调用 ToolRegistry 执行工具，
 * 无需经过 LLM 即可完成技能任务。
 *
 * offlineSteps JSON 格式示例:
 * [
 *   {"tool": "open_app", "params": {"package_name": "com.example.app"}},
 *   {"tool": "wait", "params": {"ms": 3000}},
 *   {"tool": "tap", "params": {"description": "签到按钮", "x": 540, "y": 1200}},
 *   {"tool": "finish", "params": {"message": "签到完成"}}
 * ]
 */
object OfflineSkillExecutor {

    private const val TAG = "OfflineSkillExecutor"
    private val gson = Gson()

    data class OfflineStep(
        val tool: String,
        val params: Map<String, Any> = emptyMap()
    )

    /**
     * 执行离线技能
     *
     * @param skill 含 offlineSteps 的技能
     * @param userMessage 用户消息（用于模板变量替换）
     * @param callback 任务回调（复用 AgentCallback 接口）
     * @return 是否成功完成（finish 工具被调用）
     */
    fun execute(skill: SkillSystem.Skill, userMessage: String, callback: AgentCallback): Boolean {
        val stepsJson = skill.offlineSteps ?: run {
            XLog.e(TAG, "Skill ${skill.name} has no offlineSteps")
            callback.onError(0, IllegalArgumentException("技能没有离线执行步骤"), 0)
            return false
        }

        val steps = parseSteps(stepsJson)
        if (steps.isEmpty()) {
            XLog.e(TAG, "Skill ${skill.name} offlineSteps is empty after parsing")
            callback.onError(0, IllegalArgumentException("离线执行步骤解析为空"), 0)
            return false
        }

        XLog.i(TAG, "Executing skill '${skill.name}' offline: ${steps.size} steps")

        var totalTokens = 0

        for ((index, step) in steps.withIndex()) {
            val round = index + 1
            callback.onLoopStart(round)

            val toolName = step.tool
            val displayName = ToolRegistry.getInstance().getDisplayName(toolName)
            val params = replaceTemplateVars(step.params, userMessage)
            val paramsStr = gson.toJson(params)

            callback.onToolCall(round, toolName, displayName, paramsStr)
            XLog.d(TAG, "Step $round/$${steps.size}: $toolName($paramsStr)")

            try {
                val result = ToolRegistry.getInstance().executeTool(toolName, params)
                callback.onToolResult(round, toolName, displayName, paramsStr, result)

                if (!result.isSuccess) {
                    XLog.e(TAG, "Step $round failed: $toolName → ${result.error}")
                    // 工具执行失败不立即终止，继续执行后续步骤
                }

                // finish 工具 → 任务完成
                if (toolName == "finish" && result.isSuccess) {
                    val finishMessage = result.data ?: "技能执行完成"
                    XLog.i(TAG, "Skill '${skill.name}' completed: $finishMessage")
                    callback.onComplete(round, finishMessage, totalTokens)
                    return true
                }

                // call_user → 暂停请求帮助
                if (toolName == "call_user" && result.isSuccess) {
                    val reason = result.data ?: "No reason"
                    XLog.i(TAG, "Skill '${skill.name}' requesting user help: $reason")
                    callback.onCallUser(round, reason, totalTokens)
                    return false
                }

            } catch (e: Exception) {
                XLog.e(TAG, "Step $round exception: $toolName", e)
                callback.onToolResult(
                    round, toolName, displayName, paramsStr,
                    ToolResult.error("执行异常: ${e.message}")
                )
                // 异常不终止，继续后续步骤
            }
        }

        // 所有步骤执行完毕但没有 finish
        val message = "技能 '${skill.name}' 的所有 ${steps.size} 个步骤已执行完毕"
        XLog.w(TAG, message)
        callback.onComplete(steps.size, message, totalTokens)
        return true
    }

    /**
     * 解析 offlineSteps JSON 字符串为步骤列表
     */
    private fun parseSteps(stepsJson: String): List<OfflineStep> {
        return try {
            val jsonArray = gson.fromJson(stepsJson, JsonArray::class.java)
            val steps = mutableListOf<OfflineStep>()
            for (element in jsonArray) {
                if (element !is JsonObject) continue
                val tool = element.get("tool")?.asString ?: continue
                val paramsElement = element.get("params")
                val params: Map<String, Any> = if (paramsElement != null && paramsElement.isJsonObject) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    gson.fromJson(paramsElement, type) ?: emptyMap()
                } else {
                    emptyMap()
                }
                steps.add(OfflineStep(tool, params))
            }
            steps
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to parse offlineSteps JSON", e)
            emptyList()
        }
    }

    /**
     * 替换参数值中的模板变量
     * 支持 $USER_MESSAGE, $CURRENT_TIME
     */
    private fun replaceTemplateVars(params: Map<String, Any>, userMessage: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        for ((key, value) in params) {
            val strValue = value.toString()
                .replace("\$USER_MESSAGE", userMessage)
                .replace("\$MESSAGE", userMessage)
                .replace("\$CURRENT_TIME", timeStr)

            // 尝试转为数值类型（保持 JSON 原始类型）
            result[key] = when {
                strValue.matches(Regex("-?\\d+")) -> strValue.toIntOrNull() ?: strValue
                strValue.matches(Regex("-?\\d+\\.\\d+")) -> strValue.toDoubleOrNull() ?: strValue
                strValue == "true" -> true
                strValue == "false" -> false
                else -> strValue
            }
        }
        return result
    }
}
