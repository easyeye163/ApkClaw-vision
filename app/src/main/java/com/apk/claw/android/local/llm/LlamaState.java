package com.apk.claw.android.local.llm;

import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: LlamaEngine.kt */
@Metadata(d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u000b\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\rB\u0007\b\u0004¢\u0006\u0002\u0010\u0002\u0082\u0001\u000b\u000e\u000f\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018¨\u0006\u0019"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState;", "", "()V", "Error", "Generating", "Initialized", "Initializing", "LoadingModel", "ModelReady", "PrefillingImage", "ProcessingSystemPrompt", "ProcessingUserPrompt", "Uninitialized", "UnloadingModel", "Lcom/apk/claw/android/local/llm/LlamaState$Error;", "Lcom/apk/claw/android/local/llm/LlamaState$Generating;", "Lcom/apk/claw/android/local/llm/LlamaState$Initialized;", "Lcom/apk/claw/android/local/llm/LlamaState$Initializing;", "Lcom/apk/claw/android/local/llm/LlamaState$LoadingModel;", "Lcom/apk/claw/android/local/llm/LlamaState$ModelReady;", "Lcom/apk/claw/android/local/llm/LlamaState$PrefillingImage;", "Lcom/apk/claw/android/local/llm/LlamaState$ProcessingSystemPrompt;", "Lcom/apk/claw/android/local/llm/LlamaState$ProcessingUserPrompt;", "Lcom/apk/claw/android/local/llm/LlamaState$Uninitialized;", "Lcom/apk/claw/android/local/llm/LlamaState$UnloadingModel;", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public abstract class LlamaState {
    public /* synthetic */ LlamaState(DefaultConstructorMarker defaultConstructorMarker) {
        this();
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$Uninitialized;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Uninitialized extends LlamaState {
        public static final Uninitialized INSTANCE = new Uninitialized();

        private Uninitialized() {
            super(null);
        }
    }

    private LlamaState() {
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$Initializing;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Initializing extends LlamaState {
        public static final Initializing INSTANCE = new Initializing();

        private Initializing() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$Initialized;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Initialized extends LlamaState {
        public static final Initialized INSTANCE = new Initialized();

        private Initialized() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$LoadingModel;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class LoadingModel extends LlamaState {
        public static final LoadingModel INSTANCE = new LoadingModel();

        private LoadingModel() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$ModelReady;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class ModelReady extends LlamaState {
        public static final ModelReady INSTANCE = new ModelReady();

        private ModelReady() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$ProcessingSystemPrompt;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class ProcessingSystemPrompt extends LlamaState {
        public static final ProcessingSystemPrompt INSTANCE = new ProcessingSystemPrompt();

        private ProcessingSystemPrompt() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$PrefillingImage;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class PrefillingImage extends LlamaState {
        public static final PrefillingImage INSTANCE = new PrefillingImage();

        private PrefillingImage() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$ProcessingUserPrompt;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class ProcessingUserPrompt extends LlamaState {
        public static final ProcessingUserPrompt INSTANCE = new ProcessingUserPrompt();

        private ProcessingUserPrompt() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$Generating;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Generating extends LlamaState {
        public static final Generating INSTANCE = new Generating();

        private Generating() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002¨\u0006\u0003"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$UnloadingModel;", "Lcom/apk/claw/android/local/llm/LlamaState;", "()V", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class UnloadingModel extends LlamaState {
        public static final UnloadingModel INSTANCE = new UnloadingModel();

        private UnloadingModel() {
            super(null);
        }
    }

    /* compiled from: LlamaEngine.kt */
    @Metadata(d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\u0010\u0002\u001a\u00060\u0003j\u0002`\u0004¢\u0006\u0002\u0010\u0005J\r\u0010\b\u001a\u00060\u0003j\u0002`\u0004HÆ\u0003J\u0017\u0010\t\u001a\u00020\u00002\f\b\u0002\u0010\u0002\u001a\u00060\u0003j\u0002`\u0004HÆ\u0001J\u0013\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rHÖ\u0003J\t\u0010\u000e\u001a\u00020\u000fHÖ\u0001J\t\u0010\u0010\u001a\u00020\u0011HÖ\u0001R\u0015\u0010\u0002\u001a\u00060\u0003j\u0002`\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007¨\u0006\u0012"}, d2 = {"Lcom/apk/claw/android/local/llm/LlamaState$Error;", "Lcom/apk/claw/android/local/llm/LlamaState;", "exception", "Ljava/lang/Exception;", "Lkotlin/Exception;", "(Ljava/lang/Exception;)V", "getException", "()Ljava/lang/Exception;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final /* data */ class Error extends LlamaState {
        private final Exception exception;

        public static /* synthetic */ Error copy$default(Error error, Exception exc, int i, Object obj) {
            if ((i & 1) != 0) {
                exc = error.exception;
            }
            return error.copy(exc);
        }

        /* renamed from: component1, reason: from getter */
        public final Exception getException() {
            return this.exception;
        }

        public final Error copy(Exception exception) {
            Intrinsics.checkNotNullParameter(exception, "exception");
            return new Error(exception);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return (other instanceof Error) && Intrinsics.areEqual(this.exception, ((Error) other).exception);
        }

        public int hashCode() {
            return this.exception.hashCode();
        }

        public String toString() {
            return "Error(exception=" + this.exception + ")";
        }

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public Error(Exception exception) {
            super(null);
            Intrinsics.checkNotNullParameter(exception, "exception");
            this.exception = exception;
        }

        public final Exception getException() {
            return this.exception;
        }
    }
}
