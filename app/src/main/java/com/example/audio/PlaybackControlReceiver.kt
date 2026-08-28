package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.harmanx.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.harmanx.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.harmanx.ACTION_PREVIOUS"

        var audioPlayerEngine: AudioPlayerEngine? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val engine = audioPlayerEngine ?: return
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> engine.togglePlayPause()
            ACTION_NEXT -> engine.playNext()
            ACTION_PREVIOUS -> engine.playPrevious()
        }
    }
}
