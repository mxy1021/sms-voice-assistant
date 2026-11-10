package com.mxy1021.smsvoiceassistant.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Telephony
import com.mxy1021.smsvoiceassistant.ProbeApplication
import com.mxy1021.smsvoiceassistant.sms.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val receiveElapsed = SystemClock.elapsedRealtime()
        val receiveWall = System.currentTimeMillis()
        val app = context.applicationContext as ProbeApplication
        val settings = app.settings.snapshot()
        if (!settings.announcingEnabled) { app.event("已暂停：新 SMS 不进入播报链路"); return }
        if (listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.BLUETOOTH_CONNECT).any {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }) { app.event("短信或蓝牙权限缺失，本条未解析/未播报"); return }
        val background = !app.visible
        if (app.session == null && app.foregroundFocus == null && !app.compatibilityTestActive) app.timeline.reset()
        val eventId = app.beginSmsEvent(receiveElapsed)
        app.smsStatus("广播", "已到达；后台=$background（不记录内容）")
        app.prefs.edit().putString("lastBroadcast", "短信广播已到达（内容未保存）").apply()
        val pending = goAsync()
        app.smsTime(eventId, "goAsync开始")
        var finished = false
        val finish = {
            if (!finished) { finished = true; pending.finish(); app.finishSmsEvent(eventId) }
        }
        fun reject(status: String) {
            app.event(status, background)
            app.updateSmsEvent(eventId, "最终失败节点", status)
            app.updateSmsEvent(eventId, "最终结果", "未播报")
            finish()
        }
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            app.smsTime(eventId, "PDU解析完成")
            val parts = messages.map { SmsPart(it.originatingAddress, it.timestampMillis, it.messageBody) }
            val body = SmsAssembly.assemble(parts)
                ?: return reject("PDU片段无效、发送方/时间不一致或正文超限")
            val ages = messages.map { receiveWall - it.timestampMillis }
            app.updateSmsEvent(eventId, "PDU至onReceive最小差ms", ages.min().toString())
            app.updateSmsEvent(eventId, "PDU至onReceive最大差ms", ages.max().toString())
            if (!SmsSafetyWindow.freshPdu(ages)) return reject("安全丢弃：PDU超过120秒或时钟差无效")
            if (!app.smsReplayGuard.accept(parts, receiveElapsed)) return reject("重复广播已忽略")
            app.smsStatus("解析", "新 SMS 单/多段组合成功（正文不保存）")
            app.updateSmsEvent(eventId, "解析", "成功")
            val decision = SpeechFormatter.choose(body, settings)
            app.smsTime(eventId, "短信规则完成")
            app.smsStatus("模板", decision.status)
            val text = decision.text ?: return reject(decision.status)
            // UI mutations and this entry point are serialized by the main looper.
            // SpeechSession validates its generation permit on async callbacks and writes.
            app.speak(text, background, finish, fromSms = true, smsEventId = eventId)
        } catch (_: Exception) { reject("PDU解析或播报启动失败，内容已丢弃") }
    }
}
