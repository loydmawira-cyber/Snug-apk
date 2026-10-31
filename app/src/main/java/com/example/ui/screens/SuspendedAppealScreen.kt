package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.ModerationViewModel
import kotlinx.coroutines.delay

/**
 * Shown instead of the app while an account is suspended.
 * Shows until when and why, lets the person appeal, and switches the app back on when a timed suspension ends.
 */
@Composable
fun SuspendedAppealScreen(vm: ModerationViewModel, name: String, onLogout: () -> Unit) {
    val info by vm.mySuspension.collectAsState()
    val appeal by vm.myAppeal.collectAsState()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // When a timed suspension ends, the account is switched back on automatically
    LaunchedEffect(info?.until) {
        val until = info?.until?.toDate()?.time ?: return@LaunchedEffect
        while (true) {
            val left = until - System.currentTimeMillis()
            if (left <= 0) {
                vm.liftIfExpired()
                delay(30_000L)
            } else {
                delay(minOf(left, 60_000L) + 500L)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.ui_account_suspended), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        val until = info?.until
        Text(
            if (until != null) {
                stringResource(
                    R.string.suspension_until,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(until.toDate())
                )
            } else {
                stringResource(R.string.suspension_indefinite)
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val reason = info?.reason.orEmpty()
        if (reason.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.suspension_reason, reason), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(20.dp))

        val status = appeal?.status
        if (status == "open") {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.suspension_appeal_sent),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            if (status == "denied") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.suspension_appeal_denied), style = MaterialTheme.typography.bodyMedium)
                        val note = appeal?.adminResponse.orEmpty()
                        if (note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.suspension_appeal_admin_note, note), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(stringResource(R.string.suspension_appeal_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.suspension_appeal_hint),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 1000) text = it },
                label = { Text(stringResource(R.string.suspension_appeal_field)) },
                supportingText = {
                    Text(if (failed) stringResource(R.string.suspension_appeal_failed) else stringResource(R.string.suspension_appeal_min))
                },
                isError = failed,
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                enabled = !busy && text.trim().length >= 10,
                onClick = {
                    busy = true
                    failed = false
                    vm.submitAppeal(name, text) { problem ->
                        busy = false
                        if (problem == null) text = "" else failed = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.suspension_appeal_send)) }
        }

        Spacer(modifier = Modifier.height(20.dp))
        TextButton(onClick = onLogout) { Text(stringResource(R.string.ui_log_out)) }
    }
}
