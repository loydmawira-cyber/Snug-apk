package com.example.data.security

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Real SMS verification through Firebase Phone Auth. The phone is linked to the signed-in account. */
object PhoneVerifier {

    /** Digits only with a leading +, e.g. "+254 712-345 678" becomes "+254712345678". */
    fun normalize(raw: String): String = raw.filter { it.isDigit() || it == '+' }

    fun sendCode(
        activity: Activity,
        phone: String,
        onSent: (verificationId: String) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = onAutoVerified(credential)
            override fun onVerificationFailed(e: FirebaseException) = onError(e.message ?: "Verification failed")
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) = onSent(id)
        }
        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun credentialFor(verificationId: String, code: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, code)

    /** Links the verified phone to the account. Returns null on success, otherwise an error message. */
    suspend fun link(credential: PhoneAuthCredential): String? {
        val user = Firebase.auth.currentUser ?: return "Not signed in"
        return try {
            if (user.phoneNumber.isNullOrBlank()) {
                user.linkWithCredential(credential).await()
            } else {
                user.updatePhoneNumber(credential).await()
            }
            // Refresh the token so Firestore rules can see the verified phone number
            user.getIdToken(true).await()
            null
        } catch (e: Exception) {
            e.message ?: "Could not verify this number"
        }
    }
}
