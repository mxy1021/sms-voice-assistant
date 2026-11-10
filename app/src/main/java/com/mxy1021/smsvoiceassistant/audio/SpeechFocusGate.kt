package com.mxy1021.smsvoiceassistant.audio

import android.media.AudioManager

enum class SpeechFocusAction { PAUSE, RESUME, STOP, NONE }

/** A notification may interrupt our speech. Never interpret its transient loss as completion. */
class SpeechFocusGate {
    @Volatile var held = false
        private set
    private var terminal = false
    fun granted() { if (!terminal) held = true }
    fun end() { terminal = true; held = false }
    fun change(value: Int): SpeechFocusAction {
        if (terminal) return SpeechFocusAction.NONE
        return when (value) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                val resume = !held
                held = true
                if (resume) SpeechFocusAction.RESUME else SpeechFocusAction.NONE
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                held = false
                SpeechFocusAction.PAUSE
            }
            else -> { end(); SpeechFocusAction.STOP }
        }
    }
}
