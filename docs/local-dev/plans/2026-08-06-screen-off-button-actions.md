# Screen-Off Button Actions

## Status

Complete - screen-off bindings, compact developer UI, preview recovery, and duplicate-launch prevention validated.
## Objective

Keep supported button actions working while device screen is off, including capture and recording start/stop, without changing existing behavior or mappings.

## User Correction — August 6, 2026

- Existing app button behavior and camera-button mappings must remain unchanged.
- Only screen-off availability may change.
- Do not hard-map vendor intent actions to Android key codes.
- Previous hardware-validation claims were invalid because tests used an awake display or synthetic broadcasts.

## New Evidence — August 6, 2026

- User disabled the stock camera app and requested another true screen-off test.
- User confirms the stock camera app can capture while screen is off.
- Stock app source is available at `D:\Bodycam Source\sources`.
- Device package manager confirms `com.bodycamera.nettysocket` is disabled.
- Stock manifest declares package `com.bodycamera.nettysocket` with `android:sharedUserId="android.uid.system"`.
- Stock `CameraService` starts foreground, calls `initKeyReceiver()`, and dynamically registers `DeviceKeyReceiver2` for exact camera, video, record, laser, PTT, SOS, and other vendor action pairs.
- `DeviceKeyReceiver2` receives action-only intents, pairs down/up actions into semantic numeric messages, calls `SurfaceService.takePhoto()` for camera short press, and starts/stops recording factories for video/record actions.
- Stock screen-off behavior therefore uses a long-lived service plus hard-coded action-to-semantic-command dispatch; it does not recover original Android key codes or preserve DCAM configurable key-code bindings automatically.
- Before renewed validation, package manager confirmed `com.bodycamera.nettysocket` disabled and `pidof com.bodycamera.nettysocket` returned no process.
- Clean DCAM remained running, raw `/dev/input/event1` monitor started as PID `7277`, logs were cleared, and device was set to `mWakefulness=Asleep` with `mCurrentFocus=null`.
- With stock app disabled, no stock process, `mWakefulness=Asleep`, and `mCurrentFocus=null`, one real physical camera press produced `KEY_FN_F2` down/up at device uptime `9396.048–9396.268` seconds.
- Firmware again emitted `ACTION_CAMERA_DOWN` and `ACTION_CAMERA_UP`; DCAM logged no accepted hardware-button press and performed no capture while display remained asleep.
- Disabling the stock app does not restore Activity `KeyEvent` delivery; stock app presence was not the cause of DCAM's missing callback.
- With stock app still disabled and display still asleep, one real physical recording press produced `KEY_FN_F5` down/up at device uptime `9474.024–9474.252` seconds.
- Firmware emitted `ACTION_VIDEO_DOWN` and `ACTION_VIDEO_UP`; DCAM logged no accepted hardware-button press and did not start recording.
- On this device, vendor-modified `PhoneWindowManager`—not generic Android—maps custom function keys to vendor broadcasts. Stock `CameraService` consumes those broadcasts directly.
- Previous firmware-only blocker conclusion was invalidated by stock-source evidence, explicit source design, and final physical validation.
- Premise-derived production changes to revert: none; no replacement implementation was added.

## Contract

### Must

- Expose `Button` or `Switch` input type for every feature.
- Expose mutually exclusive `Firmware broadcast` and `Key event` input sources for every feature.
- Show an action-family selector plus `Works on compatible vendor firmware.` for firmware input.
- Show a keycode selector plus `Unavailable while screen is off.` for key-event input.
- Keep Button behavior as DOWN toggles start/stop and UP does not stop.
- Keep Switch behavior as DOWN starts and UP stops.
- Keep profile keycode/type defaults; default fresh/reset matched profiles to firmware broadcast.
- Migrate matched legacy `keyCode:type` preferences once to firmware broadcast while preserving keycode/type; keep unmatched legacy preferences on key events.
- Preserve feature gates, operator-session checks, SOS timing, capture policy, and existing router behavior.

### Must Not

- Convert firmware broadcasts into Android keycodes.
- Infer Button/Switch type from an action family.
- Maintain parallel feature-policy implementations for key events and broadcasts.
- Treat synthetic broadcasts or awake-display tests as physical screen-off validation.
## Evidence

### Facts

- Target `PhoneWindowManager` consumes custom function keys while non-interactive and emits action-only vendor broadcasts with no keycode, scan-code, or `KeyEvent` extra.
- Physical F5 momentary-button and F10 latching-switch probes both emitted DOWN, LONG_PRESS, and UP for `ACTION_VIDEO`; action family alone cannot identify Button versus Switch behavior.
- Activity `KeyEvent` delivery is unavailable while display sleeps; vendor broadcasts remain available.
- Runtime binding now stores role, keycode, input type, selected source, and firmware family; layout indexes only selected source.
- Firmware action strings have one domain owner and no action-to-keycode alias path.
- On `BODYCAMERA4HHITK`, final test began and ended with `mWakefulness=Asleep`; DCAM PID `26130` remained alive.
- Physical camera press at `16:57:50.885` was accepted from `ACTION_CAMERA_DOWN`; photo pipeline passed at `16:57:51.176`.
- First physical recording press at `16:57:57.495` was accepted from `ACTION_VIDEO_DOWN`; recording startup passed at `16:57:57.635`.
- Second physical recording press at `16:58:05.734` was accepted from `ACTION_VIDEO_DOWN`; recording finalized at `16:58:05.943` after an 8.167-second recording.

