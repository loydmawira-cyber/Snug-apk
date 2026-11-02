package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminUserRow
import com.example.data.model.SuspensionOptions
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRequest
import com.example.data.model.completeness
import com.example.data.model.lastSeenText
import com.example.data.util.calculateAge
import com.example.ui.components.SnugImage
import com.example.ui.viewmodel.ModerationViewModel
import kotlinx.coroutines.delay

/** Profile completion in percent, the same rule people see in their own profile (cover photo counts as a photo). */
private fun pctOf(p: UserProfile): Int =
    p.copy(profilePhoto = p.profilePhoto.ifBlank { p.coverPhoto }).completeness().first

private fun locationOf(p: UserProfile): String {
    val parts = listOf(p.city, p.country).filter { it.isNotBlank() }
    return if (parts.isEmpty()) "Location unknown" else parts.joinToString(", ")
}

private fun photoOf(p: UserProfile): String =
    p.profilePhoto.ifBlank { p.coverPhoto }

private fun dateOf(ts: com.google.firebase.Timestamp): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(ts.toDate())

private val USER_FILTERS = listOf("All", "Verified", "Not verified", "Selfie waiting", "Suspended", "Under 60%")

/**
 * Admin: every registered person with profile completion, verified status and location.
 * Tap a person to approve their selfie, give or remove the blue check, or suspend / reinstate them.
 */
@Composable
fun UsersTab(
    vm: ModerationViewModel,
    pending: List<VerificationRequest>,
    onApprove: (VerificationRequest) -> Unit,
    onReject: (VerificationRequest) -> Unit
) {
    val users by vm.users.collectAsState()
    val results by vm.searchResults.collectAsState()
    val loading by vm.usersLoading.collectAsState()
    val exhausted by vm.usersExhausted.collectAsState()
    val totals by vm.userTotals.collectAsState()
    val fetched by vm.usersFetched.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current

    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var selectedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (users.isEmpty()) vm.loadUsers(reset = true)
        vm.loadUserTotals()
    }
    LaunchedEffect(search) {
        val q = search.trim()
        if (q.length >= 2) {
            delay(400)
            vm.searchUsers(q)
        } else {
            vm.clearSearch()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    // When a selfie request is decided it leaves the pending list: reload that person so the badge is up to date
    val pendingIds = remember(pending) { pending.map { it.uid }.toSet() }
    var lastPendingIds by remember { mutableStateOf(pendingIds) }
    LaunchedEffect(pendingIds) {
        (lastPendingIds - pendingIds).forEach { vm.refreshPerson(it) }
        lastPendingIds = pendingIds
    }

    val searching = search.trim().length >= 2
    val base = if (searching) results else users
    val shown = base.filter { row ->
        val p = row.profile
        when (filter) {
            "Verified" -> p.photoVerified
            "Not verified" -> !p.photoVerified
            "Selfie waiting" -> p.id in pendingIds
            "Suspended" -> p.banned
            "Under 60%" -> pctOf(p) < 60
            else -> true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search by name") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            USER_FILTERS.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        val t = totals
        Text(
            buildString {
                if (t != null && t.first >= 0) {
                    append("${t.first} registered")
                    if (t.second >= 0) append(", ${t.second} verified (${if (t.first > 0) t.second * 100 / t.first else 0}%)")
                    append(". ")
                }
                append("Showing ${shown.size}")
                if (!searching && !exhausted) append(" of ${users.size} loaded")
                append(". Firestore returned $fetched document" + (if (fetched == 1) "" else "s") + if (exhausted) " (all)." else ".")
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (shown.isEmpty() && loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (searching) "Nobody found. Search matches the start of the name." else "Nobody in the loaded people matches. Load more people or try another filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!searching && !exhausted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { vm.loadUsers() }) { Text("Load more people") }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.profile.id }) { row ->
                    UserRowCard(row, row.profile.id in pendingIds) { selectedId = row.profile.id }
                }
                if (!searching && !exhausted) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            else OutlinedButton(onClick = { vm.loadUsers() }) { Text("Load more people") }
                        }
                    }
                }
            }
        }
    }

    // Always show the latest copy of the person (it changes after an action)
    val current = selectedId?.let { id -> (users + results).firstOrNull { it.profile.id == id } }
    if (current != null) {
        UserSheet(
            vm = vm,
            row = current,
            request = pending.firstOrNull { it.uid == current.profile.id },
            onApprove = onApprove,
            onReject = onReject,
            onDismiss = { selectedId = null }
        )
    }
}

