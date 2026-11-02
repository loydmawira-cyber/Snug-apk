package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminUserPage
import com.example.data.model.AdminUserRow
import com.example.data.model.Appeal
import com.example.data.model.MessageReportEntry
import com.example.data.model.ReportCase
import com.example.data.model.ReportEntry
import com.example.data.model.SuspensionInfo
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Report review, timed suspensions and appeals.
 * The Firestore rules decide who may do what: only admins can read reports and change suspensions.
 */
class ModerationRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val auth = Firebase.auth

    // ------------------------------------------------------------------
    // Admin: report queue
    // ------------------------------------------------------------------

    /** Open reports grouped by the reported person, most reported first (looks at the latest 300 reports). */
    suspend fun loadCases(): List<ReportCase> {
        return try {
            val docs = db.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(300)
                .get().await().documents
            val all = docs.mapNotNull { d -> d.toObject(ReportEntry::class.java)?.copy(id = d.id) }
            val grouped = all.groupBy { it.reportedId }.filterKeys { it.isNotBlank() }
            grouped.mapNotNull { (userId, list) ->
                val open = list.filter { it.status == "open" }
                if (open.isEmpty()) return@mapNotNull null
                val snap = db.collection("users").document(userId).get().await()
                val u = snap.toObject(UserProfile::class.java)
                ReportCase(
                    userId = userId,
                    name = u?.displayName?.takeIf { it.isNotBlank() } ?: "Deleted account",
                    photo = u?.profilePhoto?.takeIf { it.isNotBlank() }
                        ?: u?.coverPhoto?.takeIf { it.isNotBlank() } ?: "",
                    banned = snap.getBoolean("banned") ?: false,
                    suspendedUntil = snap.getTimestamp("suspendedUntil"),
                    reports = open,
                    totalReports = list.size
                )
            }.sortedByDescending { it.reports.size }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "reports")
            emptyList()
        }
    }

    /** Reported messages about one person (text, photo and reason). */
    suspend fun loadMessageReports(userId: String): List<MessageReportEntry> {
        return try {
            db.collection("messageReports")
                .whereEqualTo("reportedId", userId)
                .limit(30)
                .get().await().documents
                .mapNotNull { d -> d.toObject(MessageReportEntry::class.java)?.copy(id = d.id) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "messageReports")
            emptyList()
        }
    }

    private fun auditEntry(userId: String, name: String, decision: String, reason: String): Map<String, Any> {
        val admin = auth.currentUser
        return mapOf(
            "userId" to userId,
            "userName" to name,
            "adminId" to (admin?.uid ?: ""),
            "adminEmail" to (admin?.email ?: ""),
            "decision" to decision,
            "reason" to reason,
            "decidedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun resolveReports(batch: WriteBatch, reports: List<ReportEntry>, status: String, resolution: String) {
        val adminId = auth.currentUser?.uid ?: ""
        reports.forEach { r ->
            batch.update(
                db.collection("reports").document(r.id),
                mapOf(
                    "status" to status,
                    "resolution" to resolution,
                    "resolvedBy" to adminId,
                    "resolvedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    /** Telling people is best effort: the decision is already saved when this runs. */
    private suspend fun safeNotify(toUserId: String, text: String) {
        try {
            if (toUserId.isNotBlank() && toUserId != auth.currentUser?.uid) {
                writeNotification(db, toUserId, "moderation", text, "", "SNUG Team")
            }
        } catch (e: Exception) {
            android.util.Log.w("ModerationRepository", "notification failed", e)
        }
    }

    private suspend fun thankReporters(reports: List<ReportEntry>, text: String) {
        reports.map { it.reporterId }.filter { it.isNotBlank() }.distinct().take(20).forEach { safeNotify(it, text) }
    }

    private fun formatDate(ts: Timestamp): String =
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(ts.toDate())

    /**
     * Suspends the account for [days] days (0 = permanent) and closes the open reports about it.
     * Returns null on success, otherwise an error message.
     */
    suspend fun suspendUser(userId: String, name: String, days: Int, reason: String, reports: List<ReportEntry>): String? {
        val admin = auth.currentUser ?: return "Not signed in"
        if (userId == admin.uid) return "You cannot suspend yourself."
        return try {
            val until: Timestamp? =
                if (days > 0) Timestamp(Date(System.currentTimeMillis() + days * 86_400_000L)) else null
            val batch = db.batch()
            batch.update(
                db.collection("users").document(userId),
                mapOf(
                    "banned" to true,
                    "suspendedUntil" to (until ?: FieldValue.delete()),
                    "suspensionReason" to reason
                )
            )
            resolveReports(batch, reports, "actioned", if (days > 0) "Suspended $days days" else "Suspended permanently")
            val length = if (days > 0) "$days days" else "Permanent"
            batch.set(
                db.collection("verificationAudit").document(),
                auditEntry(userId, name, "suspended", "$length: $reason")
            )
            batch.commit().await()
            val untilText = if (until != null) "until ${formatDate(until)}" else "until our team reviews it"
            safeNotify(userId, "Your account is suspended $untilText. Reason: $reason. If you think this is a mistake, open the app and send an appeal.")
            thankReporters(reports, "Thank you for your report. We reviewed it and took action.")
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId")
            e.message ?: "Could not suspend the account"
        }
    }

    /** Sends the person a warning and closes the open reports. */
    suspend fun warnUser(userId: String, name: String, message: String, reports: List<ReportEntry>): String? {
        if (auth.currentUser == null) return "Not signed in"
        return try {
            val batch = db.batch()
            resolveReports(batch, reports, "actioned", "Warning sent")
            batch.set(db.collection("verificationAudit").document(), auditEntry(userId, name, "warned", message))
            batch.commit().await()
            safeNotify(userId, "Warning from the SNUG team: $message Further reports can lead to a suspension.")
            thankReporters(reports, "Thank you for your report. We reviewed it and took action.")
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports")
            e.message ?: "Could not send the warning"
        }
    }

    /** Closes the open reports without any action against the person. */
    suspend fun dismissReports(userId: String, name: String, reports: List<ReportEntry>): String? {
        if (auth.currentUser == null) return "Not signed in"
        return try {
            val batch = db.batch()
            resolveReports(batch, reports, "dismissed", "No violation found")
            batch.set(
                db.collection("verificationAudit").document(),
                auditEntry(userId, name, "report_dismissed", "No violation found")
            )
            batch.commit().await()
            thankReporters(reports, "Thank you for your report. We reviewed it and no action was needed.")
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports")
            e.message ?: "Could not close the reports"
        }
    }

    /** Lifts a suspension right away. */
    suspend fun reinstate(userId: String, name: String): String? {
        if (auth.currentUser == null) return "Not signed in"
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("users").document(userId),
                mapOf(
                    "banned" to false,
                    "suspendedUntil" to FieldValue.delete(),
                    "suspensionReason" to FieldValue.delete()
                )
            )
            batch.set(db.collection("verificationAudit").document(), auditEntry(userId, name, "unsuspended", ""))
            batch.commit().await()
            safeNotify(userId, "Your account has been reinstated. Welcome back!")
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId")
            e.message ?: "Could not reinstate the account"
        }
    }

    // ------------------------------------------------------------------
    // Admin: all registered users
    // ------------------------------------------------------------------

    private fun DocumentSnapshot.toAdminUserRow(): AdminUserRow? {
        val p = toObject(UserProfile::class.java) ?: return null
        return AdminUserRow(
            profile = p.copy(
                id = id,
                isPaused = getBoolean("isPaused") ?: false,
                isVip = getBoolean("isVip") ?: false
            ),
            suspendedUntil = getTimestamp("suspendedUntil"),
            suspensionReason = getString("suspensionReason") ?: ""
        )
    }

    /** Newest registrations first, [pageSize] people at a time. Throws if Firestore refuses the read. */
    suspend fun loadUsers(after: DocumentSnapshot?, pageSize: Long = 25): AdminUserPage {
        var q: Query = db.collection("users").orderBy("createdAt", Query.Direction.DESCENDING)
        if (after != null) q = q.startAfter(after)
        val snap = q.limit(pageSize).get().await()
        return AdminUserPage(
            users = snap.documents.mapNotNull { it.toAdminUserRow() },
            cursor = snap.documents.lastOrNull(),
            exhausted = snap.size() < pageSize
        )
    }

    /** People whose name starts with [text] (tries it as typed and with a capital first letter). */
    suspend fun searchUsers(text: String): List<AdminUserRow> {
        val variants = listOf(text, text.replaceFirstChar { it.uppercase() }).distinct()
        val out = mutableListOf<AdminUserRow>()
        for (v in variants) {
            val snap = db.collection("users")
                .orderBy("displayName")
                .startAt(v)
                .endAt(v + "\uf8ff")
                .limit(30)
                .get().await()
            out += snap.documents.mapNotNull { it.toAdminUserRow() }
        }
        return out.distinctBy { it.profile.id }
    }

    suspend fun loadUser(userId: String): AdminUserRow? = try {
        db.collection("users").document(userId).get().await().toAdminUserRow()
    } catch (e: Exception) {
        null
    }

    /** Total registered people and how many have the blue check (server-side counts, no documents downloaded). */
    suspend fun loadUserTotals(): Pair<Long, Long> {
        return try {
            val total = db.collection("users").count().get(AggregateSource.SERVER).await().count
            val verified = db.collection("users").whereEqualTo("photoVerified", true)
                .count().get(AggregateSource.SERVER).await().count
            total to verified
        } catch (e: Exception) {
            -1L to -1L
        }
    }

    /** Gives or removes the blue check by hand (the selfie approval does the same thing automatically). */
    suspend fun setVerified(userId: String, name: String, verified: Boolean): String? {
        val admin = auth.currentUser ?: return "Not signed in"
        if (userId == admin.uid) return "You cannot change your own badge."
        return try {
            val batch = db.batch()
            batch.update(db.collection("users").document(userId), "photoVerified", verified)
            batch.set(
                db.collection("verificationAudit").document(),
                auditEntry(userId, name, if (verified) "verified" else "unverified", "Changed by an admin")
            )
            batch.commit().await()
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId")
            e.message ?: "Could not change the badge"
        }
    }

    // ------------------------------------------------------------------
    // Admin: appeals (disputes)
    // ------------------------------------------------------------------

    fun observeOpenAppeals(): Flow<List<Appeal>> {
        return db.collection("appeals")
            .whereEqualTo("status", "open")
            .snapshots()
            .map { snap ->
                snap.documents
                    .mapNotNull { d -> d.toObject(Appeal::class.java)?.copy(uid = d.id) }
                    .sortedBy { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "appeals")
                emit(emptyList())
            }
    }

    /** Accepting an appeal lifts the suspension. Denying keeps it and shows [response] to the person. */
    suspend fun decideAppeal(appeal: Appeal, accept: Boolean, response: String): String? {
        val admin = auth.currentUser ?: return "Not signed in"
        if (appeal.uid == admin.uid) return "You cannot decide your own appeal."
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("appeals").document(appeal.uid),
                mapOf(
                    "status" to if (accept) "accepted" else "denied",
                    "adminResponse" to response,
                    "decidedBy" to admin.uid,
                    "decidedAt" to FieldValue.serverTimestamp()
                )
            )
            if (accept) {
                batch.update(
                    db.collection("users").document(appeal.uid),
                    mapOf(
                        "banned" to false,
                        "suspendedUntil" to FieldValue.delete(),
                        "suspensionReason" to FieldValue.delete()
                    )
                )
            }
            batch.set(
                db.collection("verificationAudit").document(),
                auditEntry(appeal.uid, appeal.name, if (accept) "appeal_accepted" else "appeal_denied", response)
            )
            batch.commit().await()
            safeNotify(
                appeal.uid,
                if (accept) "Your appeal was accepted and your account is active again."
                else "Your appeal was reviewed and the suspension stays." + (if (response.isNotBlank()) " Note: $response" else "")
            )
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "appeals/${appeal.uid}")
            e.message ?: "Could not save the decision"
        }
    }

    // ------------------------------------------------------------------
    // The suspended person
    // ------------------------------------------------------------------

    /** Live view of my own suspension (the app switches back on the moment it is lifted). */
    fun observeMySuspension(): Flow<SuspensionInfo> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        return db.collection("users").document(uid).snapshots()
            .map { s ->
                SuspensionInfo(
                    banned = s.getBoolean("banned") ?: false,
                    until = s.getTimestamp("suspendedUntil"),
                    reason = s.getString("suspensionReason") ?: ""
                )
            }
            .catch { }
    }

    fun observeMyAppeal(): Flow<Appeal?> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        return db.collection("appeals").document(uid).snapshots()
            .map { s -> if (s.exists()) s.toObject(Appeal::class.java)?.copy(uid = s.id) else null }
            .catch { emit(null) }
    }

    /** Returns null on success, otherwise an error message. */
    suspend fun submitAppeal(name: String, message: String, info: SuspensionInfo?): String? {
        val uid = auth.currentUser?.uid ?: return "Not signed in"
        val text = message.trim()
        if (text.length < 10) return "Too short"
        return try {
            db.collection("appeals").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "name" to name,
                    "message" to text.take(1000),
                    "status" to "open",
                    "suspensionReason" to (info?.reason ?: ""),
                    "suspendedUntil" to info?.until,
                    "adminResponse" to "",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "appeals/$uid")
            e.message ?: "Could not send the appeal"
        }
    }

    /** A timed suspension that has run out is switched off by the person's own app (the rules allow only this). */
    suspend fun liftIfExpired(info: SuspensionInfo): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val until = info.until ?: return false
        if (!info.banned || until.toDate().time > System.currentTimeMillis()) return false
        return try {
            db.collection("users").document(uid).update("banned", false).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
