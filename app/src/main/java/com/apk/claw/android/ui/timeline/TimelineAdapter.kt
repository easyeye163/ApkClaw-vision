package com.apk.claw.android.ui.timeline

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.timeline.TaskTimeline
import java.text.SimpleDateFormat
import java.util.*

class TimelineAdapter : RecyclerView.Adapter<TimelineAdapter.TaskViewHolder>() {

    private val tasks = mutableListOf<TaskTimeline.TaskRecord>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun setData(list: List<TaskTimeline.TaskRecord>) {
        tasks.clear()
        tasks.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount() = tasks.size

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvChannel: TextView = itemView.findViewById(R.id.tvChannel)
        private val tvRounds: TextView = itemView.findViewById(R.id.tvRounds)

        fun bind(task: TaskTimeline.TaskRecord) {
            tvTime.text = dateFormat.format(Date(task.createdAt))
            tvMessage.text = task.userMessage

            val statusColor = when (task.status) {
                TaskTimeline.TaskStatus.COMPLETED -> Color.parseColor("#4CAF50")
                TaskTimeline.TaskStatus.FAILED -> Color.parseColor("#F44336")
                TaskTimeline.TaskStatus.CANCELLED -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#2196F3")
            }
            tvStatus.setBackgroundColor(statusColor)

            val statusText = when (task.status) {
                TaskTimeline.TaskStatus.COMPLETED -> "OK"
                TaskTimeline.TaskStatus.FAILED -> "FAIL"
                TaskTimeline.TaskStatus.CANCELLED -> "CANCEL"
                else -> "RUN"
            }
            tvStatus.text = statusText

            tvChannel.text = task.channelType
            tvRounds.text = "${task.totalRounds}r"
        }
    }
}