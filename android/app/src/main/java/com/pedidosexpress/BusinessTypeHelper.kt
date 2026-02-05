package com.pedidosexpress

/**
 * Helper para obter labels dinâmicos baseados no tipo de negócio
 */
object BusinessTypeHelper {
    private fun getLabel(user: User?, defaultLabel: String, dentistaLabel: String): String {
        return if (user?.isDentista == true) dentistaLabel else defaultLabel
    }
    
    // Labels principais
    fun ordersLabel(user: User?): String {
        return getLabel(user, "Pedidos", "Agendamentos")
    }
    
    fun menuLabel(user: User?): String {
        return getLabel(user, "Cardápio", "Procedimentos")
    }
    
    fun orderLabel(user: User?): String {
        return getLabel(user, "Pedido", "Agendamento")
    }
    
    fun ordersTodayLabel(user: User?): String {
        return getLabel(user, "Pedidos Hoje", "Agendamentos Hoje")
    }
    
    fun itemsLabel(user: User?): String {
        return getLabel(user, "Itens", "Procedimentos")
    }
    
    fun itemLabel(user: User?): String {
        return getLabel(user, "Item", "Procedimento")
    }
    
    // Labels para segmentos
    fun pendingOrdersLabel(user: User?): String {
        return getLabel(user, "Pedidos", "Agendamentos")
    }
    
    fun outForDeliveryLabel(user: User?): String {
        return getLabel(user, "Rota", "Confirmados")
    }
    
    fun finishedOrdersLabel(user: User?): String {
        return getLabel(user, "Entregues", "Concluídos")
    }
}
