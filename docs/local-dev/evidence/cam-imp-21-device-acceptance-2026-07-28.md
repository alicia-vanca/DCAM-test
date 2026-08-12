# CAM-IMP-21 Device Acceptance Evidence

**Status:** VERIFIED (REUSABLE XML COLD START + STATIC SOLO + HEADLESS TUPLE + SETTINGS OWNERSHIP + STARTUP BLACK MASK 2026-07-28)
**Date:** 2026-07-28
**Device:** `BODYCAMERA4HHITK`

## Device Identity

- Model: `BodyCamera`
- Android: 12 / API 31
- Firmware display: `833AJOAEC1_RK_V040`
- Fingerprint: `Android/full_k69v1_64_k419/k69v1_64_k419:12/SP1A.210812.016/mp1V14271:user/release-keys`

## Acceptance Fixes

- Android 12 startup compatibility: replaced direct `Stream.toList()` calls with Android-safe list construction.
- Runtime operation deadlines: aligned direct-bind/runtime deadlines with wall-clock comparisons used by both Camera2 pipelines.
- Startup fallback ownership: `CameraFlowCoordinator` accepts a lower verified tuple when camera, codec, and pipeline match; it no longer releases a valid fallback binding.
- Pipeline A preview continuity: repeating requests keep preview and encoder surfaces active together; JPEG video snapshots may omit external preview for one frame, then repeating preview continues.
- Candidate budget: static `V-F`/`I` verification creates no media; each headless deep `V-F-I` candidate owns the full 5000 ms budget.
- Historical XML note: this July 28 device run used the pre-reset `<selection>` schema. Fresh-release contract now starts directly at new format 1 with `<selectedPipeline>` and `<selectedRecordingProfile>`; no migration path exists.
- AUTO ignores stale fixed per-camera pipeline overrides from older snapshots and derives production selection from the configured durable recommendation/fallback order.
- Forced pipeline absence preserves camera inventory with empty capability options; it does not silently route that camera through another pipeline.
- Developer pipeline-mode persistence failure reports `FAILED`; session mode stays aligned with the active target, while backends without session fallback restore the previous verified pipeline.
- Device instrumentation: replaced stale blocked-preview expectation with preview-and-encoder progress contract.
- Resolution families: fast video state retains every encoder-eligible actual child and fast image state retains every JPEG actual child under each standard label, both ordered by match score.
- Static video gate: `MediaCodecInfo.VideoCapabilities.areSizeAndRateSupported(width,height,fps) = false` excludes that exact `V-F` before fast probing; no deep verified-fail fact is fabricated.
- Static image gate: regular and high-resolution JPEG outputs are merged deterministically; every returned exact `I` is treated as solo verified without manual capture.
- Matrix pruning: `V-F` rows with no simultaneous tuple are removed; static-passing `I` remains selectable evidence even without a tuple. Ranked child families remain intact; selected tuple evidence pins only the actual in use.
- Startup/runtime ownership: default and changed tuples deep-verify on a headless pipeline, verifier releases before production preview bind, and no two camera owners run concurrently.
- UI contract: startup holds a styled black mask with spinner and `Đang khởi động camera...` across auxiliary/main verification, then reveals only after the first fresh production main-camera frame. Runtime `V-F-I` verification remains asynchronous with public state `READY`, no `Verifying`, no startup message, and no verifier frame rendered.
- Owner invariant: process runtime reserves a capability-scan lease before probes start; initialize/bind/recovery submissions are rejected until scan releases the lease, and `scanInFlight` remains true until the post-release callback.
- XML lifecycle state: fresh-release format 1 requires `initializationState="incomplete|ready_reusable"`; missing state is rejected as invalid current-schema XML. `ready_reusable` is written only after every camera has committed selected tuple plus complete static solo inventory and deep tuple pass.
- Cold-start reuse: valid/fresh reusable XML loaded with no A/B fast rebuild. Reusable path uses `BIND_COMMITTED` from `CLOSED`, not `RESTORE_EXACT`; failed bind persists `incomplete` before rebuild. Android 12 parser regression is covered by `SnapshotXmlCodecDeviceTest`.
- Sibling verifier: best passing deep sibling commits immediately and stops; later siblings remain raw/static fallback evidence, not additional deep selected passes.
- Settings ownership: `RECORD_SETTINGS` exposes video resolution and FPS only; `CAMERA_SETTINGS` exposes status plus image resolution with the photo-quality-during-recording notice; capability recheck is exposed only in `DEVELOPER_SETTINGS`.
- Handoff rollback: verifier release exceptions become recovery results; release failure, preview-bind failure, or handoff exception restores/clears previous target-camera durable selection before recovery.

