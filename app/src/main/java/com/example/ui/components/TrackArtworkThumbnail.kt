package com.example.ui.components

import android.content.ContentUris
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Track
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan

@Composable
fun TrackArtworkThumbnail(
    track: Track,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val context = LocalContext.current
    var isImageLoadFailed by remember(track.id, track.albumId, track.audioPath) { mutableStateOf(false) }

    // Build actual artwork URI from MediaStore or embedded track
    val artworkUri: Uri? = remember(track.id, track.albumId, track.audioPath) {
        if (track.albumId > 0) {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                track.albumId
            )
        } else if (track.audioPath.startsWith("content://")) {
            try {
                Uri.parse(track.audioPath)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val gradientColors = remember(track.artworkColorIndex) {
        when (track.artworkColorIndex % 5) {
            0 -> listOf(CyberNeonCyan, CyberElectricPurple)
            1 -> listOf(Color(0xFFFF3D00), Color(0xFFFFB703))
            2 -> listOf(Color(0xFF00E676), Color(0xFF00B0FF))
            3 -> listOf(Color(0xFFD500F9), Color(0xFF651FFF))
            else -> listOf(CyberElectricPurple, CyberNeonCyan)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        if (artworkUri != null && !isImageLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Song Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    isImageLoadFailed = true
                },
                onSuccess = {
                    isImageLoadFailed = false
                }
            )
        }

        // Show fallback icon if no image URI or image load fails
        if (artworkUri == null || isImageLoadFailed) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Track Artwork",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
