package com.android.pulse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.pulse.data.model.Track

@Composable
fun DynamicBackdrop(currentTrack: Track?) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (currentTrack?.thumbnail != null) {
            AsyncImage(
                model = currentTrack.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(120.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.35f
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF1E1B4B), Color(0xFF030008)),
                            center = androidx.compose.ui.geometry.Offset(0f, 0f)
                        )
                    )
            )
        }
        
        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f)),
                        radius = 2000f
                    )
                )
        )
    }
}
