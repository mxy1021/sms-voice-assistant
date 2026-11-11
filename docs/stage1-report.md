# 阶段1工程与验收报告

日期：2026-11-11（Asia/Shanghai）。分支 main，基线 `8ea7ac8`，正式包名 com.mxy1021.smsvoiceassistant。目标设备 vivo X200 Pro / OriginOS7；ADB实读 vivo V2405A、Android17 / API37。未记录设备序列号或蓝牙身份值。

## 1. 当前结论与完成范围

**阶段1尚未正式验收通过。** 用户已确认H01～H06通过；H07原始失败经OriginOS自启动设置对照后真实复测通过，原FAIL及取证过程保留于第16节。阶段1按本次要求保持待验收，不自动整体放行。Safety、L02/L03及T10既有成功保留。用户现已接受方案B有限长度全文MVP；任意长度尚未实现，属于后续独立专项，不再作为阶段1范围阻塞。此前总开关、固定测试、三次重启和真实SMS失声历史保留下文；本轮新证据见第12节，不将模拟当作系统收信。

此前交付人工状态（保留历史）：用户确认修复版安装后正在复测，随后明确选择“仍在测试，先交付待验收版本”。因此本轮交付到此收尾，三个规则场景的修复后听感、后台及断USB结果均等待后续反馈，不延时推定通过。

| 范围 | 实现状态 | 验证状态 |
|---|---|---|
| 统一设置、启用/停止、许可并发门禁 | 完全实现 | 单测、前台H01真实SMS及Safety正常播放中停止通过；后台等另测 |
| 两开关、三种选文、基础OTP识别 | 完全实现基础范围 | 单测/UI及H01～H05三种行为真实SMS通过；用户本轮明确确认 |
| 正式Compose、蓝牙、权限与OriginOS引导 | 完全实现 | 真机UI及T10撤权/重复授权恢复通过；失败历史保留 |
| 原耳机配置保留 | 完全实现，无跨存储迁移 | 首批及最终安装后私有目标指纹一致 |
| 开机状态对账及BootReceiver | 完全实现设计逻辑 | 既有重启状态检查及最终H07独立SMS冷启动通过，开机事件随后执行 |
| 固定正式PCM语音测试 | 完全实现 | 用户确认耳机完整有声、音乐降低并恢复 |
| 有限长度全文MVP | 方案B已接受，受四项同时约束 | 选文/预算及超限拒绝通过，H02/H04/H05短全文真实SMS由用户确认 |
| 较长/任意长度全文 | 阶段1范围外，尚未实现 | 明确整条拒绝，不截断、不授予成功 |
| 后台、冷进程SMS、动态安全与断USB | 既定H01～H07范围通过；自然回收等另列 | 用户H06及真实H07/系统冷拉起证据；不扩展任意设备或时长保证 |

已确认真实SMS故障，修复及证据见下节。普通 am kill 未实际回收进程、开机广播未观察到，均保留局限，不补造结果。

### 真实SMS故障与修复复测

用户确认：关闭总开关不播报、开启后播报；关闭验证码筛选后只听提示音，后续切换两个规则仍失声。脱敏时序显示多条SMS解析/离线PCM合成成功（示例完整PCM约3.507秒），requestAudioFocus=1、实际蓝牙路由匹配；语音写入后收到onAudioFocusChange=-3，旧逻辑把所有非GAIN事件直接取消，约数百毫秒终结，未完成播放。此证据与通知音临时抢占相符，未收集或确定具体抢占应用。另有一条因全文音频预算超限被整条拒绝，独立记录，不能混为设置失效。

