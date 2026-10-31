package com.example.ui.components

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
    var confirmPassword by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var resetMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim(); resetMessage = null },
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
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showPassword) "Hide password" else "Show password")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        if (isRegistering) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showConfirmPassword) "Hide confirmation password" else "Show confirmation password")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }
        if (resetMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(resetMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (!isRegistering) {
            TextButton(
                onClick = {
                    if (email.isBlank()) {
                        onAuthError("Enter your email address first")
                        return@TextButton
                    }
                    isLoading = true
                    resetMessage = null
                    coroutineScope.launch {
                        try {
                            Firebase.auth.sendPasswordResetEmail(email).await()
                            resetMessage = "Password reset email sent. Open the link to choose a new password."
                            onAuthError("")
                        } catch (e: Exception) {
                            Log.e("Auth", "Could not send password reset email", e)
                            onAuthError(e.localizedMessage ?: "Could not send password reset email")
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Forgot password?") }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (email.isBlank() || password.isEmpty() || (isRegistering && confirmPassword.isEmpty())) {
                    onAuthError("Please fill all fields")
                    return@Button
                }
                if (isRegistering && password != confirmPassword) {
                    onAuthError("Passwords do not match")
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
                        val err = e.localizedMessage ?: "Authentication failed"
                        if (err.contains("provider is disabled", ignoreCase = true) ||
                            err.contains("operation is not allowed", ignoreCase = true) ||
                            err.contains("disabled for this Firebase project", ignoreCase = true)
                        ) {
                            com.example.data.security.DemoAuthManager.signInAsDemo(
                                context,
                                if (email.isNotBlank()) email else "demo.user@snug.app"
                            )
                            onAuthSuccess()
                        } else {
                            onAuthError(err)
                        }
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
        
        TextButton(onClick = {
            isRegistering = !isRegistering
            confirmPassword = ""
            resetMessage = null
            onAuthError("")
        }) {
            Text(if (isRegistering) "Already have an account? Sign In" else "New to SNUG? Create Account")
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                com.example.data.security.DemoAuthManager.signInAsDemo(
                    context,
                    if (email.isNotBlank()) email else "demo.user@snug.app"
                )
                onAuthSuccess()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Continue in Demo / Guest Mode")
        }
    }
}

fun signOut(context: android.content.Context, onSignOutComplete: () -> Unit) {
    com.example.data.security.DemoAuthManager.signOut(context)
    Firebase.auth.signOut()
    onSignOutComplete()
}
