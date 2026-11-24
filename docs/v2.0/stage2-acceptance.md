# V2.0 阶段2｜验收状态

日期 2026-11-24（Asia/Shanghai）。**NOT_READY**，技术暂评 **CONDITIONAL**，等待[目标机操作单](stage2-test-plan.md)和[验证矩阵](stage2-test-results.md)的真实证据。

| 强制放行条件 | 状态 |
|---|---|
| 真实微信通知接收及权限行为 | NOT_RUN |
| 真实单聊、群聊与可见字段 | NOT_RUN |
| Home、锁屏、静置与重启后及时收到 | NOT_RUN |
| Android 17 后台音频是否合规可用 | NOT_RUN |
| 完整指定耳机播报，音乐恢复且无手机语音 | NOT_RUN |
| 断连、撤权等安全取消及无旧语音恢复 | NOT_RUN |
| 同一交付版本的 SMS 真机回归 | NOT_RUN |

技术等级 PASS 需要上述强制真机证据。CONDITIONAL 应列出目标机、配置和场景条件。若确认现有架构下无法达到强制标准，定级 BLOCKED 并给出实际层级。阶段2关闭以用户正式确认为准，后续阶段另行授权。
