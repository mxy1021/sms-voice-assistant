package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.audio.TtsCompatibilityPolicy as Policy
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TtsCompatibilityPolicyTest {
    @Test fun simplifiedLocalesNeedNotHaveChinaCountry() {
        assertTrue(Policy.simplifiedCandidate(Locale.SIMPLIFIED_CHINESE))
        assertTrue(Policy.simplifiedCandidate(Locale.forLanguageTag("zh-Hans")))
        assertTrue(Policy.simplifiedCandidate(Locale.forLanguageTag("zh-Hans-SG")))
        assertTrue(Policy.simplifiedCandidate(Locale.CHINESE))
        assertTrue(Policy.simplifiedCandidate(Locale.forLanguageTag("cmn-Hans-CN")))
        assertFalse(Policy.simplifiedCandidate(Locale.TRADITIONAL_CHINESE))
        assertFalse(Policy.simplifiedCandidate(Locale.forLanguageTag("zh-Hant")))
        assertFalse(Policy.simplifiedCandidate(Locale.ENGLISH))
    }
    @Test fun unknownMetadataDoesNotBecomeOfflineEvidence() {
        assertTrue(Policy.explicitlyOffline(false, false))
        assertFalse(Policy.explicitlyOffline(null, false))
        assertFalse(Policy.explicitlyOffline(false, null))
        assertFalse(Policy.explicitlyOffline(true, false))
        assertFalse(Policy.explicitlyOffline(false, true))
    }
    @Test fun manualFallbackStillRequiresInitializedChineseLanguage() {
        assertTrue(Policy.mayManuallySpeak(0, 0))
        assertTrue(Policy.mayManuallySpeak(0, 1))
        assertTrue(Policy.mayManuallySpeak(0, 2))
        assertFalse(Policy.mayManuallySpeak(null, 0))
        assertFalse(Policy.mayManuallySpeak(-1, 0))
        assertFalse(Policy.mayManuallySpeak(0, null))
        assertFalse(Policy.mayManuallySpeak(0, -1))
        assertFalse(Policy.mayManuallySpeak(0, -2))
        assertFalse(Policy.mayManuallySpeak(0, 3))
    }
}
