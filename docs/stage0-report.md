# 阶段 0 技术验证与验收报告

日期：2026-11-11（Asia/Shanghai）。项目：SMS Voice Assistant；工作目录：项目根目录；GitHub：<https://github.com/mxy1021/sms-voice-assistant>；分支：`main`。

## 当前结论

阶段 0 原型代码已实现。用户已确认本 APP 默认 AIService TTS 在断网下手动播放新句成功，实际从指定蓝牙耳机输出，初始化、Voice、语言、speak 和播放回调均正常。原“离线中文 / 焦点测试”A/B 无声为已确认的失败反馈；本次新增的前台最小焦点路径待真机验证。阶段 0 仍未通过验收，不进入阶段 1。下文保留历史基线，最新结论见末尾“焦点测试无声专项”。

## 初始目录与远程核查

- 正式目录存在，初始仅有 `.git`，没有项目文件、已有提交或待保护的代码修改。
- Git 根目录即本项目根目录，不属于其他仓库；本地分支为 `main`。
- `origin` 已指向指定 HTTPS 地址，直接复用；不更换仓库、不创建嵌套仓库。
- 初次及开发中复查 `git ls-remote` 均成功且无分支；GitHub API 确认 Public、默认分支 `main`、仓库为空。
- 没有执行强推、清理未知文件、覆盖历史或独立历史合并。

## 实际测试环境

| 项目 | 实际情况 |
|---|---|
| 主机 | Windows，PowerShell |
| JDK | Eclipse Temurin OpenJDK 17.0.20.1+1，实际命令成功 |
| Gradle | 8.13，复用本机分发生成标准 Wrapper，锁定官方分发 SHA-256 |
| AGP / Kotlin | 8.12.1 / 2.2.0 |
| Android SDK | 已安装 android-36；项目明确使用 Build Tools 36.0.0 |
| min / target / compile | 33 / 34 / 36 |
| ADB | 可启动；在线已授权设备 0，离线模拟器 1；未记录序列号 |
| 计划目标 | vivo X200 Pro / OriginOS 7 / Android 16，实际版本未验证 |
| 安装及手机权限 | 未执行：无可用已授权设备 |
| 真实短信 / 蓝牙耳机 / 离线听验 | 均未验证 |

构建过程的 SDK XML 版本提示反映本机工具元数据版本差异；不据此修改全局环境。首次默认构建触发 Build Tools 35 安装流程后中止，后续显式使用已有 36.0.0。最终构建结果以下表为准。

## 开发与自动化结果（首次交付历史基线）

| 编号 | 验证项目 | 分值 | 代码状态 | 主机证据 | 真机状态 |
|---|---|---:|---|---|---|
| P0-01 | 工程构建与安装 | 10 | 已实现 Compose 单模块工程 | Debug 构建、单元测试、Lint 通过 | 未验证安装 |
| P0-02 | 短信权限与广播 | 20 | 已实现正常申请、状态复核、系统广播、单/多段解析 | 人工样例单段、多段、无效模板测试通过 | 未验证 |
| P0-03 | 蓝牙连接与音频路由 | 15 | 已实现配对选择、连接、预期及实际路由门禁 | 空地址、别名、混合输出等拒绝策略测试通过 | 未验证 |
| P0-04 | 离线中文 TTS | 15 | 已实现资源检查、内存合成、播放回调和释放 | 仅静态实现；引擎兼容不可由主机证明 | 未验证 |
| P0-05 | 音频焦点与音乐恢复 | 15 | 已实现 A/B、失败拒绝及退出释放 | 仅静态实现；播放器响应不可由主机证明 | 未验证 |
| P0-06 | 后台端到端 | 25 | 已实现接收器内有界异步会话，无 Service | 仅静态实现；纯逻辑测试不能证明后台可行 | 未验证 |
| 合计 | | 100 | | | 尚无真实设备通过项 |

### 构建命令与结果

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

最终运行结果：`BUILD SUCCESSFUL in 9s`，51 项任务（15 项执行、36 项缓存最新）。

