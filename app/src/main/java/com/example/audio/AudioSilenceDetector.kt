package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Intelligent silence and voice-note detector.
 * Scans the audio file to find:
 * 1. Intro Voice Note Start: where silence ends and the first vocal/audio note begins.
 * 2. Outro Voice Note End: where the last vocal/audio note ends and trailing dead air begins.
 * This allows cutting trailing silence from the ending song and cutting leading silence from the next song.
 */
object AudioSilenceDetector {

    private const val TAG = "AudioSilenceDetector"
    private const val AMPLITUDE_THRESHOLD = 500 // ~ -38 dB: above microphone room noise, active voice/sound
    private const val TIMEOUT_US = 5000L

    data class TrimBounds(
        val introStartMs: Long,
        val outroEndMs: Long
    )

    private val cache = ConcurrentHashMap<String, TrimBounds>()

    fun getCached(audioPath: String): TrimBounds? = cache[audioPath]

    suspend fun analyzeTrack(
        context: Context,
        audioPath: String,
        durationMs: Long
    ): TrimBounds = withContext(Dispatchers.IO) {
        if (durationMs <= 0) return@withContext TrimBounds(0L, durationMs)

        cache[audioPath]?.let { return@withContext it }

        // For synthetic tracks (FLAC demo tone generator)
        if (!audioPath.startsWith("content://") && !audioPath.startsWith("file://") && !audioPath.startsWith("android.resource://")) {
            val outro = (durationMs - 2000L).coerceAtLeast(1000L)
            val bounds = TrimBounds(introStartMs = 0L, outroEndMs = outro)
            cache[audioPath] = bounds
            return@withContext bounds
        }

        try {
            val uri = Uri.parse(audioPath)
            val extractor = MediaExtractor()
            if (audioPath.startsWith("content://") || audioPath.startsWith("android.resource://")) {
                extractor.setDataSource(context, uri, null)
            } else if (audioPath.startsWith("file://")) {
                extractor.setDataSource(uri.path ?: audioPath)
            } else {
                extractor.setDataSource(audioPath)
            }

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                extractor.release()
                val fallback = TrimBounds(0L, (durationMs - 1500L).coerceAtLeast(1000L))
                cache[audioPath] = fallback
                return@withContext fallback
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // 1. Detect Intro Voice Note Start (first 5 seconds max)
            val introStartMs = detectIntroVoiceNote(extractor, codec)

            // 2. Detect Outro Voice Note End (last 12 seconds)
            val outroEndMs = detectOutroVoiceNote(extractor, codec, durationMs, introStartMs)

            try {
                codec.stop()
                codec.release()
            } catch (_: Exception) {}

            try {
                extractor.release()
            } catch (_: Exception) {}

            val finalBounds = TrimBounds(introStartMs, outroEndMs)
            Log.d(TAG, "Analysis complete for $audioPath: introStart=${introStartMs}ms, outroEnd=${outroEndMs}ms, totalDur=${durationMs}ms")
            cache[audioPath] = finalBounds
            finalBounds
        } catch (e: Exception) {
            Log.d(TAG, "Silence detection fallback for $audioPath: ${e.message}")
            val fallback = TrimBounds(0L, (durationMs - 1500L).coerceAtLeast(1000L))
            cache[audioPath] = fallback
            fallback
        }
    }

    private fun detectIntroVoiceNote(extractor: MediaExtractor, codec: MediaCodec): Long {
        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var firstAudibleTimeMs: Long? = null

        val startTimeUs = System.currentTimeMillis() * 1000

        while (firstAudibleTimeMs == null && (System.currentTimeMillis() * 1000 - startTimeUs) < 150_000) {
            if (!sawInputEOS) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val timeMs = bufferInfo.presentationTimeUs / 1000
                    if (timeMs > 5000) {
                        // Beyond 5 seconds intro scan limit
                        firstAudibleTimeMs = 0L
                        codec.releaseOutputBuffer(outputIndex, false)
                        break
                    }
                    if (containsAudioSignal(outputBuffer, bufferInfo.offset, bufferInfo.size)) {
                        firstAudibleTimeMs = timeMs.coerceAtLeast(0L)
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            } else if (sawInputEOS && outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            }
        }

        return firstAudibleTimeMs ?: 0L
    }

    private fun detectOutroVoiceNote(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationMs: Long,
        introStartMs: Long
    ): Long {
        val scanStartMs = (durationMs - 12_000L).coerceAtLeast(introStartMs + 2000L)
        extractor.seekTo(scanStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var lastAudibleTimeMs = durationMs

        val startTimeUs = System.currentTimeMillis() * 1000

        while ((System.currentTimeMillis() * 1000 - startTimeUs) < 200_000) {
            if (!sawInputEOS) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val timeMs = bufferInfo.presentationTimeUs / 1000
                    if (containsAudioSignal(outputBuffer, bufferInfo.offset, bufferInfo.size)) {
                        lastAudibleTimeMs = timeMs
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            } else if (sawInputEOS && outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            }
        }

        // Add 250ms decay tail so last note isn't unnaturally chopped mid-vowel
        val outroCutPoint = (lastAudibleTimeMs + 250L).coerceAtMost(durationMs)
        return if (outroCutPoint < durationMs - 400L) outroCutPoint else durationMs
    }

    private fun containsAudioSignal(buffer: ByteBuffer, offset: Int, size: Int): Boolean {
        buffer.position(offset)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = buffer.asShortBuffer()
        val numShorts = size / 2
        var maxAmp = 0
        var i = 0
        while (i < numShorts) {
            val sample = abs(shortBuffer.get(i).toInt())
            if (sample > maxAmp) maxAmp = sample
            if (maxAmp > AMPLITUDE_THRESHOLD) return true
            i += 4 // Sample check stride for speed
        }
        return maxAmp > AMPLITUDE_THRESHOLD
    }
}
