# V2.0 阶段1.1测试结果

## 正式验收收尾：当前结论（2026-11-15）

本节优先于下方第一轮开发交付快照；旧失败及数据均保留。本轮基线8d8608e，本轮清理修复代码d7283215b306a49e3cd8a1830d745610cd725d65。仅修改feature/v2-sms-speech-experience；未修改8.5秒实验、main、阶段1稳定基线、版本/签名/包名/SDK。

| 项目 | 本轮结论 |
|---|---|
| 正式8秒 | FORMAL_RETAINED；安全前置清理样本通过，绝对物理终结上界仍NOT_PROVEN |
| 8.5秒 | FAILED_NOT_ADOPTED；旧8510ms失败保留，不重做实验 |
| PCM/字符扩容及5.1/5.2秒实验 | DEFERRED_OUT_OF_SCOPE，不属于本轮强制验收 |
| 耳机双击正式功能 | DEFERRED_OUT_OF_SCOPE；只保留旧测试探针，无正式UI/接收路径集成 |
| SMS-000106 | NOT_MEASURABLE；进程权限变更退出证据存在，应用清理/finish证据不能恢复 |
| 四组合8格 | 事件映射PASS，见下表；未重复发送 |
| 真实URL | MANUAL_BLOCKED；用户仅重试一条后确认仍被运营商拦截 |
| 真实SMS优先重排 | BLOCKED；保留算法/规则，不把模拟或串行播报当真实重排 |

### 8秒边界：原始缺陷与最小修复

原8004ms超时、8014ms资源释放来自budget-8000-exact-release.json的精确测点；8016ms才是观察线程时间。因此8014ms不是观察误差。原会话到8000ms才开始同步清理，时间未包含在截止前。

所有正式常量保留8000ms、5000ms、1MiB、80 UTF-16、1800ms；既有路由/清理预留仍700ms。新增的是本会话一次性清理检查：从原接收时间推导7300ms，仅在未播放或没有持有焦点的暂停状态终结等待。原PCM准入/恢复门禁已经要求保留700ms，这些状态到7300ms后不能再合法开启/恢复正时长PCM。**正在持有焦点的正常播放不被该检查终止**，仍保留8000ms最终看门狗，未缩短正常语音为7.3秒。

终结标记提前到清理入口；跨线程cancel统一投递主线程，重复终结直接返回。新增TTS取消、AudioTrack停止/释放、其他会话资源清理测点；无事件ID的PDU解析失败也记录真实finish调用/返回。权限依赖边界默认仍调用平台checkSelfPermission，受控拒绝只在测试APK注入，无产品开关。

本轮T0～T8定义采用用户的新要求：T0=onReceive入口（旧章节T0=Application入口保持历史原义）。各值相对该次入口，单位ms：

| 节点 | 慢初始化私有测试广播 | 暂停私有测试广播 | 完整5秒PCM私有测试广播 | 受控拒权复测 | 真实SMS-000123 |
|---|---:|---:|---:|---:|---:|
| T0入口 | 0 | 0 | 0 | 0 | 0 |
| T1硬8000ms超时回调 | 未触发 | 未触发 | 未触发 | 未触发 | 未触发 |
| 一次性清理检查 / 实际前置触发 | 7309 / 7313 | 7306 / 7313 | 7304 / 未触发 | 未到窗口 | 未到窗口 |
| T2开始取消TTS | 7320 | 7357 | 7577 | 2031 | 3378 |
| T3开始AudioTrack停止 | 未创建 | 7325 | 7553 | 2011 | 3356 |
| T4 AudioTrack释放返回 | 未创建 | 7348 | 7570 | 2023 | 3370 |
| T5 AudioFocus释放返回 | 未申请 | 7355 | 7576 | 2029 | 3376 |
| T6其他会话资源清理完成 | **7331** | **7364** | **7587** | **2035** | **3384** |
| T7调用PendingResult.finish | **7339** | **7369** | **7591** | **2041** | **3389** |
| T8 finish返回 | **7339** | **7369** | **7591** | **2041** | **3389** |

T2～T5是资源类别标签，不是时间排序：实现先停止并释放AudioTrack，再释放焦点，再停止/关闭TTS，故TTS时间晚于轨道/焦点。未创建对象不伪造释放时间。T6是清理API步骤完成；受控拒权和并发取消补查同时确认工作线程在finish前已terminated，100ms后也无残留。

前四列通过测试APK动态注册的私有**有序普通测试广播**执行，确实获得Android PendingResult并调用finish；其source=DEBUG_SIMULATION、broadcast=PRIVATE_TEST_ACTION_NOT_SMS。人工静音PCM与受控状态替身均明确标记，绝不是系统SMS_RECEIVED。完整5秒样本写满160000字节，7304ms检查未取消，7591ms返回finish，证明没有为了数字好看而截断该正常样本。慢初始化延迟回调到9500ms，等待至10200ms仍无旧会话复活。暂停样本用真实通知属性焦点竞争产生-3。

真实123由受保护SMS_RECEIVED进入，PCM1467ms/70400字节全量，用户确认语音完整、手机无语音；实际appVisible=false、interactive=true、keyguard=false，**不冒称锁屏新复测**。本条无预算超时，T0→T6=3384、T0→T7/T8=3389ms。系统真正分发起点仍不可观测。不能以这些样本宣称任意CPU冻结/主线程阻塞下均严格≤8000ms；活动音频最终看门狗仍在8000ms启动清理，异常停滞的绝对终结上界未证明。

### 14条终结路径审查

