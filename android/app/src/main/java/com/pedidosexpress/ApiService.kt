package com.pedidosexpress

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.*

data class User(
    val id: String,
    val username: String,
    val name: String,
    val role: String,
    @SerializedName("tenant_id") val tenantId: String? = null,
    @SerializedName("business_type") val businessType: String? = null,
    @SerializedName("show_prices_on_bot") val showPricesOnBot: Boolean? = null,
    @SerializedName("tenant_name") val tenantName: String? = null,
    @SerializedName("tenant_slug") val tenantSlug: String? = null
) {
    val isDentista: Boolean
        get() = businessType == "DENTISTA"
    
    val isRestaurante: Boolean
        get() = businessType == "RESTAURANTE" || businessType == null
}

data class Order(
    val id: String,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("customer_phone") val customerPhone: String,
    val items: List<OrderItem>,
    @SerializedName("total_price") val totalPrice: Double,
    val status: String, // "pending" | "printed" | "finished" | "out_for_delivery"
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("display_id") val displayId: String? = null,
    @SerializedName("daily_sequence") val dailySequence: Int? = null,
    @SerializedName("order_type") val orderType: String? = null,
    @SerializedName("delivery_address") val deliveryAddress: String? = null,
    @SerializedName("payment_method") val paymentMethod: String? = null,
    val subtotal: Double? = null,
    @SerializedName("delivery_fee") val deliveryFee: Double? = null,
    @SerializedName("change_for") val changeFor: Double? = null,
    @SerializedName("print_requested_at") val printRequestedAt: String? = null
)

data class OrderItem(
    val name: String,
    val quantity: Int,
    val price: Double
)

/** Item com id para criação de pedido (POST /api/orders). */
data class CreateOrderItem(
    val id: String,
    val name: String,
    val quantity: Int,
    val price: Double
)

/** Payload para criar pedido local (garçom). */
data class CreateOrderRequest(
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("customer_phone") val customerPhone: String = "",
    val items: List<CreateOrderItem>,
    @SerializedName("total_price") val totalPrice: Double,
    @SerializedName("payment_method") val paymentMethod: String = "Não especificado",
    @SerializedName("order_type") val orderType: String = "restaurante"
)

/** Resposta do POST /api/orders. */
data class CreateOrderResponse(
    val success: Boolean,
    val order: Order? = null,
    val error: String? = null
)

data class OrdersResponse(
    val orders: List<Order>,
    val pagination: Pagination
)

data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerializedName("has_more") val hasMore: Boolean
)

class ApiService(private val context: android.content.Context) {
    private val TAG = "ApiService"
    private val client = OkHttpClient()
    private val gson = Gson()
    
    private val API_BASE_URL = "https://pedidos.dmtn.com.br"
    private val API_KEY = "tamboril-burguer-api-key-2024-secure"
    private val TENANT_ID = "tamboril-burguer"
    
    private fun getAuthHeader(): String? {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val username = prefs.getString("username", null)
        val password = prefs.getString("password", null)
        
        if (username != null && password != null) {
            val credentials = "$username:$password"
            val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            return "Basic $encoded"
        }
        return null
    }
    
    private fun getUserId(): String? {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val userJson = prefs.getString("user", null)
        if (userJson != null) {
            try {
                val user = gson.fromJson(userJson, User::class.java)
                return user.id
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao parsear user", e)
            }
        }
        return null
    }
    
