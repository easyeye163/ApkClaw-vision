package com.apk.claw.android.service.monitor;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import androidx.constraintlayout.core.motion.utils.TypedValues;
import androidx.core.app.NotificationCompat;
import com.apk.claw.android.R;
import com.apk.claw.android.floating.voice.VoiceStreamFloatWindow;
import com.apk.claw.android.utils.KVUtils;
import com.apk.claw.android.utils.XLog;
import com.apk.claw.android.vision.VisionFrameBuffer;
import com.fasterxml.jackson.core.JsonPointer;
import com.google.gson.Gson;
import com.lark.oapi.ws.Constant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.Boxing;
import kotlin.io.CloseableKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorKt;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;

/* compiled from: StreamMonitorController.kt */
@Metadata(d1 = {"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\b\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0016\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eH\u0082@¢\u0006\u0002\u0010\u001fJ\u001e\u0010 \u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010!\u001a\u00020\"H\u0082@¢\u0006\u0002\u0010#J \u0010$\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010!\u001a\u00020\"H\u0082@¢\u0006\u0002\u0010#J\u0010\u0010%\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eH\u0002J\u0018\u0010&\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010'\u001a\u00020\u0004H\u0002J\u0006\u0010\u0011\u001a\u00020(J \u0010)\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010*\u001a\u00020\u00042\u0006\u0010+\u001a\u00020\u0004H\u0002J\u000e\u0010,\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eJ \u0010,\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010-\u001a\u00020\u00042\b\b\u0002\u0010.\u001a\u00020\u000bJ\u0006\u0010/\u001a\u00020\u001cR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0007X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0007X\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u001e\u0010\u000e\u001a\u00020\u00072\u0006\u0010\r\u001a\u00020\u0007@BX\u0086\u000e¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0007X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0017X\u0082\u000e¢\u0006\u0002\n\u0000R\u001e\u0010\u0018\u001a\u00020\u00042\u0006\u0010\r\u001a\u00020\u0004@BX\u0086\u000e¢\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001a¨\u00060"}, d2 = {"Lcom/apk/claw/android/service/monitor/StreamMonitorController;", "", "()V", "CHANNEL_ID", "", "CHANNEL_NAME", "DEFAULT_INTERVAL_MS", "", "MIN_INTERVAL_MS", "MIN_NOTIFY_INTERVAL_MS", "NOTIFICATION_ID", "", "TAG", "<set-?>", "intervalMs", "getIntervalMs", "()J", "isRunning", "Ljava/util/concurrent/atomic/AtomicBoolean;", "lastNotifyTimeMs", "monitorJob", "Lkotlinx/coroutines/Job;", "monitorScope", "Lkotlinx/coroutines/CoroutineScope;", "monitorTask", "getMonitorTask", "()Ljava/lang/String;", "analyzeFrame", "", "app", "Landroid/app/Application;", "(Landroid/app/Application;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "analyzeFrameHttp", TypedValues.AttributesType.S_FRAME, "Lcom/apk/claw/android/vision/VisionFrameBuffer$FrameEntry;", "(Landroid/app/Application;Lcom/apk/claw/android/vision/VisionFrameBuffer$FrameEntry;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "analyzeFrameLocal", "createNotificationChannel", "handleDetection", "description", "", "sendNotification", "title", "content", "start", "task", "intervalSec", "stop", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class StreamMonitorController {
    private static final String CHANNEL_ID = "stream_monitor_channel";
    private static final String CHANNEL_NAME = "视频流监控";
    private static final long MIN_INTERVAL_MS = 5000;
    private static final int NOTIFICATION_ID = 2001;
    private static final String TAG = "StreamMonitor";
    private static volatile long lastNotifyTimeMs;
    private static Job monitorJob;
    private static CoroutineScope monitorScope;
    public static final StreamMonitorController INSTANCE = new StreamMonitorController();
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static volatile String monitorTask = "";
    private static final long DEFAULT_INTERVAL_MS = 10000;
    private static volatile long intervalMs = DEFAULT_INTERVAL_MS;
    private static final long MIN_NOTIFY_INTERVAL_MS = 30000;

    private StreamMonitorController() {
    }

    public final String getMonitorTask() {
        return monitorTask;
    }

    public final long getIntervalMs() {
        return intervalMs;
    }

    public final boolean isRunning() {
        return isRunning.get();
    }

    public static /* synthetic */ void start$default(StreamMonitorController streamMonitorController, Application application, String str, int i, int i2, Object obj) {
        if ((i2 & 4) != 0) {
            i = 10;
        }
        streamMonitorController.start(application, str, i);
    }

    public final void start(Application app, String task, int intervalSec) {
        Intrinsics.checkNotNullParameter(app, "app");
        Intrinsics.checkNotNullParameter(task, "task");
        AtomicBoolean atomicBoolean = isRunning;
        if (atomicBoolean.get()) {
            XLog.w(TAG, "Monitor already running");
            return;
        }
        monitorTask = task;
        intervalMs = Math.max(MIN_INTERVAL_MS, intervalSec * 1000);
        atomicBoolean.set(true);
        lastNotifyTimeMs = 0L;
        createNotificationChannel(app);
        app.startForegroundService(new Intent(app, (Class<?>) StreamMonitorService.class));
        CoroutineScope CoroutineScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getIO().plus(SupervisorKt.SupervisorJob$default((Job) null, 1, (Object) null)));
        monitorScope = CoroutineScope;
        monitorJob = CoroutineScope != null ? BuildersKt__Builders_commonKt.launch$default(CoroutineScope, null, null, new StreamMonitorController$start$1(task, app, null), 3, null) : null;
        XLog.i(TAG, "Stream monitor started");
    }

    public final void start(Application app) {
        Intrinsics.checkNotNullParameter(app, "app");
        if (monitorTask.length() == 0) {
            XLog.w(TAG, "No monitor task set");
        } else {
            start$default(this, app, monitorTask, 0, 4, null);
        }
    }

    public final void stop() {
        isRunning.set(false);
        Job job = monitorJob;
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null);
        }
        CoroutineScope coroutineScope = monitorScope;
        if (coroutineScope != null) {
            CoroutineScopeKt.cancel$default(coroutineScope, null, 1, null);
        }
        monitorJob = null;
        monitorScope = null;
        XLog.i(TAG, "Stream monitor stopped");
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Can't wrap try/catch for region: R(14:0|1|(2:3|(10:5|6|(1:(1:(3:10|11|12)(2:14|15))(4:16|17|18|19))(2:34|(2:36|37)(2:38|(3:41|42|(1:44)(1:45))(5:40|26|(1:28)|11|12)))|20|21|(2:23|24)|26|(0)|11|12))|49|6|(0)(0)|20|21|(0)|26|(0)|11|12|(1:(0))) */
    /* JADX WARN: Code restructure failed: missing block: B:29:0x007d, code lost:
    
        r2 = e;
     */
    /* JADX WARN: Removed duplicated region for block: B:23:0x007a A[Catch: Exception -> 0x007d, TRY_LEAVE, TryCatch #1 {Exception -> 0x007d, blocks: (B:21:0x0076, B:23:0x007a), top: B:20:0x0076 }] */
    /* JADX WARN: Removed duplicated region for block: B:28:0x009b A[RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:34:0x0050  */
    /* JADX WARN: Removed duplicated region for block: B:8:0x0025  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final java.lang.Object analyzeFrame(android.app.Application r9, kotlin.coroutines.Continuation<? super kotlin.Unit> r10) {
        /*
            r8 = this;
            boolean r0 = r10 instanceof com.apk.claw.android.service.monitor.StreamMonitorController$analyzeFrame$1
            if (r0 == 0) goto L14
            r0 = r10
            com.apk.claw.android.service.monitor.StreamMonitorController$analyzeFrame$1 r0 = (com.apk.claw.android.service.monitor.StreamMonitorController$analyzeFrame$1) r0
            int r1 = r0.label
            r2 = -2147483648(0xffffffff80000000, float:-0.0)
            r1 = r1 & r2
            if (r1 == 0) goto L14
            int r10 = r0.label
            int r10 = r10 - r2
            r0.label = r10
            goto L19
        L14:
            com.apk.claw.android.service.monitor.StreamMonitorController$analyzeFrame$1 r0 = new com.apk.claw.android.service.monitor.StreamMonitorController$analyzeFrame$1
            r0.<init>(r8, r10)
        L19:
            java.lang.Object r10 = r0.result
            java.lang.Object r1 = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()
            int r2 = r0.label
            r3 = 2
            r4 = 1
            if (r2 == 0) goto L50
            if (r2 == r4) goto L36
            if (r2 != r3) goto L2e
            kotlin.ResultKt.throwOnFailure(r10)
            goto L9c
        L2e:
            java.lang.IllegalStateException r9 = new java.lang.IllegalStateException
            java.lang.String r10 = "call to 'resume' before 'invoke' with coroutine"
            r9.<init>(r10)
            throw r9
        L36:
            java.lang.Object r9 = r0.L$2
            com.apk.claw.android.vision.VisionFrameBuffer$FrameEntry r9 = (com.apk.claw.android.vision.VisionFrameBuffer.FrameEntry) r9
            java.lang.Object r2 = r0.L$1
            android.app.Application r2 = (android.app.Application) r2
            java.lang.Object r4 = r0.L$0
            com.apk.claw.android.service.monitor.StreamMonitorController r4 = (com.apk.claw.android.service.monitor.StreamMonitorController) r4
            kotlin.ResultKt.throwOnFailure(r10)     // Catch: java.lang.Exception -> L4a
            r7 = r10
            r10 = r9
            r9 = r2
            r2 = r7
            goto L76
        L4a:
            r10 = move-exception
            r7 = r10
            r10 = r9
            r9 = r2
            r2 = r7
            goto L81
        L50:
            kotlin.ResultKt.throwOnFailure(r10)
            com.apk.claw.android.vision.VisionFrameBuffer r10 = com.apk.claw.android.vision.VisionFrameBuffer.INSTANCE
            com.apk.claw.android.vision.VisionFrameBuffer$FrameEntry r10 = r10.getLatestFrame()
            if (r10 != 0) goto L5e
            kotlin.Unit r9 = kotlin.Unit.INSTANCE
            return r9
        L5e:
            com.apk.claw.android.utils.KVUtils r2 = com.apk.claw.android.utils.KVUtils.INSTANCE
            boolean r2 = r2.isLocalModelChatActive()
            if (r2 == 0) goto L8b
            r0.L$0 = r8     // Catch: java.lang.Exception -> L7f
            r0.L$1 = r9     // Catch: java.lang.Exception -> L7f
            r0.L$2 = r10     // Catch: java.lang.Exception -> L7f
            r0.label = r4     // Catch: java.lang.Exception -> L7f
            java.lang.Object r2 = r8.analyzeFrameLocal(r9, r10, r0)     // Catch: java.lang.Exception -> L7f
            if (r2 != r1) goto L75
            return r1
        L75:
            r4 = r8
        L76:
            java.lang.String r2 = (java.lang.String) r2     // Catch: java.lang.Exception -> L7d
            if (r2 == 0) goto L8c
            kotlin.Unit r9 = kotlin.Unit.INSTANCE     // Catch: java.lang.Exception -> L7d
            return r9
        L7d:
            r2 = move-exception
            goto L81
        L7f:
            r2 = move-exception
            r4 = r8
        L81:
            java.lang.String r5 = "Local model analysis failed, falling back to HTTP"
            java.lang.Throwable r2 = (java.lang.Throwable) r2
            java.lang.String r6 = "StreamMonitor"
            com.apk.claw.android.utils.XLog.w(r6, r5, r2)
            goto L8c
        L8b:
            r4 = r8
        L8c:
            r2 = 0
            r0.L$0 = r2
            r0.L$1 = r2
            r0.L$2 = r2
            r0.label = r3
            java.lang.Object r9 = r4.analyzeFrameHttp(r9, r10, r0)
            if (r9 != r1) goto L9c
            return r1
        L9c:
            kotlin.Unit r9 = kotlin.Unit.INSTANCE
            return r9
        */
        throw new UnsupportedOperationException("Method not decompiled: com.apk.claw.android.service.monitor.StreamMonitorController.analyzeFrame(android.app.Application, kotlin.coroutines.Continuation):java.lang.Object");
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Removed duplicated region for block: B:15:0x0188  */
    /* JADX WARN: Removed duplicated region for block: B:25:0x0156 A[RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:26:0x0157  */
    /* JADX WARN: Removed duplicated region for block: B:32:0x00a7  */
    /* JADX WARN: Removed duplicated region for block: B:40:0x00d7  */
    /* JADX WARN: Removed duplicated region for block: B:42:0x00f8  */
    /* JADX WARN: Removed duplicated region for block: B:51:0x0085  */
    /* JADX WARN: Removed duplicated region for block: B:9:0x002f  */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:37:0x00bf -> B:29:0x00c4). Please report as a decompilation issue!!! */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final java.lang.Object analyzeFrameLocal(android.app.Application r18, com.apk.claw.android.vision.VisionFrameBuffer.FrameEntry r19, kotlin.coroutines.Continuation<? super java.lang.String> r20) {
        /*
            Method dump skipped, instructions count: 428
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.apk.claw.android.service.monitor.StreamMonitorController.analyzeFrameLocal(android.app.Application, com.apk.claw.android.vision.VisionFrameBuffer$FrameEntry, kotlin.coroutines.Continuation):java.lang.Object");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final Object analyzeFrameHttp(Application application, VisionFrameBuffer.FrameEntry frameEntry, Continuation<? super Unit> continuation) {
        String string;
        JSONObject jSONObject;
        String encodeToString = Base64.encodeToString(frameEntry.getJpegBytes(), 2);
        String trimEnd = StringsKt.trimEnd(KVUtils.INSTANCE.getLlmBaseUrl(), JsonPointer.SEPARATOR);
        String llmApiKey = KVUtils.INSTANCE.getLlmApiKey();
        String llmModelName = KVUtils.INSTANCE.getLlmModelName();
        String str = trimEnd;
        if (str.length() == 0 || llmApiKey.length() == 0) {
            XLog.w(TAG, "LLM not configured, skip analysis");
            return Unit.INSTANCE;
        }
        List listOf = CollectionsKt.listOf((Object[]) new Map[]{MapsKt.mapOf(TuplesKt.to("role", "system"), TuplesKt.to("content", StringsKt.trimIndent("你是一个视频流监控助手。你的任务是持续监控摄像头画面，检测用户关注的事件。\n\n用户设定的监控任务: " + monitorTask + "\n\n分析规则:\n1. 仔细观察画面中的内容\n2. 判断画面中是否出现了用户关注的目标/事件\n3. 如果检测到 → 回复 \"DETECTED: [简要描述你看到的内容]\"\n4. 如果未检测到 → 回复 \"CLEAR: [描述你看到的内容，1句话]\"\n\n只做判断，不要提供建议或执行操作。"))), MapsKt.mapOf(TuplesKt.to("role", "user"), TuplesKt.to("content", CollectionsKt.listOf((Object[]) new Map[]{MapsKt.mapOf(TuplesKt.to(Constant.HEADER_TYPE, "text"), TuplesKt.to("text", "请分析当前摄像头画面，判断是否满足监控任务条件。")), MapsKt.mapOf(TuplesKt.to(Constant.HEADER_TYPE, "image_url"), TuplesKt.to("image_url", MapsKt.mapOf(TuplesKt.to("url", "data:image/jpeg;base64," + encodeToString))))})))});
        Pair[] pairArr = new Pair[4];
        String str2 = llmModelName;
        if (str2.length() == 0) {
            str2 = "gpt-4o";
        }
        pairArr[0] = TuplesKt.to("model", str2);
        pairArr[1] = TuplesKt.to("messages", listOf);
        pairArr[2] = TuplesKt.to("max_tokens", Boxing.boxInt(200));
        pairArr[3] = TuplesKt.to("temperature", Boxing.boxDouble(0.1d));
        Map mapOf = MapsKt.mapOf(pairArr);
        String str3 = StringsKt.endsWith$default(trimEnd, "/v1", false, 2, (Object) null) ? trimEnd + "/chat/completions" : StringsKt.contains$default((CharSequence) str, (CharSequence) "/v1/", false, 2, (Object) null) ? trimEnd + "chat/completions" : trimEnd + "/v1/chat/completions";
        String json = new Gson().toJson(mapOf);
        OkHttpClient build = new OkHttpClient.Builder().connectTimeout(60L, TimeUnit.SECONDS).readTimeout(60L, TimeUnit.SECONDS).writeTimeout(60L, TimeUnit.SECONDS).build();
        Request.Builder addHeader = new Request.Builder().url(str3).addHeader("Authorization", "Bearer " + llmApiKey).addHeader("Content-Type", "application/json");
        RequestBody.Companion companion = RequestBody.INSTANCE;
        Intrinsics.checkNotNull(json);
        Response execute = build.newCall(addHeader.post(companion.create(json, MediaType.INSTANCE.get("application/json"))).build()).execute();
        try {
            Response response = execute;
            ResponseBody body = response.body();
            if (body != null && (string = body.string()) != null) {
                if (!response.isSuccessful()) {
                    XLog.e(TAG, "Monitor API error " + response.code());
                    Unit unit = Unit.INSTANCE;
                    CloseableKt.closeFinally(execute, null);
                    return unit;
                }
                JSONObject optJSONObject = new JSONObject(string).getJSONArray("choices").optJSONObject(0);
                String optString = (optJSONObject == null || (jSONObject = optJSONObject.getJSONObject("message")) == null) ? null : jSONObject.optString("content", "");
                if (optString == null) {
                    Unit unit2 = Unit.INSTANCE;
                    CloseableKt.closeFinally(execute, null);
                    return unit2;
                }
                XLog.i(TAG, "Monitor analysis: " + optString);
                String obj = StringsKt.trim((CharSequence) optString).toString();
                if (StringsKt.startsWith(obj, "DETECTED:", true)) {
                    INSTANCE.handleDetection(application, StringsKt.trim((CharSequence) StringsKt.removePrefix(StringsKt.removePrefix(obj, (CharSequence) "DETECTED:"), (CharSequence) "DETECTED：")).toString());
                }
                Unit unit3 = Unit.INSTANCE;
                CloseableKt.closeFinally(execute, null);
                return Unit.INSTANCE;
            }
            Unit unit4 = Unit.INSTANCE;
            CloseableKt.closeFinally(execute, null);
            return unit4;
        } finally {
        }
    }

    private final void handleDetection(Application app, String description) {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastNotifyTimeMs < MIN_NOTIFY_INTERVAL_MS) {
            XLog.w(TAG, "Too frequent notification, skipping");
            return;
        }
        lastNotifyTimeMs = currentTimeMillis;
        XLog.i(TAG, "DETECTED: " + description);
        VoiceStreamFloatWindow.INSTANCE.showMonitorResult("检测到: " + description);
        sendNotification(app, "监控提醒", description);
        try {
            Object systemService = app.getSystemService("vibrator");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.os.Vibrator");
            ((Vibrator) systemService).vibrate(VibrationEffect.createOneShot(500L, -1));
        } catch (Exception unused) {
        }
    }

    private final void sendNotification(Application app, String title, String content) {
        Object systemService = app.getSystemService("notification");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.NotificationManager");
        Intent launchIntentForPackage = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        Application application = app;
        String str = content;
        Notification build = new NotificationCompat.Builder(application, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(str).setStyle(new NotificationCompat.BigTextStyle().bigText(str)).setPriority(1).setAutoCancel(true).setContentIntent(PendingIntent.getActivity(application, 0, launchIntentForPackage, 201326592)).setVibrate(new long[]{0, 300, 200, 300}).build();
        Intrinsics.checkNotNullExpressionValue(build, "build(...)");
        ((NotificationManager) systemService).notify(NOTIFICATION_ID, build);
    }

    private final void createNotificationChannel(Application app) {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, 4);
        notificationChannel.setDescription("视频流监控检测通知");
        notificationChannel.enableVibration(true);
        Object systemService = app.getSystemService("notification");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.app.NotificationManager");
        ((NotificationManager) systemService).createNotificationChannel(notificationChannel);
    }
}
