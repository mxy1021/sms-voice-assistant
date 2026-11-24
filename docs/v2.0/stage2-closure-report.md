# SMS Voice Assistant V2.0 阶段2正式关闭报告

2026-11-25（Asia/Shanghai），用户正式验收「微信通知技术验证」。阶段状态为 **CLOSED / ACCEPTED**，技术可行性等级为 **CONDITIONAL**。用户接受 N01～N24 在各自记录条件下的 PASS，以及未验证边界。交付范围为 Debug 技术验证原型。微信通知正式产品功能属于待单独授权的阶段3。[验收表](stage2-acceptance.md) · [实测矩阵](stage2-test-results.md)

## 接受范围

目标为 vivo X200 Pro（V2405A）、OriginOS 7、Android 17/API 37，应用 targetSdk 34。实测适用条件包括设备原有系统设置、有效通知使用权、会话开启微信通知、指定蓝牙耳机可用及选文符合安全预算。测得真实单聊与群聊接收、背景及重启后监听、授权撤销和恢复、指定耳机播报、音乐降低与恢复、断连取消以及 SMS 并行回归。系统实际提供的普通字段及结构化缺失都按样本记录。

关屏锁定的通知到达后，申请焦点时屏幕已点亮、设备仍锁定、应用界面不可见。断 USB 静置样本在恢复供电前完成播报。Android 17 后台音频限制涵盖现有 targetSdk，目标机结果限于已记录的生命周期与配置。查无匹配 AudioHardening 记录保持原有证据口径。[技术报告](stage2-report.md) · [Android 17 后台音频说明](https://developer.android.com/about/versions/17/changes/bg-audio)

| 未验证真机事项 | 当前证据状态 | 后续归属 |
|---|---|---|
| 持续熄屏至播放完成 | NOT_RUN | 阶段3 P0 技术风险 |
| 播放中撤销通知使用权 | NOT_RUN | 阶段3安全回归 |
| 实际播放中真实永久 AudioFocus 丢失 | NOT_RUN | 阶段3安全回归 |

实测矩阵保留群聊通知关闭的首次尝试、静置的一次性等待超时、PCM 超过 5 秒的整条拒绝、首次断连听感与轨道标记差异，以及对应复测。此次只做文档归档和只读核验。阶段3待另行授权，T01～T13 继续 TBD。[正式开发计划](development-plan.md)

## Git 与交付产物

关闭分支为 `feature/v2-stage2-wechat-feasibility`，起点为阶段1.1关闭提交 `72e1297acdf51a1a4f24d52ad92935003c28b04d`。原型和测试源码提交为 `e3c75e0857965fa951e13bf8a7feab018feccbc8`，最终实测证据提交为 `8ddeea21e1f92912f8b79b1530857100306f04fe`。阶段2关闭归档为本文件所属提交。关闭时工作区、暂存区和远程分支经过核对。

| 受保护引用 | 关闭前实读 |
|---|---|
| `main` | `c76a5bf9d19bade0174d4dec3bc8e3eb06e7559a` |
| `feature/v2-message-notifications` | `210a749c075dc1a065f9c1386dcaa2052322149c` |
| `feature/v2-sms-speech-experience` | `72e1297acdf51a1a4f24d52ad92935003c28b04d` |
| `v1.0-stage1-accepted` 标签对象 | `0fef862b67b1fcb23799b504bc33b449aecdada7` |
| `v1.0-stage1-accepted` 指向提交 | `c76a5bf9d19bade0174d4dec3bc8e3eb06e7559a` |

在本机 `D:\Projects\SMSVoiceAssistant\app\build\stage2-evidence` 重新计算三项已交付 APK 的 SHA256，结果与技术报告一致。

| APK | SHA256 |
|---|---|
| `app-debug.apk` | `18b88993768a379e92d18b5bf728c6f4cc3cd8fdc4ed66411a340f4dfa10e2ec` |
| `app-release-unsigned.apk` | `bedc5804f8ce41a868bd0b2a3f767eac8ec8cacee713a5e443f36d3592e791b1` |
| `app-debug-androidTest.apk` | `bf9b38a6363fefaeb9dc86c7a4be6a353aee8221e547edc218422c4d27bab7eb` |

构建、108 项 JVM、20 项设备自动化、Lint 及 APK 隔离统计算作原交付证据。此次关闭未重新构建、安装或运行测试。Release 产物保持 unsigned，Debug 原型交付按已验收的技术验证范围归档。
