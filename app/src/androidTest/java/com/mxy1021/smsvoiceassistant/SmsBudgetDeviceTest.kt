package com.mxy1021.smsvoiceassistant

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.media.AudioFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

/** Fixed artificial silent PCM, real AudioTrack/route/focus. NOT real TTS duration or SMS. */
@RunWith(AndroidJUnit4::class)
class SmsBudgetDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private class FixtureTts(context: Context, initialized: (Int) -> Unit, private val bytes: Int) :
        TextToSpeech(context, OnInitListener { initialized(it) }) {
        private var progress: UtteranceProgressListener? = null
        var shutdowns = 0
        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?): Int {
            progress = listener
            return super.setOnUtteranceProgressListener(listener)
        }
        override fun synthesizeToFile(text: CharSequence, params: Bundle, sink: ParcelFileDescriptor, utteranceId: String): Int {
            Handler(Looper.getMainLooper()).post {
                progress?.onStart(utteranceId)
                progress?.onBeginSynthesis(utteranceId, 16000, AudioFormat.ENCODING_PCM_16BIT, 1)
                val fixture = ByteArray(bytes)
                try { progress?.onAudioAvailable(utteranceId, fixture); progress?.onDone(utteranceId) }
                finally { fixture.fill(0) }
            }
            return SUCCESS
        }
        override fun shutdown() { try { super.shutdown() } finally { shutdowns++ } }
    }
    private fun runFixture(bytes: Int, delay: Long, expectedComplete: Boolean, waitLateCallback: Boolean = false) {
        assumeTrue(main { app.smsCoordinator.preflight() == null && app.session == null })
        val route = CompletableFuture<Boolean>()
        main { BluetoothProbe(app).inspect(app.target) { route.complete(it.output != null) } }
        assumeTrue("Original strict target required", route.get(3, TimeUnit.SECONDS))
        val original = main { app.settings.snapshot() }
        var engine: FixtureTts? = null
        val deps = object : SpeechDependencies {
            override fun createTts(context: Context, initialized: (Int) -> Unit): TextToSpeech =
                FixtureTts(context, { status -> app.main.postDelayed({ initialized(status) }, delay) }, bytes).also { engine = it }
        }
        val done = CompletableFuture<SpeechResult>()
        val started = SystemClock.elapsedRealtime()
        try {
            main {
                val id = app.beginSmsEvent(started, SmsSource.DEBUG_SIMULATION)
                app.speak("受控预算测试。", false, fromSms = true, smsEventId = id,
                    dependencies = deps, result = { done.complete(it) })
            }
            val result = done.get(11, TimeUnit.SECONDS)
            val elapsed = SystemClock.elapsedRealtime() - started
            assertEquals(expectedComplete, result.playbackCompleted)
            assertTrue(result.focusReleased)
            assertEquals(1, engine?.shutdowns)
            if (expectedComplete) {
                assertEquals(5000L, result.pcmDurationMs)
                assertEquals(bytes, result.bytesWritten)
                assertTrue(result.actualRouteConfirmed)
            } else assertEquals(0, result.bytesWritten)
            if (waitLateCallback) {
                while (SystemClock.elapsedRealtime() - started < 10_500) Thread.sleep(25)
                assertNull(main { app.session })
            }
            assertNull(main { app.session })
            assertEquals(original, main { app.settings.snapshot() })
            instrumentation.sendStatus(2, Bundle().apply { putString("stream",
                "\nSVA_BUDGET budgetMs=${SmsSafetyWindow.SESSION_BUDGET_MS};fixtureBytes=$bytes;fixedInitDelayMs=$delay;complete=${result.playbackCompleted};written=${result.bytesWritten};sessionCallbackMs=$elapsed;pendingResult=NOT_APPLICABLE;source=DEBUG_SIMULATION;pcm=SILENT_CONTROLLED_FIXTURE\n") })
        } finally { main { app.cancelSpeechWork() } }
    }
    @Test fun fiveSecondPcmPlaysEntirely() = runFixture(160000, 0, true)
    @Test fun fivePointOneSecondPcmIsRejectedEntirely() = runFixture(163200, 0, false)
    @Test fun fivePointTwoSecondPcmIsRejectedEntirely() = runFixture(166400, 0, false)
    @Test fun exactOneMiBStillFailsIndependentDurationGate() = runFixture(PcmBudget.MAX_BYTES, 0, false)
    @Test fun aboveOneMiBIsRejectedBeforeAnyPcmWrite() = runFixture(PcmBudget.MAX_BYTES + 2, 0, false)
    @Test fun sameSlowInitFixtureUsesOnlyTheConfiguredBudget() = runFixture(160000, 2400,
        SmsSafetyWindow.SESSION_BUDGET_MS == 8500L)
    @Test fun initBeyondBothDeadlinesCannotReviveAfterCleanup() = runFixture(160000, 9500, false, true)
}
