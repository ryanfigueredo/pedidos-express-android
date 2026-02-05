package com.pedidosexpress

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    
    private lateinit var apiService: ApiService
    private lateinit var isOpenSwitch: Switch
    private lateinit var saveStoreButton: Button
    private lateinit var storeProgressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val authService = AuthService(requireContext())
        val user = authService.getUser()
        
        // Informações do usuário
        view.findViewById<TextView>(R.id.user_name).text = user?.name ?: "N/A"
        view.findViewById<TextView>(R.id.user_role).text = when(user?.role) {
            "admin" -> "Administrador"
            "manager" -> "Gerente"
            else -> user?.role ?: "N/A"
        }
        view.findViewById<TextView>(R.id.user_username).text = "@${user?.username ?: "N/A"}"
        
        // Status da Loja
        apiService = ApiService(requireContext())
        isOpenSwitch = view.findViewById(R.id.is_open_switch)
        saveStoreButton = view.findViewById(R.id.save_store_button)
        storeProgressBar = view.findViewById(R.id.store_progress_bar)
        
        saveStoreButton.setOnClickListener {
            saveStoreStatus()
        }
        
        loadStoreStatus()
        
        // Logout
        view.findViewById<Button>(R.id.logout_button).setOnClickListener {
            authService.logout()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }
    
    private fun loadStoreStatus() {
        storeProgressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    apiService.getStoreStatus()
                }
                
                isOpenSwitch.isChecked = status.isOpen
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Erro ao carregar status", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                storeProgressBar.visibility = View.GONE
            }
        }
    }
    
    private fun saveStoreStatus() {
        storeProgressBar.visibility = View.VISIBLE
        saveStoreButton.isEnabled = false
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.updateStoreStatus(isOpenSwitch.isChecked)
                }
                
                Toast.makeText(requireContext(), "Status atualizado!", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Erro ao salvar status", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                storeProgressBar.visibility = View.GONE
                saveStoreButton.isEnabled = true
            }
        }
    }
}
