package com.mxy1021.smsvoiceassistant

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.mxy1021.smsvoiceassistant.audio.SpeechSession
import kotlinx.coroutines.flow.MutableStateFlow

class ProbeApplication : Application() {
    val events = MutableStateFlow("尚未运行")
    val main = Handler(Looper.getMainLooper())
    var visible = false
    var session: SpeechSession? = null
    val prefs by lazy { getSharedPreferences("stage0", MODE_PRIVATE) }
    var target: String?
        get() = prefs.getString("target", null)
        set(value) { session?.cancel(); prefs.edit().putString("target", value).apply() }
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

    fun speak(text: String, background: Boolean, finished: () -> Unit = {}) {
        if (session != null) { event("忙碌，本次拒绝播报", background); finished(); return }
        val next = SpeechSession(this, background) {
            session = null
            finished()
        }
        session = next
        next.start(text)
    }
}
