# Delay Camera Release After Screen Off

## Status
Complete

## Objective
Delay idle camera release until one minute after the screen turns off, and cancel the pending release when the screen turns on.

## Contract
### Must
- Start one 60-second release deadline when the screen turns off or the Activity starts while the display is already off.
- Restart the full 60-second deadline when an accepted photo/video hardware command occurs while the screen is off, including recording stop.
- Cancel the pending release when the screen turns on.
- Recheck screen state and camera idleness before release.
- Preserve off-screen capture, recording, wake recovery, saved-notice timing, and final-Activity release behavior.

### Must not
- Reset the deadline for unrelated idle camera-state callbacks.
- Release while capture, photo finalization, audio work, or camera transition is active.
- Change the default-on developer setting or its description.
- Clear app data or captured media.

## Evidence
### Facts
- `MainActivity` currently posts `idleScreenOffCameraRelease` immediately whenever the screen is off.
- User clarified that an off-screen capture must restart the full 60-second delay before camera release.
- Screen-off capture enters through accepted hardware key or firmware-broadcast down/up handling; video stop may occur on either a second down action or an up action depending on binding type.
- Screen-on handling already removes the pending callback before rebinding the camera.
- `AppComposition.releaseIdleCameraForScreenOff()` already performs the authoritative setting and idle-state checks.

### Hypotheses
- A fixed uptime deadline plus explicit restart on accepted off-screen hardware commands can preserve state-change retries without extending for unrelated callbacks.

## Phased Plan
- [Complete] Inspect current scheduling and tests.
- [Complete] Consider minimal deadline ownership.
- [Complete] Add focused regression.
- [Complete] Implement delayed scheduling and cancellation.
- [Complete] Confirm focused tests, full build, encoding, and diff.
- [Complete] Review final lifecycle behavior.

## Considered Solutions
- Rejected plain `postDelayed` on every camera-state callback: unrelated callbacks could postpone release indefinitely.
- Rejected a synchronous idle query inside runtime callbacks: runtime listeners may execute while the process camera owner lock is held.
- Selected one uptime deadline owned by `MainActivity`; generic state callbacks preserve it, accepted off-screen hardware down/up commands restart it, and screen-on clears it.

## Decisions
- Use `SystemClock.uptimeMillis()` and `Handler.postAtTime()` so camera-state retries preserve the existing deadline.
- Consume the deadline before attempting release. If capture is still active, a later state callback starts a new 60-second delay; if release starts, cancel any callback scheduled synchronously by release-state publication.

## Changes Made
- Created this task journal before substantive inspection.
- Added architecture regression requirements for a 60-second uptime deadline, screen-on cancellation, consumed-deadline retry behavior, and hardware down/up deadline restart.
- Added one `SystemClock.uptimeMillis()` deadline in `MainActivity`; screen-off starts/restarts it, screen-on and toggle-off clear it, generic state callbacks preserve it, and accepted hardware down/up commands restart it.
- Release execution consumes the deadline before existing `AppComposition.releaseIdleCameraForScreenOff()` safety checks; successful release clears callbacks created by synchronous camera-state publication.
- Kept authoritative `screenOff` refresh immediately after the runtime null guard to preserve existing startup-flow contract.

## Validation Results
- Focused regression command failed as expected before production edit:
  - `./gradlew.bat :app:testDebugUnitTest --tests com.dvid.dcam.architecture.MainActivityStartupFlowTest.screenOffCameraReleaseWaitsOneMinuteAndCaptureRestartsDelay --rerun-tasks`
  - Failure: expected one-minute deadline symbols and scheduling logic are absent at `MainActivityStartupFlowTest.java:402`.
- Focused regression passed after implementation: same command, `BUILD SUCCESSFUL` in 13 seconds.
- Adjacent focused run completed 91 tests with one failure: `cameraSwitchStateUsesEventsInsteadOfClockPolling` expected `releaseIdleCameraIfScreenOffNow()` to refresh `screenOff` immediately after the null guard.
- Decision: move deadline consumption after authoritative screen-state refresh and before release checks; release semantics remain unchanged.
- Same adjacent focused run passed after reorder: 91 tests, `BUILD SUCCESSFUL` in 11 seconds.
- `git diff --check -- <touched paths>` passed; only existing Git autocrlf warnings reported.
- Strict UTF-8 checks passed for production source, regression test, and journal: no BOM, LF-only, no mixed endings, replacement characters, mojibake markers, or numeric entities.
- Scheduling scan found one absolute `postAtTime`, no stale immediate/delayed posts, centralized callback removal, and all four hardware down/up restart sites.
- Full validation passed: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`, 44 tasks, `BUILD SUCCESSFUL` in 27 seconds.
- Installed `app-debug.apk` on `BODYCAMERA4HHITK` with `adb install -r`; PID `20486` launched `MainActivity`, screen awake, app data preserved, and release setting remained at default-on because no persisted override exists.
- Wake-cancellation device check passed: screen off at 14:04:52, awake at 14:05:07, inspected after the original one-minute deadline at 14:05:58, and no idle-camera release log occurred.
- Uninterrupted screen-off check passed: screen off at 14:06:27.949; camera release completed at 14:07:28.616 while display remained asleep, 60.7 seconds later.
- Off-screen cold photo check passed: firmware photo accepted at 14:08:19.316, camera rebound, photo pipeline passed at 14:08:20.445, no release at the 45-second inspection, and release completed at 14:09:19.847 while display remained asleep.
- Photo command restarted release to 60.5 seconds after accepted DOWN action. Test media was preserved.
- Off-screen video check passed: recording started after camera rebind, second firmware DOWN stopped and finalized `DCAM_000005_B01OPR_20260807_141008.mp4`, no release at the 45-second inspection, and release completed at 14:11:16.794.
- Video stop restarted release to 60.5 seconds after accepted stop DOWN action; firmware UP used the same restart path.
- Restored display awake after validation. PID `20486` remained alive, `MainActivity` regained focus, camera reopened and reached `BIND_COMMITTED`, preview resumed, and no DCAM ANR or fatal exception appeared.
- Final touched-path checks passed after journal completion: `git diff --check`, strict UTF-8, no BOM, LF-only endings, and no corruption or numeric-entity markers.

## Completion Notes
- Required work: None.
- Delivered one 60-second screen-off deadline, wake cancellation, and full deadline restart after accepted off-screen photo/video hardware commands, including video stop.
- Existing idle/safety checks, default-on DevMode toggle, capture behavior, saved notices, app data, and media remain preserved.
- Device validation used same-UID synthetic firmware broadcasts through the persisted production action families; no physical button press was repeated.
- Validation created one photo and `DCAM_000005_B01OPR_20260807_141008.mp4`; both remain on device.