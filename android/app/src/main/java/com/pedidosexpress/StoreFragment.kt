package com.pedidosexpress

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

class StoreFragment : Fragment() {
    private lateinit var apiService: ApiService
    private lateinit var progressBar: ProgressBar
    private lateinit var storeNameText: TextView
    private lateinit var isOpenSwitch: Switch
    private lateinit var saveButton: Button
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_store, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        apiService = ApiService(requireContext())
        
        progressBar = view.findViewById(R.id.progress_bar)
        storeNameText = view.findViewById(R.id.store_name)
        isOpenSwitch = view.findViewById(R.id.is_open_switch)
        saveButton = view.findViewById(R.id.save_button)
        
        saveButton.setOnClickListener {
            saveStoreStatus()
        }
        
        loadStoreStatus()
    }
    
    private fun loadStoreStatus() {
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    apiService.getStoreStatus()
                }
                
                isOpenSwitch.isChecked = status.isOpen
                
            } catch (e: Exception) {
                android.util.Log.e("StoreFragment", "Erro ao carregar status", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun saveStoreStatus() {
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.updateStoreStatus(isOpenSwitch.isChecked)
                }
                
                Toast.makeText(requireContext(), "Status atualizado!", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                android.util.Log.e("StoreFragment", "Erro ao salvar status", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
