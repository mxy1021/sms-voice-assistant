package com.mxy1021.smsvoiceassistant

import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import com.mxy1021.smsvoiceassistant.settings.*
import com.mxy1021.smsvoiceassistant.sms.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Signed, shell-launched instrumentation only. No exported injection component. */
@RunWith(AndroidJUnit4::class)
class DebugSmsInjectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private var sequence = 0
    private var failures = 0
    private data class Submission(val id: String, val done: CompletableFuture<SmsResult>)
    private fun <T> main(block: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun emit(id: String, status: String, evidence: String, result: SmsResult? = null,
        mechanism: String = "PLATFORM") {
        if (status == "FAIL") failures++
        val json = JSONObject().put("schema", 1).put("source", "DEBUG_SIMULATION")
            .put("case", id).put("status", status).put("evidence", evidence)
            .put("mechanism", mechanism)
            .put("assertions", if (status in setOf("PASS", "MANUAL_REQUIRED", "UNSUPPORTED")) "PASS" else status)
            .put("hearing", if (status == "MANUAL_REQUIRED" || result?.speech?.playbackCompleted == true) "MANUAL_REQUIRED" else "NOT_RUN")
        result?.let { r ->
            json.put("event", r.id).put("disposition", r.disposition.name).put("reason", r.reason)
            r.speech?.let { s -> json.put("pcmMs", s.pcmDurationMs).put("bytesWritten", s.bytesWritten)
                .put("actualRoute", s.actualRouteConfirmed).put("focusReleased", s.focusReleased)
                .put("focusChanges", org.json.JSONArray(s.focusChanges)) }
        }
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nSVA_DEBUG_RESULT $json\n") })
    }
    private fun configure(only: Boolean, simplify: Boolean) = main {
        // Normal repository setters, not an override of the coordinator's configuration.
        app.settings.setOtpOnly(true); app.settings.setSimplify(simplify); app.settings.setOtpOnly(only)
    }
    private fun submit(text: String, parts: List<String> = listOf(text), coordinator: SmsCoordinator = app.smsCoordinator): Submission {
        val future = CompletableFuture<SmsResult>()
        val id = main {
            val wall = System.currentTimeMillis()
            val sender = "synthetic-fixture-${++sequence}"
            coordinator.submit(parts.map { SmsPart(sender, wall, it) }, SystemClock.elapsedRealtime(),
                wall, SmsSource.DEBUG_SIMULATION) { future.complete(it) }
        }
        return Submission(id, future)
    }
    private fun await(job: Submission) = job.done.get(11, TimeUnit.SECONDS)
    private fun idle() = main { app.session == null && !app.speaking.value }
    private fun audioCondition(): String? {
        if (main { app.smsCoordinator.preflight() != null }) return "APP_DISABLED_OR_PERMISSION_MISSING"
        val future = CompletableFuture<Boolean>()
        main { BluetoothProbe(app).inspect(app.target) { future.complete(it.output != null) } }
        return if (future.get(3, TimeUnit.SECONDS)) null else "TARGET_OR_EXPECTED_ROUTE_UNAVAILABLE"
    }
    private fun message(id: String, text: String, only: Boolean, simplify: Boolean,
        ignored: Boolean = false, textLimit: Boolean = false, parts: List<String> = listOf(text)) {
        configure(only, simplify)
        val actual = main { SpeechFormatter.choose(text, app.settings.snapshot()).text }
        val expected = if (ignored || textLimit) null else if (only && simplify) {
            val detected = OtpExtractor.extract(text)
            assertNotNull("$id reliable OTP required", detected)
            SpeechFormatter.choose(text, AppSettings(announcingEnabled = true)).text
        } else text
        if (!main { app.settings.snapshot().announcingEnabled }) {
            val r = await(submit(text, parts))
            assertEquals(SmsDisposition.REJECTED, r.disposition)
            assertNull(r.speech)
            emit(id, "SKIP", "APP_DISABLED_GUARD_VERIFIED", r); return
        }
        assertTrue("$id formatter contract", actual == expected)
        if (!ignored && !textLimit) audioCondition()?.let { emit(id, "SKIP", "$it;RULES_CHECKED"); return }
        val r = await(submit(text, parts))
        assertEquals(SmsSource.DEBUG_SIMULATION, r.source)
        assertTrue("$id session must terminate before next case", idle())
        val diagnostics = main { app.smsEvents.value.first { it.id == r.id }.fields }
        assertEquals("DEBUG_SIMULATION", diagnostics["source"])
        assertEquals("未触发（解析后业务注入）", diagnostics["SMS_RECEIVED"])
        assertFalse("$id must not fabricate PendingResult.finish", diagnostics.containsKey("t.PendingResult.finish"))
        if (ignored || textLimit) {
            assertEquals(SmsDisposition.IGNORED, r.disposition); assertNull(r.speech)
            if (textLimit) assertTrue(r.reason.contains("80"))
            emit(id, "PASS", if (textLimit) "WHOLE_TEXT_REJECTED_BEFORE_TTS" else "RULE_FILTER_IGNORED", r)
        } else {
            val s = r.speech ?: error("Missing terminal speech telemetry")
            assertTrue("$id focus must be released or never requested", s.focusReleased)
            when {
                r.disposition == SmsDisposition.COMPLETED -> {
                    assertTrue(s.actualRouteConfirmed); assertTrue(s.bytesWritten > 0)
                    assertTrue((s.pcmDurationMs ?: 0) in 1..PcmBudget.MAX_SPEECH_MS)
                    emit(id, "MANUAL_REQUIRED", "RULES_AND_PCM_API_COMPLETE;NO_AUDIBILITY_ASSERTION", r)
                }
                (s.reason.contains("预算") || s.reason.contains("上限") || s.reason.contains("超过5秒")) && s.bytesWritten == 0 ->
                    emit(id, "UNSUPPORTED", "WHOLE_AUDIO_SAFELY_REJECTED;FULL_SPEECH_NOT_DELIVERED", r)
                else -> emit(id, "FAIL", "UNEXPECTED_SPEECH_FAILURE", r)
            }
        }
    }
    private fun rapid() {
        configure(true, true)
        audioCondition()?.let { emit("D10", "SKIP", it); return }
        val jobs = main {
            List(3) {
                val future = CompletableFuture<SmsResult>()
                val wall = System.currentTimeMillis()
                val id = app.smsCoordinator.submit(listOf(SmsPart("synthetic-rapid-${++sequence}", wall,
                    DebugSmsFixtures.SHORT_OTP)), SystemClock.elapsedRealtime(), wall, SmsSource.DEBUG_SIMULATION) { future.complete(it) }
                Submission(id, future)
            }
        }
        val results = jobs.map(::await)
        assertEquals(2, results.count { it.disposition == SmsDisposition.BUSY })
        assertTrue(idle())
        assertTrue(results.all { it.speech?.focusReleased != false })
        val first = results.first()
        emit("D10", if (first.disposition == SmsDisposition.COMPLETED) "MANUAL_REQUIRED" else "FAIL",
            "TWO_OVERLAPPING_INPUTS_REJECTED_BUSY;FIRST_TERMINATED", first)
    }
    private fun disabled() {
        val enabled = main { app.settings.snapshot().announcingEnabled }
        try {
            main { app.setAnnouncingEnabled(false) }
            val r = await(submit(DebugSmsFixtures.OTP))
            assertEquals(SmsDisposition.REJECTED, r.disposition); assertNull(r.speech); assertTrue(idle())
            emit("D11", "PASS", "TOTAL_SWITCH_REJECTS_BEFORE_AUDIO", r)
        } finally { main { app.setAnnouncingEnabled(enabled) } }
    }
    private fun disconnected() {
        if (!main { app.settings.snapshot().announcingEnabled }) { emit("D12", "SKIP", "APP_DISABLED"); return }
        val connected = CompletableFuture<Boolean>()
        main { BluetoothProbe(app).inspect(app.target) { connected.complete(it.connected) } }
        if (connected.get(3, TimeUnit.SECONDS)) { emit("D12", "SKIP", "MANUALLY_DISCONNECT_TARGET_FIRST"); return }
        configure(true, true)
        val r = await(submit(DebugSmsFixtures.OTP))
        assertNotEquals(SmsDisposition.COMPLETED, r.disposition)
        assertEquals(0, r.speech?.bytesWritten ?: 0); assertTrue(idle())
        emit("D12", "PASS", "DISCONNECTED_GUARD_NO_PCM_WRITTEN;SPEAKER_HEARING_NOT_ASSERTED", r)
    }
    private fun waitUntil(timeout: Long, condition: () -> Boolean): Boolean {
        val until = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return true
            Thread.sleep(15)
        }
        return condition()
    }
    private fun focus(mode: String) {
        val id = "D13.$mode"
        configure(true, true)
        audioCondition()?.let { emit(id, "SKIP", it); return }
        val enabled = main { app.settings.snapshot().announcingEnabled }
        val job = submit(DebugSmsFixtures.SHORT_OTP)
        if (!waitUntil(2500) { main { (app.session?.bytesWritten ?: 0) > 0 } }) {
            val r = await(job); emit(id, "SKIP", "SPEECH_DID_NOT_START_FOR_FOCUS_COMPETITION", r); return
        }
        val audio = instrumentation.context.getSystemService(AudioManager::class.java)
        val contender = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener({ }, app.main).build()
        var acquired = false
        try {
            acquired = main { audio.requestAudioFocus(contender) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
            val paused = acquired && waitUntil(1200) { main {
                app.session != null && app.session?.hasAudioFocus == false &&
                    app.smsEvents.value.firstOrNull { it.id == job.id }?.fields?.get("onAudioFocusChange") == "-3"
            } }
            if (!paused) {
                if (acquired) { main { audio.abandonAudioFocusRequest(contender) }; acquired = false }
                val r = await(job); emit(id, "SKIP", "REAL_SYSTEM_MINUS3_NOT_OBSERVED;NO_FAKE_CALLBACK", r); return
            }
            Thread.sleep(50)
            val bytes = main { app.session?.bytesWritten ?: 0 }
            Thread.sleep(150)
            assertEquals("$id no PCM writes without focus", bytes, main { app.session?.bytesWritten ?: 0 })
            when (mode) {
                "resume" -> { main { audio.abandonAudioFocusRequest(contender) }; acquired = false }
                "stop" -> main { app.setAnnouncingEnabled(false) }
                "timeout" -> Unit // Hold actual competing focus; original 8-second timeout must fire.
                "disconnect" -> instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "\nSVA_DEBUG_ACTION DISCONNECT_WHILE_PAUSED\n")
                })
            }
            val r = await(job)
            val s = r.speech!!
            assertTrue(s.focusChanges.contains(-3)); assertTrue(s.focusReleased); assertTrue(idle())
            if (mode == "resume") {
                assertTrue("$id real system gain required", s.focusChanges.contains(1))
                assertEquals(SmsDisposition.COMPLETED, r.disposition)
                assertTrue(s.actualRouteConfirmed)
                emit(id, "MANUAL_REQUIRED", "REAL_ANDROID_FOCUS_MINUS3_AND_GAIN;WRITES_PAUSED;API_COMPLETED", r)
            } else {
                assertFalse(s.playbackCompleted)
                if (mode == "timeout") assertTrue(s.reason.contains("8") || s.reason.contains("预算"))
                if (mode == "disconnect") {
                    val routeGuard = s.reason.startsWith("目标蓝牙断开") || s.reason.startsWith("目标输出被移除") ||
                        s.reason == "实际路由改变或权限撤销，取消播报"
                    if (!routeGuard) {
                        emit(id, "SKIP", "NO_PLATFORM_DISCONNECT_DURING_REAL_FOCUS_PAUSE_OBSERVED", r); return
                    }
                }
                main { audio.abandonAudioFocusRequest(contender) }; acquired = false
                Thread.sleep(100)
                assertTrue("$id must not revive after contender releases", idle())
                emit(id, if (mode == "disconnect") "MANUAL_REQUIRED" else "PASS",
                    "REAL_FOCUS_PAUSE_THEN_${mode.uppercase()};NO_SESSION_REVIVAL", r)
            }
        } finally {
            if (acquired) main { audio.abandonAudioFocusRequest(contender) }
            if (!job.done.isDone) { main { app.session?.cancel() }; await(job) }
            main { app.setAnnouncingEnabled(enabled) }
        }
    }
    private fun contracts() {
        assertEquals(AppSettings(), AppSettings(false, true, true, false))
        val before = main { app.settings.snapshot() }
        try {
            main { app.settings.setOtpOnly(true); app.settings.setSimplify(false); app.settings.setOtpOnly(false) }
            val snapshot = main { app.settings.snapshot() }
            assertFalse(snapshot.otpSimplifyEnabled)
            main { app.settings.setSimplify(true) }
            assertFalse(main { app.settings.snapshot().otpSimplifyEnabled })
            val restored = main { SettingsRepository(PreferenceSettingsStore(app.prefs),
                android.provider.Settings.Global.getInt(app.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)).snapshot() }
            assertTrue("Persisted settings must reload unchanged", snapshot == restored)
            assertFalse(com.mxy1021.smsvoiceassistant.audio.RoutePolicy.matches("synthetic-target", listOf("other-target")))
            emit("D00", "PASS", "DEFAULTS_RULE_DEPENDENCY_REAL_PREFERENCE_RELOAD_AND_IDENTITY_REJECTION")
        } finally { configure(before.otpOnlyEnabled, before.otpSimplifyEnabled) }
    }

    /** Controlled notification-USAGE focus owner, not carrier SMS and not an audible ringtone. */
    private fun notificationTiming(mode: String) {
        val id = "N.$mode"
        configure(true, true)
        audioCondition()?.let { emit(id, "SKIP", it); return }
        val audio = instrumentation.context.getSystemService(AudioManager::class.java)
        val competitorChanges = mutableListOf<Int>()
        val contender = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener({ competitorChanges.add(it) }, app.main).build()
        var acquired = false
        var job: Submission? = null
        try {
            if (mode == "before") {
                acquired = main { audio.requestAudioFocus(contender) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
                assertTrue("Real notification focus grant required", acquired)
            }
            val submittedAt = SystemClock.elapsedRealtime()
            val current = submit(DebugSmsFixtures.SHORT_OTP); job = current
            if (mode == "late") {
                assertTrue(waitUntil(3000) { main { (app.session?.bytesWritten ?: 0) > 0 } })
                Thread.sleep(500)
            } else if (mode != "before") {
                Thread.sleep((submittedAt + 900 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            }
            if (!acquired) {
                acquired = main { audio.requestAudioFocus(contender) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
                assertTrue("Real competing focus request required", acquired)
            }
            val holdUntil = SystemClock.elapsedRealtime() + if (mode == "before") 1500 else 4866
            if (mode == "late" || mode == "repro") {
                assertTrue("Actual -3 required, not a synthetic callback", waitUntil(1000) { main {
                    app.smsEvents.value.first { it.id == current.id }.fields["onAudioFocusChange"] == "-3"
                } })
                val bytes = main { app.session?.bytesWritten }
                Thread.sleep(150)
                assertEquals("No writes during notification focus loss", bytes, main { app.session?.bytesWritten })
            }
            Thread.sleep((holdUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            main { audio.abandonAudioFocusRequest(contender) }; acquired = false
            val r = await(current); val s = r.speech!!
            assertTrue(s.focusReleased); assertTrue(idle())
            if (mode in setOf("before", "startup")) {
                assertTrue("Speech must finish within the original budget", s.playbackCompleted)
                assertTrue(s.actualRouteConfirmed)
                assertTrue("Our initial legal focus request must reach the competing owner", main { competitorChanges.contains(-3) })
            } else {
                assertFalse(s.playbackCompleted)
                assertTrue("Long loss must cancel on insufficient budget", s.reason.contains("预算") || s.reason.contains("剩余时间不足"))
                assertTrue(s.focusChanges.contains(-3))
            }
            Thread.sleep(100); assertTrue("Late gain cannot revive terminal speech", idle())
            emit(id, if (s.playbackCompleted) "MANUAL_REQUIRED" else "PASS",
                "CONTROLLED_NOTIFICATION_FOCUS_ONLY;CARRIER_SMS_NOT_TESTED;COMPLETE=${s.playbackCompleted};" +
                    "COMPETITOR_CHANGES=${main { competitorChanges.toList() }};BUDGET_UNCHANGED", r, "ANDROID_SYSTEM")
        } finally {
            if (acquired) main { audio.abandonAudioFocusRequest(contender) }
            job?.let { if (!it.done.isDone) { main { app.session?.cancel() }; await(it) } }
        }
    }

    private fun activeChange(stop: Boolean) {
        val id = if (stop) "L01.normalStop" else "R20.ruleChange"
        configure(true, true)
        audioCondition()?.let { emit(id, "SKIP", it); return }
        val saved = main { app.settings.snapshot() }
        val job = submit(DebugSmsFixtures.SHORT_OTP)
        try {
            if (!waitUntil(2500) { main { (app.session?.bytesWritten ?: 0) > 0 && app.session?.hasAudioFocus == true } }) {
                val r = await(job); emit(id, "SKIP", "NORMAL_PLAYBACK_NOT_OBSERVED", r); return
            }
            val changedAt = SystemClock.elapsedRealtime()
            main {
                if (stop) app.setAnnouncingEnabled(false)
                else app.settings.setOtpOnly(false) // Exercise the generation gate, without calling cancel directly.
            }
            assertTrue("$id old playback must promptly terminate", waitUntil(1500) { job.done.isDone })
            val r = await(job); val speech = r.speech!!
            assertFalse(speech.playbackCompleted); assertTrue(speech.bytesWritten > 0)
            assertTrue(speech.focusReleased); assertTrue(idle())
            if (speech.focusChanges.isNotEmpty()) {
                emit(id, "SKIP", "EXTERNAL_FOCUS_INTERRUPTION_CHANGED_NORMAL_PLAYBACK_CONDITION", r); return
            }
            main { app.setAnnouncingEnabled(saved.announcingEnabled) }
            configure(saved.otpOnlyEnabled, saved.otpSimplifyEnabled)
            Thread.sleep(100); assertTrue("$id restoring configuration must not revive the old session", idle())
            assertTrue(SystemClock.elapsedRealtime() - changedAt < 2000)
            emit(id, "PASS", "NORMAL_PCM_ACTIVE_THEN_MUTATION;TERMINATED_NO_REVIVAL;STOP_HEARING_SEPARATE", r)
        } finally {
            if (!job.done.isDone) { main { app.session?.cancel() }; await(job) }
            configure(saved.otpOnlyEnabled, saved.otpSimplifyEnabled)
            main { app.setAnnouncingEnabled(saved.announcingEnabled) }
        }
    }

    private fun controlledFailure(failure: ControlledFailure) {
        val id = when (failure) {
            ControlledFailure.INIT -> "L05.init"
            ControlledFailure.SYNTHESIS -> "L05.synthesis"
            ControlledFailure.FOCUS_REQUEST -> "L06.denied"
        }
        configure(true, true)
        audioCondition()?.let { emit(id, "SKIP", it, mechanism = "CONTROLLED_SUBSTITUTE"); return }
        val dependencies = ControlledSpeechDependencies(failure)
        val coordinator = SmsCoordinator(app) { text, background, eventId, result ->
            app.speak(text, background, fromSms = true, smsEventId = eventId, result = result, dependencies = dependencies)
        }
        val r = await(submit(DebugSmsFixtures.SHORT_OTP, coordinator = coordinator))
        val speech = r.speech!!
        assertFalse(speech.playbackCompleted); assertEquals(0, speech.bytesWritten)
        assertFalse(speech.actualRouteConfirmed); assertTrue(speech.focusReleased); assertTrue(idle())
        assertTrue("$id TTS stop/shutdown invoked on the constructed platform object", main {
            (dependencies.engine?.shutdownCalls ?: 0) == 1 && (dependencies.engine?.stopCalls ?: 0) >= 1
        })
        if (failure == ControlledFailure.FOCUS_REQUEST) {
            assertEquals(1, dependencies.requests); assertEquals(1, dependencies.abandons)
            assertTrue(speech.reason.contains("音频焦点申请失败"))
        } else {
            assertEquals(0, dependencies.requests); assertEquals(0, dependencies.abandons)
            assertTrue(speech.reason.contains(if (failure == ControlledFailure.INIT) "初始化失败" else "合成失败"))
        }
        emit(id, "PASS", "CONTROLLED_FAILURE_BRANCH;ZERO_PCM;TTS_STOP_SHUTDOWN;NO_SYSTEM_ERROR_CLAIM", r,
            mechanism = "CONTROLLED_SUBSTITUTE")
    }

    private fun permanentFocusLoss() {
        val id = "L06.permanent"
        configure(true, true)
        audioCondition()?.let { emit(id, "SKIP", it, mechanism = "ANDROID_SYSTEM"); return }
        val audio = instrumentation.context.getSystemService(AudioManager::class.java)
        if (audio.isMusicActive) {
            emit(id, "SKIP", "EXTERNAL_MUSIC_ACTIVE;DO_NOT_DISRUPT_USER_MUSIC", mechanism = "ANDROID_SYSTEM"); return
        }
        val job = submit(DebugSmsFixtures.SHORT_OTP)
        val contender = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener({ }, app.main).build()
        var acquired = false
        try {
            if (!waitUntil(2500) { main { (app.session?.bytesWritten ?: 0) > 0 } }) {
                emit(id, "SKIP", "PLAYBACK_NOT_STARTED", await(job), "ANDROID_SYSTEM"); return
            }
            acquired = main { audio.requestAudioFocus(contender) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
            if (!acquired) { emit(id, "SKIP", "COMPETITOR_FOCUS_NOT_GRANTED", await(job), "ANDROID_SYSTEM"); return }
            val r = await(job); val speech = r.speech!!
            if (!speech.focusChanges.contains(-1)) {
                emit(id, "SKIP", "REAL_MINUS1_NOT_OBSERVED;NO_FAKE_CALLBACK", r, "ANDROID_SYSTEM"); return
            }
            assertFalse(speech.playbackCompleted); assertTrue(speech.focusReleased); assertTrue(idle())
            main { audio.abandonAudioFocusRequest(contender) }; acquired = false
            Thread.sleep(100); assertTrue(idle())
            emit(id, "PASS", "ACTUAL_ANDROID_MINUS1_CANCELS_AND_DOES_NOT_REVIVE", r, "ANDROID_SYSTEM")
        } finally {
            if (acquired) main { audio.abandonAudioFocusRequest(contender) }
            if (!job.done.isDone) { main { app.session?.cancel() }; await(job) }
        }
    }

    private fun permissionRevoked() {
        val id = "T10.bluetoothRevoked"
        if (app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            emit(id, "SKIP", "USER_MUST_REVOKE_BLUETOOTH_NORMALLY;HARNESS_DOES_NOT_CHANGE_PERMISSIONS"); return
        }
        if (!main { app.settings.snapshot().announcingEnabled }) { emit(id, "SKIP", "APP_DISABLED"); return }
        val r = await(submit(DebugSmsFixtures.OTP))
        assertEquals(SmsDisposition.REJECTED, r.disposition); assertNull(r.speech); assertTrue(idle())
        assertTrue(r.reason.contains("权限缺失"))
        emit(id, "PASS", "REAL_BLUETOOTH_PERMISSION_DENIED;NO_TTS_OR_PCM", r, "ANDROID_PERMISSION_STATE")
    }

    private fun manualRouteChange(disconnect: Boolean) {
        val id = if (disconnect) "L02.dynamic" else "L03.dynamic"
        configure(true, true)
        audioCondition()?.let { emit(id, "SKIP", it); return }
        val action = if (disconnect) "DISCONNECT_DURING_SPEECH" else "ROUTE_DURING_SPEECH"
        val expectedSettings = main { app.settings.snapshot() }
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nSVA_DEBUG_ACTION $action\n") })
        Thread.sleep(3000) // Preparation only; the unchanged 8-second budget starts at submit below.
        val job = submit(DebugSmsFixtures.OTP)
        val r = await(job); val speech = r.speech!!
        if (speech.playbackCompleted) {
            emit(id, "SKIP", "NO_REAL_DISCONNECT_OR_ROUTE_CHANGE_OBSERVED;DO_NOT_INFER_A_CALLBACK", r); return
        }
        // Do not treat the writer's combined "state/permission/route" error as proof
        // of a routing callback. Require a specific platform route/disconnect path,
        // with permissions and user configuration still intact.
        val specificRoutePath = speech.reason.startsWith("目标蓝牙断开") ||
            speech.reason.startsWith("目标输出被移除") ||
            speech.reason == "实际路由改变或权限撤销，取消播报"
        val routeFailure = specificRoutePath && main { app.settings.snapshot() == expectedSettings } &&
            app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!routeFailure || speech.bytesWritten <= 0 || !speech.actualRouteConfirmed) {
            emit(id, "SKIP", "REQUIRED_DYNAMIC_ROUTE_CONDITION_NOT_ESTABLISHED", r); return
        }
        assertTrue(speech.focusReleased); assertTrue(idle())
        emit(id, "MANUAL_REQUIRED", "ACTIVE_PCM_THEN_PLATFORM_ROUTE_GUARD_CANCEL;CONFIRM_NO_SPEAKER_LEAK", r, "ANDROID_ROUTE_GUARD")
    }

    @Test fun runSelectedFixtures() {
        assertTrue("Only Debug target is supported", app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val original = main { app.settings.snapshot() }
        try {
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nSVA_DEBUG_READY\n") })
            assertTrue("Open the real launcher through ADB after readiness; device must be unlocked",
                waitUntil(15_000) { main { app.visible } })
            val selected = InstrumentationRegistry.getArguments().getString("fixture", "All")
            val cases = if (selected == "All") listOf("Logic", "Otp", "OtpFull", "Normal", "Numbers", "Mixed",
                "OtpAmbiguous", "Long80", "Long81", "LongPcm", "Rapid", "Disabled", "Disconnected", "Focus", "Multipart",
                "StopDuring", "RulesDuring", "TtsFailures", "FocusFailure", "FocusPermanent")
                else if (selected == "Long") listOf("Long80", "Long81", "LongPcm")
                else if (selected == "Safety") listOf("StopDuring", "RulesDuring", "TtsFailures", "FocusFailure", "FocusPermanent")
                else listOf(selected)
            cases.forEach { case ->
                try {
                    when (case) {
                        "Logic" -> contracts()
                        "Otp" -> message("D01", DebugSmsFixtures.OTP, true, true)
                        "OtpFull" -> {
                            message("D02", DebugSmsFixtures.OTP_FULL, true, false)
                            message("D02.short", DebugSmsFixtures.SHORT_OTP, true, false)
                        }
                        "Normal" -> {
                            message("D03.filtered", DebugSmsFixtures.NORMAL, true, true, ignored = true)
                            message("D03.full", DebugSmsFixtures.NORMAL, false, true)
                            message("D03.short", DebugSmsFixtures.SHORT, false, true)
                        }
                        "Numbers" -> {
                            message("D04.filtered", DebugSmsFixtures.ORDER, true, true, ignored = true)
                            message("D04.full", DebugSmsFixtures.ORDER, false, true)
                        }
                        "Mixed" -> message("D05", DebugSmsFixtures.MIXED, true, true)
                        "OtpAmbiguous" -> {
                            assertEquals("429317", OtpExtractor.extract(DebugSmsFixtures.CONTEXTUAL)?.code)
                            message("D06.context", DebugSmsFixtures.CONTEXTUAL, true, true)
                            message("D06.ambiguous", "验证码1234或5678。", true, true, ignored = true)
                        }
                        "Long80" -> { assertEquals(80, DebugSmsFixtures.LONG80.length); message("D07", DebugSmsFixtures.LONG80, false, true) }
                        "Long81" -> { assertEquals(81, DebugSmsFixtures.LONG81.length); message("D08", DebugSmsFixtures.LONG81, false, true, textLimit = true) }
                        "LongPcm" -> message("D09", DebugSmsFixtures.LONG_PCM, false, true)
                        "Rapid" -> rapid()
                        "Disabled" -> disabled()
                        "Disconnected" -> disconnected()
                        "Focus" -> { focus("resume"); focus("stop"); focus("timeout") }
                        "NotificationRepro" -> notificationTiming("repro")
                        "NotificationStartup" -> notificationTiming("startup")
                        "NotificationBefore" -> notificationTiming("before")
                        "NotificationLate" -> notificationTiming("late")
                        "PauseDisconnect" -> focus("disconnect")
                        "StopDuring" -> activeChange(true)
                        "RulesDuring" -> activeChange(false)
                        "TtsFailures" -> { controlledFailure(ControlledFailure.INIT); controlledFailure(ControlledFailure.SYNTHESIS) }
                        "FocusFailure" -> controlledFailure(ControlledFailure.FOCUS_REQUEST)
                        "FocusPermanent" -> permanentFocusLoss()
                        "BluetoothPermission" -> permissionRevoked()
                        "DisconnectDuring" -> manualRouteChange(true)
                        "RouteDuring" -> manualRouteChange(false)
                        "Multipart" -> {
                            val parts = listOf("【测试平台】验证码：429", "317，请勿泄露。")
                            assertTrue("Business assembly only", parts.joinToString("") == DebugSmsFixtures.OTP)
                            message("D14", DebugSmsFixtures.OTP, true, true, parts = parts)
                        }
                        else -> error("Unknown fixture ID")
                    }
                } catch (_: Throwable) {
                    main { app.session?.cancel() }
                    assertTrue("Failed case cleanup must finish", waitUntil(1000) { idle() })
                    emit(case, "FAIL", "INSTRUMENTATION_ASSERTION_FAILED;NO_RAW_INPUT_LOGGED")
                }
            }
        } finally {
            main {
                app.session?.cancel()
                app.settings.setOtpOnly(true); app.settings.setSimplify(original.otpSimplifyEnabled)
                app.settings.setOtpOnly(original.otpOnlyEnabled)
                app.setAnnouncingEnabled(original.announcingEnabled)
            }
            assertTrue("Original settings must be preserved", original == main { app.settings.snapshot() })
            assertTrue("No audio session may outlive instrumentation", idle())
        }
        assertEquals("See structured SVA_DEBUG_RESULT failures", 0, failures)
    }
}
