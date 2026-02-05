package com.pedidosexpress

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var savePasswordCheckbox: CheckBox
    private lateinit var loginButton: Button
    private lateinit var progressBar: View
    private lateinit var apiService: ApiService
    private lateinit var authService: AuthService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        apiService = ApiService(this)
        authService = AuthService(this)
        
        // Se já estiver logado, ir direto para pedidos
        if (authService.isLoggedIn()) {
            startActivity(Intent(this, OrdersActivity::class.java))
            finish()
            return
        }
        
        usernameEditText = findViewById(R.id.username)
        passwordEditText = findViewById(R.id.password)
        savePasswordCheckbox = findViewById(R.id.save_password_checkbox)
        loginButton = findViewById(R.id.login_button)
        progressBar = findViewById(R.id.progress_bar)
        
        // Carregar credenciais salvas se existirem
        loadSavedCredentials()
        
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Preencha usuário e senha", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            performLogin(username, password)
        }
    }
    
    private fun performLogin(username: String, password: String) {
        loginButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    apiService.login(username, password)
                }
                
                // Salvar credenciais apenas se o checkbox estiver marcado
                if (savePasswordCheckbox.isChecked) {
                    authService.saveUser(user, username, password)
                } else {
                    // Se não marcar, salvar apenas o usuário (sem senha) e limpar senha salva
                    authService.saveUserWithoutPassword(user, username)
                }
                
                Toast.makeText(this@LoginActivity, "Login realizado!", Toast.LENGTH_SHORT).show()
                
                startActivity(Intent(this@LoginActivity, MainNavigationActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                loginButton.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun loadSavedCredentials() {
        val credentials = authService.getCredentials()
        if (credentials != null) {
            usernameEditText.setText(credentials.first)
            passwordEditText.setText(credentials.second)
            savePasswordCheckbox.isChecked = true
        } else {
            // Se não houver senha salva, desmarcar o checkbox
            savePasswordCheckbox.isChecked = false
        }
    }
}
