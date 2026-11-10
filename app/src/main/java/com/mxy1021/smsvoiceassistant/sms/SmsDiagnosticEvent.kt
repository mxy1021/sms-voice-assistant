package com.mxy1021.smsvoiceassistant.sms

/** 字段仅为状态，不保存正文、数字、发送方或设备身份。 */
data class SmsDiagnosticEvent(val id: String, val fields: Map<String, String>)

object SmsDiagnosticFormat {
    private val code = Regex("验证码[：:]\\s*[0-9]{6}(?![0-9])")
    /** 只提供格式诊断，不参与模板判定或播报准入。 */
    fun recognized(parts: List<String>): Boolean =
        parts.sumOf { it.length } <= 2048 && code.containsMatchIn(parts.joinToString(""))
}
