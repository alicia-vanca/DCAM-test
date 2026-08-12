# DCAM MVP Delivery Plan

> **Historical snapshot.** This plan preserves decisions and evidence from its update date. Do not use its package names or architecture conventions for new code. Use [`ARCHITECTURE.md`](../../../app/src/main/java/com/dvid/dcam/ARCHITECTURE.md) and [`FEATURE_DEVELOPMENT_GUIDE.md`](../../../app/src/main/java/com/dvid/dcam/FEATURE_DEVELOPMENT_GUIDE.md) instead.

**Confluence refresh note:** 2026-07-15 gap report is `docs/local-dev/evidence/confluence-refresh-gap-report-2026-07-15.md`. Treat it as current local gap triage before expanding Build 0.1 scope.

- Status: historical implementation plan
- Prepared from repository state: 2026-07-09
- Last Confluence scope review: 2026-07-15
- Active build profile: DCAM MVP Internal Build 0.1
- Active delivery gate: Working Recording Slice
- Contract baseline: DCAM–BDMA Data Contract, Confluence version 9
- Delivery baseline: DCAM Architecture Delivery Profile, Confluence version 4

Current checkpoint: the repository uses the approved smaller `:app`/`:core` Gradle shape;
password-first login, boot-scoped operator sessions and initial Room auth tables exist; credentials
are stored as bcrypt cost-10 hashes with embedded salts; 55 local tests and `assembleDebug` pass. This is a
useful Build 0.2 foundation, but it is not Build 0.1 acceptance evidence and must not delay the
Working Recording Slice.

## 1. Objective

Turn the current capture prototype into the active Build 0.1 Working Recording Slice on a selected
BodyCamera:

```text
open app
  -> check camera/storage permission and free-space budget
  -> accept recording/capture commands through one serialized coordinator
  -> record 30 seconds through the RecordingController / CameraService boundary
  -> write to Temp/staging
  -> stop and finalize
  -> publish to the final Media folder
  -> capture one sample image
  -> write the minimal dcam.db / dcam_config.cson / Logs/logs.txt outputs
  -> allow BDMA to detect and import the sample media through ADB
```

The MVP is not complete when the app merely creates a playable MP4. It is complete when the app can
finalize video and image artifacts in the supported contract layout, expose the minimum local
database/config/log contract, and pass BDMA sample import without a critical crash or corrupt file.
Full operator identity, cloud provisioning, full recovery, full kiosk policy and other Build 0.2+
platform concerns remain valid target work, but they are not Build 0.1 release blockers.

## 2. Planning rules

1. Build-level applicability comes from the Release & Build Applicability Matrix. A document-level
   P0 does not automatically block Build 0.1.
2. Reliability and preservation of evidence-like artifacts outrank secondary features and UI polish.
3. Logging, the minimal database/config contract, storage and app bootstrap remain available
   offline and must not depend on a deferred cloud feature.
4. Disabled or later-phase surfaces must not execute hidden background behavior from the critical
   startup or recording path.
5. Do not add a Gradle module, runtime component, state, table or interface unless it directly serves
   the Working Recording Slice or an approved Build 0.1 safety requirement.
6. Hardware-sensitive choices require a real BodyCamera POC. Emulator success is not acceptance
   evidence.
7. Existing class names are not proof that a capability is implemented. Acceptance is based on
   runnable behavior, measurable budgets and the applicable QA gate.

### 2.1 Authoritative sources for this revision

