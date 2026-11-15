package com.mxy1021.smsvoiceassistant.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mxy1021.smsvoiceassistant.audio.SpeechGain

enum class DisplayMode(val code: Int, val label: String) {
    SYSTEM(0, "跟随系统"), LIGHT(1, "普通模式（白色）"), DARK(2, "深色模式（黑色）");
    fun dark(systemDark: Boolean) = when (this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }
    companion object { fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: SYSTEM }
}

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
    val setupCompleted: Boolean = false,
    val speechGainDb: Int = 0,
    val displayMode: DisplayMode = DisplayMode.SYSTEM
)

data class SpeechPermit(val generation: Long, val fromSms: Boolean)

class SettingsRepository(private val store: SettingsStore, private val bootCount: Int) {
    private val lock = Any()
    private var generation = 0L
    private val mutable: MutableStateFlow<AppSettings>
    val state get() = mutable.asStateFlow()

    init {
        var enabled = store.boolean("announcingEnabled", false)
        val restore = store.boolean("bootRestoreEnabled", false)
        val lastBoot = store.int("stage1BootCount", -1)
        val storedGain = store.int("speechGainDb", 0)
        val restoredGain = SpeechGain.restored(storedGain)
        // Detect a reboot even when OEM delays BOOT_COMPLETED until after an SMS.
        if (bootCount >= 0 && lastBoot != bootCount && !restore) enabled = false
        mutable = MutableStateFlow(AppSettings(enabled,
            store.boolean("otpOnlyEnabled", true), store.boolean("otpSimplifyEnabled", true),
            restore, store.string("target"), store.boolean("setupCompleted", false),
            restoredGain,
            DisplayMode.fromCode(store.int("displayMode", 0))))
        store.write(mapOf("settingsVersion" to 1, "announcingEnabled" to enabled,
            "stage1BootCount" to bootCount))
        if (restoredGain != storedGain) store.write(mapOf("speechGainDb" to restoredGain))
    }

    fun snapshot(): AppSettings = synchronized(lock) { mutable.value }
    private fun update(invalidateSpeech: Boolean = true, change: (AppSettings) -> AppSettings) = synchronized(lock) {
        val next = change(mutable.value)
        if (next == mutable.value) return@synchronized
        if (invalidateSpeech) generation++ // Display-only changes do not cancel SMS/audio.
        store.write(mapOf("announcingEnabled" to next.announcingEnabled,
            "otpOnlyEnabled" to next.otpOnlyEnabled, "otpSimplifyEnabled" to next.otpSimplifyEnabled,
            "bootRestoreEnabled" to next.bootRestoreEnabled, "target" to next.selectedBluetoothDevice,
            "setupCompleted" to next.setupCompleted, "speechGainDb" to next.speechGainDb,
            "displayMode" to next.displayMode.code))
        mutable.value = next
    }
    fun setEnabled(value: Boolean) = update { it.copy(announcingEnabled = value,
        setupCompleted = it.setupCompleted || (value && it.selectedBluetoothDevice != null)) }
    fun setOtpOnly(value: Boolean) = update { it.copy(otpOnlyEnabled = value) }
    fun setSimplify(value: Boolean) = update { it.copy(otpSimplifyEnabled = value) }
    fun setBootRestore(value: Boolean) = update { it.copy(bootRestoreEnabled = value) }
    fun setSpeechGain(value: Int) {
        require(value in SpeechGain.levels)
        update { it.copy(speechGainDb = value) }
    }
    fun setDisplayMode(value: DisplayMode) = update(invalidateSpeech = false) { it.copy(displayMode = value) }
    fun selectDevice(value: String?) = update { it.copy(selectedBluetoothDevice = value, setupCompleted = value != null) }
    fun onBoot() {
        // For known boot count, init already reconciled this boot before any receiver/UI.
        // Do not pause a manual enable performed after init but before a delayed boot broadcast.
        if (bootCount < 0 && !snapshot().bootRestoreEnabled) setEnabled(false)
    }
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
