package com.android.pulse.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun PulseLogo(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val opacity by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFF2D55), Color(0xFFEC4899))
            )
            
            // Outer Ring
            drawCircle(
                brush = brush,
                radius = 9.dp.toPx() * scale,
                style = Stroke(width = 2.5.dp.toPx()),
                alpha = opacity
            )
            
            // Inner Path (Wavy Line)
            val path = Path().apply {
                moveTo(7.dp.toPx(), 12.dp.toPx())
                lineTo(9.dp.toPx(), 12.dp.toPx())
                lineTo(10.5.dp.toPx(), 8.dp.toPx())
                lineTo(11.5.dp.toPx(), 16.dp.toPx())
                lineTo(13.dp.toPx(), 10.dp.toPx())
                lineTo(14.dp.toPx(), 14.dp.toPx())
                lineTo(15.dp.toPx(), 12.dp.toPx())
                lineTo(17.dp.toPx(), 12.dp.toPx())
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
