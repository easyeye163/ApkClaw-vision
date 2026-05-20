package com.apk.claw.android.vision;

import android.util.Log;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: VisionFrameBuffer.kt */
@Metadata(d1 = {"\u0000H\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010 \n\u0002\b\u0006\bÆ\u0002\u0018\u00002\u00020\u0001:\u0001!B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0006\u0010\u0012\u001a\u00020\u0013J\u0010\u0010\u0014\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0015\u001a\u00020\u000eJ\b\u0010\u0016\u001a\u0004\u0018\u00010\tJ\u0010\u0010\u0017\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0015\u001a\u00020\u000eJ\u000e\u0010\u0018\u001a\u00020\u00132\u0006\u0010\u0019\u001a\u00020\u001aJ\u0014\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\t0\u001c2\u0006\u0010\u001d\u001a\u00020\u0006J\u0006\u0010\u001e\u001a\u00020\u0004J\u0006\u0010\u001f\u001a\u00020\u0013J\u0006\u0010 \u001a\u00020\u0013R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T¢\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004¢\u0006\u0002\n\u0000R\u001e\u0010\f\u001a\u00020\u000b2\u0006\u0010\n\u001a\u00020\u000b@BX\u0086\u000e¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u001e\u0010\u000f\u001a\u00020\u000e2\u0006\u0010\n\u001a\u00020\u000e@BX\u0086\u000e¢\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011¨\u0006\""}, d2 = {"Lcom/apk/claw/android/vision/VisionFrameBuffer;", "", "()V", "MAX_FRAMES", "", "TAG", "", "buffer", "Ljava/util/concurrent/ConcurrentLinkedDeque;", "Lcom/apk/claw/android/vision/VisionFrameBuffer$FrameEntry;", "<set-?>", "", "isRunning", "()Z", "", "totalPushedCount", "getTotalPushedCount", "()J", "clear", "", "getFrameClosestTo", "targetTsMs", "getLatestFrame", "getLatestFrameAtOrBefore", "offer", "jpegBytes", "", "selectFramesForQuery", "", "userText", "size", "start", "stop", "FrameEntry", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class VisionFrameBuffer {
    private static final int MAX_FRAMES = 30;
    private static final String TAG = "VisionFrameBuffer";
    private static volatile boolean isRunning;
    private static volatile long totalPushedCount;
    public static final VisionFrameBuffer INSTANCE = new VisionFrameBuffer();
    private static final ConcurrentLinkedDeque<FrameEntry> buffer = new ConcurrentLinkedDeque<>();

    private VisionFrameBuffer() {
    }

    /* compiled from: VisionFrameBuffer.kt */
    @Metadata(d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0012\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005¢\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003HÆ\u0003J\t\u0010\f\u001a\u00020\u0005HÆ\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005HÆ\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u0096\u0002J\b\u0010\u0011\u001a\u00020\u0012H\u0016J\t\u0010\u0013\u001a\u00020\u0014HÖ\u0001R\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n¨\u0006\u0015"}, d2 = {"Lcom/apk/claw/android/vision/VisionFrameBuffer$FrameEntry;", "", "timestampMs", "", "jpegBytes", "", "(J[B)V", "getJpegBytes", "()[B", "getTimestampMs", "()J", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final /* data */ class FrameEntry {
        private final byte[] jpegBytes;
        private final long timestampMs;

        public static /* synthetic */ FrameEntry copy$default(FrameEntry frameEntry, long j, byte[] bArr, int i, Object obj) {
            if ((i & 1) != 0) {
                j = frameEntry.timestampMs;
            }
            if ((i & 2) != 0) {
                bArr = frameEntry.jpegBytes;
            }
            return frameEntry.copy(j, bArr);
        }

        /* renamed from: component1, reason: from getter */
        public final long getTimestampMs() {
            return this.timestampMs;
        }

        /* renamed from: component2, reason: from getter */
        public final byte[] getJpegBytes() {
            return this.jpegBytes;
        }

        public final FrameEntry copy(long timestampMs, byte[] jpegBytes) {
            Intrinsics.checkNotNullParameter(jpegBytes, "jpegBytes");
            return new FrameEntry(timestampMs, jpegBytes);
        }

        public String toString() {
            return "FrameEntry(timestampMs=" + this.timestampMs + ", jpegBytes=" + Arrays.toString(this.jpegBytes) + ")";
        }

        public FrameEntry(long j, byte[] jpegBytes) {
            Intrinsics.checkNotNullParameter(jpegBytes, "jpegBytes");
            this.timestampMs = j;
            this.jpegBytes = jpegBytes;
        }

        public final long getTimestampMs() {
            return this.timestampMs;
        }

        public final byte[] getJpegBytes() {
            return this.jpegBytes;
        }

        public boolean equals(Object other) {
            return (other instanceof FrameEntry) && this.timestampMs == ((FrameEntry) other).timestampMs;
        }

        public int hashCode() {
            return Long.hashCode(this.timestampMs);
        }
    }

    public final boolean isRunning() {
        return isRunning;
    }

    public final long getTotalPushedCount() {
        return totalPushedCount;
    }

    public final synchronized void offer(byte[] jpegBytes) {
        Intrinsics.checkNotNullParameter(jpegBytes, "jpegBytes");
        buffer.addLast(new FrameEntry(System.currentTimeMillis(), jpegBytes));
        while (true) {
            ConcurrentLinkedDeque<FrameEntry> concurrentLinkedDeque = buffer;
            if (concurrentLinkedDeque.size() > 30) {
                concurrentLinkedDeque.pollFirst();
            } else {
                totalPushedCount++;
            }
        }
    }

    public final FrameEntry getLatestFrame() {
        return buffer.peekLast();
    }

    public final FrameEntry getFrameClosestTo(long targetTsMs) {
        ConcurrentLinkedDeque<FrameEntry> concurrentLinkedDeque = buffer;
        FrameEntry frameEntry = null;
        if (concurrentLinkedDeque.isEmpty()) {
            return null;
        }
        Iterator<FrameEntry> it = concurrentLinkedDeque.iterator();
        long j = Long.MAX_VALUE;
        while (it.hasNext()) {
            FrameEntry next = it.next();
            long abs = Math.abs(next.getTimestampMs() - targetTsMs);
            if (abs < j) {
                frameEntry = next;
                j = abs;
            }
        }
        return frameEntry;
    }

    public final FrameEntry getLatestFrameAtOrBefore(long targetTsMs) {
        Iterator<FrameEntry> it = buffer.iterator();
        FrameEntry frameEntry = null;
        while (it.hasNext()) {
            FrameEntry next = it.next();
            if (next.getTimestampMs() <= targetTsMs) {
                frameEntry = next;
            }
        }
        return frameEntry;
    }

    public final List<FrameEntry> selectFramesForQuery(String userText) {
        Intrinsics.checkNotNullParameter(userText, "userText");
        if (buffer.isEmpty()) {
            return CollectionsKt.emptyList();
        }
        List listOf = CollectionsKt.listOf((Object[]) new String[]{"刚才", "过程", "怎么做的", "动态", "变化", "刚才的"});
        if (!(listOf instanceof Collection) || !listOf.isEmpty()) {
            Iterator it = listOf.iterator();
            while (it.hasNext()) {
                if (StringsKt.contains$default((CharSequence) userText, (CharSequence) it.next(), false, 2, (Object) null)) {
                    return CollectionsKt.takeLast(CollectionsKt.toList(buffer), 10);
                }
            }
        }
        return CollectionsKt.listOfNotNull(buffer.peekLast());
    }

    public final synchronized void clear() {
        buffer.clear();
        totalPushedCount = 0L;
    }

    public final int size() {
        return buffer.size();
    }

    public final void start() {
        isRunning = true;
        Log.i(TAG, "VisionFrameBuffer started");
    }

    public final void stop() {
        isRunning = false;
        clear();
        Log.i(TAG, "VisionFrameBuffer stopped");
    }
}
