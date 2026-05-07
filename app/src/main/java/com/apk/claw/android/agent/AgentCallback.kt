package com.apk.claw.android.agent

import com.apk.claw.android.tool.ToolResult

interface AgentCallback {
    /**
     * 新的一轮 Agent Loop 开始时的回调
     * @param round 当前轮数（从 1 开始）
     */
    fun onLoopStart(round: Int)
    fun onContent(round: Int, content: String)
    fun onToolCall(round: Int, toolId: String, toolName: String, parameters: String)
    fun onToolResult(round: Int, toolId: String, toolName: String, parameters: String, result: ToolResult)
    fun onComplete(round: Int, finalAnswer: String, totalTokens: Int)
    fun onError(round: Int, error: Exception, totalTokens: Int)
    fun onSystemDialogBlocked(round: Int, totalTokens: Int)

    /**
     * 当 LLM 调用 call_user 工具时的回调
     * @param round 当前轮数
     * @param reason LLM 请求帮助的原因
     * @param totalTokens 累计 token 使用量
     */
    fun onCallUser(round: Int, reason: String, totalTokens: Int)
}
