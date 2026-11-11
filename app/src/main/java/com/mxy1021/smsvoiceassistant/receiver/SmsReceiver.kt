package com.mxy1021.smsvoiceassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Telephony
import com.mxy1021.smsvoiceassistant.ProbeApplication
import com.mxy1021.smsvoiceassistant.sms.SmsPart
import com.mxy1021.smsvoiceassistant.sms.SmsSource

/** Only the protected system broadcast decodes real PDUs. No simulation action or extras. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val receivedElapsed = SystemClock.elapsedRealtime()
        val receivedWall = System.currentTimeMillis()
        val app = context.applicationContext as ProbeApplication
        app.smsCoordinator.preflight()?.let {
            app.event(it)
            com.mxy1021.smsvoiceassistant.audio.Stage0Log.state("sms", "source", SmsSource.REAL_SMS.name)
            return
        }
        val pending = goAsync()
        var finished = false
        fun finish() { if (!finished) { finished = true; pending.finish() } }
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val parts = messages.map { SmsPart(it.originatingAddress, it.timestampMillis, it.messageBody) }
            app.smsStatus("广播", "系统SMS_RECEIVED已到达（内容不保存）")
            app.prefs.edit().putString("lastBroadcast", "系统短信广播已到达（内容未保存）").apply()
            app.smsCoordinator.submit(parts, receivedElapsed, receivedWall, SmsSource.REAL_SMS) { result ->
                finish()
                app.finishSmsEvent(result.id)
            }
        } catch (_: Exception) {
            app.event("真实PDU解析失败，内容已丢弃", !app.visible)
            finish()
        }
    }
}
