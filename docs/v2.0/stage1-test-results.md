# V2.0 阶段1实际测试结果

日期：2026-11-15（Asia/Shanghai）。设备：已验收的vivo X200 Pro（实读V2405A）、Android17/API37。安装版本 `3 / 2.0.0-stage1`。阶段验收 **PASS**：四组合真实SMS和18项强制人工验收目标通过，R03/R10原FAIL已修复并在完整条件下复测通过；原失败及辅助fixture原始状态保留。

## 自动化工程结果

| 项目 | 实际结果 | 证据边界 |
|---|---|---|
| 首批Debug构建 | PASS | assembleDebug，本轮实际执行 |
| JVM单元测试 | 79 PASS、0 FAIL、0 ERROR、0 SKIP | 首批78项通过，末轮增加延迟焦点反例后79项再次通过；不累计重跑次数 |
| androidTest构建 | PASS | assembleDebugAndroidTest；增加SimplifyDuring后再次构建成功 |
| Release构建 | PASS | assembleRelease，沿用unsigned产物，不算发布或Release覆盖安装 |
| Lint Debug | 0错误、16警告 | 原SDK/AGP/依赖提示和UseKtx；增加测试后再次Lint通过 |
| Lint Release | 0错误、15警告 | 不扩大本阶段去升级SDK/依赖 |
| Manifest/DEX隔离 | PASS | 本轮运行Verify-DebugSmsIsolation；Debug/Release均只含原三权限、0 Service、0 instrumentation；测试APK0权限/0导出组件；fixture/新测试类/替身仅在测试DEX |
| 包身份/版本 | PASS | 两个合并Manifest及metadata均为原包名、3 / 2.0.0-stage1；namespace/签名配置/SDK构建文件未改 |
| 脚本语法 | PASS | Python compile及PowerShell Parser |

构建SDK XML版本提示保留，不阻塞本轮构建；未据此调整已确认的工具链。日志位于被忽略的本地 `app/build-v2-*.log`。

## 设备JUnit（与fixture子结果不混算）

| 类 | PASS | FAIL | SKIP | 实测内容 |
|---|---:|---:|---:|---|
| ColdStartInitializationTest | 8 | 0 | 0 | 无Activity初始化、私有Preferences模拟开机计数、未配对身份拒绝、受控TTS/焦点失败清理 |
| IndependentSmsRulesDeviceTest | 7 | 0 | 0 | 四组合经SmsCoordinator受控选文，三个真实测试私有Preferences/旧值/快速更新重载测试 |
| ForegroundBluetoothRefreshTest | 3 | 0 | 0 | 五轮前后台刷新、真实空轨道路由回调/严格重查、取消丢弃晚到回调 |
| **独立JUnit合计** | **18** | **0** | **0** | 不声称真实SMS广播、自然回收或实际权限撤销通过 |

1800ms窗口修正后以上18项全部重新执行，仍为18 PASS/0 FAIL/0 SKIP，不累加为36项。

此外为组合C跨进程恢复，单独重跑s03一次：1 PASS（重复验证，不加入上表18个独立测试计数）。启动instrumentation确实替换原目标进程，重新打开APP后PID变化、C的两个保存值和UI仍正确，原设置恢复。

Home后普通 `am kill` 第一次未实际回收进程，原断言失败记录保留，不记回收PASS；随后使用上述受控instrumentation重建验证配置，不使用force-stop，不冒充自然系统回收或真实冷启动收信。

## 真机模拟fixture：最终基准集合13类命令、30条子结果

| 命令Case | 子结果数 | 原始PASS | FAIL | SKIP | MANUAL_REQUIRED | UNSUPPORTED |
|---|---:|---:|---:|---:|---:|---:|
| Logic | 1 | 1 | 0 | 0 | 0 | 0 |
| RuleMatrix | 8 | 2 | 0 | 0 | 6 | 0 |
| Safety | 7 | 7 | 0 | 0 | 0 | 0 |
| Focus | 3 | 2 | 0 | 0 | 1 | 0 |
| Long | 3 | 1 | 0 | 0 | 0 | 2 |
| Disabled | 1 | 1 | 0 | 0 | 0 | 0 |
| Multipart | 1 | 0 | 0 | 0 | 1 | 0 |
| Rapid | 1 | 0 | 0 | 0 | 1 | 0 |
| BluetoothPermission | 1 | 1 | 0 | 0 | 0 | 0 |
| NotificationDelayedStartup | 1 | 0 | 0 | 0 | 1 | 0 |
| NotificationStartup | 1 | 0 | 0 | 0 | 1 | 0 |
| NotificationBefore | 1 | 0 | 0 | 0 | 1 | 0 |
| NotificationLate | 1 | 1 | 0 | 0 | 0 | 0 |
| **合计** | **30** | **16** | **0** | **0** | **12** | **2** |

