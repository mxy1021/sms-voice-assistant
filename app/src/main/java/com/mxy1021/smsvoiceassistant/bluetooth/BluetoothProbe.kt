package com.mxy1021.smsvoiceassistant.bluetooth

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.mxy1021.smsvoiceassistant.audio.RoutePolicy
import com.mxy1021.smsvoiceassistant.audio.Stage0Log

data class RouteResult(val connected: Boolean, val output: AudioDeviceInfo?, val status: String)

class BluetoothProbe(private val context: Context) {
    companion object {
        val attributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        fun bluetooth(device: AudioDeviceInfo) = device.type in setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
    }
    private val main = Handler(Looper.getMainLooper())
    fun paired(): List<BluetoothDevice> = try {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            emptyList()
        else context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.toList().orEmpty()
    } catch (_: SecurityException) { emptyList() }

    /** 限时读取配置文件连接状态；所有代理都关闭，不进行扫描或轮询。 */
    fun inspect(target: String?, callback: (RouteResult) -> Unit) {
        if (target.isNullOrBlank() || paired().none { it.address.equals(target, true) }) {
            Stage0Log.state("context", "routeProbe", "selected=${!target.isNullOrBlank()};pairedMatch=false;" +
                "permission=${context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED}")
            callback(RouteResult(false, null, "未授权或未选择已配对耳机")); return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null || !adapter.isEnabled) {
            callback(RouteResult(false, null, "蓝牙不可用")); return
        }
        var done = false
        var connected = false
        val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.LE_AUDIO)
        var remaining = profiles.size
        val proxies = mutableMapOf<Int, BluetoothProfile>()
        lateinit var timeout: Runnable
        fun complete() {
            if (done) return
            done = true
            main.removeCallbacks(timeout)
            proxies.forEach { (id, proxy) -> adapter.closeProfileProxy(id, proxy) }
            val result = try {
                val audio = context.getSystemService(AudioManager::class.java)
                val predicted = audio.getAudioDevicesForAttributes(attributes)
                val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val output = outputs.firstOrNull {
                    bluetooth(it) && RoutePolicy.matches(target, listOf(it.address))
                }
                Stage0Log.state("context", "routeProbe",
                    "permission=${context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED};" +
                        "connected=$connected;outputs=${outputs.size};bluetoothOutputs=${outputs.count { bluetooth(it) }};" +
                        "blankBluetoothAddresses=${outputs.count { bluetooth(it) && it.address.isBlank() }};" +
                        "targetOutput=${output != null};predicted=${predicted.size};predictedBluetooth=${predicted.count { bluetooth(it) }};" +
                        "predictedTarget=${RoutePolicy.matches(target, predicted.map { it.address })}")
                if (connected && output != null && predicted.all { bluetooth(it) } &&
                    RoutePolicy.matches(target, predicted.map { it.address }))
                    RouteResult(true, output, "连接已确认；预期路由地址匹配，实际路由仍须播放时确认")
                else RouteResult(connected, null, "连接=$connected；预期路由不匹配或地址不可确认，禁止播报")
            } catch (_: Exception) { RouteResult(connected, null, "路由查询失败，禁止播报") }
            callback(result)
        }
        timeout = Runnable { complete() }
        main.postDelayed(timeout, 1200)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (done) { adapter.closeProfileProxy(profile, proxy); return }
                proxies[profile] = proxy
                try { connected = connected || proxy.connectedDevices.any { it.address.equals(target, true) } }
                catch (_: Exception) { /* 权限撤销或配置文件不支持时按未确认处理。 */ }
                remaining--
                if (remaining == 0) complete()
            }
            override fun onServiceDisconnected(profile: Int) { }
        }
        profiles.forEach {
            try { if (!adapter.getProfileProxy(context, listener, it)) remaining-- }
            catch (_: Exception) { remaining-- }
        }
        if (remaining == 0) complete()
    }
}
