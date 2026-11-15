package com.mxy1021.smsvoiceassistant.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech

/** Platform boundary. Production always uses the default implementation; no runtime selector. */
interface SpeechDependencies {
    fun processPcm(data: ByteArray, rate: Int, channels: Int, gainDb: Int,
                   cancelled: () -> Boolean): SpeechPcmProcessor.Result =
        SpeechPcmProcessor.process(data, rate, channels, gainDb, cancelled)
    fun permissionGranted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun createTts(context: Context, initialized: (Int) -> Unit): TextToSpeech =
        TextToSpeech(context) { initialized(it) }
    fun requestFocus(audio: AudioManager, request: AudioFocusRequest): Int = audio.requestAudioFocus(request)
    fun abandonFocus(audio: AudioManager, request: AudioFocusRequest): Int = audio.abandonAudioFocusRequest(request)
}

object PlatformSpeechDependencies : SpeechDependencies
