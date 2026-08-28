package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import kotlin.math.sin

@Composable
fun VisualizerCanvas(
    isPlaying: Boolean,
    bassBoostLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val barCount = 32
        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.65f
        val gap = (totalWidth / barCount) * 0.35f
        val maxHeight = size.height

        val gradient = Brush.verticalGradient(
            colors = listOf(CyberNeonCyan, CyberElectricPurple, Color(0xFFFF3D00))
        )

        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount
            // Simulate frequency spectrum height
            val bassFactor = if (i < 8) (1f + bassBoostLevel * 1.5f) else 1f
            val waveHeight = if (isPlaying) {
                val sinValue = sin(phase + i * 0.35f)
                val h = (sinValue * 0.4f + 0.5f) * maxHeight * bassFactor * (0.3f + 0.7f * (1f - normalizedIndex * 0.5f))
                h.coerceIn(12.dp.toPx(), maxHeight)
            } else {
                8.dp.toPx()
            }

            val x = i * (barWidth + gap) + gap / 2
            val y = maxHeight - waveHeight

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, waveHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
