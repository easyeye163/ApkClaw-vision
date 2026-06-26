package com.apk.claw.android.utils;

import android.content.Context;
import com.apk.claw.android.voice.HttpSttVoiceRecognizer;
import com.tencent.mmkv.MMKV;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.apache.http.client.config.CookieSpecs;

/* compiled from: KVUtils.kt */
@Metadata(d1 = {"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b%\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\u0011\n\u0002\b\u0004\n\u0002\u0010\u0012\n\u0002\b+\n\u0002\u0018\u0002\n\u0002\b>\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0006\u00105\u001a\u000206J\u000e\u00107\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000eJ\u0011\u00109\u001a\b\u0012\u0004\u0012\u00020\u000e0:¢\u0006\u0002\u0010;J\u0018\u0010<\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\b\b\u0002\u0010=\u001a\u00020\u0004J\u0010\u0010>\u001a\u0004\u0018\u00010?2\u0006\u00108\u001a\u00020\u000eJ\u0006\u0010@\u001a\u00020\u000eJ\u0006\u0010A\u001a\u00020\u000eJ\u0006\u0010B\u001a\u00020\u000eJ\u0006\u0010C\u001a\u00020\u000eJ\u0006\u0010D\u001a\u00020\u000eJ\u0006\u0010E\u001a\u00020\u000eJ\u0006\u0010F\u001a\u00020\u000eJ\u0006\u0010G\u001a\u00020\u000eJ\u0006\u0010H\u001a\u00020\u000eJ\u0018\u0010I\u001a\u00020\u00062\u0006\u00108\u001a\u00020\u000e2\b\b\u0002\u0010=\u001a\u00020\u0006J\u0006\u0010J\u001a\u00020\u000eJ\u0006\u0010K\u001a\u00020\u000eJ\u0018\u0010L\u001a\u00020\b2\u0006\u00108\u001a\u00020\u000e2\b\b\u0002\u0010=\u001a\u00020\bJ\u0018\u0010M\u001a\u00020\n2\u0006\u00108\u001a\u00020\u000e2\b\b\u0002\u0010=\u001a\u00020\nJ\u0006\u0010N\u001a\u00020\u000eJ\u0006\u0010O\u001a\u00020\u000eJ\u0006\u0010P\u001a\u00020\u000eJ\u0006\u0010Q\u001a\u00020\u000eJ\u0006\u0010R\u001a\u00020\u000eJ\u0006\u0010S\u001a\u00020\u000eJ\u0006\u0010T\u001a\u00020\nJ\u0006\u0010U\u001a\u00020\u0006J\u0018\u0010V\u001a\u00020\f2\u0006\u00108\u001a\u00020\u000e2\b\b\u0002\u0010=\u001a\u00020\fJ\u0006\u0010W\u001a\u00020\u000eJ\u0006\u0010X\u001a\u00020\u000eJ\u0006\u0010Y\u001a\u00020\u000eJ\u0006\u0010Z\u001a\u00020\u000eJ\u0018\u0010[\u001a\u00020\u000e2\u0006\u00108\u001a\u00020\u000e2\b\b\u0002\u0010=\u001a\u00020\u000eJ\u0006\u0010\\\u001a\u00020\u000eJ\u0006\u0010]\u001a\u00020\u000eJ\u0006\u0010^\u001a\u00020\u000eJ\u0006\u0010_\u001a\u00020\u000eJ\u0006\u0010`\u001a\u00020\u000eJ\u0006\u0010a\u001a\u00020\u000eJ\u0006\u0010b\u001a\u00020\u000eJ\u0006\u0010c\u001a\u00020\u000eJ\u0006\u0010d\u001a\u00020\u000eJ\u0006\u0010e\u001a\u00020\u0004J\u0006\u0010f\u001a\u00020\u0004J\u0006\u0010g\u001a\u00020\u0004J\u0006\u0010h\u001a\u00020\u0004J\u000e\u0010i\u001a\u0002062\u0006\u0010j\u001a\u00020kJ\u0006\u0010l\u001a\u00020\u0004J\u0006\u0010m\u001a\u00020\u0004J\u0006\u0010n\u001a\u00020\u0004J\u0006\u0010o\u001a\u00020\u0004J\u0006\u0010p\u001a\u00020\u0004J\u0006\u0010q\u001a\u00020\u0004J\u0006\u0010r\u001a\u00020\u0004J\u0006\u0010s\u001a\u00020\u0004J\u0016\u0010t\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\u0006\u0010u\u001a\u00020\u0004J\u0018\u0010v\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\b\u0010u\u001a\u0004\u0018\u00010?J\u0016\u0010w\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\u0006\u0010u\u001a\u00020\u0006J\u0016\u0010x\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\u0006\u0010u\u001a\u00020\bJ\u0016\u0010y\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\u0006\u0010u\u001a\u00020\nJ\u0016\u0010z\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\u0006\u0010u\u001a\u00020\fJ\u0018\u0010{\u001a\u00020\u00042\u0006\u00108\u001a\u00020\u000e2\b\u0010u\u001a\u0004\u0018\u00010\u000eJ\u001f\u0010|\u001a\u0002062\u0012\u0010}\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u000e0:\"\u00020\u000e¢\u0006\u0002\u0010~J\u000e\u0010|\u001a\u0002062\u0006\u00108\u001a\u00020\u000eJ\u000e\u0010\u007f\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u0010\u0010\u0080\u0001\u001a\u00020\u00042\u0007\u0010\u0081\u0001\u001a\u00020\u0004J\u000f\u0010\u0082\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0083\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u0010\u0010\u0084\u0001\u001a\u00020\u00042\u0007\u0010\u0081\u0001\u001a\u00020\u0004J\u000f\u0010\u0085\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0086\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0087\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0088\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0089\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u008a\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u008b\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u008c\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u0010\u0010\u008d\u0001\u001a\u00020\u00042\u0007\u0010\u008e\u0001\u001a\u00020\u0004J\u000f\u0010\u008f\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0090\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0091\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0092\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0093\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u0010\u0010\u0094\u0001\u001a\u00020\u00042\u0007\u0010\u0095\u0001\u001a\u00020\u0004J\u0010\u0010\u0096\u0001\u001a\u00020\u00042\u0007\u0010\u0081\u0001\u001a\u00020\u0004J\u000f\u0010\u0097\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u0098\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\nJ\u000f\u0010\u0099\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u0006J\u000f\u0010\u009a\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u009b\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u009c\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u009d\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u009e\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010\u009f\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010 \u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010¡\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u0010\u0010¢\u0001\u001a\u00020\u00042\u0007\u0010\u0081\u0001\u001a\u00020\u0004J\u000f\u0010£\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010¤\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010¥\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010¦\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u000f\u0010§\u0001\u001a\u00020\u00042\u0006\u0010u\u001a\u00020\u000eJ\u0007\u0010¨\u0001\u001a\u000206R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001a\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u001e\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u001f\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010 \u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010!\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\"\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010$\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010'\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010(\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010)\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010*\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010+\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010,\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010-\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010.\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010/\u001a\u00020\u000eX\u0082T¢\u0006\u0002\n\u0000R\u000e\u00100\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u00101\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u00102\u001a\u00020\u000eX\u0086T¢\u0006\u0002\n\u0000R\u000e\u00103\u001a\u000204X\u0082.¢\u0006\u0002\n\u0000¨\u0006©\u0001"}, d2 = {"Lcom/apk/claw/android/utils/KVUtils;", "", "()V", "DEFAULT_BOOL", "", "DEFAULT_DOUBLE", "", "DEFAULT_FLOAT", "", "DEFAULT_INT", "", "DEFAULT_LONG", "", KVUtils.KEY_CLOUD_CHAT_ENABLED, "", KVUtils.KEY_CLOUD_CHAT_SESSION_ID, KVUtils.KEY_CLOUD_CHAT_WS_URL, KVUtils.KEY_CONFIG_SERVER_ENABLED, KVUtils.KEY_CV_API_BASE, KVUtils.KEY_CV_CHARACTER_ID, KVUtils.KEY_CV_OPENCLAW_WS_URL, KVUtils.KEY_CV_PIPELINE_MODE, KVUtils.KEY_CV_WS_BASE, "KEY_DINGTALK_APP_KEY", "KEY_DINGTALK_APP_SECRET", "KEY_DISCORD_BOT_TOKEN", "KEY_FEISHU_APP_ID", "KEY_FEISHU_APP_SECRET", KVUtils.KEY_GUIDE_SHOWN, KVUtils.KEY_LLM_API_KEY, KVUtils.KEY_LLM_BASE_URL, KVUtils.KEY_LLM_MODEL_NAME, KVUtils.KEY_LOCAL_MODEL_API_KEY, KVUtils.KEY_LOCAL_MODEL_BASE_URL, KVUtils.KEY_LOCAL_MODEL_CHAT_ACTIVE, KVUtils.KEY_LOCAL_MODEL_ENABLED, KVUtils.KEY_LOCAL_MODEL_ID, KVUtils.KEY_LOCAL_MODEL_MAX_TOKENS, KVUtils.KEY_LOCAL_MODEL_TEMPERATURE, "KEY_QQ_APP_ID", "KEY_QQ_APP_SECRET", KVUtils.KEY_STT_API_KEY, KVUtils.KEY_STT_BASE_URL, KVUtils.KEY_STT_MODEL, "KEY_TELEGRAM_BOT_TOKEN", KVUtils.KEY_WEBRTC_ENABLED, KVUtils.KEY_WEBRTC_TOKEN, KVUtils.KEY_WEBRTC_URL, "KEY_WECHAT_API_BASE_URL", "KEY_WECHAT_BOT_TOKEN", "KEY_WECHAT_UPDATES_CURSOR", "mmkv", "Lcom/tencent/mmkv/MMKV;", "clear", "", "contains", "key", "getAllKeys", "", "()[Ljava/lang/String;", "getBoolean", "defaultValue", "getBytes", "", "getChatMode", "getCloudChatSessionId", "getCloudChatWsUrl", "getCyberVerseApiBase", "getCyberVerseCharacterId", "getCyberVerseWsBase", "getDingtalkAppKey", "getDingtalkAppSecret", "getDiscordBotToken", "getDouble", "getFeishuAppId", "getFeishuAppSecret", "getFloat", "getInt", "getLlmApiKey", "getLlmBaseUrl", "getLlmModelName", "getLocalModelApiKey", "getLocalModelBaseUrl", "getLocalModelId", "getLocalModelMaxTokens", "getLocalModelTemperature", "getLong", "getOpenClawWsUrl", "getPipelineMode", "getQqAppId", "getQqAppSecret", "getString", "getSttApiKey", "getSttBaseUrl", "getSttModel", "getTelegramBotToken", "getWebRTCToken", "getWebRTCUrl", "getWechatApiBaseUrl", "getWechatBotToken", "getWechatUpdatesCursor", "hasCyberVerseConfig", "hasLlmConfig", "hasSttConfig", "hasWebRTCConfig", "init", "context", "Landroid/content/Context;", "isChatAvailable", "isCloudChatEnabled", "isConfigServerEnabled", "isGuideShown", "isLocalModelChatActive", "isLocalModelEnabled", "isOpenClawMode", "isWebRTCEnabled", "putBoolean", "value", "putBytes", "putDouble", "putFloat", "putInt", "putLong", "putString", "remove", "keys", "([Ljava/lang/String;)V", "setChatMode", "setCloudChatEnabled", "enabled", "setCloudChatSessionId", "setCloudChatWsUrl", "setConfigServerEnabled", "setCyberVerseApiBase", "setCyberVerseCharacterId", "setCyberVerseWsBase", "setDingtalkAppKey", "setDingtalkAppSecret", "setDiscordBotToken", "setFeishuAppId", "setFeishuAppSecret", "setGuideShown", "shown", "setLlmApiKey", "setLlmBaseUrl", "setLlmModelName", "setLocalModelApiKey", "setLocalModelBaseUrl", "setLocalModelChatActive", "active", "setLocalModelEnabled", "setLocalModelId", "setLocalModelMaxTokens", "setLocalModelTemperature", "setOpenClawWsUrl", "setPipelineMode", "setQqAppId", "setQqAppSecret", "setSttApiKey", "setSttBaseUrl", "setSttModel", "setTelegramBotToken", "setWebRTCEnabled", "setWebRTCToken", "setWebRTCUrl", "setWechatApiBaseUrl", "setWechatBotToken", "setWechatUpdatesCursor", "sync", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class KVUtils {
    private static final boolean DEFAULT_BOOL = false;
    private static final double DEFAULT_DOUBLE = 0.0d;
    private static final float DEFAULT_FLOAT = 0.0f;
    private static final int DEFAULT_INT = 0;
    private static final long DEFAULT_LONG = 0;
    public static final KVUtils INSTANCE = new KVUtils();
    private static final String KEY_CLOUD_CHAT_ENABLED = "KEY_CLOUD_CHAT_ENABLED";
    private static final String KEY_CLOUD_CHAT_SESSION_ID = "KEY_CLOUD_CHAT_SESSION_ID";
    private static final String KEY_CLOUD_CHAT_WS_URL = "KEY_CLOUD_CHAT_WS_URL";
    private static final String KEY_CONFIG_SERVER_ENABLED = "KEY_CONFIG_SERVER_ENABLED";
    private static final String KEY_CV_API_BASE = "KEY_CV_API_BASE";
    private static final String KEY_CV_CHARACTER_ID = "KEY_CV_CHARACTER_ID";
    private static final String KEY_CV_OPENCLAW_WS_URL = "KEY_CV_OPENCLAW_WS_URL";
    private static final String KEY_CV_PIPELINE_MODE = "KEY_CV_PIPELINE_MODE";
    private static final String KEY_CV_WS_BASE = "KEY_CV_WS_BASE";
    public static final String KEY_DINGTALK_APP_KEY = "DEFAULT_DINGTALK_APP_KEY";
    public static final String KEY_DINGTALK_APP_SECRET = "DEFAULT_DINGTALK_APP_SECRET";
    public static final String KEY_DISCORD_BOT_TOKEN = "DEFAULT_DISCORD_BOT_TOKEN";
    public static final String KEY_FEISHU_APP_ID = "DEFAULT_FEISHU_APP_ID";
    public static final String KEY_FEISHU_APP_SECRET = "DEFAULT_FEISHU_APP_SECRET";
    private static final String KEY_GUIDE_SHOWN = "KEY_GUIDE_SHOWN";
    private static final String KEY_LLM_API_KEY = "KEY_LLM_API_KEY";
    private static final String KEY_LLM_BASE_URL = "KEY_LLM_BASE_URL";
    private static final String KEY_LLM_MODEL_NAME = "KEY_LLM_MODEL_NAME";
    private static final String KEY_LOCAL_MODEL_API_KEY = "KEY_LOCAL_MODEL_API_KEY";
    private static final String KEY_LOCAL_MODEL_BASE_URL = "KEY_LOCAL_MODEL_BASE_URL";
    private static final String KEY_LOCAL_MODEL_CHAT_ACTIVE = "KEY_LOCAL_MODEL_CHAT_ACTIVE";
    private static final String KEY_LOCAL_MODEL_ENABLED = "KEY_LOCAL_MODEL_ENABLED";
    private static final String KEY_LOCAL_MODEL_ID = "KEY_LOCAL_MODEL_ID";
    private static final String KEY_LOCAL_MODEL_MAX_TOKENS = "KEY_LOCAL_MODEL_MAX_TOKENS";
    private static final String KEY_LOCAL_MODEL_TEMPERATURE = "KEY_LOCAL_MODEL_TEMPERATURE";
    public static final String KEY_QQ_APP_ID = "DEFAULT_QQ_APP_ID";
    public static final String KEY_QQ_APP_SECRET = "DEFAULT_QQ_APP_SECRET";
    private static final String KEY_STT_API_KEY = "KEY_STT_API_KEY";
    private static final String KEY_STT_BASE_URL = "KEY_STT_BASE_URL";
    private static final String KEY_STT_MODEL = "KEY_STT_MODEL";
    public static final String KEY_TELEGRAM_BOT_TOKEN = "DEFAULT_TELEGRAM_BOT_TOKEN";
    private static final String KEY_WEBRTC_ENABLED = "KEY_WEBRTC_ENABLED";
    private static final String KEY_WEBRTC_TOKEN = "KEY_WEBRTC_TOKEN";
    private static final String KEY_WEBRTC_URL = "KEY_WEBRTC_URL";
    public static final String KEY_WECHAT_API_BASE_URL = "DEFAULT_WECHAT_API_BASE_URL";
    public static final String KEY_WECHAT_BOT_TOKEN = "DEFAULT_WECHAT_BOT_TOKEN";
    public static final String KEY_WECHAT_UPDATES_CURSOR = "DEFAULT_WECHAT_UPDATES_CURSOR";
    private static MMKV mmkv;

    private KVUtils() {
    }

    public final void init(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        MMKV.initialize(context);
        MMKV defaultMMKV = MMKV.defaultMMKV();
        Intrinsics.checkNotNullExpressionValue(defaultMMKV, "defaultMMKV(...)");
        mmkv = defaultMMKV;
    }

    public final boolean putString(String key, String value) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.encode(key, value);
    }

    public static /* synthetic */ String getString$default(KVUtils kVUtils, String str, String str2, int i, Object obj) {
        if ((i & 2) != 0) {
            str2 = "";
        }
        return kVUtils.getString(str, str2);
    }

    public final String getString(String key, String defaultValue) {
        Intrinsics.checkNotNullParameter(key, "key");
        Intrinsics.checkNotNullParameter(defaultValue, "defaultValue");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        String decodeString = mmkv2.decodeString(key, defaultValue);
        return decodeString == null ? defaultValue : decodeString;
    }

    public final boolean putInt(String key, int value) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.encode(key, value);
    }

    public static /* synthetic */ int getInt$default(KVUtils kVUtils, String str, int i, int i2, Object obj) {
        if ((i2 & 2) != 0) {
            i = 0;
        }
        return kVUtils.getInt(str, i);
    }

    public final int getInt(String key, int defaultValue) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.decodeInt(key, defaultValue);
    }

    public final boolean putLong(String key, long value) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.encode(key, value);
    }

    public static /* synthetic */ long getLong$default(KVUtils kVUtils, String str, long j, int i, Object obj) {
        if ((i & 2) != 0) {
            j = 0;
        }
        return kVUtils.getLong(str, j);
    }

    public final long getLong(String key, long defaultValue) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.decodeLong(key, defaultValue);
    }

    public final boolean putBoolean(String key, boolean value) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.encode(key, value);
    }

    public static /* synthetic */ boolean getBoolean$default(KVUtils kVUtils, String str, boolean z, int i, Object obj) {
        if ((i & 2) != 0) {
            z = false;
        }
        return kVUtils.getBoolean(str, z);
    }

    public final boolean getBoolean(String key, boolean defaultValue) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.decodeBool(key, defaultValue);
    }

    public final boolean putFloat(String key, float value) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.encode(key, value);
    }

    public static /* synthetic */ float getFloat$default(KVUtils kVUtils, String str, float f, int i, Object obj) {
        if ((i & 2) != 0) {
            f = 0.0f;
        }
        return kVUtils.getFloat(str, f);
    }

    public final float getFloat(String key, float defaultValue) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.decodeFloat(key, defaultValue);
    }

    public final boolean putDouble(String key, double value) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.encode(key, value);
    }

    public static /* synthetic */ double getDouble$default(KVUtils kVUtils, String str, double d, int i, Object obj) {
        if ((i & 2) != 0) {
            d = 0.0d;
        }
        return kVUtils.getDouble(str, d);
    }

    public final double getDouble(String key, double defaultValue) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.decodeDouble(key, defaultValue);
    }

    public final boolean putBytes(String key, byte[] value) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.encode(key, value);
    }

    public final byte[] getBytes(String key) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.decodeBytes(key);
    }

    public final boolean contains(String key) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        return mmkv2.containsKey(key);
    }

    public final void remove(String key) {
        Intrinsics.checkNotNullParameter(key, "key");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        mmkv2.removeValueForKey(key);
    }

    public final void remove(String... keys) {
        Intrinsics.checkNotNullParameter(keys, "keys");
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        mmkv2.removeValuesForKeys(keys);
    }

    public final void clear() {
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        mmkv2.clearAll();
    }

    public final String[] getAllKeys() {
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        String[] allKeys = mmkv2.allKeys();
        return allKeys == null ? new String[0] : allKeys;
    }

    public final void sync() {
        MMKV mmkv2 = mmkv;
        if (mmkv2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("mmkv");
            mmkv2 = null;
        }
        mmkv2.sync();
    }

    public final boolean isGuideShown() {
        return getBoolean(KEY_GUIDE_SHOWN, false);
    }

    public final boolean setGuideShown(boolean shown) {
        return putBoolean(KEY_GUIDE_SHOWN, shown);
    }

    public final String getDingtalkAppKey() {
        return getString(KEY_DINGTALK_APP_KEY, "");
    }

    public final boolean setDingtalkAppKey(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_DINGTALK_APP_KEY, value);
    }

    public final String getDingtalkAppSecret() {
        return getString(KEY_DINGTALK_APP_SECRET, "");
    }

    public final boolean setDingtalkAppSecret(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_DINGTALK_APP_SECRET, value);
    }

    public final String getFeishuAppId() {
        return getString(KEY_FEISHU_APP_ID, "");
    }

    public final boolean setFeishuAppId(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_FEISHU_APP_ID, value);
    }

    public final String getFeishuAppSecret() {
        return getString(KEY_FEISHU_APP_SECRET, "");
    }

    public final boolean setFeishuAppSecret(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_FEISHU_APP_SECRET, value);
    }

    public final String getQqAppId() {
        return getString(KEY_QQ_APP_ID, "");
    }

    public final boolean setQqAppId(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_QQ_APP_ID, value);
    }

    public final String getQqAppSecret() {
        return getString(KEY_QQ_APP_SECRET, "");
    }

    public final boolean setQqAppSecret(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_QQ_APP_SECRET, value);
    }

    public final String getDiscordBotToken() {
        return getString(KEY_DISCORD_BOT_TOKEN, "");
    }

    public final boolean setDiscordBotToken(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_DISCORD_BOT_TOKEN, value);
    }

    public final String getTelegramBotToken() {
        return getString(KEY_TELEGRAM_BOT_TOKEN, "");
    }

    public final boolean setTelegramBotToken(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_TELEGRAM_BOT_TOKEN, value);
    }

    public final String getWechatBotToken() {
        return getString(KEY_WECHAT_BOT_TOKEN, "");
    }

    public final boolean setWechatBotToken(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_WECHAT_BOT_TOKEN, value);
    }

    public final String getWechatApiBaseUrl() {
        return getString(KEY_WECHAT_API_BASE_URL, "");
    }

    public final boolean setWechatApiBaseUrl(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_WECHAT_API_BASE_URL, value);
    }

    public final String getWechatUpdatesCursor() {
        return getString(KEY_WECHAT_UPDATES_CURSOR, "");
    }

    public final boolean setWechatUpdatesCursor(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_WECHAT_UPDATES_CURSOR, value);
    }

    public final boolean isConfigServerEnabled() {
        return getBoolean(KEY_CONFIG_SERVER_ENABLED, false);
    }

    public final boolean setConfigServerEnabled(boolean enabled) {
        return putBoolean(KEY_CONFIG_SERVER_ENABLED, enabled);
    }

    public final String getLlmApiKey() {
        return getString(KEY_LLM_API_KEY, "");
    }

    public final boolean setLlmApiKey(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_LLM_API_KEY, value);
    }

    public final String getLlmBaseUrl() {
        return getString(KEY_LLM_BASE_URL, "");
    }

    public final boolean setLlmBaseUrl(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_LLM_BASE_URL, value);
    }

    public final String getLlmModelName() {
        return getString(KEY_LLM_MODEL_NAME, "");
    }

    public final boolean setLlmModelName(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_LLM_MODEL_NAME, value);
    }

    public final boolean hasLlmConfig() {
        return getLlmApiKey().length() > 0;
    }

    public final boolean isChatAvailable() {
        return hasLlmConfig() || isLocalModelChatActive();
    }

    public final String getSttBaseUrl() {
        return getString(KEY_STT_BASE_URL, "");
    }

    public final boolean setSttBaseUrl(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_STT_BASE_URL, value);
    }

    public final String getSttApiKey() {
        return getString(KEY_STT_API_KEY, "");
    }

    public final boolean setSttApiKey(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_STT_API_KEY, value);
    }

    public final String getSttModel() {
        return getString(KEY_STT_MODEL, HttpSttVoiceRecognizer.DEFAULT_STT_MODEL);
    }

    public final boolean setSttModel(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_STT_MODEL, value);
    }

    public final boolean hasSttConfig() {
        return getSttBaseUrl().length() > 0;
    }

    public final String getLocalModelId() {
        return getString(KEY_LOCAL_MODEL_ID, "qwen2.5-1.5b-q4");
    }

    public final boolean setLocalModelId(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_LOCAL_MODEL_ID, value);
    }

    public final boolean isLocalModelEnabled() {
        return getBoolean(KEY_LOCAL_MODEL_ENABLED, false);
    }

    public final boolean setLocalModelEnabled(boolean enabled) {
        return putBoolean(KEY_LOCAL_MODEL_ENABLED, enabled);
    }

    public final double getLocalModelTemperature() {
        return getDouble(KEY_LOCAL_MODEL_TEMPERATURE, 0.7d);
    }

    public final boolean setLocalModelTemperature(double value) {
        return putDouble(KEY_LOCAL_MODEL_TEMPERATURE, value);
    }

    public final int getLocalModelMaxTokens() {
        return getInt(KEY_LOCAL_MODEL_MAX_TOKENS, 512);
    }

    public final boolean setLocalModelMaxTokens(int value) {
        return putInt(KEY_LOCAL_MODEL_MAX_TOKENS, value);
    }

    public final String getLocalModelBaseUrl() {
        return getString(KEY_LOCAL_MODEL_BASE_URL, "http://localhost:8080/v1");
    }

    public final boolean setLocalModelBaseUrl(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_LOCAL_MODEL_BASE_URL, value);
    }

    public final String getLocalModelApiKey() {
        return getString(KEY_LOCAL_MODEL_API_KEY, "local");
    }

    public final boolean setLocalModelApiKey(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_LOCAL_MODEL_API_KEY, value);
    }

    public final boolean isLocalModelChatActive() {
        return getBoolean(KEY_LOCAL_MODEL_CHAT_ACTIVE, false);
    }

    public final boolean setLocalModelChatActive(boolean active) {
        return putBoolean(KEY_LOCAL_MODEL_CHAT_ACTIVE, active);
    }

    public final boolean isCloudChatEnabled() {
        return getBoolean(KEY_CLOUD_CHAT_ENABLED, true);
    }

    public final boolean setCloudChatEnabled(boolean enabled) {
        return putBoolean(KEY_CLOUD_CHAT_ENABLED, enabled);
    }

    public final String getCloudChatWsUrl() {
        return getString(KEY_CLOUD_CHAT_WS_URL, "ws://7110f985.r21.cpolar.top");
    }

    public final boolean setCloudChatWsUrl(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_CLOUD_CHAT_WS_URL, value);
    }

    public final String getCloudChatSessionId() {
        return getString(KEY_CLOUD_CHAT_SESSION_ID, "");
    }

    public final boolean setCloudChatSessionId(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_CLOUD_CHAT_SESSION_ID, value);
    }

    public final boolean isWebRTCEnabled() {
        return getBoolean(KEY_WEBRTC_ENABLED, true);
    }

    public final boolean setWebRTCEnabled(boolean enabled) {
        return putBoolean(KEY_WEBRTC_ENABLED, enabled);
    }

    public final String getWebRTCUrl() {
        return getString(KEY_WEBRTC_URL, "");
    }

    public final boolean setWebRTCUrl(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_WEBRTC_URL, value);
    }

    public final String getWebRTCToken() {
        return getString(KEY_WEBRTC_TOKEN, "");
    }

    public final boolean setWebRTCToken(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_WEBRTC_TOKEN, value);
    }

    public final boolean hasWebRTCConfig() {
        return getWebRTCUrl().length() > 0;
    }

    public final String getCyberVerseWsBase() {
        return getString(KEY_CV_WS_BASE, "ws://73e09112.r21.cpolar.top");
    }

    public final boolean setCyberVerseWsBase(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_CV_WS_BASE, value);
    }

    public final String getCyberVerseApiBase() {
        return getString(KEY_CV_API_BASE, "http://73e09112.r21.cpolar.top/api/v1");
    }

    public final boolean setCyberVerseApiBase(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_CV_API_BASE, value);
    }

    public final String getCyberVerseCharacterId() {
        return getString(KEY_CV_CHARACTER_ID, "claw_3a375b3f");
    }

    public final boolean setCyberVerseCharacterId(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_CV_CHARACTER_ID, value);
    }

    public final String getPipelineMode() {
        return getString(KEY_CV_PIPELINE_MODE, "omni");
    }

    public final boolean setPipelineMode(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_CV_PIPELINE_MODE, value);
    }

    public final String getChatMode() {
        return Intrinsics.areEqual(getPipelineMode(), CookieSpecs.STANDARD) ? "openclaw" : "voicellm";
    }

    public final boolean setChatMode(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return setPipelineMode(Intrinsics.areEqual(value, "openclaw") ? CookieSpecs.STANDARD : "omni");
    }

    public final boolean isOpenClawMode() {
        return Intrinsics.areEqual(getPipelineMode(), CookieSpecs.STANDARD);
    }

    public final String getOpenClawWsUrl() {
        return getString(KEY_CV_OPENCLAW_WS_URL, "ws://7110f985.r21.cpolar.top");
    }

    public final boolean setOpenClawWsUrl(String value) {
        Intrinsics.checkNotNullParameter(value, "value");
        return putString(KEY_CV_OPENCLAW_WS_URL, value);
    }

    public final boolean hasCyberVerseConfig() {
        return getCyberVerseApiBase().length() > 0 && getCyberVerseCharacterId().length() > 0;
    }
}
