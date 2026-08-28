package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.theme.DarkRedPrimary
import com.example.ui.theme.DarkRedSecondary
import com.example.ui.theme.FlacBadgeGreen
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(currentTrack.id) {
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount * 0.75f)
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            val drag = offsetX.value
                            when {
                                drag < -65f -> {
                                    // Swipe Left -> Next Track
                                    offsetX.animateTo(-250f, animationSpec = tween(120))
                                    onNextClick()
                                    offsetX.snapTo(250f)
                                    offsetX.animateTo(0f, animationSpec = spring())
                                }
                                drag > 65f -> {
                                    // Swipe Right -> Previous Track
                                    offsetX.animateTo(250f, animationSpec = tween(120))
                                    onPreviousClick()
                                    offsetX.snapTo(-250f)
                                    offsetX.animateTo(0f, animationSpec = spring())
                                }
                                else -> {
                                    // Return to center
                                    offsetX.animateTo(0f, animationSpec = spring())
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                    }
                )
            }
            .clickable { onExpandClick() }
            .testTag("mini_player"),
        color = CyberSurfaceVariant,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            // Subtle Ambient Glow Behind Mini-Player Controls
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DarkRedPrimary.copy(alpha = 0.16f),
                                DarkRedSecondary.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column {
                // Live Progress Bar Line
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = CyberNeonCyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sliding Artwork + Title + Artist section
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                translationX = offsetX.value
                                alpha = (1f - (abs(offsetX.value) / 320f)).coerceIn(0.2f, 1f)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album Art Thumbnail (Slides with gesture)
                        TrackArtworkThumbnail(
                            track = currentTrack,
                            modifier = Modifier.size(44.dp),
                            iconSize = 22.dp,
                            shape = CircleShape
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title and Artist (Slides with gesture)
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CyberTextPrimary,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (currentTrack.isFlac) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(FlacBadgeGreen.copy(alpha = 0.25f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "FLAC",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = FlacBadgeGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Text(
                                text = currentTrack.artist,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CyberTextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Static Quick Action Controls
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.testTag("mini_player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = CyberNeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.testTag("mini_player_next")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = CyberTextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