| Source | Snapshot version | Planning effect |
|---|---:|---|
| [Release & Build Applicability Matrix](../../dcam-knowledge/confluence-original/DCAM/02-Sprint-Operations/DCAM-Release-&-Build-Applicability-Matrix.md) | 2 | Owns Build 0.1 applicability and release blockers |
| [DCAM MVP Scope](../../dcam-knowledge/confluence-original/DCAM/01-Product-Management/DCAM-MVP-Scope.md) | 9 | Defines the Working Recording Slice and acceptance criteria |
| [DCAM 9-Month Development Plan](../../dcam-knowledge/confluence-original/DCAM/02-Sprint-Operations/DCAM-9-Month-Development-Plan.md) | 8 | Maps Build 0.1 to Phase 1, Sprints 1–6 |
| [Architecture Delivery Profile](../../dcam-knowledge/confluence-original/DCAM/04-Technical-Documentation/4.1-Software-Architecture/DCAM-Architecture-Home/DCAM-Architecture-Delivery-Profile.md) | 4 | Limits Phase 1 architecture and runtime state |
| [DCAM–BDMA Data Contract](../../dcam-knowledge/confluence-original/DCAM/03-Requirements/DCAM-BDMA-Data-Contract.md) | 9 | Defines media/config/database/log paths and BDMA behavior |
| [Concurrency & Threading Model](../../dcam-knowledge/confluence-original/DCAM/04-Technical-Documentation/4.2-Technical-Design/DCAM-Concurrency-&-Threading-Model-Design.md) | 3 | Requires serialized critical-path execution lanes |
| [Performance Budget](../../dcam-knowledge/confluence-original/DCAM/04-Technical-Documentation/4.2-Technical-Design/DCAM-Performance-Budget-&-Resource-Constraints.md) | 2 | Supplies measurable Build 0.1 targets |
| [Logging & Diagnostics Design](../../dcam-knowledge/confluence-original/DCAM/04-Technical-Documentation/4.2-Technical-Design/DCAM-Logging-&-Diagnostics-Design.md) | 4 | Defines local-first logging and the sanitized `logs.txt` artifact |

## 3. Current-state assessment

### 3.1 Capability matrix

| Build 0.1 capability | Current repository evidence | Assessment | Build 0.1 action |
|---|---|---|---|
| Project/layer skeleton | Feature-first packages, pure-Java `:core`, Android `:app`, ports/use cases, platform adapters, composition root and cross-module architecture tests | Present for current scope | Preserve the two-module shape; split another module only when an approved trigger exists |
| Runtime bootstrap | `AppComposition` constructs adapters and starts cloud-state work; startup is not yet focused on recording readiness | Partial | Initialize only the local recording/storage/DB/config/log prerequisites; defer cloud/platform work off the critical path |
| Recording host boundary | `RecordingForegroundService` shows a notification, is `START_NOT_STICKY`, and does not own CameraX/recording/finalization | Prototype only | Prove the minimum `RecordingController` / `CameraService` boundary on the selected BodyCamera |
| Operator login/session | Password-first login UI/use cases and Room auth/session tables already exist | Conditional foundation | Preserve if explicitly enabled, but do not expand full auth or make it a Build 0.1 blocker |
| Recording authority | `VideoRecordingUseCaseImpl` delegates directly to `CameraGateway`; CameraX adapter owns mutable active recording | Missing authority | Add the small single-threaded recording/capture/finalization coordinator required by Build 0.1; do not build the full platform state machine |
| Camera service/adapter | CameraX photo/video works behind `CameraGateway`; target-device and screen-off behavior unverified | Prototype | Keep adapter; complete real-device POC and error mapping |
| Temp/final/BDMA_READY | `Temp` folder is created, but camera/audio currently target final media paths directly; no durable finalization state | Missing | Implement staging, finalization and explicit readiness |
| Minimal SQLite output | Room v1 covers several platform tables, but there is no proven Build 0.1 media/finalization fixture for BDMA | Partial foundation | Implement only the minimal schema/state needed by the supported Build 0.1 contract and sample import |
| Media session/finalization state | No media-session/finalization repository | Missing | Add the smallest transaction boundary needed to prevent partial media from becoming BDMA-ready |
| Local-first logging | Local logs, rotation, Loggly outbox and context exist; stable contract export is not proven | Partial | Produce UTF-8, single-record-per-line, sanitized `Logs/logs.txt`; provider delivery remains conditional/deferred |
| Recovery basics | No DB/file reconciliation or safe mode | Missing | Preserve and classify interrupted Temp/final candidates needed for a safe slice; full recovery platform remains Build 0.2+ |
| BDMA-readable contract | Media folders and filenames are partly aligned; no physical-path proof, lifecycle DB, embedded compatibility metadata, MD5 workflow or E2E fixture | Partial | Complete the minimum contract path and prove it with BDMA |
| Boot/kiosk | Boot receiver, Home intent and Device Owner/Lock Task scaffolding exist | Conditional | Run only the Lock Task POC needed for device validation; full kiosk policy is deferred |
| Device awareness | Battery, free app storage and GPS capability/status are available | Partial but useful | Keep status; add recording precondition thresholds after ADR |

