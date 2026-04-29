package com.apk.claw.android.ui.skill

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.integration.FeatureIntegrationManager
import com.apk.claw.android.skill.SkillSystem
import com.apk.claw.android.widget.CommonToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SkillManageActivity : BaseActivity() {

    companion object {
        private const val TAG = "SkillManageActivity"
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
                if (!skill.isBuiltIn) {
                    val intent = Intent(this@SkillManageActivity, SkillEditActivity::class.java)
                    intent.putExtra("skillId", skill.id)
                    startActivity(intent)
                }
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