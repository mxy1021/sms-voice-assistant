package com.mxy1021.smsvoiceassistant

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mxy1021.smsvoiceassistant.bluetooth.BluetoothProbe
import com.mxy1021.smsvoiceassistant.bluetooth.ForegroundAudioAccess
import com.mxy1021.smsvoiceassistant.bluetooth.AudioPortRefresh
import com.mxy1021.smsvoiceassistant.sms.SpeechFormatter
import com.mxy1021.smsvoiceassistant.ui.Readiness
import com.mxy1021.smsvoiceassistant.audio.Stage0Log

class MainActivity : ComponentActivity() {
    private val app get() = application as ProbeApplication
    private var revision by mutableIntStateOf(0)
    private var devices by mutableStateOf<List<BluetoothDevice>>(emptyList())
    private var connected by mutableStateOf(false)
    private var routeReady by mutableStateOf(false)
    private var checking by mutableStateOf(false)
    private var routeStatus by mutableStateOf("尚未检查")
    private val portRefresh by lazy { AudioPortRefresh(this) }
    private var recoveryAttempted = false
    private var previousBluetoothPermission: Boolean? = null
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refresh() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { refresh() }
    }
    private val audioAccess = ForegroundAudioAccess(
        permitted = { granted(Manifest.permission.BLUETOOTH_CONNECT) },
        register = {
            getSystemService(AudioManager::class.java).registerAudioDeviceCallback(audioCallback, app.main)
            Stage0Log.state("context", "audioDeviceListener", "registered;permission=true")
        },
        unregister = {
            getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(audioCallback)
            Stage0Log.state("context", "audioDeviceListener", "unregistered")
        })
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        app.event("权限流程已返回，请核对实际状态；拒绝后可从系统设置授权")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
    override fun onStart() {
        super.onStart(); app.visible = true
        recoveryAttempted = false
        audioAccess.start()
    }
    override fun onResume() {
        super.onResume()
        Stage0Log.state("lifecycle", "MainActivity.onResume", "bluetoothPermission=${granted(Manifest.permission.BLUETOOTH_CONNECT)}")
        refresh()
    }
    override fun onStop() {
        audioAccess.stop()
        portRefresh.cancel()
        app.visible = false
        // SMS work is independent of Activity lifecycle. Explicit foreground tests are not.
        if (app.session?.background == false && !app.session!!.isSms) app.session?.cancel()
        super.onStop()
    }
    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    internal data class BluetoothReadiness(val permission: Boolean, val pairedTarget: Boolean,
        val connected: Boolean, val routeReady: Boolean, val checking: Boolean, val revision: Int)
    internal fun bluetoothReadiness() = BluetoothReadiness(granted(Manifest.permission.BLUETOOTH_CONNECT),
        devices.any { matches(it, app.target) }, connected, routeReady, checking, revision)
    private fun refresh() {
        val request = audioAccess.begin(app.target) ?: return
        if (previousBluetoothPermission != request.permitted) {
            recoveryAttempted = false
            previousBluetoothPermission = request.permitted
        }
        revision++
        Stage0Log.state("context", "foregroundRefresh", "request=${request.sequence};bluetoothPermission=${request.permitted}")
        val target = request.target
        devices = if (request.permitted) BluetoothProbe(this).paired() else emptyList()
        checking = request.permitted; connected = false; routeReady = false
        if (!request.permitted) {
            portRefresh.cancel()
            routeStatus = "蓝牙权限未授予，禁止播报"
            return
        }
        routeStatus = "正在检查连接与预期路由"
        try {
            BluetoothProbe(this).inspect(target) { result ->
                if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                    refresh()
                } else if (audioAccess.accepts(request, app.target)) {
                    if (result.connected && result.output == null && !recoveryAttempted && app.session == null) {
                        recoveryAttempted = true
                        routeStatus = "正在重新同步音频设备信息"
                        portRefresh.start { refresh() }
                        return@inspect
                    }
                    if (portRefresh.running) return@inspect
                    connected = result.connected; routeReady = result.output != null
                    routeStatus = result.status; checking = false
                }
            }
        } catch (_: Exception) { checking = false; routeStatus = "无法检查，请确认蓝牙权限并刷新" }
    }
    private fun openAppSettings() {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { app.event("无法打开系统设置，请手动进入本应用的应用信息") }
    }
    private fun deviceName(device: BluetoothDevice): String = try { device.name?.takeIf { it.isNotBlank() } ?: "未命名已配对设备" }
        catch (_: SecurityException) { "蓝牙权限已撤销" }
    private fun matches(device: BluetoothDevice, target: String?) = try { device.address.equals(target, true) }
        catch (_: SecurityException) { false }

    @Composable private fun MainScreen() {
        val settings by app.settings.state.collectAsState()
        val speaking by app.speaking.collectAsState()
        val event by app.events.collectAsState()
        val currentRevision = revision
        val sms = remember(currentRevision) { granted(Manifest.permission.RECEIVE_SMS) }
        val bluetooth = remember(currentRevision) { granted(Manifest.permission.BLUETOOTH_CONNECT) }
        var choosing by remember { mutableStateOf(false) }
        val selectedName = devices.firstOrNull { matches(it, settings.selectedBluetoothDevice) }?.let { deviceName(it) }
            ?: if (settings.selectedBluetoothDevice == null) "尚未选择耳机" else "已保存目标耳机（名称暂不可用）"
        val status = Readiness.status(settings, sms, bluetooth, connected, routeReady, checking, speaking)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("SMS Voice Assistant", style = MaterialTheme.typography.headlineSmall)
            Section("运行状态") {
                Text(status, style = MaterialTheme.typography.titleLarge)
                Toggle("启用短信播报", "暂停会取消在途播报，并忽略之后的新短信。",
                    settings.announcingEnabled, settings.announcingEnabled || (sms && bluetooth && settings.selectedBluetoothDevice != null)) {
                    app.setAnnouncingEnabled(it)
                }
                if (!sms || !bluetooth) Text("启用前请先授予短信和蓝牙权限。")
                if (settings.selectedBluetoothDevice == null) Text("启用前请选择已配对耳机。")
                Text("仅由新短信触发，无常驻服务。实际输出路由在播放时再次检查。", style = MaterialTheme.typography.bodySmall)
            }
            Section("短信播报规则") {
                Toggle("仅播报验证码", "开启后，无法可靠识别的短信将忽略。", settings.otpOnlyEnabled) {
                    app.settings.setOtpOnly(it); app.session?.cancel()
                }
                Toggle("精简验证码播报", if (settings.otpOnlyEnabled) "只读平台和验证码；数字逐位朗读。" else "全文模式下不可操作，原设置已保留。",
                    settings.otpSimplifyEnabled, settings.otpOnlyEnabled) {
                    app.settings.setSimplify(it); app.session?.cancel()
                }
                Text(when {
                    !settings.otpOnlyEnabled -> "当前：所有新普通 SMS 全文播报"
                    settings.otpSimplifyEnabled -> "当前：仅验证码，精简播报"
                    else -> "当前：仅验证码，全文播报"
                }, style = MaterialTheme.typography.bodyMedium)
                Text("全文最多80个字符，完整语音最多5秒，且须满足8秒总预算。超限整条拒绝并显示状态，不截断。较长全文暂不支持。",
                    style = MaterialTheme.typography.bodySmall)
            }
            Section("蓝牙设备") {
                Text(selectedName, style = MaterialTheme.typography.titleMedium)
                Text(if (checking) "连接状态：检查中" else if (connected) "连接状态：已连接" else "连接状态：未连接或无法确认")
                Text("预期音频路由：$routeStatus")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refresh(); choosing = true }, enabled = bluetooth) { Text("更换耳机") }
                    OutlinedButton(onClick = ::refresh) { Text("刷新") }
                }
                Text("只列出已配对设备；身份按已保存地址匹配。路由无法确认时拒绝播报。", style = MaterialTheme.typography.bodySmall)
            }
            Section("权限和系统设置") {
                Text("短信权限：${if (sms) "已授予" else "未授予"}")
                Text("蓝牙权限：${if (bluetooth) "已授予" else "未授予"}")
                if (!sms || !bluetooth) Button(onClick = {
                    permissions.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.BLUETOOTH_CONNECT))
                }) { Text("申请必要权限") }
                OutlinedButton(onClick = ::openAppSettings) { Text("应用权限设置") }
                Text("拒绝授权后可从系统设置手动授权；短信权限还受安装器限制。返回本页会重新检查。", style = MaterialTheme.typography.bodySmall)
                Toggle("开机后恢复工作状态", "开启：恢复上次启用/暂停状态。关闭：重启后暂停。", settings.bootRestoreEnabled) {
                    app.settings.setBootRestore(it); app.session?.cancel()
                }
                OutlinedButton(onClick = ::openAppSettings) { Text("后台运行设置") }
                Text("vivo OriginOS 7：请手动检查“允许后台高耗电”。阶段0测试中这是后台及时接收的重要条件，可能增加耗电。应用无法读取该设置：请手动确认。")
                Text("Android 17后台音频仍受系统限制；系统设置不代表后台保活保证。", style = MaterialTheme.typography.bodySmall)
            }
            Section("语音测试") {
                Text("固定文本：${SpeechFormatter.TEST_TEXT}")
                Button(onClick = { app.speak(SpeechFormatter.TEST_TEXT, false) }, enabled = bluetooth && routeReady && !speaking) { Text("测试耳机语音") }
                if (speaking) OutlinedButton(onClick = { app.session?.cancel() }) { Text("取消本次播报") }
                Text("离线中文，指定耳机，音乐可降低音量。完成回调后仍需你确认实际听感。", style = MaterialTheme.typography.bodySmall)
                Text("最近处理状态：$event")
            }
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                TextButton(onClick = {
                    startActivity(Intent().setClassName(packageName, "$packageName.Stage0DiagnosticsActivity"))
                }) { Text("开发诊断（阶段0历史工具）") }
            }
            Text("完全本地；不读取历史短信，不保存短信正文、验证码或语音。", style = MaterialTheme.typography.bodySmall)
        }
        if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("选择已配对耳机") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                if (devices.isEmpty()) Text("暂无可用设备，请先在系统中配对并授予蓝牙权限。")
                devices.forEachIndexed { index, device ->
                    TextButton(onClick = {
                        try { app.target = device.address; choosing = false; refresh() }
                        catch (_: SecurityException) { app.event("蓝牙权限已撤销，请重新授权"); refresh() }
                    }) { Text("${index + 1}. ${deviceName(device)}${if (matches(device, settings.selectedBluetoothDevice)) "（已选择）" else ""}") }
                }
            } }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("关闭") } })
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable private fun Toggle(label: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
