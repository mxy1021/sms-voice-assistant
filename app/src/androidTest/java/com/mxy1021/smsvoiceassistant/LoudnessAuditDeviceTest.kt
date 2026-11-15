package com.mxy1021.smsvoiceassistant

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mxy1021.smsvoiceassistant.audio.*
import com.mxy1021.smsvoiceassistant.sms.SmsSource
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.*

/** Fixed public phrases only. No audio files, SMS broadcasts, addresses or speech recognition. */
@RunWith(AndroidJUnit4::class)
class LoudnessAuditDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private class Capture(context: Context, initialized: (Int) -> Unit) :
        TextToSpeech(context, OnInitListener { initialized(it) }) {
        val raw = ByteArrayOutputStream()
        var rate = 0; var channels = 0; var encoding = 0
        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?): Int =
            super.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) { listener?.onStart(id) }
                override fun onBeginSynthesis(id: String, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                    rate = sampleRateInHz; channels = channelCount; encoding = audioFormat
                    listener?.onBeginSynthesis(id, sampleRateInHz, audioFormat, channelCount)
                }
                override fun onAudioAvailable(id: String, audio: ByteArray) {
                    synchronized(raw) { if (raw.size() + audio.size <= 1048576) raw.write(audio) }
                    listener?.onAudioAvailable(id, audio)
                }
                override fun onDone(id: String) { listener?.onDone(id) }
                @Deprecated("compatibility") override fun onError(id: String) { listener?.onError(id) }
                override fun onError(id: String, code: Int) { listener?.onError(id, code) }
                override fun onStop(id: String, interrupted: Boolean) { listener?.onStop(id, interrupted) }
            })
    }
    @Test fun measureProcessedPhrasesAtZeroGain() = measure(listOf(0), true)
    @Test fun measureFinalGains() = measure(SpeechGain.levels, false)
    @Test fun maximumPcmProcessingHasBoundedLatencyWithoutPlayback() {
        val data = ByteArray(960000) // Exactly 5s, 48kHz stereo; never submitted to a track.
        try {
            for (frame in 0 until 240000) for (ch in 0..1) {
                val value = ((sin(2*PI*1370*frame/48000)*.65 + sin(2*PI*11170*frame/48000)*.25)*32767).roundToInt()
                val i = (frame*2+ch)*2; data[i] = value.toByte(); data[i+1] = (value shr 8).toByte()
            }
            val start = SystemClock.elapsedRealtime()
            SpeechPcmProcessor.process(data,48000,2,9)
            val elapsed = SystemClock.elapsedRealtime()-start
            val stats = FixedPcmAudit.measure(data,48000,2)
            assertEquals(0,stats.getInt("clippingCount"))
            assertTrue(stats.getDouble("truePeakEstimateDbfs") <= -1.19)
            assertTrue("Processing must fit existing reserve on this device: ${elapsed}ms",elapsed < 700)
            instrumentation.sendStatus(2,Bundle().apply { putString("stream",
                "\nSVA_LOUDNESS_AUDIT worstCaseBytes=960000;processingMs=$elapsed;clipping=0;pcmMs=5000;playback=false\n") })
        } finally { data.fill(0) }
    }
    private fun measure(gains: List<Int>, threePhrases: Boolean) {
        assumeTrue(main { app.smsCoordinator.preflight() == null && app.session == null })
        val original = main { app.settings.snapshot() }
        val manager = app.getSystemService(AudioManager::class.java)
        val phrases = listOf("晚上七点见。", "验证码，零零九八一。", "短信语音测试，完整播放。")
        try {
            gains.forEach { gain ->
            main { app.settings.setSpeechGain(gain) }
            (if (threePhrases) phrases else listOf(phrases[2])).forEachIndexed { index, text ->
                var capture: Capture? = null
                var processed: ByteArray? = null
                var dsp: SpeechPcmProcessor.Result? = null
                var processingMs = 0L
                val deps = object : SpeechDependencies {
                    override fun processPcm(data: ByteArray, rate: Int, channels: Int, gainDb: Int,
                                            cancelled: () -> Boolean): SpeechPcmProcessor.Result {
                        val began = SystemClock.elapsedRealtime()
                        return super.processPcm(data, rate, channels, gainDb, cancelled).also {
                            processingMs = SystemClock.elapsedRealtime()-began
                            dsp = it; processed = data.clone()
                        }
                    }
                    override fun createTts(context: Context, initialized: (Int) -> Unit): TextToSpeech =
                        Capture(context, initialized).also { capture = it }
                }
                val done = CompletableFuture<SpeechResult>()
                val before = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                main {
                    val id = app.beginSmsEvent(SystemClock.elapsedRealtime(), SmsSource.DEBUG_SIMULATION)
                    app.speak(text, false, fromSms = true, smsEventId = id, dependencies = deps, result = { done.complete(it) })
                }
                var attributes: JSONObject? = null
                val until = SystemClock.elapsedRealtime() + 8000
                while (!done.isDone && SystemClock.elapsedRealtime() < until) {
                    main {
                        app.session?.let { session ->
                            val track = SpeechSession::class.java.getDeclaredField("track").apply { isAccessible = true }.get(session) as AudioTrack?
                            if (track != null && session.bytesWritten > 0) attributes = JSONObject()
                                .put("usage", track.audioAttributes.usage).put("contentType", track.audioAttributes.contentType)
                                .put("sampleRate", track.sampleRate).put("channelCount", track.channelCount)
                                .put("routedDeviceType", track.routedDevice?.type).put("sessionPositive", track.audioSessionId > 0)
                        }
                    }
                    Thread.sleep(20)
                }
                val result = done.get(10, TimeUnit.SECONDS)
                assertTrue(result.playbackCompleted); assertTrue(result.actualRouteConfirmed); assertTrue(result.focusReleased)
                val engine = capture!!
                val bytes = synchronized(engine.raw) { engine.raw.toByteArray().also { engine.raw.reset() } }
                try {
                    val output = processed!!
                    val stats = FixedPcmAudit.measure(output, engine.rate, engine.channels)
                    stats.put("sample", if (threePhrases) "A0${index + 1}" else "A03").put("source", "FIXED_TTS_NOT_SMS")
                        .put("gainDb", gain).put("encoding", engine.encoding).put("track", attributes)
                        .put("original", FixedPcmAudit.measure(bytes, engine.rate, engine.channels))
                        .put("processingMs", processingMs).put("baseGainDb", dsp!!.baseGainDb)
                        .put("minimumLimiterGain", dsp!!.minimumLimiterGain).put("finalCorrectionDb", dsp!!.finalCorrectionDb)
                        .put("streamVolumeBefore", before).put("streamVolumeAfter", manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                    assertEquals(bytes.size, output.size)
                    assertEquals(0, stats.getInt("clippingCount"))
                    assertTrue(stats.getDouble("truePeakEstimateDbfs") <= -1.19)
                    assertEquals(before, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                    output.fill(0)
                    instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nSVA_LOUDNESS_AUDIT $stats\n") })
                } finally { bytes.fill(0); processed?.fill(0) }
            }
            }
        } finally { main { app.session?.cancel(); app.settings.setSpeechGain(original.speechGainDb) } }
        assertEquals(original, main { app.settings.snapshot() })
    }
}

/** 20ms frames gated at -50dBFS; 4x/32-tap Hann-windowed sinc estimate, not certified dBTP/SPL. */
internal object FixedPcmAudit {
    private fun db(x: Double) = if (x <= 0) -120.0 else 20 * log10(x)
    fun measure(bytes: ByteArray, rate: Int, channels: Int): JSONObject {
        require(rate > 0 && channels in 1..2 && bytes.size % (2 * channels) == 0)
        val samples = bytes.size / 2
        fun sample(i: Int): Double = if (i !in 0 until samples) 0.0 else
            (((bytes[2*i].toInt() and 255) or (bytes[2*i+1].toInt() shl 8)).toShort().toInt() / 32768.0)
        var sum = 0.0; var squares = 0.0; var peak = 0.0; var clipping = 0
        for (i in 0 until samples) {
            val v = sample(i); sum += v; squares += v*v; peak = max(peak, abs(v))
            if (abs(v) >= 32767.0 / 32768) clipping++
        }
        val frameSamples = max(1, rate / 50) * channels
        var activeSq = 0.0; var activeCount = 0; var silentSamples = 0
        for (offset in 0 until samples step frameSamples) {
            val end = min(samples, offset + frameSamples); var energy = 0.0
            for (i in offset until end) energy += sample(i).let { it*it }
            if (sqrt(energy / (end-offset)) > 10.0.pow(-50.0 / 20)) { activeSq += energy; activeCount += end-offset }
            else silentSamples += end-offset
        }
        var truePeak = peak
        for (phase in 1..3) {
            val fraction = phase / 4.0
            val coeffs = DoubleArray(32) { n ->
                val t = n - 15 - fraction
                val sinc = if (abs(t) < 1e-12) 1.0 else sin(PI*t)/(PI*t)
                sinc * (0.5 + 0.5*cos(PI*t/16))
            }
            val norm = coeffs.sum(); for (n in coeffs.indices) coeffs[n] /= norm
            for (ch in 0 until channels) for (frame in 0 until samples/channels) {
                var v = 0.0
                for (n in coeffs.indices) v += sample((frame+n-15)*channels+ch)*coeffs[n]
                truePeak = max(truePeak, abs(v))
            }
        }
        val rms = sqrt(squares / max(1,samples)); val active = sqrt(activeSq / max(1,activeCount))
        return JSONObject().put("rate", rate).put("channels",channels).put("bits",16).put("bytes",bytes.size)
            .put("durationMs", bytes.size.toLong()*1000/(rate*channels*2))
            .put("wholeRmsDbfs",db(rms)).put("activeRmsDbfs",db(active)).put("peakDbfs",db(peak))
            .put("truePeakEstimateDbfs",db(truePeak)).put("crestDb",db(peak)-db(active))
            .put("dcOffset",sum/max(1,samples)).put("clippingCount",clipping)
            .put("silenceRatio",silentSamples.toDouble()/max(1,samples)).put("gateDbfs",-50)
    }
}
