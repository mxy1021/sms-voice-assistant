package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.bluetooth.ForegroundAudioAccess
import org.junit.Assert.*
import org.junit.Test

/** Controlled permission boundary; these tests do not claim Android permission transitions. */
class ForegroundAudioAccessTest {
    private var allowed = true
    private var registrations = 0
    private var removals = 0
    private val access = ForegroundAudioAccess({ allowed }, { registrations++ }, { removals++ })
    private fun begin() = access.begin("target")!!

    @Test fun deniedStartupNeverQueriesAudioThroughListenerRegistration() {
        allowed = false; access.start()
        assertFalse(begin().permitted)
        assertEquals(0, registrations)
    }
    @Test fun regrantRegistersAndAllowsNewQueryWithoutRestart() {
        allowed = false; access.start(); val denied = begin()
        allowed = true; val restored = begin()
        assertEquals(1, registrations)
        assertFalse(access.accepts(denied, "target"))
        assertTrue(access.accepts(restored, "target"))
    }
    @Test fun revokeRemovesListenerAndRejectsOldResult() {
        access.start(); val old = begin(); allowed = false
        assertFalse(access.accepts(old, "target"))
        assertFalse(begin().permitted)
        assertEquals(1, removals)
    }
    @Test fun refreshAlwaysStartsNewRequestAndRejectsStaleRouteResult() {
        access.start(); val old = begin(); val fresh = begin()
        assertFalse(access.accepts(old, "target"))
        assertTrue(access.accepts(fresh, "target"))
        assertEquals(1, registrations)
    }
    @Test fun targetChangeCannotApplyOldDeviceResult() {
        access.start(); assertFalse(access.accepts(begin(), "other"))
    }
    @Test fun stoppedCallbacksCannotRegisterOrApplyResults() {
        access.start(); val old = begin(); access.stop()
        assertNull(access.begin("target"))
        assertFalse(access.accepts(old, "target"))
        access.stop(); assertEquals(1, removals)
    }
    @Test fun repeatedForegroundCyclesRegisterExactlyOncePerCycle() {
        repeat(6) {
            access.start(); begin(); begin(); access.start(); begin()
            access.stop(); access.stop()
        }
        assertEquals(6, registrations); assertEquals(6, removals)
    }
    @Test fun repeatedRevokeRegrantRejectsAllOldResults() {
        access.start()
        repeat(6) {
            val old = begin(); allowed = false; begin(); allowed = true
            assertTrue(access.accepts(begin(), "target"))
            assertFalse(access.accepts(old, "target"))
        }
        assertEquals(7, registrations); assertEquals(6, removals)
    }
}
