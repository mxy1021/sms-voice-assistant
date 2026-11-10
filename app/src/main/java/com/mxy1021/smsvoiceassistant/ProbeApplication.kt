package com.mxy1021.smsvoiceassistant

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.mxy1021.smsvoiceassistant.audio.SpeechSession
import com.mxy1021.smsvoiceassistant.audio.DiagnosticTimeline
import com.mxy1021.smsvoiceassistant.audio.ForegroundFocusTest
import com.mxy1021.smsvoiceassistant.audio.AudioModeResult
import com.mxy1021.smsvoiceassistant.audio.Stage0Log
import com.mxy1021.smsvoiceassistant.sms.SmsDiagnosticEvent
import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
import android.app.KeyguardManager
import android.app.ActivityManager
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import com.mxy1021.smsvoiceassistant.settings.SettingsRepository
import com.mxy1021.smsvoiceassistant.settings.PreferenceSettingsStore
import android.provider.Settings

class ProbeApplication : Application() {
    val settings by lazy { SettingsRepository(PreferenceSettingsStore(prefs),
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)) }
    val speaking = MutableStateFlow(false)
    fun setAnnouncingEnabled(enabled: Boolean) {
        settings.setEnabled(enabled)
        if (!enabled) { session?.cancel(); foregroundFocus?.cancel() }
    }
    val events = MutableStateFlow("尚未运行")
    val main = Handler(Looper.getMainLooper())
    var visible = false
    var session: SpeechSession? = null
    var compatibilityTestActive = false
    var foregroundFocus: ForegroundFocusTest? = null
    val timeline = DiagnosticTimeline(sink = { Stage0Log.state("timeline", "step", it) })
    val audioModeResults = MutableStateFlow<Map<Int, AudioModeResult>>(emptyMap())
    private var audioSequence = 0
    private val smsReceiveElapsed = mutableMapOf<String, Long>()
    override fun onCreate() {
        super.onCreate()
        settings.snapshot()
        Stage0Log.state("process", "Application.onCreate", "新进程实例；不是设备标识")
        val lastExit = runCatching { getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()?.reason }.getOrNull()
        Stage0Log.state("process", "上次退出原因码", lastExit?.toString() ?: "未知")
    }
    fun environment(id: String?, stage: String) {
        val memory = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(memory)
        Stage0Log.state("context", stage,
            "ActivityStarted=$visible；interactive=${getSystemService(PowerManager::class.java).isInteractive}；" +
                "keyguard=${getSystemService(KeyguardManager::class.java).isKeyguardLocked}；importance=${memory.importance}", id)
    }
    fun smsTime(id: String?, stage: String, atElapsed: Long = SystemClock.elapsedRealtime()) {
        Stage0Log.timing(id, stage, atElapsed, id?.let { smsReceiveElapsed[it] })
        if (id != null) {
            updateSmsEvent(id, "t.$stage", atElapsed.toString())
            smsReceiveElapsed[id]?.let { updateSmsEvent(id, "d.$stage", (atElapsed - it).toString()) }
        }
    }
    fun receivedAt(id: String?) = id?.let { smsReceiveElapsed[it] }
    val smsEvents by lazy {
        val restored = runCatching {
            val array = JSONArray(prefs.getString("smsEvents", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val data = item.getJSONObject("fields")
                val fields = data.keys().asSequence().associateWith { data.getString(it) }.toMutableMap()
                if (fields["接收器finish"] == "等待") {
                    fields["最终结果"] = "上次处理未终结，原因未知"
                    fields["后台限制"] = "未验证，需要 AudioHardening 或其他系统证据"
                }
                SmsDiagnosticEvent(item.getString("id"), fields.toMap())
            }
        }.getOrDefault(emptyList())
        MutableStateFlow(restored)
    }
    private fun saveSmsEvents() {
        val data = JSONArray()
        smsEvents.value.forEach { event ->
            data.put(JSONObject().put("id", event.id).put("fields", JSONObject(event.fields)))
        }
        prefs.edit().putString("smsEvents", data.toString()).apply()
    }
    fun beginSmsEvent(receiveElapsed: Long = SystemClock.elapsedRealtime()): String {
        val sequence = prefs.getLong("smsSequence", 0) + 1
        prefs.edit().putLong("smsSequence", sequence).apply()
        val id = "SMS-${sequence.toString().padStart(6, '0')}"
        smsReceiveElapsed[id] = receiveElapsed
        while (smsReceiveElapsed.size > 16) smsReceiveElapsed.remove(smsReceiveElapsed.keys.first())
        val fields = listOf("解析", "模板", "验证码格式", "目标耳机连接", "预期路由", "实际路由", "requestAudioFocus", "onStart", "onDone", "onError")
            .associateWith { "未执行/未观测" } + mapOf(
                "SMS_RECEIVED" to "已触发", "APP前台" to visible.toString(),
                "系统API" to Build.VERSION.SDK_INT.toString(), "targetSdk" to applicationInfo.targetSdkVersion.toString(),
                "开始毫秒" to System.currentTimeMillis().toString(), "gain" to mode.toString(),
                "speak调用" to "未调用（短信仍为严格文件合成/PCM路径）",
                "最终失败节点" to "未终结", "最终结果" to "处理中", "接收器finish" to "等待",
                "后台限制" to "未验证，不能由 API 37 或无声直接推断")
        smsEvents.value = (smsEvents.value + SmsDiagnosticEvent(id, fields)).takeLast(8)
        saveSmsEvents()
        Stage0Log.state("sms", "SMS_RECEIVED", "已触发；APP前台=$visible；API=${Build.VERSION.SDK_INT}", id)
        timeline.add("[$id] SMS_RECEIVED 已触发，APP前台=$visible，API=${Build.VERSION.SDK_INT}")
        smsTime(id, "BroadcastReceiver.onReceive", receiveElapsed)
        environment(id, "onReceive状态")
        return id
    }
    fun updateSmsEvent(id: String?, field: String, value: String) {
        if (id == null) return
        smsEvents.value = smsEvents.value.map { if (it.id == id) it.copy(fields = it.fields + (field to value)) else it }
        saveSmsEvents()
        Stage0Log.state("sms", field, value, id)
        timeline.add("[$id] $field=$value")
    }
    fun finishSmsEvent(id: String) {
        smsTime(id, "PendingResult.finish")
        updateSmsEvent(id, "接收器finish", "已调用，时间=${System.currentTimeMillis()}")
    }
    fun startAudioModeTest(selectedMode: Int) {
        if (session != null || foregroundFocus != null || compatibilityTestActive) {
            timeline.add("A/B 点击被拒绝：已有测试运行，不覆盖其编号或结果"); return
        }
        if (session == null && foregroundFocus == null && !compatibilityTestActive) timeline.reset()
        val id = "AUDIO-${System.currentTimeMillis()}-${++audioSequence}"
        val defaults = listOf("目标耳机连接", "预期路由", "前置条件", "requestAudioFocus", "speak 返回", "onStart", "onDone", "onError", "onAudioFocusChange", "abandon 时点")
            .associateWith { "未执行/未观测" } + mapOf("模式/gain" to selectedMode.toString(), "APP 前台" to visible.toString(), "speak 调用" to "false", "测试结束" to "false")
        val record = AudioModeResult(id, selectedMode, defaults)
        audioModeResults.value = audioModeResults.value + (selectedMode to record)
        timeline.add("[$id] A/B 直接播放；gain=$selectedMode，前台=$visible；语音与音乐分别记录")
        fun update(field: String, value: String) {
            Stage0Log.state("audio", field, value, id)
            val current = audioModeResults.value[selectedMode] ?: return
            if (current.id == id) audioModeResults.value = audioModeResults.value +
                (selectedMode to current.copy(fields = current.fields + (field to value)))
        }
        if (!visible || compatibilityTestActive || session != null || foregroundFocus != null || selectedMode !in setOf(2, 3)) {
            update("最终失败节点", "启动门禁：非前台、忙碌或模式无效")
            update("前置条件", "false"); update("测试结束", "true")
            timeline.add("[$id] 启动被拒绝，未调用 speak"); return
        }
        // 和已通过的对照按钮实例化同一播放类，仅 gain 不同；观察器不参与播放判断。
        val next = ForegroundFocusTest(this, selectedMode, observe = { update(it.field, it.value) }) { foregroundFocus = null }
        foregroundFocus = next
        next.start()
    }
    fun confirmAudioResult(mode: Int, field: String, value: String) {
        val record = audioModeResults.value[mode] ?: return
        if (record.fields["测试结束"] != "true") return
        val changed = when (field) {
            "语音" -> record.copy(voiceHeard = value)
            "音乐响应" -> record.copy(musicResponse = value)
            "音乐恢复" -> record.copy(musicRecovery = value)
            else -> return
        }
        audioModeResults.value = audioModeResults.value + (mode to changed)
        timeline.add("[${record.id}] 人工确认$field=$value（不是 API 推断）")
    }
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
        get() = settings.snapshot().selectedBluetoothDevice
        set(value) { settings.selectDevice(value); session?.cancel(); foregroundFocus?.cancel() }
    var mode: Int
        get() = prefs.getInt("focusMode", 3)
        set(value) { prefs.edit().putInt("focusMode", value).apply() }

    /** 只接受开发者固定状态码；禁止传入短信、设备标识或异常正文。 */
    fun event(code: String, background: Boolean = false) {
        Stage0Log.state("event", "状态", code)
        val value = "${System.currentTimeMillis()}：$code"
        events.value = value
        prefs.edit().putString("lastEvent", value).apply()
        if (background) prefs.edit().putString("lastBackground", value).apply()
    }

    fun speak(text: String, background: Boolean, finished: () -> Unit = {}, fromSms: Boolean = false, smsEventId: String? = null) {
        if (session == null && foregroundFocus == null && !compatibilityTestActive && !fromSms) timeline.reset()
        timeline.add("进入${if (fromSms) "真实短信严格链路" else "离线中文 / 焦点测试按钮"}；gain=$mode，后台=$background")
        if (compatibilityTestActive || foregroundFocus != null || session != null) {
            timeline.add("播放前置条件=false：另一测试正在运行；最终失败阶段=启动门禁")
            if (fromSms) {
                smsStatus("TTS", "模板已匹配，但另一测试忙碌，未启动"); smsFinished()
                updateSmsEvent(smsEventId, "最终失败节点", "播放前置条件：另一测试忙碌")
                updateSmsEvent(smsEventId, "最终结果", "拒绝播放，未调用 speak")
            }
            event("忙碌，本次拒绝播报", background); finished(); return
        }
        if (fromSms && !settings.snapshot().announcingEnabled) {
            smsFinished(); finished(); return
        }
        val next = SpeechSession(this, background, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, fromSms, smsEventId) {
            session = null
            speaking.value = false
            if (fromSms) smsFinished()
            finished()
        }
        session = next
        speaking.value = true
        next.start(text)
    }
}
