package com.mxy1021.smsvoiceassistant.sms

/** 仅验证指定模板；真实内容只在本次调用内存中存在。 */
object OtpParser {
    const val TEST_TEXT = "您收到的网易验证码为四二九三一七。"
    private val pattern = Regex("^【网易】验证码[：:]([0-9]{6})，您正在登录网易手机账号。[\\s]*$")
    fun parse(parts: List<String>): String? {
        if (parts.isEmpty() || parts.sumOf { it.length } > 2048) return null
        val digits = pattern.matchEntire(parts.joinToString(""))?.groupValues?.get(1) ?: return null
        val spoken = digits.map { "零一二三四五六七八九"[it - '0'] }.joinToString("")
        return "您收到的网易验证码为${spoken}。"
    }
}
