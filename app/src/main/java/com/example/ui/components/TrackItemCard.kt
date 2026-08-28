package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.theme.FlacBadgeGreen

@Composable
fun TrackItemCard(
    track: Track,
    isPlaying: Boolean,
    onTrackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: (Long) -> Unit = {},
    onDeleteTrack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistSubmenu by remember { mutableStateOf(false) }

    val gradientColors = when (track.artworkColorIndex % 5) {
        0 -> listOf(CyberNeonCyan, CyberElectricPurple)
        1 -> listOf(Color(0xFFFF3D00), Color(0xFFFFB703))
        2 -> listOf(Color(0xFF00E676), Color(0xFF00B0FF))
        3 -> listOf(Color(0xFFD500F9), Color(0xFF651FFF))
        else -> listOf(CyberElectricPurple, CyberNeonCyan)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onTrackClick() }
            .testTag("track_item_${track.id}"),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else CyberSurfaceDark,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork Badge with Actual Thumbnail support
            TrackArtworkThumbnail(
                track = track,
                modifier = Modifier.size(52.dp),
                iconSize = 26.dp,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Track Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPlaying) CyberNeonCyan else CyberTextPrimary,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.padding(top = 2.dp))

                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CyberTextSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${track.sampleRate} • ${formatDuration(track.durationMs)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CyberTextMuted,
                        fontSize = 11.sp
                    )
                )
            }

            // Favorite Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("favorite_btn_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (track.isFavorite) Color(0xFFFF3D00) else CyberTextMuted
                )
            }

            // More Options Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("track_menu_btn_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = CyberTextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(CyberSurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = CyberTextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = CyberNeonCyan)
                        },
                        onClick = {
                            showMenu = false
                            showPlaylistSubmenu = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Audio Tech Details", color = CyberTextPrimary, fontWeight = FontWeight.Bold)
                                Text("${track.sampleRate} | ${track.bitrate}", color = CyberTextSecondary, fontSize = 11.sp)
                            }
                        },
                        onClick = { showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Song", color = Color(0xFFFF5252)) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                        },
                        onClick = {
                            showMenu = false
                            onDeleteTrack()
                        }
                    )
                }

                // Playlist Selection Dialog/Submenu
                if (showPlaylistSubmenu) {
                    DropdownMenu(
                        expanded = showPlaylistSubmenu,
                        onDismissRequest = { showPlaylistSubmenu = false },
                        modifier = Modifier.background(CyberSurfaceDark)
                    ) {
                        if (playlists.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No playlists available", color = CyberTextMuted) },
                                onClick = { showPlaylistSubmenu = false }
                            )
                        } else {
                            playlists.forEach { playlist ->
                                DropdownMenuItem(
                                    text = { Text(playlist.name, color = CyberTextPrimary) },
                                    onClick = {
                                        onAddToPlaylist(playlist.id)
                                        showPlaylistSubmenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
