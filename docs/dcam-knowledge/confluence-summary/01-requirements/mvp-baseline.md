# MVP requirement baseline

## Status of requirements

As of the 2026-07-15 refresh, `03 - Requirements` contains Data Contract page version 13, Android Device Operation Requirements page version 10, System Settings Requirements page version 19, Non-functional Requirements page version 14, functional requirements, and an Approved Provisional Baseline traceability matrix. Build 0.1 applicability is controlled by Release & Build Applicability Matrix and DEC-01-DEC-07 Decision Brief. Technical Design pages remain expected design intent until implementation and review correct/complete them.

The requirements below remain a consolidated orientation baseline from Product Vision, Charter, MVP Scope, Roadmap, Development Plan, Architecture, and the Data Contract. They do not replace detailed acceptance criteria, an approved Jira backlog, or the source Confluence pages.

## Build 0.1 requirement profile

- Required path: record/capture, internal staging/finalization, minimal SQLite/CSON/log outputs, MP4 MD5 readiness, and BDMA sample import through ADB.
- Reference configuration: NCC-036V, Android 12/API 31, firmware `877AOOAKN1_RK2_V009`; physical-device evidence must identify actual device.
- Identity exception: fixed `B01OPR` / `Build 0.1 Operator`, no login UI, persisted consistently where schema requires it.
- Device status: battery level, internal free space, and GPS availability state only; GPS coordinates/routes do not belong to acceptance.
- Important Media: parser/contract recognition stays, but Build 0.1 does not activate creation or marking workflow.
- Deferred work cannot reject Build 0.1 unless applicability changes: cloud provisioning, full auth, full kiosk, Remote Config, Self Update, AI, streaming, PTT, production encryption/key management, and fleet/portal expansion.
- Traceability matrix proves backlog readiness only. Release acceptance still requires Jira linkage, authoritative repository mapping, PR/build/test evidence, Technical Review, and Device POC evidence.

## Functional baseline

### Recording

- The operator can start and stop video recording.
- The app exposes a clear recording state.
- Recording works on the target BodyCamera hardware.
- The result is a local media file with associated metadata/status.
- Important start, stop, result, and error events are logged.
- Interruption/background/power-loss behavior requires detailed design; the direction is to preserve or explicitly classify data whenever possible.

### Image capture

- The operator can trigger an image capture.
- The image is stored locally with associated metadata/status.
- Trigger, result, and error events are logged.

### Local storage

- Media, metadata, and logs have a stable, documented, ADB-readable structure.
- Storage availability and free space are checked.
- The app warns, prevents, or stops unsafe recording when capacity is insufficient; the threshold/policy is TBD.
- In-progress/temporary data must be distinguishable from finalized/completed data.
- Data Contract 1.6 fixes logical roots, folder names, filename family, app/contract metadata, import cleanup, BDMA permissions, identity/provisioning boundaries, and `dcam.db`/`dcam_config.cson` ownership direction. Exact physical Android paths, low-space thresholds, retention, and recovery behavior remain for detailed implementation and device validation.

### Metadata

Product/architecture sources still expect per-media identity, device/user/time, optional valid GPS, source state, and compatibility information. Data Contract 1.6 adds these binding constraints:

| Concern | Current authority/direction |
|---|---|
| File association | Filename contains CameraID, fixed-six-character UserID, date, and time; exact collision/unique-ID policy remains TBD |
| Media metadata | Embedded in the media file when supported; standalone per-media JSON is not part of contract 1.6 |
| Important/encrypted state | Filename suffixes `_IMP`, `_enc`, or `_IMP_enc` |
| GPS and source lifecycle | Still require exact embedded fields, validation, and persistence design |
| App/contract compatibility | App package/version plus `dcam_data_contract_version`, `media_contract_version`, and `encoder_contract_version`; no `bdma_decoder_profile_id` |
| Database version | `dcam.db` must expose schema/version metadata for BDMA compatibility checks |
| Integrity | Optional same-basename `.md5` applies only to `.mp4`; image/audio have no MD5 sidecar |

