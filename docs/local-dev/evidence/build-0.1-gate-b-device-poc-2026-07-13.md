# Build 0.1 Gate B device POC evidence - 2026-07-13

Status: partial pass
Device: `KF5OF2126040802193`
Business model: `NCC-036V`
ADB `ro.product.model`: `BWC`
Android: `12` / API `31`
Firmware: `877AOOAKN1_RK2_V009`
Package: `com.dvid.dcam`
APK: `app/build/outputs/apk/debug/app-debug.apk`
APK SHA-256: `B51D66CD5C8EE2DD1B3156EAB7D81F5B6B5EEE6FEFED33146EF96A4C3A2E01E6`
APK size: `8,286,386` bytes
Installed: `2026-07-13 13:50:14 +07:00`
App external root: `/storage/emulated/0/Android/data/com.dvid.dcam/files`

## Qualification blocker: DEVICE_TOKEN contradiction

- Device POC evidence identifies actual reference-device `serial_number` as `KF5OF2126040802193`.
- Exact value has `17` characters and satisfies `[A-Z0-9]`.
- Approved filename rule requires `DEVICE_TOKEN` to be the validated `serial_number` snapshot and also limits it to `6–10` characters.
- Approved guardrails prohibit silent truncation, underscore padding, aliasing, hashing, or an invented replacement token.
- Therefore this device cannot satisfy all approved `DEVICE_TOKEN` rules: full serial violates length; any shortened or substituted value violates snapshot/no-replacement rules.
- `OPERATOR_TOKEN` value `B01OPR` is valid: exactly `6` characters and `[A-Z0-9]` only.
- Filename identity qualification is `BLOCKED - requirement contradiction`, not failed source code. Existing filenames containing the full serial are evidence of actual identity, not proof of DEC-07 compliance.
- Device POC owner must reconfirm the serial from physical-device evidence before qualification resumes.
## Build/install

- `./gradlew.bat test assembleDebug`: PASS, 2026-07-13 13:48 +07:00.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: PASS.
- Launch smoke: `mResumedActivity` = `com.dvid.dcam/.app.MainActivity`.
- Startup log check: no `dcam-cloud-init`, `Cloud identity initialization failed`, or `FATAL EXCEPTION` found after launch.

## Test results

| Test | Command/steps | Result | Evidence |
|---|---|---|---|
| Login/session precondition | UI login with default `000000` | PASS | Camera preview active; hardware keys accepted after login |
| 1.1 Basic recording | `adb shell input keyevent 79`, wait 32s, `adb shell input keyevent 79` | PASS | `DCAM_KF5OF2126040802193_000000_20260713_135307.mp4`, `80,192,700` bytes |
| MD5 sidecar | Read `.md5`, run `md5sum` on MP4 | PASS | `7f0a3b287a633d461b166100cc00eb2b` matched |
| Image capture | `adb shell input keyevent 27` | PASS | `DCAM_KF5OF2126040802193_000000_20260713_135417.jpg`, `864,741` bytes |
| 2.1 FGS notification/service | During recording, inspect `dumpsys activity services` | PASS | `RecordingForegroundService`, `isForeground=true`, `foregroundId=1001` |
| 4.2 Screen-off recording | Start recording, power off, wait 20s, power on, stop | PASS | `DCAM_KF5OF2126040802193_000000_20260713_135433.mp4`, `22,650,173` bytes |
| Screen-off MD5 | Read `.md5`, run `md5sum` | PASS | `e40649aa41ef0b9f43aa79177d356d2d` matched |
| 1.4 Force-stop staged-media recovery | Start recording, wait 12s, `adb shell am force-stop com.dvid.dcam`, relaunch | PASS | Recovery moved staged MP4 from `Temp` to `Media/Video/yyyy-MM-dd`; `Temp` empty |
| AUTO removable root | Disable vendor USB mass-storage bridge, verify `6162-6433` mounted, record/finalize | PASS | AUTO selected `/storage/6162-6433/Android/data/com.dvid.dcam/files`; `DCAM_KF5KW2124062200167_000000_20260713_143756.mp4`, matching MD5 `ecd60244474bda16e02dfe64073bc19f`; SD `Temp` empty |
| Activity recreate during recording | Rotate through `user_rotation` values while recording | PASS | `RecordingForegroundService` remained foreground; `DCAM_KF5KW2124062200167_000000_20260713_143859.mp4` finalized with matching MD5 `cf054308262b014743502a73961b5bd3`; SD `Temp` empty |
| Reboot recovery | Start SD recording, confirm staged file, `adb reboot`, remount SD, relaunch | PASS | `DCAM_KF5KW2124062200167_000000_20260713_143952.mp4` recovered with matching MD5 `31bba8eebee7c754f75c28f1ac120c55`; `Temp` empty |
| Repeated 40-second-class recording | Record after reboot recovery and vendor-package restoration | PASS | FGS stayed foreground; `DCAM_KF5KW2124062200167_000000_20260713_150606.mp4`, `101,378,386` bytes, matching MD5 `a80011b78f08614e235ad74195f50992`; SD `Temp` empty |
| Physical power-cut recovery | Start SD recording, confirm staged file and FGS, remove battery, boot, relaunch after SD/MediaProvider settle | PASS | `DCAM_KF5KW2124062200167_000000_20260713_153623.mp4` recovered with matching MD5 `77a7e90790c220c6b0189e8f9b329946`; `Temp` empty |