### 3.2 Important current hazards

- Camera ownership is bound to `MainActivity`; the notification service does not preserve or recover
  recording after Activity/process loss.
- Media is allocated in final `Media/*` locations, so BDMA can observe a file before DCAM has proven
  that it is finalized.
- There is no persisted single-active-recording invariant. Process restart can lose in-memory
  authority.
- Login-screen navigation and hardware input are session-gated even though full authentication is
  deferred for Build 0.1; this existing path must remain usable without pulling Build 0.2 auth work
  into the release gate.
- Passwords are no longer persisted as plaintext. The remaining auth risks are the default six-digit
  development credential, missing failed-attempt throttling/lockout policy, and unmeasured bcrypt
  latency on target BodyCamera hardware.
- Encryption can be configured, but key management, BDMA decryption and performance approval are
  unresolved. Hiding the Security screen alone is not sufficient; the Build 0.1 profile must enforce
  its approved deterministic policy and leave production key management to Build 0.2.
- `AppComposition` performs cloud/no-op remote-config initialization during startup even though
  advanced remote configuration is not required for the local-recording MVP.
- Demo settings controls resemble completed features but do not persist or apply approved operational
  settings.

### 3.3 Implementation progress checkpoint

| Increment | Status | Evidence / remaining boundary |
|---|---|---|
| Approved small Gradle shape | Complete | `:app` depends one-way on independently compiled/tested `:core` |
| Password-first login and operator session | Implemented | User ID is login credential; login DB reset is available without deleting media; JVM and device login verified |
| Password protection at rest | Implemented | bcrypt cost 10 with embedded per-hash salts; no plaintext credential column; no PBKDF2 compatibility verifier |
| Capture session enforcement | Implemented foundation | Navigation and hardware capture require active operator session |
| Serialized recording authority | Complete | UI, hardware and camera events route through one coordinator; focused tests pass |
| Temp staging and safe publication | Complete | Video, image and audio stage outside `Media`; verified publication and conservative recovery exist |
| Storage selection and capacity handling | Complete | Internal/External/Auto resolution and capture prechecks have focused tests |
| Activity-independent recording lifetime | Complete | App-scoped composition/coordinator survive Activity recreation |
| Audio physical power-loss recovery | Complete on reference device | External `Temp` retest recovered one AAC with `preserved=0`; see audio device evidence |
| Video destructive recovery proof | In progress | Force-stop/reboot evidence exists; remaining production-profile repetitions stay open |
| BDMA ADB import fixture | Pending | Finalized sample video/image import remains Build 0.1 gate |

### 3.4 Immediate execution order

This is the canonical execution checklist. Do not maintain parallel status checklists.

- [x] Force Build 0.1 encryption default off.
- [x] Serialize recording commands and camera completion events.
- [x] Stage video, image and audio under selected `Temp`.
- [x] Add capacity prechecks and safe storage-full rejection.
- [x] Validate and publish staged media without overwrite.
- [x] Preserve recording state across Activity recreation.
- [x] Prove external-audio physical battery-loss recovery on `BODYCAMERA4HHITK`.
- [ ] Complete destructive video recovery repetitions on applicable production storage profile.
- [ ] Prove 30-second video plus image capture on selected release firmware.
- [ ] Export and inspect sanitized local `Logs/logs.txt` fixture.
- [ ] Run shared ADB fixture and prove BDMA detects/imports sample media.
- [ ] Measure required concurrency, performance, thermal and long-running stability budgets.
- [ ] Attach final device/firmware evidence, known limitations and decision references.

Specialized procedures and results remain separate because they are evidence, not competing plans:

- Hardware procedure: [`sprint-0-poc-test-plan.md`](sprint-0-poc-test-plan.md)
- Current repository state: [`../current-repo/current-state.md`](../current-repo/current-state.md)
- Audio device evidence: [`../evidence/build-0.1-audio-device-tests-2026-07-14.md`](../evidence/build-0.1-audio-device-tests-2026-07-14.md)
- Confluence/code gap report: [`../evidence/confluence-refresh-gap-report-2026-07-15.md`](../evidence/confluence-refresh-gap-report-2026-07-15.md)

## 4. MVP product surface

### 4.1 Keep visible or implement now