最终集合全部30条 `assertions=PASS`：12类命令在窗口修正后重跑；BluetoothPermission在本轮用户真实撤销权限时实测，因在播放窗口之前即拒绝、路径不受常量修正影响，保留该项有效证据而不要求重复撤权。不把runner次数或子结果与18项独立JUnit相加制造不透明的测试数量。原始MANUAL_REQUIRED和UNSUPPORTED保留，不改写为完整验收PASS。

首轮未加延迟启动反例时为26条自动断言通过（15 PASS/9 MANUAL_REQUIRED/2 UNSUPPORTED）；随后新增反例在旧1200ms策略上实际FAIL，原失败单列于下节，不被最终30条成功覆盖。重跑前后的日志均保留，不累计为56条独立用例。

### 四组合PCM与过滤结果

| 组合 | 验证码PCM时长/写入字节 | 普通短信 | 修正后自动断言 | 用户首轮模拟听感（规则内容不变） |
|---|---|---|---|---|
| A | 2827ms / 135680，精简 | IGNORED，未启动音频 | PASS | 已确认符合预期、无手机语音 |
| B | 1867ms / 89600，原文 | IGNORED，未启动音频 | PASS | 已确认符合预期、无手机语音 |
| C | 2827ms / 135680，精简 | 1267ms / 60800，原文 | PASS | 已确认符合预期、无手机语音 |
| D | 1867ms / 89600，原文 | 1267ms / 60800，原文 | PASS | 已确认符合预期、无手机语音 |

6条正向均COMPLETED、actualRoute=true、focusReleased=true；2条普通短信过滤正确。用户本轮明确回复“模拟听感符合预期，未发现手机语音”，仅归入这轮模拟，不扩展为真实SMS或其他回归听感。

窗口修正后四组合8条再次得到同样的选文/PCM/过滤结果；未将未单独回报的新增模拟音频冒称为新一轮人工听验。修正后的锁屏/音乐真实听感另见SMS-000084及R03/R10复测。

### 回归证据

- 正常PCM中关闭总开关、修改筛选、筛选关时修改精简：均取消，释放焦点，恢复设置不复活旧语音；停止听感另列人工R08/R09。
- 真实Android焦点 `-3→GAIN`：无焦点期间不写PCM，恢复后完成；暂停中停止、8秒预算终结均不复活。永久丢失实测 `-1` 后取消；不是伪造回调。
- TTS初始化ERROR、合成ERROR、焦点申请失败为androidTest受控替身：零PCM、stop/shutdown/释放断言通过，不冒充真实引擎或系统故障。
- 80个UTF-16人工正文合成17227ms、另一长PCM人工正文8107ms，均完整预算拒绝且bytesWritten=0，原始UNSUPPORTED；81代码单元在TTS前整条拒绝PASS。1MiB及完整/剩余时长边界另有JVM断言。
- 暂停总开关时输入被前检拒绝；人工多段解析后组合完成193280字节；快速三输入时两个BUSY、一个完成。多段模拟不等于真实运营商PDU分发通过。

## 升级、UI与配置

- 旧V1.0已安装版本2，新Debug证书与旧已安装APK一致；保留数据安装后版本3。两个开关、运行/开机恢复、setupCompleted、目标耳机身份全部保持；未改密钥、卸载或清数据。
- UI smoke四组合均PASS，分别核对checked/enabled及持久化值；任意开关变化不改变另一保存值，不改变耳机/运行/开机恢复。
- 浅色/深色两种主题：自动状态核对PASS，截图已逐张目视检查，规则说明/开关/四组合C状态无截断或重叠；系统主题及原规则恢复。截图只在被忽略的 `app/build/v2-stage1/light.png` / `dark.png`。
- 配置对象重建/真实测试Preferences、组合C受控进程重建均通过；自然系统回收仍未验证。

