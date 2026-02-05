package com.pedidosexpress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrderItemsEditAdapter(
    private val items: List<OrderItem>,
    private val onQuantityChange: (position: Int, newQuantity: Int) -> Unit,
    private val onRemove: (position: Int) -> Unit
) : RecyclerView.Adapter<OrderItemsEditAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemName: TextView = view.findViewById(R.id.item_name)
        val itemPrice: TextView = view.findViewById(R.id.item_price)
        val itemQuantity: TextView = view.findViewById(R.id.item_quantity)
        val quantityDecrease: Button = view.findViewById(R.id.quantity_decrease)
        val quantityIncrease: Button = view.findViewById(R.id.quantity_increase)
        val removeButton: ImageButton = view.findViewById(R.id.remove_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_edit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val totalPrice = item.price * item.quantity

        holder.itemName.text = item.name
        holder.itemPrice.text = "R$ ${String.format("%.2f", totalPrice)}"
        holder.itemQuantity.text = item.quantity.toString()

        holder.quantityDecrease.setOnClickListener {
            val newQuantity = item.quantity - 1
            onQuantityChange(position, newQuantity)
        }

        holder.quantityIncrease.setOnClickListener {
            val newQuantity = item.quantity + 1
            onQuantityChange(position, newQuantity)
        }

        holder.removeButton.setOnClickListener {
            onRemove(position)
        }
    }

    override fun getItemCount() = items.size
}
