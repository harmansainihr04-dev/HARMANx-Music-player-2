package com.example.data.model

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val orderPosition: Int = 0
)
