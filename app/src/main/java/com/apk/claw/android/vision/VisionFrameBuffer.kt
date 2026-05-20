package com.apk.claw.android.vision

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 视频帧缓冲区（单例）
 *
 * 使用线程安全的 ConcurrentLinkedDeque 实现环形缓冲区，
 * 存储最近 MAX_FRAMES 帧的 JPEG 数据供监控和对话使用。
 */
object VisionFrameBuffer {

    private const val TAG = "VisionFrameBuffer"
    private const val MAX_FRAMES = 30

    private val buffer = ConcurrentLinkedDeque<FrameEntry>()

    @Volatile
    private var isRunning = false

    @Volatile
    private var totalPushedCount = 0L

    val running: Boolean get() = isRunning
    val totalPushedCountVal: Long get() = totalPushedCount

    /**
     * 单帧数据
     */
    data class FrameEntry(
        val timestampMs: Long,
        val jpegBytes: ByteArray
    ) {
        override fun equals(other: Any?): Boolean = other is FrameEntry && timestampMs == other.timestampMs
        override fun hashCode(): Int = timestampMs.hashCode()
    }

    fun start() {
        isRunning = true
        Log.i(TAG, "VisionFrameBuffer started")
    }

    fun stop() {
        isRunning = false
        clear()
        Log.i(TAG, "VisionFrameBuffer stopped")
    }

    /**
     * 推入一帧 JPEG 数据（自动淘汰旧帧，保持缓冲区大小 ≤ MAX_FRAMES）
     */
    fun offer(jpegBytes: ByteArray) {
        buffer.addLast(FrameEntry(System.currentTimeMillis(), jpegBytes))
        while (buffer.size > MAX_FRAMES) {
            buffer.pollFirst()
        }
        totalPushedCount++
    }

    /**
     * 获取最新一帧
     */
    val latestFrame: FrameEntry?
        get() = buffer.peekLast()

    /**
     * 获取时间上最接近指定时间戳的帧
     */
    fun getFrameClosestTo(targetTsMs: Long): FrameEntry? {
        if (buffer.isEmpty()) return null
        var closest: FrameEntry? = null
        var minDelta = Long.MAX_VALUE
        for (entry in buffer) {
            val delta = kotlin.math.abs(entry.timestampMs - targetTsMs)
            if (delta < minDelta) {
                closest = entry
                minDelta = delta
            }
        }
        return closest
    }

    /**
     * 获取时间上 ≤ 指定时间戳的最新帧
     */
    fun getLatestFrameAtOrBefore(targetTsMs: Long): FrameEntry? {
        var result: FrameEntry? = null
        for (entry in buffer) {
            if (entry.timestampMs <= targetTsMs) {
                result = entry
            }
        }
        return result
    }

    /**
     * 根据用户文本选择合适的帧集合
     *
     * 如果用户文本包含"刚才/过程/动态"等关键词，返回最近 10 帧；
     * 否则仅返回最新 1 帧。
     */
    fun selectFramesForQuery(userText: String): List<FrameEntry> {
        if (buffer.isEmpty()) return emptyList()
        val motionKeywords = listOf("刚才", "过程", "怎么做的", "动态", "变化", "刚才的")
        if (motionKeywords.any { userText.contains(it) }) {
            return buffer.toList().takeLast(10)
        }
        return listOfNotNull(buffer.peekLast())
    }

    fun size(): Int = buffer.size

    fun clear() {
        buffer.clear()
        totalPushedCount = 0L
    }
}
