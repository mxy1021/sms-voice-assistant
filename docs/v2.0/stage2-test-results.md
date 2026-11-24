# V2.0 阶段2｜证据与矩阵

日期 2026-11-24（Asia/Shanghai）。目标机实读 V2405A、系统显示标识 OriginOS 7、Android 17/API 37，当前 Android 用户为0且已安装 `com.tencent.mm`。覆盖前基线 APK 哈希与阶段1.1归档一致。覆盖安装 Debug 与测试 APK 成功，已安装 Debug SHA256 与本机产物相符。覆盖前后耳机原值、总开关、开机恢复、两个规则、增益、主题和配置完成标志一致。

起始只读过滤未发现本应用的 AudioHardening 记录。没有设置变更，无法从这次只读结果得知 hardening 开关原值。开始手动试验前核实阶段2监听组件尚未授予通知使用权。

## 自动化与静态核验

| 项目 | 结果 | 覆盖 |
|---|---|---|
| JVM | 108 PASS，0失败、错误或跳过 | 既有短信规则、字符/PCM/时限、增益、音频焦点、设置及队列等 |
| vivo 阶段2 Android 对象与组件 | 7 PASS | 安装后 Service 和 Activity 保护；构造对象检查标题/正文、扩展正文、单聊/群聊、最新条目、字段缺失、非消息、80 UTF-16。测试未发布通知 |
| vivo 原 SMS 设备回归 | 13 PASS | 7项独立规则与配置、5项受控队列、1项联系人权限拒绝。原系统短信入口和开机入口可解析，真实 SMS 同时到来待测 |
| APK 构建 | Debug、unsigned Release、Debug androidTest 通过 | 应用标识、版本和 SDK 保持基线，覆盖签名相同 |
| Lint | Debug 0 错误 / 11 警告，Release 0 / 11 | 阶段2文件未产生 Lint 问题 |
| Manifest 和 DEX | 通过 | 原4项使用权限，Debug 唯一 Service 为受系统权限保护的 NLS，Release 0 Service 且排除探针。保护短信广播、测试 fixture 隔离通过 |
| Release 包内容对照 | 通过 | 与阶段1.1归档 Release 的 ZIP 文件集合相同，只有 Git 来源元数据字节不同 |

## 真机人工用例

| ID | 项目 | 状态 | 当前证据 |
|---|---|---|---|
| N01 | 通知使用权授予 | PASS | 用户确认手动授权，ADB 确认当前用户启用该监听组件，`CONNECTED` 显示 `access=true` |
| N02 | 系统绑定、断开和重绑 | PASS | 首次 `CONNECTED`，随后观测到主动解绑导致 `DESTROYED` / `DISCONNECTED`，公开 API 请求重绑后再收到 `CONNECTED` |
| N03 | 真实单聊 | PASS | 用户确认发送，关联 `run=6ae4be3a` / `callback=1` 的真实微信 `POSTED` |
| N04 | 真实群聊 | PASS | 首次发送时该群通知关闭。开启后发送新消息，关联 `run=6ae4be3a` / `callback=3` 的真实微信 `POSTED` |
| N05 | 实际标题提取 | PASS | 两条均读取到非空 `EXTRA_TITLE`，系统显示经人工对照为单聊联系人及群聊名称 |
| N06 | 实际正文提取 | PASS | 两条均读取到非空 `EXTRA_TEXT`，系统显示经人工对照包含所发短消息，群聊正文还包含发送者 |
| N07 | 实际联系人和群名 | PASS | 本轮两个样本通过普通显示标题取得联系人或群名候选，并由用户对照确认。未提供结构化会话标题、发送者或群标记 |
| N08 | 字段缺失、预览关闭和锁屏保护 | NOT_RUN | 待真实场景 |
| N09 | 真实连续、更新、重复和移除 | NOT_RUN | 待回调序列、别名和原因码 |
| N10 | 非消息通知 | NOT_RUN | 待真实场景 |
| N11 | 前台接收 | PASS | 单聊和群聊回调时均为 `stage2Visible=true`、`importance=100`、`interactive=true`、`keyguard=false` |
| N12 | Home 接收 | NOT_RUN | 待真实通知 |
| N13 | 关屏和锁屏接收 | NOT_RUN | 待真实通知 |
| N14 | 静置接收 | NOT_RUN | 待真实通知及静置条件 |
| N15 | 重启后监听 | NOT_RUN | 待用户重启并首次解锁 |
| N16 | 撤权停止 | NOT_RUN | 待用户在系统撤权 |
| N17 | 权限恢复与再次接收 | NOT_RUN | 待用户恢复 |
| N18 | 离线 TTS 与 PCM | NOT_RUN | 待本轮固定及真实通知实验 |
| N19 | 指定耳机实际路由 | NOT_RUN | 待实际路由记录和听感 |
| N20 | 手机无语音泄漏 | NOT_RUN | 待用户确认 |
| N21 | 音乐降低及恢复 | NOT_RUN | 待用户确认 |
| N22 | Android 17 后台播放 | NOT_RUN | 官方约束明确，本机表现待测 |
| N23 | 蓝牙断开安全取消 | NOT_RUN | 待真实播放中断连 |
| N24 | 原 SMS 功能及配置 | NOT_RUN | 静态、受控设备、配置一致性已有独立证据，真实 SMS 和微信同时到来待测 |

