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
    private fun emit(id: String, status: String, evidence: String, result: SmsResult? = null) {
        if (status == "FAIL") failures++
        val json = JSONObject().put("schema", 1).put("source", "DEBUG_SIMULATION")
            .put("case", id).put("status", status).put("evidence", evidence)
            .put("assertions", if (status in setOf("PASS", "MANUAL_REQUIRED", "UNSUPPORTED")) "PASS" else status)
            .put("hearing", if (result?.speech?.playbackCompleted == true) "MANUAL_REQUIRED" else "NOT_RUN")
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
    private fun submit(text: String, parts: List<String> = listOf(text)): Submission {
        val future = CompletableFuture<SmsResult>()
        val id = main {
            val wall = System.currentTimeMillis()
            val sender = "synthetic-fixture-${++sequence}"
            app.smsCoordinator.submit(parts.map { SmsPart(sender, wall, it) }, SystemClock.elapsedRealtime(),
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
                main { audio.abandonAudioFocusRequest(contender) }; acquired = false
                Thread.sleep(100)
                assertTrue("$id must not revive after contender releases", idle())
                emit(id, "PASS", "REAL_FOCUS_PAUSE_THEN_${mode.uppercase()};NO_SESSION_REVIVAL", r)
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

    @Test fun runSelectedFixtures() {
        assertTrue("Only Debug target is supported", app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val original = main { app.settings.snapshot() }
        try {
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nSVA_DEBUG_READY\n") })
            assertTrue("Open the real launcher through ADB after readiness; device must be unlocked",
                waitUntil(15_000) { main { app.visible } })
            val selected = InstrumentationRegistry.getArguments().getString("fixture", "All")
            val cases = if (selected == "All") listOf("Logic", "Otp", "OtpFull", "Normal", "Numbers", "Mixed",
                "OtpAmbiguous", "Long80", "Long81", "LongPcm", "Rapid", "Disabled", "Disconnected", "Focus", "Multipart")
                else if (selected == "Long") listOf("Long80", "Long81", "LongPcm") else listOf(selected)
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
