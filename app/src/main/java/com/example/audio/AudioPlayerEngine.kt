package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.db.AuraDatabase
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import kotlin.math.sin

enum class RepeatMode {
    OFF, ALL, ONE
}

enum class ReverbEffect(val displayName: String, val presetValue: Short) {
    OFF("Off", PresetReverb.PRESET_NONE),
    ROOM("Room", PresetReverb.PRESET_SMALLROOM),
    HALL("Hall", PresetReverb.PRESET_LARGEROOM),
    LARGE_HALL("Large Hall", PresetReverb.PRESET_LARGEHALL)
}

enum class SpatialMode(
    val title: String,
    val description: String,
    val virtualizerStrength: Float,
    val bassCompensation: Float = 0.30f,
    val centerClarityBoost: Float = 0.20f
) {
    HEADPHONES_3D(
        title = "3D Binaural Headphones",
        description = "AirPods & IEMs tuned • Center vocal clarity lock with 360° spherical staging",
        virtualizerStrength = 1.0f,
        bassCompensation = 0.35f,
        centerClarityBoost = 0.25f
    ),
    ARENA_360(
        title = "360° Concert Arena",
        description = "Massive stadium width & acoustic depth • Dynamic multi-angle sound reflection",
        virtualizerStrength = 1.0f,
        bassCompensation = 0.20f,
        centerClarityBoost = 0.15f
    ),
    CAR_CABIN_360(
        title = "Car Cabin Surround",
        description = "Multi-speaker car dispersion • Anti-cancellation deep punchy sub-bass",
        virtualizerStrength = 0.90f,
        bassCompensation = 0.45f,
        centerClarityBoost = 0.10f
    )
}

