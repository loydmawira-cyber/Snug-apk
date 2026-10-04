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
import com.example.data.model.AppNotification
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
    suspend fun encodePhoto(context: Context, uri: Uri, maxSide: Int = 640): Result<String> = withContext(Dispatchers.IO) {
        try {
            val original = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: return@withContext Result.failure(Exception("Could not read image"))
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
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { d -> d.toObject(Match::class.java)?.copy(id = d.id) }
                    .sortedByDescending { it.lastMessageAt?.toDate()?.time ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "matches")
                emit(emptyList())
            }
    }

    /** Returns the id of the existing match with [otherUserId], or creates one. */
    suspend fun findOrCreateMatch(otherUserId: String): String? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val existing = db.collection("matches")
                .whereArrayContains("userIds", uid)
                .get().await()
                .documents.firstOrNull { d ->
                    (d.get("userIds") as? List<*>)?.contains(otherUserId) == true
                }
            if (existing != null) {
                existing.id
            } else {
                val match = Match(
                    userIds = listOf(uid, otherUserId),
                    createdAt = com.google.firebase.Timestamp.now(),
                    lastMessageAt = com.google.firebase.Timestamp.now()
                )
                db.collection("matches").add(match).await().id
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "matches")
            null
        }
    }

    suspend fun createMatch(otherUserId: String) {
        findOrCreateMatch(otherUserId)
    }
}

class ChatRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> {
        return db.collection("chats")
            .whereEqualTo("matchId", matchId)
            .snapshots()
            .map { snap ->
                snap.documents
                    .mapNotNull { d -> d.toObject(ChatMessage::class.java)?.copy(id = d.id) }
                    .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
            }
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

        // Tell the other person they have a new message
        try {
            val snap = db.collection("matches").document(matchId).get().await()
            val other = (snap.get("userIds") as? List<*>)?.firstOrNull { it != uid } as? String
            if (other != null) {
                val myName = db.collection("users").document(uid).get().await()
                    .getString("displayName") ?: "Someone"
                writeNotification(db, other, "message", "$myName: ${text.take(80)}", matchId, myName)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "notifications")
        }
    }
}

/** Saves a notification for [toUserId]. Shown by the bell icon in their app. */
suspend fun writeNotification(
    db: FirebaseFirestore,
    toUserId: String,
    type: String,
    text: String,
    matchId: String,
    fromName: String
) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    db.collection("notifications").add(
        mapOf(
            "toUserId" to toUserId,
            "fromUserId" to uid,
            "fromName" to fromName,
            "type" to type,
            "text" to text,
            "matchId" to matchId,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
    ).await()
}

class SocialRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    /** Ids of everyone I have already liked or passed. */
    fun observeMySwipes(): Flow<Set<String>> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        return db.collection("swipes")
            .whereEqualTo("fromUserId", uid)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.getString("toUserId") }.toSet() }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "swipes")
                emit(emptySet())
            }
    }

    /** Saves a like/pass. Returns true if it created a match (they had already liked me). */
    suspend fun swipe(toUserId: String, like: Boolean, myName: String): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
        return try {
            db.collection("swipes").document("${uid}_$toUserId").set(
                mapOf(
                    "fromUserId" to uid,
                    "toUserId" to toUserId,
                    "action" to if (like) "like" else "pass",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (!like) {
                Result.success(false)
            } else {
                val reverse = db.collection("swipes").document("${toUserId}_$uid").get().await()
                val mutual = reverse.exists() && reverse.getString("action") == "like"
                if (mutual) {
                    val matchId = MatchRepository(db).findOrCreateMatch(toUserId) ?: ""
                    writeNotification(db, toUserId, "match", "You and $myName matched! \uD83C\uDF89", matchId, myName)
                } else {
                    writeNotification(db, toUserId, "like", "$myName liked you \u2764\uFE0F", "", myName)
                }
                Result.success(mutual)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "swipes")
            Result.failure(e)
        }
    }

    fun observeNotifications(): Flow<List<AppNotification>> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        return db.collection("notifications")
            .whereEqualTo("toUserId", uid)
            .snapshots()
            .map { snap ->
                snap.documents
                    .mapNotNull { d -> d.toObject(AppNotification::class.java)?.copy(id = d.id) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    .take(50)
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "notifications")
                emit(emptyList())
            }
    }

    suspend fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        try {
            val batch = db.batch()
            ids.forEach { batch.update(db.collection("notifications").document(it), "read", true) }
            batch.commit().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications")
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
        "profilePhoto" to this.profilePhoto,
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
        "latitude" to this.latitude,
        "longitude" to this.longitude,
        "createdAt" to this.createdAt,
        "updatedAt" to this.updatedAt
    )
    map.putAll(other)
    return map
}

