package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.data.local.entity.toTrack
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import com.android.pulse.ui.components.GlassHeader
import com.android.pulse.ui.components.TrackItem
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun HomeView(
    audioPlayerManager: AudioPlayerManager, 
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val recentHistory by audioPlayerManager.database.historyDao().getRecentHistory().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        if (tracks.isEmpty()) {
            isLoading = true
            tracks = InnerTubeRepository.searchMusic("Lofi")
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column {
            GlassHeader(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                isSearchActive = isSearchActive,
                onSearchAction = {
                    scope.launch {
                        isLoading = true
                        tracks = InnerTubeRepository.searchMusic(searchQuery)
                        isLoading = false
                        isSearchActive = false
                    }
                },
                onSearchActivate = { isSearchActive = true },
                onSearchCancel = { 
                    isSearchActive = false
                    searchQuery = ""
                },
                onHistoryClick = onHistoryClick
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (recentHistory.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "Listen Now",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    items(recentHistory.take(10)) { entity ->
                                        val track = entity.toTrack()
                                        ListenNowCard(track) {
                                            audioPlayerManager.playTrack(track, recentHistory.map { it.toTrack() })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        Text(
                            text = "Quick Picks",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }

                    items(tracks) { track ->
                        TrackItem(track) {
                            audioPlayerManager.playTrack(track, tracks)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListenNowCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
