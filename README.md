# SMS Voice Assistant

个人自用的本地Android短信语音助手。**阶段1已于2026-11-12正式验收通过并关闭（方案B：有限长度全文MVP）。最终43 PASS、0 FAIL、2 SKIP、0 NOT_RUN；H01～H07全部PASS，不重复计数。12项强制条件全部满足，剩余强制阻塞项0。** 详情见[阶段1报告](docs/stage1-report.md)和[最终验收表](docs/stage1-remaining-acceptance.md)。H01、T10、H07原始失败及修复复测历史保留。阶段2未启动。

## 使用

1. 在系统中配对目标蓝牙耳机，安装与现有版本相同签名的APK；升级使用保留数据更新，不卸载清数据。
2. 打开APP，正常授予短信和蓝牙连接权限，选择目标耳机并刷新。已配对不代表已连接；路由不匹配时不播报。阶段0旧耳机设置原位保留。
3. vivo OriginOS7请手动确认本应用“允许后台高耗电”和系统“权限管理→自启动”。重启后独立收信的本机实测条件是自启动开启；关联启动保持关闭也已通过，无需据此开启。“后台运行设置”打开公开应用信息入口，APP不能读取或自动修改该厂商设置。开启可能增加耗电。
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

“开机后恢复工作状态”默认关闭：重启后在首次进程处理任何短信前暂停；打开则保留上次启用/暂停状态。使用系统开机计数对账和解锁后的开机接收器，不处理解锁前短信。应用内恢复开关不会授予厂商自启动许可。H07实测由SMS Receiver先冷启动进程，开机广播事件随后才到；无需先等开机广播或每次重启手动开APP。强行停止和厂商限制不保证恢复，开机恢复不等于后台保活。

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

65项主机单元测试通过，0失败；Lint0错误、16警告（新增两条为测试依赖版本提示）。总开关初始真实SMS、旧UI/重启状态证据保留；本轮短模拟三行为用户确认完整耳机有声，实际系统焦点-3/GAIN、停止/超时不复活均有断言。用户已确认H01～H06及最终H07真实复测通过，原失败保留；阶段1现已正式验收完成；本段工程测试结果为既有记录，本次归档不重跑。新增8项无Activity真机初始化/受控清理测试通过，不能用它们替代真实广播或断USB证据。

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
- [最终验收操作单：安全测试及七条真实SMS](docs/stage1-final-acceptance.md)
- [阶段0历史报告](docs/stage0-report.md)、[阶段0历史测试计划](docs/stage0-test-plan.md)：原文保留，其已测结果不自动算作阶段1回归通过。

Safety、故障、动态路由及真实收信/断USB强制验证已按报告完成；原首次安装中止和NOT_RUN是保留的历史记录。工具仍校验已安装APK与构建SHA，但归档本身不重新安装或执行测试。


真实与模拟SMS现统一使用接收后1200ms的初次焦点窗口，离线合成与窗口并行；窗口后重查权限、连接及预期路由再请求原MAY_DUCK焦点。该窗口计入原8秒预算，不能保证任意时间/时长的通知占用都能完整播报；预算不足仍取消、不截断标成功、不回退扬声器。固定手动语音测试不增加窗口。H01的真实复测与模拟证据分开记录，详见[专项测试工具](docs/debug-sms-testing.md)。


保护重启后首次收信现场时，只运行不带参数的只读检查：

```powershell
python scripts/Test-Stage1Device.py
```

它不启动APP，只读取配置、进程是否存在、Receiver可解析和当前留存入口日志计数；计数为空不证明从未投递。请勿使用会启动界面的--ui-smoke/--set-*参数，也不要先运行instrumentation、更新主APP、清数据或force-stop。系统自启动状态仍需手动确认，工具不猜测厂商许可已通过。

## 已关闭基线与适用边界

验收实现基线：`355356c780bc65dcd3a3c2d91309eb4a24b8c029`。本次为单独的文档归档提交，不改变应用实现。

验收范围仅为已接受的**方案B：有限长度全文MVP**，且仅适用于已验证的vivo X200 Pro（实读型号V2405A）、OriginOS 7、Android 17/API37及已验证配置：短信/蓝牙权限正常、指定耳机身份及实际路由匹配、允许后台高耗电、系统自启动开启；关联启动保持关闭也已通过。开机恢复使用已保存的运行/暂停设置，仅覆盖首次解锁后的行为，不保证强行停止后的投递或其他设备/系统组合。

80个UTF-16代码单元、完整PCM最多5秒、最多1MiB、接收起8秒总预算**同时生效**，700ms路由/清理余量和120秒PDU有效期保持。字符合规不保证可播放；任一超限整条拒绝，不截断、不回退扬声器，不将安全拒绝记为全文播放成功。较长/任意长度全文尚未实现，不在本次MVP范围。

两项SKIP是**R15（真实运营商多段SMS）和L09（自然系统回收后的收信）**，属于本轮非强制范围，真实场景继续未验证，不能改写为PASS。已通过的业务拼接、配置重建及H07重启冷启动不能冒充这两项真实验证。其他未单独实测的子场景仍按各条证据边界保留，不因用例归并PASS而补造真机结果。

阶段1现作为正式关闭的已验收基线。除非未来发现明确缺陷，或用户主动提出新的开发要求，不再继续修改阶段1。本次未修改生产代码、功能、架构或安全限制，未重跑已通过的测试；阶段2未启动，也未创建阶段2代码、分支或实施任务。
