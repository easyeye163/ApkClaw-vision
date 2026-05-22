package com.apk.claw.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.apk.claw.android.R
import com.apk.claw.android.ui.camera.ScreenStreamActivity
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.vision.ScreenCapturePusher
import com.apk.claw.android.vision.VisionFrameBuffer

/**
 * 屏幕捕获前台服务
 *
 * Android 14+ (API 34+) 要求 MediaProjection 必须在声明了
 * FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION 的前台服务中运行。
 *
 * 本服务负责：
 * 1. 接收 MediaProjection 授权结果
 * 2. 创建并管理 ScreenCapturePusher（帧采集 → VisionFrameBuffer）
 * 3. 通过前台通知保持服务活跃
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_service"
        private const val CHANNEL_NAME = "屏幕捕获服务"
        private const val NOTIFICATION_ID = 2003

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_START = "com.apk.claw.android.action.START_SCREEN_CAPTURE"
        const val ACTION_STOP = "com.apk.claw.android.action.STOP_SCREEN_CAPTURE"

        @Volatile
        private var _isRunning = false

        val isRunning: Boolean get() = _isRunning

        /**
         * 启动屏幕捕获服务
         */
        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止屏幕捕获服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var screenCapturePusher: ScreenCapturePusher? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        XLog.i(TAG, "ScreenCaptureService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                // 启动前台通知
                startForeground(NOTIFICATION_ID, buildNotification("屏幕捕获启动中..."))
                handleStartCapture(intent)
            }
            ACTION_STOP -> {
                stopCapture()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        _isRunning = false
        XLog.i(TAG, "ScreenCaptureService destroyed")
    }

    private fun handleStartCapture(intent: Intent) {
        if (_isRunning) {
            XLog.w(TAG, "Screen capture already running")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == 0 || resultData == null) {
            XLog.e(TAG, "Invalid MediaProjection result: resultCode=$resultCode, data=$resultData")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                XLog.e(TAG, "Failed to get MediaProjection")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            // 设置 MediaProjection 回调，在停止时清理
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    XLog.i(TAG, "MediaProjection stopped by system")
                    cleanupCapture()
                    _isRunning = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, null)

            VisionFrameBuffer.start()

            val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val pusher = ScreenCapturePusher(mediaProjection!!)
            pusher.fps = 2
            pusher.start(windowManager)
            screenCapturePusher = pusher

            _isRunning = true

            // 更新通知
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, buildNotification("屏幕捕获运行中"))

            XLog.i(TAG, "Screen capture started successfully via foreground service")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to start screen capture", e)
            cleanupCapture()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopCapture() {
        screenCapturePusher?.stop()
        screenCapturePusher = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            XLog.w(TAG, "Error stopping MediaProjection", e)
        }
        mediaProjection = null

        VisionFrameBuffer.stop()
        _isRunning = false

        XLog.i(TAG, "Screen capture stopped")
    }

    private fun cleanupCapture() {
        screenCapturePusher?.stop()
        screenCapturePusher = null
        mediaProjection = null
        VisionFrameBuffer.stop()
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ScreenStreamActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("ApkClaw 屏幕捕获")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕捕获前台服务通知"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
