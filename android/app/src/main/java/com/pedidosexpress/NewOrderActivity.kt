package com.pedidosexpress

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem as AndroidMenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class NewOrderActivity : AppCompatActivity() {

    private lateinit var apiService: ApiService
    private lateinit var customerName: android.widget.EditText
    private lateinit var customerPhone: android.widget.EditText
    private lateinit var menuRecyclerView: RecyclerView
    private lateinit var orderItemsRecyclerView: RecyclerView
    private lateinit var totalLabel: TextView
    private lateinit var btnRegister: android.widget.Button
    private lateinit var progressOverlay: View

    private val orderItems = mutableListOf<OrderItem>()
    private var menuItems: List<com.pedidosexpress.MenuItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_order)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Novo pedido (local)"

        apiService = ApiService(this)

        customerName = findViewById(R.id.customer_name)
        customerPhone = findViewById(R.id.customer_phone)
        menuRecyclerView = findViewById(R.id.menu_recycler)
        orderItemsRecyclerView = findViewById(R.id.order_items_recycler)
        totalLabel = findViewById(R.id.total_label)
        btnRegister = findViewById(R.id.btn_register_order)
        progressOverlay = findViewById(R.id.progress_overlay)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener { onSupportNavigateUp() }

        menuRecyclerView.layoutManager = LinearLayoutManager(this)
        orderItemsRecyclerView.layoutManager = LinearLayoutManager(this)

        btnRegister.setOnClickListener { registerOrder() }

        loadMenu()
        updateOrderItemsList()
        updateTotal()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: AndroidMenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadMenu() {
        progressOverlay.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            try {
                menuItems = withContext(Dispatchers.IO) {
                    apiService.getMenu()
                }
                val available = menuItems.filter { it.available }
                menuRecyclerView.adapter = MenuItemsAdapter(available) { menuItem ->
                    addItemFromMenu(menuItem)
                }
            } catch (e: Exception) {
                android.util.Log.e("NewOrderActivity", "Erro ao carregar menu", e)
                Toast.makeText(this@NewOrderActivity, "Erro ao carregar cardápio: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressOverlay.visibility = View.GONE
            }
        }
    }

    private fun addItemFromMenu(menuItem: com.pedidosexpress.MenuItem) {
        val existingIndex = orderItems.indexOfFirst { it.name == menuItem.name && it.price == menuItem.price }
        val newQty: Int
        if (existingIndex >= 0) {
            val existing = orderItems[existingIndex]
            newQty = existing.quantity + 1
            orderItems[existingIndex] = existing.copy(quantity = newQty)
        } else {
            newQty = 1
            orderItems.add(
                OrderItem(
                    name = menuItem.name,
                    quantity = 1,
                    price = menuItem.price
                )
            )
        }
        updateOrderItemsList()
        updateTotal()
        // Feedback: toast + scroll para o item no carrinho
        val msg = if (newQty == 1) "${menuItem.name} adicionado" else "${newQty}x ${menuItem.name} no pedido"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        val pos = orderItems.indexOfFirst { it.name == menuItem.name && it.price == menuItem.price }
        if (pos >= 0) {
            orderItemsRecyclerView.post {
                orderItemsRecyclerView.smoothScrollToPosition(pos)
            }
        }
    }

    private fun updateOrderItemsList() {
        orderItemsRecyclerView.adapter = OrderItemsEditAdapter(
            orderItems,
            onQuantityChange = { position, newQuantity ->
                if (newQuantity <= 0) {
                    orderItems.removeAt(position)
                } else {
                    val item = orderItems[position]
                    orderItems[position] = item.copy(quantity = newQuantity)
                }
                updateOrderItemsList()
                updateTotal()
            },
            onRemove = { position ->
                orderItems.removeAt(position)
                updateOrderItemsList()
                updateTotal()
            }
        )
    }

    private fun updateTotal() {
        val total = orderItems.sumOf { it.price * it.quantity }
        totalLabel.text = "Total: R$ ${"%.2f".format(Locale("pt", "BR"), total)}"
    }

    private fun registerOrder() {
        val name = customerName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Informe o nome do cliente", Toast.LENGTH_SHORT).show()
            return
        }
        if (orderItems.isEmpty()) {
            Toast.makeText(this, "Escolha ao menos um item do cardápio", Toast.LENGTH_SHORT).show()
            return
        }
        val total = orderItems.sumOf { it.price * it.quantity }
        val createItems = orderItems.mapIndexed { i, item ->
            CreateOrderItem(
                id = "item-$i",
                name = item.name,
                quantity = item.quantity,
                price = item.price
            )
        }
        val request = CreateOrderRequest(
            customerName = name,
            customerPhone = customerPhone.text.toString().trim(),
            items = createItems,
            totalPrice = total,
            paymentMethod = "Não especificado",
            orderType = "restaurante"
        )

        progressOverlay.visibility = View.VISIBLE
        btnRegister.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val order = withContext(Dispatchers.IO) { apiService.createOrder(request) }
                setResult(RESULT_OK, Intent().putExtra("order_id", order.id).putExtra("order_created", true))
                Toast.makeText(this@NewOrderActivity, "Pedido ${(order.displayId ?: order.id).replace("#", "")} registrado", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                progressOverlay.visibility = View.GONE
                btnRegister.isEnabled = true
                Toast.makeText(this@NewOrderActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
