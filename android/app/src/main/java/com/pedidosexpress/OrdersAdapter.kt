package com.pedidosexpress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrdersAdapter(
    private var orders: List<Order>,
    private val onOrderClick: (Order) -> Unit,
    private val onMenuClick: (Order) -> Unit,
    private val onConfirmDelivery: (Order) -> Unit,
    private val onReportProblem: (Order) -> Unit
) : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {
    
    private val animatedPositions = mutableSetOf<Int>()
    private var isFirstLoad = true

    class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val orderId: TextView = itemView.findViewById(R.id.order_id)
        val customerName: TextView = itemView.findViewById(R.id.customer_name)
        val orderTotal: TextView = itemView.findViewById(R.id.order_total)
        val orderStatus: TextView = itemView.findViewById(R.id.order_status)
        val menuButton: ImageButton = itemView.findViewById(R.id.order_menu_button)
        val deliveryActionsLayout: LinearLayout = itemView.findViewById(R.id.delivery_actions_layout)
        val confirmDeliveryButton: Button = itemView.findViewById(R.id.confirm_delivery_button)
        val reportProblemButton: Button = itemView.findViewById(R.id.report_problem_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        val displayId = order.displayId ?: order.id.take(8)
        holder.orderId.text = "Pedido #$displayId"
        holder.customerName.text = "Cliente: ${order.customerName}"
        holder.orderTotal.text = "Total: R$ ${String.format("%.2f", order.totalPrice)}"
        
        // Status
        val statusText = when (order.status) {
            "pending" -> "Pendente"
            "printed" -> "Impresso"
            "finished" -> "Finalizado"
            "out_for_delivery" -> "Em rota"
            "cancelled" -> "Cancelado"
            else -> order.status
        }
        holder.orderStatus.text = statusText
        
        // Mostrar botões de ação apenas para pedidos em rota
        val isOutForDelivery = order.status == "out_for_delivery"
        holder.deliveryActionsLayout.visibility = if (isOutForDelivery) View.VISIBLE else View.GONE
        
        if (isOutForDelivery) {
            holder.confirmDeliveryButton.setOnClickListener {
                onConfirmDelivery(order)
            }
            holder.reportProblemButton.setOnClickListener {
                onReportProblem(order)
            }
        }
        
        holder.itemView.setOnClickListener {
            onOrderClick(order)
        }
        
        holder.menuButton.setOnClickListener {
            onMenuClick(order)
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

    override fun getItemCount() = orders.size
    
    fun updateOrders(newOrders: List<Order>) {
        val previousSize = orders.size
        orders = newOrders
        
        // Se a lista foi completamente substituída (primeira carga ou refresh completo),
        // resetar as posições animadas para permitir nova animação
        if (newOrders.isEmpty() || previousSize == 0 || isFirstLoad) {
            animatedPositions.clear()
            isFirstLoad = false
        } else {
            // Para atualizações incrementais, manter apenas as posições que ainda existem
            animatedPositions.retainAll(newOrders.indices.toSet())
        }
        
        notifyDataSetChanged()
    }
}
