package com.pedidosexpress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MenuAdapter(
    private var items: List<MenuItem>,
    private val onToggleAvailable: (MenuItem) -> Unit,
    private val onEdit: (MenuItem) -> Unit,
    private val onDelete: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuItemViewHolder>() {
    
    private val animatedPositions = mutableSetOf<Int>()
    private var isFirstLoad = true

    class MenuItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemName: TextView = itemView.findViewById(R.id.item_name)
        val itemPrice: TextView = itemView.findViewById(R.id.item_price)
        val itemCategory: TextView = itemView.findViewById(R.id.item_category)
        val itemAvailableSwitch: Switch = itemView.findViewById(R.id.item_available_switch)
        val editButton: Button = itemView.findViewById(R.id.edit_button)
        val deleteButton: Button = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu, parent, false)
        return MenuItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuItemViewHolder, position: Int) {
        val item = items[position]
        holder.itemName.text = item.name
        holder.itemPrice.text = "R$ ${String.format("%.2f", item.price)}"
        holder.itemCategory.text = when(item.category) {
            "comida" -> "Comida"
            "bebida" -> "Bebida"
            "sobremesa" -> "Sobremesa"
            "hamburguer" -> "Comida" // Compatibilidade com dados antigos
            else -> item.category.replaceFirstChar { it.uppercaseChar() }
        }
        
        holder.itemAvailableSwitch.isChecked = item.available
        holder.itemAvailableSwitch.setOnCheckedChangeListener { _, _ ->
            onToggleAvailable(item)
        }
        
        holder.editButton.setOnClickListener {
            onEdit(item)
        }
        
        holder.deleteButton.setOnClickListener {
            onDelete(item)
        }
        
        // Aplicar animação fade-in + slide-up com staggered delay
        if (!animatedPositions.contains(position)) {
            animatedPositions.add(position)
            animateItemAppearance(holder.itemView, position)
        }
    }
    
    private fun animateItemAppearance(view: View, position: Int) {
        // Cancelar qualquer animação anterior que possa estar em andamento
        view.animate().cancel()
        
        // Configurar estado inicial (invisível e deslocado para baixo)
        view.alpha = 0f
        view.translationY = 100f
        
        // Calcular delay baseado na posição (staggered animation)
        val delay = position * 50L // 50ms de delay entre cada item
        
        // Animar para estado final (visível e na posição original)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun getItemCount() = items.size
    
    fun updateItems(newItems: List<MenuItem>) {
        val previousSize = items.size
        items = newItems
        
        // Se a lista foi completamente substituída (primeira carga ou refresh completo),
        // resetar as posições animadas para permitir nova animação
        if (newItems.isEmpty() || previousSize == 0 || isFirstLoad) {
            animatedPositions.clear()
            isFirstLoad = false
        } else {
            // Para atualizações incrementais, manter apenas as posições que ainda existem
            animatedPositions.retainAll(newItems.indices.toSet())
        }
        
        notifyDataSetChanged()
    }
}
