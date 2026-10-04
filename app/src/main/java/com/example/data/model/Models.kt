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
