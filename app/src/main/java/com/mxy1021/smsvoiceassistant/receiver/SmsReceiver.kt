package com.mxy1021.smsvoiceassistant.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import com.mxy1021.smsvoiceassistant.ProbeApplication
import com.mxy1021.smsvoiceassistant.sms.OtpParser

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as ProbeApplication
        val background = !app.visible
        if (app.session == null && app.foregroundFocus == null && !app.compatibilityTestActive) app.timeline.reset()
        app.timeline.add("真实 SMS 广播已到达；后台=$background（不记录内容）")
        app.smsStatus("广播", "已到达；后台=$background；时间=${System.currentTimeMillis()}")
        listOf("解析", "模板", "蓝牙", "路由", "焦点", "TTS").forEach { app.smsStatus(it, "本条尚未执行") }
        app.smsStatus("后台", if (background) "后台广播已到达，后续能否完成待观察；系统限制原因未知" else "前台广播；后台能力待验证")
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            app.smsStatus("解析", "广播已到达，但权限未授予，未解析")
            app.event("广播到达但短信权限未授予", background); return
        }
        app.event("短信广播已到达（未保存内容）", background)
        app.prefs.edit().putString("lastBroadcast", "${System.currentTimeMillis()}：短信广播已到达（内容未保存）").apply()
        val pending = goAsync()
        app.prefs.edit().putBoolean("smsPending", true).apply()
        var finished = false
        val finish = {
            if (!finished) { finished = true; pending.finish() }
        }
        var decoded = false
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            // 不拼接来自不同发送方的片段，不读取历史短信。
            if (messages.isEmpty() || messages.map { it.originatingAddress }.distinct().size != 1) {
                app.smsStatus("解析", "广播已到达但解析失败：没有有效片段或片段发送方不一致")
                app.timeline.add("最终失败阶段=SMS 解析；无有效片段或发送方不一致")
                app.smsFinished(); finish(); return
            }
            decoded = true
            app.smsStatus("解析", "PDU 单/多段解析成功（内容未保存）")
            val text = OtpParser.parse(messages.map { it.messageBody.orEmpty() })
            if (text == null) {
                app.smsStatus("模板", "解析成功，未匹配阶段 0 模板")
                app.timeline.add("解析成功但未匹配模板；未启动 TTS")
                app.event("广播已解析，未匹配阶段 0 模板", background)
                app.smsFinished(); finish()
            } else {
                app.smsStatus("模板", "已匹配阶段 0 模板（不记录验证码）")
                app.speak(text, background, finish, fromSms = true)
            }
        } catch (_: Exception) {
            if (decoded) app.smsStatus("TTS", "短信已解析，但后续匹配或启动异常；见时序")
            else app.smsStatus("解析", "广播已到达但 PDU 解析异常，未启动播放")
            app.timeline.add("最终失败阶段=${if (decoded) "模板匹配或播放启动" else "SMS PDU解析"}；错误码未知")
            app.smsFinished()
            app.event("短信解析或启动失败（内容已丢弃）", background)
            finish()
        }
    }
}
