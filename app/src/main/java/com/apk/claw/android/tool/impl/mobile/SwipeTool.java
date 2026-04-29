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
        return "Swipe from one point to another on the screen using percentage coordinates. Useful for scrolling, pulling down notifications, etc. Coordinates are automatically converted to absolute pixels.";
    }

    @Override
    public String getDescriptionCN() {
        return "在屏幕上从一个点滑动到另一个点，使用百分比坐标。适用于滚动、下拉通知等操作。坐标会自动转换为实际像素。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("start_x", "number", "Start X coordinate as percentage (0.0-1.0)", true),
                new ToolParameter("start_y", "number", "Start Y coordinate as percentage (0.0-1.0)", true),
                new ToolParameter("end_x", "number", "End X coordinate as percentage (0.0-1.0)", true),
                new ToolParameter("end_y", "number", "End Y coordinate as percentage (0.0-1.0)", true),
                new ToolParameter("duration_ms", "integer", "Swipe duration in milliseconds (default 500)", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        double startXPercent = requireDouble(params, "start_x");
        double startYPercent = requireDouble(params, "start_y");
        double endXPercent = requireDouble(params, "end_x");
        double endYPercent = requireDouble(params, "end_y");
        if (startXPercent < 0 || startXPercent > 1 || startYPercent < 0 || startYPercent > 1
            || endXPercent < 0 || endXPercent > 1 || endYPercent < 0 || endYPercent > 1) {
            return ToolResult.error("Percentage coordinates must be between 0.0 and 1.0");
        }
        int[] screenSize = getScreenSize();
        int startX = (int)(startXPercent * screenSize[0]);
        int startY = (int)(startYPercent * screenSize[1]);
        int endX = (int)(endXPercent * screenSize[0]);
        int endY = (int)(endYPercent * screenSize[1]);
        long duration = optionalLong(params, "duration_ms", 500);
        boolean success = service.performSwipe(startX, startY, endX, endY, duration);
        return success ? ToolResult.success("Swiped from (" + startXPercent + ", " + startYPercent + ") to (" + endXPercent + ", " + endYPercent + ") → absolute (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")")
                : ToolResult.error("Failed to swipe");
    }
}
