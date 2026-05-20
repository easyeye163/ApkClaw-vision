package com.apk.claw.android.local.llm;

import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.io.FilesKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Regex;
import kotlin.text.StringsKt;

/* compiled from: CpuFeatures.kt */
@Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\"\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u000e\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\b\u0010\u0017\u001a\u0004\u0018\u00010\u0004J\u000e\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00040\u0006H\u0002J\u0006\u0010\u0019\u001a\u00020\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R!\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00040\u00068BX\u0082\u0084\u0002¢\u0006\f\n\u0004\b\t\u0010\n\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u000b\u001a\u00020\f8F¢\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u000f\u001a\u00020\f8F¢\u0006\u0006\u001a\u0004\b\u0010\u0010\u000eR\u0011\u0010\u0011\u001a\u00020\f8F¢\u0006\u0006\u001a\u0004\b\u0012\u0010\u000eR\u0011\u0010\u0013\u001a\u00020\f8F¢\u0006\u0006\u001a\u0004\b\u0014\u0010\u000eR\u0011\u0010\u0015\u001a\u00020\f8F¢\u0006\u0006\u001a\u0004\b\u0016\u0010\u000e¨\u0006\u001a"}, d2 = {"Lcom/apk/claw/android/local/llm/CpuFeatures;", "", "()V", "TAG", "", "features", "", "getFeatures", "()Ljava/util/Set;", "features$delegate", "Lkotlin/Lazy;", "hasBf16", "", "getHasBf16", "()Z", "hasDotprod", "getHasDotprod", "hasFp16", "getHasFp16", "hasI8mm", "getHasI8mm", "hasSve2", "getHasSve2", "bestGgmlCpuVariant", "readFeatures", "summary", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class CpuFeatures {
    private static final String TAG = "CpuFeatures";
    public static final CpuFeatures INSTANCE = new CpuFeatures();

    /* renamed from: features$delegate, reason: from kotlin metadata */
    private static final Lazy features = LazyKt.lazy(new Function0<Set<? extends String>>() { // from class: com.apk.claw.android.local.llm.CpuFeatures$features$2
        @Override // kotlin.jvm.functions.Function0
        public final Set<? extends String> invoke() {
            Set<? extends String> readFeatures;
            readFeatures = CpuFeatures.INSTANCE.readFeatures();
            return readFeatures;
        }
    });

    private CpuFeatures() {
    }

    private final Set<String> getFeatures() {
        return (Set) features.getValue();
    }

    public final boolean getHasI8mm() {
        return getFeatures().contains("i8mm");
    }

    public final boolean getHasBf16() {
        return getFeatures().contains("bf16");
    }

    public final boolean getHasSve2() {
        return getFeatures().contains("sve2");
    }

    public final boolean getHasDotprod() {
        return getFeatures().contains("asimddp");
    }

    public final boolean getHasFp16() {
        return getFeatures().contains("fphp");
    }

    public final String bestGgmlCpuVariant() {
        if (getHasI8mm() && getHasBf16()) {
            return "v86";
        }
        return null;
    }

    public final String summary() {
        StringBuilder sb = new StringBuilder();
        CpuFeatures cpuFeatures = INSTANCE;
        sb.append("CPU features: " + CollectionsKt.joinToString$default(cpuFeatures.getFeatures(), null, null, null, 0, null, null, 63, null) + "\n");
        sb.append("dotprod=" + cpuFeatures.getHasDotprod() + " fp16=" + cpuFeatures.getHasFp16() + " i8mm=" + cpuFeatures.getHasI8mm() + " bf16=" + cpuFeatures.getHasBf16() + " sve2=" + cpuFeatures.getHasSve2() + "\n");
        String bestGgmlCpuVariant = cpuFeatures.bestGgmlCpuVariant();
        if (bestGgmlCpuVariant == null) {
            bestGgmlCpuVariant = "baseline";
        }
        sb.append("Selected ggml-cpu variant: " + bestGgmlCpuVariant);
        String sb2 = sb.toString();
        Intrinsics.checkNotNullExpressionValue(sb2, "toString(...)");
        return sb2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final Set<String> readFeatures() {
        String substringAfter$default;
        String obj;
        Set<String> set;
        try {
            List readLines$default = FilesKt.readLines$default(new File("/proc/cpuinfo"), null, 1, null);
            ArrayList arrayList = new ArrayList();
            for (Object obj2 : readLines$default) {
                if (StringsKt.startsWith$default((String) obj2, "Features", false, 2, (Object) null)) {
                    arrayList.add(obj2);
                }
            }
            String str = (String) CollectionsKt.firstOrNull((List) arrayList);
            if (str != null && (substringAfter$default = StringsKt.substringAfter$default(str, ":", (String) null, 2, (Object) null)) != null && (obj = StringsKt.trim((CharSequence) substringAfter$default).toString()) != null) {
                List<String> split = new Regex("\\s+").split(obj, 0);
                if (split != null && (set = CollectionsKt.toSet(split)) != null) {
                    return set;
                }
            }
            Set<String> emptySet = SetsKt.emptySet();
            Log.w(TAG, "No Features line in /proc/cpuinfo");
            return emptySet;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read /proc/cpuinfo", e);
            return SetsKt.emptySet();
        }
    }
}
