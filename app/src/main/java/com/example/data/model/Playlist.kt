package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val coverGradientIndex: Int = 0,
    val isSystemPlaylist: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)
