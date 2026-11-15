package com.mxy1021.smsvoiceassistant.audio

/** Owns at most one effect; failed setup always falls back to unmodified PCM. */
interface LoudnessEffect {
    fun configure(gainMb: Int)
    fun release()
}

class SessionLoudness(private val create: (Int) -> LoudnessEffect) {
    private var effect: LoudnessEffect? = null
    private var closed = false
    fun attach(sessionId: Int, gainDb: Int): Boolean {
        if (closed || effect != null || sessionId <= 0 || gainDb !in setOf(0, 3, 6)) return false
        if (gainDb == 0) return true
        var candidate: LoudnessEffect? = null
        return try {
            candidate = create(sessionId)
            candidate.configure(gainDb * 100)
            effect = candidate
            true
        } catch (_: Exception) {
            runCatching { candidate?.release() }
            false
        }
    }
    fun release() {
        closed = true
        val old = effect
        effect = null
        runCatching { old?.release() }
    }
}
