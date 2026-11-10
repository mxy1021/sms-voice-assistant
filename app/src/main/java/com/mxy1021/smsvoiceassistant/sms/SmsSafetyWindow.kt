package com.mxy1021.smsvoiceassistant.sms

/** 阶段 0 安全上限，不是正式规则引擎或后台保活策略。 */
object SmsSafetyWindow {
    const val MAX_PDU_AGE_MS = 120_000L
    const val SESSION_BUDGET_MS = 8_000L
    fun freshPdu(ages: List<Long>) = ages.isNotEmpty() && ages.all { it in 0..MAX_PDU_AGE_MS }
    fun withinBudget(startElapsed: Long, nowElapsed: Long) =
        nowElapsed >= startElapsed && nowElapsed - startElapsed < SESSION_BUDGET_MS
}
