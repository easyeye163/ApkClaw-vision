package com.apk.claw.android.floating.reasoning

/**
 * AI 推理步骤数据模型
 * 对应 AgentCallback 中的各类事件
 */
data class ReasoningStep(
    /** 步骤唯一 ID（基于 round + type 生成） */
    val id: String,
    /** 步骤类型 */
    val type: StepType,
    /** 当前 Agent 轮次 */
    val round: Int,
    /** 步骤内容（推理文本 / 工具名 / 结果摘要） */
    val content: String,
    /** 工具参数（仅 TOOL_CALL 类型有值） */
    val toolParameters: String? = null,
    /** 工具执行是否成功（仅 TOOL_RESULT 类型有值） */
    val isSuccess: Boolean? = null,
    /** 时间戳（毫秒） */
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class StepType {
        /** AI 正在推理/思考 */
        THINKING,
        /** AI 输出内容 */
        CONTENT,
        /** 调用工具 */
        TOOL_CALL,
        /** 工具返回结果 */
        TOOL_RESULT,
        /** 用户输入（追加指令） */
        USER_INPUT,
        /** 系统事件 */
        SYSTEM_EVENT,
        /** 错误 */
        ERROR,
        /** 任务完成 */
        COMPLETION
    }

    companion object {
        fun thinking(round: Int, content: String): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_think_${System.nanoTime()}",
                type = StepType.THINKING,
                round = round,
                content = content
            )
        }

        fun content(round: Int, text: String): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_content_${System.nanoTime()}",
                type = StepType.CONTENT,
                round = round,
                content = text
            )
        }

        fun toolCall(round: Int, toolId: String, toolName: String, parameters: String): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_tool_${toolId}",
                type = StepType.TOOL_CALL,
                round = round,
                content = toolName,
                toolParameters = parameters
            )
        }

        fun toolResult(
            round: Int,
            toolId: String,
            toolName: String,
            result: String,
            isSuccess: Boolean
        ): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_result_${toolId}",
                type = StepType.TOOL_RESULT,
                round = round,
                content = if (result.length > 200) result.take(200) + "..." else result,
                isSuccess = isSuccess
            )
        }

        fun userInput(round: Int, text: String): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_user_${System.nanoTime()}",
                type = StepType.USER_INPUT,
                round = round,
                content = text
            )
        }

        fun systemEvent(round: Int, text: String): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_sys_${System.nanoTime()}",
                type = StepType.SYSTEM_EVENT,
                round = round,
                content = text
            )
        }

        fun error(round: Int, error: String): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_err_${System.nanoTime()}",
                type = StepType.ERROR,
                round = round,
                content = error
            )
        }

        fun completion(round: Int, answer: String): ReasoningStep {
            return ReasoningStep(
                id = "r${round}_done_${System.nanoTime()}",
                type = StepType.COMPLETION,
                round = round,
                content = if (answer.length > 300) answer.take(300) + "..." else answer
            )
        }
    }
}
