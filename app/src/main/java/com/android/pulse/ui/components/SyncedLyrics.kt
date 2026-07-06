package com.android.pulse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pulse.data.remote.innertube.InnerTubeRepository

@Composable
fun SyncedLyrics(trackId: String, currentTimeMs: Long, durationMs: Long) {
    var lyricsText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(trackId) {
        isLoading = true
        try {
            lyricsText = InnerTubeRepository.getLyrics(trackId) ?: "Lyrics not available."
        } catch (e: Exception) {
            lyricsText = "Lyrics not available."
        } finally {
            isLoading = false
        }
    }

    val lines = lyricsText.split("\n").filter { it.isNotBlank() }
    val activeLineIndex = if (durationMs > 0 && lines.isNotEmpty()) {
        ((currentTimeMs.toFloat() / durationMs.toFloat()) * lines.size).toInt().coerceIn(0, lines.size - 1)
    } else {
        -1
    }

    val listState = rememberLazyListState()
    
    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex != -1) {
            listState.animateScrollToItem(activeLineIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.99f } // Required for BlendMode.DstIn
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.15f to Color.Black,
                        0.85f to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading Lyrics...", color = Color.Gray)
            }
        } else if (lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Lyrics not available.", color = Color.White.copy(alpha = 0.3f))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 100.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    val isActive = index == activeLineIndex
                    Text(
                        text = line,
                        fontSize = if (isActive) 24.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                scaleX = if (isActive) 1.04f else 1f
                                scaleY = if (isActive) 1.04f else 1f
                            }
                    )
                }
            }
        }
    }
}
