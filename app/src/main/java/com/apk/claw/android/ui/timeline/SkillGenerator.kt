package com.apk.claw.android.ui.timeline

import android.content.Context
import com.apk.claw.android.agent.AgentConfig
import com.apk.claw.android.agent.llm.LlmClientFactory
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.timeline.TaskTimeline
import com.apk.claw.android.utils.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SkillGenerator(private val context: Context) {

    companion object {
        private const val TAG = "SkillGenerator"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()

    data class GeneratedSkillInfo(
        val name: String,
        val description: String,
        val promptTemplate: String,
        val offlineSteps: String?
    )

    fun generateSkillFromTask(
        record: TaskTimeline.TaskRecord,
        callback: (Result<GeneratedSkillInfo>) -> Unit
    ) {
        val future = executor.submit<GeneratedSkillInfo> {
            doGenerate(record)
        }
        // 在另一个线程中等待结果，避免阻塞 executor
        Executors.newSingleThreadExecutor().execute {
            try {
                val info = future.get(60, TimeUnit.SECONDS)
                callback(Result.success(info))
            } catch (e: TimeoutException) {
                future.cancel(true)
                XLog.e(TAG, "Skill generation timed out after 60s")
                callback(Result.failure(TimeoutException("生成技能超时（60秒），请检查网络或 LLM 配置后重试")))
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to generate skill", e)
                callback(Result.failure(e))
            }
        }
    }

    private fun doGenerate(record: TaskTimeline.TaskRecord): GeneratedSkillInfo {
        val config = buildConfig()
        val client = LlmClientFactory.create(config)

        val stepsSummary = buildStepsSummary(record.steps)
        val categoryHint = determineCategoryHint(record)

        val systemPrompt = """
你是一个技能生成助手。根据用户的任务执行过程，生成一个可复用的技能。

任务目标：${record.userMessage}

执行步骤摘要：
$stepsSummary

类别提示：$categoryHint

请分析以上任务执行过程，生成一个可复用的技能。返回JSON格式（不要有任何其他内容）：
{
  "name": "技能名称（简短，中文）",
  "description": "技能描述（说明该技能能做什么）",
  "promptTemplate": "执行该技能时发给LLM的提示词模板（详细的操作步骤）",
  "triggerKeywords": ["触发关键词1", "触发关键词2"],
  "category": "PHONE_CONTROL",
  "offlineSteps": [
    {"tool": "open_app", "params": {"package_name": "com.example.app"}},
    {"tool": "wait", "params": {"ms": 3000}},
    {"tool": "tap", "params": {"description": "签到按钮", "x": 540, "y": 1200}},
    {"tool": "finish", "params": {"message": "签到完成"}}
  ]
}

注意：
1. promptTemplate 应包含详细的执行步骤，让LLM能够重复执行类似任务
2. name 应简短且有意义
3. description 应清楚说明技能用途
4. triggerKeywords 应是可能触发该技能的关键词
5. category 只能是以下之一：PHONE_CONTROL, AUTO_REPLY, CUSTOM
6. **offlineSteps 是核心字段**，它是一个 JSON 数组，定义了离线执行时需要依次调用的工具序列
   - 每个元素包含 tool(工具名) 和 params(参数对象)
   - 工具名必须是已注册的工具：open_app, tap, swipe, long_press, input_text, wait, get_screen_info, scroll_to_find, find_node_info, system_key, get_installed_apps, take_screenshot, clipboard, finish, call_user, send_file, repeat_actions
   - 参数名必须与工具的参数名完全一致
   - 最后一步必须是 finish 工具
   - 步骤应足够通用，使不同设备/分辨率上都能执行
   - 如果任务涉及视觉判断（如"找到签到按钮"），优先使用 find_node_info 或 scroll_to_find 代替固定坐标
   - 如果任务步骤完全确定且可机械化执行，请生成 offlineSteps；如果需要 LLM 实时决策则设为 null
""".trimIndent()

        val userPrompt = "请生成技能"

        val messages = listOf(
            SystemMessage.from(systemPrompt),
            UserMessage.from(userPrompt)
        )

        val response = client.chat(messages, emptyList())
        val responseText = response.text ?: throw Exception("Empty response from LLM")

        XLog.d(TAG, "LLM response: $responseText")

        val json = parseJson(responseText)
        val skillName = json.get("name")?.asString ?: "Generated Skill"
        val skillDescription = json.get("description")?.asString ?: ""
        val promptTemplate = json.get("promptTemplate")?.asString ?: ""
        val triggerKeywords = json.get("triggerKeywords")?.asJsonArray?.map { it.asString } ?: emptyList()
        val categoryStr = json.get("category")?.asString ?: "CUSTOM"
        val offlineStepsStr = if (json.has("offlineSteps") && !json.get("offlineSteps").isJsonNull) {
            val steps = json.get("offlineSteps")?.asJsonArray
            if (steps != null && steps.size() > 0) gson.toJson(steps) else null
        } else null

        val category = try {
            SkillSystem.SkillCategory.valueOf(categoryStr)
        } catch (_: Exception) {
            SkillSystem.SkillCategory.CUSTOM
        }

        val skillSystem = SkillSystem.getInstance(context)
        skillSystem.createSkill(
            name = skillName,
            description = skillDescription,
            promptTemplate = promptTemplate,
            category = category,
            triggerType = SkillSystem.TriggerType.KEYWORD,
            triggerKeywords = triggerKeywords,
            offlineSteps = offlineStepsStr
        )

        XLog.i(TAG, "Skill created: $skillName (offline=${offlineStepsStr != null}, steps=${offlineStepsStr?.let { gson.fromJson(it, List::class.java).size } ?: 0})")
        return GeneratedSkillInfo(skillName, skillDescription, promptTemplate, offlineStepsStr)
    }

    private fun buildConfig(): AgentConfig {
        // 复用 AppViewModel 的配置逻辑，确保和 Agent 一致
        var baseUrl = com.apk.claw.android.utils.KVUtils.getLlmBaseUrl().trim()
        val apiKey = com.apk.claw.android.utils.KVUtils.getLlmApiKey().trim()
        val modelName = com.apk.claw.android.utils.KVUtils.getLlmModelName().trim()

        XLog.i(TAG, "SkillGenerator config: baseUrl=$baseUrl, apiKey=${apiKey.take(8)}..., model=$modelName")

        if (baseUrl.isEmpty()) baseUrl = "https://api.openai.com/v1"

        // 校验 baseUrl 格式
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            XLog.w(TAG, "baseUrl format invalid (missing protocol): $baseUrl, trying to fix")
            baseUrl = "https://$baseUrl"
        }

        // 去掉尾部多余的斜杠和点
        baseUrl = baseUrl.trimEnd('.', '/')

        XLog.i(TAG, "SkillGenerator final baseUrl: $baseUrl")

        return AgentConfig.Builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(0.3)
            .maxIterations(5)
            .build()
    }

    private fun buildStepsSummary(steps: List<TaskTimeline.TaskStep>): String {
        val sb = StringBuilder()
        var round = 0
        for (step in steps) {
            if (step.round != round) {
                round = step.round
                sb.append("\n--- Round $round ---\n")
            }
            val typeStr = when (step.type) {
                TaskTimeline.StepType.THINKING -> "[思考]"
                TaskTimeline.StepType.TOOL_CALL -> "[工具调用]"
                TaskTimeline.StepType.TOOL_RESULT -> "[结果]"
                TaskTimeline.StepType.ERROR -> "[错误]"
                else -> ""
            }
            val content = step.content
            val truncated = if (content.length > 200) content.substring(0, 200) + "..." else content
            sb.append("$typeStr $truncated\n")
        }
        return sb.toString()
    }

    private fun determineCategoryHint(record: TaskTimeline.TaskRecord): String {
        val toolStats = record.toolCallStats
        val phoneControlTools = setOf("tap", "swipe", "long_press", "open_app", "input_text", "scroll_to_find")
        val hasPhoneControl = toolStats.keys.any { it in phoneControlTools }

        val channelType = record.channelType.lowercase()
        val isMessageChannel = channelType in setOf("wechat", "telegram", "discord", "qq", "dingtalk", "feishu")

        return when {
            hasPhoneControl -> "该任务涉及手机操作，建议分类为 PHONE_CONTROL"
            isMessageChannel -> "该任务来自消息渠道，可能涉及自动回复，建议分类为 AUTO_REPLY"
            else -> "该任务为自定义任务，建议分类为 CUSTOM"
        }
    }

    private fun parseJson(text: String): JsonObject {
        val cleaned = text.trim()
            .replace("```json", "")
            .replace("```", "")
            .trim()
        return gson.fromJson(cleaned, JsonObject::class.java)
    }
}