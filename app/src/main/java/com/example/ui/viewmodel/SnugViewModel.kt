package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.DiscoverFilters
import com.google.firebase.auth.auth
import com.example.data.model.afterSuperLike
import com.example.data.model.superLikesLeft
import com.example.data.model.UserPhoto
import com.example.data.model.UserProfile
import com.example.data.security.EmailVerifier
import com.example.data.model.hasVerifiedBadge
import com.example.data.model.publicViewForOthers
import com.example.data.util.calculateAge
import com.example.data.util.distanceKm
import com.example.data.util.reverseGeocode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import com.example.data.repository.AdminRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.SafetyRepository
import com.example.data.repository.SocialRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SnugViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepo = ProfileRepository(application)
    private val matchRepo = MatchRepository(application)
    private val chatRepo = ChatRepository(application)
    private val socialRepo = SocialRepository(application)
    private val safetyRepo = SafetyRepository(application)
    private val adminRepo = AdminRepository(application)

    // People I blocked and people who blocked me (hidden both ways)
    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())

    // How many profiles to fetch from Firestore; grows when the queue runs low
    private val _profileLimit = MutableStateFlow(100)
    val profileLimit: StateFlow<Int> = _profileLimit.asStateFlow()

    private val _filters = MutableStateFlow(DiscoverFilters())
    val filters: StateFlow<DiscoverFilters> = _filters.asStateFlow()

    private val _discoveryRetry = MutableStateFlow(0)
    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    private val _lastPassedId = MutableStateFlow<String?>(null)
    val canUndo: StateFlow<Boolean> = _lastPassedId
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentUserId = profileRepo.getCurrentUserId()

    private val _discoveryProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val discoveryProfiles: StateFlow<List<UserProfile>> = _discoveryProfiles.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    // Only people I liked leave Discover. People I passed come back later (at the end of the queue).
    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _passedIds = MutableStateFlow<List<String>>(emptyList())
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private var presenceJob: Job? = null

    /** Starts the online heartbeat (every 2 minutes while the app is open). */
    fun goOnline() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (true) {
                profileRepo.setPresence(true)
                delay(120_000)
            }
        }
    }

    fun stopPresence() {
        presenceJob?.cancel()
    }

    /** Marks me offline (app in background or logging out); [then] runs when it is saved. */
    fun goOffline(then: () -> Unit = {}) {
        presenceJob?.cancel()
        viewModelScope.launch {
            profileRepo.setPresence(false)
            then()
        }
    }

    // People who liked me and I have not answered yet (not liked back, blocked or matched)
    private val _likedMeIds = MutableStateFlow<Set<String>>(emptySet())
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    val likesYou: StateFlow<List<UserProfile>> = combine(_likedMeIds, _likedIds, _blockedIds, _matches) { likedMe, liked, blocked, matches ->
        val matched = matches.flatMap { it.userIds }.toSet()
        likedMe.filter { it !in liked && it !in blocked && it !in matched }
    }
        .distinctUntilChanged()
        .map { ids -> ids.mapNotNull { profileRepo.getProfile(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // Everyone matching my age/distance filters (used by Radar, includes people I already liked)
    private val _nearbyProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val nearbyProfiles: StateFlow<List<UserProfile>> = _nearbyProfiles.asStateFlow()

    // Swiped people leave the list, so the next card is always the first one
    val currentProfile: StateFlow<UserProfile?> = _discoveryProfiles
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        loadBlocks()
        loadVerificationStatus()
        loadAdmin()
        loadLikesReceived()
        loadDiscoveryProfiles()
        loadMatches()
        loadCurrentUserProfile()
        loadSwipes()
        loadNotifications()
    }

    private fun loadLikesReceived() {
        viewModelScope.launch {
            socialRepo.observeLikesReceived().collect { _likedMeIds.value = it }
        }
    }

    /** Deletes the account. [onDone] runs only when it worked. */
    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiMessage.value = "Deleting account..."
            val error = safetyRepo.deleteAccount()
            if (error == null) onDone() else _uiMessage.value = error
        }
    }

    private fun loadBlocks() {
        viewModelScope.launch {
            safetyRepo.observeBlockedIds().collect { _blockedIds.value = it }
        }
    }

    fun setFilters(f: DiscoverFilters) {
        _filters.value = f
    }

    fun setPaused(paused: Boolean) {
        val me = _currentUserProfile.value ?: return
        viewModelScope.launch {
            val updated = me.copy(isPaused = paused)
            val result = profileRepo.updateProfile(updated)
            if (result.isSuccess) {
                _currentUserProfile.value = updated
                _uiMessage.value = if (paused) "Profile paused. Nobody can see you." else "Profile is visible again."
            } else {
                _uiMessage.value = "Save failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun undoPass() {
        val id = _lastPassedId.value ?: return
        _passedIds.value = _passedIds.value.filter { it != id }
        _lastPassedId.value = null
    }

    fun blockUser(userId: String) {
        viewModelScope.launch {
            val sharedMatches = _matches.value.filter { it.userIds.contains(userId) }
            if (safetyRepo.block(userId)) {
                _blockedIds.value = _blockedIds.value + userId
                sharedMatches.forEach { matchRepo.unmatch(it.id) }
                _uiMessage.value = "User blocked"
            } else {
                _uiMessage.value = "Could not block user"
            }
        }
    }

    fun reportUser(userId: String, reason: String) {
        viewModelScope.launch {
            _uiMessage.value = when (safetyRepo.report(userId, reason)) {
                0 -> "Report sent. Thank you."
                1 -> "You already reported this person"
                else -> "Could not send report"
            }
        }
    }

    // ---- Photo (selfie) verification, reviewed by an admin ----
    private val _photoVerificationStatus = MutableStateFlow<String?>(null)
    val photoVerificationStatus: StateFlow<String?> = _photoVerificationStatus.asStateFlow()

    private val _photoRejectionReason = MutableStateFlow("")
    val photoRejectionReason: StateFlow<String> = _photoRejectionReason.asStateFlow()

    private fun loadVerificationStatus() {
        viewModelScope.launch {
            safetyRepo.observeVerificationStatus().collect { _photoVerificationStatus.value = it }
        }
        viewModelScope.launch {
            safetyRepo.observeVerificationReason().collect { _photoRejectionReason.value = it }
        }
    }

    // ---- Admin review dashboard (only people listed in the `admins` collection) ----
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _pendingVerifications = MutableStateFlow<List<com.example.data.model.VerificationRequest>>(emptyList())
    val pendingVerifications: StateFlow<List<com.example.data.model.VerificationRequest>> = _pendingVerifications.asStateFlow()

    private fun loadAdmin() {
        viewModelScope.launch {
            if (adminRepo.isAdmin()) {
                _isAdmin.value = true
                adminRepo.observePending().collect { _pendingVerifications.value = it }
            }
        }
    }

    fun observeVerificationAudit() = adminRepo.observeAudit()

    suspend fun loadAdminStats() = adminRepo.loadStats()
    suspend fun loadSignups() = adminRepo.loadSignups()
    suspend fun loadMostReported() = adminRepo.loadMostReported()

    fun setSuspended(user: com.example.data.model.ReportedUser, banned: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val problem = adminRepo.setBanned(user.userId, user.name, banned)
            _uiMessage.value = problem ?: if (banned) "${user.name} suspended" else "${user.name} reinstated"
            if (problem == null) onDone()
        }
    }

    fun decideVerification(request: com.example.data.model.VerificationRequest, approve: Boolean, reason: String = "") {
        viewModelScope.launch {
            val problem = adminRepo.decide(request, approve, reason)
            _uiMessage.value = problem ?: if (approve) "Approved ${request.name.ifBlank { "user" }}" else "Rejected"
        }
    }

    fun submitSelfie(pose: String, selfie: android.graphics.Bitmap) {
        viewModelScope.launch {
            val dataUri = withContext(Dispatchers.Default) {
                val maxSide = 640
                val scale = minOf(1f, maxSide.toFloat() / maxOf(selfie.width, selfie.height))
                val bmp = if (scale < 1f) {
                    android.graphics.Bitmap.createScaledBitmap(
                        selfie, (selfie.width * scale).toInt(), (selfie.height * scale).toInt(), true
                    )
                } else selfie
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            }
            val me = _currentUserProfile.value
            val reference = me?.profilePhoto?.takeIf { it.isNotBlank() } ?: me?.photos?.firstOrNull()?.url ?: ""
            _uiMessage.value = if (safetyRepo.submitPhotoVerification(pose, dataUri, reference, me?.displayName ?: "")) {
                "Selfie sent. We will review it soon."
            } else {
                "Could not send selfie"
            }
        }
    }

    fun unmatch(matchId: String) {
        viewModelScope.launch {
            _uiMessage.value = if (matchRepo.unmatch(matchId)) "Unmatched" else "Could not unmatch"
        }
    }

    private fun loadSwipes() {
        viewModelScope.launch {
            socialRepo.observeMySwipes().collect { _likedIds.value = _likedIds.value + it }
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
                // Only a confirmed missing document may create a new profile. A network or
                // permission failure must never overwrite the user's existing document.
                val profile = profileRepo.getProfileOrThrow(uid)
                if (profile == null) {
                    val newProfile = UserProfile(id = uid, displayName = "New User", createdAt = com.google.firebase.Timestamp.now())
                    val saved = profileRepo.updateProfile(newProfile)
                    if (saved.isSuccess) _currentUserProfile.value = newProfile
                    else _uiMessage.value = "Could not create profile: ${saved.exceptionOrNull()?.message}"
                } else {
                    // Clear any badge that is not backed by a really linked phone number
                    val phoneLinked = !com.google.firebase.Firebase.auth.currentUser?.phoneNumber.isNullOrBlank()
                    if (profile.isPhoneVerified && !phoneLinked) {
                        val fixed = profile.copy(isPhoneVerified = false)
                        profileRepo.updateProfile(fixed)
                        _currentUserProfile.value = fixed
                    } else {
                        _currentUserProfile.value = profile
                    }
                }
            } catch (e: Exception) {
                _uiMessage.value = "Profile Error: ${e.message}"
            }
        }
    }

    /** Called when the Discover queue is nearly empty: fetches more people (up to 500). */
    fun loadMoreProfiles() {
        if (_profileLimit.value < 500) _profileLimit.value = _profileLimit.value + 100
    }

    fun retryDiscovery() {
        _discoveryError.value = null
        _discoveryRetry.value += 1
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadDiscoveryProfiles() {
        viewModelScope.launch {
            combine(
                _currentUserProfile.map { it?.interestedIn ?: emptyList() }.distinctUntilChanged(),
                _profileLimit,
                _discoveryRetry
            ) { genders, limit, _ -> genders to limit }
                .flatMapLatest { (genders, limit) ->
                    _discoveryError.value = null
                    profileRepo.observeDiscoveryProfiles(genders, limit.toLong()) { error ->
                        _discoveryError.value = error.message ?: "Check your connection and Firebase access."
                    }
                }
                .combine(_currentUserProfile) { list, me ->
                    if (me == null) list
                    else list.filter { u ->
                        // They must also be interested in my gender (empty = open to everyone)
                        val theyLikeMe = u.interestedIn.isEmpty() || me.gender.isBlank() || me.gender in u.interestedIn
                        val age = calculateAge(u.birthDate)
                        val ageOk = theyLikeMe && age in me.minAge..me.maxAge
                        val d = distanceTo(me, u)
                        // If either person has no location yet we can't measure, so keep them
                        val distOk = d == null || d <= me.radiusKm
                        ageOk && distOk
                    }
                }
                .combine(_blockedIds) { list, blocked ->
                    list.filter { it.id !in blocked && !it.isPaused && !it.banned }
                }
                .collect { profiles ->
                    android.util.Log.d("SnugViewModel", "Loaded ${profiles.size} profiles")
                    _nearbyProfiles.value = profiles.map { it.publicViewForOthers() }
                }
        }
        viewModelScope.launch {
            combine(_nearbyProfiles, _likedIds, _passedIds, _filters) { list, liked, passed, f ->
                // The deck never runs out: new people first, then people liked in the past,
                // then everyone I already swiped on this session (oldest first), and it starts over.
                list.filter { !f.verifiedOnly || it.hasVerifiedBadge() }
                    .filter { f.lookingFor.isBlank() || it.lookingFor == f.lookingFor }
                    .sortedBy { u ->
                        val i = passed.indexOf(u.id)
                        when {
                            i >= 0 -> 1000 + i
                            u.id in liked -> 500
                            else -> 0
                        }
                    }
            }.collect { _discoveryProfiles.value = it }
        }
    }

    private fun loadMatches() {
        viewModelScope.launch {
            combine(matchRepo.observeMatches(), _blockedIds) { l, b -> l to b }.collect { (rawList, blocked) ->
                val me = currentUserId
                val list = rawList.filter { m -> m.userIds.none { it in blocked } }
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


    private val _matchCelebration = MutableStateFlow<UserProfile?>(null)
    val matchCelebration: StateFlow<UserProfile?> = _matchCelebration.asStateFlow()

    fun dismissCelebration() {
        _matchCelebration.value = null
    }

    fun likeProfile(profileId: String, superLike: Boolean = false) {
        viewModelScope.launch {
            val alreadyLiked = profileId in _likedIds.value
            // Send this card to the back of the deck so the loop keeps going
            _passedIds.value = _passedIds.value.filter { it != profileId } + profileId
            if (alreadyLiked && !superLike) {
                _uiMessage.value = "You already liked this person"
                return@launch
            }
            if (superLike) {
                val me = _currentUserProfile.value
                // Admins and VIP members have unlimited Super Likes
                val premium = _isAdmin.value || me?.isVip == true
                if (me == null || (!premium && me.superLikesLeft() <= 0)) {
                    _uiMessage.value = "No Super Likes left today. They refresh tomorrow."
                    return@launch
                }
                if (!premium) {
                    val updatedMe = me.afterSuperLike()
                    if (profileRepo.updateProfile(updatedMe).isSuccess) _currentUserProfile.value = updatedMe
                }
            }
            _likedIds.value = _likedIds.value + profileId
            val myName = _currentUserProfile.value?.displayName?.ifBlank { null } ?: "Someone"
            val result = socialRepo.swipe(profileId, true, myName, superLike)
            if (result.isFailure) {
                _uiMessage.value = "Could not save like: ${result.exceptionOrNull()?.message}"
            } else if (result.getOrDefault(false)) {
                _matchCelebration.value = _nearbyProfiles.value.firstOrNull { it.id == profileId }
                    ?: profileRepo.getProfile(profileId)
            } else {
                _uiMessage.value = if (superLike) "Super Like sent! \u2B50" else "Liked!"
            }
        }
    }

    fun nudge(userId: String) {
        viewModelScope.launch {
            val myName = _currentUserProfile.value?.displayName?.ifBlank { null } ?: "Someone"
            val result = socialRepo.nudge(userId, myName)
            _uiMessage.value = if (result.isSuccess) {
                "Nudge sent \uD83D\uDC4B"
            } else if ((result.exceptionOrNull() as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                "You already nudged them today"
            } else {
                "Could not send nudge"
            }
        }
    }

    fun superLikesLeft(): Int = _currentUserProfile.value?.superLikesLeft() ?: 0

    fun completeOnboarding() {
        val me = _currentUserProfile.value ?: return
        if (me.onboarded) return
        viewModelScope.launch {
            val updated = me.copy(onboarded = true)
            if (profileRepo.updateProfile(updated).isSuccess) _currentUserProfile.value = updated
        }
    }

    fun sendPhoto(matchId: String, uri: android.net.Uri) {
        if (!EmailVerifier.isVerified()) {
            _emailGate.value = true
            return
        }
        viewModelScope.launch {
            _uiMessage.value = "Sending photo..."
            val enc = profileRepo.encodePhoto(getApplication(), uri, 800, 200_000)
            if (enc.isFailure) {
                _uiMessage.value = "Photo failed: ${enc.exceptionOrNull()?.message}"
            } else {
                chatRepo.sendMessage(matchId, "", enc.getOrThrow())
                _uiMessage.value = null
            }
        }
    }

    fun setReaction(messageId: String, emoji: String) {
        viewModelScope.launch { chatRepo.setReaction(messageId, emoji) }
    }

    fun passProfile() {
        val id = currentProfile.value?.id ?: return
        _passedIds.value = _passedIds.value.filter { it != id } + id
        _lastPassedId.value = id
    }

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> {
        return chatRepo.observeMessages(matchId)
    }

    fun observeTyping(matchId: String, otherUserId: String): Flow<Boolean> =
        chatRepo.observeTyping(matchId, otherUserId)

    fun setTyping(matchId: String, typing: Boolean) {
        viewModelScope.launch { chatRepo.setTyping(matchId, typing) }
    }

    /** Marks everything the other person sent as seen. */
    fun markMessagesSeen(messages: List<ChatMessage>) {
        val me = currentUserId ?: return
        val unseen = messages.filter { it.senderId != me && it.readAt == null && it.id.isNotBlank() }.map { it.id }
        if (unseen.isEmpty()) return
        viewModelScope.launch { chatRepo.markSeen(unseen) }
    }

    // Messaging needs a verified email (it keeps fake and throw-away accounts out of chats)
    private val _emailGate = MutableStateFlow(false)
    val emailGate: StateFlow<Boolean> = _emailGate.asStateFlow()
    fun dismissEmailGate() { _emailGate.value = false }

    /** Returns true if the message was sent, false if the email must be verified first. */
    fun sendMessage(matchId: String, text: String): Boolean {
        if (!EmailVerifier.isVerified()) {
            _emailGate.value = true
            return false
        }
        viewModelScope.launch {
            chatRepo.sendMessage(matchId, text)
        }
        return true
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
                    // Rounded to 3 decimals (~100 m) so distances are useful without storing
                    // an exact device position.
                    val lat = Math.round(loc.latitude * 1000) / 1000.0
                    val lon = Math.round(loc.longitude * 1000) / 1000.0
                    val me = _currentUserProfile.value ?: return@launch
                    if (me.latitude == lat && me.longitude == lon && me.city.isNotBlank()) return@launch
                    val (city, country) = withContext(Dispatchers.IO) {
                        reverseGeocode(getApplication(), lat, lon)
                    }
                    val updated = me.copy(
                        latitude = lat,
                        longitude = lon,
                        city = city.ifBlank { me.city },
                        country = country.ifBlank { me.country }
                    )
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
            // Keep the avatar sharp on modern phones while remaining small enough for Firestore.
            val enc = profileRepo.encodePhoto(getApplication(), uri, 600, 90_000)
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
            val upload = profileRepo.encodePhoto(getApplication(), uri, 800, 140_000)
            if (upload.isFailure) {
                _uiMessage.value = "Photo failed: ${upload.exceptionOrNull()?.message}"
                return@launch
            }
            val latest = _currentUserProfile.value ?: me
            val totalBytes = latest.profilePhoto.length + latest.photos.sumOf { it.url.length } + upload.getOrThrow().length
            if (totalBytes > 800_000) {
                _uiMessage.value = "Your photos use too much space. Remove one before adding another."
                return@launch
            }
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

}
