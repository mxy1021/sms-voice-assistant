package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.settings.AppSettings
import com.mxy1021.smsvoiceassistant.sms.SpeechFormatter
import org.junit.Assert.*
import org.junit.Test

class UnknownContactSpeechTest {
    private val full = AppSettings(announcingEnabled=true,otpOnlyEnabled=false,otpSimplifyEnabled=false)
    @Test fun allUnavailableOrUnsafeNamesUseUnknownContactWithoutLeakingNumber() {
        listOf(null,""," ","13800138000","备注138.0013.8000","https://example.com").forEach { name ->
            assertEquals("未知联系人，晚上七点见。",SpeechFormatter.forPlayback("晚上七点见。",full,name).text)
        }
        assertEquals("张三，晚上七点见。",SpeechFormatter.forPlayback("晚上七点见。",full,"张三").text)
    }
    @Test fun allFourRulesKeepCompactFormatAndOrdinaryFiltering() {
        listOf(true,false).forEach { only -> listOf(true,false).forEach { compact ->
            val settings = full.copy(otpOnlyEnabled=only,otpSimplifyEnabled=compact)
            assertEquals(if(compact) "您收到的验证码为零零九八一。" else "未知联系人，验证码零零九八一。",
                SpeechFormatter.forPlayback("验证码00981。",settings).text)
            assertEquals(if(only) null else "未知联系人，晚上七点见。",
                SpeechFormatter.forPlayback("晚上七点见。",settings).text)
        } }
    }
    @Test fun prefixCountsTowardFullLimitWithoutPrematurelyRejectingKnownName() {
        assertEquals(80,SpeechFormatter.forPlayback("字".repeat(74),full).text!!.length)
        assertNull(SpeechFormatter.forPlayback("字".repeat(75),full).text)
        assertNotNull(SpeechFormatter.choose("字".repeat(77),full).text)
        assertEquals(80,SpeechFormatter.forPlayback("字".repeat(77),full,"张三").text!!.length)
    }
    @Test fun prefixCannotResurrectEmptyFilteredOrDisabledText() {
        assertNull(SpeechFormatter.forPlayback("https://example.com/a",full).text)
        assertNull(SpeechFormatter.forPlayback("晚上见。",full.copy(announcingEnabled=false)).text)
        assertEquals("未知联系人，金额25元，11月15日，取件码A-123。",
            SpeechFormatter.forPlayback("金额25元，11月15日，取件码A-123。详情：https://example.com/a",full).text)
    }
}