## Manual Mode Evidence

Manual Developer Settings flow completed: `AUTO → A → B → AUTO`.

### Pipeline A

- Persisted selector: `camera_pipeline_selector persistedMode=A`.
- Startup retained one ready owner after lower-tuple verification; no immediate release.
- Runtime tuple: camera `0`, H.264, `SD:720x480@30 + SD:640x480`.
- Record start: PASS, encoder start `578 ms`.
- JPEG during recording: PASS, `640x480`, `236 ms`.
- Record stop/finalization: PASS, `235 ms` finalization.
- Preview counters progressed during recording and after stop; no watchdog recovery.
- Published artifacts:
  - `DCAM_000005_B01OPR_20260728_130031.mp4`, `1,996,962` bytes, with `.md5`.
  - `DCAM_000005_B01OPR_20260728_130037.jpg`, `286,439` bytes.

### Pipeline B

- Persisted selector: `camera_pipeline_selector persistedMode=B`.
- Forced switch completed `READY:requested_verified` without a second camera owner.
- Runtime tuple: camera `0`, H.264, `SD:720x480@30 + SD:640x480`.
- Record start: PASS, encoder start `102 ms`.
- JPEG during recording: PASS, `640x480`, `237 ms`.
- Record stop/finalization: PASS, `264 ms` finalization.
- Preview counters progressed; no watchdog recovery.
- Published artifacts:
  - `DCAM_000005_B01OPR_20260728_130401.mp4`, `2,120,541` bytes, with `.md5`.
  - `DCAM_000005_B01OPR_20260728_130407.jpg`, `280,213` bytes.

### AUTO

- Persisted selector: `camera_pipeline_selector persistedMode=AUTO`.
- AUTO resolved durable recommendation to Pipeline A and direct-bound verified selection in `481 ms`.
- Record, JPEG during recording, stop, and media finalization passed.
- Activity background/foreground return preserved same process owner and resumed preview progress without rescan, duplicate command, or watchdog recovery.

## Startup and Coverage Timing

Representative cold-start logs:

- App T0 marker: `appMs=446`.
- Full fast scan: `3.6-3.7 s` for two cameras.
- Camera role order: camera `1` verified first, main camera `0` verified last and retained for preview.
- Camera `1` verified tuple: `HD:1280x720@30 + HD:1280x720`.
- Main camera `0` verified tuple: `SD:720x480@30 + SD:640x480`.
- Final connected device suite after static-solo/headless/rollback/post-release-scan-lease/startup-mask fix: 20 tests, 0 failures, `194.634 s`.

## Resolution-Family Device Evidence

- Vendor capture `DSJ_000005_000000_20260728_145257.mp4`: H.265/HEVC, `1920×1080`.
- Vendor capture `DSJ_000005_000000_20260728_150458.mp4`: H.264/AVC, `1920×1080`.
- Camera2 HAL advertises both `PRIVATE 1920×1080` and `PRIVATE 1920×1088`.
- Public AVC encoder metadata reports `1920×1080` unsupported and `1920×1088@30` supported on the target MTK encoders. This does not prove hardware cannot produce exact FHD; it defines the public encoder contract used by app fast discovery.
- Native fast shared-topology probe retained aligned FHD, and focused real verifier tests passed Pipeline A and Pipeline B with aligned `FHD:1920×1088` video plus `FHD:1920×1080` JPEG.
- Focused device verifier: 4 tests, 0 failures. Focused A/B benchmark: 1 test, 0 failures. Full connected suite: 20 tests, 0 failures.

## Post-fix Persistence Smoke

- Final debug reinstall and cold launch completed on BodyCamera; DCAM restored as default HOME and remained the sole active camera client.
- Device log recorded camera `1` in `VERIFYING` before `shared_camera_preview stage=startup_mask outcome=hidden_after_frame`; auxiliary preview never became visible.
- Durable XML selection persisted camera `0`, H.264, Pipeline A, `FHD:1920x1088@30 + FHD:1920x1080`; camera `1` persisted `HD:1280x720@30 + HD:1280x720`.
- Persisted camera `0` video inventory contains `FHD:1920x1088` and `QHD:2560x1440` with no lower video `MAX`; camera `1` keeps `MAX:1600x1200` only because it outranks that camera video standard ceiling.
- Android 12 runtime permissions CAMERA, RECORD_AUDIO, coarse/fine location, MANAGE_EXTERNAL_STORAGE, and WRITE_SETTINGS were restored; POST_NOTIFICATIONS is unavailable on API 31 as expected.

