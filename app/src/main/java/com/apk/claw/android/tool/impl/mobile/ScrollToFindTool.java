package com.apk.claw.android.tool.impl.mobile;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.service.ClawAccessibilityService;
import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 滚动查找工具：在当前页面自动滚动并查找包含指定文本的元素。
 * 找到后返回元素的百分比坐标信息，方便 LLM 直接调用 tap。
 */
public class ScrollToFindTool extends BaseTool {

    @Override
    public String getName() {
        return "scroll_to_find";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_scroll_to_find);
    }

    @Override
    public String getDescriptionEN() {
        return "Scroll the screen to find an element containing the specified text. "
                + "Automatically scrolls in the given direction and searches after each scroll. "
                + "Returns the element's center position as PERCENTAGE coordinates (0-100). "
                + "After finding, you can click it with tap(x_percent=XX, y_percent=YY). "
                + "Much more efficient than manually calling swipe + get_screen_info in a loop.";
    }

    @Override
    public String getDescriptionCN() {
        return "滚动屏幕查找包含指定文本的元素。自动在指定方向上滚动并在每次滚动后搜索。"
                + "找到后返回元素中心的百分比坐标 (0-100)。找到后可以用 tap(x_percent=XX, y_percent=YY) 点击。"
                + "比手动循环调用 swipe + get_screen_info 高效得多。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("text", "string", "The text to search for on the screen", true),
                new ToolParameter("direction", "string",
                        "Scroll direction: 'up' or 'down' (default 'down'). 'down' means content moves up to reveal lower content.", false),
                new ToolParameter("max_scrolls", "integer",
                        "Maximum number of scrolls to attempt (default 10, max 20)", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }

        String text = requireString(params, "text");
        String direction = optionalString(params, "direction", "down");
        int maxScrolls = optionalInt(params, "max_scrolls", 10);
        maxScrolls = Math.min(Math.max(maxScrolls, 1), 20);

        int[] screenSize = getScreenSize();
        int screenWidth = screenSize[0];
        int screenHeight = screenSize[1];

        int centerX = screenWidth / 2;
        int scrollStartY, scrollEndY;
        if ("up".equals(direction)) {
            scrollStartY = (int) (screenHeight * 0.3);
            scrollEndY = (int) (screenHeight * 0.7);
        } else {
            scrollStartY = (int) (screenHeight * 0.7);
            scrollEndY = (int) (screenHeight * 0.3);
        }

        ToolResult found = findElement(service, text, screenWidth, screenHeight);
        if (found != null) {
            return found;
        }

        String lastScreenContent = getScreenSnapshot(service);
        for (int i = 0; i < maxScrolls; i++) {
            boolean swiped = service.performSwipe(centerX, scrollStartY, centerX, scrollEndY, 400);
            if (!swiped) {
                return ToolResult.error("Swipe failed at scroll #" + (i + 1));
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.error("Interrupted during scroll");
            }

            found = findElement(service, text, screenWidth, screenHeight);
            if (found != null) {
                return found;
            }

            String currentScreen = getScreenSnapshot(service);
            if (currentScreen != null && currentScreen.equals(lastScreenContent)) {
                return ToolResult.error("Element with text \"" + text + "\" not found. "
                        + "Reached the " + ("up".equals(direction) ? "top" : "bottom")
                        + " after " + (i + 1) + " scroll(s).");
            }
            lastScreenContent = currentScreen;
        }

        return ToolResult.error("Element with text \"" + text + "\" not found after " + maxScrolls + " scroll(s).");
    }

    private ToolResult findElement(ClawAccessibilityService service, String text, int screenWidth, int screenHeight) {
        List<AccessibilityNodeInfo> nodes = service.findNodesByText(text);
        if (nodes.isEmpty()) {
            return null;
        }
        try {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isVisibleToUser()) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    int centerX = bounds.centerX();
                    int centerY = bounds.centerY();
                    
                    int xPercent = absoluteToPercent(centerX, screenWidth);
                    int yPercent = absoluteToPercent(centerY, screenHeight);
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("Found element with text \"").append(text).append("\"");
                    sb.append("\n  center_percent=(").append(xPercent).append(", ").append(yPercent).append(")");
                    sb.append("\n  bounds=").append(bounds.toShortString());
                    sb.append("\n  clickable=").append(node.isClickable());
                    if (node.getClassName() != null) {
                        sb.append("\n  class=").append(node.getClassName());
                    }
                    sb.append("\n  → To click, use: tap(x_percent=").append(xPercent).append(", y_percent=").append(yPercent).append(")");
                    return ToolResult.success(sb.toString());
                }
            }
            return null;
        } finally {
            ClawAccessibilityService.recycleNodes(nodes);
        }
    }

    private String getScreenSnapshot(ClawAccessibilityService service) {
        try {
            return service.getScreenTree();
        } catch (Exception e) {
            return null;
        }
    }
}