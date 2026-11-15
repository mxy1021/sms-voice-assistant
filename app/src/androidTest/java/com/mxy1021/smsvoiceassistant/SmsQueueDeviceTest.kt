package com.mxy1021.smsvoiceassistant

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mxy1021.smsvoiceassistant.audio.SpeechResult
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Controlled no-audio boundary tests production coordinator scheduling, not real SMS. */
@RunWith(AndroidJUnit4::class)
class SmsQueueDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun scenario(block: (SmsCoordinator, MutableList<String>, MutableList<(SpeechResult) -> Unit>) -> Unit) {
        assumeTrue(main { app.smsCoordinator.preflight() == null && app.session == null })
        val settings = main { app.settings.snapshot() }
        val started = mutableListOf<String>(); val callbacks = mutableListOf<(SpeechResult) -> Unit>()
        val coordinator = SmsCoordinator(app) { text, _, _, done -> started.add(text); callbacks.add(done) }
        try {
            main { app.settings.setOtpOnly(false); app.settings.setSimplify(false) }
            block(coordinator, started, callbacks)
        } finally {
            main {
                coordinator.cancelWaiting()
                callbacks.toList().forEach { it(SpeechResult(false, true, "CONTROLLED_CANCEL", "TEST")) }
                app.settings.setOtpOnly(settings.otpOnlyEnabled); app.settings.setSimplify(settings.otpSimplifyEnabled)
            }
            assertEquals(settings, main { app.settings.snapshot() })
        }
    }
    private fun submit(c: SmsCoordinator, body: String, elapsed: Long = SystemClock.elapsedRealtime()): CompletableFuture<SmsResult> {
        val done = CompletableFuture<SmsResult>()
        main {
            val wall = System.currentTimeMillis()
            c.submit(listOf(SmsPart("synthetic-${UUID.randomUUID()}", wall, body)), elapsed, wall, SmsSource.DEBUG_SIMULATION) { done.complete(it) }
        }
        return done
    }
    private fun complete(callback: (SpeechResult) -> Unit) = main { callback(SpeechResult(true, true, "CONTROLLED_COMPLETE", "TEST")) }
    @Test fun noPreemptionPriorityFifoAndFiveWaitingLimit() = scenario { c, started, callbacks ->
        val bodies = listOf("甲消息。", "乙消息。", "验证码1234。", "丙消息。", "验证码5678。", "丁消息。")
        val results = bodies.map { submit(c, it) }
        val rejected = submit(c, "验证码9999。").get(1, TimeUnit.SECONDS)
        assertEquals(SmsDisposition.BUSY, rejected.disposition)
        assertEquals(listOf("甲消息。"), started)
        repeat(6) { index -> complete(callbacks[index]) }
        assertEquals(listOf("甲消息。", "验证码一二三四。", "验证码五六七八。", "乙消息。", "丙消息。", "丁消息。"), started)
        assertTrue(results.all { it.get(1, TimeUnit.SECONDS).disposition == SmsDisposition.COMPLETED })
    }
    @Test fun waitingConsumesItsOwnOriginalBudget() = scenario { c, started, callbacks ->
        val first = submit(c, "当前消息。")
        val expired = submit(c, "等待消息。", SystemClock.elapsedRealtime() - SmsSafetyWindow.SESSION_BUDGET_MS + 150)
        assertEquals(SmsDisposition.REJECTED, expired.get(2, TimeUnit.SECONDS).disposition)
        assertEquals(1, started.size)
        complete(callbacks[0]); first.get(1, TimeUnit.SECONDS)
        assertEquals(1, started.size)
    }
    @Test fun safetyFailureClearsAllWaitingAndNeverRestartsThem() = scenario { c, started, callbacks ->
        submit(c, "当前消息。")
        val waiting = submit(c, "验证码1234。")
        main { callbacks[0](SpeechResult(false, true, "CONTROLLED_ROUTE_LOSS", "TEST")) }
        assertEquals(SmsDisposition.REJECTED, waiting.get(1, TimeUnit.SECONDS).disposition)
        main { c.drainWaiting() }; assertEquals(1, started.size)
    }
    @Test fun settingsChangeInvalidatesWaitingTextEvenAfterSwitchingBack() = scenario { c, started, callbacks ->
        submit(c, "当前消息。")
        val waiting = submit(c, "验证码1234。")
        main { app.settings.setSimplify(true); app.settings.setSimplify(false) }
        complete(callbacks[0])
        assertEquals(SmsDisposition.REJECTED, waiting.get(1, TimeUnit.SECONDS).disposition)
        main { c.drainWaiting() }; assertEquals(1, started.size)
    }
    @Test fun diagnosticOverflowCannotResetAWaitingBroadcastDeadline() = scenario { c, _, callbacks ->
        submit(c, "当前消息。")
        val received = SystemClock.elapsedRealtime() - 1000
        val id = main {
            val wall = System.currentTimeMillis()
            c.submit(listOf(SmsPart("synthetic-overflow", wall, "等待消息。")), received, wall, SmsSource.DEBUG_SIMULATION) {}
        }
        main { repeat(25) { app.beginSmsEvent(SystemClock.elapsedRealtime(), SmsSource.DEBUG_SIMULATION) } }
        assertEquals(received, main { app.receivedAt(id) })
        complete(callbacks[0])
        assertEquals(received, main { app.receivedAt(id) })
        complete(callbacks[1])
        assertNull(main { app.receivedAt(id) })
    }
}
