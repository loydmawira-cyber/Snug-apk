package com.example.ui.screens

import com.example.R
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.PhotoVerificationPoses
import com.example.data.model.UserProfile
import com.example.data.security.EmailVerifier
import com.example.ui.viewmodel.SnugViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

/** Photo (selfie) verification, plus email verification. Selfies are reviewed by an admin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoVerificationSheet(
    profile: UserProfile?,
    viewModel: SnugViewModel,
    onEmailVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    val status by viewModel.photoVerificationStatus.collectAsState()
    val pose = remember { PhotoVerificationPoses.all.random() }
    var selfie by remember { mutableStateOf<Bitmap?>(null) }
    var emailVerified by remember { mutableStateOf(EmailVerifier.isVerified()) }
    var emailBusy by remember { mutableStateOf(false) }
    var emailNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val rejectionReason by viewModel.photoRejectionReason.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    if (showCamera) {
        SelfieCaptureDialog(
            pose = pose,
            onCaptured = { selfie = it; showCamera = false },
            onDismiss = { showCamera = false }
        )
    }
    val hasPhoto = !profile?.avatarOrNull().isNullOrBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.ui_verify_it_s_really_you), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.ui_verified_profile_safety_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---------- Photo verification ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        profile?.photoVerified == true -> {
                            Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.ui_photo_verified), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.ui_selfie_match_confirmation),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        status == "pending" -> {
                            Icon(Icons.Default.HourglassTop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.ui_selfie_under_review), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.ui_badge_approval_wait),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            Text(stringResource(R.string.ui_photo_verification), fontWeight = FontWeight.Bold)
                            if (status == "rejected") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.ui_selfie_rejected_guidance, rejectionReason),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    stringResource(R.string.ui_your_pose, pose),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            val shot = selfie
                            if (shot != null) {
                                Image(
                                    bitmap = shot.asImageBitmap(),
                                    contentDescription = stringResource(R.string.ui_your_selfie),
                                    modifier = Modifier.size(160.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (!hasPhoto) {
                                Text(
                                    stringResource(R.string.ui_add_profile_picture_for_verification),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedButton(onClick = { showCamera = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(if (shot == null) R.string.ui_take_selfie else R.string.ui_retake_selfie))
                            }
                            if (shot != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.submitSelfie(pose, shot)
                                        selfie = null
                                    },
                                    enabled = hasPhoto,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.ui_submit_for_review)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.ui_selfie_privacy_notice),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---------- Email verification ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (emailVerified) Icons.Default.CheckCircle else Icons.Default.Email,
                            null,
                            tint = if (emailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.ui_email), fontWeight = FontWeight.Bold)
                            Text(
                                if (emailVerified) stringResource(R.string.ui_email_is_verified, EmailVerifier.email())
                                else stringResource(R.string.ui_confirm_email, EmailVerifier.email().ifBlank { stringResource(R.string.ui_your_email) }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!emailVerified) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !emailBusy,
                                onClick = {
                                    emailBusy = true
                                    scope.launch {
                                        val problem = EmailVerifier.send()
                                        emailNote = problem ?: context.getString(R.string.ui_verification_email_sent)
                                        emailBusy = false
                                    }
                                }
                            ) { Text(stringResource(R.string.ui_send_email)) }
                            Button(
                                enabled = !emailBusy,
                                onClick = {
                                    emailBusy = true
                                    scope.launch {
                                        emailVerified = EmailVerifier.refresh()
                                        if (emailVerified) onEmailVerified()
                                        emailNote = context.getString(if (emailVerified) R.string.ui_email_verified else R.string.ui_not_verified_yet)
                                        emailBusy = false
                                    }
                                }
                            ) { Text(stringResource(R.string.ui_i_verified)) }
                        }
                    }
                    emailNote?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun UserProfile.avatarOrNull(): String? =
    profilePhoto.takeIf { it.isNotBlank() } ?: coverPhoto.takeIf { it.isNotBlank() } ?: photos.firstOrNull()?.url

/** Plain-language safety guide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCenterSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.ui_safety_center), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            SafetyBlock(
                stringResource(R.string.ui_safety_section_1),
                listOf(stringResource(R.string.ui_safety_1_1), stringResource(R.string.ui_safety_1_2), stringResource(R.string.ui_safety_1_3))
            )
            SafetyBlock(
                stringResource(R.string.ui_safety_section_2),
                listOf(stringResource(R.string.ui_safety_2_1), stringResource(R.string.ui_safety_2_2), stringResource(R.string.ui_safety_2_3))
            )
            SafetyBlock(
                stringResource(R.string.ui_safety_section_3),
                listOf(stringResource(R.string.ui_safety_3_1), stringResource(R.string.ui_safety_3_2), stringResource(R.string.ui_safety_3_3))
            )
            SafetyBlock(
                stringResource(R.string.ui_safety_section_4),
                listOf(stringResource(R.string.ui_safety_4_1), stringResource(R.string.ui_safety_4_2), stringResource(R.string.ui_safety_4_3))
            )
            SafetyBlock(
                stringResource(R.string.ui_safety_section_5),
                listOf(stringResource(R.string.ui_safety_5_1), stringResource(R.string.ui_safety_5_2), stringResource(R.string.ui_safety_5_3))
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SafetyBlock(title: String, points: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            points.forEach {
                Text(
                    "\u2022  $it",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

/** Shown instead of the app when an admin has suspended the account. */
@Composable
fun SuspendedScreen(onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.ui_account_suspended), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.ui_suspended_account_notice),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLogout) { Text(stringResource(R.string.ui_log_out)) }
    }
}

/** Shown when someone tries to send a message without a verified email. */
@Composable
fun EmailGateDialog(onDismiss: () -> Unit, onVerified: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_verify_your_email_to_chat)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.ui_email_gate_instructions, EmailVerifier.email().ifBlank { stringResource(R.string.ui_your_email) })
                )
                note?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            note = EmailVerifier.send() ?: context.getString(R.string.ui_check_email_inbox)
                            busy = false
                        }
                    }
                ) { Text(stringResource(R.string.ui_send_email)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        if (EmailVerifier.refresh()) onVerified() else note = context.getString(R.string.ui_not_verified_open_email_link)
                        busy = false
                    }
                }
            ) { Text(stringResource(R.string.ui_i_verified)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_not_now)) } }
    )
}
