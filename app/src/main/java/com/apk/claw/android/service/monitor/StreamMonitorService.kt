package com.apk.claw.android.service.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.apk.claw.android.R
import com.apk.claw.android.ui.home.HomeActivity

class StreamMonitorService : Service() {

    companion object {
        private const val TAG = "StreamMonitorService"
        private const val CHANNEL_ID = "stream_monitor_service"
        private const val CHANNEL_NAME = "视频流监控服务"
        private const val NOTIFICATION_ID = 2002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("监控运行中"))
        Log.i(TAG, "StreamMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "StreamMonitorService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "StreamMonitorService destroyed")
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("ApkClaw 视频监控")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持视频流监控服务活跃"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
