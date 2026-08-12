# DevMode Release Camera While Screen Off

## Status
Complete

## Objective
Add a developer-mode toggle that releases idle camera resources while the screen is off. Default enabled. Explain that it saves battery but increases off-screen capture delay.

## Contract
### Must
- Add toggle to existing developer-mode settings UI.
- Persist setting with default value enabled.
- Release camera only while screen is off and capture is idle.
- Restore camera for normal capture after screen wakes or an off-screen capture action arrives.
- Preserve active video, IMP, photo, and audio capture behavior.

### Must not
- Release camera during active or in-flight capture.
- Change production defaults outside this developer setting.
- Delete app data, media, or unrelated repository work.
- Reuse latency-probe-only behavior as production lifecycle logic without confirmed integration.

## Evidence
### Facts
- User explicitly requests default-on developer toggle.
- User-visible description: saves battery and increases off-screen capture delay.
- Existing completed latency evidence measured about 0.73 seconds additional cold-camera delay.
- Work continues on branch `DCAM-4-Camera-recording-prototype`; only repository-root `AGENTS.md` applies.
- Existing debug latency-probe files and unrelated local journals are uncommitted work and must remain untouched.
- MainActivity keeps its dynamically registered firmware-button receiver through `onStop`, so screen-off physical commands still enter `HardwareButtonRouter`.
- Raw `releaseCamera()` clears pending photo/video commands and does not change `CameraFlowCoordinator` from `READY`; later `bindIfNeeded()` then skips rebind.
- `ProcessCameraRuntimeOwner` can hold commands during `BIND_COMMITTED`, but `PhotoCaptureUseCase` currently drops photo requests while camera-flow state is not `READY`.
- `SharedPreferencesDeveloperSettingsStore` is the existing persisted camera developer-settings owner and defaults missing pipeline values without new infrastructure.
- `SettingItem` already carries descriptions, but `SettingsControlRenderer` does not render descriptions for checkbox rows.
- Independent review confirmed startup release retry depended on camera-screen runtime attachment, so an already-off login/startup could leave camera open.
- Independent review confirmed the generic 15-second capability-release timeout can race a still-running idle release and reject required rebind.
- Independent review confirmed failed `BIND_COMMITTED` cleared deferred photo work and could classify recoverable released-camera state as hard `UNAVAILABLE`, discarding pending video/IMP.
- Post-fix review confirmed cached display state could become stale between initial sampling and receiver registration or before delayed release execution.
- Post-fix review confirmed audio completion retry still depended on lifecycle-gated rendering instead of the app-wide idle observer.

### Hypotheses
- Existing developer settings store and settings presentation can host one boolean without new infrastructure.
- Existing screen lifecycle and hardware-button paths can reuse normal camera release/rebind coordination.

## Phased Plan
- [Complete] Inspect developer settings, screen lifecycle, and capture gates.
- [Complete] Consider minimal persistence and lifecycle integration options.
- [Complete] Implement setting, UI, release, and restore behavior.
- [Complete] Add focused setting and lifecycle regression tests.
- [Complete] Confirm build, tests, encoding, and connected-device behavior.
- [Complete] Review behavior, architecture, and lifecycle races.
- [Complete] Diagnose fresh full-suite architecture test failure.
- [Complete] Restore tested Vietnamese toggle label.
- [Complete] Roll back unrelated test formatting drift.

## Considered Solutions
- Rejected raw runtime release plus later `bindIfNeeded()`: coordinator remains `READY`, so capture cannot recover reliably.
- Rejected `MainActivity.onStop()` release: screen-off and Activity-stop semantics differ, and active recording must continue.
- Selected coordinator-aware idle release: store committed candidate, move flow to `IDLE`, release only after conservative idle checks, then use `BIND_COMMITTED` for wake or capture recovery.
- Selected deferred photo dispatch until rebind reaches `READY`; existing serialized recording pending-start flow handles video/IMP recovery.
- Rejected capability-scan timeout semantics for idle release: timeout can report failure before runtime release reaches a terminal state.
- Selected app-wide camera-flow/runtime observation so startup and login screens retry release without camera-screen UI attachment.
- Selected direct idle-release completion, committed-bind retry, then deep startup fallback while retaining queued capture work until `READY`.

## Decisions
- Default toggle value is enabled, per user instruction.
- Keep implementation within existing developer settings and camera lifecycle abstractions.
- Persist lifecycle policy in `DeveloperSettingsStore`, not `DeveloperFeatureToggles`; this is camera behavior, not a feature gate.
- Register explicit screen on/off handling beside existing Activity receivers; do not infer screen state from `onStop()`.
- Extend checkbox rendering to show the requested description without changing rows that have no description.
- Reuse `SerializedRecordingCoordinator` pending-start recovery for video/IMP; add only a blocked-start preparation callback.
- Defer photo only when camera was released by this policy, then run it after committed-camera rebind reaches `READY`.
- Retry release after active capture or transition becomes idle while screen remains off.
- Keep released-camera recovery distinct from hard camera unavailability until direct rebind or deep startup reaches `READY`.
- Recheck photo eligibility after rebind before dispatching deferred capture.
- Re-sample `PowerManager.isInteractive()` after screen receiver registration and immediately before delayed release.
- Include audio-use-case state completion in the same app-wide idle-release subscription as camera flow/runtime.

