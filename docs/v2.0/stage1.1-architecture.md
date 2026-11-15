# V2.0 阶段1.1架构

状态：CLOSED / ACCEPTED（2026-11-16用户正式确认）；架构及生产代码本次不变。沿用SmsReceiver → SmsAssembly / ReplayGuard → SmsCoordinator → SpeechFormatter → SpeechSession；不新增接收架构或常驻对象。

关闭依据与产物核验见[正式关闭报告](stage1.1-closure-report.md)，历史实验和未验证边界原样保留，阶段2未启动。

## 文本与联系人

SmsSpeechRules为唯一四组合决策层。OtpExtractor仅处理上下文明确的码，保存当前内存中的原文span；SpeechFormatter只替换该span。SmsTextCleaner仅修改朗读副本。最终字符检查包含联系人及码拼读的长度，PCM预算仍是独立门禁。

全文模式才查询联系人。ContactMatchPolicy对中国手机号统一+86/0086/空格/横线，并支持明确国际E.164；不做尾号模糊匹配，不匹配短号、字母ID。PhoneLookup查询只投影NUMBER和DISPLAY_NAME，逐行精确复核；超过8行、同号异名、名字为号码或包含长号码时保守降级。

ContactNameResolver仅本事件创建工作线程与CancellationSignal，250ms到期放弃名称并返回“未知联系人”＋正文；查询前、逐行、返回后检查READ_CONTACTS。游标use关闭、超时取消查询、不缓存。取消Provider调用由临时线程执行以避免Binder阻塞主线程；平台查询/取消能否及时退出仍需真机压力验证，250ms是应用放弃结果期限，不是对第三方Provider内部执行时间的保证。旧permit或预算失效时不启动语音。

最终发送前统一用SpeechFormatter.forPlayback复核可靠姓名；无可靠姓名（未授权/拒绝/撤权/无匹配/超时/失败/短号或字母ID/不安全名称）则固定“未知联系人”，不朗读号码。choose用于正文预检，不能在查询前先预留较长的未知前缀而误拒绝稍后可加入短姓名的文本。最终姓名/未知提示和转换正文共同计入80 UTF-16，超限整条拒绝；空正文不会仅播未知提示，精简验证码仍无此前缀。

## 音量与资源

已替换原SessionLoudness/LoudnessEnhancer为SpeechPcmProcessor，仅处理当前会话的完整16位PCM：能量门控RMS→有界基础标准化→0/3/6/9dB用户增益→声道联动平滑限制→4倍/32 tap插值峰值估计与必要整体衰减。反向5ms时间常数预判、前向50ms释放；不是逐样本硬削顶。满幅前量化，不改变样本数、采样率、声道或内容时长；最终峰值估计守门−1.2dBFS，样本限制−2dBFS。实际输出不保证每档RMS加3dB。

在原有会话worker内处理，不新增长期线程；输入先验证5000ms/1MiB及剩余预算，处理后再做原时间、许可、焦点与实际蓝牙门禁。处理失败整条拒绝；循环响应取消/中断，私有缓冲清零，晚回调不创建轨道或恢复旧语音。现有统一end释放TTS/轨道/焦点，不叠加不可观测的硬件增强器；系统媒体音量无写调用。参数由三轮本机测量/用户听感选定，见专项审计。

显示模式追加在SettingsRepository，沿用原偏好键；新增displayMode键默认SYSTEM，更新不增加语音generation。MaterialTheme响应设置和系统模式，SideEffect只更新系统栏图标对比。短信、队列、广播生命周期不依赖Activity或主题。

## 时间测量

T0 Application.onCreate入口、T1 onReceive入口、T2 goAsync调用/返回、T3解析完成、T4 TTS初始化完成、T5/T6 PCM合成、T7焦点申请返回（须同时检查授予结果）、T8实际蓝牙路由确认、T9首次非静音PCM写入、T10播放完成标记、T11音频资源释放完成、T12实际PendingResult.finish调用/返回。