| Surface/capability | MVP treatment |
|---|---|
| Operator login | Conditional: preserve the existing implementation if enabled; no new full-auth work may block Build 0.1 |
| Camera operation screen | Keep; show recording state, storage state and controlled errors |
| Video start/stop | Keep and route through the recording authority |
| Image capture | Keep and apply storage/finalization rules |
| Emergency/SOS recording | Defer unless explicitly added through the applicability matrix |
| Files/media browser | Keep read-only and limited to finalized `Media` folders |
| About/diagnostics | Keep non-sensitive version/device/diagnostic information |
| Battery/storage/capability status | Keep available; optional capability failure must not crash capture |
| Local operational logging | Required and local-first; stable sanitized `Logs/logs.txt` is the BDMA-facing artifact |
| Hidden developer controls | Debug/internal builds only; not evidence of operational settings |

### 4.2 Hide until a real implementation is scheduled

Default the following product surfaces off for the MVP build. Hiding a surface must not disable
shared platform infrastructure.

| Surface | Reason/condition to re-enable |
|---|---|
| Standalone audio recording | Not in the Phase 1 video/image minimum; re-enable with approved use case and artifact lifecycle |
| Recording settings | Current controls are demo state; re-enable after DB-backed requested/applied settings exist |
| Camera settings | Re-enable after Camera POC exposes supported values and writes are validated |
| Audio settings | Re-enable with an approved audio requirement and persisted applied state |
| Storage settings | Current Internal/External/Auto mapping and fallback are incomplete; retain read-only storage status |
| GPS settings | Keep capability detection; re-enable settings with location permission, sampling and persistence design |
| Device settings | Split language/identity from unsupported demo controls before re-enabling |
| Security settings | Build 0.2+; re-enable after encryption/key/BDMA decision and security review |
| Cloud/network settings | Build 0.2+; local recording and logging must remain operational offline |
| Transfer | FTP/RTSP transfer is not the current ADB-based BDMA boundary |
| Video streaming/PTT/JT808 | Later phase; no MVP runtime initialization |
| Self Update UI/automatic install | Build 0.2+; manual internal deployment is sufficient for Build 0.1 |
| Advanced user/auth, face, QR and NFC | Build 0.2+ or later according to the applicability matrix |
| Loggly/Crashlytics provider integration | Conditional/deferred for Build 0.1; provider failure may never block local logging or capture |
| Realtime AI | No recording-core dependency; add later behind a proven capability boundary |

### 4.3 Code behavior for hidden capabilities

- Do not merely hide a tile while starting its worker/provider at boot.
- Remove advanced remote-config refresh from the critical startup chain. Device identity and required
  operational state belong to runtime/data ownership, not to a cloud feature.
- Keep future package README files as documentation only; do not add placeholder interfaces.
- Retain existing feature code only when it is tested, harmless, and not on the MVP critical path.
- Release configuration must explicitly list enabled product surfaces; it must not inherit a
  developer's local toggle history.

### 4.4 Scope decisions that must not remain implicit

| Tension | Required decision |
|---|---|
| Self Update and Remote Config are target capabilities but deferred by the active matrix | Keep them out of Build 0.1 startup and release criteria |
| Encryption is Build 0.2 foundation work while current source can encrypt media | Force a deterministic Build 0.1 policy and prevent accidental encryption from legacy config |
| The Data Contract is broader than Build 0.1 applicability | Freeze the smallest contract-compatible DB/CSON/log/media fixture jointly with BDMA; do not implement every target table |
| Dedicated kiosk is approved direction but deferred/POC-only in Build 0.1 | Record POC evidence if needed; do not block the Working Recording Slice on full Device Owner policy |
| Existing auth work is ahead of active Build 0.1 scope | Preserve and test the enabled path, but schedule expansion/acceptance under Build 0.2 |
| Standalone audio exists in source but is absent from the first video/image MVP baseline | Hide it unless Product explicitly adds it with the same session/finalization/recovery guarantees |

## 5. Target MVP boundaries

The names below describe responsibilities. Final names may follow an ADR, but ownership must remain.

```text
UI / hardware input
  -> optional existing Build 0.1 auth guard (only when enabled)
  -> single-threaded recording command coordinator
      -> storage precondition
      -> CameraExecutor / recording engine
      -> FileIoExecutor / staging and finalization
      -> DbExecutor / minimal media and readiness state
  -> UI state on MainThread
  -> BdmaExportService for media/config/database/log fixtures
```

