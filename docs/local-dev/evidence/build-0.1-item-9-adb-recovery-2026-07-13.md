# Build 0.1 item 9 ADB recovery evidence — 2026-07-13

Device:

- Serial: `KF5OF2126040802193`
- Model: `BWC`
- Android: `12`
- Package: `com.dvid.dcam`, `versionName=1.0`, `versionCode=1`

Commands run:

- `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- `adb shell input keyevent 79`
- `adb shell am force-stop com.dvid.dcam`
- `adb reboot`
- `adb wait-for-device`

Results:

| Scenario | Result | Artifact | Duration | Size |
|---|---|---|---:|---:|
| Force-stop during recording | Recovered from `Temp` into `Media/Video/yyyy-MM-dd` on relaunch | `DCAM_KF5OF2126040802193_000000_20260713_111640.mp4` | 00:00:11 | 29,686,142 bytes |
| Reboot during recording | Preserved/recovered into `Media/Video/yyyy-MM-dd`; `Temp` empty after boot | `DCAM_KF5OF2126040802193_000000_20260713_111748.mp4` | 00:00:11 | 29,898,223 bytes |

Local pulled samples:

- `build/item9-force-stop.mp4`
- `build/item9-reboot.mp4`

Remaining proof:

- Physical removable-storage remount test.
- Physical power-cut test.
- Repetition across multiple runs.

Recorded: 2026-07-13 11:19:18 +07:00
