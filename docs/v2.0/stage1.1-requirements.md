# V2.0 阶段1.1需求

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
| F2 | 全文可选联系人名称＋清理后正文 | READ_CONTACTS用户主动授权；PhoneLookup按当前号码查询；短号/字母发送者/模糊或冲突结果仅正文；不读发信人号码 |
| F2b | 独立链接清理 | 去除http(s)/www/常见裸域名及路径参数；保留验证码、取件码、日期、金额、订单和地址；不改原短信 |
| F3 | 两开关独立持久化 | 见四组合；沿用旧键与耳机/开机/运行设置 |
| F4 | 8秒基线→8.5秒单变量→通过后研究5.1/5.2秒PCM | 未有真实生命周期、后台、冷启动、异常完整证据前不采用8.5秒，不扩容正式PCM/字符数 |
| F5 | 0/+3/+6dB，仅当前AudioTrack的LoudnessEnhancer | 默认0；失败原始音频；不改系统或播放器音量；会话终结释放，听感待真机 |
| F6 | 耳机双击可行性 | 只允许临时MediaSession；必须实际KeyEvent且音乐不暂停/不跳曲；未通过不增加正式UI |
| Q1 | 非抢占优先队列A | 本轮用户追加纳入并确认5条待播、满时拒绝新消息；当前消息不抢占、验证码优先、同级接收FIFO；每条等待计入自身广播预算 |

| 仅播报验证码 | 精简验证码播报 | 验证码 | 普通短信 |
|---|---|---|---|
| 开 | 开 | 精简 | 忽略 |
| 开 | 关 | 联系人＋清理全文 | 忽略 |
| 关 | 开 | 精简 | 联系人＋清理全文 |
| 关 | 关 | 联系人＋清理全文 | 联系人＋清理全文 |

全文最终朗读文本（含联系人和码转换）≤80 UTF-16；完整PCM≤5000ms且≤1MiB，路由及清理余量700ms，首次焦点窗口1800ms。超限整条拒绝；安全取消可能中断已开始的语音，不因“完整”要求延迟安全终止。

官方来源：[BroadcastReceiver](https://developer.android.com/reference/android/content/BroadcastReceiver)、[广播ANR](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)、[PhoneLookup](https://developer.android.com/reference/android/provider/ContactsContract.PhoneLookup)、[LoudnessEnhancer](https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer)、[MediaSession](https://developer.android.com/reference/android/media/session/MediaSession)。goAsync不取消超时；系统计时含冷启动，不根据API37推断本机广播优先级。