- `:app:assembleDebug`：通过，APK 已生成；没有可用设备，未安装。
- `:app:testDebugUnitTest`：4 项测试，0 失败、0 错误、0 跳过。覆盖人工单段/多段、拒绝无效和超长模板、路由身份拒绝策略；不覆盖 Android 真机 API、后台生命周期或实际音频。
- `:app:lintDebug`：通过，0 错误、8 警告。警告为固定 target 34 的兼容提示、较新 Gradle/Compose/Activity 版本提示、4 项 SharedPreferences KTX 写法建议；保留固定验证基线，不隐藏警告。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，24,129,306 字节。
- APK SHA-256：`F998F4531AA8C5E00833BBC90156ABF0B0958034D5E7C43635CDD4A084D316CD`。
- 最终合并清单核验：仅请求 `android.permission.RECEIVE_SMS` 与 `android.permission.BLUETOOTH_CONNECT`。无 Service、无联网权限。
- 本地证据：`app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/test-results/testDebugUnitTest/`、`app/build/reports/lint-results-debug.html`。这些报告含本机信息，留在忽略的构建目录，不上传。

### 固定计分方法

防止用实现程度替代验收，计分固定如下；部分计分仅记录已验证子项，整项通过仍需全部必要测试有真实结果。

| 编号 | 子项分配 | 已确认分数 |
|---|---|---:|
| P0-01 | 构建/APK 6；主机单元测试 2；真机安装启动 2 | 8（安装未验证） |
| P0-02 | 正常授权 8；前后台真实广播 8；单多段解析 4 | 0（未验证） |
| P0-03 | 配对选择 3；真实连接 4；输出身份/路由 6；拒绝路径 2 | 0（未验证） |
| P0-04 | 离线资源 3；断网耳机听验 8；回调/释放 4 | 0（未验证） |
| P0-05 | A 模式 5；B 模式 5；完成/失败/取消后的恢复 5 | 0（未验证） |
| P0-06 | E01 3；E02 4；E03 4；E04 4；E05～08 各 2；E09～10 各 1 | 0（未验证） |

当前仅确认主机子项 **8/100**，只能作为进度记录，不能成为真机验收成绩。其余 92 分没有实际证据，不授分。

## 待执行真机结果记录

P0-01～06 以及 E01～10 的操作、预期与排查步骤见 [测试计划](stage0-test-plan.md)。每种条件记录至少 3 轮，完整保留失败。

| 用例/轮次 | 系统与安装途径 | 权限实际结果 | 连接/路由 | 脱敏终结状态 | 耳机听感与音乐恢复 | 结果 |
|---|---|---|---|---|---|---|
| 尚未执行 | 未验证 | 未验证 | 未验证 | 未验证 | 未验证 | 未验证 |

不可提交短信正文、真实验证码、号码、设备地址/序列号、个人日志及含数据截图。没有收到广播时，应用本身不能证明手机当时是否收到 SMS，须用现场外部记录对应；只见开始状态无完成状态不能判成功。

## 关键风险与阶段边界

1. **RECEIVE_SMS 限制**：安装器允许与用户授予均需真实验证；普通广播与带 Retriever Hash 的特殊 OTP 分发分别核查。不得以模拟广播替代。
2. **蓝牙身份/路由**：配置文件连接、预期输出与实际轨道路由分别确认。地址缺失、BLE 身份映射不可确认时拒绝；异步路由改变仍可能存在缓冲/回调窗口，动态断连必须实测。
3. **离线 TTS 与生命周期**：voice 标记不足以证明断网可用；`/dev/null` 描述符、PCM 回调及格式在厂商引擎上未验证。保守超时可能中断较慢的合成/语音，按失败记录，不回退到持久化音频。
4. **后台进程**：`goAsync()` 覆盖整个会话，8 秒后终结，不等于保活承诺。被系统杀死时只能观察最后脱敏状态，无法靠自我日志证明执行完成。
5. **Android 15/16 焦点**：官方 target 35+ 前台/前台服务门槛与本项目 target 34 分开判断；本基线不证明后续提升 target 后仍能后台获取焦点。焦点授予不等于音乐暂停/恢复成功。
6. **OriginOS**：划走、锁屏、静置、默认后台策略都未验证；不能通过偷偷加服务或调整系统设置宣称可行。

