package com.example.unseenchat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.data.local.ConversationSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onItemClick: (ConversationSummary) -> Unit
) : ListAdapter<ConversationSummary, ConversationAdapter.ConversationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(item: ConversationSummary) {
            tvSenderName.text = item.senderName
            tvLastMessage.text = item.lastMessage

            // নামের প্রথম অক্ষর প্রোফাইল প্লেসহোল্ডারে বসানো
            tvAvatar.text = item.senderName.trim().firstOrNull()?.uppercase() ?: "?"

            // সময় ফরম্যাট (যেমন: 10:05 PM)
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            tvTimestamp.text = sdf.format(Date(item.timestamp))

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ConversationSummary>() {
            override fun areItemsTheSame(oldItem: ConversationSummary, newItem: ConversationSummary): Boolean {
                return oldItem.conversationKey == newItem.conversationKey
            }

            override fun areContentsTheSame(oldItem: ConversationSummary, newItem: ConversationSummary): Boolean {
                return oldItem == newItem
            }
        }
    }
}
