package com.apk.claw.android.local.llm;

import android.content.Context;
import android.util.Log;
import com.apk.claw.android.local.llm.LlamaState;
import kotlin.Metadata;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorKt;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.apache.http.cookie.ClientCookie;

/* compiled from: LlamaEngine.kt */
@Metadata(d1 = {"\u0000b\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u000e\n\u0002\u0010\u0012\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u000f\u0018\u0000 H2\u00020\u0001:\u0001HB\u0017\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005¢\u0006\u0002\u0010\u0006J\u000e\u0010\u001b\u001a\u00020\u001cH\u0086@¢\u0006\u0002\u0010\u001dJ\u000e\u0010\u001e\u001a\u00020\u001cH\u0086@¢\u0006\u0002\u0010\u001dJ\u000b\u0010\u001f\u001a\u0004\u0018\u00010\u0005H\u0082 J\t\u0010 \u001a\u00020!H\u0082 J\u0011\u0010\"\u001a\u00020\u001c2\u0006\u0010\u0004\u001a\u00020\u0005H\u0082 J\u0011\u0010#\u001a\u00020!2\u0006\u0010$\u001a\u00020\u0005H\u0082 J\u0019\u0010%\u001a\u00020!2\u0006\u0010&\u001a\u00020\u00052\u0006\u0010'\u001a\u00020!H\u0082 J\"\u0010(\u001a\u00020\u001c2\u0006\u0010)\u001a\u00020\u00052\n\b\u0002\u0010*\u001a\u0004\u0018\u00010\u0005H\u0086@¢\u0006\u0002\u0010+J\t\u0010,\u001a\u00020\u001cH\u0082 J\t\u0010-\u001a\u00020\u001cH\u0082 J\u0016\u0010.\u001a\u00020\u001c2\u0006\u0010/\u001a\u000200H\u0086@¢\u0006\u0002\u00101J\u0019\u0010.\u001a\u00020!2\u0006\u0010/\u001a\u0002002\u0006\u00102\u001a\u00020!H\u0082 J\t\u00103\u001a\u00020!H\u0082 J\u0011\u00104\u001a\u00020!2\u0006\u00105\u001a\u00020\u0005H\u0082 J\u0019\u00106\u001a\u00020!2\u0006\u00107\u001a\u00020\u00052\u0006\u00108\u001a\u00020!H\u0082 J\u001e\u00109\u001a\b\u0012\u0004\u0012\u00020\u00050:2\u0006\u0010;\u001a\u00020\u00052\b\b\u0002\u00108\u001a\u00020!J\u0011\u0010<\u001a\u00020\u001c2\u0006\u0010=\u001a\u00020!H\u0082 J\u0011\u0010>\u001a\u00020\u001c2\u0006\u0010?\u001a\u00020!H\u0082 J\u0016\u0010@\u001a\u00020\u001c2\u0006\u0010A\u001a\u00020\u0005H\u0086@¢\u0006\u0002\u0010BJ\t\u0010C\u001a\u00020\u001cH\u0082 J\u0006\u0010D\u001a\u00020\u001cJ\t\u0010E\u001a\u00020\u0005H\u0082 J\t\u0010F\u001a\u00020\u001cH\u0082 J\u000e\u0010G\u001a\u00020\u0001H\u0086@¢\u0006\u0002\u0010\u001dR\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\bX\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\fX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010\u000e\u001a\u00020\b8F¢\u0006\u0006\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0010\u001a\u00020\b8F¢\u0006\u0006\u001a\u0004\b\u0010\u0010\u000fR\u0016\u0010\u0011\u001a\u00020\u00128\u0002X\u0083\u0004¢\u0006\b\n\u0000\u0012\u0004\b\u0013\u0010\u0014R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u0017\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\r0\u0018¢\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001a¨\u0006I"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaEngine;", "", "context", "Landroid/content/Context;", "nativeLibDir", "", "(Landroid/content/Context;Ljava/lang/String;)V", "_cancelGeneration", "", "_mmprojLoaded", "_readyForSystemPrompt", "_state", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/apk/claw/android/local/llm/LlamaState;", "isModelLoaded", "()Z", "isVisionSupported", "llamaDispatcher", "Lkotlinx/coroutines/CoroutineDispatcher;", "getLlamaDispatcher$annotations", "()V", "llamaScope", "Lkotlinx/coroutines/CoroutineScope;", "state", "Lkotlinx/coroutines/flow/StateFlow;", "getState", "()Lkotlinx/coroutines/flow/StateFlow;", "cancelGeneration", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "fullReset", "generateNextToken", "getMinicpmvVersionNative", "", "init", "load", "modelPath", "loadMmproj", "mmprojPath", "imageMaxSliceNums", "loadModel", "pathToModel", "pathToMmproj", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "nativeCancelGeneration", "nativeFullReset", "prefillImage", "imageData", "", "([BLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "imageSize", "prepare", "processSystemPrompt", "systemPrompt", "processUserPrompt", "userPrompt", "predictLength", "sendUserPrompt", "Lkotlinx/coroutines/flow/Flow;", "message", "setImageMaxSliceNumsNative", "n", "setMinicpmvVersionNative", ClientCookie.VERSION_ATTR, "setSystemPrompt", "prompt", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "shutdown", "shutdownEngine", "systemInfo", "unload", "unloadModel", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class LlamaEngine {
    public static final int DEFAULT_PREDICT_LENGTH = 512;
    private static volatile LlamaEngine instance;
    private volatile boolean _cancelGeneration;
    private volatile boolean _mmprojLoaded;
    private volatile boolean _readyForSystemPrompt;
    private final MutableStateFlow<LlamaState> _state;
    private final Context context;
    private final CoroutineDispatcher llamaDispatcher;
    private final CoroutineScope llamaScope;
    private final String nativeLibDir;
    private final StateFlow<LlamaState> state;

    /* renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private static final String TAG = "LlamaEngine";

    public /* synthetic */ LlamaEngine(Context context, String str, DefaultConstructorMarker defaultConstructorMarker) {
        this(context, str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final native String generateNextToken();

    private static /* synthetic */ void getLlamaDispatcher$annotations() {
    }

    private final native int getMinicpmvVersionNative();

    /* JADX INFO: Access modifiers changed from: private */
    public final native void init(String nativeLibDir);

    /* JADX INFO: Access modifiers changed from: private */
    public final native int load(String modelPath);

    /* JADX INFO: Access modifiers changed from: private */
    public final native int loadMmproj(String mmprojPath, int imageMaxSliceNums);

    /* JADX INFO: Access modifiers changed from: private */
    public final native void nativeCancelGeneration();

    /* JADX INFO: Access modifiers changed from: private */
    public final native void nativeFullReset();

    /* JADX INFO: Access modifiers changed from: private */
    public final native int prefillImage(byte[] imageData, int imageSize);

    /* JADX INFO: Access modifiers changed from: private */
    public final native int prepare();

    /* JADX INFO: Access modifiers changed from: private */
    public final native int processSystemPrompt(String systemPrompt);

    /* JADX INFO: Access modifiers changed from: private */
    public final native int processUserPrompt(String userPrompt, int predictLength);

    private final native void setImageMaxSliceNumsNative(int n);

    private final native void setMinicpmvVersionNative(int version);

    /* JADX INFO: Access modifiers changed from: private */
    public final native void shutdown();

    /* JADX INFO: Access modifiers changed from: private */
    public final native String systemInfo();

    /* JADX INFO: Access modifiers changed from: private */
    public final native void unload();

    private LlamaEngine(Context context, String str) {
        this.context = context;
        this.nativeLibDir = str;
        CoroutineDispatcher limitedParallelism = Dispatchers.getIO().limitedParallelism(1);
        this.llamaDispatcher = limitedParallelism;
        CoroutineScope CoroutineScope = CoroutineScopeKt.CoroutineScope(limitedParallelism.plus(SupervisorKt.SupervisorJob$default((Job) null, 1, (Object) null)));
        this.llamaScope = CoroutineScope;
        MutableStateFlow<LlamaState> MutableStateFlow = StateFlowKt.MutableStateFlow(LlamaState.Uninitialized.INSTANCE);
        this._state = MutableStateFlow;
        this.state = FlowKt.asStateFlow(MutableStateFlow);
        BuildersKt__Builders_commonKt.launch$default(CoroutineScope, null, null, new AnonymousClass1(null), 3, null);
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u000e\u0010\n\u001a\u00020\t2\u0006\u0010\u000b\u001a\u00020\fR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u0016\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\b\u001a\u0004\u0018\u00010\tX\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006\r"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaEngine$Companion;", "", "()V", "DEFAULT_PREDICT_LENGTH", "", "TAG", "", "kotlin.jvm.PlatformType", "instance", "Lcom/apk/claw/android/local/llm/LlamaEngine;", "getInstance", "context", "Landroid/content/Context;", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final LlamaEngine getInstance(Context context) {
            LlamaEngine llamaEngine;
            Intrinsics.checkNotNullParameter(context, "context");
            LlamaEngine llamaEngine2 = LlamaEngine.instance;
            if (llamaEngine2 != null) {
                return llamaEngine2;
            }
            synchronized (this) {
                String str = context.getApplicationInfo().nativeLibraryDir;
                Intrinsics.checkNotNull(str);
                if (StringsKt.isBlank(str)) {
                    throw new IllegalArgumentException("Expected a valid native library path!".toString());
                }
                llamaEngine = new LlamaEngine(context, str, null);
                Companion companion = LlamaEngine.INSTANCE;
                LlamaEngine.instance = llamaEngine;
            }
            return llamaEngine;
        }
    }

    public final StateFlow<LlamaState> getState() {
        return this.state;
    }

    public final boolean isModelLoaded() {
        return this._state.getValue() instanceof LlamaState.ModelReady;
    }

    /* renamed from: isVisionSupported, reason: from getter */
    public final boolean get_mmprojLoaded() {
        return this._mmprojLoaded;
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
    @DebugMetadata(c = "com.apk.claw.android.local.llm.LlamaEngine$1", f = "LlamaEngine.kt", i = {}, l = {}, m = "invokeSuspend", n = {}, s = {})
    /* renamed from: com.apk.claw.android.local.llm.LlamaEngine$1, reason: invalid class name */
    static final class AnonymousClass1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
        int label;

        AnonymousClass1(Continuation<? super AnonymousClass1> continuation) {
            super(2, continuation);
        }

        @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
        public final Continuation<Unit> create(Object obj, Continuation<?> continuation) {
            return LlamaEngine.this.new AnonymousClass1(continuation);
        }

        @Override // kotlin.jvm.functions.Function2
        public final Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
            return ((AnonymousClass1) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE);
        }

        @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
        public final Object invokeSuspend(Object obj) {
            boolean z;
            LlamaEngine llamaEngine;
            IntrinsicsKt.getCOROUTINE_SUSPENDED();
            if (this.label == 0) {
                ResultKt.throwOnFailure(obj);
                try {
                    try {
                        z = LlamaEngine.this._state.getValue() instanceof LlamaState.Uninitialized;
                        llamaEngine = LlamaEngine.this;
                    } catch (Exception e) {
                        Log.e(LlamaEngine.TAG, "Failed to load native library", e);
                        LlamaEngine.this._state.setValue(new LlamaState.Error(e));
                    }
                } catch (UnsatisfiedLinkError e2) {
                    Log.e(LlamaEngine.TAG, "Native library not available in this build. On-device inference is disabled.", e2);
                    LlamaEngine.this._state.setValue(new LlamaState.Error(new RuntimeException("Native library not available. On-device inference requires NDK build with llama.cpp submodule.")));
                }
                if (z) {
                    llamaEngine._state.setValue(LlamaState.Initializing.INSTANCE);
                    Log.i(LlamaEngine.TAG, "Loading native library...");
                    Log.i(LlamaEngine.TAG, CpuFeatures.INSTANCE.summary());
                    String bestGgmlCpuVariant = CpuFeatures.INSTANCE.bestGgmlCpuVariant();
                    if (bestGgmlCpuVariant != null) {
                        try {
                            Log.i(LlamaEngine.TAG, "Pre-loading optimised ggml-cpu (" + bestGgmlCpuVariant + ")");
                            System.loadLibrary("ggml-cpu-" + bestGgmlCpuVariant);
                            Log.i(LlamaEngine.TAG, "Optimised ggml-cpu (" + bestGgmlCpuVariant + ") loaded successfully");
                        } catch (UnsatisfiedLinkError e3) {
                            Log.w(LlamaEngine.TAG, "Optimised ggml-cpu-" + bestGgmlCpuVariant + " not available, using baseline", e3);
                        }
                    }
                    System.loadLibrary("apkclaw_llama");
                    LlamaEngine llamaEngine2 = LlamaEngine.this;
                    llamaEngine2.init(llamaEngine2.nativeLibDir);
                    LlamaEngine.this._state.setValue(LlamaState.Initialized.INSTANCE);
                    Log.i(LlamaEngine.TAG, "Native library loaded! System info: \n" + LlamaEngine.this.systemInfo());
                    return Unit.INSTANCE;
                }
                throw new IllegalStateException(("Cannot load native library in " + llamaEngine._state.getValue().getClass().getSimpleName() + "!").toString());
            }
            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
        }
    }

    public static /* synthetic */ Object loadModel$default(LlamaEngine llamaEngine, String str, String str2, Continuation continuation, int i, Object obj) {
        if ((i & 2) != 0) {
            str2 = null;
        }
        return llamaEngine.loadModel(str, str2, continuation);
    }

    public final Object loadModel(String str, String str2, Continuation<? super Unit> continuation) {
        Object withContext = BuildersKt.withContext(this.llamaDispatcher, new LlamaEngine$loadModel$2(this, str, str2, null), continuation);
        return withContext == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? withContext : Unit.INSTANCE;
    }

    public final Object setSystemPrompt(String str, Continuation<? super Unit> continuation) {
        Object withContext = BuildersKt.withContext(this.llamaDispatcher, new LlamaEngine$setSystemPrompt$2(str, this, null), continuation);
        return withContext == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? withContext : Unit.INSTANCE;
    }

    public final Object prefillImage(byte[] bArr, Continuation<? super Unit> continuation) {
        Object withContext = BuildersKt.withContext(this.llamaDispatcher, new LlamaEngine$prefillImage$2(this, bArr, null), continuation);
        return withContext == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? withContext : Unit.INSTANCE;
    }

    public static /* synthetic */ Flow sendUserPrompt$default(LlamaEngine llamaEngine, String str, int i, int i2, Object obj) {
        if ((i2 & 2) != 0) {
            i = 512;
        }
        return llamaEngine.sendUserPrompt(str, i);
    }

    public final Flow<String> sendUserPrompt(String message, int predictLength) {
        Intrinsics.checkNotNullParameter(message, "message");
        return FlowKt.flowOn(FlowKt.flow(new LlamaEngine$sendUserPrompt$1(this, message, predictLength, null)), this.llamaDispatcher);
    }

    public final Object cancelGeneration(Continuation<? super Unit> continuation) {
        Object withContext = BuildersKt.withContext(this.llamaDispatcher, new LlamaEngine$cancelGeneration$2(this, null), continuation);
        return withContext == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? withContext : Unit.INSTANCE;
    }

    public final Object fullReset(Continuation<? super Unit> continuation) {
        Object withContext = BuildersKt.withContext(this.llamaDispatcher, new LlamaEngine$fullReset$2(this, null), continuation);
        return withContext == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? withContext : Unit.INSTANCE;
    }

    public final Object unloadModel(Continuation<Object> continuation) {
        return BuildersKt.withContext(this.llamaDispatcher, new LlamaEngine$unloadModel$2(this, null), continuation);
    }

    public final void shutdownEngine() {
        BuildersKt__Builders_commonKt.launch$default(this.llamaScope, null, null, new LlamaEngine$shutdownEngine$1(this, null), 3, null);
    }
}