    private fun buildRequest(url: String, method: String = "GET", body: RequestBody? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader("X-API-Key", API_KEY)
            .addHeader("X-Tenant-Id", TENANT_ID)
            .addHeader("Content-Type", "application/json")
        
        // Adicionar autenticação se disponível
        getAuthHeader()?.let {
            builder.addHeader("Authorization", it)
        }
        
        getUserId()?.let {
            builder.addHeader("X-User-Id", it)
        }
        
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "".toRequestBody("application/json".toMediaType()))
            "PATCH" -> builder.patch(body ?: "".toRequestBody("application/json".toMediaType()))
            else -> builder.get()
        }
        
        return builder.build()
    }
    
    suspend fun login(username: String, password: String): User {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(mapOf("username" to username, "password" to password))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/auth/mobile-login", "POST", body)
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    // Tentar endpoint alternativo
                    val altRequest = buildRequest("$API_BASE_URL/api/auth/login", "POST", body)
                    val altResponse = client.newCall(altRequest).execute()
                    val altBody = altResponse.body?.string()
                    
                    if (!altResponse.isSuccessful) {
                        throw IOException("Login falhou: ${altResponse.code}")
                    }
                    
                    val data = gson.fromJson(altBody, Map::class.java) as Map<*, *>
                    if (data["success"] == true) {
                        val userData = data["user"] as Map<*, *>
                        return@withContext User(
                            id = userData["id"].toString(),
                            username = userData["username"].toString(),
                            name = userData["name"].toString(),
                            role = userData["role"].toString(),
                            tenantId = userData["tenant_id"]?.toString(),
                            businessType = userData["business_type"]?.toString(),
                            showPricesOnBot = (userData["show_prices_on_bot"] as? Boolean) ?: true,
                            tenantName = userData["tenant_name"]?.toString(),
                            tenantSlug = userData["tenant_slug"]?.toString()
                        )
                    }
                }
                throw IOException("Login falhou: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            if (data["success"] == true) {
                val userData = data["user"] as Map<*, *>
                User(
                    id = userData["id"].toString(),
                    username = userData["username"].toString(),
                    name = userData["name"].toString(),
                    role = userData["role"].toString(),
                    tenantId = userData["tenant_id"]?.toString(),
                    businessType = userData["business_type"]?.toString(),
                    showPricesOnBot = (userData["show_prices_on_bot"] as? Boolean) ?: true,
                    tenantName = userData["tenant_name"]?.toString(),
                    tenantSlug = userData["tenant_slug"]?.toString()
                )
            } else {
                throw IOException("Credenciais inválidas")
            }
        }
    }
    
    suspend fun getAllOrders(page: Int = 1, limit: Int = 20): OrdersResponse {
        return withContext(Dispatchers.IO) {
            val url = "$API_BASE_URL/api/orders?page=$page&limit=$limit"
            val request = buildRequest(url, "GET")
            
            Log.d(TAG, "Buscando pedidos: $url")
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Resposta da API: código ${response.code}")
            Log.d(TAG, "Body: ${responseBody?.take(500)}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Erro ao carregar pedidos: ${response.code} - $responseBody")
                throw IOException("Erro ao carregar pedidos: ${response.code} - $responseBody")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val ordersData = data["orders"] as? List<*> ?: emptyList<Any>()
            val paginationData = data["pagination"] as? Map<*, *>
            
            Log.d(TAG, "Pedidos encontrados: ${ordersData.size}")
            
            if (paginationData == null) {
                throw IOException("Resposta da API inválida: pagination não encontrado")
            }
            
            val orders = ordersData.map { orderMap ->
                val order = orderMap as Map<*, *>
                val items = (order["items"] as List<*>).map { itemMap ->
                    val item = itemMap as Map<*, *>
                    OrderItem(
                        name = item["name"].toString(),
                        quantity = (item["quantity"] as Double).toInt(),
                        price = (item["price"] as Double)
                    )
                }
                
                Order(
                    id = order["id"].toString(),
                    customerName = order["customer_name"].toString(),
                    customerPhone = order["customer_phone"].toString(),
                    items = items,
                    totalPrice = (order["total_price"] as Double),
                    status = order["status"].toString(),
                    createdAt = order["created_at"].toString(),
                    displayId = order["display_id"]?.toString(),
                    dailySequence = (order["daily_sequence"] as? Double)?.toInt(),
                    orderType = order["order_type"]?.toString(),
                    deliveryAddress = order["delivery_address"]?.toString(),
                    paymentMethod = order["payment_method"]?.toString(),
                    subtotal = (order["subtotal"] as? Double),
                    deliveryFee = (order["delivery_fee"] as? Double),
                    changeFor = (order["change_for"] as? Double),
                    printRequestedAt = order["print_requested_at"]?.toString()
                )
            }
            
            OrdersResponse(
                orders = orders,
                pagination = Pagination(
                    page = (paginationData["page"] as Double).toInt(),
                    limit = (paginationData["limit"] as Double).toInt(),
                    total = (paginationData["total"] as Double).toInt(),
                    hasMore = paginationData["has_more"] == true
                )
            )
        }
    }
    
    /** Cria um pedido local (garçom no restaurante). Usa a mesma lógica de fila/impressão. */
    suspend fun createOrder(request: CreateOrderRequest): Order {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(request)
            val body = json.toRequestBody("application/json".toMediaType())
            val req = buildRequest("$API_BASE_URL/api/orders", "POST", body)
            val response = client.newCall(req).execute()
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                val err = try { gson.fromJson(responseBody, CreateOrderResponse::class.java)?.error } catch (_: Exception) { null }
                throw IOException(err ?: "Erro ao criar pedido: ${response.code}")
            }
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val orderMap = data["order"] as? Map<*, *>
                ?: throw IOException("Resposta inválida: order não encontrado")
            val items = (orderMap["items"] as? List<*>)?.map { itemMap ->
                val item = itemMap as Map<*, *>
                OrderItem(
                    name = item["name"].toString(),
                    quantity = (item["quantity"] as? Double)?.toInt() ?: (item["quantity"] as? Int) ?: 1,
                    price = (item["price"] as? Double) ?: 0.0
                )
            } ?: emptyList()
            Order(
                id = orderMap["id"].toString(),
                customerName = orderMap["customer_name"].toString(),
                customerPhone = orderMap["customer_phone"].toString(),
                items = items,
                totalPrice = (orderMap["total_price"] as? Double) ?: 0.0,
                status = orderMap["status"].toString(),
                createdAt = orderMap["created_at"].toString(),
                displayId = orderMap["display_id"]?.toString(),
                dailySequence = (orderMap["daily_sequence"] as? Double)?.toInt(),
                orderType = orderMap["order_type"]?.toString(),
                deliveryAddress = orderMap["delivery_address"]?.toString(),
                paymentMethod = orderMap["payment_method"]?.toString(),
                subtotal = null,
                deliveryFee = null,
                changeFor = null,
                printRequestedAt = orderMap["print_requested_at"]?.toString()
            )
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(mapOf("status" to status))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/orders/$orderId/status", "PATCH", body)
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Erro ao atualizar status: ${response.code}")
            }
        }
    }
    
    suspend fun sendMessageToCustomer(phone: String, message: String) {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(mapOf("phone" to phone, "message" to message))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/bot/send-message", "POST", body)
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Erro ao enviar mensagem: ${response.code}")
            }
        }
    }
    
    suspend fun updateOrder(orderId: String, items: List<OrderItem>) {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(mapOf("items" to items))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/orders/$orderId", "PATCH", body)
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao atualizar pedido: ${response.code} - $responseBody")
            }
        }
    }
    
    suspend fun getOrderById(orderId: String): Order {
        return withContext(Dispatchers.IO) {
            // Buscar da lista completa para ter o status
            val allOrders = getAllOrders(1, 100)
            val foundOrder = allOrders.orders.find { it.id == orderId }
            
            if (foundOrder != null) {
                return@withContext foundOrder
            }
            
            // Se não encontrou na lista, buscar individualmente
            val request = buildRequest("$API_BASE_URL/api/orders/$orderId", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao buscar pedido: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val orderData = data["order"] as Map<*, *>
            val itemsData = (orderData["items"] as List<*>).map { itemMap ->
                val item = itemMap as Map<*, *>
                OrderItem(
                    name = item["name"].toString(),
                    quantity = (item["quantity"] as Double).toInt(),
                    price = (item["price"] as Double)
                )
            }
            
            Order(
                id = orderData["id"].toString(),
                customerName = orderData["customer_name"].toString(),
                customerPhone = orderData["customer_phone"].toString(),
                items = itemsData,
                totalPrice = (orderData["total_price"] as Double),
                status = "pending", // Default
                createdAt = orderData["created_at"].toString(),
                displayId = orderData["display_id"]?.toString(),
                dailySequence = (orderData["daily_sequence"] as? Double)?.toInt(),
                orderType = orderData["order_type"]?.toString(),
                deliveryAddress = orderData["delivery_address"]?.toString(),
                paymentMethod = orderData["payment_method"]?.toString(),
                subtotal = null,
                deliveryFee = null,
                changeFor = null,
                printRequestedAt = null
            )
        }
    }
    
    /** Converte valor do JSON (Int ou Double) para Int. */
    private fun mapInt(map: Map<*, *>, key: String): Int {
        val v = map[key] ?: return 0
        return when (v) {
            is Number -> v.toInt()
            is Double -> v.toInt()
            else -> (v as? Number)?.toInt() ?: 0
        }
    }

    /** Converte valor do JSON (Int ou Double) para Double. */
    private fun mapDouble(map: Map<*, *>, key: String): Double {
        val v = map[key] ?: return 0.0
        return when (v) {
            is Number -> v.toDouble()
            else -> (v as? Number)?.toDouble() ?: 0.0
        }
    }

    suspend fun getStats(): DashboardStats {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/stats", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "getStats failed: ${response.code} - $responseBody")
                throw IOException("Erro ao carregar stats: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as? Map<*, *>
                ?: throw IOException("Resposta inválida")
            val statsData = data["stats"] as? Map<*, *>
                ?: throw IOException("Resposta sem stats")
            val todayData = statsData["today"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val weekData = statsData["week"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val pendingOrders = mapInt(statsData, "pendingOrders")
            val dailyStatsData = statsData["dailyStats"] as? List<*> ?: emptyList<Any>()
            
            val dailyStats = dailyStatsData.mapNotNull { dayMap ->
                val day = dayMap as? Map<*, *> ?: return@mapNotNull null
                DailyStat(
                    day = day["day"]?.toString() ?: "",
                    orders = mapInt(day, "orders"),
                    revenue = mapDouble(day, "revenue")
                )
            }
            
            DashboardStats(
                today = DayStats(
                    orders = mapInt(todayData, "orders"),
                    revenue = mapDouble(todayData, "revenue")
                ),
                week = WeekStats(
                    orders = mapInt(weekData, "orders"),
                    revenue = mapDouble(weekData, "revenue")
                ),
                pendingOrders = pendingOrders,
                dailyStats = dailyStats
            )
        }
    }
    
    suspend fun getMenu(): List<MenuItem> {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/menu", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao carregar menu: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val itemsData = data["items"] as List<*>
            
            itemsData.map { itemMap ->
                val item = itemMap as Map<*, *>
                MenuItem(
                    id = item["id"].toString(),
                    name = item["name"].toString(),
                    price = item["price"] as Double,
                    category = item["category"].toString(),
                    available = item["available"] == true
                )
            }
        }
    }
    
    suspend fun createMenuItem(id: String, name: String, price: Double, category: String, available: Boolean = true): MenuItem {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(mapOf(
                "id" to id,
                "name" to name,
                "price" to price,
                "category" to category,
                "available" to available
            ))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/admin/menu", "POST", body)
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao criar item: ${response.code} - $responseBody")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val itemData = data["item"] as Map<*, *>
            
            MenuItem(
                id = itemData["id"].toString(),
                name = itemData["name"].toString(),
                price = itemData["price"] as Double,
                category = itemData["category"].toString(),
                available = itemData["available"] == true
            )
        }
    }
    
    suspend fun updateMenuItem(id: String, name: String? = null, price: Double? = null, category: String? = null, available: Boolean? = null): MenuItem {
        return withContext(Dispatchers.IO) {
            val bodyMap = mutableMapOf<String, Any>("id" to id)
            name?.let { bodyMap["name"] = it }
            price?.let { bodyMap["price"] = it }
            category?.let { bodyMap["category"] = it }
            available?.let { bodyMap["available"] = it }
            
            val json = gson.toJson(bodyMap)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/admin/menu", "PUT", body)
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao atualizar item: ${response.code} - $responseBody")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val itemData = data["item"] as Map<*, *>
            
            MenuItem(
                id = itemData["id"].toString(),
                name = itemData["name"].toString(),
                price = itemData["price"] as Double,
                category = itemData["category"].toString(),
                available = itemData["available"] == true
            )
        }
    }
    
    suspend fun deleteMenuItem(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/menu?id=$id", "DELETE")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao deletar item: ${response.code}")
            }
            
            val responseBody = response.body?.string()
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            data["success"] == true
        }
    }
    
    suspend fun getStoreStatus(): StoreStatus {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/store-hours", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao carregar status: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val status = (data["status"] as? Map<*, *>) ?: data
            StoreStatus(
                isOpen = status["isOpen"] == true,
                nextOpenTime = status["nextOpenTime"]?.toString(),
                message = status["message"]?.toString(),
                lastUpdated = (status["lastUpdated"]?.toString() ?: "")
            )
        }
    }
    
    suspend fun updateStoreStatus(isOpen: Boolean, message: String? = null, nextOpenTime: String? = null) {
        return withContext(Dispatchers.IO) {
            val bodyMap = mutableMapOf<String, Any?>("isOpen" to isOpen)
            if (message != null) bodyMap["message"] = message
            if (nextOpenTime != null) bodyMap["nextOpenTime"] = nextOpenTime
            val json = gson.toJson(bodyMap)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/admin/store-hours", "POST", body)
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Erro ao atualizar status: ${response.code}")
            }
        }
    }
    
    /** Lista todas as conversas (inbox) - clientes que já trocaram mensagem com o bot. */
    suspend fun getInboxConversations(): List<PriorityConversation> {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/inbox/conversations", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("Erro ao carregar conversas: ${response.code}")
            }
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val conversationsData = data["conversations"] as? List<*> ?: emptyList<Any>()
            conversationsData.map { convMap ->
                val conv = convMap as Map<*, *>
                val phone = conv["customer_phone"]?.toString() ?: conv["phone"]?.toString() ?: ""
                val lastAt = conv["last_message_at"]?.toString()
                val ts = if (lastAt != null) {
                    try { java.time.Instant.parse(lastAt).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
                } else { (conv["timestamp"] as? Double)?.toLong() ?: System.currentTimeMillis() }
                val digits = phone.replace(Regex("[^0-9]"), "")
                val waNum = if (!digits.startsWith("55") && digits.length >= 10) "55$digits" else digits
                PriorityConversation(
                    phone = phone,
                    phoneFormatted = formatPhoneForDisplay(waNum),
                    whatsappUrl = "https://wa.me/$waNum",
                    waitTime = 0,
                    timestamp = ts,
                    lastMessage = ts
                )
            }
        }
    }

    private fun formatPhoneForDisplay(phone: String): String {
        var digits = phone.replace(Regex("[^0-9]"), "")
        if (digits.startsWith("55") && digits.length > 11) digits = digits.substring(2)
        return when {
            digits.length >= 11 -> "(${digits.take(2)}) ${digits.substring(2, 7)}-${digits.substring(7, 11)}"
            digits.length >= 10 -> "(${digits.take(2)}) ${digits.substring(2, 6)}-${digits.substring(6)}"
            else -> phone
        }
    }

    /** Histórico de mensagens de uma conversa (inbox). */
    suspend fun getInboxMessages(phone: String): List<InboxMessage> {
        return withContext(Dispatchers.IO) {
            var digits = phone.replace(Regex("[^0-9]"), "")
            if (!digits.startsWith("55") && digits.length >= 10) digits = "55$digits"
            val request = buildRequest("$API_BASE_URL/api/admin/inbox/conversations/${java.net.URLEncoder.encode(digits, "UTF-8")}", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (!response.isSuccessful) return@withContext emptyList()
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val list = data["messages"] as? List<*> ?: emptyList<Any>()
            list.map { m ->
                val msg = m as Map<*, *>
                InboxMessage(
                    id = msg["id"]?.toString() ?: "",
                    direction = msg["direction"]?.toString() ?: "in",
                    body = msg["body"]?.toString() ?: "",
                    createdAt = msg["created_at"]?.toString() ?: ""
                )
            }
        }
    }
    
    suspend fun sendWhatsAppMessage(phone: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(mapOf("phone" to phone, "message" to message))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/admin/send-whatsapp", "POST", body)
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao enviar mensagem: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            data["success"] == true
        }
    }
    
    suspend fun getSubscription(): Subscription? {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/subscription", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao buscar assinatura: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val subscriptionData = data["subscription"] as? Map<*, *>
            
            if (subscriptionData == null) {
                return@withContext null
            }
            
            Subscription(
                planType = subscriptionData["planType"]?.toString() ?: "basic",
                planName = subscriptionData["planName"]?.toString() ?: "Básico",
                planMessageLimit = ((subscriptionData["planMessageLimit"] as? Double) ?: 1000.0).toInt(),
                paymentDate = subscriptionData["paymentDate"]?.toString(),
                expiresAt = subscriptionData["expiresAt"]?.toString(),
                status = subscriptionData["status"]?.toString() ?: "active",
                daysUntilExpiration = (subscriptionData["daysUntilExpiration"] as? Double)?.toInt(),
                isExpired = subscriptionData["isExpired"] == true,
                isExpiringSoon = subscriptionData["isExpiringSoon"] == true,
                paymentUrl = subscriptionData["paymentUrl"]?.toString() ?: "",
                asaasSubscriptionId = subscriptionData["asaasSubscriptionId"]?.toString(),
                asaasCustomerId = subscriptionData["asaasCustomerId"]?.toString()
            )
        }
    }
}

