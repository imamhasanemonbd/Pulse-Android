package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.audio.DownloadManager
import com.android.pulse.data.local.entity.toTrack
import com.android.pulse.ui.components.TrackItem
import androidx.media3.common.util.UnstableApi

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun OfflineMusicView(
    playerManager: AudioPlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offlineSongs by playerManager.database.offlineSongDao().getAllOfflineSongs().collectAsState(initial = emptyList())
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()

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
                            }
                        )
                    }
                }
            }
        }
    }
}
