package com.example.unseenchat.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.data.local.ChatMessageEntity
import com.example.unseenchat.data.local.MediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : ListAdapter<ChatMessageEntity, RecyclerView.ViewHolder>(DiffCallback) {

    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TYPE_INCOMING = 1
        private const val TYPE_OUTGOING = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<ChatMessageEntity>() {
            override fun areItemsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isOutgoing) TYPE_OUTGOING else TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_OUTGOING) {
            val view = inflater.inflate(R.layout.item_chat_outgoing, parent, false)
            OutgoingViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_incoming, parent, false)
            IncomingViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is OutgoingViewHolder) {
            holder.bind(message)
        } else if (holder is IncomingViewHolder) {
            holder.bind(message)
        }
    }

    inner class OutgoingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(message: ChatMessageEntity) {
            tvMessageText.text = message.messageText
            tvTimestamp.text = formatTime(message.timestamp)
        }
    }

    inner class IncomingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val ivMediaPreview: ImageView = itemView.findViewById(R.id.ivMediaPreview)
        private val btnPlayVoice: ImageButton = itemView.findViewById(R.id.btnPlayVoice)

        fun bind(message: ChatMessageEntity) {
            tvMessageText.text = message.messageText
            tvTimestamp.text = formatTime(message.timestamp)

            // ছবি প্রিভিউ লজিক
            if (message.mediaType == MediaType.IMAGE && message.localMediaPath != null) {
                val file = File(message.localMediaPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ivMediaPreview.setImageBitmap(bitmap)
                    ivMediaPreview.visibility = View.VISIBLE
                } else {
                    ivMediaPreview.visibility = View.GONE
                }
            } else {
                ivMediaPreview.visibility = View.GONE
            }

            // ভয়েস নোট প্লে লজিক
            if (message.mediaType == MediaType.VOICE && message.localMediaPath != null) {
                btnPlayVoice.visibility = View.VISIBLE
                btnPlayVoice.setOnClickListener {
                    playAudio(message.localMediaPath)
                }
            } else {
                btnPlayVoice.visibility = View.GONE
            }
        }
    }

    private fun playAudio(filePath: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
