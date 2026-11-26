package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerEngine
import com.example.audio.AudioSilenceDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GaplessAudioTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("harmanx_player_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `gapless smooth transition is enabled by default`() {
        val engine = AudioPlayerEngine.getInstance(context)
        assertTrue(engine.isGaplessEnabled.value)
    }

    @Test
    fun `toggling gapless persists saved state in SharedPreferences`() {
        val engine = AudioPlayerEngine.getInstance(context)
        val initial = engine.isGaplessEnabled.value

        engine.toggleGaplessMode()
        assertEquals(!initial, engine.isGaplessEnabled.value)
        val prefs = context.getSharedPreferences("harmanx_player_state", Context.MODE_PRIVATE)
        assertEquals(!initial, prefs.getBoolean("gapless_enabled", true))

        // Restore original default state for singleton isolation
        if (!engine.isGaplessEnabled.value) {
            engine.toggleGaplessMode()
        }
        assertTrue(engine.isGaplessEnabled.value)
    }

    @Test
    fun `silence detector preserves full track duration on non-file fallback`() = runBlocking {
        val bounds = AudioSilenceDetector.analyzeTrack(context, "synth_demo_test", 180_000L)
        assertEquals(0L, bounds.introStartMs)
        assertEquals(180_000L, bounds.outroEndMs)
    }
}