enum class EqPreset(val displayName: String, val gains: FloatArray, val bassBoost: Float) {
    FLAT("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f), 0f),
    BASS_BOOSTER("Bass Booster", floatArrayOf(6f, 8f, 4f, 1f, 0f), 0.70f),
    CUSTOM_BASS("Custom Bass", floatArrayOf(8f, 2f, 3f, 0f, 3f), 0.60f),
    ROCK("Rock", floatArrayOf(5f, 3f, -1f, 3f, 5f), 0.5f),
    ELECTRONIC("Electronic", floatArrayOf(6f, 5f, 0f, 2f, 4f), 0.7f),
    JAZZ("Jazz", floatArrayOf(3f, 2f, 1f, 2f, 3f), 0.3f),
    POP("Pop", floatArrayOf(-1f, 2f, 5f, 3f, -1f), 0.4f),
    VOCAL("Vocal", floatArrayOf(-2f, 0f, 6f, 4f, 0f), 0.2f),
    CUSTOM("Custom", floatArrayOf(0f, 0f, 0f, 0f, 0f), 0.5f)
}

class AudioPlayerEngine private constructor(private val context: Context) {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager = PlaybackNotificationManager(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var primaryPlayer: MediaPlayer? = null
    private var currentTrimBounds: AudioSilenceDetector.TrimBounds? = null
    private var silenceAnalysisJob: Job? = null
    
    private var equalizerFx: Equalizer? = null
    private var bassBoostFx: BassBoost? = null
    private var virtualizerFx: Virtualizer? = null
    private var presetReverbFx: PresetReverb? = null
    private var eqTransitionJob: Job? = null

    // Audio Focus & Becoming Noisy (Bluetooth disconnect auto-pause)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var isBecomingNoisyRegistered = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d("AudioPlayerEngine", "ACTION_AUDIO_BECOMING_NOISY received (Bluetooth/Headphones disconnected) -> Pausing playback")
                pausePlayback()
            }
        }
    }

    // Synth player thread for high-res sample tone simulation when physical file is missing
    private var synthAudioTrack: AudioTrack? = null
    private var isSynthPlaying = false
    private var synthThread: Thread? = null

    // State Flows
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isGaplessEnabled = MutableStateFlow(false)
    val isGaplessEnabled: StateFlow<Boolean> = _isGaplessEnabled.asStateFlow()

    // Equalizer State (Default: OFF)
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(0f) // 0.0 to 1.0 (Flat default)
    val bassBoostLevel: StateFlow<Float> = _bassBoostLevel.asStateFlow()

    // 360 Spatial Surround Sound State
    private val _surround360Enabled = MutableStateFlow(false)
    val surround360Enabled: StateFlow<Boolean> = _surround360Enabled.asStateFlow()

    private val _spatialMode = MutableStateFlow(SpatialMode.ARENA_360)
    val spatialMode: StateFlow<SpatialMode> = _spatialMode.asStateFlow()

    private val _surroundStrength = MutableStateFlow(1.0f) // 0.0 to 1.0
    val surroundStrength: StateFlow<Float> = _surroundStrength.asStateFlow()

    // Environmental Reverb Effect (None/Off, Room, Hall, Large Hall)
    private val _selectedReverb = MutableStateFlow(ReverbEffect.OFF)
    val selectedReverb: StateFlow<ReverbEffect> = _selectedReverb.asStateFlow()

    // Slowed + Reverb (Lofi Aesthetic Mode)
    private val _isSlowedReverbEnabled = MutableStateFlow(false)
    val isSlowedReverbEnabled: StateFlow<Boolean> = _isSlowedReverbEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f) // 0.70f to 1.30f
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // 5 Bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (-12dB to +12dB)
    private val _bandGains = MutableStateFlow(floatArrayOf(0f, 0f, 0f, 0f, 0f))
    val bandGains: StateFlow<FloatArray> = _bandGains.asStateFlow()

    private val _selectedPreset = MutableStateFlow(EqPreset.FLAT)
    val selectedPreset: StateFlow<EqPreset> = _selectedPreset.asStateFlow()

    private val _playlistQueue = MutableStateFlow<List<Track>>(emptyList())
    val playlistQueue: StateFlow<List<Track>> = _playlistQueue.asStateFlow()

    private var currentQueueIndex = -1

    private val prefs = context.getSharedPreferences("harmanx_player_state", Context.MODE_PRIVATE)

    private val handler = Handler(Looper.getMainLooper())
    private var lastSavedPosition = 0L
    private val progressUpdater = object : Runnable {
        override fun run() {
            primaryPlayer?.let { player ->
                if (player.isPlaying) {
                    val curPos = player.currentPosition.toLong()
                    val dur = player.duration.toLong()
                    _currentPositionMs.value = curPos
                    _durationMs.value = dur
                    
                    // Periodically persist playback position every ~2 seconds
                    if (Math.abs(curPos - lastSavedPosition) >= 2000) {
                        lastSavedPosition = curPos
                        savePlaybackState(curPos)
                    }

                    // Gapless Mode: Cut trailing dead air when voice note / audio sound ends
                    if (_isGaplessEnabled.value && dur > 3000L) {
                        val bounds = currentTrimBounds
                        val outroCutPoint = bounds?.outroEndMs ?: (dur - 1200L)
                        if (curPos >= outroCutPoint && curPos < dur) {
                            Log.d("AudioPlayerEngine", "Gapless active: Voice note finished at ${curPos}ms (total ${dur}ms). Cutting remaining duration & playing next song.")
                            playNext()
                        }
                    }
                }
            } ?: run {
                if (isSynthPlaying) {
                    val newPos = (_currentPositionMs.value + 200).coerceAtMost(_durationMs.value)
                    _currentPositionMs.value = newPos
                    val dur = _durationMs.value
                    if (_isGaplessEnabled.value && dur > 3000L) {
                        val bounds = currentTrimBounds
                        val outroCutPoint = bounds?.outroEndMs ?: (dur - 1500L)
                        if (newPos >= outroCutPoint) {
                            Log.d("AudioPlayerEngine", "Gapless active (synth): Voice note finished at ${newPos}ms. Cutting remaining duration & playing next song.")
                            playNext()
                        } else if (newPos >= dur) {
                            onTrackCompleted()
                        }
                    } else if (newPos >= dur) {
                        onTrackCompleted()
                    }
                }
            }
            handler.postDelayed(this, 150)
        }
    }

    init {
        val savedGapless = prefs.getBoolean("gapless_enabled", false)
        _isGaplessEnabled.value = savedGapless
        handler.post(progressUpdater)
        PlaybackControlReceiver.audioPlayerEngine = this
        restoreLastPlaybackState()
    }

    private fun savePlaybackState(positionMs: Long) {
        val track = _currentTrack.value ?: return
        try {
            prefs.edit()
                .putLong("saved_track_id", track.id)
                .putString("saved_track_path", track.audioPath)
                .putLong("saved_position_ms", positionMs)
                .putLong("saved_duration_ms", _durationMs.value)
                .apply()
        } catch (_: Exception) {}
    }

    fun restoreLastPlaybackState() {
        engineScope.launch {
            try {
                val savedTrackId = prefs.getLong("saved_track_id", -1L)
                val savedTrackPath = prefs.getString("saved_track_path", null)
                val savedPos = prefs.getLong("saved_position_ms", 0L)
                val savedDur = prefs.getLong("saved_duration_ms", 0L)

                if (savedTrackId != -1L || !savedTrackPath.isNullOrEmpty()) {
                    val db = AuraDatabase.getInstance(context)
                    val track = if (savedTrackId != -1L) {
                        db.trackDao().getTrackById(savedTrackId)
                    } else null ?: if (!savedTrackPath.isNullOrEmpty()) {
                        db.trackDao().getTrackByPath(savedTrackPath)
                    } else null

                    if (track != null) {
                        _currentTrack.value = track
                        _durationMs.value = if (savedDur > 0) savedDur else track.durationMs
                        _currentPositionMs.value = savedPos.coerceAtMost(_durationMs.value)
                        
                        // Also restore queue so next/previous works
                        val all = db.trackDao().getAllTracks().first()
                        if (all.isNotEmpty()) {
                            _playlistQueue.value = all
                            val idx = all.indexOfFirst { it.id == track.id }
                            if (idx != -1) currentQueueIndex = idx
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("AudioPlayerEngine", "Error restoring playback state", e)
            }
        }
    }

    fun getMediaSession(): MediaSession = notificationManager.mediaSession

    private fun notifyPlaybackState() {
        notificationManager.updateNotification(_currentTrack.value, _isPlaying.value, _currentPositionMs.value)
    }

    private fun registerBecomingNoisyReceiver() {
        if (!isBecomingNoisyRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                context.registerReceiver(becomingNoisyReceiver, filter)
                isBecomingNoisyRegistered = true
                Log.d("AudioPlayerEngine", "Registered ACTION_AUDIO_BECOMING_NOISY receiver")
            } catch (e: Exception) {
                Log.e("AudioPlayerEngine", "Failed to register becoming noisy receiver", e)
            }
        }
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (isBecomingNoisyRegistered) {
            try {
                context.unregisterReceiver(becomingNoisyReceiver)
                Log.d("AudioPlayerEngine", "Unregistered ACTION_AUDIO_BECOMING_NOISY receiver")
            } catch (_: Exception) {}
            isBecomingNoisyRegistered = false
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }
        } catch (_: Exception) {}
        hasAudioFocus = false
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("AudioPlayerEngine", "Audio focus loss: pausing playback")
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("AudioPlayerEngine", "Audio focus loss transient: pausing playback")
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                primaryPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                primaryPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _playlistQueue.value = tracks
        if (tracks.isNotEmpty() && startIndex in tracks.indices) {
            currentQueueIndex = startIndex
            playTrack(tracks[startIndex])
        }
    }

    fun playTrack(track: Track, startPositionMs: Long = 0L) {
        _currentTrack.value = track
        _durationMs.value = track.durationMs

        // If Gapless Mode is toggled ON: cut leading dead air/silence if starting from 0
        var actualStartMs = startPositionMs
        if (_isGaplessEnabled.value && startPositionMs == 0L) {
            val cached = AudioSilenceDetector.getCached(track.audioPath)
            if (cached != null && cached.introStartMs > 100L) {
                actualStartMs = cached.introStartMs
                Log.d("AudioPlayerEngine", "Gapless active: Cut ${cached.introStartMs}ms intro silence. Starting voice note directly.")
            }
        }

        _currentPositionMs.value = actualStartMs

        stopPlayback()
        requestAudioFocus()
        registerBecomingNoisyReceiver()

        // Background silence & voice-note analysis for current and upcoming track
        silenceAnalysisJob?.cancel()
        silenceAnalysisJob = engineScope.launch {
            val bounds = AudioSilenceDetector.analyzeTrack(context, track.audioPath, track.durationMs)
            currentTrimBounds = bounds
            
            // If Gapless is ON, started at 0, and analysis found leading dead air:
            // Fast-forward straight to first voice note if playback hasn't crossed it yet
            if (_isGaplessEnabled.value && startPositionMs == 0L && bounds.introStartMs > 150L) {
                withContext(Dispatchers.Main) {
                    primaryPlayer?.let { player ->
                        if (player.isPlaying && player.currentPosition < bounds.introStartMs) {
                            Log.d("AudioPlayerEngine", "Gapless active: Fast-forwarding past ${bounds.introStartMs}ms intro silence to first voice note.")
                            seekTo(bounds.introStartMs)
                        }
                    }
                }
            }

            // Pre-analyze upcoming track so its silence bounds are cached in advance
            getNextTrack()?.let { next ->
                AudioSilenceDetector.analyzeTrack(context, next.audioPath, next.durationMs)
            }
        }

        try {
            val uri = Uri.parse(track.audioPath)
            if (track.audioPath.startsWith("content://") || track.audioPath.startsWith("file://") || track.audioPath.startsWith("http")) {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(context, uri)
                    prepare()
                    if (actualStartMs > 0 && actualStartMs < track.durationMs) {
                        seekTo(actualStartMs.toInt())
                    }
                    start()
                }
                primaryPlayer = player
                _isPlaying.value = true
                setupAudioFx(player.audioSessionId)
                applyPlaybackSpeed()
                player.setOnCompletionListener { onTrackCompleted() }
                notifyPlaybackState()
            } else {
                // High-resolution synthesized audio mode for sample FLAC demonstration
                playSynthFlacAudio(track)
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Error playing media file, fallback to synth generator", e)
            playSynthFlacAudio(track)
        }
    }

    private fun playSynthFlacAudio(track: Track) {
        stopPlayback()
        requestAudioFocus()
        registerBecomingNoisyReceiver()

        _isPlaying.value = true
        isSynthPlaying = true
        notifyPlaybackState()

        val sampleRate = 44100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            synthAudioTrack = audioTrack

            if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack.play()
                setupAudioFx(audioTrack.audioSessionId)
            }

            synthThread = thread(start = true, isDaemon = true, name = "SynthAudioThread") {
                val bufferFrames = 1024
                val samples = ShortArray(bufferFrames * 2) // Stereo (L, R)
                var globalSampleIndex = (_currentPositionMs.value * sampleRate / 1000).toLong()

                // Musical scales and chords for demo tracks
                // Track 1: Lo-Fi Synthwave (Cm -> Ab -> Eb -> Bb)
                // Track 2: Deep Bass Groove
                // Track 3: Ambient Chillout
                val trackSeed = (track.id.toInt().coerceAtLeast(1) - 1) % 5
                val chordProgressions = listOf(
                    intArrayOf(60, 63, 67, 70), // Cm7
                    intArrayOf(56, 60, 63, 68), // Abmaj7
                    intArrayOf(58, 62, 65, 70), // Bb
                    intArrayOf(55, 58, 62, 67)  // Gm7
                )
                val baseNotes = doubleArrayOf(130.81, 146.83, 164.81, 174.61, 196.00, 220.00, 246.94, 261.63)

                val baseTempoBpm = 110 + (trackSeed * 8)

                var currentSmoothedBass = if (_eqEnabled.value) _bassBoostLevel.value else 0f
                var currentSmoothedSurround = if (_surround360Enabled.value) (_surroundStrength.value * _spatialMode.value.virtualizerStrength) else 0f

                // Algorithmic Schroeder Reverb comb-filter ring buffers (Room, Hall, Large Hall)
                val combSizes = intArrayOf(1557, 1617, 1491, 1422, 1277, 1116)
                val combBuffers = Array(combSizes.size) { FloatArray(combSizes[it]) }
                val combIndices = IntArray(combSizes.size)

                while (isSynthPlaying) {
                    val currentSpeed = _playbackSpeed.value.coerceIn(0.6f, 1.5f)
                    val effectiveTempo = baseTempoBpm * currentSpeed
                    val samplesPerBeat = (sampleRate * 60.0 / effectiveTempo).toInt().coerceAtLeast(100)
                    val pitchFactor = if (_isSlowedReverbEnabled.value) 0.90 else currentSpeed.toDouble()

                    val targetBass = if (_eqEnabled.value) _bassBoostLevel.value else 0f
                    val targetSurround = if (_surround360Enabled.value) (_surroundStrength.value * _spatialMode.value.virtualizerStrength) else 0f
                    currentSmoothedBass += (targetBass - currentSmoothedBass) * 0.05f
                    currentSmoothedSurround += (targetSurround - currentSmoothedSurround) * 0.05f

                    val bassBoostGain = 1.0 + (currentSmoothedBass * 2.8)
                    val currentSpatialMode = _spatialMode.value
                    val clarityBoost = if (_surround360Enabled.value) currentSpatialMode.centerClarityBoost else 0f
                    val eqLow = if (_eqEnabled.value) (1.0 + (_bandGains.value.getOrElse(0) { 0f } / 12.0) * 0.8) else 1.0
                    val eqMid = if (_eqEnabled.value) (1.0 + ((_bandGains.value.getOrElse(2) { 0f } / 12.0) + clarityBoost) * 0.8) else (1.0 + clarityBoost * 0.8)
                    val eqHigh = if (_eqEnabled.value) (1.0 + ((_bandGains.value.getOrElse(4) { 0f } / 12.0) + (clarityBoost * 0.5f)) * 0.8) else (1.0 + clarityBoost * 0.5f * 0.8)

                    val activeReverb = _selectedReverb.value
                    val (reverbFeedback, reverbWet) = when (activeReverb) {
                        ReverbEffect.ROOM -> Pair(0.68f, 0.45f)
                        ReverbEffect.HALL -> Pair(0.82f, 0.65f)
                        ReverbEffect.LARGE_HALL -> Pair(0.91f, 0.85f)
                        ReverbEffect.OFF -> Pair(0.0f, 0.0f)
                    }

                    var sampleOutIdx = 0
                    for (frame in 0 until bufferFrames) {
                        val currentSample = globalSampleIndex + frame
                        val currentBeat = (currentSample / samplesPerBeat).toInt()
                        val beatFraction = (currentSample % samplesPerBeat).toDouble() / samplesPerBeat
                        val currentMeasure = currentBeat / 4
                        val chordIndex = (currentMeasure % chordProgressions.size)

                        // 1. Kick & Percussion Pulse (Locked in Center Audio Stage)
                        val kickDecay = (1.0 - beatFraction * 3.5).coerceIn(0.0, 1.0)
                        val kickPitch = 50.0 + kickDecay * 100.0
                        val kickSample = if (currentBeat % 2 == 0) sin(2.0 * Math.PI * kickPitch * (beatFraction * 0.2)) * kickDecay * 7000.0 else 0.0

                        // 2. Sub-Bassline (Deep Solid Center Low-End with zero phase loss)
                        val bassNote = when (chordIndex) {
                            0 -> 65.41  // C2
                            1 -> 51.91  // Ab1
                            2 -> 58.27  // Bb1
                            else -> 48.99 // G1
                        } * pitchFactor
                        val bassPhase = (currentSample * 2.0 * Math.PI * bassNote / sampleRate)
                        val bassEnvelope = (1.0 - (beatFraction * 0.8)).coerceIn(0.2, 1.0)
                        val subBass = sin(bassPhase) * 9500.0 * bassBoostGain * eqLow * bassEnvelope

                        // 3. Arpeggio / Melodic Chord Notes (360 Wide Spatial Orbit)
                        val arpStep = ((currentSample / (samplesPerBeat / 4)) % 4).toInt()
                        val chord = chordProgressions[chordIndex]
                        val midiNote = chord[arpStep % chord.size]
                        val freq = (440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)) * pitchFactor
                        val notePhase = (currentSample * 2.0 * Math.PI * freq / sampleRate)
                        val arpEnv = (1.0 - ((currentSample % (samplesPerBeat / 4)).toDouble() / (samplesPerBeat / 4) * 1.5)).coerceIn(0.0, 1.0)
                        val arpSample = (sin(notePhase) + 0.3 * sin(notePhase * 2.0)) * 5500.0 * eqMid * arpEnv

                        // 4. Soft High-Frequency Atmosphere Pad / Shimmer (Spherical High-End Cloud)
                        val padFreq = (330.0 + (trackSeed * 40.0)) * pitchFactor
                        val padPhase = (currentSample * 2.0 * Math.PI * padFreq / sampleRate)
                        val padSample = (sin(padPhase * 1.002) + sin(padPhase * 0.998)) * 3200.0 * eqHigh

                        // Center Mid (Kick + SubBass) stays tight & punchy in the middle
                        val centerChannel = (kickSample + subBass).toFloat()
                        // Side Spatial (Arp + Pad shimmer) spreads in 360 sphere
                        val spatialSignal = (arpSample + padSample).toFloat()

                        // Multi-tap Comb-Filter Acoustic Reverb Processing
                        var reverbAccumulator = 0f
                        if (reverbWet > 0.01f) {
                            for (c in combSizes.indices) {
                                val cBuf = combBuffers[c]
                                val cIdx = combIndices[c]
                                val delayedVal = cBuf[cIdx]
                                reverbAccumulator += delayedVal
                                cBuf[cIdx] = (centerChannel * 0.2f + spatialSignal * 0.5f) + delayedVal * reverbFeedback
                                combIndices[c] = (cIdx + 1) % combSizes[c]
                            }
                            reverbAccumulator = (reverbAccumulator / combSizes.size) * reverbWet * 1.8f
                        }

                        // Advanced 360° Spherical Orbit Phase Calculation (Distinct panning & dimensional width)
                        val orbitAngle = (currentSample * 2.0 * Math.PI * 0.25 / sampleRate)
                        val orbitAmount = currentSmoothedSurround * 1.15f
                        val leftOrbit = (1.0 + orbitAmount * sin(orbitAngle)).coerceIn(0.15, 2.0)
                        val rightOrbit = (1.0 - orbitAmount * sin(orbitAngle)).coerceIn(0.15, 2.0)

                        // Mode-specific wide spatial cross-bleed
                        val crossSpread = if (_surround360Enabled.value) currentSmoothedSurround * 0.40f else 0.0f
                        val finalLeft = (centerChannel + (spatialSignal * leftOrbit) + (spatialSignal * crossSpread) + reverbAccumulator).coerceIn(-32767.0, 32767.0).toInt().toShort()
                        val finalRight = (centerChannel + (spatialSignal * rightOrbit) - (spatialSignal * crossSpread * 0.5f) + reverbAccumulator * 0.92).coerceIn(-32767.0, 32767.0).toInt().toShort()

                        samples[sampleOutIdx++] = finalLeft
                        samples[sampleOutIdx++] = finalRight
                    }

                    globalSampleIndex += bufferFrames
                    val currentTrackObj = _currentTrack.value
                    if (currentTrackObj != null && globalSampleIndex >= (currentTrackObj.durationMs * sampleRate / 1000)) {
                        globalSampleIndex = 0
                    }

                    try {
                        val trackRef = synthAudioTrack
                        if (trackRef != null && trackRef.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            trackRef.write(samples, 0, samples.size)
                        } else {
                            break
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Synth audio generation failed", e)
        }
    }

    private fun setupAudioFx(sessionId: Int) {
        // Safely initialize Equalizer
        try {
            equalizerFx?.release()
            equalizerFx = null
            if (sessionId != 0) {
                equalizerFx = Equalizer(0, sessionId).apply {
                    enabled = _eqEnabled.value
                }
                applyEqualizerGains()
            }
        } catch (e: Exception) {
            Log.d("AudioPlayerEngine", "Equalizer FX init info", e)
        }

        // Safely initialize BassBoost
        try {
            bassBoostFx?.release()
            bassBoostFx = null
            if (sessionId != 0) {
                bassBoostFx = BassBoost(0, sessionId).apply {
                    enabled = _eqEnabled.value
                    if (strengthSupported) {
                        setStrength((_bassBoostLevel.value * 1000).toInt().toShort())
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("AudioPlayerEngine", "BassBoost FX init info", e)
        }

        // Safely initialize 360 Virtualizer (Original High-Definition Hardware Virtualizer)
        try {
            virtualizerFx?.release()
            virtualizerFx = null
            if (sessionId != 0) {
                virtualizerFx = Virtualizer(0, sessionId).apply {
                    enabled = _surround360Enabled.value
                    if (strengthSupported) {
                        val mode = _spatialMode.value
                        setStrength((_surroundStrength.value * mode.virtualizerStrength * 1000).toInt().toShort())
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("AudioPlayerEngine", "Virtualizer FX init info", e)
        }

        // Safely initialize PresetReverb (Global Session 0 or Aux Output)
        try {
            presetReverbFx?.release()
            presetReverbFx = null
            presetReverbFx = PresetReverb(0, 0).apply {
                preset = if (_selectedReverb.value != ReverbEffect.OFF) _selectedReverb.value.presetValue else PresetReverb.PRESET_NONE
                enabled = _selectedReverb.value != ReverbEffect.OFF
            }

            presetReverbFx?.let { reverb ->
                try {
                    primaryPlayer?.attachAuxEffect(reverb.id)
                    primaryPlayer?.setAuxEffectSendLevel(if (_selectedReverb.value != ReverbEffect.OFF) 1.0f else 0.0f)
                } catch (_: Exception) {}

                try {
                    synthAudioTrack?.attachAuxEffect(reverb.id)
                    synthAudioTrack?.setAuxEffectSendLevel(if (_selectedReverb.value != ReverbEffect.OFF) 1.0f else 0.0f)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.d("AudioPlayerEngine", "PresetReverb FX init info", e)
        }
    }

    fun playOrResume() {
        val current = _currentTrack.value
        if (current != null) {
            if (!_isPlaying.value) {
                if (primaryPlayer != null) {
                    togglePlayPause()
                } else {
                    playTrack(current, _currentPositionMs.value)
                }
            }
        } else {
            engineScope.launch {
                val db = AuraDatabase.getInstance(context)
                val all = db.trackDao().getAllTracks().first()
                if (all.isNotEmpty()) {
                    setQueue(all, 0)
                }
            }
        }
    }

    fun playTrackById(trackId: Long) {
        engineScope.launch {
            val db = AuraDatabase.getInstance(context)
            val all = db.trackDao().getAllTracks().first()
            val index = all.indexOfFirst { it.id == trackId }
            if (index != -1) {
                setQueue(all, index)
            } else {
                val singleTrack = db.trackDao().getTrackById(trackId)
                if (singleTrack != null) {
                    setQueue(listOf(singleTrack), 0)
                }
            }
        }
    }

    fun playFromSearch(query: String) {
        engineScope.launch {
            val db = AuraDatabase.getInstance(context)
            val results = db.trackDao().searchTracks(query).first()
            if (results.isNotEmpty()) {
                setQueue(results, 0)
            } else {
                val all = db.trackDao().getAllTracks().first()
                if (all.isNotEmpty()) {
                    setQueue(all, 0)
                }
            }
        }
    }

    fun pausePlayback() {
        primaryPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
        if (isSynthPlaying) {
            isSynthPlaying = false
        }
        _isPlaying.value = false
        savePlaybackState(_currentPositionMs.value)
        notifyPlaybackState()
        abandonAudioFocus()
        unregisterBecomingNoisyReceiver()
    }

    fun togglePlayPause() {
        val current = _currentTrack.value
        if (current == null) {
            playOrResume()
            return
        }

        primaryPlayer?.let { player ->
            if (player.isPlaying) {
                pausePlayback()
            } else {
                requestAudioFocus()
                registerBecomingNoisyReceiver()
                player.start()
                _isPlaying.value = true
                notifyPlaybackState()
            }
            return
        }

        if (isSynthPlaying) {
            pausePlayback()
        } else {
            // If restored on app start, start from the saved timestamp
            playTrack(current, _currentPositionMs.value)
        }
    }

    fun playNext() {
        val next = getNextTrack()
        if (next != null) {
            val queue = _playlistQueue.value
            currentQueueIndex = queue.indexOf(next)
            playTrack(next)
        } else {
            stopPlayback()
        }
    }

    fun playPrevious() {
        val queue = _playlistQueue.value
        if (queue.isEmpty()) return
        val prevIndex = if (currentQueueIndex > 0) currentQueueIndex - 1 else queue.size - 1
        currentQueueIndex = prevIndex
        playTrack(queue[prevIndex])
    }

    private fun getNextTrack(): Track? {
        val queue = _playlistQueue.value
        if (queue.isEmpty()) return null

        if (_repeatMode.value == RepeatMode.ONE) {
            return _currentTrack.value
        }

        if (_isShuffle.value) {
            val randomIndex = (queue.indices).random()
            return queue[randomIndex]
        }

        if (currentQueueIndex < queue.size - 1) {
            return queue[currentQueueIndex + 1]
        } else if (_repeatMode.value == RepeatMode.ALL) {
            return queue[0]
        }

        return null
    }

    private fun onTrackCompleted() {
        playNext()
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        primaryPlayer?.seekTo(positionMs.toInt())
        savePlaybackState(positionMs)
        notifyPlaybackState()
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun toggleGaplessMode() {
        _isGaplessEnabled.value = !_isGaplessEnabled.value
        prefs.edit().putBoolean("gapless_enabled", _isGaplessEnabled.value).apply()
        if (_isGaplessEnabled.value) {
            // Trigger pre-analysis of current and upcoming track for instant silence cutting
            _currentTrack.value?.let { track ->
                engineScope.launch {
                    currentTrimBounds = AudioSilenceDetector.analyzeTrack(context, track.audioPath, track.durationMs)
                }
            }
            getNextTrack()?.let { next ->
                engineScope.launch {
                    AudioSilenceDetector.analyzeTrack(context, next.audioPath, next.durationMs)
                }
            }
        } else {
            currentTrimBounds = null
        }
    }

    // Equalizer Controls with Smooth Cross-Fade Transitions
    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        notificationManager.updateNotification(_currentTrack.value, _isPlaying.value, _currentPositionMs.value)
        eqTransitionJob?.cancel()
        eqTransitionJob = engineScope.launch {
            val eq = equalizerFx
            val bb = bassBoostFx
            val numSteps = 12
            val stepDelayMs = 15L // ~180ms silky smooth ramp

            val targetGains = if (enabled) _bandGains.value else FloatArray(5) { 0f }
            val targetBass = if (enabled) _bassBoostLevel.value else 0f

            val currentGains = if (enabled) FloatArray(5) { 0f } else _bandGains.value.copyOf()
            val currentBass = if (enabled) 0f else _bassBoostLevel.value

            if (enabled) {
                try {
                    // Start from 0 dB before enabling effect to avoid click
                    if (eq != null) {
                        for (i in 0 until eq.numberOfBands.toInt().coerceAtMost(5)) {
                            eq.setBandLevel(i.toShort(), 0)
                        }
                        eq.enabled = true
                    }
                    if (bb != null && bb.strengthSupported) {
                        bb.setStrength(0)
                        bb.enabled = true
                    }
                } catch (_: Exception) {}
            }

            for (step in 1..numSteps) {
                val fraction = step.toFloat() / numSteps.toFloat()
                try {
                    if (eq != null && eq.enabled) {
                        val numBands = eq.numberOfBands.toInt()
                        for (i in 0 until numBands.coerceAtMost(5)) {
                            val interpolatedGain = currentGains[i] + (targetGains[i] - currentGains[i]) * fraction
                            val mB = (interpolatedGain * 100).toInt().toShort()
                            eq.setBandLevel(i.toShort(), mB)
                        }
                    }
                    if (bb != null && bb.enabled && bb.strengthSupported) {
                        val interpolatedBass = currentBass + (targetBass - currentBass) * fraction
                        bb.setStrength((interpolatedBass * 1000).toInt().toShort())
                    }
                } catch (_: Exception) {}
                delay(stepDelayMs)
            }

            if (!enabled) {
                try {
                    eq?.enabled = false
                    bb?.enabled = false
                } catch (_: Exception) {}
            }
        }
    }

    fun setBassBoost(level: Float) { // 0.0f to 1.0f
        _bassBoostLevel.value = level
        if (!_eqEnabled.value && level > 0f) {
            setEqEnabled(true)
        }
        if (_eqEnabled.value) {
            try {
                if (bassBoostFx?.strengthSupported == true) {
                    bassBoostFx?.setStrength((level * 1000).toInt().toShort())
                }
            } catch (_: Exception) {}
        }
        if (_selectedPreset.value != EqPreset.CUSTOM) {
            _selectedPreset.value = EqPreset.CUSTOM
        }
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..4) return
        val newGains = _bandGains.value.copyOf()
        newGains[bandIndex] = gainDb
        _bandGains.value = newGains

        if (!_eqEnabled.value) {
            setEqEnabled(true)
        }
        applyEqualizerGains()
        if (_selectedPreset.value != EqPreset.CUSTOM) {
            _selectedPreset.value = EqPreset.CUSTOM
        }
    }

    fun selectPreset(preset: EqPreset, enableEqIfOff: Boolean = true) {
        _selectedPreset.value = preset
        if (enableEqIfOff && !_eqEnabled.value) {
            setEqEnabled(true)
        }
        if (preset != EqPreset.CUSTOM) {
            _bandGains.value = preset.gains.copyOf()
            _bassBoostLevel.value = preset.bassBoost
            applyEqualizerGains()
            if (_eqEnabled.value) {
                try {
                    if (bassBoostFx?.strengthSupported == true) {
                        bassBoostFx?.setStrength((preset.bassBoost * 1000).toInt().toShort())
                    }
                } catch (_: Exception) {}
            }
        }
        notificationManager.updateNotification(_currentTrack.value, _isPlaying.value, _currentPositionMs.value)
    }

    fun resetEqualizer() {
        selectPreset(EqPreset.FLAT)
    }

    fun cycleEqPreset() {
        val presets = listOf(
            EqPreset.BASS_BOOSTER,
            EqPreset.CUSTOM_BASS,
            EqPreset.ROCK,
            EqPreset.POP,
            EqPreset.VOCAL,
            EqPreset.FLAT
        )
        val currentIndex = presets.indexOf(_selectedPreset.value)
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % presets.size else 0
        if (!_eqEnabled.value) {
            setEqEnabled(true)
        }
        selectPreset(presets[nextIndex])
    }

    fun cycleBassBoost() {
        if (!_eqEnabled.value) {
            setEqEnabled(true)
        }
        val current = _bassBoostLevel.value
        val next = when {
            current < 0.25f -> 0.35f
            current < 0.65f -> 0.75f
            current < 0.95f -> 1.0f
            else -> 0.0f
        }
        setBassBoost(next)
    }

    private fun applyEqualizerGains() {
        if (!_eqEnabled.value) return
        try {
            val eq = equalizerFx ?: return
            val numBands = eq.numberOfBands.toInt()
            val gains = _bandGains.value

            for (i in 0 until numBands.coerceAtMost(5)) {
                val mB = (gains[i] * 100).toInt().toShort()
                eq.setBandLevel(i.toShort(), mB)
            }
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        unregisterBecomingNoisyReceiver()
        abandonAudioFocus()

        silenceAnalysisJob?.cancel()
        isSynthPlaying = false
        synthAudioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        synthAudioTrack = null

        primaryPlayer?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        primaryPlayer = null

        _isPlaying.value = false
        notificationManager.cancelNotification()
    }

    fun setSurround360Enabled(enabled: Boolean) {
        _surround360Enabled.value = enabled
        try {
            virtualizerFx?.enabled = enabled
            if (enabled && virtualizerFx?.strengthSupported == true) {
                val mode = _spatialMode.value
                val strength = (_surroundStrength.value * mode.virtualizerStrength * 1000).toInt().toShort()
                virtualizerFx?.setStrength(strength)
            }
            
            // When 360 sound is enabled, configure acoustics if needed
            if (enabled) {
                val currentMode = _spatialMode.value
                if (currentMode == SpatialMode.ARENA_360 && _selectedReverb.value == ReverbEffect.OFF) {
                    setReverbEffect(ReverbEffect.LARGE_HALL)
                }
            }
        } catch (e: Exception) {
            Log.d("AudioPlayerEngine", "Surround 360 toggle error", e)
        }
    }

    fun setSpatialMode(mode: SpatialMode) {
        _spatialMode.value = mode
        if (_surround360Enabled.value) {
            try {
                if (virtualizerFx?.strengthSupported == true) {
                    val strength = (_surroundStrength.value * mode.virtualizerStrength * 1000).toInt().toShort()
                    virtualizerFx?.setStrength(strength)
                }
                if (mode == SpatialMode.ARENA_360 && _selectedReverb.value == ReverbEffect.OFF) {
                    setReverbEffect(ReverbEffect.LARGE_HALL)
                }
            } catch (_: Exception) {}
        }
    }

    fun setSurroundStrength(strength: Float) {
        _surroundStrength.value = strength
        if (_surround360Enabled.value) {
            try {
                if (virtualizerFx?.strengthSupported == true) {
                    val mode = _spatialMode.value
                    virtualizerFx?.setStrength((strength * mode.virtualizerStrength * 1000).toInt().toShort())
                }
            } catch (_: Exception) {}
        }
    }

    fun setSlowedReverbEnabled(enabled: Boolean) {
        _isSlowedReverbEnabled.value = enabled
        if (enabled) {
            _playbackSpeed.value = 0.90f
            setReverbEffect(ReverbEffect.HALL)
        } else {
            _playbackSpeed.value = 1.0f
            if (_selectedReverb.value == ReverbEffect.HALL) {
                setReverbEffect(ReverbEffect.OFF)
            }
        }
        applyPlaybackSpeed()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.70f, 1.30f)
        _playbackSpeed.value = clamped
        if (clamped < 0.95f && !_isSlowedReverbEnabled.value) {
            _isSlowedReverbEnabled.value = true
            if (_selectedReverb.value == ReverbEffect.OFF) {
                setReverbEffect(ReverbEffect.HALL)
            }
        } else if (clamped == 1.0f && _isSlowedReverbEnabled.value) {
            _isSlowedReverbEnabled.value = false
            if (_selectedReverb.value == ReverbEffect.HALL) {
                setReverbEffect(ReverbEffect.OFF)
            }
        }
        applyPlaybackSpeed()
    }

    private fun applyPlaybackSpeed() {
        try {
            primaryPlayer?.let { player ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val wasPlaying = _isPlaying.value || try { player.isPlaying } catch (_: Exception) { false }
                    val currentParams = player.playbackParams
                    currentParams.speed = _playbackSpeed.value
                    currentParams.pitch = if (_isSlowedReverbEnabled.value) (_playbackSpeed.value * 0.96f).coerceIn(0.75f, 1.0f) else _playbackSpeed.value
                    player.playbackParams = currentParams
                    if (!wasPlaying) {
                        try {
                            player.pause()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("AudioPlayerEngine", "Error setting playback speed", e)
        }
    }

    fun setReverbEffect(effect: ReverbEffect) {
        _selectedReverb.value = effect
        try {
            presetReverbFx?.let { reverb ->
                if (effect == ReverbEffect.OFF) {
                    reverb.preset = PresetReverb.PRESET_NONE
                    reverb.enabled = false
                } else {
                    reverb.preset = effect.presetValue
                    reverb.enabled = true
                }
            }
            val sendLevel = if (effect != ReverbEffect.OFF) 1.0f else 0.0f
            try {
                primaryPlayer?.setAuxEffectSendLevel(sendLevel)
            } catch (_: Exception) {}
            try {
                synthAudioTrack?.setAuxEffectSendLevel(sendLevel)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.d("AudioPlayerEngine", "Reverb effect error", e)
        }
    }

    fun release() {
        stopPlayback()
        handler.removeCallbacks(progressUpdater)
        try {
            equalizerFx?.release()
            bassBoostFx?.release()
            virtualizerFx?.release()
            presetReverbFx?.release()
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerEngine? = null

        fun getInstance(context: Context): AudioPlayerEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = AudioPlayerEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