### Hypotheses

- None active. Firmware-broadcast support remains intentionally limited to compatible vendor firmware exposing configured action families.
## Phased Plan

- Inspect: complete. Confirmed framework key interception, firmware action protocol, stock-app consumption, and current router policy.
- Consider solutions: complete. Selected explicit per-feature source/type binding over aliases or hard mapping.
- Implement: complete. Added domain model, persistence/migration, UI, receiver lifecycle, and shared routing.
- Confirm: complete. Unit/build checks and real screen-off photo plus recording toggle passed.
## Considered Solutions

- Keep key-event-only bindings: rejected because Activity key events are unavailable while screen is off.
- Read `/dev/input` directly: rejected because production app lacks input-group and SELinux access.
- Convert vendor actions to keycodes or roles: rejected because it hard-codes configurable policy.
- Attach hidden action aliases to key bindings: rejected after user correction because source must be explicit and independently editable.
- Explicit source, action family, keycode, and Button/Switch type per feature: selected because it models actual firmware evidence and reuses one router policy.
## Decisions

- Model `Firmware broadcast` and `Key event` as first-class mutually exclusive sources.
- Persist both keycode and firmware family so switching sources does not erase either selection.
- Use explicit Button/Switch type for both sources because firmware actions do not encode physical mechanics.
- Register only selected firmware DOWN/UP actions with `RECEIVER_NOT_EXPORTED`; apply no interactive-display gate.
- Route both sources through existing `HardwareButtonRouter` feature policy.
- Consume configured firmware actions without feature execution while developer bindings screen is open.
- Default matched profiles to firmware broadcasts and migrate matched legacy values once; preserve unmatched legacy key-event behavior.
## Changes Made

- Added `HardwareButtonInputSource` and `FirmwareButtonActionFamily` domain values.
- Extended bindings and layouts with selected source and firmware family.
- Added profile firmware-family defaults while preserving keycode and Button/Switch defaults.
- Extended preference format to `keyCode:type:source:family` with matched-profile migration.
- Added requested developer controls and exact `KEYCODE_F*` labels.
- Added dynamic selected-action receiver registration and developer-screen consumption.
- Updated router transient state to use binding identity across both input sources.
- Added focused persistence, routing, startup-flow, migration, receiver, and source-guard tests.
## Validation Results

