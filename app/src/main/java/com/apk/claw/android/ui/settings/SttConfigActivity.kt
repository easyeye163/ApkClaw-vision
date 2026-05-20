package com.apk.claw.android.ui.settings

import android.content.ClipData
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.voice.HttpSttVoiceRecognizer
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * STT（语音识别）配置页面
 *
 * 配置 HTTP STT 服务的 Base URL、API Key 和模型名称，
 * 支持导出/导入配置到剪贴板。
 */
class SttConfigActivity : BaseActivity() {

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stt_config)

        val toolbar = findViewById<CommonToolbar>(R.id.toolbar)
        toolbar.setTitle(getString(R.string.stt_config_title))
        toolbar.showBackButton(true) { finish() }
        toolbar.setActionIcon(R.drawable.ic_export) { showImportExportMenu() }

        val etBaseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etModelName = findViewById<EditText>(R.id.etModelName)

        etBaseUrl.setText(KVUtils.getSttBaseUrl())
        etApiKey.setText(KVUtils.getSttApiKey())
        etModelName.setText(KVUtils.getSttModel())

        findViewById<KButton>(R.id.btnSave).setOnClickListener {
            val baseUrl = etBaseUrl.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()
            var model = etModelName.text.toString().trim()
            if (model.isEmpty()) model = HttpSttVoiceRecognizer.DEFAULT_STT_MODEL

            if (baseUrl.isEmpty()) {
                Toast.makeText(this, getString(R.string.stt_config_base_url_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            KVUtils.setSttBaseUrl(baseUrl)
            KVUtils.setSttApiKey(apiKey)
            KVUtils.setSttModel(model)
            Toast.makeText(this, getString(R.string.stt_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<KButton>(R.id.btnClear).setOnClickListener {
            KVUtils.setSttBaseUrl("")
            KVUtils.setSttApiKey("")
            KVUtils.setSttModel(HttpSttVoiceRecognizer.DEFAULT_STT_MODEL)
            Toast.makeText(this, getString(R.string.stt_config_cleared), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showImportExportMenu() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.stt_import_export))
            .setItems(
                arrayOf(getString(R.string.stt_export_clipboard), getString(R.string.stt_import_clipboard))
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
            addProperty("type", "stt_config")
            addProperty("base_url", KVUtils.getSttBaseUrl())
            addProperty("api_key", KVUtils.getSttApiKey())
            addProperty("model", KVUtils.getSttModel())
        }
        val jsonStr = gson.toJson(json)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("stt_config", jsonStr))
        Toast.makeText(this, getString(R.string.stt_export_success), Toast.LENGTH_SHORT).show()
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, getString(R.string.stt_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.stt_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val baseUrl = json.get("base_url")?.asString ?: ""
            val apiKey = json.get("api_key")?.asString ?: ""
            var model = json.get("model")?.asString ?: ""
            if (model.isEmpty()) model = HttpSttVoiceRecognizer.DEFAULT_STT_MODEL

            if (baseUrl.isEmpty()) {
                Toast.makeText(this, getString(R.string.stt_import_invalid_format), Toast.LENGTH_SHORT).show()
                return
            }

            findViewById<EditText>(R.id.etBaseUrl).setText(baseUrl)
            findViewById<EditText>(R.id.etApiKey).setText(apiKey)
            findViewById<EditText>(R.id.etModelName).setText(model)
            Toast.makeText(this, getString(R.string.stt_import_filled), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.stt_import_invalid_format), Toast.LENGTH_SHORT).show()
        }
    }
}
