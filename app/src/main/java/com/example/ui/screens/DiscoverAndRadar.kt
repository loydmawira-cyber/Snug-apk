package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.isOnlineNow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Star
import kotlinx.coroutines.delay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.Undo
import com.example.data.model.DiscoverFilters
import com.example.data.model.completeness
import com.example.data.model.superLikesLeft
import com.example.data.model.ProfileOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.components.PresenceBadge
import com.example.ui.components.SnugImage
import com.example.data.model.UserProfile
import com.example.data.model.avatarUrl
import com.example.data.model.basics
import com.example.data.model.placeAndDistance
import com.example.data.util.calculateAge
import com.example.data.util.formatDistance
import com.example.ui.viewmodel.SnugViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    viewModel: SnugViewModel,
    onOpenProfile: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onSetupProfile: () -> Unit = {}
) {
    val profile by viewModel.currentProfile.collectAsState()
    val profiles by viewModel.discoveryProfiles.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val likedIds by viewModel.likedIds.collectAsState()
    var swipeKey by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(profiles.size) {
        if (profiles.size <= 3) viewModel.loadMoreProfiles()
    }
    val celebration by viewModel.matchCelebration.collectAsState()

    MatchCelebrationDialog(
        other = celebration,
        onDismiss = { viewModel.dismissCelebration() },
        onSayHi = { id -> viewModel.openChat(id) { chatId -> onOpenChat(chatId) } }
    )

    // First-run welcome for people with a mostly empty profile
    val myProfile = me
    if (myProfile != null && !myProfile.onboarded && myProfile.completeness().first < 60) {
        AlertDialog(
            onDismissRequest = { viewModel.completeOnboarding() },
            title = { Text("Welcome to SNUG! \uD83D\uDC4B") },
            text = { Text("Profiles with a photo, a bio and a few interests get far more matches. It takes a minute to set yours up.") },
            confirmButton = {
                TextButton(onClick = { viewModel.completeOnboarding(); onSetupProfile() }) { Text("Set up profile") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.completeOnboarding() }) { Text("Later") }
            }
        )
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("Filters") },
            text = {
                Column {
                    Text("Looking for", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProfileOptions.lookingFor.forEach { option ->
                            FilterChip(
                                selected = filters.lookingFor == option,
                                onClick = {
                                    viewModel.setFilters(
                                        filters.copy(lookingFor = if (filters.lookingFor == option) "" else option)
                                    )
                                },
                                label = { Text(option) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Verified only", modifier = Modifier.weight(1f))
                        Switch(
                            checked = filters.verifiedOnly,
                            onCheckedChange = { viewModel.setFilters(filters.copy(verifiedOnly = it)) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = { viewModel.setFilters(DiscoverFilters()) }) { Text("Reset") }
            }
        )
    }
    
    android.util.Log.d("DiscoverScreen", "Recomposing with profile: ${profile?.displayName}")
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val maxWidthPx = constraints.maxWidth
        
        if (me?.isPaused == true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Text("Your profile is paused", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nobody can see you while you are on snooze.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.setPaused(false) }) { Text("Resume") }
            }
        } else if (profile == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Text(
                    if (filters.isActive) "No one matches your filters." else "No one new nearby. Try expanding your search!",
                    textAlign = TextAlign.Center
                )
                if (filters.isActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.setFilters(DiscoverFilters()) }) { Text("Clear filters") }
                }
            }
        } else {
            key(profile!!.id, swipeKey) {
            ProfileCard(
                profile = profile!!,
                liked = profile!!.id in likedIds,
                distanceText = placeAndDistance(profile!!, formatDistance(viewModel.distanceTo(me, profile!!))),
                onOpenProfile = { onOpenProfile(profile!!.id) },
                onLike = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    swipeKey++
                    viewModel.likeProfile(profile!!.id)
                },
                onSuperLike = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    swipeKey++
                    viewModel.likeProfile(profile!!.id, superLike = true)
                },
                onNudge = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.nudge(profile!!.id)
                },
                superLikesLeft = me?.superLikesLeft() ?: 0,
                autoAdvance = true,
                onPass = {
                    swipeKey++
                    viewModel.passProfile()
                },
                swipeThreshold = maxWidthPx / 3f
            )
            }
        }

        if (me?.isPaused != true) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).zIndex(3f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(onClick = { showFilters = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filters",
                        tint = if (filters.isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                FilledTonalIconButton(onClick = { viewModel.undoPass() }, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last pass")
                }
            }
        }
    }
}

