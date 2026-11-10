package com.mxy1021.smsvoiceassistant

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.mxy1021.smsvoiceassistant.audio.SpeechSession
import com.mxy1021.smsvoiceassistant.audio.DiagnosticTimeline
import com.mxy1021.smsvoiceassistant.audio.ForegroundFocusTest
import kotlinx.coroutines.flow.MutableStateFlow

class ProbeApplication : Application() {
    val events = MutableStateFlow("尚未运行")
    val main = Handler(Looper.getMainLooper())
    var visible = false
    var session: SpeechSession? = null
    var compatibilityTestActive = false
    var foregroundFocus: ForegroundFocusTest? = null
    val timeline = DiagnosticTimeline()
    private val smsKeys = listOf("广播", "解析", "模板", "蓝牙", "路由", "焦点", "TTS", "后台")
    val smsState by lazy {
        val restored = smsKeys.associateWith { prefs.getString("sms_$it", "未验证") ?: "未验证" }.toMutableMap()
        if (prefs.getBoolean("smsPending", false)) {
            restored["TTS"] = "上次处理没有终结记录，未完成；最后阶段=${restored["TTS"]}"
            restored["后台"] = "是否受系统限制：未知；需核对进程中止、超时和权限，不能判成功"
        }
        MutableStateFlow(restored.toMap())
    }
    fun smsStatus(key: String, value: String) {
        smsState.value = smsState.value + (key to value)
        prefs.edit().putString("sms_$key", value).apply()
    }
    fun resetSmsObservation() {
        if (session != null || foregroundFocus != null || compatibilityTestActive) {
            timeline.add("短信观察重置被拒绝：另一测试正在运行"); return
        }
        smsKeys.forEach { smsStatus(it, "待验证") }
        smsStatus("广播", "本轮尚未观察到短信广播；不代表手机未收到 SMS")
        smsStatus("后台", "系统限制尚无证据，待验证")
        prefs.edit().putBoolean("smsPending", false).apply()
        timeline.reset()
        timeline.add("本轮短信观察已准备；不发送、不模拟短信")
    }
    fun smsFinished() { prefs.edit().putBoolean("smsPending", false).apply() }
    fun startForegroundFocusTest() {
        if (session == null && foregroundFocus == null && !compatibilityTestActive) timeline.reset()
        timeline.add("点击前台最小焦点播放测试；所选模式 gain=$mode")
        if (!visible || compatibilityTestActive || session != null || foregroundFocus != null) {
            timeline.add("播放前置条件=false：非前台或另一测试忙碌；最终失败阶段=启动门禁"); return
        }
        val next = ForegroundFocusTest(this, mode) { foregroundFocus = null }
        foregroundFocus = next
        next.start()
    }
    val prefs by lazy { getSharedPreferences("stage0", MODE_PRIVATE) }
    var target: String?
        get() = prefs.getString("target", null)
        set(value) { session?.cancel(); foregroundFocus?.cancel(); prefs.edit().putString("target", value).apply() }
    var mode: Int
        get() = prefs.getInt("focusMode", 3)
        set(value) { prefs.edit().putInt("focusMode", value).apply() }

    /** 只接受开发者固定状态码；禁止传入短信、设备标识或异常正文。 */
    fun event(code: String, background: Boolean = false) {
        val value = "${System.currentTimeMillis()}：$code"
        events.value = value
        prefs.edit().putString("lastEvent", value).apply()
        if (background) prefs.edit().putString("lastBackground", value).apply()
    }

    fun speak(text: String, background: Boolean, finished: () -> Unit = {}, fromSms: Boolean = false) {
        if (session == null && foregroundFocus == null && !compatibilityTestActive && !fromSms) timeline.reset()
        timeline.add("进入${if (fromSms) "真实短信严格链路" else "离线中文 / 焦点测试按钮"}；gain=$mode，后台=$background")
        if (compatibilityTestActive || foregroundFocus != null || session != null) {
            timeline.add("播放前置条件=false：另一测试正在运行；最终失败阶段=启动门禁")
            if (fromSms) { smsStatus("TTS", "模板已匹配，但另一测试忙碌，未启动"); smsFinished() }
            event("忙碌，本次拒绝播报", background); finished(); return
        }
        val next = SpeechSession(this, background, mode, fromSms) {
            session = null
            if (fromSms) smsFinished()
            finished()
        }
        session = next
        next.start(text)
    }
}
