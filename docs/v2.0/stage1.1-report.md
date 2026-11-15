# V2.0 阶段1.1技术与交付报告

日期：2026-11-15（Asia/Shanghai）。**当前结论：功能实现与部分实测完成，阶段1.1尚未正式验收；正式参数保持8秒，不采用8.5秒，不扩容PCM。** V1.0、V2.0阶段0/阶段1历史验收和原始失败记录均未改写。

## 六项功能及追加队列

| 范围 | 实现状态 | 本轮证据与限制 |
|---|---|---|
| F1验证码 | 已实现上下文4/5/6/8位、混合码、前导零及确定码span逐位转换 | 55555/00981及混合A123真实SMS正确完整、无手机语音；A12345全文合成5507ms超5秒，整条拒绝，不能记为成功朗读 |
| F2联系人＋正文＋链接 | 已实现可选权限、PhoneLookup精确复核、有界查询、清理副本 | 用户真实联系人名称及撤权/恢复PASS；不持久化发信人或联系人；链接真实SMS被运营商限制，未验证，不能用单测冒充 |
| F3四组合 | 沿用唯一SmsSpeechRules决策层，独立旧键持久化 | JVM、设备协调器、真实UI PASS；A/B真实验证码与普通静默、C验证码、D普通及权限回归已确认；四组合8格现均有本轮真实短信及听感，后台条件另计 |
| F4预算与长度 | 已补T0～T12精确测点，独立8.5秒单常量候选，正式不提高 | 两分支93项单测；各7项受控PCM守卫PASS，但8.5秒精确T11=8510ms，严格预算FAIL；最终8秒APK已安装并哈希核验；真实finish及冷启动数据见下 |
| F5语音增强 | 0/+3/+6dB，绑定当前AudioTrack正会话ID，终结释放 | 原设备确实支持3/6，完成/取消/永久焦点丢失释放、受控失败回退、系统媒体音量不变均PASS；三档前台音乐实际听感均清楚、无失真/爆音、音乐降低并恢复；后台另计 |
| F6耳机双击 | 仅测试APK提供一次SpeechSession期间的临时MediaSession探针 | 实际收到127 DOWN/UP，音乐和锁屏音乐两轮停止语音且音乐继续、不跳曲，MediaSession释放；来源认证、Home及完整原有控制边界未满足，不正式实现 |
| 追加队列A | 用户明确纳入并确认最多5条待播、满时拒绝新消息 | 非抢占、OTP优先、同级FIFO及原期限已实现，JVM/协调器设备PASS；用户已澄清前两轮实际各只发2条验证码，均完整，未并发入队。没有普通消息漏收证据；真正3条短信测试待做，不作真实优先PASS |

## 最终问题逐项回答（当前可证实范围）

