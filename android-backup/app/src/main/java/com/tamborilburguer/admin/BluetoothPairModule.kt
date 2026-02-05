package com.tamborilburguer.admin

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import android.os.Build
import androidx.annotation.RequiresApi

class BluetoothPairModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "BluetoothPairModule"
    }

    @ReactMethod
    fun pairDevice(address: String, promise: Promise) {
        try {
            Log.d("BluetoothPairModule", "🔗 pairDevice() chamado para: $address")
            
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.e("BluetoothPairModule", "❌ BluetoothAdapter é null")
                promise.reject("NO_BLUETOOTH", "Bluetooth não disponível")
                return
            }
            Log.d("BluetoothPairModule", "✅ BluetoothAdapter obtido")

            if (!bluetoothAdapter.isEnabled) {
                Log.e("BluetoothPairModule", "❌ Bluetooth está desabilitado")
                promise.reject("BLUETOOTH_DISABLED", "Bluetooth está desabilitado")
                return
            }
            Log.d("BluetoothPairModule", "✅ Bluetooth está habilitado")

            val device = bluetoothAdapter.getRemoteDevice(address)
            Log.d("BluetoothPairModule", "📱 Dispositivo obtido: ${device.name ?: "Sem nome"} ($address)")
            
            // Verificar estado atual do pareamento
            val bondState = device.bondState
            Log.d("BluetoothPairModule", "🔍 Estado do pareamento: $bondState (BOND_NONE=${BluetoothDevice.BOND_NONE}, BOND_BONDING=${BluetoothDevice.BOND_BONDING}, BOND_BONDED=${BluetoothDevice.BOND_BONDED})")
            
            // Verificar se já está pareado
            if (bondState == BluetoothDevice.BOND_BONDED) {
                Log.d("BluetoothPairModule", "✅ Dispositivo já está pareado")
                val result: WritableMap = Arguments.createMap()
                result.putString("address", address)
                result.putString("name", device.name ?: "Desconhecido")
                result.putBoolean("alreadyPaired", true)
                promise.resolve(result)
                return
            }
            
            // Tentar parear usando createBond() (método nativo do Android)
            // Este método inicia o processo de pareamento
            // O Android mostrará um diálogo de pareamento se necessário
            Log.d("BluetoothPairModule", "🔗 Chamando createBond()...")
            val bonded = device.createBond()
            Log.d("BluetoothPairModule", "📡 createBond() retornou: $bonded")
            
            if (bonded) {
                // Pareamento iniciado com sucesso
                Log.d("BluetoothPairModule", "✅ Pareamento iniciado com sucesso")
                val result: WritableMap = Arguments.createMap()
                result.putString("address", address)
                result.putString("name", device.name ?: "Desconhecido")
                result.putBoolean("pairingStarted", true)
                promise.resolve(result)
            } else {
                Log.e("BluetoothPairModule", "❌ createBond() retornou false - dispositivo pode não estar visível ou acessível")
                promise.reject("PAIR_FAILED", "Falha ao iniciar pareamento. Verifique se o dispositivo está visível e em modo pairing.")
            }
        } catch (e: Exception) {
            Log.e("BluetoothPairModule", "❌ Exceção ao parear: ${e.message}", e)
            val errorMsg = when {
                e.message?.contains("not reachable", ignoreCase = true) == true -> "Dispositivo não alcançável. Verifique se está ligado e próximo."
                e.message?.contains("auth", ignoreCase = true) == true -> "Falha de autenticação. Verifique o PIN (padrão: 0000)."
                e.message?.contains("timeout", ignoreCase = true) == true -> "Timeout ao parear. Tente novamente."
                else -> e.message ?: "Erro desconhecido"
            }
            promise.reject("ERROR", "Erro ao parear dispositivo: $errorMsg", e)
        }
    }

    @ReactMethod
    fun isPaired(address: String, promise: Promise) {
        try {
            Log.d("BluetoothPairModule", "🔍 isPaired() chamado para: $address")
            
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.w("BluetoothPairModule", "⚠️ BluetoothAdapter é null")
                promise.resolve(false)
                return
            }

            val device = bluetoothAdapter.getRemoteDevice(address)
            val bondState = device.bondState
            val bonded = bondState == BluetoothDevice.BOND_BONDED
            
            Log.d("BluetoothPairModule", "📊 Estado do pareamento: $bondState (pareado=$bonded)")
            promise.resolve(bonded)
        } catch (e: Exception) {
            Log.e("BluetoothPairModule", "❌ Erro ao verificar pareamento: ${e.message}", e)
            promise.reject("ERROR", "Erro ao verificar pareamento: ${e.message}", e)
        }
    }
}
