package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
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
import com.example.ui.components.SnugImage
import com.example.data.model.UserProfile
import com.example.data.model.avatarUrl
import com.example.data.util.calculateAge
import com.example.data.util.formatDistance
import com.example.ui.viewmodel.SnugViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DiscoverScreen(viewModel: SnugViewModel, onOpenProfile: (String) -> Unit = {}) {
    val profile by viewModel.currentProfile.collectAsState()
    val profiles by viewModel.discoveryProfiles.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()
    
    android.util.Log.d("DiscoverScreen", "Recomposing with profile: ${profile?.displayName}")
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val maxWidthPx = constraints.maxWidth
        
        if (profile == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Text("No one new nearby. Try expanding your search!", textAlign = TextAlign.Center)
            }
        } else {
            ProfileCard(
                profile = profile!!,
                distanceText = formatDistance(viewModel.distanceTo(me, profile!!)),
                onOpenProfile = { onOpenProfile(profile!!.id) },
                onLike = { viewModel.likeProfile(profile!!.id) },
                onPass = { viewModel.passProfile() },
                swipeThreshold = maxWidthPx / 3f
            )
        }
    }
}

@Composable
fun ProfileCard(profile: UserProfile, distanceText: String, onOpenProfile: () -> Unit, onLike: () -> Unit, onPass: () -> Unit, swipeThreshold: Float) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    
    // Key to reset animation when profile changes
    LaunchedEffect(profile.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
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
                    onDragEnd = {
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
                Text(
                    text = distanceText,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = profile.bio,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilledTonalIconButton(
                        onClick = onPass,
                        modifier = Modifier.size(64.dp).testTag("pass_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Pass", modifier = Modifier.size(32.dp))
                    }
                    
                    FilledTonalIconButton(
                        onClick = { /* Super like logic */ },
                        modifier = Modifier.size(64.dp).testTag("super_like_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.Cyan
                        )
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Super Spark", modifier = Modifier.size(32.dp))
                    }
                    
                    FilledIconButton(
                        onClick = onLike,
                        modifier = Modifier.size(64.dp).testTag("like_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Like", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RadarScreen(viewModel: SnugViewModel, onOpenProfile: (String) -> Unit = {}) {
    val profiles by viewModel.discoveryProfiles.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Nearby", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(profiles) { profile ->
                RadarItem(profile, formatDistance(viewModel.distanceTo(me, profile))) { onOpenProfile(profile.id) }
            }
        }
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
                Text(distanceText, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
    }
}

