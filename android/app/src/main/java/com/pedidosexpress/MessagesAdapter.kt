package com.pedidosexpress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MessagesAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.message_text)
        val timeText: TextView = view.findViewById(R.id.time_text)
        val statusText: TextView? = view.findViewById(R.id.status_text)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == 0) {
            R.layout.item_message_received
        } else {
            R.layout.item_message_sent
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        
        holder.messageText.text = message.text
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.timeText.text = timeFormat.format(message.timestamp)
        
        if (message.isAttendant) {
            holder.statusText?.text = when (message.status) {
                ChatMessageStatus.SENDING -> "⏳"
                ChatMessageStatus.SENT -> ""
                ChatMessageStatus.ERROR -> "Falhou"
            }
            holder.statusText?.visibility = if (message.status != ChatMessageStatus.SENT) View.VISIBLE else View.GONE
        } else {
            holder.statusText?.visibility = View.GONE
        }
    }
    
    override fun getItemCount() = messages.size
    
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isAttendant) 1 else 0
    }
}
