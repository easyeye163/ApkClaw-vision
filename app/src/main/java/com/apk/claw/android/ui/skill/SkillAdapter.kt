package com.apk.claw.android.ui.skill

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.skill.SkillSystem

class SkillAdapter(
    private val skillSystem: SkillSystem,
    private val callback: OnSkillAction
) : RecyclerView.Adapter<SkillAdapter.SkillViewHolder>() {

    private val skills = mutableListOf<SkillSystem.Skill>()

    fun setData(list: List<SkillSystem.Skill>) {
        skills.clear()
        skills.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_skill, parent, false)
        return SkillViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkillViewHolder, position: Int) {
        holder.bind(skills[position])
    }

    override fun getItemCount() = skills.size

    inner class SkillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvTriggerType: TextView = itemView.findViewById(R.id.tvTriggerType)
        private val tvRunCount: TextView = itemView.findViewById(R.id.tvRunCount)
        private val tvBuiltIn: TextView = itemView.findViewById(R.id.tvBuiltIn)
        private val switchEnable: SwitchCompat = itemView.findViewById(R.id.switchEnable)
        private val btnExecute: ImageView = itemView.findViewById(R.id.btnExecute)
        private val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)

        fun bind(skill: SkillSystem.Skill) {
            tvName.text = skill.name
            tvCategory.text = skill.category.displayName
            tvDescription.text = skill.description
            tvTriggerType.text = "触发: ${skill.triggerType.displayName}"
            tvRunCount.text = "执行: ${skill.runCount}次"
            tvBuiltIn.visibility = if (skill.isBuiltIn) View.VISIBLE else View.GONE
            switchEnable.isChecked = skill.enabled

            switchEnable.setOnCheckedChangeListener { _, isChecked ->
                callback.onToggle(skill, isChecked)
            }

            btnExecute.setOnClickListener {
                callback.onExecute(skill)
            }

            btnEdit.setOnClickListener {
                callback.onEdit(skill)
            }

            btnEdit.visibility = if (skill.isBuiltIn) View.GONE else View.VISIBLE
        }
    }

    interface OnSkillAction {
        fun onToggle(skill: SkillSystem.Skill, enabled: Boolean)
        fun onEdit(skill: SkillSystem.Skill)
        fun onExecute(skill: SkillSystem.Skill)
    }
}