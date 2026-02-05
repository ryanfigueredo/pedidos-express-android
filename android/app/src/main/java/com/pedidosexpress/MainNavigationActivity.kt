package com.pedidosexpress

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainNavigationActivity : AppCompatActivity() {
    
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var authService: AuthService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_navigation)
        
        authService = AuthService(this)
        val user = authService.getUser()
        
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = "Pedidos Express"
        
        bottomNavigation = findViewById(R.id.bottom_navigation)
        
        // Atualizar labels do bottom navigation dinamicamente
        val ordersLabel = BusinessTypeHelper.ordersLabel(user)
        val menuLabel = BusinessTypeHelper.menuLabel(user)
        
        bottomNavigation.menu.findItem(R.id.nav_orders)?.title = ordersLabel
        bottomNavigation.menu.findItem(R.id.nav_menu)?.title = menuLabel
        
        // Iniciar com tela de Pedidos
        loadFragment(OrdersFragment())
        
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_orders -> {
                    loadFragment(OrdersFragment())
                    true
                }
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_menu -> {
                    loadFragment(MenuFragment())
                    true
                }
                R.id.nav_support -> {
                    loadFragment(SupportFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_logout -> {
                AuthService(this).logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            R.id.menu_settings -> {
                bottomNavigation.selectedItemId = R.id.nav_settings
                loadFragment(SettingsFragment())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
