package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Image
import com.example.data.model.icebreakers
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.components.SnugImage
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.ReportReasons
import com.example.data.model.ScamGuard
import com.example.data.model.avatarUrl
import com.example.ui.Screen
import com.example.ui.viewmodel.SnugViewModel

import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.example.data.model.calculateCompatibility

@Composable
fun MatchesScreen(viewModel: SnugViewModel, navController: NavController) {
    val matches by viewModel.matches.collectAsState()
    val likesYou by viewModel.likesYou.collectAsState()
    val me by viewModel.currentUserProfile.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (likesYou.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.Likes.route) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${likesYou.size} ${if (likesYou.size == 1) "person likes" else "people like"} you \u2764\uFE0F",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("See who", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            "New Matches",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matches yet. Keep swiping!", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(matches) { match ->
                    val other = match.otherUser
                    val comp = if (other != null) calculateCompatibility(me, other) else null
                    MatchGridItem(
                        match = match,
                        compatibility = comp,
                        onChat = { navController.navigate(Screen.Chat.createRoute(match.id)) }
                    ) {
                        match.otherUser?.id?.takeIf { it.isNotBlank() }?.let { navController.navigate(Screen.UserDetail.createRoute(it)) }
                            ?: navController.navigate(Screen.Chat.createRoute(match.id))
                    }
                }
            }
        }
    }
}

