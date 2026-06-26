package com.apk.claw.android;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.lifecycle.ViewModel;
import com.apk.claw.android.agent.AgentConfig;
import com.apk.claw.android.channel.Channel;
import com.apk.claw.android.channel.ChannelManager;
import com.apk.claw.android.channel.ChannelSetup;
import com.apk.claw.android.floating.FloatingCircleManager;
import com.apk.claw.android.server.ConfigServerManager;
import com.apk.claw.android.service.ForegroundService;
import com.apk.claw.android.service.KeepAliveJobService;
import com.apk.claw.android.ui.chat.ChatActivity;
import com.apk.claw.android.ui.home.HomeActivity;
import com.apk.claw.android.utils.KVUtils;
import com.apk.claw.android.utils.XLog;
import dev.langchain4j.model.openai.internal.OpenAiUtils;
import java.io.File;
import java.util.Base64;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.io.FilesKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorKt;

/* compiled from: AppViewModel.kt */
@Metadata(d1 = {"\u0000`\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u0012\n\u0002\b\n\u0018\u0000 72\u00020\u0001:\u00017B\u0005¢\u0006\u0002\u0010\u0002J\b\u0010\u001e\u001a\u00020\u001fH\u0002J\u0006\u0010 \u001a\u00020\u001fJ\b\u0010!\u001a\u00020\u001fH\u0002J\u0006\u0010\"\u001a\u00020\u001fJ\u0006\u0010#\u001a\u00020$J\u0006\u0010%\u001a\u00020\u001fJ\u0006\u0010&\u001a\u00020\u001fJ\u0006\u0010'\u001a\u00020\u001fJ\u0006\u0010(\u001a\u00020\u0004J\b\u0010)\u001a\u00020\u001fH\u0002J\u0006\u0010*\u001a\u00020\u001fJ \u0010+\u001a\u00020\u001f2\u0006\u0010,\u001a\u00020\u00122\b\u0010-\u001a\u0004\u0018\u00010.2\u0006\u0010/\u001a\u00020\bJ\"\u00100\u001a\u00020\u001f2\u0006\u0010,\u001a\u00020\u00122\b\u0010-\u001a\u0004\u0018\u00010.2\u0006\u0010/\u001a\u00020\bH\u0002J\u001e\u00101\u001a\u00020\u001f2\u0006\u00102\u001a\u00020\u000e2\u0006\u0010,\u001a\u00020\u00122\u0006\u00103\u001a\u00020\u0012J \u00104\u001a\u00020\u001f2\u0006\u00102\u001a\u00020\u000e2\u0006\u00105\u001a\u00020\u00122\u0006\u00103\u001a\u00020\u0012H\u0002J\u0006\u00106\u001a\u00020\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004¢\u0006\u0002\n\u0000R\u001c\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u0013\u0010\r\u001a\u0004\u0018\u00010\u000e8F¢\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0011\u001a\u00020\u00128F¢\u0006\u0006\u001a\u0004\b\u0013\u0010\u0014R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010\u0017\u001a\u00020\u0018¢\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0014\u0010\u001b\u001a\b\u0018\u00010\u001cR\u00020\u001dX\u0082\u000e¢\u0006\u0002\n\u0000¨\u00068"}, d2 = {"Lcom/apk/claw/android/AppViewModel;", "Landroidx/lifecycle/ViewModel;", "()V", "_commonInitialized", "", "channelSetup", "Lcom/apk/claw/android/channel/ChannelSetup;", "chatCallback", "Lcom/apk/claw/android/ui/chat/ChatActivity$ChatCallback;", "getChatCallback", "()Lcom/apk/claw/android/ui/chat/ChatActivity$ChatCallback;", "setChatCallback", "(Lcom/apk/claw/android/ui/chat/ChatActivity$ChatCallback;)V", "inProgressTaskChannel", "Lcom/apk/claw/android/channel/Channel;", "getInProgressTaskChannel", "()Lcom/apk/claw/android/channel/Channel;", "inProgressTaskMessageId", "", "getInProgressTaskMessageId", "()Ljava/lang/String;", "localChatScope", "Lkotlinx/coroutines/CoroutineScope;", "taskOrchestrator", "Lcom/apk/claw/android/TaskOrchestrator;", "getTaskOrchestrator", "()Lcom/apk/claw/android/TaskOrchestrator;", "wakeLock", "Landroid/os/PowerManager$WakeLock;", "Landroid/os/PowerManager;", "acquireScreenWakeLock", "", "afterInit", "bringAppToForeground", "cancelCurrentTask", "getAgentConfig", "Lcom/apk/claw/android/agent/AgentConfig;", "init", "initAgent", "initCommon", "isTaskRunning", "releaseScreenWakeLock", "showFloatingCircle", "startChatTask", "task", "imageData", "", "callback", "startLocalModelChat", "startNewTask", "channel", "messageID", "trySendScreenshot", "filePath", "updateAgentConfig", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class AppViewModel extends ViewModel {
    private static final String TAG = "AppViewModel";
    private boolean _commonInitialized;
    private final ChannelSetup channelSetup;
    private volatile ChatActivity.ChatCallback chatCallback;
    private final CoroutineScope localChatScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getIO().plus(SupervisorKt.SupervisorJob$default((Job) null, 1, (Object) null)));
    private final TaskOrchestrator taskOrchestrator;
    private PowerManager.WakeLock wakeLock;

    public AppViewModel() {
        TaskOrchestrator taskOrchestrator = new TaskOrchestrator(new Function0<AgentConfig>() { // from class: com.apk.claw.android.AppViewModel$taskOrchestrator$1
            {
                super(0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            public final AgentConfig invoke() {
                return AppViewModel.this.getAgentConfig();
            }
        }, new Function0<Unit>() { // from class: com.apk.claw.android.AppViewModel$taskOrchestrator$2
            {
                super(0);
            }

            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                AppViewModel.this.setChatCallback(null);
            }
        }, new Function0<ChatActivity.ChatCallback>() { // from class: com.apk.claw.android.AppViewModel$taskOrchestrator$3
            {
                super(0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            public final ChatActivity.ChatCallback invoke() {
                return AppViewModel.this.getChatCallback();
            }
        });
        this.taskOrchestrator = taskOrchestrator;
        this.channelSetup = new ChannelSetup(taskOrchestrator);
    }

    public final ChatActivity.ChatCallback getChatCallback() {
        return this.chatCallback;
    }

    public final void setChatCallback(ChatActivity.ChatCallback chatCallback) {
        this.chatCallback = chatCallback;
    }

    public final TaskOrchestrator getTaskOrchestrator() {
        return this.taskOrchestrator;
    }

    public final String getInProgressTaskMessageId() {
        return this.taskOrchestrator.getInProgressTaskMessageId();
    }

    public final Channel getInProgressTaskChannel() {
        return this.taskOrchestrator.getInProgressTaskChannel();
    }

    public final void init() {
        initCommon();
        initAgent();
    }

    public final void initCommon() {
        if (this._commonInitialized) {
            return;
        }
        this._commonInitialized = true;
    }

    public final void initAgent() {
        if (KVUtils.INSTANCE.isChatAvailable()) {
            this.taskOrchestrator.initAgent();
        }
    }

    public final AgentConfig getAgentConfig() {
        if (KVUtils.INSTANCE.isLocalModelChatActive()) {
            String obj = StringsKt.trim((CharSequence) KVUtils.INSTANCE.getLocalModelBaseUrl()).toString();
            return new AgentConfig.Builder().apiKey(KVUtils.INSTANCE.getLocalModelApiKey()).baseUrl(obj).modelName(KVUtils.INSTANCE.getLocalModelId()).temperature(KVUtils.INSTANCE.getLocalModelTemperature()).maxIterations(60).build();
        }
        String obj2 = StringsKt.trim((CharSequence) KVUtils.INSTANCE.getLlmBaseUrl()).toString();
        if (obj2.length() == 0) {
            obj2 = OpenAiUtils.DEFAULT_OPENAI_URL;
        }
        return new AgentConfig.Builder().apiKey(KVUtils.INSTANCE.getLlmApiKey()).baseUrl(obj2).modelName(KVUtils.INSTANCE.getLlmModelName()).temperature(0.1d).maxIterations(60).build();
    }

    public final boolean updateAgentConfig() {
        return this.taskOrchestrator.updateAgentConfig();
    }

    public final void afterInit() {
        acquireScreenWakeLock();
        ForegroundService.INSTANCE.start(ClawApplication.INSTANCE.getInstance());
        KeepAliveJobService.INSTANCE.schedule(ClawApplication.INSTANCE.getInstance());
        ConfigServerManager.INSTANCE.autoStartIfNeeded(ClawApplication.INSTANCE.getInstance());
        if (Settings.canDrawOverlays(ClawApplication.INSTANCE.getInstance())) {
            new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.apk.claw.android.AppViewModel$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    AppViewModel.afterInit$lambda$0();
                }
            });
        }
        this.channelSetup.setup();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void afterInit$lambda$0() {
        ClawApplication.INSTANCE.getAppViewModelInstance().showFloatingCircle();
    }

    private final void acquireScreenWakeLock() {
        PowerManager.WakeLock wakeLock = this.wakeLock;
        if (wakeLock == null || !wakeLock.isHeld()) {
            Object systemService = ClawApplication.INSTANCE.getInstance().getSystemService("power");
            PowerManager powerManager = systemService instanceof PowerManager ? (PowerManager) systemService : null;
            if (powerManager == null) {
                return;
            }
            PowerManager.WakeLock newWakeLock = powerManager.newWakeLock(268435462, "ApkClaw::ScreenWakeLock");
            newWakeLock.acquire();
            this.wakeLock = newWakeLock;
            XLog.i(TAG, "亮屏锁已获取");
        }
    }

    private final void releaseScreenWakeLock() {
        PowerManager.WakeLock wakeLock = this.wakeLock;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            XLog.i(TAG, "亮屏锁已释放");
        }
        this.wakeLock = null;
    }

    public final void showFloatingCircle() {
        try {
            FloatingCircleManager.show$default(FloatingCircleManager.INSTANCE, ClawApplication.INSTANCE.getInstance(), null, null, 6, null);
            FloatingCircleManager.INSTANCE.setExternalClickCallback(new Function0<Unit>() { // from class: com.apk.claw.android.AppViewModel$showFloatingCircle$1
                {
                    super(0);
                }

                @Override // kotlin.jvm.functions.Function0
                public /* bridge */ /* synthetic */ Unit invoke() {
                    invoke2();
                    return Unit.INSTANCE;
                }

                /* renamed from: invoke, reason: avoid collision after fix types in other method */
                public final void invoke2() {
                    XLog.d("AppViewModel", "Floating circle clicked → bring app to foreground");
                    AppViewModel.this.bringAppToForeground();
                }
            });
        } catch (Exception e) {
            XLog.e(TAG, "Failed to show floating circle: " + e.getMessage());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void bringAppToForeground() {
        ClawApplication companion = ClawApplication.INSTANCE.getInstance();
        Intent intent = new Intent(companion, (Class<?>) HomeActivity.class);
        intent.setFlags(872415232);
        companion.startActivity(intent);
    }

    public final boolean isTaskRunning() {
        return this.taskOrchestrator.isTaskRunning();
    }

    public final void cancelCurrentTask() {
        this.taskOrchestrator.cancelCurrentTask();
    }

    public final void startNewTask(Channel channel, String task, String messageID) {
        Intrinsics.checkNotNullParameter(channel, "channel");
        Intrinsics.checkNotNullParameter(task, "task");
        Intrinsics.checkNotNullParameter(messageID, "messageID");
        this.taskOrchestrator.startNewTask(channel, task, messageID);
    }

    public final void startChatTask(String task, byte[] imageData, ChatActivity.ChatCallback callback) {
        Intrinsics.checkNotNullParameter(task, "task");
        Intrinsics.checkNotNullParameter(callback, "callback");
        if (KVUtils.INSTANCE.isLocalModelChatActive()) {
            startLocalModelChat(task, imageData, callback);
            return;
        }
        if (!KVUtils.INSTANCE.isChatAvailable()) {
            callback.onError("LLM not configured");
            return;
        }
        if (isTaskRunning()) {
            callback.onError("Task already running");
            return;
        }
        this.chatCallback = callback;
        String str = "chat_" + System.currentTimeMillis();
        Channel channel = Channel.CHAT;
        String str2 = null;
        if (imageData != null) {
            try {
                str2 = Base64.getEncoder().encodeToString(imageData);
                FilesKt.writeBytes(new File(ClawApplication.INSTANCE.getInstance().getCacheDir(), "chat_image_" + System.currentTimeMillis() + ".png"), imageData);
            } catch (Exception e) {
                XLog.e(TAG, "Failed to process chat image", e);
            }
        }
        this.taskOrchestrator.startNewTask(channel, task, str, str2);
    }

    private final void startLocalModelChat(String task, byte[] imageData, ChatActivity.ChatCallback callback) {
        this.chatCallback = callback;
        BuildersKt__Builders_commonKt.launch$default(this.localChatScope, null, null, new AppViewModel$startLocalModelChat$1(callback, this, imageData, task, null), 3, null);
    }

    private final void trySendScreenshot(Channel channel, String filePath, String messageID) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                ChannelManager.sendImage(channel, FilesKt.readBytes(file), messageID);
            } else {
                XLog.w(TAG, "截图文件不存在: " + filePath);
            }
        } catch (Exception e) {
            XLog.e(TAG, "发送截图失败", e);
        }
    }
}
