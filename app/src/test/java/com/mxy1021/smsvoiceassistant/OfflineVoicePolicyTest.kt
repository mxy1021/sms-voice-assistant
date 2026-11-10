package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.audio.OfflineVoicePolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class OfflineVoicePolicyTest {
    @Test fun acceptsDeviceSimplifiedScriptWithoutCountry() {
        assertTrue(OfflineVoicePolicy.accepts(Locale.forLanguageTag("zh-Hans"), false, false))
        assertTrue(OfflineVoicePolicy.accepts(Locale.SIMPLIFIED_CHINESE, false, false))
    }
    @Test fun noNetworkOrUnknownInstallationFallback() {
        val locale = Locale.forLanguageTag("zh-Hans")
        assertFalse(OfflineVoicePolicy.accepts(locale, true, false))
        assertFalse(OfflineVoicePolicy.accepts(locale, null, false))
        assertFalse(OfflineVoicePolicy.accepts(locale, false, null))
        assertFalse(OfflineVoicePolicy.accepts(locale, false, true))
    }
    @Test fun rejectsTraditionalAndOtherLanguages() {
        assertFalse(OfflineVoicePolicy.accepts(Locale.forLanguageTag("zh-Hant"), false, false))
        assertFalse(OfflineVoicePolicy.accepts(Locale.TRADITIONAL_CHINESE, false, false))
        assertFalse(OfflineVoicePolicy.accepts(Locale.ENGLISH, false, false))
    }
}
