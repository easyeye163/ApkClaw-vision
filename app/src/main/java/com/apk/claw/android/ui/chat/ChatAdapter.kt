package com.apk.claw.android.ui.chat

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatActivity.ChatMessage>()
    private var markwon: Markwon? = null

    fun setMarkwon(mw: Markwon) {
        markwon = mw
    }

    fun addMessage(message: ChatActivity.ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(text: String) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages[lastIndex] = messages[lastIndex].copy(text = text, isThinking = false)
            notifyItemChanged(lastIndex)
        }
    }

    fun getMessages(): List<ChatActivity.ChatMessage> = messages.toList()

    fun clearAll() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message, markwon)
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutUser: LinearLayout = itemView.findViewById(R.id.layoutUser)
        private val layoutAgent: LinearLayout = itemView.findViewById(R.id.layoutAgent)
        private val tvUserText: TextView = itemView.findViewById(R.id.tvUserText)
        private val tvAgentText: TextView = itemView.findViewById(R.id.tvAgentText)
        private val ivUserImage: ImageView = itemView.findViewById(R.id.ivUserImage)
        private val ivAgentImage: ImageView = itemView.findViewById(R.id.ivAgentImage)

        fun bind(message: ChatActivity.ChatMessage, markwon: Markwon?) {
            layoutUser.visibility = if (message.isUser) View.VISIBLE else View.GONE
            layoutAgent.visibility = if (message.isUser) View.GONE else View.VISIBLE

            if (message.isUser) {
                tvUserText.text = message.text
                if (message.imageData != null) {
                    ivUserImage.visibility = View.VISIBLE
                    val bitmap = BitmapFactory.decodeByteArray(message.imageData, 0, message.imageData.size)
                    ivUserImage.setImageBitmap(bitmap)
                } else {
                    ivUserImage.visibility = View.GONE
                }
            } else {
                // Use Markwon (with table plugin) for Markdown rendering on agent messages
                markwon?.setMarkdown(tvAgentText, message.text ?: "")
                ivAgentImage.visibility = View.GONE
            }
        }
    }
}
