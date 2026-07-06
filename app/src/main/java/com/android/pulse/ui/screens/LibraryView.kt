package com.android.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.pulse.audio.AudioPlayerManager
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun LibraryView(
    onLikedMusicClick: () -> Unit,
    onOfflineMusicClick: () -> Unit,
    onCachedMusicClick: () -> Unit,
    onLocalMusicClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text(
                    "Library",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    LibraryListItem(
                        title = "Liked Music",
                        icon = Icons.Default.Favorite,
                        gradient = listOf(Color(0xFFE91E63), Color(0xFF9C27B0)),
                        onClick = onLikedMusicClick
                    )
                }
                item {
                    LibraryListItem(
                        title = "Offline Music",
                        icon = Icons.Default.DownloadForOffline,
                        gradient = listOf(Color(0xFF009688), Color(0xFF4CAF50)),
                        onClick = onOfflineMusicClick
                    )
                }
                item {
                    LibraryListItem(
                        title = "Cached Music",
                        icon = Icons.Default.Storage,
                        gradient = listOf(Color(0xFF607D8B), Color(0xFF455A64)),
                        onClick = onCachedMusicClick
                    )
                }
                item {
                    LibraryListItem(
                        title = "Local Music",
                        icon = Icons.Default.Folder,
                        gradient = listOf(Color(0xFFFF9800), Color(0xFFFF5722)),
                        onClick = onLocalMusicClick
                    )
                }
                item {
                    LibraryListItem(
                        title = "Playlists",
                        icon = Icons.Default.PlaylistPlay,
                        gradient = listOf(Color(0xFF2196F3), Color(0xFF03A9F4)),
                        onClick = onPlaylistsClick
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryListItem(
    title: String,
    icon: ImageVector? = null,
    imageUrl: String? = null,
    gradient: List<Color>? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (gradient != null) Modifier.background(Brush.linearGradient(gradient))
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (icon != null) {
                    Icon(icon, null, modifier = Modifier.size(28.dp), tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
