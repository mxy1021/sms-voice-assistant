# V2.0 阶段1.1架构

状态：开发中，人工待验收。沿用SmsReceiver → SmsAssembly / ReplayGuard → SmsCoordinator → SpeechFormatter → SpeechSession；不新增接收架构或常驻对象。

## 文本与联系人

SmsSpeechRules为唯一四组合决策层。OtpExtractor仅处理上下文明确的码，保存当前内存中的原文span；SpeechFormatter只替换该span。SmsTextCleaner仅修改朗读副本。最终字符检查包含联系人及码拼读的长度，PCM预算仍是独立门禁。

全文模式才查询联系人。ContactMatchPolicy对中国手机号统一+86/0086/空格/横线，并支持明确国际E.164；不做尾号模糊匹配，不匹配短号、字母ID。PhoneLookup查询只投影NUMBER和DISPLAY_NAME，逐行精确复核；超过8行、同号异名、名字为号码或包含长号码时保守降级。

ContactNameResolver仅本事件创建工作线程与CancellationSignal，250ms到期返回正文；查询前、逐行、返回后检查READ_CONTACTS。游标use关闭、超时取消查询、不缓存。取消Provider调用由临时线程执行以避免Binder阻塞主线程；平台查询/取消能否及时退出仍需真机压力验证，250ms是应用放弃结果期限，不是对第三方Provider内部执行时间的保证。旧permit或预算失效时不启动语音。

## 音量与资源

SessionLoudness只接受正AudioTrack会话ID和0/3/6dB；0不创建效果，3/6转换为300/600mB。构造、配置、启用失败释放候选并回退。SpeechSession唯一终结路径先停止/flush轨道，释放效果再释放轨道；所有正常、取消、永久焦点丢失和异常都经该路径。音效不调用系统setStreamVolume/adjustStreamVolume，也不控制其他播放器。代码作用域隔离不等于已通过设备听感。

## 时间测量

T0 Application.onCreate入口、T1 onReceive入口、T2 goAsync调用/返回、T3解析完成、T4 TTS初始化完成、T5/T6 PCM合成、T7焦点申请返回（须同时检查授予结果）、T8实际蓝牙路由确认、T9首次非静音PCM写入、T10播放完成标记、T11音频资源释放完成、T12实际PendingResult.finish调用/返回。

elapsedRealtime计时与回调到达时间分离。首次写入不是耳机实际声学起音，AudioTrack标记不是人工听感。旧版finish日志是在finish之后记录，不能伪称精确调用时间。系统分发精确起点若不可观测则null；PDU时间不是分发时间，Stage0Log初始化时间也不是Application入口。

广播预算包含排队、查询、TTS、路由、播放和清理。Android主线程暂停/进程冻结时Handler不能保证物理时刻执行，恢复时elapsedRealtime门禁必须拒绝过期PCM；没有设备证据前不能声称严格finish上界或无ANR。

## 已确认队列A

用户本轮确认：最多5条待播，满时拒绝新消息，不覆盖原待播。当前解析/联系人查询/音频会话不抢占；待播OTP优先，同级按接收顺序FIFO。正文仅在内存，联系人查询仅轮到本消息才开始，避免为每条等待消息创建查询线程。每条的接收时间和permit不重置，排队计入自身8000ms期限；等待到期立即终结本条，不续播。正常完成才调度下一条；关闭/配置变化/断连/撤权/永久焦点丢失等安全终止清空待播，恢复后只接收新消息。字符/姓名超限但尚未开始音频只拒绝本条，可以继续检查后续消息。

活动及待播事件的接收截止时间独立保留，最多当前＋5条；消息终结后释放。诊断环被突发新事件淘汰不会将待播语音预算重置。联系人查询同时最多一项；超时查询尚未退出时，后续查询降级正文，避免累积查询工作线程。
