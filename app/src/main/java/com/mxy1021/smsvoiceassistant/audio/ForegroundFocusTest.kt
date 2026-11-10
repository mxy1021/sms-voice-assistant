package com.mxy1021.smsvoiceassistant.audio

import android.media.*
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mxy1021.smsvoiceassistant.ProbeApplication
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import java.util.Locale

/** 仅前台固定模拟文本：连接和预期路由 → 焦点 → 直接 speak，不接受真实短信。 */
class ForegroundFocusTest(
    private val app: ProbeApplication,
    private val mode: Int,
    private val observe: (FocusObservation) -> Unit = {},
    private val finished: () -> Unit
) {
    companion object { const val TEST_TEXT = "前台焦点测试，验证码六八三一九五。" }
    private val main = app.main
    private val audio = app.getSystemService(AudioManager::class.java)
    private val target = app.target
    private var engine: TextToSpeech? = null
    private var focus: AudioFocusRequest? = null
    private var ended = false
    private var stage = "蓝牙/预期路由"
    private var spoke = false
    private var started = false
    private var done = false
    private val timeout = Runnable { end("超时：speak已调用=$spoke，onStart=$started，onDone=$done", false) }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = recheck()
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = recheck()
        private fun recheck() {
            if (!ended && spoke) checkRoute { /* 只复核路由，不再次调用 speak。 */ }
        }
    }
    private fun log(value: String) = app.timeline.add(value)
    private fun report(field: String, value: Any?) = observe(FocusObservation(field, value?.toString() ?: "未知"))

    fun start() {
        report("模式/gain", mode)
        report("APP 前台", app.visible)
        report("requestAudioFocus", "未调用")
        report("speak 调用", false)
        report("speak 返回", "未调用")
        listOf("onStart", "onDone", "onError", "onAudioFocusChange").forEach { report(it, "未观测到") }
        report("音乐协调", "API 不能判断，必须人工确认")
        main.postDelayed(timeout, 30_000)
        log("前台最小焦点测试开始；模式=${if (mode == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) "A" else "B"}，gain=$mode")
        log("独立新建 TTS 实例；未复用兼容性测试实例；tts.speak/回调/stop/abandon 均从此记录")
        try {
            audio.registerAudioDeviceCallback(devices, main)
            checkRoute { initialize() }
        } catch (_: Exception) { end("路由初始化异常，错误码未知", false) }
    }
    private fun checkRoute(ready: () -> Unit) {
        try {
            BluetoothProbe(app).inspect(target) { route ->
                if (!ended) {
                    log("目标蓝牙已连接=${route.connected}；预期路由=${route.status}")
                    log("播放前置条件=${route.output != null && app.visible}（前台=${app.visible}）")
                    report("APP 前台", app.visible)
                    report("目标耳机连接", route.connected)
                    report("预期路由", route.status)
                    report("前置条件", route.output != null && app.visible)
                    if (route.output == null || !app.visible) {
                        stage = "蓝牙/预期路由/前台门禁"
                        end("条件不满足，禁止播放", false)
                    } else ready()
                }
            }
        } catch (_: Exception) { stage = "蓝牙/预期路由"; end("查询异常，错误码未知", false) }
    }
    private fun initialize() {
        stage = "TTS 初始化"
        try {
            engine = TextToSpeech(app) { result -> main.post { if (!ended) initialized(result) } }
        } catch (_: Exception) { end("创建 TTS 异常", false) }
    }
    private fun initialized(result: Int) {
        log("OnInit=$result")
        if (result != TextToSpeech.SUCCESS) return end("初始化失败", false)
        try {
            val tts = engine ?: return end("TTS 实例为空", false)
            stage = "中文语言/Voice"
            val language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            log("setLanguage(zh-CN)=$language")
            if (!TtsCompatibilityPolicy.mayManuallySpeak(result, language)) return end("中文设置失败或未知", false)
            val voices = runCatching { tts.voices }.getOrNull()
            log("getVoices数量=${voices?.size ?: "未知"}；仅人工模拟测试允许语言默认回退")
            val offline = voices?.filter {
                TtsCompatibilityPolicy.simplifiedCandidate(it.locale) &&
                    TtsCompatibilityPolicy.explicitlyOffline(it.isNetworkConnectionRequired,
                        it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
            }?.sortedBy { it.name }?.sortedBy { if (it.locale.country == "CN") 0 else 1 }?.firstOrNull()
            if (offline != null) {
                val selected = tts.setVoice(offline)
                log("setVoice=$selected；Locale=${offline.locale.toLanguageTag()}；候选排序与兼容性测试一致")
                if (selected != TextToSpeech.SUCCESS) {
                    val restored = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    log("恢复 setLanguage=$restored")
                    if (!TtsCompatibilityPolicy.mayManuallySpeak(result, restored)) return end("恢复语言失败", false)
                }
            } else log("没有明确离线 Voice 候选；仅本次前台固定文本沿用 setLanguage 默认选择")
            stage = "音频属性"
            val attributes = tts.setAudioAttributes(BluetoothProbe.attributes)
            log("TTS setAudioAttributes=$attributes；TTS/预期路由/焦点均为 USAGE_MEDIA + CONTENT_TYPE_SPEECH")
            log("基础兼容性对照未显式设置属性；本测试不设置 AudioTrack 或强制路由，实际输出须听验")
            if (attributes != TextToSpeech.SUCCESS) return end("音频属性设置失败", false)
            val listener = tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onBeginSynthesis(id: String, rate: Int, format: Int, channels: Int) = callback {
                    log("onBeginSynthesis：rate=$rate，format=$format，channels=$channels")
                }
                override fun onStart(id: String) = callback { started = true; report("onStart", "已收到"); log("onStart：TTS 已启动") }
                override fun onDone(id: String) = callback { done = true; report("onDone", "已收到"); log("onDone：直接 speak 播放完成回调"); end("完成，实际听感与音乐响应待人工记录", true) }
                @Deprecated("兼容旧引擎")
                override fun onError(id: String) = callback { report("onError", "已收到，错误码未知"); end("onError，错误码未知", false) }
                override fun onError(id: String, code: Int) = callback { report("onError", code); end("onError=$code", false) }
                override fun onStop(id: String, interrupted: Boolean) = callback { end("onStop：interrupted=$interrupted", false) }
            })
            log("设置 UtteranceProgressListener=$listener")
            if (listener != TextToSpeech.SUCCESS) return end("监听器设置失败", false)
            // 冷初始化后再次检查，防止初始化期间输出已经切换。
            stage = "播报前路由复查"
            checkRoute { requestAndSpeak(tts) }
        } catch (_: Exception) { end("API 异常，错误码未知", false) }
    }
    private fun requestAndSpeak(tts: TextToSpeech) {
        try {
            stage = "音频焦点"
            focus = AudioFocusRequest.Builder(mode).setAudioAttributes(BluetoothProbe.attributes)
                .setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener({ value ->
                    if (!ended) {
                        log("onAudioFocusChange=$value")
                        report("onAudioFocusChange", value)
                        if (value != AudioManager.AUDIOFOCUS_GAIN) end("焦点丢失，取消播放", false)
                    }
                }, main).build()
            val result = audio.requestAudioFocus(focus!!)
            report("requestAudioFocus", result)
            log("requestAudioFocus=$result（1=授予，0=失败，2=延迟）")
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return end("焦点失败，未调用 tts.speak", false)
            stage = "TTS speak/播放回调"
            spoke = true
            report("speak 调用", true)
            log("真正执行 tts.speak：固定人工文本，无短信解析、无 PCM 中转")
            val request = tts.speak(TEST_TEXT, TextToSpeech.QUEUE_FLUSH, Bundle(), "stage0-foreground-focus")
            report("speak 返回", request)
            log("tts.speak 返回值=$request")
            if (request != TextToSpeech.SUCCESS) end("speak 请求失败", false)
        } catch (_: Exception) { end("请求 API 异常，错误码未知", false) }
    }
    private fun callback(action: () -> Unit) { main.post { if (!ended) action() } }
    fun cancel() = end("用户取消或离开前台", false)
    private fun end(reason: String, success: Boolean) {
        if (ended) return
        ended = true
        main.removeCallbacks(timeout)
        log("最终失败阶段=${if (success) "无" else stage}；$reason")
        log("状态汇总：speak=$spoke，onStart=$started，onDone=$done；未出现的回调即未观测到，不推测已发生")
        if (engine != null) {
            log("调用 tts.stop：speak前=${!spoke}，播放完成前=${!done}；原因=$reason")
            log("tts.stop 返回=${runCatching { engine!!.stop() }.getOrNull() ?: "异常"}")
            log("调用 tts.shutdown；${runCatching { engine!!.shutdown(); "完成" }.getOrDefault("异常")}")
            engine = null
        } else log("没有创建 TTS；未调用 stop/shutdown")
        focus?.let {
            log("调用 abandonAudioFocusRequest：onDone前=${!done}；原因=$reason")
            report("abandon 时点", "onDone前=${!done}；原因=$reason")
            val released = runCatching { audio.abandonAudioFocusRequest(it) }.getOrNull()
            log("abandonAudioFocusRequest 返回=${released ?: "异常"}")
            report("abandon 返回", released)
        } ?: run { log("未构建焦点请求，未调用 abandonAudioFocusRequest"); report("abandon 时点", "未调用，未构建焦点请求") }
        runCatching { audio.unregisterAudioDeviceCallback(devices) }
        app.event(if (success) "前台最小焦点测试回调完成，听验待记录" else "前台最小焦点测试失败：$stage")
        report("最终失败节点", if (success) "无 API 失败" else "$stage：$reason")
        report("API 结果", if (success) "播放完成回调已收到，实际有声未验证" else "未完成；语音和音乐结果分别确认")
        report("测试结束", true)
        finished()
    }
}
