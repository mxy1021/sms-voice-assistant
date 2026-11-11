package com.mxy1021.smsvoiceassistant

import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mxy1021.smsvoiceassistant.audio.SpeechResult
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import com.mxy1021.smsvoiceassistant.settings.PreferenceSettingsStore
import com.mxy1021.smsvoiceassistant.settings.SettingsRepository
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Instrumentation creates a fresh target process, NEVER an Activity or a fake SMS broadcast.
 * This proves initialization/business/cleanup only; it does not prove OEM broadcast delivery
 * or background execution privileges of a real SMS process. H07 must use a real reboot/SMS.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartInitializationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun noActivity() = main {
        assertFalse(app.visible)
        Stage.values().forEach { stage ->
            assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).isEmpty())
        }
    }
    private fun report(name: String, mechanism: String) {
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\nSVA_COLD_INIT PASS case=$name mechanism=$mechanism noActivity=true realSmsBroadcast=NOT_TESTED\n")
        })
    }
    private fun submit(coordinator: SmsCoordinator): SmsResult {
        val completed = CompletableFuture<SmsResult>()
        main {
            val now = System.currentTimeMillis()
            coordinator.submit(listOf(SmsPart("synthetic-cold-${UUID.randomUUID()}", now,
                DebugSmsFixtures.SHORT_OTP)), SystemClock.elapsedRealtime(), now,
                SmsSource.DEBUG_SIMULATION) { completed.complete(it) }
        }
        return completed.get(11, TimeUnit.SECONDS)
    }

    @Test fun dependenciesAndParsedBusinessInitializeWithoutMainActivity() {
        noActivity()
        assertEquals(Looper.getMainLooper(), app.main.looper)
        val original = main { app.settings.snapshot() }
        assertEquals(original.selectedBluetoothDevice, main { app.target })
        assumeTrue("Keep real enabled/permission state; do not bypass preflight", main { app.smsCoordinator.preflight() == null })
        var called = false
        val coordinator = SmsCoordinator(app) { _, background, _, result ->
            assertTrue(background); called = true
            result(SpeechResult(false, true, "CONTROLLED_NO_AUDIO", "CONTROLLED_BUSINESS_BOUNDARY"))
        }
        assertEquals(SmsDisposition.INCOMPLETE, submit(coordinator).disposition)
        assertTrue(called)
        assertEquals(original, main { app.settings.snapshot() })
        noActivity(); report("business", "PARSED_SIMULATION_CONTROLLED_NO_AUDIO")
    }

    private fun persistedBootState(restore: Boolean, enabled: Boolean, expected: Boolean) {
        noActivity()
        // A unique test-owned file only. Never edit/delete stage0 or the user's target/settings.
        val name = "cold-init-fixture-${UUID.randomUUID()}"
        val prefs = app.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        try {
            assertTrue(prefs.edit().putBoolean("announcingEnabled", enabled)
                .putBoolean("bootRestoreEnabled", restore).putInt("stage1BootCount", 10)
                .putString("target", "synthetic-cold-target").commit())
            val repo = SettingsRepository(PreferenceSettingsStore(prefs), 11) // No BootReceiver/onBoot call.
            assertEquals(expected, repo.snapshot().announcingEnabled)
            assertEquals("synthetic-cold-target", repo.snapshot().selectedBluetoothDevice)
            assertEquals(11, prefs.getInt("stage1BootCount", -1))
            assertEquals(expected, SettingsRepository(PreferenceSettingsStore(prefs), 11).snapshot().announcingEnabled)
        } finally { app.deleteSharedPreferences(name) }
        report("restore=$restore;enabled=$enabled", "REAL_PRIVATE_TEST_PREFERENCES_SYNTHETIC_BOOT_COUNT")
    }
    @Test fun restoreEnabledReadsSavedEnabledWithoutBootCallback() = persistedBootState(true, true, true)
    @Test fun restoreDisabledPersistsPauseBeforeAnyReceiver() = persistedBootState(false, true, false)
    @Test fun restoreEnabledPreservesSavedPause() = persistedBootState(true, false, false)

    @Test fun unpairedIdentityCannotProvideAnOutputWithoutActivity() {
        noActivity()
        val result = CompletableFuture<Boolean>()
        main { BluetoothProbe(app).inspect("synthetic-cold-unpaired") {
            result.complete(!it.connected && it.output == null)
        } }
        assertTrue(result.get(3, TimeUnit.SECONDS))
        assertNull(main { app.session })
        report("identityGate", "PLATFORM_PROBE_SYNTHETIC_UNPAIRED_TARGET_NO_AUDIO")
    }

    private fun failure(failure: ControlledFailure) {
        noActivity()
        assumeTrue("Require real enabled state and permissions", main { app.smsCoordinator.preflight() == null })
        val route = CompletableFuture<Boolean>()
        main { BluetoothProbe(app).inspect(app.target) { route.complete(it.output != null) } }
        assumeTrue("Require actual connected target/expected route", route.get(3, TimeUnit.SECONDS))
        val original = main { app.settings.snapshot() }
        val dependencies = ControlledSpeechDependencies(failure)
        val coordinator = SmsCoordinator(app) { text, background, id, result ->
            assertTrue(background)
            app.speak(text, background, fromSms = true, smsEventId = id, result = result, dependencies = dependencies)
        }
        try {
            val result = submit(coordinator)
            assertEquals(SmsDisposition.INCOMPLETE, result.disposition)
            val speech = result.speech!!
            assertEquals(0, speech.bytesWritten); assertFalse(speech.playbackCompleted)
            assertTrue(speech.focusReleased)
            assertNull(main { app.session }); assertFalse(main { app.speaking.value })
            assertEquals(1, dependencies.engine!!.shutdownCalls)
            assertTrue(dependencies.engine!!.stopCalls >= 1)
            val focusFailure = failure == ControlledFailure.FOCUS_REQUEST
            assertEquals(if (focusFailure) 1 else 0, dependencies.requests)
            assertEquals(if (focusFailure) 1 else 0, dependencies.abandons)
            assertTrue(speech.reason.contains(when (failure) {
                ControlledFailure.INIT -> "初始化失败"
                ControlledFailure.SYNTHESIS -> "合成失败"
                ControlledFailure.FOCUS_REQUEST -> "焦点申请失败"
            }))
            assertEquals(original, main { app.settings.snapshot() })
            noActivity(); report(failure.name, "CONTROLLED_SUBSTITUTE_NO_SYSTEM_FAILURE_CLAIM")
        } finally { main { app.session?.cancel() } }
    }
    @Test fun coldTtsInitFailureReleasesResources() = failure(ControlledFailure.INIT)
    @Test fun coldTtsSynthesisFailureReleasesResources() = failure(ControlledFailure.SYNTHESIS)
    @Test fun coldFocusRequestFailureReleasesResources() = failure(ControlledFailure.FOCUS_REQUEST)
}
