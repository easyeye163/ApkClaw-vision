package com.apk.claw.android.tool.impl.mobile;

import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.service.ClawAccessibilityService;
import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SwipeTool extends BaseTool {

    @Override
    public String getName() {
        return "swipe";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_swipe);
    }

    @Override
    public String getDescriptionEN() {
        return "Swipe from one point to another on the screen using percentage coordinates (0-100). " +
               "Useful for scrolling, pulling down notifications, etc. " +
               "Example: swipe(start_x_percent=50, start_y_percent=70, end_x_percent=50, end_y_percent=30) scrolls up (content moves up).";
    }

    @Override
    public String getDescriptionCN() {
        return "在屏幕上从一个点滑动到另一个点，使用百分比坐标 (0-100)。适用于滚动、下拉通知等操作。" +
               "例如：swipe(start_x_percent=50, start_y_percent=70, end_x_percent=50, end_y_percent=30) 向上滑动（内容向上移）。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("start_x_percent", "integer", "Start X position as percentage (0-100)", true),
                new ToolParameter("start_y_percent", "integer", "Start Y position as percentage (0-100)", true),
                new ToolParameter("end_x_percent", "integer", "End X position as percentage (0-100)", true),
                new ToolParameter("end_y_percent", "integer", "End Y position as percentage (0-100)", true),
                new ToolParameter("duration_ms", "integer", "Swipe duration in milliseconds (default 500)", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        int startXPercent = requireInt(params, "start_x_percent");
        int startYPercent = requireInt(params, "start_y_percent");
        int endXPercent = requireInt(params, "end_x_percent");
        int endYPercent = requireInt(params, "end_y_percent");
        
        String boundsError = validatePercentCoordinates(startXPercent, startYPercent);
        if (boundsError != null) return ToolResult.error(boundsError);
        boundsError = validatePercentCoordinates(endXPercent, endYPercent);
        if (boundsError != null) return ToolResult.error(boundsError);
        
        int startX = xPercentToAbsolute(startXPercent);
        int startY = yPercentToAbsolute(startYPercent);
        int endX = xPercentToAbsolute(endXPercent);
        int endY = yPercentToAbsolute(endYPercent);
        
        long duration = optionalLong(params, "duration_ms", 500);
        boolean success = service.performSwipe(startX, startY, endX, endY, duration);
        return success ? ToolResult.success("Swiped from (" + startXPercent + "%, " + startYPercent + "%) to (" + endXPercent + "%, " + endYPercent + "%)")
                : ToolResult.error("Failed to swipe");
    }
}