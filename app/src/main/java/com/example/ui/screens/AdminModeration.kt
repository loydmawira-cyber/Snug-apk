package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Appeal
import com.example.data.model.MessageReportEntry
import com.example.data.model.ReportCase
import com.example.data.model.SuspensionOptions
import com.example.ui.components.SnugImage
import com.example.ui.viewmodel.ModerationViewModel

private fun dateText(ts: com.google.firebase.Timestamp): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(ts.toDate())

/** Shows the result of an admin action (suspended, dismissed, ...) as a short message. */
@Composable
private fun ModerationToasts(vm: ModerationViewModel) {
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }
}

@Composable
private fun Avatar(photo: String) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (photo.isNotBlank()) {
            SnugImage(photo, null, Modifier.fillMaxSize(), ContentScale.Crop)
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Reports tab
// ---------------------------------------------------------------------------

@Composable
fun ReportsTab(vm: ModerationViewModel) {
    val cases by vm.cases.collectAsState()
    val loading by vm.loadingCases.collectAsState()
    var selected by remember { mutableStateOf<ReportCase?>(null) }
    ModerationToasts(vm)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${cases.size} accounts with open reports",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { vm.refreshCases() }) { Text("Refresh") }
        }
        if (loading && cases.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (cases.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open reports. All caught up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cases, key = { it.userId }) { c -> CaseCard(c) { selected = c } }
            }
        }
    }

    selected?.let { c -> CaseSheet(vm, c) { selected = null } }
}

@Composable
private fun CaseCard(case: ReportCase, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(case.photo)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(case.name, fontWeight = FontWeight.Bold)
                Text(
                    "${case.reports.size} open report" + (if (case.reports.size == 1) "" else "s"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                val top = case.reports.groupingBy { it.reason }.eachCount()
                    .entries.sortedByDescending { it.value }.take(2).joinToString(", ") { it.key }
                if (top.isNotBlank()) {
                    Text(top, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (case.banned) {
                Text("Suspended", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CaseSheet(vm: ModerationViewModel, case: ReportCase, onDismiss: () -> Unit) {
    var messageReports by remember { mutableStateOf<List<MessageReportEntry>>(emptyList()) }
    LaunchedEffect(case.userId) { messageReports = vm.loadMessageReports(case.userId) }

    val topReason = remember(case) {
        (case.reports.groupingBy { it.reason }.eachCount().maxByOrNull { it.value }?.key ?: "").removePrefix("Message: ")
    }
    var days by remember { mutableStateOf(3) }
    var note by remember { mutableStateOf(topReason) }
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
                Avatar(case.photo)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(case.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val until = case.suspendedUntil
                    Text(
                        when {
                            case.banned && until != null -> "Suspended until ${dateText(until)}"
                            case.banned -> "Suspended"
                            else -> "Active account, ${case.totalReports} reports in total"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (case.banned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Open reports (${case.reports.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            case.reports.sortedByDescending { it.createdAt?.seconds ?: 0L }.forEach { r ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(r.reason.ifBlank { "No reason" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(relativeTime(r.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (messageReports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Reported messages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                messageReports.forEach { m ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(m.reason, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            if (m.text.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(m.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (m.imageUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                SnugImage(
                                    m.imageUrl, null,
                                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                                    ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text("Your decision", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
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
                value = note,
                onValueChange = { if (it.length <= 300) note = it },
                label = { Text("Reason (the person sees this)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                enabled = note.isNotBlank(),
                onClick = { confirmSuspend = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Suspend $lengthText") }
            OutlinedButton(
                enabled = note.isNotBlank(),
                onClick = { vm.warnUser(case, note.trim()); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send a warning only") }
            TextButton(
                onClick = { vm.dismissReports(case); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("No violation: close the reports") }
            if (case.banned) {
                TextButton(
                    onClick = { vm.reinstate(case); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reinstate this account now") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (confirmSuspend) {
        AlertDialog(
            onDismissRequest = { confirmSuspend = false },
            title = { Text("Suspend ${case.name} $lengthText?") },
            text = { Text("They cannot use the app, disappear from Discover and Radar, and see your reason. They can send an appeal. The reports are closed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.suspendUser(case, days, note.trim())
                    confirmSuspend = false
                    onDismiss()
                }) { Text("Suspend", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSuspend = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------
// Appeals tab (disputes)
// ---------------------------------------------------------------------------

@Composable
fun AppealsTab(vm: ModerationViewModel) {
    val appeals by vm.openAppeals.collectAsState()
    var denying by remember { mutableStateOf<Appeal?>(null) }
    ModerationToasts(vm)

    if (appeals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No appeals waiting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(appeals, key = { it.uid }) { a ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name.ifBlank { a.uid.take(8) }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(relativeTime(a.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val until = a.suspendedUntil
                        Text(
                            "Suspended" + (if (until != null) " until ${dateText(until)}" else " (no end date)") +
                                (if (a.suspensionReason.isNotBlank()) ". Reason: ${a.suspensionReason}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(a.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.decideAppeal(a, true, "") }, modifier = Modifier.weight(1f)) { Text("Accept and reinstate") }
                            OutlinedButton(onClick = { denying = a }, modifier = Modifier.weight(1f)) { Text("Deny") }
                        }
                    }
                }
            }
        }
    }

    denying?.let { a ->
        var response by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { denying = null },
            title = { Text("Deny ${a.name.ifBlank { "this" }} appeal?") },
            text = {
                OutlinedTextField(
                    value = response,
                    onValueChange = { if (it.length <= 300) response = it },
                    label = { Text("Note to the person (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.decideAppeal(a, false, response.trim())
                    denying = null
                }) { Text("Deny", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { denying = null }) { Text("Cancel") } }
        )
    }
}
