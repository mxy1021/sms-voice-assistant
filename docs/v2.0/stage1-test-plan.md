# V2.0 阶段1测试计划与人工操作单

日期：2026-11-15。结果另见[测试结果](stage1-test-results.md)；[验收报告](stage1-acceptance.md)不以 API 完成替代听感或真实短信。

## 自动化批次

1. 首批最小修改：Debug构建、全量JVM单测（四组合分别测试、失败/歧义/空文本、输入与全文预算、原文不变、普通数字、快速更新、旧键/目标保持、许可失效及重建）。通过后提交功能。
2. 第二批：构建androidTest/Release，Debug/Release Lint及Manifest/DEX隔离；核对已安装V1.0与新Debug证书，`install -r`升级（测试APK另加 `-t`），不卸载/清数据/自动授予权限。
3. 真机instrumentation：新增7项（4种协调器选文、3项真实私有SharedPreferences测试）；既有8项无Activity初始化/失败清理及3项蓝牙生命周期回归。受控选文边界不播放音频，不冒充PCM或真实短信。
4. 复用 `Test-DebugSms.ps1 -Case Logic` / `-Case RuleMatrix`；后者含4组合×验证码/普通短信共8条，默认真实SmsCoordinator和SpeechSession，保留全部音频门禁。继续运行必要Safety/Focus/Long回归，逐个等待终结。听感仍 MANUAL_REQUIRED。
5. `Test-Stage1Device.py --ui-smoke` 验证四组合、两个开关均可操作、另一开关值及目标/运行/开机恢复不变；检查浅深主题。Home后正常 `am kill` 回收再重开验证配置；不得用force-stop冒充自然系统回收。

每轮测试 finally 恢复用户两个规则及原运行值；隔离Preferences使用唯一测试文件并删除。临时日志、APK、截图、指纹均在被忽略的构建目录，不提交身份、真实正文、验证码、音频或原始Logcat。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:lintRelease
python scripts/Verify-DebugSmsIsolation.py
python scripts/Test-Stage1Device.py --ui-smoke
.\scripts\Test-DebugSms.ps1 -Case Logic
.\scripts\Test-DebugSms.ps1 -Case RuleMatrix
.\scripts\Test-DebugSms.ps1 -Case Safety
.\scripts\Test-DebugSms.ps1 -Case Focus
.\scripts\Test-DebugSms.ps1 -Case Long
.\scripts\Test-DebugSms.ps1 -Case NotificationDelayedStartup
.\scripts\Test-DebugSms.ps1 -Case NotificationStartup
.\scripts\Test-DebugSms.ps1 -Case NotificationBefore
.\scripts\Test-DebugSms.ps1 -Case NotificationLate
```

JDK17须在本机可用；APK安装与指定instrumentation类执行步骤沿用[旧工具说明](../debug-sms-testing.md)，旧D00依赖预期已在本次测试代码中替换为独立开关。测试脚本本身不启用暂停中的APP。

末轮R03/R10真实失败后增加延迟启动反例：1400ms真实通知USAGE焦点竞争持续5322ms，旧1200ms策略先得到FAIL，修正单次窗口1800ms后重跑；同时保留NotificationLate“播放开始后晚到长期占用必须安全取消”的负向预期，不以本次调整承诺任意通知都可完整播放。

## 人工真实短信八项（H01～H08）

前提：已升级当前APK、目标耳机连接且身份/实际路由匹配、权限正常、总运行开启、保留正常系统短信提示音。由另一台设备发送新的蜂窝SMS；选短人工验证码及短普通短信以便完整PCM可满足5秒，使用不含个人信息的内容。无需回传号码、正文或验证码。

| 编号 | 组合/短信 | 预期 |
|---|---|---|
| H01 | A 验证码 | 平台（如有）+逐位验证码精简 |
| H02 | A 普通 | 无本APP语音，允许系统铃声 |
| H03 | B 验证码 | 原始正文全文，包括提醒 |
| H04 | B 普通 | 无本APP语音，允许系统铃声 |
| H05 | C 验证码（重点） | 精简，不误用原文 |
| H06 | C 普通（重点） | 原文全文，不生成验证码 |
| H07 | D 验证码 | 原文全文 |
| H08 | D 普通 | 原文全文 |

逐项记录：及时/完整或正确静默、是否被通知音截断、有无手机/其他设备语音；有音乐时记录降低/恢复。普通短信铃声不算本APP错误播报。超预算拒绝另记录，不能把它作为正向全文通过。

## 关键人工回归（R01～R10）

可与H项交叉覆盖，但不因同一场景历史PASS而自动记本轮PASS。

| 编号 | 操作 | 预期及证据 |
|---|---|---|
| R01 | Home后真实SMS，可结合H03 | 配置保留，短全文及时完整，无手机语音 |
| R02 | 锁屏后真实SMS，可结合H05 | 精简完整，系统提示音不截断 |
| R03 | 物理断USB、后台静置10～15分钟，再发真实SMS | 及时完整，记录实际静置条件 |
| R04 | 正在播报时断开指定耳机 | 立即停止，无手机/其他设备语音；重连后仅新消息恢复 |
| R05 | 正在播报时切媒体输出 | 未匹配/未知路由拒绝或停止，无手机语音；实际路由未改变则不冒充测试通过 |
| R06 | 耳机播放音乐时接收SMS | 音乐降低、语音完整、播后恢复，回调和听感分开 |
| R07 | 正常撤销蓝牙权限，后重新授权 | 拒绝且无语音；原目标保留，重新授权后恢复；不脚本强行授予权限 |
| R08 | 正在播报时关总开关，再开 | 立即停止；旧语音不复活，仅新SMS可播 |
| R09 | 筛选关闭时，在播报中切精简开关，再切回 | 旧许可失效并停止，不恢复旧语音，之后按新规则处理 |
| R10 | 开机恢复开且运行开；系统自启动开；用户重启首次解锁、不打开APP、断USB收SMS | 真实冷启动按保存状态处理；不得将instrumentation或模拟当作真实开机投递 |

R01～R10涉及听感/真实系统条件的部分均需用户确认。未完成标 MANUAL_REQUIRED。自然系统回收、真实运营商多段继承V1.0未验证边界，不无故重跑全部45项。

反馈只需编号、PASS/FAIL、及时/完整、音乐降低/恢复、是否泄漏与系统条件，不提供隐私内容。保留首次失败及修复复测，不以超时无回复推定通过。
