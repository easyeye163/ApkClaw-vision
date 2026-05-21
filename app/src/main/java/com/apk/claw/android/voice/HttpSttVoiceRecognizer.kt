package com.apk.claw.android.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.utils.XLog
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HTTP STT 语音识别器
 *
 * 使用 AudioRecord 录制 PCM 音频，松手后转为 WAV，通过 HTTP 发送到 OpenAI 兼容的
 * /v1/audio/transcriptions 接口进行语音转文字。
 *
 * 完全不依赖 Android SpeechRecognizer，兼容国产 ROM（OPPO、vivo 等）。
 *
 * 依赖独立 STT 配置（设置 > 模型 > STT 配置）：
 * - baseUrl: 需指向兼容 OpenAI /v1/audio/transcriptions 的服务
 * - apiKey: 用于鉴权
 * - model: STT 模型名称
 *
 * 如果未配置独立 STT，回退使用 LLM 的 baseUrl 和 apiKey。
 */
class HttpSttVoiceRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "HttpSttVoiceRecognizer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TIMEOUT_SECONDS = 30L
        private const val MIN_RECORDING_BYTES = 3200  // 16kHz * 2bytes * 0.1s = 最短有效录音

        /** 默认 STT 模型 */
        const val DEFAULT_STT_MODEL = "whisper-1"
    }

    interface Listener {
        /** 录音开始 */
        fun onRecordingStarted() {}
        /** 录音结束，正在识别中 */
        fun onTranscribing() {}
        /** 识别成功 */
        fun onResult(text: String) {}
        /** 发生错误 */
        fun onError(message: String) {}
    }

    var listener: Listener? = null
    var sttModel: String = DEFAULT_STT_MODEL

    private val isRecording = AtomicBoolean(false)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pcmBuffer: ByteArrayOutputStream? = null

    val recording: Boolean
        get() = isRecording.get()

    /**
     * 开始录音
     */
    fun startRecording() {
        if (isRecording.get()) {
            XLog.w(TAG, "Already recording")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            listener?.onError("音频参数不支持")
            XLog.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener?.onError("录音器初始化失败")
                XLog.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            pcmBuffer = ByteArrayOutputStream()
            audioRecord?.startRecording()
            isRecording.set(true)
            listener?.onRecordingStarted()
            XLog.i(TAG, "Recording started (PCM 16kHz mono)")

            recordingJob = recordingScope.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        pcmBuffer?.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: SecurityException) {
            isRecording.set(false)
            listener?.onError("录音权限不足")
            XLog.e(TAG, "SecurityException: ${e.message}")
        } catch (e: Exception) {
            isRecording.set(false)
            listener?.onError("录音启动失败: ${e.message}")
            XLog.e(TAG, "Failed to start recording", e)
        }
    }

    /**
     * 停止录音并发送音频进行 STT 识别
     */
    fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)

        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            XLog.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
        XLog.i(TAG, "Recording stopped")

        val pcmData = pcmBuffer?.toByteArray()
        pcmBuffer = null

        if (pcmData == null || pcmData.size < MIN_RECORDING_BYTES) {
            listener?.onError("录音太短，请长按说话")
            XLog.w(TAG, "Recording too short: ${pcmData?.size ?: 0} bytes")
            return
        }

        listener?.onTranscribing()
        recordingScope.launch {
            transcribeAudio(pcmData)
        }
    }

    /**
     * 将 PCM 数据转为 WAV 并发送到 STT API
     */
    private suspend fun transcribeAudio(pcmData: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val wavData = pcmToWav(pcmData, SAMPLE_RATE, 1, 16)
            XLog.i(TAG, "WAV data: ${wavData.size} bytes, sending to STT...")

            // 仅从独立 STT 配置获取
            val baseUrl = KVUtils.getSttBaseUrl().trimEnd('/')
            val apiKey = KVUtils.getSttApiKey()
            val sttModelFromConfig = KVUtils.getSttModel()
            if (sttModelFromConfig.isNotEmpty()) {
                sttModel = sttModelFromConfig
            }

            XLog.i(TAG, "STT config: baseUrl=$baseUrl, apiKey=${if (apiKey.isNotEmpty()) "***" else "(empty)"}, model=$sttModel")

            if (baseUrl.isEmpty()) {
                listener?.onError("请先配置 STT（设置 > 模型 > STT 配置）")
                return@withContext
            }

            // 构造 STT API URL（智能拼接，避免路径重复）
            val sttUrl = when {
                baseUrl.endsWith("/audio/transcriptions") -> baseUrl
                baseUrl.contains("/audio/transcriptions") -> baseUrl
                baseUrl.endsWith("/v1") -> "$baseUrl/audio/transcriptions"
                baseUrl.contains("/v1/") -> "${baseUrl}audio/transcriptions"
                else -> "$baseUrl/v1/audio/transcriptions"
            }

            XLog.i(TAG, "STT request URL: $sttUrl")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", sttModel)
                .addFormDataPart("language", "zh")
                .addFormDataPart(
                    "file",
                    "recording.wav",
                    wavData.toRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(sttUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    XLog.e(TAG, "STT HTTP ${response.code}: $body")
                    listener?.onError("语音识别失败(HTTP ${response.code})\nURL: $sttUrl")
                    return@withContext
                }

                val json = JSONObject(body)
                val text = json.optString("text", "").trim()

                if (text.isEmpty()) {
                    listener?.onError("未识别到语音内容")
                    XLog.w(TAG, "STT empty result: $body")
                } else {
                    XLog.i(TAG, "STT result: $text")
                    listener?.onResult(text)
                }
            }
        } catch (e: Exception) {
            XLog.e(TAG, "transcribeAudio failed", e)
            listener?.onError("语音识别失败: ${e.message}")
        }
    }

    /**
     * 释放所有资源
     */
    fun destroy() {
        isRecording.set(false)
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        pcmBuffer = null
        recordingScope.cancel()
        listener = null
    }

    /**
     * 将 PCM 裸数据封装为标准 WAV 格式
     */
    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)              // chunk size
        buffer.putShort(1)             // PCM format
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }
}
