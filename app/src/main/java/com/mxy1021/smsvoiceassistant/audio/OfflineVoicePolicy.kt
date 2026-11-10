package com.mxy1021.smsvoiceassistant.audio

import java.util.Locale

/** 真实短信严格门禁：支持明确的简体脚本标签，但绝不回退到未知或网络 Voice。 */
object OfflineVoicePolicy {
    fun accepts(locale: Locale, networkRequired: Boolean?, notInstalled: Boolean?): Boolean =
        locale.language in setOf("zh", "cmn", "zho") && locale.script != "Hant" &&
            (locale.country == "CN" || locale.script == "Hans" || locale.country.isEmpty()) &&
            networkRequired == false && notInstalled == false
}
