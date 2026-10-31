package com.example

import android.Manifest
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.example.data.model.missingRequiredFields
import com.example.ui.Screen
import com.example.ui.components.EmailSignInFlow
import com.example.ui.components.PinUnlockScreen
import com.example.ui.theme.SNUGTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = newBase
            .getSharedPreferences("snug_preferences", Context.MODE_PRIVATE)
            .getString("app_language", "en") ?: "en"
        val localizedConfiguration = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        super.attachBaseContext(newBase.createConfigurationContext(localizedConfiguration))
    }

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
            var appLanguage by remember {
                mutableStateOf(preferences.getString("app_language", "en") ?: "en")
            }
            var languagePrompted by remember {
                mutableStateOf(preferences.getBoolean("language_prompted", false))
            }
            SNUGTheme(darkTheme = darkTheme) {
                AppContent(
                    isDarkTheme = darkTheme,
                    appLanguage = appLanguage,
                    showLanguagePrompt = !languagePrompted,
                    onLanguageChanged = { language ->
                        preferences.edit().putString("app_language", language).putBoolean("language_prompted", true).apply()
                        appLanguage = language
                        languagePrompted = true
                        recreate()
                    },
                    onLanguagePromptFinished = {
                        preferences.edit().putBoolean("language_prompted", true).apply()
                        languagePrompted = true
                    },
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
fun AppContent(
    isDarkTheme: Boolean,
    appLanguage: String,
    showLanguagePrompt: Boolean = false,
    onLanguageChanged: (String) -> Unit,
    onLanguagePromptFinished: () -> Unit = {},
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.example.data.security.DemoAuthManager.init(context)
    }
    val pinManager = remember { PinManager(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    val demoUid by com.example.data.security.DemoAuthManager.authStateFlow.collectAsState()
    var isUnlocked by remember { mutableStateOf(!pinManager.isPinEnabled()) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser == null && !com.example.data.security.DemoAuthManager.isDemoActive()) {
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

    val isAuthenticated = currentUser != null || demoUid != null

    if (!isAuthenticated) {
        SignInScreen(
            appLanguage = appLanguage,
            showLanguagePrompt = showLanguagePrompt,
            onLanguageChanged = onLanguageChanged,
            onLanguagePromptFinished = onLanguagePromptFinished
        )
    } else if (pinManager.isPinEnabled() && !isUnlocked) {
        PinUnlockScreen(
            onUnlocked = { isUnlocked = true },
            onSignOut = {
                com.example.data.security.DemoAuthManager.signOut(context)
                Firebase.auth.signOut()
                isUnlocked = false
            }
        )
    } else {
        MainNavigation(isDarkTheme, appLanguage, onLanguageChanged, onToggleTheme)
    }
}

@Composable
fun SignInScreen(
    appLanguage: String = "en",
    showLanguagePrompt: Boolean = false,
    onLanguageChanged: (String) -> Unit = {},
    onLanguagePromptFinished: () -> Unit = {}
) {
    var showLanguageChooser by remember(showLanguagePrompt) { mutableStateOf(showLanguagePrompt) }
    if (showLanguageChooser) {
        com.example.ui.screens.AppLanguageSheet(
            currentLanguage = appLanguage,
            onApply = onLanguageChanged,
            onDismiss = {
                showLanguageChooser = false
                onLanguagePromptFinished()
            }
        )
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showLanguageChooser = true }) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.language_settings_title))
                }
            }
            Image(
                painter = painterResource(id = R.drawable.img_app_icon_1791026447003),
                contentDescription = stringResource(R.string.ui_snug_logo),
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
                text = stringResource(R.string.ui_find_your_perfect_cozy_match),
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
                onAuthError = { authError = it.takeIf(String::isNotBlank) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(isDarkTheme: Boolean, appLanguage: String, onLanguageChanged: (String) -> Unit, onToggleTheme: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Keyed by user id so each account gets its own fresh ViewModel (no leftover data from the previous user)
    val viewModel: com.example.ui.viewmodel.SnugViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = Firebase.auth.currentUser?.uid ?: com.example.data.security.DemoAuthManager.getUid() ?: "signed_out"
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val appContext = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) viewModel.refreshLocation() }
    val uiMessage by viewModel.uiMessage.collectAsState()
    val myProfile by viewModel.currentUserProfile.collectAsState()
    val moderationVm: com.example.ui.viewmodel.ModerationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val suspension by moderationVm.mySuspension.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.read }
    var showNotifications by remember { mutableStateOf(false) }
    val myAvatar = myProfile?.profilePhoto?.takeIf { it.isNotBlank() } ?: myProfile?.photos?.firstOrNull()?.url

    val hasProfile = myProfile != null

    // Admin suspended this account: show only the suspension screen
    // (the live suspension status wins over the profile loaded at start, so a lifted suspension switches the app back on)
    if (suspension?.banned ?: (myProfile?.banned == true)) {
        com.example.ui.screens.SuspendedAppealScreen(
            vm = moderationVm,
            name = myProfile?.displayName ?: "",
            onLogout = { viewModel.goOffline { com.example.ui.components.signOut(appContext) {} } }
        )
        return
    }

    // Nothing in the app works until the email is verified and the profile is complete
    var emailOk by remember(myProfile?.id) { mutableStateOf(com.example.data.security.EmailVerifier.isVerified()) }
    val missingFields = myProfile?.missingRequiredFields() ?: emptyList()
    myProfile?.let { me ->
        if (!emailOk || missingFields.isNotEmpty()) {
            com.example.ui.screens.SetupGateScreen(
                viewModel = viewModel,
                profile = me,
                missing = missingFields,
                emailVerified = emailOk,
                onEmailVerified = { emailOk = true },
                onLogout = { viewModel.goOffline { com.example.ui.components.signOut(appContext) {} } }
            )
            return
        }
    }

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
                    n.type == "verification" -> navController.navigate(Screen.Profile.route) {
                        launchSingleTop = true
                    }
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
                                contentDescription = stringResource(R.string.ui_snug_logo),
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
                                Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.ui_notifications))
                            }
                        }
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = stringResource(R.string.ui_toggle_theme)
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
                                    contentDescription = stringResource(R.string.ui_my_profile),
                                    modifier = Modifier.size(34.dp).clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = stringResource(R.string.ui_my_profile))
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
                            label = {
                                Text(when (screen.route) {
                                    Screen.Discover.route -> stringResource(R.string.nav_discover)
                                    Screen.Radar.route -> stringResource(R.string.nav_radar)
                                    Screen.Matches.route -> stringResource(R.string.nav_matches)
                                    Screen.Messages.route -> stringResource(R.string.nav_messages)
                                    Screen.Profile.route -> stringResource(R.string.nav_profile)
                                    else -> screen.title
                                })
                            },
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
                    onNavigateTab = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
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
            composable(Screen.Online.route) {
                com.example.ui.screens.OnlineScreen(viewModel, onOpenProfile = { navController.navigate(Screen.UserDetail.createRoute(it)) })
            }
            composable(Screen.Matches.route) { com.example.ui.screens.MatchesScreen(viewModel, navController) }
            composable(Screen.Likes.route) { com.example.ui.screens.LikesYouScreen(viewModel, navController) }
            composable(Screen.Messages.route) { com.example.ui.screens.MessagesScreen(viewModel, navController) }
            composable(Screen.Profile.route) {
                com.example.ui.screens.ProfileScreen(
                    viewModel,
                    appLanguage = appLanguage,
                    onLanguageChanged = onLanguageChanged,
                    onOpenAdmin = { navController.navigate(Screen.Admin.route) }
                )
            }
            composable(Screen.Admin.route) { com.example.ui.screens.AdminDashboardScreen(viewModel, navController) }
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
