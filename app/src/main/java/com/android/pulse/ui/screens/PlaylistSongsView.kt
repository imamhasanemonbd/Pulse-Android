package com.android.pulse.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.data.local.entity.PlaylistEntity
import com.android.pulse.data.local.entity.toTrack
import com.android.pulse.ui.components.TrackItem
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlaylistSongsView(
    playlist: PlaylistEntity,
    playerManager: AudioPlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by playerManager.database.playlistDao().getSongsInPlaylist(playlist.playlistId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    val coverUrl = playlist.coverUrl ?: "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=512&h=512&auto=format&fit=crop"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // HEADER SECTION
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    // Blurred Background with Stiped Effect
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(50.dp),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Darkening Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.4f), MaterialTheme.colorScheme.background)
                                )
                            )
                    )

                    // Header Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Row {
                            IconButton(onClick = {}) { Icon(Icons.Outlined.PersonAdd, null, tint = Color.White) }
                            IconButton(onClick = {}) { Icon(Icons.Outlined.Share, null, tint = Color.White) }
                            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                        }
                    }

                    // Large Title (Top Left style)
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 48.sp,
                            letterSpacing = (-1.5).sp
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 100.dp, start = 24.dp)
                    )

                    // Centered Metadata & Actions
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Imam Hasan Emon", // Creator Name
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Updated Today",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Row: Shuffle, Play, Download
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Shuffle Button
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                onClick = {}
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Shuffle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Play Button (Large Pill)
                            Button(
                                onClick = { if (songs.isNotEmpty()) playerManager.playTrack(songs[0].toTrack(), songs.map { it.toTrack() }) },
                                modifier = Modifier
                                    .height(54.dp)
                                    .width(160.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }

                            // Download Button
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                onClick = {}
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.FileDownload, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            // SONG LIST
            if (songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No songs in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(songs) { entity ->
                    val track = entity.toTrack()
                    TrackItem(
                        track = track,
                        onClick = { playerManager.playTrack(track, songs.map { it.toTrack() }) },
                        trailingContent = {
                            IconButton(onClick = {}) {
                                Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }
        }
    }
}
