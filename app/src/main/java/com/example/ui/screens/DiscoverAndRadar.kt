package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreHoriz
import kotlinx.coroutines.delay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.Undo
import com.example.data.model.DiscoverFilters
import com.example.data.model.calculateCompatibility
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
import androidx.compose.ui.res.stringResource
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
import com.example.data.model.placeAndDistance
import com.example.data.util.calculateAge
import com.example.data.util.formatDistance
import com.example.ui.viewmodel.SnugViewModel
import com.example.ui.Screen
import com.example.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    viewModel: SnugViewModel,
    onOpenProfile: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onSetupProfile: () -> Unit = {},
    onNavigateTab: (String) -> Unit = {}
) {
    val profile by viewModel.currentProfile.collectAsState()
    val profiles by viewModel.discoveryProfiles.collectAsState()
    val discoveryExhausted by viewModel.discoveryExhausted.collectAsState()
    val pagesLoaded by viewModel.discoveryPagesLoaded.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val isAdminUser by viewModel.isAdmin.collectAsState()
    val isPremium = isAdminUser || me?.isVip == true
    val discoveryError by viewModel.discoveryError.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val likedIds by viewModel.likedIds.collectAsState()
    var curatedPicks by remember { mutableStateOf(emptyList<UserProfile>()) }
    LaunchedEffect(profiles) {
        if (curatedPicks.isEmpty() && profiles.isNotEmpty()) curatedPicks = profiles.take(8)
    }
    var swipeKey by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(profiles.size, discoveryExhausted, pagesLoaded, discoveryError) {
        if (profiles.size <= 3 && !discoveryExhausted && discoveryError == null) {
            // Ask for the next page of people (ignored while one is already loading)
            delay(500)
            viewModel.loadMoreProfiles()
        }
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
            title = { Text(stringResource(com.example.R.string.welcome_title) + " \uD83D\uDC4B") },
            text = { Text(stringResource(R.string.ui_profiles_with_a_photo_a_bio_and_a_few_interests_get_far)) },
            confirmButton = {
                TextButton(onClick = { viewModel.completeOnboarding(); onSetupProfile() }) { Text(stringResource(R.string.ui_set_up_profile)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.completeOnboarding() }) { Text(stringResource(R.string.ui_later)) }
            }
        )
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text(stringResource(R.string.ui_filters_preferences), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.ui_looking_for), fontWeight = FontWeight.Bold)
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
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(stringResource(R.string.ui_filter_by_interest), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProfileOptions.interests.take(10).forEach { interest ->
                            FilterChip(
                                selected = filters.selectedInterest == interest,
                                onClick = {
                                    viewModel.setFilters(
                                        filters.copy(selectedInterest = if (filters.selectedInterest == interest) "" else interest)
                                    )
                                },
                                label = { Text(interest) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(stringResource(R.string.ui_minimum_compatibility), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to stringResource(com.example.R.string.ui_any), 70 to stringResource(com.example.R.string.ui_70_percent_plus), 80 to stringResource(com.example.R.string.ui_80_percent_plus), 90 to stringResource(com.example.R.string.ui_90_percent_plus)).forEach { (score, label) ->
                            FilterChip(
                                selected = filters.minCompatibility == score,
                                onClick = { viewModel.setFilters(filters.copy(minCompatibility = score)) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_verified_photos_only), modifier = Modifier.weight(1f))
                        Switch(
                            checked = filters.verifiedOnly,
                            onCheckedChange = { viewModel.setFilters(filters.copy(verifiedOnly = it)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_online_now_only), modifier = Modifier.weight(1f))
                        Switch(
                            checked = filters.onlineOnly,
                            onCheckedChange = { viewModel.setFilters(filters.copy(onlineOnly = it)) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text(stringResource(R.string.ui_done)) } },
            dismissButton = {
                TextButton(onClick = { viewModel.setFilters(DiscoverFilters()) }) { Text(stringResource(R.string.ui_reset)) }
            }
        )
    }
    
    android.util.Log.d("DiscoverScreen", "Recomposing with profile: ${profile?.displayName}")
    
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF100D17)).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.tab_for_you) to Screen.Discover.route,
                stringResource(R.string.tab_liked_you) to Screen.Likes.route,
                stringResource(R.string.tab_new) to Screen.Radar.route,
                stringResource(R.string.tab_online) to Screen.Online.route
            ).forEachIndexed { index, (label, route) ->
                Surface(
                    modifier = Modifier.weight(1f).height(40.dp).clickable { onNavigateTab(route) },
                    shape = RoundedCornerShape(24.dp),
                    color = if (index == 0) Color(0xFFF02B6B) else Color(0xFF241D2B),
                    border = if (index == 0) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3042))
                ) { Box(contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 13.sp) } }
            }
        }
        val sharedInterest = me?.interests?.firstOrNull { it in curatedPicks.firstOrNull()?.interests.orEmpty() }
        Surface(Modifier.fillMaxWidth().height(78.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFF261526), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF403047))) {
            Row(Modifier.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.banner_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.banner_subtitle, sharedInterest ?: stringResource(R.string.live_music)), color = Color(0xFFD8CADD), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    curatedPicks.take(3).forEach { person ->
                        val image = person.avatarUrl()
                        if (image != null) SnugImage(image, person.displayName, Modifier.size(42.dp).clip(CircleShape).background(Color.DarkGray), contentScale = ContentScale.Crop)
                        else Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF514358)))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.todays_picks), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.curated_for_you) + "  —  ♥", color = Color(0xFFE8DCEB), fontSize = 11.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.height(146.dp)) {
            items(curatedPicks, key = { "pick_${it.id}" }) { person ->
                val image = person.avatarUrl()
                Card(Modifier.width(120.dp).fillMaxHeight().clickable { onOpenProfile(person.id) }, shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2230))) {
                    Box(Modifier.fillMaxSize()) {
                        if (image != null) SnugImage(image, person.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f)))))
                        Surface(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 7.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFF02B6B)) {
                            Text(stringResource(R.string.match_percent, calculateCompatibility(me, person)), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        IconButton(onClick = { viewModel.likeProfile(person.id) }, modifier = Modifier.align(Alignment.TopEnd).size(34.dp).padding(4.dp)) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = stringResource(com.example.R.string.action_like_person, person.displayName), tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val age = if (person.showAgeToOthers && person.birthDate != null) ", ${calculateAge(person.birthDate)}" else ""
                            Text("${person.displayName.ifBlank { stringResource(com.example.R.string.member_fallback) }}$age", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (person.showLocationToOthers) {
                                Text("⌖ ${formatDistance(viewModel.distanceTo(me, person))}", color = Color.White.copy(alpha = .9f), fontSize = 9.sp, maxLines = 1)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                person.interests.take(2).forEach { interest ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF59285B).copy(alpha = .95f)) {
                                        Text(interest, color = Color.White, fontSize = 8.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(stringResource(R.string.filter_nearby), stringResource(R.string.filter_recently_active), stringResource(R.string.filter_shared_interests)).forEachIndexed { index, label ->
                val selected = when (index) {
                    0 -> true
                    1 -> filters.onlineOnly
                    else -> filters.selectedInterest.isNotBlank()
                }
                Surface(Modifier.weight(1f).height(34.dp).clickable {
                    when (index) {
                        0 -> showFilters = true
                        1 -> viewModel.setFilters(filters.copy(onlineOnly = !filters.onlineOnly))
                        else -> {
                            val interest = me?.interests?.firstOrNull().orEmpty()
                            if (interest.isNotBlank()) viewModel.setFilters(filters.copy(selectedInterest = if (filters.selectedInterest == interest) "" else interest))
                            else showFilters = true
                        }
                    }
                }, shape = RoundedCornerShape(20.dp), color = if (selected) Color(0xFFF02B6B) else Color(0xFF241D2B), border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3042))) {
                    Box(contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1) }
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp)) {

        val maxWidthPx = constraints.maxWidth
        
        if (me?.isPaused == true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.ui_your_profile_is_paused), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.ui_nobody_can_see_you_while_you_are_on_snooze), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.setPaused(false) }) { Text(stringResource(R.string.ui_resume)) }
            }
        } else if (profile == null && discoveryError != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Text(
                    stringResource(com.example.R.string.ui_could_not_load_profiles),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(discoveryError.orEmpty(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { viewModel.retryDiscovery() }) { Text(stringResource(R.string.ui_retry)) }
            }
        } else if (profile == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Text(
                    if (filters.isActive) stringResource(com.example.R.string.ui_no_one_matches_filters) else stringResource(com.example.R.string.ui_no_one_nearby_expand_search),
                    textAlign = TextAlign.Center
                )
                if (filters.isActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.setFilters(DiscoverFilters()) }) { Text(stringResource(R.string.ui_clear_filters)) }
                }
            }
        } else {
            key(profile!!.id, swipeKey) {
            ProfileCard(
                profile = profile!!,
                liked = profile!!.id in likedIds,
                compatibility = calculateCompatibility(me, profile!!),
                distanceText = placeAndDistance(profile!!, formatDistance(viewModel.distanceTo(me, profile!!)), stringResource(R.string.distance_unknown), stringResource(R.string.location_unknown)),
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
                onBoost = { viewModel.boostProfile() },
                superLikesLeft = if (isPremium) -1 else (me?.superLikesLeft() ?: 0),
                autoAdvance = true,
                onPass = {
                    swipeKey++
                    viewModel.passProfile()
                },
                swipeThreshold = maxWidthPx / 3f
            )
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
    compatibility: Int = 0,
    onSuperLike: () -> Unit = {},
    onNudge: () -> Unit = {},
    onBoost: () -> Unit = {},
    superLikesLeft: Int = 0,
    autoAdvance: Boolean = false,
    liked: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showMore by remember(profile.id) { mutableStateOf(false) }

    // Key to reset animation when profile changes
    LaunchedEffect(profile.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    // Auto-advance after five seconds; pause the timer while the user is dragging the card.
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
            val avatar = profile.avatarUrl()
            if (profile.showPhotosToOthers && avatar != null) {
                SnugImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!hasProfilePic && (profile.coverBlurred || mainPhoto?.isBlurred == true)) Modifier.blur(20.dp) else Modifier),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).zIndex(2f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { showMore = true },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF303A46).copy(alpha = .94f), contentColor = Color.White)
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.more_profile_options), modifier = Modifier.size(26.dp))
                }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.view_profile)) }, onClick = { showMore = false; onOpenProfile() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.boost_profile)) }, onClick = { showMore = false; onBoost() })
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .zIndex(2f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (compatibility > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFF02B6B)
                    ) {
                        Text(
                            stringResource(R.string.match_percent, compatibility),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
                if (profile.isBoosted || profile.isFeatured) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            "⚡ Boosted",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .zIndex(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile() }) {
                    Text(
                        text = if (profile.showAgeToOthers && profile.birthDate != null) "${profile.displayName}, ${calculateAge(profile.birthDate)}" else profile.displayName.ifBlank { "SNUG member" },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (profile.showLocationToOthers && distanceText != "Location unknown") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = stringResource(R.string.ui_location),
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
                }
                if (profile.showPresenceToOthers) {
                    val isOnline = profile.isOnlineNow()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF4CAF50) else Color.LightGray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isOnline) stringResource(R.string.online) else stringResource(R.string.offline),
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilledTonalIconButton(
                        onClick = onPass,
                        modifier = Modifier.size(44.dp).testTag("pass_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.pass), modifier = Modifier.size(26.dp))
                    }
                    
                    OutlinedIconButton(
                        onClick = onOpenProfile,
                        modifier = Modifier.size(44.dp).testTag("view_profile_button"),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.view_full_profile), modifier = Modifier.size(24.dp))
                    }
                    
                    BadgedBox(badge = { Badge { Text(if (superLikesLeft < 0) "\u221E" else superLikesLeft.toString()) } }) {
                        FilledTonalIconButton(
                            onClick = onSuperLike,
                            modifier = Modifier.size(44.dp).testTag("super_like_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF1E88E5),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.super_like), modifier = Modifier.size(24.dp))
                        }
                    }

                    OutlinedIconButton(
                        onClick = onNudge,
                        modifier = Modifier.size(44.dp).testTag("nudge_button"),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("\uD83D\uDC4B", fontSize = 22.sp)
                    }

                    FilledTonalIconButton(
                        onClick = onBoost,
                        modifier = Modifier.size(44.dp).testTag("boost_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF303A46).copy(alpha = .94f),
                            contentColor = Color(0xFFFFC14D)
                        )
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = stringResource(R.string.boost_profile), modifier = Modifier.size(25.dp))
                    }

                    FilledIconButton(
                        onClick = onLike,
                        modifier = Modifier.size(44.dp).testTag("like_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.like), modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RadarScreen(viewModel: SnugViewModel, onOpenProfile: (String) -> Unit = {}) {
    val nearby by viewModel.discoveryProfiles.collectAsState()
    val worldwide by viewModel.worldwideProfiles.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF100D17)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.new_near_you), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.new_near_you_description), color = Color(0xFFD8CADD), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (nearby.isEmpty()) Text(stringResource(R.string.no_new_nearby), color = Color(0xFFD8CADD))
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(nearby, key = { "near_${it.id}" }) { person ->
                RadarItem(person, placeAndDistance(person, formatDistance(viewModel.distanceTo(me, person)), stringResource(R.string.distance_unknown), stringResource(R.string.location_unknown)), Modifier.width(150.dp).height(205.dp)) { onOpenProfile(person.id) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.close_matches_worldwide), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.worldwide_description), color = Color(0xFFD8CADD), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        val nearbyIds = nearby.map { it.id }.toSet()
        val farMatches = worldwide.filter { it.id !in nearbyIds }.take(20)
        if (farMatches.isEmpty()) Text(stringResource(R.string.worldwide_empty), color = Color(0xFFD8CADD))
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(farMatches, key = { "world_${it.id}" }) { person ->
                Box {
                    RadarItem(person, placeAndDistance(person, formatDistance(viewModel.distanceTo(me, person)), stringResource(R.string.distance_unknown), stringResource(R.string.location_unknown)), Modifier.width(150.dp).height(205.dp)) { onOpenProfile(person.id) }
                    Surface(Modifier.align(Alignment.TopStart).padding(7.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFF02B6B)) {
                        Text(stringResource(com.example.R.string.compat_match, calculateCompatibility(me, person)), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineScreen(viewModel: SnugViewModel, onOpenProfile: (String) -> Unit = {}) {
    val likedYou by viewModel.likesYou.collectAsState()
    val nearby by viewModel.nearbyProfiles.collectAsState()
    val newProfiles by viewModel.discoveryProfiles.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    val onlineLiked = likedYou.filter { it.showPresenceToOthers && it.isOnlineNow(now) }
    val onlineNearby = nearby.filter { it.showLocationToOthers && it.showPresenceToOthers && it.isOnlineNow(now) }
        .sortedBy { viewModel.distanceTo(me, it) ?: Double.MAX_VALUE }
    val onlineNew = newProfiles.filter { it.showPresenceToOthers && it.isOnlineNow(now) }
    Column(Modifier.fillMaxSize().background(Color(0xFF100D17)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.online_now), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.online_subtitle), color = Color(0xFFD8CADD), style = MaterialTheme.typography.bodySmall)
        OnlineSection(stringResource(R.string.who_liked_you), onlineLiked, stringResource(R.string.no_liked_online), onOpenProfile)
        OnlineSection(stringResource(R.string.online_near_you), onlineNearby, stringResource(R.string.no_one_nearby_online), onOpenProfile)
        OnlineSection(stringResource(R.string.new_and_online), onlineNew, stringResource(R.string.no_new_online), onOpenProfile)
    }
}

@Composable
private fun OnlineSection(title: String, people: List<UserProfile>, emptyText: String, onOpenProfile: (String) -> Unit) {
    Spacer(Modifier.height(22.dp))
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    if (people.isEmpty()) Text(emptyText, color = Color(0xFFD8CADD), fontSize = 13.sp)
    else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(people, key = { "online_${title}_${it.id}" }) { person -> OnlineAvatar(person) { onOpenProfile(person.id) } }
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
fun RadarItem(profile: UserProfile, distanceText: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.clickable { onClick() },
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
                    .then(if (!hasProfilePic && (profile.coverBlurred || photo?.isBlurred == true)) Modifier.blur(10.dp) else Modifier),
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
                if (profile.showPresenceToOthers) {
                    PresenceBadge(profile, Color.White.copy(alpha = 0.9f), compact = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = stringResource(R.string.ui_location),
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
        title = { Text(stringResource(com.example.R.string.match_celebration) + " \uD83C\uDF89", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                SnugImage(
                    model = other.avatarUrl(),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(com.example.R.string.match_mutual_likes, other.displayName), textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onSayHi(other.id) }) { Text(stringResource(R.string.ui_say_hi)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_keep_swiping)) } }
    )
}