矩阵按实际场景追加记录。授权、系统绑定、解析 fixture、真实微信、PCM、路由标记、耳机听感和手机扬声器证据各有独立状态。阶段1.1既有验收见[关闭报告](stage1.1-closure-report.md)。

## 第一轮真实微信通知观察

### N01 / N02 系统授权与连接

2026-11-24，目标 vivo，系统时间 Asia/Shanghai。用户确认手动授权。ADB 只读检查从该组件未启用变为已启用。同一进程 `run=6ae4be3a` 取得以下回调。

| 设备时间 | 观察 |
|---|---|
| 22:41:54.068 | 进入系统设置后探针停止显示，`access=false`、`bound=false` |
| 22:42:11.504 | 首次 `CONNECTED`，`access=true`、`bound=true`、`repeated=false` |
| 22:42:16.770–22:42:16.775 | `UNBIND_REQUEST` 后收到 `DESTROYED` 和 `DISCONNECTED`，`access=true`、`bound=false` |
| 22:42:24.119–22:42:24.167 | `REBIND_REQUEST` 后 48 ms 收到 `CONNECTED`，`access=true`、`bound=true`、`repeated=false` |

授权场景取证结束时阶段2诊断页是前台 Activity，当前进程当时尚无 `POSTED`。主动解绑测试时授权保持开启，系统撤权及恢复另见 N16/N17。

### N03 / N05 / N06 / N07 / N11 真实单聊

用户确认发送新的单聊。探针在 `run=6ae4be3a` 收到 `callback=1`、`notification=1`、`channel=2`，`seenKey=false`，类别 `MESSAGE`。设备墙钟为 22:45:03.760，系统通知发布时间为 22:45:03.678，发布到回调 82 ms。`access=true`、`bound=true`、`stage2Visible=true`、`importance=100`、屏幕交互中且未锁定。

`EXTRA_TITLE` 为 6、`EXTRA_TEXT` 为 3 个 UTF-16 代码单元。扩展正文、会话标题、消息数组、结构化最新发送者/正文及群标记均缺失，非 `MessagingStyle`。用户查阅该条系统通知，确认标题显示预期联系人，正文与所发短消息一致。探针读取证据为普通标题和正文的长度，系统显示语义来自这次人工对照。

### N04 真实群聊首次尝试

用户确认已发送群聊消息。至设备时间 22:49:00，同一进程仍没有新的微信 `POSTED`。22:46:43.431 的 `callback=2` 为单聊 `notification=1` 的 `REMOVED`。探针随后在 22:46:52.840 恢复可见，`access=true`、`bound=true`。查询时系统的当前微信通知数为 0。用户随后在 vivo 的群内确认该消息已到达，并确认发送时该测试群的通知已关闭。用户已在微信中重新开启该群通知，下一条新消息用于复测。

### N04 / N05 / N06 / N07 / N11 群聊复测

用户确认开启测试群通知后发送新消息。探针在 `run=6ae4be3a` 收到 `callback=3`、`notification=3`、`channel=2`，`seenKey=false`，类别 `MESSAGE`。设备墙钟为 22:51:26.815，系统通知发布时间为 22:51:26.769，发布到回调 46 ms。`access=true`、`bound=true`、`stage2Visible=true`、`importance=100`、屏幕交互中且未锁定。

`EXTRA_TITLE` 为 3、`EXTRA_TEXT` 为 14 个 UTF-16 代码单元。扩展正文、会话标题、消息数组、结构化最新发送者/正文及群标记均缺失，非 `MessagingStyle`。用户对照系统通知，确认标题为群名，正文包含发送者和所发短消息。探针只记录字段长度和存在性。显示语义来自人工对照，适用范围为本轮样本。

### 第一轮汇总

N01～N07、N11 共 8 项通过。单聊和群聊复测的系统通知发布到回调耗时分别为 82 ms 和 46 ms，均为前台、亮屏、未锁定且监听授权有效的样本。群聊首次未收到回调的记录保留，适用条件为微信测试群已开启通知。普通字段提供两种通知的显示标题与正文，两条均缺失结构化会话与发送者字段。下一轮从 Home 后的新通知接收 N12 开始。音频相关项及其他尚未执行的场景保持 NOT_RUN。
