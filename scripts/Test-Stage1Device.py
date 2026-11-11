"""Privacy-safe ADB checks. Never sends SMS, grants permissions, clears data or reboots.

python scripts/Test-Stage1Device.py --ui-smoke
UI smoke briefly changes the OTP filter and restores it in finally; keep phone idle.
--set-enabled / --set-boot-restore intentionally change the corresponding real UI switch.
With no flags this is read-only: it never opens the app. Entry counts refer only to
retained app logs, not guaranteed broadcast delivery or a replacement for real H07.
All retained evidence is under ignored app/build/stage1-device, with no device identities.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.mxy1021.smsvoiceassistant"
ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "app/build/stage1-device"


class Device:
    def __init__(self):
        listing = subprocess.run(["adb", "devices"], capture_output=True, check=True, timeout=10).stdout.decode()
        online = [line.split()[0] for line in listing.splitlines() if re.match(r"^\S+\s+device$", line)]
        if len(online) != 1:
            raise RuntimeError("Need exactly one authorized online device; offline emulators are ignored")
        self.serial = online[0]  # Never printed or retained.
        if self.shell("getprop", "ro.product.manufacturer").strip().lower() != "vivo" or self.shell("getprop", "ro.product.model").strip() != "V2405A":
            raise RuntimeError("Online device is not the expected vivo target")

    def run(self, *args):
        return subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, check=True, timeout=25).stdout

    def shell(self, *args):
        return self.run("shell", *args).decode("utf-8", errors="replace")

    def settings(self):
        root = ET.fromstring(self.run("shell", "run-as", PACKAGE, "cat", "shared_prefs/stage0.xml"))
        data = {node.get("name"): node.get("value", node.text) for node in root}
        keys = ["announcingEnabled", "otpOnlyEnabled", "otpSimplifyEnabled", "bootRestoreEnabled", "setupCompleted"]
        result = {key: data.get(key, "false" if key in ("announcingEnabled", "bootRestoreEnabled", "setupCompleted") else "true") == "true" for key in keys}
        result["targetPresent"] = bool(data.get("target"))
        result["recordedBootCount"] = int(data.get("stage1BootCount", -1))
        return result

    def process_present(self):
        result = subprocess.run(["adb", "-s", self.serial, "shell", "pidof", PACKAGE],
                                capture_output=True, timeout=10)
        if result.returncode == 1 and not result.stdout.strip() and not result.stderr.strip():
            return False  # pidof: no matching process. An ADB error is not absence evidence.
        if result.returncode != 0:
            raise RuntimeError("Cannot inspect application process")
        return bool(result.stdout.strip())

    def retained_entry_counts(self):
        counts = dict(applicationCreates=0, realSmsEntries=0, bootReceiverEvents=0, activityResumes=0)
        raw = self.run("logcat", "-d", "-s", "SVA_STAGE0:I", "*:S").decode("utf-8", errors="replace")
        for line in raw.splitlines():
            try:
                entry = json.loads(line[line.index("{"):])
                if entry.get("package") != PACKAGE:
                    continue
                field, value = entry.get("field"), entry.get("value")
                counts["applicationCreates"] += field == "Application.onCreate"
                counts["realSmsEntries"] += field == "source" and value == "REAL_SMS"
                counts["activityResumes"] += field == "MainActivity.onResume"
                counts["bootReceiverEvents"] += field == "状态" and value in (
                    "开机状态已恢复为启用，等待新短信", "开机状态为暂停")
            except (ValueError, TypeError):
                continue  # Never echo unparsed logs or any arbitrary diagnostic values.
        return counts

    def tree(self):
        self.shell("uiautomator", "dump", "/data/local/tmp/sva-stage1-ui.xml")
        root = ET.fromstring(self.run("shell", "cat", "/data/local/tmp/sva-stage1-ui.xml"))
        if not any(node.get("package") == PACKAGE for node in root.iter("node")):
            raise RuntimeError("App is not visible; unlock the phone and open the app")
        return root

    def top(self):
        root = self.tree()
        bounds = list(map(int, re.findall(r"\d+", next(root.iter("node")).get("bounds"))))
        width, height = bounds[2], bounds[3]
        for _ in range(5):
            self.shell("input", "swipe", str(width // 2), str(height // 4), str(width // 2), str(height * 4 // 5), "180")

    def node(self, label):
        for _ in range(8):
            root = self.tree()
            for node in root.iter("node"):
                if node.get("package") == PACKAGE and node.get("content-desc") == label and node.get("checkable") == "true":
                    return node
            for node in root.iter("node"):
                if node.get("package") == PACKAGE and (node.get("text") == label or node.get("content-desc") == label):
                    return node
            bounds = list(map(int, re.findall(r"\d+", next(root.iter("node")).get("bounds"))))
            self.shell("input", "swipe", str(bounds[2] // 2), str(bounds[3] * 3 // 4), str(bounds[2] // 2), str(bounds[3] // 3), "180")
        raise RuntimeError("Required UI control not found: " + label)

    def toggle(self, label, checked):
        node = self.node(label)
        # Compose exposes the switch separately with contentDescription and checked state.
        if node.get("checkable") != "true":
            root = self.tree()
            matches = [n for n in root.iter("node") if n.get("content-desc") == label and n.get("checkable") == "true"]
            if not matches:
                raise RuntimeError("Checkable switch not found: " + label)
            node = matches[0]
        if node.get("checked") == str(checked).lower():
            return
        if node.get("enabled") != "true":
            raise RuntimeError("Switch is disabled: " + label)
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.3)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ui-smoke", action="store_true")
    parser.add_argument("--set-enabled", choices=["true", "false"])
    parser.add_argument("--set-boot-restore", choices=["true", "false"])
    args = parser.parse_args()
    device = Device()
    result = {"api": int(device.shell("getprop", "ro.build.version.sdk").strip()),
              "release": device.shell("getprop", "ro.build.version.release").strip(),
              "systemBootCount": int(device.shell("settings", "get", "global", "boot_count").strip()),
              "bootCompleted": device.shell("getprop", "sys.boot_completed").strip() == "1",
              "settings": device.settings()}
    result["processPresentBeforeRead"] = device.process_present()
    result["smsReceiverResolvable"] = PACKAGE + "/.receiver.SmsReceiver" in device.shell(
        "cmd", "package", "query-receivers", "--brief", "--user", "0", "-a", "android.provider.Telephony.SMS_RECEIVED")
    result["bootReceiverResolvable"] = PACKAGE + "/.receiver.BootReceiver" in device.shell(
        "cmd", "package", "query-receivers", "--brief", "--user", "0", "-a", "android.intent.action.BOOT_COMPLETED")
    result["retainedAppLogEntryCounts"] = device.retained_entry_counts()
    if args.ui_smoke or args.set_enabled or args.set_boot_restore:
        device.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        time.sleep(0.5)
        device.top()
    if args.ui_smoke:
        original = device.settings()
        try:
            result["productionTitleVisible"] = device.node("SMS Voice Assistant") is not None
            device.toggle("仅播报验证码", False)
            simplify = device.node("精简验证码播报")
            result["simplifyDisabledInFullMode"] = simplify.get("enabled") == "false"
            result["simplifyValuePreserved"] = original["otpSimplifyEnabled"] == device.settings()["otpSimplifyEnabled"]
            device.toggle("仅播报验证码", True)
            result["simplifyRestoredAfterFilterEnabled"] = original["otpSimplifyEnabled"] == device.settings()["otpSimplifyEnabled"]
            assert all(result[k] for k in ["productionTitleVisible", "simplifyDisabledInFullMode", "simplifyValuePreserved", "simplifyRestoredAfterFilterEnabled"])
        finally:
            device.top()
            device.toggle("仅播报验证码", original["otpOnlyEnabled"])
    if args.set_enabled:
        device.top(); device.toggle("启用短信播报", args.set_enabled == "true")
    if args.set_boot_restore:
        device.top(); device.toggle("开机后恢复工作状态", args.set_boot_restore == "true")
    result["settingsAfter"] = device.settings()
    result["processPresentAfterRead"] = device.process_present()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    # This result contains only allowed aggregate status, never body, sender, MAC or serial.
    (EVIDENCE / "verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, ET.ParseError, AssertionError) as error:
        # Do not echo raw ADB output; it can contain identities.
        print("Device verification failed: " + (str(error) if isinstance(error, RuntimeError) else type(error).__name__))
        raise SystemExit(1)
