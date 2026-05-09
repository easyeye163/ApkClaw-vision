package com.apk.claw.android.ui.settings

import android.os.Bundle
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.widget.CommonToolbar

class WebRTCConfigActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webrtc_config)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.webrtc_config_title))
            setActionIcon(R.drawable.ic_minimize) { finish() }
        }

        val switchEnabled = findViewById<Switch>(R.id.switchWebRTCEnabled)
        val etUrl = findViewById<EditText>(R.id.etWebRTCUrl)
        val etToken = findViewById<EditText>(R.id.etWebRTCToken)

        // Load saved config
        switchEnabled.isChecked = KVUtils.isWebRTCEnabled()
        etUrl.setText(KVUtils.getWebRTCUrl())
        etToken.setText(KVUtils.getWebRTCToken())

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            KVUtils.setWebRTCEnabled(isChecked)
            if (isChecked && etUrl.text.toString().trim().isEmpty()) {
                Toast.makeText(this, getString(R.string.webrtc_url_required), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<android.widget.Button>(R.id.btnSaveWebRTC).setOnClickListener {
            KVUtils.setWebRTCUrl(etUrl.text.toString().trim())
            KVUtils.setWebRTCToken(etToken.text.toString().trim())
            Toast.makeText(this, getString(R.string.webrtc_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnClearWebRTC).setOnClickListener {
            switchEnabled.isChecked = false
            etUrl.setText("")
            etToken.setText("")
            KVUtils.setWebRTCEnabled(false)
            KVUtils.setWebRTCUrl("")
            KVUtils.setWebRTCToken("")
            Toast.makeText(this, getString(R.string.webrtc_config_cleared), Toast.LENGTH_SHORT).show()
        }
    }
}
