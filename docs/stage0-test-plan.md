# 阶段 0 技术测试计划

## 环境与记录边界

设备：vivo X200 Pro / OriginOS 7。本轮 ADB 已实读 Android 17 / API 37、targetSdk 34，两项权限及 AppOps 均允许。初始 Android 16 假设不再作为依据；以前“无在线设备”的结果为历史记录。

开发基线：Windows、JDK 17、Gradle 8.13、AGP 8.12.1、Kotlin 2.2.0、Android SDK / compileSdk 36、targetSdk 34、minSdk 33。实际本机结果见验收报告。

真实记录只允许：用例编号、测试轮次、时间、系统版本、安装途径、权限是否真正授予、连接/路由判定、脱敏状态、听感、音乐响应、结果。禁止记录手机号码、短信正文、真实验证码、蓝牙地址、ADB 序列号、完整个人日志或含用户数据截图。示例文本和预期语音为任务提供的人工样例。

每个条件至少连续测试 3 次，记录每次结果；出现失败不得只保留成功轮次。没有发出真实新 SMS、没有现场听验或只有代码/回调证据时，标记“未验证”。负向用例拒绝播报为预期通过，不等于后台正向用例通过。

## 安装、连接与权限

1. Android Studio 打开项目根目录，选择 JDK 17，安装 SDK 36 / Build Tools 36.0.0，等待 Gradle 同步。无需修改系统全局环境。
2. 手机启用开发者选项、USB 调试，USB 连接，在手机确认此电脑授权。执行 `adb devices`，本地确认状态为 `device`，报告仅写“已授权设备 1 台”，不得复制序列号。多设备时在本地命令自行选择目标，不将标识写入报告。
3. 执行构建和单元测试。只有目标设备已授权且选中时执行：

   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.mxy1021.smsvoiceassistant/.MainActivity
   ```

4. 正常系统授权界面申请短信和蓝牙连接权限；回到应用核对“已授予”。不可用 `pm grant` 伪造正常授权流程的成功，不以 `install -g` 代替测试。
5. `RECEIVE_SMS` 为 Hard Restricted Permission，安装器必须允许持有该权限。记录安装来源及实际权限。可在本地查看 `adb shell dumpsys package com.mxy1021.smsvoiceassistant` 中该权限的 granted/flags；该输出可能包含本机数据，禁止原样保存或提交。安装器未允许时，记录“权限限制失败”，不要改成默认短信应用或添加权限。ADB 安装与文件管理器安装应分别记录，不能互相代表。
6. 在系统中预先配对耳机，回到应用选择目标。vivo 已有 AIService TTS 且系统断网朗读成功时，先执行本文新增的基础兼容性测试，不要求安装新引擎。本项目不会主动下载语音资源或启动配对流程。

不得自动清除数据、卸载其他应用、强停系统应用、更改系统后台管理。若用户手动调整 OriginOS 后台设置，应另列一组条件，保留默认设置的原始结果。

## P0-01：工程构建与安装（10 分）

执行 `java -version`（或使用 JDK 完整路径）、`gradlew.bat --version`、`:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:lintDebug`。确认 APK 生成，真机正常安装启动 Compose 界面，旋转与返回前台不崩溃。记录实际工具版本、任务结果、APK 本地位置。预期全部成功；仅构建成功不能判定安装通过。

## P0-02：短信权限与广播（20 分，必过）

1. 记录未授予状态，执行正常申请，确认实际 granted。拒绝权限后应不执行短信播报。
2. 用另一端向该 SIM 发送真实单段普通 SMS；对照接收时间，确认“最近短信广播”更新。再发送跨段 SMS，确认广播到达、完整拼接且不崩溃。不要在代码中手动发送广播冒充测试。
3. 前台、按 Home 后分别测试；解析的非模板文本不播报。真实模板需遵循本文 P0-06 的安全条件。
4. 如可获得，单独测试包含 SMS Retriever Hash 的真实 OTP，并记录是否延迟/不分发。不要假设普通短信通过就涵盖此类短信；无法获得则此子项“未验证”。

预期：普通真实 SMS 广播可达，分段正常，权限状态可信。特殊 OTP 的系统分发限制必须单列；如果目标所需短信不能分发，记录功能边界或失败，不通过改写短信模拟成功。

## P0-03：蓝牙连接与音频路由（15 分，必过）

1. 未授权时读取应为空或明确失败；授权后只显示已配对列表。选择耳机，检查 A2DP / HEADSET / LE_AUDIO 配置文件连接。
2. 连接耳机但将系统媒体输出切到扬声器，检测应拒绝；连接其他耳机，目标保持未连接也应拒绝。
3. 选定耳机为媒体输出，预期路由地址与目标一致；固定语音测试还需静音轨道实际路由地址一致。不接受仅同名、仅配对、仅“蓝牙已开”作为身份确认。
4. 测试中断开、切换输出、撤销蓝牙权限，观察取消及焦点释放；确认扬声器无验证码声音。每次切换都记录实际听感。

预期：正向路由可证实，所有负向均静默。BLE 地址、匿名地址或厂商映射无法对应时必须失败，不能改成按名称猜测。`routedDevice` 为瞬时观测，异步通知与缓冲有窗口；必须报告动态切换风险，不宣称绝对防泄漏。

## P0-04：离线中文 TTS（15 分，必过）

先按下文“AIService 兼容性专项”完成基础测试，再执行本节严格蓝牙/焦点路径。基础 `speak()` 成功不代表 `/dev/null` 合成、PCM、路由或后台成功。

1. 耳机和路由确认后测试固定语音：“您收到的网易验证码为四二九三一七。”
2. 关闭 Wi-Fi 和移动数据，保留 SIM 注册及蜂窝短信，不开启会关闭蜂窝收短信的飞行模式；蓝牙保持打开。重复测试并现场听验。
3. 记录简体中文支持、离线 voice 选择状态、合成开始、播放完成或失败。没有语音资源、不提供 PCM 回调、非 16 位 PCM、`/dev/null` 不兼容均按失败。
4. 点取消；离开并销毁前台验证页面；测试冷启动。检查 TTS/轨道释放及音乐状态。

预期：断网后中文准确且只从目标耳机发出。非网络 voice 元数据不能替代断网听验。当前方案避免保存语音文件，但厂商 TTS 引擎内部行为仍需设备核查。

## P0-05：两种焦点与音乐恢复（15 分）

在目标耳机播放音乐，分别选 A（`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`）与 B（`AUDIOFOCUS_GAIN_TRANSIENT`）。每个模式记录：焦点返回值状态、语音听感、播放器实际降低音量/暂停、播报结束后恢复、取消后恢复、错误/超时后恢复。使用至少目标常用播放器，记录名称和版本，不控制播放器。

预期：焦点失败无播报，所有退出路径释放焦点；播放器行为与期望相符。焦点成功但播放器不响应时该响应项失败，不能写“通过”。不得手动调低全局音量作为成功证据。

## P0-06：真实后台端到端（25 分，必过）

使用真实蜂窝 SMS，采用符合唯一测试模板的受控文本；报告只写模板命中/未命中，不写真实数字或发送方。以下每行填写至少 3 轮：

| 用例 | 条件 | 预期 | 实际 |
|---|---|---|---|
| E01 | 前台 + 目标耳机 | 广播、识别、路由、焦点、离线播报、释放 | 未验证 |
| E02 | Home 后后台 | 同上，后台结果更新 | 未验证 |
| E03 | 最近任务划走，非强行停止 | 冷进程接收并完成；失败如实记录 | 未验证 |
| E04 | 锁屏，分别短时和静置后 | 同上；不得用 USB 常亮条件替代 | 未验证 |
| E05 | 目标耳机连接且路由正确 | 实际从目标耳机输出 | 未验证 |
| E06 | 音乐正在播放，A/B 模式 | 正确焦点响应与音乐恢复 | 未验证 |
| E07 | 目标耳机未连接 | 不播报 | 未验证 |
| E08 | 耳机已连但路由为其他设备 | 不播报 | 未验证 |
| E09 | 播放途中断开/切换设备 | 尽快停止，无扬声器泄漏；实测确认 | 未验证 |
| E10 | 8 秒内无法完成冷启动/播放 | 超时失败，焦点释放，广播结束 | 未验证 |

Android 的“划走”与系统设置“强行停止”不同，后者通常阻止广播，必须分别标记。USB 调试连接可能影响电源状态，锁屏/静置再做一次断开 USB 的测试，并用应用脱敏状态及现场记录复核。

广播异步会话最多 8 秒，包含蓝牙查询、TTS 冷初始化、合成及播放。`goAsync()` 不提供永久保活保证。若重进应用只剩“开始”而无终结事件，结合发送端时间记录，按“疑似进程中止/未完成”处理，不能判成功；未观察到中止原因时写“原因未确认”。不添加常驻服务掩盖限制。

## 故障定位

| 症状 | 核查顺序 |
|---|---|
| 权限始终未授予 | 清单、安装方式、受限权限安装器允许情况、系统授权结果；不要先归咎接收器 |
| granted 但无广播 | 手机是否收到真正 SMS、SIM 状态、Hash/特殊 OTP 限制、强停状态、OriginOS 后台策略、再查接收器 |
| 配对可见但无连接 | 系统媒体配置文件是否连接；配对不能证明连接 |
| 路由始终拒绝 | 地址是否被匿名或为 BLE 另一身份、媒体输出选择、预期与实际轨道；保守拒绝符合安全门禁 |
| TTS 失败/超时 | 已安装离线中文资源、引擎版本、PCM 回调/编码、丢弃描述符兼容、8 秒总预算 |
| 焦点失败 | 前后台、实际系统/target 版本、其他音频活动；保留失败，不能绕过 |
| 焦点成功但音乐不恢复 | 播放器焦点策略、是否收到释放；实际听验与 API 状态分别记录 |

## 判定与官方依据

真实设备完成全部用例后，按报告的固定分项计分；总分至少 85，且 P0-02/03/04/06 通过。所有必要子项有真实结果，缺失均“未验证”。阶段 0 失败也构成技术结论，不能因此暗加权限或 Service。

- [受限短信权限](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)：安装器允许是前提。
- [SMS_RECEIVED 与特殊 Hash 分发](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents#SMS_RECEIVED_ACTION)：普通 SMS 与特殊 OTP 不能混同。
- [广播异步生命周期](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync())：异步仍需快速结束，本项目预算 8 秒。
- [预期音频路由](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)) 与 [实际播放路由](https://developer.android.com/reference/android/media/AudioTrack#getRoutedDevice())：不同证据。
- [TTS 音频回调](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener#onAudioAvailable(java.lang.String,%20byte%5B%5D))：合成结束与实际播放结束分开处理。
- [音频焦点](https://developer.android.com/media/optimize/audio-focus)：Android 15/16 的 target 35+ 规则不能用于排除 Android 17 限制；API 37 须另外按末节 Background Audio Hardening 对所有 target 的规则核查。OriginOS 实际限制须设备证据。

## AIService 兼容性专项（前台、手动、固定文本）

用户最新确认：vivo X200 Pro / OriginOS 7 的本 APP 兼容性手动测试，在关闭 Wi-Fi、移动数据后新句从指定蓝牙耳机成功输出；初始化、getVoices、setLanguage/setVoice/speak 和 onBeginSynthesis/onStart/onDone 正常。基础直接 speak 已成立；文件合成/PCM/严格实际路由、焦点和后台不能据此判通过。

1. 安装最新 Debug APK，进入应用前台。不需要先授权短信或蓝牙、不需要先选耳机。停止音乐及原有严格测试，保持本页可见。
2. 手动关闭 Wi-Fi 和移动数据，保留蜂窝短信能力；APP 不申请联网/网络状态权限，不能自动证明断网。勾选“我已关闭 Wi-Fi 和移动数据”。
3. 点击 **“系统默认中文 TTS 兼容性测试”**。固定新句为“语音兼容性测试，验证码七九一四五二。”，只按系统当前输出播放，可能从扬声器输出。不得输入真实验证码。
4. 等待 OnInit；查询并显示默认引擎名称/包名、语言可用返回值、setLanguage 返回值、Voice 数量及中文 Voice 的 Locale、网络和安装标记。先设置简体中文，有明确离线候选时优先 setVoice；若枚举缺失但初始化与 setLanguage 成功，则直接手动 speak 固定句。
5. 记录 speak 原始返回值、onStart/onBeginSynthesis/onDone/onError/onStop、失败阶段/错误码与资源释放。运行最多 30 秒；点击停止或离开前台会取消并调用 stop/shutdown。分别测试重复点击、停止、按 Home，确认不继续运行。
6. 只有断网条件明确、确实听到这次新句且播放完成，才记录“基础前台手动断网播放通过”。元数据或 onDone 单独出现都不足以通过；没有连接真机时仍为“待验证”。
7. 基础通过后，先用“检测连接与预期路由”和当前“A/B 直接播放”核查焦点/听感，再用“旧 PCM 链路对照（8 秒）”核查严格 Voice/实际轨道。自动短信仍保持严格筛选。最后才按 E01～10 恢复后台真实 SMS 验证。

默认引擎包名来自 `getDefaultEngine()`，名称来自 `getEngines()`。Android 公开 SDK 不提供当前实际绑定引擎包名查询，本界面明确显示“未知”，不使用反射或把默认值当作实际值。无法查询的 Voice 元数据也显示“未知”，不推断资源缺失。

需要回报的诊断字段：默认引擎名称/包名、实际引擎字段（允许未知）、OnInit、两个语言返回值、Voice 数量、中文 Voice 元数据、setVoice/当前 Voice、speak、完整回调顺序、查询异常、失败阶段/码、释放状态，以及断网人工确认、是否听到完整新句、实际输出位置。只提供本专项字段，不提供完整系统日志、真实短信、号码或设备标识。

| 用例 | 条件/目标 | 实际结果 |
|---|---|---|
| T01 | AIService 默认引擎，断网，前台手动新句 | 用户确认通过；实际从指定蓝牙耳机输出 |
| T02 | 枚举为空/未知，但 setLanguage 明确成功 | 待验证；纯逻辑允许不代表设备成功 |
| T03 | 语言设置失败或初始化失败 | 待验证；应显示阶段和原始码，不播放 |
| T04 | 停止、Home、超时、重复启动 | 待验证；取消并释放，不后台继续 |
| T05 | 随后严格蓝牙/焦点，再后台短信 | 旧 A/B 未成功但语音/音乐原因未分清；模板短信已测试，完整节点未验证 |

依据：[setLanguage 与近似 Locale 匹配](https://developer.android.com/reference/android/speech/tts/TextToSpeech#setLanguage(java.util.Locale))、[默认引擎字段含义](https://developer.android.com/reference/android/speech/tts/TextToSpeech#getDefaultEngine())、[Voice 网络要求元数据](https://developer.android.com/reference/android/speech/tts/Voice#isNetworkConnectionRequired())。枚举和标记是诊断信息，不能代替本机断网播放实测。

## 焦点无声专项复测

保留已确认的基础直接 speak 结果，先排查前台，不启动后台短信。安装本次 APK 后读取并选择同一耳机，确认蓝牙连接和预期媒体输出。保持断网、应用在前台。

1. 选模式 A，点击 **“前台最小焦点播放测试”**，应听到“前台焦点测试，验证码六八三一九五。”。等待完成后选 B 再测。此按钮使用独立 TTS，检查耳机/预期路由并直接 speak，不使用 PCM 中转，最长 30 秒。实际耳机输出仍需听验，不能仅以预期路由代替。
2. 每次保留“焦点 / 短信时序诊断”的完整本轮顺序：开始及 gain（A=3、B=2）、连接、预期路由、前置条件、初始化/中文、属性、requestAudioFocus、onAudioFocusChange（可能没有）、真正执行 speak 及返回值、onBeginSynthesis/onStart/onDone/onError/onStop、最终阶段、stop/shutdown 和 abandon 的时点与原因。没有某个回调就是未观测到，不能补造。
3. 没听到时，优先看最后阶段。焦点返回 0/2 应拒绝播放；返回 1 且 speak=0 但无回调或无声，要按 TTS/属性/输出阶段继续定位。不要直接认定引擎坏了或焦点申请必定失败。基础兼容性采用引擎默认属性，新测试采用 MEDIA/SPEECH，二者差异已显示。
4. 再点击 **“旧 PCM 链路对照（8 秒）”**（之前的“离线中文 / 焦点测试”实现）作对照。它明确显示“不调用 speak”，在合成 onDone/PCM 合格后才申请焦点；若日志停在 Voice、文件合成、PCM 或 8 秒超时，说明尚不能指控焦点请求。它的 onDone 是文件合成结束，必须等 AudioTrack 播放标记才算该路径播放完成。
5. 两种模式分别在音乐播放时测 3 轮，记录降低音量/暂停及结束后的恢复；再测取消、离开前台、断开耳机、切换非目标输出、未选设备。正常最小测试须在 speak onDone 后释放；异常/取消/超时早于 onDone 释放是预期清理，必须附原因。模式在启动时固定，中途切换只用于下轮。
6. 前台最小链路成功后，点击 **“准备本轮短信观察（不发送短信）”**，由另一端发送真实新 SMS。分别做普通不匹配文本、受控模板、耳机未连、输出不匹配，再做前台/后台/划走/锁屏。查看广播/解析/模板/蓝牙/路由/焦点/TTS/后台八栏，并对照时间线。报告只记阶段和结果，不记真实内容。

进程重启只能恢复最后脱敏阶段；“上次没有终结记录”表示未完成，可能是进程中止、引擎或其他问题，系统后台限制须外部证据确认。未观察到广播时先核对手机实际收 SMS、授权、安装方式、强停和特殊 OTP 分发；不能从无日志推断代码已运行。

| 用例 | 预期 | 结果 |
|---|---|---|
| F01/F02 | 最小直接 speak，A/B 各自完成、仅目标耳机有声 | 待真机验证 |
| F03 | 原 PCM 路径显示具体退出阶段 | 待真机定位；旧版无声已确认 |
| F04 | 音乐实际响应并在结束/取消后恢复 | 待真机验证 |
| F05 | 未连接/错误路由/忙碌时明确拒绝 | 待真机验证 |
| F06 | Home/取消/断连时立即取消最小测试并释放 | 待真机验证 |
| F07 | 短信阶段逐项可辨，后台失败不虚构原因 | 待真实短信验证 |

官方说明：[synthesizeToFile](https://developer.android.com/reference/android/speech/tts/TextToSpeech#synthesizeToFile(java.lang.CharSequence,%20android.os.Bundle,%20android.os.ParcelFileDescriptor,%20java.lang.String)) 与 [onDone](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener#onDone(java.lang.String)) 区分文件合成结束和 speak 播放结束；[音频焦点](https://developer.android.com/media/optimize/audio-focus) 要求请求与播放属性一致，焦点结果与播放器实际行为分别核验。

## 本轮 A/B 与独立 SMS 事件验收（API 37）

已确认：基础兼容性和“前台最小焦点播放测试”均从指定蓝牙耳机有声。该最小按钮仍作为对照。尚不能判断先前 A/B 未成功属于语音失败还是音乐协调失败；用户做过固定模板前后台测试，但没有完整事件证据，最终节点未验证。

### A/B 同路径

1. 安装本轮 APK，确认系统 API，保持断网、同一耳机和相同系统媒体输出。先点击原“前台最小焦点播放测试”作对照，原流程未重写。
2. 模式 A → **“离线中文 / 焦点测试（A/B 直接播放）”**，完成后模式 B → 同一按钮。二者现在都调用同一 `ForegroundFocusTest`、相同属性和固定文本，只改变 gain：A=3、B=2。旧实现移至 **“旧 PCM 链路对照（8 秒）”**，仍可排查，不混入新 A/B 结果。
3. 对每个 AUDIO 编号保存模式、前台状态、连接/路由/前置条件、焦点实际结果、speak 调用和返回、onStart/onDone/onError、abandon 时点/返回和最终 API 结果。结果只代表 API，不代表听感。
4. 现场分别点击人工“语音有声/语音未成功”“已降低音量/已暂停/音乐未响应”“音乐已恢复/未恢复”。没有音乐播放时音乐两项保持“未验证”。onDone 不能自动填充任何人工结果。A/B 只保留各自最近一次结果（内存），每轮重新初始化人工项，需外部记录 3 轮而非假设前次结果复用。
5. 先无音乐判断 TTS，再有音乐判断协调。前置条件失败、焦点失败、TTS 错误、onDone 但无声、语音有声但音乐不响应，必须单列。

### 两类短信，前后台分开

每次真实 SMS 自动生成独立 `SMS-xxxxxx`，最近 8 条脱敏字段保存在本机配置供重进页面核对。不保存正文、验证码、发送方、设备地址或原始系统日志；没有新编号仅表示应用未观察到广播，不能说明系统没有收信。

| 用例 | 从另一部手机发送 | APP 状态 | 核验重点 | 实际 |
|---|---|---|---|---|
| S-A-F | 普通不含验证码短信 | 前台 | SMS_RECEIVED、解析；模板不命中是预期，不播放 | 未验证 |
| S-A-B | 普通不含验证码短信 | Home 后后台 | 独立编号及真实后台广播；不播放 | 未验证 |
| S-B-F | 【网易】验证码：429317，您正在登录网易手机账号。 | 前台 | 模板/格式、蓝牙/路由、焦点、实际播放及终结 | 用户已做测试，完整节点证据未验证 |
| S-B-B | 同一人工模板 | Home 后后台 | 上述字段及焦点时前台状态、接收器 finish、系统证据 | 用户已做测试，完整节点证据未验证 |

每条回报编号、开始毫秒、API/target、APP 前台、解析、模板、验证码格式、目标连接、预期和实际路由、requestAudioFocus、speak 调用、onStart/onDone/onError、实际播放完成、最终失败节点、最终结果、接收器 finish。格式诊断不改变原模板准入。

真实短信继续走严格 PCM/实际路由链路，不把人工 speak 的预期路由证明当成真实码自动播报的实际路由证明。因此事件中 `speak调用=false（严格PCM）` 是明确的实现事实，合成 onDone 仍需与 AudioTrack 完成区分。goAsync 覆盖至清理结束；finish 后不再有意延续播报。进程中止后的未终结记录只表示未知，不伪称系统已拦截。

### Android 17 只读证据

依据 [Background Audio Hardening](https://developer.android.com/about/versions/17/changes/bg-audio) 和 [所有应用的行为变更](https://developer.android.com/about/versions/17/behavior-changes-all)：API 37 的后台音频门槛适用于所有 target，target 34 不豁免；需要可见 Activity 或非 SHORT_SERVICE 的前台服务。无合法生命周期时焦点可能返回失败，播放可能静默被抑制。SMS 广播到达、goAsync 和 TTS 成功回调均不等同于获得后台音频资格。

本轮不修改任何系统开关。连接唯一已授权设备后，在测试前记录起点并采集：

```powershell
$testStart = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
# 执行一轮前台或后台测试后，仅取脱敏白名单输出。
pwsh -File .\scripts\Read-AudioHardening.ps1 -SinceEpochMillis $testStart
```

主机与手机时钟若不同，请用应用 SMS 的“开始毫秒”或 AUDIO 编号中的设备时间替换 `$testStart`；也可不传时间参数后人工对照。不要把因时钟不一致被过滤的记录误判为不存在拦截。

脚本只执行 devices、getprop、dumpsys package/audio、logcat 读取，不清日志、不改变 hardening 或权限、不写原始日志。输出系统 API、target、匹配本包的 AudioHardening 条目数、level、时间、API 类别。`partial` 表示记录中没有前台服务，`full` 表示存在服务但没有 WIU；必须按事件开始毫秒核对。dumpsys 中未知时间条目可能是历史记录；无匹配不能证明未开启，仍写“未验证”。首次运行检测不到唯一在线设备时不读取日志。

原始日志中的本包 AudioHardening 条目是关键证据；过滤采集保留其存在与等级等白名单字段，不回显原行、UID、设备标识或其他 APP 数据。只回传脚本脱敏 JSON 与应用事件字段，不提交完整 Logcat/dumpsys。

必须排除 SVA_STAGE0 自身提到 AudioHardening 的诊断文字；系统记录如果只写 would，也不能据此补造焦点返回失败或实际静音。记录 exemption、厂商拦截标记与实际 API/听感，三者有差异时保留差异。

官方还区分普通 SMS 与 Retriever Hash/WebOTP 的分发延迟；API 37、target 34 不能直接套用 target 37 的普通 OTP 规则。先用普通 SMS 对照，不能从缺少模板广播直接推断音频限制。

若同一设备前台成功、后台焦点失败，且同时间本包出现 AudioHardening 记录，再认定相关后台路径受限。没有该证据时保留代码、引擎、广播分发、进程生命周期等候选。禁止以禁用 hardening、降低 target 或新增隐蔽保活作为验收。

## 已启用的 ADB 实时验证

1. 连接唯一授权真机，确认型号/API/包名；只安装本项目构建的 APK，使用 `install -r` 保留数据，不使用清数据或卸载解决签名问题。手机端拒绝时由用户确认后重试，不绕过。
2. 应用统一输出 `SVA_STAGE0` 脱敏 JSON。持续采集命令：

   ```powershell
   pwsh -File .\scripts\Read-Stage0Log.ps1 -Follow -Save -Quiet
   ```

   已明确选取唯一在线设备，离线模拟器不会被误选。不使用固定 PID，避免冷进程重启后漏日志。Save 仅写忽略目录的 `live.jsonl`；Quiet 避免终端输出背压阻塞文件采集。无需清空手机日志。
3. 用 `Read-AudioHardening.ps1` 读取系统白名单摘要；没有开关查询不假称关闭。本轮实际只见本包系统 would/partial 检查，焦点仍返回 1，保留这项事实差异。
4. 原始 UI XML 只在手机临时目录用于定位本应用已知按钮；不展示其他应用、通知或真实 SMS 内容。自动操作只触发固定人工文本，不将其当作真实短信测试。
5. Locale 回归：严格链路必须接受已明确离线且已安装的 zh-Hans；未知网络/安装标记、网络 Voice、繁体/其他语言仍拒绝。修复没有给真实 SMS 增加默认语音回退。
6. 以报告末节实际 SMS 编号为证据。SMS-000007 虽在接收时后台，焦点申请时已前台，不能误判全程后台通过；API 完成但用户听感无声也必须失败/待定位。

下一轮只做报告所列的无音乐前台成对对照及后台两类短信分离，不连点播报按钮，避免忙碌拒绝混入。需要用户提供的是编号、阶段和听感，禁止提供正文、实际验证码、号码或带隐私截图。

## Home 后台延迟专项：B01～B06（当前执行计划）

以成功的严格离线 PCM 为基线。最新人工反馈已确认前台及从 APP 直接锁屏后的真实 SMS 有声、音乐降低恢复；不能再将这两项标为完全未测试。下面单次观察不替代原三轮完整验收。

### 统一时间线与环境

- 每条真实 SMS 使用独立 SMS 编号，记录 processRun（每进程随机标记，非设备身份）及 processStartElapsed，判断是否换进程；重建后仅保留脱敏状态，不恢复正文或播放队列。
- 同机阶段时间使用 SystemClock.elapsedRealtime：onReceive、goAsync、PDU 解析、OTP 匹配、蓝牙查询起止、TTS 初始化请求/回调到达/主线程执行、PCM 合成请求/开始/完成、焦点请求起止、AudioTrack 创建起止/play/首次语音写入/全部写入/完成、焦点释放及 PendingResult.finish；Activity onStart/onStop 单列。
- pointElapsedMs 是实际观测点，dispatchLagMs 是跨线程投递到记录时的等待，sinceReceiveMs 是相对 onReceive 的间隔。不是所有点都有跨线程投递；缺失回调写未观测。
- PDU 仅记录到 onReceive 的时间差，不输出其正文、号码或绝对时间戳。此差来自墙上时钟，含运营商传输及校时误差，不能单独证明 OS 内部延迟。冻结前后快照及 Activity 时点共同判断。
- 接收及申请焦点时记录 ActivityStarted、interactive、keyguard、importance。ActivityStarted 仅表示生命周期标记，不能独自证明锁屏时实际可见或具备后台资格。
- 保持相同耳机、播放器、模式、数据连接条件；每轮记录设备系统/API/target、安装 APK 哈希。USB 连接、短信唤醒屏幕、手动看通知均记为环境影响。无需上传截图、原始日志或任何真实短信数据。

### 有界安全保护

原 8 秒总预算不变，不通过延长 goAsync 维持后台。Handler 在冻结/睡眠时不保证准时执行；恢复后在蓝牙、TTS、焦点、AudioTrack 和写入点复核 elapsedRealtime，超时丢弃在途播放并清理。若广播本身迟到，临时安全上限为 PDU 年龄 120 秒；负时间差/未知/超龄拒绝真实码播放，普通短信广播诊断仍保留。120 秒是阶段 0 安全上限，不是正式产品有效期决策；不加入持久化真实码或无限重试。

### 操作矩阵

每轮只发送一次，间隔等待终结。用户只回报语音是否完整、音乐是否降低/暂停和恢复，不回传号码或正文。

| 用例 | 操作 | 必须对照的证据 |
|---|---|---|
| B01 前台 | APP 显示，另一手机发原固定模板 | 新编号、完整阶段、焦点/轨道状态、实际听感和音乐 |
| B02 直接锁屏 | 从 APP 前台直接按电源锁屏，立即发新模板 | onStop、keyguard/interactive、阶段和听感；通知亮屏需记录 |
| B03 锁屏静置 | B02 状态继续锁屏至少 60 秒，再发新模板 | 实际静置时长、进程冻结快照；该时长不代表长时间 Doze |
| B04 Home | 从 APP 按 Home，APP 不可见，发新模板；至少观察 20 秒，不重开 | 是否有新编号/接收时间；系统短信是否确认到达；本包冻结及 procState |
| B05 返回前台 | 仅在 B04 观察结束后打开 APP，不补发短信 | 首个 onReceive 或回调相对 onStart、是否换进程、PDU 年龄、过期丢弃或实际迟播 |
| B06 Home 后锁屏 | APP 前台→Home→锁屏，发送新模板，保持不可见至少 20 秒 | 与 B02 对照；无声时确认系统收信，不先认定音频失败 |

每轮登记：用例、SMS 编号、广播到达与否、各阶段差、焦点结果、AudioTrack state/playState/实际路由、AudioHardening 等级/措辞/时间、人工语音/音乐结果、第一异常节点和未知原因。

### 无侵入采集

```powershell
# 采集日志持续写入已忽略的构建目录，避免终端背压；不限定 PID。
pwsh -File .\scripts\Read-Stage0Log.ps1 -Follow -Save -Quiet
# 切换状态前、短信后、观察结束和重新前台时分别运行。
pwsh -File .\scripts\Read-Stage0Process.ps1 -Case B04.Home -Save
pwsh -File .\scripts\Read-AudioHardening.ps1
```

进程脚本只读取本包进程块、屏幕、窗口、运行时长，不向应用发 Binder 调用、不主动解冻。OriginOS 同一进程可出现平台优化器与厂商附加两个 isFrozen，必须分别保存，缺字段为未知，不能混成一个 true/false。原始 dumpsys/logcat 不落盘，只保留白名单。日志缓冲可能轮换，单次缺记录不等于未执行；结合连续采集、系统确认收信和恢复时点交叉核对。

判定顺序：系统确认收信但没有 onReceive→广播尚未在应用执行；onReceive 已到→查首个长间隔的蓝牙/TTS/PCM回调；焦点返回0→焦点失败；焦点1但轨道/写入失败→音频阶段；仅有 API 完成→仍需听感。进程标记变化、退出原因、冻结标记分别记录，不把“would”日志当作实际拦截。只有焦点/播放发生在重新前台之前且用户确认及时完整有声，才记该轮后台成功。

不得通过关闭安全限制、延长广播预算、读取历史短信、Root、自动后台启动 Activity 或新增服务完成本轮。正式后台架构候选见报告；未完成或存在归因歧义的格子写未验证。

### 本轮执行回填

本节优先于上文历史“待验证”表：B01前台、B02直接锁屏、B03至少60秒锁屏静置均由用户确认完整语音和音乐降低恢复；B04 Home与B06 Home后锁屏观察期无新onReceive且无声；B05和B06回APP后才处理并发声。对应SMS-000015、000018/19、000020、000016、000021及具体时差见[最新报告](stage0-report.md#home-后台延迟专项诊断2026-11-11最新)。只代表本轮对照，不替代三轮稳定性及长时间静置验收。

本轮之后优先进行架构取舍，不再重复排查已经成立的中文TTS或重写PCM。若继续归因系统内部延迟，保持同一APK/设置，需取得不含用户数据的本包广播投递/厂商冻结事件时点，区分队列与冻结调度；没有这些证据前保留未知。不通过读取SMS数据库、完整广播PDU转储或原始个人日志获取证据。
