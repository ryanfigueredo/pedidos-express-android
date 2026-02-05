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
    @SerializedName("tenant_id") val tenantId: String? = null
)

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
                            tenantId = userData["tenant_id"]?.toString()
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
                    tenantId = userData["tenant_id"]?.toString()
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
    
    suspend fun getStats(): DashboardStats {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/stats", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao carregar stats: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val statsData = data["stats"] as Map<*, *>
            val todayData = statsData["today"] as Map<*, *>
            val weekData = statsData["week"] as Map<*, *>
            val pendingOrders = (statsData["pendingOrders"] as? Double)?.toInt() ?: 0
            val dailyStatsData = statsData["dailyStats"] as? List<*> ?: emptyList<Any>()
            
            val dailyStats = dailyStatsData.map { dayMap ->
                val day = dayMap as Map<*, *>
                DailyStat(
                    day = day["day"].toString(),
                    orders = (day["orders"] as? Double)?.toInt() ?: 0,
                    revenue = (day["revenue"] as? Double) ?: 0.0
                )
            }
            
            DashboardStats(
                today = DayStats(
                    orders = (todayData["orders"] as Double).toInt(),
                    revenue = (todayData["revenue"] as? Double) ?: 0.0
                ),
                week = WeekStats(
                    orders = (weekData["orders"] as Double).toInt(),
                    revenue = (weekData["revenue"] as? Double) ?: 0.0
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
            StoreStatus(
                isOpen = data["isOpen"] == true,
                nextOpenTime = data["nextOpenTime"]?.toString(),
                message = data["message"]?.toString(),
                lastUpdated = data["lastUpdated"].toString()
            )
        }
    }
    
    suspend fun updateStoreStatus(isOpen: Boolean) {
        return withContext(Dispatchers.IO) {
            val json = gson.toJson(mapOf("isOpen" to isOpen))
            val body = json.toRequestBody("application/json".toMediaType())
            val request = buildRequest("$API_BASE_URL/api/admin/store-hours", "POST", body)
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Erro ao atualizar status: ${response.code}")
            }
        }
    }
    
    suspend fun getPriorityConversations(): List<PriorityConversation> {
        return withContext(Dispatchers.IO) {
            val request = buildRequest("$API_BASE_URL/api/admin/priority-conversations", "GET")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw IOException("Erro ao carregar conversas: ${response.code}")
            }
            
            val data = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val conversationsData = data["conversations"] as? List<*> ?: emptyList<Any>()
            
            conversationsData.map { convMap ->
                val conv = convMap as Map<*, *>
                PriorityConversation(
                    phone = conv["phone"].toString(),
                    phoneFormatted = conv["phone_formatted"]?.toString() ?: conv["phone"].toString(),
                    whatsappUrl = conv["whatsapp_url"]?.toString() ?: "",
                    waitTime = ((conv["wait_time"] as? Double) ?: 0.0).toInt(),
                    timestamp = ((conv["timestamp"] as? Double) ?: 0.0).toLong(),
                    lastMessage = ((conv["last_message"] as? Double) ?: 0.0).toLong()
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
