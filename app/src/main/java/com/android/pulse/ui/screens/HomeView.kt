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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    
    // We only want to auto-refresh home data if it's empty. 
    // Constantly refreshing on currentTrack change was causing the "Disappearing Content" bug.
    fun refreshHome(category: Category? = null) {
        scope.launch {
            isLoading = true
            // Load fresh home discovery data
            val data = InnerTubeRepository.getHomeData(category?.params, null)
            homeData = data
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (homeData.quickPicks.isEmpty() && !isLoading) {
            refreshHome()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                GlassHeader(
                    onSearchActivate = onSearchClick,
                    onHistoryClick = onHistoryClick
                )
            }

            item {
                val moreCategories = listOf(
                    Category("Romance", "FEmusic_home", "ggMPOg16X2V4Y2x1c2l2ZV9y"),
                    Category("Sad", "FEmusic_home", "ggMPOg1hX2V4Y2x1c2l2ZV9y"),
                    Category("Workout", "FEmusic_home", "ggMPOg1xX2V4Y2x1c2l2ZV9y"),
                    Category("Focus", "FEmusic_home", "ggMPOg10X2V4Y2x1c2l2ZV9y")
                )
                val allCategories = (homeData.categories + moreCategories).distinctBy { it.title }

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allCategories) { category ->
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
                            label = { 
                                Text(
                                    category.title, 
                                    color = if (selectedCategory == category) Color.White else MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                selectedContainerColor = MaterialTheme.colorScheme.primary
                            ),
                            border = null,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            if (isLoading && homeData.quickPicks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
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

@UnstableApi
@Composable
fun SpeedDialSection(tracks: List<Track>, playerManager: AudioPlayerManager) {
    val pagedTracks = tracks.chunked(9)
    val pagerState = rememberPagerState(pageCount = { pagedTracks.size })

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            "Speed dial", 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp
        ) { pageIndex ->
            val pageTracks = pagedTracks[pageIndex]
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pageTracks.chunked(3).forEach { rowTracks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTracks.forEach { track ->
                            SpeedDialItem(
                                track = track,
                                modifier = Modifier.weight(1f),
                                onClick = { playerManager.playTrack(track, tracks) }
                            )
                        }
                        repeat(3 - rowTracks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                repeat(3 - (pageTracks.size + 2) / 3) {
                    Spacer(modifier = Modifier.height(120.dp).fillMaxWidth())
                }
            }
        }

        if (pagedTracks.size > 1) {
            PageIndicator(
                pageCount = pagedTracks.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun SpeedDialItem(track: Track, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
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
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                track.title,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@UnstableApi
@Composable
fun QuickPicksSection(tracks: List<Track>, playerManager: AudioPlayerManager) {
    val pagedTracks = tracks.chunked(4)
    val pagerState = rememberPagerState(pageCount = { pagedTracks.size })

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick picks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            // Fix: Pass the current page of tracks for "Play all" or the full list
            TextButton(onClick = { playerManager.playTrack(tracks[0], tracks) }) {
                Text("Play all")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(290.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp
        ) { pageIndex ->
            val pageTracks = pagedTracks[pageIndex]
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pageTracks.forEach { track ->
                    QuickPickItem(track) {
                        // Fix: Pass the current full list of tracks to ensure the queue matches the UI
                        playerManager.playTrack(track, tracks)
                    }
                }
            }
        }

        if (pagedTracks.size > 1) {
            PageIndicator(
                pageCount = pagedTracks.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun QuickPickItem(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { /* Options */ }) {
            Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (isSelected) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
            )
        }
    }
}
