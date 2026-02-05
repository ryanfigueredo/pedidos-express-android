package com.pedidosexpress

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class AuthService(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveUser(user: User, username: String, password: String) {
        prefs.edit()
            .putString("user", gson.toJson(user))
            .putString("username", username)
            .putString("password", password)
            .putBoolean("is_logged_in", true)
            .apply()
    }
    
    fun getUser(): User? {
        val userJson = prefs.getString("user", null) ?: return null
        return try {
            gson.fromJson(userJson, User::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun getCredentials(): Pair<String, String>? {
        val username = prefs.getString("username", null)
        val password = prefs.getString("password", null)
        return if (username != null && password != null) {
            Pair(username, password)
        } else {
            null
        }
    }
    
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("is_logged_in", false) && getUser() != null
    }
    
    fun logout() {
        prefs.edit()
            .remove("user")
            .remove("username")
            .remove("password")
            .putBoolean("is_logged_in", false)
            .apply()
    }
}
