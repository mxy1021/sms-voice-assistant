package com.mxy1021.smsvoiceassistant.audio

/** 空地址、别名、多个不同输出均不能作为目标身份的证明。 */
object RoutePolicy {
    fun matches(target: String?, addresses: List<String>): Boolean =
        !target.isNullOrBlank() && addresses.isNotEmpty() &&
            addresses.all { it.isNotBlank() && it.equals(target, ignoreCase = true) }
}
