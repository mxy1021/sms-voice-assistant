package com.mxy1021.smsvoiceassistant

import android.Manifest
import android.content.*
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mxy1021.smsvoiceassistant.audio.*
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import com.mxy1021.smsvoiceassistant.sms.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Ordinary private test broadcasts, NEVER SMS_RECEIVED or real SMS PDUs. */
@RunWith(AndroidJUnit4::class)
class SessionCloseoutDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private class Engine(context: Context, initialized: (Int) -> Unit, val mode: String) :
        TextToSpeech(context, OnInitListener { initialized(it) }) {
        var stops = 0; var shutdowns = 0
        private var progress: UtteranceProgressListener? = null
        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?): Int {
            progress = listener
            return super.setOnUtteranceProgressListener(listener)
        }
        override fun synthesizeToFile(text: CharSequence, params: Bundle, sink: ParcelFileDescriptor, id: String): Int {
            Handler(Looper.getMainLooper()).post {
                progress?.onStart(id)
                if (mode == "synthesisFailure") progress?.onError(id, ERROR_SYNTHESIS)
                else {
                    progress?.onBeginSynthesis(id, 16000, AudioFormat.ENCODING_PCM_16BIT, 1)
                    val data = ByteArray(if (mode == "complete") 160000 else 64000)
                    try { progress?.onAudioAvailable(id, data); progress?.onDone(id) }
                    finally { data.fill(0) }
                }
            }
            return SUCCESS
        }
        override fun stop(): Int { stops++; return super.stop() }
        override fun shutdown() { shutdowns++; super.shutdown() }
    }
    private data class Terminal(val result: SmsResult, val received: Long, val finishCall: Long,
        val finishReturn: Long, val fields: Map<String, String>, val workerTerminated: Boolean)

    private fun runCase(mode: String) {
        assumeTrue(main { app.smsCoordinator.preflight() == null && app.session == null })
        val route = CompletableFuture<Boolean>()
        main { BluetoothProbe(app).inspect(app.target) { route.complete(it.output != null) } }
        assumeTrue("Original target must remain connected", route.get(3, TimeUnit.SECONDS))
        val original = main { app.settings.snapshot() }
        val allowed = AtomicBoolean(true)
        val completions = AtomicInteger(); val finishCalls = AtomicInteger()
        var engine: Engine? = null
        var ownedSession: SpeechSession? = null
        fun worker() = SpeechSession::class.java.getDeclaredField("worker").apply { isAccessible = true }
            .get(ownedSession) as ExecutorService
        var requests = 0; var abandons = 0; var effectCreated = 0; var effectReleased = 0
        var start = 0L
        val deps = object : SpeechDependencies {
            override fun permissionGranted(context: Context, permission: String): Boolean =
                (permission != Manifest.permission.BLUETOOTH_CONNECT || allowed.get()) &&
                    super.permissionGranted(context, permission)
            override fun createTts(context: Context, initialized: (Int) -> Unit): TextToSpeech =
                Engine(context, { status ->
                    val at = when (mode) { "slowInit" -> start + 9500; "complete" -> start + 1950; else -> 0L }
                    app.main.postDelayed({ initialized(if (mode == "initFailure") TextToSpeech.ERROR else status) },
                        (at - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                }, mode).also { engine = it; ownedSession = app.session }
            override fun requestFocus(audio: AudioManager, request: AudioFocusRequest): Int {
                requests++
                if (mode == "focusFailure") return AudioManager.AUDIOFOCUS_REQUEST_FAILED
                val result = super.requestFocus(audio, request)
                if (mode == "setupException") throw IllegalStateException("CONTROLLED_SETUP_FAILURE")
                return result
            }
            override fun abandonFocus(audio: AudioManager, request: AudioFocusRequest): Int {
                abandons++
                return if (mode == "focusFailure") AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    else super.abandonFocus(audio, request)
            }
            override fun createLoudness(sessionId: Int): LoudnessEffect {
                val native = super.createLoudness(sessionId); effectCreated++
                return object : LoudnessEffect {
                    override fun configure(gainMb: Int) = native.configure(gainMb)
                    override fun release() { native.release(); effectReleased++ }
                }
            }
        }
        val done = CompletableFuture<Terminal>()
        val action = app.packageName + ".test.SESSION_CLOSEOUT"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                start = SystemClock.elapsedRealtime()
                val pending = goAsync()
                var finished = false
                val coordinator = SmsCoordinator(app) { text, background, id, terminal ->
                    app.speak(text, background, fromSms = true, smsEventId = id, dependencies = deps, result = terminal)
                }
                val now = System.currentTimeMillis()
                coordinator.submit(listOf(SmsPart("synthetic-closeout", now, "验证码1234。")), start, now,
                    SmsSource.DEBUG_SIMULATION) { result ->
                    completions.incrementAndGet()
                    if (!finished) {
                        finished = true
                        val fields = app.smsEvents.value.first { it.id == result.id }.fields.toMap()
                        val workerTerminated = worker().isTerminated
                        val call = SystemClock.elapsedRealtime()
                        finishCalls.incrementAndGet(); pending.finish()
                        val returned = SystemClock.elapsedRealtime()
                        done.complete(Terminal(result, start, call, returned, fields, workerTerminated))
                    }
                }
            }
        }
        var track: AudioTrack? = null
        var contender: AudioFocusRequest? = null
        val competingAudio = instrumentation.context.getSystemService(AudioManager::class.java)
        try {
            main {
                app.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
                app.sendOrderedBroadcast(Intent(action).setPackage(app.packageName).addFlags(Intent.FLAG_RECEIVER_FOREGROUND), null)
            }
            if (mode in setOf("permission", "concurrentCancel", "paused")) {
                val until = SystemClock.elapsedRealtime() + 5000
                while (SystemClock.elapsedRealtime() < until && !main { (app.session?.bytesWritten ?: 0) > 0 }) Thread.sleep(15)
                val session = main { app.session!! }
                assertTrue(session.bytesWritten > 0)
                track = main { SpeechSession::class.java.getDeclaredField("track").apply { isAccessible = true }.get(session) as AudioTrack }
                when (mode) {
                    "permission" -> allowed.set(false) // Test signal only; no OS permission change or process kill.
                    "concurrentCancel" -> {
                        val threads = List(2) { Thread { repeat(4) { session.cancel() } } }
                        threads.forEach { it.start() }; threads.forEach { it.join() }
                    }
                    "paused" -> {
                        contender = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                            .setOnAudioFocusChangeListener({}, app.main).build()
                        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                            main { competingAudio.requestAudioFocus(contender!!) })
                    }
                }
            }
            val terminal = done.get(11, TimeUnit.SECONDS)
            val speech = terminal.result.speech!!
            assertEquals(mode == "complete", speech.playbackCompleted)
            assertTrue(speech.focusReleased)
            assertEquals(1, engine?.stops); assertEquals(1, engine?.shutdowns)
            assertEquals(requests, abandons); assertEquals(effectCreated, effectReleased)
            assertEquals(1, completions.get()); assertEquals(1, finishCalls.get())
            assertNull(main { app.session })
            track?.let { assertEquals(AudioTrack.STATE_UNINITIALIZED, it.state) }
            val released = terminal.fields.getValue("t.其他会话资源释放完成").toLong()
            assertTrue(released <= terminal.finishCall)
            if (mode in setOf("slowInit", "paused", "complete")) assertTrue(terminal.finishReturn - start < 8000)
            if (mode == "complete") {
                assertEquals(160000, speech.bytesWritten)
                assertTrue("Reserve callback must run while active audio is protected",
                    terminal.fields.containsKey("t.清理预留检查"))
                assertFalse(terminal.fields.containsKey("t.清理预留触发"))
            } else if (mode !in setOf("permission", "concurrentCancel", "paused")) assertEquals(0, speech.bytesWritten)
            if (mode == "paused") assertTrue(speech.focusChanges.contains(-3))
            if (mode == "permission") {
                assertFalse(allowed.get())
                assertTrue(speech.reason.contains("权限") || speech.reason.contains("状态"))
                assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
                    app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
            }
            contender?.let { main { competingAudio.abandonAudioFocusRequest(it) } }; contender = null
            if (mode == "slowInit") while (SystemClock.elapsedRealtime() - start < 10200) Thread.sleep(25)
            Thread.sleep(100)
            assertNull(main { app.session }); assertEquals(1, completions.get()); assertEquals(1, finishCalls.get())
            assertTrue("No executor may remain after session cancellation", worker().isTerminated)
            val points = JSONObject()
            listOf("Handler超时回调", "清理预留检查", "清理预留触发", "TTS取消开始", "AudioTrack停止开始",
                "AudioTrack释放完成", "焦点释放完成", "其他会话资源释放完成").forEach { key ->
                points.put(key, terminal.fields["t.$key"]?.toLong()?.minus(start) ?: JSONObject.NULL)
            }
            points.put("finishCall", terminal.finishCall - start).put("finishReturn", terminal.finishReturn - start)
            val output = JSONObject().put("mode", mode).put("source", "DEBUG_SIMULATION")
                .put("broadcast", "PRIVATE_TEST_ACTION_NOT_SMS").put("event", terminal.result.id)
                .put("realPendingResult", true).put("callbacks", completions.get()).put("finishCalls", finishCalls.get())
                .put("workerTerminatedBeforeFinish", terminal.workerTerminated).put("workerTerminatedAfterWait", worker().isTerminated)
                .put("pcmBytesWritten", speech.bytesWritten).put("timingsMs", points)
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nSVA_CLOSEOUT $output\n") })
        } finally {
            contender?.let { main { competingAudio.abandonAudioFocusRequest(it) } }
            main { app.session?.cancel(); runCatching { app.unregisterReceiver(receiver) } }
            assertEquals(original, main { app.settings.snapshot() })
        }
    }
    @Test fun slowInitializationReleasesBeforeDeadlineAndLateCallbackCannotRevive() = runCase("slowInit")
    @Test fun completeFiveSecondPcmSurvivesTheCleanupReserve() = runCase("complete")
    @Test fun controlledPermissionLossCleansNativeResourcesBeforeActualFinish() = runCase("permission")
    @Test fun concurrentRepeatedCancellationFinishesExactlyOnce() = runCase("concurrentCancel")
    @Test fun initializationFailureCleansBeforeFinish() = runCase("initFailure")
    @Test fun synthesisFailureCleansBeforeFinish() = runCase("synthesisFailure")
    @Test fun deniedFocusCleansBeforeFinish() = runCase("focusFailure")
    @Test fun delayedSetupExceptionCleansBeforeFinish() = runCase("setupException")
    @Test fun pausedAudioUsesTheExistingCleanupReserveAndNeverRevives() = runCase("paused")
}
