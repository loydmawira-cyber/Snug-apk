package com.example.data.model

import com.google.firebase.Timestamp

/** One person-level report. [status] is "open" until an admin handles it ("actioned" or "dismissed"). */
data class ReportEntry(
    val id: String = "",
    val reporterId: String = "",
    val reportedId: String = "",
    val reason: String = "",
    val status: String = "open",
    val resolution: String = "",
    val createdAt: Timestamp? = null
)

/** A reported chat message (text and/or photo). A copy is kept because admins cannot read private chats. */
data class MessageReportEntry(
    val id: String = "",
    val reporterId: String = "",
    val reportedId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val reason: String = "",
    val createdAt: Timestamp? = null
)

/** Everything an admin needs to decide about one reported person. */
data class ReportCase(
    val userId: String,
    val name: String,
    val photo: String,
    val banned: Boolean,
    val suspendedUntil: Timestamp?,
    val reports: List<ReportEntry>,
    val totalReports: Int
)

/** A suspended person asking an admin to look at the decision again. status: open, accepted or denied. */
data class Appeal(
    val uid: String = "",
    val name: String = "",
    val message: String = "",
    val status: String = "open",
    val suspensionReason: String = "",
    val suspendedUntil: Timestamp? = null,
    val adminResponse: String = "",
    val createdAt: Timestamp? = null,
    val decidedAt: Timestamp? = null
)

/** What the suspended person sees: until when, and why. */
data class SuspensionInfo(
    val banned: Boolean = false,
    val until: Timestamp? = null,
    val reason: String = ""
)

object SuspensionOptions {
    /** Label to number of days. 0 means permanent. */
    val all: List<Pair<String, Int>> = listOf(
        "1 day" to 1,
        "3 days" to 3,
        "7 days" to 7,
        "30 days" to 30,
        "Permanent" to 0
    )
}

/** One registered person as the admin Users tab sees them. */
data class AdminUserRow(
    val profile: UserProfile,
    val suspendedUntil: Timestamp? = null,
    val suspensionReason: String = ""
)

/** One page of registered people. [cursor] is passed back to load the next page. */
data class AdminUserPage(
    val users: List<AdminUserRow>,
    val cursor: com.google.firebase.firestore.DocumentSnapshot?,
    val exhausted: Boolean
)