@Composable
private fun UserAvatar(photo: String, size: Int = 52) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (photo.isNotBlank()) {
            SnugImage(photo, null, Modifier.fillMaxSize(), ContentScale.Crop)
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UserRowCard(row: AdminUserRow, selfiePending: Boolean, onClick: () -> Unit) {
    val p = row.profile
    val pct = pctOf(p)
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoOf(p))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.displayName.ifBlank { "No name" } + (if (p.birthDate != null) ", ${calculateAge(p.birthDate)}" else ""),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (p.photoVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Text(locationOf(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.width(90.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$pct% complete", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    p.createdAt?.let { "Joined " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(it.toDate()) } ?: "Joined: unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (p.banned) Text("Suspended", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (selfiePending) Text("Selfie waiting", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (p.isPaused) Text("Paused", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!p.photoVerified && !selfiePending && !p.banned) Text("Not verified", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "-" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UserSheet(
    vm: ModerationViewModel,
    row: AdminUserRow,
    request: VerificationRequest?,
    onApprove: (VerificationRequest) -> Unit,
    onReject: (VerificationRequest) -> Unit,
    onDismiss: () -> Unit
) {
    val p = row.profile
    val (pct, hint) = p.copy(profilePhoto = p.profilePhoto.ifBlank { p.coverPhoto }).completeness()
    var days by remember { mutableStateOf(7) }
    var reason by remember { mutableStateOf("") }
    var confirmSuspend by remember { mutableStateOf(false) }
    val lengthText = if (days == 0) "permanently" else "for $days day" + (if (days == 1) "" else "s")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(photoOf(p), 64)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.displayName.ifBlank { "No name" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (p.photoVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        if (p.photoVerified) "Verified" else if (request != null) "Selfie waiting for review" else "Not verified",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Profile $pct% complete", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
            if (hint != null) {
                Text("Next: $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(12.dp))
            DetailLine("Location", locationOf(p))
            DetailLine("Age", if (p.birthDate != null) calculateAge(p.birthDate).toString() else "")
            DetailLine("Gender", p.gender)
            DetailLine("Interested in", p.interestedIn.joinToString(", "))
            DetailLine("Looking for", p.lookingFor)
            DetailLine("Joined", p.createdAt?.let { dateOf(it) } ?: "")
            DetailLine("Last active", p.lastSeenText())
            DetailLine("VIP", if (p.isVip) "Yes" else "No")
            DetailLine("Paused", if (p.isPaused) "Yes (hidden from Discover)" else "No")
            if (p.banned) {
                DetailLine("Suspended", row.suspendedUntil?.let { "until ${dateOf(it)}" } ?: "no end date")
                DetailLine("Reason", row.suspensionReason)
            }
            if (p.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(p.bio, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Selfie approval (the same review that used to be the only admin screen)
            if (request != null) {
                Text("Selfie approval", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onApprove(request) }, modifier = Modifier.weight(1f)) { Text("Approve") }
                    OutlinedButton(onClick = { onReject(request); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Reject...") }
                }
                Text(
                    "To compare the selfie with the profile photo, open the Selfies tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("Blue check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            if (p.photoVerified) {
                OutlinedButton(onClick = { vm.setVerified(row, false) }, modifier = Modifier.fillMaxWidth()) { Text("Remove blue check") }
            } else {
                OutlinedButton(onClick = { vm.setVerified(row, true) }, modifier = Modifier.fillMaxWidth()) { Text("Give blue check without a selfie") }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            if (p.banned) {
                Button(onClick = { vm.reinstatePerson(row) }, modifier = Modifier.fillMaxWidth()) { Text("Reinstate account") }
            } else {
                Text("Suspend for", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SuspensionOptions.all.forEach { (label, d) ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(label) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 300) reason = it },
                    label = { Text("Reason (the person sees this)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    enabled = reason.isNotBlank(),
                    onClick = { confirmSuspend = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Suspend $lengthText") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (confirmSuspend) {
        AlertDialog(
            onDismissRequest = { confirmSuspend = false },
            title = { Text("Suspend ${p.displayName.ifBlank { "this person" }} $lengthText?") },
            text = { Text("They cannot use the app, disappear from Discover and Radar, and see your reason. They can send an appeal.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.suspendPerson(row, days, reason.trim())
                    confirmSuspend = false
                    reason = ""
                }) { Text("Suspend", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSuspend = false }) { Text("Cancel") } }
        )
    }
}
