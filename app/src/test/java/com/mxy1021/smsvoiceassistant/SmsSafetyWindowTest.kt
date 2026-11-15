package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.sms.SmsSafetyWindow
import org.junit.Assert.*
import org.junit.Test

class SmsSafetyWindowTest {
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
