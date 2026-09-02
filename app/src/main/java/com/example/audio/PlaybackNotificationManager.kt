package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.example.MainActivity
import com.example.R
import com.example.data.model.Track

class PlaybackNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var _mediaSession: MediaSession? = null
    val mediaSession: MediaSession
        get() = _mediaSession ?: initMediaSession()

    companion object {
        private const val TAG = "PlaybackNotification"
        const val CHANNEL_ID = "harmanx_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_CUSTOM_SLOWED_REVERB = "com.example.harmanx.CUSTOM_SLOWED_REVERB"
        const val ACTION_CUSTOM_RESET_EQ = "com.example.harmanx.CUSTOM_RESET_EQ"
        const val ACTION_CUSTOM_TOGGLE_EQ = "com.example.harmanx.CUSTOM_TOGGLE_EQ"
        const val ACTION_CUSTOM_CYCLE_EQ_PRESET = "com.example.harmanx.CUSTOM_CYCLE_EQ_PRESET"
        const val ACTION_CUSTOM_TOGGLE_360 = "com.example.harmanx.CUSTOM_TOGGLE_360"
        const val ACTION_CUSTOM_BASS_BOOST = "com.example.harmanx.CUSTOM_BASS_BOOST"
    }

    init {
        createNotificationChannel()
        initMediaSession()
    }

    private fun loadTrackThumbnail(track: Track): Bitmap {
        // 1. Try embedded picture from MediaMetadataRetriever (works directly on audio file tags)
        try {
            val mmr = MediaMetadataRetriever()
            try {
                if (track.audioPath.startsWith("content://")) {
                    mmr.setDataSource(context, Uri.parse(track.audioPath))
                } else if (track.audioPath.isNotEmpty() && !track.audioPath.startsWith("synth_flac_")) {
                    mmr.setDataSource(track.audioPath)
                }
                val rawPicture = mmr.embeddedPicture
                if (rawPicture != null && rawPicture.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(rawPicture, 0, rawPicture.size)
                    if (bitmap != null) {
                        return bitmap
                    }
                }
            } finally {
                try {
                    mmr.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2. Try MediaStore external audio item / albumart content resolver
        try {
            if (track.id > 0) {
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val bitmap = context.contentResolver.loadThumbnail(trackUri, Size(512, 512), null)
                        if (bitmap != null) return bitmap
                    } catch (_: Exception) {}
                }
            }

            if (track.albumId > 0) {
                val albumUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    track.albumId
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val bitmap = context.contentResolver.loadThumbnail(albumUri, Size(512, 512), null)
                        if (bitmap != null) return bitmap
                    } catch (_: Exception) {}
                }
                context.contentResolver.openInputStream(albumUri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) return bitmap
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: Generate high-resolution vibrant gradient album art
        return generateGradientThumbnail(track)
    }

    private fun generateGradientThumbnail(track: Track): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = when (track.artworkColorIndex % 5) {
            0 -> intArrayOf(0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt())
            1 -> intArrayOf(0xFFFF3D00.toInt(), 0xFFFFB703.toInt())
            2 -> intArrayOf(0xFF00E676.toInt(), 0xFF00B0FF.toInt())
            3 -> intArrayOf(0xFFD500F9.toInt(), 0xFF651FFF.toInt())
            else -> intArrayOf(0xFF7C4DFF.toInt(), 0xFF00E5FF.toInt())
        }
        val shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors, null, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Draw initial letter or musical note in center
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 190f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val initial = track.title.trim().take(1).uppercase().ifEmpty { "♫" }
        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initial, canvas.width / 2f, yPos, textPaint)

        return bitmap
    }

    private fun initMediaSession(): MediaSession {
        val session = MediaSession(context, "HARMANXMediaSession").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession onPlay triggered")
                    PlaybackControlReceiver.audioPlayerEngine?.playOrResume()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession onPause triggered")
                    PlaybackControlReceiver.audioPlayerEngine?.togglePlayPause()
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession onSkipToNext triggered")
                    PlaybackControlReceiver.audioPlayerEngine?.playNext()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession onSkipToPrevious triggered")
                    PlaybackControlReceiver.audioPlayerEngine?.playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    Log.d(TAG, "MediaSession onSeekTo triggered: $pos")
                    PlaybackControlReceiver.audioPlayerEngine?.seekTo(pos)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    Log.d(TAG, "MediaSession onPlayFromMediaId: $mediaId")
                    val engine = PlaybackControlReceiver.audioPlayerEngine ?: return
                    if (mediaId == null) return

                    if (mediaId.startsWith("fx_")) {
                        handleAndroidAutoFxAction(engine, mediaId)
                        return
                    }

                    val trackId = mediaId.toLongOrNull()
                    if (trackId != null) {
                        engine.playTrackById(trackId)
                    } else if (mediaId.startsWith("track_")) {
                        val parsedId = mediaId.removePrefix("track_").toLongOrNull()
                        if (parsedId != null) engine.playTrackById(parsedId)
                    }
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    Log.d(TAG, "MediaSession onPlayFromSearch: $query")
                    val engine = PlaybackControlReceiver.audioPlayerEngine ?: return
                    if (!query.isNullOrBlank()) {
                        engine.playFromSearch(query)
                    } else {
                        engine.playOrResume()
                    }
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession onStop triggered")
                    PlaybackControlReceiver.audioPlayerEngine?.stopPlayback()
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    Log.d(TAG, "MediaSession onCustomAction: $action")
                    val engine = PlaybackControlReceiver.audioPlayerEngine ?: return
                    when (action) {
                        ACTION_CUSTOM_SLOWED_REVERB -> {
                            val current = engine.isSlowedReverbEnabled.value
                            engine.setSlowedReverbEnabled(!current)
                        }
                        ACTION_CUSTOM_TOGGLE_EQ, ACTION_CUSTOM_BASS_BOOST -> {
                            val current = engine.eqEnabled.value
                            engine.setEqEnabled(!current)
                        }
                        ACTION_CUSTOM_CYCLE_EQ_PRESET, ACTION_CUSTOM_RESET_EQ -> {
                            engine.cycleEqPreset()
                        }
                        ACTION_CUSTOM_TOGGLE_360 -> {
                            val current = engine.surround360Enabled.value
                            engine.setSurround360Enabled(!current)
                        }
                    }
                }

                private fun handleAndroidAutoFxAction(engine: AudioPlayerEngine, fxId: String) {
                    when (fxId) {
                        "fx_toggle_slowed" -> {
                            val current = engine.isSlowedReverbEnabled.value
                            engine.setSlowedReverbEnabled(!current)
                        }
                        "fx_speed_075" -> engine.setPlaybackSpeed(0.75f)
                        "fx_speed_085" -> engine.setPlaybackSpeed(0.85f)
                        "fx_speed_090" -> engine.setPlaybackSpeed(0.90f)
                        "fx_speed_095" -> engine.setPlaybackSpeed(0.95f)
                        "fx_speed_100" -> engine.setPlaybackSpeed(1.00f)

                        "fx_preset_flat" -> engine.selectPreset(EqPreset.FLAT)
                        "fx_preset_bass" -> engine.selectPreset(EqPreset.BASS_BOOSTER)
                        "fx_preset_extreme_bass", "fx_preset_custom_bass" -> engine.selectPreset(EqPreset.CUSTOM_BASS)
                        "fx_preset_rock" -> engine.selectPreset(EqPreset.ROCK)
                        "fx_preset_pop" -> engine.selectPreset(EqPreset.POP)
                        "fx_preset_vocal" -> engine.selectPreset(EqPreset.VOCAL)
                        "fx_preset_electronic" -> engine.selectPreset(EqPreset.ELECTRONIC)
                        "fx_preset_jazz" -> engine.selectPreset(EqPreset.JAZZ)

                        "fx_bass_0" -> engine.setBassBoost(0.0f)
                        "fx_bass_35" -> {
                            if (!engine.eqEnabled.value) engine.setEqEnabled(true)
                            engine.setBassBoost(0.35f)
                        }
                        "fx_bass_70" -> {
                            if (!engine.eqEnabled.value) engine.setEqEnabled(true)
                            engine.setBassBoost(0.70f)
                        }
                        "fx_bass_100" -> {
                            if (!engine.eqEnabled.value) engine.setEqEnabled(true)
                            engine.setBassBoost(1.00f)
                        }

                        "fx_toggle_360" -> {
                            val current = engine.surround360Enabled.value
                            engine.setSurround360Enabled(!current)
                        }
                        "fx_spatial_headphones" -> {
                            if (!engine.eqEnabled.value) engine.setEqEnabled(true)
                            if (!engine.surround360Enabled.value) engine.setSurround360Enabled(true)
                            engine.setSpatialMode(SpatialMode.HEADPHONES_3D)
                        }
                        "fx_spatial_arena" -> {
                            if (!engine.eqEnabled.value) engine.setEqEnabled(true)
                            if (!engine.surround360Enabled.value) engine.setSurround360Enabled(true)
                            engine.setSpatialMode(SpatialMode.ARENA_360)
                        }
                        "fx_spatial_car" -> {
                            if (!engine.eqEnabled.value) engine.setEqEnabled(true)
                            if (!engine.surround360Enabled.value) engine.setSurround360Enabled(true)
                            engine.setSpatialMode(SpatialMode.CAR_CABIN_360)
                        }

                        "fx_reverb_none" -> engine.setReverbEffect(ReverbEffect.OFF)
                        "fx_reverb_room" -> engine.setReverbEffect(ReverbEffect.ROOM)
                        "fx_reverb_hall" -> engine.setReverbEffect(ReverbEffect.HALL)
                        "fx_reverb_large_hall" -> engine.setReverbEffect(ReverbEffect.LARGE_HALL)
                    }
                }
            })
            isActive = true
        }
        _mediaSession = session
        return session
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Audio Playback Controls"
            val descriptionText = "Notifications for active music playback and controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(track: Track?, isPlaying: Boolean, currentPositionMs: Long = 0L) {
        if (track == null) {
            cancelNotification()
            return
        }

        // Extract real artwork bitmap if present
        val albumBitmap = loadTrackThumbnail(track)

        // Update MediaSession Metadata and Playback State for system seekbar slider & Android Auto display
        try {
            val session = mediaSession
            val metadataBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, track.id.toString())
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, track.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)

            if (albumBitmap != null) {
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumBitmap)
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, albumBitmap)
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, albumBitmap)
            }

            session.setMetadata(metadataBuilder.build())

            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val engine = PlaybackControlReceiver.audioPlayerEngine
            val isSlowed = engine?.isSlowedReverbEnabled?.value == true
            val isEqOn = engine?.eqEnabled?.value == true
            val is360 = engine?.surround360Enabled?.value == true
            val currentSpeed = engine?.playbackSpeed?.value ?: 1.0f

            val currentPresetName = engine?.selectedPreset?.value?.displayName ?: "Preset"

            val pbState = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackState.ACTION_PLAY_FROM_SEARCH or
                    PlaybackState.ACTION_STOP
                )
                // Add Car / Android Auto Equalizer & Slowed+Reverb Quick Action Buttons with Dedicated Icons
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_CUSTOM_SLOWED_REVERB,
                        if (isSlowed) "✨ Slowed+Reverb [ON]" else "Slowed+Reverb",
                        R.drawable.ic_auto_slowed_reverb
                    ).build()
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_CUSTOM_TOGGLE_EQ,
                        if (isEqOn) "🎛️ Equalizer [ON]" else "Equalizer [OFF]",
                        R.drawable.ic_auto_eq_preset
                    ).build()
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_CUSTOM_CYCLE_EQ_PRESET,
                        "🎛️ Preset: $currentPresetName",
                        R.drawable.ic_auto_eq_preset
                    ).build()
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_CUSTOM_TOGGLE_360,
                        if (is360) "360° Surround [ON]" else "360° Surround",
                        R.drawable.ic_auto_spatial_360
                    ).build()
                )
                .setState(state, currentPositionMs, currentSpeed)
                .build()
            session.setPlaybackState(pbState)
            session.isActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating MediaSession state", e)
        }

        // Content Intent (Open App on tap)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous Action
        val prevIntent = Intent(context, PlaybackControlReceiver::class.java).apply {
            action = PlaybackControlReceiver.ACTION_PREVIOUS
        }
        val pendingPrev = PendingIntent.getBroadcast(
            context,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play / Pause Action
        val playPauseIntent = Intent(context, PlaybackControlReceiver::class.java).apply {
            action = PlaybackControlReceiver.ACTION_PLAY_PAUSE
        }
        val pendingPlayPause = PendingIntent.getBroadcast(
            context,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next Action
        val nextIntent = Intent(context, PlaybackControlReceiver::class.java).apply {
            action = PlaybackControlReceiver.ACTION_NEXT
        }
        val pendingNext = PendingIntent.getBroadcast(
            context,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.album}")
            .setContentIntent(pendingOpenApp)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Prev", pendingPrev
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    playPauseIcon, playPauseText, pendingPlayPause
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Next", pendingNext
                ).build()
            )

        if (albumBitmap != null) {
            builder.setLargeIcon(albumBitmap)
        }

        _mediaSession?.let { session ->
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            // Permission for POST_NOTIFICATIONS might not be granted yet
        }
    }

    fun cancelNotification() {
        try {
            _mediaSession?.let { session ->
                val pbState = PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_FROM_SEARCH)
                    .setState(PlaybackState.STATE_STOPPED, 0L, 0f)
                    .build()
                session.setPlaybackState(pbState)
                session.isActive = false
            }
        } catch (_: Exception) {}
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun release() {
        cancelNotification()
        try {
            _mediaSession?.release()
        } catch (_: Exception) {}
        _mediaSession = null
    }
}
