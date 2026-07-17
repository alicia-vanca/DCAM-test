# Technical design draft digest

Source status: Confluence folder [4.2 - Technical Design](https://ducviet.atlassian.net/wiki/spaces/DVID/folder/47120392), refreshed on **2026-07-15**; current page versions are recorded in `docs/dcam-knowledge/confluence-original`.

Interpretation rule: these pages describe expected target behavior and design direction. Treat them as draft/boss-intent material, not as a concrete description of the current repository and not as final acceptance evidence. After implementation, the official Confluence pages should be corrected and completed against actual behavior.

Build 0.1 overlay: generic later-phase design is not a release blocker. Use Release & Build Applicability Matrix and DEC-01-DEC-07 Decision Brief: Working Recording Slice, internal storage, MP4 MD5 before `BDMA_READY`, fixed placeholder operator, and one reference-device evidence set.

Generic Internal/External/Auto storage, authenticated operator flow, and Important Media workflow remain later-profile behavior unless explicitly activated. Build 0.1 uses internal storage, fixed placeholder operator, no login UI, and Important Media Conditional - Not Activated.

## Source pages

The original table below is a July 8 digest snapshot. For current versions, use the downloaded source files. Latest key versions: Android Operation `v23`, Device Owner/Kiosk `v10`, In-App Console `v13`, Recording `v14`, Storage `v11`, SQLite `v19`, State Machine `v14`, BDMA Integration `v11`, Device Capability `v8`, Provisioning `v12`, Web API `v10`, Self Update `v12`, Security `v17`, Sensors `v7`, AI `v8`, Concurrency `v5`, Logging `v9`, and Performance `v7`.

## Technical Documentation coverage map

This digest set preserves content from every current page under `04 - Technical Documentation`:

| Source group | Pages condensed into maintained summaries |
|---|---|
| Architecture navigation | Architecture Home; Architecture Overview; Architecture Principles |
| Platform and modules | Android Platform & Compatibility Strategy; Application & Module Architecture; Architecture Delivery Profile |
| Data boundary | Data, Storage & BDMA Architecture; DCAM-BDMA Integration Boundary |
| Cloud and quality | Cloud Services, Update & Configuration Architecture; Logging, Diagnostics, Performance & Security |
| Runtime operation | Android Operation; State Machine; Concurrency & Threading Model |
| Dedicated device | Android Device Owner & Kiosk Policy; In-App Operation, Device Settings & Media Console |
| Capture and persistence | Recording & Capture; Storage; SQLite Database; BDMA Integration Technical Design |
| Capability and diagnostics | Device Capability & Feature Eligibility; Logging & Diagnostics; Performance Budget & Resource Constraints |
| Provisioning | Device Provisioning Web Portal; Web Portal App Design; Web Portal Implementation Design; Web Portal & Device API Contract |
| Platform services | Self Update; Security & Encryption; Sensor & Location Monitoring; Realtime AI Detection |
| Development | Android Development Standard; Android Training & Architecture Onboarding; Device POC & Hardware Validation Report |
| Decisions | ADR for Dedicated Device / Device Owner / Lock Task; ADR for `serial_number` + `dcam_cloud_device_id` |

Architecture pages are condensed mainly in `02-architecture`; Android rules and training in `03-development`; feature applicability in `04-features`; detailed runtime/design behavior remains in this file. These summaries are intended to remain usable if downloaded originals are removed.

| Page | Confluence status/version | Last API update (UTC) | Local interpretation |
|---|---:|---|---|
| [DCAM Android Operation Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48562239) | Draft 1.4 / v14 | 2026-07-08 08:59 | Runtime orchestration expectation |
| [DCAM Android Device Owner & Kiosk Policy Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49840280) | Draft 0.3 / v3 | 2026-07-08 08:53 | Dedicated-device/kiosk policy expectation |
| [DCAM In-App Operation, Device Settings & Media Console Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49840330) | Draft 0.8 / v8 | 2026-07-08 08:47 | In-app console/settings/media expectation |
| [DCAM Recording & Capture Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529484) | Draft 0.9 / v9 | 2026-07-08 02:54 | Recording/capture lifecycle expectation |
| [DCAM Storage Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496699) | Draft 0.6 / v6 | 2026-07-08 02:55 | Android storage mechanics expectation |
| [DCAM SQLite Database Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529463) | Draft 1.1 / v11 | 2026-07-08 09:17 | Database/table ownership expectation |
| [DCAM State Machine Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496753) | Approved 1.8 / v10 | 2026-07-08 09:16 | Cross-runtime guard model; still treat as target design until implemented |
| [DCAM BDMA Integration Technical Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595030) | Draft 0.5 / v5 | 2026-07-08 05:54 | BDMA implementation expectation |
| [DCAM Device Capability & Feature Eligibility Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48758788) | Draft 0.5 / v5 | 2026-07-08 09:15 | Capability/eligibility state expectation |
| [DCAM Device Provisioning Web Portal Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49315858) | Draft 0.5 / v5 | 2026-07-08 09:58 | Provisioning business-flow expectation |
| [DCAM Web Portal & Device API Contract](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49873154) | Draft 0.1 / v1 | 2026-07-08 09:56 | API/schema boundary expectation |
| [DCAM Self Update Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529439) | Draft 0.8 / v8 | 2026-07-08 09:18 | APK update expectation |
| [DCAM Security & Encryption Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496720) | Draft 1.0 / v10 | 2026-07-08 08:57 | Security/key/update/auth expectation |
| [DCAM Sensor & Location Monitoring Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496794) | Draft 0.6 / v6 | 2026-07-08 02:57 | Optional monitoring expectation |
| [DCAM Realtime AI Detection Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595090) | Draft 0.7 / v7 | 2026-07-08 02:57 | Optional realtime analytics expectation |

## Runtime and kiosk expectations

- Android Operation owns startup orchestration, policy verification, identity restore, provisioning state, login/session lifecycle, foreground service, boot/process survival, runtime module registry, update guard, recovery, and safe mode.
- Production dedicated-device operation should not rely only on fullscreen flags, immersive mode, or Home/Launcher behavior. The design expects DCAM-as-DPC/local Device Owner behavior if target firmware and factory process support it.
- External EMM, Android Management API, and Managed Google Play policy-driven update are not current-baseline assumptions.
- If required Device Owner/DPC or Lock Task policy is missing in production, normal field operation should enter controlled degraded/policy-required state rather than continuing unrestricted.
- Controlled Maintenance Mode is the only expected temporary kiosk exit path. Enter Maintenance Mode / Exit Kiosk temporarily requires a Maintenance Password Gate, safe runtime state, approved targets, audit, and policy restore.
- Normal recording/capture evidence requires an active operator session. Emergency recording may use the protected `EMERGENCY_OVERRIDE_ADMIN` system identity.

## In-app console expectations

- Record / Live View is the default screen after startup/login. Setting is the in-app console hub.
- Back on Record / Live View opens Setting; Back on Setting returns to Record / Live View; Back inside child modules returns to Setting. Back must not exit DCAM while kiosk mode is active.
- File Manager and Media Viewer are read-only/view-only. They must not delete, edit metadata, mark important, export/share, or expose in-progress/temp files as final media.
- Expected console groups include App Operation Settings, Device/System Settings, File/Storage Manager, Media Viewer, Login Settings, User Settings, Admin/Maintenance, Emergency Settings, future connection/stream/PTT/AI groups, and Diagnostics/Support.
- Future modules should stay hidden, disabled, or unavailable until their design and capability/eligibility state allow them.

## Data and reliability expectations

- `RecordingController` is the only owner allowed to start, stop, mark important, finalize, or recover recording sessions. UI, sensor, AI, and emergency managers emit commands/events; they do not call camera/storage SDKs directly.
- Recording prechecks include operator session or emergency override, feature eligibility, camera/audio permissions, storage writable state, free-space threshold, DB availability, battery/thermal policy, update/install safety, and active finalization.
- Recording states include `IDLE`, `PRECHECKING`, `PREPARING_CAMERA`, `PREPARING_STORAGE`, `STARTING`, `RECORDING`, `STOPPING`, `FINALIZING`, `BDMA_READY`, `COMPLETED`, and failure/recovery states.
- Storage design expects temp/staging files to remain invisible to BDMA. Final media appears in approved Media folders only after finalization and DB readiness conditions pass.
- Generic Internal/External/Auto root selection resolves before recording. Auto prefers External and falls back to Internal when External is unavailable, full, missing, not writable, or invalid. Build 0.1 overrides this generic behavior with its Internal-only authoritative storage profile.
- Free-space threshold names exist as design placeholders: `WARNING_FREE_SPACE`, `MIN_START_FREE_SPACE`, `CRITICAL_ACTIVE_FREE_SPACE`, `RESERVED_FINALIZATION_SPACE`, and `MIN_RECOVERY_SPACE`. Exact values remain TBD.
- `BDMA_READY` means safe for BDMA scan/import, not already imported.

## Database and BDMA expectations

- `dcam.db` is expected to own schema/versioning, identity/provisioning, kiosk snapshot if needed, console settings, maintenance audit/session state, remote config cache/apply state, user/operator/auth/session/sync data, operational/applied settings, capability/eligibility, runtime/recovery state, media/session/finalization state, BDMA import/write-back state, tracking, update state/history, and diagnostics.
- `dcam_config.cson` remains device-information only. Operational settings, console settings, kiosk settings, update settings, and remote-config state belong in `dcam.db`.
- BDMA must identify app and contract compatibility through app/package/version plus `dcam_data_contract_version`, `media_contract_version`, and `encoder_contract_version`.
- `bdma_decoder_profile_id` is not used. Unsupported app/contract versions should block import or show compatibility warning without modifying source media.
- BDMA may read device information from `dcam_config.cson` and/or `dcam.db`, but `serial_number`, `owner_name`, and `manufacture_date` are display/support information, not primary identity keys.

## Cloud, provisioning, and update expectations

- `dcam_cloud_device_id` is the server/cloud primary key. `android_id_hash` is the recovery lookup key. Raw Android system identifiers must not be logged.
- Web Portal QR provisioning is DCAM business provisioning, not Android Enterprise Device Owner enrollment.
- The current Web Portal baseline has Android generate a local QR and poll lookup by `android_id_hash`; backend pre-created provisioning challenge/session is not required for the current baseline.
- Web Portal/device APIs must be versioned, use stable reason codes, and treat server config as requested values. Android validates capability, policy, and runtime guard before applying.
- DCAM Self Update / APK update is the primary update path for the current no-external-EMM baseline. Managed Google Play policy-driven update is not applicable.
- Optional manual Play Store fallback requires GMS/Play Store, approved maintenance/factory process, Controlled Maintenance Mode, Maintenance Password Gate, no personal Google account dependency, and policy restore afterward.

## Capability, security, sensors, and AI expectations

- Official feature eligibility states are `ENABLED`, `DEGRADED`, `DISABLED_BY_POLICY`, `DISABLED_BY_PERMISSION`, `UNSUPPORTED_HARDWARE`, `UNSUPPORTED_PERFORMANCE`, `TEMPORARILY_UNAVAILABLE`, `PRUNED`, and `ERROR`. `SUPPORTED` is descriptive wording, not a runtime/persisted state.
- Capability categories include camera, audio, sensors, location, storage, compute, auth method, kiosk policy, in-app console, maintenance, Self Update, Play Store fallback, and BDMA.
- Maintenance credentials must not be hardcoded, stored plaintext, synced to BDMA, exposed through media/file viewer, or logged. Protected representation, lockout/cooldown, reset, and rotation details remain security-review TBD.
- Media encryption naming follows the Data Contract suffixes, but algorithm, key storage, rotation, and BDMA decryption compatibility remain TBD.
- Sensor/location monitoring and realtime AI are optional, capability-gated modules. They may emit event candidates or metadata, but they must not directly control recording, camera, or storage. Their failure must not crash or block core recording unless a future approved policy makes them a direct dependency.

## Implementation caution

Use these pages to understand intended direction, design vocabulary, and future acceptance discussions. Do not mark a feature complete just because it appears here. Current implementation status remains under [local current repository notes](../../local-dev/current-repo/current-state.md), and source-backed gaps remain under [local evidence](../../local-dev/evidence/README.md).

## Concurrency and threading model

- One serialized recording authority owns camera/recording state transitions. UI, hardware keys, services, recovery, and remote commands submit commands to that authority instead of mutating state independently.
- Main thread is limited to UI work and short dispatch. Camera callbacks stay short; storage, checksum, DB, diagnostics, and provider work run on bounded dedicated executors.
- Camera commands must not share an uncontrolled general thread pool. Start/stop/finalize ordering, duplicate-command rejection, timeout, cancellation, and recovery must remain deterministic.
- MP4 finalization and MD5 are separate steps. MD5 may run asynchronously, but affected media cannot become `BDMA_READY` until checksum success. New capture must not wait for unrelated checksum/provider work.
- DB access must use transactions, bounded retry for temporary lock/busy states, and no destructive reset on corruption. BDMA reads must not observe partial DB/filesystem publication.
- MainThread stalls, queue delay, camera callback duration, DB retry, checksum duration, startup, finalization, memory, storage throughput, and long-recording stability are measured diagnostics, not log-only guesses.

## Logging, diagnostics, and performance

- Local operational logging is mandatory and remains functional offline. Cloud providers such as Loggly or Crashlytics are adapters; provider failure cannot block capture, finalization, local logs, or `logs.txt` output.
- Logs use stable categories, severity, reason codes, correlation/session IDs, timestamps, component, device/app/contract version, and safe state transitions. Raw secrets, tokens, credentials, maintenance passwords, raw Android identifiers, and sensitive media content are forbidden.
- Diagnostics cover startup, policy/capability evaluation, permissions, camera/audio, storage capacity and I/O, DB health, recording lifecycle, finalization/checksum, BDMA exposure/import state, update, provisioning, recovery, and controlled maintenance.
- Crash monitoring must preserve local evidence and avoid crash loops. Recovery classifies incomplete work, reconciles staged/final files with DB state, and enters safe/degraded mode instead of silently deleting evidence.
- Performance budgets are release evidence only after measurement on target hardware. Missing device measurements remain Pending Device POC, not passed by desktop/unit tests.

## Provisioning, Web Portal, and device API

- Business provisioning is separate from Android Enterprise enrollment. Current direction uses Android-generated local QR data, Web Portal lookup by `android_id_hash`, authenticated operator review, validation, and server-side provisioning result.
- `dcam_cloud_device_id` is cloud primary identity. `android_id_hash` is recovery/lookup input, not business identity or authentication secret. Raw Android identifiers must not be logged or exposed.
- APIs are versioned and return stable reason codes. Server configuration is requested state; Android still validates capability, permission, device policy, safety, active recording/finalization, and local applicability before applying it.
- Portal workflow includes login, QR scan/manual lookup, serial/owner/manufacture-date review, conflict validation, submit, result/error state, audit, and safe retry. Duplicate serials, stale challenges, invalid QR, identity conflict, and unauthorized changes must fail explicitly.
- Portal app and backend implementation separate UI, authentication, QR parsing, validation, provisioning service, and repositories. Secrets remain server-side; client code must not embed privileged credentials.
- Provisioning, remote config, and portal outages never block Build 0.1 local recording. Their runtime modules remain deferred unless build applicability enables them.

## Identity and factory baseline

- Device identity has two different keys: immutable/audited business `serial_number` and server-generated `dcam_cloud_device_id`. Neither derives from the other; `serial_number` is not an authentication secret.
- `serial_lookup/{serial_number}` must resolve uniqueness. Duplicate serials cannot silently create another active device. Missing, invalid, or conflicting identity places device into provisioning/factory-required or quarantine state.
- SD identity file is factory input, not automatic authority. DSetup validates it, can require barcode scan, records audit evidence, and applies READY_TO_SHIP or QUARANTINED rules.
- Android persists identity consistently in approved local stores, prevents silent serial overwrite, and reports identity state without leaking secrets. Identity changes require permission, reason, audit, and downstream reconciliation.
- Factory shipment requires validated identity, app/config state, policy capability, storage/ADB behavior, update path, security checks, and required POC/QA evidence. PM approval of a reference configuration is not factory or production approval.

## Dedicated-device and kiosk ADR

- Current architecture rejects external EMM, Android Management API, and Managed Google Play as required baseline dependencies. Direction is local DCAM policy control using Device Owner/DPC and Lock Task where target firmware permits.
- Fullscreen or launcher behavior alone is insufficient for production kiosk assurance. Missing required authority yields `DEVICE_POLICY_REQUIRED` or `POLICY_DEGRADED`; normal unrestricted field operation must not continue silently.
- Controlled Maintenance Mode is audited temporary escape for approved settings/tools. Entry requires safe runtime state and protected authorization; exit restores required policy.
- Build 0.1 does not require full kiosk implementation unless device POC proves Working Recording Slice cannot operate without it. Dedicated-device direction remains binding for later production profiles.

## Device POC and hardware validation

- POC records exact model, Android/API, firmware, build, hardware identity, storage, camera/audio, GPS, GMS, policy authority, and test evidence. Empty/TBD rows mean not qualified.
- Build 0.1 WRS covers start/stop video, image capture, internal staging/finalization, MD5 success and mismatch behavior, DB/CSON/log outputs, ADB visibility/import, storage failure, interruption/reboot recovery, and basic battery/storage/GPS status.
- Kiosk/Device Owner, Self Update, optional Play Store fallback, in-app console, storage/BDMA/ADB, and factory flow each have separate POC matrices. A pass in one matrix does not imply another.
- Evidence must come from identifiable physical reference device. Emulator, desktop tests, or another firmware cannot substitute without impact review and appropriate regression.
- POC output drives capability eligibility, implementation selection, ADR updates, factory acceptance, and documented unsupported/degraded states.

## State, capability, and safety composition

- Runtime eligibility is composed from requested setting, hardware capability, permission, device-policy capability, safety policy, and temporary availability. A setting alone never proves feature availability.
- Stable eligibility states include `ENABLED`, `DEGRADED`, `DISABLED_BY_POLICY`, `DISABLED_BY_PERMISSION`, `UNSUPPORTED_HARDWARE`, `UNSUPPORTED_PERFORMANCE`, `TEMPORARILY_UNAVAILABLE`, `PRUNED`, and `ERROR`.
- State machine guards prevent recording during unsafe update/install, conflicting finalization, missing required storage/permission/camera, or policy states that apply to active profile.
- Optional sensor/location/AI modules emit observations or event candidates through boundaries. They cannot directly seize camera/storage or crash/block core recording unless future approved applicability makes them required.

## QA and traceability expectations

- Each applicable decision maps requirement/design to Jira, implementation/PR, QA Test ID, and evidence. Missing links keep row Pending, Blocked, or Partially Covered.
- Required tests include positive flow, invalid input, permission loss, unsupported capability, storage full/removal, DB busy/corrupt, camera error, process death/reboot, checksum missing/mismatch, provider outage, concurrent commands, and BDMA read during recording.
- Build 0.1 acceptance is one physical-device Working Recording Slice, not production, fleet, multi-model, multi-firmware, or shipment certification.
- Security, factory, kiosk, update, provisioning, and later-phase features require their own applicability and evidence gates; they cannot be inferred from WRS success.
