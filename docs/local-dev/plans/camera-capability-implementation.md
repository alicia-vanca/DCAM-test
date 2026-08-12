# Camera Capability and Shared Runtime Record

**Status:** Implemented and validated 2026-07-28; reviewed 2026-08-01

## Purpose

Record current camera capability behavior, architecture boundaries, and validation evidence in one canonical document.

## Architecture contract

- `feature/**` stays pure Java and never imports Android, `app`, or `platform`.
- UI and `MainActivity` never import `platform`; `AppComposition` owns production wiring.
- Use cases remain concrete final classes; interfaces exist only at external-system seams under `application/port`.
- Camera/platform code logs through `core/.../logging/application/port/Logger`; direct Android logging stays inside `platform/logging`.
- Capability snapshot, requested settings, effective runtime tuple, process camera ownership, and production gateway each have one owner.
- Capture, storage, foreground service, media finalization, encryption, MD5, and hardware-button contracts remain unchanged behind camera implementation changes.

## Capability behavior

- Fast scan builds exact per-camera and per-pipeline `V-F`, `I`, and raw `V-F-I` inventory; deep verification never upgrades unsupported or unknown evidence by assumption.
- Requested `V/F/I` settings persist per camera and remain UI authority. `currentRecordingTuple` is effective verified runtime authority and may use a different `I_runtime`.
- Startup deep-verifies only the main camera requested/default tuple. Inactive-camera edits persist without opening or verifying that camera until activation.
- Durable tuple pass/fail facts drive fallback reuse. `UNKNOWN`, timeout, cancellation, transient failure, incomplete scan, or storage blockage never prune candidates or replace the last valid snapshot.
- Recording fallback order is runtime image, FPS, then video. FPS is removed only after every fast-supported runtime image is definitively unsupported; video is removed only after no FPS remains.
- Standalone JPEG evidence remains separate from recording-tuple evidence. A successful wrong-size JPEG is retained, marks that image option unsupported, and applies fallback to the next capture; transient failure preserves selection.

## Runtime ownership

- `CameraCapabilityService` owns one process-scoped `ProcessCameraRuntimeOwner`; scans, settings transitions, preview, recording, photo capture, and recovery share it.
- Capability recheck reserves an exclusive scan lease and releases runtime ownership before probing. Release failure leaves capability unavailable instead of opening a competing camera owner.
- Runtime transitions hold at most one record start and one photo command, consume start-then-stop before readiness, preserve held commands across Activity recreation, and drop them on process cancellation.
- Active setting changes preserve busy checks, verification, persistence, rollback, runtime restoration, and pending-command behavior. Inactive setting changes are persistence-only.
- Failed transition restores the previous verified effective tuple without reverting requested settings or inventing evidence.

## Pipelines and persistence

- Pipeline A and Pipeline B keep independent implementations and evidence while sharing exact `PRIVATE(V,F) + JPEG(I_runtime)` topology and one production gateway contract.
- `AUTO` prefers a strict verified-set superset; incomparable sets use best verified tuple, pass count, common-tuple performance, then Pipeline A as deterministic final tie-break. Incomplete runs do not change the durable recommendation.
- Capability XML is atomic and explicit about incomplete versus reusable state. Reusable XML cold start direct-binds the committed tuple; invalid, incomplete, or failed bind state returns to verified rebuild flow.
- New installs and missing or corrupt global pipeline preferences default to Pipeline A. `AUTO` and Pipeline B require explicit developer selection; unset per-camera values inherit the global mode.

## Validation evidence

- Final target-device acceptance passed on BodyCamera `BODYCAMERA4HHITK`, Android 12 / API 31, including reusable XML cold start, static inventory, headless tuple verification, rollback, scan-lease ordering, startup mask gating, settings ownership, and both pipelines.
- Device evidence: `docs/local-dev/evidence/cam-imp-21-device-acceptance-2026-07-28.md`.
- Orientation acceptance remains in `docs/local-dev/camera-orientation-contract.md`; it stays separate because it governs preview, JPEG, video, UI, physical rotation, and evidence collection beyond capability rollout.
