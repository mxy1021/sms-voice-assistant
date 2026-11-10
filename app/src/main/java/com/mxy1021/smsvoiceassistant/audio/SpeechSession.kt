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

/** 一次广播内的有界验证会话：不启动服务，不写入语音或短信文件。 */
class SpeechSession(
    private val app: ProbeApplication,
    val background: Boolean,
    private val finished: () -> Unit
) {
    private val main = app.main
    private val audio = app.getSystemService(AudioManager::class.java)
    private val target = app.target
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
    private val timeout = Runnable { end("超时取消，焦点已释放；本次失败") }
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
        // 包含蓝牙查询、冷启动合成和播放在内总计最多 8 秒。
        main.postDelayed(timeout, 8000)
        try {
            app.registerReceiver(disconnect, IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED), Context.RECEIVER_EXPORTED)
            receiverRegistered = true
            audio.registerAudioDeviceCallback(deviceCallback, main)
            BluetoothProbe(app).inspect(target) { result ->
                if (!ended) {
                    if (result.output == null) end(result.status)
                    else initialize(text, result.output)
                }
            }
        } catch (_: Exception) { end("蓝牙初始化失败，拒绝播报") }
    }

    private fun initialize(text: String, output: AudioDeviceInfo) {
        app.event("TTS 初始化中", background)
        tts = TextToSpeech(app) { status ->
            main.post {
                if (!ended) {
                    try {
                        val engine = tts ?: error("未初始化")
                        if (status != TextToSpeech.SUCCESS) {
                            end("严格 TTS 初始化失败，OnInit=$status"); return@post
                        }
                        val available = engine.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
                        if (available < 0) {
                            end("严格中文语言检测失败，返回值=$available"); return@post
                        }
                        val voices = try { engine.voices } catch (_: Exception) {
                            end("严格 Voice 枚举异常，中文能力未知；请运行手动兼容性测试"); return@post
                        }
                        val voice = voices?.firstOrNull {
                            it.locale.language == "zh" && it.locale.country == "CN" && !it.isNetworkConnectionRequired &&
                                "notInstalled" !in it.features.orEmpty()
                        }
                        if (voice == null) {
                            end("严格筛选未找到可确认的离线中文 Voice；中文能力未知，请运行手动兼容性测试"); return@post
                        }
                        val voiceResult = engine.setVoice(voice)
                        if (voiceResult != TextToSpeech.SUCCESS) {
                            end("严格 TTS 设置 Voice 失败，返回值=$voiceResult"); return@post
                        }
                        if (engine.setAudioAttributes(BluetoothProbe.attributes) != TextToSpeech.SUCCESS) {
                            end("TTS 音频属性设置失败"); return@post
                        }
                        app.event("离线中文资源已选择；开始内存合成（仍需断网听验）", background)
                        engine.setOnUtteranceProgressListener(listener(output))
                        // /dev/null 为系统丢弃设备，不生成文件；PCM 仅经回调留在内存。
                        // 不使用不支持回填 WAV 头的管道，也不缓存真实验证码音频。
                        sink = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_WRITE_ONLY)
                        if (engine.synthesizeToFile(text, Bundle(), sink!!, "stage0") != TextToSpeech.SUCCESS)
                            end("TTS 合成请求失败")
                    } catch (_: Exception) { end("TTS 引擎或丢弃设备不兼容，本次失败") }
                }
            }
        }
    }

    private fun listener(output: AudioDeviceInfo) = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) { main.post { if (!ended) app.event("TTS 合成开始", background) } }
        override fun onBeginSynthesis(utteranceId: String, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
            synchronized(pcm) { rate = sampleRateInHz; encoding = audioFormat; channels = channelCount }
        }
        override fun onAudioAvailable(utteranceId: String, audio: ByteArray) {
            synchronized(pcm) {
                if (ended) return
                if (pcm.size() + audio.size <= 1024 * 1024) pcm.write(audio)
                else main.post { end("合成音频超出阶段 0 内存上限") }
            }
        }
        override fun onDone(utteranceId: String) {
            main.post {
                if (!ended) try { play(output) } catch (_: Exception) { end("音频播放初始化失败") }
            }
        }
        @Deprecated("兼容旧引擎回调")
        override fun onError(utteranceId: String) { main.post { end("TTS 合成失败") } }
        override fun onError(utteranceId: String, errorCode: Int) { main.post { end("TTS 合成失败") } }
        override fun onStop(utteranceId: String, interrupted: Boolean) { main.post { end("TTS 被取消") } }
    }

    private fun permitted() = app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    private fun matches(device: AudioDeviceInfo?) = permitted() && device != null &&
        BluetoothProbe.bluetooth(device) && RoutePolicy.matches(target, listOf(device.address))

    private fun play(output: AudioDeviceInfo) {
        val data = synchronized(pcm) { pcm.toByteArray().also { pcm.reset() } }
        if (rate !in 8000..48000 || channels !in 1..2 ||
            encoding != AudioFormat.ENCODING_PCM_16BIT || data.isEmpty()) {
            data.fill(0); end("引擎未返回支持的 PCM，拒绝播报"); return
        }
        focus = AudioFocusRequest.Builder(app.mode)
            .setAudioAttributes(BluetoothProbe.attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) end("焦点丢失，取消播报")
            }, main).build()
        if (audio.requestAudioFocus(focus!!) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            data.fill(0); end("音频焦点申请失败，拒绝播报"); return
        }
        app.event("焦点已获得；播放器降低音量或暂停及恢复仍待听验", background)
        val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minimum = AudioTrack.getMinBufferSize(rate, mask, encoding)
        if (minimum <= 0) { data.fill(0); end("PCM 配置不支持"); return }
        val player = AudioTrack.Builder().setAudioAttributes(BluetoothProbe.attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(encoding).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, rate * channels / 5 * 2)).build()
        track = player
        if (!player.setPreferredDevice(output)) { data.fill(0); end("指定输出设备失败"); return }
        // 先播放 100ms 静音，实际路由未确认前不向轨道写入验证码 PCM。
        val silence = ByteArray(rate / 10 * channels * 2)
        val silenceWritten = player.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
        if (silenceWritten <= 0) { data.fill(0); end("静音路由探测失败"); return }
        var submitted = false
        fun submit() {
            if (ended || submitted) return
            if (!matches(player.routedDevice)) { data.fill(0); end("实际播放路由无法确认，拒绝播报"); return }
            submitted = true
            playing = true
            player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) { end("播放完成回调已到达，焦点已释放；听验待人工记录") }
                override fun onPeriodicNotification(track: AudioTrack) { }
            }, main)
            player.notificationMarkerPosition = (silenceWritten + data.size) / (channels * 2)
            app.event("实际播放路由地址已匹配，开始播报", background)
            worker.execute {
                try {
                    var offset = 0
                    while (!ended && offset < data.size) {
                        if (!matches(player.routedDevice)) { main.post { end("播放中路由改变，立即取消") }; break }
                        val count = player.write(data, offset, minOf(2048, data.size - offset), AudioTrack.WRITE_BLOCKING)
                        if (count <= 0) { main.post { end("PCM 写入失败") }; break }
                        offset += count
                    }
                } catch (_: Exception) { main.post { end("播放被中断") } }
                finally { data.fill(0) }
            }
        }
        player.addOnRoutingChangedListener({ if (submitted) verifyLiveRoute() else submit() }, main)
        player.play()
        main.postDelayed({ submit() }, 250)
    }

    private fun verifyLiveRoute() { if (!ended && !matches(track?.routedDevice)) end("实际路由改变或权限撤销，取消播报") }
    fun cancel() = end("用户取消，焦点已释放")
    private fun end(code: String) {
        if (ended) return
        ended = true
        main.removeCallbacks(timeout)
        runCatching { track?.stop() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        track = null
        val focusReleased = focus?.let {
            runCatching { audio.abandonAudioFocusRequest(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false)
        } ?: true
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { sink?.close() }
        runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
        if (receiverRegistered) runCatching { app.unregisterReceiver(disconnect) }
        synchronized(pcm) { pcm.reset() }
        worker.shutdownNow()
        app.event(if (focusReleased) code else "焦点释放失败或状态未确认，本次失败", background)
        finished()
    }
}
