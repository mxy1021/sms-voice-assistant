package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.sms.OtpParser
import com.mxy1021.smsvoiceassistant.sms.SmsDiagnosticFormat
import org.junit.Assert.*
import org.junit.Test

class SmsDiagnosticFormatTest {
    @Test fun diagnosticFormatDoesNotAuthorizeUnmatchedTemplate() {
        val synthetic = listOf("【其他】验证码：429317，您正在登录网易手机账号。")
        assertTrue(SmsDiagnosticFormat.recognized(synthetic))
        assertNull(OtpParser.parse(synthetic))
    }
    @Test fun ordinarySmsAndInvalidLengthsAreNotCodeFormat() {
        assertFalse(SmsDiagnosticFormat.recognized(listOf("普通广播测试，今天正常。")))
        assertFalse(SmsDiagnosticFormat.recognized(listOf("验证码：4293179")))
        assertFalse(SmsDiagnosticFormat.recognized(listOf("字".repeat(2049))))
        assertTrue(SmsDiagnosticFormat.recognized(listOf("验证码：429", "317")))
    }
}
