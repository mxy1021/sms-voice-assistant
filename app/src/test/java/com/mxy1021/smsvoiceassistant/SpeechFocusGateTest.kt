package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.audio.*
import com.mxy1021.smsvoiceassistant.sms.PcmBudget
import org.junit.Assert.*
import org.junit.Test

class SpeechFocusGateTest {
    @Test fun notificationInterruptsThenResumesTheSameSession() {
        val gate = SpeechFocusGate()
        gate.granted(); assertTrue(gate.held)
        assertEquals(SpeechFocusAction.PAUSE, gate.change(-3))
        assertFalse(gate.held)
        assertEquals(SpeechFocusAction.RESUME, gate.change(1))
        assertTrue(gate.held)
        assertEquals(SpeechFocusAction.NONE, gate.change(1))
    }
    @Test fun repeatedTransientInterruptionsDoNotBecomePermanentLoss() {
        val gate = SpeechFocusGate(); gate.granted()
        assertEquals(SpeechFocusAction.PAUSE, gate.change(-2))
        assertEquals(SpeechFocusAction.PAUSE, gate.change(-3))
        assertEquals(SpeechFocusAction.RESUME, gate.change(1))
    }
    @Test fun permanentLossCannotBeRevivedByLateGain() {
        val gate = SpeechFocusGate(); gate.granted()
        assertEquals(SpeechFocusAction.STOP, gate.change(-1))
        assertEquals(SpeechFocusAction.NONE, gate.change(1))
        assertFalse(gate.held)
    }
    @Test fun stopOrTimeoutWhilePausedCannotBeRevived() {
        val gate = SpeechFocusGate(); gate.granted(); gate.change(-3); gate.end()
        assertEquals(SpeechFocusAction.NONE, gate.change(1))
        assertFalse(gate.held)
    }
    @Test fun remainingBudgetIncludesInterruptionAndUnplayedAudio() {
        assertTrue(PcmBudget.remainingFits(3000, 2000))
        assertFalse(PcmBudget.remainingFits(3000, 4300))
        assertFalse(PcmBudget.remainingFits(1000, 7000))
        assertFalse(PcmBudget.remainingFits(-1, 100))
        assertTrue(PcmBudget.remainingFits(5100, 1000)) // Includes the silent route probe.
    }
}
