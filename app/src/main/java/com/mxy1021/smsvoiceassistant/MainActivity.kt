package com.mxy1021.smsvoiceassistant

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import com.mxy1021.smsvoiceassistant.sms.OtpParser
import com.mxy1021.smsvoiceassistant.audio.TtsCompatibilityProbe
import com.mxy1021.smsvoiceassistant.audio.ForegroundFocusTest
import com.mxy1021.smsvoiceassistant.audio.Stage0Log

class MainActivity : ComponentActivity() {
    private val app get() = application as ProbeApplication
    private var revision by mutableIntStateOf(0)
    private var compatibilityRunning by mutableStateOf(false)
    private var offlineConfirmed by mutableStateOf(false)
    private lateinit var compatibility: TtsCompatibilityProbe
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        revision++
        app.event("权限流程已返回，请核对实际授权状态")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        compatibility = newCompatibilityProbe()
        setContent { MaterialTheme { Surface { ProbeScreen() } } }
    }
    override fun onStart() { super.onStart(); app.visible = true; revision++; Stage0Log.state("lifecycle", "Activity", "onStart；前台=true") }
    override fun onStop() {
        compatibility.cancel()
        if (app.foregroundFocus != null) app.timeline.add("Activity.onStop：取消前台最小焦点测试")
        app.foregroundFocus?.cancel()
        offlineConfirmed = false
        app.visible = false
        Stage0Log.state("lifecycle", "Activity", "onStop；前台=false")
        super.onStop()
    }
    private fun newCompatibilityProbe() = TtsCompatibilityProbe(this) {
        compatibilityRunning = false
        app.compatibilityTestActive = false
    }
    private fun testCompatibility() {
        if (!offlineConfirmed || compatibilityRunning || app.session != null || app.foregroundFocus != null || !app.visible) {
            app.timeline.add("兼容性测试按钮未启动：断网未确认、非前台或另一测试忙碌"); return
        }
        compatibility = newCompatibilityProbe()
        compatibilityRunning = true
        app.compatibilityTestActive = true
        revision++
        compatibility.start()
    }
    override fun onDestroy() {
        if (app.session?.background == false) {
            app.timeline.add("Activity.onDestroy：取消尚未结束的前台原 PCM 会话")
            app.session?.cancel()
        }
        super.onDestroy()
    }
    @Composable private fun ProbeScreen() {
        val event by app.events.collectAsState()
        val currentRevision = revision
        val diagnostics by compatibility.diagnostics.collectAsState()
        val timeline by app.timeline.entries.collectAsState()
        val smsState by app.smsState.collectAsState()
        val smsEvents by app.smsEvents.collectAsState()
        val audioModes by app.audioModeResults.collectAsState()
        fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
        var route by remember { mutableStateOf("未检测") }
        var selected by remember { mutableStateOf(app.target) }
        var mode by remember { mutableIntStateOf(app.mode) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SMS Voice Assistant · 阶段 0", style = MaterialTheme.typography.titleLarge)
            Text("系统 API ${Build.VERSION.SDK_INT}；target 34；compile 36")
            Text("系统默认中文 TTS 兼容性测试", style = MaterialTheme.typography.titleMedium)
            Text("请先关闭 Wi-Fi 和移动数据。此测试仅播放固定人工文本，按系统当前输出播放，可能使用扬声器；不检查蓝牙、不请求应用音频焦点。")
            Row {
                Checkbox(checked = offlineConfirmed, onCheckedChange = { offlineConfirmed = it }, enabled = !compatibilityRunning)
                Text("我已关闭 Wi-Fi 和移动数据（人工确认，APP 无法核实）")
            }
            Button(onClick = { testCompatibility() }, enabled = offlineConfirmed && !compatibilityRunning && app.session == null && app.foregroundFocus == null) {
                Text("系统默认中文 TTS 兼容性测试")
            }
            Text("固定测试文本：${TtsCompatibilityProbe.TEST_TEXT}")
            OutlinedButton(onClick = { compatibility.cancel() }, enabled = compatibilityRunning) { Text("停止兼容性测试") }
            diagnostics.forEach { (label, value) -> Text("$label：$value") }
            Text("onDone 或非网络 Voice 标记均不代表离线验收通过，必须记录断网条件和实际听感。基础测试成功后，再执行下方原有严格蓝牙/焦点测试。")
            HorizontalDivider()
            Text("RECEIVE_SMS：${if (granted(Manifest.permission.RECEIVE_SMS)) "已授予" else "未授予"}")
            Text("BLUETOOTH_CONNECT：${if (granted(Manifest.permission.BLUETOOTH_CONNECT)) "已授予" else "未授予"}")
            Button(onClick = { permissionRequest.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.BLUETOOTH_CONNECT)) }) {
                Text("申请两项验证权限")
            }
            Text("短信权限可能受安装器白名单限制；申请返回不代表已授予。")
            Button(onClick = { devices = BluetoothProbe(this@MainActivity).paired(); revision++ }) { Text("读取已配对设备") }
            Text(if (selected == null) "目标耳机：未选择" else "目标耳机：已选择（仅在本机保存身份）")
            devices.forEachIndexed { index, device ->
                val label = try { device.name ?: "未命名设备" } catch (_: SecurityException) { "权限已撤销" }
                OutlinedButton(onClick = {
                    try { app.target = device.address; selected = app.target; route = "目标已更换，请重新检测" }
                    catch (_: SecurityException) { route = "蓝牙权限已撤销" }
                }) { Text("${index + 1}. $label") }
            }
            Button(onClick = {
                try { BluetoothProbe(this@MainActivity).inspect(app.target) { route = it.status } }
                catch (_: Exception) { route = "检测失败，请检查权限和蓝牙状态" }
            }) { Text("检测连接与预期路由") }
            Text("蓝牙 / 音频路由：$route")
            Text("TTS：每次测试冷启动并检查离线简体中文资源；结果见下方事件。")
            Text("模式：${if (mode == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) "A 降低音乐音量" else "B 请求暂停音乐"}")
            Text("模式在每次启动时固定；播放中切换只影响下一次。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK; app.mode = mode }) { Text("模式 A") }
                OutlinedButton(onClick = { mode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT; app.mode = mode }) { Text("模式 B") }
            }
            Button(onClick = { app.startAudioModeTest(mode) }) { Text("离线中文 / 焦点测试（A/B 直接播放）") }
            Text("A/B 和下方已通过的最小测试使用同一播放类、同一固定文本与属性，仅切换焦点 gain。语音与音乐必须分别确认。")
            Button(onClick = { app.startForegroundFocusTest() }) { Text("前台最小焦点播放测试") }
            Text("固定新句：${ForegroundFocusTest.TEST_TEXT}")
            Text("仅前台模拟测试：检查指定耳机连接和预期路由，直接 speak；实际输出需听验，不替代真实短信的实际轨道路由门禁。")
            audioModes.toSortedMap().forEach { (gain, result) ->
                Text("${if (gain == 3) "模式 A" else "模式 B"} · ${result.id}", style = MaterialTheme.typography.titleMedium)
                result.fields.forEach { (field, value) -> Text("$field：$value") }
                Text("人工语音听验：${result.voiceHeard}；音乐响应：${result.musicResponse}；音乐恢复：${result.musicRecovery}")
                val ready = result.fields["测试结束"] == "true"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = ready, onClick = { app.confirmAudioResult(gain, "语音", "完整有声") }) { Text("语音有声") }
                    OutlinedButton(enabled = ready, onClick = { app.confirmAudioResult(gain, "语音", "无声或不完整") }) { Text("语音未成功") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val response = if (gain == 3) "已降低音量" else "已暂停"
                    OutlinedButton(enabled = ready, onClick = { app.confirmAudioResult(gain, "音乐响应", response) }) { Text(response) }
                    OutlinedButton(enabled = ready, onClick = { app.confirmAudioResult(gain, "音乐响应", "未降低/未暂停") }) { Text("音乐未响应") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = ready, onClick = { app.confirmAudioResult(gain, "音乐恢复", "已恢复") }) { Text("音乐已恢复") }
                    OutlinedButton(enabled = ready, onClick = { app.confirmAudioResult(gain, "音乐恢复", "未恢复") }) { Text("音乐未恢复") }
                }
            }
            OutlinedButton(onClick = { app.speak(OtpParser.TEST_TEXT, false) }) { Text("旧 PCM 链路对照（8 秒）") }
            Text("旧链路：文件合成 → PCM → AudioTrack，不调用 speak；固定人工样例仍为原样例。")
            OutlinedButton(onClick = { app.session?.cancel(); app.foregroundFocus?.cancel() }) { Text("取消并释放焦点") }
            Text("焦点 / 短信时序诊断（最近 120 条，仅内存）", style = MaterialTheme.typography.titleMedium)
            if (timeline.isEmpty()) Text("尚未执行焦点/短信链路")
            timeline.forEach { Text(it) }
            OutlinedButton(onClick = { app.resetSmsObservation() }) { Text("准备本轮短信观察（不发送短信）") }
            smsState.forEach { (key, value) -> Text("短信 $key：$value") }
            Text("独立 SMS 事件（最近 8 条，仅保存脱敏字段）", style = MaterialTheme.typography.titleMedium)
            Text("普通短信只查广播；固定模板分别在前台和后台测。未出现编号仅表示尚未观察到广播，系统收信和 AudioHardening 需外部证据。")
            smsEvents.asReversed().forEach { item ->
                Text(item.id, style = MaterialTheme.typography.titleMedium)
                item.fields.forEach { (field, value) -> Text("$field：$value") }
            }
            Text("最近事件 / TTS 状态：$event")
            Text("最近短信广播：${app.prefs.getString("lastBroadcast", "未验证")}")
            Text("最近后台执行：${app.prefs.getString("lastBackground", "未验证")}")
            Text("仅回调不能证明实际听到、断网成功或音乐恢复。真实短信内容不显示、不保存。")
            Text("诊断：未收到广播时，先查实际权限和安装器，再查特殊 OTP 分发及 OriginOS 后台策略。进程中止后只保留最后状态；没有完成状态应按失败或未验证处理。")
            Text("状态刷新 $currentRevision；真实设备验收须按 docs/stage0-test-plan.md 手工记录。")
        }
    }
}
