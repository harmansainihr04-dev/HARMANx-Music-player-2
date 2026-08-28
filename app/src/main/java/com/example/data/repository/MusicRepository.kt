package com.example.data.repository

import com.example.audio.StorageScanner
import com.example.data.db.PlaylistDao
import com.example.data.db.TrackDao
import com.example.data.model.Playlist
import com.example.data.model.PlaylistTrackCrossRef
import com.example.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class MusicRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val storageScanner: StorageScanner
) {
    val allTracks: Flow<List<Track>> = trackDao.getAllTracks()
    val flacTracks: Flow<List<Track>> = trackDao.getFlacTracks()
    val favoriteTracks: Flow<List<Track>> = trackDao.getFavoriteTracks()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    suspend fun initializePreloadedData() {
        try {
            // Delete all dummy synth/preloaded tracks
            trackDao.deleteDummyTracks()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun scanStorageForLocalAudio(): Int {
        val scanResult = storageScanner.scanAudioFilesWithFolders()
        val scanned = scanResult.allTracks
        if (scanned.isNotEmpty()) {
            val existingTracks = trackDao.getTrackCount()
            if (existingTracks == 0) {
                trackDao.insertAll(scanned)
            } else {
                for (track in scanned) {
                    val existing = trackDao.getTrackByPath(track.audioPath)
                    if (existing == null) {
                        trackDao.insertTrack(track)
                    }
                }
            }

            // Automatically detect device music folders and sync them into Playlists
            val existingPlaylists = playlistDao.getAllPlaylists().firstOrNull() ?: emptyList()
            scanResult.folderMap.forEach { (folderName, folderTracks) ->
                if (folderTracks.isNotEmpty()) {
                    val playlistName = "📁 $folderName"
                    val existingPlaylist = existingPlaylists.find {
                        it.name.equals(playlistName, ignoreCase = true) || it.name.equals(folderName, ignoreCase = true)
                    }
                    val playlistId = if (existingPlaylist == null) {
                        val colorIdx = (folderName.hashCode().let { if (it < 0) -it else it }) % 4
                        playlistDao.insertPlaylist(
                            Playlist(
                                name = playlistName,
                                description = "Auto-synced folder (${folderTracks.size} songs)",
                                coverGradientIndex = colorIdx,
                                isSystemPlaylist = false
                            )
                        )
                    } else {
                        existingPlaylist.id
                    }

                    // Add folder tracks to this playlist
                    for (track in folderTracks) {
                        val dbTrack = trackDao.getTrackByPath(track.audioPath)
                        if (dbTrack != null) {
                            playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, dbTrack.id))
                        }
                    }
                }
            }
        }
        return scanned.size
    }

    fun searchTracks(query: String): Flow<List<Track>> {
        return if (query.isBlank()) {
            trackDao.getAllTracks()
        } else {
            trackDao.searchTracks(query)
        }
    }

    suspend fun toggleFavorite(trackId: Long, currentIsFavorite: Boolean) {
        trackDao.setFavorite(trackId, !currentIsFavorite)
    }

    suspend fun createPlaylist(name: String, description: String, gradientIndex: Int): Long {
        return playlistDao.insertPlaylist(
            Playlist(
                name = name,
                description = description,
                coverGradientIndex = gradientIndex,
                isSystemPlaylist = false
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> {
        return playlistDao.getTracksForPlaylist(playlistId)
    }

    suspend fun deleteTrack(trackId: Long) {
        trackDao.deleteTrack(trackId)
    }

    suspend fun addNewTrack(
        title: String,
        artist: String,
        album: String,
        isFlac: Boolean = false,
        durationMs: Long = 210000L
    ): Long {
        val newTrack = Track(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            isFlac = isFlac,
            audioPath = "synth_audio_demo_${System.currentTimeMillis()}",
            sampleRate = if (isFlac) "96.0 kHz" else "44.1 kHz",
            bitrate = if (isFlac) "24-Bit / 2800 kbps" else "320 kbps MP3",
            fileSizeBytes = if (isFlac) 35_400_000L else 8_200_000L,
            artworkColorIndex = (0..4).random()
        )
        return trackDao.insertTrack(newTrack)
    }
}
