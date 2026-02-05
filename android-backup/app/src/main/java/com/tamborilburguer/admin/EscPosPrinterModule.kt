package com.tamborilburguer.admin

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.*

/**
 * Módulo React Native para impressão ESC/POS usando a biblioteca ESCPOS-ThermalPrinter-Android
 * Baseado na implementação que funciona: https://github.com/DantSu/ESCPOS-ThermalPrinter-Android
 */
class EscPosPrinterModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "EscPosPrinterModule"
        private const val EVENT_CONNECTED = "EscPosPrinterConnected"
        private const val EVENT_DISCONNECTED = "EscPosPrinterDisconnected"
        private const val EVENT_ERROR = "EscPosPrinterError"
    }

    private var currentPrinter: EscPosPrinter? = null
    private var currentConnection: BluetoothConnection? = null

    override fun getName(): String {
        return "EscPosPrinterModule"
    }

    /**
     * Lista impressoras Bluetooth pareadas
     * Retorna um array de objetos com: { name, address }
     */
    @ReactMethod
    fun scanPrinters(promise: Promise) {
        try {
            Log.d(TAG, "🔍 Iniciando scan de impressoras Bluetooth pareadas...")
            
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                promise.reject("NO_BLUETOOTH", "Bluetooth não disponível neste dispositivo")
                return
            }

            if (!bluetoothAdapter.isEnabled) {
                promise.reject("BLUETOOTH_DISABLED", "Bluetooth está desativado")
                return
            }

            val printers = BluetoothPrintersConnections()
            val printerList = printers.getList()

            if (printerList == null || printerList.isEmpty()) {
                Log.w(TAG, "⚠️ Nenhuma impressora pareada encontrada")
                promise.resolve(Arguments.createArray())
                return
            }

            val result = Arguments.createArray()
            for (printer in printerList) {
                val device = printer.getDevice()
                val printerInfo = Arguments.createMap().apply {
                    putString("name", device.name ?: "Unknown")
                    putString("address", device.address)
                }
                result.pushMap(printerInfo)
                Log.d(TAG, "📱 Impressora encontrada: ${device.name} (${device.address})")
            }

            Log.d(TAG, "✅ Scan concluído: ${result.size()} impressoras encontradas")
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao escanear impressoras", e)
            promise.reject("SCAN_ERROR", "Erro ao escanear impressoras: ${e.message}", e)
        }
    }

    /**
     * Conecta a uma impressora pelo endereço MAC
     */
    @ReactMethod
    fun connect(address: String, promise: Promise) {
        try {
            Log.d(TAG, "🔌 Tentando conectar à impressora: $address")

            // Desconectar impressora anterior se houver
            disconnect(null)

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                promise.reject("NO_BLUETOOTH", "Bluetooth não disponível")
                return
            }

            // Buscar dispositivo pareado
            val bondedDevices = bluetoothAdapter.bondedDevices
            var targetDevice: BluetoothDevice? = null

            for (device in bondedDevices) {
                if (device.address.equals(address, ignoreCase = true)) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                Log.w(TAG, "⚠️ Dispositivo não encontrado nos pareados: $address")
                promise.reject("DEVICE_NOT_FOUND", "Impressora não encontrada nos dispositivos pareados. Pareie primeiro nas configurações do Android.")
                return
            }

            // Criar conexão Bluetooth
            val connection = BluetoothConnection(targetDevice)
            
            try {
                // Conectar
                connection.connect()
                currentConnection = connection
                
                // Criar instância do EscPosPrinter
                // MPT-II: 203 DPI, 48mm width, 32 caracteres por linha
                currentPrinter = EscPosPrinter(connection, 203, 48f, 32)
                
                Log.d(TAG, "✅ Conectado com sucesso à impressora: ${targetDevice.name}")
                
                // Enviar evento de conexão
                sendEvent(EVENT_CONNECTED, Arguments.createMap().apply {
                    putString("address", address)
                    putString("name", targetDevice.name)
                })
                
                promise.resolve(Arguments.createMap().apply {
                    putString("address", address)
                    putString("name", targetDevice.name)
                })
            } catch (e: EscPosConnectionException) {
                Log.e(TAG, "❌ Erro de conexão ESC/POS", e)
                currentConnection?.disconnect()
                currentConnection = null
                promise.reject("CONNECTION_ERROR", "Erro ao conectar: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao conectar impressora", e)
            disconnect(null)
            promise.reject("CONNECT_ERROR", "Erro ao conectar: ${e.message}", e)
        }
    }

    /**
     * Desconecta a impressora atual
     */
    @ReactMethod
    fun disconnect(promise: Promise?) {
        try {
            Log.d(TAG, "🔌 Desconectando impressora...")
            
            currentPrinter?.disconnectPrinter()
            currentConnection?.disconnect()
            
            currentPrinter = null
            currentConnection = null
            
            sendEvent(EVENT_DISCONNECTED, Arguments.createMap())
            
            Log.d(TAG, "✅ Desconectado com sucesso")
            promise?.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao desconectar", e)
            promise?.reject("DISCONNECT_ERROR", "Erro ao desconectar: ${e.message}", e)
        }
    }

    /**
     * Verifica se está conectado
     */
    @ReactMethod
    fun isConnected(promise: Promise) {
        val connected = currentPrinter != null && currentConnection?.isConnected == true
        promise.resolve(connected)
    }

    /**
     * Imprime texto formatado ESC/POS
     * Formato suportado: [C] = centro, [L] = linha, [R] = direita
     * [b] = negrito, [u] = sublinhado, etc.
     */
    @ReactMethod
    fun printFormattedText(text: String, promise: Promise) {
        try {
            if (currentPrinter == null) {
                promise.reject("NOT_CONNECTED", "Nenhuma impressora conectada")
                return
            }

            Log.d(TAG, "🖨️ Imprimindo texto formatado (${text.length} caracteres)...")
            
            currentPrinter?.printFormattedText(text)
            
            Log.d(TAG, "✅ Impressão concluída com sucesso")
            promise.resolve(true)
        } catch (e: EscPosConnectionException) {
            Log.e(TAG, "❌ Erro de conexão durante impressão", e)
            promise.reject("PRINT_ERROR", "Erro de conexão: ${e.message}", e)
        } catch (e: EscPosParserException) {
            Log.e(TAG, "❌ Erro de parsing do texto", e)
            promise.reject("PRINT_ERROR", "Erro de formatação: ${e.message}", e)
        } catch (e: EscPosEncodingException) {
            Log.e(TAG, "❌ Erro de encoding", e)
            promise.reject("PRINT_ERROR", "Erro de encoding: ${e.message}", e)
        } catch (e: EscPosBarcodeException) {
            Log.e(TAG, "❌ Erro de código de barras", e)
            promise.reject("PRINT_ERROR", "Erro de código de barras: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao imprimir", e)
            promise.reject("PRINT_ERROR", "Erro ao imprimir: ${e.message}", e)
        }
    }

    /**
     * Envia evento para JavaScript
     */
    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    override fun getConstants(): Map<String, Any> {
        return mapOf(
            "EVENT_CONNECTED" to EVENT_CONNECTED,
            "EVENT_DISCONNECTED" to EVENT_DISCONNECTED,
            "EVENT_ERROR" to EVENT_ERROR
        )
    }
}
