package com.example.ui.screens

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

/** Photo (selfie) verification, plus email verification. Selfies are reviewed by an admin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoVerificationSheet(
    profile: UserProfile?,
    viewModel: SnugViewModel,
    onDismiss: () -> Unit
) {
    val status by viewModel.photoVerificationStatus.collectAsState()
    val pose = remember { PhotoVerificationPoses.all.random() }
    var selfie by remember { mutableStateOf<Bitmap?>(null) }
    var emailVerified by remember { mutableStateOf(EmailVerifier.isVerified()) }
    var emailBusy by remember { mutableStateOf(false) }
    var emailNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) selfie = bmp
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
            Text("Verify it's really you", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Verified profiles get a blue check and are trusted more. Fake accounts are the biggest risk on dating apps, so we check selfies against profile photos.",
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
                            Text("Photo verified", fontWeight = FontWeight.Bold)
                            Text(
                                "Your selfie matched your photos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        status == "pending" -> {
                            Icon(Icons.Default.HourglassTop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Selfie under review", fontWeight = FontWeight.Bold)
                            Text(
                                "We will add your badge once it is approved. This can take a day or two.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            Text("Photo verification", fontWeight = FontWeight.Bold)
                            if (status == "rejected") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Your last selfie could not be matched. Make sure your face is clear and in good light, then try again.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    "Your pose: $pose",
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
                                    contentDescription = "Your selfie",
                                    modifier = Modifier.size(160.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (!hasPhoto) {
                                Text(
                                    "Add a profile picture first so we have something to compare with.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedButton(onClick = { camera.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (shot == null) "Take selfie" else "Retake selfie")
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
                                ) { Text("Submit for review") }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Only you and our review team can see your selfie. It is never shown on your profile.",
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
                            Text("Email", fontWeight = FontWeight.Bold)
                            Text(
                                if (emailVerified) "${EmailVerifier.email()} is verified"
                                else "Confirm ${EmailVerifier.email().ifBlank { "your email" }}",
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
                                        emailNote = problem ?: "Email sent. Open the link, then tap \"I verified\"."
                                        emailBusy = false
                                    }
                                }
                            ) { Text("Send email") }
                            Button(
                                enabled = !emailBusy,
                                onClick = {
                                    emailBusy = true
                                    scope.launch {
                                        emailVerified = EmailVerifier.refresh()
                                        emailNote = if (emailVerified) "Email verified" else "Not verified yet"
                                        emailBusy = false
                                    }
                                }
                            ) { Text("I verified") }
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
    profilePhoto.takeIf { it.isNotBlank() } ?: photos.firstOrNull()?.url

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
            Text("Safety Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            SafetyBlock(
                "Before you meet",
                listOf(
                    "Chat and video call first. Be careful if they always avoid a video call.",
                    "Look for the blue check. It means a phone or selfie was verified.",
                    "Tell a friend who you are meeting, where, and when."
                )
            )
            SafetyBlock(
                "On the first date",
                listOf(
                    "Meet in a public place and arrange your own transport.",
                    "Keep your drink with you and stay sober enough to decide for yourself.",
                    "Leave if anything feels wrong. You do not owe anyone an explanation."
                )
            )
            SafetyBlock(
                "Protect your money and information",
                listOf(
                    "Never send money, gift cards, crypto or airtime to someone you have not met, whatever the reason.",
                    "Do not share your address, workplace, passwords, bank or ID details.",
                    "Be careful with links and with anyone who quickly asks you to move to another app."
                )
            )
            SafetyBlock(
                "Red flags",
                listOf(
                    "They declare love very fast or have an emergency that needs money.",
                    "Their photos look too perfect, or their story keeps changing.",
                    "They pressure you, get angry when you say no, or threaten to share your photos."
                )
            )
            SafetyBlock(
                "If something goes wrong",
                listOf(
                    "Block and report them from their profile or the chat menu (the three dots).",
                    "If you are in danger, call your local emergency number right away.",
                    "If you sent money, contact your bank or mobile money provider straight away and report it to the police."
                )
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
        Text("Account suspended", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "This account was suspended because it broke our community guidelines. If you think this is a mistake, please contact support.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLogout) { Text("Log out") }
    }
}

/** Shown when someone tries to send a message without a verified email. */
@Composable
fun EmailGateDialog(onDismiss: () -> Unit, onVerified: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify your email to chat") },
        text = {
            Column {
                Text(
                    "To keep SNUG safe from fake accounts, you need a verified email before you can send messages. " +
                        "Tap \"Send email\", open the link we send to ${EmailVerifier.email().ifBlank { "your email" }}, " +
                        "then come back and tap \"I verified\"."
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
                            note = EmailVerifier.send() ?: "Email sent. Check your inbox (and spam)."
                            busy = false
                        }
                    }
                ) { Text("Send email") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        if (EmailVerifier.refresh()) onVerified() else note = "Not verified yet. Open the link in the email first."
                        busy = false
                    }
                }
            ) { Text("I verified") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
