package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.data.local.entity.HomeCacheEntity
import com.android.pulse.data.local.entity.toTrack
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import com.android.pulse.data.remote.innertube.model.Category
import com.android.pulse.data.remote.innertube.model.HomeData
import com.android.pulse.ui.components.GlassHeader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun HomeView(
    audioPlayerManager: AudioPlayerManager, 
    onHistoryClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    
    var homeData by remember { mutableStateOf(HomeData()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    
    val historyEntities by audioPlayerManager.database.historyDao().getRecentHistory().collectAsState(initial = emptyList())
    val recentlyPlayed = remember(historyEntities) { historyEntities.map { it.toTrack() } }
    
    val pullToRefreshState = rememberPullToRefreshState()

    fun performRefresh(category: Category? = null, forceNetwork: Boolean = false) {
        scope.launch {
            if (forceNetwork) isRefreshing = true else isLoading = true
            
            val targetId = recentlyPlayed.firstOrNull()?.id
            android.util.Log.d("PULSE_TAG", "HomeView: performRefresh(cat=${category?.title}, related=$targetId)")
            
            val data = InnerTubeRepository.getHomeData(category?.params, targetId)
            
            if (data.quickPicks.isNotEmpty() || data.speedDial.isNotEmpty() || data.categories.isNotEmpty()) {
                homeData = data
                
                // Cache discovery structure (mostly for categories and startup stability)
                if (category == null) {
                    audioPlayerManager.database.homeCacheDao().insertHomeCache(
                        HomeCacheEntity(
                            quickPicksJson = gson.toJson(data.quickPicks),
                            speedDialJson = gson.toJson(data.speedDial),
                            categoriesJson = gson.toJson(data.categories)
                        )
                    )
                }
            }
            
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            val cache = audioPlayerManager.database.homeCacheDao().getHomeCache()
            if (cache != null) {
                val qpType = object : TypeToken<List<Track>>() {}.type
                val sdType = object : TypeToken<List<Track>>() {}.type
                val catType = object : TypeToken<List<Category>>() {}.type
                
                homeData = HomeData(
                    quickPicks = gson.fromJson(cache.quickPicksJson, qpType),
                    speedDial = gson.fromJson(cache.speedDialJson, sdType),
                    categories = gson.fromJson(cache.categoriesJson, catType)
                )
            }
            
            if (homeData.quickPicks.isEmpty() && homeData.speedDial.isEmpty()) {
                performRefresh()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = pullToRefreshState,
            onRefresh = { performRefresh(selectedCategory, forceNetwork = true) },
            modifier = Modifier.fillMaxSize()
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

                // CATEGORIES
                item {
                    val moreCategories = listOf(
                        Category("Romance", "FEmusic_home", "ggMPOg16X2V4Y2x1c2l2ZV9y"),
                        Category("Sad", "FEmusic_home", "ggMPOg1hX2V4Y2x1c2l2ZV9y"),
                        Category("Workout", "FEmusic_home", "ggMPOg1xX2V4Y2x1c2l2ZV9y"),
                        Category("Focus", "FEmusic_home", "ggMPOg10X2V4Y2x1c2l2ZV9y"),
                        Category("Party", "FEmusic_home", "ggMPOg1yX2V4Y2x1c2l2ZV9y")
                    )
                    val allCategories = (homeData.categories + moreCategories).distinctBy { it.title }

                    if (allCategories.isNotEmpty()) {
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
                                            performRefresh(forceNetwork = true)
                                        } else {
                                            selectedCategory = category
                                            performRefresh(category, forceNetwork = true)
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
                }

                if (isLoading && homeData.quickPicks.isEmpty() && homeData.speedDial.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    // 1. RECENTLY PLAYED (Stable History)
                    if (recentlyPlayed.isNotEmpty()) {
                        item { RecentlyPlayedSection(recentlyPlayed, audioPlayerManager) }
                    }

                    // DISCOVERY AGGREGATION: Combine all results for distribution
                    val discoveryTracks = (homeData.speedDial + homeData.quickPicks).distinctBy { it.id }.shuffled()
                    
                    if (discoveryTracks.isNotEmpty()) {
                        // Section 2: Top Hits (8 Tracks)
                        val topHits = discoveryTracks.take(8)
                        item {
                            TopHitsSection(
                                title = if (selectedCategory != null) "${selectedCategory!!.title} Hits" else "Trending Hits",
                                tracks = topHits,
                                playerManager = audioPlayerManager
                            )
                        }

                        // Section 3: Vibe Mix (10 Tracks)
                        val vibeMix = discoveryTracks.drop(8).take(10)
                        if (vibeMix.isNotEmpty()) {
                            item {
                                VibeMixSection(
                                    title = "Your Vibe Mix",
                                    tracks = vibeMix,
                                    playerManager = audioPlayerManager
                                )
                            }
                        }

                        // Section 4: Featured Grid (Up to 12 Tracks)
                        val featured = discoveryTracks.drop(18).take(12)
                        if (featured.isNotEmpty()) {
                            item {
                                FeaturedGridSection(
                                    title = "Featured Selection",
                                    tracks = featured,
                                    playerManager = audioPlayerManager
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun RecentlyPlayedSection(tracks: List<Track>, playerManager: AudioPlayerManager, title: String = "Recently played") {
    val pagedTracks = tracks.chunked(9)
    val pagerState = rememberPagerState(pageCount = { pagedTracks.size })

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            title, 
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
                            HomeGridItem(
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

@UnstableApi
@Composable
fun TopHitsSection(title: String, tracks: List<Track>, playerManager: AudioPlayerManager) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tracks) { track ->
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .clickable { playerManager.playTrack(track, tracks) }
                ) {
                    AsyncImage(
                        model = track.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun VibeMixSection(title: String, tracks: List<Track>, playerManager: AudioPlayerManager) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks) { track ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { playerManager.playTrack(track, tracks) }
                ) {
                    AsyncImage(
                        model = track.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun FeaturedGridSection(title: String, tracks: List<Track>, playerManager: AudioPlayerManager) {
    val pagedTracks = tracks.chunked(4)
    val pagerState = rememberPagerState(pageCount = { pagedTracks.size })

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            title,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pageTracks.chunked(2).forEach { rowTracks ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowTracks.forEach { track ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.5f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { playerManager.playTrack(track, tracks) }
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
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                                )
                                Text(
                                    track.title,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        if (rowTracks.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun HomeGridItem(track: Track, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