elapsedRealtime计时与回调到达时间分离。首次写入不是耳机实际声学起音，AudioTrack标记不是人工听感。旧版finish日志是在finish之后记录，不能伪称精确调用时间。系统分发精确起点若不可观测则null；PDU时间不是分发时间，Stage0Log初始化时间也不是Application入口。

广播预算包含排队、查询、TTS、路由、播放和清理。Android主线程暂停/进程冻结时Handler不能保证物理时刻执行，恢复时elapsedRealtime门禁必须拒绝过期PCM；没有设备证据前不能声称严格finish上界或无ANR。

异步播放初始化统一经play入口捕获Exception并调用原end清理：延迟窗口后的路由回调不能依赖已经返回的onDone外层try/catch。受控异常在焦点已申请、测试MediaSession激活后触发，要求零语音写入并释放焦点/会话；不新增生产MediaSession。

## 已确认队列A

用户本轮确认：最多5条待播，满时拒绝新消息，不覆盖原待播。当前解析/联系人查询/音频会话不抢占；待播OTP优先，同级按接收顺序FIFO。正文仅在内存，联系人查询仅轮到本消息才开始，避免为每条等待消息创建查询线程。每条的接收时间和permit不重置，排队计入自身8000ms期限；等待到期立即终结本条，不续播。正常完成才调度下一条；关闭/配置变化/断连/撤权/永久焦点丢失等安全终止清空待播，恢复后只接收新消息。字符/姓名超限但尚未开始音频只拒绝本条，可以继续检查后续消息。

活动及待播事件的接收截止时间独立保留，最多当前＋5条；消息终结后释放。诊断环被突发新事件淘汰不会将待播语音预算重置。联系人查询同时最多一项；超时查询尚未退出时，后续查询降级正文，避免累积查询工作线程。

平台投递边界：核查时[AOSP主线InboundSmsHandler的WaitingState](https://android.googlesource.com/platform/frameworks/opt/telephony/+/master/src/java/com/android/internal/telephony/InboundSmsHandler.java)会将下一次EVENT_BROADCAST_SMS阶段延后至当前广播完成。它能解释串行投递的可能性，但不是vivo固件实际实现的证明。本机两轮后一条onReceive分别在前条finish后145/123ms才发生，均没有应用内待播记录。队列只能排序已经进入APP的消息，不能排序尚未由系统交付的SMS；不得用受控协调器测试承诺真实SMS一定能重排，也不能用该机制解释未观测普通短信的原因。

最终名称防护补充：联系人显示名中累计含7位及以上数字时，不论是否用点号、空格或特殊空白分隔，保守降级正文，避免把备注内夹带的号码作为姓名读出；相应负例纳入同一93项JVM测试。

## 媒体按键技术核查

[平台MediaSession.Callback](https://developer.android.com/reference/android/media/session/MediaSession.Callback)说明回调可来自系统最高优先会话的按键，也可由MediaController发送；因此“收到KeyEvent”本身不证明目标耳机来源。[KeyEvent.getDeviceId](https://developer.android.com/reference/android/view/KeyEvent#getDeviceId())的0可表示非物理设备，其余值为运行时任意编号；[InputDevice描述符](https://developer.android.com/reference/android/view/InputDevice#getDescriptor())也可能在不可区分的相同设备间重复。依据这些公开接口，当前探针不能把媒体按键直接认证为已保存蓝牙MAC，这是能力边界推断，不是本机KeyEvent失败记录。

探针只记录代码/动作/重复次数及是否有输入设备/是否外部输入等脱敏标量，不记录设备ID、描述符、名称或地址。本机实际双击观察到127的DOWN/UP，inputDevicePresent=true、externalInput=false；这不能认证蓝牙来源。音乐及锁屏音乐两轮中，临时映射127的UP只取消本APP，用户确认语音立即停止、音乐继续不跳曲，MediaSession均释放。无音乐轮只记录按键，不声称停止验证。Home、其他按键/其他设备影响仍未验证，未满足全部条件，不正式实现。
