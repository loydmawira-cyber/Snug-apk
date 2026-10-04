package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.UserPhoto
import com.example.data.model.UserProfile
import com.example.data.util.calculateAge
import com.example.data.util.distanceKm
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import com.example.data.repository.ChatRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.SeedRepository
import com.example.data.repository.SocialRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SnugViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepo = ProfileRepository(application)
    private val matchRepo = MatchRepository(application)
    private val chatRepo = ChatRepository(application)
    private val seedRepo = SeedRepository(application)
    private val socialRepo = SocialRepository(application)

    val currentUserId = profileRepo.getCurrentUserId()

    private val _discoveryProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val discoveryProfiles: StateFlow<List<UserProfile>> = _discoveryProfiles.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    // Everyone I already liked or passed - they are removed from Discover
    private val _swipedIds = MutableStateFlow<Set<String>>(emptySet())

    // Swiped people leave the list, so the next card is always the first one
    val currentProfile: StateFlow<UserProfile?> = _discoveryProfiles
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        loadDiscoveryProfiles()
        loadMatches()
        loadCurrentUserProfile()
        loadSwipes()
        loadNotifications()
    }

    private fun loadSwipes() {
        viewModelScope.launch {
            socialRepo.observeMySwipes().collect { _swipedIds.value = _swipedIds.value + it }
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            val seen = mutableSetOf<String>()
            var first = true
            socialRepo.observeNotifications().collect { list ->
                _notifications.value = list
                if (!first) {
                    // Pop up the newest notification that arrived while the app is open
                    list.firstOrNull { !it.read && it.id !in seen }?.let { _uiMessage.value = it.text }
                }
                seen.addAll(list.map { it.id })
                first = false
            }
        }
    }

    fun markNotificationsRead() {
        val unread = _notifications.value.filter { !it.read }.map { it.id }
        viewModelScope.launch { socialRepo.markRead(unread) }
    }

    private fun loadCurrentUserProfile() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            try {
                val profile = profileRepo.getProfile(uid)
                if (profile == null) {
                    val newProfile = UserProfile(id = uid, displayName = "New User", createdAt = com.google.firebase.Timestamp.now())
                    profileRepo.updateProfile(newProfile)
                    _currentUserProfile.value = newProfile
                } else {
                    _currentUserProfile.value = profile
                }
            } catch (e: Exception) {
                _uiMessage.value = "Profile Error: ${e.message}"
            }
        }
    }

    private fun loadDiscoveryProfiles() {
        viewModelScope.launch {
            profileRepo.observeDiscoveryProfiles()
                .combine(_currentUserProfile) { list, me ->
                    if (me == null) list
                    else list.filter { u ->
                        val age = calculateAge(u.birthDate)
                        val ageOk = age in me.minAge..me.maxAge
                        val d = distanceTo(me, u)
                        // If either person has no location yet we can't measure, so keep them
                        val distOk = d == null || d <= me.radiusKm
                        ageOk && distOk
                    }
                }
                .combine(_swipedIds) { list, swiped -> list.filter { it.id !in swiped } }
                .collect {
                    android.util.Log.d("SnugViewModel", "Loaded ${it.size} profiles")
                    _discoveryProfiles.value = it
                }
        }
    }

    private fun loadMatches() {
        viewModelScope.launch {
            matchRepo.observeMatches().collect { list ->
                val me = currentUserId
                // One entry per person, even if older duplicate matches exist
                val unique = list
                    .groupBy { m -> m.userIds.firstOrNull { it != me } ?: m.id }
                    .values
                    .mapNotNull { group ->
                        group.maxWithOrNull(
                            compareBy({ it.lastMessage.isNotEmpty() }, { it.lastMessageAt?.toDate()?.time ?: 0L })
                        )
                    }
                    .sortedByDescending { it.lastMessageAt?.toDate()?.time ?: 0L }
                _matches.value = unique.map { m ->
                    val otherId = m.userIds.firstOrNull { it != me }
                    val other = otherId?.let { profileRepo.getProfile(it) }
                    m.copy(otherUser = other)
                }
            }
        }
    }

    suspend fun loadUser(id: String): UserProfile? =
        profileRepo.getProfile(id) ?: _discoveryProfiles.value.firstOrNull { it.id == id }

    /** Opens (creating if needed) the chat with [userId] and returns the match id via [onReady]. */
    fun openChat(userId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val id = matchRepo.findOrCreateMatch(userId)
            if (id != null) onReady(id) else _uiMessage.value = "Could not start chat"
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }


    fun likeProfile(profileId: String) {
        _swipedIds.value = _swipedIds.value + profileId
        viewModelScope.launch {
            val myName = _currentUserProfile.value?.displayName?.ifBlank { null } ?: "Someone"
            val result = socialRepo.swipe(profileId, true, myName)
            _uiMessage.value = when {
                result.isFailure -> "Could not save like: ${result.exceptionOrNull()?.message}"
                result.getOrDefault(false) -> "It's a match! \uD83C\uDF89"
                else -> "Liked!"
            }
        }
    }

    fun passProfile() {
        val id = currentProfile.value?.id ?: return
        _swipedIds.value = _swipedIds.value + id
        viewModelScope.launch { socialRepo.swipe(id, false, "") }
    }

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> {
        return chatRepo.observeMessages(matchId)
    }

    fun sendMessage(matchId: String, text: String) {
        viewModelScope.launch {
            chatRepo.sendMessage(matchId, text)
        }
    }

    fun updateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            val result = profileRepo.updateProfile(profile)
            if (result.isSuccess) {
                _currentUserProfile.value = profile
                _uiMessage.value = "Profile updated!"
            } else {
                _uiMessage.value = "Save failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    /** Distance in km between the signed-in user and [other], or null if either has no location. */
    fun distanceTo(me: UserProfile?, other: UserProfile): Double? {
        if (me == null) return null
        val lat1 = me.latitude ?: return null
        val lon1 = me.longitude ?: return null
        val lat2 = other.latitude ?: return null
        val lon2 = other.longitude ?: return null
        return distanceKm(lat1, lon1, lat2, lon2)
    }

    fun distanceTo(other: UserProfile): Double? = distanceTo(_currentUserProfile.value, other)

    /** Call only after location permission is granted. */
    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        viewModelScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
                var loc = client.lastLocation.await()
                if (loc == null) {
                    loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                }
                if (loc != null) {
                    // Rounded to 2 decimals (~1 km) so exact position is never stored
                    val lat = Math.round(loc.latitude * 100) / 100.0
                    val lon = Math.round(loc.longitude * 100) / 100.0
                    val me = _currentUserProfile.value ?: return@launch
                    if (me.latitude == lat && me.longitude == lon) return@launch
                    val updated = me.copy(latitude = lat, longitude = lon)
                    if (profileRepo.updateProfile(updated).isSuccess) {
                        _currentUserProfile.value = updated
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SnugViewModel", "Location failed", e)
            }
        }
    }

    fun setProfilePicture(uri: android.net.Uri) {
        viewModelScope.launch {
            val me = _currentUserProfile.value ?: return@launch
            _uiMessage.value = "Saving profile picture..."
            val enc = profileRepo.encodePhoto(getApplication(), uri, 400)
            if (enc.isFailure) {
                _uiMessage.value = "Picture failed: ${enc.exceptionOrNull()?.message}"
                return@launch
            }
            val updated = me.copy(profilePhoto = enc.getOrThrow())
            val saved = profileRepo.updateProfile(updated)
            if (saved.isSuccess) {
                _currentUserProfile.value = updated
                _uiMessage.value = "Profile picture updated!"
            } else {
                _uiMessage.value = "Picture save failed: ${saved.exceptionOrNull()?.message}"
            }
        }
    }

    fun addPhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            val me = _currentUserProfile.value ?: return@launch
            if (me.photos.size >= 4) {
                _uiMessage.value = "Maximum 4 photos"
                return@launch
            }
            _uiMessage.value = "Saving photo..."
            val upload = profileRepo.encodePhoto(getApplication(), uri)
            if (upload.isFailure) {
                _uiMessage.value = "Photo failed: ${upload.exceptionOrNull()?.message}"
                return@launch
            }
            val latest = _currentUserProfile.value ?: me
            val updated = latest.copy(photos = latest.photos + UserPhoto(url = upload.getOrThrow(), isPublic = true))
            val saved = profileRepo.updateProfile(updated)
            if (saved.isSuccess) {
                _currentUserProfile.value = updated
                _uiMessage.value = "Photo added!"
            } else {
                _uiMessage.value = "Photo save failed: ${saved.exceptionOrNull()?.message}"
            }
        }
    }

    fun seedData() {
        viewModelScope.launch {
            _uiMessage.value = "Seeding sample data..."
            val result = seedRepo.seedData()
            if (result.isSuccess) {
                _uiMessage.value = "Sample data seeded successfully!"
                loadDiscoveryProfiles()
            } else {
                _uiMessage.value = "Failed to seed data: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}
