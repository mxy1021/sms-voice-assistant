package com.mxy1021.smsvoiceassistant

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mxy1021.smsvoiceassistant.audio.*
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SpeechExperienceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun waitForAudio(): Boolean {
        val until = SystemClock.elapsedRealtime() + 4500
        while (SystemClock.elapsedRealtime() < until) {
            if (main { (app.session?.bytesWritten ?: 0) > 0 }) return true
            Thread.sleep(20)
        }
        return false
    }
    @Test fun deniedContactPermissionDoesNotOpenProvider() {
        val context = object : ContextWrapper(app) {
            override fun checkSelfPermission(permission: String) = PackageManager.PERMISSION_DENIED
            override fun getContentResolver(): android.content.ContentResolver = error("must not query")
        }
        val done = CompletableFuture<String?>()
        main { ContactNameResolver(context, app.main).resolve("13800138000") { done.complete(it) } }
        assertNull(done.get(1, TimeUnit.SECONDS))
    }
    private fun audio(gain: Int, mode: String) {
        assumeTrue("Existing permissions/enabled state required", main { app.smsCoordinator.preflight() == null })
        val route = CompletableFuture<Boolean>()
        main { BluetoothProbe(app).inspect(app.target) { route.complete(it.output != null) } }
        assumeTrue("Original target route required", route.get(3, TimeUnit.SECONDS))
        val settings = main { app.settings.snapshot() }
        val manager = app.getSystemService(AudioManager::class.java)
        if (mode == "loss") assumeTrue("Do not permanently interrupt an external music player", !manager.isMusicActive)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var created = 0; var released = 0; var configured = 0; var actualSession = 0
        val deps = object : SpeechDependencies {
            override fun createLoudness(sessionId: Int): LoudnessEffect {
                actualSession = sessionId
                if (mode == "effectFailure") throw UnsupportedOperationException("controlled effect failure")
                val native = super.createLoudness(sessionId)
                created++
                return object : LoudnessEffect {
                    override fun configure(gainMb: Int) {
                        assertEquals(gain * 100, gainMb)
                        native.configure(gainMb); configured++
                    }
                    override fun release() { try { native.release() } finally { released++ } }
                }
            }
        }
        val done = CompletableFuture<SpeechResult>()
        var contender: AudioFocusRequest? = null
        val competingAudio = instrumentation.context.getSystemService(AudioManager::class.java)
        try {
            main {
                app.settings.setSpeechGain(gain)
                val id = app.beginSmsEvent(SystemClock.elapsedRealtime(), SmsSource.DEBUG_SIMULATION)
                app.speak("短信语音测试，完整播放。", false, fromSms = true, smsEventId = id,
                    dependencies = deps, result = { done.complete(it) })
            }
            val completes = mode == "complete" || mode == "effectFailure"
            if (!completes) {
                assertTrue("PCM must start before the terminal operation", waitForAudio())
                if (mode == "cancel") main { app.session?.cancel() }
                else {
                    contender = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                        .setOnAudioFocusChangeListener({}, app.main).build()
                    assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                        main { competingAudio.requestAudioFocus(contender!!) })
                }
            }
            val result = done.get(10, TimeUnit.SECONDS)
            assertEquals(completes, result.playbackCompleted)
            assertTrue(result.focusReleased)
            if (mode == "loss") assertTrue(result.focusChanges.contains(AudioManager.AUDIOFOCUS_LOSS))
            assertEquals(created, released)
            if (gain == 0) assertEquals(0, created) else assertTrue(actualSession > 0)
            assertEquals(volume, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertNull(main { app.session })
            instrumentation.sendStatus(2, Bundle().apply { putString("stream",
                "\nSVA_EXPERIENCE gain=$gain;mode=$mode;effectCreated=$created;configured=$configured;released=$released;streamVolumeUnchanged=true;source=DEBUG_SIMULATION\n") })
        } finally {
            contender?.let { main { competingAudio.abandonAudioFocusRequest(it) } }
            main { app.session?.cancel(); app.settings.setSpeechGain(settings.speechGainDb) }
            Thread.sleep(100)
            assertNull(main { app.session })
            assertEquals(settings, main { app.settings.snapshot() })
        }
    }
    @Test fun originalGainCompletesWithoutAnEffect() = audio(0, "complete")
    @Test fun lightGainIsTrackScopedAndReleased() = audio(3, "complete")
    @Test fun strongGainIsTrackScopedAndReleased() = audio(6, "complete")
    @Test fun unsupportedEffectFallsBackToCompleteOriginalSpeech() = audio(6, "effectFailure")
    @Test fun strongGainIsReleasedOnCancellation() = audio(6, "cancel")
    @Test fun strongGainIsReleasedOnPermanentFocusLoss() = audio(6, "loss")
}
