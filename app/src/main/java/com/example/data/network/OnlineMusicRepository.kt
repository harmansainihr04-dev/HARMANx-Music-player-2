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
import java.util.concurrent.ConcurrentHashMap
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

    private val iTunesOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "HARMANX/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val radioOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "HARMANX/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val iTunesApi: ITunesSearchApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(iTunesOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ITunesSearchApi::class.java)

    private val radioMirrors = listOf(
        "https://de1.api.radio-browser.info/",
        "https://nl1.api.radio-browser.info/",
        "https://at1.api.radio-browser.info/"
    )

    @Volatile
    private var preferredRadioMirror: String? = null

    private val songCache = ConcurrentHashMap<String, List<Track>>()
    private val radioCache = ConcurrentHashMap<String, List<Track>>()

    private fun createRadioApi(baseUrl: String): RadioBrowserApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(radioOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RadioBrowserApi::class.java)
    }

    suspend fun searchITunesSongs(
        query: String,
        country: String = "IN",
        forceRefresh: Boolean = false
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().ifEmpty { "punjabi hits" }
        val cacheKey = "${country}_${cleanQuery.lowercase()}"
        if (!forceRefresh) {
            songCache[cacheKey]?.let { cached ->
                if (cached.isNotEmpty()) return@withContext Result.success(cached)
            }
        }

        try {
            val response = iTunesApi.searchSongs(
                term = cleanQuery,
                country = country,
                limit = 25
            )
            val tracks = response.results.mapIndexedNotNull { idx, dto ->
                val preview = dto.previewUrl?.takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                } ?: return@mapIndexedNotNull null
                val title = dto.trackName?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val artist = dto.artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                // Low-data fast-loading 100x100 artwork (~3-6 KB vs 40 KB)
                val lightArt = (dto.artworkUrl100 ?: dto.artworkUrl60)
                    ?.replace("60x60bb", "100x100bb")
                OnlineArtworkCache.put(preview, lightArt)

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
            if (tracks.isNotEmpty()) {
                songCache[cacheKey] = tracks
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRadioStations(
        category: RadioCategory,
        customQuery: String = "",
        forceRefresh: Boolean = false
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        val cacheKey = "${category.name}_${customQuery.trim().lowercase()}"
        if (!forceRefresh) {
            radioCache[cacheKey]?.let { cached ->
                if (cached.isNotEmpty()) return@withContext Result.success(cached)
            }
        }

        val orderedMirrors = preferredRadioMirror?.let { fav ->
            listOf(fav) + radioMirrors.filter { it != fav }
        } ?: radioMirrors

        var lastError: Exception? = null
        for (mirror in orderedMirrors) {
            try {
                val api = createRadioApi(mirror)
                val rawStations = when (category) {
                    RadioCategory.PUNJABI -> coroutineScope {
                        val byTag = async { api.searchStations(tag = "punjabi", limit = 20) }
                        val byName = async { api.searchStations(name = "punjabi", limit = 20) }
                        byTag.await() + byName.await()
                    }
                    RadioCategory.HARYANVI -> coroutineScope {
                        val byName = async { api.searchStations(name = "haryanvi", limit = 20) }
                        val byTag = async { api.searchStations(tag = "haryanvi", limit = 15) }
                        val byHaryana = async { api.searchStations(name = "haryana", limit = 15) }
                        val combined = byName.await() + byTag.await() + byHaryana.await()
                        if (combined.isNotEmpty()) {
                            combined
                        } else {
                            api.searchStations(tag = "hindi", countryCode = "IN", limit = 20)
                        }
                    }
                    RadioCategory.HINDI_BOLLYWOOD -> coroutineScope {
                        val byTag = async { api.searchStations(tag = "bollywood", limit = 20) }
                        val byHindi = async { api.searchStations(tag = "hindi", countryCode = "IN", limit = 20) }
                        byTag.await() + byHindi.await()
                    }
                    RadioCategory.SEARCH -> coroutineScope {
                        val q = customQuery.trim().ifEmpty { "punjabi" }
                        val byName = async { api.searchStations(name = q, limit = 20) }
                        val byTag = async { api.searchStations(tag = q.lowercase(), limit = 20) }
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
                            val br = it.bitrate ?: 0
                            br in 48..128
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
                            durationMs = 0L,
                            audioPath = streamUrl,
                            isFlac = false,
                            sampleRate = "LIVE RADIO • $codecStr",
                            bitrate = bitrateStr,
                            fileSizeBytes = 0L,
                            isFavorite = false,
                            artworkColorIndex = abs(safeId.toInt()) % 5
                        )
                    }

                preferredRadioMirror = mirror
                if (tracks.isNotEmpty()) {
                    radioCache[cacheKey] = tracks
                }
                return@withContext Result.success(tracks)
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IllegalStateException("Unable to load radio stations"))
    }
}
