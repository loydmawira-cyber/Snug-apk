package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.ui.unit.sp
import com.example.ui.components.SnugImage
import com.example.data.model.UserPhoto
import com.example.data.model.hasVerifiedBadge
import com.example.data.model.completeness
import com.example.data.model.ProfilePrompt
import com.example.data.model.ProfileOptions
import com.example.data.model.UserProfile
import com.example.data.model.missingRequiredFields
import com.example.data.security.PinManager
import com.example.data.security.EmailVerifier
import kotlinx.coroutines.launch
import com.example.data.util.calculateAge
import com.example.ui.components.PinSetupDialog
import com.example.ui.viewmodel.SnugViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.math.roundToInt

enum class ProfileSubScreen {
    NONE,
    PHOTO_VERIFICATION,
    SAFETY_CENTER,
    PHOTOS_PRIVACY,
    PROFILE_VISIBILITY,
    DISCOVERY_PREFERENCES,
    SECURITY_PIN_LOCK,
    VIP_MEMBERSHIP,
    HELP_SUPPORT,
    EDIT_PROFILE,
    ACCOUNT_SECURITY,
    DEVICE_SESSIONS,
    DATA_EXPORT,
    NOTIFICATION_SETTINGS,
    APP_LANGUAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SnugViewModel,
    appLanguage: String = "en",
    onLanguageChanged: (String) -> Unit = {},
    onOpenAdmin: () -> Unit = {}
) {
    val profile by viewModel.currentUserProfile.collectAsState()
    val verifyStatus by viewModel.photoVerificationStatus.collectAsState()
    var emailVerified by remember { mutableStateOf(EmailVerifier.isVerified()) }
    val isAdmin by viewModel.isAdmin.collectAsState()
    val pendingCount by viewModel.pendingVerifications.collectAsState()
    var activeSubScreen by remember { mutableStateOf(ProfileSubScreen.NONE) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val pinManager = remember { PinManager(context) }
    var isPinEnabled by remember { mutableStateOf(pinManager.isPinEnabled()) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.setProfilePicture(it) } }
    )
    val avatarUrl = profile?.profilePhoto?.takeIf { it.isNotBlank() }
        ?: profile?.photos?.firstOrNull()?.url

    // Sub-screens / Bottom Sheets
    when (activeSubScreen) {
        ProfileSubScreen.PHOTO_VERIFICATION -> {
            PhotoVerificationSheet(
                profile = profile,
                viewModel = viewModel,
                onEmailVerified = { emailVerified = true },
                onDismiss = { activeSubScreen = ProfileSubScreen.NONE }
            )
        }
        ProfileSubScreen.SAFETY_CENTER -> {
            SafetyCenterSheet(onDismiss = { activeSubScreen = ProfileSubScreen.NONE })
        }
        ProfileSubScreen.PHOTOS_PRIVACY -> {
            PhotosPrivacySheet(
                profile = profile,
                viewModel = viewModel,
                onDismiss = { activeSubScreen = ProfileSubScreen.NONE }
            )
        }
        ProfileSubScreen.PROFILE_VISIBILITY -> {
            ProfileVisibilitySheet(
                profile = profile,
                viewModel = viewModel,
                onDismiss = { activeSubScreen = ProfileSubScreen.NONE }
            )
        }
        ProfileSubScreen.DISCOVERY_PREFERENCES -> {
            DiscoveryPreferencesSheet(
                profile = profile,
                viewModel = viewModel,
                onDismiss = { activeSubScreen = ProfileSubScreen.NONE }
            )
        }
        ProfileSubScreen.SECURITY_PIN_LOCK -> {
            SecurityPinSheet(
                pinManager = pinManager,
                isPinEnabled = isPinEnabled,
                onPinStateChanged = { isPinEnabled = it },
                onDismiss = { activeSubScreen = ProfileSubScreen.NONE }
            )
        }
        ProfileSubScreen.VIP_MEMBERSHIP -> {
            VipUpgradeSheet(viewModel = viewModel, onDismiss = { activeSubScreen = ProfileSubScreen.NONE })
        }
        ProfileSubScreen.HELP_SUPPORT -> {
            HelpAndSupportSheet(onDismiss = { activeSubScreen = ProfileSubScreen.NONE })
        }
        ProfileSubScreen.ACCOUNT_SECURITY -> {
            AccountSecuritySheet(viewModel = viewModel, onDismiss = { activeSubScreen = ProfileSubScreen.NONE })
        }
        ProfileSubScreen.DEVICE_SESSIONS -> {
            DeviceSessionsSheet(viewModel = viewModel, onDismiss = { activeSubScreen = ProfileSubScreen.NONE })
        }
        ProfileSubScreen.DATA_EXPORT -> {
            DataExportSheet(profile = profile, onDismiss = { activeSubScreen = ProfileSubScreen.NONE })
        }
        ProfileSubScreen.NOTIFICATION_SETTINGS -> {
            NotificationSettingsSheet(onDismiss = { activeSubScreen = ProfileSubScreen.NONE })
        }
        ProfileSubScreen.APP_LANGUAGE -> {
            AppLanguageSheet(
                currentLanguage = appLanguage,
                onApply = onLanguageChanged,
                onDismiss = { activeSubScreen = ProfileSubScreen.NONE }
            )
        }
        ProfileSubScreen.EDIT_PROFILE -> {
            if (profile != null) {
                EditProfileDialog(
                    profile = profile!!,
                    onChangePicture = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onDismiss = { activeSubScreen = ProfileSubScreen.NONE },
                    onSave = { updatedProfile ->
                        viewModel.updateUserProfile(updatedProfile)
                        activeSubScreen = ProfileSubScreen.NONE
                    }
                )
            }
        }
        ProfileSubScreen.NONE -> {}
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.delete_account_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAccount {
                        com.example.data.security.DemoAuthManager.signOut(context)
                        Firebase.auth.signOut()
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showLogoutDialog) {
        LogoutConfirmDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                viewModel.goOffline {
                    com.example.data.security.DemoAuthManager.signOut(context)
                    Firebase.auth.signOut()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile completeness meter
        profile?.let { p ->
            val (pct, hint) = p.completeness()
            if (pct < 100) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.profile_complete, pct), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (hint != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Profile Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (avatarUrl != null) {
                        SnugImage(
                            model = avatarUrl,
                            contentDescription = stringResource(R.string.profile_photo),
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(100.dp)
                                .clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = stringResource(R.string.add_profile_picture),
                                modifier = Modifier.padding(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 4.dp
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.change_profile_picture),
                            modifier = Modifier.padding(6.dp),
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${profile?.displayName ?: stringResource(R.string.profile_default_user)}, ${calculateAge(profile?.birthDate)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (profile?.hasVerifiedBadge() == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = stringResource(R.string.verified),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                val myPlace = profile?.let { listOf(it.city, it.country).filter { s -> s.isNotBlank() }.joinToString(", ") } ?: ""
                if (myPlace.isNotBlank()) {
                    Text(
                        text = myPlace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!profile?.bio.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile!!.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                
                OutlinedButton(
                    onClick = { activeSubScreen = ProfileSubScreen.EDIT_PROFILE },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_profile_bio))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // VIP Banner Card (admins and VIP members have every premium feature already)
        val hasPremium = isAdmin || profile?.isVip == true
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !hasPremium) { activeSubScreen = ProfileSubScreen.VIP_MEMBERSHIP },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (hasPremium) R.string.vip_active else R.string.get_vip), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        stringResource(if (hasPremium) R.string.premium_unlocked else R.string.vip_benefits),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
                if (!hasPremium) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Category 1: Account & Privacy Settings
        Text(
            text = stringResource(R.string.account_privacy),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsMenuRow(
                    icon = Icons.Default.Email,
                    iconTint = if (emailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.email_verification),
                    subtitle = if (emailVerified) stringResource(R.string.verified_email, EmailVerifier.email()) else stringResource(R.string.verify_email_address),
                    badge = stringResource(if (emailVerified) R.string.verified else R.string.verify),
                    badgeColor = if (emailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    onClick = { activeSubScreen = ProfileSubScreen.PHOTO_VERIFICATION }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Face,
                    iconTint = if (profile?.photoVerified == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.photo_verification),
                    subtitle = when {
                        profile?.photoVerified == true -> stringResource(R.string.selfie_matches)
                        verifyStatus == "pending" -> stringResource(R.string.selfie_review)
                        verifyStatus == "rejected" -> stringResource(R.string.selfie_rejected)
                        else -> stringResource(R.string.take_selfie_badge)
                    },
                    badge = when {
                        profile?.photoVerified == true -> stringResource(R.string.verified)
                        verifyStatus == "pending" -> stringResource(R.string.pending)
                        else -> stringResource(R.string.verify)
                    },
                    badgeColor = if (profile?.photoVerified == true || verifyStatus == "pending") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    onClick = { activeSubScreen = ProfileSubScreen.PHOTO_VERIFICATION }
                )

                if (isAdmin) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsMenuRow(
                        icon = Icons.Default.Verified,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.admin_verify_review),
                        subtitle = stringResource(R.string.compare_selfies),
                        badge = stringResource(R.string.pending_count, pendingCount.size),
                        badgeColor = if (pendingCount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        onClick = onOpenAdmin
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Collections,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.my_photos_privacy),
                    subtitle = stringResource(R.string.photos_uploaded_visibility, profile?.photos?.size ?: 0),
                    onClick = { activeSubScreen = ProfileSubScreen.PHOTOS_PRIVACY }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Visibility,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.profile_visibility),
                    subtitle = stringResource(R.string.choose_visibility),
                    onClick = { activeSubScreen = ProfileSubScreen.PROFILE_VISIBILITY }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Lock,
                    iconTint = if (isPinEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.security_pin_lock),
                    subtitle = stringResource(if (isPinEnabled) R.string.pin_active else R.string.pin_unlock),
                    badge = stringResource(if (isPinEnabled) R.string.enabled else R.string.off),
                    badgeColor = if (isPinEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    onClick = { activeSubScreen = ProfileSubScreen.SECURITY_PIN_LOCK }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Key,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.account_credentials),
                    subtitle = stringResource(R.string.change_password_email),
                    onClick = { activeSubScreen = ProfileSubScreen.ACCOUNT_SECURITY }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Devices,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.device_sessions),
                    subtitle = stringResource(R.string.manage_devices),
                    onClick = { activeSubScreen = ProfileSubScreen.DEVICE_SESSIONS }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Download,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.data_privacy_export),
                    subtitle = stringResource(R.string.download_account_json),
                    onClick = { activeSubScreen = ProfileSubScreen.DATA_EXPORT }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Category 2: App & Matching Preferences
        Text(
            text = stringResource(R.string.app_preferences),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsMenuRow(
                    icon = Icons.Default.Tune,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.discovery_preferences),
                    subtitle = stringResource(R.string.distance_age_gender),
                    onClick = { activeSubScreen = ProfileSubScreen.DISCOVERY_PREFERENCES }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Notifications,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.notifications_quiet),
                    subtitle = stringResource(R.string.push_matches_quiet),
                    onClick = { activeSubScreen = ProfileSubScreen.NOTIFICATION_SETTINGS }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.Language,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.language_settings_title),
                    subtitle = stringResource(
                        R.string.language_settings_subtitle,
                        when (appLanguage) {
                            "es" -> stringResource(R.string.language_name_es)
                            "fr" -> stringResource(R.string.language_name_fr)
                            "de" -> stringResource(R.string.language_name_de)
                            "sw" -> stringResource(R.string.language_name_sw)
                            "ja" -> stringResource(R.string.language_name_ja)
                            "zh" -> stringResource(R.string.language_name_zh)
                            "hi" -> stringResource(R.string.language_name_hi)
                            "tl" -> stringResource(R.string.language_name_tl)
                            else -> stringResource(R.string.language_name_en)
                        }
                    ),
                    onClick = { activeSubScreen = ProfileSubScreen.APP_LANGUAGE }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.HealthAndSafety,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.safety_center),
                    subtitle = stringResource(R.string.dating_safety_tips),
                    onClick = { activeSubScreen = ProfileSubScreen.SAFETY_CENTER }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsMenuRow(
                    icon = Icons.Default.HelpOutline,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.help_guidelines),
                    subtitle = stringResource(R.string.safety_faq_support),
                    onClick = { activeSubScreen = ProfileSubScreen.HELP_SUPPORT }
                )

            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Logout Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = stringResource(R.string.log_out),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.log_out), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.sign_out_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.setPaused(profile?.isPaused != true) }) {
                Text(stringResource(if (profile?.isPaused == true) R.string.resume_profile else R.string.pause_profile))
            }
            TextButton(onClick = { showDeleteDialog = true }) {
                Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profile_footer_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}

