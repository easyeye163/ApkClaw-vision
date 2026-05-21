package com.apk.claw.android.utils

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * MMKV 键值存储工具类
 *
 * 使用方式：
 *   // 在 Application.onCreate 中初始化
 *   KVUtils.init(context)
 *
 *   // 存取数据
 *   KVUtils.putString("key", "value")
 *   val value = KVUtils.getString("key", "default")
 */
object KVUtils {


    // 钉钉配置
    const val KEY_DINGTALK_APP_KEY = "DEFAULT_DINGTALK_APP_KEY"
    const val KEY_DINGTALK_APP_SECRET = "DEFAULT_DINGTALK_APP_SECRET"
    // 飞书配置
    const val KEY_FEISHU_APP_ID = "DEFAULT_FEISHU_APP_ID"
    const val KEY_FEISHU_APP_SECRET = "DEFAULT_FEISHU_APP_SECRET"
    // QQ 机器人配置
    const val KEY_QQ_APP_ID = "DEFAULT_QQ_APP_ID"
    const val KEY_QQ_APP_SECRET = "DEFAULT_QQ_APP_SECRET"
    // Discord 机器人配置
    const val KEY_DISCORD_BOT_TOKEN = "DEFAULT_DISCORD_BOT_TOKEN"
    // Telegram 机器人配置
    const val KEY_TELEGRAM_BOT_TOKEN = "DEFAULT_TELEGRAM_BOT_TOKEN"
    // 微信 iLink Bot 配置
    const val KEY_WECHAT_BOT_TOKEN = "DEFAULT_WECHAT_BOT_TOKEN"
    const val KEY_WECHAT_API_BASE_URL = "DEFAULT_WECHAT_API_BASE_URL"
    const val KEY_WECHAT_UPDATES_CURSOR = "DEFAULT_WECHAT_UPDATES_CURSOR"

    private lateinit var mmkv: MMKV

    private const val DEFAULT_INT = 0
    private const val DEFAULT_LONG = 0L
    private const val DEFAULT_BOOL = false
    private const val DEFAULT_FLOAT = 0f
    private const val DEFAULT_DOUBLE = 0.0

    /**
     * 在 Application.onCreate 中调用初始化
     */
    fun init(context: Context) {
        MMKV.initialize(context)
        mmkv = MMKV.defaultMMKV()
    }

    // ==================== String ====================
    fun putString(key: String, value: String?): Boolean {
        return mmkv.encode(key, value)
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return mmkv.decodeString(key, defaultValue) ?: defaultValue
    }

    // ==================== Int ====================
    fun putInt(key: String, value: Int): Boolean {
        return mmkv.encode(key, value)
    }

    fun getInt(key: String, defaultValue: Int = DEFAULT_INT): Int {
        return mmkv.decodeInt(key, defaultValue)
    }

    // ==================== Long ====================
    fun putLong(key: String, value: Long): Boolean {
        return mmkv.encode(key, value)
    }

    fun getLong(key: String, defaultValue: Long = DEFAULT_LONG): Long {
        return mmkv.decodeLong(key, defaultValue)
    }

