package com.mxy1021.smsvoiceassistant.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** 只接受固定人工文本，不接收短信，不使用文件合成、蓝牙门禁或应用音频焦点请求。 */
class TtsCompatibilityProbe(context: Context, private val finished: () -> Unit) {
    companion object {
        const val TEST_TEXT = "语音兼容性测试，验证码七九一四五二。"
    }
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ended = false
    private var started = false
    private var stage = "尚未开始"
    private val fields = linkedMapOf(
        "默认引擎名称" to "未知", "默认引擎包名" to "未知",
        "实际使用引擎" to "未知（Android 公开 API 不提供实际绑定包名，不能以默认值冒充）",
        "初始化结果" to "未知", "getVoices 数量" to "未知", "可用中文 Voice" to "未知",
        "isLanguageAvailable(zh-CN)" to "未知", "setLanguage(zh-CN)" to "未知",
        "setVoice 结果" to "未知", "当前选择 Voice" to "未知", "speak 返回值" to "未知",
        "回调" to "尚未收到", "失败阶段 / 错误码" to "无", "资源释放" to "尚未执行",
        "诊断查询异常" to "无",
        "离线结论" to "待验证：须关闭 Wi-Fi 和移动数据并现场听验"
    )
    val diagnostics = MutableStateFlow(fields.toMap())
    private val callbacks = mutableListOf<String>()
    private val timeout = Runnable { fail(stage, "30 秒超时；引擎可能未回调") }

    private fun put(key: String, value: String) {
        fields[key] = value
        diagnostics.value = fields.toMap()
    }

    private fun <T> query(label: String, action: () -> T): T? = try { action() }
    catch (_: Exception) {
        put(label, "未知（API 查询异常）")
        val previous = fields["诊断查询异常"]
        put("诊断查询异常", if (previous == "无") label else "$previous；$label")
        null
    }

    fun start() {
        if (started || ended) return
        started = true
        stage = "初始化"
        main.postDelayed(timeout, 30_000)
        try {
            engine = TextToSpeech(context) { status ->
                // 即便厂商同步触发回调，也在实例赋值完成后执行检测。
                main.post { if (!ended) onInitialized(status) }
            }
        } catch (_: Exception) { fail(stage, "构造引擎异常；错误码未知") }
    }

    private fun onInitialized(status: Int) {
        put("初始化结果", "$status（${if (status == TextToSpeech.SUCCESS) "成功" else "失败"}）")
        val tts = engine ?: return fail("初始化", "实例未知")
        val defaultPackage = query("默认引擎包名") { tts.defaultEngine }
        put("默认引擎包名", defaultPackage ?: "未知")
        val engines = query("默认引擎名称") { tts.engines }
        put("默认引擎名称", engines?.firstOrNull { it.name == defaultPackage }?.label ?: "未知")
        if (status != TextToSpeech.SUCCESS) return fail("初始化", "OnInit=$status")

        stage = "中文语言检测"
        val available = query("isLanguageAvailable(zh-CN)") { tts.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE) }
        if (available != null) put("isLanguageAvailable(zh-CN)", languageResult(available))
        val language = query("setLanguage(zh-CN)") { tts.setLanguage(Locale.SIMPLIFIED_CHINESE) }
        if (language != null) put("setLanguage(zh-CN)", languageResult(language))

        stage = "Voice 枚举"
        val voices = query("getVoices 数量") { tts.voices }
        put("getVoices 数量", voices?.size?.toString() ?: "未知（未返回枚举，不能据此判断不可用）")
        val chinese = voices?.filter { TtsCompatibilityPolicy.chinese(it.locale) }
            ?.sortedBy { it.name }
        put("可用中文 Voice", when {
            chinese == null -> "未知"
            chinese.isEmpty() -> "未枚举到中文 Voice；不代表中文合成不可用"
            else -> chinese.joinToString("\n") { describe(it) }
        })

