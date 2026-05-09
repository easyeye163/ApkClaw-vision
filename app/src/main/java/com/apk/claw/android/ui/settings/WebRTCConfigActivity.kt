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
        val etWsBase = findViewById<EditText>(R.id.etWebRTCWsBase)
        val etApiBase = findViewById<EditText>(R.id.etWebRTCApiBase)
        val etCharacterId = findViewById<EditText>(R.id.etWebRTCCharacterId)

        // Load saved config with defaults
        switchEnabled.isChecked = KVUtils.isWebRTCEnabled()
        etWsBase.setText(KVUtils.getCyberVerseWsBase())
        etApiBase.setText(KVUtils.getCyberVerseApiBase())
        etCharacterId.setText(KVUtils.getCyberVerseCharacterId())

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            KVUtils.setWebRTCEnabled(isChecked)
            if (isChecked && etApiBase.text.toString().trim().isEmpty()) {
                Toast.makeText(this, getString(R.string.webrtc_url_required), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<android.widget.Button>(R.id.btnSaveWebRTC).setOnClickListener {
            KVUtils.setCyberVerseWsBase(etWsBase.text.toString().trim())
            KVUtils.setCyberVerseApiBase(etApiBase.text.toString().trim())
            KVUtils.setCyberVerseCharacterId(etCharacterId.text.toString().trim())
            Toast.makeText(this, getString(R.string.webrtc_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnClearWebRTC).setOnClickListener {
            switchEnabled.isChecked = false
            etWsBase.setText("")
            etApiBase.setText("")
            etCharacterId.setText("")
            KVUtils.setWebRTCEnabled(false)
            KVUtils.setCyberVerseWsBase("")
            KVUtils.setCyberVerseApiBase("")
            KVUtils.setCyberVerseCharacterId("")
            Toast.makeText(this, getString(R.string.webrtc_config_cleared), Toast.LENGTH_SHORT).show()
        }
    }
}
