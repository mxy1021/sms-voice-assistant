package com.mxy1021.smsvoiceassistant.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech

/** Platform boundary. Production always uses the default implementation; no runtime selector. */
interface SpeechDependencies {
    fun permissionGranted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun createLoudness(sessionId: Int): LoudnessEffect {
        require(sessionId > 0) // Never attach an effect to the global output mix (session 0).
        val effect = android.media.audiofx.LoudnessEnhancer(sessionId)
        return object : LoudnessEffect {
            override fun configure(gainMb: Int) {
                effect.setTargetGain(gainMb)
                check(effect.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS)
            }
            override fun release() = effect.release()
        }
    }
    fun createTts(context: Context, initialized: (Int) -> Unit): TextToSpeech =
        TextToSpeech(context) { initialized(it) }
    fun requestFocus(audio: AudioManager, request: AudioFocusRequest): Int = audio.requestAudioFocus(request)
    fun abandonFocus(audio: AudioManager, request: AudioFocusRequest): Int = audio.abandonAudioFocusRequest(request)
}

object PlatformSpeechDependencies : SpeechDependencies
