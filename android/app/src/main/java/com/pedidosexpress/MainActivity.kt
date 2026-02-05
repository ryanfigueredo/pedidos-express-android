package com.pedidosexpress

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Verificar se já está logado
        val authService = AuthService(this)
        
        if (authService.isLoggedIn()) {
            // Ir direto para navegação principal
            startActivity(Intent(this, MainNavigationActivity::class.java))
        } else {
            // Ir para login
            startActivity(Intent(this, LoginActivity::class.java))
        }
        
        finish()
    }
}
