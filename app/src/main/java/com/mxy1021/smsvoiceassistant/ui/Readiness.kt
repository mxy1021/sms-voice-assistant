package com.mxy1021.smsvoiceassistant.ui

import com.mxy1021.smsvoiceassistant.settings.AppSettings

object Readiness {
    fun status(settings: AppSettings, smsPermission: Boolean, bluetoothPermission: Boolean,
        connected: Boolean, routeReady: Boolean, checking: Boolean, speaking: Boolean): String = when {
        speaking -> "正在准备或播报"
        !settings.announcingEnabled -> "已暂停"
        !smsPermission || !bluetoothPermission -> "权限缺失"
        settings.selectedBluetoothDevice == null -> "尚未选择耳机"
        checking -> "正在检查播报条件"
        !connected -> "目标耳机未连接"
        !routeReady -> "音频路由不匹配"
        else -> "已启用，等待短信"
    }
}
