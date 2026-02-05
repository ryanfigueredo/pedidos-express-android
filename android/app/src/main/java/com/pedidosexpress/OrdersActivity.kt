package com.pedidosexpress

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
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
    private lateinit var testPrintButton: Button
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
            onOrderClick = { order ->
                // Imprimir pedido quando clicar
                if (checkBluetoothPermissions()) {
                    printerHelper.printOrder(order)
                } else {
                    requestBluetoothPermissions()
                }
            },
            onMenuClick = { order ->
                // Por enquanto, apenas imprimir também
                if (checkBluetoothPermissions()) {
                    printerHelper.printOrder(order)
                } else {
                    requestBluetoothPermissions()
                }
            },
            onConfirmDelivery = { order ->
                // TODO: Implementar confirmação de entrega
            },
            onReportProblem = { order ->
                // TODO: Implementar reportar problema
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
}
