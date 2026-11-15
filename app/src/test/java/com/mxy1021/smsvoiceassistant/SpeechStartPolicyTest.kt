package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.audio.SpeechStartPolicy
import com.mxy1021.smsvoiceassistant.sms.PcmBudget
import com.mxy1021.smsvoiceassistant.sms.SmsSafetyWindow
import org.junit.Assert.*
import org.junit.Test

class SpeechStartPolicyTest {
    @Test fun manualVoiceTestDoesNotWaitForAnSmsNotification() {
        assertEquals(0L, SpeechStartPolicy.delayMs(false, 1000, 1000))
    }
    @Test fun smsSynthesisRunsInsideTheWindowWithoutAddingASecondFullDelay() {
        assertEquals(1800L, SpeechStartPolicy.delayMs(true, 1000, 1000))
        assertEquals(1600L, SpeechStartPolicy.delayMs(true, 1000, 1200))
        assertEquals(1L, SpeechStartPolicy.delayMs(true, 1000, 2799))
    }
    @Test fun slowSynthesisAndRepeatedQueriesNeverRestartTheWindow() {
        assertEquals(0L, SpeechStartPolicy.delayMs(true, 1000, 2800))
        assertEquals(0L, SpeechStartPolicy.delayMs(true, 1000, 5000))
    }
    @Test fun invalidClockDoesNotCreateAnUnboundedDelay() {
        assertEquals(0L, SpeechStartPolicy.delayMs(true, 1000, 999))
    }
    @Test fun observedDelayedStartupFallsInsideSingleBoundedWindow() {
        assertEquals(365L, SpeechStartPolicy.delayMs(true, 1000, 2435))
        assertEquals(0L, SpeechStartPolicy.delayMs(true, 1000, 2800))
        assertEquals(8000L, SmsSafetyWindow.SESSION_BUDGET_MS)
    }
    @Test fun windowStillAllowsMaximumPcmWithinUnchangedBudgetWhenSetupIsFast() {
        assertEquals(8000L, SmsSafetyWindow.SESSION_BUDGET_MS)
        assertTrue(PcmBudget.fits(5000, SpeechStartPolicy.SMS_INITIAL_WINDOW_MS + 250))
        assertFalse(PcmBudget.fits(5000, 2300))
    }
    @Test fun waitingNeverMakesOldSessionBudgetFreshAgain() {
        val received = 1000L
        val now = received + SmsSafetyWindow.SESSION_BUDGET_MS
        assertEquals(0L, SpeechStartPolicy.delayMs(true, received, now))
        assertFalse(SmsSafetyWindow.withinBudget(received, now))
    }
}
