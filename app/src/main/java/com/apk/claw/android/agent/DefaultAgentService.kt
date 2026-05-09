package com.apk.claw.android.agent

import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.agent.langchain.LangChain4jToolBridge
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.agent.llm.LlmClient
import com.apk.claw.android.agent.llm.LlmClientFactory
import com.apk.claw.android.agent.llm.LlmResponse
import com.apk.claw.android.agent.llm.StreamingListener
import com.apk.claw.android.service.ClawAccessibilityService
import com.apk.claw.android.tool.ToolRegistry
import com.apk.claw.android.tool.impl.GetScreenInfoTool
import com.apk.claw.android.tool.ToolResult
import com.apk.claw.android.utils.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.Content
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.agent.tool.ToolExecutionRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DefaultAgentService : AgentService {

    companion object {
        private const val TAG = "AgentService"
        private val GSON = Gson()

        /** LLM API 调用失败时的最大重试次数 */
        private const val MAX_API_RETRIES = 3
        /** 死循环检测：滑动窗口大小 */
        private const val LOOP_DETECT_WINDOW = 4

        /** 是否将网络请求/响应原始数据输出到沙盒缓存文件，方便调试 */
        @JvmField
        var FILE_LOGGING_ENABLED = false
        @JvmField
        var FILE_LOGGING_CACHE_DIR: File? = null

        /** 观察类工具：执行后需要自动截图 */
        val OBSERVATION_TOOLS = setOf("get_screen_info", "take_screenshot", "scroll_to_find", "find_node_info")

        /** 截图最大宽度 */
        const val SCREENSHOT_MAX_WIDTH = 720

        /** JPEG 质量 */
        const val SCREENSHOT_QUALITY = 75

        /** 滑动截图窗口：保留最近 N 张截图（Feature 2: Multi-round Visual Memory） */
        private const val MAX_SCREENSHOT_HISTORY = 3

        /** 截图验证：检测空白/黑色截图的最大重试次数（Feature 3: Structured Error Recovery） */
        private const val MAX_SCREENSHOT_RETRIES = 3

        /** Action history injection：保留最近 N 次操作记录（Feature 4: Action History Injection） */
        private const val MAX_ACTION_HISTORY = 10

        /** 需要验证是否产生屏幕变化的操作类工具 */
        private val ACTION_TOOLS = setOf("tap", "swipe", "long_press")
    }

    private lateinit var config: AgentConfig
    private lateinit var llmClient: LlmClient
    private lateinit var toolSpecs: List<dev.langchain4j.agent.tool.ToolSpecification>
    private var executor: ExecutorService? = null
    private val running = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    // ==================== 视觉感知：截图采集与注入 ====================

    /**
     * 截取屏幕并返回 Bitmap 对象（用于验证和后续处理）。
     * 缩放到最大 720px 宽度。
     */
    private fun captureScreenBitmap(): Bitmap? {
        val service = ClawAccessibilityService.getInstance()
        if (service == null) return null

        try {
            val bitmap = service.takeScreenshot(5000)
            if (bitmap == null) return null

            // Hardware Bitmap 不支持 getPixel()，先转换为 ARGB_8888 软件位图
            val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                val copied = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                bitmap.recycle()
                copied
            } else {
                bitmap
            }

            val scaledBitmap = scaleBitmap(safeBitmap, SCREENSHOT_MAX_WIDTH)
            safeBitmap.recycle()
            return scaledBitmap
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to capture screenshot bitmap", e)
            return null
        }
    }

    /**
     * 截取屏幕并转换为 Base64 编码的 JPEG。
     * 缩放到最大 720px 宽度，质量 75%。
     * 包含空白/黑色截图检测和重试（Feature 3: Structured Error Recovery）。
     */
    private fun captureScreenAsBase64(): String? {
        for (attempt in 0 until MAX_SCREENSHOT_RETRIES) {
            val scaledBitmap = captureScreenBitmap() ?: return null

            // 检测空白/黑色截图
            if (isBitmapBlank(scaledBitmap)) {
                XLog.w(TAG, "Screenshot appears blank/black (attempt ${attempt + 1}/$MAX_SCREENSHOT_RETRIES)")
                scaledBitmap.recycle()
                if (attempt < MAX_SCREENSHOT_RETRIES - 1) {
                    try { Thread.sleep(500) } catch (_: InterruptedException) {}
                    continue
                }
                // 最后一次还是空白，仍然返回
                XLog.e(TAG, "Screenshot still blank after $MAX_SCREENSHOT_RETRIES attempts, returning as-is")
                return null
            }

            try {
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_QUALITY, stream)
                scaledBitmap.recycle()

                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                XLog.d(TAG, "Screenshot captured: ${base64.length} chars base64")
                return base64
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to compress screenshot", e)
                scaledBitmap.recycle()
                return null
            }
        }
        return null
    }

    /**
     * 截取屏幕并返回 Bitmap + Base64 对。
     * 用于需要同时验证和注入的场景。
     */
    private fun captureScreenBitmapAndBase64(): Pair<Bitmap?, String?> {
        for (attempt in 0 until MAX_SCREENSHOT_RETRIES) {
            val scaledBitmap = captureScreenBitmap()

            if (scaledBitmap == null) {
                return Pair(null, null)
            }

            // 检测空白/黑色截图
            if (isBitmapBlank(scaledBitmap)) {
                XLog.w(TAG, "Screenshot appears blank/black (attempt ${attempt + 1}/$MAX_SCREENSHOT_RETRIES)")
                scaledBitmap.recycle()
                if (attempt < MAX_SCREENSHOT_RETRIES - 1) {
                    try { Thread.sleep(500) } catch (_: InterruptedException) {}
                    continue
                }
                XLog.e(TAG, "Screenshot still blank after $MAX_SCREENSHOT_RETRIES attempts")
                return Pair(null, null)
            }

            try {
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_QUALITY, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                XLog.d(TAG, "Screenshot captured: ${scaledBitmap.width}x${scaledBitmap.height}, ${base64.length} chars base64")
                return Pair(scaledBitmap, base64)
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to compress screenshot", e)
                scaledBitmap.recycle()
                return Pair(null, null)
            }
        }
        return Pair(null, null)
    }

    /**
     * 检测 Bitmap 是否大部分为空白/黑色（Feature 3: Structured Error Recovery）。
     * 通过采样像素检测颜色方差来判断。
     */
    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return true

        // 采样检测：每隔若干像素取一个样本
        val step = maxOf(1, minOf(width, height) / 20)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var sampleCount = 0

        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = bitmap.getPixel(x, y)
                totalR += android.graphics.Color.red(pixel)
                totalG += android.graphics.Color.green(pixel)
                totalB += android.graphics.Color.blue(pixel)
                sampleCount++
            }
        }

        if (sampleCount == 0) return true

        val avgR = totalR / sampleCount
        val avgG = totalG / sampleCount
        val avgB = totalB / sampleCount

        // 如果平均颜色太暗（黑色）或太亮（白色），则判定为空白
        val isBlack = avgR < 15 && avgG < 15 && avgB < 15
        val isWhite = avgR > 240 && avgG > 240 && avgB > 240

        if (!isBlack && !isWhite) return false

        // 进一步检查方差：如果颜色几乎一致，说明是纯色
        var varianceSum = 0L
        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel).toLong()
                val g = android.graphics.Color.green(pixel).toLong()
                val b = android.graphics.Color.blue(pixel).toLong()
                varianceSum += (r - avgR) * (r - avgR)
                varianceSum += (g - avgG) * (g - avgG)
                varianceSum += (b - avgB) * (b - avgB)
            }
        }

        val avgVariance = varianceSum / (sampleCount * 3)
        // 方差极低说明是纯色屏幕
        return avgVariance < 100
    }

    /**
     * 计算 Bitmap 的感知哈希（简化版），用于比较两张截图是否相同。
     */
    private fun computeScreenHash(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return 0

        var hash = 0
        val step = maxOf(1, minOf(width, height) / 10)
        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                hash = hash * 31 + bitmap.getPixel(x, y)
            }
        }
        return hash
    }

    /**
     * 缩放 Bitmap 到指定最大宽度，保持比例。
     */
    private fun scaleBitmap(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val ratio = maxWidth.toDouble() / source.width
        val newHeight = (source.height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, maxWidth, newHeight, true)
    }

    /**
     * 创建带截图的 UserMessage。
     */
    private fun createUserMessageWithImage(text: String, base64Image: String?): UserMessage {
        if (base64Image == null) {
            return UserMessage.from(text)
        }
        val contents = mutableListOf<Content>()
        contents.add(dev.langchain4j.data.message.TextContent.from(text))
        // 使用 data URL 格式传递 base64 图片，符合 OpenAI API 规范
        val dataUrl = "data:image/jpeg;base64,$base64Image"
        contents.add(ImageContent.from(dataUrl))
        return UserMessage.from(contents)
    }

    override fun initialize(config: AgentConfig) {
        this.config = config
        this.llmClient = LlmClientFactory.create(config)
        this.toolSpecs = LangChain4jToolBridge.buildToolSpecifications()
        this.executor = Executors.newSingleThreadExecutor()
        XLog.i(TAG, "Agent initialized: provider=${config.provider}, model=${config.modelName}, streaming=${config.streaming}")
    }

    override fun updateConfig(config: AgentConfig) {
        if (running.get()) {
            cancel()
            XLog.w(TAG, "Task was running during config update, cancelled")
        }
        executor?.shutdownNow()
        initialize(config)
        XLog.i(TAG, "Agent config updated, new model: ${config.modelName}")
    }

    override fun executeTask(userPrompt: String, callback: AgentCallback) {
        executeTask(userPrompt, null, callback)
    }

    override fun executeTask(userPrompt: String, userImageBase64: String?, callback: AgentCallback) {
        if (running.get()) {
            callback.onError(0, IllegalStateException("Agent is already running a task"), 0)
            return
        }

        running.set(true)
        cancelled.set(false)

        executor?.submit {
            try {
                runAgentLoop(userPrompt, userImageBase64, callback)
            } catch (e: Exception) {
                XLog.e(TAG, "Agent execution error", e)
                callback.onError(0, e, 0)
            } finally {
                running.set(false)
            }
        }
    }

    // ==================== 环境预检 ====================

    private fun preCheck(): String? {
        // 本地对话不需要无障碍服务（纯 LLM 文本/图像分析）
        // 其他渠道（微信、飞书等自动化操作）才需要
        return null
    }

    // ==================== 设备上下文 ====================

    private fun buildDeviceContext(): String {
        val app = ClawApplication.instance
        val sb = StringBuilder()
        sb.append("\n\n## 设备信息\n")
        sb.append("- 品牌: ").append(Build.BRAND).append("\n")
        sb.append("- 型号: ").append(Build.MODEL).append("\n")
        sb.append("- Android 版本: ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")

        try {
            val wm = app
                .getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            sb.append("- 屏幕分辨率: ").append(dm.widthPixels).append("x").append(dm.heightPixels).append("\n")
        } catch (e: Exception) {
            XLog.w(TAG, "Failed to get display metrics", e)
        }

        sb.append("- 已注册工具数: ").append(ToolRegistry.getAllTools().size).append("\n")

        val appName = try {
            val appInfo = app.packageManager.getApplicationInfo(app.packageName, 0)
            app.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) { "CoPaw" }
        sb.append("\n## 本应用信息\n")
        sb.append("- 应用名: ").append(appName).append("\n")
        sb.append("- 包名: ").append(app.packageName).append("\n")
        sb.append("- 当用户提到'自己/本应用/这个应用'时，指的就是上述应用\n")

        return sb.toString()
    }

    // ==================== LLM 调用（带重试） ====================

    private fun chatWithRetry(messages: List<ChatMessage>, callback: AgentCallback, iteration: Int): LlmResponse {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_API_RETRIES) {
            if (cancelled.get()) throw RuntimeException(ClawApplication.instance.getString(R.string.agent_task_cancelled))
            try {
                return if (config.streaming) {
                    val textBuilder = StringBuilder()
                    llmClient.chatStreaming(messages, toolSpecs, object : StreamingListener {
                        override fun onPartialText(token: String) {
                            textBuilder.append(token)
                            callback.onContent(iteration, token)
                        }
                        override fun onComplete(response: LlmResponse) {}
                        override fun onError(error: Throwable) {}
                    })
                } else {
                    llmClient.chat(messages, toolSpecs)
                }
            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: ""
                // Token 耗尽或认证失败不重试
                if (msg.contains("401") || msg.contains("403") || msg.contains("insufficient")) {
                    throw e
                }
                val delay = (Math.pow(2.0, attempt.toDouble()) * 1000).toLong()
                XLog.w(TAG, "LLM API call failed (attempt ${attempt + 1}/$MAX_API_RETRIES), retrying in ${delay}ms: $msg")
                try {
                    Thread.sleep(delay)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
        throw lastException!!
    }

    // ==================== 死循环检测 ====================

    private data class RoundFingerprint(val screenHash: Int, val toolCall: String)

    private fun isStuckInLoop(history: LinkedList<RoundFingerprint>): Boolean {
        if (history.size < LOOP_DETECT_WINDOW) return false
        val first = history.first()
        return history.all { it == first }
    }

    // ==================== 上下文压缩 ====================

    /** 保护区：最近 N 轮完整保留 */
    private val KEEP_RECENT_ROUNDS = 3

    /** 大输出观察类工具 → 压缩后占位符 */
    private val OBSERVATION_PLACEHOLDERS = mapOf(
        "get_screen_info" to "[屏幕信息已省略]",
        "take_screenshot" to "[截图结果已省略]",
        "find_node_info" to "[节点查找结果已省略]",
        "get_installed_apps" to "[应用列表已省略]",
        "scroll_to_find" to "[滚动查找结果已省略]"
    )

    /**
     * 发送前压缩历史消息，节省 input token：
     * - get_screen_info：全局只保留最新一条完整结果
     * - 截图：保留最近 MAX_SCREENSHOT_HISTORY 张（Feature 2: Sliding Screenshot Window）
     * - 保护区（最近 KEEP_RECENT_ROUNDS 轮）：完整保留
     * - 保护区外：AI thinking 不动，tool result 压缩为一行摘要
     */
    private fun getUserMessageText(msg: UserMessage): String {
        return msg.contents()?.filter { it is TextContent }?.joinToString("") { (it as TextContent).text() } ?: ""
    }

    private fun compressHistoryForSend(messages: MutableList<ChatMessage>) {
        val charsBefore = messages.sumOf { msg ->
            when (msg) {
                is AiMessage -> (msg.text()?.length ?: 0) + (msg.toolExecutionRequests()?.sumOf { req -> req.arguments()?.length ?: 0 } ?: 0)
                is ToolExecutionResultMessage -> msg.text().length
                is UserMessage -> {
                    val textLen = getUserMessageText(msg).length
                    val imageLen = msg.contents()?.filter { c -> c is ImageContent }?.size ?: 0
                    textLen + imageLen * 1000
                }
                is SystemMessage -> msg.text().length
                else -> 0
            }
        }
        val msgCountBefore = messages.size

        // 0. 清理历史截图：保留最近 MAX_SCREENSHOT_HISTORY 张截图（Feature 2: Sliding Screenshot Window）
        val imageIndices = mutableListOf<Int>()
        for (i in messages.indices) {
            val msg = messages[i]
            if (msg is UserMessage && msg.contents()?.any { it is ImageContent } == true) {
                imageIndices.add(i)
            }
        }
        val keepFrom = maxOf(0, imageIndices.size - MAX_SCREENSHOT_HISTORY)
        for (count in 0 until imageIndices.size) {
            val i = imageIndices[count]
            if (count >= keepFrom) continue // 在保留窗口内
            val msg = messages[i]
            if (msg is UserMessage && msg.contents()?.any { it is ImageContent } == true) {
                val textOnlyContents = msg.contents()?.filter { it !is ImageContent }
                if (textOnlyContents != null && textOnlyContents.isNotEmpty()) {
                    messages[i] = UserMessage.from(textOnlyContents)
                } else {
                    val text = getUserMessageText(msg)
                    if (text.contains("[视觉感知]")) {
                        messages[i] = UserMessage.from("[视觉感知] 截图已省略，请回忆之前的屏幕内容或重新调用观察工具。")
                    }
                }
            }
        }

        // 1. get_screen_info 特殊处理：无视分级，全局只保留最新一条完整结果
        val screenPlaceholder = OBSERVATION_PLACEHOLDERS["get_screen_info"]!!
        val lastScreenIdx = messages.indexOfLast {
            it is ToolExecutionResultMessage && it.toolName() == "get_screen_info"
        }
        for (i in messages.indices) {
            val msg = messages[i]
            if (msg is ToolExecutionResultMessage
                && msg.toolName() == "get_screen_info"
                && i != lastScreenIdx
                && msg.text() != screenPlaceholder
            ) {
                messages[i] = ToolExecutionResultMessage.from(msg.id(), msg.toolName(), screenPlaceholder)
            }
        }

        // 1. 找出所有 AiMessage 的索引，每个代表一轮
        val aiIndices = messages.indices.filter { messages[it] is AiMessage }
        if (aiIndices.size <= KEEP_RECENT_ROUNDS) return

        val totalRounds = aiIndices.size

        for (roundIdx in aiIndices.indices) {
            val roundFromEnd = totalRounds - roundIdx
            if (roundFromEnd <= KEEP_RECENT_ROUNDS) break // 保护区

            val aiIndex = aiIndices[roundIdx]

            // 收集本轮的 ToolExecutionResultMessage 索引
            var j = aiIndex + 1
            while (j < messages.size && messages[j] is ToolExecutionResultMessage) {
                compressToolResultMessage(messages, j)
                j++
            }
        }

        // 压缩后统计
        val charsAfter = messages.sumOf { msg ->
            when (msg) {
                is AiMessage -> (msg.text()?.length ?: 0) + (msg.toolExecutionRequests()?.sumOf { it.arguments()?.length ?: 0 } ?: 0)
                is ToolExecutionResultMessage -> msg.text().length
                is UserMessage -> getUserMessageText(msg).length
                is SystemMessage -> msg.text().length
                else -> 0
            }
        }
        val saved = charsBefore - charsAfter
        if (saved > 0) {
            XLog.i(TAG, "上下文压缩: ${charsBefore}→${charsAfter}字符, 节省${saved}字符(${saved * 100 / charsBefore}%), 轮数=${aiIndices.size}")
        }
    }

    /** 压缩 Tool Result：观察类工具用占位符，其他工具截取摘要 */
    private fun compressToolResultMessage(messages: MutableList<ChatMessage>, index: Int) {
        val msg = messages[index] as ToolExecutionResultMessage
        val text = msg.text()
        if (text.length <= 100) return // 已足够简短，无需压缩

        val placeholder = OBSERVATION_PLACEHOLDERS[msg.toolName()]
        if (placeholder != null) {
            messages[index] = ToolExecutionResultMessage.from(msg.id(), msg.toolName(), placeholder)
            return
        }

        // 其他工具：解析 JSON 提取摘要
        val compressed = summarizeToolResult(text)
        messages[index] = ToolExecutionResultMessage.from(msg.id(), msg.toolName(), compressed)
    }

    /** 将 ToolResult JSON 压缩为一行摘要 */
    private fun summarizeToolResult(resultJson: String): String {
        return try {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = GSON.fromJson(resultJson, mapType)
            val isSuccess = map["isSuccess"] as? Boolean ?: false
            if (isSuccess) {
                val data = map["data"]?.toString() ?: "ok"
                "✓ " + if (data.length > 80) data.take(80) + "..." else data
            } else {
                val error = map["error"]?.toString() ?: "failed"
                "✗ " + if (error.length > 80) error.take(80) + "..." else error
            }
        } catch (_: Exception) {
            if (resultJson.length > 80) resultJson.take(80) + "..." else resultJson
        }
    }

    /**
     * 将操作记录格式化为简短的字符串（Feature 4: Action History Injection）
     */
    private fun formatActionRecord(toolName: String, params: Map<String, Any>): String {
        val paramStr = params.entries
            .filter { it.key != "wait_after" }
            .take(3)
            .joinToString(",") { "${it.key}=${it.value}" }
        return if (paramStr.isNotEmpty()) "$toolName($paramStr)" else toolName
    }

    // ==================== 主执行循环 ====================

    private fun runAgentLoop(userPrompt: String, callback: AgentCallback) {
        runAgentLoop(userPrompt, null, callback)
    }

    private fun runAgentLoop(userPrompt: String, userImageBase64: String?, callback: AgentCallback) {
        // 环境预检
        preCheck()?.let {
            callback.onError(0, RuntimeException(it), 0)
            return
        }

        // 构建 System Prompt（原始 + 设备上下文 + 本地大脑 + 技能匹配）
        var fullSystemPrompt = config.systemPrompt + buildDeviceContext()
        try {
            val integration = FeatureIntegrationManager.getInstance(ClawApplication.instance)
            fullSystemPrompt = integration.buildEnhancedSystemPrompt(userPrompt, fullSystemPrompt)
        } catch (_: Exception) {
            // 集成模块未就绪时忽略
        }

        val messages = mutableListOf<ChatMessage>()
        messages.add(SystemMessage.from(fullSystemPrompt))
        // 如果用户附带了图片，使用多模态 UserMessage（TextContent + ImageContent）
        if (userImageBase64 != null) {
            messages.add(createUserMessageWithImage(userPrompt, userImageBase64))
        } else {
            messages.add(UserMessage.from(userPrompt))
        }

        var iterations = 0
        var totalTokens = 0
        val maxIterations = config.maxIterations
        val loopHistory = LinkedList<RoundFingerprint>()
        var lastScreenHash = 0

        // Feature 4: Action History — 最近执行的操作列表
        val actionHistory = LinkedList<String>()

        // Feature 3: Stale Action Detection — 屏幕无变化计数
        var staleActionCount = 0
        var preActionScreenHash: Int? = null

        // Feature 6: JSON Lines Event Logger
        val eventLogger = AgentEventLogger(ClawApplication.instance.cacheDir)
        try {
            eventLogger.start()
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to start event logger", e)
        }

        try {
            while (iterations < maxIterations && !cancelled.get()) {
                iterations++
                callback.onLoopStart(iterations)
                eventLogger.logLoopStart(iterations)

                // 发送前分级压缩历史消息，节省 token
                compressHistoryForSend(messages)

                // Feature 4: Action History Injection — 在 LLM 调用前注入最近操作历史
                if (actionHistory.isNotEmpty()) {
                    val historyText = actionHistory.joinToString("\n")
                    val historyMessage = UserMessage.from(
                        "## Recent Actions（最近操作记录）\n" +
                        "以下是你最近执行的操作，请参考避免重复：\n" +
                        "$historyText\n" +
                        "请根据这些记录避免重复已执行的操作。"
                    )
                    messages.add(historyMessage)
                    eventLogger.logActionHistory(iterations, actionHistory.toList())
                }

                // Feature 6: Log LLM request
                eventLogger.logLlmRequest(iterations, messages.size)

                // LLM 调用（带重试）
                val llmResponse: LlmResponse
                try {
                    llmResponse = chatWithRetry(messages, callback, iterations)
                } catch (e: Exception) {
                    XLog.e(TAG, "LLM API call failed after retries", e)
                    eventLogger.logError(iterations, "LLM API call failed: ${e.message}")
                    callback.onError(iterations, RuntimeException(ClawApplication.instance.getString(R.string.agent_api_call_failed, e.message)), totalTokens)
                    return
                }

                // 累加 token 用量
                val roundTokens = llmResponse.tokenUsage?.totalTokenCount() ?: 0
                totalTokens += roundTokens

                // Feature 6: Log LLM response
                val toolCallNames = llmResponse.toolExecutionRequests?.map { it.name() ?: "unknown" } ?: emptyList()
                eventLogger.logLlmResponse(iterations, toolCallNames, roundTokens)

                // 将 AI 消息添加到历史（需要构造 AiMessage）
                val aiMessage = if (llmResponse.hasToolExecutionRequests()) {
                    if (llmResponse.text.isNullOrEmpty()) {
                        AiMessage.from(llmResponse.toolExecutionRequests)
                    } else {
                        AiMessage.from(llmResponse.text, llmResponse.toolExecutionRequests)
                    }
                } else {
                    AiMessage.from(llmResponse.text ?: "")
                }
                messages.add(aiMessage)

                // 非流式模式下推送思考内容
                if (!config.streaming && !llmResponse.text.isNullOrEmpty()) {
                    callback.onContent(iterations, llmResponse.text)
                }

                // 如果没有工具调用，Agent 认为完成了
                if (!llmResponse.hasToolExecutionRequests()) {
                    eventLogger.logComplete(iterations, totalTokens, llmResponse.text ?: "Task completed")
                    callback.onComplete(iterations, llmResponse.text ?: ClawApplication.instance.getString(R.string.agent_task_completed), totalTokens)
                    return
                }

                // 执行工具调用
                for (toolRequest in llmResponse.toolExecutionRequests) {
                    if (cancelled.get()) {
                        eventLogger.logComplete(iterations, totalTokens, "Task cancelled")
                        callback.onComplete(iterations, ClawApplication.instance.getString(R.string.agent_task_cancel), totalTokens)
                        return
                    }

                    val toolName = toolRequest.name() ?: ""
                    val displayName = ToolRegistry.getInstance().getDisplayName(toolName)
                    val toolArgs = toolRequest.arguments() ?: "{}"
                    callback.onToolCall(iterations, toolName, displayName, toolArgs)
                    eventLogger.logToolCall(iterations, toolName, toolArgs)

                    // 解析参数
                    val mapType = object : TypeToken<Map<String, Any>>() {}.type
                    var params: Map<String, Any>? = try {
                        GSON.fromJson(toolArgs, mapType)
                    } catch (e: Exception) {
                        HashMap()
                    }
                    if (params == null) params = HashMap()

                    // Feature 3: Action Verification — 对 tap/swipe/long_press 先截图记录当前状态
                    if (ACTION_TOOLS.contains(toolName)) {
                        val preBitmap = captureScreenBitmap()
                        if (preBitmap != null) {
                            preActionScreenHash = computeScreenHash(preBitmap)
                            preBitmap.recycle()
                        }
                    }

                    val result = ToolRegistry.getInstance().executeTool(toolName, params)
                    val paramsString = if (params.isEmpty()) "" else params.toString()
                    callback.onToolResult(iterations, toolName, displayName, paramsString, result)
                    eventLogger.logToolResult(iterations, toolName, result.isSuccess)

                    // Feature 10: call_user tool — 当 LLM 请求帮助时，暂停并通知用户
                    if (toolName == "call_user" && result.isSuccess) {
                        val reason = result.data ?: "No reason provided"
                        XLog.i(TAG, "LLM called call_user: $reason")
                        eventLogger.logComplete(iterations, totalTokens, "call_user: $reason")
                        callback.onCallUser(iterations, reason, totalTokens)
                        return
                    }

                    // 检测到系统弹窗阻塞 → 截图通知用户并结束任务
                    if (!result.isSuccess && result.error == GetScreenInfoTool.SYSTEM_DIALOG_BLOCKED) {
                        XLog.w(TAG, "System dialog blocked, notifying user and stopping task")
                        callback.onSystemDialogBlocked(iterations, totalTokens)
                        return
                    }

                    // finish 工具 → 任务完成
                    if (toolName == "finish" && result.isSuccess) {
                        val finishData = result.data
                        eventLogger.logComplete(iterations, totalTokens, finishData ?: "Task completed")
                        callback.onComplete(iterations, finishData ?: ClawApplication.instance.getString(R.string.agent_task_completed), totalTokens)
                        return
                    }

                    // Feature 4: 记录到 Action History
                    val actionRecord = formatActionRecord(toolName, params)
                    actionHistory.addLast(actionRecord)
                    if (actionHistory.size > MAX_ACTION_HISTORY) {
                        actionHistory.removeFirst()
                    }

                    // 记录指纹用于死循环检测
                    if (toolName == "get_screen_info" && result.isSuccess && result.data != null) {
                        lastScreenHash = result.data.hashCode()
                    } else if (toolName.isNotEmpty() && toolName != "get_screen_info") {
                        loopHistory.addLast(RoundFingerprint(lastScreenHash, "$toolName:$toolArgs"))
                        if (loopHistory.size > LOOP_DETECT_WINDOW) {
                            loopHistory.removeFirst()
                        }
                    }

                    // 添加工具结果到消息
                    val resultJson = GSON.toJson(result)
                    messages.add(ToolExecutionResultMessage.from(toolRequest, resultJson))
                    XLog.d(TAG, "displayName:$displayName toolName:$toolName")

                    // Feature 3: Action Verification — 对 tap/swipe/long_press 执行后验证屏幕是否变化
                    if (ACTION_TOOLS.contains(toolName) && result.isSuccess && preActionScreenHash != null) {
                        try {
                            val postBitmap = captureScreenBitmap()
                            if (postBitmap != null) {
                                val postHash = computeScreenHash(postBitmap)
                                postBitmap.recycle()
                                if (postHash == preActionScreenHash) {
                                    staleActionCount++
                                    XLog.w(TAG, "Action '$toolName' did not change screen (stale count: $staleActionCount)")
                                    eventLogger.logErrorRecovery(iterations, "stale_action", staleActionCount)
                                    if (staleActionCount >= 2) {
                                        staleActionCount = 0
                                        messages.add(
                                            UserMessage.from(
                                                "[系统警告] 你最近连续 $staleActionCount 次操作后屏幕没有任何变化。" +
                                                "这说明你的操作可能没有生效。请重新观察屏幕，确认目标元素位置是否正确。" +
                                                "如果页面正在加载，请使用 wait 等待后再检查。" +
                                                "如果确实无法操作，请使用 call_user 工具请求用户帮助。"
                                            )
                                        )
                                    }
                                } else {
                                    staleActionCount = 0
                                }
                            }
                        } catch (e: Exception) {
                            XLog.e(TAG, "Action verification failed", e)
                        }
                        preActionScreenHash = null
                    }

                    // 观察类工具执行后自动截图并注入 LLM（视觉感知）
                    if (OBSERVATION_TOOLS.contains(toolName) && result.isSuccess) {
                        val (bitmap, screenshotBase64) = captureScreenBitmapAndBase64()
                        if (screenshotBase64 != null) {
                            eventLogger.logScreenshot(iterations,
                                if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "unknown",
                                screenshotBase64.length
                            )
                            val imageMessage = createUserMessageWithImage(
                                "[视觉感知] 屏幕截图已自动采集，请结合截图内容进行分析。",
                                screenshotBase64
                            )
                            messages.add(imageMessage)
                            XLog.d(TAG, "Screenshot injected after $toolName")
                        }
                        bitmap?.recycle()
                    }
                }

                // 死循环检测
                if (isStuckInLoop(loopHistory)) {
                    XLog.w(TAG, "Dead loop detected at iteration $iterations")
                    eventLogger.logErrorRecovery(iterations, "dead_loop")
                    messages.add(
                        UserMessage.from(
                            "[系统提示] 检测到你连续多轮执行了相同的操作且屏幕没有变化，你可能陷入了死循环。" +
                            "请尝试完全不同的方法：按 system_key(key=\"back\") 回退、滑动页面寻找目标、或重新打开 App。" +
                            "如果确实无法完成任务，请调用 call_user 请求用户帮助，或调用 finish 说明原因。"
                        )
                    )
                    loopHistory.clear()
                }
                XLog.d(TAG, "轮数:$iterations all=$totalTokens 本轮=${llmResponse.tokenUsage?.totalTokenCount()}")
            }

            if (cancelled.get()) {
                eventLogger.logComplete(iterations, totalTokens, "Task cancelled")
                callback.onComplete(iterations, ClawApplication.instance.getString(R.string.agent_task_cancel), totalTokens)
            } else {
                eventLogger.logError(iterations, "Max iterations reached ($maxIterations)")
                callback.onError(iterations, RuntimeException(ClawApplication.instance.getString(R.string.agent_max_iterations, maxIterations)), totalTokens)
            }
        } finally {
            eventLogger.close()
        }
    }

    override fun cancel() {
        cancelled.set(true)
    }

    override fun shutdown() {
        cancel()
        executor?.shutdownNow()
    }

    override fun isRunning(): Boolean = running.get()
}
