package com.mxy1021.smsvoiceassistant.sms

import com.mxy1021.smsvoiceassistant.settings.AppSettings

data class Otp(val platform: String?, val code: String)

/** Deliberately small recognizer: code must immediately follow an OTP keyword. */
object OtpExtractor {
    private val candidate = Regex("(?:验证码|校验码|动态码)[\\s：:]*[为是]?[\\s：:]*([A-Za-z0-9]+)")
    private val platform = Regex("^\\s*【([\\p{IsHan}A-Za-z0-9 ._-]{1,20})】")
    fun extract(body: String): Otp? {
        val hits = candidate.findAll(body).toList()
        if (hits.size != 1) return null
        val match = hits.single()
        val code = match.groupValues[1]
        val numeric = code.all { it in '0'..'9' }
        if (numeric && code.length !in setOf(4, 6, 8)) return null
        if (!numeric && (code.length !in 4..8 || code.none { it in '0'..'9' })) return null
        val suffix = body.substring(match.range.last + 1)
        // Do not reinterpret decimals, dates, quantities, or amounts as codes.
        if (Regex("^(?:[年月日元号]|[.．:/-][0-9])").containsMatchIn(suffix)) return null
        if (Regex("^\\s*(?:或|[/／]|[0-9]|、\\s*[A-Za-z0-9])").containsMatchIn(suffix)) return null
        return Otp(platform.find(body)?.groupValues?.get(1)?.trim(), code)
    }
}

object OtpDetector { fun detect(body: String): Otp? = OtpExtractor.extract(body) }

data class SpeechDecision(val text: String?, val status: String)

object SpeechFormatter {
    const val MAX_INPUT_CHARS = 2048
    const val MAX_FULL_TEXT_CHARS = 80
    const val TEST_TEXT = "短信语音助手测试，耳机播放正常。"
    fun choose(body: String, settings: AppSettings): SpeechDecision {
        if (!settings.announcingEnabled) return SpeechDecision(null, "已暂停，本条不播报")
        if (body.isBlank() || body.length > MAX_INPUT_CHARS)
            return SpeechDecision(null, "正文为空或超过输入上限，本条未播报")
        if (!settings.otpOnlyEnabled) return full(body)
        val otp = OtpDetector.detect(body) ?: return SpeechDecision(null, "未可靠识别验证码，本条忽略")
        if (!settings.otpSimplifyEnabled) return full(body)
        val code = otp.code.map { ch ->
            if (ch in '0'..'9') "零一二三四五六七八九"[ch - '0'].toString()
            else "${if (ch.isLowerCase()) "小写" else "大写"}字母${ch.uppercaseChar()}"
        }.joinToString(if (otp.code.all { it in '0'..'9' }) "" else "，")
        return SpeechDecision("您收到的${otp.platform.orEmpty()}验证码为${code}。", "精简验证码，等待音频安全检查")
    }
    private fun full(body: String) = if (body.length > MAX_FULL_TEXT_CHARS)
        SpeechDecision(null, "全文超过80字符安全上限，本条整条未播报")
    else SpeechDecision(body, "全文播报，等待实际音频时长检查")
}

object PcmBudget {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_SPEECH_MS = 5000L
    const val ROUTE_AND_CLEANUP_MS = 700L
    fun durationMs(bytes: Int, rate: Int, channels: Int): Long? {
        if (bytes !in 1..MAX_BYTES || rate !in 8000..48000 || channels !in 1..2 || bytes % (channels * 2) != 0) return null
        return (bytes.toLong() * 1000 + rate * channels * 2 - 1) / (rate * channels * 2)
    }
    fun fits(duration: Long, elapsed: Long) = duration in 1..MAX_SPEECH_MS && elapsed >= 0 &&
        duration + elapsed + ROUTE_AND_CLEANUP_MS < SmsSafetyWindow.SESSION_BUDGET_MS
    fun remainingFits(remainingMs: Long, elapsed: Long) = remainingMs in 0..(MAX_SPEECH_MS + 100) && elapsed >= 0 &&
        remainingMs + elapsed + ROUTE_AND_CLEANUP_MS < SmsSafetyWindow.SESSION_BUDGET_MS
}
