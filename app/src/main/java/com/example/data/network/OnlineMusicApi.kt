package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ITunesTrackDto(
    val trackId: Long = 0L,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val previewUrl: String? = null,
    val artworkUrl100: String? = null,
    val artworkUrl60: String? = null,
    val trackTimeMillis: Long? = null,
    val primaryGenreName: String? = null
)

@JsonClass(generateAdapter = true)
data class RadioStationDto(
    val stationuuid: String = "",
    val name: String = "",
    val url: String = "",
    @Json(name = "url_resolved") val urlResolved: String? = null,
    val favicon: String? = null,
    val tags: String? = null,
    val country: String? = null,
    val countrycode: String? = null,
    val state: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val hls: Int? = null,
    val votes: Int? = null,
    val clickcount: Int? = null,
    val lastcheckok: Int? = null
)

interface ITunesSearchApi {
    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("country") country: String = "IN",
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 35
    ): ITunesSearchResponse
}

interface RadioBrowserApi {
    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String? = null,
        @Query("tag") tag: String? = null,
        @Query("countrycode") countryCode: String? = null,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("limit") limit: Int = 30
    ): List<RadioStationDto>
}
