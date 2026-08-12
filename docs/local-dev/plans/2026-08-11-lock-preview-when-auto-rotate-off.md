# Lock Preview When Auto-Rotate Is Off

- Status: Blocked — exact HAL transition identified, but no direct signal is available to the ordinary app process.
- Objective: Detect the exact direct signal when the BWC HAL flips the input stream or the rotating camera module changes front/back state. Do not change transforms until that signal is proven.

## Contract

### Must

- Catch a direct HAL, Camera2 metadata, system input, or vendor state transition exactly when stream orientation/mirror state changes.
- Keep sensor posture, firmware flip state, and display rotation as distinct inputs.
- Preserve all unrelated local identity, storage, audio, encryption, CI, and camera-output changes.

### Must not

- Change preview/image/video transforms before a direct transition signal is reproduced and correlated.
- Infer hidden HAL state from current device angle, a 180-degree branch, scene pixels, or continuous frame scanning.
- Change enabled auto-rotate behavior in this slice.

## Evidence

### Facts

- User confirms target BWC is connected, physically upside down at 180 degrees, with auto-rotate off.
- Prior focused log showed app-owned preview compensation changed 0 to 180 while display remained `ROTATION_0`.
- Removing sensor compensation previously fixed this rotate-off case.
- Current source rollback restored `MainActivity.refreshDisplayRotationIfNeeded` to the `HEAD` behavior and removed the disabled-auto-rotate guard.
- Current source has no `OrientationEventListener` or locked preview compensation path.
- August 11, 2026 user correction: `rotate-off-r180-explicit-after-guard.png` is visibly inverted. At capture time `accelerometer_rotation=0`, `user_rotation=0`, display rotation was `0`, and source contained no sensor compensation.
- The guard-only validation therefore failed the user-visible requirement. It proves only that the app stopped changing display-driven rotation; it does not stabilize HAL-transformed input pixels.
- August 11, 2026 user correction: do not change preview pixels until the exact HAL flip transition is caught. Required evidence is a direct event/state signal for stream flip or physical module front/back mode, not a current-angle heuristic or scene-pixel guess.
- CameraService advertises `com.mediatek.control.capture.flipmode` only as a request key. It does not advertise `com.mediatek.singlehwsetting.module` or `.transform` as request, result, or characteristic keys.
- The current raw Camera2 latest-result dump contains neither `flipmode`, `module`, nor `transform`; tag monitoring produced no events for those tags.
- SensorService history identifies `camerahalserver` PID 788 as `hidl_client_pid_788`, subscribing to accelerometer and gyroscope while camera processing runs.
- `camerahalserver` has no open input, hall, hinge, or switch device. The only physical switch input is `/dev/input/event1`; the controlled camera-module transition emitted no event and InputReader retained `SwitchValues: 0`.
- Root-only kernel logs expose the exact OV4689 state as `set_mirror_flip()image_mirror = 1` or `3`; the driver repeats the current value while streaming.
- No app-readable mirror state exists in Camera2, Surface input metadata found so far, sysfs, properties, or `/proc/driver/camera_info`. `/dev/kmsg` is root-only and unprivileged `dmesg` is denied.
- The first controlled motion is invalid as transition evidence: the sensor powered on only after the motion and initialized at the final `image_mirror=1` state.
- Valid live-stream trace `build/rotation-validation/bwc-investigation/controlled-hal-flip-r0-to-r180-20260811-194428-kernel.txt` captured `image_mirror=1` at kernel timestamp `4106.734131`, then the exact change to `image_mirror=3` at `4137.893409`.
- At the exact change, Android auto-rotate, user rotation, and display orientation remained `0`. Adjacent raw accelerometer callbacks were `x=-9893,y=67,z=-450` at `4137.805772` and `x=-9749,y=28,z=-565` at `4137.894054`.
- The exact flip signal is therefore inside the OV4689/kernel-HAL path, not a Camera2 result, display event, input switch, or SurfaceTexture transform event.
- `/system/xbin/su` is executable only by root or shell (`4750 root:shell`). `run-as` retained shell supplementary groups during the diagnostic, but the actual app process has neither shell group nor capabilities, so this does not provide an app path to kernel logs.
- Vendor Binder inspection found registered `IBGService` and `IISPModule`; `IATMs` was not registered. `IBGService` exposes frame completion/status/timestamp callbacks, and `IISPModule` exposes offline ISP configure/process/result operations. Neither exposes mirror, flip, orientation, or physical module-facing state.
- The exact transition is therefore observable in root-only kernel output, but not consumable by this ordinary `untrusted_app` process.

