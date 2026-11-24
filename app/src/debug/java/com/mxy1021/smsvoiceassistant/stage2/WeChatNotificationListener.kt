package com.mxy1021.smsvoiceassistant.stage2

import android.os.Process
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mxy1021.smsvoiceassistant.ProbeApplication

class WeChatNotificationListener : NotificationListenerService() {
    private val app get() = application as ProbeApplication

    override fun onListenerConnected() = WeChatProbe.connected(app, this)

    override fun onListenerDisconnected() = WeChatProbe.disconnected(app, this, "DISCONNECTED")

    override fun onDestroy() {
        WeChatProbe.disconnected(app, this, "DESTROYED")
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        if (isWeChat(sbn)) WeChatProbe.posted(app, this, sbn, wall, elapsed)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (isWeChat(sbn)) WeChatProbe.removed(app, this, sbn, reason)
    }

    private fun isWeChat(sbn: StatusBarNotification) =
        sbn.packageName == WeChatProbe.WECHAT_PACKAGE && sbn.user == Process.myUserHandle()
}
