package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.UserPhoto
import com.example.data.model.UserProfile
import com.example.data.model.publicViewForOthers
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Gallery photos live in users/{uid}/photos/p0..p3, not inside the profile document. */
private const val MAX_GALLERY_PHOTOS = 4

/** One page of people for Discover. [cursor] is passed back to load the next page. */
data class DiscoveryPage(
    val profiles: List<UserProfile>,
    val cursor: DocumentSnapshot?,
    val exhausted: Boolean
)

private fun roundCoord(v: Double?): Double? = v?.let { Math.round(it * 100) / 100.0 } // ~1 km

class ProfileRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    fun getCurrentUserId(): String? = auth.currentUser?.uid ?: com.example.data.security.DemoAuthManager.getUid()

    /**
     * [withPhotos] also loads the person's public gallery (extra reads), so only the full
     * profile screen asks for it. Lists such as matches only need the avatar.
     */
    suspend fun getProfile(userId: String, withPhotos: Boolean = false): UserProfile? {
        return try {
            val profile = getProfileOrThrow(userId)
            if (userId == getCurrentUserId()) {
                profile
            } else {
                val full = if (withPhotos && profile != null && profile.showPhotosToOthers) {
                    // Old profiles may still carry their photos inside the document
                    profile.copy(photos = loadPublicPhotos(userId).ifEmpty { profile.photos })
                } else profile
                full?.publicViewForOthers()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            null
        }
    }

    /** Returns null only when the document is absent; read/permission errors must not look like a new user. */
    suspend fun getProfileOrThrow(userId: String): UserProfile? {
        val snapshot = db.collection("users").document(userId).get().await()
        if (!snapshot.exists()) return null
        val base = snapshot.toUserProfile()?.copy(id = userId)
            ?: throw IllegalStateException("Profile document exists but could not be decoded")
        return if (userId == getCurrentUserId()) {
            // Remove legacy phone fields from this account's profile and private contact record.
            db.collection("users").document(userId).set(
                mapOf("phoneNumber" to FieldValue.delete(), "isPhoneVerified" to FieldValue.delete()),
                SetOptions.merge()
            ).await()
            db.collection("users").document(userId).collection("private").document("contact")
                .set(mapOf("phoneNumber" to FieldValue.delete()), SetOptions.merge()).await()
            // A failed gallery read must throw too, otherwise saving would wipe the gallery.
            val gallery = loadOwnPhotos(userId)
            val withGallery = base.copy(photos = gallery.ifEmpty { base.photos }) // old layout: photos still in the document
            slimDownOwnProfile(userId, withGallery, hasLegacyField = snapshot.contains("photos"), galleryMigrated = gallery.isNotEmpty())
        } else {
            base
        }
    }

    private fun photosCollection(uid: String) =
        db.collection("users").document(uid).collection("photos")

    private fun DocumentSnapshot.toUserPhoto(): UserPhoto? {
        val url = getString("url") ?: return null
        @Suppress("UNCHECKED_CAST")
        val allowed = (get("allowedUserIds") as? List<String>) ?: emptyList()
        return UserPhoto(
            url = url,
            isBlurred = getBoolean("isBlurred") ?: false,
            isPublic = getBoolean("isPublic") ?: true,
            allowedUserIds = allowed
        )
    }

    private suspend fun loadOwnPhotos(uid: String): List<UserPhoto> =
        photosCollection(uid).get().await().documents
            .sortedBy { it.getLong("order") ?: 0L }
            .mapNotNull { it.toUserPhoto() }

    /** Other people's gallery: the rules only let the query return photos marked public. */
    private suspend fun loadPublicPhotos(uid: String): List<UserPhoto> = try {
        photosCollection(uid).whereEqualTo("isPublic", true).get().await().documents
            .sortedBy { it.getLong("order") ?: 0L }
            .mapNotNull { it.toUserPhoto() }
    } catch (e: Exception) {
        emptyList()
    }

    /** Replaces my whole gallery (max 4 photos) in one atomic write. */
    suspend fun savePhotos(photos: List<UserPhoto>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
        return try {
            val batch = db.batch()
            for (i in 0 until MAX_GALLERY_PHOTOS) {
                val ref = photosCollection(uid).document("p$i")
                val photo = photos.getOrNull(i)
                if (photo != null) {
                    batch.set(
                        ref,
                        mapOf(
                            "url" to photo.url,
                            "isBlurred" to photo.isBlurred,
                            "isPublic" to photo.isPublic,
                            "allowedUserIds" to photo.allowedUserIds,
                            "order" to i
                        )
                    )
                } else {
                    batch.delete(ref)
                }
            }
            withTimeout(20000) { batch.commit().await() }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/photos")
            Result.failure(e)
        }
    }

    /**
     * The picture shown on Discover cards when someone has no profile picture: a small copy of
     * their first public gallery photo, plus whether it should be blurred.
     */
    suspend fun coverFor(photo: UserPhoto?): Pair<String, Boolean> {
        if (photo == null) return "" to false
        val small = if (photo.url.startsWith("data:image")) {
            shrinkDataUri(photo.url, 640, 60_000) ?: ""
        } else photo.url
        return small to photo.isBlurred
    }

    /**
     * One-time clean-up of profiles saved by older app versions: gallery photos move out of the
     * profile document and an oversized avatar is shrunk. Best effort, never blocks login.
     */
    private suspend fun slimDownOwnProfile(
        uid: String,
        profile: UserProfile,
        hasLegacyField: Boolean,
        galleryMigrated: Boolean
    ): UserProfile {
        var result = profile
        try {
            val updates = mutableMapOf<String, Any>()
            if (hasLegacyField) {
                val safeToDrop = galleryMigrated || profile.photos.isEmpty() || savePhotos(profile.photos).isSuccess
                if (safeToDrop) updates["photos"] = FieldValue.delete()
            }
            if (profile.profilePhoto.length > 80_000) {
                shrinkDataUri(profile.profilePhoto, 640, 60_000)?.let {
                    updates["profilePhoto"] = it
                    result = result.copy(profilePhoto = it)
                }
            }
            if (profile.coverPhoto.isBlank() && profile.photos.any { it.isPublic }) {
                val (cover, blurred) = coverFor(profile.photos.first { it.isPublic })
                if (cover.isNotBlank()) {
                    updates["coverPhoto"] = cover
                    updates["coverBlurred"] = blurred
                    result = result.copy(coverPhoto = cover, coverBlurred = blurred)
                }
            }
            if (updates.isNotEmpty()) {
                db.collection("users").document(uid).update(updates).await()
            }
        } catch (e: Exception) {
            android.util.Log.w("ProfileRepository", "Profile clean-up skipped", e)
        }
        return result
    }

    /** Heartbeat: tells everyone whether I am online. Silent if it fails. */
    suspend fun setPresence(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).set(
                mapOf("online" to online, "lastActive" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            android.util.Log.w("Presence", "Could not update presence", e)
        }
    }

    suspend fun updateProfile(profile: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
        return try {
            withTimeout(20000) {
                db.collection("users").document(uid)
                    .set(
                        profile.copy(id = uid) + mapOf(
                            "phoneNumber" to FieldValue.delete(),
                            "isPhoneVerified" to FieldValue.delete(),
                            // Public location is only accurate to about 1 km
                            "latitude" to roundCoord(profile.latitude),
                            "longitude" to roundCoord(profile.longitude),
                            "online" to true, // saving means the app is open
                            "lastActive" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    // Spark plan: no Firebase Storage. Photos are shrunk and saved as a data URI.
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
            Result.success(toDataUri(compressBitmap(original, maxSide, maxBytes)))
        } catch (e: Exception) {
            android.util.Log.e("PhotoEncode", "Failed", e)
            Result.failure(e)
        }
    }

    /** Re-compresses a saved "data:image/..." photo into a smaller one. Null if it is not a data URI. */
    suspend fun shrinkDataUri(dataUri: String, maxSide: Int, maxBytes: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!dataUri.startsWith("data:image")) return@withContext null
                val raw = Base64.decode(dataUri.substringAfter("base64,"), Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@withContext null
                toDataUri(compressBitmap(bmp, maxSide, maxBytes))
            } catch (e: Exception) {
                android.util.Log.w("PhotoEncode", "Shrink failed", e)
                null
            }
        }

    private fun toDataUri(jpeg: ByteArray) =
        "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)

    private fun compressBitmap(original: Bitmap, maxSide: Int, maxBytes: Int): ByteArray {
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
        return bytes
    }

    /**
     * One page of people, [pageSize] at a time (one read per person). [genders] narrows the
     * query on the server (empty = everyone). Pass the returned cursor to get the next page.
     * This replaces a live listener on the whole list, so other people's presence updates
     * no longer cost reads on every phone.
     */
    suspend fun loadDiscoveryPage(
        genders: List<String>,
        after: DocumentSnapshot?,
        pageSize: Long = 30
    ): DiscoveryPage {
        try {
            var query: Query = db.collection("users")
            if (genders.isNotEmpty()) query = query.whereIn("gender", genders)
            query = query.orderBy(FieldPath.documentId())
            if (after != null) query = query.startAfter(after)
            val snapshot = query.limit(pageSize).get().await()
            val me = getCurrentUserId()
            val profiles = snapshot.documents
                .mapNotNull { d -> d.toUserProfile()?.copy(id = d.id) }
                .filter { it.id != me }
                // Old profiles still carry their gallery here; cards only need the first one
                .map { it.copy(photos = it.photos.take(1)) }
            return DiscoveryPage(profiles, snapshot.documents.lastOrNull(), snapshot.size() < pageSize)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            throw e
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

    suspend fun setArchived(matchId: String, archived: Boolean): Boolean {
        return try {
            db.collection("matches").document(matchId).update("isArchived", archived).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "matches/$matchId")
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
        val uid = Firebase.auth.currentUser?.uid ?: com.example.data.security.DemoAuthManager.getUid() ?: return
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

    suspend fun togglePin(messageId: String, pinned: Boolean) {
        try {
            db.collection("chats").document(messageId).update("isPinned", pinned).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$messageId")
        }
    }

    suspend fun sendMessage(
        matchId: String,
        text: String,
        imageUrl: String? = null,
        replyToText: String = "",
        replyToSender: String = ""
    ) {
        val preview = if (imageUrl != null && text.isBlank()) "\uD83D\uDCF7 Photo" else text
        val uid = Firebase.auth.currentUser?.uid ?: com.example.data.security.DemoAuthManager.getUid() ?: return
        try {
            val message = ChatMessage(
                matchId = matchId,
                senderId = uid,
                text = text,
                imageUrl = imageUrl,
                replyToText = replyToText,
                replyToSender = replyToSender,
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
    val uid = Firebase.auth.currentUser?.uid ?: com.example.data.security.DemoAuthManager.getUid() ?: return
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

    /**
     * Sends a friendly nudge. The notification id includes today's date, and the rules only let the
     * receiver edit it, so each person can be nudged by you once per day.
     */
    suspend fun nudge(toUserId: String, myName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
        return try {
            val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
            db.collection("notifications").document("nudge_${uid}_${toUserId}_$day").set(
                mapOf(
                    "toUserId" to toUserId,
                    "fromUserId" to uid,
                    "fromName" to myName,
                    "type" to "nudge",
                    "text" to "$myName nudged you \uD83D\uDC4B",
                    "matchId" to "",
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
            try {
                db.collection("verificationRequests").document(uid).delete().await()
            } catch (_: Exception) {
            }
            // Subcollections are not deleted with the user document, so remove them first
            for (i in 0 until MAX_GALLERY_PHOTOS) {
                db.collection("users").document(uid).collection("photos").document("p$i").delete().await()
            }
            db.collection("users").document(uid).collection("private").document("contact").delete().await()
            db.collection("users").document(uid).delete().await()
            user.delete().await()
            return null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            return "Could not delete account: ${e.message}"
        }
    }

    /** 0 = sent, 1 = you already reported this person, 2 = failed. One report per person (stops spam). */
    suspend fun report(otherUserId: String, reason: String): Int {
        val uid = auth.currentUser?.uid ?: return 2
        return try {
            db.collection("reports").document("${uid}_$otherUserId").set(
                mapOf(
                    "reporterId" to uid,
                    "reportedId" to otherUserId,
                    "reason" to reason,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            0
        } catch (e: Exception) {
            if ((e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                1
            } else {
                handleFirestoreError(e, OperationType.CREATE, "reports")
                2
            }
        }
    }

    /**
     * Reports one message (text or photo). A copy of the message is saved with the report because
     * admins cannot read private chats. 0 = sent, 1 = you already reported it, 2 = failed.
     */
    suspend fun reportMessage(message: ChatMessage, reason: String): Int {
        val uid = auth.currentUser?.uid ?: return 2
        if (message.id.isBlank() || message.senderId.isBlank() || message.senderId == uid) return 2
        return try {
            db.collection("messageReports").document("${uid}_${message.id}").set(
                mapOf(
                    "reporterId" to uid,
                    "reportedId" to message.senderId,
                    "messageId" to message.id,
                    "matchId" to message.matchId,
                    "text" to message.text.take(500),
                    "imageUrl" to message.imageUrl,
                    "reason" to reason,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            // Also counts towards the person-level list the admin dashboard ranks (ignored if already reported)
            report(message.senderId, "Message: $reason")
            0
        } catch (e: Exception) {
            if ((e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                1
            } else {
                handleFirestoreError(e, OperationType.CREATE, "messageReports")
                2
            }
        }
    }

    /** Status of my photo-verification request: null = none, otherwise "pending", "approved" or "rejected". */
    fun observeVerificationStatus(): Flow<String?> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        return db.collection("verificationRequests").document(uid).snapshots()
            .map { snap -> if (snap.exists()) snap.getString("status") ?: "pending" else null }
            .catch { emit(null) }
    }

    /** Why my last selfie was rejected ("" when there is no reason). */
    fun observeVerificationReason(): Flow<String> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        return db.collection("verificationRequests").document(uid).snapshots()
            .map { snap -> snap.getString("rejectionReason") ?: "" }
            .catch { emit("") }
    }

    /** Saves a selfie for an admin to review. Only I and admins can read it. */
    suspend fun submitPhotoVerification(
        pose: String,
        selfieDataUri: String,
        referenceDataUri: String = "",
        name: String = ""
    ): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val ref = db.collection("verificationRequests").document(uid)
            // A rejected or old request must be removed before a new one can be sent
            if (ref.get().await().exists()) ref.delete().await()
            ref.set(
                mapOf(
                    "uid" to uid,
                    "name" to name,
                    "pose" to pose,
                    "selfie" to selfieDataUri,
                    "reference" to referenceDataUri,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "verificationRequests/$uid")
            false
        }
    }
}

/** Admin-only access to the photo-verification queue. The rules enforce who is an admin. */
class AdminRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    suspend fun isAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            // Only admins are allowed to list verification requests, so a successful read means admin.
            // The Firestore rules are the single source of truth for who is an admin.
            db.collection("verificationRequests").limit(1).get().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun observePending(): Flow<List<com.example.data.model.VerificationRequest>> {
        return db.collection("verificationRequests")
            .whereEqualTo("status", "pending")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { d ->
                    d.toObject(com.example.data.model.VerificationRequest::class.java)?.copy(uid = d.id)
                }.sortedBy { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "verificationRequests")
                emit(emptyList())
            }
    }

    /** Counts users and reports on the server (cheap: no documents are downloaded). */
    suspend fun loadStats(): com.example.data.model.AdminStats = kotlinx.coroutines.coroutineScope {
        val users = db.collection("users")
        val now = System.currentTimeMillis()
        fun ago(ms: Long) = com.google.firebase.Timestamp(java.util.Date(now - ms))
        suspend fun count(q: com.google.firebase.firestore.Query): Long = try {
            q.count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
        } catch (e: Exception) {
            -1L
        }
        val min5 = 5 * 60_000L
        val day = 24 * 60 * 60_000L
        val a = listOf(
            async { count(users) },
            async { count(users.whereGreaterThan("lastActive", ago(min5))) },
            async { count(users.whereGreaterThan("lastActive", ago(day))) },
            async { count(users.whereGreaterThan("lastActive", ago(7 * day))) },
            async { count(users.whereGreaterThan("createdAt", ago(day))) },
            async { count(users.whereGreaterThan("createdAt", ago(7 * day))) },
            async { count(users.whereEqualTo("photoVerified", true)) },
            async { count(users.whereEqualTo("isVip", true)) },
            async { count(users.whereEqualTo("banned", true)) },
            async { count(users.whereEqualTo("isPaused", true)) },
            async { count(users.whereEqualTo("gender", "Male")) },
            async { count(users.whereEqualTo("gender", "Female")) },
            async { count(db.collection("reports")) },
            async { count(db.collection("verificationRequests").whereEqualTo("status", "pending")) }
        ).map { it.await() }
        com.example.data.model.AdminStats(
            totalUsers = a[0], activeNow = a[1], active24h = a[2], active7d = a[3],
            newToday = a[4], new7d = a[5], photoVerified = a[6],
            vip = a[7], banned = a[8], paused = a[9], men = a[10], women = a[11],
            reports = a[12], pendingReviews = a[13]
        )
    }

    /** New users per day for the last [days] days (oldest first). */
    suspend fun loadSignups(days: Int = 7): List<com.example.data.model.SignupDay> = kotlinx.coroutines.coroutineScope {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val fmt = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        val dayMs = 24 * 60 * 60_000L
        (days - 1 downTo 0).map { back ->
            val start = todayStart - back * dayMs
            val end = start + dayMs
            val label = fmt.format(java.util.Date(start))
            async {
                val n = try {
                    db.collection("users")
                        .whereGreaterThanOrEqualTo("createdAt", com.google.firebase.Timestamp(java.util.Date(start)))
                        .whereLessThan("createdAt", com.google.firebase.Timestamp(java.util.Date(end)))
                        .count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
                } catch (e: Exception) {
                    0L
                }
                com.example.data.model.SignupDay(label, n)
            }
        }.map { it.await() }
    }

    /** The 10 most reported people (looks at the latest 500 reports). */
    suspend fun loadMostReported(): List<com.example.data.model.ReportedUser> {
        return try {
            val docs = db.collection("reports").limit(500).get().await().documents
            val grouped = docs.groupBy { it.getString("reportedId") ?: "" }.filterKeys { it.isNotBlank() }
            grouped.entries.sortedByDescending { it.value.size }.take(10).mapNotNull { (id, list) ->
                val u = db.collection("users").document(id).get().await()
                    .toObject(UserProfile::class.java) ?: return@mapNotNull null
                com.example.data.model.ReportedUser(
                    userId = id,
                    name = u.displayName,
                    photo = u.profilePhoto.takeIf { it.isNotBlank() } ?: u.coverPhoto.takeIf { it.isNotBlank() } ?: u.photos.firstOrNull()?.url ?: "",
                    reports = list.size,
                    reasons = list.mapNotNull { it.getString("reason") }.groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }.take(3).map { "${it.key} (${it.value})" },
                    banned = u.banned
                )
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "reports")
            emptyList()
        }
    }

    /** Suspends or lifts the suspension, and records it in the audit trail. Returns null on success. */
    suspend fun setBanned(userId: String, name: String, banned: Boolean): String? {
        val admin = auth.currentUser ?: return "Not signed in"
        if (userId == admin.uid) return "You cannot suspend yourself."
        return try {
            val batch = db.batch()
            // A plain suspend / reinstate from the stats list has no end date, so any timed-suspension fields are cleared
            batch.update(
                db.collection("users").document(userId),
                mapOf(
                    "banned" to banned,
                    "suspendedUntil" to FieldValue.delete(),
                    "suspensionReason" to FieldValue.delete()
                )
            )
            batch.set(
                db.collection("verificationAudit").document(),
                mapOf(
                    "userId" to userId,
                    "userName" to name,
                    "adminId" to admin.uid,
                    "adminEmail" to (admin.email ?: ""),
                    "decision" to if (banned) "suspended" else "unsuspended",
                    "reason" to "",
                    "decidedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId")
            e.message ?: "Could not update the account"
        }
    }

    fun observeAudit(): Flow<List<com.example.data.model.VerificationAuditEntry>> {
        return db.collection("verificationAudit")
            .orderBy("decidedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snap -> snap.toObjects(com.example.data.model.VerificationAuditEntry::class.java) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "verificationAudit")
                emit(emptyList())
            }
    }

    /**
     * Approves or rejects a request, writes the audit entry and notifies the user.
     * The selfie images are erased from the request as soon as a decision is made.
     * Returns null on success, otherwise an error message.
     */
    suspend fun decide(
        request: com.example.data.model.VerificationRequest,
        approve: Boolean,
        reason: String
    ): String? {
        val admin = auth.currentUser ?: return "Not signed in"
        if (request.uid == admin.uid) return "You cannot review your own selfie."
        return try {
            val decision = if (approve) "approved" else "rejected"
            val batch = db.batch()
            batch.update(
                db.collection("verificationRequests").document(request.uid),
                mapOf(
                    "status" to decision,
                    "reviewedBy" to admin.uid,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "rejectionReason" to if (approve) "" else reason,
                    "selfie" to "",
                    "reference" to ""
                )
            )
            if (approve) {
                batch.update(db.collection("users").document(request.uid), "photoVerified", true)
            }
            batch.set(
                db.collection("verificationAudit").document(),
                mapOf(
                    "userId" to request.uid,
                    "userName" to request.name,
                    "adminId" to admin.uid,
                    "adminEmail" to (admin.email ?: ""),
                    "decision" to decision,
                    "reason" to if (approve) "" else reason,
                    "decidedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            // The decision is saved. Telling the user is best effort.
            try {
                val text = if (approve) {
                    "Your photo is verified! You now have the blue check."
                } else {
                    "Your selfie was not accepted" + (if (reason.isNotBlank()) ": $reason" else "") + ". You can try again."
                }
                writeNotification(db, request.uid, "verification", text, "", "SNUG Team")
            } catch (e: Exception) {
                android.util.Log.w("AdminRepository", "notification failed", e)
            }
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "verificationRequests/${request.uid}")
            e.message ?: "Could not save the decision"
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
        // Gallery photos are saved separately (savePhotos), never inside the profile document
        "coverPhoto" to this.coverPhoto,
        "coverBlurred" to this.coverBlurred,
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
        "showNameToOthers" to this.showNameToOthers,
        "showAgeToOthers" to this.showAgeToOthers,
        "showLocationToOthers" to this.showLocationToOthers,
        "showPhotosToOthers" to this.showPhotosToOthers,
        "showBioToOthers" to this.showBioToOthers,
        "showInterestsToOthers" to this.showInterestsToOthers,
        "showPromptsToOthers" to this.showPromptsToOthers,
        "showAboutMeToOthers" to this.showAboutMeToOthers,
        "showPresenceToOthers" to this.showPresenceToOthers,
        "showVerifiedBadgeToOthers" to this.showVerifiedBadgeToOthers,
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

/**
 * Firestore's automatic mapping skips Kotlin booleans named isXxx (it looks for "xxx" instead),
 * so these are read by hand to make sure paused / verified / VIP always load correctly.
 */
private fun DocumentSnapshot.toUserProfile(): UserProfile? {
    val base = toObject(UserProfile::class.java) ?: return null
    return base.copy(
        isPaused = getBoolean("isPaused") ?: false,
        isVip = getBoolean("isVip") ?: false,
        showNameToOthers = getBoolean("showNameToOthers") ?: true,
        showAgeToOthers = getBoolean("showAgeToOthers") ?: true,
        showLocationToOthers = getBoolean("showLocationToOthers") ?: true,
        showPhotosToOthers = getBoolean("showPhotosToOthers") ?: true,
        showBioToOthers = getBoolean("showBioToOthers") ?: true,
        showInterestsToOthers = getBoolean("showInterestsToOthers") ?: true,
        showPromptsToOthers = getBoolean("showPromptsToOthers") ?: true,
        showAboutMeToOthers = getBoolean("showAboutMeToOthers") ?: true,
        showPresenceToOthers = getBoolean("showPresenceToOthers") ?: true,
        showVerifiedBadgeToOthers = getBoolean("showVerifiedBadgeToOthers") ?: true
    )
}
