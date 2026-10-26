package com.jkhx.player.core.player.tts.engine

interface TtsEngine {
    val engineId: String
    suspend fun synthesize(text: String, voiceId: String, speechRate: Float): ByteArray?
    fun release()
}

