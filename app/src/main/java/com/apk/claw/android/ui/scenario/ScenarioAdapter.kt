package com.apk.claw.android.ui.scenario

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R

/**
 * 应用场景列表适配器
 */
class ScenarioAdapter(
    private val scenarios: List<Scenario>,
    private val onScenarioClick: (Scenario) -> Unit
) : RecyclerView.Adapter<ScenarioAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvTag: android.widget.TextView = itemView.findViewById(R.id.tvTag)
        val tvTitle: android.widget.TextView = itemView.findViewById(R.id.tvTitle)
        val tvDescription: android.widget.TextView = itemView.findViewById(R.id.tvDescription)
        val tvImageHint: android.widget.TextView = itemView.findViewById(R.id.tvImageHint)
        val tvBottomHint: android.widget.TextView = itemView.findViewById(R.id.tvBottomHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scenario, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scenario = scenarios[position]
        holder.tvTag.text = scenario.tag
        holder.tvTitle.text = scenario.title
        holder.tvDescription.text = scenario.description
        holder.tvImageHint.visibility = if (scenario.supportsImage) android.view.View.VISIBLE else android.view.View.GONE

        // FPV 游戏场景显示不同的底部提示
        if (scenario.isFPVGame) {
            holder.tvBottomHint.text = "点击开始飞行"
        } else {
            holder.tvBottomHint.text = "支持本地/远程模型"
        }

        holder.itemView.setOnClickListener {
            onScenarioClick(scenario)
        }
    }

    override fun getItemCount(): Int = scenarios.size
}