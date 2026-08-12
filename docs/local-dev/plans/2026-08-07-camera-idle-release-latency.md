# Camera Idle Release Latency Test

## Status
Complete

## Objective
Measure button-press-to-recording and button-press-to-photo-capture delay when camera resources are released during idle screen-off state.

## Contract
### Must
- Collect device evidence before changing camera lifecycle behavior.
- Measure both video recording and photo capture cold-start delay.
- Keep current user-visible capture behavior unchanged during measurement.

### Must not
- Modify unrelated BDMA or DCAM behavior.
- Overwrite existing untracked or staged work.
- Claim latency results without reproducible timestamps.

## Evidence
### Facts
- Test device `BODYCAMERA4HHITK` is connected through bundled ADB.
- Device runs Android 12 / API 31.
- Installed packages include `com.dvid.dcam` and `com.bodycamera.nettysocket`.
- Before testing, `com.dvid.dcam/com.dvid.dcam.app.MainActivity` was foreground and the screen was on.
- Source contains `ProcessCameraRuntimeOwner.releaseCamera()` plus debug camera benchmark support.
- Production code intentionally keeps camera runtime ready while screen is off so active recording can continue and idle capture avoids reinitialization.
- After `releaseCamera()` reaches `CLOSED`, direct photo or recording commands are rejected until `bindCommitted()` starts; commands submitted during that rebind are held and run after camera reaches `READY`.
- Key-event input logging records main-thread delivery lag from Android event time; firmware-broadcast input currently records acceptance without an origin timestamp.
- Same-UID firmware broadcasts are reproducible through `run-as com.dvid.dcam am broadcast --user 0`; a hot screen-on photo trial logged button acceptance at epoch `1786066405.567` and camera-pipeline completion at `1786066405.868` (301 ms).
- Installed debug build exposes app logs immediately through Logcat tag `DCAM` and persists detailed timing to `Logs/logs.txt`.
- Matrix run `20260807-1786067180711` completed on `BODYCAMERA4HHITK`, Android 12 / API 31, native surface-sharing pipeline, USB power, battery 8%, thermal status 0, CPU/GPU 58 C, and skin 35 C.
- `PowerManager` remained `Asleep` through all 12 matrix trials; probe cleanup released camera-ready foreground state and restored the display to `Awake`.
- User correction after completion: connected device hardware buttons now vibrate but do not capture photo or video.
- Test-derived operational changes requiring audit: debug APK reinstall restarted the app process, the probe released camera-ready state, and the display was restored to awake.
- Regression screenshot `.local/camera-latency/regression-screen.png` shows active preview and operator `USER B01OPR`; memory-only operator-session loss is not the cause.
- Regression logs `.local/camera-latency/regression-logs.txt` show firmware photo, video, and laser button actions accepted without photo/video pipeline start; audio record still starts and saves, proving button delivery and storage remain functional.
- Final probe cleanup directly released the shared camera runtime; `MainActivity` later resumed without a logged committed rebind.
- Device log sequence proves release at `2026-08-07 08:48:50.595`, `MainActivity` resume at `08:48:51.222`, then accepted photo/video firmware actions at `08:58:23`-`08:58:38` without any capture pipeline entry.
- Source confirms `CameraFlowCoordinator.bindIfNeeded()` starts only from `IDLE` or `UNAVAILABLE`; photo/video guards reject when shared runtime is not `READY`, and rejected video start can remain pending.
- Android 12 broadcast history shows explicit `run-as` sends to the non-exported manifest probe receiver were skipped even with caller UID `10119`; prior assumption that same-UID ADB delivery was reliable is invalidated.
- Exporting the debug receiver behind `android.permission.DUMP` still left static broadcasts skipped despite shell holding `DUMP`. Adding an intent filter produced `match=0x108000` but was also skipped.
- Verbose device evidence identifies the OEM rule: `BroadcastQueue` logs `suppress to start process of staticReceiver for package:com.dvid.dcam`. Static manifest delivery is unusable on this firmware.
- Because the release send was skipped during the first hardened validation attempt, the following firmware video action started normally and produced `DCAM_000005_B01OPR_20260807_092158.mp4`; it was stopped and finalized successfully after 53 seconds. No test media was deleted.
- Current DCAM branch is `DCAM-4-Camera-recording-prototype`.
- Existing untracked file `docs/local-dev/plans/2026-08-06-dcam-23-evidence-batch.md` is unrelated and must remain untouched.