## Reusable XML Cold-start Smoke

- Historical 2026-07-28 initialization used the pre-reset XML schema. Fresh-release format-1 device acceptance for `<selectedPipeline>` + `<selectedRecordingProfile>` must be rerun before this section can prove current durable XML.
- Second force-stop/cold launch: `camera_capability_load result=loaded`, `snapshot_reuse outcome=ready_reusable`, `camera_fast_scan=0`, `camera_verification=0`, `BIND_COMMITTED` start/end for main camera `0`, `RESTORE_EXACT=0`, and `startup_mask outcome=hidden_after_frame=1`.
- No auxiliary camera verification ran on reusable cold start; production preview became visible only after a fresh main-camera frame.

## Automated Validation

All required commands passed. Final result XML totals: core unit `3/3`, app unit `539/539`, connected device `21/21`; zero failures, errors, or skips:

```text
.\gradlew.bat :core:test
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```

Connected result XML:

`app/build/outputs/androidTest-results/connected/debug/TEST-BodyCamera - 12-_app-.xml`

A/B report device log:

`app/build/outputs/androidTest-results/connected/debug/BodyCamera - 12/logcat-com.dvid.dcam.platform.camera.shared.benchmark.CameraPipelineBenchmarkDeviceTest-exportsSameTupleAbReportForBothPipelines.txt`

Coverage includes both real pipelines, exact encoder FPS metadata, regular/high-resolution JPEG union, strict MAX, orphan-video removal/orphan-image retention, headless tuple verification, verified-handoff rollback behavior, capability-scan reservation and post-release scan-flag ordering, styled startup-mask/frame gating, async READY runtime transitions without startup/verifying messages, A/B report export, malformed JPEG rejection, recovery, exact record/JPEG topology, and preview/encoder continuity.

Unit coverage includes ranked video/image resolution children, encoder-gated video fallback, video/image sibling pruning and pinning, first-render pinned UI dimensions, image-label removal when no effective tuple remains, selected-option fallback, corrupt/unsupported capability XML rebuild, storage-capacity rejection/fallback, storage-limit recording stop, staged publication/finalization, process death, Activity reattach, camera unavailable/recovery, and held-command cancellation.

## Source Scan

- Removed CameraX dependencies from `app/build.gradle`.
- Deleted `CameraXCameraGatewayImpl`, `CameraXPreviewView`, `CameraBackendSelection`, `Camera2CaptureController`, `AndroidDeviceCapabilities`, `CaptureStorageFailureClassifier`, and `DcamMediaStore`, plus obsolete tests.
- Removed CameraX-shaped media output APIs and switched production callers to shared staging/finalization contracts.
- Production contains one `SharedCameraGateway`, one `SharedCameraRuntimeBackend`, and one `ProcessCameraRuntimeOwner` construction path.
- Source scan found no `androidx.camera`, `ProcessCameraProvider`, `CameraXCameraGatewayImpl`, `LEGACY`, or `SHADOW` production path.
- No temporary compatibility bridge remains; no deletion owner remains.

## Operational Rollback

App contains no legacy backend. Rollback uses previous signed release artifact and matching configuration backup:

1. Stop active recording and verify finalization completed.
2. Preserve current capability/config XML and logs for diagnosis.
3. Verify previous APK uses same application ID and signing key.
4. Install previous release with `adb install -r -d <previous-signed-release.apk>` or approved device-owner update channel.
5. Restore matching prior configuration only when schema/version requires it; otherwise preserve app data.
6. Launch previous release, verify camera preview, one record/stop cycle, one JPEG, storage publication, and foreground-service state.
7. If downgrade install is rejected, do not enable a second camera backend; use approved signed update/rollback tooling or controlled app-data reset after backup.

## Known Limitations

- Vendor software can record exact `1920×1080` AVC/HEVC, while Android public AVC encoder metadata rejects app-owned `1920×1080` and accepts `1920×1088`; production therefore exposes the verified aligned FHD actual rather than claiming exact FHD through an unsupported public encoder path.
- Strict MAX evidence: camera `0` does not expose a video `MAX` below represented `QHD:2560×1440`; no hard-coded quality routing was added.
- No additional target device was connected for CAM-IMP-21. Device matrix currently contains this BodyCamera hardware/firmware identity.

## Remaining Handoff

- Camera migration has no remaining legacy deletion or compatibility-bridge handoff.
- Reusable XML state and cold-start direct-bind path are complete; no future deletion owner or bridge remains.
- Future device additions must repeat full A/B comparison, AUTO/A/B manual smoke, timing review, and required Gradle/device suite before recommendation rollout.