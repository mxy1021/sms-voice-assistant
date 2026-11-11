# Debug模拟短信测试

本工具以固定人工fixture减少真实SMS发送次数。它只验证解析后的业务、实际TTS/PCM、路由及焦点行为，**不能证明运营商SMS、系统PDU分发、Home/锁屏广播、断USB稳定性、真实有声或音乐恢复已通过**。阶段1仍待验收。

## 实现与安全入口

- 测试代码/人工正文只在 `app/src/androidTest`，入口为签名 instrumentation 的 DebugSmsInjectionTest。
- 正式路径：受BROADCAST_SMS保护的SmsReceiver → 系统PDU解析 → SmsCoordinator。
- 模拟路径：ADB启动已安装测试runner → 固定fixture → 同一个SmsCoordinator。
- 汇合点是解析后的SmsPart列表。共享层不包含fixture、Intent注入接口或测试特权；来源枚举只影响诊断，不影响安全策略。
- 不发SMS_RECEIVED广播，不碰短信数据库，不申请READ_SMS/SEND_SMS/INTERNET，不改AppOps、权限、电池策略或AudioHardening，不启动Service。
- 测试APK无Activity/Receiver/Service/Provider导出入口。AndroidX自动合入的三个占位Activity及REORDER_TASKS权限已移除。目标Debug/Release APK均不含测试类、fixture、runner声明或注入协议。
- 每个模拟事件为 `SIM-进程随机编号-序号`，`source=DEBUG_SIMULATION`；系统事件保留SMS编号、`source=REAL_SMS`。模拟事件明确SMS_RECEIVED未触发，不生成PendingResult.finish或真实PDU投递记录。

## 首次准备

在PowerShell7中于工程根目录执行。构建依赖下载不代表应用拥有联网权限。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
adb devices -l
# 在本地选定正确设备，赋给 $svaDevice；不要把实际序列号写入文档或提交。
adb -s $svaDevice install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $svaDevice install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

上述APK路径已由本工程Gradle输出metadata核对。`-t`只允许安装标为testOnly的测试APK，`-r`保留数据；不加`-g`，不卸载或清数据。签名冲突停止处理，不换签名。

手机确认两次安装，保持解锁、目标耳机连接，正常授予已有短信/蓝牙权限。**先在正式UI手动启用总开关**，工具不会为了让正向用例通过而自动启用暂停的APP。测试会临时切换正常规则设置并恢复；D11及D13.stop暂时关闭后恢复原运行值。请保持手机空闲，避免同时手动改设置或启动另一轮测试。

## 一键使用

```powershell
.\scripts\Test-DebugSms.ps1 -Case Otp
.\scripts\Test-DebugSms.ps1 -Case Normal
.\scripts\Test-DebugSms.ps1 -Case Long
.\scripts\Test-DebugSms.ps1 -Case All
```

上述四种命令均需在真实安装后的设备执行，结果见阶段1报告的本轮表。多个在线设备时必须显式选择：

```powershell
.\scripts\Test-DebugSms.ps1 -Case All -Serial $svaDevice
```

脚本从本机SDK或PATH寻找ADB，检查设备授权、已安装Debug/测试包，从Gradlemetadata读取包名、从设备`pm list instrumentation`发现实际runner组件，不假定组件名称。参数仅fixture ID，不传中文正文或任意文本。

OriginOS7最小试运行中，instrumentation内部startActivitySync曾45秒超时；最终采用就绪握手：测试输出SVA_DEBUG_READY后，脚本通过公开正常`am start`打开已有正式主界面，再等待真实onStart状态。没有修改系统后台启动限制，也没有设置虚假的前台标记。**这些是前台instrumentation测试，不是后台系统SMS测试。**

脚本等待整个instrumentation结束后输出结构化结果；All内部逐条等待终结，唯一例外D10刻意同时提交三个输入验证忙碌拒绝，之后仍等待全部终结才继续。任何断言失败/启动异常/缺少结构化结果都返回非零码。SKIP、UNSUPPORTED和待听感不会伪装成完整验收通过。

## Fixture与额外命令

| 编号 | 人工场景 | 命令/预期 |
|---|---|---|
| D00 | 默认、规则依赖、真实配置重载、身份不匹配 | Logic；不播放 |
| D01 | 测试平台数字验证码 | Otp；筛选开/精简开 |
| D02 | 带有效期/提醒的验证码全文 | OtpFull；筛选开/精简关，另带短全文控制样例 |
| D03 | 会议通知普通SMS | Normal；先筛选忽略，再全文，另带短全文控制 |
| D04 | 金额、时间、订单号 | Numbers；不得误识别验证码，再验证全文路径 |
| D05 | A7b29C混合码 | Mixed；逐位与大小写；实际音频仍可能超预算 |
| D06 | 前有18点、后有有效期，及明确多候选负例 | OtpAmbiguous；不能取第一组数字 |
| D07 | 精确80个UTF-16代码单元 | Long80；不凭字符数宣称播完 |
| D08 | 精确81个UTF-16代码单元 | Long81；在TTS前整条拒绝 |
| D09 | 字符合法、完整语音可能超过5秒 | LongPcm；依真实PCM预算拒绝或播放 |
| D10 | 一次快速提交三条 | Rapid；一条会话，其余忙碌拒绝 |
| D11 | 总开关关闭 | Disabled；无TTS/PCM，随后恢复原值 |
| D12 | 指定耳机未连接 | Disconnected；目标若仍连接则SKIP，需用户手动断开后重跑 |
| D13 | 系统焦点竞争 | Focus；恢复、停止、超时三个子用例 |
| D14 | 两个人工片段组成验证码 | Multipart；仅业务拼接，不是运营商PDU验证 |

