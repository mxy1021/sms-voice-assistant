# V2.0 阶段2｜技术验证报告

验收准备度 **NOT_READY**。技术暂评 **CONDITIONAL**。Android 官方基线对无可见 Activity 和无合格 FGS 的后台焦点及实际播放有限制。目标 vivo 上能否在保留当前配置时完成整条播报由本轮实验决定。

## 1–2 Git 与阶段1.1

起始工作区干净，`git fetch origin` 成功。本地和远程的阶段1.1稳定分支均是 `72e1297acdf51a1a4f24d52ad92935003c28b04d`。与验收源码 `fbd183b71f1eec74a84e00976256044c579a367c` 比较只有文档变化。阶段2分支 `feature/v2-stage2-wechat-feasibility` 从关闭提交新建。主分支 `c76a5bf9`、阶段1分支 `210a749c`、阶段1.1关闭提交和 V1.0 annotated tag 对象 `0fef862b` 均与归档吻合。

## 3–25 验证与交付

| 序号 | 项目 | 结果 |
|---|---|---|
| 3 | NotificationListenerService | 已验证安装组件声明、保护和隔离。系统绑定的回调待测。 |
| 4 | 通知使用权 | 系统授权入口就绪；初始只读核查该组件未获授权。待用户在系统设置操作。 |
| 5 | 真实微信单聊 | 用户0安装了 `com.tencent.mm`；真实单聊 NOT_RUN。 |
| 6 | 真实微信群聊 | NOT_RUN。 |
| 7 | 实际可用字段 | 尚未取得真实微信数据。探针覆盖标题、正文、扩展正文、会话标题、最新消息及其发送者、群标记、类别、通知键/频道别名和发布时间。 |
| 8 | 缺失或受保护字段 | 预览关闭、屏幕锁定与正文缺失场景 NOT_RUN。探针记录缺失及可见性，真实可读范围待测。 |
| 9 | Home | NOT_RUN。 |
| 10 | 锁屏和关屏 | NOT_RUN。 |
| 11 | 后台静置 | NOT_RUN。 |
| 12 | 重启、撤权和恢复 | NOT_RUN。 |
| 13 | Android 17 后台限制 | 官方基础规则涵盖 targetSdk 34。无可见 Activity 且无合格 FGS 时，实际播放可能被静默阻止，同时焦点 API 会拒绝无有效生命周期的申请。系统绑定监听器提供通知事件，进程 importance 无法证实音频执行资格。vivo 上受阻层级还需用本轮 `AudioHardening`、实际焦点和听感核对。FGS 路径需要另行决策。[官方说明](https://developer.android.com/about/versions/17/changes/bg-audio) |
| 14 | 离线 TTS 与 PCM | 已接入既有链路；本轮固定句和真实通知音频实测 NOT_RUN。 |
| 15 | 指定耳机实际播报 | NOT_RUN。 |
| 16 | 音乐降低与恢复 | NOT_RUN。 |
| 17 | 手机扬声器安全 | NOT_RUN。当前实现沿用静音探路和逐次写入确认。动态切路由的回调与缓冲边界仍需要用户实测。 |
| 18 | SMS 回归 | 主源码、主 Manifest 和配置未改；13项受控设备回归通过。同签名覆盖保留原设置与耳机。组合真实 SMS/微信听验 NOT_RUN。 |
| 19 | 自动化 | JVM 108/108；vivo 设备测试 20/20（7项阶段2组件/解析、13项 SMS）。 |
| 20 | 构建与 Lint | Debug、unsigned Release、androidTest 均通过。Debug/Release Lint 各0错误、11警告。[矩阵](stage2-test-results.md) |
| 21 | 修改文件 | 见下表。 |
| 22 | APK 与 SHA256 | 见下表。 |
| 23 | Git | 原型与受控测试提交 `e3c75e0857965fa951e13bf8a7feab018feccbc8`，阶段2文档见本文件所属提交。[阶段2分支](https://github.com/mxy1021/sms-voice-assistant/tree/feature/v2-stage2-wechat-feasibility) |
| 24 | 当前未决 | 真实单聊/群聊字段、后台与重启、实际音频系统行为、听感与真实 SMS 回归待人工。T01～T13 保持 TBD。 |
| 25 | 阶段3条件 | 尚未满足。完成[人工操作单](stage2-test-plan.md)后按真实证据复核，阶段2关闭与后续阶段须用户明确确认。 |

| 修改范围 | 实际文件 |
|---|---|
| Debug 注册与入口 | `app/src/debug/AndroidManifest.xml`、`Stage0DiagnosticsActivity.kt`、`app/src/debug/res/values/wechat_probe.xml` |
| Debug 原型 | `app/src/debug/java/com/mxy1021/smsvoiceassistant/stage2/` 下的 `WeChatNotificationListener.kt`、`WeChatProbe.kt`、`WeChatProbeActivity.kt`、`WeChatFields.kt` |
| 自动化 | `WeChatProbeDeviceTest.kt`、`scripts/Verify-DebugSmsIsolation.py` |
| 阶段2文档 | 本目录的 `stage2-requirements.md`、`stage2-architecture.md`、`stage2-test-plan.md`、`stage2-test-results.md`、`stage2-report.md`、`stage2-acceptance.md` |

导出目录 `D:\Projects\SMSVoiceAssistant\app\build\stage2-evidence`。设备安装后的 APK 与 Debug 哈希一致。

| 文件 | SHA256 |
|---|---|
| `app-debug.apk` | `18b88993768a379e92d18b5bf728c6f4cc3cd8fdc4ed66411a340f4dfa10e2ec` |
| `app-release-unsigned.apk` | `bedc5804f8ce41a868bd0b2a3f767eac8ec8cacee713a5e443f36d3592e791b1` |
| `app-debug-androidTest.apk` | `bf9b38a6363fefaeb9dc86c7a4be6a353aee8221e547edc218422c4d27bab7eb` |
