package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.settings.AppSettings
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Test

class AnnouncementRulesTest {
    private val enabled = AppSettings(announcingEnabled = true)
    @Test fun standardOtpAndPlatform() {
        val input = "【网易】验证码：429317，您正在登录网易手机账号（若非本人操作，请删除本短信）。"
        assertEquals("您收到的网易验证码为四二九三一七。", SpeechFormatter.choose(input, enabled).text)
    }
    @Test fun supportedLengthsAndContexts() {
        listOf("您的验证码为1234。", "验证码123456，请勿泄露。", "【示例】验证码：12345678。").forEach {
            assertNotNull(OtpDetector.detect(it))
        }
        listOf("验证码123", "验证码12345", "验证码1234567", "验证码123456789").forEach { assertNull(OtpDetector.detect(it)) }
    }
    @Test fun leadingZeroAndUnknownPlatform() {
        assertEquals("您收到的验证码为零一二三。", SpeechFormatter.choose("验证码0123。", enabled).text)
    }
    @Test fun lettersAreSpelledWithoutDroppingCharacters() {
        assertEquals("您收到的验证码为字母A，零，字母B，一。", SpeechFormatter.choose("验证码：a0B1。", enabled).text)
        assertNull(OtpDetector.detect("验证码ABCD。"))
    }
    @Test fun ordinarySmsWithManyNumbersIsNotOtp() {
        listOf("订单123456已发货，金额1234.56元，日期2026-11-11。",
            "请联系13800138000，订单123456。", "验证码有效期2026年，请重新申请。",
            "验证码：1234.56元", "验证码：2026年", "验证码：13800138000").forEach {
            assertNull(OtpDetector.detect(it)); assertNull(SpeechFormatter.choose(it, enabled).text)
        }
    }
    @Test fun unrelatedNumbersNeverReplaceContextualCode() {
        val otp = OtpDetector.detect("订单12345678，您的验证码为0123，请勿泄露。")!!
        assertEquals("0123", otp.code)
    }
    @Test fun ambiguityAndMissingCodeAreRejected() {
        listOf("验证码：1234，验证码：5678。", "验证码为，请稍候。", "验证码可能是1234或5678").forEach {
            assertNull(SpeechFormatter.choose(it, enabled).text)
        }
    }
    @Test fun otpFullTextPreservesAllCharactersAndSafetyReminder() {
        val body = "【示例】验证码：0123，非本人操作请忽略。"
        assertEquals(body, SpeechFormatter.choose(body, enabled.copy(otpSimplifyEnabled = false)).text)
        assertNull(SpeechFormatter.choose("明天开会。", enabled.copy(otpSimplifyEnabled = false)).text)
    }
    @Test fun allSmsModeAlwaysUsesUnmodifiedFullText() {
        listOf("明天14:30开会。", "验证码a0B1，请勿泄露。").forEach { text ->
            listOf(true, false).forEach { simplify ->
                assertEquals(text, SpeechFormatter.choose(text, enabled.copy(otpOnlyEnabled = false, otpSimplifyEnabled = simplify)).text)
            }
        }
    }
    @Test fun disabledOverridesEveryRuleCombination() {
        listOf(true, false).forEach { only -> listOf(true, false).forEach { simplify ->
            assertNull(SpeechFormatter.choose("验证码1234。", enabled.copy(announcingEnabled = false,
                otpOnlyEnabled = only, otpSimplifyEnabled = simplify)).text)
        } }
    }
    @Test fun fullTextNeverTruncates() {
        val full = enabled.copy(otpOnlyEnabled = false)
        assertEquals("字".repeat(80), SpeechFormatter.choose("字".repeat(80), full).text)
        assertNull(SpeechFormatter.choose("字".repeat(81), full).text)
        assertNull(SpeechFormatter.choose("验证码1234。" + "字".repeat(2048), enabled).text)
    }
    @Test fun multipartUsesFrameworkOrderAndRejectsMixedIdentityOrTime() {
        val parts = listOf(SmsPart("synthetic", 100, "【示例】验证码：12"), SmsPart("synthetic", 100, "34。"))
        assertEquals("【示例】验证码：1234。", SmsAssembly.assemble(parts))
        assertNotNull(OtpDetector.detect(SmsAssembly.assemble(parts)!!))
        assertNull(SmsAssembly.assemble(parts + SmsPart("other", 100, "56")))
        assertNull(SmsAssembly.assemble(parts + SmsPart("synthetic", 101, "56")))
        assertNull(SmsAssembly.assemble(listOf(SmsPart(null, 1, "text"))))
        assertNull(SmsAssembly.assemble(emptyList()))
        assertNull(SmsAssembly.assemble(List(17) { SmsPart("synthetic", 1, "x") }))
    }
    @Test fun replayIsSuppressedButLaterSmsIsAllowed() {
        val guard = SmsReplayGuard()
        val parts = listOf(SmsPart("synthetic", 100, "验证码1234。"))
        assertTrue(guard.accept(parts, 1)); assertFalse(guard.accept(parts, 2))
        assertTrue(guard.accept(parts.map { it.copy(timestamp = 101) }, 3))
        assertTrue(guard.accept(parts, 120002))
    }
    @Test fun pcmDurationMustFitWholeMessageAndRemainingBudget() {
        assertEquals(5000L, PcmBudget.durationMs(160000, 16000, 1))
        assertTrue(PcmBudget.fits(5000, 2000))
        assertFalse(PcmBudget.fits(5001, 0))
        assertFalse(PcmBudget.fits(5000, 2300))
        assertNull(PcmBudget.durationMs(3, 16000, 1))
        assertNull(PcmBudget.durationMs(PcmBudget.MAX_BYTES + 1, 16000, 1))
        assertFalse(PcmBudget.fits(1000, -1))
    }
}
