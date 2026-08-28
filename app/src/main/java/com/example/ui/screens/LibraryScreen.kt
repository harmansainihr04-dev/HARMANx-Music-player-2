package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.components.TrackItemCard
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.theme.DarkRedPrimary
import com.example.ui.theme.DarkRedSecondary
import com.example.ui.theme.FlacBadgeGreen
import com.example.ui.viewmodel.FilterMode

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    currentPlayingTrack: Track?,
    isPlaying: Boolean,
    searchQuery: String,
    selectedFilter: FilterMode,
    isScanInProgress: Boolean,
    lastScanMessage: String?,
    playlists: List<Playlist>,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (FilterMode) -> Unit,
    onTrackClick: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onScanStorageClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onDeleteTrack: (Track) -> Unit = {},
    onAddNewTrack: (title: String, artist: String, album: String, isFlac: Boolean) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // YouTube Music Style Subtle Top Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkRedPrimary.copy(alpha = if (isPlaying && currentPlayingTrack != null) 0.14f else 0.07f),
                            DarkRedSecondary.copy(alpha = if (isPlaying && currentPlayingTrack != null) 0.06f else 0.02f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
        Spacer(modifier = Modifier.height(16.dp))

        // App Brand Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.harmanx_music_app_logo_1786194716963),
                contentDescription = "HARMANX Music Logo",
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, CyberNeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HARMANX MUSIC",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = CyberNeonCyan,
                        letterSpacing = 1.2.sp
                    )
                )
                Text(
                    text = "Simple Audio & MP3 Player",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = CyberTextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
            placeholder = { Text("Search songs, artists, albums...", color = CyberTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = CyberTextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberNeonCyan,
                unfocusedBorderColor = CyberSurfaceVariant,
                focusedContainerColor = CyberSurfaceDark,
                unfocusedContainerColor = CyberSurfaceDark,
                focusedTextColor = CyberTextPrimary,
                unfocusedTextColor = CyberTextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == FilterMode.ALL,
                    onClick = { onFilterChange(FilterMode.ALL) },
                    label = { Text("All Tracks (${tracks.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberNeonCyan,
                        selectedLabelColor = Color.White,
                        containerColor = CyberSurfaceDark,
                        labelColor = CyberTextSecondary
                    ),
                    modifier = Modifier.testTag("filter_all")
                )
            }

            item {
                FilterChip(
                    selected = selectedFilter == FilterMode.FAVORITES,
                    onClick = { onFilterChange(FilterMode.FAVORITES) },
                    label = { Text("Liked Songs") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF3D00),
                        selectedLabelColor = Color.White,
                        containerColor = CyberSurfaceDark,
                        labelColor = CyberTextSecondary
                    ),
                    modifier = Modifier.testTag("filter_favorites")
                )
            }
        }

        // Scan Toast / Message if available
        lastScanMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                color = CyberSurfaceVariant
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall.copy(color = CyberNeonCyan),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Shuffle All & Queue Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRACKS LIST",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = CyberTextMuted,
                    letterSpacing = 1.sp
                )
            )

            Button(
                onClick = onShuffleAllClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberSurfaceVariant,
                    contentColor = CyberNeonCyan
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("shuffle_all_btn")
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shuffle All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tracks List
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = CyberNeonCyan,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No audio files found yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = CyberTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allow Storage & Notification permissions to automatically detect all songs on your device.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = CyberTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onScanStorageClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberNeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("grant_permission_scan_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isScanInProgress) "Scanning Device..." else "Allow Permissions & Scan",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isThisPlaying = currentPlayingTrack?.id == track.id && isPlaying
                    TrackItemCard(
                        track = track,
                        isPlaying = isThisPlaying,
                        onTrackClick = { onTrackClick(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        playlists = playlists,
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track.id) },
                        onDeleteTrack = { onDeleteTrack(track) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
}
