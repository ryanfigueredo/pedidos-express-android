package com.pedidosexpress

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem as AndroidMenuItem
import android.widget.Button
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditOrderActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var printerHelper: PrinterHelper
    private lateinit var orderItemsRecyclerView: RecyclerView
    private lateinit var menuItemsRecyclerView: RecyclerView
    private lateinit var totalPriceText: TextView
    private lateinit var saveButton: Button
    private lateinit var printButton: Button
    private lateinit var progressBar: View
    
    private var order: Order? = null
    private val orderItems = mutableListOf<OrderItem>()
    private var menuItems: List<MenuItem> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_order)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Pedido"
        
        apiService = ApiService(this)
        printerHelper = PrinterHelper(this)
        
        val orderId = intent.getStringExtra("order_id")
        if (orderId == null) {
            Toast.makeText(this, "Pedido não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        orderItemsRecyclerView = findViewById(R.id.order_items_recycler)
        menuItemsRecyclerView = findViewById(R.id.menu_items_recycler)
        totalPriceText = findViewById(R.id.total_price)
        saveButton = findViewById(R.id.save_button)
        printButton = findViewById(R.id.print_button)
        progressBar = findViewById(R.id.progress_bar)
        
        orderItemsRecyclerView.layoutManager = LinearLayoutManager(this)
        menuItemsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        saveButton.setOnClickListener {
            saveOrder()
        }
        
        printButton.setOnClickListener {
            if (order != null && orderItems.isNotEmpty()) {
                // Atualizar pedido antes de imprimir
                val updatedOrder = order!!.copy(
                    items = orderItems,
                    totalPrice = orderItems.sumOf { it.price * it.quantity }
                )
                printerHelper.printOrder(updatedOrder)
            } else {
                Toast.makeText(this, "Adicione itens ao pedido antes de imprimir", Toast.LENGTH_SHORT).show()
            }
        }
        
        loadOrder(orderId)
        loadMenu()
    }
    
    override fun onOptionsItemSelected(item: AndroidMenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadOrder(orderId: String) {
        progressBar.visibility = android.view.View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                order = withContext(Dispatchers.IO) {
                    apiService.getOrderById(orderId)
                }
                
                orderItems.clear()
                orderItems.addAll(order!!.items)
                
                updateOrderItemsList()
                updateTotal()
                
            } catch (e: Exception) {
                android.util.Log.e("EditOrderActivity", "Erro ao carregar pedido", e)
                Toast.makeText(this@EditOrderActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun loadMenu() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                menuItems = withContext(Dispatchers.IO) {
                    apiService.getMenu()
                }
                
                menuItemsRecyclerView.adapter = MenuItemsAdapter(
                    menuItems.filter { it.available },
                    onItemClick = { menuItem: MenuItem ->
                        addItemToOrder(menuItem)
                    }
                )
                
            } catch (e: Exception) {
                android.util.Log.e("EditOrderActivity", "Erro ao carregar menu", e)
                Toast.makeText(this@EditOrderActivity, "Erro ao carregar menu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun addItemToOrder(menuItem: MenuItem) {
        val existingIndex = orderItems.indexOfFirst { it.name == menuItem.name && it.price == menuItem.price }
        
        if (existingIndex >= 0) {
            val existing = orderItems[existingIndex]
            orderItems[existingIndex] = existing.copy(quantity = existing.quantity + 1)
        } else {
            orderItems.add(OrderItem(
                name = menuItem.name,
                quantity = 1,
                price = menuItem.price
            ))
        }
        
        updateOrderItemsList()
        updateTotal()
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
        totalPriceText.text = "Total: R$ ${String.format("%.2f", total)}"
    }
    
    private fun saveOrder() {
        if (orderItems.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos um item ao pedido", Toast.LENGTH_SHORT).show()
            return
        }
        
        progressBar.visibility = android.view.View.VISIBLE
        saveButton.isEnabled = false
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val orderId = order!!.id
                
                // Atualizar pedido
                withContext(Dispatchers.IO) {
                    apiService.updateOrder(orderId, orderItems)
                }
                
                // Notificar cliente
                val displayId = (order!!.displayId ?: order!!.id.take(8)).replace("#", "")
                val itemsText = orderItems.joinToString("\n") { item ->
                    "${item.quantity}x ${item.name} - R$ ${String.format("%.2f", item.price * item.quantity)}"
                }
                val total = orderItems.sumOf { it.price * it.quantity }
                
                val message = "Olá ${order!!.customerName}! 👋\n\n" +
                        "Seu pedido $displayId foi *atualizado*:\n\n" +
                        itemsText + "\n\n" +
                        "*Total: R$ ${String.format("%.2f", total)}*\n\n" +
                        "O pedido será preparado com as alterações."
                
                try {
                    withContext(Dispatchers.IO) {
                        apiService.sendMessageToCustomer(order!!.customerPhone, message)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EditOrderActivity", "Erro ao enviar mensagem", e)
                    // Continuar mesmo se falhar o envio da mensagem
                }
                
                Toast.makeText(this@EditOrderActivity, "Pedido atualizado e cliente notificado!", Toast.LENGTH_LONG).show()
                
                // Retornar resultado para OrdersFragment atualizar
                setResult(RESULT_OK)
                finish()
                
            } catch (e: Exception) {
                android.util.Log.e("EditOrderActivity", "Erro ao salvar pedido", e)
                Toast.makeText(this@EditOrderActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = android.view.View.GONE
                saveButton.isEnabled = true
            }
        }
    }
}