依据链接及条件见测试计划的官方资料。应用清单最终必须仅有两项请求权限；依赖自动合入的旧版自定义接收器权限在本项目 API 33 基线上移除。

## Git 与公开仓库审查

已审查本次 23 个项目文件、忽略规则、暂存区差异与敏感模式，格式检查通过。没有发现真实短信、号码、设备序列号、Token、私钥或本机用户目录配置；源码数字来自任务明确提供的人工样例。Wrapper JAR 已纳入，APK、构建目录、日志、密钥和本机配置均排除。自动扫描只能作为人工审查的辅助，不能证明不存在任何未知格式敏感内容。

工程交付首次提交：`7e455e90d12dd7b14e96d05c4bc1a3021e0a32e2`，信息：`feat: add stage 0 Android feasibility prototype`。

已执行普通 `git push -u origin main` 并成功建立上游。随后 `git ls-remote origin refs/heads/main` 与本地 HEAD 均为上述 SHA；`git status` 显示 `main...origin/main` 且工作区干净。GitHub API 也确认该提交及 23 个文件存在，包含源码、文档和 Wrapper JAR；不含 APK、测试日志或本机配置。

本次报告补记作为后续文档提交，继续使用普通快进推送。最终提交 SHA 和最后一次远程核对以 Git 历史及交付消息为准，避免把文档提交自身 SHA 写入其内容形成自引用；上述 SHA 是已验证的工程交付提交，后续只修改本报告。

## 验收决定

必须同时满足：至少 85/100；P0-02、P0-03、P0-04、P0-06 全部通过；所有验收项有真实结果；未执行均明确标记。当前无可用真机，**阶段 0 未通过验收，暂不进入阶段 1**。

## AIService TTS 兼容性修复（2026-11-11）

本节保留上轮修复时的环境、构建与待测状态；用户最新确认结果见末节，不再把基础手动 speak 标记为未验证。

### 已知证据和原实现问题

用户亲测：vivo X200 Pro / OriginOS 7，系统首选 AIService TTS，中文（中国），联网及关闭 Wi-Fi/移动数据后系统朗读均成功。本机 ADB 复查仍为在线设备 0、离线模拟器 1；本次没有在目标手机执行修改后的 APP。

源码核查：Manifest 已有 `android.intent.action.TTS_SERVICE` queries；原初始化已等待 OnInit 并投递主线程。问题在严格筛选只接受枚举中的 `zh` + `CN` + 不需网络 + 无 notInstalled，未找到时直接终结，且把“枚举无候选”和“setVoice 失败”合并为“未安装离线语音”。未先用 setLanguage 验证默认语言播放路径，也没有显示返回值和枚举信息。

已经确认的是该提示证明不了系统没有离线中文；AIService 具体是空枚举、Locale 差异还是标记/设置失败，目前未知，不能虚构其返回值。`isNetworkConnectionRequired` 是 Voice 元数据的网络要求，不是本次断网听验的结果。

### 最小修复

- 新增独立的前台手动兼容性测试，固定新句“语音兼容性测试，验证码七九一四五二。”。不接收短信内容、不保存诊断文件，不调用文件合成，不进行蓝牙判断或应用音频焦点请求。
- 等待默认 TTS OnInit，分别记录 isLanguageAvailable / setLanguage 简体中文返回值；优先选择元数据明确的离线简体中文 Voice，兼容 `zh`、`zh-Hans` 等无地区中文 Locale；无候选时只要初始化及 setLanguage 成功，就允许该次人工触发的 speak。无法查询一律标未知，不直接判断资源不可用。
- 界面显示默认引擎名称/包名、实际引擎字段、枚举数量/中文 Voice/Locale/网络及安装标记、当前 Voice、请求返回值、回调、查询异常、错误阶段和码、资源释放。实际绑定引擎包名没有公开查询 API，因此明确为未知，不能拿默认引擎值冒充。
- 人工确认 Wi-Fi 与移动数据关闭后才能点击；这不是自动网络检测或离线通过证据。30 秒超时、完成、错误、用户停止或页面离开均释放 TTS；手动诊断与原有会话互斥，避免真实短信混入诊断播放。
- 原有严格条件、音频焦点、蓝牙身份/路由和 8 秒广播生命周期不放宽；仅细分严格初始化、语言、枚举、setVoice 错误提示。不修改接收器架构、验证码规则、权限、targetSdk 或 Service。

