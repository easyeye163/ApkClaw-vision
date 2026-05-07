package com.apk.claw.android.ui.timeline

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.timeline.TaskTimeline
import com.apk.claw.android.ui.skill.SkillManageActivity
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton
import com.apk.claw.android.widget.LoadingDialog
import java.text.SimpleDateFormat
import java.util.*

class TaskDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
    }

    private lateinit var tvTaskMessage: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvChannel: TextView
    private lateinit var tvRounds: TextView
    private lateinit var tvTokens: TextView
    private lateinit var rvSteps: RecyclerView
    private lateinit var btnFormSkill: KButton

    private lateinit var taskTimeline: TaskTimeline
    private lateinit var stepAdapter: TaskStepAdapter
    private var recordId: String = ""
    private var record: TaskTimeline.TaskRecord? = null
    private var loadingDialog: LoadingDialog? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)

        recordId = intent.getStringExtra(EXTRA_RECORD_ID) ?: ""
        taskTimeline = FeatureIntegrationManager.getInstance(this).taskTimeline
        record = taskTimeline.getTask(recordId)

        if (record == null) {
            finish()
            return
        }

        initViews()
        loadData()
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).setTitle(getString(R.string.task_detail_title))

        tvTaskMessage = findViewById(R.id.tvTaskMessage)
        tvStatus = findViewById(R.id.tvStatus)
        tvTime = findViewById(R.id.tvTime)
        tvDuration = findViewById(R.id.tvDuration)
        tvChannel = findViewById(R.id.tvChannel)
        tvRounds = findViewById(R.id.tvRounds)
        tvTokens = findViewById(R.id.tvTokens)
        rvSteps = findViewById(R.id.rvSteps)
        btnFormSkill = findViewById(R.id.btnFormSkill)

        stepAdapter = TaskStepAdapter()
        rvSteps.adapter = stepAdapter
        rvSteps.layoutManager = LinearLayoutManager(this)

        btnFormSkill.setOnClickListener {
            generateSkill()
        }

        val status = record?.status
        if (status == TaskTimeline.TaskStatus.COMPLETED) {
            btnFormSkill.visibility = View.VISIBLE
        } else {
            btnFormSkill.visibility = View.GONE
        }
    }

    private fun loadData() {
        val task = record ?: return

        tvTaskMessage.text = task.userMessage

        val (statusText, statusColor) = when (task.status) {
            TaskTimeline.TaskStatus.COMPLETED -> Pair(getString(R.string.task_status_completed), Color.parseColor("#4CAF50"))
            TaskTimeline.TaskStatus.FAILED -> Pair(getString(R.string.task_status_failed), Color.parseColor("#F44336"))
            TaskTimeline.TaskStatus.CANCELLED -> Pair(getString(R.string.task_status_cancelled), Color.parseColor("#FF9800"))
            TaskTimeline.TaskStatus.RUNNING -> Pair(getString(R.string.task_status_running), Color.parseColor("#2196F3"))
        }
        tvStatus.text = statusText
        tvStatus.setBackgroundColor(statusColor)

        tvTime.text = dateFormat.format(Date(task.createdAt))

        val duration = task.durationMs ?: 0
        tvDuration.text = if (duration > 0) "${duration / 1000}s" else ""

        tvChannel.text = getString(R.string.task_channel, task.channelType)
        tvRounds.text = getString(R.string.task_rounds, task.totalRounds)
        tvTokens.text = getString(R.string.task_tokens, task.llmTokensUsed)

        stepAdapter.setData(task.steps)
    }

    private fun generateSkill() {
        val task = record ?: return

        loadingDialog = LoadingDialog.show(this, getString(R.string.skill_generating), true)
        loadingDialog?.show()

        val generator = SkillGenerator(this)
        generator.generateSkillFromTask(task) { result ->
            runOnUiThread {
                loadingDialog?.dismiss()
                loadingDialog = null

                if (result.isSuccess) {
                    val info = result.getOrNull()
                    if (info != null) {
                        android.widget.Toast.makeText(
                            this,
                            getString(R.string.skill_generated, info.name),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(this, SkillManageActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.skill_generate_failed, result.exceptionOrNull()?.message ?: "Unknown"),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}