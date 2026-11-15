# V2.0 阶段1.1开发计划

1. 只读检查：已完成。git status干净；fetch成功；本地/远程feature/v2-message-notifications及验收基线均为210a749。原配置3/2.0.0-stage1，SDK33/34/36。建立feature/v2-sms-speech-experience，不触碰main和标签。
2. 实现F1/F2/F3/F5：保留正式8秒、5秒、1MiB、80 UTF-16、1800ms窗口。分批测试构建，旧测试断言按本轮码拼读及清理规则更新，不修改历史验收表。
3. 队列：只读检查发现基线尚无队列，用户已明确纳入本轮；用户确认5条待播、满时拒绝新消息，现接入非抢占优先队列，所有等待受各自接收期限限制，安全取消清空旧消息。
4. 预算：先保留原已安装版本8秒真实日志；补充精确测点后收集新8秒；独立experiment/sms-budget-8500ms只改8000→8500。相同样本/场景对比，未通过不采用、不进入5.1/5.2秒播放扩容。
5. 双击：仅测试APK内临时MediaSession验证目标耳机KeyEvent，不正式接入；没有音乐/音乐/Home/锁屏及资源释放均需证据。不可可靠分辨来源或会影响音乐即判不满足，不添加UI或常驻监听。
6. 验证：JVM、设备instrumentation、Debug/Release/androidTest、Lint、合并Manifest与测试隔离；实短信和人工听感独立统计。升级使用同签名install -r，不卸载或清数据，不自动授予联系人。
7. 文档与Git：七份stage1.1独立文档；提交前核对diff和staged清单，排除APK/日志/私有信息；推送feature，核实远端SHA。正式验收等用户真实SMS及后台听感。
