package com.example.audio

import android.media.MediaDescription
import android.media.browse.MediaBrowser
import android.net.Uri
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.util.Log
import com.example.data.db.AuraDatabase
import com.example.data.model.Playlist
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HarmanxMediaBrowserService : MediaBrowserService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var audioPlayerEngine: AudioPlayerEngine
    private lateinit var database: AuraDatabase

    companion object {
        private const val TAG = "HarmanxMediaService"
        const val MEDIA_ROOT_ID = "harmanx_media_root"
        const val CATEGORY_ALL_SONGS = "category_all_songs"
        const val CATEGORY_FLAC = "category_flac"
        const val CATEGORY_FAVORITES = "category_favorites"
        const val CATEGORY_PLAYLISTS = "category_playlists"
        const val CATEGORY_EQUALIZER_FX = "category_equalizer_fx"
        
        const val CATEGORY_FX_PRESETS = "category_fx_presets"
        const val CATEGORY_FX_SLOWED = "category_fx_slowed"
        const val CATEGORY_FX_BASS = "category_fx_bass"
        const val CATEGORY_FX_SURROUND = "category_fx_surround"
        const val CATEGORY_FX_REVERB = "category_fx_reverb"

        const val PREFIX_PLAYLIST = "playlist_"
        const val PREFIX_TRACK = "track_"
        const val PREFIX_FX = "fx_"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HarmanxMediaBrowserService created for Android Auto")
        audioPlayerEngine = AudioPlayerEngine.getInstance(applicationContext)
        database = AuraDatabase.getInstance(applicationContext)

        // Set session token so Android Auto / MediaControllers can control playback
        sessionToken = audioPlayerEngine.getMediaSession().sessionToken
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Log.d(TAG, "onGetRoot called by client: $clientPackageName")
        // Allow Android Auto and automotive media clients to connect
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowser.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren for parentId: $parentId")
        result.detach()

        serviceScope.launch {
            try {
                when (parentId) {
                    MEDIA_ROOT_ID -> {
                        val rootCategories = listOf(
                            createBrowsableMediaItem(
                                id = CATEGORY_ALL_SONGS,
                                title = "All Songs",
                                subtitle = "Browse complete library"
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_FLAC,
                                title = "Hi-Res FLAC",
                                subtitle = "Audiophile Lossless"
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_FAVORITES,
                                title = "Favorites",
                                subtitle = "Your top-rated tracks"
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_PLAYLISTS,
                                title = "Playlists",
                                subtitle = "Curated playlists & mixes"
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_EQUALIZER_FX,
                                title = "🎛️ Equalizer & FX",
                                subtitle = "Car Audio Presets, Bass & Slowed+Reverb"
                            )
                        )
                        result.sendResult(rootCategories)
                    }

                    CATEGORY_EQUALIZER_FX -> {
                        val fxSections = listOf(
                            createBrowsableMediaItem(
                                id = CATEGORY_FX_PRESETS,
                                title = "🎚️ EQ Sound Profiles",
                                subtitle = "Bass Boost, Rock, Pop, Vocal, EDM, Jazz..."
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_FX_SLOWED,
                                title = "✨ Slowed + Reverb & Speed",
                                subtitle = "Lo-Fi 0.85x, Chill 0.75x, Deep Hall Reverb"
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_FX_BASS,
                                title = "🔊 Car Sub-Bass Booster",
                                subtitle = "Adjust bass depth & car punch"
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_FX_SURROUND,
                                title = "🌐 360° Spatial Surround",
                                subtitle = "Immersive multi-speaker car virtualizer"
                            ),
                            createBrowsableMediaItem(
                                id = CATEGORY_FX_REVERB,
                                title = "🏛️ Reverb Acoustics",
                                subtitle = "Room & Hall acoustic concert simulations"
                            )
                        )
                        result.sendResult(fxSections)
                    }

                    CATEGORY_FX_PRESETS -> {
                        val items = listOf(
                            createPlayableFxItem("${PREFIX_FX}preset_flat", "Flat / Studio Neutral", "Balanced studio master profile"),
                            createPlayableFxItem("${PREFIX_FX}preset_bass", "Bass Booster", "Enhanced sub-bass frequencies"),
                            createPlayableFxItem("${PREFIX_FX}preset_extreme_bass", "Extreme Bass", "Maximum low-end power"),
                            createPlayableFxItem("${PREFIX_FX}preset_rock", "Rock Punch", "Punchy drums & clear guitar riffs"),
                            createPlayableFxItem("${PREFIX_FX}preset_pop", "Pop Clarity", "Vibrant crisp vocals & upbeat rhythm"),
                            createPlayableFxItem("${PREFIX_FX}preset_vocal", "Vocal / Podcast", "Enhanced speech clarity"),
                            createPlayableFxItem("${PREFIX_FX}preset_electronic", "Electronic / EDM", "Deep synths & club bass"),
                            createPlayableFxItem("${PREFIX_FX}preset_jazz", "Jazz & Acoustic", "Warm midrange acoustic tones")
                        )
                        result.sendResult(items)
                    }

                    CATEGORY_FX_SLOWED -> {
                        val items = listOf(
                            createPlayableFxItem("${PREFIX_FX}toggle_slowed", "✨ Toggle Slowed + Reverb (ON/OFF)", "0.90x default tempo + Hall reverb"),
                            createPlayableFxItem("${PREFIX_FX}speed_090", "⭐ 0.90x Default Slowed Tempo", "Recommended slowed + reverb tempo"),
                            createPlayableFxItem("${PREFIX_FX}speed_085", "⚡ 0.85x Lo-Fi Aesthetic Speed", "Classic deeper slowed speed"),
                            createPlayableFxItem("${PREFIX_FX}speed_075", "⚡ 0.75x Chill Slowed Tempo", "Ultra relaxed ambient speed"),
                            createPlayableFxItem("${PREFIX_FX}speed_095", "⚡ 0.95x Mild Slow Tempo", "Subtle mellow tempo"),
                            createPlayableFxItem("${PREFIX_FX}speed_100", "⚡ 1.00x Normal Tempo", "Standard original song speed")
                        )
                        result.sendResult(items)
                    }

                    CATEGORY_FX_BASS -> {
                        val items = listOf(
                            createPlayableFxItem("${PREFIX_FX}bass_0", "🔈 Bass Boost: OFF", "Standard low-end response"),
                            createPlayableFxItem("${PREFIX_FX}bass_35", "🔉 Bass Boost: Medium (35%)", "Tight clean punch"),
                            createPlayableFxItem("${PREFIX_FX}bass_70", "🔊 Bass Boost: Heavy (70%)", "Deep car door rattling bass"),
                            createPlayableFxItem("${PREFIX_FX}bass_100", "💥 Bass Boost: Extreme (100%)", "Maximum sub-woofer power")
                        )
                        result.sendResult(items)
                    }

                    CATEGORY_FX_SURROUND -> {
                        val items = listOf(
                            createPlayableFxItem("${PREFIX_FX}toggle_360", "🌐 360° Spatial Audio (Toggle)", "Switch 3D virtualizer ON/OFF"),
                            createPlayableFxItem("${PREFIX_FX}spatial_headphones", "🎧 3D Binaural Headphones", "Center vocal lock + binaural orbit"),
                            createPlayableFxItem("${PREFIX_FX}spatial_arena", "🏛️ 360° Concert Arena", "Expansive stadium soundstage"),
                            createPlayableFxItem("${PREFIX_FX}spatial_car", "🚗 Car Cabin 360 Surround", "Cabin multi-speaker dispersion & tight sub-bass")
                        )
                        result.sendResult(items)
                    }

                    CATEGORY_FX_REVERB -> {
                        val items = listOf(
                            createPlayableFxItem("${PREFIX_FX}reverb_none", "🏛️ Reverb: None", "Direct studio sound"),
                            createPlayableFxItem("${PREFIX_FX}reverb_room", "🏛️ Small Studio Room", "Subtle natural reflection"),
                            createPlayableFxItem("${PREFIX_FX}reverb_hall", "🏛️ Medium Concert Hall", "Balanced acoustic space"),
                            createPlayableFxItem("${PREFIX_FX}reverb_large_hall", "🏛️ Large Cathedral Hall", "Vast concert reverberation")
                        )
                        result.sendResult(items)
                    }

                    CATEGORY_ALL_SONGS -> {
                        val tracks = database.trackDao().getAllTracks().first()
                        val mediaItems = tracks.map { it.toMediaItem() }
                        result.sendResult(mediaItems)
                    }

                    CATEGORY_FLAC -> {
                        val tracks = database.trackDao().getFlacTracks().first()
                        val mediaItems = tracks.map { it.toMediaItem() }
                        result.sendResult(mediaItems)
                    }

                    CATEGORY_FAVORITES -> {
                        val tracks = database.trackDao().getFavoriteTracks().first()
                        val mediaItems = tracks.map { it.toMediaItem() }
                        result.sendResult(mediaItems)
                    }

                    CATEGORY_PLAYLISTS -> {
                        val playlists = database.playlistDao().getAllPlaylists().first()
                        val mediaItems = playlists.map { playlist ->
                            createBrowsableMediaItem(
                                id = "$PREFIX_PLAYLIST${playlist.id}",
                                title = playlist.name,
                                subtitle = playlist.description
                            )
                        }
                        result.sendResult(mediaItems)
                    }

                    else -> {
                        if (parentId.startsWith(PREFIX_PLAYLIST)) {
                            val playlistIdStr = parentId.removePrefix(PREFIX_PLAYLIST)
                            val playlistId = playlistIdStr.toLongOrNull()
                            if (playlistId != null) {
                                val tracks = database.playlistDao().getTracksForPlaylist(playlistId).first()
                                val mediaItems = tracks.map { it.toMediaItem() }
                                result.sendResult(mediaItems)
                            } else {
                                result.sendResult(emptyList())
                            }
                        } else {
                            result.sendResult(emptyList())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media children for $parentId", e)
                result.sendResult(emptyList())
            }
        }
    }

    private fun createBrowsableMediaItem(
        id: String,
        title: String,
        subtitle: String
    ): MediaBrowser.MediaItem {
        val description = MediaDescription.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_BROWSABLE)
    }

    private fun createPlayableFxItem(
        id: String,
        title: String,
        subtitle: String
    ): MediaBrowser.MediaItem {
        val description = MediaDescription.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE)
    }

    private fun Track.toMediaItem(): MediaBrowser.MediaItem {
        val description = MediaDescription.Builder()
            .setMediaId(this.id.toString())
            .setTitle(this.title)
            .setSubtitle(this.artist)
            .setDescription(this.album)
            .setMediaUri(Uri.parse(this.audioPath))
            .build()
        return MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