`Long`依次执行D07/D08/D09；`All`包含上表所有用例。所有正文都是任务给定或人工生成，未取自真实短信；既不写入收件箱，也不持久化正文、验证码或PCM。

## 焦点证据

D13在语音真正写入后，另一个AudioFocusRequest向Android实际请求MAY_DUCK，用系统回调观察-3。没有直接调用SpeechSession的焦点回调或人为记录数值冒充事件。

恢复子用例检查：-3确实到达、暂停期间PCM写入不增长、真实GAIN回调、原许可/实际路由/剩余预算门禁、播放终结及焦点释放。停止/超时子用例在实际临时抢占期间关闭总开关或等原8秒超时，释放竞争者后会话不得复活。永久丢失-1及晚到回调另由既有单元测试验证；没有真实-1系统竞争证据便不标真机通过。

若设备无法产生预期系统竞争，明确SKIP，不用测试替身假装真机事件。模拟焦点竞争也不是手机真实SMS通知音的分发证明；最新原始短信失声故障仍需少量真实SMS复核。

## 结果解读

| 状态 | 含义 |
|---|---|
| PASS | 指明的逻辑或系统断言通过；不自动包含听感 |
| FAIL | 实际断言或工具执行失败，命令非零退出 |
| SKIP | 开关、权限、目标连接或实际焦点条件不足 |
| NOT_RUN | 未执行，没有证据 |
| MANUAL_REQUIRED | 业务及PCM完成回调通过，完整有声仍需人工确认 |
| UNSUPPORTED | 完整语音超当前边界，安全拒绝；不是全文功能通过 |

每条JSON同时含source、case、assertions、hearing；有音频时报告实际PCM时长、写入字节、路由匹配、焦点变化及释放结果。没有短信内容/发送方/设备身份。API完成不能证明耳机有声、无扬声器泄漏或音乐恢复。

80个UTF-16代码单元只是准入上限；PCM≤5秒、≤1MiB及总预算8秒仍严格生效。D02/D04/D05/D07/D09可能因真实合成时长无法播完而UNSUPPORTED。用户现已正式接受方案B：上述同时生效的限制属于阶段1有限全文MVP；超限拒绝是符合MVP的安全行为，工具仍保留UNSUPPORTED表示该条完整语音未交付。较长/任意长度全文尚未实现，后续独立开发，不再作为本阶段范围阻塞；真实SMS及安全/后台条件仍需通过。

