package com.example.data.security

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DemoAuthManager {
    private var _demoUserId by mutableStateOf<String?>(null)
    private var _demoEmail by mutableStateOf<String?>(null)
    private val _authStateFlow = MutableStateFlow<String?>(null)
    val authStateFlow: StateFlow<String?> = _authStateFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("snug_demo_auth", Context.MODE_PRIVATE)
        val savedUid = prefs.getString("demo_uid", null)
        val savedEmail = prefs.getString("demo_email", null)
        if (savedUid != null) {
            _demoUserId = savedUid
            _demoEmail = savedEmail ?: "demo.user@snug.app"
            _authStateFlow.value = _demoUserId
        }
    }

    fun isDemoActive(): Boolean = _demoUserId != null

    fun getUid(): String? = _demoUserId

    fun getEmail(): String = _demoEmail ?: "demo.user@snug.app"

    fun signInAsDemo(context: Context, email: String = "demo.user@snug.app") {
        val cleanEmail = email.ifBlank { "demo.user@snug.app" }
        val uid = "demo_" + cleanEmail.lowercase().replace(Regex("[^a-z0-9]"), "")
        _demoUserId = uid
        _demoEmail = cleanEmail
        _authStateFlow.value = uid
        
        context.getSharedPreferences("snug_demo_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("demo_uid", uid)
            .putString("demo_email", cleanEmail)
            .apply()
    }

    fun signOut(context: Context) {
        _demoUserId = null
        _demoEmail = null
        _authStateFlow.value = null
        context.getSharedPreferences("snug_demo_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
