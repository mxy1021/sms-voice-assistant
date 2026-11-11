package com.mxy1021.smsvoiceassistant.bluetooth

/** Main-thread lifecycle gate. Never seed Android's audio-port cache without Bluetooth permission. */
class ForegroundAudioAccess(
    private val permitted: () -> Boolean,
    private val register: () -> Unit,
    private val unregister: () -> Unit,
) {
    data class Request(val sequence: Long, val target: String?, val permitted: Boolean)
    private var active = false
    private var registered = false
    private var sequence = 0L

    fun start() { active = true }
    fun stop() {
        active = false
        sequence++
        removeListener()
    }
    fun begin(target: String?): Request? {
        if (!active) return null
        val allowed = permitted()
        if (!allowed) removeListener()
        else if (!registered) {
            register()
            registered = true
        }
        return Request(++sequence, target, allowed)
    }
    fun accepts(request: Request, target: String?): Boolean =
        active && request.sequence == sequence && request.target == target && request.permitted && permitted()

    private fun removeListener() {
        if (registered) {
            unregister()
            registered = false
        }
    }
}
