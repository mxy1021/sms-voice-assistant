package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.audio.*
import com.mxy1021.smsvoiceassistant.settings.*
import com.mxy1021.smsvoiceassistant.sms.*
import org.junit.Assert.*
import org.junit.Test

class SpeechExperienceTest {
    private val full = AppSettings(announcingEnabled = true, otpOnlyEnabled = false, otpSimplifyEnabled = false)
    @Test fun numericLengthsAndLeadingZeros() {
        listOf("1234", "55555", "123456", "12345678", "00981").forEach { value ->
            assertEquals(value, OtpDetector.detect("验证码$value。")?.code)
            val digits = value.map { "零一二三四五六七八九"[it - '0'] }.joinToString("")
            assertEquals("验证码$digits。", SpeechFormatter.choose("验证码$value。", full).text)
        }
    }
    @Test fun mixedCaseAndEnglishTemplates() {
        assertEquals("A12345", OtpDetector.detect("Your verification code is A12345.")?.code)
        assertEquals("00981", OtpDetector.detect("OTP: 00981")?.code)
        assertEquals("00981", OtpDetector.detect("00981为您的验证码，请勿泄露。")?.code)
        assertEquals("验证码大写字母A，一，二，三，四，五。", SpeechFormatter.choose("验证码A12345。", full).text)
    }
    @Test fun negativesAndOnlyCodeSpanChanges() {
        listOf("金额55555元", "日期2026-11-15", "订单12345678", "编号55555", "请拨13800138000",
            "验证码55555元", "验证码12345.67元", "验证码1234567", "验证码1234或5678", "验证码2026 年").forEach {
            assertNull(it, OtpDetector.detect(it))
            assertEquals(it, SpeechFormatter.choose(it, full).text)
        }
        assertEquals("验证码零零九八一，金额25元，11月15日。",
            SpeechFormatter.choose("验证码00981，金额25元，11月15日。", full).text)
    }
    @Test fun linksAndMeaningfulInformation() {
        val cases = mapOf(
            "请看http://example.com/a?x=1，明天见。" to "请看，明天见。",
            "您的快递已到。详情：https://example.com/a?utm_id=123" to "您的快递已到。",
            "先看https://a.com/x，然后明天见。" to "先看，然后明天见。",
            "链接：https://a.com/a；链接：https://b.cn/?a=1。" to "",
            "验证码00981，详情https://a.com/123。" to "验证码00981，详情。",
            "取件码A-123，金额12.50元，11月15日18:30，北京路8号。" to "取件码A-123，金额12.50元，11月15日18:30，北京路8号。",
            "请看www.example.com/path?q=1，订单AB123。" to "请看，订单AB123。",
            "通知example.cn/a#b 明天开会。" to "通知 明天开会。"
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, SmsTextCleaner.clean(input)) }
        assertNull(SpeechFormatter.choose("https://example.com/path", full).text)
        val original = "验证码00981，详情：https://a.com/x"
        assertEquals("张三，验证码零零九八一", SpeechFormatter.choose(original, full, "张三").text)
        assertEquals("验证码00981，详情：https://a.com/x", original)
    }
    @Test fun contactsDoNotExposeNumbersOrGuessShortSenders() {
        val valid = "13800138000"
        assertEquals("张三", ContactMatchPolicy.match(valid, listOf("+86 138-0013-8000" to "张三")))
        assertEquals("张三", ContactMatchPolicy.match("008613800138000", listOf(valid to "张三")))
        listOf("10086", "10612345678", "BANK", "12345", "1380013800", "13800138000;DROP").forEach {
            assertNull(ContactMatchPolicy.match(it, listOf(it to "张三")))
        }
        assertNull(ContactMatchPolicy.match(valid, emptyList()))
        assertNull(ContactMatchPolicy.match(valid, listOf("13900138000" to "张三")))
        assertNull(ContactMatchPolicy.match(valid, listOf(valid to "张三", valid to "李四")))
        listOf("13800138000", "张三13800138000", "张三138.0013.8000", "张三138\u00a00013\u00a08000",
            "https://a.com", "张\n三").forEach {
            assertEquals("晚上见。", SpeechFormatter.choose("晚上见。", full, it).text)
        }
    }
    @Test fun permissionDeniedRevokedAndProviderFailureDegradeToBodyOnly() {
        var allowed = false; var queries = 0; var revoke = false; var fail = false
        val lookup = ContactLookup({ allowed }) {
            queries++
            if (revoke) allowed = false
            if (fail) throw SecurityException("controlled revocation")
            listOf("13800138000" to "张三")
        }
        assertNull(lookup.resolve("13800138000")); assertEquals(0, queries)
        allowed = true
        assertEquals("张三", lookup.resolve("13800138000")); assertEquals(1, queries)
        revoke = true
        assertNull(lookup.resolve("13800138000")); assertEquals(2, queries)
        assertNull(lookup.resolve("13800138000")); assertEquals(2, queries)
        allowed = true; revoke = false; fail = true
        assertNull(lookup.resolve("13800138000")); assertEquals(3, queries)
    }
    @Test fun namesApplyOnlyToFullModeForEverySwitchCombination() {
        listOf(true, false).forEach { only -> listOf(true, false).forEach { simplify ->
            val settings = full.copy(otpOnlyEnabled = only, otpSimplifyEnabled = simplify)
            assertEquals(if (simplify) "您收到的验证码为五五五五五。" else "张三，验证码五五五五五。",
                SpeechFormatter.choose("验证码55555。", settings, "张三").text)
            assertEquals(if (only) null else "张三，晚上见。", SpeechFormatter.choose("晚上见。", settings, "张三").text)
            assertEquals(if (only) null else "晚上见。", SpeechFormatter.choose("晚上见。", settings).text)
        } }
    }
    @Test fun fullBudgetIncludesNamesAndSpelledLettersAndUtf16() {
        assertNull(SpeechFormatter.choose("字".repeat(80), full, "张三").text)
        assertNotNull(SpeechFormatter.choose("字".repeat(77), full, "张三").text)
        listOf(100, 120, 160).forEach { assertNull(SpeechFormatter.choose("字".repeat(it), full).text) }
        assertEquals(80, SpeechFormatter.choose("😀".repeat(40), full).text!!.length)
        assertNull(SpeechFormatter.choose("😀".repeat(41), full).text)
        listOf(5000L, 5100L, 5200L).forEach { assertEquals(it == 5000L, PcmBudget.fits(it, 1800)) }
        assertNotNull(PcmBudget.durationMs(PcmBudget.MAX_BYTES, 48000, 2))
        assertNull(PcmBudget.durationMs(PcmBudget.MAX_BYTES + 1, 48000, 2))
        assertEquals(8000L < SmsSafetyWindow.SESSION_BUDGET_MS, SmsSafetyWindow.withinBudget(0, 8000))
        assertFalse(SmsSafetyWindow.withinBudget(0, 8500))
    }
    private class Effect : LoudnessEffect {
        var gain = -1; var releases = 0; var fail = false
        override fun configure(gainMb: Int) { gain = gainMb; if (fail) error("controlled") }
        override fun release() { releases++ }
    }
    @Test fun volumeIsPerTrackAndReleaseIsIdempotent() {
        listOf(0, 3, 6).forEach { gain ->
                val effect = Effect(); var calls = 0
                val session = SessionLoudness { id -> assertEquals(17, id); calls++; effect }
                assertTrue(session.attach(17, gain))
                assertEquals(if (gain == 0) 0 else 1, calls)
                if (gain != 0) assertEquals(gain * 100, effect.gain)
                session.release(); session.release()
                assertEquals(if (gain == 0) 0 else 1, effect.releases)
                assertFalse(session.attach(17, gain))
        }
    }
    @Test fun audioEffectFailureAndGlobalSessionAreSafe() {
        val effect = Effect().also { it.fail = true }
        val session = SessionLoudness { effect }
        assertFalse(session.attach(0, 6)); assertFalse(session.attach(17, 9))
        assertFalse(session.attach(17, 6)); assertEquals(1, effect.releases)
        session.release(); assertEquals(1, effect.releases)
        assertFalse(SessionLoudness { error("unsupported") }.attach(17, 3))
    }
    @Test fun gainPersistsWithoutLosingOldConfigurationOrRevivingAudio() {
        val store = MemorySettingsStore(); val repo = SettingsRepository(store, 1)
        repo.selectDevice("synthetic"); repo.setEnabled(true)
        val before = repo.snapshot(); val permit = repo.permit(true)!!
        listOf(3, 6, 0).forEach { gain ->
            repo.setSpeechGain(gain)
            assertEquals(before.copy(speechGainDb = gain), SettingsRepository(store, 1).snapshot())
            assertFalse(repo.valid(permit))
        }
        store.data["speechGainDb"] = 99
        assertEquals(0, SettingsRepository(store, 1).snapshot().speechGainDb)
    }
}
