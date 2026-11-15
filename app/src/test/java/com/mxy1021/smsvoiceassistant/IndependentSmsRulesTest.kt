package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.settings.AppSettings
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Test

class IndependentSmsRulesTest {
    private val otpBody = "【示例】验证码：0123，请勿泄露。"
    private val normalBody = "明天14:30开会。"
    private val simplified = "您收到的示例验证码为零一二三。"
    private fun settings(only: Boolean, simplify: Boolean) = AppSettings(
        announcingEnabled = true, otpOnlyEnabled = only, otpSimplifyEnabled = simplify)

    private fun combination(only: Boolean, simplify: Boolean, otpMode: SmsSpeechMode,
        normalMode: SmsSpeechMode, otpText: String, normalText: String?) {
        assertEquals(otpMode, SmsSpeechRules.decide(OtpDetector.detect(otpBody), only, simplify))
        assertEquals(normalMode, SmsSpeechRules.decide(null, only, simplify))
        assertEquals(otpText, SpeechFormatter.choose(otpBody, settings(only, simplify)).text)
        assertEquals(normalText, SpeechFormatter.choose(normalBody, settings(only, simplify)).text)
    }

    @Test fun s01OnlyAndSimplifyOn() = combination(true, true,
        SmsSpeechMode.SIMPLIFIED, SmsSpeechMode.IGNORE, simplified, null)

    @Test fun s02OnlyOnSimplifyOff() = combination(true, false,
        SmsSpeechMode.FULL, SmsSpeechMode.IGNORE, otpBody, null)

    @Test fun s03OnlyOffSimplifyOn() = combination(false, true,
        SmsSpeechMode.SIMPLIFIED, SmsSpeechMode.FULL, simplified, normalBody)

    @Test fun s04BothOff() = combination(false, false,
        SmsSpeechMode.FULL, SmsSpeechMode.FULL, otpBody, normalBody)

    @Test fun ambiguousAndFailedRecognitionNeverInventACode() {
        listOf("验证码1234或5678。", "验证码：1234，验证码：5678。", "验证码为，请稍候。",
            "验证码12345。").forEach { body ->
            assertNull(OtpDetector.detect(body))
            listOf(true, false).forEach { only -> listOf(true, false).forEach { simplify ->
                assertEquals(if (only) null else body, SpeechFormatter.choose(body, settings(only, simplify)).text)
            } }
        }
    }

    @Test fun ordinaryNumbersStayFullEvenWhenSimplifyIsOn() {
        listOf("订单123456已发货，金额1234.56元。", "验证码：2026年", "请联系13800138000。").forEach { body ->
            assertNull(OtpDetector.detect(body))
            assertEquals(body, SpeechFormatter.choose(body, settings(false, true)).text)
        }
    }

    @Test fun blankAndInputOverflowAreRejectedForEveryCombination() {
        listOf(true, false).forEach { only -> listOf(true, false).forEach { simplify ->
            listOf("", " \n\t", "字".repeat(SpeechFormatter.MAX_INPUT_CHARS + 1)).forEach { body ->
                assertNull(SpeechFormatter.choose(body, settings(only, simplify)).text)
            }
        } }
    }

    @Test fun fullBudgetRejectsWholeBodyButAllowsAnEligibleSummary() {
        val body = otpBody + "字".repeat(81)
        listOf(true, false).forEach { only ->
            assertNull(SpeechFormatter.choose(body, settings(only, false)).text)
            assertEquals(simplified, SpeechFormatter.choose(body, settings(only, true)).text)
        }
        listOf(true, false).forEach { simplify ->
            assertNull(SpeechFormatter.choose("字".repeat(81), settings(false, simplify)).text)
            assertEquals("字".repeat(80), SpeechFormatter.choose("字".repeat(80), settings(false, simplify)).text)
        }
    }

    @Test fun summaryDoesNotMutateLaterFullTextIncludingWhitespaceAndCase() {
        val body = "  【示例】验证码：a0B1，非本人操作请忽略。\n"
        val original = body.toCharArray()
        assertNotEquals(body, SpeechFormatter.choose(body, settings(false, true)).text)
        listOf(true, false).forEach { only ->
            assertEquals(body, SpeechFormatter.choose(body, settings(only, false)).text)
        }
        assertArrayEquals(original, body.toCharArray())
    }

    @Test fun successiveSnapshotsUseCurrentRulesWithoutCachedDecision() {
        repeat(50) {
            s03OnlyOffSimplifyOn(); s04BothOff(); s01OnlyAndSimplifyOn(); s02OnlyOnSimplifyOff()
        }
    }
}