| 路径 | 代码/证据与边界 |
|---|---|
| 1正常完整播放 | marker→end→结果回调→Receiver.finish；本轮5秒私有广播及真实123，均先清理再finish |
| 2总预算超时 | 8000ms看门狗保留；未播放/暂停先使用既有预留清理；原8014ms失败不删除，不能承诺硬回调无调度晚到 |
| 3 PCM超限/写入超时 | PcmBudget整条准入、写入循环elapsed门禁；旧108/109零写入，当前针对性单测覆盖正式边界；未做扩容实验 |
| 4 TTS初始化失败 | 本轮受控ERROR：T6=64ms，finish调用69/返回70ms，一次stop/shutdown |
| 5 TTS合成失败 | 本轮受控onError：T6=74ms，finish78ms，零语音写入 |
| 6焦点申请失败 | 本轮受控拒绝：T6=1826ms，finish1831ms，未创建轨道；替身不冒称系统实际拒绝 |
| 7焦点永久丢失 | SpeechExperienceDeviceTest实际系统LOSS，终止/效果释放PASS |
| 8焦点临时丢失 | 本轮真实-3/GAIN恢复完整；停止/暂停到预留窗口后清理、不复活，Focus封装3场景API通过 |
| 9蓝牙断开 | 广播/输出移除/路由变化→同一end；104/105用户历史实测保留，本轮不重复断连 |
| 10蓝牙撤权 | 活进程拒权边界→同一end，本轮受控拒权PASS；106系统终止进程仍不可测 |
| 11关闭总开关 | setAnnouncingEnabled(false)→cancelSpeechWork；旧103真实PASS，本轮固定Focus.stop及并发cancel验证同一清理 |
| 12用户停止 | APP内“取消本次播报”→cancelSpeechWork；系统强停属于进程终止，无法保证Java finally/finish，未强停冒充此路径 |
| 13异常退出 | play入口捕获初始化Exception→end，本轮受控异步异常T6=1832/finish1835ms；OOM/外部kill等不承诺Java清理执行 |
| 14重复/并发终结 | 两线程各4次cancel：回调与finish均1次、轨道STATE_UNINITIALIZED、TTS stop/shutdown与焦点释放各1次、旧会话不复活 |

实际AudioTrack、焦点、LoudnessEnhancer及TTS对象被使用并核对释放计数/状态；这是所测路径的无残留证据，不扩张为所有系统原生故障都可保证释放。Receiver、Coordinator与Session各有一次性终结保护，正常回调链只有在Session清理完成之后才触达PendingResult.finish。没有在音频资源释放前提前finish以规避预算。

### SMS-000106历史核查

