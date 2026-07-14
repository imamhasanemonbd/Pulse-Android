package com.android.pulse.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.delay

data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Composable
fun SyncedLyrics(trackId: String, title: String, artist: String, durationMs: Long, currentTimeMs: Long) {
    var rawLyrics by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val lyricLines = remember(rawLyrics) { parseLrc(rawLyrics) }
    
    LaunchedEffect(trackId) {
        isLoading = true
        try {
            rawLyrics = InnerTubeRepository.getLyrics(trackId, title, artist, durationMs) ?: ""
        } catch (e: Exception) {
            rawLyrics = ""
        } finally {
            isLoading = false
        }
    }

    val activeLineIndex = remember(currentTimeMs, lyricLines) {
        if (lyricLines.isEmpty()) -1
        else {
            val index = lyricLines.indexOfLast { it.timeMs <= currentTimeMs }
            if (index == -1 && lyricLines.isNotEmpty()) 0 else index
        }
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
            .graphicsLayer { alpha = 0.99f }
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
                Text(text = "Searching Lyrics...", color = Color.Gray)
            }
        } else if (lyricLines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Lyrics not available.", color = Color.White.copy(alpha = 0.3f))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(lyricLines) { index, line ->
                    val isActive = index == activeLineIndex
                    Text(
                        text = line.text,
                        fontSize = if (isActive) 26.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                scaleX = if (isActive) 1.05f else 1f
                                scaleY = if (isActive) 1.05f else 1f
                            }
                    )
                }
            }
        }
    }
}

/**
 * Robust LRC Parser (Support [mm:ss.xx] and [mm:ss])
 */
fun parseLrc(lrc: String): List<LyricLine> {
    if (lrc.isBlank()) return emptyList()
    
    // Check if it is LRC format
    if (!lrc.contains("[")) {
        // Fallback: Static text lyrics (simulated timings)
        return lrc.split("\n").filter { it.isNotBlank() }.mapIndexed { i, text ->
            LyricLine(i * 3000L, text) // 3s gap
        }
    }

    val lines = mutableListOf<LyricLine>()
    // Improved regex to handle various LRC timestamp formats
    val pattern = "\\[(\\d+):(\\d+)[.:](\\d+)?\\](.*)".toRegex()

    lrc.lines().forEach { rawLine ->
        val match = pattern.find(rawLine)
        if (match != null) {
            val mins = match.groupValues[1].toLong()
            val secs = match.groupValues[2].toLong()
            val msPart = match.groupValues[3].let { if (it.isEmpty()) "0" else it }.take(3).padEnd(3, '0').toLong()
            
            val totalMs = (mins * 60 * 1000) + (secs * 1000) + msPart
            val text = match.groupValues[4].trim()
            
            if (text.isNotBlank()) {
                lines.add(LyricLine(totalMs, text))
            }
        }
    }
    
    return lines.sortedBy { it.timeMs }
}
