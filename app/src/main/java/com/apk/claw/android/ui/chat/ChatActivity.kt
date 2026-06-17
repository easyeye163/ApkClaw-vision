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
import java.io.InputStreamReader
import java.util.Base64
import android.widget.Switch
import android.widget.CompoundButton
import com.apk.claw.android.floating.voice.TtsManager
import com.apk.claw.android.webrtc.DirectWebRTCManager
import androidx.lifecycle.lifecycleScope
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
    private lateinit var switchLocalModel: Switch
    private lateinit var tvConnectionStatus: android.widget.TextView
    private var voiceInputController: VoiceInputController? = null
    private var isListening = false
    private var ttsManager: TtsManager? = null
    private var ttsEnabled = false
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

        // 初始化 TTS
        ttsEnabled = KVUtils.isTtsEnabled()
        ttsManager = TtsManager(this)

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
        // 禁用 ItemAnimator 防止流式输出时每个 token 触发淡入淡出动画导致闪烁
        rvMessages.itemAnimator = null

        loadChatHistory()

        // Handle incoming intents
        handleSkillIntent()
        handleMarkdownIntent()
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

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        voiceFloatTextHandled = false
        handleMarkdownIntent()
        handleScreenshotIntent()
        handleVoiceFloatIntent()
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
        ttsManager?.shutdown()
        ttsManager = null
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
            if (isChecked && switchLocalModel.isChecked) {
                switchLocalModel.isChecked = false
                KVUtils.setLocalModelChatActive(false)
            }
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
        // 本地模型开关
        switchLocalModel = findViewById(R.id.switchLocalModel)
        switchLocalModel.isChecked = KVUtils.isLocalModelChatActive()
        switchLocalModel.setOnCheckedChangeListener { _, isChecked ->
            KVUtils.setLocalModelChatActive(isChecked)
            if (isChecked && switchCloudMode.isChecked) {
                switchCloudMode.isChecked = false
                KVUtils.setCloudChatEnabled(false)
                CloudChatManager.disconnect()
                stopObservingConnectionState()
                tvConnectionStatus.visibility = android.view.View.GONE
            }
            updateModeHint()
        }
        updateModeHint()
        btnVoice = findViewById(R.id.btnVoice)
        btnVoice.setOnClickListener { toggleVoiceInput() }
        // TTS 语音朗读按钮
        val btnTts = findViewById<ImageView>(R.id.btnTts)
        updateTtsButton(btnTts)
        btnTts.setOnClickListener {
            ttsEnabled = !ttsEnabled
            KVUtils.setTtsEnabled(ttsEnabled)
            updateTtsButton(btnTts)
            if (!ttsEnabled) ttsManager?.stop()
        }
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
        when {
            switchLocalModel.isChecked -> {
                tvModeHint.text = getString(R.string.chat_mode_local_llm)
                tvModeHint.visibility = View.VISIBLE
            }
            switchCloudMode.isChecked -> {
                val modeName = CloudChatManager.getModeDisplayName()
                tvModeHint.text = "${getString(R.string.chat_cloud_mode)} · $modeName"
                tvModeHint.visibility = View.VISIBLE
            }
            else -> {
                tvModeHint.text = getString(R.string.chat_mode_local)
                tvModeHint.visibility = View.VISIBLE
            }
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
     * Handle external file intents: .md/.txt files opened from file managers,
     * WeChat/QQ "open with", or shared text.
     */
    private fun handleMarkdownIntent() {
        val intent = intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> handleViewIntent(intent)
            Intent.ACTION_SEND -> handleSendIntent(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleSendMultipleIntent(intent)
        }
    }

    /** Check if a URI points to a .md/.markdown/.txt file */
    private fun isMarkdownFile(uri: Uri): Boolean {
        // Try lastPathSegment first (works for file:// and some content://)
        val segment = uri.lastPathSegment?.lowercase() ?: ""
        if (segment.endsWith(".md") || segment.endsWith(".markdown") || segment.endsWith(".txt")) return true
        // For content:// URIs, query ContentResolver for the display name
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex).lowercase()
                        if (name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt")) return true
                    }
                }
            } catch (_: Exception) {}
            // Fallback: try to read as text and see if it looks like markdown
            // (some URIs don't expose DISPLAY_NAME)
            return true // Accept all content:// text URIs, worst case user sees non-md content
        }
        return false
    }

    /** Get display file name from URI */
    private fun getFileName(uri: Uri): String {
        val segment = uri.lastPathSegment?.substringAfterLast("/") ?: ""
        if (segment.contains(".")) return segment
        // Try ContentResolver for content:// URIs
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (name.isNotBlank()) return name
                    }
                }
            } catch (_: Exception) {}
        }
        return "文档"
    }

    /** Handle file manager "open with" (ACTION_VIEW) */
    private fun handleViewIntent(intent: Intent) {
        val uri: Uri = intent.data ?: return
        if (!isMarkdownFile(uri)) return
        val mdText = readTextFromUri(uri) ?: return
        showMarkdownInChat(mdText, getFileName(uri))
    }

    /** Handle WeChat/QQ "open with" single file (ACTION_SEND) */
    private fun handleSendIntent(intent: Intent) {
        // EXTRA_TEXT: plain text shared directly
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText != null && sharedText.isNotBlank()) {
            showMarkdownInChat(sharedText.trim(), "分享文本")
            return
        }
        // EXTRA_STREAM: file URI shared
        val uri: Uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) ?: return
        if (!isMarkdownFile(uri)) return
        val mdText = readTextFromUri(uri) ?: return
        showMarkdownInChat(mdText, getFileName(uri))
    }

    /** Handle multiple files sent at once (ACTION_SEND_MULTIPLE) */
    private fun handleSendMultipleIntent(intent: Intent) {
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        val sb = StringBuilder()
        for (uri in uris) {
            if (isMarkdownFile(uri)) {
                val text = readTextFromUri(uri)
                if (text != null) {
                    sb.append("### ${getFileName(uri)}\n\n$text\n\n---\n\n")
                }
            }
        }
        if (sb.isBlank()) return
        showMarkdownInChat(sb.toString().trimEnd('-', ' ', '\n'), "批量文档")
    }

    /** Read text content from a URI */
    private fun readTextFromUri(uri: Uri): String? {
        return try {
            val content = StringBuilder()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(4096)
                    var read: Int
                    while (reader.read(buffer).also { read = it } != -1) {
                        content.append(buffer, 0, read)
                    }
                }
            }
            val text = content.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "文件内容为空", Toast.LENGTH_SHORT).show()
                null
            } else {
                text
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    /** Display markdown text in chat as user message and auto-send to AI */
    private fun showMarkdownInChat(mdText: String, fileName: String) {
        val displayText = "### $fileName\n\n$mdText"
        adapter.addMessage(ChatMessage(
            text = displayText,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            isMarkdown = true
        ))
        rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
        val prompt = "请阅读并分析以下 Markdown 文档内容，给出摘要和关键要点：\n\n$mdText"
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            sendMessageWithPrompt(prompt)
        }, 500)
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

        // 根据开关决定走本地模型、云端还是本地 LLM
        when {
            switchLocalModel.isChecked -> sendLocalModelMessage(text)
            switchCloudMode.isChecked -> sendCloudMessage(text)
            else -> sendLocalMessage(text, imageDataToSend)
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
                    speakAnswer(answer)
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
     * 本地模型模式：使用 llama.cpp 引擎直接推理
     */
    private fun sendLocalModelMessage(text: String) {
        val engine = com.apk.claw.android.local.llm.LlamaEngine.getInstance(this)
        if (!engine.isModelLoaded) {
            Toast.makeText(this, getString(R.string.chat_local_model_not_loaded), Toast.LENGTH_LONG).show()
            return
        }

        val thinkingMessage = ChatMessage(
            text = getString(R.string.chat_local_model_generating),
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isThinking = true
        )
        adapter.addMessage(thinkingMessage)
        rvMessages.smoothScrollToPosition(adapter.itemCount - 1)

        val sb = StringBuilder()
        lifecycleScope.launch {
            try {
                // 50ms 节流：高速 token 累积后批量刷新 UI，避免逐 token 刷新导致闪烁
                var lastUpdateTime = 0L
                val throttleMs = 50L

                engine.sendUserPrompt(text).collect { token ->
                    sb.append(token)
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= throttleMs) {
                        lastUpdateTime = now
                        runOnUiThread {
                            // 流式输出时实时过滤  分析过程，用户不会看到思考内容
                            val display = stripThinkTags(sb.toString())
                            adapter.updateLastMessage(display.ifEmpty { getString(R.string.chat_local_model_generating) })
                            rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
                // 流式完成：确保最终状态刷新并保存
                runOnUiThread {
                    val answer = stripThinkTags(sb.toString())
                    adapter.updateLastMessage(answer.ifEmpty { getString(R.string.chat_local_model_generating) })
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                    persistChatHistory()
                    speakAnswer(answer)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val errorMsg = getString(R.string.chat_local_model_error, e.message ?: "Unknown")
                    adapter.updateLastMessage(errorMsg)
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                    persistChatHistory()
                }
            }
        }
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
                    speakAnswer(answer)
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

    /**
     * 过滤 <think>...</think> 标签及其内容（某些模型如 DeepSeek 会输出思考过程）
     * 支持流式中间态：当 </think> 尚未到达时，截断未闭合的 <think 块
     */
    private fun stripThinkTags(text: String): String {
        var result = text
        while (true) {
            val start = result.indexOf("<think")
            if (start < 0) break
            val end = result.indexOf("</think", start)
            if (end >= 0) {
                val close = result.indexOf(">", end + 7)
                result = if (close >= 0) result.removeRange(start, close + 1) else result.removeRange(start, result.length)
            } else {
                // 流式中间态：</think> 尚未到达，截断 <think 及其之后的所有内容
                result = result.removeRange(start, result.length)
                break
            }
        }
        return result.trim()
    }

    /**
     * 使用 TTS 朗读 AI 回复（如果 TTS 已启用）
     */
    private fun speakAnswer(text: String) {
        if (ttsEnabled && ttsManager != null) {
            ttsManager?.speak(stripMarkdownForTts(text))
        }
    }

    /**
     * 去除 Markdown 格式标记，保留纯文本供 TTS 朗读。
     * 例如：**加粗** → 加粗，~~删除线~~ → 删除线，# 标题 → 标题
     */
    private fun stripMarkdownForTts(text: String): String {
        return text
            // 去除代码块
            .replace(Regex("```[\\s\\S]*?```"), "")
            // 去除行内代码
            .replace(Regex("`[^`]+`"), "")
            // 去除图片 ![alt](url) → 保留 alt
            .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
            // 去除链接 [text](url) → 保留 text
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
            // 去除加粗 **text** 或 __text__ → text
            .replace(Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__"), "$1$2")
            // 去除斜体 *text* 或 _text_ → text（注意不匹配已去除的加粗）
            .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)"), "$1$2")
            // 去除删除线 ~~text~~ → text
            .replace(Regex("~~([^~]+)~~"), "$1")
            // 去除标题标记 # ## ### 等
            .replace(Regex("^#{1,6}\\s+"), "")
            // 去除引用 >
            .replace(Regex("(?m)^>\\s+"), "")
            // 去除无序列表标记
            .replace(Regex("(?m)^[-*+]\\s+"), "")
            // 去除有序列表标记
            .replace(Regex("(?m)^\\d+\\.\\s+"), "")
            // 去除分隔线
            .replace(Regex("^---+$"), "")
            .replace(Regex("^\\*\\*\\*+$"), "")
            // 清理多余空行（保留单换行）
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * 更新 TTS 按钮图标状态
     */
    private fun updateTtsButton(btn: ImageView) {
        if (ttsEnabled) {
            btn.setImageResource(R.drawable.ic_volume_up)
            btn.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.colorBrandPrimary))
        } else {
            btn.setImageResource(R.drawable.ic_volume_off)
            btn.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.colorTextSecondary))
        }
    }

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val imageData: ByteArray? = null,
        val timestamp: Long,
        val isThinking: Boolean = false,
        val isMarkdown: Boolean = false
    )

    interface ChatCallback {
        fun onProgress(step: String)
        fun onComplete(answer: String)
        fun onError(error: String)
    }
}
