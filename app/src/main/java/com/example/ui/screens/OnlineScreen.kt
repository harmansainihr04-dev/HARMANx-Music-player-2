package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import com.example.data.network.RadioCategory
import com.example.data.network.isLiveRadio
import com.example.ui.components.TrackArtworkThumbnail
import com.example.ui.theme.CyberDeepBackground
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.theme.DarkRedPrimary

private enum class OnlineModeTab(val label: String) {
    ITUNES_SONGS("iTunes Songs"),
    LIVE_RADIO("Live Radio")
}

@Composable
fun OnlineScreen(
    onlineSongs: List<Track>,
    radioStations: List<Track>,
    currentPlayingTrack: Track?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isSongsLoading: Boolean,
    isRadioLoading: Boolean,
    songsError: String?,
    radioError: String?,
    activeQuery: String,
    selectedRadioCategory: RadioCategory,
    onInitialize: () -> Unit,
    onSearchSongs: (String) -> Unit,
    onSelectRadioCategory: (RadioCategory) -> Unit,
    onSearchRadio: (String) -> Unit,
    onPlayTrackFromList: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(OnlineModeTab.ITUNES_SONGS) }
    var searchInput by remember(selectedTab) {
        mutableStateOf(if (selectedTab == OnlineModeTab.ITUNES_SONGS) activeQuery else "")
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        onInitialize()
    }

    val quickSongChips = remember {
        listOf(
            "Punjabi Hits",
            "Haryanvi",
            "Sidhu Moose Wala",
            "Diljit Dosanjh",
            "Karan Aujla",
            "Bollywood Top",
            "Lo-Fi Chill",
            "English Pop"
        )
    }

    val radioCategories = remember {
        listOf(RadioCategory.PUNJABI, RadioCategory.HARYANVI, RadioCategory.HINDI_BOLLYWOOD)
    }

    val displayedItems = if (selectedTab == OnlineModeTab.ITUNES_SONGS) onlineSongs else radioStations
    val isLoading = if (selectedTab == OnlineModeTab.ITUNES_SONGS) isSongsLoading else isRadioLoading
    val errorMessage = if (selectedTab == OnlineModeTab.ITUNES_SONGS) songsError else radioError

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDeepBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ONLINE MUSIC",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = CyberNeonCyan,
                        letterSpacing = 2.sp
                    )
                )
                Text(
                    text = if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                        "iTunes Search • 30s Hi-Fi Song Previews"
                    } else {
                        "24/7 Live Punjabi, Haryanvi & Desi FM Radio"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(color = CyberTextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                        onSearchSongs(searchInput.ifBlank { activeQuery })
                    } else {
                        if (searchInput.isNotBlank()) onSearchRadio(searchInput)
                        else onSelectRadioCategory(selectedRadioCategory)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CyberSurfaceVariant)
                    .testTag("online_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Online Streams",
                    tint = CyberNeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mode Switcher (iTunes Songs vs Live Radio)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CyberSurfaceDark)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OnlineModeTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val tag = if (tab == OnlineModeTab.ITUNES_SONGS) "online_tab_songs" else "online_tab_radio"
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(listOf(CyberNeonCyan.copy(alpha = 0.25f), CyberElectricPurple.copy(alpha = 0.28f)))
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .then(
                            if (isSelected) Modifier.border(1.dp, CyberNeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            else Modifier
                        )
                        .clickable { selectedTab = tab }
                        .testTag(tag),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (tab == OnlineModeTab.ITUNES_SONGS) Icons.Default.MusicNote else Icons.Default.Radio,
                        contentDescription = null,
                        tint = if (isSelected) CyberNeonCyan else CyberTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) CyberTextPrimary else CyberTextMuted
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            placeholder = {
                Text(
                    text = if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                        "Search Punjabi, Haryanvi, Hindi or English..."
                    } else {
                        "Search Punjabi, Haryanvi, FM station..."
                    },
                    color = CyberTextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberNeonCyan)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchInput.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchInput = ""
                                if (selectedTab == OnlineModeTab.LIVE_RADIO) {
                                    onSelectRadioCategory(selectedRadioCategory)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = CyberTextMuted)
                        }
                    }
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                                onSearchSongs(searchInput)
                            } else {
                                onSearchRadio(searchInput)
                            }
                        },
                        modifier = Modifier.testTag("online_search_button")
                    ) {
                        Icon(Icons.Default.Public, contentDescription = "Go Search", tint = CyberElectricPurple)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                        onSearchSongs(searchInput)
                    } else {
                        onSearchRadio(searchInput)
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberNeonCyan,
                unfocusedBorderColor = CyberSurfaceVariant,
                focusedTextColor = CyberTextPrimary,
                unfocusedTextColor = CyberTextPrimary,
                focusedContainerColor = CyberSurfaceDark,
                unfocusedContainerColor = CyberSurfaceDark
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("online_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Discovery / Category Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                items(quickSongChips) { chip ->
                    val selected = activeQuery.equals(chip, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            searchInput = chip
                            onSearchSongs(chip)
                        },
                        label = {
                            Text(
                                text = chip,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberNeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberNeonCyan,
                            containerColor = CyberSurfaceDark,
                            labelColor = CyberTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = CyberSurfaceVariant,
                            selectedBorderColor = CyberNeonCyan
                        )
                    )
                }
            } else {
                items(radioCategories) { cat ->
                    val selected = selectedRadioCategory == cat && searchInput.isBlank()
                    FilterChip(
                        selected = selected,
                        onClick = {
                            searchInput = ""
                            onSelectRadioCategory(cat)
                        },
                        label = {
                            Text(
                                text = cat.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DarkRedPrimary.copy(alpha = 0.22f),
                            selectedLabelColor = Color(0xFFFF8A80),
                            containerColor = CyberSurfaceDark,
                            labelColor = CyberTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = CyberSurfaceVariant,
                            selectedBorderColor = DarkRedPrimary
                        )
                    )
                }
            }
        }

        AnimatedVisibility(visible = isBuffering) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = CyberNeonCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Connecting & buffering stream with HARMANX Audio Engine...",
                    style = MaterialTheme.typography.labelSmall.copy(color = CyberNeonCyan)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Content State
        when {
            isLoading && displayedItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyberNeonCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                                "Fetching songs from iTunes..."
                            } else {
                                "Tuning Punjabi & Haryanvi Live Radio..."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(color = CyberTextSecondary)
                        )
                    }
                }
            }

            errorMessage != null && displayedItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = CyberTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Could not load online music",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall.copy(color = CyberTextSecondary)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (selectedTab == OnlineModeTab.ITUNES_SONGS) {
                                    onSearchSongs(searchInput.ifBlank { activeQuery })
                                } else {
                                    onSelectRadioCategory(selectedRadioCategory)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberNeonCyan),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Retry", color = CyberDeepBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            displayedItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedTab == OnlineModeTab.ITUNES_SONGS) Icons.Default.MusicNote else Icons.Default.Radio,
                            contentDescription = null,
                            tint = CyberTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No results found. Try another search!",
                            style = MaterialTheme.typography.bodyMedium.copy(color = CyberTextSecondary)
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                ) {
                    itemsIndexed(displayedItems, key = { index, t -> "${t.id}_${t.audioPath}_$index" }) { index, track ->
                        val isCurrent = currentPlayingTrack?.audioPath == track.audioPath
                        OnlineTrackItemCard(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            onClick = { onPlayTrackFromList(displayedItems, index) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackItemCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isLive = track.isLiveRadio

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isCurrent) {
                    Modifier.border(1.5.dp, CyberNeonCyan, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .testTag("online_track_item_${track.id}"),
        color = if (isCurrent) CyberSurfaceVariant else CyberSurfaceDark,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkThumbnail(
                track = track,
                modifier = Modifier.size(54.dp),
                iconSize = 26.dp,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) CyberNeonCyan else CyberTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isLive) DarkRedPrimary.copy(alpha = 0.25f)
                                else CyberElectricPurple.copy(alpha = 0.22f)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isLive) "● LIVE" else "30s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isLive) Color(0xFFFF8A80) else CyberElectricPurple
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CyberTextSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${track.album} • ${track.bitrate}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CyberTextMuted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Now Playing",
                    tint = CyberNeonCyan,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(20.dp)
                )
            }

            if (!isLive) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Track Options",
                            tint = CyberTextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(CyberSurfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Next", color = CyberTextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = CyberNeonCyan)
                            },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Queue", color = CyberTextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = CyberElectricPurple)
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            }
                        )
                    }
                }
            }
        }
    }
}
