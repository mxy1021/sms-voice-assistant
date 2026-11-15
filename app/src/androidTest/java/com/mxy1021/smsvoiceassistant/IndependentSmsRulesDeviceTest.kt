package com.mxy1021.smsvoiceassistant

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mxy1021.smsvoiceassistant.audio.SpeechResult
import com.mxy1021.smsvoiceassistant.settings.*
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Business selection uses a controlled no-audio boundary; RuleMatrix separately tests real PCM. */
@RunWith(AndroidJUnit4::class)
class IndependentSmsRulesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ProbeApplication
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun combination(only: Boolean, simplify: Boolean) {
        assumeTrue("Keep real enabled/permission state", main { app.smsCoordinator.preflight() == null })
        val original = main { app.settings.snapshot() }
        try {
            main { app.settings.setOtpOnly(only); app.settings.setSimplify(simplify) }
            listOf(DebugSmsFixtures.SHORT_OTP, DebugSmsFixtures.SHORT).forEachIndexed { index, body ->
                val expected = if (index == 0) {
                    if (simplify) "您收到的验证码为一二三四。" else body
                } else if (only) null else body
                var calls = 0
                val coordinator = SmsCoordinator(app) { text, _, _, done ->
                    calls++
                    assertEquals(expected, text)
                    done(SpeechResult(false, true, "CONTROLLED_NO_AUDIO", "CONTROLLED_RULE_SELECTION"))
                }
                val terminal = CompletableFuture<SmsResult>()
                main {
                    val now = System.currentTimeMillis()
                    coordinator.submit(listOf(SmsPart("synthetic-rules-${UUID.randomUUID()}", now, body)),
                        SystemClock.elapsedRealtime(), now, SmsSource.DEBUG_SIMULATION) { terminal.complete(it) }
                }
                val result = terminal.get(3, TimeUnit.SECONDS)
                assertEquals(SmsSource.DEBUG_SIMULATION, result.source)
                assertEquals(if (expected == null) 0 else 1, calls)
                assertEquals(if (expected == null) SmsDisposition.IGNORED else SmsDisposition.INCOMPLETE, result.disposition)
                assertNull(main { app.session })
            }
        } finally {
            main { app.settings.setOtpOnly(original.otpOnlyEnabled); app.settings.setSimplify(original.otpSimplifyEnabled) }
            assertEquals(original, main { app.settings.snapshot() })
        }
    }

    @Test fun s01OnlyAndSimplifyOn() = combination(true, true)
    @Test fun s02OnlyOnSimplifyOff() = combination(true, false)
    @Test fun s03OnlyOffSimplifyOn() = combination(false, true)
    @Test fun s04BothOff() = combination(false, false)

    private fun isolatedPreferences(block: (android.content.SharedPreferences) -> Unit) {
        val before = main { app.settings.snapshot() }
        val name = "independent-rules-${UUID.randomUUID()}"
        try { block(app.getSharedPreferences(name, Context.MODE_PRIVATE)) }
        finally {
            app.deleteSharedPreferences(name)
            assertEquals(before, main { app.settings.snapshot() })
        }
    }

    @Test fun freshInstallRuleDefaultsAreBothOn() = isolatedPreferences { prefs ->
        val actual = SettingsRepository(PreferenceSettingsStore(prefs), 7).snapshot()
        assertTrue(actual.otpOnlyEnabled); assertTrue(actual.otpSimplifyEnabled)
        assertFalse(actual.announcingEnabled)
    }

    @Test fun v1PreferencesReloadAllFourCombinationsWithoutMigration() = isolatedPreferences { prefs ->
        listOf(true, false).forEach { only -> listOf(true, false).forEach { simplify ->
            assertTrue(prefs.edit().putBoolean("otpOnlyEnabled", only).putBoolean("otpSimplifyEnabled", simplify)
                .putString("target", "synthetic-v1-target").putBoolean("announcingEnabled", true)
                .putBoolean("bootRestoreEnabled", true).putBoolean("setupCompleted", true)
                .putInt("stage1BootCount", 7).putInt("settingsVersion", 1).putInt("focusMode", 3).commit())
            val original = prefs.all.toMap()
            val settings = SettingsRepository(PreferenceSettingsStore(prefs), 7).snapshot()
            assertEquals(only, settings.otpOnlyEnabled); assertEquals(simplify, settings.otpSimplifyEnabled)
            assertTrue(settings.announcingEnabled); assertTrue(settings.bootRestoreEnabled)
            assertEquals("synthetic-v1-target", settings.selectedBluetoothDevice)
            assertEquals(original, prefs.all)
        } }
    }

    @Test fun independentSettersPersistWithRealSharedPreferences() = isolatedPreferences { prefs ->
        val repository = SettingsRepository(PreferenceSettingsStore(prefs), 7)
        repository.selectDevice("synthetic-v1-target"); repository.setBootRestore(true)
        repeat(40) { index ->
            val only = index % 2 == 0
            val simplify = index % 3 == 0
            val previous = repository.snapshot().otpSimplifyEnabled
            repository.setOtpOnly(only)
            assertEquals(previous, repository.state.value.otpSimplifyEnabled)
            repository.setSimplify(simplify)
            assertEquals(only, repository.state.value.otpOnlyEnabled)
            assertEquals(simplify, prefs.getBoolean("otpSimplifyEnabled", !simplify))
            assertEquals(repository.snapshot(), SettingsRepository(PreferenceSettingsStore(prefs), 7).snapshot())
        }
        assertEquals("synthetic-v1-target", repository.snapshot().selectedBluetoothDevice)
    }
}
