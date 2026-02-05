package com.pedidosexpress

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.EditText
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuFragment : Fragment() {
    private lateinit var apiService: ApiService
    private lateinit var authService: AuthService
    private lateinit var progressBar: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var menuRecyclerView: RecyclerView
    private lateinit var addFab: FloatingActionButton
    private lateinit var categoryTabs: TabLayout
    private var editingItem: MenuItem? = null
    private var allMenuItems: List<MenuItem> = emptyList()
    private var currentCategory: String = "comida" // bebida, comida, sobremesa
    
    // Limite do WhatsApp: máximo 10 itens por mensagem interativa
    // Limite por categoria: 9 itens (deixando margem de segurança)
    companion object {
        private const val MAX_ITEMS_PER_CATEGORY = 9
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_menu, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        apiService = ApiService(requireContext())
        authService = AuthService(requireContext())
        
        // Atualizar título dinamicamente
        val user = authService.getUser()
        activity?.title = BusinessTypeHelper.menuLabel(user)
        
        progressBar = view.findViewById(R.id.progress_bar)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        menuRecyclerView = view.findViewById(R.id.menu_recycler)
        addFab = view.findViewById(R.id.add_fab)
        categoryTabs = view.findViewById(R.id.category_tabs)
        
        menuRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        menuRecyclerView.adapter = MenuAdapter(
            emptyList(),
            onToggleAvailable = { item -> toggleAvailable(item) },
            onEdit = { item -> showEditDialog(item) },
            onDelete = { item -> showDeleteDialog(item) }
        )
        
        categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> currentCategory = "bebida"
                    1 -> currentCategory = "comida"
                    2 -> currentCategory = "sobremesa"
                }
                filterMenuByCategory()
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Selecionar tab de Comidas por padrão
        categoryTabs.getTabAt(1)?.select()
        
        // Atualizar contadores nas tabs após carregar o menu
        updateTabCounters()
        
        swipeRefresh.setOnRefreshListener {
            loadMenu()
        }
        
        addFab.setOnClickListener {
            showAddDialog()
        }
        
        loadMenu()
    }
    
    private fun loadMenu() {
        if (!isAdded || context == null) return
        
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    apiService.getMenu()
                }
                
                if (isAdded) {
                    // Normalizar categorias antigas para novas
                    allMenuItems = items.map { item ->
                        val normalizedCategory = when (item.category) {
                            "hamburguer" -> "comida"
                            else -> item.category
                        }
                        item.copy(category = normalizedCategory)
                    }
                    filterMenuByCategory()
                    updateTabCounters()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MenuFragment", "Erro ao carregar menu", e)
                if (isAdded && context != null) {
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }
    
    private fun filterMenuByCategory() {
        val filteredItems = allMenuItems.filter { item ->
            item.category == currentCategory
        }
        (menuRecyclerView.adapter as? MenuAdapter)?.updateItems(filteredItems)
        updateTabCounters()
    }
    
    private fun updateTabCounters() {
        val bebidasCount = allMenuItems.count { it.category == "bebida" }
        val comidasCount = allMenuItems.count { it.category == "comida" }
        val sobremesasCount = allMenuItems.count { it.category == "sobremesa" }
        
        categoryTabs.getTabAt(0)?.text = "Bebidas ($bebidasCount/$MAX_ITEMS_PER_CATEGORY)"
        categoryTabs.getTabAt(1)?.text = "Comidas ($comidasCount/$MAX_ITEMS_PER_CATEGORY)"
        categoryTabs.getTabAt(2)?.text = "Sobremesas ($sobremesasCount/$MAX_ITEMS_PER_CATEGORY)"
    }
    
    private fun toggleAvailable(item: MenuItem) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.updateMenuItem(item.id, available = !item.available)
                }
                loadMenu()
                if (isAdded && context != null) {
                    Toast.makeText(context, "Status atualizado", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MenuFragment", "Erro ao atualizar status", e)
                if (isAdded && context != null) {
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                loadMenu() // Recarregar para reverter o switch
            }
        }
    }
    
    private fun showAddDialog() {
        editingItem = null
        showItemDialog(null)
    }
    
    private fun showEditDialog(item: MenuItem) {
        editingItem = item
        showItemDialog(item)
    }
    
    private fun showItemDialog(item: MenuItem?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_menu_item, null)
        
        val nameInput = dialogView.findViewById<EditText>(R.id.item_name_input)
        val priceInput = dialogView.findViewById<EditText>(R.id.item_price_input)
        val categoryGroup = dialogView.findViewById<RadioGroup>(R.id.category_group)
        val availableSwitch = dialogView.findViewById<Switch>(R.id.item_available_switch)
        val saveButton = dialogView.findViewById<Button>(R.id.save_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.dialog_title)
        
        titleText?.text = if (item == null) "Adicionar Item" else "Editar Item"
        
        if (item != null) {
            nameInput.setText(item.name)
            priceInput.setText(String.format("%.2f", item.price))
            when (item.category) {
                "bebida" -> dialogView.findViewById<RadioButton>(R.id.category_bebida).isChecked = true
                "sobremesa" -> dialogView.findViewById<RadioButton>(R.id.category_sobremesa).isChecked = true
                else -> dialogView.findViewById<RadioButton>(R.id.category_comida).isChecked = true
            }
            availableSwitch.isChecked = item.available
        } else {
            // Ao adicionar novo item, usar a categoria da tab selecionada
            when (currentCategory) {
                "bebida" -> dialogView.findViewById<RadioButton>(R.id.category_bebida).isChecked = true
                "sobremesa" -> dialogView.findViewById<RadioButton>(R.id.category_sobremesa).isChecked = true
                else -> dialogView.findViewById<RadioButton>(R.id.category_comida).isChecked = true
            }
        }
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        saveButton.setOnClickListener {
            val name = nameInput.text?.toString()?.trim()
            val priceText = priceInput.text?.toString()?.trim()
            val selectedCategoryId = categoryGroup.checkedRadioButtonId
            val category = when (selectedCategoryId) {
                R.id.category_bebida -> "bebida"
                R.id.category_sobremesa -> "sobremesa"
                else -> "comida"
            }
            val available = availableSwitch.isChecked
            
            if (name.isNullOrEmpty() || priceText.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val price = try {
                priceText.toDouble()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Preço inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Verificar limite de itens por categoria (apenas ao adicionar novo item)
            if (item == null) {
                val itemsInCategory = allMenuItems.count { it.category == category }
                if (itemsInCategory >= MAX_ITEMS_PER_CATEGORY) {
                    val categoryName = when (category) {
                        "bebida" -> "Bebidas"
                        "sobremesa" -> "Sobremesas"
                        else -> "Comidas"
                    }
                    Toast.makeText(
                        requireContext(),
                        "Limite atingido! Máximo de $MAX_ITEMS_PER_CATEGORY itens por categoria.\n\n$categoryName já possui $itemsInCategory itens.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                
                // Adicionar novo item
                val newId = name.lowercase().replace(Regex("[^a-z0-9]"), "_")
                saveMenuItem(newId, name, price, category, available)
            } else {
                // Editar item existente - verificar limite apenas se mudou de categoria
                if (item.category != category) {
                    val itemsInNewCategory = allMenuItems.count { it.category == category && it.id != item.id }
                    if (itemsInNewCategory >= MAX_ITEMS_PER_CATEGORY) {
                        val categoryName = when (category) {
                            "bebida" -> "Bebidas"
                            "sobremesa" -> "Sobremesas"
                            else -> "Comidas"
                        }
                        Toast.makeText(
                            requireContext(),
                            "Limite atingido! Máximo de $MAX_ITEMS_PER_CATEGORY itens por categoria.\n\n$categoryName já possui $itemsInNewCategory itens.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }
                }
                
                // Editar item existente
                updateMenuItem(item.id, name, price, category, available)
            }
            
            dialog.dismiss()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveMenuItem(id: String, name: String, price: Double, category: String, available: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.createMenuItem(id, name, price, category, available)
                }
                loadMenu()
                if (isAdded && context != null) {
                    Toast.makeText(context, "Item adicionado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MenuFragment", "Erro ao adicionar item", e)
                if (isAdded && context != null) {
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateMenuItem(id: String, name: String, price: Double, category: String, available: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.updateMenuItem(id, name = name, price = price, category = category, available = available)
                }
                loadMenu()
                if (isAdded && context != null) {
                    Toast.makeText(context, "Item atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MenuFragment", "Erro ao atualizar item", e)
                if (isAdded && context != null) {
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showDeleteDialog(item: MenuItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir Item")
            .setMessage("Tem certeza que deseja excluir \"${item.name}\"?")
            .setPositiveButton("Excluir") { _, _ ->
                deleteMenuItem(item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun deleteMenuItem(item: MenuItem) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.deleteMenuItem(item.id)
                }
                loadMenu()
                if (isAdded && context != null) {
                    Toast.makeText(context, "Item excluído com sucesso!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MenuFragment", "Erro ao excluir item", e)
                if (isAdded && context != null) {
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
