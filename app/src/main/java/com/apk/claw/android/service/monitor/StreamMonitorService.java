package com.apk.claw.android.service.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.apk.claw.android.R;
import com.apk.claw.android.ui.home.HomeActivity;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: StreamMonitorService.kt */
@Metadata(d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0004\u0018\u0000 \u00132\u00020\u0001:\u0001\u0013B\u0005¢\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0002J\b\u0010\u0007\u001a\u00020\bH\u0002J\u0014\u0010\t\u001a\u0004\u0018\u00010\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u0016J\b\u0010\r\u001a\u00020\bH\u0016J\b\u0010\u000e\u001a\u00020\bH\u0016J\"\u0010\u000f\u001a\u00020\u00102\b\u0010\u000b\u001a\u0004\u0018\u00010\f2\u0006\u0010\u0011\u001a\u00020\u00102\u0006\u0010\u0012\u001a\u00020\u0010H\u0016¨\u0006\u0014"}, d2 = {"Lcom/apk/claw/android/service/monitor/StreamMonitorService;", "Landroid/app/Service;", "()V", "buildNotification", "Landroid/app/Notification;", NotificationCompat.CATEGORY_STATUS, "", "createNotificationChannel", "", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "", "flags", "startId", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes.dex */
public final class StreamMonitorService extends Service {
    private static final String CHANNEL_ID = "stream_monitor_service";
    private static final String CHANNEL_NAME = "视频流监控服务";
    private static final int NOTIFICATION_ID = 2002;
    private static final String TAG = "StreamMonitorService";

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("监控运行中"));
        Log.i(TAG, "StreamMonitorService created");
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "StreamMonitorService started");
        return 1;
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "StreamMonitorService destroyed");
    }

    private final Notification buildNotification(String status) {
        StreamMonitorService streamMonitorService = this;
        Notification build = new NotificationCompat.Builder(streamMonitorService, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher).setContentTitle("ApkClaw 视频监控").setContentText(status).setPriority(-1).setContentIntent(PendingIntent.getActivity(streamMonitorService, 0, new Intent(streamMonitorService, (Class<?>) HomeActivity.class), 201326592)).setOngoing(true).build();
        Intrinsics.checkNotNullExpressionValue(build, "build(...)");
        return build;
    }

    private final void createNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, 2);
        notificationChannel.setDescription("保持视频流监控服务活跃");
        ((NotificationManager) getSystemService(NotificationManager.class)).createNotificationChannel(notificationChannel);
    }
}