## Changes Made
- Created this task journal before substantive inspection.
- Confirmed scoped editing rules, active branch, and unrelated uncommitted work before feature edits.
- Added default-on persisted `releaseCameraWhenScreenOff` policy to the existing developer camera settings store.
- Added stable DevMode setting ID, localized label/description resources, and checkbox-description rendering with unchanged no-description rows.
- Added coordinator-owned idle release state, committed-candidate rebind, wake preparation, and one deferred-photo ready queue.
- Added recording blocked-start preparation and conservative photo/audio work signals for release safety.
- Wired explicit screen on/off receiver state, idle retry after capture/runtime updates, screen-wake rebind, and DevMode persistence handling.
- Wired conservative AppComposition idle checks, camera-ready foreground release/restart, blocked video/IMP preparation, and deferred photo execution.
- Marked MainActivity screen-off state volatile because camera runtime observers may request release from a non-main thread.
- Added app-wide camera-flow/runtime observation before startup permission flow, removing release retry dependence on camera-screen UI attachment.
- Removed capability-scan timeout from idle release only; wake/capture rebind now waits for actual release completion.
- Added released-camera recovery state, deep startup fallback after failed committed bind, retained deferred photos and pending video/IMP, and repeated-press recovery retry.
- Deferred photo dispatch now rechecks current capture eligibility after camera recovery.
- Re-sampled interactive display state after screen receiver registration and immediately before delayed release; stale queued callbacks now cancel when cached state is on.
- Restored the Vietnamese toggle label to tested copy `Đóng camera khi màn hình tắt`; description and lifecycle behavior unchanged.
- Rebuilt `MainActivityStartupFlowTest.java` from `HEAD` plus only the 54 intended feature-test lines; removed 1,365 lines of unrelated formatting churn from the working diff.
- Added audio state subscriptions and folded audio completion into the same app-wide idle-release observer as camera flow/runtime.
- Added focused regression coverage for already-off startup observer ordering, nonterminal release timeout, failed committed-bind fallback with retained photo, repeated pending recording recovery, and deferred eligibility recheck.
- Added post-registration display-state and pre-release resampling source guards plus async audio completion observer coverage.