修复保留同一MAY_DUCK请求：-2/-3仅暂停本应用AudioTrack及PCM写入，GAIN时核对旧许可、实际路由和剩余完整音频预算再继续；-1永久丢失仍取消。原8秒总计时不延长，停止/超时后不接受晚到GAIN复活。设置setWillPauseWhenDucked(true)只声明本应用语音如何响应别人暂占焦点，不请求暂停音乐，不增加模式B。依据[Android音频焦点官方说明](https://developer.android.com/media/optimize/audio-focus)。新增5项状态/预算测试后共44项、0失败，构建和Lint通过；真机修复结果等待用户反馈，旧失败保留。

## 2. 五个开发批次

每批在继续叠加前均构建、单元测试和Lint，然后发起保留数据安装；vivo安装确认由用户在手机完成。人工SMS结果未完成时只推进不依赖该结果的工作。

| 批次 | 内容/提交 | 构建 | 单测 | Lint | ADB安装 |
|---|---|---|---|---|---|
| 一 | b0b184b：统一设置、总开关、写入许可 | 通过，28秒 | 21/21 | 0错13警告 | install -r成功，旧目标保留 |
| 二 | 5e63382：规则、识别、分段/防重、全文预算 | 通过，14秒 | 35/35 | 0错13警告 | install -r成功 |
| 三 | 54f55ca：正式UI、Debug诊断隔离 | 通过，22秒 | 36/36 | 0错14警告 | install -r成功 |
| 四 | 0da0e72：开机恢复、正式语音链路确认 | 通过，11秒 | 38/38 | 0错14警告 | install -r成功；随后三次真实重启 |
| 五 | 8da1929：多候选/大小写、PCM清零、ADB脚本、综合边界 | 通过，14秒；最后完整命令12秒 | 39/39 | 0错14警告 | 最终install -r成功 |

固定语音测试控件在批次三接入，批次四完成开机状态与语音验证整理。没有先写完全部代码再首次测试/安装。全部安装保留数据，未卸载、清配置、更换签名、扩大SDK或关闭系统安全限制。

## 3. 正式主界面与设置

Material3浅色/深色主题、滚动卡片、安全区域。卡片包含运行状态/总开关、两个规则、耳机名称和连接/预期路由、配对设备选择/刷新、真实权限/系统申请/应用设置、开机恢复、OriginOS背景说明、固定语音测试/取消和最近处理状态。

启用前要求已授权并选目标；可以在耳机断连时保留启用等待。准备/播报状态与真实设置联动，无“后台Service运行”表述。查询结果按请求序号及目标检查，避免旧回调覆盖新选择。返回前台或音频设备变化会刷新。配对与连接分开，播放仍核对实际AudioTrack路由。

公开应用详情Intent作为权限/后台设置入口。OriginOS高耗电状态显示“请手动确认”，不伪造已授权，不自动改安全策略。权限申请拒绝及安装器限制有说明；本轮未撤销现有受限短信权限做故障测试。

历史控件移到src/debug的非导出Stage0DiagnosticsActivity，Release合并Manifest不含该页面。历史A/B实现保留但正式音频始终强制gain=3（MAY_DUCK），正式UI无A/B切换。

## 4. 规则与识别

新安装及阶段0升级默认：运行关、验证码筛选开、精简开、开机恢复关。升级保留原target。

- 筛选开/精简开：可靠验证码生成平台及验证码语音，数字逐位；字母逐个注明大小写。
- 筛选开/精简关：可靠验证码使用完整原文；普通短信忽略。
- 筛选关：所有新普通SMS使用完整原文，精简控件禁用、原值保留。
- 总开关关：无论规则如何都不处理新短信；停止撤销在途许可并取消资源。

识别紧邻“验证码/校验码/动态码”的4/6/8位数字、4～8位含数字的字母数字组合；可含常见分隔符及为/是。平台来自开头有限格式【平台】，未知不编造。普通多数字、手机号、订单、日期、金额负例及多候选有主机测试。纯字母、复杂语义、多语言高级模板不在基础支持范围，不能保证所有验证码识别。

单次系统广播按框架片段顺序组合，要求发送方/时间戳一致，最多16段/2048字符；不跨广播拼接。进程内最多32个临时加盐摘要用于重复投递保护，不持久化正文/发送方/摘要；进程重建后的全局去重不保证。不同时间戳分段保守拒绝，真实运营商多段仍待验证。

## 5. 全文与生命周期限制

**不是任意长度全文完成。** 输入最多80个UTF-16字符；语音真实PCM≤1MiB、16位单/双声道、8～48kHz、完整时长≤5秒，且已用时间+完整语音+700ms余量<8秒。合成后及路由确认后分别检查；达不到条件在语音写入前整条拒绝并显示原因。80字符以内也可能因TTS耗时/语速被拒绝。

全文直接传原文，不删提醒、不改数字字母、不摘要、不无提示截断。播报中停止/超时/路由变化等中断记未完成，不能把已播放的片段标成全文成功。保留120秒PDU年龄、8秒elapsedRealtime预算和超时清理；内存PCM在清理时擦除，/dev/null路径不变。

[BroadcastReceiver.goAsync](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync())仍有时间限制；[Android17后台音频限制](https://developer.android.com/about/versions/17/changes/bg-audio)独立于target34。本轮不改成Service/FGS，不延长预算，不关闭AudioHardening。较长全文需要未来重新评估由用户明确触发的可见播放等方案；当前明确未完整支持。

## 6. 配置、停止与开机恢复

SettingsRepository封装同一个stage0 SharedPreferences，旧target原位保留，无DataStore风险迁移。设置写入使用apply同步更新进程内视图、异步落盘，不在Receiver做同步迁移。正常进程重建读取同一配置。

设置代次及每次非阻塞PCM写入共用锁。停止使旧许可失效，即使再启用也不会恢复旧会话；每次写入检查权限和实际路由。取消清理TTS、AudioTrack、PCM、音频焦点及goAsync结果。忙碌拒绝新任务，不排队。

开机首次Application初始化读取Settings.Global.BOOT_COUNT：若新开机且恢复关，先持久化暂停，再允许任何Receiver工作；恢复开则保留原运行值。BOOT_COMPLETED仅记录/处理状态，晚到广播不覆盖同一开机内手动启用。存储仅解锁后可用，不承诺解锁前短信、强停或厂商自动启动限制。

| 真实重启 | 重启前 | 解锁后、未打开APP | 首次启动后 |
|---|---|---|---|
| 一 | 恢复开、运行开 | 原配置仍开；未观察到独立BootReceiver事件 | 运行开，开机计数对账成功 |
| 二 | 恢复开、运行关 | 原配置仍关；未观察到独立开机事件 | 运行关，对账成功 |
| 三 | 恢复关、运行开 | 磁盘仍是上次运行开；进程未执行 | Application初始化先写为关，对账成功 |

第三组不是仅UI暂停，初始化发生在所有Receiver之前。未观察到开机广播的内部原因未定位；未模拟广播或调整厂商安全策略。重启后的真实SMS能力仍待用户验证。测试结束已通过真实UI恢复此前运行开、恢复开、两规则开及原目标。

## 7. ADB、UI和人工证据

- 初始唯一在线授权真机为预期vivo；另有离线模拟器，所有操作均限定目标真机。
- 既有APP已安装；User0两权限granted，RECEIVE_SMS AppOps allow、安装器exempt标记存在；原型focusMode=3。只读取配置/脱敏状态，不读短信收件箱。
- 五批install -r均Success；旧目标在首批及最终安装后指纹一致，指纹和身份值不提交。
- ADB UI检查主标题、两规则、筛选关闭时精简禁用、值保留及恢复均通过；最初快照在页面下半，未错误当成缺UI。最终浅/深截图人工视觉检查正常，原系统auto主题已恢复。
- 普通Home后am kill未真正回收进程；重新打开配置一致，但**不记为冷进程回收测试通过**。重启启动与仓库重建单测是独立证据。
- 观察到固定测试PCM全部写入、播放完成及焦点释放回调；用户明确确认“指定耳机完整有声，音乐降低并恢复”。该结果不包含真实SMS或本轮断网听验。
- 用户已开始真实SMS/断USB人工验收，尚未反馈的项目仍未验证。没有模拟SMS，不能用按钮回调替代。

此前阶段1焦点修复APK（模拟工具补充前）：24,245,331字节；SHA-256 `cfe54f9e5516a8c644b4f277c5f71a8c62d660a2f2538a6e53c616de19e00599`，保留数据更新Success。本地构建证据在app/build，不上传APK、UI原图、配置或原始日志。此前五批与本轮模拟工具构建分别记录。

## 8. 阶段0回归状态

| 项目 | 本阶段证据 | 结论 |
|---|---|---|
| 离线中文Voice及PCM架构 | 严格Voice筛选与原PCM路径保留；固定测试有声 | PCM固定测试通过；本轮断网未验证 |
| 音乐降低、结束恢复 | 用户固定测试确认及释放时序 | 固定测试通过；真实SMS待验证 |
| Home、锁屏、静置 | 原链路保留 | 本阶段真实SMS未验证 |
| 耳机断开/重连、路由切换 | 原门禁保留，新增运行许可 | 本阶段动态真实回归未验证；不承诺缓冲窗口零泄漏 |

docs/stage0-report.md和stage0-test-plan.md与基线无差异，历史验收没有重写，也没有当本轮新回归结果。

## 9. 自动化与代码审查

最终执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

五批最终检查BUILD SUCCESSFUL，12秒、39测试。随后焦点故障修复再次执行同一完整命令：BUILD SUCCESSFUL，14秒；44测试、0失败、0错误、0跳过；Lint0错误、14警告。警告为固定target、Gradle/Compose/Activity版本提示及KTX写法建议，未为此升级或掩盖警告。额外processReleaseMainManifest成功。

Debug/Release合并权限均恰为RECEIVE_SMS、BLUETOOTH_CONNECT、RECEIVE_BOOT_COMPLETED；Service数0，Release无诊断Activity。未新增应用依赖或网络权限。静态审查：实际PCM仍严格目标路由、MAY_DUCK、结束释放；没有全局音量/播放器控制或正文落盘。测试覆盖配置升级/重建、规则依赖、许可竞态、识别/格式化、多数字负例、多段身份/时间、防重、全文/PCM预算及状态映射。没有故障注入便不授予TTS/焦点失败的真机通过。

## 10. 原阶段1Git交付（模拟工具补充前）

五批代码提交见上表，后续焦点修复代码提交为fa395c2；文档提交包含需求、T/R/L逐项清单、实际报告及README。已普通推送origin/main；焦点修复推送后本地与远程完整SHA均为fa395c26e6fdc3941be991cb937174ebcd0d2887，工作区干净。本次收尾文档提交的最终SHA由交付消息和Git记录给出，避免文档自引用。推送前检查diff、忽略规则与敏感字段；不提交APK/构建/截图/原始日志/设备身份/密钥。保留阶段0历史与现有用户配置。

## 11. 验收与继续条件

T01～T15、R01～R20、L01～L10的实际状态见[完整测试清单](stage1-test-plan.md)。当前仍不能宣布阶段1通过：方案B已接受，其他安全/真实SMS条件未补齐；已发现规则切换测试中的临时焦点中断故障，修复版待真实复测。初始总开关启用/停止由用户确认；真实SMS三种规则、播放中停止/竞争、Home/锁屏、动态断连、权限撤销、后台静置及断USB结果仍待确认。保留已知失败；未测试的项目不判通过或失败。

补齐必要人工验收并处理真实回归后再判断是否通过；本任务不进入阶段2。

## 12. Debug模拟工具补充与验收准备（2026-11-11）

本轮基线与远程均核对为92e477e3997c08ab097d36b499b8f472d66fb877，开始时工作区干净；阶段0历史文件未改。使用AndroidX instrumentation，仅在androidTest中保存固定fixture与入口。SmsReceiver保留原Manifest及BROADCAST_SMS属性，只由真实系统广播解析PDU；解析后与模拟输入共同调用SmsCoordinator。共享层同样检查运行、权限、片段/有效期、规则、防重，再进入原SpeechSession。PCM内部仅增加只读终结遥测，原合成、路由、焦点、写入和清理策略未重构。

### 构建与安装

最终完整命令包括assembleDebug、testDebugUnitTest、lintDebug、assembleDebugAndroidTest、assembleRelease，BUILD SUCCESSFUL（11秒）；51项单元测试，0失败/错误/跳过；Lint0错误、16警告，新两条为测试依赖版本提示。测试依赖只在androidTestImplementation，未升级原应用依赖/SDK、修改包名或签名。

目标vivo V2405A，实读Android17/API37；Debug与测试APK均install -r成功，测试APK额外-t允许testOnly安装，不加-g。首次内部startActivitySync超时的工具故障已通过就绪握手、ADB正常打开已有正式Activity解决；没有新增导出注入入口或放开后台安全限制。所有测试是前台instrumentation，不是Home/锁屏系统收信测试。

最终Debug APK 24,220,307字节，SHA256 `5522d979ee9d1645351676d800872138642aa5af0157ab6599e3d7bb3097e11b`；测试APK 864,602字节，SHA256 `62f38cadecc0761560a39780f4740b84ebb80e0bcc05a38f2dd656eecece4636`。Release unsigned APK构建成功，仅作隔离检查，未替换签名或安装到手机。

### 实际设备证据

Otp、Normal、Long、All四种命令实际执行并返回0。最终All每次1个JUnit入口、22条业务子结果：PASS8、MANUAL_REQUIRED8、UNSUPPORTED5、SKIP1、FAIL0。D12在All时因目标仍连接而SKIP，用户随后实际断开耳机，单独Disconnected命令PASS，PCM写入0；之后用户重连。不能把命令退出0解释成全部D用例已可完整播报。

完整[D01～D14结果表](debug-sms-testing.md)记录具体PCM时长及各子结果。用户明确确认三种短模拟样例（精简验证码、短验证码全文、短普通全文）均完整从指定耳机听到。这不包含本轮音乐听感、专项焦点恢复听感、后台或真实SMS。

D13由实际AudioManager竞争请求产生系统-3和GAIN回调，记录[-3,1]；断言暂停期间PCM写入不增长、恢复时实际路由仍匹配、完成后焦点释放。另两组实际临时抢占期间停止/原8秒超时，释放竞争者后不复活，最终超时elapsed约8008ms。没有手动调用焦点回调冒充系统事件。永久丢失-1及晚到回调仍只有单元测试证据；真实SMS通知音的原故障仍需少量真实SMS复核。

D02原文7.707秒、D04全文7.787秒、D05混合码精简语音8.227秒、D07的80代码单元17.227秒、D09的合法长度原文8.107秒，均整条拒绝、语音写入0；D08的81代码单元在TTS前拒绝。这些安全断言通过不代表全文/混合码完整播报通过。80代码单元上限也不是可播成功长度保证。

D10三个解析后输入，一条播放、两条BUSY，所有终结及焦点释放断言通过；D14只证明两个虚拟片段的业务组合，不证明运营商PDU排序、重组或广播投递。当前未对native资源泄漏作独立量测，不用会话清空替代全部资源故障注入。

### 隔离、隐私和配置

实际Debug/Release合并Manifest权限恰为RECEIVE_SMS、BLUETOOTH_CONNECT、RECEIVE_BOOT_COMPLETED，Service数0、目标APK instrumentation数0，真实SmsReceiver仍导出且要求BROADCAST_SMS、action仅SMS_RECEIVED。测试APK移除了AndroidX三个导出占位Activity与REORDER_TASKS；无Activity/Receiver/Service/Provider、无uses-permission，唯一入口是测试instrumentation注册。

scripts/Verify-DebugSmsIsolation.py检查上述实际Manifest与三份APK DEX：入口类、fixture、就绪/结果协议及虚拟正文只在测试APK，Release和目标Debug APK均未包含；来源枚举在main仅作诊断标签，不是组件或安全特权。Release不含历史诊断Activity。未新增网络/历史短信权限、联网代码、Service或后台轮询。

模拟记录source=DEBUG_SIMULATION、独立SIM编号、SMS_RECEIVED未触发、无PendingResult.finish；真实路径source=REAL_SMS。所有原始本地输出都在被忽略的app/build，未收集真实正文/号码。最终四个配置值和目标耳机指纹与本轮安装前一致，私有配置未发现fixture正文或格式化虚拟验证码文本；不持久化虚拟正文、验证码或PCM。

### 剩余验收与范围决策

[45项剩余验收表](stage1-remaining-acceptance.md)逐项列出编号、名称、当前状态、模拟/真实SMS要求、断USB、听感、阻塞、证据和下一步。优先补原真实SMS通知故障的三种短行为，再做动态断连/路由、断USB的Home/锁屏/静置/音乐、重启未打开APP前真实收信、冷进程与真实多段。

原需求是所有新普通SMS全文播报，当前实现仍部分完成。候选A为继续保持原要求并解决超限，候选B为用户明确接受阶段1有限全文、任意长度另立后续任务；该段为此前状态；用户现已明确选择B，范围变更以第13节为准。本轮只补模拟能力与证据，不宣布阶段1通过，也不进入阶段2。最终Git提交和普通推送后的本地/远程SHA由交付消息提供。

## 13. 方案B正式范围决定与本轮收尾

用户明确选择方案B：阶段1接受有限长度全文MVP。80个UTF-16代码单元、完整PCM最多5秒、数据最多1MiB、会话原预算8秒同时生效，任一不满足整条拒绝，不能截断或回退扬声器。现有700ms余量、120秒消息有效期和其他安全门禁保持不变。

D02/D04/D05/D07/D09超限结果保留；它们的安全拒绝符合新MVP范围，不能改写成这些长内容已完整播放。混合验证码D05已识别但语音8.227秒，属于明确限制。较长/任意长度全文尚未实现，后续独立专项，本轮不实施。README已明确提醒音频预算可能使SMS无法播报。

此次批准只解除任意长度全文的范围阻塞，不批准其他测试通过。阶段1仍为待验收。用户本轮要求先完成文档及无需手动操作的测试；动态断连/路由、蓝牙撤权恢复、真实SMS、断USB/重启操作保留待执行。自然进程回收和真实运营商多段保留未验证风险，非本次强制放行条件；若发生真实关键缺陷仍阻塞。H07新进程真实收信及其余12项强制放行条件不豁免。

### 本轮第二批的实现与实际结果

新增StopDuring（正常PCM播放时停止）、RulesDuring（正常播放时改规则，旧许可失效）、TtsFailures（受控初始化/合成错误）、FocusFailure（受控请求失败）、FocusPermanent（仅无外部音乐活动时制造实际系统永久焦点丢失），以及需人工操作的动态断连、切输出和蓝牙权限检查。共享层/音频仅增加默认委托平台的普通依赖边界；没有改音频格式、PCM写入/路由策略或四项限制，没有产品运行时故障选择器。受控类只在androidTest。

所有新设备用例均NOT_RUN。实际正常install -r更新返回INSTALL_FAILED_ABORTED: User rejected permissions；未卸载、清数据、换签名、更改权限或绕过安装检查，也没有要求用户在其已延期的操作上立即配合。手机保留旧版本，配置及目标耳机指纹与本轮开始一致。新增脚本已校验本地与已安装两APK的SHA，真实验证旧构建不匹配时在启动instrumentation前退出非零；这不等于任何新安全测试通过。

主机最终命令assembleDebug/testDebugUnitTest/lintDebug/assembleDebugAndroidTest/assembleRelease成功（5秒），51项单元测试，0失败/错误/跳过；Lint0错误16警告。PS7解析通过。Manifest/三APK DEX隔离检查通过，新增失败替身类只在测试APK，三项原权限、无Service和真实SMS Receiver属性均保持。最初测试替身的Bundle可空签名编译错误已按本地SDK36签名修正，最终构建通过；不把未编译/未安装的尝试记设备通过。

本轮Debug APK SHA256 `02538983935556d9e44391e479ac50fe093c70ab7ed8695bdfdee15ac093723b`（24,220,647字节）；测试APK SHA256 `7e38f5e8970c087843c60d342ecf53fb8fd19b9b7fadc666865fb71ab2a111ab`（892,778字节）。均仅在本地build，不上传二进制/原始日志。新测试以后执行时须明确区分CONTROLLED_SUBSTITUTE和真正Android回调，不依据API完成回调推断听感。

### 强制人工验收与当前结论

新[最终验收操作单](stage1-final-acceptance.md)给出L01/L02/L03/T10/L05/L06/R20操作、受控证据边界和H01～H07真实SMS顺序（含物理断USB的Home/锁屏/静置/音乐/断连重连/开机首次解锁未开APP）。本轮用户明确先完成文档与非手动测试，这些人工场景均未执行，未发模拟广播替代真实SMS，未自动重启或am force-stop。

目前没有新增已确认的关键安全缺陷；但原真实SMS通知失声尚未完成真实修复后复核，动态路由、权限恢复及新故障测试缺证据，不能据此确认无关键安全问题。范围B已正式接受，仅解除任意长度全文范围阻塞。其他12项放行条件仍须满足，阶段1保持待验收，不进入阶段2。

## 14. 蓝牙重新授权恢复缺陷（2026-11-11，本轮）

### 原始失败与诊断

用户实际撤销BLUETOOTH_CONNECT后，APP正确禁止测试；重新授权返回同一APP进程，耳机连接true，但运行状态路由不匹配，测试按钮禁用。清理后台重开或运行instrumentation后恢复。用户按诊断版复现，并确认蓝牙区域手动“刷新”无效。此失败记录保留。

脱敏日志确认：正常时目标输出匹配、预期设备数1；撤权后Android自行启动的拒权进程在重新授权后，checkSelfPermission=true、profile连接=true、蓝牙音频输出数2，但目标输出匹配false、预期设备数0。onResume和refresh均已运行。故不是仅缺少onResume、只更新Compose状态或把配对当连接的问题。日志未记录地址/设备名、真实短信内容或号码。系统撤权引起的进程变化与人工清后台不同，修复不得依赖用户重启。

Android17官方源码提供与现场一致的缓存机制：AudioManager的getDevices使用进程静态音频端口缓存；getAudioDevicesForAttributes将当前服务端描述符按type/address映射到该缓存；AudioPolicyService在缺蓝牙权限时脱敏端口地址。普通重新注册监听不主动清除端口缓存。因此拒权阶段的音频查询可导致授权后地址仍不匹配；新进程清空静态缓存，解释重开和instrumentation的表象。缓存机制为官方源码分析，设备现场事实为前述计数/权限日志；未把AOSP源码等同于可读取的OriginOS内部实现。

参考：[AudioManager Android17](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/media/java/android/media/AudioManager.java)、[AudioPortEventHandler Android17](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/media/java/android/media/AudioPortEventHandler.java)、[AudioPolicyService Android17](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android17-release/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp)。

### 第一轮修复与验证边界

ForegroundAudioAccess集中管理前台监听注册/注销及查询代次：只有实际权限允许时注册；拒权时清空UI设备/连接/路由状态，停止监听；恢复后重新读取配对列表、profile连接及预期音频输出；旧请求、旧目标、已离开前台或已撤权的回调不能更新就绪状态。真实播放的AudioTrack路由门禁保持不变。没有修改正式SMS Receiver、规则、持久化配置、TTS或PCM内部流程。

新增8项受控单元测试覆盖拒权启动、授权恢复、撤权注销、旧回调、目标变化、离开前台及反复循环；这些不冒充真实权限变化。ForegroundBluetoothRefreshTest在实际设备上验证五次Home/返回的真实Activity生命周期和实际权限读取；真正撤权会终止目标进程，因此完整撤销/重新授权另需人工两轮确认。现有Readiness/RoutePolicy测试继续覆盖已连接但路由不匹配时拒绝。

当前主机构建Debug、androidTest、Release及testDebugUnitTest/lintDebug通过：59项单元测试、0失败/错误，Lint0错误16警告。实际Manifest/DEX隔离检查通过：三项原权限、Service0、受系统权限保护的SMS Receiver保持，Release无模拟入口。最终设备回归和本轮修复结论待后续证据补齐，不以构建成功代替真机恢复。

### 补录的用户验收证据

用户本轮明确报告Safety六项自动化通过、L02动态断连与L03路由切换自动化及人工通过；T10实际撤权保护PASS，未撤权及恢复后的权限拒绝用例SKIP。上述是用户提供的后续结果，保留此前安装器拒绝/NOT_RUN历史。T10的SKIP不能证明重新授权恢复，新缺陷在完成复测前仍阻塞。本轮不判定阶段1正式通过。

### 第二轮恢复方案（设备验证前）

第一轮仅按权限延迟注册监听的候选版，现场仍记录一次授权后目标输出false/预期数量0，不能视为修复通过。新增前台、单次、有界的AudioPortRefresh：只在实际蓝牙权限允许、profile确认连接但严格路由不匹配、无进行中语音会话时触发。通过公开AudioTrack路由回调使框架重新获取端口信息；Android17的AudioTrack.broadcastRoutingChange在分发回调前重置AudioManager端口代次（[官方源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/media/java/android/media/AudioTrack.java)）。这是公开回调机制的兼容性处理，不调用隐藏API/反射，不修改系统策略。

恢复轨道音量为0，不调用任何write方法、PCM写入恒为0、不持有短信或验证码、不申请焦点、不改全局音量，仅偏好现有蓝牙输出，800ms超时或离开前台立即释放。它的路由不作为目标耳机身份或播报许可；回调/超时结束后必须重新读取已配对列表、profile连接、目标完整地址和预期音频路由。检查仍不匹配则仍然禁止播报。每次前台/权限变化最多一次，无后台轮询；真正语音播放架构未改。

新增真机测试分别验证真实路由回调后释放并严格重查、取消后晚到回调不再更新、真实Activity连续前后台5次。这些测试与用户实际撤销/恢复两轮分开记录；原始T10恢复失败不能被新进程instrumentation启动掩盖。设备结果待后续补录。

### 第二轮现场观测（手动听感仍待确认）

应用与测试APK均已正常install -r更新成功，保留数据。恢复版本的同一processRun中实际观察到：onResume蓝牙权限false → onResume权限true → profile连接true而targetOutput=false/predicted=0 → audioPortRefresh真实routingCallback=true、pcmBytesWritten=0、focusRequested=false、released=true → 完整routeProbe恢复targetOutput=true、predicted=1、predictedTarget=true。该序列中没有进程重启或instrumentation；因此不是用启动测试掩盖状态问题。按钮、固定语音及第二次撤权/恢复的用户确认尚待补录，不凭这些API日志宣布听感通过。

### 最终真机确认与工程结果

用户明确确认最新版无需清理后台/重开/手动刷新，两轮真实系统撤权→返回禁止→重新授权→返回均自动恢复；测试按钮可用，固定语音完整从指定耳机听到。现场日志对应每轮在各自拒权启动进程内完成授权后的恢复，不是重启掩盖。Android在撤权时自行结束旧进程是系统权限机制，恢复不要求用户重新启动。

人工完成后才运行instrumentation，避免它重建进程干扰恢复现场。ForegroundBluetoothRefreshTest实际OK (3 tests)：真实Activity五次Home/返回/权限与设备路由重查、真实空轨道路由回调后释放并严格重查、取消后晚到回调不生效。无跳过或失败。随后Test-DebugSms.ps1 -Case Safety返回0、6条PASS、FAIL0/SKIP0：L01.normalStop和R20.ruleChange在正常PCM实际写入后停止/改规则并终结不复活；L05.init、L05.synthesis和L06.denied明确使用CONTROLLED_SUBSTITUTE，零PCM且清理；L06.permanent通过ANDROID_SYSTEM实际-1取消及释放、不复活。这些均不声明收到真实SMS或提供新的音乐听感证据。

最终Debug/androidTest/Release构建、59项单元测试（0失败/错误）、Lint（0错误16警告）通过；实际合并Manifest及三APK DEX隔离检查通过，新增真机测试类仅在测试APK。原三权限、Service0、真实SMS Receiver及PCM核心代码保持。应用和测试APK保留数据更新Success；配置四布尔值、目标耳机指纹与本轮开始一致，无敏感正文/地址/设备序列号/原始日志进入Git。

结论：本次蓝牙权限恢复缺陷已修复并通过目标机重复验收；T10本次蓝牙范围通过，原失败及第一候选失败记录保留。其他真实SMS、通知音焦点、后台及断USB待验收事项维持原状态；阶段1整体仍待验收，不进入阶段2。Git普通提交/推送及最终SHA由交付消息报告。

## 15. H01真实SMS通知音中断阻塞（2026-11-11）

### 原始失败与实际根因

基线main为873897407b9ff57a4faed008b4359fb93735501a，开始时工作区干净；API37真机在线，离线模拟器未参与。用户报告前台真实验证码即时开始但被短信通知铃声打断、未完整，无手机扬声器验证码泄漏。H01=FAIL；H02～H07暂停，不用Safety/L02/L03/T10历史通过覆盖。

既有脱敏REAL_SMS会话SMS-000047给出以下相对BroadcastReceiver.onReceive时间：

| 时间ms | 真实应用事件 |
|---|---|
| 0 | 真实SMS_RECEIVED；总预算从此计时 |
| 137 | 完整离线PCM合成完成；预计语音3187ms |
| 141～144 | 请求MAY_DUCK；系统返回GRANTED=1 |
| 188 | 首次语音PCM写入，实际目标路由匹配 |
| 922 | 实际onAudioFocusChange=-3（CAN_DUCK） |
| 925 | APP主动pause，等待临时焦点恢复 |
| 5788 | 实际onAudioFocusChange=1（GAIN） |
| 约5790 | 剩余播放预算不够，APP主动取消未完成语音 |
| 5800～5804 | abandon焦点并确认释放 |
| 5816 | INCOMPLETE；PendingResult.finish |

确认的直接原因是临时占用约4866ms后剩余预算不足，不是把-3误当永久丢失，也不是没有GAIN或Handler先到8秒。未观察到路由变化失败。原日志没有最终累计写入/恢复时精确剩余帧，不能补造；用户报告通知铃声与应用焦点事件相符，但没有据此猜测具体竞争包身份、系统内部通知实现或音频声长。

### 受控复现与时序修复

先仅补脱敏遥测及测试，不更改产品时序。NotificationRepro以通知USAGE+SONIFICATION的真实Android MAY_DUCK请求，在解析后输入约900ms抢占、4866ms后释放，实际得到[-3,1]：PCM2827ms、完整135680字节，仅写入4800字节；GAIN时预算约2225ms、剩余PCM约2884ms，正确拒绝恢复且释放。这是受控焦点竞争复现，不播放测试通知音、不生成系统SMS、不等于运营商通知；PASS只表示失败机制复现/安全断言成立，不表示H01通过。NotificationBefore同时证明已有通知焦点时，随后初次请求本APP的MAY_DUCK可获合法许可并完整播放，竞争者收到实际-3。

最小产品修复为SpeechStartPolicy：所有解析后SMS（真实和模拟相同）在接收后1200ms之前不做首次焦点请求；TTS合成并行，不是合成后再加1200ms。合成慢于窗口则不再等。等待结束检查原许可、权限、原8秒预算，重新查询目标连接/预期路由后才进入原PCM/focus/实际路由流程。固定手动语音测试不增加等待。窗口为本设备已观测约922ms通知焦点启动提供余量，是有界调度缓冲，不检测或保证任意厂商通知时序；很晚或持续占用仍可能安全取消，不能承诺所有系统通知都不会打断。

MAY_DUCK、MEDIA+SPEECH、setWillPauseWhenDucked(true)、-2/-3暂停、GAIN时原许可/路由/剩余预算检查、-1取消、700ms路由与清理余量、8秒/5秒/1MiB/80UTF16均未改。不在失焦后重新抢焦点、不无限等待、不关闭通知或安全机制、不更改全局音量/第三方播放器，无Service。参照[官方音频焦点说明](https://developer.android.com/media/optimize/audio-focus)：临时失焦不能视作完成，恢复仍须合法焦点。

遥测增加匿名会话/来源、焦点事件及派发时刻、剩余总预算/未播放PCM时长、累计/完整字节数、暂停/恢复/拒绝决策、实际路由确认与终结/释放。所有数据仅生成状态/数值，不含真实正文、码、手机号、地址、设备身份或PCM。TTS完成、焦点请求和首次写入原时间点保留；晚到焦点会记录ignored，不恢复终结会话。

### 当前构建和补测（待后续结果补齐）

Debug/androidTest/Release构建、65项单元测试（0失败/错误/跳过）、Lint0错误16警告及三APK Manifest/DEX隔离通过。新增6项时序单测，原59项保持；无新增权限或Service，真实SmsReceiver/解析/规则不变。APK均install -r保留数据更新成功。

修复版NotificationStartup（约900ms通知竞争）、NotificationBefore均完整写入135680字节、实际路由确认并释放，API断言通过，听感单列待确认；NotificationLate刻意在语音已开始后再长期占用，实际[-3,1]、写入6848字节，恢复预算不足安全取消。Focus三项实测：短[-3,1]恢复完整；暂停期间停止不复活；持续失焦约8011ms按原预算超时/释放、晚释放竞争者不复活。这些不等于本轮真实SMS复测通过。

H01原FAIL保留，只有用户确认最终版真实验证码完整播放且安全检查满足才更新为PASS；否则保持阻塞。阶段1整体待验收，不进入阶段2。

### 修复后真实SMS及音乐确认

用户已明确报告最终版一条真实短验证码“及时开始、验证码完整、没有再次被铃声截断、无手机扬声器语音”。对应REAL_SMS匿名会话SMS-000048：合成176ms完成；窗口内并行合成，实际再等1022ms；1222～1224ms首次请求焦点返回1，1283ms首次语音写入，4408ms全部写入，4627ms完成标记，4665ms焦点释放成功，4679msPendingResult.finish。完整PCM3187ms，152960/152960字节写入，剩余PCM0；播放期间本APP无失焦回调，实际路由匹配。不能虚构窗口前其他应用的焦点回调，但本APP完整播放与用户实际通知铃声场景复测均有证据。

音乐已由用户实际播放后，再运行一条固定模拟Otp：完整PCM4027ms、193280字节、实际路由匹配且释放；用户确认“语音完整、音乐降低后恢复”。此为模拟SMS业务加真实音乐听感，不伪装成H04真实SMS或后台验收。

用户本机后续日志还实际观察到一条解析后模拟会话：真实-3约1295ms、pause约1299ms，约2304ms目标输出被移除后安全取消，累计PCM保持4800字节，约2321ms释放成功，未复活。该自动安全证据成立；用户表示此前不知道操作开始时机，故暂停断连后的手机听感仍单独待明确确认，不能因日志替代。此前两次等待未观测断连为SKIP并保留。脚本现用中文【现在断开耳机】即时Host提示、UTF8输出及3秒准备倒计时（在提交/8秒计时前），便于本机执行；不延长会话预算。

当前H01真实复测成功，原FAIL永久保留；本轮其他焦点/路由安全断言及音乐回归已完成，暂停断连的人工听感待收尾。H02～H07本轮不推进，阶段1仍待验收。

### 本次最终结论（后续结果覆盖上述过程中的待确认状态，失败历史保留）

用户随后提交本机PauseDisconnect截图：D13.disconnect自动断言PASS，实际focusChanges=[-3]、目标输出移除后INCOMPLETE（预期的中途取消）、写入4800字节、实际路由曾确认、焦点释放true、无旧会话复活。用户进一步明确确认“没有手机扬声器语音”。因此暂停断连专项自动与人工通过，之前两次SKIP不改写。MANUAL_REQUIRED是自动断言完成但仍需听感的工具状态，用户补充的听感结论单列，不伪造工具原始输出为PASS。

H01现在标记PASS：真实短信及时开始、验证码完整、无再次铃声截断及手机语音；真实时序约1.224秒获焦点、4.679秒广播终结，字节152960/152960，期间无本APP失焦，焦点正确释放。相关实际-3恢复、暂停停止/超时、预算不足取消、实际-1、动态断连及严格路由检查通过；迟到GAIN以受控状态机测试和实际竞争者晚释放不复活为证，不声称强制制造了真实晚到回调。音乐下模拟SMS用户确认语音完整、降低及恢复。任意较晚/持续的焦点占用仍可能安全取消，1200ms窗口不构成跨设备/任意通知时序保证。

本轮真实SMS只补发一条成功H01，不要求继续发H02～H07。65单测0失败/错误/跳过，Debug/androidTest/Release构建通过、Lint0错误16警告、Manifest/DEX隔离通过，PowerShell7解析及UTF8脚本实际执行通过。修复版设备业务自动断言共14项满足：Notification三项、Focus三项、Safety六项、暂停断连及音乐Otp各一项；部分完整播放样例的工具原始状态仍是MANUAL_REQUIRED，听感只授予用户实际确认的范围。原版机制复现两项与两次SKIP另记，不混算H01通过。

原8秒/5秒/1MiB/80UTF16、MAY_DUCK、离线TTS、严格身份和实际路由门禁、无Service/网络/全局音量控制均保持。没有未解决的本次H01关键缺陷；H02～H07继续本轮暂停/未执行，后台、断USB、后续真实规则和开机收信仍需安排。阶段1整体待验收，不进入阶段2。正常提交推送及本地/远程SHA核对结果由交付消息给出。

## 16. H07重启后首次SMS：现场保护、系统设置对照与独立冷启动（2026-11-11）

### 原始失败与只读取证

本轮基线main为572729765402db92b7236c61ee8675ffd42315ec，开始工作区干净。用户明确H01～H06已通过；H07原始FAIL：正常重启、首次解锁、指定耳机连接、未打开APP，真实SMS只响系统铃声，没有APP语音或手机扬声器内容。原FAIL保留，不以模拟替代。

最初ADB仅有离线模拟器，用户按要求只重连USB；随后实读唯一在线vivo V2405A、Android17/API37。取证前后未启动Activity、安装、force-stop、清数据、发送模拟广播或改系统策略。初始启动计数71，用户0已RUNNING_UNLOCKED、sys.boot_completed=1，APP无进程；包installed/非标准stopped、两个Manifest Receiver可解析、user0短信/蓝牙权限已授予，短信受限权限有安装器豁免，AppOps RECEIVE_SMS允许。其他用户的拒绝条目不误算为当前用户缺权。

私有配置：announcingEnabled=true、bootRestoreEnabled=true、目标存在，但stage1BootCount仍70；最后保存的真实SMS诊断来自重启前。当前app专用日志为空，events没有目标进程创建记录。系统保留SMS广播总体摘要，但无目标接收器投递/具体拒绝记录；AppOps检查允许及全局SMS摘要均不能证明本APP收到广播。没有捕获原始H07的精确接收/拒绝时序，不能虚构“系统明确deny某条广播”日志。系统私有dumpsys也未提供可用的自启动状态。

用户随后在系统设置明确确认“自启动、关联启动均关闭”。此时应用内恢复开关虽开，不能授予厂商启动许可。主APP及APK保持不变，只请用户开启自启动，关联启动保持关闭，进行真实重启/未开APP/断USB收信对照。记录分别来自启动计数71、72、最终74，不能把不同重启的uptime拼成一次连续时间线，也不虚构中间重启的测试详情。

### 最终真实H07证据与根因范围

用户确认最终成功条件：自启动开、关联启动关；从最后重启到收信未打开APP，收信时USB断开、耳机连接；真实短验证码及时完整播报且无手机扬声器语音。由用户随后连接USB采集的最终启动74中：

- 系统events的am_proc_start明确启动原因为broadcast，组件为本项目SmsReceiver；不是先打开Activity或instrumentation。
- Application.onCreate新进程事件先于真实SMS处理；REAL_SMS匿名会话SMS-000057的ActivityStarted=false，后台importance=300，无MainActivity恢复事件。
- 原始接收起相对时间如下：

| 时间ms | 实际事件 |
|---|---|
| 0 | 真实SmsReceiver.onReceive |
| 1895 | 冷进程离线PCM合成完成 |
| 1954～1965 | 请求原MAY_DUCK，系统返回GRANTED=1 |
| 2112 | 严格实际路由匹配后首次语音写入 |
| 5161 | 全部PCM写入 |
| 5368 | 播放完成标记 |
| 5389 | 焦点释放完成且成功 |
| 5395 | 业务COMPLETED，PendingResult.finish |
| 约5404 | 随后才出现BootReceiver专用“开机状态已恢复为启用”事件 |

完整PCM3147ms，151040/151040字节，最终未播放PCM0，目标实际路由已确认，未失焦；原8秒预算内完成。应用保存启动计数变为74，enabled/restore保持true。BootReceiver专用事件文案只存在于该接收器，取证发生在任何测试APK安装/运行之前，故有真实开机接收器执行证据，且晚于本次真实SMS完成。

实测定位为本机OriginOS自启动门禁条件：关闭时未观察到冷拉起/处理，开启后同一APK由SMS独立创建进程并完成。没有捕获厂商内部拒绝规则，因此不声称逆向证明了具体拦截函数；设置前后对照和实际冷启动事件支持该运行条件。关联启动在关闭状态已通过，本轮无证据要求开启。允许后台高耗电仍按阶段0/本机既有条件保留，不自动改其他策略。

### 代码审查的八个问题

1. SMS接收不依赖先收到BOOT_COMPLETED。Manifest SmsReceiver是独立入口；真实进程启动原因及迟到开机事件已实证。
2. SMS_RECEIVED声明、BROADCAST_SMS发送方保护、导出属性正确，当前系统可解析。SMS/开机广播属于[Android隐式广播例外](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)；[Manifest Receiver可独立启动进程](https://developer.android.com/develop/background-work/background-tasks/broadcasts)，但厂商额外门禁仍影响实际投递。
3. 开机恢复只对账已保存启用/暂停：ProbeApplication.onCreate即初始化SettingsRepository，读取系统启动计数；恢复关则先持久化暂停，恢复开保留原状态。BootReceiver不启动Service/Activity/播报，也不充当保活。
4. 配置不依赖Activity。首次SMS启动Application时已初始化设置，迟到BootReceiver不会把已知启动计数下的状态重复重置。
5. 协调器、Handler、蓝牙查询、离线TTS、AudioTrack及焦点均从Application/会话按需创建，无MainActivity初始化依赖。MainActivity的连接状态仅用于UI，不代替真实播放门禁。
6. 本次真正冷启动已独立完成蓝牙、TTS及焦点；新无Activity测试补故障清理。未发现需修改已验证PCM内部实现的证据。
7. 用户设置状态和同APK对照支持OriginOS自启动许可为必要运行条件，原失败的具体系统拒绝日志缺失，不以推测补造。
8. H06既有/已启动进程能后台处理，不证明重启后的新进程一定获厂商启动许可；H07测试的是另一条冷拉起边界。

首次解锁已被系统确认，默认凭据加密配置可读。未引入Direct Boot存储或解锁前处理；[Android Direct Boot说明](https://developer.android.com/privacy-and-security/direct-boot)与当前解锁后范围一致。

### 本轮修改、测试及证据边界

没有修改main生产代码、Manifest、设置键、权限、SDK、签名、短信规则、TTS或PCM。主Debug APK在真实复测前后及最终构建后SHA与已安装APK相同；本轮只更新独立测试APK，目标APP未重装。实际修复是用户开启系统自启动，关联启动保持关；README/测试文档补齐此条件，不把它伪装成代码修复。

新增ColdStartInitializationTest共8项真机JUnit全部实际通过（无失败/跳过、OK(8 tests)）：

| 需求 | 证据 | 实际边界 |
|---|---|---|
| A：无进程时SMS独立启动 | 真正H07的am_proc_start/SmsReceiver、REAL_SMS和用户听感 | 非模拟；未用force-stop、am kill或模拟系统SMS制造投递 |
| B：恢复开读取保存状态 | 真实H07；新无Activity私有测试配置读写 | 测试配置使用合成启动计数、不冒充系统重启 |
| C：恢复关保持暂停；原暂停保持 | 新真实SharedPreferences包装测试2项、既有设置单测/重启历史 | 本轮不新增恢复关的真实SMS样本；测试只删除自己创建的唯一临时配置文件，不碰stage0 |
| D：无Activity初始化 | 新进程无任何Activity的协调层受控无音频注入；8项每项检查生命周期监控为空 | instrumentation会创建目标进程，不能代替真正系统广播证据；A另有真实H07 |
| E：身份/路由无法确认安全拒绝 | 新平台BluetoothProbe合成未配对目标返回无连接/无输出；H06既有真实断连通过 | 不冒充本轮实际物理冷启动断连 |
| F：TTS/焦点异常清理 | 新3项CONTROLLED_SUBSTITUTE初始化、合成、焦点拒绝；零PCM、stop/shutdown、释放及空会话 | 不声称真实引擎/系统恰好发生错误 |
| G：H01～H06不回归 | 用户本轮明确六项通过；生产APK字节不变、原单测与Manifest/DEX检查 | 不把新增instrumentation当作这些真实场景的重新实测 |

Debug、androidTest、Release构建及testDebugUnitTest/lintDebug成功（46秒）；65项既有JVM单测有效缓存通过，0失败/错误/跳过，Lint0错误16警告。三APK隔离检查通过，新冷启动类只在测试APK；三项原权限、Service0、受保护真实SMS Receiver均保持。

Test-Stage1Device.py默认不带参数现增加进程前后、两个Receiver可解析和留存app日志入口计数，实机执行通过；不打开APP、不授权、不清数据、不发广播，只输出允许的计数/配置。日志计数仅代表当前保留内容，非投递证明；带UI/设置修改参数仍会打开APP，保护现场时禁止使用这些参数。完整原始系统日志仅本地忽略目录app/build/h07-private分析，不进入Git；公开文档只有脱敏结果。

### H07判定及阶段边界

原H07=FAIL保留，最终H07=PASS（用户确认重启后未开APP、断USB、及时完整且无手机语音；系统独立冷拉起及释放证据齐全）。本次H07无需再发真实SMS；用户H01～H06已确认通过。没有本次仍待修的H07关键缺陷，不要求每次重启手动开APP。阶段1按本次要求保持待验收，尚未替用户作整阶段最终放行，不进入阶段2；自然进程回收及真实运营商多段仍按既定未验证风险保留。
