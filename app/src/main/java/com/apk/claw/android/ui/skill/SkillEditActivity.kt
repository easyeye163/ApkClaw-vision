package com.apk.claw.android.ui.skill

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton

class SkillEditActivity : BaseActivity() {

    private lateinit var etName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etKeywords: EditText
    private lateinit var etPrompt: EditText
    private lateinit var btnSave: KButton

    private var editingSkillId: String? = null
    private lateinit var skillSystem: SkillSystem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skill_edit)

        skillSystem = FeatureIntegrationManager.getInstance(this).skillSystem
        editingSkillId = intent.getStringExtra("skillId")

        initViews()
        loadSkill()
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(if (editingSkillId != null) getString(R.string.skill_edit_title) else getString(R.string.skill_add_title))
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
                etName.setText(skill.name)
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
            return
        }

        if (editingSkillId != null) {
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