- Focused input and architecture suite passed: 48 tests.
- `./gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug` passed.
- Path-scoped `git diff --check`, strict UTF-8 scan, trailing-whitespace scan, stale-alias scan, and single-action-owner scan passed.
- Debug APK installed on `BODYCAMERA4HHITK`; persisted profile bindings use `FIRMWARE_BROADCAST` with expected types and families.
- ActivityManager confirmed selected DOWN/UP actions registered under `com.dvid.dcam.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Real screen-off photo capture passed while display remained asleep.
- Real screen-off recording Button press started recording; second press stopped and cleanly finalized it while display remained asleep.
## Completion Notes

- Status: complete.
- Required work: None.
- Deliberate compatibility ceiling: firmware-broadcast input works only on vendor firmware exposing selected action families; Key event remains available for other devices but cannot operate while screen is off.
## Switch Broadcast Probe — August 6, 2026

- Status: complete.
- Objective: identify actual broadcast/state contract emitted by connected device when user changes one physical switch.
- Contract: observe only; do not infer button-versus-switch semantics without device evidence.
- Fact: connected ADB serial is `KF5OF2126040802193`; Android 12; build `877AOOAKN1_RK2_V009`; display currently awake.
- Plan: capture raw input, full framework logcat, and ActivityManager broadcast history across one physical switch change.
- First transition result: `/dev/input/event1` emitted `KEY_FN_F10 DOWN` at uptime `261.016096`; no release occurred while switch remained in changed position.
- Framework result: `PhoneWindowManager.sendKeyButtonBroadcast` emitted `android.intent.action.ACTION_VIDEO_DOWN` at `15:22:22.173` and `android.intent.action.ACTION_VIDEO_LONG_PRESS` at `15:22:22.683`.
- App-window result: Android delivered repeated `KEYCODE_F10 ACTION_DOWN` events while switch remained changed, reaching repeat count 512 before monitor stop.
- Evidence conclusion: first edge behaves as a held key, not a self-contained momentary button press. Full switch contract remains unconfirmed until physical return edge is captured.
- Return transition result: `/dev/input/event1` emitted `KEY_FN_F10 UP` at uptime `423.396076`.
- Framework result: `PhoneWindowManager` emitted `android.intent.action.ACTION_VIDEO_UP` at `15:25:04.502`.
- Full observed sequence: switch change emitted `ACTION_VIDEO_DOWN`, emitted `ACTION_VIDEO_LONG_PRESS` after approximately 510 ms, held `KEYCODE_F10` down with repeats, then emitted `ACTION_VIDEO_UP` when returned.
- Device conclusion: firmware exposes this latching switch through the same DOWN/LONG_PRESS/UP action protocol used for a held momentary button. Broadcast contains no distinct button-versus-switch type.
- Mapping limitation: earlier F5 momentary-button evidence and current F10 switch evidence both use `ACTION_VIDEO_DOWN/UP`; an action-only receiver cannot identify which physical control sent the broadcast.
- UI consequence: firmware-broadcast binding selects an action family, while explicit Button/Switch selection supplies behavior because action names do not encode physical type.
- Probe status: complete. Required physical transitions captured; no further device action needed.
- Cleanup result: background ADB monitors stopped, temporary host captures removed, logcat cleared, and temporary `PhoneWindowManager`/`ActivityManager` VERBOSE properties restored to unset.

## Held Recording Button Probe — August 6, 2026

- Status: superseded by completed `Recording Button Hold Probe` below.
- Objective: verify whether holding and releasing the physical recording button emits the same `ACTION_VIDEO_DOWN`, `ACTION_VIDEO_LONG_PRESS`, and `ACTION_VIDEO_UP` sequence as the latching F10 switch.
- Result: completed in the consolidated probe below.
## Recording Button Hold Probe — August 6, 2026

- Status: complete.
- Objective: verify whether holding and releasing the physical recording button emits the same `ACTION_VIDEO_DOWN`, `ACTION_VIDEO_LONG_PRESS`, and `ACTION_VIDEO_UP` sequence as the F10 latching switch.
- Contract: use real physical input; capture raw input and framework broadcasts; do not infer equivalence from source alone.
- Known switch evidence: F10 change emitted `ACTION_VIDEO_DOWN`, emitted `ACTION_VIDEO_LONG_PRESS` after approximately 510 ms, and emitted `ACTION_VIDEO_UP` on return.
- Physical result: F5 emitted `KEY_FN_F5 DOWN` at uptime `12989.868119` and `KEY_FN_F5 UP` at `12996.468092`; held duration was approximately 6.600 seconds.
- Framework result: `PhoneWindowManager` emitted `ACTION_VIDEO_DOWN` at `15:48:48.919`, `ACTION_VIDEO_LONG_PRESS` at `15:48:49.441`, and `ACTION_VIDEO_UP` at `15:48:55.512`.
- Timing result: long-press broadcast occurred approximately 522 ms after down; up broadcast followed physical release.
- Comparison result: held F5 momentary button and latched F10 switch emit the same DOWN, LONG_PRESS, and UP action sequence. Difference exists in physical mechanics and state duration, not broadcast action names.
- Diagnostic note: duplicated ActivityManager warnings represent receiver-resolution diagnostics; one `ContextImpl.sendBroadcast` call and one stock receiver delivery were observed for each action.
- Device-state note: `com.bodycamera.nettysocket` is currently enabled and running as system PID `8260`; `PhoneWindowManager` stack traces still prove system framework originates these broadcasts.
- Probe status: complete. Held-button sequence physically confirmed.
- Cleanup result: ADB monitor processes stopped and temporary host captures removed. No device log properties or package state were changed for this probe.

## Discussion Correction — August 6, 2026

- Status: stopped.
- User correction: current conversation is design discussion; no stock APK inspection or implementation was requested.
- Premise-derived actions: inspected local stock source and pulled installed APK into a temporary host directory.
- Rollback: temporary APK and extracted dex files deleted. No production code, device configuration, package state, or runtime behavior changed.
- Decision: continue design discussion only unless user explicitly requests implementation or further inspection.

## Explicit Input Source Binding — August 6, 2026

- Status: complete.
- Objective: implement per-feature Input type and Input source configuration in developer mode, then use selected source for runtime delivery including screen-off firmware broadcasts.
- Must: expose `Button` or `Switch` input type per feature.
- Must: expose mutually exclusive `Firmware broadcast` and `Key event` sources per feature.
- Must: show firmware action-family selector and description `Works on compatible vendor firmware.`
- Must: show keycode selector and description `Unavailable while screen is off.`
- Must: preserve current behavior: Button toggles start/stop; Switch DOWN starts and UP stops.
- Must: keep device-profile whitelist keycode and input-type defaults.
- Must: default app bindings to Firmware broadcast while taking default input type from device profile.
- Must not: convert broadcast action families into keycodes at runtime.
- Must not: change feature gates, operator-session rules, SOS timing, capture rules, or unrelated screen-on behavior.
- Fact: user explicitly replaced implicit alias behavior with an editable source/type model.
- Fact: broadcast and key-event paths must converge on the existing feature router rather than duplicate feature policy.
- Resolved hypothesis: binding aliases were replaced by a first-class source field and persisted action-family value.
- Resolved hypothesis: profile metadata seeds default action families without runtime action-to-keycode mapping.
- Phases: inspect complete; consider solutions complete; implement complete; confirm complete; device validation complete.
- Inspect result: current persistence stores only `keyCode:type`; current UI exposes only keycode and type; layout indexes keycodes and aliases simultaneously; receiver rejects broadcasts while display is interactive; router transient state identifies broadcast-triggered holds and switches through associated keycodes.
- Inspect result: `SettingsControlRenderer` already supports described radios plus enabled and indented child choices; project has no Robolectric dependency but has a small `PreferenceAccess` test-seam pattern.
- Decision: add explicit `HardwareButtonInputSource` and `FirmwareButtonActionFamily`; keep profile keycode/type metadata and family defaults, persist selected source separately, and index only selected source at runtime.
- Decision: matched legacy `keyCode:type` values migrate once to `FIRMWARE_BROADCAST`; unmatched legacy values remain `KEY_EVENT`; fresh and reset matched profiles use `FIRMWARE_BROADCAST` and profile input type.
- Decision: both input paths resolve one binding and reuse existing router policy; broadcast actions never convert to keycodes.
- Resume fact: worktree is clean at implementation start; prior alias design, if present, is committed repository state rather than pending edits.
- Change batch: added explicit `HardwareButtonInputSource` and `FirmwareButtonActionFamily`; binding now stores keycode, input type, selected source, and firmware family without aliases.
- Change batch: layout now indexes only selected key-event or firmware-broadcast source and exposes selected firmware actions for receiver registration.
- Change batch: device profiles now keep keycode/type defaults plus explicit firmware family metadata; BWC F10 switch defaults to `ACTION_VIDEO`.
- Change batch: preferences now persist stable `keyCode:type:source:family` values, default fresh/reset profiles to firmware broadcast, retain legacy two-field values as key events, and preserve keycode/family selections independently.
- Change batch: developer UI now renders `Input type`, described `Input source` radios, and source-specific `Action family` or `Keycode` choices for each feature.
- Change batch: router uses binding identity for switch and SOS transient state; key and firmware paths share existing policy without converting actions to keycodes.
- Change batch: Activity registers only selected firmware actions with `RECEIVER_NOT_EXPORTED`, accepts them while screen is on or off, and refreshes registration after binding changes.
- Test batch: added preference regressions for broadcast defaults, profile switch type, legacy key-event migration, independent key/family persistence, and duplicate family clearing.
- Test batch: replaced alias router tests with explicit firmware Button toggle, firmware Switch DOWN/UP, and cross-source rejection coverage.
- Test batch: added source guard for requested developer UI, selected-action receiver registration, `RECEIVER_NOT_EXPORTED`, no interactive-display rejection, and no alias path.
- Validation result: production and test compilation passed; 47 focused tests passed and one architecture source test failed because its helper looked for a core-module file under the app source root.
- Validation result: core-source helper fixed; all behavior tests still pass. One source assertion failed because source labels are owned by preferences, while the screen consumes them through the use case.
- Validation result: source-label ownership assertion fixed; all behavior tests still pass. One receiver guard sliced at the nested `@Override` before the receiver body.
- Validation result: focused input and architecture suite passed, 48 tests total.
- Validation result: `:core:test`, `:app:testDebugUnitTest`, and `:app:assembleDebug` passed.
- Behavior review finding: firmware broadcasts currently bypass the developer key-console consumption path and could trigger configured features while bindings are being edited.
- Behavior review finding: a matched device upgrading from legacy `keyCode:type` preferences would remain on key events and still fail screen-off behavior until manual reset.
- Architecture review finding: action-family UI order currently depends on `enum.values()` instead of an explicit ordered option list.
- Decision: consume firmware actions on the developer bindings screen, migrate legacy matched-profile roles once to firmware broadcast while preserving saved keycode/type, keep unknown-device legacy bindings as key events, and define explicit action-family order.
- Review fix batch: matched-profile legacy values now migrate once to firmware broadcast while retaining saved keycode/type; unmatched devices retain key-event behavior.
- Review fix batch: firmware broadcasts are consumed without feature execution on the developer bindings screen, matching key-console behavior.
- Review fix batch: action-family options now use an explicit ordered list rather than enum declaration order.
- Test update: added matched/unmatched legacy migration cases, explicit family-order assertion, dependent-control state guards, and developer-screen broadcast consumption guard.
- Validation result after behavior-review fixes: focused input suite and full core/app tests plus debug build passed.
- Architecture review result: domain/application layers have no Android imports; exact firmware action strings have one owner; persisted values use stable names; no alias path or enum ordinal dependency remains.
- Architecture review cleanup: renamed settings initialization port because it now performs migration as well as first-write initialization; copied preference snapshots before migration.
- Final validation result: `:core:test`, `:app:testDebugUnitTest`, and `:app:assembleDebug` passed after review cleanup; path-scoped diff, UTF-8, trailing-whitespace, stale-alias, and single-action-owner checks passed.
- Device result: debug APK installed successfully on `BODYCAMERA4HHITK` with data preserved; app launched; persisted bindings are `FIRMWARE_BROADCAST` with profile types/families; ActivityManager shows all selected DOWN/UP actions registered under `RECEIVER_NOT_EXPORTED`.
- Physical result: with display asleep, camera press completed photo capture; first recording press started recording; second recording press stopped and finalized an 8.167-second video.
- Completion result: required work complete; no remaining implementation or validation step.

## UI Nesting Correction - August 6, 2026

- Status: complete.
- User correction: current screen renders both `Action family` and `Keycode` after the complete source radio group instead of beneath their owning radio option.
- Must: render `Action family` immediately beneath `Firmware broadcast`, followed by its compatibility description.
- Must: render `Keycode` immediately beneath `Key event`, followed by its screen-off limitation description.
- Must: keep source-specific selector enabled only for its selected source.
- Must not: change runtime binding behavior, defaults, migration, or screen-off delivery.
- Phases: correction recorded; inspect complete; consider solutions complete; implement complete; confirm complete.
- Inspect fact: `DeveloperButtonBindingsScreen.section()` creates `Action family` and `Keycode` as sibling top-level `SettingItem` rows after the complete `Input source` radio row.
- Inspect fact: `DescribedRadioOptionUiState` currently supports only label, description, and enabled state; renderer has no option-owned child control.
- Considered: custom one-off screen rendering was rejected because it would duplicate settings control behavior and styling.
- Decision: let a described radio option own one nested choice, render that choice immediately after its radio button, and use the choice description row for the source-specific note.
- Scope: change presentation model, generic renderer, developer binding model, and focused tests only; runtime binding behavior remains untouched.
- Production change: each `DescribedRadioOptionUiState` can own one nested `CHOICE` item.
- Production change: renderer now emits radio button, owned choice, then choice description before the next radio button.
- Production change: developer binding sections now contain only top-level `Input type` and `Input source`; `Action family` and `Keycode` belong to their corresponding source options.
- Production change: refresh and enabled-state logic now skips interleaved non-radio children and preserves nested selector state.
- Test change: added nested-choice ownership coverage and a source guard proving developer selectors render through the option-owned choice path.
- Validation result: focused `SettingItemTest` and `MainActivityStartupFlowTest` passed; debug Java compilation succeeded.
- Validation result: full `:app:testDebugUnitTest` and `:app:assembleDebug` passed.
- Device result: installed hierarchy is `Firmware broadcast`, nested `Action family`, compatibility description, `Key event`, nested `Keycode`, screen-off description.
- Device result: selecting `Key event` disabled `Action family` and enabled `Keycode`; restoring `Firmware broadcast` reversed those states.
- Completion Notes: Required work: None.
- Final validation result: path-scoped diff check, strict UTF-8 scan, corruption scan, and line-ending preservation checks passed.
## Inactive Source Selector Guard - August 6, 2026

- Status: complete.
- User correction: radio selection currently does not reliably prevent changing the binding selector owned by the inactive source.
- Must: inactive `Action family` or `Keycode` selector must reject opening and selection changes.
- Must: preserve inactive selector value so switching source later restores the saved choice.
- Must not: change source selection, runtime routing, migration, or screen-off behavior.
- Fact: nested choice rows receive disabled visual state from their selected source.
- Hypothesis: relying on Android enabled state alone is insufficient because nested choice popup and selection callbacks lack an explicit `SettingItem.isEnabled()` guard.
- Phases: correction recorded; reproduce complete; diagnose complete; implement complete; confirm complete; final checks complete.
- Reproduce result: installed row reports inactive `Keycode` as disabled, matching visual state; user confirms interaction still changes the binding.
- Diagnose fact: `SettingsControlRenderer.choice()` checks only optional `canOpen`; normal developer rendering supplies no policy callback, and popup selection callback has no row-enabled recheck.
- Decision: reject choice opening when either row or value view is disabled, then recheck both before accepting popup selection.
- Production change: SettingsControlRenderer.choice() now checks row and value enabled state before opening a popup and again before applying a selected value.
- Test change: source guard asserts both disabled-state checks remain present.
- Validation result: focused `MainActivityStartupFlowTest` passed; debug Java compilation succeeded.
- Validation result: full `:app:testDebugUnitTest` and `:app:assembleDebug` passed.
- Device result: tapping inactive `KEYCODE_F5` produced no popup (`ListView` count 0; one keycode node remained).
- Device result: after selecting `Key event`, the active Keycode selector opened its popup (`ListView` count 1; nine keycode nodes).
- Cleanup result: popup closed and Record source restored to `Firmware broadcast`; Keycode returned to disabled state.
- Final validation result: path-scoped diff check, strict UTF-8 scan, corruption scan, line-ending check, and explicit guard source assertions passed.
- Completion Notes: Required work: None.
## Final UI Semantics Correction and Preview Freeze - August 6, 2026

- Status: complete.
- User correction: source radio selects which binding source is active; it must not disable, gray, or block editing the other source selector.
- Required order: `Firmware broadcast` radio, compatibility description, `Action family` combo; then `Key event` radio, screen-off description, `Keycode` combo.
- Must: keep both `Action family` and `Keycode` visible, enabled, and editable regardless of selected radio.
- Must: preserve each selector value independently while radio controls runtime source selection only.
- Must: inspect connected-device frozen preview from runtime evidence before changing camera behavior.
- Must not: change routing, persistence, migration, screen-off handling, capture policy, or camera lifecycle without evidence.
- Invalidated premise: inactive source selectors should be disabled and reject interaction.
- Premise-derived changes to revert: source-dependent `.withEnabled(...)` on nested selectors; explicit disabled-row popup guards added for that premise; source assertions and device expectations requiring inactive-selector blocking.
- Preview fact: user reports connected-device preview is frozen after current validation work.
- Preview diagnosis: duplicate HOME and standard `MainActivity` tasks shared one retained preview surface; HAL stayed live while visible preview consumption stalled.
- Phases: correction, preview recovery, UI implementation, and confirmation complete.
- Preview evidence: DCAM PID `28226` is the active CameraService client for camera 0; no current camera-service denial or device error is present.
- Preview evidence: current process opened camera 0, committed retained runtime binding, and logged first preview frame at `17:34:52.670`.
- Preview evidence: Activity recreated at `17:42:45` while developer bindings were open; the new camera screen attached retained preview and logged another first frame at `17:42:45.884`.
- Preview evidence: recent DCAM logs contain no camera disconnect, capture-session error, or frame-stall report after that recreation.
- Diagnostic step: compare camera preview pixels across timed screenshots to distinguish a visually frozen surface from a live but static scene.
- Preview reproduction: three screenshots captured approximately 1.2 seconds apart while camera screen was visible.
- Preview result: sampled the `480x272` preview region at 8,160 points; frame 1 vs 2 and frame 2 vs 3 each had `0` changed samples and mean RGB delta `0`.
- Preview conclusion: connected-device preview is genuinely frozen, not merely a static scene; camera-service ownership alone does not prove frame delivery.
- Diagnostic step: compare SurfaceFlinger presentation timestamps and CameraService request/result counters to locate the stalled boundary.
- Camera HAL result: request numbers advanced continuously around 30 fps with successful stream buffers and results while preview pixels remained unchanged.
- SurfaceFlinger result: visible `MainActivity#1` buffer timestamps advanced only about once per second, matching status-clock redraws rather than camera frames.
- Boundary conclusion: camera/HAL capture is live; freeze lies between camera output consumption and visible preview composition.
- Lifecycle clue: SurfaceFlinger lists two DCAM `ActivityRecord` layers and multiple `MainActivity` window layers after the `17:42:45` Activity recreation.
- Diagnostic step: inspect Activity stack and retained preview attach/detach ownership before changing camera code.
- Activity-stack result: DCAM has two `MainActivity` tasks in one process: resumed HOME task `#1573` and stopped standard task `#1571`.
- Window result: HOME-task window `6f3ad7a` is visible with a surface; stopped standard-task window `cd68eef` has no surface but its Activity remains in history.
- Operational fact: validation launched `MainActivity` explicitly with `am start -n`, creating the standard task; later device HOME handling created the separate HOME task.
- Root-cause hypothesis: process-retained camera gateway and two Activity preview views left visible HOME preview attached to a stale or non-driving consumer.
- Diagnostic step: inspect gateway single-preview ownership and detach timing, then recover device with one HOME task if source agrees.
- Source result: every Activity creates a new `SharedCameraPreviewView` around one process-retained `SurfaceHandle`/`SurfaceTexture`.
- Source result: attaching a new preview clears the previous view callback but the stopped Activity and its TextureView remain alive until `onDestroy`; `onStop` does not release its capture runtime.
- Source result: manifest `singleTask` does not prevent separate HOME-type and standard tasks created by different launch intents on this firmware.
- Root cause: explicit validation launch created a standard task, then HOME launched a second Activity using the same process SurfaceTexture; HAL stayed live while visible TextureView stopped consuming frames.
- Decision: no camera production change. Recover device by force-stopping both tasks and launching DCAM only through HOME; all remaining device validation must use HOME, not `am start -n MainActivity`.
- Phase update: connected-device recovery started. Force-stop DCAM, launch through HOME only, confirm one task, then compare timed preview screenshots.
- Recovery result: force-stop plus HOME launch created one resumed DCAM HOME task `#1575`; no standard DCAM task remained.
- Preview recovery result: three screenshots about 1.2 seconds apart showed `8041/8160` and `7978/8160` changed preview samples; preview is live again.
- Recovery conclusion: duplicate-task validation state caused the freeze. No camera production change required.
- Phase update: corrected developer binding UI implementation started. Scope is renderer order, removal of source-dependent selector disabling, and removal of premise-derived disabled-row guards/tests only.
- UI change batch: removed source-dependent `.withEnabled(...)` from both nested selectors, so radio selection no longer grays or disables either saved binding value.
- UI change batch: nested described-radio layout now renders radio, standalone compatibility description, then nested choice; nested choice no longer owns the description.
- UI change batch: removed redundant disabled-row popup guards and inverted architecture assertions to preserve always-editable selectors.
- Phase update: focused source diff and tests started before device UI validation.
- Focused validation result: `MainActivityStartupFlowTest` and `SettingItemTest` passed.
- Diff validation result: path-scoped `git diff --check` passed; mojibake and numeric-entity scans found no introduced corruption.
- Phase result: corrected nested control editability implementation complete.
- Phase update: full test/build and connected-device UI validation started.
- Full validation result: `:app:testDebugUnitTest` and `:app:assembleDebug` passed; debug APK ready for HOME-only device launch.
- Device UI result: rendered order is radio, description, nested selector for both sources; both selectors are visible, full-opacity, enabled, and clickable.
- Selector interaction result: with `Firmware broadcast` selected, both Action family and inactive Keycode popups opened; with `Key event` selected, the inactive Action family popup also opened.
- Preference preservation result: final Record source restored to `Firmware broadcast`; `ACTION_CAMERA` and `KEYCODE_F5` remained unchanged.
- Final preview result: after returning to camera, timed screenshots showed `7752/8160` and `7785/8160` changed preview samples; preview remained live.
- Final task result: one resumed DCAM HOME task `#1577`; no standard DCAM task remained.
- Final validation result: focused tests, full `:app:testDebugUnitTest`, `:app:assembleDebug`, path-scoped diff checks, UTF-8 scans, device UI interaction, and preview liveness checks passed.

