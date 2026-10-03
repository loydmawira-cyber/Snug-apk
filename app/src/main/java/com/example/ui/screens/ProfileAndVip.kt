package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.util.calculateAge
import com.example.ui.viewmodel.SnugViewModel

@Composable
fun ProfileScreen(viewModel: SnugViewModel) {
    val profile by viewModel.currentUserProfile.collectAsState()
    var showVipModal by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showVipModal) {
        VipUpgradeModal(onDismiss = { showVipModal = false })
    }
    
    if (showEditDialog && profile != null) {
        EditProfileDialog(
            profile = profile!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedProfile ->
                viewModel.updateUserProfile(updatedProfile)
                showEditDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = profile?.photos?.firstOrNull() ?: "https://via.placeholder.com/150",
                contentDescription = "Profile Photo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable { showEditDialog = true },
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.size(32.dp).clickable { showEditDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.padding(6.dp),
                    tint = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${profile?.displayName ?: "User"}, ${calculateAge(profile?.birthDate)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text("${if (profile?.photos?.isNotEmpty() == true) 100 else 70}% Profile Completion", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        LinearProgressIndicator(
            progress = { 0.7f },
            modifier = Modifier
                .width(200.dp)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        VipCard(onClick = { showVipModal = true })
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSection(viewModel)
    }
}

@Composable
fun VipCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Get SNUG VIP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Unlimited likes, rewinds & more", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun SettingsSection(viewModel: SnugViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Discovery Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Distance Radius: 50km", style = MaterialTheme.typography.bodyMedium)
        Slider(value = 0.5f, onValueChange = {})
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Age Range: 18 - 35", style = MaterialTheme.typography.bodyMedium)
        RangeSlider(value = 0.2f..0.6f, onValueChange = {})
        
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        
        ListItem(
            headlineContent = { Text("App Settings") },
            leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) }
        )
        ListItem(
            headlineContent = { Text("Help & Support") },
            leadingContent = { Icon(Icons.Default.Help, contentDescription = null) }
        )
        ListItem(
            headlineContent = { Text("Logout", color = Color.Red) },
            leadingContent = { Icon(Icons.Default.Logout, contentDescription = null, tint = Color.Red) },
            modifier = Modifier.clickable { /* Logout */ }
        )

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.seedData() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Seed Sample Data (Jane, Peter, Bridget, Agnes)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipUpgradeModal(onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Upgrade to VIP",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                VipBenefitItem("Unlimited Likes", "Swipe to your heart's content")
                VipBenefitItem("Unlimited Rewinds", "Oops! Bring them back")
                VipBenefitItem("5 Daily Super Sparks", "Stand out from the crowd")
                VipBenefitItem("Passport", "Match anywhere in the world")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceTier("1 Month", "$9.99")
                    PriceTier("6 Months", "$7.49/mo", isPopular = true)
                    PriceTier("12 Months", "$4.99/mo")
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape
                ) {
                    Text("START 7-DAY FREE TRIAL")
                }
                TextButton(onClick = onDismiss) {
                    Text("No thanks", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
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
fun RowScope.PriceTier(title: String, price: String, isPopular: Boolean = false) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        border = if (isPopular) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        color = if (isPopular) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPopular) {
                Text("POPULAR", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(price, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    profile: com.example.data.model.UserProfile,
    onDismiss: () -> Unit,
    onSave: (com.example.data.model.UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Edit Profile", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(profile.copy(displayName = name, bio = bio)) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