### Hypotheses
- Confirmed: releasing idle camera adds camera-open and session-configuration time to first record/photo action.
- Confirmed: existing application pipeline logs provide stable recording-start and photo-capture endpoints once a simulated button boundary is added.
- Invalidated: APK reinstall cleared the memory-only operator session. Active preview and `USER B01OPR` prove session remains available.
- Hypothesis: direct probe cleanup left `CameraFlowCoordinator` logically `READY` while shared runtime became `CLOSED`, so `bindIfNeeded()` skipped rebind.
- Hypothesis: first rejected video start left `SerializedRecordingCoordinator` pending, so later video presses exit early after haptic feedback.

## Phased Plan
- [Completed] Inspect camera lifecycle and existing diagnostics. Confirmed hot-camera contract, release/rebind state transitions, input boundaries, and device logging.
- [Completed] Consider minimal measurement approaches. Selected a debug-only connected-device probe after unrelated androidTest compilation blockers prevented instrumentation execution.
- [Completed] Implement one non-exported debug receiver for repeated hot and cold video/photo timing without touching production behavior.
- [Completed] Confirm code hygiene, diff integrity, durable result documentation, device cleanup, and screen restoration.
- [Completed] Diagnose post-test button-only vibration and restore device state with a data-preserving app process restart.
- [Completed] Harden probe preparation and failure cleanup without changing production sources.
- [Completed] Install hardened probe and validate release, stale pending-video cleanup, restore, photo, and video.
- [Completed] Run final diff, encoding, manifest, focused unit tests, and device-state checks.

## Considered Solutions
- Existing application logs alone: retained for timing endpoints, but insufficient to release and rebind the process-owned camera on demand.
- Android instrumentation test: implemented first, then removed because 11 pre-existing unrelated androidTest compile errors block every instrumentation APK.
- Debug-only non-exported broadcast receiver: selected because normal debug APK compiles, production behavior stays unchanged, and same-UID ADB commands provide a reproducible app-accepted button boundary.

## Decisions
- Preserve production hot-camera lifecycle; measurement code exists only in `src/debug`.
- Run three hot and three cold trials for video and photo while `PowerManager.isInteractive()` is false.
- Measure video until runtime reaches `RECORDING`; measure photo both to camera-pipeline completion and full `CAPTURE_PHOTO` operation completion.
- Treat debug receiver entry as simulated accepted-button time. Physical switch-to-app firmware delivery latency is outside this test.
- Restore connected device with app process restart, not app-data deletion; this rebuilds coordinator/runtime state and clears memory-only pending video state while preserving operator data and media.
- Harden debug probe preparation/error cleanup so a direct runtime release cannot strand normal capture flow again.
- Expose the debug-only probe receiver to ADB with `android.permission.DUMP`; shell can invoke it, while ordinary third-party apps cannot hold this privileged permission.
- Register the `DUMP`-guarded probe receiver dynamically through the existing AndroidX Startup provider; remove static receiver registration to bypass OEM suppression without touching production sources.

## Changes Made
- Reused this existing task journal and removed accidental duplicate `2026-08-07-screen-off-capture-delay-test.md`.
- Added then removed `CameraIdleReleaseLatencyDeviceTest.java` after unrelated stale androidTest sources blocked instrumentation compilation.
- Added debug-only `CameraIdleReleaseLatencyReceiver` plus AndroidX Startup dynamic registration guarded by `android.permission.DUMP`.
- Probe supports prepare, idle release, hot/cold video, and hot/cold photo actions; cold capture submits command during committed rebind.
- Kept release and production source sets unchanged.
- Added debug `RESTORE` action; probe preparation now clears stale pending video, detects `CameraFlowCoordinator.READY` plus closed runtime, and retries through normal coordinator binding.
- Probe failure cleanup now stops active recording and restores ready camera state instead of directly releasing camera and foreground-service ownership.
- Replaced OEM-blocked static probe receiver registration with a debug AndroidX Startup initializer that registers the receiver dynamically and requires sender permission `android.permission.DUMP`.

