package com.android.pulse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val items = listOf(NavItem.Home, NavItem.Explore, NavItem.Library, NavItem.Settings, NavItem.LikedSongs)
    
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val tabBackStack = remember { mutableStateListOf(0) }
    
    var showPlayerSheet by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var selectedPlaylistForView by remember { mutableStateOf<PlaylistEntity?>(null) }
    
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()

    BackHandler(enabled = true) {
        if (showAboutScreen) {
            showAboutScreen = false
        } else if (showHistoryScreen) {
            showHistoryScreen = false
        } else if (selectedPlaylistForView != null) {
            selectedPlaylistForView = null
        } else if (showPlayerSheet) {
            showPlayerSheet = false
        } else if (pagerState.currentPage != 0) {
            if (tabBackStack.size > 1) {
                tabBackStack.removeAt(tabBackStack.size - 1)
                val prevPage = tabBackStack.last()
                scope.launch {
                    pagerState.animateScrollToPage(prevPage)
                }
            } else {
                scope.launch {
                    pagerState.animateScrollToPage(0)
                }
            }
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (tabBackStack.isEmpty() || tabBackStack.last() != pagerState.currentPage) {
            // Avoid adding nested routes to the main tab backstack if we want it strictly for tabs
            if (pagerState.currentPage == 0) {
                tabBackStack.clear()
                tabBackStack.add(0)
            } else if (pagerState.currentPage < 4) {
                if (tabBackStack.contains(pagerState.currentPage)) {
                    tabBackStack.remove(pagerState.currentPage)
                }
                tabBackStack.add(pagerState.currentPage)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (currentTrack != null) {
                        MiniPlayer(
                            trackTitle = currentTrack!!.title,
                            artistName = currentTrack!!.artist,
                            thumbnailUrl = currentTrack!!.thumbnail,
                            isPlaying = isPlaying,
                            onTogglePlay = { playerManager.togglePlayPause() },
                            onClick = { showPlayerSheet = true }
                        )
                    }
                    val currentRoute = items[pagerState.currentPage].route
                    
                    BottomNavBar(
                        selectedRoute = if (pagerState.currentPage >= 4) "library" else currentRoute,
                        onItemClick = { route ->
                            val index = items.indexOfFirst { it.route == route }
                            if (index != -1) {
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
                    beyondViewportPageCount = 1,
                    userScrollEnabled = false,
                    pageSpacing = 16.dp,
                ) { page ->
                    val viewModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    when (items[page]) {
                        NavItem.Home -> HomeView(
                            audioPlayerManager = playerManager,
                            onHistoryClick = { showHistoryScreen = true },
                            modifier = viewModifier
                        )
                        NavItem.Explore -> ExploreView(playerManager, viewModifier)
                        NavItem.Library -> LibraryView(
                            audioPlayerManager = playerManager,
                            onLikedSongsClick = { 
                                scope.launch { pagerState.animateScrollToPage(items.indexOf(NavItem.LikedSongs)) }
                            },
                            onPlaylistClick = { playlist ->
                                selectedPlaylistForView = playlist
                            },
                            modifier = viewModifier
                        )
                        NavItem.Settings -> SettingsView(
                            audioPlayerManager = playerManager, 
                            onAboutClick = { showAboutScreen = true },
                            modifier = viewModifier
                        )
                        NavItem.LikedSongs -> LikedSongsView(
                            playerManager = playerManager, 
                            onBack = { 
                                scope.launch { pagerState.animateScrollToPage(items.indexOf(NavItem.Library)) }
                            },
                            modifier = viewModifier
                        )
                        else -> {}
                    }
                }
            }
        }

        val springSpec = spring<IntOffset>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )

        Overlay(visible = showAboutScreen, springSpec = springSpec) {
            AboutView(onBack = { showAboutScreen = false })
        }

        Overlay(visible = showHistoryScreen, springSpec = springSpec) {
            HistoryView(playerManager = playerManager, onBack = { showHistoryScreen = false })
        }

        Overlay(visible = selectedPlaylistForView != null, springSpec = springSpec) {
            if (selectedPlaylistForView != null) {
                PlaylistSongsView(
                    playlist = selectedPlaylistForView!!,
                    playerManager = playerManager,
                    onBack = { selectedPlaylistForView = null }
                )
            }
        }

        AnimatedVisibility(
            visible = showPlayerSheet,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = springSpec
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = springSpec
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            PlayerSheet(
                playerManager = playerManager,
                onDismiss = { showPlayerSheet = false }
            )
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
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = springSpec
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = springSpec
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
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
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
