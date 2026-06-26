package com.apk.claw.android.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * CameraX 帧推送器
 *
 * 将摄像头画面按指定帧率转换为 JPEG 字节流并推入 VisionFrameBuffer。
 * 支持 YUV_420_888 → JPEG 转换、缩放到 MAX_FRAME_WIDTH、旋转处理。
 */
class CameraFramePusher(
    private val lifecycleOwner: LifecycleOwner,
    private val previewSurfaceProvider: Preview.SurfaceProvider? = null
) {

    companion object {
        private const val TAG = "CameraFramePusher"
        private const val DEFAULT_FPS = 1
        private const val DEFAULT_JPEG_QUALITY = 80
        private const val MAX_FRAME_WIDTH = 720
    }

    interface Callback {
        fun onCameraError(message: String)
    }

    var callback: Callback? = null
    var fps: Int = DEFAULT_FPS
        set(value) { field = value.coerceIn(1, 30) }
    var jpegQuality: Int = DEFAULT_JPEG_QUALITY
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    val running: Boolean get() = isRunning.get()

    private val isRunning = AtomicBoolean(false)
    private val globalRunning = AtomicBoolean(false)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastFrameTimeMs = 0L

    fun start() {
        if (isRunning.get()) {
            Log.w(TAG, "Already running")
            return
        }
        val context = lifecycleOwner as android.content.Context
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
                globalRunning.set(true)
                Log.i(TAG, "CameraFramePusher started (facing=${if (lensFacing == CameraSelector.LENS_FACING_BACK) "back" else "front"}, fps=$fps)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                callback?.onCameraError("摄像头启动失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // 预览
        previewSurfaceProvider?.let {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(it)
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
        }

        // 帧分析
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameTimeMs >= 1000L / fps) {
                    lastFrameTimeMs = now
                    processFrame(imageProxy)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            } finally {
                imageProxy.close()
            }
        }

        provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
        isRunning.set(true)
    }

    /**
     * 处理单帧：YUV_420_888 → JPEG → 缩放 → 旋转 → 推入 VisionFrameBuffer
     */
    private fun processFrame(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: return
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        try {
            // 提取 YUV 数据（NV12 格式）
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv12 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv12, 0, ySize)
            vBuffer.get(nv12, ySize, vSize)
            uBuffer.get(nv12, ySize + vSize, uSize)

            // YUV → JPEG
            val yuvImage = YuvImage(nv12, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            val jpegStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), jpegQuality, jpegStream)
            var jpegBytes = jpegStream.toByteArray()

            // 缩放到 MAX_FRAME_WIDTH（如果超过）
            if (image.width > MAX_FRAME_WIDTH) {
                val srcBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return
                val scaledWidth = MAX_FRAME_WIDTH
                val scaledHeight = (srcBitmap.height * MAX_FRAME_WIDTH) / srcBitmap.width
                val scaledBitmap = Bitmap.createScaledBitmap(srcBitmap, scaledWidth, scaledHeight, true)

                // 旋转
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, matrix, true)

                // 压缩回 JPEG
                val outStream = ByteArrayOutputStream()
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outStream)
                jpegBytes = outStream.toByteArray()

                scaledBitmap.recycle()
                rotatedBitmap.recycle()
                srcBitmap.recycle()
            }

            VisionFrameBuffer.offer(jpegBytes)
        } catch (e: Exception) {
            Log.e(TAG, "processFrame error", e)
        }
    }

    fun stop() {
        isRunning.set(false)
        globalRunning.set(false)
        analysisExecutor.shutdown()
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding camera", e)
        }
        cameraProvider = null
        Log.i(TAG, "CameraFramePusher stopped")
    }

    fun switchCamera() {
        if (!isRunning.get()) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        cameraProvider?.let {
            try {
                it.unbindAll()
                bindCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Error switching camera", e)
            }
        }
    }
}
