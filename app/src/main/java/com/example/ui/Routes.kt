package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Onboarding : Screen("onboarding", "Welcome", Icons.Default.Favorite)
    object Discover : Screen("discover", "Discover", Icons.Default.Style)
    object Radar : Screen("radar", "Radar", Icons.Default.Radar)
    object Matches : Screen("matches", "Matches", Icons.Default.FavoriteBorder)
    object Messages : Screen("messages", "Messages", Icons.Default.Forum)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object Chat : Screen("chat/{matchId}", "Chat", Icons.Default.Chat) {
        fun createRoute(matchId: String) = "chat/$matchId"
    }
}
