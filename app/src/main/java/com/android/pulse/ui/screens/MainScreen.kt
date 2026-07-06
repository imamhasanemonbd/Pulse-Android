package com.android.pulse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.ui.components.BottomNavBar
import com.android.pulse.ui.components.NavItem
import com.android.pulse.ui.components.PlayerSheet
import com.android.pulse.data.local.entity.PlaylistEntity
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

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
    
    // Library sub-screens
    var showLikedMusic by remember { mutableStateOf(false) }
    var showOfflineMusic by remember { mutableStateOf(false) }
    var showCachedMusic by remember { mutableStateOf(false) }
    var showLocalMusic by remember { mutableStateOf(false) }
    var showAllPlaylists by remember { mutableStateOf(false) }
    var selectedPlaylistForView by remember { mutableStateOf<PlaylistEntity?>(null) }
    
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()

    val isMiniPlayerVisible = currentTrack != null && !showPlayerSheet

    // Helper to close all overlays when switching tabs
    fun closeAllOverlays() {
        showAboutScreen = false
        showHistoryScreen = false
        showSearchScreen = false
        showLikedMusic = false
        showOfflineMusic = false
        showCachedMusic = false
        showLocalMusic = false
        showAllPlaylists = false
        selectedPlaylistForView = null
    }

    // HIERARCHICAL BACK NAVIGATION:
    // Liked -> Playlists -> Library -> Home -> Exit
    // About -> Setting -> Home -> Exit
    // Search -> Home -> Exit
    BackHandler(enabled = true) {
        if (showPlayerSheet) {
            showPlayerSheet = false
        } else if (showAboutScreen) {
            showAboutScreen = false
        } else if (showHistoryScreen) {
            showHistoryScreen = false
        } else if (showSearchScreen) {
            showSearchScreen = false
        } else if (selectedPlaylistForView != null) {
            selectedPlaylistForView = null
            showAllPlaylists = true // Back from PlaylistSongs goes to AllPlaylists
        } else if (showLikedMusic) {
            showLikedMusic = false // Back from Liked goes to Library
            scope.launch { pagerState.animateScrollToPage(items.indexOf(NavItem.Library)) }
        } else if (showAllPlaylists) {
            showAllPlaylists = false // Back from Playlists goes to Library
            scope.launch { pagerState.animateScrollToPage(items.indexOf(NavItem.Library)) }
        } else if (showOfflineMusic || showCachedMusic || showLocalMusic) {
            // These screens go back to Library
            showOfflineMusic = false
            showCachedMusic = false
            showLocalMusic = false
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
                                // BUG FIX: Reset all overlays when switching tabs via bottom nav
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
                    beyondViewportPageCount = 0,
                    userScrollEnabled = false,
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
                        else -> {}
                    }
                }

                val overlayModifier = Modifier.fillMaxSize().padding(innerPadding)
                val springSpec = spring<IntOffset>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

                // OVERLAYS: Standardized with back-stack support
                Overlay(visible = showSearchScreen, springSpec = springSpec) {
                    SearchView(playerManager = playerManager, onBack = { showSearchScreen = false }, modifier = overlayModifier)
                }

                Overlay(visible = showHistoryScreen, springSpec = springSpec) {
                    HistoryView(playerManager = playerManager, onBack = { showHistoryScreen = false }, modifier = overlayModifier)
                }

                Overlay(visible = showAboutScreen, springSpec = springSpec) {
                    AboutView(onBack = { showAboutScreen = false }, modifier = overlayModifier)
                }
                
                // Library Sub-screens
                Overlay(visible = showAllPlaylists, springSpec = springSpec) {
                    AllPlaylistsView(
                        audioPlayerManager = playerManager,
                        onBack = { showAllPlaylists = false },
                        onPlaylistClick = { playlist -> 
                            selectedPlaylistForView = playlist
                            showAllPlaylists = false
                        },
                        modifier = overlayModifier
                    )
                }

                Overlay(visible = showLikedMusic, springSpec = springSpec) {
                    LikedSongsView(playerManager = playerManager, onBack = { 
                        showLikedMusic = false
                    }, modifier = overlayModifier)
                }

                Overlay(visible = selectedPlaylistForView != null, springSpec = springSpec) {
                    if (selectedPlaylistForView != null) {
                        PlaylistSongsView(
                            playlist = selectedPlaylistForView!!,
                            playerManager = playerManager,
                            onBack = { 
                                showAllPlaylists = true // Per requested order
                                selectedPlaylistForView = null 
                            },
                            modifier = overlayModifier
                        )
                    }
                }
                
                // Placeholder Overlays for newly requested features
                Overlay(visible = showOfflineMusic, springSpec = springSpec) {
                    OfflineMusicView(playerManager = playerManager, onBack = { showOfflineMusic = false }, modifier = overlayModifier)
                }
                Overlay(visible = showCachedMusic, springSpec = springSpec) {
                    PlaceholderView("Cached Music", onBack = { showCachedMusic = false }, modifier = overlayModifier)
                }
                Overlay(visible = showLocalMusic, springSpec = springSpec) {
                    PlaceholderView("Local Music", onBack = { showLocalMusic = false }, modifier = overlayModifier)
                }
            }
        }

        // FULL SCREEN PLAYER SHEET
        AnimatedVisibility(
            visible = showPlayerSheet,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
        ) {
            PlayerSheet(
                playerManager = playerManager,
                isVisible = showPlayerSheet,
                onDismiss = { showPlayerSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderView(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("$title logic coming soon...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Overlay(
    visible: Boolean, 
    springSpec: SpringSpec<IntOffset>, 
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = springSpec) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = springSpec) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
    ) {
        content()
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trackTitle, 
                    color = MaterialTheme.colorScheme.onSurface, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold, 
                    maxLines = 1
                )
                Text(
                    artistName, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 12.sp, 
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
