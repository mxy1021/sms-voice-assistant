package com.mxy1021.smsvoiceassistant

import com.mxy1021.smsvoiceassistant.settings.AppSettings
import com.mxy1021.smsvoiceassistant.ui.Readiness
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessTest {
    @Test fun readinessUsesRealConditionsAndNeverClaimsAServiceIsRunning() {
        val enabled = AppSettings(announcingEnabled = true, selectedBluetoothDevice = "synthetic")
        assertEquals("已启用，等待短信", Readiness.status(enabled, true, true, true, true, false, false))
        assertEquals("权限缺失", Readiness.status(enabled, false, true, true, true, false, false))
        assertEquals("尚未选择耳机", Readiness.status(enabled.copy(selectedBluetoothDevice = null), true, true, false, false, false, false))
        assertEquals("目标耳机未连接", Readiness.status(enabled, true, true, false, false, false, false))
        assertEquals("音频路由不匹配", Readiness.status(enabled, true, true, true, false, false, false))
        assertEquals("已暂停", Readiness.status(enabled.copy(announcingEnabled = false), true, true, true, true, false, false))
        assertEquals("正在准备或播报", Readiness.status(enabled, true, true, true, true, false, true))
    }
}
