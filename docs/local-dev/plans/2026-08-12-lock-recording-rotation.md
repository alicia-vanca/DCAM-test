# Lock Recording Rotation

## Status

In progress — inspect phase.

## Objective

While video recording, lock UI and preview rotation. Photo capture must still use current device rotation to choose horizontal or vertical output.

## Contract

### Must

- Freeze UI rotation for recording duration.
- Freeze preview rotation for recording duration.
- Keep photo orientation responsive to current rotation while recording.
- Preserve existing recording, photo capture, lifecycle, and fallback behavior outside this split.

### Must not

- Reuse frozen recording/UI rotation as photo orientation input.
- Change orientation behavior when not recording.
- Add parallel rotation state without evidence existing state cannot serve each owner.
- Weaken existing tests or unrelated behavior contracts.

## Evidence

### Facts

- User explicitly separates UI/preview rotation from photo orientation during recording.
- Existing uncommitted changes add an auto-rotate guard in `MainActivity.refreshDisplayRotationIfNeeded` and source assertions in `SharedCameraGatewaySourceTest`; they belong to prior task and must be preserved.
- `MainActivity` owns display-change callbacks and calls `runtime.refreshDisplayRotation()`.
- `SharedCameraRuntimeBackend.refreshDisplayRotation()` currently forwards one refresh to pipeline provider, where preview and capture rotation may share update flow.
- `CaptureState.isVideoRecording()` exposes recording state to UI layer.
- `ProcessCameraRuntimeOwner.capturePhoto()` permits photo capture while runtime state is `RECORDING`.

### Hypotheses

- One shared rotation state may currently drive both preview/UI and photo capture orientation.
- Smallest fix may preserve live sensor rotation for capture while gating only display-facing consumers during recording.

## Phased Plan

- Inspect — complete: display rotation currently drives Activity configuration, preview transforms, encoder metadata, and JPEG orientation through one shared pipeline rotation. Recording-state events are available in `MainActivity`; backend knows whether photo capture occurs during active video.
- Consider solutions — complete: freeze Activity orientation and suppress preview refresh during video recording; pass a separate sensor-derived rotation only to each recording-time JPEG request.
- Implement — in progress: add recording UI/preview lock, live photo rotation source, explicit JPEG rotation input, and focused tests.
- Confirm — pending: run focused tests, diff checks, and scope review.

## Considered Solutions

- Gate only display-facing consumers while video recording: selected because it preserves existing preview/video orientation state and avoids shared-state mutation.
- Add a dedicated low-rate `OrientationEventListener` source for recording-time photos: selected because locked Activity display rotation no longer reports physical posture.
- Reuse pipeline `setRotation` before photo capture: rejected because it would rotate preview/video metadata and violate the contract.
- Change standalone photo orientation: rejected because existing non-recording behavior must remain unchanged.

## Decisions With Rationale

- Keep display rotation and photo capture orientation as distinct behavior domains because user explicitly requires different recording-time behavior.
- Supply JPEG rotation as an explicit capture argument during active recording; never write it into shared pipeline rotation state.
- Preserve current pipeline rotation for standalone photos and all video metadata.

## Changes Made

- Created this task journal.

## Validation Results

- Not run.

## Remaining Risks / Next Step

- Trace current rotation ownership and identify exact shared state or consumer gate.