package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.audio.DownloadManager
import com.android.pulse.data.local.entity.toTrack
import com.android.pulse.ui.components.TrackItem
import androidx.media3.common.util.UnstableApi
import com.android.pulse.data.local.entity.OfflineSongEntity

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun OfflineMusicView(
    playerManager: AudioPlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val offlineSongs by playerManager.database.offlineSongDao().getAllOfflineSongs().collectAsState(initial = emptyList())
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()

    var songToRemove by remember { mutableStateOf<OfflineSongEntity?>(null) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Offline Music", fontWeight = FontWeight.Bold) },
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
            if (offlineSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("No downloaded music yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    items(offlineSongs) { entity ->
                        val track = entity.toTrack()
                        val progress = downloadProgress[entity.id]
                        
                        TrackItem(
                            track = track,
                            onClick = { 
                                if (entity.isFinished) {
                                    playerManager.playTrack(track, offlineSongs.filter { it.isFinished }.map { it.toTrack() }) 
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (entity.isFinished) {
                                        Icon(
                                            Icons.Default.DownloadDone, 
                                            null, 
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else if (progress != null) {
                                        CircularProgressIndicator(
                                            progress = progress,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Downloading,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    IconButton(onClick = { songToRemove = entity }) {
                                        Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (songToRemove != null) {
        ModalBottomSheet(
            onDismissRequest = { songToRemove = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = songToRemove!!.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                    maxLines = 1
                )
                ListItem(
                    headlineContent = { Text("Remove from Downloads") },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        DownloadManager.removeTrack(context, playerManager.database, songToRemove!!.id)
                        songToRemove = null
                    }
                )
            }
        }
    }
}
