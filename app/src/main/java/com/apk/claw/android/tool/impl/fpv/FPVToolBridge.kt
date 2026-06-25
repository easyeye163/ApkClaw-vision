package com.apk.claw.android.tool.impl.fpv

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * FPV 游戏 WebView 通信桥梁（单例）
 *
 * 当 FPVGameActivity 打开时，注册其 WebView；
 * 当原生 Agent 工具（addTree, addHouseBody 等）被调用时，
 * 通过此桥梁向 WebView 注入 JavaScript 来操作 3D 世界。
 */
object FPVToolBridge {

    private const val TAG = "FPVToolBridge"
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var webView: WebView? = null

    /** 当前游戏是否处于活跃状态 */
    val isGameActive: Boolean get() = webView != null

    /**
     * FPVGameActivity 在 onResume 时调用
     */
    fun registerWebView(wv: WebView) {
        mainHandler.post { webView = wv }
        Log.d(TAG, "WebView registered, game is active")
    }

    /**
     * FPVGameActivity 在 onPause/onDestroy 时调用
     */
    fun unregisterWebView() {
        mainHandler.post { webView = null }
        Log.d(TAG, "WebView unregistered")
    }

    /**
     * 向 WebView 执行 JavaScript 以添加/操作动态 3D 物体
     *
     * 调用链：原生 Tool → FPVToolBridge → WebView JS → Zustand Store → 3D 渲染
     *
     * @param toolName 工具名（如 "addTree"、"addHouseBody"）
     * @param params   工具参数（x, z, height, color 等）
     * @return 执行结果 JSON 字符串
     */
    fun executeGameTool(toolName: String, params: Map<String, Any>): String {
        val wv = webView
            ?: return """{"success":false,"error":"FPV 游戏未打开，请先启动 FPV 飞行游戏再使用建造工具。"}"""

        // Sanitize tool name to prevent injection
        if (!toolName.matches(Regex("^[a-zA-Z_]+$"))) {
            return """{"success":false,"error":"非法工具名: $toolName"}"""
        }

        val paramsJson = gson.toJson(params)
        val js = "(function(){try{var r=window.__fpv_buildStore.__executeNativeTool('$toolName',$paramsJson);return JSON.stringify(r);}catch(e){return JSON.stringify({success:false,error:e.message});}})()"

        var result = ""
        val lock = Object()
        var completed = false

        mainHandler.post {
            try {
                wv.evaluateJavascript(js) { value ->
                    // evaluateJavascript returns quoted JSON string, unquote it
                    result = if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                        try { gson.fromJson(value, String::class.java) } catch (_: Exception) { value }
                    } else {
                        value ?: """{"success":false,"error":"空返回"}"""
                    }
                    synchronized(lock) {
                        completed = true
                        lock.notifyAll()
                    }
                }
            } catch (e: Exception) {
                result = """{"success":false,"error":"WebView 执行失败: ${e.message}"}"""
                synchronized(lock) {
                    completed = true
                    lock.notifyAll()
                }
            }
        }

        // Wait for async result (max 3 seconds)
        synchronized(lock) {
            if (!completed) {
                try { lock.wait(3000) } catch (_: InterruptedException) {}
            }
        }

        return if (result.isBlank()) """{"success":false,"error":"执行超时(3s)"}""" else result
    }

    /**
     * 获取当前动态物体数量
     */
    fun getDynamicObjectCount(): Int {
        val wv = webView ?: return -1
        var count = -1
        val lock = Object()
        var completed = false

        mainHandler.post {
            try {
                wv.evaluateJavascript("(function(){try{return String(window.__fpv_buildStore.getState().dynamicObjects.length);}catch(e){return '-1';}})()") { value ->
                    count = value?.trim('"')?.toIntOrNull() ?: -1
                    synchronized(lock) {
                        completed = true
                        lock.notifyAll()
                    }
                }
            } catch (_: Exception) {
                synchronized(lock) { completed = true; lock.notifyAll() }
            }
        }

        synchronized(lock) {
            if (!completed) { try { lock.wait(2000) } catch (_: InterruptedException) {} }
        }
        return count
    }
}