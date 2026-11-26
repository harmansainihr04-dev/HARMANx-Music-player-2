package com.example.data.network

import com.example.BuildConfig
import com.example.data.model.Track
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class RadioCategory(val label: String) {
    PUNJABI("Punjabi Radio"),
    HARYANVI("Haryanvi Radio"),
    HINDI_BOLLYWOOD("Hindi / Desi FM"),
    SEARCH("Search Radio")
}

class OnlineMusicRepository {

    private val moshi = Moshi.Builder().build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "HARMANX/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val iTunesApi: ITunesSearchApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ITunesSearchApi::class.java)

    private val radioMirrors = listOf(
        "https://de1.api.radio-browser.info/",
        "https://nl1.api.radio-browser.info/",
        "https://at1.api.radio-browser.info/"
    )

    private fun createRadioApi(baseUrl: String): RadioBrowserApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RadioBrowserApi::class.java)
    }

    suspend fun searchITunesSongs(
        query: String,
        country: String = "IN"
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim().ifEmpty { "punjabi hits" }
            val response = iTunesApi.searchSongs(
                term = cleanQuery,
                country = country,
                limit = 35
            )
            val tracks = response.results.mapIndexedNotNull { idx, dto ->
                val preview = dto.previewUrl?.takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                } ?: return@mapIndexedNotNull null
                val title = dto.trackName?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val artist = dto.artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                val highResArt = (dto.artworkUrl100 ?: dto.artworkUrl60)
                    ?.replace("100x100bb", "600x600bb")
                    ?.replace("60x60bb", "600x600bb")
                OnlineArtworkCache.put(preview, highResArt)

                val safeId = if (dto.trackId > 0L) -dto.trackId else -(100_000L + idx)
                Track(
                    id = safeId,
                    title = title,
                    artist = artist,
                    album = dto.collectionName?.ifBlank { "iTunes 30s Preview" } ?: "iTunes 30s Preview",
                    durationMs = 30_000L,
                    audioPath = preview,
                    isFlac = false,
                    sampleRate = "iTunes 30s • ${dto.primaryGenreName ?: "Music"}",
                    bitrate = "256 kbps AAC",
                    fileSizeBytes = 0L,
                    isFavorite = false,
                    artworkColorIndex = abs(safeId.toInt()) % 5
                )
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRadioStations(
        category: RadioCategory,
        customQuery: String = ""
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (mirror in radioMirrors) {
            try {
                val api = createRadioApi(mirror)
                val rawStations = when (category) {
                    RadioCategory.PUNJABI -> coroutineScope {
                        val byTag = async { api.searchStations(tag = "punjabi", limit = 30) }
                        val byName = async { api.searchStations(name = "punjabi", limit = 30) }
                        val byBhangra = async { api.searchStations(tag = "bhangra", limit = 15) }
                        byTag.await() + byName.await() + byBhangra.await()
                    }
                    RadioCategory.HARYANVI -> coroutineScope {
                        val byName = async { api.searchStations(name = "haryanvi", limit = 25) }
                        val byTag = async { api.searchStations(tag = "haryanvi", limit = 25) }
                        val byHaryana = async { api.searchStations(name = "haryana", limit = 20) }
                        val byDesi = async { api.searchStations(name = "desi", countryCode = "IN", limit = 20) }
                        val combined = byName.await() + byTag.await() + byHaryana.await() + byDesi.await()
                        if (combined.isNotEmpty()) {
                            combined
                        } else {
                            api.searchStations(tag = "hindi", countryCode = "IN", limit = 25)
                        }
                    }
                    RadioCategory.HINDI_BOLLYWOOD -> coroutineScope {
                        val byTag = async { api.searchStations(tag = "bollywood", limit = 25) }
                        val byHindi = async { api.searchStations(tag = "hindi", countryCode = "IN", limit = 25) }
                        byTag.await() + byHindi.await()
                    }
                    RadioCategory.SEARCH -> coroutineScope {
                        val q = customQuery.trim().ifEmpty { "punjabi" }
                        val byName = async { api.searchStations(name = q, limit = 25) }
                        val byTag = async { api.searchStations(tag = q.lowercase(), limit = 25) }
                        byName.await() + byTag.await()
                    }
                }

                val tracks = rawStations
                    .filter { dto ->
                        val streamUrl = dto.urlResolved?.takeIf { it.isNotBlank() } ?: dto.url
                        val isHttp = streamUrl.startsWith("http://") || streamUrl.startsWith("https://")
                        val isDirectMedia = (dto.hls ?: 0) == 0 && !streamUrl.endsWith(".m3u8", ignoreCase = true)
                        val isHealthy = (dto.lastcheckok ?: 1) == 1
                        dto.name.isNotBlank() && isHttp && isDirectMedia && isHealthy
                    }
                    .distinctBy { (it.urlResolved ?: it.url).lowercase() }
                    .sortedWith(
                        compareByDescending<RadioStationDto> {
                            (it.urlResolved ?: it.url).startsWith("https://")
                        }.thenByDescending {
                            (it.votes ?: 0) + (it.clickcount ?: 0)
                        }
                    )
                    .mapIndexed { idx, dto ->
                        val streamUrl = dto.urlResolved?.takeIf { it.isNotBlank() } ?: dto.url
                        OnlineArtworkCache.put(streamUrl, dto.favicon)

                        val stableHash = abs((dto.stationuuid.ifEmpty { streamUrl }).hashCode().toLong())
                        val safeId = -(10_000_000_000L + stableHash + idx)
                        val region = listOfNotNull(
                            dto.state?.trim()?.takeIf { it.isNotBlank() },
                            dto.country?.trim()?.takeIf { it.isNotBlank() },
                            dto.language?.trim()?.takeIf { it.isNotBlank() }
                        ).distinct().joinToString(" • ").ifEmpty { "India • Live Radio" }

                        val tagsLabel = dto.tags
                            ?.split(",")
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            ?.take(2)
                            ?.joinToString(" • ")
                            .orEmpty()
                            .ifEmpty { "Live FM Broadcast" }

                        val codecStr = dto.codec?.trim()?.uppercase()?.ifBlank { "STREAM" } ?: "STREAM"
                        val bitrateStr = if ((dto.bitrate ?: 0) > 0) "${dto.bitrate} kbps $codecStr" else "Live $codecStr"

                        Track(
                            id = safeId,
                            title = dto.name.trim(),
                            artist = region,
                            album = tagsLabel,
                            durationMs = 0L, // Live radio stream (continuous)
                            audioPath = streamUrl,
                            isFlac = false,
                            sampleRate = "LIVE RADIO • $codecStr",
                            bitrate = bitrateStr,
                            fileSizeBytes = 0L,
                            isFavorite = false,
                            artworkColorIndex = abs(safeId.toInt()) % 5
                        )
                    }

                return@withContext Result.success(tracks)
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IllegalStateException("Unable to load radio stations"))
    }
}
