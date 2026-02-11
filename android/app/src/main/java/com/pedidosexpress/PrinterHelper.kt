package com.pedidosexpress

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import kotlinx.coroutines.*

class PrinterHelper(private val context: Context) {
    private val TAG = "PrinterHelper"
    private var currentPrinter: EscPosPrinter? = null
    private var currentConnection: BluetoothConnection? = null
    
    /**
     * Verifica se tem permissões Bluetooth
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Lista impressoras Bluetooth pareadas
     */
    fun getPairedPrinters(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Sem permissões Bluetooth")
            return emptyList()
        }
        
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth não disponível")
            return emptyList()
        }
        
        val pairedDevices = bluetoothAdapter.bondedDevices
        return pairedDevices.filter { device ->
            // Filtrar apenas impressoras (geralmente têm "printer" ou "POS" no nome)
            device.name.contains("printer", ignoreCase = true) ||
            device.name.contains("POS", ignoreCase = true) ||
            device.name.contains("thermal", ignoreCase = true)
        }
    }
    
    /**
     * Conecta à impressora
     */
    fun connectToPrinter(device: BluetoothDevice, callback: (Boolean, String?) -> Unit) {
        if (!hasBluetoothPermissions()) {
            callback(false, "Permissões Bluetooth necessárias")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = BluetoothPrintersConnections.selectFirstPaired()
                    ?: throw Exception("Nenhuma impressora pareada encontrada")
                
                currentConnection = connection
                currentPrinter = EscPosPrinter(connection, 203, 48f, 32)
                
                withContext(Dispatchers.Main) {
                    callback(true, null)
                    Toast.makeText(context, "Conectado à impressora!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar", e)
                withContext(Dispatchers.Main) {
                    callback(false, e.message)
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Imprime texto formatado
     */
    fun printFormattedText(text: String, callback: ((Boolean, String?) -> Unit)? = null) {
        if (!hasBluetoothPermissions()) {
            callback?.invoke(false, "Permissões Bluetooth necessárias")
            Toast.makeText(context, "Permissões Bluetooth necessárias", Toast.LENGTH_SHORT).show()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (currentPrinter == null) {
                    // Tentar conectar automaticamente
                    val connection = BluetoothPrintersConnections.selectFirstPaired()
                        ?: throw Exception("Nenhuma impressora pareada. Conecte primeiro.")
                    
                    currentConnection = connection
                    currentPrinter = EscPosPrinter(connection, 203, 48f, 32)
                }
                
                Log.d(TAG, "Imprimindo texto (${text.length} caracteres)...")
                currentPrinter?.printFormattedText(text)
                
                withContext(Dispatchers.Main) {
                    callback?.invoke(true, null)
                    Toast.makeText(context, "Impressão concluída!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: EscPosConnectionException) {
                Log.e(TAG, "Erro de conexão", e)
                withContext(Dispatchers.Main) {
                    callback?.invoke(false, "Erro de conexão: ${e.message}")
                    Toast.makeText(context, "Erro de conexão: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: EscPosParserException) {
                Log.e(TAG, "Erro de parsing", e)
                withContext(Dispatchers.Main) {
                    callback?.invoke(false, "Erro de formatação: ${e.message}")
                    Toast.makeText(context, "Erro de formatação: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao imprimir", e)
                withContext(Dispatchers.Main) {
                    callback?.invoke(false, e.message)
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Teste de impressão - mínimo para não gastar folha
     */
    fun testPrint() {
        val testText = "[C]Funcionando"
        printFormattedText(testText)
    }
    
    /**
     * Imprime um pedido formatado
     */
    fun printOrder(order: Order) {
        val orderText = formatOrder(order)
        printFormattedText(orderText)
    }
    
    /**
     * Normaliza nome: "Hambúrguer"/"Hamburguer" → "Hamb." para exibição no ticket.
     */
    private fun removeHamburguerPrefix(productName: String): String {
        return productName
            .replace(Regex("[Hh]amb[uú]rguer", RegexOption.IGNORE_CASE), "Hamb.")
            .trim()
    }
    
    /**
     * Formata pedido para impressão
     */
    private fun formatOrder(order: Order): String {
        val displayId = (order.displayId ?: order.id.take(8)).replace("#", "")
        
        // Converter data para horário Brasil (GMT-3)
        val timeStr = try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = dateFormat.parse(order.createdAt)
            
            val brazilTimeZone = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            timeFormat.timeZone = brazilTimeZone
            timeFormat.format(date ?: java.util.Date())
        } catch (e: Exception) {
            order.createdAt.take(5) // Fallback: pegar apenas HH:mm se houver
        }
        
        // Determinar endereço ou tipo de pedido
        val addressInfo = when {
            order.deliveryAddress != null && order.deliveryAddress.isNotEmpty() -> {
                "End: ${order.deliveryAddress}"
            }
            order.orderType == "dine_in" || order.orderType == "restaurant" -> {
                "Comer no restaurante"
            }
            else -> {
                "Comer no restaurante" // Default
            }
        }
        
        return buildString {
            appendLine("[C]<b>PEDIDO $displayId</b>")
            appendLine()
            appendLine("[L]Cliente: ${order.customerPhone}")
            appendLine("[L]Horário: $timeStr")
            appendLine("[L]$addressInfo")
            appendLine()
            appendLine("[L]<font size='big'><b>ITENS:</b></font>")
            order.items.forEach { item: OrderItem ->
                val productName = removeHamburguerPrefix(item.name)
                appendLine("[L]<font size='big'>${item.quantity}x $productName</font>")
            }
            appendLine()
            appendLine()
        }
    }
    
    /**
     * Desconecta da impressora
     */
    fun disconnect() {
        try {
            currentConnection?.disconnect()
            currentConnection = null
            currentPrinter = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desconectar", e)
        }
    }
}
