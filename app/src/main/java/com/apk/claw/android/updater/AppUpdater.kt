package com.apk.claw.android.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.apk.claw.android.utils.XLog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * App Auto-Updater
 *
 * Uses a fixed GitHub raw path (version.json) to check for updates.
 * The version.json contains the latest version info and APK download URL.
 *
 * Fixed path: https://raw.githubusercontent.com/easyeye163/ApkClaw-vision/main/version.json
 *
 * version.json format:
 * {
 *   "versionCode": 20,
 *   "versionName": "0.0.22",
 *   "downloadUrl": "https://github.com/easyeye163/ApkClaw-vision/releases/download/v0.0.22/ApkClaw-latest.apk",
 *   "releaseNotes": "Bug fixes and improvements",
 *   "fileSize": 12345678
 * }
 *
 * Flow:
 * 1. checkForUpdate() -> fetches version.json from GitHub raw path
 * 2. Compares versionCode with current app versionCode
 * 3. If newer: downloadAndInstall() -> DownloadManager + install intent
 */
class AppUpdater(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdater"

        const val GITHUB_OWNER = "easyeye163"
        const val GITHUB_REPO = "ApkClaw-vision"
        const val GITHUB_BRANCH = "main"

        // Fixed path for version info on GitHub
        const val VERSION_JSON_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/$GITHUB_BRANCH/version.json"

        const val GITHUB_RELEASES_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

        // APK file name in DownloadManager
        private const val APK_FILE_NAME = "ApkClaw_update.apk"

        private const val TIMEOUT_SECONDS = 30L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Update check result
     */
    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersionCode: Int,
        val latestVersionName: String,
        val currentVersionCode: Int,
        val currentVersionName: String,
        val downloadUrl: String? = null,
        val releaseNotes: String? = null,
        val fileSize: Long = 0
    )

    /**
     * Check the fixed GitHub path for the latest version info
     */
    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentVersionName = getCurrentVersionName()
            val currentVersionCode = getCurrentVersionCode()
            XLog.d(TAG, "Current version: $currentVersionName (code: $currentVersionCode)")

            val request = Request.Builder()
                .url(VERSION_JSON_URL)
                .header("User-Agent", "ApkClaw/$currentVersionName")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                XLog.w(TAG, "Failed to fetch version.json, HTTP ${response.code}")
                return@withContext UpdateInfo(
                    hasUpdate = false,
                    latestVersionCode = currentVersionCode,
                    latestVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
            }

            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)

            val latestVersionCode = json.optInt("versionCode", 0)
            val latestVersionName = json.optString("versionName", "")
            val downloadUrl = json.optString("downloadUrl", "")
            val releaseNotes = json.optString("releaseNotes", "")
            val fileSize = json.optLong("fileSize", 0)

            val hasUpdate = latestVersionCode > currentVersionCode
            XLog.d(TAG, "Latest: $latestVersionName (code: $latestVersionCode), hasUpdate: $hasUpdate")

            UpdateInfo(
                hasUpdate = hasUpdate,
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                downloadUrl = downloadUrl.ifEmpty { null },
                releaseNotes = releaseNotes.ifEmpty { null },
                fileSize = fileSize
            )
        } catch (e: Exception) {
            XLog.e(TAG, "Update check failed", e)
            val currentVersionName = getCurrentVersionName()
            val currentVersionCode = getCurrentVersionCode()
            UpdateInfo(
                hasUpdate = false,
                latestVersionCode = currentVersionCode,
                latestVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            )
        }
    }

    /**
     * Download APK via DownloadManager and trigger install
     */
    suspend fun downloadAndInstall(downloadUrl: String, versionName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            XLog.d(TAG, "Downloading: $downloadUrl")

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("ApkClaw v$versionName")
                setDescription("Downloading update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            XLog.d(TAG, "Download started, id: $downloadId")

            // Wait for download completion
            val success = waitForDownload(downloadId)
            if (!success) {
                XLog.e(TAG, "Download failed")
                return@withContext false
            }

            // Trigger install
            val apkFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APK_FILE_NAME
            )
            installApk(apkFile)
            true
        } catch (e: Exception) {
            XLog.e(TAG, "Download and install failed", e)
            false
        }
    }

    /**
     * Wait for DownloadManager to complete
     */
    private suspend fun waitForDownload(downloadId: Long): Boolean = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        cursor.close()
                        if (cont.isActive) cont.resume(status == DownloadManager.STATUS_SUCCESSFUL)
                    } else {
                        cursor.close()
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )

        cont.invokeOnCancellation {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    /**
     * Trigger APK install via intent
     */
    private fun installApk(apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
        XLog.d(TAG, "Install intent launched for: ${apkFile.absolutePath}")
    }

    /**
     * Get current app version name
     */
    fun getCurrentVersionName(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }

    /**
     * Get current app version code
     */
    fun getCurrentVersionCode(): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            info.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1) {
            String.format("%.1f MB", mb)
        } else {
            String.format("%.0f KB", bytes / 1024.0)
        }
    }
}
