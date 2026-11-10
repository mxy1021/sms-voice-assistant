package com.mxy1021.smsvoiceassistant.audio

/** 只描述 API 结果，不把播放回调等同于耳机听感或音乐响应。 */
data class FocusObservation(val field: String, val value: String)

data class AudioModeResult(
    val id: String,
    val mode: Int,
    val fields: Map<String, String> = emptyMap(),
    val voiceHeard: String = "未验证",
    val musicResponse: String = "未验证",
    val musicRecovery: String = "未验证"
)
