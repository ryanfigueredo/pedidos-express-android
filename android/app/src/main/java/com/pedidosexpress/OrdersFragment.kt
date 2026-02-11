package com.pedidosexpress

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrdersFragment : Fragment() {
    private lateinit var ordersRecyclerView: RecyclerView
    private lateinit var testPrintButton: ImageButton
    private lateinit var progressBar: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var ordersTabs: TabLayout
    private lateinit var printerHelper: PrinterHelper
    private lateinit var apiService: ApiService
    private lateinit var authService: AuthService
    
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadOrders(true)
            handler.postDelayed(this, 5000)
        }
    }
    
    private val printedOrderIds = mutableSetOf<String>()
    private var allOrders: List<Order> = emptyList()
    private var currentSection: String = "pending" // pending, out_for_delivery, finished
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val REQUEST_EDIT_ORDER = 1002
        private const val REQUEST_NEW_ORDER = 1003
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_orders, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        apiService = ApiService(requireContext())
        printerHelper = PrinterHelper(requireContext())
        authService = AuthService(requireContext())
        
        // Atualizar título dinamicamente
        val user = authService.getUser()
        activity?.title = BusinessTypeHelper.ordersLabel(user)
        
        ordersRecyclerView = view.findViewById(R.id.orders_recycler)
        testPrintButton = view.findViewById(R.id.test_print_button)
        progressBar = view.findViewById(R.id.progress_bar)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        ordersTabs = view.findViewById(R.id.orders_tabs)
        val fabNewOrder = view.findViewById<FloatingActionButton>(R.id.fab_new_order)
        fabNewOrder.setOnClickListener {
            startActivityForResult(Intent(requireContext(), NewOrderActivity::class.java), REQUEST_NEW_ORDER)
        }
        
        ordersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        val ordersAdapter = OrdersAdapter(
            emptyList(),
            onPrint = { order ->
                if (checkBluetoothPermissions()) {
                    printerHelper.printOrder(order)
                } else {
                    requestBluetoothPermissions()
                }
            },
            onSendToDelivery = { order ->
                showSendToDeliveryConfirm(order)
            },
            onEdit = { order ->
                showEditOrderDialog(order)
            },
            onWhatsApp = { order ->
                openWhatsAppForCustomer(order.customerPhone)
            },
            onConfirmDelivery = { order ->
                confirmDelivery(order)
            },
            onReportProblem = { order ->
                reportDeliveryProblem(order)
            }
        )
        ordersRecyclerView.adapter = ordersAdapter
        
        ordersTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> currentSection = "pending"
                    1 -> currentSection = "out_for_delivery"
                    2 -> currentSection = "finished"
                }
                filterOrdersBySection()
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Selecionar tab de Pedidos por padrão
        ordersTabs.getTabAt(0)?.select()
        
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
        
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
        }
        
        loadOrders(false)
        handler.postDelayed(refreshRunnable, 5000)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
    }
    
    private fun loadOrders(silent: Boolean) {
        if (!isAdded || context == null) return
        
        if (!silent) {
            progressBar.visibility = View.VISIBLE
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getAllOrders(1, 20)
                }
                
                if (!isAdded || context == null) return@launch
                
                android.util.Log.d("OrdersFragment", "Pedidos carregados: ${response.orders.size}")
                
                val sortedOrders = response.orders.sortedByDescending { it.createdAt }
                allOrders = sortedOrders
                filterOrdersBySection()
                
                if (sortedOrders.isEmpty() && !silent) {
                    context?.let { ctx ->
                        Toast.makeText(ctx, "Nenhum pedido encontrado", Toast.LENGTH_SHORT).show()
                    }
                }
                
                detectAndPrintNewOrders(sortedOrders)
                
            } catch (e: Exception) {
                android.util.Log.e("OrdersFragment", "Erro ao carregar pedidos", e)
                if (!silent && isAdded && context != null) {
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }
    
    private fun detectAndPrintNewOrders(orders: List<Order>) {
        if (!isAdded || context == null) return
        if (!checkBluetoothPermissions()) return
        
        CoroutineScope(Dispatchers.Main).launch {
            if (!isAdded || context == null) return@launch
            
            orders.forEach { order ->
                if (order.status == "pending" && 
                    !printedOrderIds.contains(order.id) &&
                    (order.printRequestedAt != null || !printedOrderIds.contains(order.id))) {
                    
                    printedOrderIds.add(order.id)
                    
                    handler.postDelayed({
                        if (isAdded && context != null) {
                            printerHelper.printOrder(order)
                            
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    apiService.updateOrderStatus(order.id, "printed")
                                } catch (e: Exception) {
                                    android.util.Log.e("OrdersFragment", "Erro ao marcar como impresso", e)
                                }
                            }
                        }
                    }, 1000)
                }
            }
        }
    }
    
    private fun checkBluetoothPermissions(): Boolean {
        val ctx = context ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        requestPermissions(permissions, PERMISSION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE && isAdded && context != null) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(context, "Permissões Bluetooth concedidas!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permissões Bluetooth são necessárias para imprimir", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showSendToDeliveryConfirm(order: Order) {
        val displayId = (order.displayId ?: order.id.take(8)).replace("#", "")
        AlertDialog.Builder(requireContext())
            .setTitle("Enviar para entrega")
            .setMessage("Enviar o pedido $displayId para entrega?")
            .setPositiveButton("Enviar") { _, _ ->
                updateOrderStatus(order, "out_for_delivery", null)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Abre o app WhatsApp para conversar com o cliente (número do pedido). */
    private fun openWhatsAppForCustomer(phone: String) {
        var digits = phone.replace(Regex("[^0-9]"), "")
        if (digits.length == 10 || digits.length == 11) {
            if (!digits.startsWith("55")) digits = "55$digits"
        }
        if (digits.isEmpty()) {
            Toast.makeText(requireContext(), "Telefone do cliente não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("https://wa.me/$digits")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun showUpdateStatusDialog(order: Order) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_order_status, null)
        
        val statusGroup = dialogView.findViewById<RadioGroup>(R.id.status_group)
        val customMessageLayout = dialogView.findViewById<ViewGroup>(R.id.custom_message_layout)
        val messageInput = dialogView.findViewById<EditText>(R.id.message_input)
        
        statusGroup.setOnCheckedChangeListener { _, checkedId ->
            val isAlterado = checkedId == R.id.status_altered
            customMessageLayout.visibility = if (isAlterado) View.VISIBLE else View.GONE
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Atualizar Status do Pedido")
            .setView(dialogView)
            .setPositiveButton("Confirmar") { _, _ ->
                val selectedId = statusGroup.checkedRadioButtonId
                val status = when (selectedId) {
                    R.id.status_delivery -> "out_for_delivery"
                    R.id.status_cancelled -> "cancelled"
                    R.id.status_altered -> "altered"
                    else -> return@setPositiveButton
                }
                
                val customMessage = messageInput.text?.toString()?.trim()
                
                if (status == "altered" && customMessage.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Digite a mensagem de alteração", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                updateOrderStatus(order, status, customMessage)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun updateOrderStatus(order: Order, status: String, customMessage: String?) {
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.updateOrderStatus(order.id, status)
                }
                
                // Ao enviar para entrega: avisar o cliente e pedido vai para a aba Rota
                if (status == "out_for_delivery") {
                    try {
                        val displayId = (order.displayId ?: order.id.take(8)).replace("#", "")
                        val message = "Olá ${order.customerName}! 🛵\n\nSeu pedido $displayId *saiu para entrega*!\n\nEm breve chegaremos aí."
                        withContext(Dispatchers.IO) {
                            apiService.sendMessageToCustomer(order.customerPhone, message)
                        }
                        Toast.makeText(requireContext(), "Cliente avisado. Pedido na aba Rota.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("OrdersFragment", "Erro ao avisar cliente", e)
                        Toast.makeText(requireContext(), "Pedido enviado para entrega. Erro ao avisar cliente: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                // Se for status "altered", enviar mensagem ao cliente
                else if (status == "altered" && !customMessage.isNullOrEmpty()) {
                    try {
                        val displayId = (order.displayId ?: order.id.take(8)).replace("#", "")
                        val message = "Olá ${order.customerName}! 👋\n\n" +
                                "Sobre seu pedido $displayId:\n\n" +
                                "$customMessage\n\n" +
                                "Por favor, responda *SIM* ou *NÃO* para confirmarmos a alteração."
                        
                        withContext(Dispatchers.IO) {
                            apiService.sendMessageToCustomer(order.customerPhone, message)
                        }
                        
                        Toast.makeText(requireContext(), "Status atualizado e mensagem enviada ao cliente!", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        android.util.Log.e("OrdersFragment", "Erro ao enviar mensagem", e)
                        Toast.makeText(requireContext(), "Status atualizado, mas erro ao enviar mensagem: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val statusText = when (status) {
                        "out_for_delivery" -> "Pedido saiu para entrega"
                        "cancelled" -> "Pedido cancelado"
                        else -> "Status atualizado"
                    }
                    Toast.makeText(requireContext(), statusText, Toast.LENGTH_SHORT).show()
                }
                
                loadOrders(false)
                
            } catch (e: Exception) {
                android.util.Log.e("OrdersFragment", "Erro ao atualizar status", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun showEditOrderDialog(order: Order) {
        val intent = android.content.Intent(requireContext(), EditOrderActivity::class.java)
        intent.putExtra("order_id", order.id)
        startActivityForResult(intent, REQUEST_EDIT_ORDER)
    }
    
    private fun filterOrdersBySection() {
        val filteredOrders = when (currentSection) {
            "pending" -> allOrders.filter { it.status == "pending" || it.status == "printed" }
            "out_for_delivery" -> allOrders.filter { it.status == "out_for_delivery" }
            "finished" -> allOrders.filter { it.status == "finished" || it.status == "cancelled" }
            else -> allOrders
        }
        (ordersRecyclerView.adapter as? OrdersAdapter)?.updateOrders(filteredOrders)
        updateTabCounters()
    }
    
    private fun updateTabCounters() {
        val pendingCount = allOrders.count { it.status == "pending" || it.status == "printed" }
        val deliveryCount = allOrders.count { it.status == "out_for_delivery" }
        val finishedCount = allOrders.count { it.status == "finished" || it.status == "cancelled" }
        
        val user = authService.getUser()
        ordersTabs.getTabAt(0)?.text = "${BusinessTypeHelper.pendingOrdersLabel(user)} ($pendingCount)"
        ordersTabs.getTabAt(1)?.text = "${BusinessTypeHelper.outForDeliveryLabel(user)} ($deliveryCount)"
        ordersTabs.getTabAt(2)?.text = "${BusinessTypeHelper.finishedOrdersLabel(user)} ($finishedCount)"
    }
    
    private fun confirmDelivery(order: Order) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Entrega")
            .setMessage("Confirmar que o pedido ${(order.displayId ?: order.id.take(8)).replace("#", "")} foi entregue?")
            .setPositiveButton("Confirmar") { _, _ ->
                updateOrderStatus(order, "finished", null)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun reportDeliveryProblem(order: Order) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delivery_problem, null)
        val problemInput = dialogView.findViewById<EditText>(R.id.problem_input)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Reportar Problema")
            .setView(dialogView)
            .setPositiveButton("Enviar") { _, _ ->
                val problem = problemInput.text?.toString()?.trim()
                if (problem.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Descreva o problema", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Enviar mensagem ao cliente sobre o problema
                val displayId = (order.displayId ?: order.id.take(8)).replace("#", "")
                val message = "Olá ${order.customerName}! 👋\n\n" +
                        "Sobre seu pedido $displayId:\n\n" +
                        "Encontramos um problema: $problem\n\n" +
                        "Estamos resolvendo e entraremos em contato em breve."
                
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        withContext(Dispatchers.IO) {
                            apiService.sendMessageToCustomer(order.customerPhone, message)
                        }
                        Toast.makeText(requireContext(), "Problema reportado e cliente notificado", Toast.LENGTH_LONG).show()
                        loadOrders(false)
                    } catch (e: Exception) {
                        android.util.Log.e("OrdersFragment", "Erro ao reportar problema", e)
                        Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_ORDER && resultCode == android.app.Activity.RESULT_OK) {
            loadOrders(false)
        }
        if (requestCode == REQUEST_NEW_ORDER && resultCode == android.app.Activity.RESULT_OK) {
            // Recarregar lista após registrar pedido local; mesma lógica de impressão será aplicada
            loadOrders(false)
        }
    }
}
