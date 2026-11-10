package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.settings.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MemorySettingsStore(val data: MutableMap<String, Any?> = mutableMapOf()) : SettingsStore {
    override fun boolean(key: String, default: Boolean) = data[key] as? Boolean ?: default
    override fun string(key: String) = data[key] as? String
    override fun int(key: String, default: Int) = data[key] as? Int ?: default
    override fun write(values: Map<String, Any?>) { data.putAll(values) }
}

class SettingsRepositoryTest {
    @Test fun freshDefaults() {
        val settings = SettingsRepository(MemorySettingsStore(), 1).snapshot()
        assertEquals(AppSettings(), settings)
    }
    @Test fun upgradePreservesOnlyExistingConfigurationWithoutInventingEnabledState() {
        val store = MemorySettingsStore(mutableMapOf("target" to "synthetic-device", "focusMode" to 2))
        val repo = SettingsRepository(store, 1)
        assertEquals("synthetic-device", repo.snapshot().selectedBluetoothDevice)
        assertFalse(repo.snapshot().announcingEnabled)
        assertEquals(2, store.data["focusMode"]) // historical setting is not used by production
    }
    @Test fun recreationAndRuleDependency() {
        val store = MemorySettingsStore()
        val repo = SettingsRepository(store, 1)
        repo.setEnabled(true); repo.setSimplify(false); repo.setOtpOnly(false); repo.setSimplify(true)
        val restored = SettingsRepository(store, 1)
        assertTrue(restored.snapshot().announcingEnabled)
        assertFalse(restored.snapshot().otpSimplifyEnabled)
        restored.setOtpOnly(true)
        assertFalse(restored.snapshot().otpSimplifyEnabled)
    }
    @Test fun stopAndRestartInvalidateOldSmsAndManualPermits() {
        val repo = SettingsRepository(MemorySettingsStore(), 1)
        assertNull(repo.permit(true))
        repo.setEnabled(true)
        val permit = repo.permit(true)!!
        val manual = repo.permit(false)!!
        repo.setEnabled(false); repo.setEnabled(true)
        assertFalse(repo.valid(permit)); assertFalse(repo.valid(manual))
        var written = false
        repo.withPermit(permit) { written = true }
        assertFalse(written)
    }
    @Test fun concurrentStopSerializesWithWrite() {
        val repo = SettingsRepository(MemorySettingsStore(), 1)
        repo.setEnabled(true)
        val permit = repo.permit(true)!!
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writer = executor.submit { repo.withPermit(permit) { entered.countDown(); release.await(2, TimeUnit.SECONDS) } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val stop = executor.submit { repo.setEnabled(false) }
            release.countDown(); writer.get(3, TimeUnit.SECONDS); stop.get(3, TimeUnit.SECONDS)
            assertNull(repo.withPermit(permit) { "unexpected PCM" })
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun rebootWithoutRestorePausesBeforeAnyReceiver() {
        val store = MemorySettingsStore(); val repo = SettingsRepository(store, 3)
        repo.setEnabled(true)
        assertFalse(SettingsRepository(store, 4).snapshot().announcingEnabled)
    }
    @Test fun rebootRestorePreservesBothEnabledAndPaused() {
        val store = MemorySettingsStore(); val repo = SettingsRepository(store, 3)
        repo.setBootRestore(true); repo.setEnabled(true)
        val rebooted = SettingsRepository(store, 4)
        assertTrue(rebooted.snapshot().announcingEnabled)
        rebooted.onBoot(); assertTrue(rebooted.snapshot().announcingEnabled)
        rebooted.setEnabled(false)
        assertFalse(SettingsRepository(store, 5).snapshot().announcingEnabled)
    }
    @Test fun delayedBootBroadcastDoesNotUndoManualEnableAfterUnlock() {
        val store = MemorySettingsStore()
        val before = SettingsRepository(store, 1); before.setEnabled(true)
        val after = SettingsRepository(store, 2)
        assertFalse(after.snapshot().announcingEnabled)
        after.setEnabled(true); after.onBoot()
        assertTrue(after.snapshot().announcingEnabled)
    }
    @Test fun unknownBootCountUsesActualBootBroadcastWithoutPausingOnProcessRecreation() {
        val store = MemorySettingsStore()
        val repo = SettingsRepository(store, -1); repo.setEnabled(true)
        assertTrue(SettingsRepository(store, -1).snapshot().announcingEnabled)
        repo.onBoot(); assertFalse(repo.snapshot().announcingEnabled)
    }
}