### 5.1 Application-owned boundaries

- Minimal runtime bootstrap for local logging, DB/config, storage and recording readiness.
- Start/stop/image-capture commands through one serialized authority.
- Camera/recording engine port selected after POC.
- Recording host boundary required by the selected Android/device POC.
- Minimal media/finalization state repository.
- Staging/finalization storage port.
- Storage-health/precondition port.
- Build 0.1 BDMA export/fixture boundary.
- Basic interrupted-artifact reconciliation where required for safe finalization.

### 5.2 Platform implementations

- Room/SQLite repositories for the minimum Build 0.1 state.
- Android foreground-service host.
- CameraX, Camera2 or vendor recording engine selected by the device POC.
- Internal/external filesystem or MediaStore implementation selected by the storage POC/decision.
- Permission, storage-health and basic device-status adapters.

No application/domain class may import Android, CameraX, Room, filesystem, WorkManager or a cloud
provider.

## 6. Persisted MVP model

Freeze the smallest Build 0.1 DDL/output fixture with BDMA before implementing more platform tables.
Room is already present, but the release gate is contract behavior, not completion of the full target
schema.

The broader [Database DDL v1 design](database-ddl-v1-design.md) and
[reference SQL](database-ddl-v1.sql) remain the Build 0.2 target proposal. Build 0.1 may select only
the reviewed subset needed for media readiness, compatibility metadata and BDMA sample import.

Development schema note: Room v1 has not shipped, so plaintext removal and bcrypt adoption were
folded into the exported v1 schema. Existing development installs containing plaintext or PBKDF2
credentials must clear application data, reinstall, or reprovision users; there is intentionally no
production compatibility verifier or migration for those unshipped credential formats.

### 6.1 Required for Build 0.1

| Area/table | Minimum fields/behavior |
|---|---|
| Schema/compatibility metadata | DB schema version, app package/version, data/media/encoder contract versions |
| Local identity/config | Minimum local `serial_number` and device-information fields required by the supported CSON/DB fixture; no cloud provisioning dependency |
| Media state | Stable media/session ID, type, start/stop time, state, staging path and final path needed by the sample flow |
| Finalization/readiness | Finalization result, safe failure reason and `BDMA_READY` or equivalent contract state |
| Existing auth tables | May remain when the current login path is enabled, but no additional full user/auth schema is required for Build 0.1 |

### 6.2 Deferred platform schema

- full user/auth synchronization and operator lifecycle
- full runtime, foreground-service, eligibility and policy state
- requested/applied settings ownership beyond the Build 0.1 fixture
- `media_import_state`
- `external_change_log`
- cloud identity/provisioning cache beyond the local minimum

These become applicable in Build 0.2 or through explicit change control. Diagnostic events remain in
bounded local logs unless the approved Build 0.1 fixture requires otherwise.

### 6.3 Transaction invariants

1. At most one active video recording row.
2. The staging identity exists before the final path can become visible to BDMA.
3. A final path is not `BDMA_READY` until the file is closed, validated and published.
4. If the existing login path is enabled, its operator snapshot is stable; lack of full auth does not
   block the no-login/basic-placeholder Build 0.1 profile.
5. BDMA cannot update active media lifecycle fields.
6. Unknown or ambiguous interrupted artifacts are preserved, never silently deleted.
7. Filename inputs required by the supported Build 0.1 contract are validated before allocation.

## 7. Recording and finalization state model

Use explicit states rather than inferring truth from a file's existence:

```text
REQUESTED
  -> PRECHECKED
  -> STARTING
  -> RECORDING
  -> STOP_REQUESTED
  -> FINALIZING
  -> COMPLETED / BDMA_READY

Any active state
  -> FAILED
  -> RECOVERY_REQUIRED
  -> RECOVERED or CORRUPTED
```

Minimum reason codes include:

- `OPERATOR_AUTH_REQUIRED` (only when the conditional auth profile is enabled)
- `RECORDING_ALREADY_ACTIVE`
- `CAMERA_PERMISSION_REQUIRED`
- `MICROPHONE_PERMISSION_REQUIRED`
- `CAMERA_FAILED`
- `FOREGROUND_SERVICE_START_FAILED`
- `STORAGE_UNAVAILABLE`
- `STORAGE_LOW`
- `STORAGE_FULL`
- `STAGING_CREATE_FAILED`
- `FINALIZATION_FAILED`
- `DATABASE_BUSY`
- `DATABASE_CORRUPTED`
- `RECOVERY_REQUIRED`

