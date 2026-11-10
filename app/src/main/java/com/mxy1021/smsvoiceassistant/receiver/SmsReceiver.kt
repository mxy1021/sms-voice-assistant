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
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            app.event("广播到达但短信权限未授予", background); return
        }
        app.event("短信广播已到达（未保存内容）", background)
        app.prefs.edit().putString("lastBroadcast", "${System.currentTimeMillis()}：短信广播已到达（内容未保存）").apply()
        val pending = goAsync()
        var finished = false
        val finish = {
            if (!finished) { finished = true; pending.finish() }
        }
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            // 不拼接来自不同发送方的片段，不读取历史短信。
            val text = if (messages.map { it.originatingAddress }.distinct().size == 1)
                OtpParser.parse(messages.map { it.messageBody.orEmpty() }) else null
            if (text == null) { app.event("广播已解析，未匹配阶段 0 模板", background); finish() }
            else app.speak(text, background, finish)
        } catch (_: Exception) {
            app.event("短信解析或启动失败（内容已丢弃）", background)
            finish()
        }
    }
}
