package com.apk.claw.android.ui.chat

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.apk.claw.android.R
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Lightweight camera capture activity.
 * Opens the system camera app via ACTION_IMAGE_CAPTURE,
 * compresses the result, and returns it to the caller.
 */
class CameraActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_DATA = "photo_data"
    }

    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private val cameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            processPhoto()
        } else {
            finish()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, R.string.chat_camera_error, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val photosDir = File(filesDir, "Pictures")
            if (!photosDir.exists()) photosDir.mkdirs()
            photoFile = File(photosDir, "camera_${System.currentTimeMillis()}.jpg")

            photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile!!
            )

            val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (cameraIntent.resolveActivity(packageManager) != null) {
                cameraLauncher.launch(cameraIntent)
            } else {
                Toast.makeText(this, R.string.chat_camera_error, Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.chat_camera_error, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processPhoto() {
        try {
            val file = photoFile ?: run {
                Toast.makeText(this, R.string.chat_photo_failed, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            if (!file.exists()) {
                Toast.makeText(this, R.string.chat_photo_failed, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (rawBitmap == null) {
                Toast.makeText(this, R.string.chat_photo_failed, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val compressedData = compressImage(rawBitmap)
            rawBitmap.recycle()
            file.delete()

            if (compressedData == null) {
                Toast.makeText(this, R.string.chat_photo_failed, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_PHOTO_DATA, compressedData)
            }
            setResult(RESULT_OK, resultIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.chat_photo_failed, Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    /**
     * Compress image: scale to max 2048px + JPEG quality reduction to <= 1MB
     */
    private fun compressImage(bitmap: android.graphics.Bitmap): ByteArray? {
        return try {
            val maxDim = 2048
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else {
                bitmap
            }

            var quality = 75
            var bytes: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                bytes = stream.toByteArray()
                quality -= 10
            } while (bytes.size > 1024 * 1024 && quality >= 30)

            if (scaled !== bitmap) scaled.recycle()
            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
