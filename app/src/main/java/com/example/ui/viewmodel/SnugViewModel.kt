package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.UserPhoto
import com.example.data.model.UserProfile
import com.example.data.util.calculateAge
import com.example.data.repository.ChatRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.SeedRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SnugViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepo = ProfileRepository(application)
    private val matchRepo = MatchRepository(application)
    private val chatRepo = ChatRepository(application)
    private val seedRepo = SeedRepository(application)

    val currentUserId = profileRepo.getCurrentUserId()

    private val _discoveryProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val discoveryProfiles: StateFlow<List<UserProfile>> = _discoveryProfiles.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _currentProfileIndex = MutableStateFlow(0)
    val currentProfile: StateFlow<UserProfile?> = combine(_discoveryProfiles, _currentProfileIndex) { profiles, index ->
        if (profiles.isNotEmpty() && index < profiles.size) profiles[index] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        loadDiscoveryProfiles()
        loadMatches()
        loadCurrentUserProfile()
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
                        age in me.minAge..me.maxAge
                    }
                }
                .collect {
                    android.util.Log.d("SnugViewModel", "Loaded ${it.size} profiles")
                    _discoveryProfiles.value = it
                }
        }
    }

    private fun loadMatches() {
        viewModelScope.launch {
            matchRepo.observeMatches().collect { matches ->
                _matches.value = matches
            }
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    fun nextProfile() {
        if (_discoveryProfiles.value.isNotEmpty()) {
            _currentProfileIndex.value = (_currentProfileIndex.value + 1) % _discoveryProfiles.value.size
        }
    }

    fun likeProfile(profileId: String) {
        android.util.Log.d("SnugViewModel", "Liking profile: $profileId")
        _uiMessage.value = "Liked!"
        nextProfile()
        viewModelScope.launch {
            matchRepo.createMatch(profileId)
        }
    }

    fun passProfile() {
        android.util.Log.d("SnugViewModel", "Passing profile")
        _uiMessage.value = "Passed"
        nextProfile()
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