        // 只有元数据明确的简体中文离线 Voice 才优先设置；未知或失败时保留语言默认选择。
        val offline = chinese?.filter {
            TtsCompatibilityPolicy.simplifiedCandidate(it.locale) &&
                TtsCompatibilityPolicy.explicitlyOffline(network(it), notInstalled(it))
        }?.sortedBy { if (it.locale.country == "CN") 0 else 1 }?.firstOrNull()
        if (TtsCompatibilityPolicy.mayManuallySpeak(status, language) && offline != null) {
            val selected = query("setVoice 结果") { tts.setVoice(offline) }
            if (selected != null) put("setVoice 结果", "$selected（${if (selected == 0) "成功" else "失败，恢复 setLanguage"}）")
            if (selected != TextToSpeech.SUCCESS) {
                val restored = query("setLanguage(zh-CN)") { tts.setLanguage(Locale.SIMPLIFIED_CHINESE) }
                if (restored != null) put("setLanguage(zh-CN)", "恢复：${languageResult(restored)}")
                showSelectedVoice(tts)
                if (!TtsCompatibilityPolicy.mayManuallySpeak(status, restored))
                    return fail("中文语言恢复", "setLanguage=${restored ?: "未知"}")
            }
        } else put("setVoice 结果", "未调用：无明确离线候选或语言设置未成功")
        showSelectedVoice(tts)
        if (!TtsCompatibilityPolicy.mayManuallySpeak(status, language))
            return fail("中文语言设置", "setLanguage=${language ?: "未知"}；未调用 speak")

        stage = "speak 请求"
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = callback("onStart")
            override fun onBeginSynthesis(utteranceId: String, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) =
                callback("onBeginSynthesis：采样率=$sampleRateInHz，编码=$audioFormat，声道=$channelCount")
            override fun onDone(utteranceId: String) = callback("onDone") { release() }
            @Deprecated("兼容旧引擎回调")
            override fun onError(utteranceId: String) = callback("onError（错误码未知）") { fail("合成/播放回调", "未知") }
            override fun onError(utteranceId: String, errorCode: Int) =
                callback("onError($errorCode)") { fail("合成/播放回调", errorCode.toString()) }
            override fun onStop(utteranceId: String, interrupted: Boolean) =
                callback("onStop（中断=$interrupted）") { fail("合成/播放回调", "播放被停止，错误码未知") }
        }
        try {
            val result = tts.setOnUtteranceProgressListener(listener)
            if (result != TextToSpeech.SUCCESS) return fail("监听器设置", result.toString())
            val resultSpeak = tts.speak(TEST_TEXT, TextToSpeech.QUEUE_FLUSH, Bundle(), "stage0-manual-compatibility")
            put("speak 返回值", "$resultSpeak（${if (resultSpeak == 0) "请求已接受，不代表已经听到" else "请求失败"}）")
            if (resultSpeak != TextToSpeech.SUCCESS) fail("speak 请求", resultSpeak.toString())
            else stage = "等待合成/播放回调"
        } catch (_: Exception) { fail(stage, "API 异常，错误码未知") }
    }

    private fun showSelectedVoice(tts: TextToSpeech) {
        val voice = query("当前选择 Voice") { tts.voice }
        put("当前选择 Voice", voice?.let { describe(it) } ?: "未知（不据此判断语言不可用）")
    }
    private fun network(voice: Voice): Boolean? = query("Voice 网络标记") { voice.isNetworkConnectionRequired }
    private fun notInstalled(voice: Voice): Boolean? = query("Voice 安装标记") {
        voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
    }
    private fun describe(voice: Voice) = "${voice.name}；Locale=${voice.locale.toLanguageTag()}；" +
        "需要网络=${network(voice)?.toString() ?: "未知"}；notInstalled=${notInstalled(voice)?.toString() ?: "未知"}"

    private fun callback(value: String, after: () -> Unit = {}) {
        main.post {
            if (!ended) {
                callbacks.add(value)
                put("回调", callbacks.takeLast(16).joinToString("\n"))
                after()
            }
        }
    }
    private fun languageResult(result: Int) = "$result（${when (result) {
        TextToSpeech.LANG_AVAILABLE -> "语言支持"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "语言和地区支持"
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "语言、地区和变体支持"
        TextToSpeech.LANG_MISSING_DATA -> "缺少数据"
        TextToSpeech.LANG_NOT_SUPPORTED -> "不支持"
        else -> "未知返回值"
    }}）"
    private fun fail(failedStage: String, error: String) {
        if (ended) return
        put("失败阶段 / 错误码", "$failedStage：$error")
        release()
    }
    fun cancel() = fail("用户取消/页面离开", "未继续测试，错误码不适用")
    private fun release() {
        if (ended) return
        ended = true
        main.removeCallbacks(timeout)
        val current = engine
        val stopped = runCatching { current?.stop() }.isSuccess
        val closed = runCatching { current?.shutdown() }.isSuccess
        engine = null
        put("资源释放", when {
            current == null -> "无可释放引擎实例"
            stopped && closed -> "已调用 stop / shutdown"
            else -> "释放调用异常，状态未知"
        })
        finished()
    }
}
