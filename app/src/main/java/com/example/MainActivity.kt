package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.FullPlayerModal
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AuraTheme
import com.example.ui.theme.CyberDeepBackground
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.viewmodel.MusicViewModel

enum class NavTab {
    LIBRARY, PLAYLISTS, EQUALIZER, SETTINGS
}

class MainActivity : ComponentActivity() {
    private val musicViewModel: MusicViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
            } else {
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            }
            if (audioGranted) {
                musicViewModel.scanDeviceAudioFiles()
            }
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AuraTheme {
                AuraMusicApp(
                    viewModel = musicViewModel,
                    onRequestPermissions = {
                        requestStoragePermissions()
                    }
                )
            }
        }

        handleIncomingIntent(intent)

        // Check if permissions already granted, scan immediately
        checkAndTriggerInitialScan()
    }

    private fun checkAndTriggerInitialScan() {
        val hasAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAudioPermission) {
            // Request permissions on launch if not granted
            requestStoragePermissions()
        }
    }

    private fun requestStoragePermissions() {
        try {
            val permissionsToRequest = mutableListOf<String>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                }
            } else {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        try {
            if (intent.action == Intent.ACTION_VIEW) {
                val uri = intent.data
                if (uri != null) {
                    musicViewModel.playFromUri(uri)
                }
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun AuraMusicApp(
    viewModel: MusicViewModel,
    onRequestPermissions: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(NavTab.LIBRARY) }
    var isFullPlayerVisible by remember { mutableStateOf(false) }

    val tracks by viewModel.displayedTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isGaplessEnabled by viewModel.isGaplessEnabled.collectAsStateWithLifecycle()

    val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
    val bassBoostLevel by viewModel.bassBoostLevel.collectAsStateWithLifecycle()
    val surround360Enabled by viewModel.surround360Enabled.collectAsStateWithLifecycle()
    val spatialMode by viewModel.spatialMode.collectAsStateWithLifecycle()
    val surroundStrength by viewModel.surroundStrength.collectAsStateWithLifecycle()
    val isSlowedReverbEnabled by viewModel.isSlowedReverbEnabled.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val bandGains by viewModel.bandGains.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val playlistQueue by viewModel.playlistQueue.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isScanInProgress by viewModel.isScanInProgress.collectAsStateWithLifecycle()
    val lastScanMessage by viewModel.lastScanMessage.collectAsStateWithLifecycle()

    // Step-by-step back press navigation handling
    BackHandler(enabled = isFullPlayerVisible) {
        isFullPlayerVisible = false
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedTab != NavTab.LIBRARY) {
        selectedTab = NavTab.LIBRARY
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedTab == NavTab.LIBRARY && searchQuery.isNotEmpty()) {
        viewModel.setSearchQuery("")
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CyberDeepBackground,
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(CyberSurfaceDark)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Persistent Mini Player when track is active
                if (currentTrack != null && !isFullPlayerVisible) {
                    MiniPlayer(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.playNext() },
                        onPreviousClick = { viewModel.playPrevious() },
                        onExpandClick = { isFullPlayerVisible = true }
                    )
                }

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = CyberSurfaceDark,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == NavTab.LIBRARY && !isFullPlayerVisible,
                        onClick = {
                            selectedTab = NavTab.LIBRARY
                            isFullPlayerVisible = false
                        },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberNeonCyan,
                            selectedTextColor = CyberNeonCyan,
                            indicatorColor = CyberNeonCyan.copy(alpha = 0.15f),
                            unselectedIconColor = CyberTextMuted,
                            unselectedTextColor = CyberTextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_library")
                    )

                    NavigationBarItem(
                        selected = selectedTab == NavTab.PLAYLISTS && !isFullPlayerVisible,
                        onClick = {
                            selectedTab = NavTab.PLAYLISTS
                            isFullPlayerVisible = false
                        },
                        icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlists") },
                        label = { Text("Playlists", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberNeonCyan,
                            selectedTextColor = CyberNeonCyan,
                            indicatorColor = CyberNeonCyan.copy(alpha = 0.15f),
                            unselectedIconColor = CyberTextMuted,
                            unselectedTextColor = CyberTextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_playlists")
                    )

                    NavigationBarItem(
                        selected = selectedTab == NavTab.EQUALIZER && !isFullPlayerVisible,
                        onClick = {
                            selectedTab = NavTab.EQUALIZER
                            isFullPlayerVisible = false
                        },
                        icon = { Icon(Icons.Default.Equalizer, contentDescription = "Equalizer") },
                        label = { Text("Equalizer", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberNeonCyan,
                            selectedTextColor = CyberNeonCyan,
                            indicatorColor = CyberNeonCyan.copy(alpha = 0.15f),
                            unselectedIconColor = CyberTextMuted,
                            unselectedTextColor = CyberTextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_equalizer")
                    )

                    NavigationBarItem(
                        selected = selectedTab == NavTab.SETTINGS && !isFullPlayerVisible,
                        onClick = {
                            selectedTab = NavTab.SETTINGS
                            isFullPlayerVisible = false
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberNeonCyan,
                            selectedTextColor = CyberNeonCyan,
                            indicatorColor = CyberNeonCyan.copy(alpha = 0.15f),
                            unselectedIconColor = CyberTextMuted,
                            unselectedTextColor = CyberTextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavTab.LIBRARY -> LibraryScreen(
                    tracks = tracks,
                    currentPlayingTrack = currentTrack,
                    isPlaying = isPlaying,
                    searchQuery = searchQuery,
                    selectedFilter = selectedFilter,
                    isScanInProgress = isScanInProgress,
                    lastScanMessage = lastScanMessage,
                    playlists = playlists,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onFilterChange = { viewModel.setFilterMode(it) },
                    onTrackClick = { track -> viewModel.playSingleTrack(track) },
                    onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                    onAddToPlaylist = { playlistId, trackId -> viewModel.addTrackToPlaylist(playlistId, trackId) },
                    onScanStorageClick = {
                        onRequestPermissions()
                        viewModel.scanDeviceAudioFiles()
                    },
                    onShuffleAllClick = {
                        if (tracks.isNotEmpty()) {
                            val shuffled = tracks.shuffled()
                            viewModel.playTrackList(shuffled)
                        }
                    },
                    onDeleteTrack = { track -> viewModel.deleteTrack(track) },
                    onAddNewTrack = { title, artist, album, isFlac ->
                        viewModel.addNewTrack(title, artist, album, isFlac)
                    }
                )

                NavTab.PLAYLISTS -> PlaylistsScreen(
                    playlists = playlists,
                    currentPlayingTrack = currentTrack,
                    isPlaying = isPlaying,
                    onCreatePlaylist = { name, desc, colorIdx -> viewModel.createPlaylist(name, desc, colorIdx) },
                    onDeletePlaylist = { playlistId -> viewModel.deletePlaylist(playlistId) },
                    onGetPlaylistTracks = { playlistId -> viewModel.getTracksForPlaylist(playlistId) },
                    onPlayPlaylistTracks = { playlistTracks -> viewModel.playTrackList(playlistTracks) },
                    onTrackClick = { track -> viewModel.playSingleTrack(track) },
                    onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                    onRemoveTrackFromPlaylist = { playlistId, trackId -> viewModel.removeTrackFromPlaylist(playlistId, trackId) }
                )

                NavTab.EQUALIZER -> EqualizerScreen(
                    eqEnabled = eqEnabled,
                    bassBoostLevel = bassBoostLevel,
                    surround360Enabled = surround360Enabled,
                    spatialMode = spatialMode,
                    surroundStrength = surroundStrength,
                    isSlowedReverbEnabled = isSlowedReverbEnabled,
                    playbackSpeed = playbackSpeed,
                    bandGains = bandGains,
                    selectedPreset = selectedPreset,
                    onEqEnabledToggle = { viewModel.setEqEnabled(it) },
                    onBassBoostChange = { viewModel.setBassBoost(it) },
                    onSurround360Toggle = { viewModel.setSurround360Enabled(it) },
                    onSpatialModeSelect = { viewModel.setSpatialMode(it) },
                    onSurroundStrengthChange = { viewModel.setSurroundStrength(it) },
                    onSlowedReverbToggle = { viewModel.setSlowedReverbEnabled(it) },
                    onPlaybackSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onBandGainChange = { index, gain -> viewModel.setBandGain(index, gain) },
                    onPresetSelect = { preset -> viewModel.selectPreset(preset) }
                )

                NavTab.SETTINGS -> SettingsScreen(
                    isGaplessEnabled = isGaplessEnabled,
                    onToggleGapless = { viewModel.toggleGaplessMode() },
                    onScanStorageClick = { viewModel.scanDeviceAudioFiles() }
                )
            }

            // Full Screen Modal Player Overlay
            FullPlayerModal(
                isVisible = isFullPlayerVisible,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                isGaplessEnabled = isGaplessEnabled,
                bassBoostLevel = bassBoostLevel,
                isSlowedReverbEnabled = isSlowedReverbEnabled,
                playlistQueue = playlistQueue,
                onDismiss = { isFullPlayerVisible = false },
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onNextClick = { viewModel.playNext() },
                onPreviousClick = { viewModel.playPrevious() },
                onSeekTo = { viewModel.seekTo(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onToggleGapless = { viewModel.toggleGaplessMode() },
                onToggleSlowedReverb = { viewModel.setSlowedReverbEnabled(!isSlowedReverbEnabled) },
                onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                onOpenEqualizer = {
                    selectedTab = NavTab.EQUALIZER
                    isFullPlayerVisible = false
                }
            )
        }
    }
}
