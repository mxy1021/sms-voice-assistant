# SMS Voice Assistant：阶段 0

仅实现 Android 原生技术验证。不是正式产品，不包含阶段 1～5 的规则引擎、短信历史、常驻服务或云端功能。

## 打开与构建

用 Android Studio 打开本目录，使用 JDK 17 和 Android SDK 36。配置为 Kotlin 2.2.0、AGP 8.12.1、Gradle 8.13、Compose、单模块；minSdk 33 / targetSdk 34 / compileSdk 36。SDK 路径由环境变量或本机 `local.properties` 提供，后者禁止提交。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，不上传仓库。构建时需要下载依赖；应用运行时不申请联网权限。

## 验证方式

1. 按 [测试计划](docs/stage0-test-plan.md) 安装并正常申请两项权限。
2. 在系统设置中先配对耳机，再在验证界面读取、选择并检测。
3. 在系统预先安装离线简体中文 TTS 资源。关 Wi-Fi、关移动数据，保留蜂窝短信能力。分别测试 A/B 模式和固定合成样例。
4. 使用真实新短信测试前台、后台、划走、锁屏、音乐及拒绝播报场景。真实内容不写入报告。
5. 仅在 [验收报告](docs/stage0-report.md) 有真实证据且满足全部门槛时验收。

## 安全与生命周期

- 清单仅声明 `RECEIVE_SMS`、`BLUETOOTH_CONNECT`；不申请默认短信角色，不查询短信数据库，不扫描设备。
- 只识别任务指定的网易六位模板；示例中的数字完全为人工合成数据。
- 私有配置只保存目标蓝牙身份、焦点模式和最近脱敏状态；不保存发送方、短信、验证码或语音文件。禁止备份。
- 预期路由须与目标蓝牙地址精确匹配；再用静音 `AudioTrack` 的实际路由核对。地址为空、不同、查询失败或焦点失败均拒绝播报。不会用名称推测身份。
- TTS 选择已安装的非网络简体中文 voice，以 `/dev/null` 描述符丢弃文件输出，通过合成回调在内存收集有界 PCM，再由同属性的 `AudioTrack` 播放。这样能够检查实际播放轨道，而不是把 TTS 的预期路由当作实际输出。仅支持 16 位 PCM、单/双声道；引擎不兼容即失败，不回退到扬声器或临时文件。
- `goAsync()` 一直持有至播放完成/拒绝/错误/取消/8 秒超时；所有终结路径释放焦点、TTS、轨道及广播。冷启动合成时间也算在 8 秒内，不保证所有设备均能完成。
- 监听实际路由和设备断连，写入音频块前再次校验。异步路由通知仍有延迟，不能宣称零泄漏保证；必须实测断连和切路由。音乐响应和听感只能由真机确认。
- 无后台轮询、无 Service、无 WorkManager、无音量修改、无播放器控制。Android/OriginOS 杀进程后，应用不能自行证明未收到的短信或被中断的语音；必须对照外部测试记录。

## 重要文件

| 路径 | 用途 |
|---|---|
| `app/src/main/AndroidManifest.xml` | 最小权限与受系统发送权限保护的短信广播 |
| `app/src/main/java/com/mxy1021/smsvoiceassistant/MainActivity.kt` | Compose 验证界面 |
| `app/src/main/java/com/mxy1021/smsvoiceassistant/receiver/SmsReceiver.kt` | 真实广播、分段提取与异步生命周期 |
| `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/OtpParser.kt` | 单一模板识别与逐位中文读法 |
| `app/src/main/java/com/mxy1021/smsvoiceassistant/bluetooth/BluetoothProbe.kt` | 配对、配置文件连接、预期路由 |
| `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SpeechSession.kt` | 离线合成、实际路由、两种焦点、有界清理 |
| `app/src/test/java/com/mxy1021/smsvoiceassistant/Stage0Test.kt` | 合成数据解析与拒绝路由测试 |
| `docs/stage0-test-plan.md` | 真机执行与隐私记录规则 |
| `docs/stage0-report.md` | 实际构建结果、计分与验收结论 |

当前真机验证状态以报告为准。没有真实设备结果时，不具备进入阶段 1 的验收依据。