// -------------------------------------------------------------
// SUB-SCREENS / MODAL BOTTOM SHEETS
// -------------------------------------------------------------

@Composable
fun PhotoItem(photo: UserPhoto, onDelete: () -> Unit, onToggleBlur: () -> Unit, onTogglePublic: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        SnugImage(
            model = photo.url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (photo.isBlurred) Modifier.blur(10.dp) else Modifier),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Black.copy(alpha = 0.5f))))
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                IconButton(onClick = onToggleBlur) {
                    Icon(
                        if (photo.isBlurred) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.toggle_blur),
                        tint = if (photo.isBlurred) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Surface(
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                IconButton(onClick = onTogglePublic) {
                    Icon(
                        if (photo.isPublic) Icons.Default.Public else Icons.Default.Lock,
                        contentDescription = if (photo.isPublic) stringResource(R.string.photo_make_private) else stringResource(R.string.photo_make_public),
                        tint = if (photo.isPublic) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Surface(
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileVisibilitySheet(
    profile: UserProfile?,
    viewModel: SnugViewModel,
    onDismiss: () -> Unit
) {
    val current = profile ?: return
    var showName by remember(current.id) { mutableStateOf(current.showNameToOthers) }
    var showAge by remember(current.id) { mutableStateOf(current.showAgeToOthers) }
    var showLocation by remember(current.id) { mutableStateOf(current.showLocationToOthers) }
    var showPhotos by remember(current.id) { mutableStateOf(current.showPhotosToOthers) }
    var showBio by remember(current.id) { mutableStateOf(current.showBioToOthers) }
    var showInterests by remember(current.id) { mutableStateOf(current.showInterestsToOthers) }
    var showPrompts by remember(current.id) { mutableStateOf(current.showPromptsToOthers) }
    var showAboutMe by remember(current.id) { mutableStateOf(current.showAboutMeToOthers) }
    var showPresence by remember(current.id) { mutableStateOf(current.showPresenceToOthers) }
    var showVerifiedBadge by remember(current.id) { mutableStateOf(current.showVerifiedBadgeToOthers) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.profile_visibility), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.visibility_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            VisibilityPreferenceRow(stringResource(R.string.visibility_name), stringResource(R.string.visibility_name_desc), showName) { showName = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_age), stringResource(R.string.visibility_age_desc), showAge) { showAge = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_location), stringResource(R.string.visibility_location_desc), showLocation) { showLocation = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_photos), stringResource(R.string.visibility_photos_desc), showPhotos) { showPhotos = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_bio), stringResource(R.string.visibility_bio_desc), showBio) { showBio = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_interests), stringResource(R.string.visibility_interests_desc), showInterests) { showInterests = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_prompts), stringResource(R.string.visibility_prompts_desc), showPrompts) { showPrompts = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_about_me), stringResource(R.string.visibility_about_me_desc), showAboutMe) { showAboutMe = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_online_status), stringResource(R.string.visibility_online_desc), showPresence) { showPresence = it }
            VisibilityPreferenceRow(stringResource(R.string.visibility_verified_badge), stringResource(R.string.visibility_verified_desc), showVerifiedBadge) { showVerifiedBadge = it }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.updateUserProfile(
                        current.copy(
                            showNameToOthers = showName,
                            showAgeToOthers = showAge,
                            showLocationToOthers = showLocation,
                            showPhotosToOthers = showPhotos,
                            showBioToOthers = showBio,
                            showInterestsToOthers = showInterests,
                            showPromptsToOthers = showPrompts,
                            showAboutMeToOthers = showAboutMe,
                            showPresenceToOthers = showPresence,
                            showVerifiedBadgeToOthers = showVerifiedBadge
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) {
                Text(stringResource(R.string.save_visibility_settings))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VisibilityPreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotosPrivacySheet(
    profile: UserProfile?,
    viewModel: SnugViewModel,
    onDismiss: () -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.addPhoto(it) }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.my_photos_privacy), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.photos_privacy_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_photo))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (profile?.photos?.isEmpty() == true) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_photos_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    profile?.photos?.forEachIndexed { index, photo ->
                        PhotoItem(
                            photo = photo,
                            onDelete = {
                                val newList = profile.photos.toMutableList().apply { removeAt(index) }
                                viewModel.updateGallery(newList)
                            },
                            onToggleBlur = {
                                val newList = profile.photos.toMutableList().apply {
                                    this[index] = photo.copy(isBlurred = !photo.isBlurred)
                                }
                                viewModel.updateGallery(newList)
                            },
                            onTogglePublic = {
                                val newList = profile.photos.toMutableList().apply {
                                    this[index] = photo.copy(isPublic = !photo.isPublic)
                                }
                                viewModel.updateGallery(newList)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) {
                Text(stringResource(R.string.done))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryPreferencesSheet(
    profile: UserProfile?,
    viewModel: SnugViewModel,
    onDismiss: () -> Unit
) {
    // Distance steps in km. The last step means stringResource(R.string.anywhere) (worldwide).
    val distanceStops = remember { listOf(5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 20000) }
    var distanceIndex by remember {
        mutableFloatStateOf(
            (distanceStops.indexOfFirst { it >= (profile?.radiusKm ?: 50) }
                .let { if (it == -1) distanceStops.lastIndex else it }).toFloat()
        )
    }
    val distanceKm = distanceStops[distanceIndex.roundToInt().coerceIn(0, distanceStops.lastIndex)]
    var ageRange by remember { mutableStateOf((profile?.minAge ?: 18).toFloat()..(profile?.maxAge ?: 35).toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.discovery_preferences), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.discovery_preferences_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(24.dp))

            // Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.maximum_distance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        if (distanceKm >= 20000) stringResource(R.string.anywhere) else "$distanceKm km",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Slider(
                value = distanceIndex,
                onValueChange = { distanceIndex = it },
                valueRange = 0f..distanceStops.lastIndex.toFloat(),
                steps = distanceStops.size - 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Age Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.age_preference), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        stringResource(R.string.profile_age_range_years, ageRange.start.roundToInt(), ageRange.endInclusive.roundToInt()),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            RangeSlider(
                value = ageRange,
                onValueChange = { ageRange = it },
                valueRange = 18f..65f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    profile?.let {
                        viewModel.updateUserProfile(
                            it.copy(
                                radiusKm = distanceKm,
                                minAge = ageRange.start.roundToInt(),
                                maxAge = ageRange.endInclusive.roundToInt()
                            )
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) {
                Text(stringResource(R.string.save_preferences))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPinSheet(
    pinManager: PinManager,
    isPinEnabled: Boolean,
    onPinStateChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var showPinSetupDialog by remember { mutableStateOf(false) }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onDismiss = { showPinSetupDialog = false },
            onPinSet = {
                onPinStateChanged(true)
                showPinSetupDialog = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(stringResource(R.string.pin_lock_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.pin_sheet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.enable_pin_lock), fontWeight = FontWeight.Bold)
                        Text(if (isPinEnabled) stringResource(R.string.pin_lock_enabled) else stringResource(R.string.pin_lock_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                showPinSetupDialog = true
                            } else {
                                pinManager.clearPin()
                                onPinStateChanged(false)
                            }
                        }
                    )
                }
            }

            if (isPinEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showPinSetupDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.change_pin))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) {
                Text(stringResource(R.string.close))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipUpgradeSheet(viewModel: SnugViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val billing = viewModel.billing
    val plans by billing.plans.collectAsState()
    val loading by billing.loading.collectAsState()
    val busy by billing.busy.collectAsState()
    val message by billing.message.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()
    var currentTab by remember { mutableStateOf(0) }

    // Default to the 6-month plan (the "popular" one), else the first plan
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = plans.firstOrNull { it.basePlanId == selectedId }
        ?: plans.firstOrNull { it.months == 6 } ?: plans.firstOrNull()

    // Close the sheet as soon as the server confirmed VIP
    LaunchedEffect(me?.isVip) { if (me?.isVip == true) onDismiss() }
    DisposableEffect(Unit) { onDispose { billing.clearMessage() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.vip_club_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.vip_club_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            TabRow(selectedTabIndex = currentTab) {
                Tab(selected = currentTab == 0, onClick = { currentTab = 0 }, text = { Text(stringResource(R.string.vip_tiers)) })
                Tab(selected = currentTab == 1, onClick = { currentTab = 1 }, text = { Text(stringResource(R.string.vip_compare)) })
                Tab(selected = currentTab == 2, onClick = { currentTab = 2 }, text = { Text(stringResource(R.string.vip_manage)) })
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (currentTab) {
                0 -> {
                    VipBenefitItem(stringResource(R.string.vip_unlimited_likes), stringResource(R.string.vip_unlimited_likes_desc))
                    VipBenefitItem(stringResource(R.string.vip_unlimited_rewinds), stringResource(R.string.vip_unlimited_rewinds_desc))
                    VipBenefitItem(stringResource(R.string.vip_super_likes_title), stringResource(R.string.vip_super_likes_desc))
                    VipBenefitItem(stringResource(R.string.vip_boost_title), stringResource(R.string.vip_boost_desc))
                    VipBenefitItem(stringResource(R.string.vip_likes_title), stringResource(R.string.vip_likes_desc))
                    VipBenefitItem(stringResource(R.string.vip_filters_title), stringResource(R.string.vip_filters_desc))

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.profile_vip_trial_annual),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                        loading -> CircularProgressIndicator()
                        plans.isEmpty() -> Text(
                            stringResource(R.string.vip_syncing_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            plans.forEach { plan ->
                                PriceTier(
                                    title = stringResource(R.string.profile_plan_months, plan.months),
                                    price = if (plan.months > 1 && plan.perMonthText.isNotBlank()) stringResource(R.string.profile_price_per_month, plan.perMonthText) else plan.priceText,
                                    isPopular = plan.months == 6,
                                    isSelected = plan.basePlanId == selected?.basePlanId,
                                    onClick = { selectedId = plan.basePlanId }
                                )
                            }
                        }
                    }

                    message?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity != null && selected != null) billing.launchPurchase(activity, selected)
                        },
                        enabled = selected != null && !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = CircleShape
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(
                            if (selected != null && selected.trialDays > 0) stringResource(R.string.profile_start_free_trial, selected.trialDays) else stringResource(R.string.profile_subscribe_now)
                        )
                    }

                    if (selected != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.profile_purchase_terms,
                                selected.trialDays,
                                selected.priceText,
                                when (selected.months) { 1 -> stringResource(R.string.profile_month); 12 -> stringResource(R.string.profile_year); else -> stringResource(R.string.profile_months, selected.months) }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                1 -> {
                    VipComparisonTable()
                }
                2 -> {
                    VipManagementTab(profile = me, onManage = {
                        val uri = android.net.Uri.parse(
                            "https://play.google.com/store/account/subscriptions?sku=${com.example.data.billing.BillingManager.PRODUCT_ID}&package=${context.packageName}"
                        )
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                    }, onRestore = { viewModel.restorePurchases() })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewModel.restorePurchases() }) {
                    Text(stringResource(R.string.restore_purchases))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun VipComparisonTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(stringResource(R.string.feature), modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.free), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.vip), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()

            val rows = listOf(
                Triple(stringResource(R.string.profile_compare_daily_likes), "50", stringResource(R.string.profile_unlimited)),
                Triple(stringResource(R.string.profile_compare_rewind), stringResource(R.string.profile_none), stringResource(R.string.profile_unlimited)),
                Triple(stringResource(R.string.profile_compare_super_likes), stringResource(R.string.profile_one_per_day), stringResource(R.string.profile_five_per_day)),
                Triple(stringResource(R.string.profile_compare_monthly_boost), stringResource(R.string.profile_no), stringResource(R.string.profile_one_per_month)),
                Triple(stringResource(R.string.profile_compare_likes), stringResource(R.string.profile_blurred), stringResource(R.string.profile_full)),
                Triple(stringResource(R.string.profile_compare_filters), stringResource(R.string.profile_basic), stringResource(R.string.profile_all_vip)),
                Triple(stringResource(R.string.profile_compare_verified_badge), stringResource(R.string.profile_included), stringResource(R.string.profile_priority_review)),
                Triple(stringResource(R.string.profile_compare_photo_privacy), stringResource(R.string.profile_yes), stringResource(R.string.profile_yes))
            )

            rows.forEach { (feat, free, vip) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(feat, modifier = Modifier.weight(1.8f), style = MaterialTheme.typography.bodySmall)
                    Text(free, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vip, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
fun VipManagementTab(profile: UserProfile?, onManage: () -> Unit, onRestore: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.current_subscription_status), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (profile?.isVip == true) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (profile?.isVip == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (profile?.isVip == true) stringResource(R.string.vip_active_membership) else stringResource(R.string.vip_free_plan),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (profile?.isVip == true)
                        stringResource(R.string.vip_subscription_description)
                    else
                        stringResource(R.string.vip_free_tier_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.manage_google_play))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.billing_purchase_history), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.vip_billing_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.restore_sync_purchases))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecuritySheet(viewModel: SnugViewModel, onDismiss: () -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.account_credentials_security), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.account_credentials_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(20.dp))

            Text(stringResource(R.string.change_password), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.new_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.confirm_new_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                enabled = !busy && newPassword.length >= 6 && newPassword == confirmPassword,
                onClick = {
                    busy = true
                    viewModel.changePassword(newPassword) { success, err ->
                        busy = false
                        isError = !success
                        statusMessage = if (success) context.getString(R.string.password_updated_successfully) else err ?: context.getString(R.string.password_update_failed)
                        if (success) {
                            newPassword = ""
                            confirmPassword = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_password))
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Text(stringResource(R.string.change_email_address), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.current_email, EmailVerifier.email().ifBlank { stringResource(R.string.not_set) }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text(stringResource(R.string.new_email_address)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                enabled = !busy && android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail.trim()).matches(),
                onClick = {
                    busy = true
                    viewModel.changeEmail(newEmail.trim()) { success, err ->
                        busy = false
                        isError = !success
                        statusMessage = if (success) context.getString(R.string.profile_email_verification_sent, newEmail) else err ?: context.getString(R.string.profile_email_update_failed)
                        if (success) newEmail = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.send_email_verification))
            }

            statusMessage?.let {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSessionsSheet(viewModel: SnugViewModel, onDismiss: () -> Unit) {
    var signOutNotice by remember { mutableStateOf<String?>(null) }
    val sessionsRevokedNotice = stringResource(R.string.sessions_revoked_notice)
    val deviceModel = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + android.os.Build.MODEL
    val osVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.device_sessions), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.device_sessions_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(deviceModel, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                                Text(stringResource(R.string.this_device), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text(osVersion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.active_verified_session), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.sign_out_other_devices), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.revoke_sessions_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            signOutNotice = sessionsRevokedNotice
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.revoke_other_sessions))
                    }
                }
            }

            signOutNotice?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportSheet(profile: UserProfile?, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val dataJson = remember(profile) {
        val p = profile ?: UserProfile()
        """
        {
          "export_generated_at": "${java.util.Date()}",
          "account_id": "${p.id}",
          "display_name": "${p.displayName}",
          "gender": "${p.gender}",
          "interested_in": ${p.interestedIn.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},
          "interests": ${p.interests.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},
          "bio": "${p.bio.replace("\"", "\\\"")}",
          "photo_verified": ${p.photoVerified},
          "is_vip": ${p.isVip},
          "city": "${p.city}",
          "country": "${p.country}",
          "prompts_count": ${p.prompts.size},
          "photos_count": ${p.photos.size}
        }
        """.trimIndent()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.data_privacy_export), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.data_export_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    dataJson,
                    modifier = Modifier.padding(12.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(dataJson))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (copied) stringResource(R.string.copied_to_clipboard) else stringResource(R.string.copy_json_export))
            }

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSheet(onDismiss: () -> Unit) {
    var matchAlerts by remember { mutableStateOf(true) }
    var messageAlerts by remember { mutableStateOf(true) }
    var likesAlerts by remember { mutableStateOf(true) }
    var emailDigest by remember { mutableStateOf(false) }
    var quietHoursEnabled by remember { mutableStateOf(true) }
    var quietHoursStart by remember { mutableStateOf("22:00") }
    var quietHoursEnd by remember { mutableStateOf("08:00") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.notifications_quiet), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.notification_preferences_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(20.dp))

            Text(stringResource(R.string.push_notifications), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            NotificationToggleRow(stringResource(R.string.notification_new_matches), stringResource(R.string.notification_new_matches_desc), matchAlerts) { matchAlerts = it }
            HorizontalDivider()
            NotificationToggleRow(stringResource(R.string.notification_new_messages), stringResource(R.string.notification_new_messages_desc), messageAlerts) { messageAlerts = it }
            HorizontalDivider()
            NotificationToggleRow(stringResource(R.string.notification_likes), stringResource(R.string.notification_likes_desc), likesAlerts) { likesAlerts = it }
            HorizontalDivider()
            NotificationToggleRow(stringResource(R.string.notification_weekly_email), stringResource(R.string.notification_weekly_email_desc), emailDigest) { emailDigest = it }

            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.quiet_hours), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            NotificationToggleRow(stringResource(R.string.notification_enable_quiet_hours), stringResource(R.string.notification_quiet_hours_desc), quietHoursEnabled) { quietHoursEnabled = it }

            if (quietHoursEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = quietHoursStart,
                        onValueChange = { quietHoursStart = it },
                        label = { Text(stringResource(R.string.start_time)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = quietHoursEnd,
                        onValueChange = { quietHoursEnd = it },
                        label = { Text(stringResource(R.string.end_time)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_preferences)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NotificationToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageSheet(currentLanguage: String = "en", onApply: (String) -> Unit = {}, onDismiss: () -> Unit) {
    var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    val languages = listOf(
        "en" to R.string.language_name_en,
        "es" to R.string.language_name_es,
        "fr" to R.string.language_name_fr,
        "de" to R.string.language_name_de,
        "sw" to R.string.language_name_sw,
        "ja" to R.string.language_name_ja,
        "zh" to R.string.language_name_zh,
        "hi" to R.string.language_name_hi,
        "tl" to R.string.language_name_tl
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.language_sheet_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.language_sheet_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(20.dp))

            languages.forEach { (code, nameRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLanguage = code }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedLanguage == code, onClick = { selectedLanguage = code })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(nameRes), fontWeight = if (selectedLanguage == code) FontWeight.Bold else FontWeight.Normal)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { onApply(selectedLanguage); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.confirm_language))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpAndSupportSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.help_safety_center), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.help_safety_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(20.dp))

            HelpCard(stringResource(R.string.help_community_guidelines), stringResource(R.string.help_community_guidelines_desc), Icons.Default.HealthAndSafety)
            Spacer(modifier = Modifier.height(10.dp))
            HelpCard(stringResource(R.string.help_safety_tips), stringResource(R.string.help_safety_tips_desc), Icons.Default.Security)
            Spacer(modifier = Modifier.height(10.dp))
            HelpCard(stringResource(R.string.help_contact_support), stringResource(R.string.help_contact_support_desc), Icons.Default.Email)

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) {
                Text(stringResource(R.string.done))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun HelpCard(title: String, desc: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VipBenefitItem(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun RowScope.PriceTier(
    title: String,
    price: String,
    isPopular: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPopular) {
                Text(stringResource(R.string.popular), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(price, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.logout_confirm_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.logout_confirm_message),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.log_out))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
/** Text followed by a red * when the field is compulsory. */
@Composable
private fun RequiredLabel(text: String, required: Boolean = true) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            if (required) {
                withStyle(
                    androidx.compose.ui.text.SpanStyle(color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                ) { append(" *") }
            }
        }
    )
}

@Composable
private fun RequiredTitle(text: String) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            withStyle(
                androidx.compose.ui.text.SpanStyle(color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            ) { append(" *") }
        },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    RequiredTitle(title)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                // tap again to clear
                onClick = { onSelect(if (selected == option) "" else option) },
                label = { Text(option) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
    profile: UserProfile,
    onChangePicture: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var gender by remember { mutableStateOf(profile.gender) }
    val interestedIn = remember { mutableStateListOf(*profile.interestedIn.toTypedArray()) }
    val interests = remember { mutableStateListOf(*profile.interests.toTypedArray()) }
    var birthYear by remember { mutableStateOf(profile.birthDate?.toDate()?.year?.plus(1900)?.toString() ?: "") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var lookingFor by remember { mutableStateOf(profile.lookingFor) }
    var haveKids by remember { mutableStateOf(profile.haveKids) }
    var wantKids by remember { mutableStateOf(profile.wantKids) }
    var smoking by remember { mutableStateOf(profile.smoking) }
    var drinking by remember { mutableStateOf(profile.drinking) }
    var exercise by remember { mutableStateOf(profile.exercise) }
    var religion by remember { mutableStateOf(profile.religion) }
    var education by remember { mutableStateOf(profile.education) }
    var pets by remember { mutableStateOf(profile.pets) }
    var zodiac by remember { mutableStateOf(profile.zodiac) }
    var jobTitle by remember { mutableStateOf(profile.jobTitle) }
    var heightText by remember { mutableStateOf(if (profile.heightCm > 0) profile.heightCm.toString() else "") }
    var hideDistance by remember { mutableStateOf(profile.hideDistance) }
    val stillNeededPrefix = stringResource(R.string.still_needed_prefix)

    val promptQuestions = remember { mutableStateListOf(*Array(3) { profile.prompts.getOrNull(it)?.question ?: "" }) }
    val promptAnswers = remember { mutableStateListOf(*Array(3) { profile.prompts.getOrNull(it)?.answer ?: "" }) }

    val allInterests = ProfileOptions.interests
    val genders = listOf("Male", "Female", "Non-binary")

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.edit_profile), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(
                            androidx.compose.ui.text.SpanStyle(color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                        ) { append("*") }
                        append(stringResource(R.string.profile_required_optional_note))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                val dialogAvatar = profile.profilePhoto.takeIf { it.isNotBlank() } ?: profile.photos.firstOrNull()?.url
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (dialogAvatar != null) {
                        SnugImage(
                            model = dialogAvatar,
                            contentDescription = stringResource(R.string.profile_photo),
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clickable { onChangePicture() },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(96.dp).clickable { onChangePicture() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = stringResource(R.string.add_profile_picture),
                                modifier = Modifier.padding(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(onClick = onChangePicture) {
                        RequiredLabel(if (dialogAvatar != null) stringResource(R.string.change_picture) else stringResource(R.string.add_profile_picture))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { RequiredLabel(stringResource(R.string.display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { RequiredLabel(stringResource(R.string.visibility_bio)) },
                    placeholder = { Text(stringResource(R.string.bio_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) birthYear = it },
                    label = { RequiredLabel(stringResource(R.string.birth_year_18_plus)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(14.dp))

                Spacer(modifier = Modifier.height(20.dp))
                RequiredTitle(stringResource(R.string.i_am))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genders.forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = g },
                            label = { Text(g) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                RequiredTitle(stringResource(R.string.interested_in))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genders.forEach { g ->
                        FilterChip(
                            selected = interestedIn.contains(g),
                            onClick = {
                                if (interestedIn.contains(g)) interestedIn.remove(g) else interestedIn.add(g)
                            },
                            label = { Text(g) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                RequiredTitle(stringResource(R.string.interests_hobbies_minimum))
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allInterests.forEach { interest ->
                        FilterChip(
                            selected = interests.contains(interest),
                            onClick = {
                                if (interests.contains(interest)) interests.remove(interest) else interests.add(interest)
                            },
                            label = { Text(interest) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.profile_prompts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.profile_prompts_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (slot in 0 until 3) {
                    Spacer(modifier = Modifier.height(10.dp))
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                promptQuestions[slot].ifBlank { stringResource(R.string.pick_a_prompt) },
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ProfileOptions.promptQuestions.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q) },
                                    onClick = { promptQuestions[slot] = q; menuOpen = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_search)) },
                                onClick = { promptQuestions[slot] = ""; promptAnswers[slot] = ""; menuOpen = false }
                            )
                        }
                    }
                    if (promptQuestions[slot].isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = promptAnswers[slot],
                            onValueChange = { if (it.length <= 150) promptAnswers[slot] = it },
                            label = { Text(stringResource(R.string.your_answer)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.more_about_me), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                ChoiceSection(stringResource(R.string.looking_for), ProfileOptions.lookingFor, lookingFor) { lookingFor = it }
                ChoiceSection(stringResource(R.string.kids), ProfileOptions.haveKids, haveKids) { haveKids = it }
                ChoiceSection(stringResource(R.string.future_kids), ProfileOptions.wantKids, wantKids) { wantKids = it }
                ChoiceSection(stringResource(R.string.smoking), ProfileOptions.smoking, smoking) { smoking = it }
                ChoiceSection(stringResource(R.string.drinking), ProfileOptions.drinking, drinking) { drinking = it }
                ChoiceSection(stringResource(R.string.exercise), ProfileOptions.exercise, exercise) { exercise = it }
                ChoiceSection(stringResource(R.string.religion), ProfileOptions.religion, religion) { religion = it }
                ChoiceSection(stringResource(R.string.education), ProfileOptions.education, education) { education = it }
                ChoiceSection(stringResource(R.string.pets), ProfileOptions.pets, pets) { pets = it }
                ChoiceSection(stringResource(R.string.zodiac_sign), ProfileOptions.zodiac, zodiac) { zodiac = it }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = jobTitle,
                    onValueChange = { if (it.length <= 40) jobTitle = it },
                    label = { Text(stringResource(R.string.job_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) heightText = it },
                    label = { Text(stringResource(R.string.height_cm)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.hide_my_distance), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.hide_distance_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = hideDistance, onCheckedChange = { hideDistance = it })
                }

                saveError?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(28.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            val year = birthYear.toIntOrNull() ?: 0
                            val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            val calendar = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.YEAR, year)
                                set(java.util.Calendar.MONTH, 0)
                                set(java.util.Calendar.DAY_OF_MONTH, 1)
                            }
                            val candidate = profile.copy(
                                displayName = name, 
                                bio = bio, 
                                gender = gender,
                                birthDate = if (year in 1900..(thisYear - 18)) com.google.firebase.Timestamp(calendar.time) else null,
                                interestedIn = interestedIn.toList(),
                                interests = interests.toList(),
                                lookingFor = lookingFor,
                                haveKids = haveKids,
                                wantKids = wantKids,
                                smoking = smoking,
                                drinking = drinking,
                                exercise = exercise,
                                religion = religion,
                                education = education,
                                pets = pets,
                                zodiac = zodiac,
                                prompts = (0 until 3)
                                    .map { ProfilePrompt(promptQuestions[it], promptAnswers[it].trim()) }
                                    .filter { it.question.isNotBlank() && it.answer.isNotBlank() },
                                hideDistance = hideDistance,
                                jobTitle = jobTitle.trim(),
                                heightCm = heightText.toIntOrNull()?.takeIf { it in 100..250 } ?: 0
                            )
                            val missing = candidate.missingRequiredFields()
                            if (missing.isEmpty()) {
                                saveError = null
                                onSave(candidate)
                            } else {
                                saveError = stillNeededPrefix + missing.joinToString(", ")
                            }
                        },
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.save_profile))
                    }
                }
            }
        }
    }
}

/**
 * Shown instead of the app until the email is verified and every required profile field is filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGateScreen(
    viewModel: SnugViewModel,
    profile: UserProfile,
    missing: List<String>,
    emailVerified: Boolean,
    onEmailVerified: () -> Unit,
    onLogout: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val emailSentNote = stringResource(R.string.email_sent_check_inbox)
    val emailNotVerifiedNote = stringResource(R.string.email_not_verified_yet)
    val message by viewModel.uiMessage.collectAsState()
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.setProfilePicture(it) } }
    )

    if (showEdit) {
        EditProfileDialog(
            profile = profile,
            onChangePicture = {
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { showEdit = false },
            onSave = { updated ->
                viewModel.updateUserProfile(updated)
                showEdit = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.finish_setting_up), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_description),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Step 1: email
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (emailVerified) Icons.Default.CheckCircle else Icons.Default.Email,
                        contentDescription = null,
                        tint = if (emailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.setup_verify_email), fontWeight = FontWeight.Bold)
                }
                if (!emailVerified) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_email_verification_instructions, EmailVerifier.email().ifBlank { stringResource(R.string.profile_your_address) }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    note?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    note = EmailVerifier.send() ?: emailSentNote
                                    busy = false
                                }
                            }
                        ) { Text(stringResource(R.string.send_email)) }
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    if (EmailVerifier.refresh()) onEmailVerified() else note = emailNotVerifiedNote
                                    busy = false
                                }
                            }
                        ) { Text(stringResource(R.string.i_verified)) }
                    }
                } else {
                    Text(stringResource(R.string.done), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: profile
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (missing.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (missing.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.setup_complete_profile), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (missing.isEmpty()) {
                    Text(stringResource(R.string.done), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(stringResource(R.string.still_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    missing.forEach {
                        Text("\u2022 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = { showEdit = true }) { Text(stringResource(R.string.edit_profile)) }
                }
            }
        }

        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onLogout) { Text(stringResource(R.string.log_out)) }
    }
}