## Observations

- Recording requires an active operator session; pre-login hardware record key does nothing by design.
- Basic recording writes to `Temp` while active and publishes to `Media/Video/yyyy-MM-dd` after stop.
- MD5 sidecar naming is currently `<base>.md5`, not `<base>.mp4.md5`.
- Recovery MD5 fix was re-verified on ADB target `BODYCAMERA4HHITK` after clean-installing APK SHA-256 `635FBA40973B14900C49EB94CF7A2B51C8F0CB323F132C29411223ADD7924A9A`. Force-stop recovery produced `DCAM_KF5KW2124062200167_000000_20260713_142121.mp4` (`30,836,218` bytes) and matching `.md5` digest `d87adec5376109b5a069c3dbd2947183`; `Temp` was empty and recovery reported `recovered=1`.
- Removable UUID is device/card-specific. Earlier BWC evidence used `E3AE-18F6`; current `BODYCAMERA4HHITK` uses `6162-6433`. DCAM does not hardcode either value: `DcamStorage` discovers secondary app roots through `Context.getExternalFilesDirs(null)` and refreshes them before capture. AUTO capture to mounted `6162-6433` is verified.
- Firmware vendor app `com.bodycamera.nettysocket` exports the SD card through `mass_storage,adb`, changing the public volume to `MEDIA_SHARED` and removing `/storage/6162-6433` from Android apps. Force-stop is not persistent after reboot. Temporarily disabling the vendor package and selecting USB function `none`/`adb` allowed deterministic SD testing; the package was re-enabled after testing.
- Reboot recovery first encountered the vendor USB race: DCAM reported `recovered=0, preserved=1` while SD availability changed, then recovered the same staged file after stable remount. No staged-media loss occurred.
- Physical power-cut recovery first encountered an Android MediaProvider race: volume `6162-6433` was mounted, but MediaProvider still reported `Volume 6162-6433 not found`, so DCAM preserved the staged file (`recovered=0, preserved=1`). After a clean relaunch once MediaProvider settled, DCAM recovered the same file (`recovered=1, preserved=0`) and generated matching MD5. No staged-media loss occurred.
- `adb root` can leave this firmware's composite USB interface offline on Windows until USB/Windows restart. Avoid `adb root` in repeatable evidence runs.
- Physical SD removal is not testable without camera disassembly because the card is packed inside the enclosure. Treat physical insert/remove coverage as not executable on this device; use controlled USB/storage-state simulation only when firmware supports it.
- One post-reboot login-to-preview attempt produced `Input dispatching timed out (Application does not have a focused window)` while CameraHAL was busy. Clean app restart restored preview, and a later 40-second-class recording passed. Track as intermittent device/CameraHAL observation, not a completed stability pass.
- `adb shell find -printf` is unsupported on this device; use `ls`, `md5sum` and exact paths for later evidence.

## Remaining Gate B tests

- Long-duration recording with thermal/storage measurements.
- More repetition across cold boots to characterize intermittent CameraHAL/focus ANR.

## Gate B status

Gate B is not complete, but tested BodyCamera hardware passes core smoke POC for Build 0.1 recording, image capture, FGS foreground state, screen-off recording, activity recreation, dynamic removable-root selection, Temp-to-Media finalization, MD5 for normal and recovered videos, reboot recovery, physical power-cut recovery, and repeated recording. Long-duration thermal/storage evidence remains.
