package com.apk.claw.android.voice

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音输入控制器
 *
 * 使用 HttpSttVoiceRecognizer（AudioRecord + HTTP STT）替代 Android SpeechRecognizer，
 * 完全避免 ERROR_RECOGNIZER_BUSY(8) 和 ERROR_CLIENT(11) 等国产 ROM 兼容问题。
 *
 * 核心设计：
 * 1. AudioRecord 录制 PCM 16kHz 单声道
 * 2. 松手后转为 WAV，通过 HTTP POST 发送到 /v1/audio/transcriptions
 * 3. 优先使用独立 STT 配置，未配置时回退使用 LLM 配置
 * 4. 无需依赖 Google SpeechRecognizer
 */
class VoiceInputController(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputController"
    }

    interface Listener {
        /** 语音识别开始（录音中） */
        fun onListeningStarted() {}
        /** 录音结束，正在识别中 */
        fun onTranscribing() {}
        /** 实时中间结果（HTTP STT 模式下不适用，松手后才返回结果） */
        fun onPartialResults(text: String) {}
        /** 最终结果 */
        fun onFinalResult(text: String) {}
        /** 发生错误 */
        fun onError(errorCode: Int, message: String) {}
        /** 录音音量变化 (HTTP STT 模式下不适用) */
        fun onRmsChanged(rmsdB: Float) {}
    }

    private var sttRecognizer: HttpSttVoiceRecognizer? = null
    private val isListeningAtomic = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)

    var listener: Listener? = null

    val isListening: Boolean
        get() = isListeningAtomic.get()

    /**
     * 开始语音识别（开始录音）
     */
    fun startListening() {
        if (isListeningAtomic.get()) {
            Log.w(TAG, "Already listening, ignoring")
            return
        }
        if (destroyed.get()) {
            Log.w(TAG, "Controller destroyed")
            return
        }

        // 销毁旧的识别器
        sttRecognizer?.destroy()

        val recognizer = HttpSttVoiceRecognizer(context.applicationContext)
        recognizer.listener = object : HttpSttVoiceRecognizer.Listener {
            override fun onRecordingStarted() {
                Log.i(TAG, "Recording started")
                isListeningAtomic.set(true)
                listener?.onListeningStarted()
            }

            override fun onTranscribing() {
                Log.i(TAG, "Transcribing...")
                isListeningAtomic.set(false)
                listener?.onTranscribing()
            }

            override fun onResult(text: String) {
                Log.i(TAG, "Result: $text")
                isListeningAtomic.set(false)
                listener?.onFinalResult(text)
            }

            override fun onError(message: String) {
                Log.w(TAG, "Error: $message")
                isListeningAtomic.set(false)
                listener?.onError(ERROR_HTTP_STT, message)
            }
        }
        sttRecognizer = recognizer
        recognizer.startRecording()
    }

    /**
     * 停止语音识别（停止录音并发送识别）
     */
    fun stopListening() {
        if (!isListeningAtomic.get()) return
        Log.i(TAG, "stopListening")
        sttRecognizer?.stopRecording()
    }

    /** HTTP STT 错误码常量 */
    private val ERROR_HTTP_STT = 100

    /**
     * 完全释放控制器资源
     */
    fun destroy() {
        destroyed.set(true)
        isListeningAtomic.set(false)
        sttRecognizer?.destroy()
        sttRecognizer = null
        listener = null
    }
}
