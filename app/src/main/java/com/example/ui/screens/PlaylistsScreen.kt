package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.TrackItemCard
import com.example.ui.theme.CyberDeepBackground
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    currentPlayingTrack: Track?,
    isPlaying: Boolean,
    onCreatePlaylist: (String, String, Int) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onGetPlaylistTracks: (Long) -> Flow<List<Track>>,
    onPlayPlaylistTracks: (List<Track>) -> Unit,
    onTrackClick: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRemoveTrackFromPlaylist: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPlaylistForDetail by remember { mutableStateOf<Playlist?>(null) }

    BackHandler(enabled = selectedPlaylistForDetail != null) {
        selectedPlaylistForDetail = null
    }

    val gradientPresets = listOf(
        listOf(CyberNeonCyan, CyberElectricPurple),
        listOf(Color(0xFFFF3D00), Color(0xFFFFB703)),
        listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
        listOf(Color(0xFFD500F9), Color(0xFF651FFF))
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PLAYLISTS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = CyberNeonCyan,
                            letterSpacing = 2.sp
                        )
                    )
                    Text(
                        text = "Custom FLAC Collections",
                        style = MaterialTheme.typography.labelSmall.copy(color = CyberTextSecondary)
                    )
                }

                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberNeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("create_playlist_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Playlist", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlists Grid
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No playlists created yet",
                        style = MaterialTheme.typography.bodyLarge.copy(color = CyberTextMuted)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val gradient = gradientPresets[playlist.coverGradientIndex % gradientPresets.size]

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedPlaylistForDetail = playlist }
                                .testTag("playlist_card_${playlist.id}"),
                            colors = CardDefaults.cardColors(containerColor = CyberSurfaceDark)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(gradient.map { it.copy(alpha = 0.35f) }))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Brush.linearGradient(gradient)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (playlist.name.contains("📁") || playlist.name.contains("Folder", ignoreCase = true)) {
                                                    Icons.Default.Folder
                                                } else {
                                                    Icons.Default.PlaylistPlay
                                                },
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }

                                        if (!playlist.isSystemPlaylist) {
                                            IconButton(
                                                onClick = { onDeletePlaylist(playlist.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Playlist",
                                                    tint = CyberTextMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = CyberTextPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (playlist.description.isNotEmpty()) {
                                            Text(
                                                text = playlist.description,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = CyberTextSecondary,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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

        // Create Playlist Dialog
        if (showCreateDialog) {
            var playlistName by remember { mutableStateOf("") }
            var playlistDesc by remember { mutableStateOf("") }
            var selectedGradientIndex by remember { mutableIntStateOf(0) }

            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                containerColor = CyberSurfaceDark,
                title = { Text("Create Custom Playlist", color = CyberTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text("Playlist Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberNeonCyan,
                                unfocusedBorderColor = CyberSurfaceVariant,
                                focusedTextColor = CyberTextPrimary,
                                unfocusedTextColor = CyberTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playlist_name_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = playlistDesc,
                            onValueChange = { playlistDesc = it },
                            label = { Text("Description (Optional)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberNeonCyan,
                                unfocusedBorderColor = CyberSurfaceVariant,
                                focusedTextColor = CyberTextPrimary,
                                unfocusedTextColor = CyberTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Cover Style Accent:", style = MaterialTheme.typography.labelSmall, color = CyberTextSecondary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            gradientPresets.forEachIndexed { idx, grad ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Brush.linearGradient(grad))
                                        .clickable { selectedGradientIndex = idx }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playlistName.isNotBlank()) {
                                onCreatePlaylist(playlistName.trim(), playlistDesc.trim(), selectedGradientIndex)
                                showCreateDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberNeonCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("save_playlist_btn")
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel", color = CyberTextMuted)
                    }
                }
            )
        }

        // Selected Playlist Detail Bottom Sheet View
        selectedPlaylistForDetail?.let { playlist ->
            val playlistTracksState = onGetPlaylistTracks(playlist.id).collectAsState(initial = emptyList())
            val playlistTracks = playlistTracksState.value
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { selectedPlaylistForDetail = null },
                sheetState = sheetState,
                containerColor = CyberSurfaceDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTextPrimary
                                )
                            )
                            if (playlist.description.isNotEmpty()) {
                                Text(
                                    text = playlist.description,
                                    style = MaterialTheme.typography.bodySmall.copy(color = CyberTextSecondary)
                                )
                            }
                            Text(
                                text = "${playlistTracks.size} tracks",
                                style = MaterialTheme.typography.labelSmall.copy(color = CyberTextMuted)
                            )
                        }

                        if (playlistTracks.isNotEmpty()) {
                            Button(
                                onClick = {
                                    onPlayPlaylistTracks(playlistTracks)
                                    selectedPlaylistForDetail = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberNeonCyan, contentColor = Color.Black),
                                modifier = Modifier.testTag("play_playlist_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text("Play All", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (playlistTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No tracks added to this playlist yet.", color = CyberTextMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(playlistTracks, key = { it.id }) { track ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        TrackItemCard(
                                            track = track,
                                            isPlaying = currentPlayingTrack?.id == track.id && isPlaying,
                                            onTrackClick = { onTrackClick(track) },
                                            onToggleFavorite = { onToggleFavorite(track) }
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveTrackFromPlaylist(playlist.id, track.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove from playlist",
                                            tint = CyberTextMuted
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