## 人工真实SMS与安全回归

H01～H08（四组合）及R01～R10全部取得本轮对应证据和人工确认，18 PASS/0 FAIL/0 SKIP/0 MANUAL_REQUIRED。R03/R10原失败另行保留，不删除；R04/R05的具体人工动作与无泄漏确认不由单条共享API日志推断，证据边界详见验收报告。

| 真实事件 | 对应项 | API证据 | 用户确认 |
|---|---|---|---|
| SMS-000068 | H01 A验证码精简 | REAL_SMS、PCM完整135680字节/2827ms、实际路由匹配、COMPLETED，广播约4646ms终结 | 及时完整、无铃声截断/手机语音 |
| SMS-000069 | H02 A普通过滤 | REAL_SMS、IGNORED、未启动音频，广播约9ms终结 | 正确静默 |
| SMS-000070 | H03 B验证码全文、R01 Home | REAL_SMS、APP非前台、PCM完整185600字节/3867ms、路由匹配、COMPLETED，广播约5297ms终结 | Home后及时完整、无截断/手机语音 |
| SMS-000071 | H04 B普通过滤、R01 Home | REAL_SMS、APP非前台、IGNORED、未启动音频，广播约47ms终结 | 正确静默 |
| SMS-000072 | H05 C验证码精简、R02锁屏 | REAL_SMS、APP非前台、完整166400字节/3467ms、路由匹配、COMPLETED，约5584ms终结 | 锁屏下正确精简，及时完整、无截断/手机语音 |
| SMS-000073 | H06 C普通全文、R02锁屏 | REAL_SMS、APP非前台、完整145280字节/3027ms、路由匹配、COMPLETED，约4519ms终结 | 锁屏下正确全文，及时完整、无截断/手机语音 |
| SMS-000075 | H07 D验证码全文、R06音乐 | REAL_SMS、APP非前台、完整95360字节/1987ms、路由匹配、COMPLETED，约3888ms终结 | 全文正确完整、无泄漏，音乐降低并恢复 |
| SMS-000076 | H08 D普通全文、R06音乐 | REAL_SMS、APP非前台、完整68480字节/1427ms、路由匹配、COMPLETED，约3047ms终结 | 全文正确完整、无泄漏，音乐降低并恢复 |
| SMS-000077 | R08总开关停止 | REAL_SMS、已确认实际路由，写入70336/131840字节后用户取消并释放，INCOMPLETE（按要求停止，不冒充完成） | 立即停，重开不复活，无手机语音 |
| SMS-000078 | R09精简切换停止 | REAL_SMS、已确认实际路由，写入86720/141440字节后用户取消并释放，INCOMPLETE（按要求停止） | 立即停，切回不复活，无手机语音 |
| SMS-000079 | 动态路由回归期间新短信 | REAL_SMS、完整151040字节/3147ms、实际路由匹配、COMPLETED，约4706ms终结 | 用户整体确认重连后新消息恢复 |
| SMS-000081 | 动态输出安全取消 | REAL_SMS、开始时实际路由已确认，写入125632/154880字节后“目标输出被移除”而取消并释放，约3836ms终结 | 用户确认R04/R05动作中止且无手机语音；不把这单个事件伪称为两次独立回调 |
| SMS-000082 | R07重新授权恢复 | REAL_SMS、APP前台、完整139520字节/2907ms、路由匹配、COMPLETED，约4452ms终结 | 用户确认原耳机正常恢复，无需重新选择/反复刷新，新短信完整且无手机语音 |
| SMS-000084 | 窗口修正后组合C锁屏/音乐复验 | REAL_SMS、APP非前台、完整141440字节/2947ms、路由匹配、无失焦、COMPLETED，约5642ms终结 | 用户确认真实短信完整，音乐降低/恢复，无截断或泄漏 |
| SMS-000085 | R03/R10修正后重启/断USB/静置复测 | 启动计数76，接收在启动后约655108ms；非前台/interactive=false/keyguard=true，完整95360字节/1987ms，路由匹配、COMPLETED，约4128ms终结 | 用户确认解锁未开APP、断USB锁屏静置10分钟，及时完整无手机语音 |
| SMS-000086 | 同轮修正后后台新SMS | 非前台/interactive=false/keyguard=true，完整141440字节/2947ms，路由匹配、无失焦、COMPLETED，约4933ms终结 | 同轮用户完整无泄漏确认；实际走全文路径，OTP精简另见H01/H05 |

