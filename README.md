# SMS Voice Assistant：阶段 0 交付归档

阶段 0 核心技术可行性验证：**通过**。结论仅适用于 vivo X200 Pro、OriginOS 7、Android 17 / API 37，以及用户开启「允许后台高耗电」后的已测条件。完整边界和跨设备兼容性尚未全部验证。本次只完成阶段 0 收尾，尚未实施阶段 1～5。

## 当前需求与架构决策

正式产品只保留模式 A：`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`。模式 B（暂停音乐）、`AUDIOFOCUS_GAIN_TRANSIENT` 暂停策略、暂停后恢复播放、A/B 切换设置及相关后续维护任务正式取消。原型中的 A/B 界面和直接 TTS 按钮仅作为历史诊断工具保留，不代表正式产品功能；本次不改已验证业务代码，后续可另开独立清理任务。

优先沿用已验证的事件驱动链路：

`SMS_RECEIVED BroadcastReceiver → 本地模板识别 → 指定蓝牙设备连接检查 → 预期路由验证 → 可确认的离线中文 TTS 合成 → 内存 PCM → 请求 MAY_DUCK 焦点 → AudioTrack 静音探测并确认实际路由 → 写入语音 PCM → 播放结束/失败/取消后清理并释放焦点`。

音乐降低及恢复交由系统和播放器响应；不直接修改全局媒体音量，不直接控制第三方播放器。路由无法确认、目标耳机未连接、焦点失败时拒绝自动播报，不回退手机扬声器。网络 Voice、未安装 Voice 和无法确认离线状态的 Voice 继续严格拒绝；保留已经修复的 zh-Hans 支持。

不使用常驻 Service、Foreground Service、定时轮询、联网或云端 API，不需要 Root、系统签名或默认短信角色。只申请 RECEIVE_SMS、BLUETOOTH_CONNECT，不读取短信数据库，不保存真实短信、验证码或语音文件。

## 已确认结果与运行配置

用户真实设备确认：短信权限及 AppOps 允许；真实普通 SMS 可接收解析，非验证码不播报；固定模板识别成功；AIService 断网中文及 PCM/AudioTrack 完整播报成功；目标耳机断开不走扬声器，重连恢复自动播报。

OriginOS 7 必须由用户在本应用的系统后台/耗电管理中核对并开启 **「允许后台高耗电」**。设置入口随系统版本可能变化；应用不自动调整该设置，也不关闭 Android 安全限制。系统升级、应用更新或设置重置后需重新核验。此配置有后台耗电影响，尚无量化耗电结论。

开启后用户确认 S01～S06 全部通过：Home 连续3条、Home后锁屏连续3次、后台静置10～15分钟、音乐降低后恢复、耳机断开拒绝扬声器、重连恢复。6/6 是六组场景结果，不是100分评分或所有边界稳定性保证。历史 Home 广播迟到高度符合 OriginOS 耗电管理影响，内部具体调度机制未完全证实。

## 打开、构建与验证

Android Studio 打开本目录，使用 JDK 17、Android SDK / Build Tools 36.0.0。Kotlin 2.2.0、AGP 8.12.1、Gradle 8.13、Jetpack Compose、单模块；minSdk 33 / targetSdk 34 / compileSdk 36。SDK 路径使用环境变量或被忽略的 local.properties。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，不上传仓库。构建依赖下载与应用运行无联网权限是两回事。本轮仅改文档，未重跑构建；最近代码版本构建、14项单元测试、Lint及保留数据安装结果见[验收报告](docs/stage0-report.md)。

按[测试计划](docs/stage0-test-plan.md)正常授权、选择耳机并核对后台设置。断网时关闭 Wi-Fi 和移动数据，同时保留蜂窝短信能力；不得用模拟广播代替真实 SMS。

## 生命周期、安全与后续边界

预期路由与目标身份严格核对，再检查 AudioTrack 实际路由；不按名称推测设备身份。PCM仅内存收集、以 /dev/null 丢弃文件输出；只支持16位单/双声道。私有配置保存目标蓝牙身份、原型焦点模式和脱敏诊断状态，禁止备份，不保存短信或验证码。

goAsync 持有至终结清理；原8秒预算不延长。冻结时 Handler 不保证准时执行，恢复后用 elapsedRealtime 复核超时；PDU临时安全上限120秒，过期或时间差无效拒绝播放。这是阶段0保护，正式产品有效期和并发策略仍待完善。路由变化回调有延迟，不能承诺零音频泄漏。

后续验证包括划走APP、重启恢复、数小时后台、播报中断连/切路由、TTS错误清理、超时过期、密集并发、多设备系统及设置重置。已测连续3条不等于重叠突发队列已通过。当前设备的无Service基线已成立，但系统升级可能改变限制，需要重新验证；FGS不属于当前决策。

阶段1可在上述范围与风险下开始规划：配置引导、状态诊断、隐私约束、有限有效期及失败处理。模式B不进入正式需求或待修复清单；本轮不实施阶段1。

## 重要文件

| 路径 | 用途 |
|---|---|
| app/src/main/AndroidManifest.xml | 最小权限与受保护SMS接收器 |
| app/src/main/java/com/mxy1021/smsvoiceassistant/receiver/SmsReceiver.kt | 真实PDU解析、有界异步会话 |
| app/src/main/java/com/mxy1021/smsvoiceassistant/bluetooth/BluetoothProbe.kt | 指定设备、连接及预期路由 |
| app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SpeechSession.kt | 已验证的严格离线PCM、实际路由、焦点与清理 |
| app/src/main/java/com/mxy1021/smsvoiceassistant/sms/SmsSafetyWindow.kt | 阶段0过期和会话预算保护 |
| scripts/Read-Stage0Log.ps1、scripts/Read-Stage0Process.ps1 | 脱敏事件与只读进程诊断 |
| docs/stage0-report.md | 当前验收、证据来源与完整历史归档 |
| docs/stage0-test-plan.md | 当前验收范围及后续边界清单 |
