package com.jkhx.player.core.player.tts.ducking

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

class AudioDuckingController(
    private val playerProvider: () -> ExoPlayer?
) {
    private var duckJob: Job? = null
    private var isDucked: Boolean = false
    private var baseVolume: Float = 1.0f

    fun duck(targetVolume: Float = 0.35f, durationMs: Long = 140L, scope: CoroutineScope) {
        val player = playerProvider() ?: return
        if (isDucked) return
        isDucked = true

        duckJob?.cancel()
        duckJob = scope.launch(Dispatchers.Main) {
            val startVol = player.volume
            val steps = 7
            val stepDelay = durationMs / steps
            val diff = targetVolume - startVol
            for (i in 1..steps) {
                player.volume = (startVol + diff * (i.toFloat() / steps)).coerceIn(0f, 1f)
                delay(stepDelay)
            }
            player.volume = targetVolume
        }
    }

    fun restore(durationMs: Long = 200L, scope: CoroutineScope) {
        val player = playerProvider() ?: return
        if (!isDucked) return
        isDucked = false

        duckJob?.cancel()
        duckJob = scope.launch(Dispatchers.Main) {
            val startVol = player.volume
            val targetVol = baseVolume
            val steps = 8
            val stepDelay = durationMs / steps
            val diff = targetVol - startVol
            for (i in 1..steps) {
                player.volume = (startVol + diff * (i.toFloat() / steps)).coerceIn(0f, 1f)
                delay(stepDelay)
            }
            player.volume = targetVol
        }
    }

    fun setBaseVolume(vol: Float) {
        baseVolume = vol.coerceIn(0f, 1f)
        if (!isDucked) {
            playerProvider()?.volume = baseVolume
        }
    }

    fun reset() {
        duckJob?.cancel()
        isDucked = false
        playerProvider()?.volume = baseVolume
    }
}

