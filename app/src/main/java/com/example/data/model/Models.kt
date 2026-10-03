package com.example.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val id: String = "",
    val displayName: String = "",
    val bio: String = "",
    val birthDate: Timestamp? = null,
    val gender: String = "",
    val interestedIn: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val isVip: Boolean = false,
    val dailyLikesUsed: Int = 0,
    val lastLikeReset: Timestamp? = null,
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
