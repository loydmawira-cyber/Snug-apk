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
import com.example.data.model.avatarUrl
import com.example.ui.Screen
import com.example.ui.viewmodel.SnugViewModel

@Composable
fun MatchesScreen(viewModel: SnugViewModel, navController: NavController) {
    val matches by viewModel.matches.collectAsState()
    val likesYou by viewModel.likesYou.collectAsState()

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
                    MatchGridItem(
                        match = match,
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
fun MatchGridItem(match: Match, onChat: () -> Unit = {}, onClick: () -> Unit) {
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
    // Show every match here so you can start a chat even before anyone has sent a message
    val messagesOnly = matches

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Messages",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        if (messagesOnly.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matches yet. Like someone on Discover to start chatting!", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messagesOnly) { match ->
                    MatchRow(
                        match = match,
                        onAvatarClick = {
                            match.otherUser?.id?.takeIf { it.isNotBlank() }?.let { navController.navigate(Screen.UserDetail.createRoute(it)) }
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
fun MatchRow(match: Match, onAvatarClick: () -> Unit = {}, onClick: () -> Unit) {
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
        if (match.lastMessageAt != null) {
            Text(
                text = relativeTime(match.lastMessageAt),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
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

    Scaffold(
        topBar = {
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
                        Text(other?.displayName ?: "Chat")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    var confirm by remember { mutableStateOf<String?>(null) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Unmatch") }, onClick = { menuOpen = false; confirm = "unmatch" })
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
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
              Column {
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
                        placeholder = { Text("Type a message...") },
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
                                viewModel.sendMessage(matchId, text)
                                text = ""
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
                reverseLayout = false // In a real app, reverseLayout = true and list is reversed
            ) {
                items(messages) { message ->
                    ChatBubble(
                        message,
                        isMe = message.senderId == viewModel.currentUserId,
                        onReact = { viewModel.setReaction(message.id, it) }
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
fun ChatBubble(message: ChatMessage, isMe: Boolean, onReact: (String) -> Unit = {}) {
    var menuOpen by remember { mutableStateOf(false) }
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
                    if (message.imageUrl != null) {
                        SnugImage(
                            model = message.imageUrl,
                            contentDescription = "Photo",
                            modifier = Modifier.size(width = 220.dp, height = 260.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                text = "${user.displayName}, ${com.example.data.util.calculateAge(user.birthDate)}",
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
