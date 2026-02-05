package com.pedidosexpress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConversationsAdapter(
    private val conversations: List<PriorityConversation>,
    private val onConversationClick: (PriorityConversation) -> Unit
) : RecyclerView.Adapter<ConversationsAdapter.ViewHolder>() {
    
    var selectedPhone: String? = null
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val phoneText: TextView = view.findViewById(R.id.phone_text)
        val waitTimeText: TextView = view.findViewById(R.id.wait_time_text)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        val isSelected = conversation.phone == selectedPhone
        
        // Formatar número para exibição mais compacta
        val displayPhone = formatPhoneCompact(conversation.phoneFormatted)
        holder.phoneText.text = displayPhone
        holder.waitTimeText.text = formatWaitTime(conversation.waitTime)
        
        holder.itemView.setBackgroundColor(
            if (isSelected) 0xFFEA580C.toInt() else 0xFFFFFFFF.toInt()
        )
        holder.phoneText.setTextColor(
            if (isSelected) 0xFFFFFFFF.toInt() else 0xFF1F2937.toInt()
        )
        holder.waitTimeText.setTextColor(
            if (isSelected) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt()
        )
        
        holder.itemView.setOnClickListener {
            onConversationClick(conversation)
        }
    }
    
    private fun formatPhoneCompact(phoneFormatted: String): String {
        // Se o número já está formatado como (XX) XXXXX-XXXX, manter assim
        // Caso contrário, tentar formatar
        return if (phoneFormatted.matches(Regex("\\(\\d{2}\\) \\d{5}-\\d{4}"))) {
            phoneFormatted
        } else {
            // Tentar formatar número longo
            val digits = phoneFormatted.replace(Regex("[^0-9]"), "")
            if (digits.length >= 11) {
                val ddd = digits.substring(0, 2)
                val part1 = digits.substring(2, 7)
                val part2 = digits.substring(7, 11)
                "($ddd) $part1-$part2"
            } else {
                phoneFormatted
            }
        }
    }
    
    override fun getItemCount() = conversations.size
    
    private fun formatWaitTime(minutes: Int): String {
        return when {
            minutes < 1 -> "Agora"
            minutes < 60 -> "$minutes min"
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                "${hours}h ${mins}min"
            }
        }
    }
}
