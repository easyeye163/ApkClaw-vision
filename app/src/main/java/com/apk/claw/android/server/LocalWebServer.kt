package com.apk.claw.android.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

class LocalWebServer(private val context: Context, port: Int = 18080) : NanoHTTPD(port) {

    companion object {
        private const val ASSET_BASE = "web/fpv/"
        private val MIME_MAP = mapOf(
            "html" to "text/html; charset=utf-8", "htm" to "text/html; charset=utf-8",
            "css" to "text/css; charset=utf-8", "js" to "application/javascript; charset=utf-8",
            "json" to "application/json; charset=utf-8", "png" to "image/png",
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
            "svg" to "image/svg+xml", "ico" to "image/x-icon", "webp" to "image/webp",
            "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf",
            "mp3" to "audio/mpeg", "mp4" to "video/mp4", "wasm" to "application/wasm",
            "bin" to "application/octet-stream"
        )
        fun getMimeType(path: String): String {
            val ext = path.substringAfterLast('.', "").lowercase()
            return MIME_MAP[ext] ?: "application/octet-stream"
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.removePrefix("/")
        val assetPath = if (uri.isEmpty() || uri.endsWith("/")) ASSET_BASE + "index.html" else ASSET_BASE + uri
        return try {
            val inputStream: InputStream = context.assets.open(assetPath)
            newChunkedResponse(Response.Status.OK, getMimeType(assetPath), inputStream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404: $uri")
        }
    }
}