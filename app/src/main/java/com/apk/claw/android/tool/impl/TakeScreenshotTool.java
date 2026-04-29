package com.apk.claw.android.tool.impl;

import android.graphics.Bitmap;
import android.util.Base64;

import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.service.ClawAccessibilityService;
import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TakeScreenshotTool extends BaseTool {

    @Override
    public String getName() {
        return "take_screenshot";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_screenshot);
    }

    @Override
    public String getDescriptionEN() {
        return "Take a screenshot of the current screen. Returns the local file path of the saved PNG image. Requires Android 11+ (API 30).";
    }

    @Override
    public String getDescriptionCN() {
        return "对当前屏幕进行截图，保存为 PNG 文件并返回本地文件路径。需要 Android 11+（API 30）。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.emptyList();
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }

        Bitmap bitmap = service.takeScreenshot(5000);
        if (bitmap == null) {
            return ToolResult.error("Failed to take screenshot. Requires Android 11+ (API 30).");
        }

        try {
            Bitmap softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (softBitmap != null) {
                bitmap.recycle();
                bitmap = softBitmap;
            }

            File dir = new File(ClawApplication.Companion.getInstance().getCacheDir(), "screenshots");
            if (!dir.exists()) dir.mkdirs();

            String filename = System.currentTimeMillis() + ".png";
            File file = new File(dir, filename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            bitmap.recycle();

            return ToolResult.success(file.getAbsolutePath());
        } catch (Exception e) {
            bitmap.recycle();
            return ToolResult.error("Failed to save screenshot: " + e.getMessage());
        }
    }

    /** 视觉方案截图最大宽度（像素），超过此宽度会等比缩放 */
    private static final int MAX_SCREENSHOT_WIDTH = 720;
    /** 视觉方案截图 JPEG 质量 */
    private static final int SCREENSHOT_JPEG_QUALITY = 75;

    /**
     * Capture a screenshot and return it as a base64-encoded JPEG string.
     * The screenshot is scaled down to a max width of 720px to reduce token usage,
     * and compressed as JPEG at 75% quality.
     *
     * @return base64 JPEG string, or null if capture failed
     */
    public static String captureScreenBase64() {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) return null;
        Bitmap bitmap = service.takeScreenshot(5000);
        if (bitmap == null) return null;
        try {
            Bitmap softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (softBitmap != null) {
                bitmap.recycle();
                bitmap = softBitmap;
            }
            // 缩放到最大 720px 宽度，减少 base64 体积和 LLM token 消耗
            if (bitmap.getWidth() > MAX_SCREENSHOT_WIDTH) {
                float scale = (float) MAX_SCREENSHOT_WIDTH / bitmap.getWidth();
                int newWidth = MAX_SCREENSHOT_WIDTH;
                int newHeight = (int) (bitmap.getHeight() * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                bitmap.recycle();
                bitmap = scaled;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_JPEG_QUALITY, baos);
            bitmap.recycle();
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            bitmap.recycle();
            return null;
        }
    }
}
