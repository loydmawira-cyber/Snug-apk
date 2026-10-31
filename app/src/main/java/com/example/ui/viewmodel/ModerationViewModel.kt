package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
