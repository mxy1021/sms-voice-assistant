package com.mxy1021.smsvoiceassistant.stage2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mxy1021.smsvoiceassistant.ProbeApplication

class WeChatProbeActivity : ComponentActivity() {
    private val app get() = application as ProbeApplication
    private var notice by mutableStateOf("")
    private var accessOnResume by mutableStateOf(false)
    private var packageFound by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { Screen() } } }
    }

    override fun onStart() {
        super.onStart()
        WeChatProbe.visibility(app, true)
    }

    override fun onResume() {
        super.onResume()
        accessOnResume = WeChatProbe.accessGranted(app)
        packageFound = runCatching {
            val flags = PackageManager.ApplicationInfoFlags.of(0)
            packageManager.getApplicationInfo(WeChatProbe.WECHAT_PACKAGE, flags).packageName ==
                WeChatProbe.WECHAT_PACKAGE
        }.getOrDefault(false)
    }

    override fun onStop() {
        WeChatProbe.visibility(app, false)
        super.onStop()
    }

    @Composable
    private fun Screen() {
        val rows by WeChatProbe.entries.collectAsState()
        val bound by WeChatProbe.bound.collectAsState()
        val armed by WeChatProbe.armed.collectAsState()
        val fixedComplete by WeChatProbe.fixedComplete.collectAsState()
        val fixedHeard by WeChatProbe.fixedHeard.collectAsState()
        val speaking by app.speaking.collectAsState()
        val settings by app.settings.state.collectAsState()
        val audioAvailable = !settings.announcingEnabled && !speaking
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("微信通知技术验证", style = MaterialTheme.typography.titleLarge)
            Text("本用户的微信包：${if (packageFound) WeChatProbe.WECHAT_PACKAGE else "未找到"}")
            Text("通知使用权：${if (accessOnResume) "已授予" else "未授予"} · 连接：${if (bound) "已连接" else "未连接"}")
            Button(onClick = {
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (_: ActivityNotFoundException) { notice = "请手动打开系统的通知使用权设置。" }
            }) { Text("打开系统通知使用权") }
            OutlinedButton(onClick = { WeChatProbe.requestUnbind(app) }, enabled = bound) {
                Text("测试一次系统解绑")
            }
            OutlinedButton(onClick = { WeChatProbe.requestRebind(app) }, enabled = accessOnResume && !bound) {
                Text("请求系统重新绑定")
            }
            if (notice.isNotEmpty()) Text(notice)
            Text("监听默认只观察。音频实验期间请暂停短信播报，完成后恢复原设置。")
            Button(onClick = { WeChatProbe.fixed(app) }, enabled = audioAvailable) {
                Text("播放固定测试语音")
            }
            if (fixedComplete && !fixedHeard) {
                OutlinedButton(onClick = WeChatProbe::confirmFixedHeard) {
                    Text("已听到完整耳机语音，手机无语音")
                }
            }
            val canArm = fixedHeard && audioAvailable && bound && packageFound
            OutlinedButton(
                onClick = { WeChatProbe.arm(app, WeChatProbe.AudioSource.FIXED_NOTIFICATION) },
                enabled = canArm
            ) { Text("下一条测试通知：固定句") }
            OutlinedButton(
                onClick = { WeChatProbe.arm(app, WeChatProbe.AudioSource.REAL_WECHAT) },
                enabled = canArm
            ) { Text("下一条测试通知：真实字段") }
            if (armed != null) {
                Text("已等待下一条含「${WeChatFields.TEST_MESSAGE}」的新微信通知。最多等待 10 分钟。")
            }
            OutlinedButton(onClick = { WeChatProbe.stop(app) }) { Text("取消等待或本次测试") }
            Text("最近事件", style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) Text("暂无回调")
            rows.asReversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
