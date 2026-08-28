package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerEngine
import com.example.audio.EqPreset
import com.example.audio.ReverbEffect
import com.example.audio.RepeatMode
import com.example.audio.SpatialMode
import com.example.audio.StorageScanner
import com.example.data.db.AuraDatabase
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FilterMode {
    ALL, FLAC_ONLY, FAVORITES
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AuraDatabase.getInstance(application)
    private val storageScanner = StorageScanner(application)
    val repository = MusicRepository(db.trackDao(), db.playlistDao(), storageScanner)
    val playerEngine = AudioPlayerEngine.getInstance(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FilterMode.ALL)
    val selectedFilter: StateFlow<FilterMode> = _selectedFilter.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combine search and filter modes reactively
    val displayedTracks: StateFlow<List<Track>> = combine(
        _searchQuery,
        _selectedFilter
    ) { query, filter ->
        query to filter
    }.flatMapLatest { (query, filter) ->
        when (filter) {
            FilterMode.ALL -> repository.searchTracks(query)
            FilterMode.FLAC_ONLY -> repository.flacTracks
            FilterMode.FAVORITES -> repository.favoriteTracks
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player state forwards
    val currentTrack = playerEngine.currentTrack
    val isPlaying = playerEngine.isPlaying
    val currentPositionMs = playerEngine.currentPositionMs
    val durationMs = playerEngine.durationMs
    val isShuffle = playerEngine.isShuffle
    val repeatMode = playerEngine.repeatMode
    val isGaplessEnabled = playerEngine.isGaplessEnabled

    // Equalizer & Spatial Audio state forwards
    val eqEnabled = playerEngine.eqEnabled
    val bassBoostLevel = playerEngine.bassBoostLevel
    val surround360Enabled = playerEngine.surround360Enabled
    val spatialMode = playerEngine.spatialMode
    val surroundStrength = playerEngine.surroundStrength
    val selectedReverb = playerEngine.selectedReverb
    val isSlowedReverbEnabled = playerEngine.isSlowedReverbEnabled
    val playbackSpeed = playerEngine.playbackSpeed
    val bandGains = playerEngine.bandGains
    val selectedPreset = playerEngine.selectedPreset
    val playlistQueue = playerEngine.playlistQueue

    private val _isScanInProgress = MutableStateFlow(false)
    val isScanInProgress: StateFlow<Boolean> = _isScanInProgress.asStateFlow()

    private val _lastScanMessage = MutableStateFlow<String?>(null)
    val lastScanMessage: StateFlow<String?> = _lastScanMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializePreloadedData()
        }
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) {
        playerEngine.setQueue(tracks, startIndex)
    }

    fun playSingleTrack(track: Track) {
        val currentList = displayedTracks.value
        val index = currentList.indexOfFirst { it.id == track.id }
        if (index != -1) {
            playerEngine.setQueue(currentList, index)
        } else {
            playerEngine.setQueue(listOf(track), 0)
        }
    }

    fun playFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                var title = "External Audio"
                var artist = "Unknown Artist"
                var album = "Audio File"
                var durationMs = 0L
                var isFlac = false

                // Try to resolve filename from ContentResolver
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            val displayName = cursor.getString(nameIndex)
                            if (!displayName.isNullOrBlank()) {
                                title = displayName.substringBeforeLast(".")
                                if (displayName.endsWith(".flac", ignoreCase = true)) {
                                    isFlac = true
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val metaTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val metaArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val metaAlbum = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val metaDuration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val mime = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                    if (!metaTitle.isNullOrBlank()) title = metaTitle
                    if (!metaArtist.isNullOrBlank()) artist = metaArtist
                    if (!metaAlbum.isNullOrBlank()) album = metaAlbum
                    durationMs = metaDuration?.toLongOrNull() ?: 180000L
                    if (mime?.contains("flac", ignoreCase = true) == true) {
                        isFlac = true
                    }
                    retriever.release()
                } catch (_: Exception) {}

                val externalTrack = Track(
                    id = System.currentTimeMillis(),
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = if (durationMs > 0) durationMs else 180000L,
                    audioPath = uri.toString(),
                    isFlac = isFlac,
                    sampleRate = if (isFlac) "24-bit / 96.0 kHz" else "16-bit / 44.1 kHz",
                    bitrate = if (isFlac) "2830 kbps" else "320 kbps",
                    fileSizeBytes = 25_000_000,
                    isFavorite = false,
                    artworkColorIndex = (0..5).random()
                )
                playerEngine.setQueue(listOf(externalTrack), 0)
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error opening URI", e)
            }
        }
    }

    fun togglePlayPause() = playerEngine.togglePlayPause()
    fun playNext() = playerEngine.playNext()
    fun playPrevious() = playerEngine.playPrevious()
    fun seekTo(positionMs: Long) = playerEngine.seekTo(positionMs)
    fun toggleShuffle() = playerEngine.toggleShuffle()
    fun cycleRepeatMode() = playerEngine.cycleRepeatMode()
    fun toggleGaplessMode() = playerEngine.toggleGaplessMode()

    fun setEqEnabled(enabled: Boolean) = playerEngine.setEqEnabled(enabled)
    fun setBassBoost(level: Float) = playerEngine.setBassBoost(level)
    fun setSurround360Enabled(enabled: Boolean) = playerEngine.setSurround360Enabled(enabled)
    fun setSpatialMode(mode: SpatialMode) = playerEngine.setSpatialMode(mode)
    fun setSurroundStrength(strength: Float) = playerEngine.setSurroundStrength(strength)
    fun setReverbEffect(effect: ReverbEffect) = playerEngine.setReverbEffect(effect)
    fun setSlowedReverbEnabled(enabled: Boolean) = playerEngine.setSlowedReverbEnabled(enabled)
    fun setPlaybackSpeed(speed: Float) = playerEngine.setPlaybackSpeed(speed)
    fun cycleEqPreset() = playerEngine.cycleEqPreset()
    fun cycleBassBoost() = playerEngine.cycleBassBoost()
    fun setBandGain(bandIndex: Int, gainDb: Float) = playerEngine.setBandGain(bandIndex, gainDb)
    fun selectPreset(preset: EqPreset) = playerEngine.selectPreset(preset)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterMode(mode: FilterMode) {
        _selectedFilter.value = mode
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track.id, track.isFavorite)
        }
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            repository.deleteTrack(track.id)
            if (currentTrack.value?.id == track.id) {
                playNext()
            }
        }
    }

    fun addNewTrack(title: String, artist: String, album: String, isFlac: Boolean) {
        viewModelScope.launch {
            repository.addNewTrack(
                title = if (title.isBlank()) "New Audio Track" else title,
                artist = if (artist.isBlank()) "Unknown Artist" else artist,
                album = if (album.isBlank()) "Single Album" else album,
                isFlac = isFlac
            )
        }
    }

    fun createPlaylist(name: String, description: String, coverIndex: Int) {
        viewModelScope.launch {
            repository.createPlaylist(name, description, coverIndex)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun getTracksForPlaylist(playlistId: Long) = repository.getTracksForPlaylist(playlistId)

    fun scanDeviceAudioFiles() {
        viewModelScope.launch {
            _isScanInProgress.value = true
            _lastScanMessage.value = "Scanning local storage for FLAC & audio files..."
            val count = repository.scanStorageForLocalAudio()
            _isScanInProgress.value = false
            _lastScanMessage.value = if (count > 0) "Found $count new local audio tracks!" else "Scan completed. Storage up to date."
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
