package com.mxy1021.smsvoiceassistant.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech

/** Platform boundary. Production always uses the default implementation; no runtime selector. */
interface SpeechDependencies {
    fun createTts(context: Context, initialized: (Int) -> Unit): TextToSpeech =
        TextToSpeech(context) { initialized(it) }
    fun requestFocus(audio: AudioManager, request: AudioFocusRequest): Int = audio.requestAudioFocus(request)
    fun abandonFocus(audio: AudioManager, request: AudioFocusRequest): Int = audio.abandonAudioFocusRequest(request)
}

object PlatformSpeechDependencies : SpeechDependencies
