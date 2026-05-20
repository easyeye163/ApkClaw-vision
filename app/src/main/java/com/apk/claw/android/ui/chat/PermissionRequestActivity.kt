package com.apk.claw.android.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * 透明权限请求 Activity
 * 用于从非 Activity 环境（如悬浮窗、Service）弹出系统权限申请对话框。
 * 用法：PermissionRequestActivity.requestPermission(context, permission) { granted -> ... }
 */
class PermissionRequestActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PERMISSION = "permission"

        /** 权限请求结果回调，使用后自动清除 */
        @Volatile
        private var pendingCallback: ((Boolean) -> Unit)? = null

        /**
         * 从任意 Context 启动透明权限请求 Activity
         * @param context 任意 Context（Application 也可以，会自动加 FLAG_ACTIVITY_NEW_TASK）
         * @param permission 要申请的权限字符串，如 Manifest.permission.RECORD_AUDIO
         * @param callback 权限请求结果回调，true = 已授权
         */
        @JvmStatic
        fun requestPermission(
            context: Context,
            permission: String,
            callback: (Boolean) -> Unit
        ) {
            pendingCallback = callback
            val intent = Intent(context, PermissionRequestActivity::class.java).apply {
                putExtra(EXTRA_PERMISSION, permission)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val cb = pendingCallback
            pendingCallback = null
            if (!granted) {
                Toast.makeText(
                    this,
                    getString(com.apk.claw.android.R.string.voice_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
            cb?.invoke(granted)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = intent.getStringExtra(EXTRA_PERMISSION)
        if (permission.isNullOrEmpty()) {
            finish()
            return
        }
        // 直接发起权限请求，系统会弹出权限对话框
        permissionLauncher.launch(permission)
    }

    override fun finish() {
        super.finish()
        // 去掉 Activity 切换动画，保持透明无缝体验
        overridePendingTransition(0, 0)
    }
}
