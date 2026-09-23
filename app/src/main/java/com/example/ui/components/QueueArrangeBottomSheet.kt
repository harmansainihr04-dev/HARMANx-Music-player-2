package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.theme.DarkRedPrimary
import com.example.ui.theme.FlacBadgeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueArrangeBottomSheet(
    playlistQueue: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onReorderQueue: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val totalDurationMs = playlistQueue.sumOf { it.durationMs }
    val totalMinutes = totalDurationMs / 1000 / 60
    val totalSeconds = (totalDurationMs / 1000) % 60

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberSurfaceDark,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header: Title + Queue Count + Clear Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = CyberNeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PLAYLIST QUEUE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyberNeonCyan,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "${playlistQueue.size} songs • ${totalMinutes}m ${totalSeconds}s",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = CyberTextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                if (playlistQueue.size > 1) {
                    TextButton(
                        onClick = onClearQueue,
                        colors = ButtonDefaults.textButtonColors(contentColor = DarkRedPrimary),
                        modifier = Modifier.testTag("clear_queue_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Queue",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Guidance hint for user
            Text(
                text = "Tap song to play • Use ↑ & ↓ to arrange play order",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = CyberTextMuted,
                    fontSize = 10.sp
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            if (playlistQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty.\nTap the three dots (⋮) on any song to 'Play Next' or 'Add to Queue'.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = CyberTextMuted),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(playlistQueue, key = { idx, track -> "${track.id}_$idx" }) { idx, track ->
                        val isThisPlaying = track.id == currentTrack?.id

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPlayTrackAt(idx) }
                                .testTag("queue_item_$idx"),
                            color = if (isThisPlaying) CyberSurfaceVariant else CyberSurfaceDark,
                            border = if (isThisPlaying) {
                                androidx.compose.foundation.BorderStroke(1.dp, CyberNeonCyan.copy(alpha = 0.8f))
                            } else {
                                androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder.copy(alpha = 0.5f))
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Index or Playing Indicator
                                Box(
                                    modifier = Modifier.width(30.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isThisPlaying && isPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Playing",
                                            tint = CyberNeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else if (isThisPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Selected",
                                            tint = CyberNeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = CyberTextMuted,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Track info
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isThisPlaying) CyberNeonCyan else CyberTextPrimary,
                                                fontWeight = if (isThisPlaying) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (track.isFlac) {
                                            Spacer(modifier = Modifier.width(6.dp))
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

                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = CyberTextSecondary,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Queue Arrange Controls: Move Up, Move Down, Remove
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Move Up
                                    IconButton(
                                        onClick = { onReorderQueue(idx, idx - 1) },
                                        enabled = idx > 0,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("queue_move_up_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (idx > 0) CyberNeonCyan else CyberTextMuted.copy(alpha = 0.3f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = { onReorderQueue(idx, idx + 1) },
                                        enabled = idx < playlistQueue.size - 1,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("queue_move_down_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (idx < playlistQueue.size - 1) CyberNeonCyan else CyberTextMuted.copy(alpha = 0.3f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Remove from Queue
                                    IconButton(
                                        onClick = { onRemoveFromQueue(idx) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("queue_remove_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = CyberTextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
