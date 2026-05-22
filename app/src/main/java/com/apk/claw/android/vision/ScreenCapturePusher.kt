package com.apk.claw.android.vision

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 屏幕捕获帧推送器
 *
 * 使用 MediaProjection + ImageReader 捕获屏幕画面，
 * 按指定帧率转换为 JPEG 并推入 VisionFrameBuffer。
 */
class ScreenCapturePusher(
    private val mediaProjection: MediaProjection
) {

    companion object {
        private const val TAG = "ScreenCapturePusher"
        private const val DEFAULT_JPEG_QUALITY = 70
        private const val MAX_FRAME_WIDTH = 720
        private const val VIRTUAL_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
    }

    var fps: Int = 2
        set(value) { field = value.coerceIn(1, 10) }
    var jpegQuality: Int = DEFAULT_JPEG_QUALITY
    val running: Boolean get() = isRunning.get()

    private val isRunning = AtomicBoolean(false)
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val captureHandler = Handler(Looper.getMainLooper())
    private var lastFrameTimeMs = 0L
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    /**
     * 启动屏幕捕获
     */
    fun start(windowManager: WindowManager) {
        if (isRunning.get()) {
            Log.w(TAG, "Already running")
            return
        }

        // 获取屏幕尺寸
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        // 缩放分辨率以减少性能开销
        val captureWidth = screenWidth.coerceAtMost(MAX_FRAME_WIDTH)
        val captureHeight = (screenHeight * captureWidth) / screenWidth

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                val now = System.currentTimeMillis()
                val intervalMs = 1000L / fps
                if (now - lastFrameTimeMs >= intervalMs) {
                    lastFrameTimeMs = now
                    processImage(reader)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
            } finally {
                reader.acquireLatestImage()?.close()
            }
        }, captureHandler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )

        isRunning.set(true)
        VisionFrameBuffer.start()
        Log.i(TAG, "ScreenCapturePusher started (${captureWidth}x${captureHeight}@${fps}fps)")
    }

    /**
     * 处理单帧图像：ImageReader → Bitmap → JPEG → 推入 VisionFrameBuffer
     */
    private fun processImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height

            // 读取像素数据
            val rowBytes = rowStride - pixelStride
            val bitmap = Bitmap.createBitmap(width + rowBytes / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            // 裁剪掉多余的部分（rowStride 可能大于 width * pixelStride）
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

            // 压缩为 JPEG
            val stream = ByteArrayOutputStream()
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
            val jpegBytes = stream.toByteArray()

            VisionFrameBuffer.offer(jpegBytes)

            croppedBitmap.recycle()
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "processImage error", e)
        } finally {
            image.close()
        }
    }

    /**
     * 停止屏幕捕获
     */
    fun stop() {
        isRunning.set(false)
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing virtual display", e)
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing image reader", e)
        }
        imageReader = null
        captureExecutor.shutdown()
        Log.i(TAG, "ScreenCapturePusher stopped")
    }
}
