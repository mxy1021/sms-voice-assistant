# V2.0 阶段2｜技术设计与 Q0 审查

基线 `72e1297acdf51a1a4f24d52ad92935003c28b04d`，应用和测试源码与阶段1.1验收源码 `fbd183b71f1eec74a84e00976256044c579a367c` 相同。以阶段1.1关闭后的决定为准。

## Q0 只读审查

| 问题 | 仓库结论 |
|---|---|
| 现有入口与持久化 | 主 Manifest 有受 `BROADCAST_SMS` 保护的 `SmsReceiver`、`BootReceiver`、`MainActivity` 与 `ProbeApplication`，四项使用权限，零 Service。`SettingsRepository` 按启动计数对账，保留原耳机、运行、开机恢复、短信规则、增益和主题。`MainActivity` 提供真实可见生命周期、耳机刷新及现有手动语音入口。 |
| 直接复用 | `ProbeApplication.speak` 已有独立的非 SMS 入口，复用 `SpeechSession`、严格离线 Voice、`SpeechPcmProcessor`、用户增益、`SpeechFocusGate`、MAY_DUCK、`BluetoothProbe`、静音探路、实际地址匹配、每次非阻塞写入安全检查和完整清理。`SpeechDependencies` 已提供实际平台调用入口，可记录焦点结果并附加 Debug 运行门禁。 |
| 与短信广播耦合 | `SmsReceiver` 持有 `goAsync()` 的 `PendingResult`，由 `SmsCoordinator` 完成回调后 `finish`。PDU、120秒新鲜度、重放保护、规则、查联系人、最多5条待播与接收起8秒及1800ms初始焦点窗都属于短信路径。`SpeechSession` 只有短信路径引用其接收时钟并要求 RECEIVE_SMS。 |
| 通知是否需要 `PendingResult` | `SpeechSession` 的非 SMS 路径已经可以独立运行。通知回调只记录到达时间和暂取字段，启动一次会话后立即返回。通知会话自身从创建时用单调时钟，以现有内部8秒防护、5秒/1MiB PCM、700ms预留收尾。通知入口另记到会话启动的时间，不占用广播预算，也不冻结统一消息有效期。整段测试选文仍在 TTS 前检查80 UTF-16。 |
| 是否需要改消息协调器 | 不需要。观察模式全程独立，可与 SMS 同时运行。为避免共享轨道占用改变已验收的 SMS 排队或接收期限，主动音频实验要求用户暂时暂停 SMS 播报，实验结束恢复原值。使用现有共享会话的单会话门禁，不复制轨道或调度代码。 |
| 没有 Activity 时音频条件 | 监听器连接/进程 importance 可测，进程被绑定不等于有可见 Activity 或 FGS。Android 17 官方的基础限制涵盖 `targetSdk 34`，焦点可能被拒绝、播放可能静默失败。先在原配置测回调与焦点，焦点拒绝就沿用现有安全退出，后续 Track 标记和实际发声不能由该次推定。 |
| 隔离方式 | Debug Manifest 声明唯一系统绑定通知监听器与非导出诊断 Activity，从现有 Debug 页进入。使用同一包名、同一进程和原设置；生产 Manifest、Release 和短信模块源码无需改变。 |

## 最小原型

1. 通过系统设置由用户授权，监听器以 `BIND_NOTIFICATION_LISTENER_SERVICE` 保护 Service，使用官方 action 与 `exported=false`。连接后才处理本地用户的 `com.tencent.mm` 新回调。断开、销毁、撤权停止实验，按需手动 `requestRebind`；提供单次公开 `requestUnbind` 对照测试。记录新进程、连接重复、事件和缺失，避免周期任务和重放活动通知。
2. 记录标题、正文、扩展正文、会话题名、最新结构化消息/发送者、组标记、可见性、类别与脱敏 channel/通知标识。只解析最新 `EXTRA_MESSAGES` 条目，字段缺失保持未知。普通标题仅是候选；不把冒号前的片段推断为特定身份。系统展示与监听器读取分别核对。
3. 先观察真实通知字段，再用固定语句做前台听验。用户确认后可对受控测试句的一次新微信通知选择固定语音对照或真实字段语音。选文优先完整的结构化字段，其次使用原样可见标题和匹配受控句的正文候选。仅允许人为约定的短测试句触发，避免任意微信通知进入引擎。监听状态失效直接取消，仅取消原型自己的会话。系统管理绑定之外的后台权限、进程和前台状态不调整。
4. 日志只包含白名单状态、长度、时间、数值、布尔值和进程内随机脱敏编号。聊天内容不进入观察页、Logcat、应用状态保存或报告，临时选文仅交给既有离线 TTS 会话。报告中的真实证据仍须由用户按操作单确认。

依据：[NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)、[Notification](https://developer.android.com/reference/android/app/Notification)、[MessagingStyle.Message](https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message)、[Android 17 后台音频限制](https://developer.android.com/about/versions/17/changes/bg-audio)。
