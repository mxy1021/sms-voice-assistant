package com.mxy1021.smsvoiceassistant.audio

/** Read-only terminal telemetry. No text, code, sender, device identity or audio bytes. */
data class SpeechResult(
    val playbackCompleted: Boolean,
    val focusReleased: Boolean,
    val reason: String,
    val stage: String,
    val pcmDurationMs: Long? = null,
    val bytesWritten: Int = 0,
    val actualRouteConfirmed: Boolean = false,
    val focusChanges: List<Int> = emptyList(),
    val busy: Boolean = false
)