## Validation Results
- Hot screen-on firmware-broadcast photo baseline: 301 ms from app acceptance to completed camera capture pipeline.
- `./gradlew.bat :app:compileDebugAndroidTestJavaWithJavac` failed with 11 pre-existing errors in unrelated device tests; no error referenced the removed latency test.
- `./gradlew.bat :app:compileDebugJavaWithJavac` passed before and after probe robustness review.
- `./gradlew.bat :app:installDebug` passed and installed on `BODYCAMERA4HHITK`.
- Matrix run `20260807-1786067180711` completed 12/12 captures with no probe failure while screen remained asleep.

| Capture | Camera | Button to pipeline, trials 1/2/3 | Button to operation complete, trials 1/2/3 | Rebind ready, trials 1/2/3 |
| --- | --- | --- | --- | --- |
| Video | Hot | 113 / 100 / 99 ms | 119 / 105 / 104 ms | N/A |
| Video | Cold | 840 / 847 / 849 ms | 844 / 853 / 856 ms | 745 / 755 / 760 ms |
| Photo | Hot | 278 / 274 / 290 ms | 340 / 320 / 341 ms | N/A |
| Photo | Cold | 1022 / 1017 / 1023 ms | 1068 / 1053 / 1054 ms | 760 / 757 / 763 ms |

- Median button-to-pipeline: video hot 113 ms, video cold 849 ms, penalty 736 ms.
- Median button-to-pipeline: photo hot 290 ms, photo cold 1023 ms, penalty 733 ms.
- Median cold rebind-ready time: video 760 ms; photo 760 ms.
- Idle release took 120-142 ms across six releases; median 134.5 ms.
- Full parsed evidence is at `.local/camera-latency/20260807-1786067180711/summary.json`.
- Path-scoped `git diff --check`, no-index checks for new files, manifest diff inspection, UTF-8 corruption scan, and forbidden-logging scan passed.
- Final `./gradlew.bat :app:compileDebugJavaWithJavac` passed after CRLF alignment with adjacent debug Java sources.
- Data-preserving recovery changed app PID from `3444` to `4697`, reopened camera 0, and logged `BIND_COMMITTED ... outcome=ready` at `2026-08-07 09:10:28.992`.
- Post-restart screenshot `.local/camera-latency/after-process-restart.png` shows active preview and `USER B01OPR`.
- Same-UID firmware validation created `DCAM_000005_B01OPR_20260807_091206.jpg` and `DCAM_000005_B01OPR_20260807_091209.mp4` plus its MD5 sidecar; no existing media or app data was removed.
- Hardened debug receiver passed `:app:compileDebugJavaWithJavac`; path-scoped `git diff --check` also passed.
- First hardened device attempt was invalid: package broadcast history recorded both `RELEASE` and `RESTORE` as skipped manifest receivers, so it did not exercise probe lifecycle logic.
- Dynamic receiver delivery passed: package-scoped shell `RESTORE` logged `Prepare camera latency probe success` at line 1829 of `.local/camera-latency/final-release-restore-capture-logs.txt`.
- Exact regression passed: release completed at line 1847; firmware video press while closed was accepted at line 1850; normal coordinator `BIND_COMMITTED` completed at line 1868; restore completed at line 1871; no recording start occurred before the deliberate post-restore video action at line 1898.
- Post-restore photo and video passed, creating `DCAM_000005_B01OPR_20260807_093921.jpg`, `DCAM_000005_B01OPR_20260807_093924.mp4`, and its MD5 sidecar. The video finalized successfully at line 1952.
- Final device state: PID `6104`, camera client open, `MainActivity` resumed, display awake, capture idle.
- Focused `CameraFlowCoordinatorTest` and `SerializedRecordingCoordinatorTest` passed through `:app:testDebugUnitTest`.
- Final merged manifest contains the debug initializer metadata and no static latency receiver; `git diff --check`, UTF-8/mojibake scan, numeric-entity scan, and forbidden-logging scan passed.

## Completion Notes
- Required work: None.
- Production camera lifecycle and release source set remain unchanged; all hardening stays in `src/debug`.
- Full `androidTest` compilation remains blocked by 11 pre-existing unrelated errors; focused unit tests and connected-device validation passed.
