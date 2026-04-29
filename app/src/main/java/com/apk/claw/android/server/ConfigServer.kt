package com.apk.claw.android.server

import android.content.Context
import com.apk.claw.android.BuildConfig
import com.apk.claw.android.channel.ChannelManager
import com.apk.claw.android.tool.ToolRegistry
import com.apk.claw.android.tool.ToolResult
import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.brain.LocalBrain
import com.apk.claw.android.timeline.TaskTimeline
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.relay.RelayRoomSystem
import fi.iki.elonen.NanoHTTPD
/**
 * 局域网 HTTP 配置服务器
 * 提供 H5 页面用于在电脑浏览器上配置钉钉/飞书 key
 */
class ConfigServer(
    private val context: Context,
    port: Int = PORT
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "ConfigServer"
        const val PORT = 9527
        private const val MIME_HTML = "text/html"
        private const val MIME_JSON = "application/json"
    }

    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        // CORS 预检请求
        if (session.method == Method.OPTIONS) {
            return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
        }

        val uri = session.uri
        val method = session.method

        return try {
            when {
                (uri == "/" || uri == "/index.html") && method == Method.GET -> serveHtml()
                uri == "/api/channels" && method == Method.GET -> handleGetChannels()
                uri == "/api/channels" && method == Method.POST -> handlePostChannels(session)
                uri == "/api/llm" && method == Method.GET -> handleGetLlm()
                uri == "/api/llm" && method == Method.POST -> handlePostLlm(session)
                uri == "/debug.html" && method == Method.GET && BuildConfig.DEBUG -> serveDebugHtml()
                uri == "/api/debug/tools" && method == Method.GET && BuildConfig.DEBUG -> handleGetTools()
                uri == "/api/debug/execute" && method == Method.POST && BuildConfig.DEBUG -> handleExecuteTool(session)
                uri == "/api/debug/screen-full" && method == Method.GET && BuildConfig.DEBUG -> handleGetScreenFull()
                uri.startsWith("/api/debug/file") && method == Method.GET && BuildConfig.DEBUG -> handleServeFile(session)
                // ==================== LobsterHUD Pro 功能 API ====================
                uri == "/api/brain/entries" && method == Method.GET -> handleGetBrainEntries(session)
                uri == "/api/brain/search" && method == Method.GET -> handleSearchBrain(session)
                uri == "/api/brain/entry" && method == Method.POST -> handleAddBrainEntry(session)
                uri == "/api/timeline/tasks" && method == Method.GET -> handleGetTimelineTasks(session)
                uri == "/api/timeline/stats" && method == Method.GET -> handleGetTimelineStats()
                uri == "/api/skills" && method == Method.GET -> handleGetSkills()
                uri == "/api/skill" && method == Method.POST -> handleCreateSkill(session)
                uri == "/api/relay/rooms" && method == Method.GET -> handleGetRelayRooms()
                uri == "/api/relay/create" && method == Method.POST -> handleCreateRelayRoom(session)
                uri == "/api/relay/join" && method == Method.POST -> handleJoinRelayRoom(session)
                uri == "/api/features/overview" && method == Method.GET -> handleGetFeaturesOverview()
                else -> corsResponse(
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND, MIME_JSON,
                        """{"code":-1,"message":"not found"}"""
                    )
                )
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Server error: ${e.message}")
            corsResponse(
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_JSON,
                    """{"code":-1,"message":"${e.message}"}"""
                )
            )
        }
    }

    private fun serveHtml(): Response {
        val inputStream = context.assets.open("web/index.html")
        val html = inputStream.bufferedReader().use { it.readText() }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_HTML, html))
    }

    private fun handleGetChannels(): Response {
        val data = JsonObject().apply {
            addProperty("dingtalkAppKey", KVUtils.getDingtalkAppKey())
            addProperty("dingtalkAppSecret", KVUtils.getDingtalkAppSecret())
            addProperty("feishuAppId", KVUtils.getFeishuAppId())
            addProperty("feishuAppSecret", KVUtils.getFeishuAppSecret())
            addProperty("qqAppId", KVUtils.getQqAppId())
            addProperty("qqAppSecret", KVUtils.getQqAppSecret())
            addProperty("discordBotToken", KVUtils.getDiscordBotToken())
            addProperty("telegramBotToken", KVUtils.getTelegramBotToken())
        }
        val result = JsonObject().apply {
            addProperty("code", 0)
            add("data", data)
            addProperty("message", "ok")
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
    }

    private fun handlePostChannels(session: IHTTPSession): Response {
        // NanoHTTPD 要求先 parseBody 才能读取 POST body
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""

        val json = try {
            gson.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            return corsResponse(
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_JSON,
                    """{"code":-1,"message":"invalid json"}"""
                )
            )
        }

        var reinitDingtalk = false
        var reinitFeishu = false
        var reinitQQ = false
        var reinitDiscord = false
        var reinitTelegram = false

        // 钉钉配置
        if (json.has("dingtalkAppKey")) {
            val value = json.get("dingtalkAppKey").asString
            KVUtils.setDingtalkAppKey(value)
            reinitDingtalk = true
        }
        if (json.has("dingtalkAppSecret")) {
            val value = json.get("dingtalkAppSecret").asString
            // 如果是脱敏值则跳过
            if (!isMaskedValue(value)) {
                KVUtils.setDingtalkAppSecret(value)
                reinitDingtalk = true
            }
        }

        // 飞书配置
        if (json.has("feishuAppId")) {
            val value = json.get("feishuAppId").asString
            KVUtils.setFeishuAppId(value)
            reinitFeishu = true
        }
        if (json.has("feishuAppSecret")) {
            val value = json.get("feishuAppSecret").asString
            if (!isMaskedValue(value)) {
                KVUtils.setFeishuAppSecret(value)
                reinitFeishu = true
            }
        }

        // QQ 配置
        if (json.has("qqAppId")) {
            val value = json.get("qqAppId").asString
            KVUtils.setQqAppId(value)
            reinitQQ = true
        }
        if (json.has("qqAppSecret")) {
            val value = json.get("qqAppSecret").asString
            if (!isMaskedValue(value)) {
                KVUtils.setQqAppSecret(value)
                reinitQQ = true
            }
        }

        // Discord 配置
        if (json.has("discordBotToken")) {
            val value = json.get("discordBotToken").asString
            if (!isMaskedValue(value)) {
                KVUtils.setDiscordBotToken(value)
                reinitDiscord = true
            }
        }

        // Telegram 配置
        if (json.has("telegramBotToken")) {
            val value = json.get("telegramBotToken").asString
            if (!isMaskedValue(value)) {
                KVUtils.setTelegramBotToken(value)
                reinitTelegram = true
            }
        }

        // 重新初始化对应通道
        if (reinitDingtalk) {
            ChannelManager.reinitDingTalkFromStorage()
        }
        if (reinitFeishu) {
            ChannelManager.reinitFeiShuFromStorage()
        }
        if (reinitQQ) {
            ChannelManager.reinitQQFromStorage()
        }
        if (reinitDiscord) {
            ChannelManager.reinitDiscordFromStorage()
        }
        if (reinitTelegram) {
            ChannelManager.reinitTelegramFromStorage()
        }

        // 通知 Settings 页面刷新绑定状态
        if (reinitDingtalk || reinitFeishu || reinitQQ || reinitDiscord || reinitTelegram) {
            ConfigServerManager.notifyConfigChanged()
        }

        val result = JsonObject().apply {
            addProperty("code", 0)
            addProperty("message", "ok")
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
    }

    private fun handleGetLlm(): Response {
        val apiKey = KVUtils.getLlmApiKey()
        val data = JsonObject().apply {
            addProperty("llmApiKey", apiKey)
            addProperty("llmBaseUrl", KVUtils.getLlmBaseUrl())
            addProperty("llmModelName", KVUtils.getLlmModelName())
        }
        val result = JsonObject().apply {
            addProperty("code", 0)
            add("data", data)
            addProperty("message", "ok")
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
    }

    private fun handlePostLlm(session: IHTTPSession): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""

        val json = try {
            gson.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            return corsResponse(
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_JSON,
                    """{"code":-1,"message":"invalid json"}"""
                )
            )
        }

        if (json.has("llmApiKey")) {
            val value = json.get("llmApiKey").asString
            if (!isMaskedValue(value)) {
                KVUtils.setLlmApiKey(value)
            }
        }
        if (json.has("llmBaseUrl")) {
            KVUtils.setLlmBaseUrl(json.get("llmBaseUrl").asString)
        }
        if (json.has("llmModelName")) {
            val value = json.get("llmModelName").asString.trim()
            KVUtils.setLlmModelName(if (value.isEmpty()) "" else value)
        }

        ConfigServerManager.notifyConfigChanged()

        val result = JsonObject().apply {
            addProperty("code", 0)
            addProperty("message", "ok")
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
    }

    // ==================== Debug (仅 DEBUG 构建) ====================
    
    private fun handleGetScreenFull(): Response {
        val service = com.apk.claw.android.service.ClawAccessibilityService.getInstance()
            ?: return corsResponse(
                newFixedLengthResponse(
                    Response.Status.OK, MIME_JSON,
                    """{"code":-1,"message":"Accessibility service is not running"}"""
                )
            )
        val tree = service.screenTreeFull
        val data = JsonObject().apply {
            addProperty("success", tree != null)
            addProperty("data", tree ?: "")
        }
        val result = JsonObject().apply {
            addProperty("code", 0)
            add("data", data)
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
    }

    private fun serveDebugHtml(): Response {
        val inputStream = context.assets.open("web/debug.html")
        val html = inputStream.bufferedReader().use { it.readText() }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_HTML, html))
    }

    private fun handleGetTools(): Response {
        val tools = ToolRegistry.getAllTools()
        val arr = JsonArray()
        for (tool in tools) {
            val obj = JsonObject().apply {
                addProperty("name", tool.getName())
                addProperty("displayName", tool.getDisplayName())
                addProperty("description", tool.getDescription())
                val params = JsonArray()
                for (p in tool.getParameters()) {
                    params.add(JsonObject().apply {
                        addProperty("name", p.name)
                        addProperty("type", p.type)
                        addProperty("description", p.description)
                        addProperty("required", p.isRequired)
                    })
                }
                add("parameters", params)
            }
            arr.add(obj)
        }
        val result = JsonObject().apply {
            addProperty("code", 0)
            add("data", arr)
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
    }

    private fun handleExecuteTool(session: IHTTPSession): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""

        val json = try {
            gson.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            return corsResponse(
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_JSON,
                    """{"code":-1,"message":"invalid json"}"""
                )
            )
        }

        val toolName = json.get("tool")?.asString ?: return corsResponse(
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_JSON,
                """{"code":-1,"message":"missing tool name"}"""
            )
        )

        val params = mutableMapOf<String, Any>()
        try {
            json.getAsJsonObject("params")?.entrySet()?.forEach { (key, value) ->
                when {
                    value.isJsonNull -> {}
                    !value.isJsonPrimitive -> params[key] = value.toString()
                    value.asJsonPrimitive.isNumber -> params[key] = value.asNumber
                    value.asJsonPrimitive.isBoolean -> params[key] = value.asBoolean
                    else -> params[key] = value.asString
                }
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Debug param parse error: ${e.message}")
        }

        XLog.d(TAG, "Debug execute: $toolName params=$params")

        val toolResult = try {
            ToolRegistry.executeTool(toolName, params)
        } catch (e: Exception) {
            XLog.e(TAG, "Debug execute error", e)
            ToolResult.error("Exception: ${e.message}")
        }

        val data = JsonObject().apply {
            addProperty("success", toolResult.isSuccess)
            addProperty("data", toolResult.data)
            addProperty("error", toolResult.error)
        }
        val result = JsonObject().apply {
            addProperty("code", 0)
            add("data", data)
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
    }

    private fun handleServeFile(session: IHTTPSession): Response {
        val path = session.parms["path"] ?: return corsResponse(
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_JSON,
                """{"code":-1,"message":"missing path param"}"""
            )
        )
        // 安全校验：只允许访问 cache 目录下的文件
        val cacheDir = context.cacheDir.absolutePath
        val file = java.io.File(path)
        if (!file.exists() || !file.absolutePath.startsWith(cacheDir)) {
            return corsResponse(
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_JSON,
                    """{"code":-1,"message":"file not found or access denied"}"""
                )
            )
        }
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        return corsResponse(newFixedLengthResponse(Response.Status.OK, mime, file.inputStream(), file.length()))
    }

    /**
     * 脱敏：只显示后4位，前面用 * 替代
     */
    private fun maskSecret(secret: String): String {
        if (secret.isEmpty()) return ""
        if (secret.length <= 4) return secret
        return "*".repeat(secret.length - 4) + secret.takeLast(4)
    }

    /**
     * 判断是否为脱敏后的值（包含 *）
     */
    private fun isMaskedValue(value: String): Boolean {
        return value.contains("*")
    }

    // ==================== LobsterHUD Pro 功能 API Handlers ====================

    private fun handleGetFeaturesOverview(): Response {
        val overview = try {
            FeatureIntegrationManager.getInstance(context).getFeatureOverview()
        } catch (_: Exception) { mapOf("initialized" to false) }
        return jsonOk(overview)
    }

    private fun handleGetBrainEntries(session: IHTTPSession): Response {
        val brain = LocalBrain.getInstance(context)
        val namespace = session.parms["namespace"]
        val entries = if (namespace != null) brain.getEntriesByNamespace(namespace) else brain.getAllEntries()
        val arr = JsonArray()
        for (e in entries) { arr.add(gson.toJsonTree(e)) }
        return jsonOk(mapOf("entries" to arr, "total" to entries.size))
    }

    private fun handleSearchBrain(session: IHTTPSession): Response {
        val brain = LocalBrain.getInstance(context)
        val query = session.parms["q"] ?: return jsonErr("missing q param")
        val limit = session.parms["limit"]?.toIntOrNull() ?: 10
        val results = brain.search(query, limit = limit)
        val arr = JsonArray()
        for (r in results) { arr.add(gson.toJsonTree(r)) }
        return jsonOk(mapOf("results" to arr))
    }

    private fun handleAddBrainEntry(session: IHTTPSession): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { return jsonErr("invalid json") }
        val content = json.get("content")?.asString ?: return jsonErr("missing content")
        val namespace = json.get("namespace")?.asString ?: "memory"
        val brain = LocalBrain.getInstance(context)
        val entry = brain.addEntry(content = content, namespace = namespace, tags = listOf("api"))
        return jsonOk(mapOf("id" to entry.id))
    }

    private fun handleGetTimelineTasks(session: IHTTPSession): Response {
        val timeline = TaskTimeline.getInstance(context)
        val limit = session.parms["limit"]?.toIntOrNull() ?: 20
        val status = session.parms["status"]
        val tasks = if (status != null) {
            try { timeline.getTasksByStatus(TaskTimeline.TaskStatus.valueOf(status)) } catch (_: Exception) { timeline.getRecentTasks(limit) }
        } else { timeline.getRecentTasks(limit) }
        val arr = JsonArray()
        for (t in tasks) { arr.add(gson.toJsonTree(t)) }
        return jsonOk(mapOf("tasks" to arr))
    }

    private fun handleGetTimelineStats(): Response {
        val timeline = TaskTimeline.getInstance(context)
        return jsonOk(timeline.getStats())
    }

    private fun handleGetSkills(): Response {
        val skills = SkillSystem.getInstance(context).getAllSkills()
        val arr = JsonArray()
        for (s in skills) { arr.add(gson.toJsonTree(s)) }
        return jsonOk(mapOf("skills" to arr, "total" to skills.size))
    }

    private fun handleCreateSkill(session: IHTTPSession): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { return jsonErr("invalid json") }
        val name = json.get("name")?.asString ?: return jsonErr("missing name")
        val description = json.get("description")?.asString ?: ""
        val promptTemplate = json.get("promptTemplate")?.asString ?: ""
        val skillSystem = SkillSystem.getInstance(context)
        val skill = skillSystem.createSkill(name = name, description = description, promptTemplate = promptTemplate)
        return jsonOk(mapOf("id" to skill.id, "name" to skill.name))
    }

    private fun handleGetRelayRooms(): Response {
        val relay = RelayRoomSystem.getInstance(context)
        val rooms = relay.getMyRooms()
        val arr = JsonArray()
        for (r in rooms) { arr.add(gson.toJsonTree(r)) }
        return jsonOk(mapOf("rooms" to arr, "deviceId" to relay.config.myDeviceId))
    }

    private fun handleCreateRelayRoom(session: IHTTPSession): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { return jsonErr("invalid json") }
        val name = json.get("name")?.asString ?: ""
        val password = json.get("password")?.asString
        val room = RelayRoomSystem.getInstance(context).createRoom(name = name, password = password)
        return jsonOk(mapOf("roomId" to room.roomId, "roomName" to room.roomName))
    }

    private fun handleJoinRelayRoom(session: IHTTPSession): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { return jsonErr("invalid json") }
        val roomId = json.get("roomId")?.asString ?: return jsonErr("missing roomId")
        val password = json.get("password")?.asString
        val room = RelayRoomSystem.getInstance(context).joinRoom(roomId, password)
            ?: return jsonErr("failed to join room")
        return jsonOk(mapOf("roomId" to room.roomId, "deviceCount" to room.devices.size))
    }

    private fun jsonOk(data: Any): Response {
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, gson.toJson(mapOf("code" to 0, "data" to data))))
    }

    private fun jsonErr(msg: String): Response {
        return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_JSON, gson.toJson(mapOf("code" to -1, "message" to msg))))
    }

    private fun corsResponse(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }
}
