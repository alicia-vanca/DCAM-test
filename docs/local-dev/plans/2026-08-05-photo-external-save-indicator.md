# Photo External Save Indicator

## Status
Complete.

## Objective
Show `Đang lưu` while photo data is being saved, especially when external storage takes about half a second, matching the existing video finalize feedback.

## Contract
### Must
- Show visible saving feedback during photo save completion.
- Reuse existing video finalize UI behavior where practical.
- Keep feedback active until photo save finishes or fails.
- Preserve current photo capture and storage behavior.

### Must not
- Change storage destination, save ordering, or media lifecycle semantics.
- Leave the busy state stuck after success or failure.
- Introduce a second competing progress system.

## Evidence
### Facts
- User reports external photo saving can take about 500 ms.
- Video finalize already exposes an `Đang lưu` state suitable for reuse.
- Photo finalization is asynchronous in `SharedCameraRuntimeBackend` and reports only terminal success or failure.
- `MainActivity` already shows persistent `media_saving` feedback from capture state.
- Photos may be captured while video recording, so photo saving must not reuse video-only `saving` semantics.
- Worktree contains broad existing changes; edits must preserve unrelated work in target files.

### Hypotheses
- Photo flow currently ends capture UI before storage completion.
- A separate photo-saving state can reuse the existing notice without changing recording semantics.

## Phased Plan
- [x] Inspect — located async photo finalization, capture events, capture state, and persistent saving notice. Complete.
- [x] Consider solutions — kept video finalizing state separate and added photo-saving state. Complete.
- [x] Implement — applied focused event, state, UI, and regression-test changes. Complete.
- [x] Confirm — focused tests, compile, whitespace, UTF-8, and entity checks passed. Complete.

## Considered Solutions
- Reuse `CaptureState.saving` directly: rejected because photo capture is allowed during video recording and this flag suppresses recording UI.
- Show notice directly from storage/platform code: rejected because it bypasses existing capture-state UI lifecycle.
- Add separate photo-saving capture events and state: chosen as smallest behavior-safe reuse.

## Decisions
- Emit photo-saving immediately before asynchronous photo finalization.
- Track and replay photo-saving through `SerializedRecordingCoordinator`.
- Show persistent saving notice when either video finalization or photo finalization is active.
- Clear photo-saving on photo success or photo failure without changing video state.

## Changes Made
- Added this task journal.
- Added photo-saving and photo-failure capture events.
- Added independent photo-saving state preserved across video and audio state updates.
- Emitted photo-saving before asynchronous photo finalization and replayed it on capture-event rebind.
- Reused `MainActivity` persistent `media_saving` notice for either video or photo finalization.
- Added regression coverage for backend event order, state clearing, saving-state replay, and persistent notice wiring.
- Repaired malformed journal text introduced by an earlier journal-only edit; source files were unaffected.

## Validation Results
- `./gradlew.bat :app:testDebugUnitTest --tests "com.dvid.dcam.platform.camera.shared.SharedCameraRuntimeBackendTest" --tests "com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinatorTest" --tests "com.dvid.dcam.app.ui.MainViewModelCaptureBindingTest" --tests "com.dvid.dcam.architecture.MainActivityStartupFlowTest"` passed twice; final run completed in 2 seconds.
- Main and unit-test Java compilation passed through the focused Gradle task.
- Path-scoped `git diff --check` passed. Git reported an existing LF-to-CRLF warning for `SharedCameraRuntimeBackendTest.java`; this task preserved its LF endings.
- Path-scoped mojibake and numeric-entity scans returned no matches.
- Touched saving-state and notice hunks were inspected after edits.

## Completion Notes
Required work: None.
- Photo save feedback now spans asynchronous finalization success or failure.
- Existing unrelated worktree changes remain untouched.
