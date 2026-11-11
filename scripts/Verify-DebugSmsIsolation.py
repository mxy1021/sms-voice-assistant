"""Verify built APKs/manifests, not just source-set naming. No device access or private data."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED = {"android.permission." + name for name in ("RECEIVE_SMS", "BLUETOOTH_CONNECT", "RECEIVE_BOOT_COMPLETED")}
MARKERS = (b"DebugSmsInjectionTest", b"DebugSmsFixtures", b"SVA_DEBUG_RESULT", b"SVA_DEBUG_READY",
           "【测试平台】验证码：".encode("utf-8"), "这是一条用于检查完整语音播放时间上限".encode("utf-8"))
FAULT_MARKERS = (b"ControlledSpeechDependencies", b"ObservedFailureTts", b"ControlledFailure")


def manifest(path):
    return ET.parse(ROOT / path).getroot()


def dex(path):
    with zipfile.ZipFile(path) as archive:
        return b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))


def apk(variant):
    directory = ROOT / "app/build/outputs/apk" / variant
    metadata = json.loads((directory / "output-metadata.json").read_text(encoding="utf-8"))
    assert len(metadata["elements"]) == 1, "Expected one built APK"
    return directory / metadata["elements"][0]["outputFile"]


def main():
    for variant in ("debug", "release"):
        root = manifest(f"app/build/intermediates/merged_manifests/{variant}/process{variant.title()}Manifest/AndroidManifest.xml")
        assert {n.get(ANDROID + "name") for n in root.findall("uses-permission")} == EXPECTED
        assert not root.findall(".//service")
        assert not root.findall("instrumentation")
        components = [n.get(ANDROID + "name", "") for n in root.findall(".//application/*")]
        assert not any("DebugSms" in name or "Injection" in name or "Simulator" in name for name in components)
        receiver = next(n for n in root.findall(".//receiver") if n.get(ANDROID + "name", "").endswith(".SmsReceiver"))
        assert receiver.get(ANDROID + "exported") == "true"
        assert receiver.get(ANDROID + "permission") == "android.permission.BROADCAST_SMS"
        assert [n.get(ANDROID + "name") for n in receiver.findall("intent-filter/action")] == ["android.provider.Telephony.SMS_RECEIVED"]
        data = dex(apk(variant))
        assert all(marker not in data for marker in MARKERS), f"Test code leaked into {variant}"
        assert all(marker not in data for marker in FAULT_MARKERS), f"Failure substitutes leaked into {variant}"
        if variant == "release":
            assert not any("Stage0DiagnosticsActivity" in name for name in components)
    test = manifest("app/build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml")
    assert len(test.findall("instrumentation")) == 1
    assert not test.findall("uses-permission")
    assert not any(test.findall(".//" + tag) for tag in ("activity", "receiver", "service", "provider"))
    data = dex(apk("androidTest/debug"))
    assert all(marker in data for marker in MARKERS)
    assert all(marker in data for marker in FAULT_MARKERS)
    print(json.dumps({"status": "PASS", "debugAndReleasePermissions": sorted(EXPECTED),
                      "services": 0, "appInstrumentationEntries": 0, "testApkExportedComponents": 0,
                      "testOnlyProtocolAndFixtures": True, "protectedRealSmsReceiver": True}))


if __name__ == "__main__":
    main()
