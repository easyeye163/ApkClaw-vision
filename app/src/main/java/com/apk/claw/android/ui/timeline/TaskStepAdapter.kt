package com.apk.claw.android.ui.timeline

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.timeline.TaskTimeline

class TaskStepAdapter : RecyclerView.Adapter<TaskStepAdapter.StepViewHolder>() {

    private val steps = mutableListOf<TaskTimeline.TaskStep>()

    fun setData(list: List<TaskTimeline.TaskStep>) {
        steps.clear()
        steps.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position])
    }

    override fun getItemCount() = steps.size

    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvRound: TextView = itemView.findViewById(R.id.tvRound)

        fun bind(step: TaskTimeline.TaskStep) {
            val context = itemView.context
            
            val (iconRes, typeText, color) = when (step.type) {
                TaskTimeline.StepType.THINKING -> Triple(R.drawable.ic_brain, context.getString(R.string.step_thinking), Color.parseColor("#2196F3"))
                TaskTimeline.StepType.TOOL_CALL -> Triple(R.drawable.ic_tool_call, context.getString(R.string.step_tool_call), Color.parseColor("#4CAF50"))
                TaskTimeline.StepType.TOOL_RESULT -> Triple(R.drawable.ic_check, context.getString(R.string.step_tool_result), Color.parseColor("#8BC34A"))
                TaskTimeline.StepType.USER_INPUT -> Triple(R.drawable.ic_user_input, context.getString(R.string.step_user_input), Color.parseColor("#FF9800"))
                TaskTimeline.StepType.SYSTEM_EVENT -> Triple(R.drawable.ic_system_event, context.getString(R.string.step_system_event), Color.parseColor("#9E9E9E"))
                TaskTimeline.StepType.ERROR -> Triple(R.drawable.ic_close, context.getString(R.string.step_error), Color.parseColor("#F44336"))
                TaskTimeline.StepType.COMPLETION -> Triple(R.drawable.ic_check, context.getString(R.string.step_completion), Color.parseColor("#4CAF50"))
            }

            ivIcon.setImageResource(iconRes)
            ivIcon.setColorFilter(color)
            tvType.text = typeText
            tvType.setTextColor(color)

            val content = step.content
            tvContent.text = if (content.length > 500) content.substring(0, 500) + "..." else content

            tvRound.text = "R${step.round}"
            tvRound.visibility = if (step.round > 0) View.VISIBLE else View.GONE
        }
    }
}