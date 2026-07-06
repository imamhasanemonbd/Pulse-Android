package com.android.pulse.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.audio.DownloadManager
import com.android.pulse.data.local.entity.PlaylistEntity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlayerSheet(
    playerManager: AudioPlayerManager,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    
    val track by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isLoading by playerManager.isLoading.collectAsState()
    val currentTime by playerManager.currentTime.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val pulsation by playerManager.pulsation.collectAsState()
    val isLiked by playerManager.isLiked.collectAsState()
    
    val volume by playerManager.volume.collectAsState()
    
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val currentProgress = track?.let { downloadProgress[it.id] }

    var showMoreOptions by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // Smooth Slider Logic
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    LaunchedEffect(currentTime, isDraggingSlider) {
        if (!isDraggingSlider && duration > 0) {
            sliderPosition = currentTime.toFloat() / duration
        }
    }

    if (track == null) return

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            offsetY.snapTo(0f)
        }
    }

    val draggableState = rememberDraggableState { delta ->
        coroutineScope.launch {
            offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    coroutineScope.launch {
                        if (offsetY.value > 400f || velocity > 1500f) {
                            onDismiss()
                        } else {
                            offsetY.animateTo(
                                targetValue = 0f, 
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessHigh
                                )
                            )
                        }
                    }
                }
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dynamic Blurred Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Notch Safety Spacer
                Spacer(modifier = Modifier.statusBarsPadding())
                
                // Drag Handle
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(36.dp, 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )

                // Header Morphing
                AnimatedVisibility(
                    visible = showLyrics || showQueue,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track!!.thumbnail,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track!!.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                track!!.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { playerManager.toggleLike() }) {
                            Icon(
                                if (isLiked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                null,
                                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showMoreOptions = true }) {
                            Icon(Icons.Default.MoreHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Main Area (Artwork, Lyrics, or Queue)
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyrics) {
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            SyncedLyrics(
                                trackId = track!!.id,
                                currentTimeMs = if (isDraggingSlider) (sliderPosition * duration).toLong() else currentTime,
                                durationMs = duration
                            )
                        }
                    } else if (showQueue) {
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            QueueView(playerManager)
                        }
                    } else {
                        // Artwork
                        val scale by animateFloatAsState(
                            targetValue = if (isPlaying) pulsation else 0.96f,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                            label = "artworkScale"
                        )
                        AsyncImage(
                            model = track!!.thumbnail,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .aspectRatio(1f)
                                .scale(scale)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .shadow(48.dp, MaterialTheme.shapes.extraLarge)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Metadata, Slider, Controls (Bottom Section)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Metadata (Only show if artwork is showing)
                    if (!showLyrics && !showQueue) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track!!.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    modifier = Modifier.basicMarquee(),
                                    maxLines = 1
                                )
                                Text(
                                    text = track!!.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            
                            Row {
                                IconButton(onClick = { playerManager.toggleLike() }) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = { showMoreOptions = true }) {
                                    Icon(
                                        Icons.Default.MoreHoriz, 
                                        null, 
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Progress Slider
                    Box(contentAlignment = Alignment.Center) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { 
                                isDraggingSlider = true
                                sliderPosition = it 
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                playerManager.seekTo((sliderPosition * duration).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = if (isLoading) Color.Transparent else MaterialTheme.colorScheme.onSurface,
                                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.onSurface,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayTime = if (isDraggingSlider) (sliderPosition * duration).toLong() else currentTime
                        Text(formatTime(displayTime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-${formatTime(duration - displayTime)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Playback Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { playerManager.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(48.dp))
                        }
                        IconButton(onClick = { playerManager.togglePlayPause() }, modifier = Modifier.size(84.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        IconButton(onClick = { playerManager.skipNext() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(48.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Slider(
                            value = volume,
                            onValueChange = { playerManager.setVolume(it) },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onSurface,
                                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Bottom Utilities
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            showLyrics = !showLyrics
                            showQueue = false
                        }) {
                            Icon(
                                Icons.Default.ChatBubbleOutline, 
                                null, 
                                modifier = Modifier.size(24.dp),
                                tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { 
                            val intent = Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Cast, null, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { 
                            showQueue = !showQueue
                            showLyrics = false
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.QueueMusic, 
                                null, 
                                modifier = Modifier.size(24.dp),
                                tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // More Options Sheet
    if (showMoreOptions) {
        ModalBottomSheet(
            onDismissRequest = { showMoreOptions = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track!!.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(track!!.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(track!!.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                ListItem(
                    headlineContent = { Text("Add to Playlist") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    modifier = Modifier.clickable { 
                        showAddToPlaylist = true
                        showMoreOptions = false
                    }
                )

                // BUG FIX: Check both database flag AND local progress to determine UI state
                val isDownloadedByDb by playerManager.database.offlineSongDao().isDownloaded(track!!.id).collectAsState(initial = false)
                val isActuallyFinished = isDownloadedByDb && currentProgress == null

                ListItem(
                    headlineContent = { 
                        Text(if (isActuallyFinished) "Remove Download" else if (currentProgress != null) "Downloading... ${(currentProgress * 100).toInt()}%" else "Download") 
                    },
                    leadingContent = { 
                        if (currentProgress != null && !isActuallyFinished) {
                            CircularProgressIndicator(
                                progress = { currentProgress },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(if (isActuallyFinished) Icons.Default.DeleteOutline else Icons.Default.Download, null, tint = if (isActuallyFinished) MaterialTheme.colorScheme.error else LocalContentColor.current) 
                        }
                    },
                    modifier = Modifier.clickable { 
                        if (isActuallyFinished) {
                            DownloadManager.removeTrack(context, playerManager.database, track!!.id)
                        } else if (currentProgress == null) {
                            DownloadManager.downloadTrack(context, playerManager.database, track!!)
                        }
                        showMoreOptions = false
                    }
                )

                ListItem(
                    headlineContent = { Text(if (isLiked) "Remove from Favorites" else "Mark as Favorite") },
                    leadingContent = { Icon(if (isLiked) Icons.Default.Star else Icons.Outlined.StarBorder, null) },
                    modifier = Modifier.clickable { 
                        playerManager.toggleLike()
                        showMoreOptions = false
                    }
                )
            }
        }
    }

    if (showAddToPlaylist) {
        val playlists by playerManager.database.playlistDao().getAllPlaylists().collectAsState(initial = emptyList())
        var showNewPlaylistDialog by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showAddToPlaylist = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("Add to Playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                
                ListItem(
                    headlineContent = { Text("New Playlist") },
                    leadingContent = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.clickable { showNewPlaylistDialog = true }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                            modifier = Modifier.clickable {
                                playerManager.addTrackToPlaylist(playlist.playlistId, track!!, null)
                                showAddToPlaylist = false
                            }
                        )
                    }
                }
            }
        }

        if (showNewPlaylistDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewPlaylistDialog = false },
                title = { Text("New Playlist") },
                text = { TextField(value = name, onValueChange = { name = it }, placeholder = { Text("Name") }) },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            playerManager.createPlaylistAndAddTrack(name, track!!)
                        }
                        showNewPlaylistDialog = false
                        showAddToPlaylist = false
                    }) { Text("Create") }
                }
            )
        }
    }
}

@UnstableApi
@Composable
fun QueueView(playerManager: AudioPlayerManager) {
    val shuffleMode by playerManager.shuffleMode.collectAsState()
    val repeatMode by playerManager.repeatMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Queue Controls (Apple Music Style)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val controlModifier = Modifier.weight(1f).height(48.dp)
            val activeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

            Surface(
                onClick = { playerManager.toggleShuffle() },
                modifier = controlModifier,
                shape = RoundedCornerShape(12.dp),
                color = if (shuffleMode) activeColor else inactiveColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp))
                }
            }

            Surface(
                onClick = { playerManager.cycleRepeatMode() },
                modifier = controlModifier,
                shape = RoundedCornerShape(12.dp),
                color = if (repeatMode != Player.REPEAT_MODE_OFF) activeColor else inactiveColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                onClick = { /* Autoplay Toggle */ },
                modifier = controlModifier,
                shape = RoundedCornerShape(12.dp),
                color = inactiveColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AllInclusive, null, modifier = Modifier.size(20.dp))
                }
            }
        }

        Text(
            "Playing Next", 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(playerManager.currentQueue) { track ->
                TrackItem(track) {
                    playerManager.playTrack(track, playerManager.currentQueue)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