    // ==================== Boolean ====================
    fun putBoolean(key: String, value: Boolean): Boolean {
        return mmkv.encode(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = DEFAULT_BOOL): Boolean {
        return mmkv.decodeBool(key, defaultValue)
    }

    // ==================== Float ====================
    fun putFloat(key: String, value: Float): Boolean {
        return mmkv.encode(key, value)
    }

    fun getFloat(key: String, defaultValue: Float = DEFAULT_FLOAT): Float {
        return mmkv.decodeFloat(key, defaultValue)
    }

    // ==================== Double ====================
    fun putDouble(key: String, value: Double): Boolean {
        return mmkv.encode(key, value)
    }

    fun getDouble(key: String, defaultValue: Double = DEFAULT_DOUBLE): Double {
        return mmkv.decodeDouble(key, defaultValue)
    }

    // ==================== Bytes ====================
    fun putBytes(key: String, value: ByteArray?): Boolean {
        return mmkv.encode(key, value)
    }

    fun getBytes(key: String): ByteArray? {
        return mmkv.decodeBytes(key)
    }

    // ==================== 常用操作 ====================
    fun contains(key: String): Boolean {
        return mmkv.containsKey(key)
    }

    fun remove(key: String) {
        mmkv.removeValueForKey(key)
    }

    fun remove(vararg keys: String) {
        mmkv.removeValuesForKeys(keys)
    }

    fun clear() {
        mmkv.clearAll()
    }

    fun getAllKeys(): Array<String> {
        return mmkv.allKeys() ?: emptyArray()
    }

    /**
     * 同步写入磁盘（默认是异步的）
     */
    fun sync() {
        mmkv.sync()
    }


    // ==================== 引导页 ====================
    private const val KEY_GUIDE_SHOWN = "KEY_GUIDE_SHOWN"

    fun isGuideShown(): Boolean = getBoolean(KEY_GUIDE_SHOWN, false)

    fun setGuideShown(shown: Boolean) = putBoolean(KEY_GUIDE_SHOWN, shown)

    // ==================== 钉钉配置 ====================
    fun getDingtalkAppKey(): String = getString(KEY_DINGTALK_APP_KEY, "")
    fun setDingtalkAppKey(value: String) = putString(KEY_DINGTALK_APP_KEY, value)
    fun getDingtalkAppSecret(): String = getString(KEY_DINGTALK_APP_SECRET, "")
    fun setDingtalkAppSecret(value: String) = putString(KEY_DINGTALK_APP_SECRET, value)

    // ==================== 飞书配置 ====================
    fun getFeishuAppId(): String = getString(KEY_FEISHU_APP_ID, "")
    fun setFeishuAppId(value: String) = putString(KEY_FEISHU_APP_ID, value)
    fun getFeishuAppSecret(): String = getString(KEY_FEISHU_APP_SECRET, "")
    fun setFeishuAppSecret(value: String) = putString(KEY_FEISHU_APP_SECRET, value)

    // ==================== QQ 机器人配置 ====================
    fun getQqAppId(): String = getString(KEY_QQ_APP_ID, "")
    fun setQqAppId(value: String) = putString(KEY_QQ_APP_ID, value)
    fun getQqAppSecret(): String = getString(KEY_QQ_APP_SECRET, "")
    fun setQqAppSecret(value: String) = putString(KEY_QQ_APP_SECRET, value)

    // ==================== Discord 机器人配置 ====================
    fun getDiscordBotToken(): String = getString(KEY_DISCORD_BOT_TOKEN, "")
    fun setDiscordBotToken(value: String) = putString(KEY_DISCORD_BOT_TOKEN, value)

    // ==================== Telegram 机器人配置 ====================
    fun getTelegramBotToken(): String = getString(KEY_TELEGRAM_BOT_TOKEN, "")
    fun setTelegramBotToken(value: String) = putString(KEY_TELEGRAM_BOT_TOKEN, value)

    // ==================== 微信 iLink Bot 配置 ====================
    fun getWechatBotToken(): String = getString(KEY_WECHAT_BOT_TOKEN, "")
    fun setWechatBotToken(value: String) = putString(KEY_WECHAT_BOT_TOKEN, value)
    fun getWechatApiBaseUrl(): String = getString(KEY_WECHAT_API_BASE_URL, "")
    fun setWechatApiBaseUrl(value: String) = putString(KEY_WECHAT_API_BASE_URL, value)
    fun getWechatUpdatesCursor(): String = getString(KEY_WECHAT_UPDATES_CURSOR, "")
    fun setWechatUpdatesCursor(value: String) = putString(KEY_WECHAT_UPDATES_CURSOR, value)

    // ==================== 局域网配置服务 ====================
    private const val KEY_CONFIG_SERVER_ENABLED = "KEY_CONFIG_SERVER_ENABLED"
    fun isConfigServerEnabled(): Boolean = getBoolean(KEY_CONFIG_SERVER_ENABLED, false)
    fun setConfigServerEnabled(enabled: Boolean) = putBoolean(KEY_CONFIG_SERVER_ENABLED, enabled)

    private const val KEY_LLM_API_KEY = "KEY_LLM_API_KEY"
    private const val KEY_LLM_BASE_URL = "KEY_LLM_BASE_URL"
    private const val KEY_LLM_MODEL_NAME = "KEY_LLM_MODEL_NAME"
    // 本地模型配置
    private const val KEY_LOCAL_MODEL_ID = "KEY_LOCAL_MODEL_ID"
    private const val KEY_LOCAL_MODEL_TEMPERATURE = "KEY_LOCAL_MODEL_TEMPERATURE"
    private const val KEY_LOCAL_MODEL_MAX_TOKENS = "KEY_LOCAL_MODEL_MAX_TOKENS"
    private const val KEY_LOCAL_MODEL_BASE_URL = "KEY_LOCAL_MODEL_BASE_URL"
    private const val KEY_LOCAL_MODEL_API_KEY = "KEY_LOCAL_MODEL_API_KEY"
    private const val KEY_LOCAL_MODEL_CHAT_ACTIVE = "KEY_LOCAL_MODEL_CHAT_ACTIVE"
    private const val KEY_LOCAL_MODEL_ENABLED = "KEY_LOCAL_MODEL_ENABLED"

    fun getLlmApiKey(): String = getString(KEY_LLM_API_KEY, "")
    fun setLlmApiKey(value: String) = putString(KEY_LLM_API_KEY, value)
    fun getLlmBaseUrl(): String = getString(KEY_LLM_BASE_URL, "")
    fun setLlmBaseUrl(value: String) = putString(KEY_LLM_BASE_URL, value)
    fun getLlmModelName(): String = getString(KEY_LLM_MODEL_NAME, "")
    fun setLlmModelName(value: String) = putString(KEY_LLM_MODEL_NAME, value)

    /** 是否已配置 LLM（API Key 非空即视为已配置） */
    fun hasLlmConfig(): Boolean = getLlmApiKey().isNotEmpty()

    /** 是否可用聊天（云端 LLM 或本地模型已激活） */
    fun isChatAvailable(): Boolean = hasLlmConfig() || isLocalModelChatActive()

    // ==================== 本地模型配置 ====================
    fun getLocalModelId(): String = getString(KEY_LOCAL_MODEL_ID, "qwen2.5-1.5b-q4")
    fun setLocalModelId(value: String): Boolean = putString(KEY_LOCAL_MODEL_ID, value)

    fun isLocalModelEnabled(): Boolean = getBoolean(KEY_LOCAL_MODEL_ENABLED, false)
    fun setLocalModelEnabled(enabled: Boolean): Boolean = putBoolean(KEY_LOCAL_MODEL_ENABLED, enabled)

    fun getLocalModelTemperature(): Double = getDouble(KEY_LOCAL_MODEL_TEMPERATURE, 0.7)
    fun setLocalModelTemperature(value: Double): Boolean = putDouble(KEY_LOCAL_MODEL_TEMPERATURE, value)

    fun getLocalModelMaxTokens(): Int = getInt(KEY_LOCAL_MODEL_MAX_TOKENS, 512)
    fun setLocalModelMaxTokens(value: Int): Boolean = putInt(KEY_LOCAL_MODEL_MAX_TOKENS, value)

    fun getLocalModelBaseUrl(): String = getString(KEY_LOCAL_MODEL_BASE_URL, "http://localhost:8080/v1")
    fun setLocalModelBaseUrl(value: String): Boolean = putString(KEY_LOCAL_MODEL_BASE_URL, value)

    fun getLocalModelApiKey(): String = getString(KEY_LOCAL_MODEL_API_KEY, "local")
    fun setLocalModelApiKey(value: String): Boolean = putString(KEY_LOCAL_MODEL_API_KEY, value)

    fun isLocalModelChatActive(): Boolean = getBoolean(KEY_LOCAL_MODEL_CHAT_ACTIVE, false)
    fun setLocalModelChatActive(active: Boolean): Boolean = putBoolean(KEY_LOCAL_MODEL_CHAT_ACTIVE, active)

    // ==================== STT（语音识别）配置 ====================
    private const val KEY_STT_BASE_URL = "KEY_STT_BASE_URL"
    private const val KEY_STT_API_KEY = "KEY_STT_API_KEY"
    private const val KEY_STT_MODEL = "KEY_STT_MODEL"

    fun getSttBaseUrl(): String = getString(KEY_STT_BASE_URL, "")
    fun setSttBaseUrl(value: String): Boolean = putString(KEY_STT_BASE_URL, value)

    fun getSttApiKey(): String = getString(KEY_STT_API_KEY, "")
    fun setSttApiKey(value: String): Boolean = putString(KEY_STT_API_KEY, value)

    fun getSttModel(): String = getString(KEY_STT_MODEL, "")
    fun setSttModel(value: String): Boolean = putString(KEY_STT_MODEL, value)

    fun hasSttConfig(): Boolean = getSttBaseUrl().isNotEmpty()

    // ==================== 云端对话模式 ====================
    private const val KEY_CLOUD_CHAT_ENABLED = "KEY_CLOUD_CHAT_ENABLED"
    private const val KEY_CLOUD_CHAT_WS_URL = "KEY_CLOUD_CHAT_WS_URL"
    private const val KEY_CLOUD_CHAT_SESSION_ID = "KEY_CLOUD_CHAT_SESSION_ID"

    fun isCloudChatEnabled(): Boolean = getBoolean(KEY_CLOUD_CHAT_ENABLED, true)
    fun setCloudChatEnabled(enabled: Boolean) = putBoolean(KEY_CLOUD_CHAT_ENABLED, enabled)

    fun getCloudChatWsUrl(): String = getString(KEY_CLOUD_CHAT_WS_URL, "ws://7110f985.r21.cpolar.top")
    fun setCloudChatWsUrl(value: String) = putString(KEY_CLOUD_CHAT_WS_URL, value)

    fun getCloudChatSessionId(): String = getString(KEY_CLOUD_CHAT_SESSION_ID, "")
    fun setCloudChatSessionId(value: String) = putString(KEY_CLOUD_CHAT_SESSION_ID, value)

    // ==================== CyberVerse Direct WebRTC 配置 ====================
    private const val KEY_WEBRTC_ENABLED = "KEY_WEBRTC_ENABLED"
    private const val KEY_WEBRTC_URL = "KEY_WEBRTC_URL"
    private const val KEY_WEBRTC_TOKEN = "KEY_WEBRTC_TOKEN"

    fun isWebRTCEnabled(): Boolean = getBoolean(KEY_WEBRTC_ENABLED, false)
    fun setWebRTCEnabled(enabled: Boolean) = putBoolean(KEY_WEBRTC_ENABLED, enabled)

    fun getWebRTCUrl(): String = getString(KEY_WEBRTC_URL, "")
    fun setWebRTCUrl(value: String) = putString(KEY_WEBRTC_URL, value)

    fun getWebRTCToken(): String = getString(KEY_WEBRTC_TOKEN, "")
    fun setWebRTCToken(value: String) = putString(KEY_WEBRTC_TOKEN, value)

    fun hasWebRTCConfig(): Boolean = getWebRTCUrl().isNotEmpty()

    // ==================== CyberVerse Direct 模式配置 ====================
    private const val KEY_CV_WS_BASE = "KEY_CV_WS_BASE"
    private const val KEY_CV_API_BASE = "KEY_CV_API_BASE"
    private const val KEY_CV_CHARACTER_ID = "KEY_CV_CHARACTER_ID"
    private const val KEY_CV_PIPELINE_MODE = "KEY_CV_PIPELINE_MODE"
    private const val KEY_CV_OPENCLAW_WS_URL = "KEY_CV_OPENCLAW_WS_URL"

    fun getCyberVerseWsBase(): String = getString(KEY_CV_WS_BASE, "ws://73e09112.r21.cpolar.top")
    fun setCyberVerseWsBase(value: String) = putString(KEY_CV_WS_BASE, value)

    fun getCyberVerseApiBase(): String = getString(KEY_CV_API_BASE, "http://73e09112.r21.cpolar.top/api/v1")
    fun setCyberVerseApiBase(value: String) = putString(KEY_CV_API_BASE, value)

    fun getCyberVerseCharacterId(): String = getString(KEY_CV_CHARACTER_ID, "claw_3a375b3f")
    fun setCyberVerseCharacterId(value: String) = putString(KEY_CV_CHARACTER_ID, value)

    /**
     * Pipeline 模式: "omni" (VoiceLLM) 或 "standard" (OpenClaw)
     * - omni: VoiceLLM 单流模式，不依赖 OpenClaw brain，服务端内置语音处理
     * - standard: 标准 ASR→LLM→TTS→Avatar 流水线，可选连接 OpenClaw brain (ws://7110f985)
     */
    fun getPipelineMode(): String = getString(KEY_CV_PIPELINE_MODE, "omni")
    fun setPipelineMode(value: String) = putString(KEY_CV_PIPELINE_MODE, value)

    /** 兼容旧接口: 聊天模式别名 */
    fun getChatMode(): String = if (getPipelineMode() == "standard") "openclaw" else "voicellm"
    fun setChatMode(value: String) = setPipelineMode(if (value == "openclaw") "standard" else "omni")

    fun isOpenClawMode(): Boolean = getPipelineMode() == "standard"

    /** OpenClaw brain WebSocket 地址（仅 standard 模式使用） */
    fun getOpenClawWsUrl(): String = getString(KEY_CV_OPENCLAW_WS_URL, "ws://7110f985.r21.cpolar.top")
    fun setOpenClawWsUrl(value: String) = putString(KEY_CV_OPENCLAW_WS_URL, value)

    fun hasCyberVerseConfig(): Boolean = getCyberVerseApiBase().isNotEmpty() && getCyberVerseCharacterId().isNotEmpty()

    // ==================== TTS 语音朗读配置 ====================
    private const val KEY_TTS_ENABLED = "KEY_TTS_ENABLED"
    private const val KEY_TTS_LANGUAGE = "KEY_TTS_LANGUAGE"
    private const val KEY_TTS_SPEECH_RATE = "KEY_TTS_SPEECH_RATE"
    private const val KEY_TTS_PITCH = "KEY_TTS_PITCH"

    /** TTS 语音朗读是否启用（默认关闭） */
    fun isTtsEnabled(): Boolean = getBoolean(KEY_TTS_ENABLED, false)
    fun setTtsEnabled(enabled: Boolean) = putBoolean(KEY_TTS_ENABLED, enabled)

    fun getTtsLanguage(): String = getString(KEY_TTS_LANGUAGE, "zh-CN")
    fun setTtsLanguage(value: String): Boolean = putString(KEY_TTS_LANGUAGE, value)

    fun getTtsSpeechRate(): Float = getFloat(KEY_TTS_SPEECH_RATE, 0.92f)
    fun setTtsSpeechRate(value: Float): Boolean = putFloat(KEY_TTS_SPEECH_RATE, value)

    fun getTtsPitch(): Float = getFloat(KEY_TTS_PITCH, 1.0f)
    fun setTtsPitch(value: Float): Boolean = putFloat(KEY_TTS_PITCH, value)
}
