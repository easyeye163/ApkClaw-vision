package com.apk.claw.android.ui.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.apache.http.HttpStatus;

/* compiled from: LocalModelInfo.kt */
@Metadata(d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0016\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0006\b\u0086\b\u0018\u0000 52\u00020\u0001:\u00015Bk\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\f\u001a\u00020\u0003¢\u0006\u0002\u0010\rJ\u0018\u0010\u0019\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00030\u001b0\u001aJ\t\u0010\u001c\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001d\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001e\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001f\u001a\u00020\u0003HÆ\u0003J\t\u0010 \u001a\u00020\u0003HÆ\u0003J\u000b\u0010!\u001a\u0004\u0018\u00010\u0003HÆ\u0003J\u000b\u0010\"\u001a\u0004\u0018\u00010\u0003HÆ\u0003J\u000b\u0010#\u001a\u0004\u0018\u00010\u0003HÆ\u0003J\u000b\u0010$\u001a\u0004\u0018\u00010\u0003HÆ\u0003J\u000b\u0010%\u001a\u0004\u0018\u00010\u0003HÆ\u0003Jw\u0010&\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00032\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\f\u001a\u00020\u0003HÆ\u0001J\u000e\u0010'\u001a\u00020(2\u0006\u0010)\u001a\u00020*J\u0013\u0010+\u001a\u00020,2\b\u0010-\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\u000e\u0010.\u001a\u00020*2\u0006\u0010)\u001a\u00020*J\t\u0010/\u001a\u000200HÖ\u0001J\u000e\u00101\u001a\u00020,2\u0006\u0010)\u001a\u00020*J\u0010\u00102\u001a\u0004\u0018\u00010*2\u0006\u0010)\u001a\u00020*J\u000e\u00103\u001a\u00020*2\u0006\u0010)\u001a\u00020*J\t\u00104\u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0005\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0013\u0010\n\u001a\u0004\u0018\u00010\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u000fR\u0013\u0010\u000b\u001a\u0004\u0018\u00010\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u000fR\u0011\u0010\u0004\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u000fR\u0011\u0010\u0006\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u000fR\u0013\u0010\b\u001a\u0004\u0018\u00010\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u000fR\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u000fR\u0013\u0010\u0007\u001a\u0004\u0018\u00010\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u000fR\u0011\u0010\f\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u000fR\u0013\u0010\t\u001a\u0004\u0018\u00010\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u000f¨\u00066"}, d2 = {"Lcom/apk/claw/android/ui/settings/LocalModelInfo;", "", "id", "", "displayName", "description", "ggufFileName", "mmprojFileName", "hfRepo", "msRepo", "directGgufUrl", "directMmprojUrl", "modelSize", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", "getDescription", "()Ljava/lang/String;", "getDirectGgufUrl", "getDirectMmprojUrl", "getDisplayName", "getGgufFileName", "getHfRepo", "getId", "getMmprojFileName", "getModelSize", "getMsRepo", "buildDownloadUrls", "", "Lkotlin/Pair;", "component1", "component10", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "downloadedSize", "", "baseDir", "Ljava/io/File;", "equals", "", "other", "ggufPath", "hashCode", "", "isDownloaded", "mmprojPath", "modelDir", "toString", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final /* data */ class LocalModelInfo {
    private static final List<LocalModelInfo> AVAILABLE_MODELS;

    /* renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private static final LocalModelInfo DEFAULT_MODEL;
    private final String description;
    private final String directGgufUrl;
    private final String directMmprojUrl;
    private final String displayName;
    private final String ggufFileName;
    private final String hfRepo;
    private final String id;
    private final String mmprojFileName;
    private final String modelSize;
    private final String msRepo;

    /* renamed from: component1, reason: from getter */
    public final String getId() {
        return this.id;
    }

    /* renamed from: component10, reason: from getter */
    public final String getModelSize() {
        return this.modelSize;
    }

    /* renamed from: component2, reason: from getter */
    public final String getDisplayName() {
        return this.displayName;
    }

    /* renamed from: component3, reason: from getter */
    public final String getDescription() {
        return this.description;
    }

    /* renamed from: component4, reason: from getter */
    public final String getGgufFileName() {
        return this.ggufFileName;
    }

    /* renamed from: component5, reason: from getter */
    public final String getMmprojFileName() {
        return this.mmprojFileName;
    }

    /* renamed from: component6, reason: from getter */
    public final String getHfRepo() {
        return this.hfRepo;
    }

    /* renamed from: component7, reason: from getter */
    public final String getMsRepo() {
        return this.msRepo;
    }

    /* renamed from: component8, reason: from getter */
    public final String getDirectGgufUrl() {
        return this.directGgufUrl;
    }

    /* renamed from: component9, reason: from getter */
    public final String getDirectMmprojUrl() {
        return this.directMmprojUrl;
    }

    public final LocalModelInfo copy(String id, String displayName, String description, String ggufFileName, String mmprojFileName, String hfRepo, String msRepo, String directGgufUrl, String directMmprojUrl, String modelSize) {
        Intrinsics.checkNotNullParameter(id, "id");
        Intrinsics.checkNotNullParameter(displayName, "displayName");
        Intrinsics.checkNotNullParameter(description, "description");
        Intrinsics.checkNotNullParameter(ggufFileName, "ggufFileName");
        Intrinsics.checkNotNullParameter(modelSize, "modelSize");
        return new LocalModelInfo(id, displayName, description, ggufFileName, mmprojFileName, hfRepo, msRepo, directGgufUrl, directMmprojUrl, modelSize);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocalModelInfo)) {
            return false;
        }
        LocalModelInfo localModelInfo = (LocalModelInfo) other;
        return Intrinsics.areEqual(this.id, localModelInfo.id) && Intrinsics.areEqual(this.displayName, localModelInfo.displayName) && Intrinsics.areEqual(this.description, localModelInfo.description) && Intrinsics.areEqual(this.ggufFileName, localModelInfo.ggufFileName) && Intrinsics.areEqual(this.mmprojFileName, localModelInfo.mmprojFileName) && Intrinsics.areEqual(this.hfRepo, localModelInfo.hfRepo) && Intrinsics.areEqual(this.msRepo, localModelInfo.msRepo) && Intrinsics.areEqual(this.directGgufUrl, localModelInfo.directGgufUrl) && Intrinsics.areEqual(this.directMmprojUrl, localModelInfo.directMmprojUrl) && Intrinsics.areEqual(this.modelSize, localModelInfo.modelSize);
    }

    public int hashCode() {
        int hashCode = ((((((this.id.hashCode() * 31) + this.displayName.hashCode()) * 31) + this.description.hashCode()) * 31) + this.ggufFileName.hashCode()) * 31;
        String str = this.mmprojFileName;
        int hashCode2 = (hashCode + (str == null ? 0 : str.hashCode())) * 31;
        String str2 = this.hfRepo;
        int hashCode3 = (hashCode2 + (str2 == null ? 0 : str2.hashCode())) * 31;
        String str3 = this.msRepo;
        int hashCode4 = (hashCode3 + (str3 == null ? 0 : str3.hashCode())) * 31;
        String str4 = this.directGgufUrl;
        int hashCode5 = (hashCode4 + (str4 == null ? 0 : str4.hashCode())) * 31;
        String str5 = this.directMmprojUrl;
        return ((hashCode5 + (str5 != null ? str5.hashCode() : 0)) * 31) + this.modelSize.hashCode();
    }

    public String toString() {
        return "LocalModelInfo(id=" + this.id + ", displayName=" + this.displayName + ", description=" + this.description + ", ggufFileName=" + this.ggufFileName + ", mmprojFileName=" + this.mmprojFileName + ", hfRepo=" + this.hfRepo + ", msRepo=" + this.msRepo + ", directGgufUrl=" + this.directGgufUrl + ", directMmprojUrl=" + this.directMmprojUrl + ", modelSize=" + this.modelSize + ")";
    }

    public LocalModelInfo(String id, String displayName, String description, String ggufFileName, String str, String str2, String str3, String str4, String str5, String modelSize) {
        Intrinsics.checkNotNullParameter(id, "id");
        Intrinsics.checkNotNullParameter(displayName, "displayName");
        Intrinsics.checkNotNullParameter(description, "description");
        Intrinsics.checkNotNullParameter(ggufFileName, "ggufFileName");
        Intrinsics.checkNotNullParameter(modelSize, "modelSize");
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.ggufFileName = ggufFileName;
        this.mmprojFileName = str;
        this.hfRepo = str2;
        this.msRepo = str3;
        this.directGgufUrl = str4;
        this.directMmprojUrl = str5;
        this.modelSize = modelSize;
    }

    public final String getId() {
        return this.id;
    }

    public final String getDisplayName() {
        return this.displayName;
    }

    public final String getDescription() {
        return this.description;
    }

    public final String getGgufFileName() {
        return this.ggufFileName;
    }

    public final String getMmprojFileName() {
        return this.mmprojFileName;
    }

    public final String getHfRepo() {
        return this.hfRepo;
    }

    public final String getMsRepo() {
        return this.msRepo;
    }

    public final String getDirectGgufUrl() {
        return this.directGgufUrl;
    }

    public final String getDirectMmprojUrl() {
        return this.directMmprojUrl;
    }

    public /* synthetic */ LocalModelInfo(String str, String str2, String str3, String str4, String str5, String str6, String str7, String str8, String str9, String str10, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(str, str2, str3, str4, (i & 16) != 0 ? null : str5, (i & 32) != 0 ? null : str6, (i & 64) != 0 ? null : str7, (i & 128) != 0 ? null : str8, (i & 256) != 0 ? null : str9, (i & 512) != 0 ? "" : str10);
    }

    public final String getModelSize() {
        return this.modelSize;
    }

    /* compiled from: LocalModelInfo.kt */
    @Metadata(d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002R\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\b\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n¨\u0006\u000b"}, d2 = {"Lcom/apk/claw/android/ui/settings/LocalModelInfo$Companion;", "", "()V", "AVAILABLE_MODELS", "", "Lcom/apk/claw/android/ui/settings/LocalModelInfo;", "getAVAILABLE_MODELS", "()Ljava/util/List;", "DEFAULT_MODEL", "getDEFAULT_MODEL", "()Lcom/apk/claw/android/ui/settings/LocalModelInfo;", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final List<LocalModelInfo> getAVAILABLE_MODELS() {
            return LocalModelInfo.AVAILABLE_MODELS;
        }

        public final LocalModelInfo getDEFAULT_MODEL() {
            return LocalModelInfo.DEFAULT_MODEL;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    static {
        String str = null;
        String str2 = null;
        List<LocalModelInfo> listOf = CollectionsKt.listOf((Object[]) new LocalModelInfo[]{new LocalModelInfo("qwen2.5-1.5b-q4", "Qwen2.5-1.5B (Q4_K_M)", "通义千问轻量级模型，纯文本对话 (1.5B)", "qwen2.5-1.5b-instruct-q4_k_m.gguf", null, "Qwen/Qwen2.5-1.5B-Instruct-GGUF", "Qwen/Qwen2.5-1.5B-Instruct-GGUF", null, null, "~1.0 GB", HttpStatus.SC_BAD_REQUEST, null), new LocalModelInfo("qwen2.5-3b-q4", "Qwen2.5-3B (Q4_K_M)", "通义千问轻量级模型，纯文本对话 (3B)", "qwen2.5-3b-instruct-q4_k_m.gguf", null, "Qwen/Qwen2.5-3B-Instruct-GGUF", "Qwen/Qwen2.5-3B-Instruct-GGUF", str, null, "~1.9 GB", HttpStatus.SC_BAD_REQUEST, null), new LocalModelInfo("minicpm-v-4.6-q4", "MiniCPM-V-4.6 (Q4_K_M)", "面壁智能多模态模型，支持图文理解 (1.2B)", "MiniCPM-V-4_6-Q4_K_M.gguf", "mmproj-model-f16.gguf", "openbmb/MiniCPM-V-4.6-gguf", "OpenBMB/MiniCPM-V-4.6-gguf", str2, null, "~3.5 GB", 384, null), new LocalModelInfo("minicpm-v-4-q4", "MiniCPM-V-4 (Q4_K_M)", "面壁智能多模态模型，支持图文理解 (4.1B)", "ggml-model-Q4_K_M.gguf", "mmproj-model-f16.gguf", "openbmb/MiniCPM-V-4-gguf", "OpenBMB/MiniCPM-V-4-gguf", null, 0 == true ? 1 : 0, "~3.5 GB", 384, null), new LocalModelInfo("llama-3.2-1b-q4", "Llama-3.2-1B (Q4_K_M)", "Meta 轻量级模型，纯文本对话 (1B)", "llama-3.2-1b-instruct-q4_k_m.gguf", str, "hugging-quants/Llama-3.2-1B-Instruct-GGUF", "AI-ModelScope/Llama-3.2-1B-Instruct-GGUF", null, 0 == true ? 1 : 0, "~0.8 GB", HttpStatus.SC_BAD_REQUEST, null), new LocalModelInfo("llama-3.2-3b-q4", "Llama-3.2-3B (Q4_K_M)", "Meta 轻量级模型，纯文本对话 (3B)", "Llama-3.2-3B-Instruct-Q4_K_M.gguf", str2, "hugging-quants/Llama-3.2-3B-Instruct-GGUF", "AI-ModelScope/Llama-3.2-3B-Instruct-GGUF", null, 0 == true ? 1 : 0, "~1.9 GB", HttpStatus.SC_BAD_REQUEST, null)});
        AVAILABLE_MODELS = listOf;
        DEFAULT_MODEL = (LocalModelInfo) CollectionsKt.first((List) listOf);
    }

    public final File modelDir(File baseDir) {
        Intrinsics.checkNotNullParameter(baseDir, "baseDir");
        return new File(baseDir, "local_models/" + this.id);
    }

    public final File ggufPath(File baseDir) {
        Intrinsics.checkNotNullParameter(baseDir, "baseDir");
        return new File(modelDir(baseDir), this.ggufFileName);
    }

    public final File mmprojPath(File baseDir) {
        Intrinsics.checkNotNullParameter(baseDir, "baseDir");
        if (this.mmprojFileName != null) {
            return new File(modelDir(baseDir), this.mmprojFileName);
        }
        return null;
    }

    public final boolean isDownloaded(File baseDir) {
        Intrinsics.checkNotNullParameter(baseDir, "baseDir");
        return ggufPath(baseDir).exists();
    }

    public final long downloadedSize(File baseDir) {
        Intrinsics.checkNotNullParameter(baseDir, "baseDir");
        File ggufPath = ggufPath(baseDir);
        long length = ggufPath.exists() ? ggufPath.length() : 0L;
        File mmprojPath = mmprojPath(baseDir);
        return (mmprojPath == null || !mmprojPath.exists()) ? length : length + mmprojPath.length();
    }

    public final List<Pair<String, String>> buildDownloadUrls() {
        ArrayList arrayList = new ArrayList();
        String str = this.hfRepo;
        if (str != null) {
            arrayList.add(TuplesKt.to("HuggingFace", "https://huggingface.co/" + str + "/resolve/main/" + this.ggufFileName));
            String str2 = this.mmprojFileName;
            if (str2 != null) {
                arrayList.add(TuplesKt.to("HuggingFace-mmproj", "https://huggingface.co/" + str + "/resolve/main/" + str2));
            }
        }
        String str3 = this.msRepo;
        if (str3 != null) {
            arrayList.add(TuplesKt.to("ModelScope", "https://modelscope.cn/models/" + str3 + "/resolve/master/" + this.ggufFileName));
            String str4 = this.mmprojFileName;
            if (str4 != null) {
                arrayList.add(TuplesKt.to("ModelScope-mmproj", "https://modelscope.cn/models/" + str3 + "/resolve/master/" + str4));
            }
        }
        String str5 = this.directGgufUrl;
        if (str5 != null) {
            arrayList.add(TuplesKt.to("Direct", str5));
        }
        String str6 = this.directMmprojUrl;
        if (str6 != null) {
            arrayList.add(TuplesKt.to("Direct-mmproj", str6));
        }
        return arrayList;
    }
}