### Completion Notes

- Required work: None.
- Camera production changes for preview freeze: None.
- Remaining compatibility limit: firmware broadcast input requires compatible vendor firmware; Key event remains unavailable while screen is off.
- Superseded validation rule: HOME-only launch was required before duplicate-launch prevention below.
## Duplicate MainActivity Prevention — August 6, 2026

- Status: complete.
- User correction: operational recovery is insufficient; app must remain safe if a standard `MainActivity` launch occurs while HOME task exists.
- Must: prevent concurrent `MainActivity` instances from attaching separate views to one process-retained preview surface.
- Must: preserve HOME behavior, current bindings, camera lifecycle semantics, and normal settings navigation.
- Must not: patch camera frame production or reset user preferences.
- Fact: current debug APK can create separate HOME and standard tasks on vendor firmware despite manifest `singleTask`.
- Diagnosis: malformed standard launch must be redirected before `AppComposition.create(...)`, using a different Activity trampoline on this firmware.
- Phases: reproduce, solution selection, implementation, failure diagnosis, and confirmation complete.
- Reproduction result: cold `am start -n com.dvid.dcam/.app.MainActivity` created standard task `#1578`; pressing HOME then created separate HOME task `#1580`.
- Preview result during this reproduction: timed frames still changed, so duplicate tasks are a confirmed unsafe state but not a deterministic immediate freeze trigger.
- Redirect experiment: cold explicit `ACTION_MAIN` + `CATEGORY_HOME` created one HOME task `#1584`; HOME reentry reused it.
- Redirect experiment: sending explicit HOME intent while standard task `#1585` was active created separate HOME task `#1587`, allowing the standard instance to finish before any composition or preview attachment.
- Decision: make launcher target HOME directly and add an early `MainActivity` non-HOME redirect before `AppComposition.create(...)`; ignore non-HOME `onNewIntent` values so later recreation retains HOME identity.
- Expected files: `DcamLauncherActivity.java`, `MainActivity.java`, and `DcamKioskLifecycleTest.java`.
- Phase result: reproduction and solution selection complete.
- Phase update: minimal launch normalization implementation started.
- Change batch: launcher now sends `CATEGORY_HOME` directly, avoiding cold standard-task creation during normal app-icon launch.
- Change batch: `MainActivity` redirects any non-HOME cold launch before composition, then removes the malformed task.
- Change batch: non-HOME `onNewIntent` values are ignored so an existing HOME Activity retains HOME launch identity across recreation.
- Test batch: added source guards for pre-composition redirect, HOME intent, malformed-task removal, and normalized reentry.
- Phase update: focused diff and lifecycle tests started.
- Focused validation result: `DcamKioskLifecycleTest` passed and production Java compilation succeeded.
- Diff validation result: path-scoped `git diff --check`, mojibake scan, and numeric-entity scan passed.
- Phase result: minimal launch prevention implementation complete.
- Phase update: full build and cold-launch device validation started.
- Full validation result: `:app:testDebugUnitTest` and `:app:assembleDebug` passed.
- Failed device validation: cold standard launch entered redirect, but redirected Activity crashed during `onDestroy` because normal cleanup unconditionally unregistered receivers that early return never registered.
- Crash evidence: `IllegalArgumentException: Receiver not registered` at `MainActivity.onDestroy`; HOME task did not survive and standard task remained stopped.
- Decision: mark redirect-only Activity before launch and make `onDestroy` call only `super.onDestroy()` for that uninitialized instance.
- Phase update: redirect-only lifecycle cleanup fix started.
- Cleanup fix batch: redirect-only Activity now marks itself before launching HOME and bypasses all uninitialized normal cleanup in `onDestroy`.
- Test update: lifecycle source guard now requires redirect flag assignment and redirect-only `onDestroy` branch.
- Phase update: focused test and second cold-launch validation started.
- Focused validation result: lifecycle test and debug APK build passed.
- Second device validation: redirect-only cleanup no longer crashed; ActivityTaskManager logged HOME start, but no DCAM Activity task survived and Settings resumed.
- Device-specific inference: `finishAndRemoveTask()` removed the redirected task container too broadly on this firmware.
- Decision: use plain `finish()`; redirected standard root becomes empty and Android can remove it without removing the newly created HOME task.
- Phase update: finish semantics correction started.
- Finish correction: redirect-only Activity now calls plain `finish()` after starting HOME; regression source assertion updated.
- Phase update: third cold-launch device validation started.
- Third device validation: plain `finish()` avoided crashes but same-component HOME start still produced no surviving DCAM Activity; process remained while Settings resumed.
- ActivityTaskManager evidence: standard `MainActivity` START was followed by same-component HOME START, then both Activity records disappeared.
- Decision: same-component self-redirect is unsupported on this firmware. Route malformed launch through existing no-display `DcamLauncherActivity`, which then starts HOME as a different Activity transition.
- Phase update: launcher-trampoline redirect started.
- Trampoline change: malformed `MainActivity` now starts `DcamLauncherActivity` and finishes; launcher starts the HOME-category `MainActivity`.
- Test update: cold-launch source guard now requires launcher trampoline while launcher guard retains exact HOME-category assertion.
- Phase update: fourth cold-launch device validation started.
- Fourth device validation passed: cold direct `am start -n MainActivity` ended with one resumed HOME task `#1595`, no standard DCAM task, and no crash.
- Preview result after direct cold launch: timed frames changed `7776/8160` and `7768/8160` samples.
- Repeat-launch result: another standard explicit start while HOME was active delivered to the existing instance; HOME reentry kept the same single task.
- Normal launcher validation passed: cold `DcamLauncherActivity` launch ended with one resumed HOME task `#1598`, no crash, and `7772/8160` changed preview samples.
- Phase result: task duplication prevention and preview liveness device validation complete.
- Phase update: full test suite and final diff/UTF-8 gate started.
- Final full validation result: `:app:testDebugUnitTest` and `:app:assembleDebug` passed.
- Final source validation result: full `git diff --check`, path-scoped UTF-8 corruption scan, numeric-entity scan, and stale self-redirect scan passed.

