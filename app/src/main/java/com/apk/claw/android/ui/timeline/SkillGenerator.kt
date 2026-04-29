package com.apk.claw.android.ui.timeline

import android.content.Context
import com.apk.claw.android.agent.AgentConfig
import com.apk.claw.android.agent.llm.LlmClientFactory
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.timeline.TaskTimeline
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import java.util.concurrent.Executors

class SkillGenerator(private val context: Context) {

    companion object {
        private const val TAG = "SkillGenerator"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()

    data class GeneratedSkillInfo(
        val name: String,
        val description: String,
        val promptTemplate: String
    )

    fun generateSkillFromTask(
        record: TaskTimeline.TaskRecord,
        callback: (Result<GeneratedSkillInfo>) -> Unit
    ) {
        executor.execute {
            try {
                val info = doGenerate(record)
                callback(Result.success(info))
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
  "category": "PHONE_CONTROL"
}

注意：
1. promptTemplate 应包含详细的执行步骤，让LLM能够重复执行类似任务
2. name 应简短且有意义
3. description 应清楚说明技能用途
4. triggerKeywords 应是可能触发该技能的关键词
5. category 只能是以下之一：PHONE_CONTROL, AUTO_REPLY, CUSTOM
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
            triggerKeywords = triggerKeywords
        )

        XLog.i(TAG, "Skill created: $skillName")
        return GeneratedSkillInfo(skillName, skillDescription, promptTemplate)
    }

    private fun buildConfig(): AgentConfig {
        var baseUrl = KVUtils.getLlmBaseUrl().trim()
        if (baseUrl.isEmpty()) baseUrl = "https://api.openai.com/v1"
        return AgentConfig.Builder()
            .apiKey(KVUtils.getLlmApiKey())
            .baseUrl(baseUrl)
            .modelName(KVUtils.getLlmModelName())
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