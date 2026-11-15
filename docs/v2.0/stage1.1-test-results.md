# V2.0 阶段1.1测试结果

日期：2026-11-15。**当前：93项JVM、19项设备功能回归及1项受控MediaSession异常释放测试通过；8秒/8.5秒各7项受控守卫断言通过，但8.5秒精确资源释放8510ms，严格预算FAIL，不采用、不扩容，最终8秒APK已安装并哈希核验。** 四组合8格、5位/前导零、联系人及撤权恢复、三档前台音乐听感、Home/锁屏、总开关、断连重连、蓝牙撤权恢复均由用户确认。耳机双击仅有部分真机可行性成功，不作为正式功能。静置/重启、长文本及连续真实队列等仍待验收。以下开发初期快照和失败记录保留，以文首及后续追加的最新结论为准。

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
