package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.audio.RoutePolicy
import com.mxy1021.smsvoiceassistant.sms.OtpParser
import org.junit.Assert.*
import org.junit.Test

class Stage0Test {
    // 以下均为任务明确提供的人工样例，不来自真实短信。
    @Test fun singlePart() {
        assertEquals(OtpParser.TEST_TEXT, OtpParser.parse(listOf("【网易】验证码：429317，您正在登录网易手机账号。")))
    }
    @Test fun multipart() {
        assertEquals(OtpParser.TEST_TEXT, OtpParser.parse(listOf("【网易】验证码：429", "317，您正在登录网易手机账号。")))
    }
    @Test fun unsupportedAndAmbiguousMessagesAreRejected() {
        assertNull(OtpParser.parse(emptyList()))
        assertNull(OtpParser.parse(listOf("【其他】验证码：429317，您正在登录网易手机账号。")))
        assertNull(OtpParser.parse(listOf("【网易】验证码：4293179，您正在登录网易手机账号。")))
        assertNull(OtpParser.parse(listOf("【网易】验证码：429317，您正在登录网易手机账号。验证码：429317")))
        assertNull(OtpParser.parse(listOf("字".repeat(2049))))
    }
    @Test fun exactRouteRequired() {
        assertTrue(RoutePolicy.matches("TARGET", listOf("target")))
        assertFalse(RoutePolicy.matches(null, listOf("target")))
        assertFalse(RoutePolicy.matches("target", emptyList()))
        assertFalse(RoutePolicy.matches("target", listOf("")))
        assertFalse(RoutePolicy.matches("target", listOf("target", "speaker")))
        assertFalse(RoutePolicy.matches("target", listOf("alias")))
    }
}
