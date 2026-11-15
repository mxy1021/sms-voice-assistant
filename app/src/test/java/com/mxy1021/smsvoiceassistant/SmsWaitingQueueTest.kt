package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.sms.SmsWaitingQueue
import org.junit.Assert.*
import org.junit.Test

class SmsWaitingQueueTest {
    @Test fun otpPriorityAndReceptionOrderAreIndependentOfLookupCompletionOrder() {
        val q = SmsWaitingQueue<String>(5)
        q.offer("normal2", false, 2); q.offer("otp4", true, 4)
        q.offer("otp3", true, 3); q.offer("normal1", false, 1)
        assertEquals(listOf("otp3", "otp4", "normal1", "normal2"), List(4) { q.poll() })
        assertNull(q.poll())
    }
    @Test fun fullQueueRejectsNewWithoutOverwritingAnyWaitingMessage() {
        val q = SmsWaitingQueue<String>(3)
        repeat(3) { assertTrue(q.offer("$it", false, it.toLong())) }
        assertFalse(q.offer("newOtp", true, 4))
        assertEquals(listOf("0", "1", "2"), List(3) { q.poll() })
    }
    @Test fun expiredOrCancelledEntriesNeverReappear() {
        val q = SmsWaitingQueue<String>(5)
        q.offer("expired", true, 0); q.offer("cancelled", false, 1)
        q.remove("expired")
        assertEquals(listOf("cancelled"), q.drain()); assertNull(q.poll())
        assertTrue(q.offer("fresh", false, 2)); assertEquals("fresh", q.poll())
    }
}
