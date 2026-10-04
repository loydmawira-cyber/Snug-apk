package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.components.SnugImage
import com.example.data.model.UserProfile
import com.example.data.model.avatarUrl
import com.example.data.model.basics
import com.example.data.model.placeAndDistance
import com.example.data.util.calculateAge
import com.example.data.util.formatDistance
import com.example.ui.Screen
import com.example.ui.viewmodel.SnugViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileScreen(userId: String, viewModel: SnugViewModel, navController: NavController) {
    val me by viewModel.currentUserProfile.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val user by produceState<UserProfile?>(initialValue = null, key1 = userId) {
        value = viewModel.loadUser(userId)
    }
    val match = matches.firstOrNull { it.userIds.contains(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.displayName ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val u = user
        if (u == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val avatar = u.avatarUrl()
                if (avatar != null) {
                    SnugImage(
                        model = avatar,
                        contentDescription = "Profile picture",
                        modifier = Modifier.size(160.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(160.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${u.displayName}, ${calculateAge(u.birthDate)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (u.isPhoneVerified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    placeAndDistance(u, formatDistance(viewModel.distanceTo(me, u))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (u.gender.isNotBlank()) {
                    Text(
                        u.gender,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (u.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(u.bio, style = MaterialTheme.typography.bodyLarge)
                }

                if (u.lookingFor.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Looking for: ${u.lookingFor}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                val basics = u.basics()
                if (basics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "About",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        basics.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }

                if (u.interests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Interests",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        u.interests.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }

                if (u.photos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Photos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    u.photos.forEach { photo ->
                        SnugImage(
                            model = photo.url,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .then(if (photo.isBlurred) Modifier.blur(20.dp) else Modifier),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (me?.id != userId) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (match != null) {
                                navController.navigate(Screen.Chat.createRoute(match.id))
                            } else {
                                viewModel.openChat(userId) { id ->
                                    navController.navigate(Screen.Chat.createRoute(id))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = CircleShape
                    ) {
                        Text("Message")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