### Hypotheses

- Invalidated hypothesis: guarding display refresh alone does not restore upright preview when the current HAL buffer is already inverted.
- Remaining hypothesis: the afternoon success addressed an app-owned second rotation, while the current BWC hidden HAL state supplies an independently inverted buffer.

## Phased Plan

- Inspect: complete — source has no compensation path; display refresh lacks only the auto-rotate guard. BWC `KF5OF2126040802193` reports `accelerometer_rotation=0`, `user_rotation=0`, display rotation `0`.
- Consider solutions: complete — add only the auto-rotate guard at display-refresh owner.
- Implement: complete — added one auto-rotate guard and focused source assertions; no compensation code added.
- Confirm: failed — code/test/build/install passed, but current preview remained visibly inverted.
- Detect exact HAL transition: complete — valid live trace caught kernel `image_mirror` changing `1` to `3` while display rotation stayed `0`.
- Check vendor Binder bridge: complete — no registered service inspected exposes mirror, flip, orientation, or physical module-facing state.
- Decide integration path: blocked — a privileged kernel reader would satisfy direct detection; sensor-history inference would not.

## Considered Solutions

- Re-add sensor compensation: rejected by user contract and prior reproduction.
- Add a 180-degree branch: rejected because current angle does not uniquely identify HAL state.
- Guard display refresh while auto-rotate is disabled: retained as correct app-layer behavior, but proven insufficient to stabilize HAL-transformed input.
- Privileged/root companion reading the kernel state: technically viable, but requires device provisioning or boot integration outside an ordinary APK.
- Sensor-history state machine: technically possible, but rejected for this slice because it infers hidden HAL state instead of catching the exact direct event.

## Decisions With Rationale

- Do not reintroduce compensation. `refreshDisplayRotationIfNeeded` must return before reading/applying display rotation when auto-rotate is disabled.
- Do not add a preview/image/video transform from the kernel trace alone. The app still lacks an app-readable transition source.
- Keep the current guard separate from HAL correction; it prevents app-owned display rotation changes only.

## Changes Made

- Created this task journal.
- Confirmed current installed target and source owner before editing.
- Updated `MainActivity.refreshDisplayRotationIfNeeded` to return when auto-rotate is disabled.
- Extended `SharedCameraGatewaySourceTest.healthyRotationDeduplicatesRefreshAndSuppressesIntermediateLogs` to require the guard before display access and reject sensor compensation state.
- Path-scoped diff, CRLF preservation, compensation-token scan, and `git diff --check` pass.
- Focused regression `SharedCameraGatewaySourceTest.healthyRotationDeduplicatesRefreshAndSuppressesIntermediateLogs` passed.
- `:app:assembleDebug` passed. APK installed successfully on BWC `KF5OF2126040802193`.
- First device launch via `monkey` changed `accelerometer_rotation` to `1`, invalidating that sample. Repeated validation used explicit `com.dvid.dcam/.app.DcamLauncherActivity`; `accelerometer_rotation=0`, `user_rotation=0`, and display rotation `0` remained stable.
- Current preview capture: `build/rotation-validation/rotate-off-r180-explicit-after-guard.png`. User confirmed it remains inverted; guard-only visual validation failed.
- Completed vendor Binder inspection; no app-readable mirror/flip/orientation service contract was found.

## Validation Results

- Guard source test, debug build, and APK install passed.
- Device visual validation failed: HAL-provided pixels remain inverted while display rotation and app compensation stay at zero.
- Direct transition validation passed at the diagnostic layer: kernel `image_mirror` changed `1 → 3` during live streaming while display state stayed unchanged.
- App integration validation is blocked: the exact state is absent from Camera2 results, input events, readable system state, and vendor Binder APIs.

## Remaining Risks / Next Step

- Blocker: exact state transition exists only in root-only OV4689/kernel logging; current app sandbox exposes no direct event or state for it.
- No transform fix can satisfy the current direct-signal contract without adding a signal source.
- Feasible path 1: provision a privileged/root companion that reads the kernel transition and publishes a narrow app-readable state.
- Feasible path 2: explicitly permit a sensor-history state machine that infers HAL state; this remains indirect.
- Until one path is approved, leave preview/image/video transforms unchanged.