### Completion Notes

- Required work: None.
- Final behavior: cold launcher and cold direct `MainActivity` launches both normalize to one HOME task before camera composition.
- Final behavior: standard launch delivered to an existing HOME Activity is ignored, preserving HOME intent identity and preview ownership.
- Camera pipeline changes: None.
- Compatibility ceiling: `MainActivity` remains HOME-only; future deep links require explicit routing before the non-HOME guard.
## Nested Radio Description Styling — August 6, 2026

- Status: complete.
- User correction: nested button-binding descriptions look much worse than prior described-radio controls.
- Must: render label and description with the same compact multiline radio styling used by existing camera pipeline choices.
- Must: keep nested Action family or Keycode selector after the radio description.
- Must: keep both nested selectors visible, enabled, and editable regardless of selected source.
- Must not: change binding persistence, routing, launch prevention, or camera behavior.
- Fact: current nested path renders a 48dp radio row, then a separate description row, creating excessive vertical gap and different alignment.
- Diagnosis: standalone nested description caused the mismatch; existing `describedRadioText(state)` restores prior compact typography and alignment.
- Phases: inspect, implementation, build validation, and device confirmation complete.
- Source result: non-nested described radios already use `describedRadioText(state)`, which produces the compact smaller gray second line shown in the camera pipeline UI.
- Source result: nested radios instead render label-only RadioButton plus standalone TextView, causing different spacing and typography.
- Decision: use `describedRadioText(state)` for every described RadioButton, delete standalone nested description row, and keep nested choice immediately after the RadioButton.
- Expected files: `SettingsControlRenderer.java` and `MainActivityStartupFlowTest.java`.
- Phase result: inspect and solution selection complete.
- Phase update: compact multiline radio implementation started.
- Change batch: every described RadioButton now uses `describedRadioText(state)`, including options with nested selectors.
- Change batch: removed standalone nested description TextView; nested choice remains immediately after the multiline RadioButton.
- Test update: source guard now requires styled radio text before nested choice and forbids standalone nested description row.
- Phase update: focused tests and device visual confirmation started.
- Build validation result: full `:app:testDebugUnitTest` and `:app:assembleDebug` passed; diff and UTF-8 checks passed.
- Device validation retry condition: prior serial alias `BODYCAMERA4HHITK` disappeared; connected device is now exposed as serial `KF5OF2126040802193`.
- Phase update: device visual confirmation resumed with current serial.
- Device visual result: Firmware broadcast and Key event each render label plus smaller gray description inside one compact RadioButton, matching prior camera pipeline controls.
- Device layout result: nested Action family follows immediately after the compact radio text; no standalone description row or large dead gap remains.
- Device state preservation result: connected BWC profile remained `Switch` with `ACTION_VIDEO`; validation did not change bindings.
- Device UI state result: rendered controls remained enabled; only styling changed.

### Completion Notes

- Required work: None.
- Final validation: full `:app:testDebugUnitTest`, `:app:assembleDebug`, diff/UTF-8 checks, and connected-device screenshot passed.