feature-8000-safety-read.json保留106入口、合成与首次写入，缺少资源终结和finish；不存在可补写的T6/T7/T8。cold-sms-107.json记录下一次Application入口、上一退出原因码8，以及系统am_proc_start原因broadcast/SmsReceiver、非instrumentation。官方[ApplicationExitInfo.REASON_PERMISSION_CHANGE](https://developer.android.com/reference/android/app/ApplicationExitInfo#REASON_PERMISSION_CHANGE)将8定义为运行时权限变更导致进程被杀。结合用户撤蓝牙权限操作，支持权限变更终止的判断，不等于已观察Java异常栈或finally。

SmsReceiver没有能在进程死亡后继续执行的finally；正常路径通过Coordinator结果回调finish，catch只处理可捕获异常；Coordinator的finally只释放在进程存活时的截止时间记录。外部进程终止不保证这些代码执行。未发现goAsync之后正常分支无意提前return且永久遗失回调的路径；排队有期限、异步失败均走终结。结论保持NOT_MEASURABLE，不称历史finish成功或应用提前退出导致的既定缺陷。

本轮受控拒权只把SpeechDependencies的权限读数置false，Android实际蓝牙权限仍为GRANTED、没有杀进程；已播放3200字节后安全取消，原生轨道已释放、焦点与TTS各关闭一次、工作线程在finish前退出，回调/finish各1次。它验证当前存活进程的拒权清理，不替代106历史，也不冒称真实系统撤权生命周期。

### 四组合8个真实SMS证据映射（历史提取，无重测）

| 组合（仅码/精简） | 类型 | 真实SMS ID | 预期 | 原始听感反馈 | 结论 |
|---|---|---|---|---|---|
| A 开/开 | 验证码 | SMS-000091 | 精简 | U3：“精简/全文正确…无手机语音” | PASS，finish4984ms |
| A 开/开 | 普通 | SMS-000092 | 静默 | U3：“两条普通短信都静默” | PASS，IGNORED，8ms |
| B 开/关 | 验证码 | SMS-000093 | 联系人＋全文 | U3：“精简/全文正确…无手机语音” | PASS，finish4817ms |
| B 开/关 | 普通 | SMS-000094 | 静默 | U3：“两条普通短信都静默” | PASS，IGNORED，10ms |
| C 关/开 | 验证码 | SMS-000088（89前导零补充） | 精简 | U1：“逐位正确、完整、手机没有语音” | PASS，5035ms |
| C 关/开 | 普通 | SMS-000097（98/99音量补充） | 联系人＋全文 | U4：“各档清楚…音乐降低后恢复、手机无语音” | PASS，4254ms |
| D 关/关 | 验证码 | SMS-000100 | 联系人＋全文，码逐位 | U5：“全文（含联系人）且验证码逐位完整、无手机语音” | PASS，4815ms |
| D 关/关 | 普通 | SMS-000090 | 联系人＋全文 | U2：不带链接“可以读出正确联系人，无手机语音” | PASS，4694ms |

U1/U2为会话请求call_5527d0c4cf3e414091104796f175b89c的第0/1项用户回复；U3为call_1c7b1b6d2f9747128764c47ba16b3170第0项；U4/U5为call_c9e31f6f5cda4d69ac98a9915a520e30第0/1项。U3、U4是按原问题目标取得的批次听感，没有捏造逐字转录或姓名。联系人精确匹配另有90、96、100的明确反馈；未存号码121及联系人撤权95/恢复96独立保留。开关配置来自用户操作记录及当轮UI/设置核对，不臆造旧日志不存在的逐事件配置快照。

事件API文件：88/89见feature-8000-first-read.json及second-read.json；90～94见feature-8000-matrix-read.json；97～100见feature-8000-volume-confirmed.json。早期88的JSON尚无disposition字段，但完整PCM全量写入、finish与U1反馈均存在，不补写原文件。8格均有实际编号，无需编造或补发。

### URL覆盖与本轮真实发送

SpeechExperienceTest.linksAndMeaningfulInformation覆盖https、www、裸域名、多链接、中文标点、中间链接、查询/跟踪参数、验证码、金额、日期时间、取件码、订单和地址保护；本轮补上明确http输入断言并通过。原字符串不变亦有断言。用户仅按本轮要求尝试一次指定合法URL短信，回复“仍被拦截”；因此真实过滤结论为MANUAL_BLOCKED，不升级PASS，不伪造广播，不以测试替身充数。

替代步骤：在本机执行`./gradlew.bat testDebugUnitTest --tests com.mxy1021.smsvoiceassistant.SpeechExperienceTest`可复查清理副本断言，仅属自动化逻辑证据。以后如合法真实SMS通道可达，用短正文“晚上七点见。https://example.com/a”单次测试，保持仅码/精简均关闭、指定耳机连接；应只读联系人（若匹配）＋短正文、无URL/手机语音，读取新的REAL_SMS编号及实际finish，再由用户确认。不要改用RCS/伪造SMS广播或大量反复发送。

### 自动化、异常与产物状态

本轮针对性27/27 JVM通过，覆盖SmsSafetyWindowTest、SpeechExperienceTest、SpeechFocusGateTest、SpeechStartPolicyTest；不把旧全量93项冒称本轮全量执行。Debug/Release/androidTest构建通过，Lint0错误16/15警告，合并Manifest与APK隔离PASS，4权限0Service，无版本/包名/SDK/签名变化。

首轮相关设备16方法：9个新终结方法全部PASS，原7个音频/权限方法中6通过、0dB方法1失败（系统媒体读数40→100）。用户明确没有手动调整；原closeout-instrumentation.txt保留。源码没有全局setStreamVolume/adjustStreamVolume调用，仅旧端口刷新对自身静音轨道setVolume(0f)。留存AudioService音量事件中没有本APP调用者。AOSP[getStreamVolume按设备取值](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/audio/AudioService.java)说明端点变化可能影响读数，但缺少失败瞬间的端点快照，不能把它断言为本次根因。

补充工作线程观测后只重跑并发取消、受控拒权及失败0dB三项，3/3 PASS；前两项工作线程在finish前已terminated且等待后仍无残留；0dB复测streamVolumeUnchanged=true。首次变化仍记OPEN_OBSERVATION，未解释的失败不删除、不直接改写为PASS；没有修改全局音量去“恢复”数字。当前Focus封装1方法的实际-3/GAIN恢复、停止、预留清理3场景API断言PASS，听感不冒称已人工验证。

证据：closeout-build.log、closeout-instrumentation.txt、closeout-targeted-rerun.txt、closeout-focus.txt、closeout-real-sms.json、closeout-volume-audit.json、closeout-volume-device-indices.json。测试包一次INSTALL_FAILED_ABORTED和随后等待本机授权均实际发生；用户授权后安装Success，不绕过保护、不卸载/清数据。

本轮只新增可追踪真实样本123；旧88～121的34个归档事件不重测，不用编号跨度推算全部收信数量。新样本与35个已归档真实事件统计对应，未保存短信号码、联系人或正文。正式结论仍NOT_ACCEPTED；队列BLOCKED、URL MANUAL_BLOCKED及音量观察项供用户决定，暂缓项不再作为强制验收缺口。

## 第一轮开发与交付历史（保留原始快照）

日期：2026-11-15。**当前：93项JVM、19项设备功能回归、1项受控MediaSession异常释放及1个Focus封装方法的3个实际焦点场景API断言通过；8/8.5秒各7项受控守卫通过，但8.5秒精确资源释放8510ms，严格预算FAIL，不采用、不扩容，最终8秒APK已安装并哈希核验。** 四组合8格、5位/前导零/短混合码、联系人及撤权恢复、未存号码仅正文、三档音乐听感、Home/锁屏、停止/断连/撤蓝牙恢复、真正连续三条、重启不开APP及断USB锁屏静置约10分钟收信均由用户确认。34条真实事件中25完整；静置120精确finish4356ms，未存号码121为3596ms。耳机双击不作为正式功能，真实P0重排未触发，链接受阻，其他未测项及正式人工验收保留。以下早期快照和失败记录保留，以文首及后续最新结论为准。

## 原安装版本8秒基线（未安装或改动手机代码前）

只读保留日志REAL_SMS SMS-000087；用户确认已听到语音、没有音乐、没有锁屏。不是本轮新功能真实SMS验收，日志不含短信正文，不能从日志确认其具体内容。

| 指标 | 观测 |
|---|---:|
| onReceive elapsedRealtime | 5138082ms |
| 旧finish后日志点 | 5143100ms |
| onReceive→旧finish后测点 | 5018ms（不是精确调用采样） |
| 接收→首次PCM写入 | 1907ms（不是耳机声学起音） |
| TTS初始化 | 37ms |
| PCM合成 | 93ms |
| 完整PCM | 2947ms / 141440字节 |
| 已写入PCM | 141440字节 |
| 首次写入→播放标记 | 3078ms |
| T0 / T2 / 独立T3 / T8 / T11 | 旧版没有完整独立采样，不可补造 |
| 系统分发起点及分发→finish | 不可直接测量 |
| 冷启动 | 本次无完整证据，不能判定 |

本地脱敏证据在忽略目录app/build/stage1.1-device/original-8000.json，读取脚本Measure-SmsBudget.py不会发送短信、开APP或修改权限。

## 开发初期状态（历史快照）

待后续追加最终自动化计数、设备API证据、真实SMS与人工结果。8.5秒尚未通过；5.1/5.2秒扩容未启动；双击尚无实际耳机KeyEvent，不写PASS或FAIL。

## 功能构建与设备自动化（8秒）

- JVM：93/93 PASS，0失败、0错误、0跳过（含权限撤销/失败、链接、UTF-16/PCM边界、音效回退与队列基础规则）。
- 设备：顺序安装后18项instrumentation PASS；包括7项轨道/联系人拒权、7项独立规则与真实Preferences、4项受控无音频协调器队列测试。新增诊断环溢出截止时间测试待最终设备重跑，不能计入上述18项。
- 真实轨道效果：+3/+6dB均创建1、配置1、释放1；0dB无效果；永久焦点丢失、用户取消后无会话复活；受控效果失败仍完成原始语音；STREAM_MUSIC实读音量前后相同。这些是DEBUG_SIMULATION/设备API证据，不是用户听感或真实SMS。
- Debug、Release（unsigned）、androidTest构建PASS；Lint Debug 0错误16警告、Release 0错误15警告；SDK/版本/签名不改。Manifest与APK测试隔离PASS，4权限、0Service、无生产instrumentation/测试fixture。
- 四组合真实UI状态及独立持久化smoke复测PASS。初次UI脚本因新增页面长度找不到滚动上方开关，修正定位后通过；原失败保留于本地忽略证据。
- 同签名install -r后首次设置读取与升级前一致；早期旧测试包重叠运行曾使精简开关偏离，已通过生产UI恢复并验证原状态。之后用户为真实测试主动切换不属于升级丢失。

### 保留的失败记录

1. 首次compileDebugKotlin发现setTargetGain返回Unit，错误地与状态码比较；改为调用并对setEnabled返回值检查后构建通过。
2. 首次设备测试与测试APK安装重叠，运行旧测试包：8项中3失败（新类尚不存在、两项旧全文数字断言）；不计为本轮功能通过。顺序安装后18项全通过。原输出instrumentation-functional.txt保留，不覆盖为PASS。
3. 初次UI定位失败，输出ui-smoke.json保留；修正后的ui-smoke-rerun.json四组合PASS，并恢复原设置。

## 新8秒真实日志（待用户逐条听感对应）

| 事件 | onReceive→精确finish返回 | PCM | 写入 | 首次写入延迟 | TTS初始化/合成 | 播放标记→资源释放 |
|---|---:|---|---:|---:|---|---:|
| SMS-000088 | 5035ms | 2987ms / 143360字节 | 143360 | 1891ms | 40/87ms | 25ms |
| SMS-000089 | 5035ms | 3027ms / 145280字节 | 145280 | 1890ms | 6/85ms | 37ms |

两条均观测foreground广播标志=true；系统分发精确起点仍不可直接测量。Application入口早于本次接收约260/278秒，为已存活进程，不是冷启动耗时。不能拿Application至finish约262/280秒当作广播超时，也不能省略真实冷启动专项。新功能听感仍等待用户确认；未从日志读取或保存其正文。

## 真实短信人工反馈（用户本轮确认）

- S01：验证码55555、00981均逐位正确、完整，手机没有语音。对应新8秒事件SMS-000088/89，API记录均完整写入并释放，finish返回5035ms。
- S03：不带链接的联系人短信，用户确认正确联系人名称且无手机语音；SMS-000090完成，PCM2627ms/126080字节全量写入，finish4694ms。
- S05：运营商限制，用户无法发送带链接短信；真实SMS链接过滤记EXTERNAL_BLOCKED/NOT_VERIFIED，不能改记PASS。链接过滤单测PASS，模拟听感及更完整URL场景仍与真实SMS分开。
- S02及S04：已收到SMS-000091～96的脱敏API事件；两条普通短信IGNORED（8/10ms finish），其余完整播放；等待用户逐条听感和权限操作确认，不从PCM或时长推断正文、联系人或授权状态。

最终8秒设备复核19/19 PASS（在原18项基础上增加诊断环溢出不重置等待广播期限的回归）；最终覆盖安装/测试前后真实设置快照一致。证据instrumentation-final.txt及before-final-install.json/after-final-instrumentation.json位于忽略目录。

## 8.5秒单变量候选

已建立experiment/sms-budget-8500ms；相对共同功能/测试基线的净差异只有SESSION_BUDGET_MS从8000到8500。其余80 UTF-16、5000ms、1MiB、1800ms初次焦点窗口、700ms余量、MAY_DUCK及严格路由不变。

两分支93项JVM均PASS，Debug/androidTest构建成功。首轮候选单测有一条旧测试将8秒的4300ms剩余边界写死，已在共同测试基线改成“配置预算－PCM－余量”并补边界前1ms断言；首轮92/93及复测93/93证据分别保留。不把测试断言适配当成设备8.5秒通过。

本轮尚未完成候选的真实SMS、精确finish与B01～B12对比；候选不采用，正式仍8秒。受控静音PCM预算fixtures已建立，设备结果待执行后追加；静音fixtures不是实际TTS语音，也没有PendingResult，不用于宣称广播9秒或冷启动通过。

### A/B筛选及联系人撤权/恢复：用户确认PASS

用户明确反馈A/B两组合“精简/全文正确、两条普通短信都静默、无手机语音”；联系人撤销后只读正文，重新在APP主动授权后恢复名称。对应REAL_SMS事件：

| 事件 | 目标 | 实际结果 | finish返回耗时 | 完整PCM |
|---|---|---|---:|---|
| SMS-000091 | A：仅码开/精简开，验证码 | 用户精简听感PASS，COMPLETED | 4984ms | 3027ms / 145280字节 |
| SMS-000092 | A：普通短信 | 用户静默PASS，IGNORED，无音频 | 8ms | 不适用 |
| SMS-000093 | B：仅码开/精简关，验证码 | 用户全文听感PASS，COMPLETED | 4817ms | 2747ms / 131840字节 |
| SMS-000094 | B：普通短信 | 用户静默PASS，IGNORED，无音频 | 10ms | 不适用 |
| SMS-000095 | 联系人权限撤销 | 用户仅正文PASS，COMPLETED | 3493ms | 1467ms / 70400字节 |
| SMS-000096 | 主动重新授权 | 用户恢复名称PASS，COMPLETED | 4113ms | 2147ms / 103040字节 |

每条完成事件已写入字节数等于完整PCM字节数；两条忽略没有TTS/PCM。以上不含C普通短信、D验证码的本轮完整组合覆盖，混合码/无联系人号码/音量/后台/队列等人工目标仍需补测，不用旧阶段记录替代。

## 8秒受控预算设备结果（7/7断言PASS）

使用固定人工静音PCM、真实AudioTrack/焦点/原目标严格路由；不是实际TTS语音、不是SMS_RECEIVED、无PendingResult。

| 固定样本 | 8秒结果 | 观察终结耗时 | 精确资源释放相对入口 |
|---|---|---:|---:|
| 5000ms PCM、无额外初始化延迟 | 完整160000字节写入，播放标记完成 | 7396ms | 7394ms |
| 5000ms PCM、额外2400ms初始化延迟 | 整条拒绝，0字节写入 | 2474ms | 2472ms |
| 5100ms PCM | 整条拒绝，0字节 | 1825ms | 1823ms |
| 5200ms PCM | 整条拒绝，0字节 | 1829ms | 1826ms |
| 1MiB PCM | 仍因独立时长门禁拒绝，0字节 | 1834ms | 1832ms |
| 1MiB+2字节 | 内存门禁拒绝，0字节 | 68ms | 66ms |
| 额外9500ms初始化延迟 | 超时释放，0字节；晚回调不复活 | 8016ms | 8014ms |

精确超时回调发生在8004ms、T11资源释放8014ms。8016ms是测试线程观察终结的时间，不能伪称实际PendingResult.finish。**这揭示现有截止时间Handler在预算到点才发起清理，受调度和清理耗时影响；不能从常量8000/8500推导“所有资源/finish严格在预算内”。** 7项通过仅表示完整播放/整条拒绝/释放/不复活断言，严格实际终结上界需要单独判定。证据budget-8000-instrumentation.txt及budget-8000-exact-release.json保留。

8.5秒候选安装被vivo安装器“超级守护”管控，明确提示需要授权本次安装；已请求用户在本机授权，不关闭或修改全局保护。此时尚未得到候选设备结果，不将安装受阻当成8.5秒技术失败。

### 候选安装后续

用户已明确将在手机授权本次安装；安装器随后实际返回Success，候选应用与测试包已安装并开始同一套7项受控测试。没有关闭全局保护或代替用户授予联系人权限。候选晚初始化样本的测试线程观察终结为8512ms，精确资源释放仍需关联T11，不直接用观察者时间冒称实际finish。

## 8.5秒同样本受控对比：守卫断言7/7 PASS，严格预算验收FAIL

候选44bc07e99f00615eb86b797cdf063bfc3c6d20cd与功能5da72ad1bd46d35ee503d20879031ae35e15d45f净代码差异只有SESSION_BUDGET_MS（8000→8500），使用相同固定样本和设备/耳机。

| 样本 | 8秒 | 8.5秒 | 8.5秒精确资源释放 |
|---|---|---|---:|
| 5000ms，额外初始化0 | 全量160000字节，观察7396ms | 全量160000字节，观察7389ms | 7387ms |
| 5000ms，额外初始化2400ms | 0写入整条拒绝，观察2474ms | 全量160000字节，观察7967ms | 7963ms |
| 5100ms | 0写入拒绝 | 0写入拒绝，观察1834ms | 1831ms |
| 5200ms | 0写入拒绝 | 0写入拒绝，观察1831ms | 1828ms |
| 1MiB | 时长门禁，0写入 | 时长门禁，0写入，观察1837ms | 1835ms |
| 1MiB+2字节 | 内存门禁，0写入 | 内存门禁，0写入，观察57ms | 56ms |
| 额外初始化9500ms | 超时/不复活，精确释放8014ms | 超时/不复活，观察8512ms | **8510ms，超过8500ms** |

候选超时回调精确8501ms。根因是当前Handler到总预算到点才发起同步清理，调度晚到及清理时间没有在硬截止前完成。保持单变量实验边界，本轮不同时改变清理、路由或启动参数来掩盖该结果。**不采用8.5秒；不进入提高PCM上限的第三轮。** 当前5.1/5.2仅验证仍被现有5秒门禁拒绝，未把它们设置为可播放参数。

这些受控测试没有PendingResult；8510ms是T11资源释放，不是实际finish。候选真实SMS finish、系统分发至finish和B01～B12全部真实场景仍未完成，不伪造ANR或广播阻塞结论。严格内部预算已有反例，因此没有继续扩大候选人工验收或PCM上限。

已保留budget-8500-instrumentation.txt、budget-8500-exact-release.json；已同签名install -r恢复正式8秒应用及测试包。拉取手机实际安装APK计算SHA256，与正式8秒产物相同：d18d312f43c77543349192fd156b37f04c48ead37aa1776e4741560d3daa0fb6。不是仅凭相同versionName猜测预算。恢复后设置快照另存after-budget-restored.json，未卸载或清数据。

最终代码审查新增名称内点号/不间断空格分隔长号码负例，纯文本策略保守降级；完整JVM仍93/93，Debug/Release/androidTest及Lint重新通过。此修正不改变预算、PCM、路由或音量实现；8.5秒已失败的实验数据仍按原共同基线5da72ad/候选44bc07e保存，不冒称为此后构建的实测。

最终人工交接：三档0/3/6dB（前台/Home/锁屏音乐）及C普通/D验证码的测试请求已发出，尚无用户反馈或对应新事件，保持NOT_RUN/待反馈。名称防护最终Debug产物7729f62d…尚未覆盖当前手机的8秒恢复构建d18d312f…，避免打断该轮操作；待反馈后完成更新及后续耳机实际按键/后台回归。

## 后续真实音量及C/D：用户反馈已到，后台条件保留待测

用户明确反馈三档均清楚、无失真/爆音、音乐降低后恢复、手机无语音；D全文包含联系人且码逐位完整。四条真实API均完成并全量写入：

| 事件 | 目标/配置 | finish返回 | PCM | 实际到达环境 |
|---|---|---:|---|---|
| SMS-000097 | C普通，0dB（不建效果） | 4254ms | 2187ms / 104960字节 | 前台、interactive=true、keyguard=false |
| SMS-000098 | C普通，+3dB，效果配置成功 | 4234ms | 2147ms / 103040字节 | 前台、interactive=true、keyguard=false |
| SMS-000099 | C普通，+6dB，效果配置成功 | 4253ms | 2187ms / 104960字节 | 前台、interactive=true、keyguard=false |
| SMS-000100 | D验证码全文，0dB | 4815ms | 2747ms / 131840字节 | 前台、interactive=true、keyguard=false |

四组合的8格目标现已取得本轮真实SMS与相应用户听感；三档前台音乐下真实听感PASS。但**Home/锁屏并未获得对应到达环境证据**：虽然提问给出了Home/锁屏操作，用户回复没有单独确认这两项，实际API均前台解锁。助手曾在进度中错误扩大为后台通过，已立即纠正；不能把这四条计入Home/锁屏PASS。

本轮新功能真实事件SMS-000088～100共13条：11完整播放、2规则忽略。计数不包含原安装版本基线SMS-000087，不包含任何模拟fixture。链接仍因用户报告运营商限制未发送，不计真实过滤通过。最终名称防护APK已开始同签名保留数据更新，安装完成与哈希另记；耳机首轮物理按键准备请求已发出，待实际执行。

## 最终8秒安装及后续真机回归（覆盖上述历史交接快照）

最终Debug APK已同签名保留数据安装，拉取手机实际APK核验SHA256=7729f62d2914ba36547f194eef6eb75350daf789a44b20d39ca16de6afc34b85，与最终8秒产物一致，原设置保留。证据final-install-verification.json。不是仅凭版本号判断预算，也没有卸载或清数据。

| 真实事件 | 场景与用户反馈 | onReceive→finish返回 | 完整PCM / 已写入 |
|---|---|---:|---|
| SMS-000101 | Home等5秒、音乐；完整、duck后恢复、无手机语音 | 4273ms | 2187ms / 104960字节全量 |
| SMS-000102 | 锁屏等5秒、音乐；完整、duck后恢复、无手机语音 | 4326ms | 2187ms / 104960字节全量 |
| SMS-000103 | 播报中关总开关；立即停止、重开旧语音不复活、无手机语音 | 3073ms | 2747ms / 131840字节，写58048；INCOMPLETE安全取消 |
| SMS-000104 | 播报中断耳机；立即停止、无手机语音 | 3142ms | 2747ms / 131840字节，写62144；INCOMPLETE安全取消 |
| SMS-000105 | 同耳机重连后新短信完整，无需重选，旧语音不复活 | 4767ms | 2707ms / 129920字节全量 |
| SMS-000106 | 播报中撤销蓝牙权限；用户确认停止且无手机语音 | **不可测：没有终结/finish记录** | 已观测PCM2747ms；最终字节及资源释放不可补造 |
| SMS-000107 | 正常重新授权后新短信完整、无需重选耳机 | 4813ms | 2707ms / 129920字节全量 |

101到达时appVisible=false、interactive=true、keyguard=false；102到达时false/false/true，取得独立于97～100的Home/锁屏证据。101～107实际gain=+6dB，不误写为0dB。

107记录Application入口→onReceive=59ms、入口→finish=4872ms；同次系统am_proc_start原因为broadcast/SmsReceiver且非instrumentation，构成撤权退出后真实SMS冷启动关联证据。上次退出原因码8；106无finish，不能将进程退出冒称应用完成了清理回调。系统精确广播分发起点仍不可见，4872ms不是系统完整分发周期，也不是重启或自然回收场景。脱敏证据feature-8000-background-confirmed.json、feature-8000-safety-read.json、cold-sms-107.json、process-start-causes-safety.json。

截至SMS-000107，本轮新功能真实SMS共20条：15完成、2规则忽略、2安全取消未完整、1撤权后终结不可观测。安全取消的部分写入属于用户主动停止后的结果，不是超限短信允许部分播放。

## 临时MediaSession物理双击可行性（仅测试APK）

| 轮次 | 真实按键与释放 | 用户观察 / 判定 |
|---|---|---|
| 无音乐、仅记录 | 127 DOWN/UP；创建/释放=true；未配置停止映射 | 只证明本轮收到按键，不算无音乐停止PASS |
| 音乐、临时映射127 UP | keyUps=1，cancelled=true，创建/释放=true | 用户确认语音立即停止、音乐继续且未跳曲，单轮PASS |
| 锁屏音乐、同一映射 | keyUps=1，cancelled=true，创建/释放=true | 用户确认语音立即停止、音乐继续且未跳曲，单轮PASS |

三轮均inputDevicePresent=true、externalInput=false、sourceAuthenticated=false；未记录设备名称、地址、ID或描述符。不能由此把KeyEvent认证到已保存目标蓝牙设备；公开MediaSession回调也可被MediaController发送，这是来源边界，不冒称其他设备实际失败。证据headset-probe-no-music.txt、headset-probe-music-stop.txt、headset-probe-locked-music.txt。

这是测试APK固定语音，**不是生产功能或真实SMS按键集成测试**；没有正式开关、生产MediaSession或常驻对象。Home专项、无音乐停止、其他按键/其他设备控制边界、平台真实异常尚未验证，不承诺不改变全部原有控制。尚未满足全部正式实现门槛，维持不正式实现。

另补测试APK受控异常入口controlledMediaFailure：在临时MediaSession激活后抛出固定异常，要求0字节写入、焦点释放、会话释放；释放标志仅在原生release返回后设置。该测试包assembleDebugAndroidTest成功，产物隔离复查PASS。继续工作时ADB未检测到手机，尚未安装执行，明确记NOT_RUN；不能用编译成功冒称异常退出已通过。生产Debug APK及其指纹不变。

后续连接恢复后，测试APKinstall -r成功。受控异常首轮1项失败在created断言：严格蓝牙预检查记录目标连接=false，于创建MediaSession前拒绝，尚未触发受控异常；这既不证明异常释放失败，也不能计作该路径通过。原输出headset-probe-controlled-exception.txt与脱敏media-controlled-first-precondition.json保留，已请用户连接原耳机后复测。

### 异步播放初始化异常：发现缺陷、修复与复测

连接原耳机后的第二轮真实触发固定CONTROLLED_MEDIA_SETUP_FAILURE，进程崩溃，未取得应用终结/释放断言；原记录headset-probe-controlled-exception-rerun.txt保留。根因：onDone内的try/catch不能覆盖1800ms延迟窗口后的BluetoothProbe异步回调，该回调直接进入play，焦点/轨道初始化异常会逃逸主线程。这是本次受控探针揭示的异常处理缺口，不是普通短信或平台随机抛错的发生率证据。

修复913bb1fa8b72aa092387282b419cc618d34f1b18：统一play入口捕获Exception，进入现有end清理路径；不改变预算、焦点策略、路由门禁、PCM或增益参数。原始异常文本不输出为生产诊断。相同受控异常复测1/1 PASS：created=true、released=true、keyUps=0、controlledFailure=true；断言语音写入0字节、焦点释放、app.session为空。这里只验证固定受控异常，不冒称覆盖所有系统故障。

该修复后93/93 JVM、19/19设备功能/规则/队列回归均PASS，0失败0跳过；Debug/Release/androidTest构建PASS，Lint仍0错误16/15警告，产物隔离4权限0Service PASS。证据async-playback-guard-build.log、headset-probe-controlled-exception-fixed.txt、instrumentation-async-guard.txt。用户为永久焦点丢失回归主动暂停音乐，测试完成后gain恢复原6dB，原运行/两个规则/开机/耳机配置保留。

修复APK同签名install -r并从手机拉取核验：Debug SHA256=3160606f086104011b7680afae3f4b94512a4cf911a2e9d20e82d0dbfcef5c50；Release unsigned=430620911b74712de6732efe810e1e2baec604f8a2a24440f3299e7ef4dd740c。旧7729f62d…构建及元数据已另存feature-8000-before-async-guard，不覆盖历史实验和人工证据；SMS-000088～107属于修复前构建，修复后的真实短信另记。

## 修复后真实SMS：混合码与整条拒绝

| 事件 | API结果与精确finish | 用户反馈 / 证据边界 |
|---|---|---|
| SMS-000108、109 | 均合成5507ms / 264320字节，写入0；finish1831/1832ms | 用户确认A12345全文只有短信铃声、没有语音；至少对应本轮该样本，不能把另一次同样时长记录擅自映射不同正文。已识别混合码但超5秒，整条拒绝；不记混合码完整朗读PASS |
| SMS-000110 | REJECTED，finish6ms；片段无效/发送方时间不一致或输入超限的统一拼接拒绝原因，未启动音频 | 用户81字中文样本整条静默；未读取PDU内容进一步定位，不能将其记为80字符门禁通过，也不放宽既有防混发约束 |
| SMS-000111 | COMPLETED，PCM4387ms / 210560字节全量；finish6409ms | 用户确认A123字母数字完整正确、手机无语音；本轮真实语音最大完整PCM提高至已观测4387ms，正式上限仍5000ms |
| SMS-000112 | 解析组合成功后IGNORED，明确原因“全文超过80字符安全上限”；finish43ms，无TTS/PCM | 用户81个英文字母整条静默、没有前半段；本轮真实80字符门禁与不截断证据PASS |

API分类INCOMPLETE不等于已部分播放：108/109均0字节；112沿用规则不播报的IGNORED分类，实际原因是全文超限。所有时序均onReceive→实际PendingResult.finish返回，不是系统分发周期。脱敏证据async-guard-long-confirmed.json、async-guard-long-reasons.json、async-guard-char-limit-confirmed.json、sms-112-char-limit-reason.json。

截至112，新功能真实SMS共25条：16完整、3规则/全文门禁忽略、2主动安全取消、2PCM超限零写入、1拼接拒绝、1撤权后终结不可观测。长混合码不保证播报成功；保持原大写/小写字母表达和完整优先，没有为了样本通过修改PCM或预算。

## 连续真实SMS首轮：两条完整，未触发入队

用户反馈听到A123后00981，第一条未被打断，没有只播前半段，手机无语音。对应113完整4387ms/210560字节、finish6449ms；114完整2747ms/131840字节、finish4830ms。114的onReceive发生于113的finish返回后145ms，两条均没有“等待队列”字段，**本轮未构成应用内并发入队，不能据听到的顺序判定P0优先PASS**。

指令中的普通短信“第二条。”尚未找到对应APP入口/终结事件，已请用户确认是否实际发送并在目标手机收到；不假设被队列丢弃、忽略或超时。PDU时间至入口差1910/3503ms包含不可分离的网络/时钟/系统阶段，不是系统广播排队耗时。证据queue-user-confirmed.json、queue-user-confirmed-reasons.json。

截至114，本轮共27条真实SMS事件，18完整，其余分类同112统计；连续队列专项仍为PARTIAL/NOT_EXERCISED，受控协调器优先级/容量/FIFO测试PASS不替代真实SMS并发验证。

### 连续重测：当时的待排查判断（后已澄清并撤回）

用户说明首轮第二条可能发送晚，主动重测后再次反馈听到A123、00981，第一条未被打断、各条没有前半段、手机无语音。当时助手将“第二条在手机短信应用中收到”误解为普通样本已收到的确认（见下文用户澄清）。APP实际仅观测115/116两个REAL_SMS事件：均完整，分别4387ms/210560字节、2747ms/131840字节，finish6483/4826ms；116入口在115的finish返回后123ms，仍没有入队字段。

只读核对运行开=true、仅码=false、精简=false、gain=6、开机恢复=true。该时间段无未关联source入口或真实PDU解析失败事件。短信应用显示收到不等于本APP收到受保护SMS_RECEIVED，也未证实其为SMS而非其他消息类型；本轮普通消息入口缺口原因未确认，不记作队列拒绝、P0成功或已定位的系统丢失。已请求无播报时单独发送同样普通文本并反馈收到/播报及可见消息类型，当前等待结果。

证据queue-repeat-confirmed.json、queue-repeat-confirmed-reasons.json；两轮入口串行现象与AOSP WaitingState的延后机制相容，但不能用上游源码替代本机投递证据或解释未观测消息。保持架构不变，不读收件箱、不伪造广播。截至116，本轮APP实际观测29条真实SMS，20完整；未观测普通消息不擅自计入真实SMS事件数。

### 用户澄清：前两轮只发送了两条验证码，没有漏收证据

用户明确回答：“此前两轮实际只发送了‘验证码A123’和‘验证码00981’这两条验证码短信”。助手此前把反馈中的“第二条”误解成了短信正文“第二条。”，因此上述普通消息投递缺口判断撤回，不能保留为APP/系统漏收或队列丢弃的证据。前两轮4个真实事件及其时序仍有效，但没有普通样本，也未触发应用内入队；不记P0优先PASS。

已改用三行明确正文：验证码A123、苹果到了。、验证码00981，要求实际发送三条独立短信，等待本轮真实结果；不再要求另发“第二条。”。正式报告和验收表已同步更正。

## 真正三条独立短信：连续完整通过，仍未实际入队

用户确认本轮实际发送三条，按1→2→3顺序听到；普通短信收到，没有打断、没有前半段、手机无语音。117～119均为REAL_SMS、Home解锁状态（appVisible=false、interactive=true、keyguard=false），gain=6。

| 事件 | 完整PCM与全量写入 | onReceive→精确finish返回 | 与前条的入口关系 |
|---|---|---:|---|
| SMS-000117 | 4387ms / 210560字节 | 6403ms | 首条 |
| SMS-000118 | 1867ms / 89600字节 | 3927ms | 前条finish后117ms才进入APP |
| SMS-000119 | 2747ms / 131840字节 | 4816ms | 前条finish后153ms才进入APP |

无“等待队列”记录；本轮三条接收/完整播报与用户听感PASS，但应用内并发排队/验证码超越普通消息仍为NOT_EXERCISED，不得写成P0实际优先通过。队列只重排已经进入APP的待播事件；不改变受保护广播与goAsync生命周期去追求尚未交付消息的重排。证据three-real-sms-confirmed.json及three-real-sms-confirmed-reasons.json。不存在先前误解的普通消息漏收；此前缺口判断已撤回。

截至119，本轮APP观测32条真实SMS：23完整、3规则/全文门禁忽略、2主动安全取消、2PCM超限零写入、1拼接拒绝、1撤权后终结不可测。三条听感不重复计入新的单独并发成功数量。

## 最终8秒实际焦点竞争回归（固定语音，非真实SMS）

现有DebugSmsInjectionTest的Focus选择器执行1个instrumentation封装用例，包含3个实际Android焦点场景；总体OK (1 test)，三个场景API断言PASS，不扩充为3个JUnit方法。固定样本PCM2827ms，原设置及耳机配置测试前后保持一致，证据focus-regression-final.txt、focus-regression-settings.json。

- D13.resume：实际焦点序列-3、1；暂停时写入不增长，恢复后135680字节完整写入、实际路由确认、焦点释放。听感仍MANUAL_REQUIRED，不能将API完成当真实SMS或人工听感。
- D13.stop：实际-3后关闭运行，写入6848字节后安全取消，释放竞争焦点后旧会话不复活。
- D13.timeout：实际-3后保持竞争焦点直到总预算超时，写入4800字节后取消；焦点释放、旧会话不复活。超时原因记录actualelapsed=8013ms，是Handler内构造取消原因时的采样，不是PendingResult.finish，也不是精确T11；再次说明8秒常量不保证物理终结≤8000ms。

该回归使用真实系统通知USAGE焦点请求但没有播放通知铃声，不能代替真实短信通知音的人工重叠验证。与既有19项设备功能和1项受控异常方法分开记录。完成后已请求用户正常重启、首次解锁不打开APP、接原耳机、断USB锁屏静置10～15分钟后收真实SMS；该轮尚无反馈，不计PASS，也不将请求发出时间当实际静置开始。

## 重启后不开APP＋断USB锁屏静置：本轮组合实测PASS

用户按操作单反馈：静置约10分钟，联系人及正文完整，音乐降低后恢复，短信铃声正常且未打断语音，手机无语音；完成后接回USB。系统boot_count由76变77，原运行/两个规则/增益6dB/开机恢复设置均保留，原目标配置仍存在。SMS-000120的接收状态appVisible=false、interactive=false、keyguard=true，实际foreground广播标志=true；PCM2187ms/104960字节全量写入、增益效果配置成功、COMPLETED。

该组合同时覆盖R03和R10，不重复计作两条短信；R05在本轮正常通知铃声链路的用户反馈通过，不能推导所有晚到铃声与语音重叠变体均已真机验证。静置与物理断USB、未主动打开APP来自用户操作反馈；系统重启计数和入口锁屏状态为独立读取证据。

| 节点/指标 | 本次onReceive为0的相对时间 |
|---|---:|
| T0 Application.onCreate | 不可测：当前留存日志无入口记录 |
| T1 onReceive | 0ms |
| T2 goAsync调用/返回 | 33ms / 33ms |
| T3短信解析完成 | 97ms |
| T4 TTS初始化完成 | 387ms |
| T5 PCM合成开始 | 484ms |
| T6 PCM合成完成 | 658ms |
| T7 Audio Focus获得（申请返回） | 1853ms |
| T8实际蓝牙路由确认 | 1967ms |
| T9首次PCM写入 | 1973ms |
| T10播放完成标记 | 4290ms |
| T11音频资源释放 | 4345ms |
| T12 PendingResult.finish调用/返回 | **4356ms / 4356ms** |
| TTS初始化 / PCM合成 | 140ms / 174ms |
| 首写入→播放标记 / 播放标记→释放 | 2317ms / 55ms |

系统精确分发起点仍不可直接测量；首次写入不等于耳机声学起音。留存Application/BootReceiver/Activity标记和系统进程启动原因均为空，不能据缺失记录判定120是短信冷启动，也不能补造Application→finish或系统全周期。107的撤权退出后SMS冷启动证据独立保留，不能替代本次缺失的T0。

证据post-reboot-idle-confirmed.json、post-reboot-idle-context.json与先前pre-reboot-idle.json。此次未安装APK、未打开本APP、未改变权限或设置，只读核对。至120，本轮33条真实事件：24完整、3规则/全文门禁忽略、2主动安全取消、2PCM超限零写入、1拼接拒绝、1撤权后终结不可测；包含真实基线87之外的全部本轮事件，不含任何SIM fixture。正式阶段1.1仍NOT_ACCEPTED，未完成的场景不补记PASS。

## 未存联系人真实号码：仅正文PASS

用户在联系人权限已授权、两个规则开关关闭的条件下，让未存号码发送固定公共测试文本，反馈只读正文、不读号码且手机无语音。只读包权限核对READ_CONTACTS granted=true，排除把未授权降级冒称无法匹配的情况；未读取或保存该号码/联系人。

SMS-000121为REAL_SMS、COMPLETED，PCM1467ms/70400字节全量写入，onReceive→实际finish返回3596ms；APP非前台、手机解锁，gain=6。证据unmatched-sender-confirmed.json及unmatched-sender-permission.json。本轮最终观测88～121共34个真实事件：25完整、3规则/全文门禁忽略、2主动安全取消、2PCM超限零写入、1拼接拒绝、1撤权后终结不可测。各类不等于“25/34通过率”，其中忽略/拒绝/取消多为主动设计的负例或安全场景。