Reason codes are domain/application values. SDK exception text may be logged safely but must not
become the product state contract.

### 7.1 Finalization order

1. Allocate session ID and staging destination.
2. Persist the minimum requested/start state; include the operator snapshot only when conditional
   auth is enabled.
3. Start the foreground host and recording engine.
4. Record only to `Temp`/staging or an equivalent pending MediaStore artifact invisible to BDMA.
5. Stop and await the engine's final callback.
6. Close/flush output and validate non-empty readable media.
7. Apply only the deterministic encryption policy approved for Build 0.1.
8. Move/rename atomically when supported; otherwise use a verified copy/fsync/publish strategy from
   the storage ADR.
9. Persist final path, completion and `BDMA_READY` in one controlled workflow.
10. Publish/scan the final media only after readiness.
11. If MP4 checksum is enabled, generate it asynchronously after `BDMA_READY`; checksum pending or
    failure must not block a new recording or hide otherwise valid final media.

If any step after recording fails, preserve staging and persist `RECOVERY_REQUIRED`.

## 8. Delivery sequence

This sequence follows the Confluence 9-Month Development Plan for Phase 1. Existing Build 0.2 auth
work is preserved but does not replace or reorder these gates.

### Sprint 1 — onboarding and build baseline

**Goal:** every developer can build, run, debug and explain the MVP/target-architecture split.

Work:

- Keep the approved small Gradle/module shape.
- Remove deferred cloud/platform initialization from recording readiness.
- Record the active Build 0.1 feature profile in build/test configuration.
- Verify a runnable APK on the selected test device.

Exit gate: repeatable build/run/debug steps and no unexplained Build 0.2 dependency in the critical
startup path.

### Sprint 2 — camera and storage POC

**Goal:** prove the selected camera, recording-host and physical storage choices on a BodyCamera.

Work:

- Record 30 seconds and capture an image with device model, firmware and Android version evidence.
- Measure CameraX/Camera2/vendor callback behavior, foreground/background behavior and screen-off
  behavior needed by the Build 0.1 slice.
- Benchmark candidate storage roots, ADB visibility, sustained write speed, free-space reporting and
  same-filesystem move/rename behavior.
- Decide the minimum Build 0.1 encryption/checksum policy; do not let checksum delay `BDMA_READY`.

Exit gate: one selected device/camera/storage path is reproducible and unknowns are documented.

### Sprint 3 — Working Recording Slice

**Goal:** pass WRS-001 through WRS-005 and WRS-008/009 before platform expansion.

Work:

- Route UI/hardware events through one `RecordingCommandExecutor` or equivalent serialized queue.
- Keep camera callbacks event-only; perform file I/O and DB work on their owned executors.
- Implement permission and free-space prechecks.
- Record 30 seconds, stop, finalize from Temp to final media, then capture one image.
- Enforce one active recording and deterministic timeout/rejection behavior.

Exit gate: runnable APK, 30-second video, image capture, correct supported naming/path, finalized
media and no critical happy-path crash.

### Sprint 4 — minimal contract output and BDMA import

**Goal:** pass WRS-006/007 and the complete active Build 0.1 release gate.

Work:

- Freeze and generate the minimal supported `dcam.db` fixture.
- Generate `dcam_config.cson` as device information only for the supported fields.
- Generate UTF-8 sanitized `Logs/logs.txt` with one complete logical record per line.
- Ensure BDMA ignores `Temp`, sees only finalized media and treats a missing MP4 checksum according
  to the approved unverified/warning policy.
- Run BDMA sample detection/import through ADB and retain fixture/test evidence.

Exit gate: BDMA detects/imports video and image samples; minimal DB/CSON/log outputs are compatible;
provider availability is not required.

### Sprints 5–6 — Build 0.1 hardening

**Goal:** make the Working Recording Slice a repeatable DCAM MVP Internal Build 0.1.

Work:

- Execute the applicable failure matrix for camera, storage, DB busy, callback delay, finalization,
  activity recreation and provider outage.
