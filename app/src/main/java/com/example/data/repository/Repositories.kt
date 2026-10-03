package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProfileRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun getProfile(userId: String): UserProfile? {
        return try {
            db.collection("users").document(userId).get().await().toObject(UserProfile::class.java)?.copy(id = userId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            null
        }
    }

    suspend fun updateProfile(profile: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
        return try {
            withTimeout(20000) {
                db.collection("users").document(uid)
                    .set((profile.copy(id = uid) + mapOf("updatedAt" to FieldValue.serverTimestamp())))
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    // Spark plan: no Firebase Storage. Photos are shrunk and saved inside the profile as a data URI.
    suspend fun encodePhoto(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val original = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: return@withContext Result.failure(Exception("Could not read image"))
            val maxSide = 640
            val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height))
            val bmp = if (scale < 1f)
                Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
            else original
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 65, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            Result.success("data:image/jpeg;base64,$b64")
        } catch (e: Exception) {
            android.util.Log.e("PhotoEncode", "Failed", e)
            Result.failure(e)
        }
    }

    fun observeDiscoveryProfiles(): Flow<List<UserProfile>> {
        return db.collection("users")
            .limit(50)
            .snapshots()
            .map { snapshot -> 
                val profiles = snapshot.documents.mapNotNull { d ->
                    d.toObject(UserProfile::class.java)?.copy(id = d.id)
                }
                profiles.filter { user -> user.id != getCurrentUserId() }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "users")
                emit(emptyList())
            }
    }
}

class MatchRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    fun observeMatches(): Flow<List<Match>> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        return db.collection("matches")
            .whereArrayContains("userIds", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Match::class.java)
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "matches")
                emit(emptyList())
            }
    }

    suspend fun createMatch(otherUserId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val match = Match(
                userIds = listOf(uid, otherUserId),
                createdAt = com.google.firebase.Timestamp.now(),
                lastMessageAt = com.google.firebase.Timestamp.now()
            )
            db.collection("matches").add(match).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "matches")
        }
    }
}

class ChatRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> {
        return db.collection("chats")
            .whereEqualTo("matchId", matchId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { it.toObjects(ChatMessage::class.java) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "chats")
                emit(emptyList())
            }
    }

    suspend fun sendMessage(matchId: String, text: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val message = ChatMessage(
                matchId = matchId,
                senderId = uid,
                text = text,
                createdAt = com.google.firebase.Timestamp.now()
            )
            db.collection("chats").add(message).await()
            
            // Update last message in match
            val matchRef = db.collection("matches").document(matchId)
            matchRef.update(
                mapOf(
                    "lastMessage" to text,
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chats/matches")
        }
    }
}

// Operator overloading helper for map merging
private operator fun <K, V> Map<K, V>.plus(other: Map<K, V>): Map<K, V> = this.toMutableMap().apply { putAll(other) }
private operator fun UserProfile.plus(other: Map<String, Any?>): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>(
        "id" to this.id,
        "displayName" to this.displayName,
        "bio" to this.bio,
        "birthDate" to this.birthDate,
        "gender" to this.gender,
        "interestedIn" to this.interestedIn,
        "interests" to this.interests,
        "photos" to this.photos.map { photo ->
            mapOf(
                "url" to photo.url,
                "isBlurred" to photo.isBlurred,
                "isPublic" to photo.isPublic,
                "allowedUserIds" to photo.allowedUserIds
            )
        },
        "phoneNumber" to this.phoneNumber,
        "isPhoneVerified" to this.isPhoneVerified,
        "isVip" to this.isVip,
        "dailyLikesUsed" to this.dailyLikesUsed,
        "lastLikeReset" to this.lastLikeReset,
        "minAge" to this.minAge,
        "maxAge" to this.maxAge,
        "radiusKm" to this.radiusKm,
        "createdAt" to this.createdAt,
        "updatedAt" to this.updatedAt
    )
    map.putAll(other)
    return map
}

