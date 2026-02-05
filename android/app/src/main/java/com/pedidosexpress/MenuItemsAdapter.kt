package com.pedidosexpress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MenuItemsAdapter(
    private val items: List<MenuItem>,
    private val onItemClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuItemsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemName: TextView = view.findViewById(R.id.menu_item_name)
        val itemCategory: TextView = view.findViewById(R.id.menu_item_category)
        val itemPrice: TextView = view.findViewById(R.id.menu_item_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_select, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.itemName.text = item.name
        holder.itemPrice.text = "R$ ${String.format("%.2f", item.price)}"
        
        val categoryText = when (item.category.lowercase()) {
            "comida" -> "Comida"
            "bebida" -> "Bebida"
            "sobremesa" -> "Sobremesa"
            else -> item.category
        }
        holder.itemCategory.text = categoryText

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size
}