@Composable
fun ProfileCard(
    profile: UserProfile,
    distanceText: String,
    onOpenProfile: () -> Unit,
    onLike: () -> Unit,
    onPass: () -> Unit,
    swipeThreshold: Float,
    onSuperLike: () -> Unit = {},
    onNudge: () -> Unit = {},
    superLikesLeft: Int = 0,
    autoAdvance: Boolean = false,
    liked: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    
    var isDragging by remember { mutableStateOf(false) }

    // Key to reset animation when profile changes
    LaunchedEffect(profile.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    // Auto-advance: after 5 seconds the card slides left (a pass). Waits while you drag it.
    LaunchedEffect(profile.id, autoAdvance, isDragging) {
        if (autoAdvance && !isDragging) {
            delay(5000)
            offsetX.animateTo(-2000f, tween(300))
            onPass()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer {
                rotationZ = offsetX.value / 20f
            }
            .pointerInput(profile.id) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragCancel = { isDragging = false },
                    onDragEnd = {
                        isDragging = false
                        coroutineScope.launch {
                            if (offsetX.value > swipeThreshold) {
                                // Swipe Right - Like
                                offsetX.animateTo(2000f, tween(300))
                                onLike()
                            } else if (offsetX.value < -swipeThreshold) {
                                // Swipe Left - Pass
                                offsetX.animateTo(-2000f, tween(300))
                                onPass()
                            } else {
                                // Snap back
                                launch { offsetX.animateTo(0f, tween(300)) }
                                launch { offsetY.animateTo(0f, tween(300)) }
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            val hasProfilePic = profile.profilePhoto.isNotBlank()
            val mainPhoto = profile.photos.firstOrNull()
            SnugImage(
                model = profile.avatarUrl(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!hasProfilePic && mainPhoto?.isBlurred == true) Modifier.blur(20.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .zIndex(2f)
            ) {
                Icon(Icons.Default.Info, contentDescription = "View profile", tint = Color.White)
            }
            if (liked) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .zIndex(2f)
                ) {
                    Text(
                        "Liked \u2764",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 300f
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .zIndex(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile() }) {
                    Text(
                        text = "${profile.displayName}, ${calculateAge(profile.birthDate)}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (profile.isPhoneVerified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Verified Phone",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                PresenceBadge(profile, Color.White.copy(alpha = 0.9f))
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = distanceText,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (profile.lookingFor.isNotBlank()) {
                    Text(
                        text = "Looking for: ${profile.lookingFor}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val quickFacts = profile.basics().take(3).joinToString(" \u2022 ")
                if (quickFacts.isNotBlank()) {
                    Text(
                        text = quickFacts,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = profile.bio,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilledTonalIconButton(
                        onClick = onPass,
                        modifier = Modifier.size(50.dp).testTag("pass_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Pass", modifier = Modifier.size(26.dp))
                    }
                    
                    OutlinedIconButton(
                        onClick = onOpenProfile,
                        modifier = Modifier.size(50.dp).testTag("view_profile_button"),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "View full profile", modifier = Modifier.size(24.dp))
                    }
                    
                    BadgedBox(badge = { Badge { Text(superLikesLeft.toString()) } }) {
                        FilledTonalIconButton(
                            onClick = onSuperLike,
                            modifier = Modifier.size(50.dp).testTag("super_like_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF1E88E5),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Super Like", modifier = Modifier.size(24.dp))
                        }
                    }

                    OutlinedIconButton(
                        onClick = onNudge,
                        modifier = Modifier.size(50.dp).testTag("nudge_button"),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("\uD83D\uDC4B", fontSize = 22.sp)
                    }

                    FilledIconButton(
                        onClick = onLike,
                        modifier = Modifier.size(50.dp).testTag("like_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Like", modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RadarScreen(viewModel: SnugViewModel, onOpenProfile: (String) -> Unit = {}) {
    val profiles by viewModel.nearbyProfiles.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()

    // Re-check every 30 seconds so people who left the app drop off the Online row
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val onlineNearby = profiles
        .filter { it.isOnlineNow(now) }
        .sortedBy { viewModel.distanceTo(me, it) ?: Double.MAX_VALUE } // closest first

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Online now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        if (onlineNearby.isEmpty()) {
            Text(
                "No one nearby is online right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(onlineNearby, key = { it.id }) { person ->
                    OnlineAvatar(person) { onOpenProfile(person.id) }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Nearby", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(profiles) { profile ->
                RadarItem(profile, placeAndDistance(profile, formatDistance(viewModel.distanceTo(me, profile)))) { onOpenProfile(profile.id) }
            }
        }
    }
}

/** Round photo, first name and a green online badge. Used in the Online now row on Radar. */
@Composable
private fun OnlineAvatar(profile: UserProfile, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable { onClick() }
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                SnugImage(
                    model = profile.avatarUrl(),
                    contentDescription = profile.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            profile.displayName.substringBefore(" "),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RadarItem(profile: UserProfile, distanceText: String, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.height(200.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            val photo = profile.photos.firstOrNull()
            val hasProfilePic = profile.profilePhoto.isNotBlank()
            SnugImage(
                model = profile.avatarUrl(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!hasProfilePic && photo?.isBlurred == true) Modifier.blur(10.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 100f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(profile.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                PresenceBadge(profile, Color.White.copy(alpha = 0.9f), compact = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        distanceText,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        maxLines = 2,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun MatchCelebrationDialog(other: UserProfile?, onDismiss: () -> Unit, onSayHi: (String) -> Unit) {
    if (other == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("It's a match! \uD83C\uDF89", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                SnugImage(
                    model = other.avatarUrl(),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("You and ${other.displayName} liked each other.", textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onSayHi(other.id) }) { Text("Say hi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep swiping") } }
    )
}
