package com.mxy1021.smsvoiceassistant.audio

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mxy1021.smsvoiceassistant.ProbeApplication
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import com.mxy1021.smsvoiceassistant.sms.SmsSafetyWindow
import com.mxy1021.smsvoiceassistant.sms.PcmBudget

/** 一次广播内的有界验证会话：不启动服务，不写入语音或短信文件。 */
class SpeechSession(
    private val app: ProbeApplication,
    val background: Boolean,
    private val focusMode: Int,
    private val fromSms: Boolean,
    private val smsEventId: String? = null,
    private val finished: () -> Unit
) {
    val isSms get() = fromSms
    private val main = app.main
    private val audio = app.getSystemService(AudioManager::class.java)
    private val target = app.target
    private val permit = app.settings.permit(fromSms)
    private fun authorized() = permit?.let { app.settings.valid(it) } == true && permitted() &&
        (!fromSms || app.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    private var tts: TextToSpeech? = null
    private var sink: ParcelFileDescriptor? = null
    private var focus: AudioFocusRequest? = null
    private var track: AudioTrack? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val pcm = ByteArrayOutputStream()
    private var rate = 0
    private var encoding = 0
    private var channels = 0
    @Volatile private var ended = false
    private var playing = false
    private var receiverRegistered = false
    private var stage = "蓝牙/预期路由"
    private var synthesisStarted = false
    private var synthesisDone = false
    private var playbackDone = false
    private var synthesisRequested = false
    private var focusGranted = false
    @Volatile private var pcmOverflow = false
    private val startElapsed = app.receivedAt(smsEventId) ?: SystemClock.elapsedRealtime()
    private fun mark(point: String, at: Long = SystemClock.elapsedRealtime()) {
        if (fromSms) app.smsTime(smsEventId, point, at)
        else Stage0Log.timing(null, point, at, startElapsed)
    }
    private fun alive(next: String): Boolean {
        if (ended) return false
        if (!authorized()) { end("运行状态、配置或权限已改变，取消播报"); return false }
        if (!SmsSafetyWindow.withinBudget(startElapsed, SystemClock.elapsedRealtime())) {
            stage = "elapsedRealtime预算超时：$next"
            mark("超时退出")
            end("恢复执行时已超过8秒，丢弃在途语音，不延长广播")
            return false
        }
        return true
    }
    private fun log(value: String) = app.timeline.add(value)
    private fun sms(key: String, value: String) { if (fromSms) app.smsStatus(key, value) }
    private fun record(key: String, value: Any?) { if (fromSms) app.updateSmsEvent(smsEventId, key, value?.toString() ?: "未知") }
    private val timeout = Runnable {
        mark("Handler超时回调")
        end("8 秒总预算超时；实际elapsed=${SystemClock.elapsedRealtime() - startElapsed}ms；合成请求=$synthesisRequested，onStart=$synthesisStarted，合成完成=$synthesisDone，播放完成=$playbackDone")
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == track?.routedDevice?.id || it.address.equals(target, true) })
                end("目标输出被移除，取消播报")
            else if (playing) verifyLiveRoute()
        }
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (playing) verifyLiveRoute()
        }
    }
    private val disconnect = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (device?.address.equals(target, true)) end("目标蓝牙断开，取消播报")
        }
    }

    fun start(text: String) {
        mark("PCM会话开始")
        if (!alive("会话开始")) return
        record("speak调用", "false（严格文件合成/PCM，不调用 speak）")
        record("gain", focusMode)
        log("原 PCM 焦点测试开始；模式=${if (focusMode == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) "A" else "B"}，gain=$focusMode；8 秒包含冷初始化")
        log("tts.speak=不调用；请求路径=synthesizeToFile(/dev/null) → PCM → 焦点 → AudioTrack；与直接 speak 不同")
        log("独立创建新 TTS，不复用兼容性实例；焦点回调及 stop/abandon 在本时序记录")
        // 包含蓝牙查询、冷启动合成和播放在内总计最多 8 秒。
        main.postDelayed(timeout, maxOf(0L, SmsSafetyWindow.SESSION_BUDGET_MS - (SystemClock.elapsedRealtime() - startElapsed)))
        try {
            app.registerReceiver(disconnect, IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED), Context.RECEIVER_EXPORTED)
            receiverRegistered = true
            audio.registerAudioDeviceCallback(deviceCallback, main)
            mark("蓝牙查询开始")
            BluetoothProbe(app).inspect(target) { result ->
                mark("蓝牙查询完成")
                if (alive("蓝牙回调")) {
                    log("目标蓝牙已连接=${result.connected}；预期音频路由=${result.status}")
                    log("播放前置条件=${result.output != null}")
                    sms("蓝牙", if (result.connected) "目标设备已连接" else "模板已匹配，目标设备未连接或连接无法确认")
                    sms("路由", result.status)
                    record("目标耳机连接", result.connected)
                    record("预期路由", result.status)
                    record("前置条件", result.output != null)
                    if (result.output == null) end(result.status)
                    else initialize(text, result.output)
                }
            }
        } catch (_: Exception) { end("蓝牙初始化失败，拒绝播报") }
    }

    private fun initialize(text: String, output: AudioDeviceInfo) {
        if (!alive("TTS初始化开始")) return
        mark("TTS初始化开始")
        stage = "TTS 初始化"
        log("开始初始化 TTS（默认引擎）")
        app.event("TTS 初始化中", background)
        tts = TextToSpeech(app) { status ->
            val arrived = SystemClock.elapsedRealtime()
            main.post {
                mark("TTS初始化回调到达", arrived)
                if (alive("TTS初始化回调")) {
                    mark("TTS初始化完成")
                    try {
                        val engine = tts ?: error("未初始化")
                        log("OnInit=$status")
                        record("TTS初始化", status)
                        if (status != TextToSpeech.SUCCESS) {
                            end("严格 TTS 初始化失败，OnInit=$status"); return@post
                        }
                        stage = "严格中文语言/Voice 筛选"
                        val available = engine.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
                        log("isLanguageAvailable(zh-CN)=$available；原路径未调用 setLanguage")
                        if (available < 0) {
                            end("严格中文语言检测失败，返回值=$available"); return@post
                        }
                        val voices = try { engine.voices } catch (_: Exception) {
                            end("严格 Voice 枚举异常，中文能力未知；请运行手动兼容性测试"); return@post
                        }
                        val candidates = voices?.filter {
                            OfflineVoicePolicy.accepts(it.locale, it.isNetworkConnectionRequired,
                                it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                        }?.sortedBy { it.name }?.sortedBy { if (it.locale.country == "CN") 0 else 1 }
                        val voice = candidates?.firstOrNull()
                        log("getVoices数量=${voices?.size ?: "未知"}；严格离线候选=${voice != null}")
                        log("严格候选数量=${candidates?.size ?: "未知"}；允许简体脚本 Hans；未知网络/安装状态仍拒绝")
                        if (voice == null) {
                            end("严格筛选未找到可确认的离线中文 Voice；中文能力未知，请运行手动兼容性测试"); return@post
                        }
                        val voiceResult = engine.setVoice(voice)
                        log("setVoice=$voiceResult")
                        log("严格选中 Locale=${voice.locale.toLanguageTag()}；需要网络=${voice.isNetworkConnectionRequired}；notInstalled=${voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)}")
                        if (voiceResult != TextToSpeech.SUCCESS) {
                            end("严格 TTS 设置 Voice 失败，返回值=$voiceResult"); return@post
                        }
                        stage = "TTS 音频属性"
                        val attributes = engine.setAudioAttributes(BluetoothProbe.attributes)
                        log("TTS setAudioAttributes=$attributes；预期路由/TTS/焦点/AudioTrack 均为 MEDIA + SPEECH")
                        if (attributes != TextToSpeech.SUCCESS) {
                            end("TTS 音频属性设置失败"); return@post
                        }
                        app.event("离线中文资源已选择；开始内存合成（仍需断网听验）", background)
                        val listenerResult = engine.setOnUtteranceProgressListener(listener(output))
                        log("设置 UtteranceProgressListener=$listenerResult")
                        if (listenerResult != TextToSpeech.SUCCESS) { end("设置合成监听器失败"); return@post }
                        // /dev/null 为系统丢弃设备，不生成文件；PCM 仅经回调留在内存。
                        // 不使用不支持回填 WAV 头的管道，也不缓存真实验证码音频。
                        stage = "文件合成/PCM 回调"
                        sink = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_WRITE_ONLY)
                        if (!alive("PCM合成请求")) return@post
                        synthesisRequested = true
                        mark("PCM合成请求")
                        log("调用 synthesizeToFile；尚未申请焦点，不调用 tts.speak")
                        val request = engine.synthesizeToFile(text, Bundle(), sink!!, "stage0")
                        log("synthesizeToFile 返回=$request")
                        record("文件合成请求", request)
                        sms("TTS", "已请求文件合成，speak 未使用；等待合成回调")
                        if (request != TextToSpeech.SUCCESS)
                            end("TTS 合成请求失败")
                    } catch (_: Exception) { end("TTS 引擎或丢弃设备不兼容，本次失败") }
                }
            }
        }
    }

    private fun listener(output: AudioDeviceInfo) = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) { main.post { if (!ended) {
            synthesisStarted = true
            log("onStart：文件合成开始，不代表已经播放")
            record("onStart", "已收到（文件合成，非直接 speak）")
            sms("TTS", "TTS 合成已启动，尚未确认音频播放完成")
            app.event("TTS 合成开始", background)
        } } }
        override fun onBeginSynthesis(utteranceId: String, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
            val arrived = SystemClock.elapsedRealtime()
            synchronized(pcm) { rate = sampleRateInHz; encoding = audioFormat; channels = channelCount }
            main.post { mark("PCM合成开始", arrived); if (!ended) log("onBeginSynthesis：rate=$sampleRateInHz，format=$audioFormat，channels=$channelCount") }
        }
        override fun onAudioAvailable(utteranceId: String, audio: ByteArray) {
            synchronized(pcm) {
                if (ended) return
                if (!pcmOverflow && pcm.size() + audio.size <= PcmBudget.MAX_BYTES) pcm.write(audio)
                else { pcmOverflow = true; main.post { end("合成音频超出内存上限，整条拒绝") } }
            }
        }
        override fun onDone(utteranceId: String) {
            val arrived = SystemClock.elapsedRealtime()
            main.post {
                mark("PCM合成完成", arrived)
                if (alive("PCM合成完成")) try {
                    synthesisDone = true
                    log("onDone：文件合成完成，PCM字节数=${synchronized(pcm) { pcm.size() }}；接下来才申请焦点与播放")
                    record("onDone", "已收到（文件合成，尚不代表实际播放完成）")
                    play(output)
                } catch (_: Exception) { end("音频播放初始化失败") }
            }
        }
        @Deprecated("兼容旧引擎回调")
        override fun onError(utteranceId: String) { main.post { if (!ended) { record("onError", "已收到，错误码未知"); log("onError：错误码未知"); end("TTS 合成失败") } } }
        override fun onError(utteranceId: String, errorCode: Int) { main.post { if (!ended) { record("onError", errorCode); log("onError=$errorCode"); end("TTS 合成失败，错误码=$errorCode") } } }
        override fun onStop(utteranceId: String, interrupted: Boolean) { main.post { if (!ended) { log("onStop：interrupted=$interrupted"); end("TTS 被取消") } } }
    }

    private fun permitted() = app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    private fun matches(device: AudioDeviceInfo?) = permitted() && device != null &&
        BluetoothProbe.bluetooth(device) && RoutePolicy.matches(target, listOf(device.address))

    private fun play(output: AudioDeviceInfo) {
        if (!alive("PCM播放准备")) return
        stage = "PCM 格式校验"
        val data = synchronized(pcm) { pcm.toByteArray().also { pcm.reset() } }
        if (rate !in 8000..48000 || channels !in 1..2 ||
            encoding != AudioFormat.ENCODING_PCM_16BIT || data.isEmpty() || pcmOverflow) {
            data.fill(0); end("引擎未返回支持的 PCM，拒绝播报"); return
        }
        val duration = PcmBudget.durationMs(data.size, rate, channels)
        val elapsed = SystemClock.elapsedRealtime() - startElapsed
        if (duration == null || !PcmBudget.fits(duration, elapsed)) {
            data.fill(0); end("完整音频超过5秒或剩余8秒预算不足，整条未播报"); return
        }
        record("PCM时长ms", duration)
        log("PCM 格式校验通过；当前才开始请求焦点")
        stage = "音频焦点"
        focus = AudioFocusRequest.Builder(focusMode)
            .setAudioAttributes(BluetoothProbe.attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (!ended) {
                    log("onAudioFocusChange=$change")
                    if (change != AudioManager.AUDIOFOCUS_GAIN) end("焦点丢失，取消播报")
                }
            }, main).build()
        if (!alive("申请音频焦点")) { data.fill(0); return }
        mark("requestAudioFocus开始")
        app.environment(smsEventId, "申请焦点状态")
        val focusResult = audio.requestAudioFocus(focus!!)
        mark("requestAudioFocus完成")
        record("焦点申请时APP前台", app.visible)
        record("requestAudioFocus", focusResult)
        log("requestAudioFocus=$focusResult（1=授予，0=失败，2=延迟）")
        sms("焦点", if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "焦点已获得；等待实际播放" else "蓝牙/预期路由满足，但焦点申请失败：$focusResult")
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            data.fill(0); end("音频焦点申请失败，拒绝播报"); return
        }
        focusGranted = true
        stage = "AudioTrack 初始化/静音实际路由"
        app.event("焦点已获得；播放器降低音量或暂停及恢复仍待听验", background)
        val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minimum = AudioTrack.getMinBufferSize(rate, mask, encoding)
        if (minimum <= 0) { data.fill(0); end("PCM 配置不支持"); return }
        if (!alive("AudioTrack创建")) { data.fill(0); return }
        mark("AudioTrack创建开始")
        val player = AudioTrack.Builder().setAudioAttributes(BluetoothProbe.attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(encoding).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, rate * channels / 5 * 2)).build()
        track = player
        mark("AudioTrack创建完成")
        record("AudioTrack.state", player.state)
        val preferred = player.setPreferredDevice(output)
        log("AudioTrack setPreferredDevice=$preferred")
        if (!preferred) { data.fill(0); end("指定输出设备失败"); return }
        // 先播放 100ms 静音，实际路由未确认前不向轨道写入验证码 PCM。
        val silence = ByteArray(rate / 10 * channels * 2)
        val silenceWritten = player.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
        if (silenceWritten <= 0) { data.fill(0); end("静音路由探测失败"); return }
        var submitted = false
        fun submit() {
            if (ended || submitted) return
            if (!alive("实际路由回调")) { data.fill(0); return }
            val matched = matches(player.routedDevice)
            log("静音轨道实际路由匹配=$matched；未匹配前不写入语音 PCM")
            record("实际路由", matched)
            if (!matched) { data.fill(0); end("实际播放路由无法确认，拒绝播报"); return }
            if (!PcmBudget.fits(duration, SystemClock.elapsedRealtime() - startElapsed)) {
                data.fill(0); end("路由检查后完整音频预算不足，整条未播报"); return
            }
            submitted = true
            playing = true
            stage = "AudioTrack 播放/完成回调"
            sms("TTS", "焦点成功，合成已完成，AudioTrack 播放中；尚未完成")
            player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) {
                    if (!ended) {
                        mark("播放完成")
                        if (!alive("播放完成回调")) return
                        playbackDone = true
                        log("AudioTrack 播放标记完成")
                        record("实际播放完成", "AudioTrack 完成标记已收到，听验未验证")
                        end("播放完成回调已到达，焦点已释放；听验待人工记录")
                    }
                }
                override fun onPeriodicNotification(track: AudioTrack) { }
            }, main)
            player.notificationMarkerPosition = (silenceWritten + data.size) / (channels * 2)
            app.event("实际播放路由地址已匹配，开始播报", background)
            worker.execute {
                try {
                    var offset = 0
                    while (!ended && offset < data.size) {
                        if (!SmsSafetyWindow.withinBudget(startElapsed, SystemClock.elapsedRealtime())) {
                            main.post { if (!ended) { stage = "PCM写入elapsed超时"; mark("超时退出"); end("写入前预算已过期，丢弃语音") } }
                            break
                        }
                        val count = permit?.let { token -> app.settings.withPermit(token) {
                            if (ended || !authorized() || !matches(player.routedDevice)) -1
                            else player.write(data, offset, minOf(2048, data.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                        } } ?: -1
                        if (count < 0) { main.post { end("运行状态、权限或路由改变，取消 PCM 写入") }; break }
                        if (count == 0) { Thread.sleep(10); continue }
                        if (offset == 0) {
                            val writtenAt = SystemClock.elapsedRealtime()
                            main.post { mark("音频实际写入", writtenAt) }
                        }
                        offset += count
                    }
                    if (!ended && offset == data.size) {
                        val writtenAt = SystemClock.elapsedRealtime()
                        main.post { mark("音频全部写入", writtenAt) }
                    }
                } catch (_: Exception) { main.post { end("播放被中断") } }
                finally { data.fill(0) }
            }
        }
        player.addOnRoutingChangedListener({ if (submitted) verifyLiveRoute() else submit() }, main)
        if (!alive("AudioTrack.play")) { data.fill(0); return }
        mark("AudioTrack.play")
        player.play()
        record("AudioTrack.playState", player.playState)
        log("AudioTrack.play 已执行；等待实际路由，随后写入 PCM")
        main.postDelayed({ submit() }, 250)
    }

    private fun verifyLiveRoute() { if (!ended && !matches(track?.routedDevice)) end("实际路由改变或权限撤销，取消播报") }
    fun cancel() = end("用户取消，焦点已释放")
    private fun end(code: String) {
        if (ended) return
        ended = true
        main.removeCallbacks(timeout)
        log("最终失败阶段=${if (playbackDone) "无" else stage}；$code")
        record("最终失败节点", if (playbackDone) "无 API 失败" else stage)
        record("最终结果", if (playbackDone) "PCM 播放完成回调，听验未验证" else "未完成：$code")
        record("后台限制", "未验证；${if (background) "后台失败需对照 AudioHardening 和生命周期日志" else "前台执行不能证明后台可播放"}")
        log("状态汇总：tts.speak未使用，合成请求=$synthesisRequested，onStart=$synthesisStarted，onDone(合成)=$synthesisDone，播放完成=$playbackDone")
        if (track != null) log("调用 AudioTrack.stop/flush/release；播放完成前=${!playbackDone}；原因=$code")
        runCatching { track?.stop() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        track = null
        val focusReleased = focus?.let {
            mark("焦点释放开始")
            log("调用 abandonAudioFocusRequest：合成onDone前=${!synthesisDone}，实际播放完成前=${!playbackDone}；原因=$code")
            runCatching { audio.abandonAudioFocusRequest(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false).also { mark("焦点释放完成") }
        } ?: true
        log(if (focus == null) "未申请焦点，未调用 abandonAudioFocusRequest" else "焦点释放成功=$focusReleased")
        if (tts != null) {
            log("调用 tts.stop：合成请求前=${!synthesisRequested}，合成onDone前=${!synthesisDone}，实际播放完成前=${!playbackDone}；原因=$code")
            log("tts.stop 返回=${runCatching { tts!!.stop() }.getOrNull() ?: "异常"}")
            log("调用 tts.shutdown；${runCatching { tts!!.shutdown(); "完成" }.getOrDefault("异常")}")
        } else log("未创建 TTS，没有调用 stop/shutdown")
        runCatching { sink?.close() }
        runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
        if (receiverRegistered) runCatching { app.unregisterReceiver(disconnect) }
        synchronized(pcm) { pcm.reset() }
        worker.shutdownNow()
        sms("TTS", when {
            playbackDone -> "合成和播放完成回调已到达；实际耳机听验待记录"
            synthesisStarted -> "TTS 合成已启动但播放未完成；失败阶段=$stage"
            focusGranted -> "焦点成功但 TTS/播放未启动；失败阶段=$stage"
            else -> "TTS/播放未启动或未收到启动回调；失败阶段=$stage"
        })
        if (fromSms && background && !playbackDone)
            sms("后台", "后台处理未完成，最后阶段=$stage；可能是代码/引擎/系统限制，原因未确认")
        if (fromSms && background && playbackDone) sms("后台", "本次后台完成回调已到达；真实听验仍须记录")
        app.event(if (focusReleased) code else "焦点释放失败或状态未确认，本次失败", background)
        finished()
    }
}
