package com.example.data.network

import com.example.data.model.Track
import java.util.concurrent.ConcurrentHashMap

object OnlineArtworkCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun put(streamUrl: String, artworkUrl: String?) {
        if (!artworkUrl.isNullOrBlank() && (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://"))) {
            cache[streamUrl] = artworkUrl
        }
    }

    fun get(streamUrl: String): String? = cache[streamUrl]
}

val Track.isOnlineStream: Boolean
    get() = audioPath.startsWith("http://") || audioPath.startsWith("https://")

val Track.isLiveRadio: Boolean
    get() = isOnlineStream && durationMs <= 0L
