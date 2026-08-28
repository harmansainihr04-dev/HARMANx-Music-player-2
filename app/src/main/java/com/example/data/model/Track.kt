package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [androidx.room.Index(value = ["audioPath"], unique = true)]
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val audioPath: String, // File path or raw resource / uri string
    val isFlac: Boolean = true,
    val sampleRate: String = "24-bit / 96.0 kHz",
    val bitrate: String = "2830 kbps",
    val fileSizeBytes: Long = 32_500_000,
    val isFavorite: Boolean = false,
    val artworkColorIndex: Int = 0, // Gradient theme index fallback
    val albumId: Long = -1L, // MediaStore Album ID for actual album cover thumbnail
    val dateAddedMs: Long = System.currentTimeMillis()
)
