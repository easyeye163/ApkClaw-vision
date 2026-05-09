package com.apk.claw.android.floating.reasoning

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R

/**
 * AI 推理步骤列表适配器
 * 在悬浮面板中以时间线形式展示 Agent 的推理过程和操作步骤
 */
class ReasoningAdapter : RecyclerView.Adapter<ReasoningAdapter.StepViewHolder>() {

    private val steps = mutableListOf<ReasoningStep>()

    /** 最大保留条目数，超出后移除最早的 */
    private val maxEntries = 200

    /** 面板打开时间基准，用于计算经过时间 */
    private var baseTimestamp: Long = System.currentTimeMillis()

    fun setBaseTimestamp(timestamp: Long) {
        baseTimestamp = timestamp
    }

    fun addStep(step: ReasoningStep) {
        if (steps.size >= maxEntries) {
            steps.removeAt(0)
            notifyItemRemoved(0)
        }
        steps.add(step)
        notifyItemInserted(steps.size - 1)
    }

    /** 更新最后一个 THINKING 类型的步骤内容（流式更新） */
    fun updateLastThinking(round: Int, content: String) {
        val lastIndex = steps.indexOfLast { it.type == ReasoningStep.StepType.THINKING && it.round == round }
        if (lastIndex >= 0) {
            steps[lastIndex] = steps[lastIndex].copy(content = content)
            notifyItemChanged(lastIndex)
        }
    }

    fun clear() {
        val size = steps.size
        steps.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getStepCount() = steps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reasoning_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position])
    }

    override fun getItemCount() = steps.size

    /** 返回最后一个位置，用于 auto-scroll */
    fun getLastPosition() = if (steps.isEmpty()) -1 else steps.size - 1

    // ==================== ViewHolder ====================

    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val iconIndicator: View = itemView.findViewById(R.id.iconIndicator)
        private val iconView: ImageView = itemView.findViewById(R.id.ivStepIcon)
        private val tvRound: TextView = itemView.findViewById(R.id.tvRound)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvDetail: TextView = itemView.findViewById(R.id.tvDetail)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val layoutDetail: LinearLayout = itemView.findViewById(R.id.layoutDetail)

        fun bind(step: ReasoningStep) {
            // 轮次标签
            tvRound.text = "#${step.round}"

            // 经过时间（基于面板打开时间）
            val elapsed = ((step.timestamp - baseTimestamp).coerceAtLeast(0)) / 1000
            val minutes = (elapsed / 60) % 60
            val seconds = elapsed % 60
            tvTime.text = String.format("%02d:%02d", minutes, seconds)

            // 根据类型设置图标和颜色
            when (step.type) {
                ReasoningStep.StepType.THINKING -> {
                    iconIndicator.setBackgroundColor(Color.parseColor("#FF5856D6"))
                    iconView.setImageResource(R.drawable.ic_brain)
                    iconView.imageTintList = null
                    tvContent.setTextColor(Color.parseColor("#E6FFFFFF"))
                    layoutDetail.visibility = View.GONE
                }
                ReasoningStep.StepType.CONTENT -> {
                    iconIndicator.setBackgroundColor(Color.parseColor("#FF2BA471"))
                    iconView.setImageResource(R.drawable.ic_message)
                    iconView.imageTintList = null
                    tvContent.setTextColor(Color.parseColor("#E6FFFFFF"))
                    layoutDetail.visibility = View.GONE
                }
                ReasoningStep.StepType.TOOL_CALL -> {
                    iconIndicator.setBackgroundColor(Color.parseColor("#FF1AAFFF"))
                    iconView.setImageResource(R.drawable.ic_tool_call)
                    iconView.imageTintList = null
                    tvContent.setTextColor(Color.parseColor("#E6FFFFFF"))
                    // 显示工具参数
                    layoutDetail.visibility = View.VISIBLE
                    tvDetail.text = formatParameters(step.toolParameters)
                }
                ReasoningStep.StepType.TOOL_RESULT -> {
                    val successColor = if (step.isSuccess == true) "#FF2BA471" else "#FFF6685D"
                    iconIndicator.setBackgroundColor(Color.parseColor(successColor))
                    iconView.setImageResource(
                        if (step.isSuccess == true) R.drawable.ic_check
                        else R.drawable.ic_close
                    )
                    iconView.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    tvContent.setTextColor(Color.parseColor("#99FFFFFF"))
                    layoutDetail.visibility = View.GONE
                }
                ReasoningStep.StepType.USER_INPUT -> {
                    iconIndicator.setBackgroundColor(Color.parseColor("#FFE37318"))
                    iconView.setImageResource(R.drawable.ic_user_input)
                    iconView.imageTintList = null
                    tvContent.setTextColor(Color.parseColor("#E6FFFFFF"))
                    layoutDetail.visibility = View.GONE
                }
                ReasoningStep.StepType.SYSTEM_EVENT -> {
                    iconIndicator.setBackgroundColor(Color.parseColor("#FF7E7DB2"))
                    iconView.setImageResource(R.drawable.ic_system_event)
                    iconView.imageTintList = null
                    tvContent.setTextColor(Color.parseColor("#99FFFFFF"))
                    layoutDetail.visibility = View.GONE
                }
                ReasoningStep.StepType.ERROR -> {
                    iconIndicator.setBackgroundColor(Color.parseColor("#FFF6685D"))
                    iconView.setImageResource(R.drawable.ic_close)
                    iconView.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    tvContent.setTextColor(Color.parseColor("#FFF6685D"))
                    layoutDetail.visibility = View.GONE
                }
                ReasoningStep.StepType.COMPLETION -> {
                    iconIndicator.setBackgroundColor(Color.parseColor("#FF2BA471"))
                    iconView.setImageResource(R.drawable.ic_check)
                    iconView.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    tvContent.setTextColor(Color.parseColor("#E6FFFFFF"))
                    layoutDetail.visibility = View.GONE
                }
            }

            // 内容
            tvContent.text = step.content
            tvContent.maxLines = when (step.type) {
                ReasoningStep.StepType.THINKING -> 4
                ReasoningStep.StepType.TOOL_RESULT -> 3
                ReasoningStep.StepType.COMPLETION -> 5
                else -> 2
            }
        }

        private fun formatParameters(params: String?): String {
            if (params.isNullOrBlank()) return ""
            // 截断过长的参数，保留可读性
            return if (params.length > 300) params.take(300) + "..." else params
        }
    }
}
