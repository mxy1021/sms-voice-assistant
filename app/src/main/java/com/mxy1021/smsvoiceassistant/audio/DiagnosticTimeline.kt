package com.mxy1021.smsvoiceassistant.audio

import kotlinx.coroutines.flow.MutableStateFlow

/** 仅接收脱敏阶段信息，不接收短信、设备身份或异常正文；最近 120 条只保留在内存。 */
class DiagnosticTimeline(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    val entries = MutableStateFlow<List<String>>(emptyList())
    private var origin = clock()
    private var sequence = 0
    @Synchronized fun reset() { origin = clock(); sequence = 0; entries.value = emptyList() }
    @Synchronized fun add(message: String) {
        sequence++
        entries.value = (entries.value + "$sequence · +${maxOf(0, clock() - origin)}ms · $message").takeLast(120)
    }
}