### 验证与交付

已执行 `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`，最终 `BUILD SUCCESSFUL in 10s`（51 项任务，15 项执行、36 项缓存最新）。

- Debug APK 已生成：`app/build/outputs/apk/debug/app-debug.apk`，24,129,306 字节；SHA-256：`9FC6E8DC97B4FB9C20D4B11C804ABF01C8F81034D15FE93544804C67A34D96DD`。此项取代前文首次交付 APK 的历史校验值。
- 原有 4 项测试与新增 3 项兼容策略测试全部通过：合计 7 项，0 失败、0 错误、0 跳过。新增覆盖无地区/简体脚本 Locale、未知网络/安装标记不能充当离线证据、初始化/语言设置失败或未知时禁止手动请求。纯逻辑不证明设备 TTS 成功。
- Lint：0 错误、8 警告，与基线相同，未关闭检查。
- APK 权限实查仍仅 `RECEIVE_SMS` / `BLUETOOTH_CONNECT`；Manifest、targetSdk、短信接收器和识别规则无变更。无新增 Service、在线 SDK 或真实数据文件。
- 变更包括 MainActivity、ProbeApplication、SpeechSession、新增 TtsCompatibilityProbe / TtsCompatibilityPolicy 及其单元测试，以及两份阶段 0 文档。兼容诊断仅在内存/界面显示，不写日志或偏好文件。

T01～05、本 APP 在 AIService 的断网朗读和后续蓝牙/后台均标记 **待验证**。系统自带测试成功不增加本 APP 验收分数；仍维持已确认主机子项 8/100。

本专项提交信息：`fix: improve TTS compatibility with vivo AIService`；仅允许普通推送 main，不使用 force push。提交自身 SHA 与远程比对结果见 Git 历史及交付消息，不把提交自身 SHA 写入本文形成自引用。

## 焦点测试无声专项（2026-11-11）

### 已确认真机反馈

vivo X200 Pro / OriginOS 7：APP 识别默认 AIService TTS，关闭 Wi-Fi 和移动数据后，手动兼容性测试的新句成功从指定蓝牙耳机输出。初始化成功、getVoices 正常、setLanguage/setVoice/speak 成功，onBeginSynthesis/onStart/onDone 均收到。以上按用户确认记为成立，不要求重新安装 TTS 引擎。原“离线中文 / 焦点测试”在 A、B 模式均无声；尚无该路径时序数据，不能断定 requestAudioFocus 失败。

### 源码发现与最可能原因

原焦点按钮进入 `ProbeApplication.speak` → `SpeechSession`，实际并不调用 `tts.speak`，而是严格 Voice 筛选 → `/dev/null` 文件合成 → PCM 回调收集 → 格式校验 → 请求焦点 → 静音 AudioTrack 实际路由 → 写入语音。只有合成完成且 PCM 合格才申请焦点；8 秒预算包含所有冷启动、蓝牙查询、合成和播放。

因此最可能是**两条播放实现不等价、原 PCM 链路的额外门禁/合成方式或总预算导致无声**。候选包括严格 Voice 筛选提前拒绝、文件合成兼容/PCM 回调缺失或格式限制、8 秒超时、静音实际路由未匹配；焦点本身也须看返回值，不能排除或直接定罪。基础 speak 成功不证明文件合成和 PCM 播放成功。

原实现 TTS 与焦点属于同一会话，未复用已经关闭的兼容性实例；原正常完成路径等 AudioTrack 播放标记后释放，未发现无条件在开始前 stop 或正常播放中提前释放焦点的代码。错误、超时、丢焦和 Activity 销毁会取消，需用新增时序定位本次具体触发点。原模式读取在异步合成后进行，现改为启动时固定 A/B，避免等待期间切模式改变本次请求。

### 修改与隔离边界

