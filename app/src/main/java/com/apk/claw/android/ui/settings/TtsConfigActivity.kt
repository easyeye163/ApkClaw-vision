package com.apk.claw.android.ui.settings

import android.content.ClipData
import android.content.DialogInterface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.floating.voice.TtsManager
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.widget.CommonToolbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.Locale

class TtsConfigActivity : BaseActivity() {

    companion object {
        private val LANGUAGES = listOf(
            "zh-CN" to "中文",
            "en-US" to "English",
            "ja-JP" to "日本語",
            "ko-KR" to "한국어",
            "fr-FR" to "Français",
            "de-DE" to "Deutsch"
        )
    }

    private val gson = Gson()

    private lateinit var switchEnable: Switch
    private lateinit var spinnerLanguage: Spinner
    private lateinit var seekbarRate: SeekBar
    private lateinit var seekbarPitch: SeekBar
    private lateinit var tvRateValue: TextView
    private lateinit var tvPitchValue: TextView
    private var ttsForTest: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tts_config)

        val toolbar = findViewById<CommonToolbar>(R.id.toolbar)
        toolbar.setTitle(getString(R.string.tts_config_title))
        toolbar.showBackButton(true) { finish() }
        toolbar.setActionIcon(R.drawable.ic_export) { showImportExportMenu() }

        switchEnable = findViewById(R.id.switchTtsEnable)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        seekbarRate = findViewById(R.id.seekbarSpeechRate)
        seekbarPitch = findViewById(R.id.seekbarPitch)
        tvRateValue = findViewById(R.id.tvSpeechRateValue)
        tvPitchValue = findViewById(R.id.tvPitchValue)

        setupLanguageSpinner()
        loadSavedValues()

        seekbarRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val rate = (progress + 10) / 100f
                tvRateValue.text = "%.2f".format(rate)
            }
        })

        seekbarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val pitch = (progress + 50) / 100f
                tvPitchValue.text = "%.2f".format(pitch)
            }
        })

        findViewById<View>(R.id.btnTestTts).setOnClickListener { testTts() }
        findViewById<View>(R.id.btnSaveTts).setOnClickListener { saveSettings() }
    }

    private fun setupLanguageSpinner() {
        val labels = LANGUAGES.map { "${it.first} ${it.second}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinnerLanguage.adapter = adapter
    }

    private fun loadSavedValues() {
        switchEnable.isChecked = KVUtils.isTtsEnabled()

        val savedLang = KVUtils.getTtsLanguage()
        val langIndex = LANGUAGES.indexOfFirst { it.first == savedLang }
        if (langIndex >= 0) spinnerLanguage.setSelection(langIndex)

        val savedRate = KVUtils.getTtsSpeechRate()
        seekbarRate.progress = ((savedRate * 100) - 10).toInt().coerceIn(0, 190)
        tvRateValue.text = "%.2f".format(savedRate)

        val savedPitch = KVUtils.getTtsPitch()
        seekbarPitch.progress = ((savedPitch * 100) - 50).toInt().coerceIn(0, 150)
        tvPitchValue.text = "%.2f".format(savedPitch)
    }

    private fun saveSettings() {
        KVUtils.setTtsEnabled(switchEnable.isChecked)

        val langPos = spinnerLanguage.selectedItemPosition
        if (langPos in LANGUAGES.indices) {
            KVUtils.setTtsLanguage(LANGUAGES[langPos].first)
        }

        val rate = (seekbarRate.progress + 10) / 100f
        KVUtils.setTtsSpeechRate(rate)

        val pitch = (seekbarPitch.progress + 50) / 100f
        KVUtils.setTtsPitch(pitch)

        Toast.makeText(this, getString(R.string.tts_config_saved), Toast.LENGTH_SHORT).show()
    }

    private fun testTts() {
        ttsForTest?.shutdown()
        val testText = getString(R.string.tts_config_test_text)
        val langPos = spinnerLanguage.selectedItemPosition
        val locale = if (langPos in LANGUAGES.indices) {
            Locale.forLanguageTag(LANGUAGES[langPos].first)
        } else {
            Locale.CHINESE
        }
        val rate = (seekbarRate.progress + 10) / 100f
        val pitch = (seekbarPitch.progress + 50) / 100f

        ttsForTest = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsForTest?.setLanguage(locale)
                ttsForTest?.setSpeechRate(rate)
                ttsForTest?.setPitch(pitch)
                ttsForTest?.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "tts_test")
            } else {
                runOnUiThread {
                    Toast.makeText(this@TtsConfigActivity, getString(R.string.tts_config_init_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== 导入导出 ====================

    private fun showImportExportMenu() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tts_import_export))
            .setItems(
                arrayOf(getString(R.string.tts_export_clipboard), getString(R.string.tts_import_clipboard))
            ) { _: DialogInterface, which: Int ->
                when (which) {
                    0 -> exportToClipboard()
                    1 -> importFromClipboard()
                }
            }
            .show()
    }

    private fun exportToClipboard() {
        val json = JsonObject().apply {
            addProperty("type", "tts_config")
            addProperty("enabled", KVUtils.isTtsEnabled())
            addProperty("language", KVUtils.getTtsLanguage())
            addProperty("speech_rate", KVUtils.getTtsSpeechRate())
            addProperty("pitch", KVUtils.getTtsPitch())
        }
        val jsonStr = gson.toJson(json)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("tts_config", jsonStr))
        Toast.makeText(this, getString(R.string.tts_export_success), Toast.LENGTH_SHORT).show()
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, getString(R.string.tts_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.tts_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val json = gson.fromJson(text, JsonObject::class.java)

            // 读取 enabled
            val enabled = json.get("enabled")?.asBoolean
            if (enabled != null) switchEnable.isChecked = enabled

            // 读取 language
            val lang = json.get("language")?.asString
            if (lang != null) {
                val langIndex = LANGUAGES.indexOfFirst { it.first == lang }
                if (langIndex >= 0) spinnerLanguage.setSelection(langIndex)
            }

            // 读取 speech_rate
            val rate = json.get("speech_rate")?.asFloat
            if (rate != null) {
                seekbarRate.progress = ((rate * 100) - 10).toInt().coerceIn(0, 190)
                tvRateValue.text = "%.2f".format(rate)
            }

            // 读取 pitch
            val pitch = json.get("pitch")?.asFloat
            if (pitch != null) {
                seekbarPitch.progress = ((pitch * 100) - 50).toInt().coerceIn(0, 150)
                tvPitchValue.text = "%.2f".format(pitch)
            }

            Toast.makeText(this, getString(R.string.tts_import_filled), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.tts_import_invalid_format), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsForTest?.shutdown()
        ttsForTest = null
    }
}
