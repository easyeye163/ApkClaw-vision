package com.apk.claw.android.ui.chat

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.apk.claw.android.widget.CommonToolbar
import java.io.ByteArrayOutputStream

class ChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_SKILL_PROMPT = "skill_prompt"
        const val EXTRA_SKILL_NAME = "skill_name"
        const val EXTRA_MATCHED_SKILL_ID = "matched_skill_id"
    }

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: android.widget.EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnAttach: ImageView
    private lateinit var ivPreview: ImageView
    private lateinit var btnRemovePreview: ImageView
    private lateinit var adapter: ChatAdapter

    private var selectedImageUri: Uri? = null
    private var selectedImageData: ByteArray? = null

    private lateinit var skillSystem: com.apk.claw.android.skill.SkillSystem

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
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val stream = ByteArrayOutputStream()
                bitmap?.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, stream)
                selectedImageData = stream.toByteArray()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        skillSystem = FeatureIntegrationManager.getInstance(this).skillSystem

        initViews()
        adapter = ChatAdapter()
        rvMessages.adapter = adapter
        rvMessages.layoutManager = LinearLayoutManager(this)

        addWelcomeMessage()

        handleSkillIntent()
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
        ivPreview = findViewById(R.id.ivPreview)
        btnRemovePreview = findViewById(R.id.btnRemovePreview)

        btnSend.setOnClickListener { sendMessage() }
        btnAttach.setOnClickListener { imagePickerLauncher.launch("image/*") }
        btnRemovePreview.setOnClickListener {
            selectedImageUri = null
            selectedImageData = null
            ivPreview.visibility = View.GONE
            btnRemovePreview.visibility = View.GONE
        }
    }

    private fun addWelcomeMessage() {
        adapter.addMessage(ChatMessage(
            text = getString(R.string.chat_welcome),
            isUser = false,
            timestamp = System.currentTimeMillis()
        ))
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

    private fun sendMessageInternal(text: String) {
        etMessage.text.clear()

        if (selectedImageData != null) {
            ivPreview.visibility = View.GONE
            btnRemovePreview.visibility = View.GONE
            selectedImageUri = null
            selectedImageData = null
        }

        val userMessage = ChatMessage(
            text = text,
            isUser = true,
            imageData = selectedImageData,
            timestamp = System.currentTimeMillis()
        )
        adapter.addMessage(userMessage)
        rvMessages.smoothScrollToPosition(adapter.itemCount - 1)

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

        appViewModel.startChatTask(text, selectedImageData, object : ChatCallback {
            override fun onProgress(step: String) {
                runOnUiThread {
                    adapter.updateLastMessage(step)
                }
            }

            override fun onComplete(answer: String) {
                runOnUiThread {
                    adapter.updateLastMessage(answer)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    adapter.updateLastMessage(error)
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