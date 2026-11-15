# V2.0 阶段1.1需求

## 阶段1.1当前正式增量（2026-11-15～16）

阶段1已验收于210a749c075dc1a065f9c1386dcaa2052322149c；阶段1.1最终响度/显示模式复核为READY_FOR_USER_ACCEPTANCE，尚未由用户正式关闭。阶段2未启动。下方阶段0形成的完整路线图继续作为历史规划，不能把通知监听等后续目标误读为本轮实现授权。

- F5正式用户档位为 **0、+3、+6、+9dB** 离散滑动条，默认0；用户在七档固定语音试验后明确移除12/15/18dB，取代本轮最初七档要求。0/3/6旧配置保持；候选版高档配置读取时降至9，非法值回0；不改变耳机、启用、开机恢复或短信规则配置。
- 0dB表示额外用户增益为0，仍有基础响度处理。完整PCM按20ms能量窗排除低于−50dBFS的窗，本轮验证选定有效RMS目标−12dBFS、基础提升最多9dB；随后用户增益与平滑峰值保护，4倍插值峰值估计兜底。目标不是耳机SPL或LUFS保证；必须根据测量与听感放行，不允许整数满幅削波或硬削顶冒充Limiter。
- F2新增全文降级提示：无法可靠取得联系人时统一读“未知联系人，正文”，不读号码、不把权限缺失称为陌生人；精简验证码格式不变。前缀计入80 UTF-16，最终超限整条拒绝。此后续用户需求取代历史“仅正文”降级规则，历史95/121记录不改写。
- 普通（白色）、深色（黑色）、跟随系统三种显示模式，默认跟随系统；立即更新并持久化。显示设置不作废音频许可、不取消当前SMS。保持Compose/Material3，无后台重构。
- 8000ms为APP内部预算，正常可控路径应包含处理、清理及finish；保留5000ms/1MiB/80 UTF-16/1800ms及700ms既有清理预留。不承诺系统冻结/杀进程下绝对物理终结≤8000ms，历史8014ms失败不改写。
- 队列最多5条待播、当前不抢占、验证码优先、同级FIFO、满拒新、保留原接收时刻及8秒期限。仅排序已进入APP的消息；117～119入口串行，没有真实A→C→B证据。用户已接受该适用范围，不提前finish或增加Service。
- URL自动化PASS；真实SMS受运营商拦截为MANUAL_BLOCKED，用户接受，不再大量发送或作为强制阻塞。SMS-000106仍NOT_MEASURABLE，接受权限变更杀进程边界，保留活进程受控拒权测试，不补写历史finish。
- 原0dB媒体40→100首轮失败保留；用户后来明确是本人手调，状态CLOSED_BY_USER_CLARIFICATION，关闭APP代码缺陷归因，保留复测PASS，不追加专项排查。
- 8.5秒FAILED_NOT_ADOPTED；长文本/字符扩容、5.1/5.2秒及耳机双击正式功能DEFERRED_OUT_OF_SCOPE。实验分支冻结。不新增权限、Service、FGS、WorkManager、轮询或常驻媒体对象；版本/SDK/签名/包身份及严格蓝牙路由不变。

具体证据见[响度审计](audio-loudness-audit.md)、[本轮结果](stage1.1-test-results.md)及[验收条件](stage1.1-acceptance.md)。

日期：2026-11-15（Asia/Shanghai）。阶段1验收基线：`210a749c075dc1a065f9c1386dcaa2052322149c`。本轮尚未正式验收，不改变V1.0、阶段0、阶段1结论或失败历史。

## 安全与工程门禁

- 仅受保护的真实SMS_RECEIVED触发，goAsync有界处理；不增加Service、FGS、WorkManager、轮询、保活、通知监听或AccessibilityService。
- 不读历史短信，不联网、不上传；不保存短信、号码、联系人、音频。脱敏计时/状态与内容分离。
- 保留指定蓝牙身份和实际路由双门禁、不回退扬声器、离线TTS、MAY_DUCK、临时焦点暂停/恢复、永久丢失终止、配置/权限/断连取消与旧语音不复活。
- 版本仍为3 / 2.0.0-stage1；包名、namespace、SDK、签名不变。仅在独立feature及实验分支交付，不合并稳定基线、不强推、不改V1标签。

## 本轮范围

| ID | 要求 | 验收边界 |
|---|---|---|
| F1 | 上下文识别4/5/6/8位及4～8位混合码，前导零；确定的验证码逐位转换 | 普通金额/日期/电话/订单/编号不转换；歧义不猜测 |
| F2 | 全文联系人名称或“未知联系人”＋清理后正文 | READ_CONTACTS用户主动授权；PhoneLookup按当前号码查询；短号/字母发送者/模糊或冲突结果用“未知联系人”前缀；不读发信人号码 |
| F2b | 独立链接清理 | 去除http(s)/www/常见裸域名及路径参数；保留验证码、取件码、日期、金额、订单和地址；不改原短信 |
| F3 | 两开关独立持久化 | 见四组合；沿用旧键与耳机/开机/运行设置 |
| F4 | 正式保留8秒/5秒/80 UTF-16 | 8.5秒FAILED_NOT_ADOPTED，扩容DEFERRED_OUT_OF_SCOPE |
| F5 | 0/+3/+6/+9dB离散滑动条＋经测量的基础响度处理 | 单一PCM增益链＋平滑峰值保护；取代原LoudnessEnhancer，避免重复增益；不改系统或播放器音量 |
| UI | 普通/深色/跟随系统 | 默认跟随系统、立即生效/持久化/系统响应；不影响短信或音频 |
| F6 | 历史耳机双击探针 | 正式功能DEFERRED_OUT_OF_SCOPE，不接入生产SMS或正式UI |
| Q1 | 非抢占优先队列A | 本轮用户追加纳入并确认5条待播、满时拒绝新消息；当前消息不抢占、验证码优先、同级接收FIFO；每条等待计入自身广播预算 |

| 仅播报验证码 | 精简验证码播报 | 验证码 | 普通短信 |
|---|---|---|---|
| 开 | 开 | 精简 | 忽略 |
| 开 | 关 | 联系人或未知联系人＋清理全文 | 忽略 |
| 关 | 开 | 精简 | 联系人或未知联系人＋清理全文 |
| 关 | 关 | 联系人或未知联系人＋清理全文 | 联系人或未知联系人＋清理全文 |

全文最终朗读文本（含联系人和码转换）≤80 UTF-16；完整PCM≤5000ms且≤1MiB，路由及清理余量700ms，首次焦点窗口1800ms。超限整条拒绝；安全取消可能中断已开始的语音，不因“完整”要求延迟安全终止。

官方来源：[BroadcastReceiver](https://developer.android.com/reference/android/content/BroadcastReceiver)、[广播ANR](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)、[PhoneLookup](https://developer.android.com/reference/android/provider/ContactsContract.PhoneLookup)、[LoudnessEnhancer](https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer)、[MediaSession](https://developer.android.com/reference/android/media/session/MediaSession)。goAsync不取消超时；系统计时含冷启动，不根据API37推断本机广播优先级。
