package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.model.isOnlineNow
import com.example.data.model.lastSeenText
import kotlinx.coroutines.delay

/** Green dot + "Online", or grey dot + "Offline" (optionally with the last seen time). */
@Composable
fun PresenceBadge(
    profile: UserProfile,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showLastSeen: Boolean = false,
    compact: Boolean = false
) {
    // Re-check every 30 seconds so someone who closed the app turns offline on its own
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val online = profile.isOnlineNow(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(if (compact) 7.dp else 9.dp)
                .clip(CircleShape)
                .background(if (online) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
        )
        Spacer(modifier = Modifier.width(if (compact) 4.dp else 6.dp))
        Text(
            text = if (online) "Online" else if (showLastSeen) profile.lastSeenText(now) else "Offline",
            color = textColor,
            fontSize = if (compact) 10.sp else 12.sp
        )
    }
}
