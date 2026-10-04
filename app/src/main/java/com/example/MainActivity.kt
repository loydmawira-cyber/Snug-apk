package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.SnugImage
import com.example.data.security.PinManager
import com.example.ui.Screen
import com.example.ui.components.EmailSignInFlow
import com.example.ui.components.PinUnlockScreen
import com.example.ui.theme.SNUGTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val preferences = remember {
                context.getSharedPreferences("snug_preferences", MODE_PRIVATE)
            }
            val systemDark = isSystemInDarkTheme()
            var darkTheme by remember {
                mutableStateOf(
                    preferences.getBoolean("dark_theme", systemDark)
                )
            }
            
            SNUGTheme(darkTheme = darkTheme) {
                AppContent(
                    isDarkTheme = darkTheme,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                        preferences.edit().putBoolean("dark_theme", darkTheme).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun AppContent(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val pinManager = remember { PinManager(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isUnlocked by remember { mutableStateOf(!pinManager.isPinEnabled()) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser == null) {
                isUnlocked = false
            } else if (!pinManager.isPinEnabled()) {
                isUnlocked = true
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        SignInScreen()
    } else if (pinManager.isPinEnabled() && !isUnlocked) {
        PinUnlockScreen(
            onUnlocked = { isUnlocked = true },
            onSignOut = { isUnlocked = false }
        )
    } else {
        MainNavigation(isDarkTheme, onToggleTheme)
    }
}

@Composable
fun SignInScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_icon_1791026447003),
                contentDescription = "SNUG Logo",
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "SNUG",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Find your perfect cozy match.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            var authError by remember { mutableStateOf<String?>(null) }
            if (authError != null) {
                Text(
                    text = authError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            EmailSignInFlow(
                onAuthSuccess = { authError = null },
                onAuthError = { authError = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Keyed by user id so each account gets its own fresh ViewModel (no leftover data from the previous user)
    val viewModel: com.example.ui.viewmodel.SnugViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = Firebase.auth.currentUser?.uid ?: "signed_out"
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val appContext = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) viewModel.refreshLocation() }
    val uiMessage by viewModel.uiMessage.collectAsState()
    val myProfile by viewModel.currentUserProfile.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.read }
    var showNotifications by remember { mutableStateOf(false) }
    val myAvatar = myProfile?.profilePhoto?.takeIf { it.isNotBlank() } ?: myProfile?.photos?.firstOrNull()?.url

    val hasProfile = myProfile != null

    // Online while the app is on screen, offline when it goes to the background
    val activity = appContext as? androidx.activity.ComponentActivity
    DisposableEffect(activity, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.goOnline()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.goOffline()
                else -> {}
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            viewModel.stopPresence()
        }
    }
    LaunchedEffect(hasProfile) {
        if (hasProfile) {
            val granted = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.refreshLocation()
            else locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val items = listOf(
        Screen.Discover,
        Screen.Radar,
        Screen.Matches,
        Screen.Messages,
        Screen.Profile
    )

    if (showNotifications) {
        com.example.ui.screens.NotificationsSheet(
            notifications = notifications,
            onOpen = { n ->
                showNotifications = false
                viewModel.markNotificationsRead()
                when {
                    (n.type == "match" || n.type == "message") && n.matchId.isNotBlank() ->
                        navController.navigate(Screen.Chat.createRoute(n.matchId))
                    n.fromUserId.isNotBlank() ->
                        navController.navigate(Screen.UserDetail.createRoute(n.fromUserId))
                }
            },
            onDismiss = {
                showNotifications = false
                viewModel.markNotificationsRead()
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (items.any { it.route == currentDestination?.route }) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_icon_1791026447003),
                                contentDescription = "SNUG Logo",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "SNUG",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNotifications = true }) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(if (unreadCount > 9) "9+" else unreadCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        }
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                        IconButton(onClick = {
                            navController.navigate(Screen.Profile.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }) {
                            if (myAvatar != null) {
                                SnugImage(
                                    model = myAvatar,
                                    contentDescription = "My profile",
                                    modifier = Modifier.size(34.dp).clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = "My profile")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (items.any { it.route == currentDestination?.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Discover.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Discover.route) {
                com.example.ui.screens.DiscoverScreen(
                    viewModel,
                    onOpenProfile = { navController.navigate(Screen.UserDetail.createRoute(it)) },
                    onOpenChat = { navController.navigate(Screen.Chat.createRoute(it)) },
                    onSetupProfile = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Radar.route) {
                com.example.ui.screens.RadarScreen(viewModel, onOpenProfile = { navController.navigate(Screen.UserDetail.createRoute(it)) })
            }
            composable(Screen.Matches.route) { com.example.ui.screens.MatchesScreen(viewModel, navController) }
            composable(Screen.Likes.route) { com.example.ui.screens.LikesYouScreen(viewModel, navController) }
            composable(Screen.Messages.route) { com.example.ui.screens.MessagesScreen(viewModel, navController) }
            composable(Screen.Profile.route) { com.example.ui.screens.ProfileScreen(viewModel) }
            composable(Screen.Chat.route) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
                com.example.ui.screens.ChatScreen(
                    matchId, viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(Screen.UserDetail.createRoute(it)) }
                )
            }
            composable(Screen.UserDetail.route) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                com.example.ui.screens.UserProfileScreen(userId, viewModel, navController)
            }
        }
    }
}

// Placeholder Screens removed as they are now implemented in separate files
