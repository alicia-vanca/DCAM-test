# Build 0.1 audio device tests - 2026-07-14

## Target

Confluence applicability note: removable/AUTO results below are useful hardware evidence but are not Build 0.1 acceptance. Active Build 0.1 profile requires internal storage only; retain these results for later storage-profile review.

- ADB serial: `BODYCAMERA4HHITK`
- Model: `Android BodyCamera`
- Android: `12` / API `31`
- App: `com.dvid.dcam`, version `1.0`, version code `1`
- Removable volume: `6162-6433`
- Audio hardware command: `KEYCODE_F3` (`133`)

## Results

| Test | Result | Evidence |
|---|---|---|
| Internal normal finalization | PASS | `DCAM_KF5KW2124062200167_000000_20260714_155423.aac` staged under internal `Temp`, foreground service reported `isForeground=true`, then finalized under internal `Media/Audio/yyyy-MM-dd` at `49,600` bytes; internal `Temp` was empty. |
| AUTO removable normal finalization | PASS | `DCAM_KF5KW2124062200167_000000_20260714_155304.aac` and `DCAM_KF5KW2124062200167_000000_20260714_155337.aac` finalized under `/storage/6162-6433/.../Media/Audio/yyyy-MM-dd`; removable `Temp` was empty. |
| Screen-off continuation | PASS | `DCAM_KF5KW2124062200167_000000_20260714_155505.aac` grew from `31,000` to `92,225` bytes during ten seconds screen-off; `RecordingForegroundService` remained foreground. |
| Configuration rotation continuation | PASS | Same staged AAC grew from `92,225` to `156,550` bytes while rotating through `user_rotation=1` and back to `0`, then finalized at `157,325` bytes; removable `Temp` was empty. |
| Force-stop recovery | PASS | `DCAM_KF5KW2124062200167_000000_20260714_155635.aac` remained staged at `62,000` bytes after `am force-stop`, then relaunch reported `recovered=1, preserved=0, duplicates=0` and moved it to removable `Media/Audio/yyyy-MM-dd`; `Temp` was empty. |
| Reboot recovery | PASS with vendor remount race | `DCAM_KF5KW2124062200167_000000_20260714_155707.aac` was staged at about `62,000` bytes before reboot. Boot exposed SD as `shared`, so initial recovery reported `recovered=0`. After disabling `com.bodycamera.nettysocket`, changing USB functions through `none` back to `adb`, and waiting for volume `6162-6433` to become `mounted`, same `62,000` byte AAC appeared under removable `Media/Audio/yyyy-MM-dd`; `Temp` was empty. |
| Physical power-cut recovery | HISTORICAL FAIL; FIXED 2026-07-15 | July 14 runs produced two zero-byte preserved artifacts. After durability sync and configured external-Temp staging fixes, July 15 retest passed with `recovered=1, preserved=0, duplicates=0`; see retest section below. |

## Observations

- Audio capture used `Temp` during active recording and published only after normal stop or recovery.
- Screen-off and Activity recreation did not stop `MediaRecorder`; staged file size continued increasing.
- Reboot repeats firmware USB mass-storage race already recorded for video. Evaluate recovery only after removable volume and MediaProvider settle.
- No pre-existing media was deleted or modified.
- July 14 power-cut failure occurred below recovery publication: active AAC bytes were not durable on SD. July 15 configured-external-Temp retest passed after durability sync changes.

## Remaining action

Completed on 2026-07-15. Exact staged AAC remained non-zero after boot, recovery reported `recovered=1`, final file existed under `Media/Audio/yyyy-MM-dd`, and `Temp` was empty.
## External Temp power-loss retest - 2026-07-15

- Updated APK stages audio in configured removable storage:
  `/storage/6162-6433/Android/data/com.dvid.dcam/files/Temp`.
- Internal app-private `Temp` remained empty during capture.
- Cleaned the two prior preserved zero-byte artifacts before retest:
  `DCAM_KF5KW2124062200167_000000_20260714_160710.aac` and
  `DCAM_KF5KW2124062200167_000000_20260714_162207.aac`.
- Baseline recovery after cleanup: `recovered=0, preserved=0, duplicates=0`.
- Fresh physical battery-cut run used
  `DCAM_KF5KW2124062200167_B01OPR_20260715_165048.aac`.
- Before power cut, staged AAC was non-zero and growing at `36,425` bytes.
- After reboot and removable-volume mount settling, recovery reported:
  `recovered=1, preserved=0, duplicates=0`.
- Final AAC size: `3,847,100` bytes under removable `Media/Audio/yyyy-MM-dd`.
- Source staging file was removed; removable `Temp` was empty.

**Current result:** PASS. External Temp staging and physical power-loss recovery work on `BODYCAMERA4HHITK`. Wait for removable-volume mount before judging recovery; firmware briefly reports the SD volume as `shared` during boot.

**Remaining action:** Repeat on each production firmware/storage profile. Keep internal-only Build 0.1 acceptance separate from this removable-storage qualification.
