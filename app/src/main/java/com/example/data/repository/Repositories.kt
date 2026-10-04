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
            val base = db.collection("users").document(userId).get().await()
                .toObject(UserProfile::class.java)?.copy(id = userId) ?: return null
            if (userId == auth.currentUser?.uid) {
                // Phone numbers live in a private document nobody else can read
                val phone = try {
                    db.collection("users").document(userId).collection("private").document("contact")
                        .get().await().getString("phoneNumber")
                } catch (e: Exception) { null }
                base.copy(phoneNumber = phone ?: "")
            } else {
                base.copy(phoneNumber = "")
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            null
        }
    }

    suspend fun updateProfile(profile: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
        return try {
            // The verified badge only counts if a phone number is really linked to this account
            val verified = profile.isPhoneVerified && !auth.currentUser?.phoneNumber.isNullOrBlank()
            withTimeout(20000) {
                db.collection("users").document(uid)
                    .set(
                        profile.copy(id = uid, isPhoneVerified = verified) + mapOf(
                            "phoneNumber" to "", // never stored on the public profile
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
                db.collection("users").document(uid).collection("private").document("contact")
                    .set(mapOf("phoneNumber" to profile.phoneNumber))
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    // Spark plan: no Firebase Storage. Photos are shrunk and saved inside the document as a data URI.
    // Firestore documents are limited to 1 MB, so every photo is squeezed under [maxBytes].
    suspend fun encodePhoto(
        context: Context,
        uri: Uri,
        maxSide: Int = 800,
        maxBytes: Int = 140_000
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val original = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: return@withContext Result.failure(Exception("Could not read image"))
            var side = maxSide
            var bytes = ByteArray(0)
            while (true) {
                val scale = minOf(1f, side.toFloat() / maxOf(original.width, original.height))
                val bmp = if (scale < 1f)
                    Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
                else original
                var quality = 82
                while (true) {
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    bytes = out.toByteArray()
                    if (bytes.size <= maxBytes || quality <= 45) break
                    quality -= 10
                }
                if (bytes.size <= maxBytes || side <= 320) break
                side = (side * 0.8f).toInt()
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success("data:image/jpeg;base64,$b64")
        } catch (e: Exception) {
            android.util.Log.e("PhotoEncode", "Failed", e)
            Result.failure(e)
        }
    }

    /** [genders] narrows the query on the server (empty = everyone). [limit] grows as people run out. */
    fun observeDiscoveryProfiles(genders: List<String> = emptyList(), limit: Long = 100): Flow<List<UserProfile>> {
        var query: Query = db.collection("users")
        if (genders.isNotEmpty()) query = query.whereIn("gender", genders)
        return query
            .limit(limit)
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

    suspend fun unmatch(matchId: String): Boolean {
        return try {
            db.collection("matches").document(matchId).delete().await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "matches/$matchId")
            false
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

    /** Tells the other person I am (or stopped) typing in this chat. */
    suspend fun setTyping(matchId: String, typing: Boolean) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            db.collection("typing").document("${matchId}_$uid").set(
                mapOf(
                    "matchId" to matchId,
                    "userId" to uid,
                    "typing" to typing,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            // Typing status is best effort, never bother the user with it
            android.util.Log.w("ChatRepository", "typing update failed", e)
        }
    }

    fun observeTyping(matchId: String, otherUserId: String): Flow<Boolean> {
        return db.collection("typing").document("${matchId}_$otherUserId")
            .snapshots()
            .map { snap ->
                val on = snap.getBoolean("typing") == true
                val at = snap.getTimestamp("updatedAt")?.toDate()?.time
                // Ignore stale flags (e.g. the other phone lost signal while typing)
                on && (at == null || System.currentTimeMillis() - at < 12_000L)
            }
            .catch { emit(false) }
    }

    /** Marks messages from the other person as seen. */
    suspend fun markSeen(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        try {
            val batch = db.batch()
            messageIds.forEach {
                batch.update(db.collection("chats").document(it), "readAt", FieldValue.serverTimestamp())
            }
            batch.commit().await()
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "mark seen failed", e)
        }
    }

    suspend fun setReaction(messageId: String, emoji: String) {
        try {
            db.collection("chats").document(messageId).update("reaction", emoji).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$messageId")
        }
    }

    suspend fun sendMessage(matchId: String, text: String, imageUrl: String? = null) {
        val preview = if (imageUrl != null && text.isBlank()) "\uD83D\uDCF7 Photo" else text
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val message = ChatMessage(
                matchId = matchId,
                senderId = uid,
                text = text,
                imageUrl = imageUrl,
                createdAt = com.google.firebase.Timestamp.now()
            )
            db.collection("chats").add(message).await()
            
            // Update last message in match
            val matchRef = db.collection("matches").document(matchId)
            matchRef.update(
                mapOf(
                    "lastMessage" to preview,
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
                writeNotification(db, other, "message", "$myName: ${preview.take(80)}", matchId, myName)
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

    /** Ids of everyone I have liked. Passed people are not stored, so they can show up again. */
    fun observeMySwipes(): Flow<Set<String>> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        return db.collection("swipes")
            .whereEqualTo("fromUserId", uid)
            .snapshots()
            .map { snap ->
                snap.documents
                    .mapNotNull { d -> if (d.getString("action") in listOf("like", "superlike")) d.getString("toUserId") else null }
                    .toSet()
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "swipes")
                emit(emptySet())
            }
    }

    /** Saves a like/pass. Returns true if it created a match (they had already liked me). */
    suspend fun swipe(toUserId: String, like: Boolean, myName: String, superLike: Boolean = false): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
        return try {
            db.collection("swipes").document("${uid}_$toUserId").set(
                mapOf(
                    "fromUserId" to uid,
                    "toUserId" to toUserId,
                    "action" to if (!like) "pass" else if (superLike) "superlike" else "like",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (!like) {
                Result.success(false)
            } else {
                val reverse = db.collection("swipes").document("${toUserId}_$uid").get().await()
                val mutual = reverse.exists() && reverse.getString("action") in listOf("like", "superlike")
                if (mutual) {
                    val matchId = MatchRepository(db).findOrCreateMatch(toUserId) ?: ""
                    writeNotification(db, toUserId, "match", "You and $myName matched! \uD83C\uDF89", matchId, myName)
                } else {
                    writeNotification(
                        db, toUserId, "like",
                        if (superLike) "$myName super liked you \u2B50" else "$myName liked you \u2764\uFE0F",
                        "", myName
                    )
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

    /** Ids of everyone who has liked me. */
    fun observeLikesReceived(): Flow<Set<String>> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        return db.collection("swipes")
            .whereEqualTo("toUserId", uid)
            .snapshots()
            .map { snap ->
                snap.documents
                    .mapNotNull { d -> if (d.getString("action") in listOf("like", "superlike")) d.getString("fromUserId") else null }
                    .toSet()
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "swipes")
                emit(emptySet())
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

class SafetyRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    /** Ids of people I blocked plus people who blocked me. Neither side sees the other. */
    fun observeBlockedIds(): Flow<Set<String>> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        val mine = db.collection("blocks").whereEqualTo("blockerId", uid).snapshots()
            .map { snap -> snap.documents.mapNotNull { it.getString("blockedId") }.toSet() }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "blocks")
                emit(emptySet())
            }
        val theirs = db.collection("blocks").whereEqualTo("blockedId", uid).snapshots()
            .map { snap -> snap.documents.mapNotNull { it.getString("blockerId") }.toSet() }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "blocks")
                emit(emptySet())
            }
        return kotlinx.coroutines.flow.combine(mine, theirs) { a, b -> a + b }
    }

    suspend fun block(otherUserId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("blocks").document("${uid}_$otherUserId").set(
                mapOf(
                    "blockerId" to uid,
                    "blockedId" to otherUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "blocks")
            false
        }
    }

    /**
     * Deletes my profile and the data I own, then my sign-in account.
     * Returns null on success or a message to show the user.
     */
    suspend fun deleteAccount(): String? {
        val user = auth.currentUser ?: return "Not signed in"
        val uid = user.uid
        // Firebase only allows deleting an account shortly after signing in
        val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
        if (System.currentTimeMillis() - lastSignIn > 4 * 60 * 1000L) {
            return "For security, log out, sign back in, then delete your account right away."
        }
        try {
            for (m in db.collection("matches").whereArrayContains("userIds", uid).get().await().documents) {
                m.reference.delete().await()
            }
            for (s in db.collection("swipes").whereEqualTo("fromUserId", uid).get().await().documents) {
                s.reference.delete().await()
            }
            for (b in db.collection("blocks").whereEqualTo("blockerId", uid).get().await().documents) {
                b.reference.delete().await()
            }
            for (n in db.collection("notifications").whereEqualTo("toUserId", uid).get().await().documents) {
                n.reference.delete().await()
            }
            db.collection("users").document(uid).delete().await()
            user.delete().await()
            return null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            return "Could not delete account: ${e.message}"
        }
    }

    suspend fun report(otherUserId: String, reason: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("reports").add(
                mapOf(
                    "reporterId" to uid,
                    "reportedId" to otherUserId,
                    "reason" to reason,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports")
            false
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
        "city" to this.city,
        "lookingFor" to this.lookingFor,
        "haveKids" to this.haveKids,
        "wantKids" to this.wantKids,
        "smoking" to this.smoking,
        "drinking" to this.drinking,
        "exercise" to this.exercise,
        "religion" to this.religion,
        "education" to this.education,
        "pets" to this.pets,
        "zodiac" to this.zodiac,
        "jobTitle" to this.jobTitle,
        "heightCm" to this.heightCm,
        "isPaused" to this.isPaused,
        "hideDistance" to this.hideDistance,
        "onboarded" to this.onboarded,
        "superLikesUsed" to this.superLikesUsed,
        "lastSuperLikeDay" to this.lastSuperLikeDay,
        "prompts" to this.prompts.map { mapOf("question" to it.question, "answer" to it.answer) },
        "country" to this.country,
        "createdAt" to this.createdAt,
        "updatedAt" to this.updatedAt
    )
    map.putAll(other)
    return map
}
