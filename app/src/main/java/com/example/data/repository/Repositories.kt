package com.example.data.repository

import android.content.Context
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
            db.collection("users").document(userId).get().await().toObject(UserProfile::class.java)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            null
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).set(profile + mapOf("updatedAt" to FieldValue.serverTimestamp())).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
        }
    }

    fun observeDiscoveryProfiles(): Flow<List<UserProfile>> {
        return db.collection("users")
            .limit(50)
            .snapshots()
            .map { snapshot -> 
                val profiles = snapshot.toObjects(UserProfile::class.java)
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
        "createdAt" to this.createdAt,
        "updatedAt" to this.updatedAt
    )
    map.putAll(other)
    return map
}

