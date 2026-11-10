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

class MainActivity : ComponentActivity() {
    private val app get() = application as ProbeApplication
    private var revision by mutableIntStateOf(0)
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        revision++
        app.event("权限流程已返回，请核对实际授权状态")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { ProbeScreen() } } }
    }
    override fun onStart() { super.onStart(); app.visible = true; revision++ }
    override fun onStop() { app.visible = false; super.onStop() }
    override fun onDestroy() {
        if (app.session?.background == false) app.session?.cancel()
        super.onDestroy()
    }
    @Composable private fun ProbeScreen() {
        val event by app.events.collectAsState()
        val currentRevision = revision
        fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
        var route by remember { mutableStateOf("未检测") }
        var selected by remember { mutableStateOf(app.target) }
        var mode by remember { mutableIntStateOf(app.mode) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SMS Voice Assistant · 阶段 0", style = MaterialTheme.typography.titleLarge)
            Text("系统 API ${Build.VERSION.SDK_INT}；target 34；compile 36")
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
