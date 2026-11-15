"""Read only the app's structured diagnostics. Never exports raw logcat, senders or SMS.

No broadcast injection, device permission changes, app start, installation or log clearing.
Each report explicitly distinguishes real PendingResult measurements from simulations.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--since-epoch-ms", type=int, default=0)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,60}", args.label):
        raise SystemExit("Invalid evidence label")
    devices = subprocess.run(["adb", "devices"], capture_output=True, check=True, timeout=10).stdout.decode()
    online = [s.split()[0] for s in devices.splitlines() if re.match(r"^\S+\s+device$", s)]
    if len(online) != 1:
        raise SystemExit("Exactly one authorized device required")
    raw = subprocess.run(["adb", "-s", online[0], "logcat", "-d", "-v", "raw", "-s", "SVA_STAGE0:I", "*:S"],
                         capture_output=True, check=True, timeout=25).stdout.decode("utf-8", errors="replace")
    entries = []
    for line in raw.splitlines():
        try:
            item = json.loads(line)
            if item.get("schema") == 1 and item.get("package") == "com.mxy1021.smsvoiceassistant":
                entries.append(item)
        except (ValueError, TypeError):
            pass
    events = []
    for source in entries:
        ident = source.get("id", "")
        if source.get("field") != "source" or source.get("value") != "REAL_SMS" or not re.fullmatch(r"SMS-[0-9]+", ident):
            continue
        if source.get("epochMs", 0) < args.since_epoch_ms:
            continue
        rows = [e for e in entries if e.get("id") == ident]
        points = {e["field"]: e["pointElapsedMs"] for e in rows if e.get("kind") == "timing" and isinstance(e.get("pointElapsedMs"), int)}
        created = [e["pointElapsedMs"] for e in entries if e.get("processRun") == source.get("processRun") and
                   e.get("field") == "Application.onCreate入口" and isinstance(e.get("pointElapsedMs"), int)]
        if created:
            points["Application.onCreate入口"] = created[0]
        flags = [e for e in entries if e.get("field") == "广播foregroundFlag" and
                 e.get("processRun") == source.get("processRun") and 0 <= source.get("elapsedMs", 0) - e.get("elapsedMs", 0) < 1000]
        def delta(end, start):
            return points[end] - points[start] if end in points and start in points else None
        def integer(field):
            values = [e.get("value") for e in rows if e.get("field") == field]
            return int(values[-1]) if values and re.fullmatch(r"[0-9]+", str(values[-1])) else None
        events.append(dict(id=ident, source="REAL_SMS", pointsElapsedMs=points,
            foregroundBroadcast=flags[-1]["value"] == "true" if flags else None,
            systemDispatchElapsedMs=None, systemDispatchToFinishMs=None,
            systemDispatchObservation="UNAVAILABLE: no exact system_server dispatch timestamp",
            receiverToFinishMs=delta("PendingResult.finish返回", "BroadcastReceiver.onReceive"),
            legacyPostFinishMarkerMs=delta("PendingResult.finish", "BroadcastReceiver.onReceive"),
            applicationToFinishMs=delta("PendingResult.finish返回", "Application.onCreate入口"),
            processAgeAtReceiveMs=delta("BroadcastReceiver.onReceive", "Application.onCreate入口"),
            coldStartAssociation="UNVERIFIED: process lifetime is not cold-start duration",
            receiverToFirstWriteMs=delta("音频实际写入", "BroadcastReceiver.onReceive"),
            ttsInitMs=delta("TTS初始化完成", "TTS初始化开始"),
            synthesisMs=delta("PCM合成完成", "PCM合成开始"),
            firstWriteToPlaybackMarkerMs=delta("播放完成", "音频实际写入"),
            playbackToAudioReleaseMs=delta("音频资源释放完成", "播放完成"),
            pcmDurationMs=integer("PCM时长ms"), pcmBytes=integer("完整PCM字节数"),
            writtenBytes=integer("PCM写入字节数")))
    report = dict(label=args.label, provenance="READ_ONLY_RETAINED_REAL_SMS_LOGS", events=events,
                  note="First write is not Bluetooth acoustic onset. Legacy finish marker is after finish, not the exact call.")
    destination = Path(__file__).resolve().parents[1] / "app/build/stage1.1-device" / (args.label + ".json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise SystemExit("Evidence label already exists; use a new label")
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
