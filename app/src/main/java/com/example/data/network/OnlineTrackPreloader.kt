package com.example.data.network

import android.content.Context
import android.util.Log
import coil.Coil
import com.example.BuildConfig
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Downloads the next online iTunes preview (~900 KB) into app cacheDir only after
 * the current song has started smoothly, enabling instant 0ms switches & stutter-free crossfades
 * without competing with the active stream on slow 4G connections.
 */
object OnlineTrackPreloader {
    private const val TAG = "OnlineTrackPreloader"
    private const val PRELOAD_DIR_NAME = "preloaded_tracks"
    private const val IMAGE_CACHE_DIR_NAME = "image_cache"
    private const val MAX_PRELOADED_FILES = 3
    private const val MAX_PRELOAD_BYTES = 15L * 1024L * 1024L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeCall = AtomicReference<Call?>(null)
    private val activeUrl = AtomicReference<String?>(null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun preloadDir(context: Context): File {
        return File(context.cacheDir, PRELOAD_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun cacheKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun getCachedFile(context: Context, url: String): File? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val file = File(preloadDir(context), "${cacheKey(url)}.m4a")
        return if (file.exists() && file.length() > 2048L) {
            try {
                file.setLastModified(System.currentTimeMillis())
            } catch (_: Exception) {}
            file
        } else {
            null
        }
    }

    fun cancelCurrentPreload() {
        activeUrl.set(null)
        activeCall.getAndSet(null)?.let { call ->
            try {
                call.cancel()
            } catch (_: Exception) {}
        }
    }

    fun preloadTrack(
        context: Context,
        track: Track,
        onReady: (File) -> Unit = {}
    ) {
        if (!track.isOnlineStream || track.isLiveRadio) return
        val url = track.audioPath

        getCachedFile(context, url)?.let { existing ->
            onReady(existing)
            return
        }

        if (activeUrl.get() == url) return
        cancelCurrentPreload()
        activeUrl.set(url)

        scope.launch {
            val dir = preloadDir(context)
            val key = cacheKey(url)
            val tmpFile = File(dir, "$key.tmp")
            val finalFile = File(dir, "$key.m4a")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HARMANX/${BuildConfig.VERSION_NAME} (Android)")
                .build()

            val call = client.newCall(request)
            activeCall.set(call)

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        tmpFile.delete()
                        return@launch
                    }
                    val body = response.body ?: return@launch
                    body.byteStream().use { input ->
                        tmpFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                    if (!call.isCanceled() && tmpFile.exists() && tmpFile.length() > 2048L) {
                        if (finalFile.exists()) finalFile.delete()
                        if (tmpFile.renameTo(finalFile)) {
                            trimCacheDirectory(dir)
                            Log.d(TAG, "Preloaded next track (${finalFile.length() / 1024} KB): ${track.title}")
                            onReady(finalFile)
                        }
                    } else {
                        tmpFile.delete()
                    }
                }
            } catch (e: Exception) {
                tmpFile.delete()
                Log.d(TAG, "Preload cancelled/skipped for ${track.title}: ${e.message}")
            } finally {
                if (activeCall.get() === call) {
                    activeCall.set(null)
                }
                if (activeUrl.get() == url) {
                    activeUrl.set(null)
                }
            }
        }
    }

    private fun trimCacheDirectory(dir: File) {
        try {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".m4a") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            var totalBytes = 0L
            files.forEachIndexed { index, file ->
                totalBytes += file.length()
                if (index >= MAX_PRELOADED_FILES || totalBytes > MAX_PRELOAD_BYTES) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun getTotalCacheSizeBytes(context: Context): Long {
        fun dirSize(file: File): Long {
            if (!file.exists()) return 0L
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
        }
        return dirSize(File(context.cacheDir, PRELOAD_DIR_NAME)) +
            dirSize(File(context.cacheDir, IMAGE_CACHE_DIR_NAME))
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearAllCaches(context: Context) {
        cancelCurrentPreload()
        try {
            File(context.cacheDir, PRELOAD_DIR_NAME).deleteRecursively()
        } catch (_: Exception) {}
        try {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        } catch (_: Exception) {}
        try {
            File(context.cacheDir, IMAGE_CACHE_DIR_NAME).deleteRecursively()
        } catch (_: Exception) {}
    }
}
