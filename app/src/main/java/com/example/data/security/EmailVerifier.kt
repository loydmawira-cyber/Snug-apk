package com.example.data.security

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

/** Confirms the email address really belongs to the person (Firebase sends the verification email). */
object EmailVerifier {
    fun email(): String = Firebase.auth.currentUser?.email ?: ""

    fun isVerified(): Boolean = Firebase.auth.currentUser?.isEmailVerified == true

    /** Returns null on success, otherwise an error message. */
    suspend fun send(): String? {
        val user = Firebase.auth.currentUser ?: return "Not signed in"
        return try {
            user.sendEmailVerification().await()
            null
        } catch (e: Exception) {
            e.message ?: "Could not send the email"
        }
    }

    /** Re-reads the account from Firebase and returns whether the email is now verified. */
    suspend fun refresh(): Boolean {
        val user = Firebase.auth.currentUser ?: return false
        return try {
            user.reload().await()
            // Refresh the sign-in token so Firestore rules see email_verified = true straight away
            if (user.isEmailVerified) user.getIdToken(true).await()
            user.isEmailVerified
        } catch (e: Exception) {
            false
        }
    }
}
