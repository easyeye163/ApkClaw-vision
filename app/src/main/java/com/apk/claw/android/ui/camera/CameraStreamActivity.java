package com.apk.claw.android.ui.camera;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.floating.voice.VoiceStreamFloatWindow;
import com.apk.claw.android.service.monitor.StreamMonitorController;
import com.apk.claw.android.utils.XLog;
import com.apk.claw.android.vision.CameraFramePusher;
import com.apk.claw.android.vision.VisionFrameBuffer;
import java.util.ArrayList;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: CameraStreamActivity.kt */
@Metadata(d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0015\n\u0002\b\u0007\u0018\u0000 (2\u00020\u0001:\u0001(B\u0005¢\u0006\u0002\u0010\u0002J\b\u0010\u0014\u001a\u00020\u0015H\u0002J\b\u0010\u0016\u001a\u00020\u0015H\u0002J\u0012\u0010\u0017\u001a\u00020\u00152\b\u0010\u0018\u001a\u0004\u0018\u00010\u0019H\u0014J\b\u0010\u001a\u001a\u00020\u0015H\u0014J\b\u0010\u001b\u001a\u00020\u0015H\u0014J-\u0010\u001c\u001a\u00020\u00152\u0006\u0010\u001d\u001a\u00020\u000e2\u000e\u0010\u001e\u001a\n\u0012\u0006\b\u0001\u0012\u00020 0\u001f2\u0006\u0010!\u001a\u00020\"H\u0016¢\u0006\u0002\u0010#J\b\u0010$\u001a\u00020\u0015H\u0014J\b\u0010%\u001a\u00020\u0015H\u0002J\b\u0010&\u001a\u00020\u0015H\u0002J\b\u0010'\u001a\u00020\u0015H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082.¢\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0012X\u0082.¢\u0006\u0002\n\u0000¨\u0006)"}, d2 = {"Lcom/apk/claw/android/ui/camera/CameraStreamActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "btnCloseCamera", "Landroid/widget/ImageButton;", "btnSwitchCamera", "Landroid/widget/Button;", "btnToggleMonitor", "btnVoiceFloat", "cameraFramePusher", "Lcom/apk/claw/android/vision/CameraFramePusher;", "isMonitoring", "", "lensFacing", "", "previewView", "Landroidx/camera/view/PreviewView;", "tvMonitorStatus", "Landroid/widget/TextView;", "tvStatus", "bindButtons", "", "checkPermissionsAndStart", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "onPause", "onRequestPermissionsResult", "requestCode", "permissions", "", "", "grantResults", "", "(I[Ljava/lang/String;[I)V", "onResume", "startCameraStream", "startMonitoring", "stopMonitoring", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class CameraStreamActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_AUDIO = 1001;
    private static final String TAG = "CameraStreamActivity";
    private ImageButton btnCloseCamera;
    private Button btnSwitchCamera;
    private Button btnToggleMonitor;
    private Button btnVoiceFloat;
    private CameraFramePusher cameraFramePusher;
    private boolean isMonitoring;
    private int lensFacing = 1;
    private PreviewView previewView;
    private TextView tvMonitorStatus;
    private TextView tvStatus;

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(1024, 1024);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        setContentView(R.layout.activity_camera_stream);
        View findViewById = findViewById(R.id.preview_view);
        Intrinsics.checkNotNullExpressionValue(findViewById, "findViewById(...)");
        this.previewView = (PreviewView) findViewById;
        View findViewById2 = findViewById(R.id.tv_camera_status);
        Intrinsics.checkNotNullExpressionValue(findViewById2, "findViewById(...)");
        this.tvStatus = (TextView) findViewById2;
        View findViewById3 = findViewById(R.id.tv_monitor_status);
        Intrinsics.checkNotNullExpressionValue(findViewById3, "findViewById(...)");
        this.tvMonitorStatus = (TextView) findViewById3;
        View findViewById4 = findViewById(R.id.btn_switch_camera);
        Intrinsics.checkNotNullExpressionValue(findViewById4, "findViewById(...)");
        this.btnSwitchCamera = (Button) findViewById4;
        View findViewById5 = findViewById(R.id.btn_toggle_monitor);
        Intrinsics.checkNotNullExpressionValue(findViewById5, "findViewById(...)");
        this.btnToggleMonitor = (Button) findViewById5;
        View findViewById6 = findViewById(R.id.btn_voice_float);
        Intrinsics.checkNotNullExpressionValue(findViewById6, "findViewById(...)");
        this.btnVoiceFloat = (Button) findViewById6;
        View findViewById7 = findViewById(R.id.btn_close_camera);
        Intrinsics.checkNotNullExpressionValue(findViewById7, "findViewById(...)");
        this.btnCloseCamera = (ImageButton) findViewById7;
        checkPermissionsAndStart();
        bindButtons();
    }

    private final void checkPermissionsAndStart() {
        ArrayList arrayList = new ArrayList();
        CameraStreamActivity cameraStreamActivity = this;
        if (ContextCompat.checkSelfPermission(cameraStreamActivity, "android.permission.CAMERA") != 0) {
            arrayList.add("android.permission.CAMERA");
        }
        if (ContextCompat.checkSelfPermission(cameraStreamActivity, "android.permission.RECORD_AUDIO") != 0) {
            arrayList.add("android.permission.RECORD_AUDIO");
        }
        ArrayList arrayList2 = arrayList;
        if (!arrayList2.isEmpty()) {
            ActivityCompat.requestPermissions(this, (String[]) arrayList2.toArray(new String[0]), 1001);
        } else {
            startCameraStream();
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Intrinsics.checkNotNullParameter(permissions, "permissions");
        Intrinsics.checkNotNullParameter(grantResults, "grantResults");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            for (int i : grantResults) {
                if (i != 0) {
                    Toast.makeText(this, "需要摄像头和麦克风权限", 1).show();
                    finish();
                    return;
                }
            }
            startCameraStream();
        }
    }

    private final void startCameraStream() {
        TextView textView = null;
        try {
            VisionFrameBuffer.INSTANCE.start();
            CameraStreamActivity cameraStreamActivity = this;
            PreviewView previewView = this.previewView;
            if (previewView == null) {
                Intrinsics.throwUninitializedPropertyAccessException("previewView");
                previewView = null;
            }
            CameraFramePusher cameraFramePusher = new CameraFramePusher(cameraStreamActivity, previewView.getSurfaceProvider());
            cameraFramePusher.setLensFacing(this.lensFacing);
            cameraFramePusher.setFps(2);
            cameraFramePusher.setCallback(new CameraStreamActivity$startCameraStream$1$1(this));
            cameraFramePusher.start();
            this.cameraFramePusher = cameraFramePusher;
            String str = this.lensFacing == 0 ? "前置" : "后置";
            TextView textView2 = this.tvStatus;
            if (textView2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvStatus");
                textView2 = null;
            }
            textView2.setText(str.concat("摄像头已启动"));
            XLog.i(TAG, "Camera stream started with frame pusher (lensFacing=" + this.lensFacing + ")");
        } catch (Exception e) {
            XLog.e(TAG, "Failed to start camera", e);
            TextView textView3 = this.tvStatus;
            if (textView3 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("tvStatus");
            } else {
                textView = textView3;
            }
            textView.setText("摄像头启动失败: " + e.getMessage());
        }
    }

    private final void bindButtons() {
        ImageButton imageButton = this.btnCloseCamera;
        Button button = null;
        if (imageButton == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnCloseCamera");
            imageButton = null;
        }
        imageButton.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.camera.CameraStreamActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CameraStreamActivity.bindButtons$lambda$2(CameraStreamActivity.this, view);
            }
        });
        Button button2 = this.btnSwitchCamera;
        if (button2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnSwitchCamera");
            button2 = null;
        }
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.camera.CameraStreamActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CameraStreamActivity.bindButtons$lambda$3(CameraStreamActivity.this, view);
            }
        });
        Button button3 = this.btnToggleMonitor;
        if (button3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnToggleMonitor");
            button3 = null;
        }
        button3.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.camera.CameraStreamActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CameraStreamActivity.bindButtons$lambda$4(CameraStreamActivity.this, view);
            }
        });
        Button button4 = this.btnVoiceFloat;
        if (button4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnVoiceFloat");
        } else {
            button = button4;
        }
        button.setOnClickListener(new View.OnClickListener() { // from class: com.apk.claw.android.ui.camera.CameraStreamActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CameraStreamActivity.bindButtons$lambda$5(CameraStreamActivity.this, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void bindButtons$lambda$2(CameraStreamActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void bindButtons$lambda$3(CameraStreamActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        CameraFramePusher cameraFramePusher = this$0.cameraFramePusher;
        if (cameraFramePusher != null) {
            cameraFramePusher.switchCamera();
        }
        CameraFramePusher cameraFramePusher2 = this$0.cameraFramePusher;
        int lensFacing = cameraFramePusher2 != null ? cameraFramePusher2.getLensFacing() : this$0.lensFacing;
        this$0.lensFacing = lensFacing;
        String str = lensFacing == 0 ? "前置" : "后置";
        Toast.makeText(this$0, "切换到" + str + "摄像头", 0).show();
        TextView textView = this$0.tvStatus;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvStatus");
            textView = null;
        }
        textView.setText(str.concat("摄像头已启动"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void bindButtons$lambda$4(CameraStreamActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (this$0.isMonitoring) {
            this$0.stopMonitoring();
        } else {
            this$0.startMonitoring();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void bindButtons$lambda$5(CameraStreamActivity this$0, View view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Button button = null;
        if (VoiceStreamFloatWindow.INSTANCE.isShowing()) {
            VoiceStreamFloatWindow.INSTANCE.dismiss();
            Button button2 = this$0.btnVoiceFloat;
            if (button2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("btnVoiceFloat");
            } else {
                button = button2;
            }
            button.setText("语音助手");
            return;
        }
        VoiceStreamFloatWindow voiceStreamFloatWindow = VoiceStreamFloatWindow.INSTANCE;
        Application application = this$0.getApplication();
        Intrinsics.checkNotNull(application, "null cannot be cast to non-null type com.apk.claw.android.ClawApplication");
        voiceStreamFloatWindow.show((ClawApplication) application);
        Button button3 = this$0.btnVoiceFloat;
        if (button3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnVoiceFloat");
        } else {
            button = button3;
        }
        button.setText("隐藏语音");
    }

    private final void startMonitoring() {
        if (StreamMonitorController.INSTANCE.isRunning()) {
            return;
        }
        StreamMonitorController streamMonitorController = StreamMonitorController.INSTANCE;
        Application application = getApplication();
        Intrinsics.checkNotNull(application, "null cannot be cast to non-null type com.apk.claw.android.ClawApplication");
        streamMonitorController.start((ClawApplication) application);
        this.isMonitoring = true;
        Button button = this.btnToggleMonitor;
        TextView textView = null;
        if (button == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnToggleMonitor");
            button = null;
        }
        button.setText("停止监控");
        TextView textView2 = this.tvMonitorStatus;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvMonitorStatus");
            textView2 = null;
        }
        textView2.setText("监控运行中...");
        TextView textView3 = this.tvMonitorStatus;
        if (textView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvMonitorStatus");
        } else {
            textView = textView3;
        }
        textView.setVisibility(0);
    }

    private final void stopMonitoring() {
        StreamMonitorController.INSTANCE.stop();
        this.isMonitoring = false;
        Button button = this.btnToggleMonitor;
        TextView textView = null;
        if (button == null) {
            Intrinsics.throwUninitializedPropertyAccessException("btnToggleMonitor");
            button = null;
        }
        button.setText("开始监控");
        TextView textView2 = this.tvMonitorStatus;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("tvMonitorStatus");
        } else {
            textView = textView2;
        }
        textView.setText("监控已停止");
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        super.onPause();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        if (this.isMonitoring) {
            StreamMonitorController.INSTANCE.stop();
        }
        VoiceStreamFloatWindow.INSTANCE.dismiss();
        CameraFramePusher cameraFramePusher = this.cameraFramePusher;
        if (cameraFramePusher != null) {
            cameraFramePusher.stop();
        }
        this.cameraFramePusher = null;
        VisionFrameBuffer.INSTANCE.stop();
    }
}
