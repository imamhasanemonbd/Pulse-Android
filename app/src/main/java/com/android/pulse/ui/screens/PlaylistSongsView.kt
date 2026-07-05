package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch {
                            playerManager.database.playlistDao().deletePlaylist(playlist)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "This playlist is empty",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songs) { entity ->
                        val track = entity.toTrack()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                TrackItem(track) {
                                    playerManager.playTrack(track, songs.map { it.toTrack() })
                                }
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    playerManager.database.playlistDao().removeSongFromPlaylist(playlist.playlistId, track.id)
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove from Playlist", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
