package com.example.ui.screens

import androidx.compose.foundation.background
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

/** Admin-only: compare each live selfie with the user's reference photo, then approve or reject. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(viewModel: SnugViewModel, navController: NavController) {
    val isAdmin by viewModel.isAdmin.collectAsState()
    val pending by viewModel.pendingVerifications.collectAsState()
    val audit by remember { viewModel.observeVerificationAudit() }.collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(0) }
    var rejecting by remember { mutableStateOf<VerificationRequest?>(null) }
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var statsRefresh by remember { mutableStateOf(0) }
    var signups by remember { mutableStateOf<List<SignupDay>>(emptyList()) }
    var reported by remember { mutableStateOf<List<ReportedUser>>(emptyList()) }
    LaunchedEffect(tab, statsRefresh, isAdmin) {
        if (tab == 2 && isAdmin) {
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
            title = { Text("Reject ${req.name.ifBlank { "this selfie" }}?") },
            text = {
                Column {
                    Text("Pick a reason. The user sees it and can try again.", style = MaterialTheme.typography.bodySmall)
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
                ) { Text("Reject", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verification review") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!isAdmin) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("You do not have access to this page.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pending (${pending.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Stats") })
            }
            if (tab == 2) {
                StatsTab(
                    stats = stats,
                    signups = signups,
                    reported = reported,
                    onSuspend = { user, banned -> viewModel.setSuspended(user, banned) { statsRefresh++ } },
                    onRefresh = { statsRefresh++ }
                )
            } else if (tab == 0) {
                if (pending.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No selfies waiting. All caught up!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pending, key = { it.uid }) { req ->
                            RequestCard(
                                req = req,
                                onApprove = { viewModel.decideVerification(req, approve = true) },
                                onReject = { rejecting = req }
                            )
                        }
                    }
                }
            } else {
                if (audit.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No decisions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun RequestCard(req: VerificationRequest, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(req.name.ifBlank { "Unknown user" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Submitted " + relativeTime(req.createdAt).let { if (it.isBlank() || it == "now") "just now" else "$it ago" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Required pose: ${req.pose}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoColumn("Reference photo", req.reference, Modifier.weight(1f))
                PhotoColumn("Live selfie", req.selfie, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reject") }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Approve") }
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
                    Text("No photo", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
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
    val approved = e.decision == "approved" || e.decision == "unsuspended"
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (e.decision) {
                        "approved" -> "Approved"
                        "suspended" -> "Suspended"
                        "unsuspended" -> "Reinstated"
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
                Text("Reason: ${e.reason}", style = MaterialTheme.typography.bodySmall)
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
    onRefresh: () -> Unit
) {
    var confirm by remember { mutableStateOf<ReportedUser?>(null) }
    confirm?.let { user ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (user.banned) "Reinstate ${user.name}?" else "Suspend ${user.name}?") },
            text = {
                Text(
                    if (user.banned) "They will be able to use the app again."
                    else "They will see \"Account suspended\", disappear from Discover and Radar, and cannot like, match or message."
                )
            },
            confirmButton = {
                TextButton(onClick = { onSuspend(user, !user.banned); confirm = null }) {
                    Text(if (user.banned) "Reinstate" else "Suspend", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
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
        "Total users" to n(stats.totalUsers),
        "Online now" to n(stats.activeNow),
        "Active 24 hours" to n(stats.active24h),
        "Active 7 days" to n(stats.active7d),
        "New today" to n(stats.newToday),
        "New this week" to n(stats.new7d),
        "Photo verified" to n(stats.photoVerified),
        "Phone verified" to n(stats.phoneVerified),
        "VIP members" to n(stats.vip),
        "Men" to n(stats.men),
        "Women" to n(stats.women),
        "Paused (snooze)" to n(stats.paused),
        "Suspended" to n(stats.banned),
        "Reports received" to n(stats.reports),
        "Reviews waiting" to n(stats.pendingReviews)
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
                        Text("New users, last 7 days", fontWeight = FontWeight.Bold)
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
        item { Text("Most reported users", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        if (reported.isEmpty()) {
            item { Text("No reports yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(reported, key = { it.userId }) { user ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        TextButton(onClick = { confirm = user }) {
                            Text(
                                if (user.banned) "Reinstate" else "Suspend",
                                color = if (user.banned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Online now = active in the last 5 minutes. A dash means the number could not be loaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
