package com.mxy1021.smsvoiceassistant.sms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import com.mxy1021.smsvoiceassistant.ProbeApplication
import com.mxy1021.smsvoiceassistant.audio.SpeechResult

/** Provenance affects diagnostics only, never permission, rule, route or playback policy. */
enum class SmsSource { REAL_SMS, DEBUG_SIMULATION }
enum class SmsDisposition { REJECTED, IGNORED, BUSY, COMPLETED, INCOMPLETE }
data class SmsResult(val id: String, val source: SmsSource, val disposition: SmsDisposition,
    val reason: String, val speech: SpeechResult? = null)

/** Main-looper business boundary for already parsed messages; contains no fixtures or test hooks. */
class SmsCoordinator(private val app: ProbeApplication,
    private val startSpeech: (String, Boolean, String, (SpeechResult) -> Unit) -> Unit = { text, background, id, result ->
        app.speak(text, background, fromSms = true, smsEventId = id, result = result)
    }) {
    fun preflight(): String? = when {
        !app.settings.snapshot().announcingEnabled -> "已暂停：新 SMS 不进入播报链路"
        listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.BLUETOOTH_CONNECT).any {
            app.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        } -> "短信或蓝牙权限缺失，本条未解析/未播报"
        else -> null
    }

    fun submit(parts: List<SmsPart>, receivedElapsed: Long, receivedWall: Long,
        source: SmsSource, completed: (SmsResult) -> Unit): String {
        check(Looper.myLooper() == Looper.getMainLooper())
        val id = app.beginSmsEvent(receivedElapsed, source)
        var finished = false
        fun finish(disposition: SmsDisposition, reason: String, speech: SpeechResult? = null) {
            if (finished) return
            finished = true
            app.updateSmsEvent(id, "业务结果", disposition.name)
            app.updateSmsEvent(id, "业务终结原因", reason)
            if (speech == null) {
                app.updateSmsEvent(id, "最终结果", "${disposition.name}：未启动音频")
                app.updateSmsEvent(id, "最终失败节点", reason)
            }
            completed(SmsResult(id, source, disposition, reason, speech))
        }
        try {
            preflight()?.let { finish(SmsDisposition.REJECTED, it); return id }
            val settings = app.settings.snapshot()
            val body = SmsAssembly.assemble(parts)
            if (body == null) {
                finish(SmsDisposition.REJECTED, "片段无效、发送方/时间不一致或正文超限"); return id
            }
            val ages = parts.map { receivedWall - it.timestamp }
            val ageLabel = if (source == SmsSource.REAL_SMS) "PDU至onReceive" else "虚拟消息年龄"
            app.updateSmsEvent(id, "${ageLabel}最小差ms", ages.min().toString())
            app.updateSmsEvent(id, "${ageLabel}最大差ms", ages.max().toString())
            if (!SmsSafetyWindow.freshPdu(ages)) {
                finish(SmsDisposition.REJECTED, "安全丢弃：消息超过120秒或时钟差无效"); return id
            }
            if (!app.smsReplayGuard.accept(parts, receivedElapsed)) {
                finish(SmsDisposition.IGNORED, "重复消息已忽略"); return id
            }
            app.updateSmsEvent(id, "解析", "解析后单/多段组合成功")
            val decision = SpeechFormatter.choose(body, settings)
            app.smsTime(id, "短信规则完成")
            app.smsStatus("模板", decision.status)
            val text = decision.text
            if (text == null) {
                app.event(decision.status, !app.visible)
                finish(SmsDisposition.IGNORED, decision.status); return id
            }
            startSpeech(text, !app.visible, id) { speech ->
                finish(when {
                    speech.busy -> SmsDisposition.BUSY
                    speech.playbackCompleted && speech.focusReleased -> SmsDisposition.COMPLETED
                    else -> SmsDisposition.INCOMPLETE
                }, speech.reason, speech)
            }
        } catch (_: Exception) {
            // Only generated state codes cross this diagnostic boundary, never exception text.
            finish(SmsDisposition.REJECTED, "解析后短信业务处理异常")
        }
        return id
    }
}
