package com.apk.claw.android.tool.impl;

import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tool that allows the LLM to signal it needs human help.
 * When called, the agent pauses and notifies the user via callback.
 */
public class CallUserTool extends BaseTool {

    @Override
    public String getName() {
        return "call_user";
    }

    @Override
    public String getDisplayName() {
        return "呼叫用户";
    }

    @Override
    public String getDescriptionEN() {
        return "Request human help when you are stuck, unsure, or encounter a situation that requires human intervention (e.g., login, payment, CAPTCHA, or unexpected UI). Use this instead of guessing or making potentially harmful actions.";
    }

    @Override
    public String getDescriptionCN() {
        return "当你遇到无法处理的情况时，请求人类帮助。适用于登录、支付、验证码、弹窗无法关闭、或不确定如何操作的场景。不要猜测操作，直接呼叫用户介入。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("reason", "string", "Explain why human help is needed and what the user should do", true)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String reason = requireString(params, "reason");
        return ToolResult.success(reason);
    }
}
