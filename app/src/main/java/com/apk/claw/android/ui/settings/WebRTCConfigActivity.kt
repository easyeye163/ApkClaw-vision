package com.apk.claw.android.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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

    private val MODE_VALUES = arrayOf("omni", "standard")
    private val MODE_LABELS = arrayOf(
        "VoiceLLM (omni) - 内置语音处理",
        "OpenClaw (standard) - ASR→LLM→TTS 流水线"
    )
    private val MODE_DESCRIPTIONS = arrayOf(
        "VoiceLLM 模式：服务端内置语音大模型，单流处理音频+文本，不依赖外部 OpenClaw brain",
        "OpenClaw 模式：标准流水线（ASR→LLM→TTS→Avatar），需要配置 OpenClaw brain 地址"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webrtc_config)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.webrtc_config_title))
            setActionIcon(R.drawable.ic_minimize) { finish() }
        }

        val switchEnabled = findViewById<Switch>(R.id.switchWebRTCEnabled)
        val spinnerMode = findViewById<Spinner>(R.id.spinnerPipelineMode)
        val tvModeDesc = findViewById<TextView>(R.id.tvPipelineModeDesc)
        val layoutOpenClaw = findViewById<View>(R.id.layoutOpenClaw)
        val etOpenClawWsUrl = findViewById<EditText>(R.id.etOpenClawWsUrl)
        val etWsBase = findViewById<EditText>(R.id.etWebRTCWsBase)
        val etApiBase = findViewById<EditText>(R.id.etWebRTCApiBase)
        val etCharacterId = findViewById<EditText>(R.id.etWebRTCCharacterId)

        // Load saved config with defaults
        switchEnabled.isChecked = KVUtils.isWebRTCEnabled()
        etWsBase.setText(KVUtils.getCyberVerseWsBase())
        etApiBase.setText(KVUtils.getCyberVerseApiBase())
        etCharacterId.setText(KVUtils.getCyberVerseCharacterId())
        etOpenClawWsUrl.setText(KVUtils.getOpenClawWsUrl())

        // Setup pipeline mode spinner
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, MODE_LABELS)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMode.adapter = modeAdapter

        // Find current mode index
        val currentMode = KVUtils.getPipelineMode()
        val currentIndex = MODE_VALUES.indexOf(currentMode).coerceAtLeast(0)
        spinnerMode.setSelection(currentIndex)
        updateModeUI(currentIndex, tvModeDesc, layoutOpenClaw)

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateModeUI(position, tvModeDesc, layoutOpenClaw)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            KVUtils.setWebRTCEnabled(isChecked)
            if (isChecked && etApiBase.text.toString().trim().isEmpty()) {
                Toast.makeText(this, getString(R.string.webrtc_url_required), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<android.widget.Button>(R.id.btnSaveWebRTC).setOnClickListener {
            val selectedMode = MODE_VALUES[spinnerMode.selectedItemPosition]
            KVUtils.setPipelineMode(selectedMode)
            KVUtils.setCyberVerseWsBase(etWsBase.text.toString().trim())
            KVUtils.setCyberVerseApiBase(etApiBase.text.toString().trim())
            KVUtils.setCyberVerseCharacterId(etCharacterId.text.toString().trim())
            KVUtils.setOpenClawWsUrl(etOpenClawWsUrl.text.toString().trim())
            Toast.makeText(this, getString(R.string.webrtc_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnClearWebRTC).setOnClickListener {
            switchEnabled.isChecked = false
            spinnerMode.setSelection(0) // Reset to VoiceLLM
            etWsBase.setText("")
            etApiBase.setText("")
            etCharacterId.setText("")
            etOpenClawWsUrl.setText("")
            KVUtils.setWebRTCEnabled(false)
            KVUtils.setPipelineMode("omni")
            KVUtils.setCyberVerseWsBase("")
            KVUtils.setCyberVerseApiBase("")
            KVUtils.setCyberVerseCharacterId("")
            KVUtils.setOpenClawWsUrl("")
            Toast.makeText(this, getString(R.string.webrtc_config_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModeUI(position: Int, tvModeDesc: TextView, layoutOpenClaw: View) {
        tvModeDesc.text = MODE_DESCRIPTIONS[position]
        if (position == 1) {
            // Standard/OpenClaw mode: show OpenClaw brain URL field
            layoutOpenClaw.visibility = View.VISIBLE
        } else {
            // VoiceLLM mode: hide OpenClaw brain URL field
            layoutOpenClaw.visibility = View.GONE
        }
    }
}
