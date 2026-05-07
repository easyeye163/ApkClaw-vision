package com.apk.claw.android.ui.chat

import android.app.Activity
import android.os.Bundle

/**
 * Legacy: no longer used for screenshots (AccessibilityService is used instead).
 * Kept only for the EXTRA_SCREENSHOT_PATH constant referenced by ChatActivity.
 */
class ScreenshotPermissionActivity : Activity() {

    companion object {
        const val EXTRA_SCREENSHOT_PATH = "screenshot_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
