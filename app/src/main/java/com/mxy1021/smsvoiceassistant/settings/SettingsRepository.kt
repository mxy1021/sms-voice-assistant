package com.mxy1021.smsvoiceassistant.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One source of truth; legacy target remains at its original key. No message data. */
interface SettingsStore {
    fun boolean(key: String, default: Boolean): Boolean
    fun string(key: String): String?
    fun int(key: String, default: Int): Int
    fun write(values: Map<String, Any?>)
}

data class AppSettings(
    val announcingEnabled: Boolean = false,
    val otpOnlyEnabled: Boolean = true,
    val otpSimplifyEnabled: Boolean = true,
    val bootRestoreEnabled: Boolean = false,
    val selectedBluetoothDevice: String? = null,
    val setupCompleted: Boolean = false
)

data class SpeechPermit(val generation: Long, val fromSms: Boolean)

class SettingsRepository(private val store: SettingsStore, bootCount: Int) {
    private val lock = Any()
    private var generation = 0L
    private val mutable: MutableStateFlow<AppSettings>
    val state get() = mutable.asStateFlow()

    init {
        var enabled = store.boolean("announcingEnabled", false)
        val restore = store.boolean("bootRestoreEnabled", false)
        val lastBoot = store.int("stage1BootCount", -1)
        // Detect a reboot even when OEM delays BOOT_COMPLETED until after an SMS.
        if (bootCount >= 0 && lastBoot != bootCount && !restore) enabled = false
        mutable = MutableStateFlow(AppSettings(enabled,
            store.boolean("otpOnlyEnabled", true), store.boolean("otpSimplifyEnabled", true),
            restore, store.string("target"), store.boolean("setupCompleted", false)))
        store.write(mapOf("settingsVersion" to 1, "announcingEnabled" to enabled,
            "stage1BootCount" to bootCount))
    }

    fun snapshot(): AppSettings = synchronized(lock) { mutable.value }
    private fun update(change: (AppSettings) -> AppSettings) = synchronized(lock) {
        val next = change(mutable.value)
        if (next == mutable.value) return@synchronized
        generation++ // In-flight text is never played under a different configuration.
        store.write(mapOf("announcingEnabled" to next.announcingEnabled,
            "otpOnlyEnabled" to next.otpOnlyEnabled, "otpSimplifyEnabled" to next.otpSimplifyEnabled,
            "bootRestoreEnabled" to next.bootRestoreEnabled, "target" to next.selectedBluetoothDevice,
            "setupCompleted" to next.setupCompleted))
        mutable.value = next
    }
    fun setEnabled(value: Boolean) = update { it.copy(announcingEnabled = value) }
    fun setOtpOnly(value: Boolean) = update { it.copy(otpOnlyEnabled = value) }
    fun setSimplify(value: Boolean) = update { if (it.otpOnlyEnabled) it.copy(otpSimplifyEnabled = value) else it }
    fun setBootRestore(value: Boolean) = update { it.copy(bootRestoreEnabled = value) }
    fun selectDevice(value: String?) = update { it.copy(selectedBluetoothDevice = value, setupCompleted = value != null) }
    fun onBoot() { if (!snapshot().bootRestoreEnabled) setEnabled(false) }
    fun permit(fromSms: Boolean): SpeechPermit? = synchronized(lock) {
        if (fromSms && !mutable.value.announcingEnabled) null else SpeechPermit(generation, fromSms)
    }
    fun valid(permit: SpeechPermit): Boolean = synchronized(lock) { validLocked(permit) }
    private fun validLocked(permit: SpeechPermit) = permit.generation == generation &&
        (!permit.fromSms || mutable.value.announcingEnabled)

    /** Serialize a NON_BLOCKING PCM write with settings changes; no writes after stop returns. */
    fun <T> withPermit(permit: SpeechPermit, write: () -> T): T? = synchronized(lock) {
        if (validLocked(permit)) write() else null
    }
}
