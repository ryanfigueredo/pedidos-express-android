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
        
        // Se estiver selecionado, mostrar ordem na fila (1, 2, 3...)
        // Se não estiver selecionado, mostrar número de telefone
        if (isSelected) {
            val queueNumber = position + 1
            holder.phoneText.text = queueNumber.toString()
        } else {
            val displayPhone = formatPhoneCompact(conversation.phoneFormatted)
            holder.phoneText.text = displayPhone
        }
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
        // Extrair apenas dígitos
        var digits = phoneFormatted.replace(Regex("[^0-9]"), "")
        
        // Se o número não começa com 55 (código do país), adicionar
        if (!digits.startsWith("55") && digits.length >= 10) {
            digits = "55$digits"
        }
        
        // Formatar: +55 (XX) XXXXX-XXXX
        return when {
            digits.length >= 13 -> {
                // Número completo com código do país
                val countryCode = digits.substring(0, 2) // 55
                val ddd = digits.substring(2, 4) // DDD
                val part1 = digits.substring(4, 9) // Primeiros 5 dígitos
                val part2 = digits.substring(9, 13) // Últimos 4 dígitos
                "+$countryCode ($ddd) $part1-$part2"
            }
            digits.length >= 11 -> {
                // DDD + número (sem código do país)
                val ddd = digits.substring(0, 2)
                val part1 = digits.substring(2, 7)
                val part2 = digits.substring(7, 11)
                "+55 ($ddd) $part1-$part2"
            }
            else -> {
                // Fallback: se já está formatado, adicionar +55 no início
                if (phoneFormatted.matches(Regex("\\(\\d{2}\\) \\d{5}-\\d{4}"))) {
                    "+55 $phoneFormatted"
                } else {
                    phoneFormatted
                }
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
