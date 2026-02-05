package com.pedidosexpress

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val authService = AuthService(this)
        val user = authService.getUser()
        
        findViewById<TextView>(R.id.user_name).text = "Usuário: ${user?.name ?: "N/A"}"
        findViewById<TextView>(R.id.user_role).text = "Função: ${user?.role ?: "N/A"}"
        
        findViewById<Button>(R.id.logout_button).setOnClickListener {
            authService.logout()
            finish()
        }
    }
}
