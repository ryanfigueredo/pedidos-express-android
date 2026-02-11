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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OrdersAdapter(
    private var orders: List<Order>,
    private val onPrint: (Order) -> Unit,
    private val onSendToDelivery: (Order) -> Unit,
    private val onEdit: (Order) -> Unit,
    private val onWhatsApp: (Order) -> Unit,
    private val onConfirmDelivery: (Order) -> Unit,
    private val onReportProblem: (Order) -> Unit
) : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {
    
    private val animatedPositions = mutableSetOf<Int>()
    private var isFirstLoad = true

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR")).apply {
        timeZone = TimeZone.getDefault()
    }
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).apply {
        timeZone = TimeZone.getDefault()
    }

    class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val orderId: TextView = itemView.findViewById(R.id.order_id)
        val orderTime: TextView = itemView.findViewById(R.id.order_time)
        val customerName: TextView = itemView.findViewById(R.id.customer_name)
        val customerPhone: TextView = itemView.findViewById(R.id.customer_phone)
        val orderTotal: TextView = itemView.findViewById(R.id.order_total)
        val orderStatus: TextView = itemView.findViewById(R.id.order_status)
        val orderActionsLayout: LinearLayout = itemView.findViewById(R.id.order_actions_layout)
        val btnImprimir: Button = itemView.findViewById(R.id.btn_imprimir)
        val btnEnviar: Button = itemView.findViewById(R.id.btn_enviar)
        val btnEditar: Button = itemView.findViewById(R.id.btn_editar)
        val btnWhatsapp: ImageButton = itemView.findViewById(R.id.btn_whatsapp)
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
        val displayId = (order.displayId ?: order.id.take(8)).replace("#", "")
        holder.orderId.text = "Pedido $displayId"
        holder.orderTime.text = formatOrderTime(order.createdAt)
        holder.customerName.text = "Cliente: ${order.customerName}"
        val phoneDisplay = formatPhone(order.customerPhone)
        holder.customerPhone.text = "Tel: $phoneDisplay"
        holder.customerPhone.visibility = if (phoneDisplay.isNotBlank() && order.customerPhone != "local") View.VISIBLE else View.GONE
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
        
        // Mostrar botões de ação (Imprimir, Enviar, Editar, Atendimento) para pedidos não em rota
        val isOutForDelivery = order.status == "out_for_delivery"
        holder.orderActionsLayout.visibility = if (isOutForDelivery) View.GONE else View.VISIBLE
        holder.deliveryActionsLayout.visibility = if (isOutForDelivery) View.VISIBLE else View.GONE
        
        if (isOutForDelivery) {
            holder.confirmDeliveryButton.setOnClickListener {
                onConfirmDelivery(order)
            }
            holder.reportProblemButton.setOnClickListener {
                onReportProblem(order)
            }
        } else {
            holder.btnImprimir.setOnClickListener { onPrint(order) }
            holder.btnEnviar.setOnClickListener { onSendToDelivery(order) }
            holder.btnEditar.setOnClickListener { onEdit(order) }
        }
        holder.btnWhatsapp.setOnClickListener { onWhatsApp(order) }
        
        // Aplicar animação fade-in + slide-up com staggered delay
        if (!animatedPositions.contains(position)) {
            animatedPositions.add(position)
            animateItemAppearance(holder.itemView, position)
        }
    }
    
    private fun formatPhone(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 11 && digits.startsWith("55") -> "(${digits.substring(2, 4)}) ${digits.substring(4, 9)}-${digits.substring(9)}"
            digits.length == 11 -> "(${digits.take(2)}) ${digits.substring(2, 7)}-${digits.substring(7)}"
            digits.length == 10 -> "(${digits.take(2)}) ${digits.substring(2, 6)}-${digits.substring(6)}"
            digits.isNotEmpty() -> phone
            else -> ""
        }
    }

    private fun formatOrderTime(isoDate: String): String {
        return try {
            val normalized = if (isoDate.length >= 19) isoDate.substring(0, 19) else isoDate
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
                timeZone = if (isoDate.endsWith("Z")) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
            }
            val parsed = parser.parse(normalized)
            if (parsed != null) {
                val today = java.util.Calendar.getInstance()
                val orderCal = java.util.Calendar.getInstance().apply { time = parsed }
                val sameDay = today.get(java.util.Calendar.YEAR) == orderCal.get(java.util.Calendar.YEAR) &&
                    today.get(java.util.Calendar.DAY_OF_YEAR) == orderCal.get(java.util.Calendar.DAY_OF_YEAR)
                if (sameDay) "Pedido às ${timeFormat.format(parsed)}" else "Pedido em ${dateFormat.format(parsed)}"
            } else "Pedido às --:--"
        } catch (_: Exception) {
            "Pedido às --:--"
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
