package com.mxy1021.smsvoiceassistant.sms

/** Pending events only. The running event is never preempted. No retry or persistence. */
class SmsWaitingQueue<T>(private val capacity: Int) {
    init { require(capacity > 0) }
    private data class Entry<T>(val value: T, val otp: Boolean, val sequence: Long)
    private val entries = mutableListOf<Entry<T>>()
    fun offer(value: T, otp: Boolean, sequence: Long): Boolean {
        if (entries.size >= capacity) return false
        entries.add(Entry(value, otp, sequence))
        return true
    }
    fun poll(): T? {
        val next = entries.minWithOrNull(compareByDescending<Entry<T>> { it.otp }.thenBy { it.sequence }) ?: return null
        entries.remove(next)
        return next.value
    }
    fun remove(value: T) { entries.removeAll { it.value == value } }
    fun drain(): List<T> = entries.map { it.value }.also { entries.clear() }
}