1. **六项分别完成多少？** F1/F2/F3/F5已实现且自动化通过，真实SMS覆盖上述部分；F4完成8/8.5受控对比，严格预算未通过，未采用8.5；F6只有测试APK探针，未正式实现。队列按本轮追加范围实现。
2. **哪些自动化通过？** 异步播放异常修复后93/93 JVM、19/19设备功能/队列及1/1固定受控MediaSession异常释放；此前8秒及8.5秒各7/7受控PCM预算断言（严格预算另判FAIL）；四组合UI、Debug/Release/androidTest构建、Lint和产物隔离均通过。计数分组不同，不重复当成真实SMS数。
3. **哪些真实SMS通过？** 用户确认5位/前导零/短混合码A123、四组合8格、联系人及撤权恢复、三档音乐听感、Home/锁屏音乐恢复、总开关、断连重连/撤蓝牙恢复、无手机语音；81个英文字母明确在全文门禁整条静默。88～116共29条：20完整、3规则/全文门禁忽略、2安全取消、2PCM超限零写入、1拼接拒绝、1撤权后终结不可测。连续两轮实际各只发送2条验证码，没有入队，不宣称真实P0通过，也不再将未发送的普通样本记为投递缺口；链接受阻，未存联系人和后台剩余目标未完整覆盖。
4. **8.5秒是否通过？** 未通过，不采用。固定慢初始化超时的精确T11=8510ms，超过8500ms；净变更仅8000→8500。7项守卫断言通过不能抵消严格预算反例；真实候选PendingResult及系统全周期未测。
5. **实际finish耗时？** 截至116，20条完整真实SMS从onReceive到实际finish返回3493～6483ms；规则/全文门禁8/10/43ms；主动取消3073/3142ms；两条PCM超限1831/1832ms，拼接拒绝6ms；撤蓝牙106无finish，记不可测。逐条见测试结果。旧版本87为5018ms的finish后测点，不冒称精确调用；候选真实finish没有数据。
6. **冷启动是否影响系统生命周期？** 官方计时含进程冷启动。107在撤权退出后由真实SMS启动，Application入口→onReceive59ms、入口→finish4872ms、onReceive→finish4813ms，系统am_proc_start关联broadcast/SmsReceiver且非instrumentation。系统分发精确起点不可直接测量，不能把Application入口当分发起点；这也不等于重启或自然回收验证。参见[官方广播ANR文档](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)。
7. **完整播放时长提高多少？** 正式上限没有提高，仍5.0秒。受控人工静音5.0秒完整只验证音频路径，不等于真实TTS听感；本轮用户确认完整的真实SMS最大PCM为4387ms。A12345全文5507ms整条拒绝，没有部分播放。
8. **字符与PCM上限？** 最终朗读文本含姓名及码拼读≤80 UTF-16；完整PCM≤5000ms且≤1MiB；首次焦点窗口1800ms、路由及清理余量700ms。100/120/160字符和5.1/5.2秒的当前测试是整条拒绝，不是扩容成功。
9. **音量独立于音乐吗？** 仅附加当前轨道正会话ID，实际设备3/6dB创建配置释放成功，STREAM_MUSIC前后未变，未调用全局音量或播放器控制API。用户固定系统音量比较0/3/6dB均清楚、无失真或爆音、音乐降低后恢复；这是本设备本轮听感，不是所有耳机或信号的无削波保证。
10. **双击能停语音且不影响音乐吗？** 测试APK在音乐及锁屏音乐两轮映射本机实际127 UP后，用户确认立即停止语音，音乐继续且不跳曲，会话释放。无音乐轮仅记录；Home、其他按键/设备、来源认证和生产SMS集成未满足，暂不提供正式功能。没有借此接管长期媒体控制。
11. **新增Service或长期后台机制？** 没有。合并Manifest、Debug/Release产物检查均0 Service，无FGS、WorkManager、轮询、常驻媒体或AccessibilityService；联系人工作仅当前查询，超时放弃结果并取消，最多一项，不能保证不配合取消的系统Provider内部执行时间。
12. **Manifest新增权限？** 仅可选android.permission.READ_CONTACTS。总共RECEIVE_SMS、BLUETOOTH_CONNECT、RECEIVE_BOOT_COMPLETED、READ_CONTACTS；无INTERNET、READ_SMS、通知监听或无障碍权限。
13. **未完成测试？** 真实链接（运营商受阻）、未存联系人、连续真实队列、80/100/120/160中文长文本的完整真实场景（81中文在拼接处被拒绝）、断USB静置/重启/通知音专项、8.5秒B01～B12全套和真实finish/系统全周期、耳机无音乐停止/Home/其他控制与来源边界。A12345已测但因5507ms超限未播，不冒称成功。详见[本轮测试计划](stage1.1-test-plan.md)及[待验收表](stage1.1-acceptance.md)。
14. **Git与工作区？** 功能分支feature/v2-sms-speech-experience，当前生产源代码基线913bb1fa8b72aa092387282b419cc618d34f1b18（预算对比的共同基线仍为5da72ad）；候选experiment/sms-budget-8500ms独立保留，实际预算候选构建基线44bc07e99f00615eb86b797cdf063bfc3c6d20cd。最终文档提交SHA、远端一致性与干净工作区以最后核验和交付回复为准。不合并main、不改稳定分支/标签、不强推。

## 实测技术边界与未解决事项

8秒受控晚初始化：超时回调精确8004ms，资源释放8014ms，测试线程观察终结8016ms。当前Handler在预算到点才发起清理，不能据常量宣称实际全部资源/finish严格≤8000或8500。此项必须纳入8.5秒判定；7项预算断言通过不等于严格实际终结上界通过。没有系统分发精确起点时不承诺完整广播≤9秒或无ANR。候选同样超时回调8501ms、T11=8510ms；已据此拒绝采用8.5秒，未扩容5.1/5.2秒。原参数保守保留也不等于本轮所有后台行为已验收。

联系人采用保守号码规范化：中国手机号+86/0086/空格/横线及明确国际E.164；短号、字母ID、不支持或不确定的格式只正文。超过8行/冲突名称/号码式名称不猜测，不枚举整个目录；查询250ms放弃结果，并发至多一项。原始短信与联系人仅当前内存，不缓存或新增正文持久化。

URL清理为明确http(s)、www和常见裸域名规则，不承诺识别所有不规范URL或所有语言文本；中文标点、多链接、中间链接、验证码/取件码/金额/日期保护已有单测。实际带链接SMS受运营商限制，保持人工待验收。

## APK与复现

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

## 最终构建指纹与当前人工交接

- 生产APK代码基线：913bb1fa8b72aa092387282b419cc618d34f1b18；新增的测试APK固定异常入口已实测，并据失败修复延迟播放异常逃逸，原失败和修复后成功均保留。末次文档提交另计。
- 最终Debug APK SHA256：3160606f086104011b7680afae3f4b94512a4cf911a2e9d20e82d0dbfcef5c50。
- 最终Release unsigned SHA256：430620911b74712de6732efe810e1e2baec604f8a2a24440f3299e7ef4dd740c。
- 所有产物保留3 / 2.0.0-stage1，min/target/compile=33/34/36，包名/namespace/签名配置未修改。
- 四组合及三档听感、Home/锁屏、停止和断连/撤权恢复已有本轮用户确认；静置/重启、长文本/队列及其他缺项继续按待验收表记录。正式验收仍等待用户，不把构建、受控fixture或部分按键成功替代完整验收。