## 构建隔离与排障

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
.\gradlew.bat :app:assembleDebugAndroidTest :app:assembleRelease
python scripts/Verify-DebugSmsIsolation.py
```

隔离脚本解析实际合并Manifest与三份APK的DEX，检查真实Receiver仍受保护、目标权限精确为三项、无Service/目标instrumentation、测试APK无导出组件，测试类/fixture/协议只在测试APK。单独的来源枚举用于诊断，不是模拟入口。

- ADB缺失：安装platform-tools或设置ANDROID_SDK_ROOT/ANDROID_HOME。
- unauthorized/offline：在手机正常授权或恢复连接；不绕过授权。多设备用-Serial。
- 测试包不存在：先构建、保留数据安装两个APK；普通Release不支持此工具。
- 安装停住：查看手机系统安装确认页，不关闭安全检查。
- 没有就绪/前台：解锁手机并使用最终脚本，不直接省略握手只运行am instrument。
- APP_DISABLED：正式UI启用；无需改脚本绕过总开关。
- 连接/路由SKIP：核对目标耳机、媒体输出及两项权限；不按设备名猜身份。
- UNSUPPORTED：查看PCM/预算边界，不截断、延长预算或暗加服务。
- 需要记录本地输出时写入被忽略的app/build目录；不要提交原始Logcat、设备标识或任何真实短信。

完整45项原验收的剩余工作见[阶段1剩余验收表](stage1-remaining-acceptance.md)。本工具通过不会自动改变真实SMS、后台、断USB和完整听感的验收状态。

## 模拟工具交付轮实际执行结果（基线7bf842d，2026-11-11）

目标为vivo X200 Pro / OriginOS7，实读Android17 API37。Otp、Normal、Long、All四种一键命令均在已安装设备实际执行，返回码0。每次是1个JUnit instrumentation入口，All输出22条结构化业务子结果，并非22个独立JUnit测试。最终All：PASS8、MANUAL_REQUIRED8、UNSUPPORTED5、SKIP1、FAIL0；其中D12连接条件不满足，随后由用户断开耳机单独补测PASS。没有把UNSUPPORTED或SKIP算成完整播报通过。

| 编号 | 自动化结果 | 本轮证据与人工边界 |
|---|---|---|
| D01 | MANUAL_REQUIRED，断言PASS | 4.027秒、193280字节、实际路由匹配、完成及释放；用户确认精简短样例完整耳机有声 |
| D02 | 原样例UNSUPPORTED；短控制MANUAL_REQUIRED | 原文7.707秒，写入0；短验证码全文1.867秒，用户确认完整耳机有声 |
| D03 | 筛选PASS；全文MANUAL_REQUIRED | 普通原文3.627秒、短控制1.267秒；用户确认短普通全文完整耳机有声 |
| D04 | 筛选PASS；全文UNSUPPORTED | 金额/订单不误识别；全文7.787秒，写入0 |
| D05 | 识别/格式化PASS；语音UNSUPPORTED | 大小写选文正确，但完整语音8.227秒，写入0；不能称混合码播报通过 |
| D06 | 上下文MANUAL_REQUIRED；歧义负例PASS | 正确提取关键词后的码而非18点；4.027秒；多候选忽略，独立听感未另授分 |
| D07 | UNSUPPORTED，拒绝断言PASS | 精确80代码单元合成17.227秒，写入0，原文没有截断 |
| D08 | PASS | 81代码单元在TTS前整条拒绝，没有音频会话 |
| D09 | UNSUPPORTED，拒绝断言PASS | 字符合法但实际8.107秒，写入0，按音频而非字符数判断 |
| D10 | MANUAL_REQUIRED，断言PASS | 同时三输入，一条2.827秒完成、两条BUSY，全部终结/焦点释放；不是系统SMS突发验证 |
| D11 | PASS | 总开关关闭，解析后入口拒绝，未启动音频，原运行值恢复 |
| D12 | PASS（单独补测） | 用户真实断开目标，API确认connected=false，写入0、未确认播放路由；未单独授予扬声器听感通过 |
| D13 | 恢复MANUAL_REQUIRED；停止/超时PASS | 真实系统回调[-3,1]，暂停时写入不增长；恢复完成；停止不复活；最终超时elapsed约8008ms后取消释放 |
| D14 | MANUAL_REQUIRED，业务断言PASS | 两个人工片段组合后4.027秒、路由匹配/完成/释放；没有运营商PDU或系统广播证据 |

用户只明确确认三种短模拟业务的耳机完整听感；没有自动扩展为真实SMS、D13专项听感、音乐恢复、动态断连或断USB成功。

最终主机命令通过：51项单元测试、0失败/错误/跳过，Lint0错误/16警告；Debug、androidTest及Release均构建成功。新增两条为测试依赖版本提示。隔离脚本通过；本轮结束四项配置与目标耳机指纹均与安装前一致，私有配置中未发现fixture正文或格式化验证码文本。原始本地输出仅在被忽略的app/build/debug-sms-*.txt。

## 方案B收尾新增用例（已构建，设备NOT_RUN）

新增命令：Safety、StopDuring、RulesDuring、TtsFailures、FocusFailure、FocusPermanent、BluetoothPermission、DisconnectDuring、RouteDuring。Safety为6个非人工子用例；All扩展为28个业务子结果，手动动态/权限场景仍须单独选择。上节22条为基线已执行证据，不是本轮新增测试通过。

mechanism字段将CONTROLLED_SUBSTITUTE（初始化ERROR、合成onError、焦点请求失败替身）、ANDROID_SYSTEM（实际永久-1竞争）、ANDROID_PERMISSION_STATE和ANDROID_ROUTE_GUARD分别标记。替身不能证明系统确实发出错误；实际永久焦点测试遇到外部音乐活动会SKIP，不自动暂停音乐。人工动态场景会提前提示，仅在观察到真实动态条件时才产生API证据，无扬声器泄漏仍需听感。

本轮安装器返回INSTALL_FAILED_ABORTED（User rejected permissions），用户要求先完成非手动部分，故新设备场景均NOT_RUN。脚本新增已安装目标/测试APK与本地构建SHA核对；不匹配在instrumentation启动前失败，实际负向验证通过，防止旧APK成功运行却被记为新安全用例通过。目标配置及耳机选择未清除。

SpeechDependencies为main中的普通平台依赖边界，默认始终委托原TextToSpeech/AudioManager，无配置开关、反射选择器、fixture或外部入口。失败替身只在androidTest；实际Release/Debug DEX隔离检查也验证替身类未泄漏。操作与证据要求详见[最终验收操作单](stage1-final-acceptance.md)。