- Enforce and report the Build 0.1 performance budgets in section 11.
- Run at least 4 hours continuous recording, at least 50 start/stop sessions, file-descriptor leak
  checks and bounded thread/memory/log-growth checks.
- Verify sensitive-data sanitization across internal logs, `logs.txt` and any enabled provider.
- Freeze supported contract versions, install steps, test report, known issues and BDMA evidence.

Release gate:

- Working Recording Slice and all Build 0.1-applicable QA groups pass.
- No critical bug remains in recording, capture, finalization, minimal DB/CSON/log output or BDMA
  sample import.
- Deferred features are neither initialized on the critical path nor used to reject the release.

## 9. Decision and POC register

| Decision | Build 0.1 treatment | Must be settled by |
|---|---|---|
| CameraX vs Camera2 vs vendor SDK | Required device POC decision | Sprint 2 exit |
| Recording host / foreground-service behavior | Required only to the extent needed by the selected device and recording slice | Sprint 2 exit |
| Physical storage root and finalization mechanics | Required device/storage decision | Sprint 2 exit |
| SQLite implementation and minimal BDMA fixture | Required for the supported Build 0.1 schema/output | Before Sprint 4 |
| Critical-path execution lanes and timeouts | Required by Concurrency & Threading Model v3 | Before Sprint 3 exit |
| MP4 checksum policy | Required for every Build 0.1 MP4; finalize first, compute MD5 asynchronously, then set `BDMA_READY` only after success | Before Sprint 4 |
| Encryption/key management | Full implementation deferred to Build 0.2; Build 0.1 behavior must be deterministic | Before Sprint 3 exit |
| Password auth, emergency identity and advanced user management | Deferred to Build 0.2 unless explicitly enabled | Build 0.2 planning |
| BDMA write-back fields, import tracking and cleanup | Deferred unless the Build 0.1 fixture explicitly requires them | Build 0.2 planning |

An implementation task cannot make a deferred decision release-blocking without first updating the
Confluence applicability matrix or recording an approved temporary exception.

## 10. Runtime failure test matrix

At minimum, automate application logic where possible and execute device-dependent rows on real
hardware:

| Scenario | Expected MVP result |
|---|---|
| Start without operator | Under the default Build 0.1 no-login/basic-placeholder profile, continue through normal prechecks; if auth is explicitly enabled, reject safely |
| Double start/concurrent key | One active session; deterministic rejection for the other |
| Activity recreated during recording | Recording authority remains singular |
| Process/service killed while recording | Persisted state found on restart; recovery required; no duplicate |
| Kill while finalizing | Resume/reconcile or classify; preserve artifact |
| App restart/reboot after interrupted work | Preserve Temp/final candidates, reconcile the minimum state and return to recording readiness within the applicable budget |
| Storage full before start | Controlled rejection |
| Storage full during recording | Safe stop/failure classification; preserve staging where possible |
| External storage removed | Build 0.1 uses internal storage only; reject external-root dependency. Auto/removable behavior is later-profile evidence, not Build 0.1 acceptance |
| DB busy/locked by BDMA | Bounded retry/defer; no corruption; recording policy remains deterministic |
| DB corrupt | Preserve original DB; safe mode/recovery; no destructive auto-reset |
| BDMA reads while recording | Only finalized media visible; `Temp` ignored |
| Camera SDK error | `CAMERA_FAILED`, resources released, diagnostic correlation retained |
| FGS cannot start | Reject/defer operation; no false active recording state |
| Permission revoked | Only affected command blocked; app remains usable |
| Encryption configured while MVP policy is off | Output remains unencrypted and policy decision is logged safely |
| MP4 checksum still running or fails | Preserve final MP4, record Checksum Pending/Failed, block `BDMA_READY` and BDMA import for that item; new recording remains independent |
| Loggly/Crashlytics/backend unavailable | Local operational logs and `logs.txt` continue; capture/finalization does not fail |

## 11. Test strategy

### Build 0.1 measurable release budgets

