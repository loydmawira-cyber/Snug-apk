package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminUserRow
import com.example.data.model.Appeal
import com.example.data.model.MessageReportEntry
import com.example.data.model.ReportCase
import com.example.data.model.SuspensionInfo
import com.example.data.repository.ModerationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reports, suspensions and appeals. One ViewModel for both the admin screens and the suspended person's screen. */
class ModerationViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ModerationRepository(application)

    // ---- Admin ----
    private val _cases = MutableStateFlow<List<ReportCase>>(emptyList())
    val cases: StateFlow<List<ReportCase>> = _cases.asStateFlow()

    private val _loadingCases = MutableStateFlow(false)
    val loadingCases: StateFlow<Boolean> = _loadingCases.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    val openAppeals: StateFlow<List<Appeal>> = repo.observeOpenAppeals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshCases() {
        viewModelScope.launch {
            _loadingCases.value = true
            _cases.value = repo.loadCases()
            _loadingCases.value = false
        }
    }

    suspend fun loadMessageReports(userId: String): List<MessageReportEntry> = repo.loadMessageReports(userId)

    private fun act(okText: String, block: suspend () -> String?) {
        viewModelScope.launch {
            val problem = block()
            _message.value = problem ?: okText
            if (problem == null) refreshCases()
        }
    }

    /** days = 0 means permanent. */
    fun suspendUser(case: ReportCase, days: Int, reason: String) =
        act("${case.name} suspended") { repo.suspendUser(case.userId, case.name, days, reason, case.reports) }

    fun warnUser(case: ReportCase, message: String) =
        act("Warning sent to ${case.name}") { repo.warnUser(case.userId, case.name, message, case.reports) }

    fun dismissReports(case: ReportCase) =
        act("Reports closed") { repo.dismissReports(case.userId, case.name, case.reports) }

    fun reinstate(case: ReportCase) =
        act("${case.name} reinstated") { repo.reinstate(case.userId, case.name) }

    fun decideAppeal(appeal: Appeal, accept: Boolean, response: String) =
        act(if (accept) "Appeal accepted, ${appeal.name} reinstated" else "Appeal denied") {
            repo.decideAppeal(appeal, accept, response)
        }

    // ---- Admin: all registered users ----
    private val _users = MutableStateFlow<List<AdminUserRow>>(emptyList())
    val users: StateFlow<List<AdminUserRow>> = _users.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AdminUserRow>>(emptyList())
    val searchResults: StateFlow<List<AdminUserRow>> = _searchResults.asStateFlow()

    private val _usersLoading = MutableStateFlow(false)
    val usersLoading: StateFlow<Boolean> = _usersLoading.asStateFlow()

    private val _usersExhausted = MutableStateFlow(false)
    val usersExhausted: StateFlow<Boolean> = _usersExhausted.asStateFlow()

    /** Registered people and how many of them are verified (-1 = could not be loaded). */
    private val _userTotals = MutableStateFlow<Pair<Long, Long>?>(null)
    val userTotals: StateFlow<Pair<Long, Long>?> = _userTotals.asStateFlow()

    private var usersCursor: com.google.firebase.firestore.DocumentSnapshot? = null

    /** [reset] starts again from the newest registration. */
    fun loadUsers(reset: Boolean = false) {
        if (_usersLoading.value) return
        if (!reset && _usersExhausted.value) return
        viewModelScope.launch {
            _usersLoading.value = true
            try {
                if (reset) {
                    usersCursor = null
                    _usersExhausted.value = false
                }
                val page = repo.loadUsers(usersCursor)
                usersCursor = page.cursor ?: usersCursor
                _users.value = if (reset) page.users else (_users.value + page.users).distinctBy { it.profile.id }
                _usersExhausted.value = page.exhausted
            } catch (e: Exception) {
                _message.value = "Could not load users: ${e.message}"
            }
            _usersLoading.value = false
        }
    }

    fun loadUserTotals() {
        viewModelScope.launch { _userTotals.value = repo.loadUserTotals() }
    }

    fun searchUsers(text: String) {
        viewModelScope.launch {
            try {
                _searchResults.value = repo.searchUsers(text)
            } catch (e: Exception) {
                _message.value = "Search failed: ${e.message}"
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    /** Reloads one person after an admin action so the list shows the new state. */
    private suspend fun refreshUser(userId: String) {
        val fresh = repo.loadUser(userId) ?: return
        _users.value = _users.value.map { if (it.profile.id == userId) fresh else it }
        _searchResults.value = _searchResults.value.map { if (it.profile.id == userId) fresh else it }
    }

    private fun actOnUser(userId: String, okText: String, block: suspend () -> String?) {
        viewModelScope.launch {
            val problem = block()
            _message.value = problem ?: okText
            if (problem == null) {
                refreshUser(userId)
                loadUserTotals()
            }
        }
    }

    fun setVerified(row: AdminUserRow, verified: Boolean) =
        actOnUser(row.profile.id, if (verified) "${row.profile.displayName} verified" else "Badge removed") {
            repo.setVerified(row.profile.id, row.profile.displayName, verified)
        }

    /** days = 0 means permanent. */
    fun suspendPerson(row: AdminUserRow, days: Int, reason: String) =
        actOnUser(row.profile.id, "${row.profile.displayName} suspended") {
            repo.suspendUser(row.profile.id, row.profile.displayName, days, reason, emptyList())
        }

    fun reinstatePerson(row: AdminUserRow) =
        actOnUser(row.profile.id, "${row.profile.displayName} reinstated") {
            repo.reinstate(row.profile.id, row.profile.displayName)
        }

    /** Call after a selfie was approved or rejected so the row shows the new badge. */
    fun refreshPerson(userId: String) {
        viewModelScope.launch {
            refreshUser(userId)
            loadUserTotals()
        }
    }

    // ---- The suspended person ----
    val mySuspension: StateFlow<SuspensionInfo?> = repo.observeMySuspension()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val myAppeal: StateFlow<Appeal?> = repo.observeMyAppeal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** [onDone] gets null on success, otherwise an error message. */
    fun submitAppeal(name: String, message: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            onDone(repo.submitAppeal(name, message, mySuspension.value))
        }
    }

    fun liftIfExpired() {
        viewModelScope.launch {
            mySuspension.value?.let { repo.liftIfExpired(it) }
        }
    }
}
