package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

@Composable
fun VisualizerCanvas(
    isPlaying: Boolean,
    bassBoostLevel: Float,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val twoPi = (2.0 * Math.PI).toFloat()
        while (isActive) {
            delay(33L) // Smooth ~30fps spectrum update with zero battery burn when paused
            phase = (phase + 0.17f) % twoPi
        }
    }

    val gradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                CyberNeonCyan.copy(alpha = 0.30f),
                CyberElectricPurple.copy(alpha = 0.30f),
                Color(0xFFFF3D00).copy(alpha = 0.30f)
            )
        )
    }

    Canvas(
        modifier = modifier.fillMaxWidth()
    ) {
        val barCount = 32
        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.65f
        val gap = (totalWidth / barCount) * 0.35f
        val maxHeight = size.height

        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount
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
