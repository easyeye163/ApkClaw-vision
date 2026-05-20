package com.apk.claw.android.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R

/**
 * 本地模型选择列表适配器
 *
 * 展示预设的 GGUF 模型列表，支持单选切换。
 */
class LocalModelAdapter(
    private val models: List<LocalModelInfo>,
    selectedModelId: String,
    private val onModelSelected: (LocalModelInfo) -> Unit
) : RecyclerView.Adapter<LocalModelAdapter.ViewHolder>() {

    private var currentSelectedId: String = selectedModelId

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_model_name)
        val tvDesc: TextView = itemView.findViewById(R.id.tv_model_desc)
        val tvSize: TextView = itemView.findViewById(R.id.tv_model_size)
        val tvSelected: TextView = itemView.findViewById(R.id.iv_selected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_local_model_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        holder.tvName.text = model.displayName
        holder.tvDesc.text = model.description
        holder.tvSize.text = model.modelSize

        val isSelected = model.id == currentSelectedId
        holder.tvSelected.text = if (isSelected) "●" else "○"
        holder.tvSelected.alpha = if (isSelected) 1.0f else 0.4f

        holder.itemView.setOnClickListener {
            if (model.id == currentSelectedId) return@setOnClickListener
            val prevIndex = models.indexOfFirst { it.id == currentSelectedId }
            currentSelectedId = model.id
            if (prevIndex >= 0) notifyItemChanged(prevIndex)
            notifyItemChanged(position)
            onModelSelected(model)
        }
    }

    override fun getItemCount(): Int = models.size
}
