package com.apk.claw.android.ui.skill

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.skill.SkillSystem
import com.google.android.material.card.MaterialCardView

class SkillAdapter(
    private val skillSystem: SkillSystem,
    private val callback: OnSkillAction
) : RecyclerView.Adapter<SkillAdapter.SkillViewHolder>() {

    private val skills = mutableListOf<SkillSystem.Skill>()
    private var swipedPosition = -1

    fun setData(list: List<SkillSystem.Skill>) {
        skills.clear()
        skills.addAll(list)
        notifyDataSetChanged()
    }

    fun closeSwipedItem() {
        if (swipedPosition >= 0 && swipedPosition < skills.size) {
            notifyItemChanged(swipedPosition)
            swipedPosition = -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_skill, parent, false)
        return SkillViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkillViewHolder, position: Int) {
        holder.bind(skills[position], position == swipedPosition)
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
        private val contentCard: MaterialCardView = itemView.findViewById(R.id.contentCard)
        private val deleteLayout: LinearLayout = itemView.findViewById(R.id.deleteLayout)

        private var startX = 0f
        private var isSwiping = false
        private val deleteWidth = 80f
        private val swipeThreshold = 50f

        fun bind(skill: SkillSystem.Skill, isSwiped: Boolean) {
            tvName.text = skill.name
            tvCategory.text = skill.category.displayName
            tvDescription.text = skill.description
            tvTriggerType.text = "触发: ${skill.triggerType.displayName}"
            tvRunCount.text = "执行: ${skill.runCount}次"
            tvBuiltIn.visibility = if (skill.isBuiltIn) View.VISIBLE else View.GONE
            switchEnable.isChecked = skill.enabled

            if (isSwiped) {
                contentCard.translationX = -deleteWidth
                deleteLayout.visibility = View.VISIBLE
            } else {
                contentCard.translationX = 0f
                deleteLayout.visibility = View.GONE
            }

            switchEnable.setOnCheckedChangeListener { _, isChecked ->
                closeSwipedItem()
                callback.onToggle(skill, isChecked)
            }

            btnExecute.setOnClickListener {
                closeSwipedItem()
                callback.onExecute(skill)
            }

            btnEdit.setOnClickListener {
                closeSwipedItem()
                callback.onEdit(skill)
            }

            deleteLayout.setOnClickListener {
                closeSwipedItem()
                callback.onDelete(skill)
            }

            contentCard.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        isSwiping = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isSwiping) {
                            val deltaX = event.rawX - startX
                            if (deltaX < 0) {
                                val translation = Math.max(deltaX, -deleteWidth)
                                contentCard.translationX = translation
                                deleteLayout.visibility = if (translation < -swipeThreshold) View.VISIBLE else View.GONE
                            } else if (deltaX > 0 && contentCard.translationX < 0) {
                                val translation = Math.min(0f, contentCard.translationX + deltaX)
                                contentCard.translationX = translation
                                deleteLayout.visibility = if (translation < -swipeThreshold) View.VISIBLE else View.GONE
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isSwiping) {
                            isSwiping = false
                            if (contentCard.translationX < -swipeThreshold) {
                                val position = bindingAdapterPosition
                                if (position != RecyclerView.NO_POSITION && position != swipedPosition) {
                                    closeSwipedItem()
                                    swipedPosition = position
                                    contentCard.translationX = -deleteWidth
                                    deleteLayout.visibility = View.VISIBLE
                                }
                            } else {
                                contentCard.translationX = 0f
                                deleteLayout.visibility = View.GONE
                                if (bindingAdapterPosition == swipedPosition) {
                                    swipedPosition = -1
                                }
                            }
                        }
                        false
                    }
                    else -> false
                }
            }
        }
    }

    interface OnSkillAction {
        fun onToggle(skill: SkillSystem.Skill, enabled: Boolean)
        fun onEdit(skill: SkillSystem.Skill)
        fun onExecute(skill: SkillSystem.Skill)
        fun onDelete(skill: SkillSystem.Skill)
    }
}