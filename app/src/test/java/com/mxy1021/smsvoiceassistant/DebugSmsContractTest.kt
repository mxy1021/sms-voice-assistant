package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.settings.AppSettings
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Test

class DebugSmsContractTest {
    private val on = AppSettings(announcingEnabled = true)
    @Test fun suppliedOtpPlatformAndCode() {
        val body = "【测试平台】验证码：429317，请勿泄露。"
        assertEquals("您收到的测试平台验证码为四二九三一七。", SpeechFormatter.choose(body, on).text)
    }
    @Test fun suppliedOtpFullRetainsExpiryAndWarning() {
        val body = "【测试平台】您的验证码为429317，有效期五分钟，请勿告知他人。"
        assertEquals(body, SpeechFormatter.choose(body, on.copy(otpSimplifyEnabled = false)).text)
    }
    @Test fun suppliedNormalAndOrderAreNotOtp() {
        listOf("今天下午三点开会，请记得带上资料。", "您的订单金额为129元，预计18点送达，订单号202611110001。").forEach {
            assertNull(SpeechFormatter.choose(it, on).text)
            assertEquals(it, SpeechFormatter.choose(it, on.copy(otpOnlyEnabled = false)).text)
        }
    }
    @Test fun contextualCodeDoesNotPickFirstNumber() {
        val otp = OtpExtractor.extract("【测试平台】您于18点申请验证码429317，有效期5分钟。")!!
        assertEquals("429317", otp.code)
    }
    @Test fun suppliedMixedCodePreservesCaseInSpelling() {
        val text = SpeechFormatter.choose("【测试平台】验证码为A7b29C，请勿泄露。", on).text!!
        assertEquals("您收到的测试平台验证码为大写字母A，七，小写字母B，二，九，大写字母C。", text)
    }
    @Test fun fullTextLimitCountsUtf16UnitsNotUnicodeCodePoints() {
        val body = "😀".repeat(40)
        assertEquals(80, body.length)
        assertEquals(body, SpeechFormatter.choose(body, on.copy(otpOnlyEnabled = false)).text)
        assertNull(SpeechFormatter.choose(body + "。", on.copy(otpOnlyEnabled = false)).text)
    }
    @Test fun validCharacterCountCannotOverrideActualPcmLimits() {
        assertNull(PcmBudget.durationMs(PcmBudget.MAX_BYTES + 2, 24000, 1))
        val sixSeconds = PcmBudget.durationMs(288000, 24000, 1)!!
        assertEquals(6000L, sixSeconds)
        assertFalse(PcmBudget.fits(sixSeconds, 100))
    }
}
