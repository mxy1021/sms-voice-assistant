package com.mxy1021.smsvoiceassistant

/** Only artificial data; this file is never packaged into the application APK. */
internal object DebugSmsFixtures {
    const val OTP = "【测试平台】验证码：429317，请勿泄露。"
    const val OTP_FULL = "【测试平台】您的验证码为429317，有效期五分钟，请勿告知他人。"
    const val NORMAL = "今天下午三点开会，请记得带上资料。"
    const val ORDER = "您的订单金额为129元，预计18点送达，订单号202611110001。"
    const val MIXED = "【测试平台】验证码为A7b29C，请勿泄露。"
    const val CONTEXTUAL = "【测试平台】您于18点申请验证码429317，有效期5分钟。"
    val LONG80 = "这是一段人工构造的短信边界测试内容。".repeat(8).take(80)
    val LONG81 = LONG80 + "。"
    const val LONG_PCM = "这是一条用于检查完整语音播放时间上限的人工短信，请勿将字符数量当作播放成功。"
    const val SHORT = "明天开会。"
    const val SHORT_OTP = "验证码1234。"
}