## Validation Results
- Settings/UI edit batch passed path-scoped `git diff --check`.
- Touched settings/UI files passed UTF-8 corruption and numeric-entity scans.
- Camera-flow and capture-use-case edit batch passed path-scoped `git diff --check`; original LF line endings remained LF.
- AppComposition and MainActivity wiring batch passed path-scoped `git diff --check`.
- First focused test run stopped at `compileDebugJavaWithJavac`: field lambda referenced later instance fields and caused three `illegal forward reference` errors in `MainActivity`.
- Replaced the field lambda with a method reference; behavior unchanged, Java initialization order now valid.
- Second focused run compiled and executed 99 tests; one existing source guard failed because it required the former one-line camera-state observer.
- Updated that guard to require the event observer, camera-switch refresh, and new screen-off idle-release retry in order; behavioral contract remains event-driven.
- Focused suite passed: 99 tests across developer settings persistence, camera flow, photo capture, recording coordination, audio pending work, and MainActivity architecture.
- Full `:app:testDebugUnitTest :app:assembleDebug` passed: 44 Gradle tasks, 4 executed and 40 up to date.
- Final `volatile` screen-state visibility edit occurred after that pass.
- Focused six-class rerun passed after the visibility edit.
- Full `:app:testDebugUnitTest :app:assembleDebug` rerun passed after the visibility edit: 44 Gradle tasks, 4 executed and 40 up to date.
- Journal update briefly encoded a Markdown backtick as U+000B; strict UTF-8 replacement removed it before further validation. No product file was affected.
- Full line-ending audit found 16 touched tracked files changed from repository LF to CRLF; targeted LF restoration started before further edits or device validation.
- Restored original LF endings in those 16 files. All 18 tracked feature files now match repository LF/BOM style; path-scoped diff check and strict UTF-8, control-character, mojibake, and numeric-entity scans pass.
- Device phase started on `BODYCAMERA4HHITK` with screen awake. Install uses `adb install -r` only so existing app data remains.
- Installed post-fix debug APK successfully with `adb install -r`; existing app data remained.
- Device UI shows Vietnamese lifecycle label and battery/delay description. With preference absent, switch rendered checked; off/on taps persisted `false` then `true`, and final state is enabled.
- Screen-off idle test logged successful runtime `RELEASE`, project release event, camera service disconnect, and both camera devices closed.
- Screen wake logged successful `BIND_COMMITTED` and camera service reconnect.
- Shell-UID synthetic firmware broadcasts were correctly denied by the non-exported dynamic receiver; app-UID broadcasts required explicit `--user 0`.
- App-UID off-screen photo broadcast was accepted, rebound with `BIND_COMMITTED`, completed photo capture with `PASS`, then released camera again while display remained asleep.
- App-UID off-screen video broadcast was accepted, rebound, started recording with `PASS`, stayed unreleased while recording, stopped/finalized with `PASS`, then released camera while display remained asleep.
- Device evidence exercises the exact firmware-broadcast routing code but remains synthetic; no physical button press was performed.
- Connected device detected as `BODYCAMERA4HHITK`; device validation remains active.
- Reviewer `019fda42-5743-76f2-b7b9-656c69c559e1` returned no high findings, three medium lifecycle findings, and one low deferred-eligibility finding for this feature.
- Fresh reviewer `019fda4e-2060-7c52-a4f1-4002ca8fe93b` found no high issues, two medium lifecycle gaps, and one low integrated VIDEO/IMP test gap; medium fixes started.
- Post-fix focused six-class lifecycle suite passed; fresh reviewer redirected to current fixed diff before full build.
- Post-fix full `:app:testDebugUnitTest :app:assembleDebug` passed: 44 Gradle tasks, 4 executed and 40 up to date.
- Final-medium-fix focused run compiled 104 tests; one architecture source guard failed because it selected the pre-registration display-state sample instead of searching after receiver registration.
- Corrected the source guard to search after receiver registration; final focused six-class suite passed all 104 tests.
- Full 903-test run built the APK but one architecture test rejected nested `AudioRecordingUseCase.StateSubscription`; replacing it with a plain observer-release `Runnable` keeps feature APIs within naming rules.
- Replaced the nested interface with a plain observer-release `Runnable`; targeted layer dependency, startup flow, and audio tests passed.
- Final independent review found no high- or medium-severity issues. Remaining low risk is missing one integrated failed-`BIND_COMMITTED` VIDEO/IMP fallback test and one stopped-Activity screen-off audio-completion lifecycle test.
- Final cached full `:app:testDebugUnitTest :app:assembleDebug` validation passed: 44 Gradle tasks, 8 executed and 36 up to date.
- Forced `:app:testDebugUnitTest --rerun-tasks` executed 903 tests and exposed one failure: `MainActivityStartupFlowTest.devModeIdleCameraReleaseIsDefaultOnAndCoordinatorAware()` at line 392. Diagnosis started before any behavior change.
- Diagnosis: lifecycle assertions passed; only exact Vietnamese label differed. Test requires `Đóng camera khi màn hình tắt`, while the resource contained later text `Đóng camera khi tắt màn hình`.
- Decision: preserve the explicit source-guard contract and restore only the tested localized label. Camera lifecycle behavior and requested description remain unchanged.
- Targeted rerun of `MainActivityStartupFlowTest.devModeIdleCameraReleaseIsDefaultOnAndCoordinatorAware()` passed after restoring the localized label.
- Fresh full `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` passed with all 44 Gradle tasks executed.
- Final scope inspection found `MainActivityStartupFlowTest.java` had been reformatted outside the feature hunks: raw diff grew to 881 additions and 432 deletions instead of the earlier focused 54-line addition.
- Decision: restore the file from `HEAD`, then reapply only the two focused lifecycle source guards and one DevMode contract test. Preserve current localized copy `Đóng camera khi màn hình tắt`.
- Rollback verification: `MainActivityStartupFlowTest.java` now has a focused `54 additions, 0 deletions` diff; total feature diff returned to 18 tracked files with 698 additions and 26 deletions, excluding unrelated debug manifest work.
- Post-rollback fresh `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks` passed with all 44 Gradle tasks executed.
- Final JUnit XML totals: 903 tests, 0 failures, 0 errors, 0 skipped across 124 result files.
- Final path-scoped checks passed: `git diff --check`, strict UTF-8 decoding, LF-only endings, no BOM, no invalid control characters, no mojibake, no numeric entities, and no forbidden logging in added lines.
- Reinstalled final `app-debug.apk` with `adb install -r`; `com.dvid.dcam/.app.DcamLauncherActivity` launched as PID `10239`, persisted `release_camera_when_screen_off` remained `true`, and no ANR window remained.
- Synthetic audio device validation timed out and Android showed `DCAM không phản hồi`; no audio action reached the foreground service in captured evidence, so no unrelated behavior change was made.
- Closed the ANR dialog, then relaunched `com.dvid.dcam/.app.DcamLauncherActivity`; app PID `9155` opened `MainActivity`. App data and media were not cleared.

## Completion Notes
- Required work: None.
- Delivered default-on DevMode camera release policy, localized label and battery/delay description, idle-only screen-off release, and wake/off-screen capture recovery.
- Independent final review found no high- or medium-severity issues.
- Remaining low risk: no single integrated test covers failed `BIND_COMMITTED` fallback for pending VIDEO/IMP, and no stopped-Activity lifecycle test covers audio completion followed by screen-off release.
- Device limitation: photo/video evidence used app-UID synthetic firmware broadcasts, not a physical button press. Synthetic audio validation reached an unrelated ANR without evidence of an audio action entering the foreground service; no speculative audio fix was made.
- Unrelated debug manifest, latency-probe files, and other local journals remain untouched.