The exact embedded metadata fields/encoding and SQLite table details remain implementation/design work. Implementations must not introduce a standalone media JSON contract without updating the Data Contract.

### Device status and capability

- Report or record battery, storage, and GPS availability for MVP.
- Detect camera, microphone, GPS, storage, network, GMS, battery, and potentially USB capabilities before enabling dependent features.
- Missing optional capability must degrade gracefully rather than crash the core flow.
- Feature eligibility states should follow the Technical Design vocabulary when implemented: `ENABLED`, `DEGRADED`, `DISABLED_BY_POLICY`, `DISABLED_BY_PERMISSION`, `UNSUPPORTED_HARDWARE`, `UNSUPPORTED_PERFORMANCE`, `TEMPORARILY_UNAVAILABLE`, `PRUNED`, and `ERROR`.

### Logging and diagnostics

- Maintain local application, recording, capture, camera, GPS, storage, update, and crash/error logs as applicable.
- Logs include timestamps and useful context such as device/session/file IDs when safe.
- Do not log passwords, tokens, secrets, raw sensitive metadata, or media content.
- Local logs must exist even when cloud diagnostics are unavailable.
- Rotation/retention is required; the official policy is TBD.

### BDMA integration

- DCAM creates and finalizes source data locally.
- BDMA detects the connected device and initiates ADB discovery/read/sync.
- BDMA imports, validates, maps, indexes, stores, and displays the data.
- BDMA must not modify source media, embedded metadata, MD5 content, `Temp`, or `logs.txt`.
- BDMA may update `dcam_config.cson` for device information only through an approved contract path and may write only approved `dcam.db` tables/fields, subject to schema, corruption, locking, and concurrent-write safeguards.
- BDMA may delete successfully imported source media under the contract cleanup matrix. Missing-MD5 MP4 deletion requires confirmation for each file.
- BDMA must not silently guess or regenerate missing metadata unless a later contract explicitly permits it.
- Contract/schema mismatch is a shared DCAM–BDMA concern and requires version handling.
- BDMA must not use `bdma_decoder_profile_id`; it identifies app and media compatibility through app/data/media/encoder contract metadata and its built-in compatibility table.

## Non-functional baseline

| Quality | Baseline expectation |
|---|---|
| Availability | Core recording, capture, storage, metadata, logging, and BDMA-readiness work without Internet |
| Reliability | ≥99% record/capture success; no critical corruption or main-flow crash |
| Integrity | MP4-only optional MD5/import outcomes are decided; DCAM finalize validation, persisted lifecycle, and recovery remain TBD |
| Performance | No camera, file, database, or network work blocks the UI thread; recording has priority over diagnostics/cloud |
| Compatibility | Capability-based operation across BodyCamera hardware/firmware and GMS/non-GMS environments |
| Testability | Business flows depend on interfaces, allowing fake platform services |
| Observability | Important actions emit start/result/error diagnostics sufficient for field support |
| Security | Least-required permissions; no hardcoded/logged secrets; media/metadata/update security to be designed |
| Maintainability | Modular layers, vendor SDK isolation, schema versioning, and ADRs for major decisions |

## Acceptance baseline

The MVP Scope lists these acceptance conditions:

1. The app runs on BodyCamera.
2. The user can record video.
3. The user can capture an image.
4. Media is stored in the agreed structure.
5. Metadata is created for each media file.
6. Valid GPS is recorded when available.
7. Main-flow logging works.
8. BDMA can ingest DCAM data.
9. Near-full storage and unavailable GPS do not crash the app.

## Requirement precedence

When sources differ or implementation has moved ahead of the documents:

1. Approved Product/Requirement decision and Data Contract.
2. Approved ADR and finalized Technical Design.
3. Architecture principles and Android Development Standard.
4. Current prototype behavior.

Do not treat current filenames, folders, database tables, Loggly behavior, SOS/audio UI, or build settings as approved product requirements merely because they exist in code.
