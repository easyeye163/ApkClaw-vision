package com.apk.claw.android.ui.skill

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton

class SkillEditActivity : BaseActivity() {

    companion object {
        const val EXTRA_SKILL_ID = "skillId"
        const val EXTRA_IS_COPY = "isCopy"
    }

    private lateinit var etName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etKeywords: EditText
    private lateinit var etPrompt: EditText
    private lateinit var btnSave: KButton

    private var editingSkillId: String? = null
    private var isCopy: Boolean = false
    private var originalSkill: SkillSystem.Skill? = null
    private lateinit var skillSystem: SkillSystem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skill_edit)

        skillSystem = FeatureIntegrationManager.getInstance(this).skillSystem
        editingSkillId = intent.getStringExtra(EXTRA_SKILL_ID)
        isCopy = intent.getBooleanExtra(EXTRA_IS_COPY, false)

        initViews()
        loadSkill()
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            val title = when {
                isCopy -> getString(R.string.skill_copy_title)
                editingSkillId != null -> getString(R.string.skill_edit_title)
                else -> getString(R.string.skill_add_title)
            }
            setTitle(title)
        }

        etName = findViewById(R.id.etName)
        etDescription = findViewById(R.id.etDescription)
        etKeywords = findViewById(R.id.etKeywords)
        etPrompt = findViewById(R.id.etPrompt)
        btnSave = findViewById(R.id.btnSave)

        btnSave.setOnClickListener { saveSkill() }
    }

    private fun loadSkill() {
        editingSkillId?.let { id ->
            skillSystem.getSkill(id)?.let { skill ->
                originalSkill = skill
                etName.setText(if (isCopy) skill.name + " (副本)" else skill.name)
                etDescription.setText(skill.description)
                etKeywords.setText(skill.triggerKeywords.joinToString(","))
                etPrompt.setText(skill.promptTemplate)
            }
        }
    }

    private fun saveSkill() {
        val name = etName.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val keywords = etKeywords.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val prompt = etPrompt.text.toString().trim()

        if (name.isEmpty() || prompt.isEmpty()) {
            Toast.makeText(this, getString(R.string.skill_name_prompt_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (isCopy || originalSkill?.isBuiltIn == true) {
            skillSystem.createSkill(
                name = name,
                description = description,
                triggerKeywords = keywords,
                promptTemplate = prompt,
                category = originalSkill?.category ?: SkillSystem.SkillCategory.CUSTOM,
                triggerType = originalSkill?.triggerType ?: SkillSystem.TriggerType.KEYWORD
            )
            Toast.makeText(this, getString(R.string.skill_copied), Toast.LENGTH_SHORT).show()
        } else if (editingSkillId != null) {
            skillSystem.updateSkill(editingSkillId!!, mapOf(
                "name" to name,
                "description" to description,
                "triggerKeywords" to keywords,
                "promptTemplate" to prompt
            ))
        } else {
            skillSystem.createSkill(
                name = name,
                description = description,
                triggerKeywords = keywords,
                promptTemplate = prompt
            )
        }

        finish()
    }
}