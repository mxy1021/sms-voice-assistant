package com.mxy1021.smsvoiceassistant.audio

import java.util.Locale

/** 仅供前台人工诊断使用，不参与真实短信的严格离线门禁。 */
object TtsCompatibilityPolicy {
    fun chinese(locale: Locale) = locale.language in setOf("zh", "cmn", "zho")

    fun simplifiedCandidate(locale: Locale) = chinese(locale) && locale.script != "Hant" &&
        (locale.country == "CN" || locale.country.isEmpty() || locale.script == "Hans")

    fun explicitlyOffline(networkRequired: Boolean?, notInstalled: Boolean?) =
        networkRequired == false && notInstalled == false

    // 枚举缺失不等于语言不可用；仍必须有初始化和 setLanguage 的明确成功结果。
    fun mayManuallySpeak(initResult: Int?, languageResult: Int?) =
        initResult == 0 && languageResult in 0..2
}
