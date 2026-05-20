package com.apk.claw.android.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: CameraFramePusher.kt */
@Metadata(d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u0000 /2\u00020\u0001:\u0002./B\u0019\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005¢\u0006\u0002\u0010\u0006J\b\u0010&\u001a\u00020'H\u0003J\u0010\u0010(\u001a\u00020'2\u0006\u0010)\u001a\u00020*H\u0002J\b\u0010+\u001a\u00020'H\u0007J\u0006\u0010,\u001a\u00020'J\u0006\u0010-\u001a\u00020'R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004¢\u0006\u0002\n\u0000R\u001c\u0010\t\u001a\u0004\u0018\u00010\nX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e¢\u0006\u0002\n\u0000R\u001a\u0010\u0011\u001a\u00020\u0012X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u0018X\u0082\u0004¢\u0006\u0002\n\u0000R\u001a\u0010\u001a\u001a\u00020\u0012X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u0014\"\u0004\b\u001c\u0010\u0016R\u000e\u0010\u001d\u001a\u00020\u001eX\u0082\u000e¢\u0006\u0002\n\u0000R\u001a\u0010\u001f\u001a\u00020\u0012X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b \u0010\u0014\"\u0004\b!\u0010\u0016R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\u0004\u001a\u0004\u0018\u00010\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010\"\u001a\u00020#8F¢\u0006\u0006\u001a\u0004\b$\u0010%¨\u00060"}, d2 = {"Lcom/apk/claw/android/vision/CameraFramePusher;", "", "lifecycleOwner", "Landroidx/lifecycle/LifecycleOwner;", "previewSurfaceProvider", "Landroidx/camera/core/Preview$SurfaceProvider;", "(Landroidx/lifecycle/LifecycleOwner;Landroidx/camera/core/Preview$SurfaceProvider;)V", "analysisExecutor", "Ljava/util/concurrent/ExecutorService;", "callback", "Lcom/apk/claw/android/vision/CameraFramePusher$Callback;", "getCallback", "()Lcom/apk/claw/android/vision/CameraFramePusher$Callback;", "setCallback", "(Lcom/apk/claw/android/vision/CameraFramePusher$Callback;)V", "cameraProvider", "Landroidx/camera/lifecycle/ProcessCameraProvider;", "fps", "", "getFps", "()I", "setFps", "(I)V", "globalRunning", "Ljava/util/concurrent/atomic/AtomicBoolean;", "isRunning", "jpegQuality", "getJpegQuality", "setJpegQuality", "lastFrameTimeMs", "", "lensFacing", "getLensFacing", "setLensFacing", "running", "", "getRunning", "()Z", "bindCamera", "", "processFrame", "imageProxy", "Landroidx/camera/core/ImageProxy;", "start", "stop", "switchCamera", "Callback", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class CameraFramePusher {
    private static final int DEFAULT_FPS = 1;
    private static final int DEFAULT_JPEG_QUALITY = 80;
    private static final int MAX_FRAME_WIDTH = 720;
    private static final String TAG = "CameraFramePusher";
    private final ExecutorService analysisExecutor;
    private Callback callback;
    private ProcessCameraProvider cameraProvider;
    private int fps;
    private final AtomicBoolean globalRunning;
    private final AtomicBoolean isRunning;
    private int jpegQuality;
    private long lastFrameTimeMs;
    private int lensFacing;
    private final LifecycleOwner lifecycleOwner;
    private final Preview.SurfaceProvider previewSurfaceProvider;

    /* compiled from: CameraFramePusher.kt */
    @Metadata(d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&¨\u0006\u0006"}, d2 = {"Lcom/apk/claw/android/vision/CameraFramePusher$Callback;", "", "onCameraError", "", "message", "", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public interface Callback {
        void onCameraError(String message);
    }

    public CameraFramePusher(LifecycleOwner lifecycleOwner, Preview.SurfaceProvider surfaceProvider) {
        Intrinsics.checkNotNullParameter(lifecycleOwner, "lifecycleOwner");
        this.lifecycleOwner = lifecycleOwner;
        this.previewSurfaceProvider = surfaceProvider;
        this.fps = 1;
        this.jpegQuality = DEFAULT_JPEG_QUALITY;
        this.lensFacing = 1;
        this.isRunning = new AtomicBoolean(false);
        this.globalRunning = new AtomicBoolean(false);
        ExecutorService newSingleThreadExecutor = Executors.newSingleThreadExecutor();
        Intrinsics.checkNotNullExpressionValue(newSingleThreadExecutor, "newSingleThreadExecutor(...)");
        this.analysisExecutor = newSingleThreadExecutor;
    }

    public /* synthetic */ CameraFramePusher(LifecycleOwner lifecycleOwner, Preview.SurfaceProvider surfaceProvider, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(lifecycleOwner, (i & 2) != 0 ? null : surfaceProvider);
    }

    public final Callback getCallback() {
        return this.callback;
    }

    public final void setCallback(Callback callback) {
        this.callback = callback;
    }

    public final int getFps() {
        return this.fps;
    }

    public final void setFps(int i) {
        this.fps = i;
    }

    public final int getJpegQuality() {
        return this.jpegQuality;
    }

    public final void setJpegQuality(int i) {
        this.jpegQuality = i;
    }

    public final int getLensFacing() {
        return this.lensFacing;
    }

    public final void setLensFacing(int i) {
        this.lensFacing = i;
    }

    public final boolean getRunning() {
        return this.isRunning.get();
    }

    public final void start() {
        if (this.isRunning.get()) {
            Log.w(TAG, "Already running");
            return;
        }
        Object obj = this.lifecycleOwner;
        Intrinsics.checkNotNull(obj, "null cannot be cast to non-null type android.content.Context");
        final ListenableFuture<ProcessCameraProvider> processCameraProvider = ProcessCameraProvider.getInstance((Context) obj);
        Intrinsics.checkNotNullExpressionValue(processCameraProvider, "getInstance(...)");
        Runnable runnable = new Runnable() { // from class: com.apk.claw.android.vision.CameraFramePusher$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                CameraFramePusher.start$lambda$0(CameraFramePusher.this, processCameraProvider);
            }
        };
        Object obj2 = this.lifecycleOwner;
        Intrinsics.checkNotNull(obj2, "null cannot be cast to non-null type android.content.Context");
        processCameraProvider.addListener(runnable, ContextCompat.getMainExecutor((Context) obj2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Multi-variable type inference failed */
    public static final void start$lambda$0(CameraFramePusher this$0, ListenableFuture future) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(future, "$future");
        try {
            this$0.cameraProvider = (ProcessCameraProvider) future.get();
            this$0.bindCamera();
            this$0.globalRunning.set(true);
            Log.i(TAG, "CameraFramePusher started (facing=" + (this$0.lensFacing == 1 ? "back" : "front") + ", fps=" + this$0.fps + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera", e);
            Callback callback = this$0.callback;
            if (callback != null) {
                callback.onCameraError("摄像头启动失败: " + e.getMessage());
            }
        }
    }

    private final void bindCamera() {
        ProcessCameraProvider processCameraProvider = this.cameraProvider;
        if (processCameraProvider == null) {
            return;
        }
        processCameraProvider.unbindAll();
        CameraSelector build = new CameraSelector.Builder().requireLensFacing(this.lensFacing).build();
        Intrinsics.checkNotNullExpressionValue(build, "build(...)");
        if (this.previewSurfaceProvider != null) {
            Preview build2 = new Preview.Builder().build();
            Intrinsics.checkNotNullExpressionValue(build2, "build(...)");
            build2.setSurfaceProvider(this.previewSurfaceProvider);
            processCameraProvider.bindToLifecycle(this.lifecycleOwner, build, build2);
        }
        ImageAnalysis build3 = new ImageAnalysis.Builder().setBackpressureStrategy(0).setOutputImageFormat(1).build();
        Intrinsics.checkNotNullExpressionValue(build3, "build(...)");
        build3.setAnalyzer(this.analysisExecutor, new ImageAnalysis.Analyzer() { // from class: com.apk.claw.android.vision.CameraFramePusher$$ExternalSyntheticLambda1
            @Override // androidx.camera.core.ImageAnalysis.Analyzer
            public final void analyze(ImageProxy imageProxy) {
                CameraFramePusher.bindCamera$lambda$1(CameraFramePusher.this, imageProxy);
            }
        });
        processCameraProvider.bindToLifecycle(this.lifecycleOwner, build, build3);
        this.isRunning.set(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void bindCamera$lambda$1(CameraFramePusher this$0, ImageProxy imageProxy) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(imageProxy, "imageProxy");
        try {
            try {
                long currentTimeMillis = System.currentTimeMillis();
                if (currentTimeMillis - this$0.lastFrameTimeMs >= 1000 / this$0.fps) {
                    this$0.lastFrameTimeMs = currentTimeMillis;
                    this$0.processFrame(imageProxy);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing frame", e);
            }
        } finally {
            imageProxy.close();
        }
    }

    private final void processFrame(ImageProxy imageProxy) {
        Bitmap decodeByteArray;
        Image image = imageProxy.getImage();
        if (image == null) {
            return;
        }
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            ByteBuffer buffer2 = image.getPlanes()[1].getBuffer();
            ByteBuffer buffer3 = image.getPlanes()[2].getBuffer();
            int remaining = buffer.remaining();
            int remaining2 = buffer2.remaining();
            int remaining3 = buffer3.remaining();
            byte[] bArr = new byte[remaining + remaining2 + remaining3];
            buffer.get(bArr, 0, remaining);
            buffer3.get(bArr, remaining, remaining3);
            buffer2.get(bArr, remaining + remaining3, remaining2);
            YuvImage yuvImage = new YuvImage(bArr, 17, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), this.jpegQuality, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            if (image.getWidth() > 720 && (decodeByteArray = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length)) != null) {
                Bitmap createScaledBitmap = Bitmap.createScaledBitmap(decodeByteArray, 720, (decodeByteArray.getHeight() * 720) / decodeByteArray.getWidth(), true);
                Intrinsics.checkNotNullExpressionValue(createScaledBitmap, "createScaledBitmap(...)");
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                Bitmap createBitmap = Bitmap.createBitmap(createScaledBitmap, 0, 0, createScaledBitmap.getWidth(), createScaledBitmap.getHeight(), matrix, true);
                Intrinsics.checkNotNullExpressionValue(createBitmap, "createBitmap(...)");
                ByteArrayOutputStream byteArrayOutputStream2 = new ByteArrayOutputStream();
                createBitmap.compress(Bitmap.CompressFormat.JPEG, this.jpegQuality, byteArrayOutputStream2);
                byte[] byteArray2 = byteArrayOutputStream2.toByteArray();
                createScaledBitmap.recycle();
                createBitmap.recycle();
                decodeByteArray.recycle();
                byteArray = byteArray2;
            }
            VisionFrameBuffer visionFrameBuffer = VisionFrameBuffer.INSTANCE;
            Intrinsics.checkNotNull(byteArray);
            visionFrameBuffer.offer(byteArray);
        } catch (Exception e) {
            Log.e(TAG, "processFrame error", e);
        }
    }

    public final void stop() {
        this.isRunning.set(false);
        this.globalRunning.set(false);
        this.analysisExecutor.shutdown();
        try {
            ProcessCameraProvider processCameraProvider = this.cameraProvider;
            if (processCameraProvider != null) {
                processCameraProvider.unbindAll();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error unbinding camera", e);
        }
        this.cameraProvider = null;
        Log.i(TAG, "CameraFramePusher stopped");
    }

    public final void switchCamera() {
        if (this.isRunning.get()) {
            this.lensFacing = this.lensFacing == 1 ? 0 : 1;
            ProcessCameraProvider processCameraProvider = this.cameraProvider;
            if (processCameraProvider != null) {
                try {
                    processCameraProvider.unbindAll();
                    bindCamera();
                    Unit unit = Unit.INSTANCE;
                } catch (Exception e) {
                    Integer.valueOf(Log.e(TAG, "Error switching camera", e));
                }
            }
        }
    }
}
