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
        return "Perform a long press at the specified screen position using percentage coordinates (0-100). " +
               "x_percent=0 is left edge, x_percent=100 is right edge. " +
               "y_percent=0 is top edge, y_percent=100 is bottom edge.";
    }

    @Override
    public String getDescriptionCN() {
        return "在屏幕指定位置执行长按，使用百分比坐标 (0-100)。x_percent=0 为左边缘，x_percent=100 为右边缘。y_percent=0 为上边缘，y_percent=100 为下边缘。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("x_percent", "integer", "X position as percentage (0-100, left to right)", true),
                new ToolParameter("y_percent", "integer", "Y position as percentage (0-100, top to bottom)", true),
                new ToolParameter("duration_ms", "integer", "Duration of long press in milliseconds (default 1000)", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        int xPercent = requireInt(params, "x_percent");
        int yPercent = requireInt(params, "y_percent");
        String boundsError = validatePercentCoordinates(xPercent, yPercent);
        if (boundsError != null) return ToolResult.error(boundsError);
        long duration = optionalLong(params, "duration_ms", 1000);
        
        int x = xPercentToAbsolute(xPercent);
        int y = yPercentToAbsolute(yPercent);
        
        boolean success = service.performLongPress(x, y, duration);
        return success ? ToolResult.success("Long pressed at (" + xPercent + "%, " + yPercent + "%) for " + duration + "ms")
                : ToolResult.error("Failed to long press at (" + xPercent + "%, " + yPercent + "%)");
    }
}