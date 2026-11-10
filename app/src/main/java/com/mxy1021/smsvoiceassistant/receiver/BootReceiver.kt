package com.mxy1021.smsvoiceassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mxy1021.smsvoiceassistant.ProbeApplication

/** Credential-encrypted settings, after unlock only. Never starts playback, Activity or Service. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as ProbeApplication
        app.settings.onBoot()
        app.event(if (app.settings.snapshot().announcingEnabled) "开机状态已恢复为启用，等待新短信" else "开机状态为暂停")
    }
}
