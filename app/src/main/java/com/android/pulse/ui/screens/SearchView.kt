package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import com.android.pulse.ui.components.GlassHeader
import com.android.pulse.ui.components.TrackItem
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun SearchView(audioPlayerManager: AudioPlayerManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column {
            GlassHeader(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                isSearchActive = isSearchActive,
                onSearchAction = {
                    scope.launch {
                        isLoading = true
                        tracks = InnerTubeRepository.searchMusic(searchQuery)
                        isLoading = false
                        isSearchActive = false
                    }
                },
                onSearchActivate = { isSearchActive = true },
                onSearchCancel = { 
                    onBack()
                },
                onHistoryClick = {}
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(tracks) { track ->
                        TrackItem(track) {
                            audioPlayerManager.playTrack(track, tracks)
                        }
                    }
                }
            }
        }
    }
}
