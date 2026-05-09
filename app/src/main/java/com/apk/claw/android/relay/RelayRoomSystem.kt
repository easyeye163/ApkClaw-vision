package com.apk.claw.android.relay

import android.content.Context
import android.util.Log
import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Relay 房间系统 - 远程控制与设备配对
 * 
 * 参考 LobsterHUD Pro 的"Relay房间号"功能，支持多设备间通过房间号建立连接，
 * 实现远程消息中继和跨设备控制。
 * 
 * 核心能力：
 * - 房间创建/加入/退出
 * - 房间号（6位数字）快速配对
 * - WebSocket 消息中继（通过长轮询模拟）
 * - 房间内消息广播
 * - 设备在线状态管理
 * - 房间管理员权限
 * - 消息加密传输（可选）
 * - HTTP API 中继接口
 */
class RelayRoomSystem private constructor(private val context: Context) {

    companion object {
        private const val TAG = "RelayRoom"
        private const val CONFIG_KEY = "relay_room_config"
        private const val DEVICES_KEY = "relay_known_devices"

        @Volatile
        private var instance: RelayRoomSystem? = null

        fun getInstance(context: Context): RelayRoomSystem {
            return instance ?: synchronized(this) {
                instance ?: RelayRoomSystem(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val executor = Executors.newCachedThreadPool()
    private val rooms = ConcurrentHashMap<String, Room>()
    private val httpServer: RelayHttpServer?
    private val listeners = CopyOnWriteArrayList<RelayListener>()

    interface RelayListener {
        fun onRoomCreated(room: Room) {}
        fun onRoomJoined(room: Room, device: DeviceInfo) {}
        fun onDeviceLeft(room: Room, deviceId: String) {}
        fun onMessageReceived(room: Room, fromDevice: DeviceInfo, message: RelayMessage) {}
        fun onRoomDestroyed(roomId: String) {}
    }

    // ==================== 数据模型 ====================

    data class Room(
        val roomId: String,                    // 6位数字房间号
        val roomName: String = "",
        val password: String? = null,          // 可选密码
        val createdBy: String,                 // 创建者设备ID
        val createdAt: Long = System.currentTimeMillis(),
        val maxDevices: Int = 10,
        val autoDestroyMinutes: Int = 60,      // 无活动自动销毁时间
        val devices: MutableList<DeviceInfo> = mutableListOf(),
        val messages: MutableList<RelayMessage> = mutableListOf(),
        var isActive: Boolean = true,
        var lastActivityAt: Long = System.currentTimeMillis()
    )

    data class DeviceInfo(
        val deviceId: String = UUID.randomUUID().toString().take(8),
        val deviceName: String,
        val platform: String = "android",      // android / ios / web / desktop
        val connectedAt: Long = System.currentTimeMillis(),
        val lastHeartbeatAt: Long = System.currentTimeMillis(),
        val isAdmin: Boolean = false,
        val metadata: Map<String, String> = emptyMap()
    )

    data class RelayMessage(
        val messageId: String = UUID.randomUUID().toString(),
        val fromDeviceId: String,
        val toDeviceId: String? = null,        // null 表示广播
        val type: MessageType = MessageType.TEXT,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        var delivered: Boolean = false,
        var deliveredTo: MutableList<String> = mutableListOf()
    )

    enum class MessageType {
        TEXT,           // 文本消息
        COMMAND,        // 控制命令（转发给 Agent）
        HEARTBEAT,      // 心跳
        SYNC_REQUEST,   // 同步请求
        SYNC_RESPONSE,  // 同步响应
        ROOM_INFO       // 房间信息
    }

    data class RelayConfig(
        val myDeviceId: String = UUID.randomUUID().toString().take(8),
        val myDeviceName: String = "Android-${android.os.Build.MODEL}",
        val httpPort: Int = 9528,
        val heartbeatIntervalSec: Int = 30,
        val autoReconnect: Boolean = true
    )

    var config: RelayConfig

    init {
        config = KVUtils.getString(CONFIG_KEY)?.let {
            try { gson.fromJson(it, RelayConfig::class.java) } catch (_: Exception) { null }
        } ?: RelayConfig()

        httpServer = try {
            RelayHttpServer(config.httpPort, this).apply { start() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Relay HTTP server", e)
            null
        }

        Log.d(TAG, "Relay system initialized, device: ${config.myDeviceName} (${config.myDeviceId})")
    }

    // ==================== 房间管理 ====================

    /**
     * 创建房间
     */
    fun createRoom(
        name: String = "",
        password: String? = null,
        maxDevices: Int = 10,
        autoDestroyMinutes: Int = 60
    ): Room {
        val roomId = generateRoomId()
        val myDevice = DeviceInfo(
            deviceId = config.myDeviceId,
            deviceName = config.myDeviceName,
            isAdmin = true
        )

        val room = Room(
            roomId = roomId,
            roomName = name.ifEmpty { "房间 $roomId" },
            password = password,
            createdBy = config.myDeviceId,
            maxDevices = maxDevices,
            autoDestroyMinutes = autoDestroyMinutes,
            devices = mutableListOf(myDevice)
        )
        rooms[roomId] = room

        listeners.forEach { it.onRoomCreated(room) }
        Log.d(TAG, "Room created: $roomId (${room.roomName})")
        return room
    }

    /**
     * 加入房间
     */
    fun joinRoom(roomId: String, password: String? = null, deviceName: String = config.myDeviceName): Room? {
        val room = rooms[roomId] ?: return null

        // 验证密码
        if (room.password != null && room.password != password) {
            Log.w(TAG, "Join room $roomId failed: wrong password")
            return null
        }

        // 检查人数
        if (room.devices.size >= room.maxDevices) {
            Log.w(TAG, "Join room $roomId failed: room full")
            return null
        }

        // 检查是否已在房间中
        if (room.devices.any { it.deviceId == config.myDeviceId }) {
            return room
        }

        val device = DeviceInfo(
            deviceId = config.myDeviceId,
            deviceName = deviceName
        )
        room.devices.add(device)
        room.lastActivityAt = System.currentTimeMillis()

        // 广播消息
        broadcastMessage(room, null, MessageType.ROOM_INFO, "设备 ${device.deviceName} 加入了房间")
        listeners.forEach { it.onRoomJoined(room, device) }
        Log.d(TAG, "Joined room: $roomId as ${device.deviceName}")
        return room
    }

    /**
     * 离开房间
     */
    fun leaveRoom(roomId: String) {
        val room = rooms[roomId] ?: return
        room.devices.removeAll { it.deviceId == config.myDeviceId }
        room.lastActivityAt = System.currentTimeMillis()

        if (room.devices.isEmpty()) {
            destroyRoom(roomId)
        } else {
            broadcastMessage(room, null, MessageType.ROOM_INFO, "设备 ${config.myDeviceName} 离开了房间")
        }
    }

    /**
     * 销毁房间
     */
    fun destroyRoom(roomId: String) {
        val room = rooms.remove(roomId) ?: return
        room.isActive = false
        listeners.forEach { it.onRoomDestroyed(roomId) }
        Log.d(TAG, "Room destroyed: $roomId")
    }

    // ==================== 消息中继 ====================

    /**
     * 发送消息到房间
     */
    fun sendMessage(roomId: String, content: String, type: MessageType = MessageType.TEXT, toDeviceId: String? = null): RelayMessage? {
        val room = rooms[roomId] ?: return null
        return sendMessage(room, content, type, toDeviceId)
    }

    /**
     * 发送控制命令（转发给 Agent 执行）
     */
    fun sendCommand(roomId: String, command: String, toDeviceId: String? = null): RelayMessage? {
        return sendMessage(roomId, command, MessageType.COMMAND, toDeviceId)
    }

    /**
     * 获取房间的新消息（长轮询接口）
     */
    fun pollMessages(roomId: String, sinceTimestamp: Long = 0, timeoutMs: Long = 30000): List<RelayMessage> {
        val room = rooms[roomId] ?: return emptyList()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val newMessages = room.messages.filter {
                it.timestamp > sinceTimestamp &&
                (it.toDeviceId == null || it.toDeviceId == config.myDeviceId) &&
                config.myDeviceId !in it.deliveredTo
            }
            if (newMessages.isNotEmpty()) {
                for (msg in newMessages) {
                    msg.deliveredTo.add(config.myDeviceId)
                }
                return newMessages
            }
            try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
        }
        return emptyList()
    }

    // ==================== 查询接口 ====================

    fun getRoom(roomId: String): Room? = rooms[roomId]
    fun getActiveRooms(): List<Room> = rooms.values.filter { it.isActive }
    fun getMyRooms(): List<Room> = rooms.values.filter {
        it.isActive && it.devices.any { d -> d.deviceId == config.myDeviceId }
    }
    fun getCurrentRoom(): Room? = getMyRooms().firstOrNull()

    fun isRoomExists(roomId: String): Boolean = rooms.containsKey(roomId)
    fun getRoomDeviceCount(roomId: String): Int = rooms[roomId]?.devices?.size ?: 0

    // ==================== 内部方法 ====================

    private fun sendMessage(room: Room, content: String, type: MessageType, toDeviceId: String?): RelayMessage {
        val message = RelayMessage(
            fromDeviceId = config.myDeviceId,
            toDeviceId = toDeviceId,
            type = type,
            content = content
        )
        room.messages.add(message)
        room.lastActivityAt = System.currentTimeMillis()

        // 限制消息历史长度
        if (room.messages.size > 1000) {
            repeat(room.messages.size - 1000) { room.messages.removeAt(0) }
        }

        // 通知监听器
        room.devices.find { it.deviceId == config.myDeviceId }?.let { device ->
            listeners.forEach { it.onMessageReceived(room, device, message) }
        }
        return message
    }

    private fun broadcastMessage(room: Room, fromDeviceId: String?, type: MessageType, content: String) {
        val message = RelayMessage(
            fromDeviceId = fromDeviceId ?: config.myDeviceId,
            toDeviceId = null,
            type = type,
            content = content
        )
        room.messages.add(message)
        room.lastActivityAt = System.currentTimeMillis()
    }

    private fun generateRoomId(): String {
        var roomId: String
        do {
            roomId = (100000..999999).random().toString()
        } while (rooms.containsKey(roomId))
        return roomId
    }

    fun addListener(listener: RelayListener) { listeners.add(listener) }
    fun removeListener(listener: RelayListener) { listeners.remove(listener) }

    /**
     * HTTP API 服务器
     */
    class RelayHttpServer(port: Int, private val relay: RelayRoomSystem) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val params = session.parms

            return when {
                uri == "/api/room/create" -> {
                    val name = params["name"] ?: ""
                    val password = params["password"]
                    val room = relay.createRoom(name = name, password = password)
                    jsonOk(mapOf("roomId" to room.roomId, "roomName" to room.roomName))
                }
                uri == "/api/room/join" -> {
                    val roomId = params["roomId"] ?: return jsonErr("roomId required")
                    val password = params["password"]
                    val room = relay.joinRoom(roomId, password)
                        ?: return jsonErr("Failed to join room")
                    jsonOk(mapOf("roomId" to room.roomId, "deviceCount" to room.devices.size))
                }
                uri == "/api/room/leave" -> {
                    val roomId = params["roomId"] ?: return jsonErr("roomId required")
                    relay.leaveRoom(roomId)
                    jsonOk(mapOf("status" to "left"))
                }
                uri == "/api/room/info" -> {
                    val roomId = params["roomId"] ?: return jsonErr("roomId required")
                    val room = relay.getRoom(roomId) ?: return jsonErr("Room not found")
                    jsonOk(mapOf(
                        "roomId" to room.roomId,
                        "roomName" to room.roomName,
                        "deviceCount" to room.devices.size,
                        "devices" to room.devices.map { mapOf("id" to it.deviceId, "name" to it.deviceName) },
                        "messageCount" to room.messages.size,
                        "isActive" to room.isActive
                    ))
                }
                uri == "/api/message/send" -> {
                    val roomId = params["roomId"] ?: return jsonErr("roomId required")
                    val content = params["content"] ?: return jsonErr("content required")
                    val typeStr = params["type"] ?: "TEXT"
                    val toDeviceId = params["toDeviceId"]
                    val type = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.TEXT }
                    val msg = relay.sendMessage(roomId, content, type, toDeviceId)
                        ?: return jsonErr("Failed to send message")
                    jsonOk(mapOf("messageId" to msg.messageId))
                }
                uri == "/api/message/poll" -> {
                    val roomId = params["roomId"] ?: return jsonErr("roomId required")
                    val since = (params["since"]?.toLongOrNull() ?: 0)
                    val messages = relay.pollMessages(roomId, since)
                    jsonOk(messages.map { mapOf(
                        "id" to it.messageId,
                        "from" to it.fromDeviceId,
                        "to" to it.toDeviceId,
                        "type" to it.type.name,
                        "content" to it.content,
                        "timestamp" to it.timestamp
                    )})
                }
                else -> jsonErr("Not found", 404)
            }
        }

        private fun jsonOk(data: Any): Response {
            return newFixedLengthResponse(Response.Status.OK, "application/json", Gson().toJson(data))
        }
        private fun jsonErr(msg: String, status: Int = 400): Response {
            val statusEnum = Response.Status.values().find { it.requestStatus == status } ?: Response.Status.BAD_REQUEST
            return newFixedLengthResponse(statusEnum, "application/json", Gson().toJson(mapOf("error" to msg)))
        }
    }

    fun shutdown() {
        httpServer?.stop()
        rooms.clear()
    }
}
