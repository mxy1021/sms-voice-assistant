# SMS Voice Assistant

个人自用的本地 Android 短信语音助手。阶段1代码已部署，已补充Debug业务模拟、短样例听感和真实系统临时焦点竞争验证；**真实SMS通知故障复测及后台验收仍待完成，阶段1尚未通过**。用户已正式接受方案B：阶段1为有限长度全文MVP；较长/任意长度全文留待后续独立专项。此范围决定不代表阶段1测试通过。详见[阶段1报告](docs/stage1-report.md)与[剩余验收表](docs/stage1-remaining-acceptance.md)。不进入阶段2。

## 使用

1. 在系统中配对目标蓝牙耳机，安装与现有版本相同签名的APK；升级使用保留数据更新，不卸载清数据。
2. 打开APP，正常授予短信和蓝牙连接权限，选择目标耳机并刷新。已配对不代表已连接；路由不匹配时不播报。阶段0旧耳机设置原位保留。
3. vivo OriginOS7请手动确认本应用“允许后台高耗电”。“后台运行设置”打开公开应用信息入口，APP不能读取或自动修改该厂商设置。开启可能增加耗电。
4. 点击“测试耳机语音”，只播放固定人工文本。需要亲自确认完整耳机听感以及音乐降低/恢复；回调不能代替听感。
5. 手动打开“启用短信播报”；新安装和阶段0升级默认暂停。停止会取消在途播放并忽略后续短信。
6. 选择两个规则开关。Home、锁屏和正常进程重建保留配置；真实后台表现仍需在当前系统设置下测试。

| 仅播报验证码 | 精简验证码播报 | 新普通SMS行为 |
|---|---|---|
| 开 | 开 | 仅可靠验证码，读平台与验证码 |
| 开 | 关 | 仅可靠验证码，读完整正文 |
| 关 | 任意保存值 | 所有SMS读完整正文；精简开关禁用但保留值 |

两个规则默认开启。数字逐位朗读；常见数字码及部分字母数字混合码支持基础识别，未知/多候选保守忽略。全文不会删除安全提示或改数字字母。

**全文边界：**最多80个UTF-16字符只是输入上限；完整PCM还必须≤5秒、≤1MiB，并满足从接收起8秒总预算及清理余量。超限整条拒绝，在最近处理状态中提示，不截断。超出上述任一限制的SMS（包括混合验证码）可能无法播报；更短文本也可能因TTS冷启动或语速超时被整条拒绝。较长/任意长度全文不在本阶段MVP范围，尚未实现。

“开机后恢复工作状态”默认关闭：重启后在首次进程处理任何短信前暂停；打开则保留上次启用/暂停状态。使用系统开机计数对账和解锁后的开机接收器，不处理解锁前短信。目标机本轮未观察到独立开机广播；强行停止和厂商限制不保证恢复，开机恢复不等于后台保活。

## 架构与隐私

沿用阶段0已验证路径：SMS_RECEIVED → 新PDU → 规则 → 指定蓝牙连接/预期路由 → 严格离线中文TTS → 内存PCM → MAY_DUCK焦点 → AudioTrack静音实际路由验证 → 播放/清理。实际写入前重新检查运行许可、权限及路由；忙碌时拒绝新任务，不排队。

只请求 RECEIVE_SMS、BLUETOOTH_CONNECT、RECEIVE_BOOT_COMPLETED。无INTERNET、历史短信数据库、短信上传、正文/验证码/音频文件保存、常驻Service、FGS、后台轮询、音量修改或播放器控制。音乐降低及恢复由系统/播放器处理。阶段0历史诊断工具仅Debug页面可见，正式界面没有A/B模式；正式音频固定MAY_DUCK。

目标设备为vivo X200 Pro / OriginOS7 / 实读Android17 API37，min/target/compile固定33/34/36。Android17有[独立后台音频限制](https://developer.android.com/about/versions/17/changes/bg-audio)，本机历史成功不代表所有设备兼容。动态断连/切路由仍有系统回调及缓冲窗口，需要真实安全回归，不承诺零泄漏。

## 构建与验证

Windows PowerShell，JDK17，Android SDK36 / Build Tools36.0.0；AGP8.12.1、Gradle8.13、Kotlin2.2.0。SDK路径用环境变量或被忽略的local.properties。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
python scripts/Test-Stage1Device.py
python scripts/Test-Stage1Device.py --ui-smoke
```

APK：`app/build/outputs/apk/debug/app-debug.apk`，不提交仓库。设备脚本需要ADB在PATH、唯一在线授权目标机、已安装Debug版本；UI检查需解锁且手机空闲。它会短暂切换筛选并恢复，不发SMS、不授权、不清数据、不重启。详细参数、真实短信/重启/断USB步骤见测试计划。

51项主机单元测试通过，0失败；Lint0错误、16警告（新增两条为测试依赖版本提示）。总开关初始真实SMS、旧UI/重启状态证据保留；本轮短模拟三行为用户确认完整耳机有声，实际系统焦点-3/GAIN、停止/超时不复活均有断言。真实SMS通知、Home/锁屏及物理断USB结果仍待确认。

Debug开发测试只使用固定人工fixture，通过独立instrumentation APK接入解析后共享业务层，不模拟系统SMS_RECEIVED：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest :app:assembleRelease
# 首次需正常安装测试APK；完整准备步骤见下方工具文档。
.\scripts\Test-DebugSms.ps1 -Case Otp
.\scripts\Test-DebugSms.ps1 -Case Normal
.\scripts\Test-DebugSms.ps1 -Case Long
.\scripts\Test-DebugSms.ps1 -Case All
python scripts/Verify-DebugSmsIsolation.py
```

四种一键命令已在目标机执行。All中超预算的原文和混合码会标UNSUPPORTED，完成回调仍标MANUAL_REQUIRED，不自动授予阶段1通过。测试入口、fixture及协议只在测试APK；Release无测试组件。

## 文档

- [阶段1需求与边界](docs/stage1-requirements.md)
- [阶段1用例、人工步骤及实际结果](docs/stage1-test-plan.md)
- [阶段1实现与交付报告](docs/stage1-report.md)
- [Debug模拟工具、命令及D01～D14实际结果](docs/debug-sms-testing.md)
- [方案B与45项剩余验收](docs/stage1-remaining-acceptance.md)
- [阶段0历史报告](docs/stage0-report.md)、[阶段0历史测试计划](docs/stage0-test-plan.md)：原文保留，其已测结果不自动算作阶段1回归通过。