- 保留原按钮及自动短信的严格 PCM/实际路由门禁，加入开始、模式、连接、预期路由、前置条件、Voice/合成、PCM、焦点返回值及回调、播放标记、stop/shutdown/abandon 的时间顺序、调用原因和最终阶段。原链路明确显示“未调用 speak”，合成 onDone 与实际播放完成分别记录。
- 新增“前台最小焦点播放测试”：固定新句“前台焦点测试，验证码六八三一九五。”；耳机连接/预期路由 → 新 TTS 初始化/中文 → 再次路由检查 → 焦点 → 直接 speak。独立实例，不经过短信、后台或 PCM 中转；仅手动模拟路径可沿用兼容性语言默认回退。
- 预期路由、requestAudioFocus、TTS 与原 AudioTrack 使用同一 `USAGE_MEDIA + CONTENT_TYPE_SPEECH`。基础兼容性测试仍未显式设置属性，作为已通过的对照，不改变它的播放方式。新测试显示此差异，不假称引擎默认属性已知。
- 最小测试正常等 speak 的 onDone 后清理；失败/取消/丢焦/30 秒超时/离开前台立即清理，日志注明是否在 onDone 前、原因及返回值。没有把 requestAudioFocus 成功当成必有 GAIN 回调或音乐已响应。
- UI 分栏显示短信广播、解析、模板、蓝牙、路由、焦点、TTS、后台。新增“准备本轮短信观察”，未观察到广播不等于系统没收到短信。只持久化脱敏阶段快照与未终结标记；进程重启发现未完成时明确原因未知，不伪称后台受限已证实。时间线最近 120 条仅在内存，不保存短信、数字、设备身份或异常正文。
- 不新增权限、Service、网络依赖，不改变 targetSdk、短信规则或单模块架构；保留 BroadcastReceiver + goAsync 有界会话。

### 现状与验收

| 项目 | 当前状态 |
|---|---|
| AIService / 离线中文 / 基础直接 speak / 指定耳机听感 | 用户确认通过 |
| 原焦点按钮 A/B | 用户确认无声，失败，具体阶段待新增日志定位 |
| 新前台最小焦点 A/B | 代码已实现，待真机验证 |
| 音乐降低/暂停与恢复 | 待真机验证 |
| 真实短信前台、后台、划走、锁屏 | 均未取得本轮证据，待验证 |
| 后台受系统限制 | 原因未确认，不能把未完成直接归为系统限制 |

本机无在线已授权设备，不能安装或宣称新路径有声。基础手动路径通过不等于严格 P0-04、P0-05、P0-06 全部通过；不重新臆算总分，仍不满足阶段 0 验收条件。

### 本次主机验证与工程交付

- 执行 `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`，最终 `BUILD SUCCESSFUL in 17s`，51 项任务（15 项执行，36 项缓存最新）。
- 现有 7 项单元测试全部通过：0 失败、0 错误、0 跳过。没有用纯逻辑测试替代音频/蓝牙/后台真机测试。
- Lint：0 错误、11 警告；包括固定 target、可更新依赖及 SharedPreferences KTX 写法建议，没有关闭检查。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，24,129,306 字节；SHA-256：`62EDE712035BE5C11326BC6167DE533D61EFBAD7AD5233F6F8A9BA42B7EC7F77`。这是本次最新产物，取代前文历史 APK 校验值。
- 直接检查 APK：只请求 `RECEIVE_SMS`、`BLUETOOTH_CONNECT`。清单、构建配置/target、OtpParser 无修改；未增加 Service、网络库或权限。
- 本次修改 MainActivity、ProbeApplication、SpeechSession、TtsCompatibilityProbe、SmsReceiver、两份阶段 0 文档；新增 DiagnosticTimeline、ForegroundFocusTest，共 9 个相关文件。接收器只增加解析与启动阶段诊断，保留原广播方式及 goAsync 生命周期；规则不变。
- 公开仓库只纳入源码和脱敏文档；APK、本机配置、测试 XML/HTML 日志继续留在忽略的构建目录。诊断中不输出真实短信、数字、蓝牙地址或设备序列号。

提交信息：`fix: diagnose no-audio issue in focus test flow`；仅普通推送 origin/main。最终提交自身 SHA、远程核验及干净工作区状态以交付消息和 Git 历史为准。