| Area | Mandatory target |
|---|---|
| Recording | Warm start ≤ 2.0s; stop ≤ 1.5s; critical finalization to `BDMA_READY` ≤ 5.0s; image capture ≤ 1.5s; precheck ≤ 500ms |
| Critical path | MP4 finalization precedes asynchronous MD5; checksum blocks only that item's `BDMA_READY`, never a new recording or unrelated capture |
| Memory | Idle ≤ 80 MB; recording ≤ 200 MB; growth ≤ 5 MB/hour |
| Storage/I/O | Sustained recording write ≥ 15 MB/s; same-filesystem move ≤ 500ms; media DB transaction ≤ 100ms |
| Free space | Start threshold = estimated 30-minute recording size + 500 MB; storage-full handling causes no file corruption |
| Startup | Cold boot to `READY` ≤ 8s; process restart to `READY` ≤ 4s; conditional login UI renders ≤ 1s after `READY` |
| Concurrency | MainThread operation < 500ms; coordinator queue ≤ 50ms; camera callback ≤ 100ms; camera timeout ≤ 5s; DB busy retry ≤ 3 attempts and ≤ 1s total |
| Stability | ≥ 4 hours continuous recording; ≥ 50 sessions without degradation; zero leaked file descriptors; ≤ 15 app-owned steady-state threads |

Targets are measured on the selected BodyCamera. A device-specific adjustment requires recorded POC
evidence and approval; it is not silently relaxed in code or tests.

### Local JVM tests

- Recording authority transitions and invalid transitions.
- Storage/permission preconditions and conditional auth behavior when enabled.
- Filename and contract-version validation.
- Finalization decision logic and failure classification.
- Basic interrupted-artifact reconciliation with fake DB/files.
- Finalize-before-checksum ordering, checksum-gated `BDMA_READY`, and provider-failure isolation.
- Developer-gate behavior only for explicitly disableable surfaces.
- Architecture dependency rules and always-on infrastructure rule.

### Android instrumentation/integration tests

- Room/SQLite fresh minimal Build 0.1 schema and any migration actually shipped in Build 0.1.
- Transaction and unique-active-session constraints.
- Staging-to-final file operations on supported storage modes.
- Restart/readiness and conditional boot/session behavior if auth is enabled.
- Service start/stop/bind behavior allowed by instrumentation.

### Real BodyCamera tests

- Camera/FGS/screen-off/process kill/reboot.
- Hardware key debouncing and simultaneous commands.
- Physical storage, ADB visibility, removal/full behavior.
- Lock Task only when required by the device POC; full Device Owner policy is deferred.
- Long-running stability, power, thermal and performance.
- BDMA sample import and applicable DB-read concurrency fixtures.

Unit-test and `assembleDebug` success are necessary but not sufficient for MVP acceptance.

## 12. Definition of Done

Build 0.1 is done only when:

- the APK builds and runs on the selected BodyCamera;
- one serialized authority owns recording/capture/finalization transitions and critical work does not
  block MainThread;
- a 30-second video and sample image are captured and finalized under the supported contract path;
- active/partial media remains in Temp/staging and is never imported as final media;
- the minimal `dcam.db`, device-information-only `dcam_config.cson` and sanitized UTF-8
  `Logs/logs.txt` artifacts are produced;
- BDMA detects/imports the sample video and image through ADB;
- checksum work, cloud/provider failure and deferred platform features do not block recording or
  `BDMA_READY`;
- applicable concurrency, performance, sanitization, failure and stability gates pass;
- no critical bug remains in the Build 0.1 flow;
- contract versions, device/firmware evidence, test report and known limitations accompany the build;
- fixed `B01OPR` / `Build 0.1 Operator` identity is consistent; full auth/login completion is not used
  as a Build 0.1 acceptance criterion.

## 13. Explicit non-goals for the first MVP

- Realtime AI detection.
- Full operator/user management plus face, QR or NFC authentication.
- Live streaming, PTT or JT808.
- Advanced remote configuration payload/rollout.
- Cloud identity and Web Portal provisioning.
- Full Device Owner/kiosk policy and recovery stack.
- Full feature eligibility, runtime registry and platform state machine.
- Cloud media upload/sync.
- Advanced BDMA conflict resolution.
- Production-grade fleet management.
- Remote Config and Self Update implementation.
- Production encryption/key management (Build 0.2 foundation).
- Full settings console backed by demo/local-only state.

These items are activated by the Release & Build Applicability Matrix in Build 0.2+ or by explicit
change control. They must not consume Build 0.1 contingency or reject the Working Recording Slice.

