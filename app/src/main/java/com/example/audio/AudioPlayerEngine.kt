package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
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
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.db.AuraDatabase
import com.example.data.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
import kotlin.math.PI
import kotlin.math.cos
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
    private var nextPlayer: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private var nextPlayerTrack: Track? = null
    private var nextPlayerQueueIndex: Int = -1
    private var isNextPlayerPrepared: Boolean = false
    private var nextPlayerGeneration: Long = 0L
    private var currentAudioSessionId: Int = 0
    private var plannedNextIndex: Int? = null
    private var isCrossfading: Boolean = false
    private var crossfadeJob: Job? = null

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
                checkAudioOutputDevices()
            }
        }
    }

    private var synthAudioTrack: AudioTrack? = null
    private var isSynthPlaying = false
    private var synthThread: Thread? = null
    private var currentSynthActiveFlag: AtomicBoolean? = null
    private var fadingOutSynthTrack: AudioTrack? = null
    private var fadingOutSynthFlag: AtomicBoolean? = null

    // WakeLock to prevent CPU from going into deep sleep during background audio playback
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Harmanx::AudioPlaybackWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(6 * 60 * 60 * 1000L) // Safe 6 hour max timeout
                Log.d("AudioPlayerEngine", "WakeLock acquired for background audio playback")
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Could not acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("AudioPlayerEngine", "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Could not release WakeLock", e)
        }
    }

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

    private val _isGaplessEnabled = MutableStateFlow(true)
    val isGaplessEnabled: StateFlow<Boolean> = _isGaplessEnabled.asStateFlow()

    // External Audio Routing State (Bluetooth / Wired Aux detection)
    private val _isPhysicalExternalAudioConnected = MutableStateFlow(false)
    private val _isAuxSimulated = MutableStateFlow(false)
    private val _isExternalAudioConnected = MutableStateFlow(false)
    val isExternalAudioConnected: StateFlow<Boolean> = _isExternalAudioConnected.asStateFlow()
    val isAuxSimulated: StateFlow<Boolean> = _isAuxSimulated.asStateFlow()
    private var userEqPreference = false

    // Equalizer State (Default: OFF - enabled ONLY when Bluetooth or Wired Aux is connected)
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

    private val _customPresets = MutableStateFlow<List<CustomEqPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomEqPreset>> = _customPresets.asStateFlow()

    private val _selectedCustomPresetId = MutableStateFlow<String?>(null)
    val selectedCustomPresetId: StateFlow<String?> = _selectedCustomPresetId.asStateFlow()

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

                    // Smooth Crossfade / Gapless Transition Engine (active only when Gapless toggle is ON)
                    if (_isGaplessEnabled.value && dur > 3000L && !isCrossfading) {
                        val effectiveCrossfadeMs = minOf(AUTO_CROSSFADE_MS, (dur / 2L).coerceAtLeast(1000L))
                        val remainingMs = dur - curPos

                        if (remainingMs in 1L..effectiveCrossfadeMs) {
                            if (isNextPlayerPrepared && nextPlayer != null) {
                                startCrossfadeTransition(remainingMs)
                            } else {
                                triggerAutoCrossfadeNext(remainingMs)
                            }
                        } else {
                            // Only cut trailing dead air when genuine digital silence (>800ms) was confirmed
                            val bounds = currentTrimBounds
                            if (bounds != null && bounds.outroEndMs < dur - 800L && curPos >= bounds.outroEndMs && curPos < dur) {
                                Log.d("AudioPlayerEngine", "Gapless active: Genuine dead air at ${curPos}ms (total ${dur}ms). Transitioning seamlessly.")
                                playNext()
                            }
                        }
                    }
                }
            } ?: run {
                if (isSynthPlaying) {
                    val newPos = (_currentPositionMs.value + 150).coerceAtMost(_durationMs.value)
                    _currentPositionMs.value = newPos
                    val dur = _durationMs.value
                    val remainingMs = dur - newPos
                    if (_isGaplessEnabled.value && dur > 3000L && !isCrossfading) {
                        val effectiveCrossfadeMs = minOf(
                            AUTO_CROSSFADE_MS,
                            (dur / 2L).coerceAtLeast(1000L)
                        )
                        if (remainingMs in 1L..effectiveCrossfadeMs) {
                            triggerAutoCrossfadeNext(remainingMs)
                            handler.postDelayed(this, 120)
                            return
                        }
                    }
                    if (newPos >= dur) {
                        onTrackCompleted()
                    }
                }
            }
            handler.postDelayed(this, 120)
        }
    }

    init {
        val savedGapless = prefs.getBoolean("gapless_enabled", true)
        _isGaplessEnabled.value = savedGapless
        _customPresets.value = loadCustomPresets()
        handler.post(progressUpdater)
        PlaybackControlReceiver.audioPlayerEngine = this
        initAudioDeviceMonitoring()
        restoreLastPlaybackState()
    }

    private fun initAudioDeviceMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        Log.d("AudioPlayerEngine", "Audio devices added -> checking connection")
                        checkAudioOutputDevices()
                    }
                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        Log.d("AudioPlayerEngine", "Audio devices removed -> checking connection")
                        checkAudioOutputDevices()
                    }
                }, handler)
            } catch (e: Exception) {
                Log.e("AudioPlayerEngine", "Failed to register AudioDeviceCallback", e)
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.device.action.ACL_CONNECTED")
                addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    Log.d("AudioPlayerEngine", "Audio broadcast received: ${intent?.action}")
                    handler.postDelayed({ checkAudioOutputDevices() }, 300)
                }
            }, filter)
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Failed to register audio intent receiver", e)
        }

        checkAudioOutputDevices()
    }

    fun checkAudioOutputDevices() {
        val physicalConnected = isExternalAudioDeviceConnected()
        _isPhysicalExternalAudioConnected.value = physicalConnected
        val effectiveConnected = physicalConnected || _isAuxSimulated.value
        val previousEffective = _isExternalAudioConnected.value
        _isExternalAudioConnected.value = effectiveConnected

        Log.d("AudioPlayerEngine", "Audio output: physical=$physicalConnected, simulated=${_isAuxSimulated.value}, effective=$effectiveConnected (previous=$previousEffective)")

        if (!effectiveConnected) {
            // When disconnected from Bluetooth / Aux and on normal device speakers:
            // Equalizer MUST NOT WORK and MUST NOT REMAIN ACTIVE!
            if (_eqEnabled.value) {
                userEqPreference = true
                Log.d("AudioPlayerEngine", "Normal device speakers active: Equalizer suspended to protect speakers.")
                disableEqualizerHardwareEffect()
                _eqEnabled.value = false
                notificationManager.updateNotification(_currentTrack.value, _isPlaying.value, _currentPositionMs.value)
            }
        } else {
            // When Bluetooth or Aux connects:
            // If user had previously enabled EQ, restore it smoothly!
            if (userEqPreference && !_eqEnabled.value) {
                Log.d("AudioPlayerEngine", "External audio connected: Restoring Equalizer.")
                setEqEnabledInternal(true)
            }
        }
    }

    fun setSimulateAux(simulated: Boolean) {
        _isAuxSimulated.value = simulated
        checkAudioOutputDevices()
    }

    fun toggleSimulateAux() {
        setSimulateAux(!_isAuxSimulated.value)
    }

    private fun isExternalAudioDeviceConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { device ->
                    when (device.type) {
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_AUX_LINE,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_USB_DEVICE -> true
                        else -> {
                            var matched = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && device.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                                matched = true
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && device.type == AudioDeviceInfo.TYPE_HEARING_AID) {
                                matched = true
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                // 26 = TYPE_BLE_HEADSET, 27 = TYPE_BLE_SPEAKER, 30 = TYPE_BLE_BROADCAST
                                if (device.type == 26 || device.type == 27 || device.type == 30) {
                                    matched = true
                                }
                            }
                            matched
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Error detecting audio output devices", e)
            false
        }
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
    fun getNotificationManager(): PlaybackNotificationManager = notificationManager

    private fun notifyPlaybackState() {
        notificationManager.updateNotification(_currentTrack.value, _isPlaying.value, _currentPositionMs.value)
        if (_isPlaying.value) {
            try {
                val serviceIntent = Intent(context, HarmanxMediaBrowserService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.d("AudioPlayerEngine", "Notice starting HarmanxMediaBrowserService: ${e.message}")
            }
        }
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

    fun addToPlayNext(track: Track) {
        val current = _currentTrack.value
        if (current == null) {
            setQueue(listOf(track), 0)
            return
        }
        val queue = _playlistQueue.value.toMutableList()
        val currIdx = if (currentQueueIndex in queue.indices && queue[currentQueueIndex].id == current.id) {
            currentQueueIndex
        } else {
            val found = queue.indexOfFirst { it.id == current.id }
            if (found != -1) {
                found
            } else {
                queue.add(0, current)
                currentQueueIndex = 0
                0
            }
        }
        val insertIdx = (currIdx + 1).coerceAtMost(queue.size)
        // If track exists later in queue, remove it so it moves to next
        val laterIdx = queue.indices.firstOrNull { it > currIdx && queue[it].id == track.id }
        if (laterIdx != null) {
            queue.removeAt(laterIdx)
        }
        queue.add(insertIdx, track)
        _playlistQueue.value = queue
        invalidateAndPrepareNextPlayer()
    }

    fun addToQueue(track: Track) {
        val current = _currentTrack.value
        if (current == null) {
            setQueue(listOf(track), 0)
            return
        }
        val queue = _playlistQueue.value.toMutableList()
        if (queue.none { it.id == current.id }) {
            queue.add(0, current)
            currentQueueIndex = 0
        }
        queue.add(track)
        _playlistQueue.value = queue
        invalidateAndPrepareNextPlayer()
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val queue = _playlistQueue.value.toMutableList()
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        _playlistQueue.value = queue
        val current = _currentTrack.value
        if (current != null) {
            val newIdx = queue.indexOfFirst { it.id == current.id }
            if (newIdx != -1) currentQueueIndex = newIdx
        }
        invalidateAndPrepareNextPlayer()
    }

    fun removeFromQueue(index: Int) {
        val queue = _playlistQueue.value.toMutableList()
        if (index !in queue.indices) return
        val isRemovingCurrent = (index == currentQueueIndex) || (queue[index].id == _currentTrack.value?.id)
        queue.removeAt(index)
        _playlistQueue.value = queue
        if (isRemovingCurrent) {
            if (queue.isNotEmpty()) {
                val nextIndex = index.coerceAtMost(queue.size - 1)
                currentQueueIndex = nextIndex
                playTrack(queue[nextIndex])
            } else {
                currentQueueIndex = -1
                stopPlayback()
            }
        } else {
            val current = _currentTrack.value
            if (current != null) {
                val newIdx = queue.indexOfFirst { it.id == current.id }
                if (newIdx != -1) currentQueueIndex = newIdx
            }
            invalidateAndPrepareNextPlayer()
        }
    }

    fun playQueueTrack(index: Int) {
        val queue = _playlistQueue.value
        if (index in queue.indices) {
            currentQueueIndex = index
            playTrack(queue[index])
        }
    }

    fun clearQueue() {
        val current = _currentTrack.value
        if (current != null) {
            _playlistQueue.value = listOf(current)
            currentQueueIndex = 0
        } else {
            _playlistQueue.value = emptyList()
            currentQueueIndex = -1
        }
        invalidateAndPrepareNextPlayer()
    }

    fun playTrack(track: Track, startPositionMs: Long = 0L, crossfadeOverrideMs: Long? = null) {
        val shouldSmoothCrossfade = startPositionMs == 0L &&
            _isGaplessEnabled.value &&
            _isPlaying.value &&
            (primaryPlayer != null || (isSynthPlaying && synthAudioTrack != null))

        val fadeDurationMs = if (shouldSmoothCrossfade) {
            val speed = _playbackSpeed.value.coerceIn(0.5f, 1.5f)
            val baseFade = crossfadeOverrideMs ?: MANUAL_SKIP_CROSSFADE_MS
            (baseFade / speed).toLong().coerceIn(300L, AUTO_CROSSFADE_MS)
        } else 0L

        if (shouldSmoothCrossfade) {
            detachCurrentSourceForCrossfade()
        } else {
            stopPlayback()
        }

        _currentTrack.value = track
        _durationMs.value = track.durationMs
        plannedNextIndex = null

        // If Gapless Mode is toggled ON: only cut genuine digital dead-air >400ms if cached
        var actualStartMs = startPositionMs
        if (_isGaplessEnabled.value && startPositionMs == 0L) {
            val cached = AudioSilenceDetector.getCached(track.audioPath)
            if (cached != null && cached.introStartMs > 400L) {
                actualStartMs = cached.introStartMs
                Log.d("AudioPlayerEngine", "Gapless active: Cut ${cached.introStartMs}ms digital intro silence.")
            }
        }

        _currentPositionMs.value = actualStartMs

        requestAudioFocus()
        registerBecomingNoisyReceiver()

        // Background silence analysis for genuine digital dead-air only
        silenceAnalysisJob?.cancel()
        silenceAnalysisJob = engineScope.launch {
            val bounds = AudioSilenceDetector.analyzeTrack(context, track.audioPath, track.durationMs)
            currentTrimBounds = bounds

            if (_isGaplessEnabled.value && startPositionMs == 0L && bounds.introStartMs > 400L) {
                withContext(Dispatchers.Main) {
                    primaryPlayer?.let { player ->
                        if (player.isPlaying && player.currentPosition < bounds.introStartMs && player.currentPosition < 800) {
                            Log.d("AudioPlayerEngine", "Gapless active: Skipping ${bounds.introStartMs}ms digital intro silence.")
                            seekTo(bounds.introStartMs)
                        }
                    }
                }
            }
        }

        // 1. Check if track is a preloaded synthetic sample track
        if (track.audioPath.startsWith("synth_demo_")) {
            playSynthFlacAudio(track, startSilentForCrossfade = shouldSmoothCrossfade)
            if (shouldSmoothCrossfade) {
                runCrossfadeVolumeRamp(
                    incomingMp = null,
                    incomingSynth = synthAudioTrack,
                    fadeDurationMs = fadeDurationMs,
                    incomingTitle = track.title
                )
            }
            return
        }

        // 2. Standard Android MediaPlayer with pre-buffered dual-player gapless support
        try {
            val prevSessionId = currentAudioSessionId
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                if (shouldSmoothCrossfade && prevSessionId != 0) {
                    try {
                        audioSessionId = prevSessionId
                    } catch (_: Exception) {}
                }
            }
            setDataSourceRobustly(player, track.audioPath)
            player.prepare()

            if (actualStartMs > 0 && actualStartMs < track.durationMs) {
                player.seekTo(actualStartMs.toInt())
            }
            if (shouldSmoothCrossfade) {
                player.setVolume(0f, 0f)
            }
            player.start()
            primaryPlayer = player
            val newSessionId = player.audioSessionId
            acquireWakeLock()
            _isPlaying.value = true
            if (shouldSmoothCrossfade && prevSessionId != 0 && newSessionId == prevSessionId) {
                try {
                    presetReverbFx?.let { reverb ->
                        player.attachAuxEffect(reverb.id)
                        player.setAuxEffectSendLevel(if (_selectedReverb.value != ReverbEffect.OFF) 1.0f else 0.0f)
                    }
                } catch (_: Exception) {}
            } else {
                currentAudioSessionId = newSessionId
                setupAudioFx(currentAudioSessionId)
            }
            applyPlaybackSpeed()
            player.setOnCompletionListener { onTrackCompleted() }
            notifyPlaybackState()
            if (shouldSmoothCrossfade) {
                runCrossfadeVolumeRamp(
                    incomingMp = player,
                    incomingSynth = null,
                    fadeDurationMs = fadeDurationMs,
                    incomingTitle = track.title
                )
            } else {
                prepareNextPlayerForGapless()
            }
            Log.d("AudioPlayerEngine", "Playing via MediaPlayer: ${track.title} (session=$currentAudioSessionId)")
            return
        } catch (e: Exception) {
            Log.w("AudioPlayerEngine", "MediaPlayer could not prepare ${track.title} (${e.message})")
            try { primaryPlayer?.release() } catch (_: Exception) {}
            primaryPlayer = null
            if (shouldSmoothCrossfade) {
                cancelCrossfade(restoreVolume = false)
            }
        }

        // File completely unreadable (corrupt or missing)
        Log.e("AudioPlayerEngine", "Audio track could not be decoded: ${track.title} (${track.audioPath})")
        _isPlaying.value = false
        releaseWakeLock()
        notifyPlaybackState()
    }

    private fun setDataSourceRobustly(player: MediaPlayer, audioPath: String) {
        val uri = Uri.parse(audioPath)
        if (audioPath.startsWith("content://")) {
            var configured = false
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.declaredLength < 0) {
                        player.setDataSource(afd.fileDescriptor)
                    } else {
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                    }
                    configured = true
                }
            } catch (e: Exception) {
                Log.d("AudioPlayerEngine", "openAssetFileDescriptor notice: ${e.message}")
            }
            if (!configured) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        player.setDataSource(pfd.fileDescriptor)
                        configured = true
                    }
                } catch (e: Exception) {
                    Log.d("AudioPlayerEngine", "openFileDescriptor notice: ${e.message}")
                }
            }
            if (!configured) {
                player.setDataSource(context, uri)
            }
        } else if (audioPath.startsWith("file://")) {
            player.setDataSource(uri.path ?: audioPath.removePrefix("file://"))
        } else if (audioPath.startsWith("/")) {
            player.setDataSource(audioPath)
        } else if (audioPath.startsWith("http://") || audioPath.startsWith("https://")) {
            player.setDataSource(audioPath)
        } else {
            throw IllegalArgumentException("Non-standard audio path: $audioPath")
        }
    }

    private fun playSynthFlacAudio(track: Track, startSilentForCrossfade: Boolean = false) {
        if (!startSilentForCrossfade) {
            stopPlayback()
        }
        requestAudioFocus()
        registerBecomingNoisyReceiver()

        acquireWakeLock()
        _isPlaying.value = true
        isSynthPlaying = true
        val trackActiveFlag = AtomicBoolean(true)
        currentSynthActiveFlag = trackActiveFlag
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

            if (startSilentForCrossfade) {
                try { audioTrack.setVolume(0f) } catch (_: Exception) {}
            }
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

                val canApplyEq = _eqEnabled.value && _isExternalAudioConnected.value
                var currentSmoothedBass = if (canApplyEq) _bassBoostLevel.value else 0f
                var currentSmoothedSurround = if (_surround360Enabled.value) (_surroundStrength.value * _spatialMode.value.virtualizerStrength) else 0f

                // Algorithmic Schroeder Reverb comb-filter ring buffers (Room, Hall, Large Hall)
                val combSizes = intArrayOf(1557, 1617, 1491, 1422, 1277, 1116)
                val combBuffers = Array(combSizes.size) { FloatArray(combSizes[it]) }
                val combIndices = IntArray(combSizes.size)

                while (trackActiveFlag.get()) {
                    val currentSpeed = _playbackSpeed.value.coerceIn(0.6f, 1.5f)
                    val effectiveTempo = baseTempoBpm * currentSpeed
                    val samplesPerBeat = (sampleRate * 60.0 / effectiveTempo).toInt().coerceAtLeast(100)
                    val pitchFactor = if (_isSlowedReverbEnabled.value) 0.90 else currentSpeed.toDouble()

                    val isEqActive = _eqEnabled.value && _isExternalAudioConnected.value
                    val targetBass = if (isEqActive) _bassBoostLevel.value else 0f
                    val targetSurround = if (_surround360Enabled.value) (_surroundStrength.value * _spatialMode.value.virtualizerStrength) else 0f
                    currentSmoothedBass += (targetBass - currentSmoothedBass) * 0.05f
                    currentSmoothedSurround += (targetSurround - currentSmoothedSurround) * 0.05f

                    val bassBoostGain = 1.0 + (currentSmoothedBass * 2.8)
                    val currentSpatialMode = _spatialMode.value
                    val clarityBoost = if (_surround360Enabled.value) currentSpatialMode.centerClarityBoost else 0f
                    val eqLow = if (isEqActive) (1.0 + (_bandGains.value.getOrElse(0) { 0f } / 12.0) * 0.8) else 1.0
                    val eqMid = if (isEqActive) (1.0 + ((_bandGains.value.getOrElse(2) { 0f } / 12.0) + clarityBoost) * 0.8) else (1.0 + clarityBoost * 0.8)
                    val eqHigh = if (isEqActive) (1.0 + ((_bandGains.value.getOrElse(4) { 0f } / 12.0) + (clarityBoost * 0.5f)) * 0.8) else (1.0 + clarityBoost * 0.5f * 0.8)

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
                        if (trackActiveFlag.get() && audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack.write(samples, 0, samples.size)
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
        val canEnableEq = _eqEnabled.value && _isExternalAudioConnected.value
        // Safely initialize Equalizer
        try {
            equalizerFx?.release()
            equalizerFx = null
            if (sessionId != 0) {
                equalizerFx = Equalizer(0, sessionId).apply {
                    enabled = canEnableEq
                }
                if (canEnableEq) {
                    applyEqualizerGains()
                }
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
                    enabled = canEnableEq
                    if (strengthSupported && canEnableEq) {
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
                try {
                    val savedTrackId = prefs.getLong("saved_track_id", -1L)
                    val db = AuraDatabase.getInstance(context)
                    
                    if (savedTrackId != -1L) {
                        val track = db.trackDao().getTrackById(savedTrackId)
                        if (track != null) {
                            val all = db.trackDao().getAllTracks().first()
                            val index = all.indexOfFirst { it.id == savedTrackId }
                            if (index != -1) {
                                _playlistQueue.value = all
                                currentQueueIndex = index
                            } else {
                                _playlistQueue.value = listOf(track)
                                currentQueueIndex = 0
                            }
                            
                            val savedPos = prefs.getLong("saved_position_ms", 0L)
                            
                            // Switch to main thread if required by playTrack, though playTrack is usually fine on main or we can just call it
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                playTrack(track, savedPos)
                            }
                            return@launch
                        }
                    }
                    
                    // Fallback if no saved track
                    val all = db.trackDao().getAllTracks().first()
                    if (all.isNotEmpty()) {
                        setQueue(all, 0)
                    }
                } catch (e: Exception) {
                    Log.e("AudioPlayerEngine", "Error resuming last track", e)
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
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery == "song" || lowerQuery == "music" || lowerQuery == "play song" || lowerQuery == "play music" || lowerQuery == "some music") {
            playOrResume()
            return
        }

        engineScope.launch {
            val db = AuraDatabase.getInstance(context)
            val results = db.trackDao().searchTracks(query).first()
            if (results.isNotEmpty()) {
                setQueue(results, 0)
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    playOrResume()
                }
            }
        }
    }

    fun pausePlayback() {
        cancelCrossfade(restoreVolume = true)
        primaryPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
        if (isSynthPlaying) {
            currentSynthActiveFlag?.set(false)
            currentSynthActiveFlag = null
            try {
                synthAudioTrack?.pause()
                synthAudioTrack?.flush()
            } catch (_: Exception) {}
            isSynthPlaying = false
        }
        _isPlaying.value = false
        releaseWakeLock()
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
                acquireWakeLock()
                player.start()
                _isPlaying.value = true
                notifyPlaybackState()
                if (_isGaplessEnabled.value && nextPlayer == null) {
                    prepareNextPlayerForGapless()
                }
            }
            return
        }

        if (isSynthPlaying) {
            pausePlayback()
        } else {
            // If restored on app start or new session, start from the saved timestamp
            playTrack(current, _currentPositionMs.value)
        }
    }

    fun playNext() {
        val queue = _playlistQueue.value
        val nextIdx = resolveNextQueueIndex()
        if (nextIdx != null && nextIdx in queue.indices) {
            val next = queue[nextIdx]
            // If nextPlayer is already pre-buffered for this exact track & we aren't mid-crossfade:
            if (_isGaplessEnabled.value && !isCrossfading && isNextPlayerPrepared && nextPlayer != null && nextPlayerQueueIndex == nextIdx && nextPlayerTrack?.id == next.id && primaryPlayer != null) {
                if (_isPlaying.value) {
                    startCrossfadeTransition(MANUAL_SKIP_CROSSFADE_MS)
                } else {
                    promoteNextPlayerDirectly()
                }
            } else {
                currentQueueIndex = nextIdx
                plannedNextIndex = null
                playTrack(next)
            }
        } else {
            stopPlayback()
        }
    }

    private fun triggerAutoCrossfadeNext(remainingMs: Long) {
        val queue = _playlistQueue.value
        val nextIdx = resolveNextQueueIndex()
        if (nextIdx != null && nextIdx in queue.indices) {
            currentQueueIndex = nextIdx
            plannedNextIndex = null
            playTrack(queue[nextIdx], startPositionMs = 0L, crossfadeOverrideMs = remainingMs)
        }
    }

    fun playPrevious() {
        val queue = _playlistQueue.value
        if (queue.isEmpty()) return
        val prevIndex = if (currentQueueIndex > 0) currentQueueIndex - 1 else queue.size - 1
        currentQueueIndex = prevIndex
        plannedNextIndex = null
        playTrack(queue[prevIndex])
    }

    private fun resolveNextQueueIndex(): Int? {
        val queue = _playlistQueue.value
        if (queue.isEmpty()) return null

        if (_repeatMode.value == RepeatMode.ONE) {
            return if (currentQueueIndex in queue.indices) currentQueueIndex else 0
        }

        if (_isShuffle.value) {
            val existing = plannedNextIndex
            if (existing != null && existing in queue.indices && (queue.size == 1 || existing != currentQueueIndex)) {
                return existing
            }
            val candidates = if (queue.size > 1) {
                queue.indices.filter { it != currentQueueIndex }
            } else {
                queue.indices.toList()
            }
            val chosen = candidates.random()
            plannedNextIndex = chosen
            return chosen
        }

        if (currentQueueIndex < queue.size - 1) {
            return currentQueueIndex + 1
        } else if (_repeatMode.value == RepeatMode.ALL) {
            return 0
        }

        return null
    }

    private fun getNextTrack(): Track? {
        val queue = _playlistQueue.value
        val idx = resolveNextQueueIndex() ?: return null
        return queue.getOrNull(idx)
    }

    private fun onTrackCompleted() {
        if (isCrossfading) {
            // Crossfade coroutine is already running; outgoing finished slightly early
            return
        }

        val armedNext = nextPlayer
        val armedTrack = nextPlayerTrack
        val armedIdx = nextPlayerQueueIndex
        if (_isGaplessEnabled.value && isNextPlayerPrepared && armedNext != null && armedTrack != null) {
            promoteNextPlayerAfterHardwareHandoff(armedNext, armedTrack, armedIdx)
            return
        }

        playNext()
    }

    private fun promoteNextPlayerAfterHardwareHandoff(
        armedNext: MediaPlayer,
        armedTrack: Track,
        armedIdx: Int
    ) {
        val oldPrimary = primaryPlayer
        primaryPlayer = armedNext
        nextPlayer = null
        nextPlayerTrack = null
        nextPlayerQueueIndex = -1
        isNextPlayerPrepared = false
        plannedNextIndex = null

        currentQueueIndex = armedIdx
        _currentTrack.value = armedTrack
        _isPlaying.value = true

        try {
            if (!armedNext.isPlaying) {
                armedNext.start()
            }
            armedNext.setVolume(1.0f, 1.0f)
            armedNext.setOnCompletionListener { onTrackCompleted() }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Gapless handoff fallback for ${armedTrack.title}: ${e.message}")
            try { oldPrimary?.release() } catch (_: Exception) {}
            playTrack(armedTrack)
            return
        }

        try {
            oldPrimary?.setOnCompletionListener(null)
            oldPrimary?.release()
        } catch (_: Exception) {}

        val newSessionId = try { armedNext.audioSessionId } catch (_: Exception) { currentAudioSessionId }
        if (newSessionId != 0 && newSessionId != currentAudioSessionId) {
            currentAudioSessionId = newSessionId
            setupAudioFx(currentAudioSessionId)
        } else {
            // Re-sync reverb aux send on newly promoted player without tearing down AudioFX
            try {
                presetReverbFx?.let { reverb ->
                    armedNext.attachAuxEffect(reverb.id)
                    armedNext.setAuxEffectSendLevel(if (_selectedReverb.value != ReverbEffect.OFF) 1.0f else 0.0f)
                }
            } catch (_: Exception) {}
        }

        if (_playbackSpeed.value != 1.0f || _isSlowedReverbEnabled.value) {
            applyPlaybackSpeedTo(armedNext)
        }

        _durationMs.value = try {
            val d = armedNext.duration.toLong()
            if (d > 0) d else armedTrack.durationMs
        } catch (_: Exception) { armedTrack.durationMs }
        _currentPositionMs.value = try {
            armedNext.currentPosition.toLong().coerceAtLeast(0L)
        } catch (_: Exception) { 0L }

        notifyPlaybackState()
        launchSilenceAnalysis(armedTrack)
        prepareNextPlayerForGapless()
        Log.d("AudioPlayerEngine", "0ms Gapless transition completed -> ${armedTrack.title} (session=$currentAudioSessionId)")
    }

    private fun promoteNextPlayerDirectly() {
        val armedNext = nextPlayer ?: return
        val armedTrack = nextPlayerTrack ?: return
        val armedIdx = nextPlayerQueueIndex

        val oldPrimary = primaryPlayer
        try {
            oldPrimary?.setNextMediaPlayer(null)
            oldPrimary?.stop()
            oldPrimary?.release()
        } catch (_: Exception) {}

        primaryPlayer = armedNext
        nextPlayer = null
        nextPlayerTrack = null
        nextPlayerQueueIndex = -1
        isNextPlayerPrepared = false
        plannedNextIndex = null

        currentQueueIndex = armedIdx
        _currentTrack.value = armedTrack
        requestAudioFocus()
        registerBecomingNoisyReceiver()
        acquireWakeLock()
        _isPlaying.value = true

        try {
            armedNext.setVolume(1.0f, 1.0f)
            armedNext.start()
            armedNext.setOnCompletionListener { onTrackCompleted() }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Direct gapless start failed: ${e.message}")
            playTrack(armedTrack)
            return
        }

        val newSessionId = try { armedNext.audioSessionId } catch (_: Exception) { currentAudioSessionId }
        if (newSessionId != 0 && newSessionId != currentAudioSessionId) {
            currentAudioSessionId = newSessionId
            setupAudioFx(currentAudioSessionId)
        } else {
            try {
                presetReverbFx?.let { reverb ->
                    armedNext.attachAuxEffect(reverb.id)
                    armedNext.setAuxEffectSendLevel(if (_selectedReverb.value != ReverbEffect.OFF) 1.0f else 0.0f)
                }
            } catch (_: Exception) {}
        }

        if (_playbackSpeed.value != 1.0f || _isSlowedReverbEnabled.value) {
            applyPlaybackSpeedTo(armedNext)
        }

        _durationMs.value = try {
            val d = armedNext.duration.toLong()
            if (d > 0) d else armedTrack.durationMs
        } catch (_: Exception) { armedTrack.durationMs }
        _currentPositionMs.value = 0L

        notifyPlaybackState()
        launchSilenceAnalysis(armedTrack)
        prepareNextPlayerForGapless()
    }

    private fun detachCurrentSourceForCrossfade() {
        cancelCrossfade(restoreVolume = false)
        releaseNextPlayer()
        silenceAnalysisJob?.cancel()

        primaryPlayer?.let { outMp ->
            try { outMp.setOnCompletionListener(null) } catch (_: Exception) {}
            try { outMp.setNextMediaPlayer(null) } catch (_: Exception) {}
            fadingOutPlayer = outMp
            primaryPlayer = null
        }

        if (isSynthPlaying || synthAudioTrack != null) {
            fadingOutSynthTrack = synthAudioTrack
            fadingOutSynthFlag = currentSynthActiveFlag
            synthAudioTrack = null
            currentSynthActiveFlag = null
            isSynthPlaying = false
        }

        isCrossfading = true
    }

    private fun runCrossfadeVolumeRamp(
        incomingMp: MediaPlayer?,
        incomingSynth: AudioTrack?,
        fadeDurationMs: Long,
        incomingTitle: String
    ) {
        crossfadeJob?.cancel()
        val durationSafeMs = fadeDurationMs.coerceAtLeast(300L)
        crossfadeJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            val startTime = System.currentTimeMillis()
            while (isCrossfading) {
                val elapsed = System.currentTimeMillis() - startTime
                val t = (elapsed.toFloat() / durationSafeMs.toFloat()).coerceIn(0f, 1f)
                // Equal-power Apple Music style crossfade curve (constant perceived loudness)
                val outVol = cos(t * PI / 2.0).toFloat().coerceIn(0f, 1f)
                val inVol = sin(t * PI / 2.0).toFloat().coerceIn(0f, 1f)
                try { fadingOutPlayer?.setVolume(outVol, outVol) } catch (_: Exception) {}
                try { fadingOutSynthTrack?.setVolume(outVol) } catch (_: Exception) {}
                try { incomingMp?.setVolume(inVol, inVol) } catch (_: Exception) {}
                try { incomingSynth?.setVolume(inVol) } catch (_: Exception) {}

                if (t >= 1f) break
                delay(25L)
            }

            releaseFadingOutSources()
            try { incomingMp?.setVolume(1.0f, 1.0f) } catch (_: Exception) {}
            try { incomingSynth?.setVolume(1.0f) } catch (_: Exception) {}
            isCrossfading = false
            prepareNextPlayerForGapless()
            Log.d("AudioPlayerEngine", "Smooth Equal-Power Crossfade finished -> $incomingTitle (${durationSafeMs}ms)")
        }
    }

    private fun releaseFadingOutSources() {
        try {
            fadingOutPlayer?.stop()
            fadingOutPlayer?.release()
        } catch (_: Exception) {}
        fadingOutPlayer = null

        fadingOutSynthFlag?.set(false)
        fadingOutSynthFlag = null
        try {
            fadingOutSynthTrack?.stop()
            fadingOutSynthTrack?.release()
        } catch (_: Exception) {}
        fadingOutSynthTrack = null
    }

    private fun startCrossfadeTransition(remainingMs: Long) {
        val outgoing = primaryPlayer ?: return
        val incoming = nextPlayer ?: return
        val incomingTrack = nextPlayerTrack ?: return
        val incomingIdx = nextPlayerQueueIndex

        cancelCrossfade(restoreVolume = false)
        isCrossfading = true

        // Detach outgoing from primaryPlayer immediately and promote incoming so UI & progress stay responsive
        fadingOutPlayer = outgoing
        try { outgoing.setOnCompletionListener(null) } catch (_: Exception) {}
        try { outgoing.setNextMediaPlayer(null) } catch (_: Exception) {}

        primaryPlayer = incoming
        nextPlayer = null
        nextPlayerTrack = null
        nextPlayerQueueIndex = -1
        isNextPlayerPrepared = false
        plannedNextIndex = null

        currentQueueIndex = incomingIdx
        _currentTrack.value = incomingTrack
        _isPlaying.value = true

        try {
            incoming.setVolume(0f, 0f)
            incoming.start()
            incoming.setOnCompletionListener { onTrackCompleted() }
            if (_playbackSpeed.value != 1.0f || _isSlowedReverbEnabled.value) {
                applyPlaybackSpeedTo(incoming)
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Crossfade start failed: ${e.message}")
            isCrossfading = false
            try { outgoing.release() } catch (_: Exception) {}
            fadingOutPlayer = null
            playTrack(incomingTrack)
            return
        }

        val newSessionId = try { incoming.audioSessionId } catch (_: Exception) { currentAudioSessionId }
        if (newSessionId != 0 && newSessionId != currentAudioSessionId) {
            currentAudioSessionId = newSessionId
            setupAudioFx(currentAudioSessionId)
        } else {
            try {
                presetReverbFx?.let { reverb ->
                    incoming.attachAuxEffect(reverb.id)
                    incoming.setAuxEffectSendLevel(if (_selectedReverb.value != ReverbEffect.OFF) 1.0f else 0.0f)
                }
            } catch (_: Exception) {}
        }

        _durationMs.value = try {
            val d = incoming.duration.toLong()
            if (d > 0) d else incomingTrack.durationMs
        } catch (_: Exception) { incomingTrack.durationMs }
        _currentPositionMs.value = 0L
        notifyPlaybackState()
        launchSilenceAnalysis(incomingTrack)

        val speed = _playbackSpeed.value.coerceIn(0.5f, 1.5f)
        val fadeDurationMs = (remainingMs / speed).toLong().coerceIn(300L, AUTO_CROSSFADE_MS)
        runCrossfadeVolumeRamp(
            incomingMp = incoming,
            incomingSynth = null,
            fadeDurationMs = fadeDurationMs,
            incomingTitle = incomingTrack.title
        )
    }

    private fun cancelCrossfade(restoreVolume: Boolean) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        if (isCrossfading) {
            isCrossfading = false
            releaseFadingOutSources()
            if (restoreVolume) {
                try { primaryPlayer?.setVolume(1.0f, 1.0f) } catch (_: Exception) {}
                try { synthAudioTrack?.setVolume(1.0f) } catch (_: Exception) {}
            }
        } else {
            releaseFadingOutSources()
        }
    }

    private fun launchSilenceAnalysis(track: Track) {
        silenceAnalysisJob?.cancel()
        silenceAnalysisJob = engineScope.launch {
            currentTrimBounds = AudioSilenceDetector.analyzeTrack(context, track.audioPath, track.durationMs)
        }
    }

    private fun releaseNextPlayer() {
        nextPlayerGeneration++
        isNextPlayerPrepared = false
        nextPlayerTrack = null
        nextPlayerQueueIndex = -1
        try { primaryPlayer?.setNextMediaPlayer(null) } catch (_: Exception) {}
        nextPlayer?.let { np ->
            try {
                np.setOnPreparedListener(null)
                np.setOnCompletionListener(null)
                np.setOnErrorListener(null)
                np.release()
            } catch (_: Exception) {}
        }
        nextPlayer = null
    }

    private fun invalidateAndPrepareNextPlayer() {
        plannedNextIndex = null
        releaseNextPlayer()
        if (_isGaplessEnabled.value && primaryPlayer != null) {
            prepareNextPlayerForGapless()
        }
    }

    private fun prepareNextPlayerForGapless() {
        if (!_isGaplessEnabled.value || primaryPlayer == null) return
        val queue = _playlistQueue.value
        val nextIdx = resolveNextQueueIndex() ?: run {
            releaseNextPlayer()
            return
        }
        val nextTrack = queue.getOrNull(nextIdx) ?: run {
            releaseNextPlayer()
            return
        }
        if (nextTrack.audioPath.startsWith("synth_demo_")) {
            releaseNextPlayer()
            return
        }

        releaseNextPlayer()
        val gen = ++nextPlayerGeneration

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                if (currentAudioSessionId != 0) {
                    try {
                        audioSessionId = currentAudioSessionId
                    } catch (e: Exception) {
                        Log.w("AudioPlayerEngine", "Could not share audioSessionId on nextPlayer: ${e.message}")
                    }
                }
            }

            try {
                presetReverbFx?.let { reverb ->
                    mp.attachAuxEffect(reverb.id)
                    mp.setAuxEffectSendLevel(if (_selectedReverb.value != ReverbEffect.OFF) 1.0f else 0.0f)
                }
            } catch (_: Exception) {}

            setDataSourceRobustly(mp, nextTrack.audioPath)
            nextPlayer = mp
            nextPlayerTrack = nextTrack
            nextPlayerQueueIndex = nextIdx
            isNextPlayerPrepared = false

            mp.setOnPreparedListener { preparedMp ->
                if (gen != nextPlayerGeneration || nextPlayer !== preparedMp || primaryPlayer == null) {
                    try { preparedMp.release() } catch (_: Exception) {}
                    return@setOnPreparedListener
                }
                isNextPlayerPrepared = true
                preparedMp.setOnCompletionListener { onTrackCompleted() }

                if (_isGaplessEnabled.value) {
                    try {
                        primaryPlayer?.setNextMediaPlayer(preparedMp)
                        Log.d("AudioPlayerEngine", "Gapless nextPlayer pre-buffered & armed: ${nextTrack.title} (session=${preparedMp.audioSessionId})")
                    } catch (e: Exception) {
                        Log.w("AudioPlayerEngine", "setNextMediaPlayer failed: ${e.message}")
                    }
                }
            }

            mp.setOnErrorListener { failedMp, what, extra ->
                Log.w("AudioPlayerEngine", "Async prepare error on nextPlayer (${nextTrack.title}): $what/$extra")
                if (nextPlayer === failedMp) {
                    nextPlayer = null
                    nextPlayerTrack = null
                    nextPlayerQueueIndex = -1
                    isNextPlayerPrepared = false
                }
                try { failedMp.release() } catch (_: Exception) {}
                true
            }

            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w("AudioPlayerEngine", "Could not pre-buffer next track ${nextTrack.title}: ${e.message}")
            releaseNextPlayer()
        }
    }

    fun seekTo(positionMs: Long) {
        cancelCrossfade(restoreVolume = true)
        _currentPositionMs.value = positionMs
        primaryPlayer?.seekTo(positionMs.toInt())
        if (_isGaplessEnabled.value && nextPlayer == null && primaryPlayer != null) {
            prepareNextPlayerForGapless()
        }
        savePlaybackState(positionMs)
        notifyPlaybackState()
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        invalidateAndPrepareNextPlayer()
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        invalidateAndPrepareNextPlayer()
    }

    fun toggleGaplessMode() {
        _isGaplessEnabled.value = !_isGaplessEnabled.value
        prefs.edit().putBoolean("gapless_enabled", _isGaplessEnabled.value).apply()
        if (_isGaplessEnabled.value) {
            _currentTrack.value?.let { launchSilenceAnalysis(it) }
            prepareNextPlayerForGapless()
        } else {
            cancelCrossfade(restoreVolume = true)
            releaseNextPlayer()
            currentTrimBounds = null
        }
    }

    // Equalizer Controls with Smooth Cross-Fade Transitions
    fun setEqEnabled(enabled: Boolean): Boolean {
        if (enabled && !_isExternalAudioConnected.value) {
            Log.w("AudioPlayerEngine", "Equalizer blocked: Normal device speakers in use. Bluetooth or wired aux required.")
            _eqEnabled.value = false
            userEqPreference = false
            return false
        }
        userEqPreference = enabled
        setEqEnabledInternal(enabled)
        return true
    }

    private fun setEqEnabledInternal(enabled: Boolean) {
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

    private fun disableEqualizerHardwareEffect() {
        eqTransitionJob?.cancel()
        try {
            equalizerFx?.enabled = false
            bassBoostFx?.enabled = false
        } catch (_: Exception) {}
    }

    private fun loadCustomPresets(): List<CustomEqPreset> {
        val jsonStr = prefs.getString("custom_eq_presets_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CustomEqPreset>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Custom Preset")
                val gainsArr = obj.optJSONArray("gains")
                val gains = FloatArray(5)
                if (gainsArr != null) {
                    for (j in 0 until minOf(5, gainsArr.length())) {
                        gains[j] = gainsArr.optDouble(j, 0.0).toFloat()
                    }
                }
                val bassBoost = obj.optDouble("bassBoost", 0.5).toFloat()
                list.add(CustomEqPreset(id, name, gains, bassBoost))
            }
            list
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Failed to load custom presets", e)
            emptyList()
        }
    }

    private fun persistCustomPresets(list: List<CustomEqPreset>) {
        try {
            val array = JSONArray()
            for (preset in list) {
                val obj = JSONObject()
                obj.put("id", preset.id)
                obj.put("name", preset.name)
                val gainsArr = JSONArray()
                for (gain in preset.gains) {
                    gainsArr.put(gain.toDouble())
                }
                obj.put("gains", gainsArr)
                obj.put("bassBoost", preset.bassBoost.toDouble())
                array.put(obj)
            }
            prefs.edit().putString("custom_eq_presets_json", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Failed to persist custom presets", e)
        }
    }

    fun saveCustomPreset(name: String, gains: FloatArray, bassBoost: Float): CustomEqPreset {
        val cleanName = name.trim().ifEmpty { "My Preset" }
        val newPreset = CustomEqPreset(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            gains = gains.copyOf(),
            bassBoost = bassBoost
        )
        val updated = _customPresets.value.toMutableList().apply {
            val existingIdx = indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
            if (existingIdx != -1) {
                set(existingIdx, newPreset)
            } else {
                add(newPreset)
            }
        }
        _customPresets.value = updated
        persistCustomPresets(updated)
        selectCustomPreset(newPreset)
        return newPreset
    }

    fun deleteCustomPreset(presetId: String) {
        val updated = _customPresets.value.filter { it.id != presetId }
        _customPresets.value = updated
        persistCustomPresets(updated)
        if (_selectedCustomPresetId.value == presetId) {
            _selectedCustomPresetId.value = null
            selectPreset(EqPreset.FLAT)
        }
    }

    fun selectCustomPreset(preset: CustomEqPreset, enableEqIfOff: Boolean = true) {
        _selectedCustomPresetId.value = preset.id
        _selectedPreset.value = EqPreset.CUSTOM
        if (enableEqIfOff && !_eqEnabled.value && _isExternalAudioConnected.value) {
            setEqEnabled(true)
        }
        _bandGains.value = preset.gains.copyOf()
        _bassBoostLevel.value = preset.bassBoost
        if (_isExternalAudioConnected.value) {
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

    fun setBassBoost(level: Float) { // 0.0f to 1.0f
        _bassBoostLevel.value = level
        if (!_eqEnabled.value && level > 0f && _isExternalAudioConnected.value) {
            setEqEnabled(true)
        }
        if (_eqEnabled.value && _isExternalAudioConnected.value) {
            try {
                if (bassBoostFx?.strengthSupported == true) {
                    bassBoostFx?.setStrength((level * 1000).toInt().toShort())
                }
            } catch (_: Exception) {}
        }
        _selectedCustomPresetId.value = null
        if (_selectedPreset.value != EqPreset.CUSTOM) {
            _selectedPreset.value = EqPreset.CUSTOM
        }
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..4) return
        val newGains = _bandGains.value.copyOf()
        newGains[bandIndex] = gainDb
        _bandGains.value = newGains

        if (!_eqEnabled.value && _isExternalAudioConnected.value) {
            setEqEnabled(true)
        }
        if (_isExternalAudioConnected.value) {
            applyEqualizerGains()
        }
        _selectedCustomPresetId.value = null
        if (_selectedPreset.value != EqPreset.CUSTOM) {
            _selectedPreset.value = EqPreset.CUSTOM
        }
    }

    fun selectPreset(preset: EqPreset, enableEqIfOff: Boolean = true) {
        _selectedCustomPresetId.value = null
        _selectedPreset.value = preset
        if (enableEqIfOff && !_eqEnabled.value && _isExternalAudioConnected.value) {
            setEqEnabled(true)
        }
        if (preset != EqPreset.CUSTOM) {
            _bandGains.value = preset.gains.copyOf()
            _bassBoostLevel.value = preset.bassBoost
            if (_isExternalAudioConnected.value) {
                applyEqualizerGains()
                if (_eqEnabled.value) {
                    try {
                        if (bassBoostFx?.strengthSupported == true) {
                            bassBoostFx?.setStrength((preset.bassBoost * 1000).toInt().toShort())
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        notificationManager.updateNotification(_currentTrack.value, _isPlaying.value, _currentPositionMs.value)
    }

    fun resetEqualizer() {
        _selectedCustomPresetId.value = null
        selectPreset(EqPreset.FLAT)
    }

    fun cycleEqPreset() {
        if (!_isExternalAudioConnected.value) {
            Log.d("AudioPlayerEngine", "Preset cycling ignored on device speaker")
            return
        }
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
        if (!_isExternalAudioConnected.value) {
            Log.d("AudioPlayerEngine", "Bass boost cycling ignored on device speaker")
            return
        }
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
        if (!_eqEnabled.value || !_isExternalAudioConnected.value) return
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

        cancelCrossfade(restoreVolume = false)
        releaseNextPlayer()
        silenceAnalysisJob?.cancel()
        isSynthPlaying = false
        currentSynthActiveFlag?.set(false)
        currentSynthActiveFlag = null
        synthAudioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        synthAudioTrack = null

        primaryPlayer?.let {
            try {
                it.setNextMediaPlayer(null)
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        primaryPlayer = null
        currentAudioSessionId = 0

        _isPlaying.value = false
        releaseWakeLock()
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
        primaryPlayer?.let { applyPlaybackSpeedTo(it) }
    }

    private fun applyPlaybackSpeedTo(player: MediaPlayer) {
        try {
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
        private const val AUTO_CROSSFADE_MS = 8000L
        private const val MANUAL_SKIP_CROSSFADE_MS = 5000L

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
