package com.pedidosexpress

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrdersActivity : AppCompatActivity() {
    private lateinit var ordersRecyclerView: RecyclerView
    private lateinit var testPrintButton: ImageButton
    private lateinit var progressBar: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var printerHelper: PrinterHelper
    private lateinit var apiService: ApiService
    private lateinit var authService: AuthService
    private lateinit var ordersAdapter: OrdersAdapter
    
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadOrders(true)
            handler.postDelayed(this, 5000) // Atualizar a cada 5 segundos
        }
    }
    
    private val printedOrderIds = mutableSetOf<String>()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)
        
        // Verificar se está logado
        authService = AuthService(this)
        if (!authService.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        apiService = ApiService(this)
        printerHelper = PrinterHelper(this)
        
        ordersRecyclerView = findViewById(R.id.orders_recycler)
        testPrintButton = findViewById(R.id.test_print_button)
        progressBar = findViewById(R.id.progress_bar)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        
        ordersRecyclerView.layoutManager = LinearLayoutManager(this)
        
        ordersAdapter = OrdersAdapter(
            emptyList(),
            onPrint = { order ->
                if (checkBluetoothPermissions()) {
                    printerHelper.printOrder(order)
                } else {
                    requestBluetoothPermissions()
                }
            },
            onSendToDelivery = { order ->
                AlertDialog.Builder(this)
                    .setTitle("Enviar para entrega")
                    .setMessage("Enviar o pedido ${(order.displayId ?: order.id.take(8)).replace("#", "")} para entrega?")
                    .setPositiveButton("Enviar") { _, _ ->
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    apiService.updateOrderStatus(order.id, "out_for_delivery")
                                    val msg = "Olá ${order.customerName}! 🛵\n\nSeu pedido ${(order.displayId ?: order.id.take(8)).replace("#", "")} *saiu para entrega*!\n\nEm breve chegaremos aí."
                                    apiService.sendMessageToCustomer(order.customerPhone, msg)
                                }
                                Toast.makeText(this@OrdersActivity, "Cliente avisado. Pedido na aba Rota.", Toast.LENGTH_SHORT).show()
                                loadOrders(false)
                            } catch (e: Exception) {
                                Toast.makeText(this@OrdersActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            },
            onEdit = { order ->
                startActivity(Intent(this, EditOrderActivity::class.java).putExtra("order_id", order.id))
            },
            onWhatsApp = { order ->
                openWhatsAppForCustomer(order.customerPhone)
            },
            onConfirmDelivery = { order ->
                AlertDialog.Builder(this)
                    .setTitle("Confirmar Entrega")
                    .setMessage("Confirmar que o pedido foi entregue?")
                    .setPositiveButton("Confirmar") { _, _ ->
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                withContext(Dispatchers.IO) { apiService.updateOrderStatus(order.id, "finished") }
                                Toast.makeText(this@OrdersActivity, "Entrega confirmada!", Toast.LENGTH_SHORT).show()
                                loadOrders(false)
                            } catch (e: Exception) {
                                Toast.makeText(this@OrdersActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            },
            onReportProblem = { order ->
                Toast.makeText(this, "Reportar problema: em breve", Toast.LENGTH_SHORT).show()
            }
        )
        ordersRecyclerView.adapter = ordersAdapter
        
        swipeRefresh.setOnRefreshListener {
            loadOrders(false)
        }
        
        testPrintButton.setOnClickListener {
            if (checkBluetoothPermissions()) {
                printerHelper.testPrint()
            } else {
                requestBluetoothPermissions()
            }
        }
        
        // Solicitar permissões ao abrir a tela
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
        }
        
        // Carregar pedidos inicialmente
        loadOrders(false)
        
        // Iniciar atualização automática
        handler.postDelayed(refreshRunnable, 5000)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }
    
    private fun loadOrders(silent: Boolean) {
        if (!silent) {
            progressBar.visibility = View.VISIBLE
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getAllOrders(1, 20)
                }
                
                android.util.Log.d("OrdersActivity", "Pedidos carregados: ${response.orders.size}")
                
                // Ordenar por data (mais recentes primeiro)
                val sortedOrders = response.orders.sortedByDescending { 
                    it.createdAt 
                }
                
                ordersAdapter.updateOrders(sortedOrders)
                
                if (sortedOrders.isEmpty() && !silent) {
                    Toast.makeText(this@OrdersActivity, "Nenhum pedido encontrado", Toast.LENGTH_SHORT).show()
                }
                
                // Detectar e imprimir novos pedidos automaticamente
                detectAndPrintNewOrders(sortedOrders)
                
            } catch (e: Exception) {
                android.util.Log.e("OrdersActivity", "Erro ao carregar pedidos", e)
                if (!silent) {
                    Toast.makeText(this@OrdersActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }
    
    private fun detectAndPrintNewOrders(orders: List<Order>) {
        if (!checkBluetoothPermissions()) return
        
        CoroutineScope(Dispatchers.Main).launch {
            orders.forEach { order ->
                // Imprimir se:
                // 1. Status é pending E (print_requested_at existe OU é novo pedido)
                // 2. Ainda não foi impresso
                if (order.status == "pending" && 
                    !printedOrderIds.contains(order.id) &&
                    (order.printRequestedAt != null || !printedOrderIds.contains(order.id))) {
                    
                    printedOrderIds.add(order.id)
                    
                    // Imprimir após um pequeno delay para evitar múltiplas impressões
                    handler.postDelayed({
                        printerHelper.printOrder(order)
                        
                        // Atualizar status para "printed" após imprimir
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                apiService.updateOrderStatus(order.id, "printed")
                            } catch (e: Exception) {
                                // Ignorar erro silenciosamente
                            }
                        }
                    }, 1000)
                }
            }
        }
    }
    
    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 e anteriores
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            // Android 11 e anteriores
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissões Bluetooth concedidas!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissões Bluetooth são necessárias para imprimir", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Abre o WhatsApp para conversar com o cliente (número do pedido). */
    private fun openWhatsAppForCustomer(phone: String?) {
        val raw = phone?.trim() ?: return
        val digits = raw.replace(Regex("[^0-9]"), "")
        if (digits.isEmpty()) return
        val uri = Uri.parse("https://wa.me/$digits")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
