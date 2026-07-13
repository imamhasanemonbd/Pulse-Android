package com.android.pulse.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.ui.components.*
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import com.android.pulse.data.local.entity.PlaylistEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import coil.compose.AsyncImage

@UnstableApi
@Composable
fun MainScreen(playerManager: AudioPlayerManager) {
    val items = listOf(NavItem.Home, NavItem.Library, NavItem.Settings)
    val pagerState = rememberPagerState(pageCount = { items.size }) 
    val scope = rememberCoroutineScope()
    
    // NAVIGATION STATE
    var showPlayerSheet by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    var searchInitialQuery by remember { mutableStateOf<String?>(null) }
    
    // Library sub-screens
    var showLikedMusic by remember { mutableStateOf(false) }
    var showOfflineMusic by remember { mutableStateOf(false) }
    var showAllPlaylists by remember { mutableStateOf(false) }
    var selectedPlaylistForView by remember { mutableStateOf<PlaylistEntity?>(null) }
    
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()

    val isMiniPlayerVisible = currentTrack != null && !showPlayerSheet

    fun closeAllOverlays() {
        showAboutScreen = false
        showHistoryScreen = false
        showSearchScreen = false
        searchInitialQuery = null
        showLikedMusic = false
        showOfflineMusic = false
        showAllPlaylists = false
        selectedPlaylistForView = null
    }

    BackHandler(enabled = true) {
        if (showPlayerSheet) {
            showPlayerSheet = false
        } else if (showAboutScreen) {
            showAboutScreen = false
        } else if (showHistoryScreen) {
            showHistoryScreen = false
        } else if (showSearchScreen) {
            showSearchScreen = false
            searchInitialQuery = null
        } else if (selectedPlaylistForView != null) {
            selectedPlaylistForView = null
            showAllPlaylists = true
        } else if (showLikedMusic) {
            showLikedMusic = false
            scope.launch { pagerState.animateScrollToPage(items.indexOf(NavItem.Library)) }
        } else if (showAllPlaylists) {
            showAllPlaylists = false
            scope.launch { pagerState.animateScrollToPage(items.indexOf(NavItem.Library)) }
        } else if (showOfflineMusic) {
            showOfflineMusic = false
            scope.launch { pagerState.animateScrollToPage(items.indexOf(NavItem.Library)) }
        } else if (pagerState.currentPage != 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    if (isMiniPlayerVisible) {
                        Column {
                            MiniPlayer(
                                trackTitle = currentTrack?.title ?: "Unknown",
                                artistName = currentTrack?.artist ?: "Unknown",
                                thumbnailUrl = currentTrack?.thumbnail,
                                isPlaying = isPlaying,
                                onTogglePlay = { playerManager.togglePlayPause() },
                                onClick = { showPlayerSheet = true }
                            )
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                    
                    BottomNavBar(
                        selectedRoute = items[pagerState.currentPage].route,
                        onItemClick = { route ->
                            val index = items.indexOfFirst { it.route == route }
                            if (index != -1) {
                                closeAllOverlays()
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        }
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false // Tabs handle navigation
                ) { page ->
                    val viewModifier = Modifier.padding(innerPadding)
                    when (items[page]) {
                        NavItem.Home -> HomeView(
                            audioPlayerManager = playerManager,
                            onHistoryClick = { showHistoryScreen = true },
                            onSearchClick = { showSearchScreen = true },
                            modifier = viewModifier
                        )
                        NavItem.Library -> LibraryView(
                            onLikedMusicClick = { showLikedMusic = true },
                            onDownloadedClick = { showOfflineMusic = true },
                            onPlaylistsClick = { showAllPlaylists = true },
                            modifier = viewModifier
                        )
                        NavItem.Settings -> SettingsView(
                            audioPlayerManager = playerManager,
                            onAboutClick = { showAboutScreen = true },
                            modifier = viewModifier
                        )
                        else -> { /* Other NavItems not in bottom bar */ }
                    }
                }

                // NAVIGATION OVERLAYS
                Overlay(visible = showAboutScreen) {
                    PlaceholderView("About Pulse", { showAboutScreen = false })
                }
                
                Overlay(visible = showHistoryScreen) {
                    PlaceholderView("Listen History", { showHistoryScreen = false })
                }

                Overlay(visible = showSearchScreen) {
                    SearchView(
                        playerManager = playerManager,
                        onBack = { 
                            showSearchScreen = false
                            searchInitialQuery = null
                        },
                        initialQuery = searchInitialQuery
                    )
                }

                Overlay(visible = showLikedMusic) {
                    LikedSongsView(playerManager, onBack = { showLikedMusic = false })
                }

                Overlay(visible = showOfflineMusic) {
                    OfflineMusicView(playerManager, onBack = { showOfflineMusic = false })
                }

                Overlay(visible = showAllPlaylists) {
                    AllPlaylistsView(
                        audioPlayerManager = playerManager,
                        onBack = { showAllPlaylists = false },
                        onPlaylistClick = { 
                            selectedPlaylistForView = it
                            showAllPlaylists = false
                        }
                    )
                }

                Overlay(visible = selectedPlaylistForView != null) {
                    selectedPlaylistForView?.let { playlist ->
                        PlaylistSongsView(
                            playlist = playlist,
                            playerManager = playerManager,
                            onBack = { selectedPlaylistForView = null }
                        )
                    }
                }
            }
        }

        // PLAYER SHEET
        AnimatedVisibility(
            visible = showPlayerSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            PlayerSheet(
                playerManager = playerManager,
                isVisible = showPlayerSheet,
                onDismiss = { showPlayerSheet = false },
                onSearchArtist = { artist ->
                    showPlayerSheet = false
                    searchInitialQuery = artist
                    showSearchScreen = true
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderView(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Screen coming soon", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun Overlay(visible: Boolean, animationSpec: SpringSpec<IntOffset> = spring(), content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}

@Composable
fun MiniPlayer(
    trackTitle: String,
    artistName: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
