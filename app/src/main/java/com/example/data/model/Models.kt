package com.example.data.model

import com.google.firebase.Timestamp

data class UserPhoto(
    val url: String = "",
    val isBlurred: Boolean = false,
    val isPublic: Boolean = true,
    val allowedUserIds: List<String> = emptyList()
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
    val createdAt: Timestamp? = null
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

/** "City, Country - 12 km away", falling back gracefully when something is unknown. */
fun placeAndDistance(profile: UserProfile, distanceText: String): String {
    val place = profile.locationLabel()
    val dist = distanceText.takeIf { it != "Distance unknown" }
    return when {
        place.isNotBlank() && dist != null -> "$place \u2022 $dist"
        place.isNotBlank() -> place
        dist != null -> dist
        else -> "Location unknown"
    }
}

object ProfileOptions {
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
