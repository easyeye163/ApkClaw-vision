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

public class LongPressTool extends BaseTool {

    @Override
    public String getName() {
        return "long_press";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_long_press);
    }

    @Override
    public String getDescriptionEN() {
        return "Perform a long press at the specified screen position using percentage coordinates (x_percent, y_percent) ranging from 0 to 100, for a given duration. Coordinates are automatically converted to absolute pixels.";
    }

    @Override
    public String getDescriptionCN() {
        return "在指定的屏幕位置执行长按操作，使用百分比坐标 (x_percent, y_percent)，范围 0~100，持续指定时长。坐标会自动转换为实际像素。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("x_percent", "number", "X coordinate as percentage (0-100). 0=left edge, 100=right edge, 50=center", true),
                new ToolParameter("y_percent", "number", "Y coordinate as percentage (0-100). 0=top edge, 100=bottom edge, 50=center", true),
                new ToolParameter("duration_ms", "integer", "Duration of long press in milliseconds (default 1000)", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        double xPercent = requireDouble(params, "x_percent");
        double yPercent = requireDouble(params, "y_percent");
        if (xPercent < 0 || xPercent > 100 || yPercent < 0 || yPercent > 100) {
            return ToolResult.error("Percentage coordinates must be between 0 and 100");
        }
        int[] screenSize = getScreenSize();
        int absX = (int)(xPercent / 100.0 * screenSize[0]);
        int absY = (int)(yPercent / 100.0 * screenSize[1]);
        long duration = optionalLong(params, "duration_ms", 1000);
        boolean success = service.performLongPress(absX, absY, duration);
        return success ? ToolResult.success("Long pressed at (" + xPercent + ", " + yPercent + ") → absolute (" + absX + ", " + absY + ") for " + duration + "ms")
                : ToolResult.error("Failed to long press at (" + absX + ", " + absY + ")");
    }
}
