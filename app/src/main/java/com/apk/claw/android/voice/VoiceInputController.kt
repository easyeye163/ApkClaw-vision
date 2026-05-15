package com.apk.claw.android.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音输入控制器
 * 参考 X-OmniClaw 的 VoiceRecorderManager 设计思路，
 * 封装 SpeechRecognizer 生命周期管理，避免 ERROR_RECOGNIZER_BUSY(8) 和 ERROR_CLIENT(11)。
 *
 * 核心设计：
 * 1. 每次开始前彻底销毁旧实例，避免 busy 冲突
 * 2. 设置 EXTRA_CALLING_PACKAGE，部分国产 ROM 需要此参数
 * 3. 错误后延迟重试，避免立即重建触发 11
 * 4. 不在 onEndOfSpeech 里主动 stopListening，让引擎自然结束
 */
class VoiceInputController(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputController"
        /** 销毁后重建的冷却时间(ms)，避免连续创建触发 ERROR_CLIENT(11) */
        private const val RECREATE_COOLDOWN_MS = 300L
    }

    interface Listener {
        /** 语音识别开始（onReadyForSpeech） */
        fun onListeningStarted() {}
        /** 实时中间结果 */
        fun onPartialResults(text: String) {}
        /** 最终结果 */
        fun onFinalResult(text: String) {}
        /** 发生错误 */
        fun onError(errorCode: Int, message: String) {}
        /** 录音音量变化 (0-15) */
        fun onRmsChanged(rmsdB: Float) {}
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val isListeningAtomic = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    var listener: Listener? = null

    val isListening: Boolean
        get() = isListeningAtomic.get()

    /**
     * 开始语音识别
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

        // 先销毁旧的 Recognizer
        destroyRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechRecognizer", e)
            listener?.onError(SpeechRecognizer.ERROR_CLIENT, "无法创建语音识别器")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, "zh-CN,en-US")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // 部分设备需要 EXTRA_PROMPT 才能正常启动
            putExtra(RecognizerIntent.EXTRA_PROMPT, "")
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "onReadyForSpeech")
                isListeningAtomic.set(true)
                listener?.onListeningStarted()
            }

            override fun onBeginningOfSpeech() {
                Log.i(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                listener?.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.i(TAG, "onEndOfSpeech")
                // 不在这里 stopListening，让引擎自然结束
            }

            override fun onError(error: Int) {
                Log.w(TAG, "onError: code=$error")
                isListeningAtomic.set(false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音输入"
                    SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别器忙碌，请重试"
                    SpeechRecognizer.ERROR_CLIENT -> "语音识别客户端错误，请重试"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    else -> "语音识别错误($error)"
                }
                listener?.onError(error, msg)

                // 延迟销毁，避免立即重建触发 ERROR_CLIENT(11) 或 ERROR_RECOGNIZER_BUSY(8)
                mainHandler.postDelayed({
                    destroyRecognizer()
                }, RECREATE_COOLDOWN_MS)
            }

            override fun onResults(results: Bundle?) {
                Log.i(TAG, "onResults")
                isListeningAtomic.set(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.i(TAG, "Recognized: $text")
                    listener?.onFinalResult(text)
                } else {
                    listener?.onError(SpeechRecognizer.ERROR_NO_MATCH, "未识别到语音")
                }
                destroyRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    listener?.onPartialResults(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "startListening called")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            isListeningAtomic.set(false)
            listener?.onError(SpeechRecognizer.ERROR_CLIENT, "启动语音识别失败: ${e.message}")
            destroyRecognizer()
        }
    }

    /**
     * 停止语音识别（用户主动松手时调用）
     */
    fun stopListening() {
        if (!isListeningAtomic.get()) return
        Log.i(TAG, "stopListening")
        isListeningAtomic.set(false)
        try {
            // 注意：某些设备调用 stopListening 会触发 ERROR_CLIENT(11)
            // 仅在 isListening 为 true 时调用
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening error", e)
        }
        // 延迟销毁，让 onResults 或 onError 有时间回调
        mainHandler.postDelayed({
            destroyRecognizer()
        }, RECREATE_COOLDOWN_MS)
    }

    /**
     * 销毁 SpeechRecognizer 实例
     */
    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroyRecognizer error", e)
        }
        speechRecognizer = null
        isListeningAtomic.set(false)
    }

    /**
     * 完全释放控制器资源
     */
    fun destroy() {
        destroyed.set(true)
        isListeningAtomic.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        listener = null
    }
}
