package com.apk.claw.android.ui.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
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
        val spinnerChatMode = findViewById<Spinner>(R.id.spinnerChatMode)
        val etOpenClawWsUrl = findViewById<EditText>(R.id.etOpenClawWsUrl)
        val llOpenClawConfig = findViewById<android.widget.LinearLayout>(R.id.llOpenClawConfig)
        val tvOpenClawTip = findViewById<TextView>(R.id.tvOpenClawTip)
        val tvVoiceLlmTip = findViewById<TextView>(R.id.tvVoiceLlmTip)

        // Load saved config with defaults
        switchEnabled.isChecked = KVUtils.isWebRTCEnabled()
        etWsBase.setText(KVUtils.getCyberVerseWsBase())
        etApiBase.setText(KVUtils.getCyberVerseApiBase())
        etCharacterId.setText(KVUtils.getCyberVerseCharacterId())
        etOpenClawWsUrl.setText(KVUtils.getOpenClawWsUrl())

        // Chat mode spinner
        val modes = arrayOf("VoiceLLM", "OpenClaw")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerChatMode.adapter = modeAdapter

        // Set current selection
        val currentMode = KVUtils.getChatMode()
        spinnerChatMode.setSelection(if (currentMode == "openclaw") 1 else 0)

        // Update OpenClaw fields visibility based on mode
        fun updateModeVisibility(position: Int) {
            val isOpenClaw = position == 1
            llOpenClawConfig.visibility = if (isOpenClaw) android.view.View.VISIBLE else android.view.View.GONE
            tvOpenClawTip.visibility = if (isOpenClaw) android.view.View.VISIBLE else android.view.View.GONE
            tvVoiceLlmTip.visibility = if (isOpenClaw) android.view.View.GONE else android.view.View.VISIBLE
        }

        // Initial visibility
        updateModeVisibility(spinnerChatMode.selectedItemPosition)

        spinnerChatMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateModeVisibility(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            KVUtils.setWebRTCEnabled(isChecked)
            if (isChecked && etApiBase.text.toString().trim().isEmpty()) {
                Toast.makeText(this, getString(R.string.webrtc_url_required), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnSaveWebRTC).setOnClickListener {
            KVUtils.setWebRTCEnabled(switchEnabled.isChecked)
            KVUtils.setCyberVerseWsBase(etWsBase.text.toString().trim())
            KVUtils.setCyberVerseApiBase(etApiBase.text.toString().trim())
            KVUtils.setCyberVerseCharacterId(etCharacterId.text.toString().trim())
            KVUtils.setOpenClawWsUrl(etOpenClawWsUrl.text.toString().trim())

            // Save chat mode
            val selectedMode = if (spinnerChatMode.selectedItemPosition == 1) "openclaw" else "voicellm"
            KVUtils.setChatMode(selectedMode)

            Toast.makeText(this, getString(R.string.webrtc_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnClearWebRTC).setOnClickListener {
            switchEnabled.isChecked = false
            etWsBase.setText("")
            etApiBase.setText("")
            etCharacterId.setText("")
            etOpenClawWsUrl.setText("")
            spinnerChatMode.setSelection(0)
            KVUtils.setWebRTCEnabled(false)
            KVUtils.setCyberVerseWsBase("")
            KVUtils.setCyberVerseApiBase("")
            KVUtils.setCyberVerseCharacterId("")
            KVUtils.setOpenClawWsUrl("")
            KVUtils.setChatMode("voicellm")
            updateModeVisibility(0)
            Toast.makeText(this, getString(R.string.webrtc_config_cleared), Toast.LENGTH_SHORT).show()
        }
    }
}