只收集生成的状态字段，不读取正文/发送方/验证码；模拟与真实来源分开。操作及回报字段见[人工清单](stage1-test-plan.md)，最终数量及状态见[验收报告](stage1-acceptance.md)。

D组H07首次真实尝试SMS-000074：完整PCM247040字节/5147ms，超过5秒，约1225ms时安全终结，语音PCM写入0，尚未申请焦点/确认实际播放路由。原尝试记UNSUPPORTED/整条安全拒绝，不算全文完成，也不据此放宽预算；短人工正文SMS-000075已复测PASS，原拒绝记录保留。四组合目标8 PASS/0 FAIL，共9次真实尝试，不把安全拒绝计作完整播放成功。

动态回归期间另有SMS-000080：完整PCM394880字节，预算门禁整条拒绝，写入0，约1227ms内广播终结，未确认播放路由；仅作为额外安全拒绝，不算动态停止或全文完成。

## R03/R10首轮真实FAIL与修复过程（永久保留）

用户实际条件：重启、首次解锁未打开APP、断USB锁屏静置15分钟；系统SMS已收到，但没有可听语音。SMS-000083为REAL_SMS，接收时APP非前台/interactive=false/keyguard=true；开机计数74→75，开机后约50秒Application已按保存状态恢复启用，接收在启动后约916725ms。说明投递、配置恢复已成功，不把失败推测为厂商未自启动。

| 接收后时间 | 实际事件 |
|---|---|
| 631ms | 完整PCM合成完成，141440字节/2947ms |
| 1254～1267ms | 原1200ms窗口后请求焦点，GRANTED |
| 1423ms | 首次语音PCM写入，实际目标路由匹配 |
| 1435ms | 实际临时失焦-3；随后暂停，累计仅4800字节 |
| 6757ms | GAIN恢复；剩余预算约1241ms，剩余PCM约3004ms，remainingFits=false |
| 6785ms | 安全取消、释放焦点并finish；不是播放完成 |

原R03/R10听感结论FAIL，不能以已投递或安全取消改写为PASS。具体竞争包未知，仅确认临时焦点竞争时序。

先加NotificationDelayedStartup受控真实Android焦点竞争（约1400ms启动、保持5322ms）：旧策略实际[-3,1]、写入4800字节后取消，正向断言FAIL。然后只改SpeechStartPolicy单次窗口1200→1800ms；原接收起点、8秒总预算、5秒/1MiB/80 UTF-16、路由和MAY_DUCK均不改。修复后该专项完整135680字节/2827ms，actualRoute/focusReleased=true，自动断言PASS。Startup/Before均完整，Late仍在晚到长期失焦后按预算取消且不复活。

随后Debug/Release/androidTest构建、79项单测、两种Lint、Manifest/DEX隔离、18项设备JUnit及受影响fixture全部通过；保留数据安装后当前设置/目标身份不变。修复后短真实SMS-000084由用户确认锁屏/音乐场景完整无泄漏；再完整执行重启/首次解锁不开APP/断USB/锁屏静置10分钟，SMS-000085/86完整播放，用户确认及时完整无手机语音。R03/R10最终PASS，旧真实FAIL及旧策略受控FAIL永久保留。

## 原失败/工具限制

初次安装ABORTED、测试APK流式安装ABORTED及120秒无提示超时、临时runner发现的CRLF问题、Home/am kill未实际回收，均保留于[问题记录](stage1-known-issues.md)。安装改非流式后同一签名/安全检查正常成功并通过APK摘要校验。无当前未修复关键应用FAIL，强制人工待办已清零；辅助fixture未单独听验及继承未验证场景仍如实保留。

原始脱敏fixture日志、JUnit输出、UI/升级/重建摘要位于被忽略的本地 `app/build/v2-stage1/`。只将汇总写入仓库，V1.0原始验收文档不变。
