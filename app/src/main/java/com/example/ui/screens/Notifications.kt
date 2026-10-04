package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppNotification

private fun timeAgo(millis: Long?): String {
    if (millis == null) return "now"
    val diff = System.currentTimeMillis() - millis
    val min = diff / 60000
    return when {
        min < 1 -> "now"
        min < 60 -> "${min}m"
        min < 60 * 24 -> "${min / 60}h"
        else -> "${min / (60 * 24)}d"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<AppNotification>,
    onOpen: (AppNotification) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nothing yet. Likes, matches and messages will show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(notifications) { n ->
                        val icon: ImageVector = when (n.type) {
                            "match" -> Icons.Default.Favorite
                            "message" -> Icons.Default.Chat
                            else -> Icons.Default.FavoriteBorder
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (!n.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { onOpen(n) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (n.type == "nudge") {
                                        Text("\uD83D\uDC4B", fontSize = 20.sp)
                                    } else {
                                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                n.text,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (!n.read) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                timeAgo(n.createdAt?.toDate()?.time),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
