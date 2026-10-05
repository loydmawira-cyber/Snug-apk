package com.example.data.model

import com.google.firebase.Timestamp

data class UserPhoto(
    val url: String = "",
    val isBlurred: Boolean = false,
    val isPublic: Boolean = true,
    val allowedUserIds: List<String> = emptyList()
)

data class ProfilePrompt(
    val question: String = "",
    val answer: String = ""
)

data class UserProfile(
    val id: String = "",
    val displayName: String = "",
    val bio: String = "",
    val birthDate: Timestamp? = null,
    val gender: String = "",
    val interestedIn: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val profilePhoto: String = "",
    val photos: List<UserPhoto> = emptyList(),
    val phoneNumber: String = "",
    val isPhoneVerified: Boolean = false,
    // Set only by an admin in the Firebase Console (rules stop the app from changing these)
    val photoVerified: Boolean = false,
    val banned: Boolean = false,
    val isVip: Boolean = false,
    val dailyLikesUsed: Int = 0,
    val lastLikeReset: Timestamp? = null,
    val minAge: Int = 18,
    val maxAge: Int = 35,
    val radiusKm: Int = 50,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String = "",
    val country: String = "",
    val lookingFor: String = "",
    val haveKids: String = "",
    val wantKids: String = "",
    val smoking: String = "",
    val drinking: String = "",
    val exercise: String = "",
    val religion: String = "",
    val education: String = "",
    val pets: String = "",
    val zodiac: String = "",
    val jobTitle: String = "",
    val heightCm: Int = 0,
    val isPaused: Boolean = false,
    val prompts: List<ProfilePrompt> = emptyList(),
    val hideDistance: Boolean = false,
    val showNameToOthers: Boolean = true,
    val showAgeToOthers: Boolean = true,
    val showLocationToOthers: Boolean = true,
    val showPhotosToOthers: Boolean = true,
    val showBioToOthers: Boolean = true,
    val showInterestsToOthers: Boolean = true,
    val showPromptsToOthers: Boolean = true,
    val showAboutMeToOthers: Boolean = true,
    val showPresenceToOthers: Boolean = true,
    val showVerifiedBadgeToOthers: Boolean = true,
    val onboarded: Boolean = false,
    val online: Boolean = false,
    val lastActive: Timestamp? = null,
    val superLikesUsed: Int = 0,
    val lastSuperLikeDay: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class Match(
    val id: String = "",
    val userIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val otherUser: UserProfile? = null // Helper for UI
)

data class ChatMessage(
    val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val createdAt: Timestamp? = null,
    val readAt: Timestamp? = null,
    val reaction: String = ""
)

/** Best picture to show for a user: their profile picture, else the first gallery photo. */
fun UserProfile.avatarUrl(): String? =
    profilePhoto.takeIf { it.isNotBlank() } ?: photos.firstOrNull()?.url

data class AppNotification(
    val id: String = "",
    val toUserId: String = "",
    val fromUserId: String = "",
    val fromName: String = "",
    val type: String = "", // "like", "match" or "message"
    val text: String = "",
    val matchId: String = "",
    val read: Boolean = false,
    val createdAt: Timestamp? = null
)

fun UserProfile.locationLabel(): String =
    listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")

/** Removes fields the profile owner chose not to show in public app screens. */
fun UserProfile.publicViewForOthers(): UserProfile {
    val showLocation = showLocationToOthers
    val showAboutMe = showAboutMeToOthers
    return copy(
        displayName = if (showNameToOthers) displayName else "SNUG member",
        birthDate = birthDate.takeIf { showAgeToOthers },
        bio = if (showBioToOthers) bio else "",
        gender = if (showAboutMe) gender else "",
        interestedIn = emptyList(),
        interests = if (showInterestsToOthers) interests else emptyList(),
        profilePhoto = if (showPhotosToOthers) profilePhoto else "",
        photos = if (showPhotosToOthers) photos.filter { it.isPublic } else emptyList(),
        phoneNumber = "",
        minAge = 18,
        maxAge = 99,
        radiusKm = 20_000,
        latitude = latitude.takeIf { showLocation },
        longitude = longitude.takeIf { showLocation },
        city = if (showLocation) city else "",
        country = if (showLocation) country else "",
        lookingFor = if (showAboutMe) lookingFor else "",
        haveKids = if (showAboutMe) haveKids else "",
        wantKids = if (showAboutMe) wantKids else "",
        smoking = if (showAboutMe) smoking else "",
        drinking = if (showAboutMe) drinking else "",
        exercise = if (showAboutMe) exercise else "",
        religion = if (showAboutMe) religion else "",
        education = if (showAboutMe) education else "",
        pets = if (showAboutMe) pets else "",
        zodiac = if (showAboutMe) zodiac else "",
        jobTitle = if (showAboutMe) jobTitle else "",
        heightCm = if (showAboutMe) heightCm else 0,
        prompts = if (showPromptsToOthers) prompts else emptyList(),
        hideDistance = hideDistance || !showLocation,
        online = showPresenceToOthers && online,
        lastActive = lastActive.takeIf { showPresenceToOthers },
        isVip = false,
        dailyLikesUsed = 0,
        lastLikeReset = null,
        superLikesUsed = 0,
        lastSuperLikeDay = ""
    )
}

/** "City, Country - 12 km away", falling back gracefully when something is unknown. */
fun placeAndDistance(profile: UserProfile, distanceText: String): String {
    val place = profile.locationLabel()
    val dist = distanceText.takeIf { it != "Distance unknown" && !profile.hideDistance }
    return when {
        place.isNotBlank() && dist != null -> "$place \u2022 $dist"
        place.isNotBlank() -> place
        dist != null -> dist
        else -> "Location unknown"
    }
}

object ProfileOptions {
    val promptQuestions = listOf(
        "My ideal Sunday is...",
        "I'm happiest when...",
        "A green flag for me is...",
        "The way to my heart is...",
        "My most useless talent is...",
        "We'll get along if...",
        "Two truths and a lie...",
        "Best trip I ever took..."
    )
    val lookingFor = listOf("Long-term relationship", "Marriage", "Casual dating", "Short-term fun", "New friends", "Still figuring it out")
    val haveKids = listOf("Have kids", "No kids")
    val wantKids = listOf("Want kids", "Don't want kids", "Open to kids", "Not sure")
    val smoking = listOf("Non-smoker", "Smoke socially", "Smoke regularly", "Trying to quit")
    val drinking = listOf("Don't drink", "Drink socially", "Drink often")
    val exercise = listOf("Never exercise", "Exercise sometimes", "Exercise often", "Exercise daily")
    val religion = listOf("Christian", "Muslim", "Hindu", "Buddhist", "Jewish", "Spiritual", "Atheist", "Agnostic", "Other")
    val education = listOf("High school", "Trade school", "Some college", "Bachelor's degree", "Master's degree", "PhD")
    val pets = listOf("Dog lover", "Cat lover", "Love all pets", "No pets")
    val zodiac = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    val interests = listOf(
        "Music", "Travel", "Art", "Hiking", "Fitness", "Reading", "Gaming", "Cooking", "Photography",
        "Movies", "Yoga", "Pets", "Dancing", "Football", "Coffee", "Foodie", "Church", "Volunteering",
        "Fashion", "Tech", "Nature", "Beach", "Concerts", "Writing", "Business", "Comedy"
    )
}

/** Short facts about a person, ready to show as chips. Empty answers are skipped. */
fun UserProfile.basics(): List<String> = listOf(
    jobTitle,
    education,
    if (heightCm > 0) "$heightCm cm" else "",
    religion,
    haveKids,
    wantKids,
    smoking,
    drinking,
    exercise,
    pets,
    zodiac
).filter { it.isNotBlank() }

/** In-memory Discover filters. Empty / false means "no filter". */
data class DiscoverFilters(
    val lookingFor: String = "",
    val verifiedOnly: Boolean = false
) {
    val isActive: Boolean get() = lookingFor.isNotBlank() || verifiedOnly
}

object ReportReasons {
    val all = listOf("Fake profile or spam", "Inappropriate photos", "Harassment or abuse", "Underage user", "Scam or asking for money", "Other")
}

/** Interests two people have in common. */
fun UserProfile.sharedInterestsWith(other: UserProfile?): List<String> =
    if (other == null) emptyList() else interests.filter { it in other.interests }

const val SUPER_LIKES_PER_DAY = 3

private fun todayKey(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

fun UserProfile.superLikesLeft(): Int =
    if (lastSuperLikeDay == todayKey()) (SUPER_LIKES_PER_DAY - superLikesUsed).coerceAtLeast(0) else SUPER_LIKES_PER_DAY

fun UserProfile.afterSuperLike(): UserProfile =
    copy(
        superLikesUsed = if (lastSuperLikeDay == todayKey()) superLikesUsed + 1 else 1,
        lastSuperLikeDay = todayKey()
    )

/** Profile completeness in percent plus a hint about what to add next (null when complete). */
fun UserProfile.completeness(): Pair<Int, String?> {
    val checks = listOf(
        (profilePhoto.isNotBlank() || photos.isNotEmpty()) to "Add a profile photo",
        (bio.trim().length >= 20) to "Write a bio of at least 20 characters",
        (interests.size >= 3) to "Pick at least 3 interests",
        lookingFor.isNotBlank() to "Say what you are looking for",
        prompts.any { it.answer.isNotBlank() } to "Answer a profile prompt",
        gender.isNotBlank() to "Add your gender",
        (jobTitle.isNotBlank() || education.isNotBlank()) to "Add your job or education"
    )
    val done = checks.count { it.first }
    val hint = checks.firstOrNull { !it.first }?.second
    return (done * 100 / checks.size) to hint
}

/** Conversation starters built from what the two profiles have in common. */
fun icebreakers(me: UserProfile?, other: UserProfile): List<String> {
    val out = mutableListOf<String>()
    other.sharedInterestsWith(me).firstOrNull()?.let { out += "I see we both like $it! What got you into it?" }
    other.prompts.firstOrNull { it.answer.isNotBlank() }?.let { out += "Loved your answer to \"${it.question}\". Tell me more!" }
    other.interests.firstOrNull()?.let { out += "What is your favorite thing about $it?" }
    if (other.jobTitle.isNotBlank()) out += "How did you end up working as ${other.jobTitle}?"
    if (out.isEmpty()) out += "Hey ${other.displayName.ifBlank { "there" }}! How is your week going?"
    return out.take(3)
}

private const val ONLINE_WINDOW_MS = 5 * 60 * 1000L

/** Online = the app said so AND it checked in within the last 5 minutes (covers crashes/lost signal). */
fun UserProfile.isOnlineNow(now: Long = System.currentTimeMillis()): Boolean {
    val t = lastActive?.toDate()?.time ?: return false
    return online && now - t < ONLINE_WINDOW_MS
}

fun UserProfile.lastSeenText(now: Long = System.currentTimeMillis()): String {
    val t = lastActive?.toDate()?.time ?: return "Offline"
    val min = (now - t) / 60000
    return when {
        min < 1 -> "Last seen just now"
        min < 60 -> "Last seen ${min}m ago"
        min < 60 * 24 -> "Last seen ${min / 60}h ago"
        else -> "Last seen ${min / (60 * 24)}d ago"
    }
}

/** Blue check: the phone is verified, or an admin confirmed the selfie matches the photos. */
fun UserProfile.hasVerifiedBadge(): Boolean = isPhoneVerified || photoVerified

/** Simple scam check on a message before it is sent. Returns a warning, or null if it looks fine. */
object ScamGuard {
    private val moneyWords = listOf(
        "send money", "western union", "moneygram", "gift card", "giftcard", "bitcoin", "crypto", "usdt",
        "wire transfer", "cash app", "cashapp", "paypal", "mpesa", "m-pesa", "bank account", "bank details",
        "investment", "loan", "airtime", "iban", "send me cash"
    )
    private val linkPattern = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)

    fun warningFor(text: String): String? {
        val t = text.lowercase()
        if (moneyWords.any { it in t }) {
            return "This message mentions money or payments. Never send money, gift cards or crypto to someone you have not met in person, whatever the story."
        }
        if (linkPattern.containsMatchIn(t)) {
            return "This message contains a link. Only share links you trust. Scammers use links to steal passwords and money."
        }
        return null
    }
}

object PhotoVerificationPoses {
    val all = listOf(
        "Smile and give a thumbs up",
        "Make a peace sign next to your cheek",
        "Hold up three fingers beside your face",
        "Touch your nose with one finger and smile",
        "Wave at the camera with an open hand"
    )
}

/** A selfie waiting for (or already given) an admin decision. Images are data URIs. */
data class VerificationRequest(
    val uid: String = "",
    val name: String = "",
    val pose: String = "",
    val selfie: String = "",
    val reference: String = "",
    val status: String = "pending",
    val rejectionReason: String = "",
    val createdAt: Timestamp? = null
)

/** One approve / reject decision in the audit trail. */
data class VerificationAuditEntry(
    val userId: String = "",
    val userName: String = "",
    val adminId: String = "",
    val adminEmail: String = "",
    val decision: String = "",
    val reason: String = "",
    val decidedAt: Timestamp? = null
)

object RejectionReasons {
    val all = listOf(
        "Blurry photo",
        "Face does not match profile photos",
        "Face covered (hat, sunglasses, mask)",
        "Wrong pose",
        "Poor lighting",
        "Other"
    )
}

/** App-wide numbers for the admin dashboard. -1 means "could not be loaded". */
data class AdminStats(
    val totalUsers: Long = -1,
    val activeNow: Long = -1,
    val active24h: Long = -1,
    val active7d: Long = -1,
    val newToday: Long = -1,
    val new7d: Long = -1,
    val photoVerified: Long = -1,
    val phoneVerified: Long = -1,
    val vip: Long = -1,
    val banned: Long = -1,
    val paused: Long = -1,
    val men: Long = -1,
    val women: Long = -1,
    val reports: Long = -1,
    val pendingReviews: Long = -1
)

data class SignupDay(val label: String, val count: Long)

/** Someone other users reported, with how often and why. */
data class ReportedUser(
    val userId: String,
    val name: String,
    val photo: String,
    val reports: Int,
    val reasons: List<String>,
    val banned: Boolean
)
