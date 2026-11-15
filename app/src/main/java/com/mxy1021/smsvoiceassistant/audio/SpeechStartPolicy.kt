package com.mxy1021.smsvoiceassistant.audio

/** Let SMS notification dispatch start before our first focus request, while TTS runs in parallel.
 * All parsed SMS sources use the same rule. This is not a notification detector or a retry policy.
 * The original receive-time deadline continues to run throughout this one bounded window.
 */
object SpeechStartPolicy {
    // A locked/idle device delivered a competing focus request at receive +1435ms.
    const val SMS_INITIAL_WINDOW_MS = 1800L
    fun delayMs(fromSms: Boolean, receivedElapsed: Long, nowElapsed: Long): Long {
        if (!fromSms || nowElapsed < receivedElapsed) return 0
        return (SMS_INITIAL_WINDOW_MS - (nowElapsed - receivedElapsed)).coerceAtLeast(0)
    }
}
