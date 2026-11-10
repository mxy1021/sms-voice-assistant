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
    override fun onStart() { super.onStart(); app.visible = true; revision++ }
    override fun onStop() {
        compatibility.cancel()
        offlineConfirmed = false
        app.visible = false
        super.onStop()
    }
    private fun newCompatibilityProbe() = TtsCompatibilityProbe(this) {
        compatibilityRunning = false
        app.compatibilityTestActive = false
    }
    private fun testCompatibility() {
        if (!offlineConfirmed || compatibilityRunning || app.session != null || !app.visible) return
        compatibility = newCompatibilityProbe()
        compatibilityRunning = true
        app.compatibilityTestActive = true
        revision++
        compatibility.start()
    }
    override fun onDestroy() {
        if (app.session?.background == false) app.session?.cancel()
        super.onDestroy()
    }
    @Composable private fun ProbeScreen() {
        val event by app.events.collectAsState()
        val currentRevision = revision
        val diagnostics by compatibility.diagnostics.collectAsState()
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
            Button(onClick = { testCompatibility() }, enabled = offlineConfirmed && !compatibilityRunning && app.session == null) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK; app.mode = mode }) { Text("模式 A") }
                OutlinedButton(onClick = { mode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT; app.mode = mode }) { Text("模式 B") }
            }
            Button(onClick = { app.speak(OtpParser.TEST_TEXT, false) }) { Text("离线中文 / 焦点测试") }
            Text("固定合成样例：${OtpParser.TEST_TEXT}")
            OutlinedButton(onClick = { app.session?.cancel() }) { Text("取消并释放焦点") }
            Text("最近事件 / TTS 状态：$event")
            Text("最近短信广播：${app.prefs.getString("lastBroadcast", "未验证")}")
            Text("最近后台执行：${app.prefs.getString("lastBackground", "未验证")}")
            Text("仅回调不能证明实际听到、断网成功或音乐恢复。真实短信内容不显示、不保存。")
            Text("诊断：未收到广播时，先查实际权限和安装器，再查特殊 OTP 分发及 OriginOS 后台策略。进程中止后只保留最后状态；没有完成状态应按失败或未验证处理。")
            Text("状态刷新 $currentRevision；真实设备验收须按 docs/stage0-test-plan.md 手工记录。")
        }
    }
}
