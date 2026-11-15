package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.sms.SmsSafetyWindow
import org.junit.Assert.*
import org.junit.Test

class SmsSafetyWindowTest {
    @Test fun existingCleanupReserveOnlyEndsUnplayableOrPausedWork() {
        val start = 1000L
        val boundary = start + SmsSafetyWindow.SESSION_BUDGET_MS - 700
        assertFalse(SmsSafetyWindow.shouldReserveCleanup(start, boundary - 1, false, false, false))
        assertTrue(SmsSafetyWindow.shouldReserveCleanup(start, boundary, false, false, false))
        assertTrue(SmsSafetyWindow.shouldReserveCleanup(start, boundary, true, false, false))
        assertFalse(SmsSafetyWindow.shouldReserveCleanup(start, start - 1, false, false, false))
    }
    @Test fun reserveCannotTruncateActiveAudioOrReopenCompletedWork() {
        val start = 1000L
        for (elapsed in 7300L..7999L step 100) {
            assertFalse(SmsSafetyWindow.shouldReserveCleanup(start, start + elapsed, true, true, false))
            assertFalse(SmsSafetyWindow.shouldReserveCleanup(start, start + elapsed, false, false, true))
        }
    }
    @Test fun oldOrInvalidPduCannotStartSpeech() {
        assertTrue(SmsSafetyWindow.freshPdu(listOf(1000, 2000)))
        assertFalse(SmsSafetyWindow.freshPdu(listOf(1000, 120001)))
        assertFalse(SmsSafetyWindow.freshPdu(listOf(-1)))
        assertFalse(SmsSafetyWindow.freshPdu(emptyList()))
    }
    @Test fun elapsedDeadlineSurvivesDelayedHandlerExecution() {
        assertTrue(SmsSafetyWindow.withinBudget(1000, 1000 + SmsSafetyWindow.SESSION_BUDGET_MS - 1))
        assertFalse(SmsSafetyWindow.withinBudget(1000, 1000 + SmsSafetyWindow.SESSION_BUDGET_MS))
        assertFalse(SmsSafetyWindow.withinBudget(1000, 300000))
        assertFalse(SmsSafetyWindow.withinBudget(1000, 999))
    }
}
