package com.apk.claw.android.ui.chat;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.apk.claw.android.ClawApplicationKt;
import com.apk.claw.android.R;
import com.apk.claw.android.base.BaseActivity;
import com.apk.claw.android.floating.voice.VoiceInteractionFloatWindow;
import com.apk.claw.android.integration.FeatureIntegrationManager;
import com.apk.claw.android.skill.SkillSystem;
import com.apk.claw.android.ui.chat.ChatActivity;
import com.apk.claw.android.ui.chat.CloudChatManager;
import com.apk.claw.android.ui.settings.LocalModelInfo;
import com.apk.claw.android.utils.KVUtils;
import com.apk.claw.android.voice.VoiceInputController;
import com.apk.claw.android.widget.CommonToolbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Ref;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;

/* compiled from: ChatActivity.kt */
@Metadata(d1 = {"\u0000\u009b\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0002\b\u001d*\u0001\u001d\u0018\u0000 b2\u00020\u0001:\u0003`abB\u0005¢\u0006\u0002\u0010\u0002J\u0014\u00101\u001a\u0004\u0018\u00010\"2\b\u00102\u001a\u0004\u0018\u000103H\u0002J\u0010\u00104\u001a\u0002052\u0006\u00106\u001a\u000207H\u0002J\u0010\u00108\u001a\u00020\u00142\u0006\u00109\u001a\u00020\u0014H\u0002J\b\u0010:\u001a\u000205H\u0002J\b\u0010;\u001a\u000205H\u0002J\u001c\u0010<\u001a\u0002052\u0006\u0010=\u001a\u00020\u00142\n\b\u0002\u0010>\u001a\u0004\u0018\u00010\u0014H\u0002J\b\u0010?\u001a\u000205H\u0002J\b\u0010@\u001a\u000205H\u0002J\b\u0010A\u001a\u000205H\u0002J\b\u0010B\u001a\u000205H\u0002J\b\u0010C\u001a\u000205H\u0002J\u0012\u0010D\u001a\u0002052\b\u0010E\u001a\u0004\u0018\u00010FH\u0014J\b\u0010G\u001a\u000205H\u0014J\u0010\u0010H\u001a\u0002052\u0006\u0010I\u001a\u00020\rH\u0014J\b\u0010J\u001a\u000205H\u0014J\b\u0010K\u001a\u000205H\u0014J\b\u0010L\u001a\u000205H\u0014J\b\u0010M\u001a\u000205H\u0002J\b\u0010N\u001a\u000205H\u0002J\u0010\u0010O\u001a\u0002052\u0006\u0010P\u001a\u00020\u0014H\u0002J\u001a\u0010Q\u001a\u0002052\u0006\u0010P\u001a\u00020\u00142\b\u0010R\u001a\u0004\u0018\u00010\"H\u0002J\b\u0010S\u001a\u000205H\u0002J\u001c\u0010T\u001a\u0002052\u0006\u0010P\u001a\u00020\u00142\n\b\u0002\u0010R\u001a\u0004\u0018\u00010\"H\u0002J\u0010\u0010U\u001a\u0002052\u0006\u0010V\u001a\u00020\u0014H\u0002J\b\u0010W\u001a\u000205H\u0002J\u0018\u0010X\u001a\u0002052\u0006\u00106\u001a\u0002072\u0006\u0010Y\u001a\u00020\u0014H\u0002J\b\u0010Z\u001a\u000205H\u0002J\b\u0010[\u001a\u000205H\u0002J\b\u0010\\\u001a\u000205H\u0002J\b\u0010]\u001a\u000205H\u0002J\b\u0010^\u001a\u000205H\u0002J\b\u0010_\u001a\u000205H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u001c\u0010\u000b\u001a\u0010\u0012\f\u0012\n \u000e*\u0004\u0018\u00010\r0\r0\fX\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082.¢\u0006\u0002\n\u0000R\u001c\u0010\u0013\u001a\u0010\u0012\f\u0012\n \u000e*\u0004\u0018\u00010\u00140\u00140\fX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0016X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082.¢\u0006\u0002\n\u0000R\u001c\u0010\u001b\u001a\u0010\u0012\f\u0012\n \u000e*\u0004\u0018\u00010\u00140\u00140\fX\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\u001c\u001a\u00020\u001dX\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u001eR\u000e\u0010\u001f\u001a\u00020 X\u0082.¢\u0006\u0002\n\u0000R\u0010\u0010!\u001a\u0004\u0018\u00010\"X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010#\u001a\u0004\u0018\u00010$X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020&X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010'\u001a\u00020(X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010)\u001a\u00020(X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010*\u001a\u00020+X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010,\u001a\u00020+X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010-\u001a\u00020\u0016X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010.\u001a\u0004\u0018\u00010/X\u0082\u000e¢\u0006\u0002\n\u0000R\u001c\u00100\u001a\u0010\u0012\f\u0012\n \u000e*\u0004\u0018\u00010\u00140\u00140\fX\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006c"}, d2 = {"Lcom/apk/claw/android/ui/chat/ChatActivity;", "Lcom/apk/claw/android/base/BaseActivity;", "()V", "adapter", "Lcom/apk/claw/android/ui/chat/ChatAdapter;", "btnAttach", "Landroid/widget/ImageView;", "btnCamera", "btnRemovePreview", "btnSend", "btnVoice", "cameraLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "kotlin.jvm.PlatformType", "connectionObserverJob", "Lkotlinx/coroutines/Job;", "etMessage", "Landroid/widget/EditText;", "imagePickerLauncher", "", "isListening", "", "isLocalModelActive", "ivPreview", "layoutLocalModelSwitch", "Landroid/view/View;", "notificationPermissionLauncher", "pushListener", "com/apk/claw/android/ui/chat/ChatActivity$pushListener$1", "Lcom/apk/claw/android/ui/chat/ChatActivity$pushListener$1;", "rvMessages", "Landroidx/recyclerview/widget/RecyclerView;", "selectedImageData", "", "selectedImageUri", "Landroid/net/Uri;", "skillSystem", "Lcom/apk/claw/android/skill/SkillSystem;", "switchCloudMode", "Landroid/widget/Switch;", "switchLocalModel", "tvConnectionStatus", "Landroid/widget/TextView;", "tvLocalModelName", "voiceFloatTextHandled", "voiceInputController", "Lcom/apk/claw/android/voice/VoiceInputController;", "voicePermissionLauncher", "compressImage", "bitmap", "Landroid/graphics/Bitmap;", "executeWithSkill", "", "skill", "Lcom/apk/claw/android/skill/SkillSystem$Skill;", "getLocalModelDisplayName", "modelId", "handlePushIntent", "handleScreenshotIntent", "handleScreenshotPath", "screenshotPath", "customPrompt", "handleSkillIntent", "handleVoiceFloatIntent", "initViews", "initVoiceController", "loadChatHistory", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "onNewIntent", "intent", "onPause", "onStart", "onStop", "persistChatHistory", "requestNotificationPermission", "sendCloudMessage", "text", "sendLocalMessage", "imageData", "sendMessage", "sendMessageInternal", "sendMessageWithPrompt", "prompt", "setupLocalModelSwitch", "showSkillMatchDialog", "originalText", "startListening", "startObservingConnectionState", "stopListening", "stopObservingConnectionState", "toggleVoiceInput", "updateModeHint", "ChatCallback", "ChatMessage", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class ChatActivity extends BaseActivity {
    public static final String EXTRA_MATCHED_SKILL_ID = "matched_skill_id";
    public static final String EXTRA_PUSH_TEXT = "push_text";
    public static final String EXTRA_SKILL_NAME = "skill_name";
    public static final String EXTRA_SKILL_PROMPT = "skill_prompt";
    private static final int MAX_MESSAGES = 100;
    private static final String STORAGE_KEY = "chat_history_messages";
    private static final String TAG = "ChatActivity";
    private ChatAdapter adapter;
    private ImageView btnAttach;
    private ImageView btnCamera;
    private ImageView btnRemovePreview;
    private ImageView btnSend;
    private ImageView btnVoice;
    private final ActivityResultLauncher<Intent> cameraLauncher;
    private Job connectionObserverJob;
    private EditText etMessage;
    private final ActivityResultLauncher<String> imagePickerLauncher;
    private boolean isListening;
    private boolean isLocalModelActive;
    private ImageView ivPreview;
    private View layoutLocalModelSwitch;
    private final ActivityResultLauncher<String> notificationPermissionLauncher;
    private final ChatActivity$pushListener$1 pushListener;
    private RecyclerView rvMessages;
    private byte[] selectedImageData;
    private Uri selectedImageUri;
    private SkillSystem skillSystem;
    private Switch switchCloudMode;
    private Switch switchLocalModel;
    private TextView tvConnectionStatus;
    private TextView tvLocalModelName;
    private boolean voiceFloatTextHandled;
    private VoiceInputController voiceInputController;
    private final ActivityResultLauncher<String> voicePermissionLauncher;

    /* renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private static final Gson gson = new Gson();

    /* compiled from: ChatActivity.kt */
    @Metadata(d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J\u0010\u0010\u0006\u001a\u00020\u00032\u0006\u0010\u0007\u001a\u00020\u0005H&J\u0010\u0010\b\u001a\u00020\u00032\u0006\u0010\t\u001a\u00020\u0005H&¨\u0006\n"}, d2 = {"Lcom/apk/claw/android/ui/chat/ChatActivity$ChatCallback;", "", "onComplete", "", "answer", "", "onError", "error", "onProgress", "step", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public interface ChatCallback {
        void onComplete(String answer);

        void onError(String error);

        void onProgress(String step);
    }

    /* JADX WARN: Type inference failed for: r0v9, types: [com.apk.claw.android.ui.chat.ChatActivity$pushListener$1] */
    public ChatActivity() {
        ActivityResultLauncher<String> registerForActivityResult = registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda7
            @Override // androidx.activity.result.ActivityResultCallback
            public final void onActivityResult(Object obj) {
                ChatActivity.imagePickerLauncher$lambda$0(ChatActivity.this, (Uri) obj);
            }
        });
        Intrinsics.checkNotNullExpressionValue(registerForActivityResult, "registerForActivityResult(...)");
        this.imagePickerLauncher = registerForActivityResult;
        ActivityResultLauncher<Intent> registerForActivityResult2 = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda8
            @Override // androidx.activity.result.ActivityResultCallback
            public final void onActivityResult(Object obj) {
                ChatActivity.cameraLauncher$lambda$1(ChatActivity.this, (ActivityResult) obj);
            }
        });
        Intrinsics.checkNotNullExpressionValue(registerForActivityResult2, "registerForActivityResult(...)");
        this.cameraLauncher = registerForActivityResult2;
        ActivityResultLauncher<String> registerForActivityResult3 = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda9
            @Override // androidx.activity.result.ActivityResultCallback
            public final void onActivityResult(Object obj) {
                ChatActivity.notificationPermissionLauncher$lambda$4(ChatActivity.this, (Boolean) obj);
            }
        });
        Intrinsics.checkNotNullExpressionValue(registerForActivityResult3, "registerForActivityResult(...)");
        this.notificationPermissionLauncher = registerForActivityResult3;
        this.pushListener = new CloudChatManager.PushListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$pushListener$1
            @Override // com.apk.claw.android.ui.chat.CloudChatManager.PushListener
            public void onPushMessage(String text) {
                ChatAdapter chatAdapter;
                RecyclerView recyclerView;
                ChatAdapter chatAdapter2;
                Intrinsics.checkNotNullParameter(text, "text");
                chatAdapter = ChatActivity.this.adapter;
                ChatAdapter chatAdapter3 = null;
                if (chatAdapter == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("adapter");
                    chatAdapter = null;
                }
                chatAdapter.addMessage(new ChatActivity.ChatMessage(text, false, null, System.currentTimeMillis(), false, 20, null));
                recyclerView = ChatActivity.this.rvMessages;
                if (recyclerView == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
                    recyclerView = null;
                }
                chatAdapter2 = ChatActivity.this.adapter;
                if (chatAdapter2 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("adapter");
                } else {
                    chatAdapter3 = chatAdapter2;
                }
                recyclerView.smoothScrollToPosition(chatAdapter3.getItemCount() - 1);
                ChatActivity.this.persistChatHistory();
            }
        };
        ActivityResultLauncher<String> registerForActivityResult4 = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda10
            @Override // androidx.activity.result.ActivityResultCallback
            public final void onActivityResult(Object obj) {
                ChatActivity.voicePermissionLauncher$lambda$15(ChatActivity.this, (Boolean) obj);
            }
        });
        Intrinsics.checkNotNullExpressionValue(registerForActivityResult4, "registerForActivityResult(...)");
        this.voicePermissionLauncher = registerForActivityResult4;
    }

    /* compiled from: ChatActivity.kt */
    @Metadata(d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0006\u0010\u000e\u001a\u00020\u000fJ\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00120\u0011J\u0014\u0010\u0013\u001a\u00020\u000f2\f\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00120\u0011R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\u0015"}, d2 = {"Lcom/apk/claw/android/ui/chat/ChatActivity$Companion;", "", "()V", "EXTRA_MATCHED_SKILL_ID", "", "EXTRA_PUSH_TEXT", "EXTRA_SKILL_NAME", "EXTRA_SKILL_PROMPT", "MAX_MESSAGES", "", "STORAGE_KEY", "TAG", "gson", "Lcom/google/gson/Gson;", "clearMessages", "", "loadMessages", "", "Lcom/apk/claw/android/ui/chat/ChatActivity$ChatMessage;", "saveMessages", "messages", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final void saveMessages(List<ChatMessage> messages) {
            Intrinsics.checkNotNullParameter(messages, "messages");
            try {
                ArrayList arrayList = new ArrayList();
                for (Object obj : messages) {
                    if (!((ChatMessage) obj).isThinking()) {
                        arrayList.add(obj);
                    }
                }
                List<ChatMessage> takeLast = CollectionsKt.takeLast(arrayList, 100);
                ArrayList arrayList2 = new ArrayList(CollectionsKt.collectionSizeOrDefault(takeLast, 10));
                for (ChatMessage chatMessage : takeLast) {
                    Pair[] pairArr = new Pair[4];
                    pairArr[0] = TuplesKt.to("text", chatMessage.getText());
                    pairArr[1] = TuplesKt.to("isUser", Boolean.valueOf(chatMessage.isUser()));
                    pairArr[2] = TuplesKt.to("timestamp", Long.valueOf(chatMessage.getTimestamp()));
                    byte[] imageData = chatMessage.getImageData();
                    pairArr[3] = TuplesKt.to("imageBase64", imageData != null ? Base64.getEncoder().encodeToString(imageData) : null);
                    arrayList2.add(MapsKt.mapOf(pairArr));
                }
                KVUtils.INSTANCE.putString(ChatActivity.STORAGE_KEY, ChatActivity.gson.toJson(arrayList2));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public final List<ChatMessage> loadMessages() {
            try {
                String string$default = KVUtils.getString$default(KVUtils.INSTANCE, ChatActivity.STORAGE_KEY, null, 2, null);
                if (string$default.length() == 0) {
                    return CollectionsKt.emptyList();
                }
                List list = (List) ChatActivity.gson.fromJson(string$default, new TypeToken<List<? extends Map<String, ? extends Object>>>() { // from class: com.apk.claw.android.ui.chat.ChatActivity$Companion$loadMessages$type$1
                }.getType());
                if (list == null) {
                    return CollectionsKt.emptyList();
                }
                List<Map> list2 = list;
                ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(list2, 10));
                for (Map map : list2) {
                    Object obj = map.get("text");
                    String str = obj instanceof String ? (String) obj : null;
                    if (str == null) {
                        str = "";
                    }
                    String str2 = str;
                    Object obj2 = map.get("isUser");
                    Boolean bool = obj2 instanceof Boolean ? (Boolean) obj2 : null;
                    boolean booleanValue = bool != null ? bool.booleanValue() : false;
                    Object obj3 = map.get("timestamp");
                    Double d = obj3 instanceof Double ? (Double) obj3 : null;
                    long doubleValue = d != null ? (long) d.doubleValue() : 0L;
                    Object obj4 = map.get("imageBase64");
                    String str3 = obj4 instanceof String ? (String) obj4 : null;
                    arrayList.add(new ChatMessage(str2, booleanValue, str3 != null ? Base64.getDecoder().decode(str3) : null, doubleValue, false, 16, null));
                }
                return arrayList;
            } catch (Exception e) {
                e.printStackTrace();
                return CollectionsKt.emptyList();
            }
        }

        public final void clearMessages() {
            KVUtils.INSTANCE.putString(ChatActivity.STORAGE_KEY, "");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void imagePickerLauncher$lambda$0(ChatActivity this$0, Uri uri) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (uri != null) {
            this$0.selectedImageUri = uri;
            ImageView imageView = this$0.ivPreview;
            ImageView imageView2 = null;
            if (imageView == null) {
                Intrinsics.throwUninitializedPropertyAccessException("ivPreview");
                imageView = null;
            }
            imageView.setImageURI(uri);
            ImageView imageView3 = this$0.ivPreview;
            if (imageView3 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("ivPreview");
                imageView3 = null;
            }
            imageView3.setVisibility(0);
            ImageView imageView4 = this$0.btnRemovePreview;
            if (imageView4 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("btnRemovePreview");
            } else {
                imageView2 = imageView4;
            }
            imageView2.setVisibility(0);
            try {
                InputStream openInputStream = this$0.getContentResolver().openInputStream(uri);
                Bitmap decodeStream = BitmapFactory.decodeStream(openInputStream);
                if (openInputStream != null) {
                    openInputStream.close();
                }
                this$0.selectedImageData = this$0.compressImage(decodeStream);
                if (decodeStream != null) {
                    decodeStream.recycle();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void cameraLauncher$lambda$1(ChatActivity this$0, ActivityResult activityResult) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (activityResult.getResultCode() == -1) {
            Intent data = activityResult.getData();
            ImageView imageView = null;
            byte[] byteArrayExtra = data != null ? data.getByteArrayExtra(CameraActivity.EXTRA_PHOTO_DATA) : null;
            if (byteArrayExtra != null) {
                this$0.selectedImageData = byteArrayExtra;
                this$0.selectedImageUri = null;
                Bitmap decodeByteArray = BitmapFactory.decodeByteArray(byteArrayExtra, 0, byteArrayExtra.length);
                if (decodeByteArray != null) {
                    ImageView imageView2 = this$0.ivPreview;
                    if (imageView2 == null) {
                        Intrinsics.throwUninitializedPropertyAccessException("ivPreview");
                        imageView2 = null;
                    }
                    imageView2.setImageBitmap(decodeByteArray);
                    ImageView imageView3 = this$0.ivPreview;
                    if (imageView3 == null) {
                        Intrinsics.throwUninitializedPropertyAccessException("ivPreview");
                        imageView3 = null;
                    }
                    imageView3.setVisibility(0);
                    ImageView imageView4 = this$0.btnRemovePreview;
                    if (imageView4 == null) {
                        Intrinsics.throwUninitializedPropertyAccessException("btnRemovePreview");
                    } else {
                        imageView = imageView4;
                    }
                    imageView.setVisibility(0);
                }
            }
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:15:0x005a  */
    /* JADX WARN: Removed duplicated region for block: B:19:0x0060 A[Catch: Exception -> 0x0064, TRY_LEAVE, TryCatch #0 {Exception -> 0x0064, blocks: (B:6:0x0004, B:8:0x000c, B:12:0x0037, B:13:0x003c, B:19:0x0060, B:24:0x0015), top: B:5:0x0004 }] */
    /* JADX WARN: Removed duplicated region for block: B:23:0x005e A[EDGE_INSN: B:23:0x005e->B:18:0x005e BREAK  A[LOOP:0: B:13:0x003c->B:22:?], SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private final byte[] compressImage(android.graphics.Bitmap r7) {
        /*
            r6 = this;
            r0 = 0
            if (r7 != 0) goto L4
            return r0
        L4:
            int r1 = r7.getWidth()     // Catch: java.lang.Exception -> L64
            r2 = 2048(0x800, float:2.87E-42)
            if (r1 > r2) goto L15
            int r1 = r7.getHeight()     // Catch: java.lang.Exception -> L64
            if (r1 <= r2) goto L13
            goto L15
        L13:
            r1 = r7
            goto L37
        L15:
            float r1 = (float) r2     // Catch: java.lang.Exception -> L64
            int r2 = r7.getWidth()     // Catch: java.lang.Exception -> L64
            int r3 = r7.getHeight()     // Catch: java.lang.Exception -> L64
            int r2 = java.lang.Math.max(r2, r3)     // Catch: java.lang.Exception -> L64
            float r2 = (float) r2     // Catch: java.lang.Exception -> L64
            float r1 = r1 / r2
            int r2 = r7.getWidth()     // Catch: java.lang.Exception -> L64
            float r2 = (float) r2     // Catch: java.lang.Exception -> L64
            float r2 = r2 * r1
            int r2 = (int) r2     // Catch: java.lang.Exception -> L64
            int r3 = r7.getHeight()     // Catch: java.lang.Exception -> L64
            float r3 = (float) r3     // Catch: java.lang.Exception -> L64
            float r3 = r3 * r1
            int r1 = (int) r3     // Catch: java.lang.Exception -> L64
            r3 = 1
            android.graphics.Bitmap r1 = android.graphics.Bitmap.createScaledBitmap(r7, r2, r1, r3)     // Catch: java.lang.Exception -> L64
        L37:
            kotlin.jvm.internal.Intrinsics.checkNotNull(r1)     // Catch: java.lang.Exception -> L64
            r2 = 75
        L3c:
            java.io.ByteArrayOutputStream r3 = new java.io.ByteArrayOutputStream     // Catch: java.lang.Exception -> L64
            r3.<init>()     // Catch: java.lang.Exception -> L64
            android.graphics.Bitmap$CompressFormat r4 = android.graphics.Bitmap.CompressFormat.JPEG     // Catch: java.lang.Exception -> L64
            r5 = r3
            java.io.OutputStream r5 = (java.io.OutputStream) r5     // Catch: java.lang.Exception -> L64
            r1.compress(r4, r2, r5)     // Catch: java.lang.Exception -> L64
            byte[] r3 = r3.toByteArray()     // Catch: java.lang.Exception -> L64
            java.lang.String r4 = "toByteArray(...)"
            kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(r3, r4)     // Catch: java.lang.Exception -> L64
            int r2 = r2 + (-10)
            int r4 = r3.length     // Catch: java.lang.Exception -> L64
            r5 = 1048576(0x100000, float:1.469368E-39)
            if (r4 <= r5) goto L5e
            r4 = 30
            if (r2 >= r4) goto L3c
        L5e:
            if (r1 == r7) goto L63
            r1.recycle()     // Catch: java.lang.Exception -> L64
        L63:
            return r3
        L64:
            r7 = move-exception
            r7.printStackTrace()
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.apk.claw.android.ui.chat.ChatActivity.compressImage(android.graphics.Bitmap):byte[]");
    }

    @Override // com.apk.claw.android.base.BaseActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content);
        View childAt = viewGroup != null ? viewGroup.getChildAt(0) : null;
        ViewGroup viewGroup2 = childAt instanceof ViewGroup ? (ViewGroup) childAt : null;
        if (viewGroup2 != null) {
            ViewCompat.setOnApplyWindowInsetsListener(viewGroup2, new OnApplyWindowInsetsListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda0
                @Override // androidx.core.view.OnApplyWindowInsetsListener
                public final WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsetsCompat) {
                    WindowInsetsCompat onCreate$lambda$3$lambda$2;
                    onCreate$lambda$3$lambda$2 = ChatActivity.onCreate$lambda$3$lambda$2(view, windowInsetsCompat);
                    return onCreate$lambda$3$lambda$2;
                }
            });
        }
        ChatActivity chatActivity = this;
        this.skillSystem = FeatureIntegrationManager.INSTANCE.getInstance(chatActivity).getSkillSystem();
        initViews();
        ChatAdapter chatAdapter = new ChatAdapter();
        this.adapter = chatAdapter;
        Markwon build = Markwon.builder(chatActivity).usePlugin(TablePlugin.create(chatActivity)).build();
        Intrinsics.checkNotNullExpressionValue(build, "build(...)");
        chatAdapter.setMarkwon(build);
        RecyclerView recyclerView = this.rvMessages;
        if (recyclerView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView = null;
        }
        ChatAdapter chatAdapter2 = this.adapter;
        if (chatAdapter2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter2 = null;
        }
        recyclerView.setAdapter(chatAdapter2);
        RecyclerView recyclerView2 = this.rvMessages;
        if (recyclerView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView2 = null;
        }
        recyclerView2.setLayoutManager(new LinearLayoutManager(chatActivity));
        RecyclerView recyclerView3 = this.rvMessages;
        if (recyclerView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView3 = null;
        }
        recyclerView3.setItemAnimator(null);
        loadChatHistory();
        handleSkillIntent();
        handleScreenshotIntent();
        handlePushIntent();
        handleVoiceFloatIntent();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final WindowInsetsCompat onCreate$lambda$3$lambda$2(View v, WindowInsetsCompat windowInsets) {
        Intrinsics.checkNotNullParameter(v, "v");
        Intrinsics.checkNotNullParameter(windowInsets, "windowInsets");
        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
        return windowInsets;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void notificationPermissionLauncher$lambda$4(ChatActivity this$0, Boolean bool) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (bool.booleanValue()) {
            return;
        }
        Toast.makeText(this$0, "未授予通知权限，将无法收到推送提醒", 0).show();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onStart() {
        super.onStart();
        Switch r0 = this.switchCloudMode;
        if (r0 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("switchCloudMode");
            r0 = null;
        }
        if (r0.isChecked()) {
            requestNotificationPermission();
            CloudChatManager.INSTANCE.setPushListener(this.pushListener);
            CloudChatManager.INSTANCE.connectForPush();
            startObservingConnectionState();
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        super.onPause();
        persistChatHistory();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onStop() {
        super.onStop();
        CloudChatManager.INSTANCE.setPushListener(null);
        stopObservingConnectionState();
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    protected void onNewIntent(Intent intent) {
        Intrinsics.checkNotNullParameter(intent, "intent");
        super.onNewIntent(intent);
        setIntent(intent);
        this.voiceFloatTextHandled = false;
        handleScreenshotIntent();
        handleVoiceFloatIntent();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
        VoiceInputController voiceInputController = this.voiceInputController;
        if (voiceInputController != null) {
            voiceInputController.destroy();
        }
        this.voiceInputController = null;
        VoiceInteractionFloatWindow.INSTANCE.setOnVoiceResultCallback(null);
        CloudChatManager.INSTANCE.disconnect();
    }

    private final void handlePushIntent() {
        String stringExtra = getIntent().getStringExtra(EXTRA_PUSH_TEXT);
        if (stringExtra == null || stringExtra.length() <= 0) {
            return;
        }
        ChatAdapter chatAdapter = this.adapter;
        ChatAdapter chatAdapter2 = null;
        if (chatAdapter == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter = null;
        }
        chatAdapter.addMessage(new ChatMessage(stringExtra, false, null, System.currentTimeMillis(), false, 20, null));
        RecyclerView recyclerView = this.rvMessages;
        if (recyclerView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView = null;
        }
        ChatAdapter chatAdapter3 = this.adapter;
        if (chatAdapter3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
        } else {
            chatAdapter2 = chatAdapter3;
        }
        recyclerView.smoothScrollToPosition(chatAdapter2.getItemCount() - 1);
        persistChatHistory();
        getIntent().removeExtra(EXTRA_PUSH_TEXT);
    }

    private final void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == 0) {
            return;
        }
        this.notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS");
    }

    private final void initViews() {
        CommonToolbar commonToolbar = (CommonToolbar) findViewById(R.id.toolbar);
        commonToolbar.setTitle(getString(R.string.chat_title));
        commonToolbar.setActionIcon(R.drawable.ic_minimize, new Function0<Unit>() { // from class: com.apk.claw.android.ui.chat.ChatActivity$initViews$1$1
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
                ChatActivity.this.finish();
            }
        });
        View findViewById = findViewById(R.id.rvMessages);
        Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)");
        this.rvMessages = (RecyclerView) findViewById;
        View findViewById2 = findViewById(R.id.etMessage);
        Intrinsics.checkNotNullExpressionValue(findViewById2, "findViewById(...)");
        this.etMessage = (EditText) findViewById2;
        View findViewById3 = findViewById(R.id.btnSend);
        Intrinsics.checkNotNullExpressionValue(findViewById3, "findViewById(...)");
        this.btnSend = (ImageView) findViewById3;
        View findViewById4 = findViewById(R.id.btnAttach);
        Intrinsics.checkNotNullExpressionValue(findViewById4, "findViewById(...)");
        this.btnAttach = (ImageView) findViewById4;
        View findViewById5 = findViewById(R.id.btnCamera);
        Intrinsics.checkNotNullExpressionValue(findViewById5, "findViewById(...)");
        this.btnCamera = (ImageView) findViewById5;
        View findViewById6 = findViewById(R.id.ivPreview);
        Intrinsics.checkNotNullExpressionValue(findViewById6, "findViewById(...)");
        this.ivPreview = (ImageView) findViewById6;
        View findViewById7 = findViewById(R.id.btnRemovePreview);
        Intrinsics.checkNotNullExpressionValue(findViewById7, "findViewById(...)");
        this.btnRemovePreview = (ImageView) findViewById7;
        ImageView imageView = this.btnSend;
        ImageView imageView2 = null;
        if (imageView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnSend");
            imageView = null;
        }
        imageView.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ChatActivity.initViews$lambda$6(ChatActivity.this, view);
            }
        });
        EditText editText = this.etMessage;
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("etMessage");
            editText = null;
        }
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda14
            @Override // android.widget.TextView.OnEditorActionListener
            public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                boolean initViews$lambda$7;
                initViews$lambda$7 = ChatActivity.initViews$lambda$7(ChatActivity.this, textView, i, keyEvent);
                return initViews$lambda$7;
            }
        });
        ImageView imageView3 = this.btnAttach;
        if (imageView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnAttach");
            imageView3 = null;
        }
        imageView3.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda15
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ChatActivity.initViews$lambda$8(ChatActivity.this, view);
            }
        });
        ImageView imageView4 = this.btnCamera;
        if (imageView4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnCamera");
            imageView4 = null;
        }
        imageView4.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ChatActivity.initViews$lambda$9(ChatActivity.this, view);
            }
        });
        View findViewById8 = findViewById(R.id.switchCloudMode);
        Intrinsics.checkNotNullExpressionValue(findViewById8, "findViewById(...)");
        Switch r0 = (Switch) findViewById8;
        this.switchCloudMode = r0;
        if (r0 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("switchCloudMode");
            r0 = null;
        }
        r0.setChecked(KVUtils.INSTANCE.isCloudChatEnabled());
        Switch r02 = this.switchCloudMode;
        if (r02 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("switchCloudMode");
            r02 = null;
        }
        r02.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda2
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                ChatActivity.initViews$lambda$10(ChatActivity.this, compoundButton, z);
            }
        });
        updateModeHint();
        setupLocalModelSwitch();
        View findViewById9 = findViewById(R.id.btnVoice);
        Intrinsics.checkNotNullExpressionValue(findViewById9, "findViewById(...)");
        ImageView imageView5 = (ImageView) findViewById9;
        this.btnVoice = imageView5;
        if (imageView5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnVoice");
            imageView5 = null;
        }
        imageView5.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ChatActivity.initViews$lambda$11(ChatActivity.this, view);
            }
        });
        ImageView imageView6 = this.btnRemovePreview;
        if (imageView6 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnRemovePreview");
        } else {
            imageView2 = imageView6;
        }
        imageView2.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ChatActivity.initViews$lambda$12(ChatActivity.this, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void initViews$lambda$6(ChatActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.sendMessage();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final boolean initViews$lambda$7(ChatActivity this$0, TextView textView, int i, KeyEvent keyEvent) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (i != 4) {
            return false;
        }
        this$0.sendMessage();
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void initViews$lambda$8(ChatActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.imagePickerLauncher.launch("image/*");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void initViews$lambda$9(ChatActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.cameraLauncher.launch(new Intent(this$0, (Class<?>) CameraActivity.class));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void initViews$lambda$10(ChatActivity this$0, CompoundButton compoundButton, boolean z) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(compoundButton, "<anonymous parameter 0>");
        KVUtils.INSTANCE.setCloudChatEnabled(z);
        this$0.updateModeHint();
        if (z) {
            this$0.requestNotificationPermission();
            CloudChatManager.INSTANCE.setPushListener(this$0.pushListener);
            CloudChatManager.INSTANCE.connectForPush();
            this$0.startObservingConnectionState();
            return;
        }
        CloudChatManager.INSTANCE.disconnect();
        this$0.stopObservingConnectionState();
        TextView textView = this$0.tvConnectionStatus;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvConnectionStatus");
            textView = null;
        }
        textView.setVisibility(8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void initViews$lambda$11(ChatActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.toggleVoiceInput();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void initViews$lambda$12(ChatActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        ImageView imageView = null;
        this$0.selectedImageUri = null;
        this$0.selectedImageData = null;
        ImageView imageView2 = this$0.ivPreview;
        if (imageView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("ivPreview");
            imageView2 = null;
        }
        imageView2.setVisibility(8);
        ImageView imageView3 = this$0.btnRemovePreview;
        if (imageView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnRemovePreview");
        } else {
            imageView = imageView3;
        }
        imageView.setVisibility(8);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v10, types: [android.view.View] */
    private final void setupLocalModelSwitch() {
        View findViewById = findViewById(R.id.layoutLocalModelSwitch);
        Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)");
        this.layoutLocalModelSwitch = findViewById;
        View findViewById2 = findViewById(R.id.switchLocalModel);
        Intrinsics.checkNotNullExpressionValue(findViewById2, "findViewById(...)");
        this.switchLocalModel = (Switch) findViewById2;
        View findViewById3 = findViewById(R.id.tvLocalModelName);
        Intrinsics.checkNotNullExpressionValue(findViewById3, "findViewById(...)");
        this.tvLocalModelName = (TextView) findViewById3;
        Switch r2 = null;
        if (KVUtils.INSTANCE.isLocalModelEnabled()) {
            View view = this.layoutLocalModelSwitch;
            if (view == null) {
                Intrinsics.throwUninitializedPropertyAccessException("layoutLocalModelSwitch");
                view = null;
            }
            view.setVisibility(0);
            String localModelId = KVUtils.INSTANCE.getLocalModelId();
            TextView textView = this.tvLocalModelName;
            if (textView == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvLocalModelName");
                textView = null;
            }
            textView.setText(getString(R.string.chat_local_model_label, new Object[]{getLocalModelDisplayName(localModelId)}));
            Switch r0 = this.switchLocalModel;
            if (r0 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("switchLocalModel");
                r0 = null;
            }
            r0.setChecked(KVUtils.INSTANCE.isLocalModelChatActive());
            Switch r02 = this.switchLocalModel;
            if (r02 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("switchLocalModel");
                r02 = null;
            }
            r02.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda6
                @Override // android.widget.CompoundButton.OnCheckedChangeListener
                public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                    ChatActivity.setupLocalModelSwitch$lambda$13(ChatActivity.this, compoundButton, z);
                }
            });
            Switch r03 = this.switchLocalModel;
            if (r03 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("switchLocalModel");
            } else {
                r2 = r03;
            }
            this.isLocalModelActive = r2.isChecked();
            return;
        }
        ?? r04 = this.layoutLocalModelSwitch;
        if (r04 == 0) {
            Intrinsics.throwUninitializedPropertyAccessException("layoutLocalModelSwitch");
        } else {
            r2 = r04;
        }
        r2.setVisibility(8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void setupLocalModelSwitch$lambda$13(ChatActivity this$0, CompoundButton compoundButton, boolean z) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(compoundButton, "<anonymous parameter 0>");
        this$0.isLocalModelActive = z;
        KVUtils.INSTANCE.setLocalModelChatActive(z);
        this$0.updateModeHint();
        if (z) {
            Switch r2 = this$0.switchCloudMode;
            TextView textView = null;
            if (r2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("switchCloudMode");
                r2 = null;
            }
            r2.setChecked(false);
            KVUtils.INSTANCE.setCloudChatEnabled(false);
            CloudChatManager.INSTANCE.disconnect();
            this$0.stopObservingConnectionState();
            TextView textView2 = this$0.tvConnectionStatus;
            if (textView2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvConnectionStatus");
            } else {
                textView = textView2;
            }
            textView.setVisibility(8);
            ClawApplicationKt.getAppViewModel().initAgent();
            Toast.makeText(this$0, this$0.getString(R.string.chat_local_model_activated), 0).show();
            return;
        }
        ClawApplicationKt.getAppViewModel().initAgent();
    }

    private final String getLocalModelDisplayName(String modelId) {
        Object obj;
        try {
            Iterator<T> it = LocalModelInfo.INSTANCE.getAVAILABLE_MODELS().iterator();
            while (true) {
                if (!it.hasNext()) {
                    obj = null;
                    break;
                }
                obj = it.next();
                if (Intrinsics.areEqual(((LocalModelInfo) obj).getId(), modelId)) {
                    break;
                }
            }
            LocalModelInfo localModelInfo = (LocalModelInfo) obj;
            if (localModelInfo == null) {
                return modelId;
            }
            String displayName = localModelInfo.getDisplayName();
            return displayName == null ? modelId : displayName;
        } catch (Exception unused) {
            return modelId;
        }
    }

    private final void updateModeHint() {
        View findViewById = findViewById(R.id.tvConnectionStatus);
        Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)");
        this.tvConnectionStatus = (TextView) findViewById;
        TextView textView = (TextView) findViewById(R.id.tvModeHint);
        Switch r1 = this.switchCloudMode;
        if (r1 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("switchCloudMode");
            r1 = null;
        }
        if (r1.isChecked()) {
            textView.setText("云端模式 · " + CloudChatManager.INSTANCE.getModeDisplayName());
            textView.setVisibility(0);
        } else {
            if (this.isLocalModelActive) {
                textView.setText(getString(R.string.chat_mode_local_model, new Object[]{getLocalModelDisplayName(KVUtils.INSTANCE.getLocalModelId())}));
                textView.setTextColor(getColor(R.color.colorBrandPrimary));
                textView.setVisibility(0);
                return;
            }
            textView.setText(getString(R.string.chat_mode_local));
            textView.setTextColor(getColor(R.color.colorTextSecondary));
            textView.setVisibility(0);
        }
    }

    private final void startObservingConnectionState() {
        Job launch$default;
        View findViewById = findViewById(R.id.tvConnectionStatus);
        Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)");
        TextView textView = (TextView) findViewById;
        this.tvConnectionStatus = textView;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvConnectionStatus");
            textView = null;
        }
        textView.setVisibility(0);
        launch$default = BuildersKt__Builders_commonKt.launch$default(CoroutineScopeKt.CoroutineScope(Dispatchers.getMain()), null, null, new ChatActivity$startObservingConnectionState$1(this, null), 3, null);
        this.connectionObserverJob = launch$default;
    }

    private final void stopObservingConnectionState() {
        Job job = this.connectionObserverJob;
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, (CancellationException) null, 1, (Object) null);
        }
        this.connectionObserverJob = null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void voicePermissionLauncher$lambda$15(ChatActivity this$0, Boolean bool) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNull(bool);
        if (bool.booleanValue()) {
            this$0.startListening();
        } else {
            Toast.makeText(this$0, this$0.getString(R.string.voice_permission_required), 0).show();
        }
    }

    private final void toggleVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != 0) {
            this.voicePermissionLauncher.launch("android.permission.RECORD_AUDIO");
            return;
        }
        VoiceInteractionFloatWindow voiceInteractionFloatWindow = VoiceInteractionFloatWindow.INSTANCE;
        voiceInteractionFloatWindow.setOnVoiceResultCallback(new ChatActivity$toggleVoiceInput$1(this));
        Application application = getApplication();
        Intrinsics.checkNotNullExpressionValue(application, "getApplication(...)");
        voiceInteractionFloatWindow.show(application);
    }

    private final void initVoiceController() {
        VoiceInputController voiceInputController = this.voiceInputController;
        if (voiceInputController != null) {
            voiceInputController.destroy();
        }
        VoiceInputController voiceInputController2 = new VoiceInputController(this);
        voiceInputController2.setListener(new VoiceInputController.Listener() { // from class: com.apk.claw.android.ui.chat.ChatActivity$initVoiceController$1
            @Override // com.apk.claw.android.voice.VoiceInputController.Listener
            public void onTranscribing() {
            }

            @Override // com.apk.claw.android.voice.VoiceInputController.Listener
            public void onRmsChanged(float f) {
                VoiceInputController.Listener.DefaultImpls.onRmsChanged(this, f);
            }

            @Override // com.apk.claw.android.voice.VoiceInputController.Listener
            public void onListeningStarted() {
                ImageView imageView;
                ChatActivity.this.isListening = true;
                imageView = ChatActivity.this.btnVoice;
                if (imageView == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("btnVoice");
                    imageView = null;
                }
                imageView.setImageResource(R.drawable.ic_mic_active);
                ChatActivity chatActivity = ChatActivity.this;
                Toast.makeText(chatActivity, chatActivity.getString(R.string.voice_listening), 0).show();
            }

            @Override // com.apk.claw.android.voice.VoiceInputController.Listener
            public void onPartialResults(String text) {
                EditText editText;
                EditText editText2;
                Intrinsics.checkNotNullParameter(text, "text");
                editText = ChatActivity.this.etMessage;
                EditText editText3 = null;
                if (editText == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("etMessage");
                    editText = null;
                }
                editText.setText(text);
                editText2 = ChatActivity.this.etMessage;
                if (editText2 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("etMessage");
                } else {
                    editText3 = editText2;
                }
                editText3.setSelection(text.length());
            }

            @Override // com.apk.claw.android.voice.VoiceInputController.Listener
            public void onFinalResult(String text) {
                ImageView imageView;
                EditText editText;
                EditText editText2;
                Intrinsics.checkNotNullParameter(text, "text");
                ChatActivity.this.isListening = false;
                imageView = ChatActivity.this.btnVoice;
                EditText editText3 = null;
                if (imageView == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("btnVoice");
                    imageView = null;
                }
                imageView.setImageResource(R.drawable.ic_mic);
                editText = ChatActivity.this.etMessage;
                if (editText == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("etMessage");
                    editText = null;
                }
                editText.setText(text);
                editText2 = ChatActivity.this.etMessage;
                if (editText2 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("etMessage");
                } else {
                    editText3 = editText2;
                }
                editText3.setSelection(text.length());
                ChatActivity.this.sendMessage();
            }

            @Override // com.apk.claw.android.voice.VoiceInputController.Listener
            public void onError(int errorCode, String message) {
                ImageView imageView;
                Intrinsics.checkNotNullParameter(message, "message");
                ChatActivity.this.isListening = false;
                imageView = ChatActivity.this.btnVoice;
                if (imageView == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("btnVoice");
                    imageView = null;
                }
                imageView.setImageResource(R.drawable.ic_mic);
                Toast.makeText(ChatActivity.this, message, 0).show();
            }
        });
        this.voiceInputController = voiceInputController2;
    }

    private final void startListening() {
        if (this.isListening) {
            return;
        }
        if (this.voiceInputController == null) {
            initVoiceController();
        }
        VoiceInputController voiceInputController = this.voiceInputController;
        if (voiceInputController != null) {
            voiceInputController.startListening();
        }
    }

    private final void stopListening() {
        this.isListening = false;
        ImageView imageView = this.btnVoice;
        if (imageView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnVoice");
            imageView = null;
        }
        imageView.setImageResource(R.drawable.ic_mic);
        VoiceInputController voiceInputController = this.voiceInputController;
        if (voiceInputController != null) {
            voiceInputController.stopListening();
        }
    }

    private final void loadChatHistory() {
        List<ChatMessage> loadMessages = INSTANCE.loadMessages();
        ChatAdapter chatAdapter = null;
        if (loadMessages.isEmpty()) {
            ChatAdapter chatAdapter2 = this.adapter;
            if (chatAdapter2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("adapter");
            } else {
                chatAdapter = chatAdapter2;
            }
            String string = getString(R.string.chat_welcome);
            Intrinsics.checkNotNullExpressionValue(string, "getString(...)");
            chatAdapter.addMessage(new ChatMessage(string, false, null, System.currentTimeMillis(), false, 20, null));
            return;
        }
        for (ChatMessage chatMessage : loadMessages) {
            ChatAdapter chatAdapter3 = this.adapter;
            if (chatAdapter3 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("adapter");
                chatAdapter3 = null;
            }
            chatAdapter3.addMessage(chatMessage);
        }
        RecyclerView recyclerView = this.rvMessages;
        if (recyclerView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView = null;
        }
        ChatAdapter chatAdapter4 = this.adapter;
        if (chatAdapter4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
        } else {
            chatAdapter = chatAdapter4;
        }
        recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void persistChatHistory() {
        Companion companion = INSTANCE;
        ChatAdapter chatAdapter = this.adapter;
        if (chatAdapter == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter = null;
        }
        companion.saveMessages(chatAdapter.getMessages());
    }

    private final void handleSkillIntent() {
        final String stringExtra = getIntent().getStringExtra("skill_prompt");
        String stringExtra2 = getIntent().getStringExtra("skill_name");
        String stringExtra3 = getIntent().getStringExtra(EXTRA_MATCHED_SKILL_ID);
        ChatAdapter chatAdapter = null;
        if (stringExtra3 != null) {
            SkillSystem skillSystem = this.skillSystem;
            if (skillSystem == null) {
                Intrinsics.throwUninitializedPropertyAccessException("skillSystem");
                skillSystem = null;
            }
            SkillSystem.Skill skill = skillSystem.getSkill(stringExtra3);
            if (skill != null) {
                ChatAdapter chatAdapter2 = this.adapter;
                if (chatAdapter2 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("adapter");
                } else {
                    chatAdapter = chatAdapter2;
                }
                String string = getString(R.string.skill_matched, new Object[]{skill.getName()});
                Intrinsics.checkNotNullExpressionValue(string, "getString(...)");
                chatAdapter.addMessage(new ChatMessage(string, false, null, System.currentTimeMillis(), false, 20, null));
                executeWithSkill(skill);
                return;
            }
        }
        if (stringExtra != null) {
            String str = stringExtra;
            if (str.length() > 0) {
                EditText editText = this.etMessage;
                if (editText == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("etMessage");
                    editText = null;
                }
                editText.setText(str);
                if (stringExtra2 != null) {
                    ChatAdapter chatAdapter3 = this.adapter;
                    if (chatAdapter3 == null) {
                        Intrinsics.throwUninitializedPropertyAccessException("adapter");
                    } else {
                        chatAdapter = chatAdapter3;
                    }
                    String string2 = getString(R.string.skill_executing, new Object[]{stringExtra2});
                    Intrinsics.checkNotNullExpressionValue(string2, "getString(...)");
                    chatAdapter.addMessage(new ChatMessage(string2, true, null, System.currentTimeMillis(), false, 20, null));
                }
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda11
                    @Override // java.lang.Runnable
                    public final void run() {
                        ChatActivity.handleSkillIntent$lambda$16(ChatActivity.this, stringExtra);
                    }
                }, 300L);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void handleSkillIntent$lambda$16(ChatActivity this$0, String str) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.sendMessageWithPrompt(str);
    }

    private final void handleScreenshotIntent() {
        String stringExtra = getIntent().getStringExtra(ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH);
        String stringExtra2 = getIntent().getStringExtra("voice_text");
        if (stringExtra != null) {
            handleScreenshotPath(stringExtra, stringExtra2);
            getIntent().removeExtra(ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH);
            getIntent().removeExtra("voice_text");
        }
    }

    private final void handleVoiceFloatIntent() {
        String stringExtra = getIntent().getStringExtra(ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH);
        String stringExtra2 = getIntent().getStringExtra("voice_text");
        if (stringExtra2 != null) {
            String str = stringExtra2;
            if (str.length() <= 0 || stringExtra != null || this.voiceFloatTextHandled) {
                return;
            }
            this.voiceFloatTextHandled = true;
            EditText editText = this.etMessage;
            if (editText == null) {
                Intrinsics.throwUninitializedPropertyAccessException("etMessage");
                editText = null;
            }
            editText.setText(str);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { // from class: com.apk.claw.android.ui.chat.ChatActivity$$ExternalSyntheticLambda5
                @Override // java.lang.Runnable
                public final void run() {
                    ChatActivity.handleVoiceFloatIntent$lambda$17(ChatActivity.this);
                }
            }, 300L);
            getIntent().removeExtra("voice_text");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void handleVoiceFloatIntent$lambda$17(ChatActivity this$0) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.sendMessage();
    }

    static /* synthetic */ void handleScreenshotPath$default(ChatActivity chatActivity, String str, String str2, int i, Object obj) {
        if ((i & 2) != 0) {
            str2 = null;
        }
        chatActivity.handleScreenshotPath(str, str2);
    }

    /* JADX WARN: Code restructure failed: missing block: B:21:0x0058, code lost:
    
        if (r3 == null) goto L22;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private final void handleScreenshotPath(java.lang.String r18, java.lang.String r19) {
        /*
            Method dump skipped, instructions count: 221
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.apk.claw.android.ui.chat.ChatActivity.handleScreenshotPath(java.lang.String, java.lang.String):void");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void handleScreenshotPath$lambda$19(ChatActivity this$0, String prompt, byte[] bArr) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(prompt, "$prompt");
        this$0.sendMessageInternal(prompt, bArr);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void sendMessage() {
        EditText editText = this.etMessage;
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("etMessage");
            editText = null;
        }
        String obj = StringsKt.trim((CharSequence) editText.getText().toString()).toString();
        if (obj.length() == 0 && this.selectedImageData == null) {
            return;
        }
        SkillSystem skillSystem = this.skillSystem;
        if (skillSystem == null) {
            Intrinsics.throwUninitializedPropertyAccessException("skillSystem");
            skillSystem = null;
        }
        SkillSystem.Skill matchSkill = skillSystem.matchSkill(obj);
        if (matchSkill != null) {
            showSkillMatchDialog(matchSkill, obj);
        } else {
            sendMessageInternal$default(this, obj, null, 2, null);
        }
    }

    private final void sendMessageWithPrompt(String prompt) {
        sendMessageInternal$default(this, prompt, null, 2, null);
    }

    private final void executeWithSkill(SkillSystem.Skill skill) {
        SkillSystem skillSystem;
        SkillSystem skillSystem2 = this.skillSystem;
        if (skillSystem2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("skillSystem");
            skillSystem = null;
        } else {
            skillSystem = skillSystem2;
        }
        sendMessageInternal$default(this, SkillSystem.buildSkillPrompt$default(skillSystem, skill, "", null, 4, null), null, 2, null);
    }

    private final void showSkillMatchDialog(SkillSystem.Skill skill, String originalText) {
        SkillSystem skillSystem;
        ChatAdapter chatAdapter = this.adapter;
        if (chatAdapter == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter = null;
        }
        String string = getString(R.string.skill_matched, new Object[]{skill.getName()});
        Intrinsics.checkNotNullExpressionValue(string, "getString(...)");
        chatAdapter.addMessage(new ChatMessage(string, false, null, System.currentTimeMillis(), false, 20, null));
        Toast.makeText(this, getString(R.string.skill_matched, new Object[]{skill.getName()}), 0).show();
        SkillSystem skillSystem2 = this.skillSystem;
        if (skillSystem2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("skillSystem");
            skillSystem = null;
        } else {
            skillSystem = skillSystem2;
        }
        sendMessageInternal$default(this, SkillSystem.buildSkillPrompt$default(skillSystem, skill, originalText, null, 4, null), null, 2, null);
    }

    static /* synthetic */ void sendMessageInternal$default(ChatActivity chatActivity, String str, byte[] bArr, int i, Object obj) {
        if ((i & 2) != 0) {
            bArr = null;
        }
        chatActivity.sendMessageInternal(str, bArr);
    }

    private final void sendMessageInternal(String text, byte[] imageData) {
        EditText editText = this.etMessage;
        Switch r1 = null;
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("etMessage");
            editText = null;
        }
        editText.getText().clear();
        if (imageData == null) {
            imageData = this.selectedImageData;
        }
        if (this.selectedImageData != null) {
            ImageView imageView = this.ivPreview;
            if (imageView == null) {
                Intrinsics.throwUninitializedPropertyAccessException("ivPreview");
                imageView = null;
            }
            imageView.setVisibility(8);
            ImageView imageView2 = this.btnRemovePreview;
            if (imageView2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("btnRemovePreview");
                imageView2 = null;
            }
            imageView2.setVisibility(8);
            this.selectedImageUri = null;
            this.selectedImageData = null;
        }
        ChatMessage chatMessage = new ChatMessage(text, true, imageData, System.currentTimeMillis(), false, 16, null);
        ChatAdapter chatAdapter = this.adapter;
        if (chatAdapter == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter = null;
        }
        chatAdapter.addMessage(chatMessage);
        RecyclerView recyclerView = this.rvMessages;
        if (recyclerView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView = null;
        }
        ChatAdapter chatAdapter2 = this.adapter;
        if (chatAdapter2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter2 = null;
        }
        recyclerView.smoothScrollToPosition(chatAdapter2.getItemCount() - 1);
        Switch r0 = this.switchCloudMode;
        if (r0 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("switchCloudMode");
        } else {
            r1 = r0;
        }
        if (r1.isChecked()) {
            sendCloudMessage(text);
        } else {
            sendLocalMessage(text, imageData);
        }
    }

    private final void sendLocalMessage(String text, byte[] imageData) {
        ChatAdapter chatAdapter = null;
        if (ClawApplicationKt.getAppViewModel().isTaskRunning()) {
            ChatAdapter chatAdapter2 = this.adapter;
            if (chatAdapter2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("adapter");
            } else {
                chatAdapter = chatAdapter2;
            }
            String string = getString(R.string.chat_task_running);
            Intrinsics.checkNotNullExpressionValue(string, "getString(...)");
            chatAdapter.addMessage(new ChatMessage(string, false, null, System.currentTimeMillis(), false, 20, null));
            return;
        }
        String string2 = getString(R.string.chat_thinking);
        Intrinsics.checkNotNullExpressionValue(string2, "getString(...)");
        ChatMessage chatMessage = new ChatMessage(string2, false, null, System.currentTimeMillis(), true, 4, null);
        ChatAdapter chatAdapter3 = this.adapter;
        if (chatAdapter3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter3 = null;
        }
        chatAdapter3.addMessage(chatMessage);
        RecyclerView recyclerView = this.rvMessages;
        if (recyclerView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView = null;
        }
        ChatAdapter chatAdapter4 = this.adapter;
        if (chatAdapter4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
        } else {
            chatAdapter = chatAdapter4;
        }
        recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        ClawApplicationKt.getAppViewModel().startChatTask(text, imageData, new ChatActivity$sendLocalMessage$1(this, new StringBuilder(), new Ref.ObjectRef(), new Handler(Looper.getMainLooper())));
    }

    private final void sendCloudMessage(String text) {
        String string = getString(R.string.chat_thinking);
        Intrinsics.checkNotNullExpressionValue(string, "getString(...)");
        ChatMessage chatMessage = new ChatMessage(string, false, null, System.currentTimeMillis(), true, 4, null);
        ChatAdapter chatAdapter = this.adapter;
        ChatAdapter chatAdapter2 = null;
        if (chatAdapter == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
            chatAdapter = null;
        }
        chatAdapter.addMessage(chatMessage);
        RecyclerView recyclerView = this.rvMessages;
        if (recyclerView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("rvMessages");
            recyclerView = null;
        }
        ChatAdapter chatAdapter3 = this.adapter;
        if (chatAdapter3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("adapter");
        } else {
            chatAdapter2 = chatAdapter3;
        }
        recyclerView.smoothScrollToPosition(chatAdapter2.getItemCount() - 1);
        Ref.BooleanRef booleanRef = new Ref.BooleanRef();
        booleanRef.element = true;
        CloudChatManager.INSTANCE.sendMessage(text, new ChatActivity$sendCloudMessage$1(this, booleanRef));
    }

    /* compiled from: ChatActivity.kt */
    @Metadata(d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\t\n\u0002\b\u0012\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B3\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\b\b\u0002\u0010\n\u001a\u00020\u0005¢\u0006\u0002\u0010\u000bJ\t\u0010\u0013\u001a\u00020\u0003HÆ\u0003J\t\u0010\u0014\u001a\u00020\u0005HÆ\u0003J\u000b\u0010\u0015\u001a\u0004\u0018\u00010\u0007HÆ\u0003J\t\u0010\u0016\u001a\u00020\tHÆ\u0003J\t\u0010\u0017\u001a\u00020\u0005HÆ\u0003J=\u0010\u0018\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\n\u001a\u00020\u0005HÆ\u0001J\u0013\u0010\u0019\u001a\u00020\u00052\b\u0010\u001a\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u001b\u001a\u00020\u001cHÖ\u0001J\t\u0010\u001d\u001a\u00020\u0003HÖ\u0001R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0007¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\n\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u0004\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\t¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012¨\u0006\u001e"}, d2 = {"Lcom/apk/claw/android/ui/chat/ChatActivity$ChatMessage;", "", "text", "", "isUser", "", "imageData", "", "timestamp", "", "isThinking", "(Ljava/lang/String;Z[BJZ)V", "getImageData", "()[B", "()Z", "getText", "()Ljava/lang/String;", "getTimestamp", "()J", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "other", "hashCode", "", "toString", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final /* data */ class ChatMessage {
        private final byte[] imageData;
        private final boolean isThinking;
        private final boolean isUser;
        private final String text;
        private final long timestamp;

        public static /* synthetic */ ChatMessage copy$default(ChatMessage chatMessage, String str, boolean z, byte[] bArr, long j, boolean z2, int i, Object obj) {
            if ((i & 1) != 0) {
                str = chatMessage.text;
            }
            if ((i & 2) != 0) {
                z = chatMessage.isUser;
            }
            boolean z3 = z;
            if ((i & 4) != 0) {
                bArr = chatMessage.imageData;
            }
            byte[] bArr2 = bArr;
            if ((i & 8) != 0) {
                j = chatMessage.timestamp;
            }
            long j2 = j;
            if ((i & 16) != 0) {
                z2 = chatMessage.isThinking;
            }
            return chatMessage.copy(str, z3, bArr2, j2, z2);
        }

        /* renamed from: component1, reason: from getter */
        public final String getText() {
            return this.text;
        }

        /* renamed from: component2, reason: from getter */
        public final boolean getIsUser() {
            return this.isUser;
        }

        /* renamed from: component3, reason: from getter */
        public final byte[] getImageData() {
            return this.imageData;
        }

        /* renamed from: component4, reason: from getter */
        public final long getTimestamp() {
            return this.timestamp;
        }

        /* renamed from: component5, reason: from getter */
        public final boolean getIsThinking() {
            return this.isThinking;
        }

        public final ChatMessage copy(String text, boolean isUser, byte[] imageData, long timestamp, boolean isThinking) {
            Intrinsics.checkNotNullParameter(text, "text");
            return new ChatMessage(text, isUser, imageData, timestamp, isThinking);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ChatMessage)) {
                return false;
            }
            ChatMessage chatMessage = (ChatMessage) other;
            return Intrinsics.areEqual(this.text, chatMessage.text) && this.isUser == chatMessage.isUser && Intrinsics.areEqual(this.imageData, chatMessage.imageData) && this.timestamp == chatMessage.timestamp && this.isThinking == chatMessage.isThinking;
        }

        public int hashCode() {
            int hashCode = ((this.text.hashCode() * 31) + Boolean.hashCode(this.isUser)) * 31;
            byte[] bArr = this.imageData;
            return ((((hashCode + (bArr == null ? 0 : Arrays.hashCode(bArr))) * 31) + Long.hashCode(this.timestamp)) * 31) + Boolean.hashCode(this.isThinking);
        }

        public String toString() {
            return "ChatMessage(text=" + this.text + ", isUser=" + this.isUser + ", imageData=" + Arrays.toString(this.imageData) + ", timestamp=" + this.timestamp + ", isThinking=" + this.isThinking + ")";
        }

        public ChatMessage(String text, boolean z, byte[] bArr, long j, boolean z2) {
            Intrinsics.checkNotNullParameter(text, "text");
            this.text = text;
            this.isUser = z;
            this.imageData = bArr;
            this.timestamp = j;
            this.isThinking = z2;
        }

        public /* synthetic */ ChatMessage(String str, boolean z, byte[] bArr, long j, boolean z2, int i, DefaultConstructorMarker defaultConstructorMarker) {
            this(str, z, (i & 4) != 0 ? null : bArr, j, (i & 16) != 0 ? false : z2);
        }

        public final String getText() {
            return this.text;
        }

        public final boolean isUser() {
            return this.isUser;
        }

        public final byte[] getImageData() {
            return this.imageData;
        }

        public final long getTimestamp() {
            return this.timestamp;
        }

        public final boolean isThinking() {
            return this.isThinking;
        }
    }
}
