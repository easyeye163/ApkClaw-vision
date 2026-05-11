package com.apk.claw.android.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.widget.CommonToolbar

/**
 * 云端对话模式配置页（WebSocket 地址配置）
 */
class CloudChatConfigActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_chat_config)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.cloud_chat_config_title))
            showBackButton(true) { finish() }
        }

        val etWsUrl = findViewById<EditText>(R.id.etWsUrl)
        etWsUrl.setText(KVUtils.getCloudChatWsUrl())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val wsUrl = etWsUrl.text.toString().trim()
            KVUtils.setCloudChatWsUrl(wsUrl)
            Toast.makeText(this, R.string.cloud_chat_config_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            etWsUrl.text.clear()
            KVUtils.setCloudChatWsUrl("")
            KVUtils.setCloudChatSessionId("")
            Toast.makeText(this, R.string.cloud_chat_config_cleared, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
