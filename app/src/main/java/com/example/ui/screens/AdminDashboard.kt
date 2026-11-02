package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.model.AdminStats
import com.example.data.model.RejectionReasons
import com.example.data.model.ReportedUser
import com.example.data.model.SignupDay
import com.example.data.model.VerificationAuditEntry
import com.example.data.model.VerificationRequest
import com.example.ui.components.SnugImage
import com.example.ui.viewmodel.SnugViewModel

import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.stringResource

/** Admin-only: compare each live selfie with the user's reference photo, then approve or reject. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(viewModel: SnugViewModel, navController: NavController) {
    val isAdmin by viewModel.isAdmin.collectAsState()
    val pending by viewModel.pendingVerifications.collectAsState()
    val audit by remember { viewModel.observeVerificationAudit() }.collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(6) }
    var rejecting by remember { mutableStateOf<VerificationRequest?>(null) }
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var statsRefresh by remember { mutableStateOf(0) }
    var signups by remember { mutableStateOf<List<SignupDay>>(emptyList()) }
    var reported by remember { mutableStateOf<List<ReportedUser>>(emptyList()) }
    var sortOldestFirst by remember { mutableStateOf(false) }
    val modVm: com.example.ui.viewmodel.ModerationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val reportCases by modVm.cases.collectAsState()
    val openAppeals by modVm.openAppeals.collectAsState()

    LaunchedEffect(isAdmin) {
        if (isAdmin) modVm.refreshCases()
    }

    val sortedPending = remember(pending, sortOldestFirst) {
        if (sortOldestFirst) {
            pending.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
        } else {
            pending.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
    }

    LaunchedEffect(tab, statsRefresh, isAdmin) {
        if ((tab == 2 || tab == 6) && isAdmin) {
            stats = null
            stats = viewModel.loadAdminStats()
            signups = viewModel.loadSignups()
            reported = viewModel.loadMostReported()
        }
    }

    rejecting?.let { req ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text(stringResource(com.example.R.string.admin_reject_title, req.name.ifBlank { "this selfie" })) },
            text = {
                Column {
                    Text(stringResource(R.string.ui_pick_a_reason_the_user_sees_it_and_can_try_again), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RejectionReasons.all.forEach { option ->
                            FilterChip(
                                selected = reason == option,
                                onClick = { reason = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reason.isNotBlank(),
                    onClick = {
                        viewModel.decideVerification(req, approve = false, reason = reason)
                        rejecting = null
                    }
                ) { Text(stringResource(R.string.ui_reject), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_admin_dashboard)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back))
                    }
                }
            )
        }
    ) { padding ->
        if (!isAdmin) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_you_do_not_have_access_to_this_page), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tab == 6) {
                DashboardOverview(
                    stats = stats,
                    signups = signups,
                    pendingCount = pending.size,
                    reportCount = reportCases.size,
                    appealCount = openAppeals.size,
                    audit = audit,
                    onNavigate = { tab = it },
                    onRefresh = { statsRefresh++ }
                )
            } else if (tab == 5) {
                UsersTab(
                    vm = modVm,
                    pending = pending,
                    onApprove = { req -> viewModel.decideVerification(req, approve = true) },
                    onReject = { req -> rejecting = req }
                )
            } else if (tab == 3) {
                ReportsTab(modVm)
            } else if (tab == 4) {
                AppealsTab(modVm)
            } else if (tab == 2) {
                StatsTab(
                    stats = stats,
                    signups = signups,
                    reported = reported,
                    onSuspend = { user, banned -> viewModel.setSuspended(user, banned) { statsRefresh++ } },
                    onToggleVip = { uid, vip -> viewModel.setVipStatus(uid, vip) { statsRefresh++ } },
                    onRefresh = { statsRefresh++ }
                )
            } else if (tab == 0) {
                if (pending.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ui_no_selfies_waiting_all_caught_up), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { sortOldestFirst = !sortOldestFirst },
                                label = { Text(if (sortOldestFirst) stringResource(com.example.R.string.ui_oldest_first) else stringResource(com.example.R.string.ui_newest_first)) },
                                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = { viewModel.bulkApprovePending() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(com.example.R.string.admin_approve_all, pending.size))
                            }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(sortedPending, key = { it.uid }) { req ->
                                RequestCard(
                                    req = req,
                                    onApprove = { viewModel.decideVerification(req, approve = true) },
                                    onReject = { rejecting = req }
                                )
                            }
                        }
                    }
                }
            } else {
                if (audit.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ui_no_decisions_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(audit) { AuditRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardOverview(
    stats: AdminStats?,
    signups: List<SignupDay>,
    pendingCount: Int,
    reportCount: Int,
    appealCount: Int,
    audit: List<VerificationAuditEntry>,
    onNavigate: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    val plum = MaterialTheme.colorScheme.onSurface
    val rose = MaterialTheme.colorScheme.primary
    val softRose = MaterialTheme.colorScheme.surfaceContainer
    fun count(value: Long?): String = if (value == null || value < 0) "—" else value.toString()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Good morning, Alex", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = plum)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Your community at a glance", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewMetric("Users", count(stats?.totalUsers), Modifier.weight(1f), plum) { onNavigate(5) }
                    OverviewMetric("Online", count(stats?.activeNow), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewMetric("New today", count(stats?.newToday), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                    OverviewMetric("VIP members", count(stats?.vip), Modifier.weight(1f), plum)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = softRose)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Safety & review queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = plum)
                            Text("${pendingCount + reportCount + appealCount} items to review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onNavigate(0) }) { Text("Open queue", color = rose) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverviewQueueChip("Verify", pendingCount, Modifier.weight(1f)) { onNavigate(0) }
                        OverviewQueueChip("Reports", reportCount, Modifier.weight(1f)) { onNavigate(3) }
                        OverviewQueueChip("Appeals", appealCount, Modifier.weight(1f)) { onNavigate(4) }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Member activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("New sign-ups · last 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onNavigate(2) }) { Text("Insights") }
                    }
                    if (signups.isEmpty()) {
                        Text("Activity data will appear here when available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        val maxCount = signups.maxOf { it.count }.coerceAtLeast(1L)
                        Row(modifier = Modifier.fillMaxWidth().height(104.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            signups.forEach { day ->
                                Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight((day.count.toFloat() / maxCount).coerceIn(0.08f, 1f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(rose))
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(day.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Community health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OverviewHealthRow("Photo verified", stats?.photoVerified, stats?.totalUsers)
                    OverviewHealthRow("Reports received", stats?.reports, null)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent admin activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onNavigate(1) }) { Text("History") }
                    }
                    if (audit.isEmpty()) {
                        Text("No recent decisions yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        audit.take(2).forEach { entry ->
                            Column {
                                Text("${entry.decision.replace('_', ' ').replaceFirstChar { it.uppercase() }} · ${entry.userName.ifBlank { entry.userId.take(8) }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(relativeTime(entry.decidedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh overview") } }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    modifier: Modifier,
    accent: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null
) {
    Card(modifier = if (onClick == null) modifier else modifier.clickable { onClick() }, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun OverviewQueueChip(label: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun OverviewHealthRow(label: String, value: Long?, total: Long?) {
    val detail = when {
        value == null || value < 0 -> "—"
        total != null && total > 0 -> "${((value.toFloat() / total) * 100).toInt()}%"
        else -> value.toString()
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(detail, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RequestCard(req: VerificationRequest, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(req.name.ifBlank { stringResource(com.example.R.string.ui_unknown_user) }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(com.example.R.string.ui_submitted_time, relativeTime(req.createdAt).let { if (it.isBlank() || it == "now") stringResource(com.example.R.string.ui_just_now) else stringResource(com.example.R.string.ui_time_ago, it) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(com.example.R.string.admin_required_pose, req.pose), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoColumn(stringResource(com.example.R.string.ui_reference_photo), req.reference, Modifier.weight(1f))
                PhotoColumn(stringResource(com.example.R.string.ui_live_selfie), req.selfie, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.ui_reject)) }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_approve)) }
            }
        }
    }
}

@Composable
private fun PhotoColumn(label: String, image: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        if (image.isBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ui_no_photo), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            SnugImage(
                model = image,
                contentDescription = label,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun AuditRow(e: VerificationAuditEntry) {
    val approved = e.decision == "approved" || e.decision == "unsuspended" ||
        e.decision == "report_dismissed" || e.decision == "appeal_accepted" || e.decision == "verified"
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (e.decision) {
                        "approved" -> "Approved"
                        "suspended" -> "Suspended"
                        "unsuspended" -> "Reinstated"
                        "verified" -> "Badge given"
                        "unverified" -> "Badge removed"
                        "warned" -> "Warning sent"
                        "report_dismissed" -> "Reports closed"
                        "appeal_accepted" -> "Appeal accepted"
                        "appeal_denied" -> "Appeal denied"
                        else -> "Rejected"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(e.userName.ifBlank { e.userId.take(8) }, modifier = Modifier.weight(1f))
                Text(
                    relativeTime(e.decidedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "By ${e.adminEmail.ifBlank { e.adminId.take(8) }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (e.reason.isNotBlank()) {
                Text(stringResource(com.example.R.string.admin_reason, e.reason), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsTab(
    stats: AdminStats?,
    signups: List<SignupDay>,
    reported: List<ReportedUser>,
    onSuspend: (ReportedUser, Boolean) -> Unit,
    onToggleVip: (String, Boolean) -> Unit = { _, _ -> },
    onRefresh: () -> Unit
) {
    var confirm by remember { mutableStateOf<ReportedUser?>(null) }
    var userSearch by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") } // "All", "Active", "Suspended"

    val filteredReported = remember(reported, userSearch, statusFilter) {
        reported.filter { user ->
            val matchesSearch = userSearch.isBlank() ||
                user.name.contains(userSearch, ignoreCase = true) ||
                user.reasons.any { it.contains(userSearch, ignoreCase = true) }
            val matchesStatus = when (statusFilter) {
                "Active" -> !user.banned
                "Suspended" -> user.banned
                else -> true
            }
            matchesSearch && matchesStatus
        }
    }

    confirm?.let { user ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (user.banned) stringResource(com.example.R.string.ui_reinstate_user, user.name) else stringResource(com.example.R.string.ui_suspend_user, user.name)) },
            text = {
                Text(
                    if (user.banned) stringResource(com.example.R.string.ui_user_can_use_app_again)
                    else "They will see \"Account suspended\", disappear from Discover and Radar, and cannot like, match or message."
                )
            },
            confirmButton = {
                TextButton(onClick = { onSuspend(user, !user.banned); confirm = null }) {
                    Text(if (user.banned) stringResource(com.example.R.string.ui_reinstate) else stringResource(com.example.R.string.ui_suspend), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }
    if (stats == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    fun n(v: Long) = if (v < 0) "-" else v.toString()
    val cards = listOf(
        stringResource(com.example.R.string.ui_total_users) to n(stats.totalUsers),
        stringResource(com.example.R.string.ui_online_now) to n(stats.activeNow),
        stringResource(com.example.R.string.ui_active_24_hours) to n(stats.active24h),
        stringResource(com.example.R.string.ui_active_7_days) to n(stats.active7d),
        stringResource(com.example.R.string.ui_new_today) to n(stats.newToday),
        stringResource(com.example.R.string.ui_new_this_week) to n(stats.new7d),
        stringResource(com.example.R.string.ui_photo_verified) to n(stats.photoVerified),
        stringResource(com.example.R.string.ui_vip_members) to n(stats.vip),
        stringResource(com.example.R.string.ui_men) to n(stats.men),
        stringResource(com.example.R.string.ui_women) to n(stats.women),
        stringResource(com.example.R.string.ui_paused_snooze) to n(stats.paused),
        stringResource(com.example.R.string.ui_suspended) to n(stats.banned),
        stringResource(com.example.R.string.ui_reports_received) to n(stats.reports),
        stringResource(com.example.R.string.ui_reviews_waiting) to n(stats.pendingReviews)
    )
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cards.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (signups.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.ui_new_users_last_7_days), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        val maxCount = (signups.maxOf { it.count }).coerceAtLeast(1L)
                        Row(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            signups.forEach { day ->
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(day.count.toString(), style = MaterialTheme.typography.labelSmall)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(((day.count.toFloat() / maxCount) * 0.7f).coerceAtLeast(0.03f))
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            signups.forEach {
                                Text(
                                    it.label,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Column {
                Text(stringResource(R.string.ui_user_management_reports), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = userSearch,
                    onValueChange = { userSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ui_search_users_by_name_or_report_reason)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (userSearch.isNotEmpty()) {
                            IconButton(onClick = { userSearch = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(stringResource(com.example.R.string.ui_all), stringResource(com.example.R.string.ui_active), stringResource(com.example.R.string.ui_suspended)).forEach { filter ->
                        FilterChip(
                            selected = statusFilter == filter,
                            onClick = { statusFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
            }
        }
        if (filteredReported.isEmpty()) {
            item {
                Text(
                    if (userSearch.isNotEmpty() || statusFilter != "All") stringResource(com.example.R.string.ui_no_users_match_filter) else stringResource(com.example.R.string.ui_no_reports_yet),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredReported, key = { it.userId }) { user ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SnugImage(
                                model = user.photo,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${user.name.ifBlank { "Unknown" }} \u2022 ${user.reports} report${if (user.reports == 1) "" else "s"}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    user.reasons.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onToggleVip(user.userId, true) }) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.ui_grant_vip))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { confirm = user }) {
                                Text(
                                    if (user.banned) stringResource(com.example.R.string.ui_reinstate) else stringResource(com.example.R.string.ui_suspend),
                                    color = if (user.banned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_refresh)) }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(com.example.R.string.ui_online_stats_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
