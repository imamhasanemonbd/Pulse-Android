package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import com.android.pulse.data.remote.innertube.model.Category
import com.android.pulse.data.remote.innertube.model.HomeData
import com.android.pulse.ui.components.GlassHeader
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi
import kotlin.random.Random

@UnstableApi
@Composable
fun HomeView(
    audioPlayerManager: AudioPlayerManager, 
    onHistoryClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var homeData by remember { mutableStateOf(HomeData()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    
    val currentTrack by audioPlayerManager.currentTrack.collectAsState()
    val history by audioPlayerManager.database.historyDao().getRecentHistory().collectAsState(initial = emptyList())
    
    var lastLoadedTrackId by remember { mutableStateOf<String?>(null) }

    fun refreshHome(category: Category? = null, relatedVideoId: String? = null) {
        scope.launch {
            isLoading = true
            
            // Personalization logic: Mix current track and random history tracks
            val targetId = if (relatedVideoId != null) {
                relatedVideoId
            } else if (history.isNotEmpty()) {
                history.first().id // Use most recent if nothing playing
            } else {
                null
            }

            val data = InnerTubeRepository.getHomeData(category?.params, targetId)
            homeData = data
            isLoading = false
            
            if (relatedVideoId != null) {
                lastLoadedTrackId = relatedVideoId
            }
        }
    }

    LaunchedEffect(currentTrack?.id) {
        if (currentTrack != null) {
            if (currentTrack!!.id != lastLoadedTrackId) {
                refreshHome(relatedVideoId = currentTrack!!.id)
            }
        } else if (homeData.quickPicks.isEmpty() && !isLoading) {
            refreshHome()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column {
            GlassHeader(
                onSearchActivate = onSearchClick,
                onHistoryClick = onHistoryClick
            )

            if (homeData.categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(homeData.categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                if (selectedCategory == category) {
                                    selectedCategory = null
                                    refreshHome()
                                } else {
                                    selectedCategory = category
                                    refreshHome(category)
                                }
                            },
                            label = { Text(category.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                selectedContainerColor = MaterialTheme.colorScheme.primary
                            ),
                            border = null
                        )
                    }
                }
            }

            if (isLoading && homeData.quickPicks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // LOCKED ORDER: Speed Dial always at TOP
                    if (homeData.speedDial.isNotEmpty()) {
                        item { SpeedDialSection(homeData.speedDial, audioPlayerManager) }
                    }
                    
                    if (homeData.quickPicks.isNotEmpty()) {
                        item { QuickPicksSection(homeData.quickPicks, audioPlayerManager) }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun QuickPicksSection(tracks: List<Track>, playerManager: AudioPlayerManager) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick picks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { playerManager.playTrack(tracks[0], tracks) }) {
                Text("Play all")
            }
        }

        val chunks = tracks.chunked(4)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(chunks) { columnTracks ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    columnTracks.forEach { track ->
                        QuickPickItem(track) {
                            playerManager.playTrack(track, tracks)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPickItem(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { /* Options */ }) {
            Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@UnstableApi
@Composable
fun SpeedDialSection(tracks: List<Track>, playerManager: AudioPlayerManager) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("YOUR FAVORITES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Speed dial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        val chunks = tracks.chunked(3)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chunks) { columnTracks ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    columnTracks.forEach { track ->
                        SpeedDialItem(track) {
                            playerManager.playTrack(track, tracks)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedDialItem(track: Track, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        Text(
            track.title,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
