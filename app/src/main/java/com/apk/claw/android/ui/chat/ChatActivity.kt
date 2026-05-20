package com.apk.claw.android.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.appViewModel
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.channel.Channel
import com.apk.claw.android.floating.FloatingCircleManager
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.ui.skill.SkillManageActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.voice.VoiceInputController
import com.apk.claw.android.widget.CommonToolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import android.widget.Switch
import android.widget.CompoundButton
import com.apk.claw.android.webrtc.DirectWebRTCManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_SKILL_PROMPT = "skill_prompt"
        const val EXTRA_SKILL_NAME = "skill_name"
        const val EXTRA_MATCHED_SKILL_ID = "matched_skill_id"
        const val EXTRA_PUSH_TEXT = "push_text"
        private const val STORAGE_KEY = "chat_history_messages"
        private const val MAX_MESSAGES = 100
        private val gson = Gson()

        fun saveMessages(messages: List<ChatMessage>) {
            try {
                // 只保存最近的消息，排除 isThinking 状态的消息
                val toSave = messages
                    .filter { !it.isThinking }
                    .takeLast(MAX_MESSAGES)
                    .map { msg ->
                        mapOf(
                            "text" to msg.text,
                            "isUser" to msg.isUser,
                            "timestamp" to msg.timestamp,
                            "imageBase64" to (msg.imageData?.let { Base64.getEncoder().encodeToString(it) })
                        )
                    }
                KVUtils.putString(STORAGE_KEY, gson.toJson(toSave))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun loadMessages(): List<ChatMessage> {
            return try {
                val json = KVUtils.getString(STORAGE_KEY)
                if (json.isEmpty()) return emptyList()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = gson.fromJson(json, type) ?: return emptyList()
                list.map { map ->
                    ChatMessage(
                        text = map["text"] as? String ?: "",
                        isUser = map["isUser"] as? Boolean ?: false,
                        timestamp = (map["timestamp"] as? Double)?.toLong() ?: 0L,
                        imageData = (map["imageBase64"] as? String)?.let { Base64.getDecoder().decode(it) }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

        fun clearMessages() {
            KVUtils.putString(STORAGE_KEY, "")
        }
    }

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: android.widget.EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnAttach: ImageView
    private lateinit var btnCamera: ImageView
    private lateinit var ivPreview: ImageView
    private lateinit var btnRemovePreview: ImageView
    private lateinit var btnVoice: ImageView
    private lateinit var switchCloudMode: Switch
    private lateinit var tvConnectionStatus: android.widget.TextView
    private var voiceInputController: VoiceInputController? = null
    private var isListening = false
    private lateinit var adapter: ChatAdapter

    private var selectedImageUri: Uri? = null
    private var selectedImageData: ByteArray? = null

    private lateinit var skillSystem: com.apk.claw.android.skill.SkillSystem

    // Screenshot permission is handled by ScreenshotPermissionActivity

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            ivPreview.setImageURI(uri)
            ivPreview.visibility = View.VISIBLE
            btnRemovePreview.visibility = View.VISIBLE
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val rawBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                selectedImageData = compressImage(rawBitmap)
                rawBitmap?.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Camera capture result launcher */
    private val cameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val photoData: ByteArray? = result.data?.getByteArrayExtra(CameraActivity.EXTRA_PHOTO_DATA)
            if (photoData != null) {
                selectedImageData = photoData
                selectedImageUri = null
                val bitmap = BitmapFactory.decodeByteArray(photoData, 0, photoData.size)
                if (bitmap != null) {
                    ivPreview.setImageBitmap(bitmap)
                    ivPreview.visibility = View.VISIBLE
                    btnRemovePreview.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * 压缩图片：限制最大边长 + JPEG 压缩 + 文件大小上限
     */
    private fun compressImage(bitmap: android.graphics.Bitmap?): ByteArray? {
        if (bitmap == null) return null
        try {
            // 缩放到最大边长 2048px
            val maxDim = 2048
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }

            // JPEG 压缩，逐步降低质量直到 <= 1MB
            var quality = 75
            var bytes: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                bytes = stream.toByteArray()
                quality -= 10
            } while (bytes.size > 1024 * 1024 && quality >= 30)

            if (scaled !== bitmap) scaled.recycle()
            return bytes
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 适配 Android 15+ edge-to-edge: 手动处理 IME insets，将输入框推到键盘上方
        val content = findViewById<ViewGroup>(android.R.id.content)
        val rootView = content?.getChildAt(0) as? ViewGroup
        rootView?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
                val imeHeight = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                v.updatePadding(bottom = imeHeight)
                windowInsets
            }
        }

        skillSystem = FeatureIntegrationManager.getInstance(this).skillSystem

        initViews()
        adapter = ChatAdapter()
        adapter.setMarkwon(Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .build())
        rvMessages.adapter = adapter
        rvMessages.layoutManager = LinearLayoutManager(this)

        loadChatHistory()

        // Handle incoming intents
        handleSkillIntent()
        handleScreenshotIntent()
        handlePushIntent()
        handleVoiceFloatIntent()
    }

    // 通知权限请求 launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "未授予通知权限，将无法收到推送提醒", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        // 云端模式下建立长连接以接收推送
        if (switchCloudMode.isChecked) {
            requestNotificationPermission()
            CloudChatManager.setPushListener(pushListener)
            CloudChatManager.connectForPush()
            startObservingConnectionState()
        }
    }

    override fun onPause() {
        super.onPause()
        persistChatHistory()
    }

    override fun onStop() {
        super.onStop()
        // Activity 不可见时注销推送监听（通知仍由 CloudChatManager 发送）
        CloudChatManager.setPushListener(null)
        stopObservingConnectionState()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        voiceInputController?.destroy()
        voiceInputController = null
        // 清除语音悬浮框回调，避免引用已销毁的Activity
        com.apk.claw.android.floating.voice.VoiceInteractionFloatWindow.onVoiceResultCallback = null
        CloudChatManager.disconnect()
    }

    /**
     * 处理通知栏点击带来的推送消息，显示在聊天界面
     */
    private fun handlePushIntent() {
        val pushText = intent.getStringExtra(EXTRA_PUSH_TEXT)
        if (pushText != null && pushText.isNotEmpty()) {
            adapter.addMessage(ChatMessage(
                text = pushText,
                isUser = false,
                timestamp = System.currentTimeMillis()
            ))
            rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
            persistChatHistory()
            intent.removeExtra(EXTRA_PUSH_TEXT)
        }
    }

    /**
     * 请求通知权限 (Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * 推送消息监听器 —— 收到 push 时在聊天界面显示消息
     */
    private val pushListener = object : CloudChatManager.PushListener {
        override fun onPushMessage(text: String) {
            // 添加推送消息到聊天列表
            adapter.addMessage(ChatMessage(
                text = text,
                isUser = false,
                timestamp = System.currentTimeMillis()
            ))
            rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
            persistChatHistory()
        }
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.chat_title))
            setActionIcon(R.drawable.ic_minimize) {
                finish()
            }
        }

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnAttach = findViewById(R.id.btnAttach)
        btnCamera = findViewById(R.id.btnCamera)
        ivPreview = findViewById(R.id.ivPreview)
        btnRemovePreview = findViewById(R.id.btnRemovePreview)

        btnSend.setOnClickListener { sendMessage() }
        // 回车键发送消息
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
        btnAttach.setOnClickListener { imagePickerLauncher.launch("image/*") }
        btnCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            cameraLauncher.launch(intent)
        }
        // 云端模式开关
        switchCloudMode = findViewById(R.id.switchCloudMode)
        switchCloudMode.isChecked = com.apk.claw.android.utils.KVUtils.isCloudChatEnabled()
        switchCloudMode.setOnCheckedChangeListener { _, isChecked ->
            com.apk.claw.android.utils.KVUtils.setCloudChatEnabled(isChecked)
            updateModeHint()
            if (isChecked) {
                // 开启云端模式：建立长连接 + 请求通知权限
                requestNotificationPermission()
                CloudChatManager.setPushListener(pushListener)
                CloudChatManager.connectForPush()
                startObservingConnectionState()
            } else {
                // 关闭云端模式：断开连接
                CloudChatManager.disconnect()
                stopObservingConnectionState()
                tvConnectionStatus.visibility = android.view.View.GONE
            }
        }
        updateModeHint()
        btnVoice = findViewById(R.id.btnVoice)
        btnVoice.setOnClickListener { toggleVoiceInput() }
        btnRemovePreview.setOnClickListener {
            selectedImageUri = null
            selectedImageData = null
            ivPreview.visibility = View.GONE
            btnRemovePreview.visibility = View.GONE
        }
    }

    /**
     * 更新模式提示（显示当前是本地模式还是云端模式）
     */
    private fun updateModeHint() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        val tvModeHint = findViewById<android.widget.TextView>(R.id.tvModeHint)
        if (switchCloudMode.isChecked) {
            val modeName = CloudChatManager.getModeDisplayName()
            tvModeHint.text = "云端模式 · $modeName"
            tvModeHint.visibility = View.VISIBLE
        } else {
            tvModeHint.text = getString(R.string.chat_mode_local)
            tvModeHint.visibility = View.VISIBLE
        }
    }

    /**
     * Observe WebRTC connection state to show cloud chat connection status.
     */
    private var connectionObserverJob: kotlinx.coroutines.Job? = null

    private fun startObservingConnectionState() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvConnectionStatus.visibility = android.view.View.VISIBLE
        connectionObserverJob = CoroutineScope(Dispatchers.Main).launch {
            DirectWebRTCManager.connectionState.collect { state ->
                    if (!switchCloudMode.isChecked) return@collect
                    when (state) {
                        DirectWebRTCManager.ConnectionState.CONNECTED -> {
                            tvConnectionStatus.text = "\u2705 云端+数字人已连接"
                            tvConnectionStatus.setTextColor(getColor(R.color.colorBrandPrimary))
                        }
                        DirectWebRTCManager.ConnectionState.CONNECTING -> {
                            tvConnectionStatus.text = "\u23F3 连接中..."
                            tvConnectionStatus.setTextColor(getColor(R.color.colorTextHint))
                        }
                        DirectWebRTCManager.ConnectionState.DISCONNECTED -> {
                            tvConnectionStatus.text = "\u26A0 仅云端模式"
                            tvConnectionStatus.setTextColor(getColor(R.color.colorTextHint))
                        }
                        DirectWebRTCManager.ConnectionState.ERROR -> {
                            tvConnectionStatus.text = "\u274C 连接异常"
                            tvConnectionStatus.setTextColor(getColor(R.color.colorErrorPrimary))
                        }
                    }
                }
        }
    }

    private fun stopObservingConnectionState() {
        connectionObserverJob?.cancel()
        connectionObserverJob = null
    }

    // ==================== Voice Input ====================

    private val voicePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            Toast.makeText(this, getString(R.string.voice_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleVoiceInput() {
        // 点击麦克风按钮弹出语音悬浮框（与悬浮菜单的语音悬浮框一致）
        // 使用回调模式：语音识别完成后直接回传文本到当前ChatActivity，避免截屏+重启Activity
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val voiceFloat = com.apk.claw.android.floating.voice.VoiceInteractionFloatWindow
        voiceFloat.onVoiceResultCallback = { text ->
            // 在主线程执行UI操作和发送消息
            runOnUiThread {
                etMessage.setText(text)
                etMessage.setSelection(text.length)
                sendMessage()
            }
        }
        voiceFloat.show(application)
    }

    private fun initVoiceController() {
        voiceInputController?.destroy()
        val controller = VoiceInputController(this)
        controller.listener = object : VoiceInputController.Listener {
            override fun onListeningStarted() {
                isListening = true
                btnVoice.setImageResource(R.drawable.ic_mic_active)
                Toast.makeText(this@ChatActivity, getString(R.string.voice_listening), Toast.LENGTH_SHORT).show()
            }

            override fun onTranscribing() {
                // 录音结束，正在通过 HTTP STT 识别
            }

            override fun onPartialResults(text: String) {
                etMessage.setText(text)
                etMessage.setSelection(text.length)
            }

            override fun onFinalResult(text: String) {
                isListening = false
                btnVoice.setImageResource(R.drawable.ic_mic)
                etMessage.setText(text)
                etMessage.setSelection(text.length)
                sendMessage()
            }

            override fun onError(errorCode: Int, message: String) {
                isListening = false
                btnVoice.setImageResource(R.drawable.ic_mic)
                Toast.makeText(this@ChatActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
        voiceInputController = controller
    }

    private fun startListening() {
        if (isListening) return
        if (voiceInputController == null) initVoiceController()
        voiceInputController?.startListening()
    }

    private fun stopListening() {
        isListening = false
        btnVoice.setImageResource(R.drawable.ic_mic)
        voiceInputController?.stopListening()
    }

    private fun loadChatHistory() {
        val savedMessages = loadMessages()
        if (savedMessages.isEmpty()) {
            // 没有历史记录时显示欢迎消息
            adapter.addMessage(ChatMessage(
                text = getString(R.string.chat_welcome),
                isUser = false,
                timestamp = System.currentTimeMillis()
            ))
        } else {
            // 恢复历史消息
            for (msg in savedMessages) {
                adapter.addMessage(msg)
            }
            rvMessages.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun persistChatHistory() {
        saveMessages(adapter.getMessages())
    }

    private fun handleSkillIntent() {
        val skillPrompt = intent.getStringExtra(EXTRA_SKILL_PROMPT)
        val skillName = intent.getStringExtra(EXTRA_SKILL_NAME)
        val matchedSkillId = intent.getStringExtra(EXTRA_MATCHED_SKILL_ID)

        if (matchedSkillId != null) {
            val skill = skillSystem.getSkill(matchedSkillId)
            if (skill != null) {
                adapter.addMessage(ChatMessage(
                    text = getString(R.string.skill_matched, skill.name),
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                ))
                executeWithSkill(skill)
                return
            }
        }

        if (skillPrompt != null && skillPrompt.isNotEmpty()) {
            etMessage.setText(skillPrompt)
            if (skillName != null) {
                adapter.addMessage(ChatMessage(
                    text = getString(R.string.skill_executing, skillName),
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                ))
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendMessageWithPrompt(skillPrompt)
            }, 300)
        }
    }

    /**
     * Handle screenshot intent from ScreenshotPermissionActivity or VoiceInteractionFloatWindow.
     * Screenshot is already taken and saved; we just show it and auto-send for description.
     * When voice_text is provided, use it as the user prompt instead of the default describe_screenshot.
     */
    private fun handleScreenshotIntent() {
        val screenshotPath = intent.getStringExtra(ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH)
        val voiceText = intent.getStringExtra("voice_text")
        if (screenshotPath != null) {
            handleScreenshotPath(screenshotPath, voiceText)
            intent.removeExtra(ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH)
            intent.removeExtra("voice_text")
        }
    }

    /**
     * Handle voice float intent: voice_text without screenshot (direct text send)
     */
    private var voiceFloatTextHandled = false

    private fun handleVoiceFloatIntent() {
        val screenshotPath = intent.getStringExtra(ScreenshotPermissionActivity.EXTRA_SCREENSHOT_PATH)
        val voiceText = intent.getStringExtra("voice_text")
        if (voiceText != null && voiceText.isNotEmpty() && screenshotPath == null && !voiceFloatTextHandled) {
            voiceFloatTextHandled = true
            // No screenshot, just send the voice text directly
            etMessage.setText(voiceText)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendMessage()
            }, 300)
            intent.removeExtra("voice_text")
        }
    }

    /**
     * Handle a screenshot file path: show preview and auto-send description request
     */
    private fun handleScreenshotPath(screenshotPath: String, customPrompt: String? = null) {
        try {
            val file = File(screenshotPath)
            if (!file.exists()) {
                Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                return
            }

            // Read image data from file
            val bitmap = BitmapFactory.decodeFile(screenshotPath)
            if (bitmap == null) {
                Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                return
            }

            // Compress image (reuse compressImage for consistency)
            val imageData = compressImage(bitmap)
            bitmap.recycle()
            if (imageData == null) {
                Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                return
            }

            // Use custom voice prompt or default screenshot description
            val prompt = customPrompt?.takeIf { it.isNotBlank() } ?: getString(R.string.describe_screenshot)

            // Add image preview message
            adapter.addMessage(ChatMessage(
                text = if (customPrompt != null) "$customPrompt" else getString(R.string.screenshot_saved),
                isUser = true,
                imageData = imageData,
                timestamp = System.currentTimeMillis()
            ))
            rvMessages.smoothScrollToPosition(adapter.itemCount - 1)

            // Auto-send description request with image
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendMessageInternal(prompt, imageData)
            }, 500)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty() && selectedImageData == null) return

        val matchedSkill = skillSystem.matchSkill(text)
        if (matchedSkill != null) {
            showSkillMatchDialog(matchedSkill, text)
            return
        }

        sendMessageInternal(text)
    }

    private fun sendMessageWithPrompt(prompt: String) {
        sendMessageInternal(prompt)
    }

    private fun executeWithSkill(skill: com.apk.claw.android.skill.SkillSystem.Skill) {
        val prompt = skillSystem.buildSkillPrompt(skill, "")
        sendMessageInternal(prompt)
    }

    private fun showSkillMatchDialog(skill: com.apk.claw.android.skill.SkillSystem.Skill, originalText: String) {
        adapter.addMessage(ChatMessage(
            text = getString(R.string.skill_matched, skill.name),
            isUser = false,
            timestamp = System.currentTimeMillis()
        ))

        Toast.makeText(this, getString(R.string.skill_matched, skill.name), Toast.LENGTH_SHORT).show()

        val prompt = skillSystem.buildSkillPrompt(skill, originalText)
        sendMessageInternal(prompt)
    }

    /**
     * Send message with custom image data (used for screenshot)
     */
    private fun sendMessageInternal(text: String, imageData: ByteArray? = null) {
        etMessage.text.clear()

        // CRITICAL FIX: Save imageData before clearing selectedImageData
        val imageDataToSend = imageData ?: selectedImageData

        if (selectedImageData != null) {
            ivPreview.visibility = View.GONE
            btnRemovePreview.visibility = View.GONE
            selectedImageUri = null
            selectedImageData = null
        }

        val userMessage = ChatMessage(
            text = text,
            isUser = true,
            imageData = imageDataToSend,
            timestamp = System.currentTimeMillis()
        )
        adapter.addMessage(userMessage)
        rvMessages.smoothScrollToPosition(adapter.itemCount - 1)

        // 根据云端模式开关决定走云端还是本地
        if (switchCloudMode.isChecked) {
            sendCloudMessage(text)
        } else {
            sendLocalMessage(text, imageDataToSend)
        }
    }

    /**
     * 本地模式：使用 AppViewModel + Agent 执行
     */
    private fun sendLocalMessage(text: String, imageData: ByteArray?) {
        if (appViewModel.isTaskRunning()) {
            adapter.addMessage(ChatMessage(
                text = getString(R.string.chat_task_running),
                isUser = false,
                timestamp = System.currentTimeMillis()
            ))
            return
        }

        val thinkingMessage = ChatMessage(
            text = getString(R.string.chat_thinking),
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isThinking = true
        )
        adapter.addMessage(thinkingMessage)
        rvMessages.smoothScrollToPosition(adapter.itemCount - 1)

        // 用于区分首次进度更新和后续更新
        var firstProgress = true

        appViewModel.startChatTask(text, imageData, object : ChatCallback {
            override fun onProgress(step: String) {
                runOnUiThread {
                    if (firstProgress) {
                        adapter.updateLastMessage(step)
                        firstProgress = false
                    } else {
                        adapter.addMessage(ChatMessage(
                            text = step,
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }

            override fun onComplete(answer: String) {
                runOnUiThread {
                    adapter.addMessage(ChatMessage(
                        text = answer,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    ))
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                    persistChatHistory()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    adapter.addMessage(ChatMessage(
                        text = error,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    ))
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                    persistChatHistory()
                }
            }
        })
    }

    /**
     * 云端模式：通过 CloudChatManager 发送（根据模式走 VoiceLLM 或 OpenClaw）
     */
    private fun sendCloudMessage(text: String) {
        val thinkingMessage = ChatMessage(
            text = getString(R.string.chat_thinking),
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isThinking = true
        )
        adapter.addMessage(thinkingMessage)
        rvMessages.smoothScrollToPosition(adapter.itemCount - 1)

        var firstProgress = true

        CloudChatManager.sendMessage(text, object : ChatCallback {
            override fun onProgress(step: String) {
                runOnUiThread {
                    if (firstProgress) {
                        adapter.updateLastMessage(step)
                        firstProgress = false
                    } else {
                        // 云端模式：更新最后一条消息（累积文本）
                        adapter.updateLastMessage(step)
                    }
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }

            override fun onComplete(answer: String) {
                runOnUiThread {
                    // updateLastMessage 已包含最终文本，无需再添加
                    persistChatHistory()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    if (firstProgress) {
                        adapter.updateLastMessage(error)
                    } else {
                        adapter.addMessage(ChatMessage(
                            text = error,
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                    persistChatHistory()
                }
            }
        })
    }

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val imageData: ByteArray? = null,
        val timestamp: Long,
        val isThinking: Boolean = false
    )

    interface ChatCallback {
        fun onProgress(step: String)
        fun onComplete(answer: String)
        fun onError(error: String)
    }
}
