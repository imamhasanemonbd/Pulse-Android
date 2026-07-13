package com.android.pulse.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
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
    modifier: Modifier = Modifier,
    initialQuery: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    var query by remember { mutableStateOf(initialQuery ?: "") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Pulse Online, 1: Library
    var selectedCategory by remember { mutableStateOf("Songs") }
    val categories = listOf("Songs", "Videos", "Artists", "Albums", "Community playlists")
    
    val categoryParams = mapOf(
        "Songs" to "Eg-KAQwIARAAGAAgACgB",
        "Videos" to "Eg-KAQwIARABGAAgACgB",
        "Artists" to "Eg-KAQwIARADGAAgACgB",
        "Albums" to "Eg-KAQwIARACGAAgACgB",
        "Community playlists" to "Eg-KAQwIARAFGAAgACgB"
    )

    val likedSongs by playerManager.database.likedSongDao().getAllLikedSongs().collectAsState(initial = emptyList())

    fun performSearch() {
        if (query.isBlank()) return
        scope.launch {
            isLoading = true
            if (selectedTab == 0) {
                results = InnerTubeRepository.searchMusic(query, categoryParams[selectedCategory])
            } else {
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

    LaunchedEffect(Unit) {
        if (initialQuery != null) {
            performSearch()
        } else {
            delay(300)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
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
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                placeholder = { Text("Search songs, artists...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { 
                    performSearch()
                    keyboardController?.hide()
                }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    } else {
                        IconButton(onClick = { startVoiceSearch() }) {
                            Icon(Icons.Default.Mic, null)
                        }
                    }
                }
            )
            
            IconButton(onClick = { /* Music Detection */ }) {
                Icon(Icons.Default.GraphicEq, null)
            }
        }

        // Tabs
        Row(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SearchTabItem("YT Music", selectedTab == 0) { selectedTab = 0 }
            Spacer(modifier = Modifier.width(16.dp))
            SearchTabItem("Library", selectedTab == 1) { selectedTab = 1 }
        }

        // Filter Categories
        if (selectedTab == 0) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        }

        // Results
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Search for artists, songs or playlists", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Best Match Card (if in Songs category and has results)
                if (selectedCategory == "Songs" && selectedTab == 0) {
                    item {
                        BestMatchCard(results[0], playerManager) {
                            playerManager.playTrack(it, results)
                        }
                    }
                    items(results.drop(1)) { track ->
                        TrackItem(track) {
                            playerManager.playTrack(track, results)
                        }
                    }
                } else {
                    items(results) { track ->
                        TrackItem(track) {
                            playerManager.playTrack(track, results)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTabItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@UnstableApi
@Composable
fun BestMatchCard(track: Track, playerManager: AudioPlayerManager, onPlay: (Track) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Song • ${track.artist}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { /* Menu */ }) {
                    Icon(Icons.Default.MoreVert, null)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onPlay(track) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                
                OutlinedButton(
                    onClick = { /* Save */ },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlaylistAdd, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
