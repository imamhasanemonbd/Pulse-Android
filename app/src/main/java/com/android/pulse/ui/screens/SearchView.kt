package com.android.pulse.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.data.local.entity.PlaylistEntity
import com.android.pulse.data.local.entity.PlaylistSongCrossRef
import com.android.pulse.data.local.entity.toTrack
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import com.android.pulse.ui.components.TrackItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun SearchView(
    playerManager: AudioPlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Pulse Online, 1: Library
    var selectedCategory by remember { mutableStateOf("Songs") }
    val categories = listOf("Songs", "Videos", "Artists", "Albums", "Community playlists")
    
    // Category mapping to InnerTube params
    val categoryParams = mapOf(
        "Songs" to "Eg-KAQwIARAAGAAgACgB",
        "Videos" to "Eg-KAQwIARABGAAgACgB",
        "Artists" to "Eg-KAQwIARADGAAgACgB",
        "Albums" to "Eg-KAQwIARACGAAgACgB",
        "Community playlists" to "Eg-KAQwIARAFGAAgACgB"
    )

    val history by playerManager.database.historyDao().getRecentHistory().collectAsState(initial = emptyList())
    val likedSongs by playerManager.database.likedSongDao().getAllLikedSongs().collectAsState(initial = emptyList())
    val playlists by playerManager.database.playlistDao().getAllPlaylists().collectAsState(initial = emptyList())

    var showPlaylistSheet by remember { mutableStateOf(false) }
    var trackToSave by remember { mutableStateOf<Track?>(null) }
    var isListening by remember { mutableStateOf(false) }

    fun performSearch() {
        if (query.isBlank()) return
        scope.launch {
            isLoading = true
            if (selectedTab == 0) {
                // Online Search
                results = InnerTubeRepository.searchMusic(query, categoryParams[selectedCategory])
            } else {
                // Library Search
                results = likedSongs
                    .filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
                    .map { it.toTrack() }
            }
            isLoading = false
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spokenText != null) {
            query = spokenText
            performSearch()
        }
    }

    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        voiceLauncher.launch(intent)
    }

    fun startMusicDetection() {
        // Show "Listening" UI within Pulse
        isListening = true
        scope.launch {
            delay(3000) // Simulate detection
            isListening = false
            // Start real voice search as fallback or just simulate for now
            startVoiceSearch()
        }
    }

    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            delay(300)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    // Auto-refresh search when category or tab changes
    LaunchedEffect(selectedCategory, selectedTab) {
        if (query.isNotEmpty()) {
            performSearch()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom Search Bar
        Surface(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search songs, artists, podca...", fontSize = 16.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        performSearch()
                        keyboardController?.hide()
                    })
                )
                
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; results = emptyList() }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
                
                IconButton(onClick = { startVoiceSearch() }) {
                    Icon(Icons.Default.Mic, null)
                }
                IconButton(onClick = { startMusicDetection() }) {
                    Icon(if (isListening) Icons.Default.GraphicEq else Icons.Default.GraphicEq, null, tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Pulse Online / Library Tabs
        Row(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SearchTabItem("YT Music", selectedTab == 0) { selectedTab = 0 }
            SearchTabItem("Library", selectedTab == 1) { selectedTab = 1 }
        }

        if (isListening) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Listening for music...", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                // Category Chips
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    selectedContainerColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = null
                            )
                        }
                    }
                }

                // Best Match Card
                item {
                    BestMatchCard(
                        track = results[0], 
                        playerManager = playerManager,
                        onSave = { 
                            trackToSave = it
                            showPlaylistSheet = true
                        }
                    )
                }

                // Other Results
                items(results.drop(1)) { track ->
                    TrackItem(track) {
                        playerManager.playTrack(track, results)
                    }
                }
            }
        } else if (selectedTab == 1 && query.isNotEmpty()) {
            // Library Search empty state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching songs in your library", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Recent Searches View
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                item {
                    Text(
                        "Recent searches", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        items(history.take(5)) { entity ->
                            RecentTrackItem(entity.title, entity.thumbnailUrl) {
                                query = entity.title
                                performSearch()
                            }
                        }
                    }
                }
                
                items(history.take(10)) { entity ->
                    HistoryQueryItem(entity.title) {
                        query = entity.title
                        performSearch()
                    }
                }
            }
        }
    }

    // Playlist Selection Sheet
    if (showPlaylistSheet && trackToSave != null) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    "Add to playlist", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                
                ListItem(
                    headlineContent = { Text("New playlist") },
                    leadingContent = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.clickable { 
                        // Logic for new playlist could be added here
                        playerManager.createPlaylistAndAddTrack("My Playlist", trackToSave!!)
                        showPlaylistSheet = false
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                            modifier = Modifier.clickable {
                                playerManager.addTrackToPlaylist(playlist.playlistId, trackToSave!!, null)
                                showPlaylistSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTabItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(24.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@UnstableApi
@Composable
fun BestMatchCard(
    track: Track, 
    playerManager: AudioPlayerManager,
    onSave: (Track) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title, 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold, 
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface // FIX: Explicitly use theme color
                    )
                    Text(
                        "Song • ${track.artist}", 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant // FIX: Explicitly use variant
                    )
                }
                IconButton(onClick = { /* More options */ }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { playerManager.playTrack(track, listOf(track)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface, 
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                
                OutlinedButton(
                    onClick = { onSave(track) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    border = null,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun RecentTrackItem(title: String, thumbnail: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(120.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            title, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis, 
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HistoryQueryItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Default.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
