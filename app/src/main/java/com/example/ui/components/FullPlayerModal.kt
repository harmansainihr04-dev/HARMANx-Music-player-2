package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RepeatMode
import com.example.data.model.Track
import com.example.ui.theme.CyberDeepBackground
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.theme.DarkRedPrimary
import com.example.ui.theme.DarkRedSecondary
import com.example.ui.theme.FlacBadgeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerModal(
    isVisible: Boolean,
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    isGaplessEnabled: Boolean,
    bassBoostLevel: Float,
    isSlowedReverbEnabled: Boolean = false,
    playlistQueue: List<Track>,
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleGapless: () -> Unit,
    onToggleSlowedReverb: () -> Unit = {},
    onToggleFavorite: (Track) -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible || currentTrack == null) return

    var showQueueSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_player_modal"),
        color = CyberDeepBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // YouTube Music Style Ambient Top Bloom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkRedPrimary.copy(alpha = if (isPlaying) 0.22f else 0.12f),
                                DarkRedSecondary.copy(alpha = if (isPlaying) 0.10f else 0.04f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("full_player_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Player",
                        tint = CyberTextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyberTextMuted,
                            letterSpacing = 2.sp
                        )
                    )
                    Text(
                        text = currentTrack.sampleRate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = CyberNeonCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }

                IconButton(
                    onClick = { onToggleFavorite(currentTrack) },
                    modifier = Modifier.testTag("full_player_favorite")
                ) {
                    Icon(
                        imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentTrack.isFavorite) Color(0xFFFF3D00) else CyberTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Artwork Container with Live Audio Visualizer Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(CyberSurfaceDark, CyberDeepBackground)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background actual song album artwork / gradient
                TrackArtworkThumbnail(
                    track = currentTrack,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 72.dp,
                    shape = RoundedCornerShape(24.dp)
                )

                // Dark atmospheric translucent glass overlay for crisp visualizer contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Live Dynamic Audio Visualizer layered directly over the thumbnail
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    VisualizerCanvas(
                        isPlaying = isPlaying,
                        bassBoostLevel = bassBoostLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Audio Spectrum Visualizer",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track Meta Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${currentTrack.artist} — ${currentTrack.album}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = CyberTextSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Format: ${if (currentTrack.isFlac) "FLAC Lossless Audio" else "Audio Track"} | Bitrate: ${currentTrack.bitrate}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CyberTextMuted,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seekbar
            Column(modifier = Modifier.fillMaxWidth()) {
                val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        onSeekTo((newProgress * durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = CyberNeonCyan,
                        activeTrackColor = CyberNeonCyan,
                        inactiveTrackColor = CyberSurfaceDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("full_player_seekbar")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPositionMs),
                        style = MaterialTheme.typography.labelSmall.copy(color = CyberTextMuted)
                    )
                    Text(
                        text = formatDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(color = CyberTextMuted)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Playback Main Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.testTag("full_player_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) CyberNeonCyan else CyberTextMuted
                    )
                }

                // Previous Button
                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("full_player_prev")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = CyberTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(CyberNeonCyan, CyberElectricPurple)
                            )
                        )
                        .clickable { onPlayPauseClick() }
                        .testTag("full_player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = CyberDeepBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("full_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = CyberTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Repeat Mode Button
                IconButton(
                    onClick = onCycleRepeatMode,
                    modifier = Modifier.testTag("full_player_repeat")
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) CyberNeonCyan else CyberTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bottom Audio Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slowed + Reverb Toggle Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSlowedReverbEnabled) DarkRedPrimary.copy(alpha = 0.35f) else CyberSurfaceDark)
                        .clickable { onToggleSlowedReverb() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("slowed_reverb_fullplayer_badge")
                ) {
                    Text(
                        text = if (isSlowedReverbEnabled) "✨ Slowed ON" else "Slowed+Reverb",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSlowedReverbEnabled) DarkRedPrimary else CyberTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Gapless Toggle Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isGaplessEnabled) CyberElectricPurple.copy(alpha = 0.25f) else CyberSurfaceDark)
                        .clickable { onToggleGapless() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("gapless_toggle_badge")
                ) {
                    Text(
                        text = if (isGaplessEnabled) "⚡ Gapless ON" else "Gapless OFF",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isGaplessEnabled) CyberElectricPurple else CyberTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Equalizer Button
                IconButton(
                    onClick = {
                        onDismiss()
                        onOpenEqualizer()
                    },
                    modifier = Modifier.testTag("full_player_eq_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = CyberNeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Queue Button
                IconButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.testTag("full_player_queue_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = CyberTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Up Next Queue Modal Sheet
        if (showQueueSheet) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = sheetState,
                containerColor = CyberSurfaceDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "PLAYLIST QUEUE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyberNeonCyan,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(playlistQueue) { idx, track ->
                            val isThisPlaying = track.id == currentTrack.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isThisPlaying) CyberNeonCyan else CyberTextMuted,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.width(32.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isThisPlaying) CyberNeonCyan else CyberTextPrimary,
                                            fontWeight = if (isThisPlaying) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall.copy(color = CyberTextSecondary)
                                    )
                                }

                                if (track.isFlac) {
                                    Text(
                                        text = "FLAC",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FlacBadgeGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
