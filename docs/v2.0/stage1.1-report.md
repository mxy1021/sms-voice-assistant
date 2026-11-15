# V2.0 阶段1.1技术与交付报告

## 正式关闭（2026-11-16，Asia/Shanghai）

**CLOSED / ACCEPTED。** 用户已明确正式验收阶段1.1，并接受最终验收报告列明的全部ACCEPTED_LIMITATION、MANUAL_BLOCKED、NOT_MEASURABLE和DEFERRED_OUT_OF_SCOPE。正式关闭不改变这些项目的证据状态，不把未测、受阻、不可测或历史失败改记PASS。

验收源码为[fbd183b71f1eec74a84e00976256044c579a367c](https://github.com/mxy1021/sms-voice-assistant/commit/fbd183b71f1eec74a84e00976256044c579a367c)，用户确认的最终交付文档提交为[68b378baca2599915b747f3f22c64ca0c769ff6b](https://github.com/mxy1021/sms-voice-assistant/commit/68b378baca2599915b747f3f22c64ca0c769ff6b)。详见[正式关闭报告](stage1.1-closure-report.md)。本次只归档Markdown，不修改生产代码或测试代码，不构建、不安装、不重跑验收测试；阶段2未启动。

## 验收前交付与测试记录（原文保留）

本轮最终源码：[commit fbd183b71f1eec74a84e00976256044c579a367c](https://github.com/mxy1021/sms-voice-assistant/commit/fbd183b71f1eec74a84e00976256044c579a367c)；分支feature/v2-sms-speech-experience。文档验收结论为READY_FOR_USER_ACCEPTANCE，尚未正式关闭。

## 最终功能完善交付（2026-11-16，当前优先）

当前结论 **READY_FOR_USER_ACCEPTANCE**：四档响度、显示模式、最终重启后台真实SMS及自动化强制项已具备证据，等待用户正式确认关闭阶段1.1；不自行宣布已验收。

用户已确认的范围：正式0/+3/+6/+9dB，七档试验后的12/15/18由用户明确移除；三种主题；8秒APP内部预算；只保证APP内队列排序；真实URL MANUAL_BLOCKED及106 NOT_MEASURABLE是已接受边界；40→100为用户本人手调、CLOSED_BY_USER_CLARIFICATION。不得再把这些已确认决定列为待选问题。原8014ms、音量首轮失败/复测及上游串行记录均保留。8.5秒FAILED_NOT_ADOPTED，扩容/耳机双击正式功能DEFERRED_OUT_OF_SCOPE。

音频审计先提交5434df961515e87a346559602840a27fd2e12963，之后才修改音频。原0dB有效语音约−20dBFS而峰值仍有6～8dB余量，没有APP重复缩放或额外轨道衰减证据；音乐没有额外降低源PCM。用户原版无/有音乐均认为小，duck正常。此为已证实贡献因素，不推断耳机端唯一根因，不宣称测得SPL。

最终局部处理：原worker内能量门控RMS标准化（−12dBFS目标、最大+9dB），合并用户0/3/6/9dB，声道联动平滑峰值限制及4倍插值估计兜底；取代LoudnessEnhancer，避免保护后再叠加硬件增益。20ms门控、反向5ms/前向50ms减益包络、样本−2dBFS/插值估计−1.2dBFS；不是整数硬削顶，无独立大型压缩框架。输入/输出样本数、PCM上限及完整播放原则不变，耗时进入原预算，门禁不足整条拒绝。

最终0dB三句有效RMS约−12.7～−12.3dBFS，原−20dBFS约提升8dB；用户确认音量合适、自然，无明显压缩感/失真/爆音/手机语音。四档无/有音乐用户均确认清楚完整，音乐降低后恢复；全部客观满幅0、媒体音量60→60。名义增益不等于保护后的等间隔响度，个别档位可不单调，保留测量。完整前后表、DC/crest/静音/峰值估计口径见[专项审计](audio-loudness-audit.md)。

显示模式沿用Compose/Material3：普通/深色/跟随系统，默认系统；立即更新并保存新displayMode键，语音generation不变。真实控件、背景像素、系统模式切换、Activity重建/偏好恢复通过；另已取得用户人工视觉、APP关闭重开及手机正常重启持久化确认。

| 交付检查 | 当前结果 |
|---|---|
| 六项原功能 | F1/F2联系人/F3保留历史PASS；URL自动化PASS、真实MANUAL_BLOCKED；F5改四档PCM处理并通过固定句听感；F4扩容/F6正式双击暂缓，8.5失败不采用 |
| 当前测试 | JVM108/108；音频/终结设备18/18；主题/四档UI2/2＋最大PCM1/1；音乐四档固定语音1方法4会话；结果与历史失败分别归档 |
| 预算/冷启动 | 8000/5000/1MiB/80/1800/700均未变；最大人工PCM处理432ms，仍须动态剩余预算门禁。不可控系统冻结/杀进程不承诺物理上界；本轮Application入口未保留，未冒称新冷启动测量 |
| Release/Debug/Lint | Debug/Release unsigned/androidTest成功；Lint0错、Debug16/Release15历史警告 |
| 架构/隐私 | 无新增权限/Service/FGS/WorkManager/轮询/常驻媒体会话；不保存真实短信/联系人/号码/音频；不联网；无系统音量写入或扬声器回退 |
| Manifest/版本 | 原4权限，仅READ_CONTACTS可选；版本3/2.0.0-stage1，33/34/36及包名/namespace/签名配置不变；隔离脚本PASS |
| 新人工与真实SMS | 三主题/APP重开/手机重启持久化PASS；SMS-000124 Home与125锁屏断USB，均完整含联系人/音乐降低恢复/手机无语音，finish3667/3644ms |

### 音频/主题构建真实SMS与手机重启（2026-11-16）

用户确认三种主题文字/控件清楚、立即切换；选深色/+3后APP关闭重开保留。正常重启、首次解锁不打开本APP、原耳机/音乐/固定媒体音量、断USB条件下，Home与锁屏两条均完整含联系人、音乐降低后恢复、手机无语音。用户记录call_6ebcfbf446e946159d8c6ad40e5a96fa第0/1项；随后只读核对真实事件与偏好，未再次启动APP、模拟广播或强停应用。

实际偏好：speechGainDb=3、displayMode=2（DARK）、bootCount由77→78；enabled/bootRestore仍true、only/simplify仍false、target仍存在，不需要重选耳机。手机重启持久化与人工视觉 **PASS**。这不是延用Activity重建测试来冒称手机重启。

| 真实事件 | 环境 | onReceive绝对elapsed | PCM处理阶段 | 首次写入 | PCM/写入字节 | 其他资源T6 | 音频资源标记 | finish调用T7/返回T8 |
|---|---|---:|---:|---:|---|---:|---:|---|
| SMS-000124 | Home后台，+3dB | 78249 | 321 | 1985 | 1467ms / 70400=70400 | 3648 | 3655 | 3666 / 3667 |
| SMS-000125 | 锁屏，+3dB | 99026 | 101 | 1961 | 1467ms / 70400=70400 | 3627 | 3635 | 3644 / 3644 |

表中阶段/清理/finish均相对onReceive的ms，仅onReceive一列是本次启动以来的绝对elapsedRealtime。两事件source=REAL_SMS、业务COMPLETED、foregroundBroadcast=true；上下文分别appVisible=false/interactive=true/keyguard=false与false/false/true。TTS初始化70/26ms，合成96/90ms；播放标记距首次写入1588/1593ms，后者不是耳机声学起音或准确声学播放时长。

| 事件 | 超时回调T1 | TTS取消开始T2 | Track停止开始T3 | Track释放T4 | 焦点释放T5 | 其他资源T6 | finish调用T7 | 返回T8 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| SMS-000124 | 未触发 | 3637 | 3600 | 3621 | 3632 | 3648 | 3666 | 3667 |
| SMS-000125 | 未触发 | 3612 | 3573 | 3594 | 3607 | 3627 | 3644 | 3644 |

正常路径资源释放先于finish；两条均在原8秒内部预算内。本轮Application.onCreate入口未在保留日志中取得，Application→finish和系统真正分发→finish均 **NOT_MEASURABLE**，不声称取得新短信进程冷启动耗时或证明系统完整生命周期物理上界。历史SMS-000107冷启动相关证据及8014ms仍保留。本轮未重复10～15分钟静置，旧120是历史证据，不能冒称新构建长静置。

证据：app/build/stage1.1-device/loudness-final-real-sms.json、post-reboot-loudness-settings.json。真实SMS只保存脱敏标量，不保存正文、联系人、号码或音频。本轮真实事件124/125与历史88～121（34条）及123合计37条可追溯记录；没有补造122。

### 本轮APK

所有路径相对D:\Projects\SMSVoiceAssistant，独立保存，未覆盖旧closeout或8秒原版证据。

| APK | SHA256 |
|---|---|
| app\build\stage1.1-final-20261116\app-debug.apk | 9d28eca52253fe756efa6b4439054e8f6e73fbf31368c8cfcca9320e11366535 |
| app\build\stage1.1-final-20261116\app-release-unsigned.apk | fdc4e5dc1064739e548f6bbf6a1e63c7a1b42cff05490355ed0af76dde9a4889 |
| app\build\stage1.1-final-20261116\app-debug-androidTest.apk | 4cad9f3ca8e88977572b1bde1f5eaebe27aac0acec6b928b34112233c157fa56 |

Release未配置发行签名，本轮不改签名；安装/真机测试使用Debug。后续源提交SHA及远端一致性在最终交付回复中核对，不把Q1提交当成最终功能提交。仅feature/v2-sms-speech-experience，不合并main，不改实验分支、V1标签或阶段1基线。


### 后续新增：未知联系人提示（最终APK）

用户在音频/主题回归完成后新增“无法获取联系人时读提示＋正文”。正式选用“未知联系人，正文”，不推断对方身份为陌生人，不读号码；精简验证码不变。通过统一forPlayback最终决策加入前缀，保持初步正文检查与查询后最终长度检查分开；未授权、无匹配及查询失败都归入同一降级输出。80限制包含6 UTF-16的提示与逗号，未知时正文最多74；更长整条拒绝，不截断；可靠短姓名仍按真实合计长度检查。

新增4项UnknownContactSpeechTest（无/空/不安全名称、真实姓名、四组合、74/75/已知短姓名边界、URL清理空文、普通数字保护），全套JVM **108/108 PASS**。受影响的IndependentSmsRulesDeviceTest 7及SmsQueueDeviceTest 5共 **12/12设备PASS**，使用受控业务注入而非真实SMS；验证未知提示、独立四组合、原队列非抢占/P0/FIFO/容量与截止时间规则。旧Debug注入工具的全文预期及80字样本同步更新，不把未跑旧整套工具写成PASS。

**真实SMS-000126 PASS**：+3dB，前台/解锁，用户明确确认“未知联系人，晚上七点见。”完整、不读号码、手机无语音（call_a9f0388df6324c7cacec561ccdf22fc3）。用户未说明选用未存号码还是暂撤联系人权限的测试方式，因此只证明降级话术真实通过，不分别伪称两种方式都已补测。

onReceive绝对elapsed=613668ms；PCM2747ms、131840字节全部写入；处理阶段106ms，首写1915ms；Track停止开始4771、释放4781、焦点释放4784、TTS取消开始4785、其他资源T6=4789、finish调用/返回T7/T8=4791ms，均相对接收。系统分发起点不可观测。Application入口595469ms早于接收18199ms，Application→finish22990ms是已运行进程寿命加本条处理，**不是冷初始化耗时或广播耗时**，不误判8秒预算超限。

该增量之后的最终APK为9d28eca5…，已安装且设备APK SHA256与交付一致；只改变全文发送者降级和相应UI说明/测试，音频DSP、主题持久化、蓝牙、预算/finish未再改动。音频/主题构建1c6174ba…的124/125重启后台证据保留并注明版本，不声称在9d28上重新执行了重启或整套18项音频回归；新增受影响路径由12项设备与真实126闭环。总计38条可追溯真实SMS（历史35＋124/125/126），122仍未补造。

最终无新增权限/Service/后台机制，Debug/unsigned Release/androidTest构建、108单元、最新Lint及APK隔离通过；当前READY_FOR_USER_ACCEPTANCE，等待用户确认关闭阶段1.1。

### 本轮修改文件清单（相对da5c70e，含Q1审计）

- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/DebugSmsInjectionTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/IndependentSmsRulesDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/LoudnessAuditDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/SessionCloseoutDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/SmsQueueDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/SpeechExperienceDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/ThemeGainDeviceTest.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/MainActivity.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SessionLoudness.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SpeechDependencies.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SpeechPcmProcessor.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SpeechSession.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/settings/SettingsRepository.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/AnnouncementRules.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/SmsCoordinator.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/SpeechExperienceTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/SpeechPcmProcessorTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/UnknownContactSpeechTest.kt`
- `docs/v2.0/architecture.md`
- `docs/v2.0/audio-loudness-audit.md`
- `docs/v2.0/development-plan.md`
- `docs/v2.0/requirements.md`
- `docs/v2.0/stage1.1-acceptance.md`
- `docs/v2.0/stage1.1-architecture.md`
- `docs/v2.0/stage1.1-report.md`
- `docs/v2.0/stage1.1-requirements.md`
- `docs/v2.0/stage1.1-test-plan.md`
- `docs/v2.0/stage1.1-test-results.md`
- `docs/v2.0/technical-decisions.md`
- `scripts/Verify-DebugSmsIsolation.py`

## 历史收尾快照（原文及失败记录保留，当前决定以上文为准）

日期：2026-11-15（Asia/Shanghai）。**本轮正式验收收尾已形成证据：安全前置清理已最小修复；106仍NOT_MEASURABLE；四组合8格映射PASS；真实URL MANUAL_BLOCKED；真实SMS优先重排BLOCKED。阶段1.1仍NOT_ACCEPTED，由用户决定是否接受这些边界。** 原始失败、阶段1稳定基线及V1标签不变。

## 本轮范围与预算结论

正式8000ms、PCM5000ms/1MiB、80 UTF-16、1800ms初次焦点窗口和既有700ms路由/清理预留均未改值。长文本/字符扩容、5.1/5.2实验、耳机双击正式功能为DEFERRED_OUT_OF_SCOPE，不再作为阶段1.1强制验收。8.5秒为FAILED_NOT_ADOPTED，原8510ms释放失败永久保留；本轮不修改独立实验分支。

本轮基线8d8608eb7bcba3c14b820615d6ec6f4794578030，清理修复代码[d7283215b306a49e3cd8a1830d745610cd725d65](https://github.com/mxy1021/sms-voice-assistant/commit/d7283215b306a49e3cd8a1830d745610cd725d65)。原8004ms超时/8014ms资源释放是实际测点，不是8016ms观察线程误差。只在未播放/已暂停、无法再通过既有预留门禁的状态，于推导的7300ms检查启动清理；正在持有焦点的正常语音继续播放，8000ms最终看门狗保留。跨线程cancel统一进入主线程，一次性终结标记前移，并分别记录轨道、焦点、TTS、其他资源和finish时间。

| 样本 | 清理完成T6 | finish调用T7 / 返回T8 | 结论 |
|---|---:|---:|---|
| 慢初始化私有有序测试广播 | 7331ms | 7339 / 7339ms | 零语音写入，9500ms晚回调未复活 |
| 暂停私有有序测试广播 | 7364ms | 7369 / 7369ms | 真实焦点-3后释放，恢复竞争焦点不复活 |
| 完整5秒人工PCM测试广播 | 7587ms | 7591 / 7591ms | 7304ms检查未截断，160000字节完整写入 |
| 受控权限拒绝复测 | 2035ms | 2041 / 2041ms | 轨道已释放、线程在finish前已退出，完成/finish各1次 |
| **真实SMS-000123** | **3384ms** | **3389 / 3389ms** | 用户确认完整、手机无语音；后台解锁，未冒称锁屏 |

前四行是DEBUG_SIMULATION的私有普通测试广播，使用真实Android PendingResult，但不是SMS_RECEIVED或真实短信。详细T0～T8、14条终结路径审查见测试结果的“正式验收收尾”章节。真实123的AudioTrack释放3370ms、焦点释放3376ms，TTS取消开始3378ms，均先于finish。系统广播分发起点不可观测；**主线程/CPU冻结以及活动音频异常停滞时，严格物理终结≤8000ms仍NOT_PROVEN**，本轮不以提前截断正常语音换取表面上界。

## SMS-000106及四组合证据

106有入口与播放中记录，无可恢复的清理/finish测点；107新进程记录上次退出原因8、由broadcast/SmsReceiver启动且非instrumentation。官方[权限变更退出原因](https://developer.android.com/reference/android/app/ApplicationExitInfo#REASON_PERMISSION_CHANGE)支持系统因运行时权限变更终止进程的判断。进程被杀不保证Java finally或finish执行，不能写为历史终结成功。状态保持NOT_MEASURABLE。当前存活进程的拒权清理已用受控权限读数测试，真实系统权限未撤销、未强停；它不替代106。

四组合真实证据已逐项映射，无补发：A验证码91/普通92，B验证码93/普通94，C验证码88（89前导零补充）/普通97，D验证码100/普通90。各行的预期、原始用户反馈锚点、真实日志文件及finish均见测试结果映射表；没有编造编号，批次反馈不捏造为逐字听写。F1/F2/F3/F5保留正式实现及既有真实测试，匹配/未匹配联系人、撤权恢复等历史通过不重复跑。

## 真实URL与替代操作

http、https、www、裸域名、多链接、中文标点、中间URL、查询参数和验证码/金额/日期/取件码保护均有通过的断言，本轮补明确http样本。用户只重试一条合法URL短信并反馈仍被拦截，故真实URL为**MANUAL_BLOCKED**，不重复大量发送、不冒充真实过滤PASS。可用测试计划中的JVM命令复查逻辑；未来合法SMS通道可达时单发短正文＋URL，由新REAL_SMS编号、耳机听感和finish共同补证。RCS或伪造广播不算替代真实SMS。

## 真实验证码优先队列：BLOCKED

应用没有持锁等待整个播放的同步代码：SmsReceiver.goAsync后返回，SmsCoordinator在主线程按processing/session状态决定入队；waiting.offer在已解析/分类后执行，只有当前终结后才poll，按验证码优先、同级接收顺序FIFO；最多5条待播，满时拒新，各自接收期限不重置。问题在APP入口之前：finish一直等到资源释放和完整会话结束。

核查的[AOSP InboundSmsHandler](https://android.googlesource.com/platform/frameworks/opt/telephony/+/master/src/java/com/android/internal/telephony/InboundSmsHandler.java)用有序广播投递SMS，并在WaitingState推迟下一SMS广播阶段直到当前完成；[现代按进程广播队列](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/BroadcastQueueModernImpl.java)亦在finishReceiverLocked结束当前项后再makeActiveNextPending。本机117→118、118→119的入口分别晚于前条finish 117/153ms，与此一致。上游源码不是vivo固件逐行证明，也不外推全部厂商/多SIM变体；但当前实测路径不能可靠获得多个待播的独立真实SMS，**不能承诺真实短信P0超越先到普通短信**。

队列适用于已经并发进入协调器的解析后事件（已有受控测试），也能按代码容纳“手动固定语音占用期间首条真实SMS到达”等单条等待场景；后者未另行实测，不声称形成真实多SMS重排。仅保留算法不等于真实运营商重排可行性PASS，正式状态BLOCKED。

最小可行交付是保留规则和队列、保留BLOCKED，由用户决定是否接受当前SMS按系统顺序处理的架构边界/暂缓真实重排强制验收。若必须可靠重排，需要接收后及时finish，再由独立受支持的生命周期处理队列；这会改变当前广播绑定播放/先释放再finish约束，通常还需要前台组件或用户可见承载。即便短时新增Service也不符合本轮“不得新增Service”，因此没有实施，也没有先finish后继续后台播报、转WorkManager或擅改产品规则。

## 本轮验证、产物与未决项

针对性JVM27/27，构建Debug/Release/androidTest成功，Lint0错误16/15警告，隔离检查4权限0Service。新9个清理设备方法全部通过；首轮原7个音频方法有1次0dB读数40→100失败，用户确认未操作音量。只重跑该项及2个线程观测方法后3/3通过；Focus封装1方法的3个实际系统焦点场景API通过。第一次音量变化仍为**OPEN_OBSERVATION，根因未证实**，不因复测通过而删除；未发现APP全局音量写调用，但这不能倒推已定位系统原因。未更改系统媒体音量来修正读数。

当前待用户审阅：真实重排BLOCKED、URL MANUAL_BLOCKED、106历史NOT_MEASURABLE、严格物理终结上界NOT_PROVEN、一次音量快照异常OPEN_OBSERVATION。暂缓功能不计强制缺口。没有把已通过的四组合、联系人或旧后台场景无意义重跑。正常真实123与受控普通广播明确分开，绝不以模拟代替真实短信。

本轮APK（旧产物目录保持原样）：`app/build/stage1.1-closeout-artifacts/`。

- Debug SHA256：fe9cfb68438792df81e15504f89eeeb9d62f628ee6d21d62f6d8c0a0ceb28576。
- Release unsigned SHA256：87340844d37a08f29ea5ad4736167448cdfb7903127607925f99ee314a7bfe7b。
- androidTest SHA256：d758f0f7c85bbb5b563702fbf3c6251bf4474eb22fca8f61d4a1e756dda8ae95。
- APK代码树对应d728321；构建发生于该提交之前，基于8d8608e工作区修复，metadata记录这一点，不把构建时VCS父提交冒称源代码未改。Debug已同签名保留数据安装，设置与耳机配置核对保留。

本轮修改：生产4文件（SpeechSession、SpeechDependencies、SmsSafetyWindow、SmsReceiver）；测试3文件（新SessionCloseoutDeviceTest、SmsSafetyWindowTest、SpeechExperienceTest）；隔离脚本1文件；报告/结果/验收/测试计划4文档。最终文档SHA、远端一致性与干净工作区见交付回复及本报告所属提交。只推送feature/v2-sms-speech-experience；8.5秒实验仍保持51d29903d99a965dcc73fe92112ea0225883b62b，main/阶段1/V1标签不变。

## 主体交付时六项功能及追加队列（历史快照）

| 范围 | 实现状态 | 本轮证据与限制 |
|---|---|---|
| F1验证码 | 已实现上下文4/5/6/8位、混合码、前导零及确定码span逐位转换 | 55555/00981及混合A123真实SMS正确完整、无手机语音；A12345全文合成5507ms超5秒，整条拒绝，不能记为成功朗读 |
| F2联系人＋正文＋链接 | 已实现可选权限、PhoneLookup精确复核、有界查询、清理副本 | 真实匹配名称、撤权恢复、已授权但未存号码仅正文PASS；不持久化发信人或联系人；链接真实SMS受运营商限制，不能用单测冒充 |
| F3四组合 | 沿用唯一SmsSpeechRules决策层，独立旧键持久化 | JVM、设备协调器、真实UI PASS；A/B真实验证码与普通静默、C验证码、D普通及权限回归已确认；四组合8格现均有本轮真实短信及听感，后台条件另计 |
| F4预算与长度 | 已补T0～T12精确测点，独立8.5秒单常量候选，正式不提高 | 两分支93项单测；各7项受控PCM守卫PASS，但8.5秒精确T11=8510ms，严格预算FAIL；最终8秒APK已安装并哈希核验；真实finish及冷启动数据见下 |
| F5语音增强 | 0/+3/+6dB，绑定当前AudioTrack正会话ID，终结释放 | 原设备确实支持3/6，完成/取消/永久焦点丢失释放、受控失败回退、系统媒体音量不变均PASS；三档前台音乐实际听感均清楚、无失真/爆音、音乐降低并恢复；后台另计 |
| F6耳机双击 | 仅测试APK提供一次SpeechSession期间的临时MediaSession探针 | 实际收到127 DOWN/UP，音乐和锁屏音乐两轮停止语音且音乐继续、不跳曲，MediaSession释放；来源认证、Home及完整原有控制边界未满足，不正式实现 |
| 追加队列A | 用户明确纳入并确认最多5条待播、满时拒绝新消息 | 非抢占、OTP优先、同级FIFO及原期限已实现，JVM/协调器设备PASS；真正三条真实短信按1→2→3完整，入口串行，未实际入队，不作真实P0重排PASS。此前两轮实际各只发2条，普通漏收判断已撤回 |

## 主体交付14项答复（历史，以本轮收尾结论为准）

1. **六项分别完成多少？** F1/F2/F3/F5已实现且自动化通过，真实SMS覆盖上述部分；F4完成8/8.5受控对比，严格预算未通过，未采用8.5；F6只有测试APK探针，未正式实现。队列按本轮追加范围实现。
2. **哪些自动化通过？** 异常修复后93/93 JVM、19/19设备功能/队列、1/1受控MediaSession异常释放；另1个Focus instrumentation封装方法的3个实际焦点场景API断言PASS。此前8/8.5秒各7/7受控PCM预算断言（严格预算另判FAIL）；四组合UI、Debug/Release/androidTest构建、Lint和隔离通过。各分组不重复计入真实SMS或听感。
3. **哪些真实SMS通过？** 用户确认5位/前导零/短混合码、四组合8格、匹配名称/撤权恢复/已授权但未存号码只读正文、三档音乐、Home/锁屏、停止/断连/撤蓝牙恢复、无手机语音、超限整条静默、真正连续3条，以及重启不开APP＋断USB锁屏静置约10分钟后的完整播报/音乐恢复/正常短信铃声不打断。88～121共34条：25完整、3规则/全文门禁忽略、2主动安全取消、2PCM超限零写入、1拼接拒绝、1撤权后终结不可测。该分类不是通过率；未实际入队不宣称P0重排通过，链接仍受阻。
4. **8.5秒是否通过？** 未通过，不采用。固定慢初始化超时的精确T11=8510ms，超过8500ms；净变更仅8000→8500。7项守卫断言通过不能抵消严格预算反例；真实候选PendingResult及系统全周期未测。
5. **实际finish耗时？** 截至121，25条完整真实SMS从onReceive到实际finish返回3493～6483ms；重启静置样本4356ms，未存号码样本3596ms。规则/全文门禁8/10/43ms；主动取消3073/3142ms；两条PCM超限1831/1832ms，拼接拒绝6ms；撤蓝牙106无finish，记不可测。旧版本87为5018ms的finish后测点，不冒称精确调用；候选真实finish没有数据。
6. **冷启动是否影响系统生命周期？** 官方计时含进程冷启动。107（修复前8秒构建）在撤权退出后由真实SMS启动，Application入口→onReceive59ms、入口→finish4872ms、onReceive→finish4813ms，系统am_proc_start关联broadcast/SmsReceiver且非instrumentation。当前修复构建120完成重启/静置回归，boot_count76→77，但留存日志无T0/进程启动原因，不能称120为已测精确短信冷启动。所有系统分发起点仍不可测，不将Application入口代替分发起点，也不冒称自然回收已验证。参见[官方广播ANR文档](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)。
7. **完整播放时长提高多少？** 正式上限没有提高，仍5.0秒。受控人工静音5.0秒完整只验证音频路径，不等于真实TTS听感；本轮用户确认完整的真实SMS最大PCM为4387ms。A12345全文5507ms整条拒绝，没有部分播放。
8. **字符与PCM上限？** 最终朗读文本含姓名及码拼读≤80 UTF-16；完整PCM≤5000ms且≤1MiB；首次焦点窗口1800ms、路由及清理余量700ms。100/120/160字符和5.1/5.2秒的当前测试是整条拒绝，不是扩容成功。
9. **音量独立于音乐吗？** 仅附加当前轨道正会话ID，实际设备3/6dB创建配置释放成功，STREAM_MUSIC前后未变，未调用全局音量或播放器控制API。用户固定系统音量比较0/3/6dB均清楚、无失真或爆音、音乐降低后恢复；这是本设备本轮听感，不是所有耳机或信号的无削波保证。
10. **双击能停语音且不影响音乐吗？** 测试APK在音乐及锁屏音乐两轮映射本机实际127 UP后，用户确认立即停止语音，音乐继续且不跳曲，会话释放。无音乐轮仅记录；Home、其他按键/设备、来源认证和生产SMS集成未满足，暂不提供正式功能。没有借此接管长期媒体控制。
11. **新增Service或长期后台机制？** 没有。合并Manifest、Debug/Release产物检查均0 Service，无FGS、WorkManager、轮询、常驻媒体或AccessibilityService；联系人工作仅当前查询，超时放弃结果并取消，最多一项，不能保证不配合取消的系统Provider内部执行时间。
12. **Manifest新增权限？** 仅可选android.permission.READ_CONTACTS。总共RECEIVE_SMS、BLUETOOTH_CONNECT、RECEIVE_BOOT_COMPLETED、READ_CONTACTS；无INTERNET、READ_SMS、通知监听或无障碍权限。
13. **未完成测试？** 真实链接（运营商受阻）、真实应用内队列并发/P0重排（输入一直串行）、80/100/120/160中文长度的完整真实矩阵（81中文在拼接处被拒绝）、自然回收、晚到通知铃声重叠变体及真实短号/字母发送者等子场景；8.5秒门禁已失败，未继续其B01～B12全套真实场景/finish/系统全周期或PCM扩容；耳机无音乐停止/Home/其他控制与来源边界未完成，故不正式实现。A12345已测但5507ms超限未播。未存联系人及重启静置正常铃声均已通过。详见测试计划/验收表。
14. **Git与工作区？** 功能分支feature/v2-sms-speech-experience，当前生产源代码基线913bb1fa8b72aa092387282b419cc618d34f1b18（预算对比的共同基线仍为5da72ad）；候选experiment/sms-budget-8500ms独立保留，实际预算候选构建基线44bc07e99f00615eb86b797cdf063bfc3c6d20cd。最终文档提交SHA、远端一致性与干净工作区以最后核验和交付回复为准。不合并main、不改稳定分支/标签、不强推。

## 主体交付时技术边界（历史，原数据保留）

8秒受控晚初始化：超时回调精确8004ms，资源释放8014ms，测试线程观察终结8016ms。当前Handler在预算到点才发起清理，不能据常量宣称实际全部资源/finish严格≤8000或8500。此项必须纳入8.5秒判定；7项预算断言通过不等于严格实际终结上界通过。没有系统分发精确起点时不承诺完整广播≤9秒或无ANR。候选同样超时回调8501ms、T11=8510ms；已据此拒绝采用8.5秒，未扩容5.1/5.2秒。原参数保守保留也不等于本轮所有后台行为已验收。

联系人采用保守号码规范化：中国手机号+86/0086/空格/横线及明确国际E.164；短号、字母ID、不支持或不确定的格式只正文。超过8行/冲突名称/号码式名称不猜测，不枚举整个目录；查询250ms放弃结果，并发至多一项。原始短信与联系人仅当前内存，不缓存或新增正文持久化。

URL清理为明确http(s)、www和常见裸域名规则，不承诺识别所有不规范URL或所有语言文本；中文标点、多链接、中间链接、验证码/取件码/金额/日期保护已有单测。实际带链接SMS受运营商限制，保持人工待验收。

## 主体交付时APK与复现（历史产物保留）

- 正式8秒Debug：`D:/Projects/SMSVoiceAssistant/app/build/stage1.1-artifacts/feature-8000/app-debug.apk`。
- Release未签名构建：同目录`app-release-unsigned.apk`，不是正式签名发布包。
- androidTest：同目录`app-debug-androidTest.apk`，只含测试，不是生产APK。
- 8.5秒实验：`D:/Projects/SMSVoiceAssistant/app/build/stage1.1-artifacts/experiment-8500/app-debug.apk`，只用于候选测试，禁止当作已验收版。
- 各目录metadata.json记录对应代码提交、预算及SHA256；APK/脱敏运行证据位于忽略的build目录，不进入Git。
- 使用同签名install -r保留数据；不卸载、清数据、伪造SMS_RECEIVED、强停冒充自然回收或自动授予联系人。实验结束后先恢复8秒构建d18d312f…，后安装名称防护7729f62d…；异步异常修复后安装最终3160606f…，从手机拉取实际APK哈希一致，设置保留。最新证据async-guard-install.json、async-guard-installed-verification.json；旧构建另存feature-8000-before-async-guard。
- 构建：`./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease`。
- 产物隔离：`python scripts/Verify-DebugSmsIsolation.py`；真实计时只读：`python scripts/Measure-SmsBudget.py --label <唯一标签>`。

GitHub：[功能代码提交](https://github.com/mxy1021/sms-voice-assistant/commit/913bb1fa8b72aa092387282b419cc618d34f1b18)、[功能分支](https://github.com/mxy1021/sms-voice-assistant/tree/feature/v2-sms-speech-experience)、[8.5秒候选分支](https://github.com/mxy1021/sms-voice-assistant/tree/experiment/sms-budget-8500ms)。

## 修改文件清单（相对210a749）

- `README.md`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/DebugSmsInjectionTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/HeadsetMediaProbeTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/IndependentSmsRulesDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/SmsBudgetDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/SmsQueueDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/SessionCloseoutDeviceTest.kt`
- `app/src/androidTest/java/com/mxy1021/smsvoiceassistant/SpeechExperienceDeviceTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/MainActivity.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/ProbeApplication.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SessionLoudness.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SpeechDependencies.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/audio/SpeechSession.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/receiver/SmsReceiver.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/settings/SettingsRepository.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/AnnouncementRules.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/ContactMatchPolicy.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/ContactNameResolver.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/SmsCoordinator.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/SmsSafetyWindow.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/SmsTextCleaner.kt`
- `app/src/main/java/com/mxy1021/smsvoiceassistant/sms/SmsWaitingQueue.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/AnnouncementRulesTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/DebugSmsContractTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/IndependentSmsRulesTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/SmsSafetyWindowTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/SmsWaitingQueueTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/SpeechExperienceTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/SpeechFocusGateTest.kt`
- `app/src/test/java/com/mxy1021/smsvoiceassistant/SpeechStartPolicyTest.kt`
- `docs/v2.0/stage1.1-acceptance.md`
- `docs/v2.0/stage1.1-architecture.md`
- `docs/v2.0/stage1.1-development-plan.md`
- `docs/v2.0/stage1.1-report.md`
- `docs/v2.0/stage1.1-requirements.md`
- `docs/v2.0/stage1.1-test-plan.md`
- `docs/v2.0/stage1.1-test-results.md`
- `scripts/Measure-SmsBudget.py`
- `scripts/Test-Stage1Device.py`
- `scripts/Verify-DebugSmsIsolation.py`

## 主体交付构建指纹（历史）

- 生产APK代码基线：913bb1fa8b72aa092387282b419cc618d34f1b18；新增的测试APK固定异常入口已实测，并据失败修复延迟播放异常逃逸，原失败和修复后成功均保留。末次文档提交另计。
- 最终Debug APK SHA256：3160606f086104011b7680afae3f4b94512a4cf911a2e9d20e82d0dbfcef5c50。
- 最终Release unsigned SHA256：430620911b74712de6732efe810e1e2baec604f8a2a24440f3299e7ef4dd740c。
- 所有产物保留3 / 2.0.0-stage1，min/target/compile=33/34/36，包名/namespace/签名配置未修改。
- 四组合及三档听感、匹配/未存联系人、权限撤销恢复、Home/锁屏、停止和断连/撤权恢复、真正连续三条、重启不开APP＋断USB锁屏静置约10分钟及正常短信铃声均取得本轮用户反馈。34个真实事件不含任何SIM fixture；真实P0重排未触发、链接受阻及其他缺项继续按验收表记录。正式验收仍等待用户，不以交付、构建或部分按键成功代替验收。