@Composable
fun MatchGridItem(
    match: Match,
    compatibility: Int? = null,
    onChat: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.aspectRatio(1f).clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            SnugImage(
                model = match.otherUser?.avatarUrl(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
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
            if (compatibility != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Text(
                        "\u2728 $compatibility% Match",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = match.otherUser?.displayName ?: "Someone",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            )
            IconButton(
                onClick = onChat,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            ) {
                Icon(Icons.Default.Chat, contentDescription = "Message", tint = Color.White)
            }
        }
    }
}

@Composable
fun MessagesScreen(viewModel: SnugViewModel, navController: NavController) {
    val matches by viewModel.matches.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Active, 1: Archived
    var searchQuery by remember { mutableStateOf("") }

    val activeMatches = matches.filter { !it.isArchived }
    val archivedMatches = matches.filter { it.isArchived }
    val currentList = if (selectedTab == 0) activeMatches else archivedMatches
    val filteredList = if (searchQuery.isBlank()) currentList else currentList.filter {
        (it.otherUser?.displayName ?: "").contains(searchQuery, ignoreCase = true) ||
            it.lastMessage.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Messages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (archivedMatches.isNotEmpty() || selectedTab == 1) {
                AssistChip(
                    onClick = { selectedTab = if (selectedTab == 0) 1 else 0 },
                    label = { Text(if (selectedTab == 0) "Archived (${archivedMatches.size})" else "Active Chats") },
                    leadingIcon = {
                        Icon(
                            if (selectedTab == 0) Icons.Default.Archive else Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar for Conversations
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search conversations...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (selectedTab == 1) "No archived conversations."
                    else if (searchQuery.isNotEmpty()) "No matching conversations found."
                    else "No matches yet. Like someone on Discover to start chatting!",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredList, key = { it.id }) { match ->
                    MatchRow(
                        match = match,
                        onAvatarClick = {
                            match.otherUser?.id?.takeIf { it.isNotBlank() }?.let { navController.navigate(Screen.UserDetail.createRoute(it)) }
                        },
                        onToggleArchive = {
                            viewModel.setArchived(match.id, !match.isArchived)
                        }
                    ) { navController.navigate(Screen.Chat.createRoute(match.id)) }
                }
            }
        }
    }
}

@Composable
fun MatchAvatar(match: Match, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        SnugImage(
            model = match.otherUser?.avatarUrl(),
            contentDescription = null,
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Text(
            text = match.otherUser?.displayName ?: "Someone",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MatchRow(
    match: Match,
    onAvatarClick: () -> Unit = {},
    onToggleArchive: () -> Unit = {},
    onClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SnugImage(
            model = match.otherUser?.avatarUrl(),
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable { onAvatarClick() },
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.otherUser?.displayName ?: "Someone",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = match.lastMessage.ifEmpty { "New match! Say hello 👋" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (match.lastMessageAt != null) {
                Text(
                    text = relativeTime(match.lastMessageAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (match.isArchived) "Unarchive" else "Archive") },
                        leadingIcon = {
                            Icon(if (match.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onToggleArchive()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(matchId: String, viewModel: SnugViewModel, onBack: () -> Unit, onOpenProfile: (String) -> Unit = {}) {
    val allMatches by viewModel.matches.collectAsState()
    val other = allMatches.firstOrNull { it.id == matchId }?.otherUser
    val messages by viewModel.observeMessages(matchId).collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var scamWarning by remember { mutableStateOf<String?>(null) }
    val emailGate by viewModel.emailGate.collectAsState()
    var showReport by remember { mutableStateOf(false) }
    var reportingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var disappearingMode by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

    val me by viewModel.currentUserProfile.collectAsState()
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.sendPhoto(matchId, it) } }
    )
    val otherId = other?.id
    val otherTyping by remember(matchId, otherId) {
        if (otherId.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(false)
        else viewModel.observeTyping(matchId, otherId)
    }.collectAsState(initial = false)

    // Send my typing status (refreshed every 5 s while typing, cleared after 3 s of silence)
    var lastTypingSent by remember { mutableStateOf(0L) }
    LaunchedEffect(text) {
        if (text.isBlank()) {
            if (lastTypingSent != 0L) {
                lastTypingSent = 0L
                viewModel.setTyping(matchId, false)
            }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastTypingSent > 5000L) {
                lastTypingSent = now
                viewModel.setTyping(matchId, true)
            }
            kotlinx.coroutines.delay(3000)
            lastTypingSent = 0L
            viewModel.setTyping(matchId, false)
        }
    }
    DisposableEffect(matchId) {
        onDispose { viewModel.setTyping(matchId, false) }
    }

    // Tell the sender I have seen their messages while this chat is open
    LaunchedEffect(messages) {
        viewModel.markMessagesSeen(messages)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    val lastSeenId = messages.lastOrNull { it.senderId == viewModel.currentUserId && it.readAt != null }?.id
    val pinnedMessage = messages.firstOrNull { it.isPinned }

    val displayedMessages = if (searchQuery.isBlank()) messages else messages.filter {
        it.text.contains(searchQuery, ignoreCase = true)
    }

    if (emailGate) {
        EmailGateDialog(
            onDismiss = { viewModel.dismissEmailGate() },
            onVerified = { viewModel.dismissEmailGate() }
        )
    }
    scamWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { scamWarning = null },
            title = { Text("Stay safe") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = {
                    scamWarning = null
                    val replyText = replyingTo?.text ?: ""
                    val replySender = if (replyingTo != null) {
                        if (replyingTo?.senderId == viewModel.currentUserId) "You" else other?.displayName ?: "Them"
                    } else ""
                    if (viewModel.sendMessage(matchId, text, replyToText = replyText, replyToSender = replySender)) {
                        text = ""
                        replyingTo = null
                    }
                }) { Text("Send anyway") }
            },
            dismissButton = { TextButton(onClick = { scamWarning = null }) { Text("Edit message") } }
        )
    }
    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Report ${other?.displayName ?: "user"}") },
            text = {
                Column {
                    ReportReasons.all.forEach { reason ->
                        TextButton(
                            onClick = {
                                showReport = false
                                other?.id?.takeIf { it.isNotBlank() }?.let { viewModel.reportUser(it, reason) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(reason) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showReport = false }) { Text("Cancel") } }
        )
    }

    reportingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { reportingMessage = null },
            title = { Text("Report this message") },
            text = {
                Column {
                    ReportReasons.all.forEach { reason ->
                        TextButton(
                            onClick = {
                                reportingMessage = null
                                viewModel.reportMessage(target, reason)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(reason) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { reportingMessage = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { other?.id?.takeIf { it.isNotBlank() }?.let(onOpenProfile) }
                        ) {
                            SnugImage(
                                model = other?.avatarUrl(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(other?.displayName ?: "Chat", style = MaterialTheme.typography.titleMedium)
                                if (disappearingMode) {
                                    Text("Disappearing messages on", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }
                        var menuOpen by remember { mutableStateOf(false) }
                        var confirm by remember { mutableStateOf<String?>(null) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (disappearingMode) "Disappearing messages: On" else "Disappearing messages: Off") },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                onClick = {
                                    disappearingMode = !disappearingMode
                                    menuOpen = false
                                }
                            )
                            DropdownMenuItem(text = { Text("Unmatch") }, onClick = { menuOpen = false; confirm = "unmatch" })
                            DropdownMenuItem(text = { Text("Report") }, onClick = { menuOpen = false; showReport = true })
                            DropdownMenuItem(text = { Text("Block") }, onClick = { menuOpen = false; confirm = "block" })
                        }
                        confirm?.let { action ->
                            AlertDialog(
                                onDismissRequest = { confirm = null },
                                title = { Text(if (action == "block") "Block ${other?.displayName ?: "user"}?" else "Unmatch?") },
                                text = {
                                    Text(
                                        if (action == "block") "You will no longer see each other and this chat will be removed."
                                        else "This removes the match and the chat."
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirm = null
                                        if (action == "block") {
                                            other?.id?.takeIf { it.isNotBlank() }?.let { viewModel.blockUser(it) }
                                        } else {
                                            viewModel.unmatch(matchId)
                                        }
                                        onBack()
                                    }) { Text(if (action == "block") "Block" else "Unmatch") }
                                },
                                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
                            )
                        }
                    }
                )

                if (isSearching) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search messages in this chat...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (pinnedMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pinned: ${pinnedMessage.text.ifEmpty { "Photo" }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.togglePinMessage(pinnedMessage.id, false) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Unpin", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
              Column {
                if (messages.size < 5) {
                    Text(
                        "Stay safe: never send money or share bank details.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 12.dp, end = 12.dp)
                    )
                }
                if (replyingTo != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(32.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (replyingTo!!.senderId == viewModel.currentUserId) "Replying to yourself" else "Replying to ${other?.displayName ?: "Them"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    replyingTo!!.text.ifBlank { "Photo" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (messages.isEmpty() && other != null) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(icebreakers(me, other)) { idea ->
                            AssistChip(onClick = { text = idea }, label = { Text(idea, maxLines = 1) })
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.Image, contentDescription = "Send photo")
                    }
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (replyingTo != null) "Type your reply..." else "Type a message...") },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                val warning = ScamGuard.warningFor(text)
                                if (warning != null) {
                                    scamWarning = warning
                                } else {
                                    val replyText = replyingTo?.text ?: ""
                                    val replySender = if (replyingTo != null) {
                                        if (replyingTo?.senderId == viewModel.currentUserId) "You" else other?.displayName ?: "Them"
                                    } else ""
                                    if (viewModel.sendMessage(matchId, text, replyToText = replyText, replyToSender = replySender)) {
                                        text = ""
                                        replyingTo = null
                                    }
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
              }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = false
            ) {
                items(displayedMessages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        isMe = message.senderId == viewModel.currentUserId,
                        onReact = { viewModel.setReaction(message.id, it) },
                        onReply = { replyingTo = message },
                        onTogglePin = { viewModel.togglePinMessage(message.id, !message.isPinned) },
                        onReport = if (message.senderId != viewModel.currentUserId) {
                            { reportingMessage = message }
                        } else null
                    )
                    if (message.id == lastSeenId) {
                        Text(
                            "Seen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            if (otherTyping) {
                Text(
                    "${other?.displayName ?: "They"} is typing...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    onReact: (String) -> Unit = {},
    onReply: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onReport: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                modifier = Modifier.pointerInput(message.id) {
                    detectTapGestures(onLongPress = { menuOpen = true })
                },
                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                )
            ) {
                Column {
                    if (message.replyToText.isNotBlank()) {
                        Surface(
                            color = if (isMe) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(24.dp)
                                        .background(if (isMe) Color.White else MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    if (message.replyToSender.isNotBlank()) {
                                        Text(
                                            message.replyToSender,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMe) Color.White else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        message.replyToText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        color = if (isMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    if (message.imageUrl != null) {
                        SnugImage(
                            model = message.imageUrl,
                            contentDescription = "Photo",
                            modifier = Modifier.size(width = 220.dp, height = 260.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = message.text,
                                modifier = Modifier.weight(1f, fill = false),
                                color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (message.isPinned) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (message.reaction.isNotBlank()) {
                Text(message.reaction, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDC4D", "\uD83D\uDE2E", "\uD83D\uDE22").forEach { emoji ->
                        TextButton(onClick = {
                            menuOpen = false
                            onReact(if (message.reaction == emoji) "" else emoji)
                        }) { Text(emoji, fontSize = 20.sp) }
                    }
                }
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onReply()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (message.isPinned) "Unpin message" else "Pin message") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onTogglePin()
                    }
                )
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy text") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            clipboardManager.setText(AnnotatedString(message.text))
                        }
                    )
                }
                if (onReport != null) {
                    DropdownMenuItem(
                        text = { Text("Report message") },
                        onClick = {
                            menuOpen = false
                            onReport()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesYouScreen(viewModel: SnugViewModel, navController: NavController) {
    val likes by viewModel.likesYou.collectAsState()
    val celebration by viewModel.matchCelebration.collectAsState()
    MatchCelebrationDialog(
        other = celebration,
        onDismiss = { viewModel.dismissCelebration() },
        onSayHi = { id -> viewModel.openChat(id) { chatId -> navController.navigate(Screen.Chat.createRoute(chatId)) } }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liked You") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (likes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No new likes right now.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(likes) { user ->
                    Card(
                        modifier = Modifier.aspectRatio(0.8f).clickable {
                            navController.navigate(Screen.UserDetail.createRoute(user.id))
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box {
                            SnugImage(
                                model = user.avatarUrl(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                            startY = 150f
                                        )
                                    )
                            )
                            Text(
                                text = if (user.showAgeToOthers && user.birthDate != null) {
                                    "${user.displayName}, ${com.example.data.util.calculateAge(user.birthDate)}"
                                } else {
                                    user.displayName.ifBlank { "SNUG member" }
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                            )
                            FilledIconButton(
                                onClick = { viewModel.likeProfile(user.id) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = "Like back", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "now", "5m", "3h", "2d" or a short date for older messages. */
fun relativeTime(ts: com.google.firebase.Timestamp?): String {
    val then = ts?.toDate()?.time ?: return ""
    val mins = ((System.currentTimeMillis() - then) / 60_000L).coerceAtLeast(0)
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 60 * 24 -> "${mins / 60}h"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(then))
    }
}
