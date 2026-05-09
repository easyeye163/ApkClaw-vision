package com.apk.claw.android.ui.skill

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.appViewModel
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.ui.chat.ChatActivity
import com.apk.claw.android.widget.CommonToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SkillManageActivity : BaseActivity() {

    companion object {
        private const val TAG = "SkillManageActivity"
        const val EXTRA_SKILL_PROMPT = "skill_prompt"
        const val EXTRA_SKILL_NAME = "skill_name"
        private const val REQUEST_CODE_IMPORT_FILE = 1001
    }

    private lateinit var rvSkills: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: SkillAdapter
    private lateinit var tabs: List<TextView>

    private var currentCategory: String? = null
    private lateinit var skillSystem: SkillSystem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skill_manage)

        skillSystem = FeatureIntegrationManager.getInstance(this).skillSystem

        initViews()
        loadSkills()
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.skill_title))
            setActionIcon(R.drawable.ic_export) { showExportMenu() }
        }

        rvSkills = findViewById(R.id.rvSkills)
        tvEmpty = findViewById(R.id.tvEmpty)
        fabAdd = findViewById(R.id.fabAdd)

        adapter = SkillAdapter(skillSystem, object : SkillAdapter.OnSkillAction {
            override fun onToggle(skill: SkillSystem.Skill, enabled: Boolean) {
                skillSystem.toggleSkill(skill.id, enabled)
                loadSkills()
            }

            override fun onEdit(skill: SkillSystem.Skill) {
                val intent = Intent(this@SkillManageActivity, SkillEditActivity::class.java)
                intent.putExtra(SkillEditActivity.EXTRA_SKILL_ID, skill.id)
                if (skill.isBuiltIn) {
                    intent.putExtra(SkillEditActivity.EXTRA_IS_COPY, true)
                }
                startActivity(intent)
            }

            override fun onExecute(skill: SkillSystem.Skill) {
                if (appViewModel.isTaskRunning()) {
                    Toast.makeText(this@SkillManageActivity, getString(R.string.chat_task_running), Toast.LENGTH_SHORT).show()
                    return
                }
                Toast.makeText(this@SkillManageActivity, getString(R.string.skill_execute_success), Toast.LENGTH_SHORT).show()
                val intent = Intent(this@SkillManageActivity, com.apk.claw.android.ui.chat.ChatActivity::class.java)
                intent.putExtra(com.apk.claw.android.ui.chat.ChatActivity.EXTRA_SKILL_PROMPT, skill.promptTemplate)
                intent.putExtra(com.apk.claw.android.ui.chat.ChatActivity.EXTRA_SKILL_NAME, skill.name)
                startActivity(intent)
            }

            override fun onDelete(skill: SkillSystem.Skill) {
                if (skill.isBuiltIn) {
                    Toast.makeText(this@SkillManageActivity, getString(R.string.skill_cannot_delete_builtin), Toast.LENGTH_SHORT).show()
                    return
                }
                com.apk.claw.android.widget.ConfirmDialog.showWarm(
                    this@SkillManageActivity,
                    getString(R.string.skill_delete),
                    getString(R.string.skill_delete_confirm, skill.name),
                    getString(R.string.common_confirm),
                    getString(R.string.common_cancel),
                    isDismissible = true,
                    onAction = {
                        skillSystem.deleteSkill(skill.id)
                        loadSkills()
                        Toast.makeText(this@SkillManageActivity, getString(R.string.skill_deleted), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        })

        rvSkills.adapter = adapter
        rvSkills.layoutManager = LinearLayoutManager(this)

        tabs = listOf(
            findViewById(R.id.tabAll),
            findViewById(R.id.tabAutoReply),
            findViewById(R.id.tabPhoneControl),
            findViewById(R.id.tabCrossApp)
        )

        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener {
                selectTab(index)
            }
        }

        fabAdd.setOnClickListener {
            startActivity(Intent(this, SkillEditActivity::class.java))
        }
    }

    /**
     * 显示导出/导入菜单
     */
    private fun showExportMenu() {
        val items = arrayOf(
            getString(R.string.skill_export_all_clipboard),
            getString(R.string.skill_import_from_clipboard)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.skill_import_export))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> exportAllToClipboard()
                    1 -> importFromClipboard()
                }
            }
            .show()
    }

    /**
     * 导出所有技能到剪贴板
     */
    private fun exportAllToClipboard() {
        val allSkills = skillSystem.getAllSkills()
        if (allSkills.isEmpty()) {
            Toast.makeText(this, getString(R.string.skill_export_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val json = skillSystem.exportAllSkills()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("skills_export", json))
        Toast.makeText(this, getString(R.string.skill_export_success, allSkills.size), Toast.LENGTH_SHORT).show()
    }

    /**
     * 从剪贴板导入技能
     */
    private fun importFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, getString(R.string.skill_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val json = clip.getItemAt(0)?.text?.toString()
        if (json.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.skill_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        importFromJson(json)
    }

    /**
     * 从 JSON 字符串导入技能（剪贴板或文件共用）
     */
    private fun importFromJson(json: String) {
        // 先尝试判断是单个技能还是数组
        val trimmed = json.trim()
        val count = if (trimmed.startsWith("[")) {
            skillSystem.importSkills(trimmed)
        } else {
            val skill = skillSystem.importSkill(trimmed)
            if (skill != null) 1 else 0
        }
        if (count > 0) {
            Toast.makeText(this, getString(R.string.skill_import_success, count), Toast.LENGTH_SHORT).show()
            loadSkills()
        } else {
            Toast.makeText(this, getString(R.string.skill_import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectTab(index: Int) {
        tabs.forEach { it.setTextColor(getColor(R.color.colorTextSecondary)) }
        tabs[index].setTextColor(getColor(R.color.colorBrandPrimary))

        currentCategory = when (index) {
            0 -> null
            1 -> "AUTO_REPLY"
            2 -> "PHONE_CONTROL"
            3 -> "CROSS_APP"
            else -> null
        }
        loadSkills()
    }

    private fun loadSkills() {
        val allSkills = skillSystem.getAllSkills()
        val filtered = if (currentCategory != null) {
            allSkills.filter { it.category.name == currentCategory }
        } else {
            allSkills
        }

        adapter.setData(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadSkills()
    }
}