data class DashboardStats(
    val today: DayStats,
    val week: WeekStats,
    val pendingOrders: Int = 0,
    val dailyStats: List<DailyStat> = emptyList()
)

data class DayStats(
    val orders: Int,
    val revenue: Double
)

data class WeekStats(
    val orders: Int,
    val revenue: Double
)

data class DailyStat(
    val day: String,
    val orders: Int,
    val revenue: Double
)

data class MenuItem(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val available: Boolean
)

data class StoreStatus(
    val isOpen: Boolean,
    val nextOpenTime: String?,
    val message: String?,
    val lastUpdated: String
)

data class PriorityConversation(
    val phone: String,
    @SerializedName("phone_formatted") val phoneFormatted: String,
    @SerializedName("whatsapp_url") val whatsappUrl: String,
    @SerializedName("wait_time") val waitTime: Int,
    val timestamp: Long,
    @SerializedName("last_message") val lastMessage: Long
)

data class InboxMessage(
    val id: String,
    val direction: String,
    val body: String,
    @SerializedName("created_at") val createdAt: String
)

data class Subscription(
    val planType: String,
    val planName: String,
    val planMessageLimit: Int,
    val paymentDate: String?,
    val expiresAt: String?,
    val status: String,
    val daysUntilExpiration: Int?,
    val isExpired: Boolean,
    val isExpiringSoon: Boolean,
    val paymentUrl: String,
    val asaasSubscriptionId: String?,
    val asaasCustomerId: String?
)
