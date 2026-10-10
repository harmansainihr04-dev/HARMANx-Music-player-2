package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CyberDeepBackground
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.SpatialBlue
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, futuristic Animated Splash Screen for HARMANX Music Player.
 * Displays glowing neon logo with pulsating soundwave equalizer rings.
 * (Phase 3 text reveal is omitted per user preference).
 */
@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Entrance scale & alpha animations
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val overallAlpha = remember { Animatable(1f) }

    // Continuous audio shockwave pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    val waveRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveRotation"
    )

    LaunchedEffect(Unit) {
        // Phase 1: Center Neon Logo Entrance (zoom-in & glow fade-in)
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
        logoScale.animateTo(
            targetValue = 1.05f,
            animationSpec = tween(450, easing = FastOutSlowInEasing)
        )
        logoScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        )

        // Hold pulse animation for visual impact
        delay(400)

        // Phase 4: Smooth transition fade-out into Main Library
        overallAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overallAlpha.value)
            .background(CyberDeepBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Allow instant skip on tap
                onSplashFinished()
            }
            .testTag("animated_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Subtle background radial glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CyberNeonCyan.copy(alpha = 0.12f),
                        CyberElectricPurple.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.75f
                ),
                center = center,
                radius = size.width * 0.75f
            )
        }

        // Concentric Audio Equalizer & Sound Wave Rings
        Canvas(modifier = Modifier.size(340.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 80.dp.toPx()

            // 3 Concentric Expanding Shockwave Rings
            for (i in 0..2) {
                val waveOffset = (pulseProgress.value + (i * 0.33f)) % 1f
                val ringRadius = baseRadius + (waveOffset * 75.dp.toPx())
                val ringAlpha = (1f - waveOffset).coerceIn(0f, 0.7f) * 0.45f

                val ringColor = if (i % 2 == 0) CyberNeonCyan else CyberElectricPurple
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx() * (1f - waveOffset * 0.5f))
                )
            }

            // Circular Equalizer Frequency Bars around the Logo
            val barCount = 36
            val angleStep = (2 * PI / barCount).toFloat()
            val rotRad = (waveRotation.value * PI / 180f).toFloat()

            for (i in 0 until barCount) {
                val angle = i * angleStep + rotRad
                // Dynamic oscillating bar length mimicking audio frequencies
                val waveFactor = sin(i * 0.6f + pulseProgress.value * 2 * PI).toFloat()
                val barLength = 6.dp.toPx() + (waveFactor * 10.dp.toPx()).coerceAtLeast(0f)

                val innerR = baseRadius + 14.dp.toPx()
                val outerR = innerR + barLength

                val startX = center.x + innerR * cos(angle)
                val startY = center.y + innerR * sin(angle)
                val endX = center.x + outerR * cos(angle)
                val endY = center.y + outerR * sin(angle)

                val barColor = if (i % 3 == 0) {
                    SpatialBlue
                } else if (i % 2 == 0) {
                    CyberNeonCyan
                } else {
                    CyberElectricPurple
                }

                drawLine(
                    color = barColor.copy(alpha = 0.75f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Center Glowing Logo Container
        Box(
            modifier = Modifier
                .size(128.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value),
            contentAlignment = Alignment.Center
        ) {
            // Neon Glow Outer Ring
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                CyberNeonCyan,
                                CyberElectricPurple,
                                SpatialBlue,
                                CyberNeonCyan
                            )
                        )
                    )
            )

            // Inner Dark Circle
            Box(
                modifier = Modifier
                    .size(122.dp)
                    .clip(CircleShape)
                    .background(CyberDeepBackground),
                contentAlignment = Alignment.Center
            ) {
                // HARMANX Logo Asset
                Image(
                    painter = painterResource(id = R.drawable.harmanx_music_app_logo_1786194716963),
                    contentDescription = "HARMANX Logo",
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}
