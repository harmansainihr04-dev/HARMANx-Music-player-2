package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

/**
 * Universal Hardware/Software Audio Decoder using Android's MediaExtractor + MediaCodec + AudioTrack.
 * Handles Lossless Hi-Res FLAC (24-bit/96kHz, 192kHz), Raw WAV (PCM), M4A/AAC, OGG Vorbis & Opus
 * that native MediaPlayer fails to decode or prepare.
 */
class MediaCodecAudioPlayer(private val context: Context) {

    private var audioTrack: AudioTrack? = null
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null

    @Volatile
    private var isPlaying = false

    @Volatile
    private var isPaused = false

    @Volatile
    private var isReleased = false

    @Volatile
    private var currentPositionMs = 0L

    private var durationMs = 0L
    private var decodeThread: Thread? = null

    var onPositionUpdate: ((Long) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null

    companion object {
        private const val TAG = "MediaCodecAudioPlayer"
        private const val TIMEOUT_US = 10000L
    }

    fun getAudioSessionId(): Int {
        return audioTrack?.audioSessionId ?: 0
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying && !isPaused

    fun getCurrentPositionMs(): Long = currentPositionMs

    fun start(audioPath: String, startMs: Long = 0L): Boolean {
        stop()
        isReleased = false
        isPaused = false
        currentPositionMs = startMs

        try {
            val newExtractor = MediaExtractor()
            val uri = Uri.parse(audioPath)
            if (audioPath.startsWith("content://")) {
                var opened = false
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        newExtractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                        opened = true
                    }
                } catch (_: Exception) {}
                if (!opened) {
                    newExtractor.setDataSource(context, uri, null)
                }
            } else if (audioPath.startsWith("file://")) {
                newExtractor.setDataSource(uri.path ?: audioPath.removePrefix("file://"))
            } else if (audioPath.startsWith("/")) {
                newExtractor.setDataSource(audioPath)
            } else {
                newExtractor.setDataSource(context, uri, null)
            }

            var audioIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until newExtractor.trackCount) {
                val format = newExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioIndex = i
                    trackFormat = format
                    break
                }
            }

            if (audioIndex == -1 || trackFormat == null) {
                Log.e(TAG, "No audio track found in $audioPath")
                newExtractor.release()
                return false
            }

            newExtractor.selectTrack(audioIndex)
            extractor = newExtractor

            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            val channelCount = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                durationMs = trackFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
            }

            val channelConfig = if (channelCount == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }

            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
            val bufferSize = (minBufSize * 4).coerceAtLeast(8192)

            val newAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = newAudioTrack
            if (newAudioTrack.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed")
                stop()
                return false
            }

            val newCodec = MediaCodec.createDecoderByType(mime)
            newCodec.configure(trackFormat, null, null, 0)
            newCodec.start()
            codec = newCodec

            if (startMs > 0) {
                newExtractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            newAudioTrack.play()
            isPlaying = true
            isPaused = false

            decodeThread = thread(start = true, name = "MediaCodecAudioDecoder") {
                decodeLoop(newExtractor, newCodec, newAudioTrack)
            }

            Log.d(TAG, "Successfully started MediaCodec player for mime=$mime, rate=$sampleRate, channels=$channelCount")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MediaCodec player for $audioPath", e)
            stop()
            return false
        }
    }

    private fun decodeLoop(ext: MediaExtractor, dec: MediaCodec, track: AudioTrack) {
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (isPlaying && !isReleased && !sawOutputEOS) {
            if (isPaused) {
                try {
                    Thread.sleep(30)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            if (!sawInputEOS) {
                val inputIndex = try {
                    dec.dequeueInputBuffer(TIMEOUT_US)
                } catch (_: Exception) { -1 }

                if (inputIndex >= 0) {
                    val inputBuffer = dec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = ext.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            try {
                                dec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } catch (_: Exception) {}
                            sawInputEOS = true
                        } else {
                            val sampleTimeUs = ext.sampleTime
                            try {
                                dec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                                ext.advance()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            val outputIndex = try {
                dec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            } catch (_: Exception) { -1 }

            if (outputIndex >= 0) {
                val outputBuffer = dec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val pcmBytes = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(pcmBytes)

                    var written = 0
                    while (written < pcmBytes.size && isPlaying && !isReleased) {
                        if (isPaused) {
                            try { Thread.sleep(20) } catch (_: Exception) { break }
                            continue
                        }
                        val result = track.write(pcmBytes, written, pcmBytes.size - written)
                        if (result > 0) {
                            written += result
                        } else {
                            break
                        }
                    }

                    val timeMs = bufferInfo.presentationTimeUs / 1000L
                    if (timeMs >= 0) {
                        currentPositionMs = timeMs
                        onPositionUpdate?.invoke(timeMs)
                    }
                }

                try {
                    dec.releaseOutputBuffer(outputIndex, false)
                } catch (_: Exception) {}

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                    break
                }
            }
        }

        if (sawOutputEOS && isPlaying) {
            onCompletion?.invoke()
        }
    }

    fun pause() {
        isPaused = true
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        isPaused = false
        try {
            audioTrack?.play()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs
        try {
            extractor?.seekTo(positionMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Seek error in MediaCodecPlayer: ${e.message}")
        }
    }

    fun stop() {
        isPlaying = false
        isPaused = false
        try {
            decodeThread?.interrupt()
            decodeThread = null
        } catch (_: Exception) {}

        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        codec = null

        try {
            extractor?.release()
        } catch (_: Exception) {}
        extractor = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun release() {
        isReleased = true
        stop()
    }
}
