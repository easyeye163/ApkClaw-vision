package com.apk.claw.android.floating.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS 语音朗读管理器
 *
 * 封装 Android TextToSpeech，用于朗读 AI 回复文本。
 * 支持中文语音，自动过滤 JSON 动作块和代码块。
 *
 * 使用方式：
 *   val tts = TtsManager(context)
 *   tts.speak("你好世界")
 *   tts.shutdown() // 不再使用时调用
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"
    }

    var isReady: Boolean = false
        private set

    @Volatile
    private var lastSpokenText: String = ""

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS init failed: $status")
            return
        }
        tts?.let {
            val lang = com.apk.claw.android.utils.KVUtils.getTtsLanguage()
            val rate = com.apk.claw.android.utils.KVUtils.getTtsSpeechRate()
            val pitch = com.apk.claw.android.utils.KVUtils.getTtsPitch()
            val locale = try { Locale.forLanguageTag(lang) } catch (_: Exception) { Locale.CHINESE }
            it.setLanguage(locale)
            it.setSpeechRate(rate)
            it.setPitch(pitch)
        }
        isReady = true
        Log.i(TAG, "TTS initialized (lang=$lang)")
    }

    /**
     * 朗读文本
     * - 自动过滤 JSON 动作块、代码块、多余空白
     * - 如果文本为空或与上次朗读内容相同则跳过
     */
    fun speak(text: String) {
        if (!isReady) {
            Log.w(TAG, "TTS not ready")
            return
        }
        val sanitized = sanitizeForTts(text)
        if (sanitized.isEmpty() || sanitized == lastSpokenText) {
            return
        }
        lastSpokenText = sanitized
        tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "tts_stream")
    }

    /**
     * 停止当前朗读
     */
    fun stop() {
        lastSpokenText = ""
        tts?.stop()
    }

    /**
     * 释放 TTS 资源
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    /**
     * 清理待朗读文本：去除 JSON 动作块、代码围栏、多余空白
     */
    private fun sanitizeForTts(text: String): String {
        return text
            .replace(Regex("```json[\\s\\S]*?```"), "")
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("\\{[^{}]*\"action\"[^{}]*\\}"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
