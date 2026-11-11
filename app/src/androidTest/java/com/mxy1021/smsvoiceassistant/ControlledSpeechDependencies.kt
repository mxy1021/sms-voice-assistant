package com.mxy1021.smsvoiceassistant

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mxy1021.smsvoiceassistant.audio.SpeechDependencies

/** Explicit test substitutes; these failures are NOT reported as real engine/system callbacks. */
internal enum class ControlledFailure { INIT, SYNTHESIS, FOCUS_REQUEST }

internal class ControlledSpeechDependencies(private val failure: ControlledFailure) : SpeechDependencies {
    var engine: ObservedFailureTts? = null
        private set
    var requests = 0
        private set
    var abandons = 0
        private set
    override fun createTts(context: Context, initialized: (Int) -> Unit): TextToSpeech {
        val nativeInit: (Int) -> Unit = if (failure == ControlledFailure.INIT) ({ _ -> }) else initialized
        val tts = ObservedFailureTts(context, nativeInit, failure == ControlledFailure.SYNTHESIS)
        engine = tts
        if (failure == ControlledFailure.INIT) Handler(Looper.getMainLooper()).post { initialized(TextToSpeech.ERROR) }
        return tts
    }
    override fun requestFocus(audio: AudioManager, request: AudioFocusRequest): Int {
        requests++
        check(failure == ControlledFailure.FOCUS_REQUEST) { "Engine failure must terminate before focus" }
        return AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }
    override fun abandonFocus(audio: AudioManager, request: AudioFocusRequest): Int {
        abandons++
        // The substitute denied the request and acquired no real system focus.
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
}

internal class ObservedFailureTts(context: Context, initialized: (Int) -> Unit, private val failSynthesis: Boolean) :
    TextToSpeech(context, OnInitListener { initialized(it) }) {
    var shutdownCalls = 0
        private set
    var stopCalls = 0
        private set
    private var progress: UtteranceProgressListener? = null
    override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?): Int {
        progress = listener
        return super.setOnUtteranceProgressListener(listener)
    }
    override fun synthesizeToFile(text: CharSequence, params: Bundle, fileDescriptor: ParcelFileDescriptor, utteranceId: String): Int {
        if (!failSynthesis) return super.synthesizeToFile(text, params, fileDescriptor, utteranceId)
        Handler(Looper.getMainLooper()).post {
            progress?.onStart(utteranceId)
            progress?.onError(utteranceId, ERROR_SYNTHESIS)
        }
        return SUCCESS
    }
    override fun stop(): Int { stopCalls++; return super.stop() }
    override fun shutdown() { super.shutdown(); shutdownCalls++ }
}
