package com.example.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun EmailSignInFlow(
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty()) {
                    onAuthError("Please fill all fields")
                    return@Button
                }
                isLoading = true
                coroutineScope.launch {
                    try {
                        if (isRegistering) {
                            val created = Firebase.auth.createUserWithEmailAndPassword(email, password).await()
                            // Send the verification email straight away (needed before messaging)
                            try {
                                created.user?.sendEmailVerification()?.await()
                            } catch (e: Exception) {
                                Log.w("Auth", "Could not send verification email", e)
                            }
                        } else {
                            Firebase.auth.signInWithEmailAndPassword(email, password).await()
                        }
                        onAuthSuccess()
                    } catch (e: Exception) {
                        Log.e("Auth", "Auth failed", e)
                        onAuthError(e.localizedMessage ?: "Authentication failed")
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isLoading,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (isRegistering) "Create Account" else "Sign In")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { isRegistering = !isRegistering }) {
            Text(if (isRegistering) "Already have an account? Sign In" else "New to SNUG? Create Account")
        }
    }
}

fun signOut(onSignOutComplete: () -> Unit) {
    Firebase.auth.signOut()
    onSignOutComplete()
}
