# DCAM MVP Internal Build 0.1 - Consolidated Delivery Report and Plan

**Status date:** 2026-07-14
**Active build:** DCAM MVP Internal Build 0.1  
**Delivery gate:** Working Recording Slice  
**Purpose:** Merge overlapping local state, gaps, plans and checklists into one current execution view. Confluence remains source of truth.

## 1. Executive decision

```text
Record / Capture
  -> Save / Finalize Media
  -> Create required MP4 MD5 readiness evidence
  -> Expose minimal dcam.db / dcam_config.cson / Logs/logs.txt
  -> BDMA sample import through ADB
```

Do not expand platform architecture before this slice is demoable on selected BodyCamera. Full authentication, cloud/web provisioning, full kiosk, Remote Config, Self Update, AI, Live Streaming and PTT are deferred. They cannot reject Build 0.1 unless Release & Build Applicability Matrix changes.

## 2. Authoritative baseline

| Authority | Current effect |
|---|---|
| DCAM Project Home | Declares Build 0.1, Working Recording Slice, required outputs and deferred features |
| Release & Build Applicability Matrix | Owns build applicability and release blockers |
| Architecture Delivery Profile | Requires recording-first delivery; blocks unnecessary layers/modules |
| DCAM-BDMA Data Contract | Owns folder, naming, MD5, readiness, import and cleanup behavior |
| Requirement-Design-Test Traceability Matrix | Defines Requirement, Design, Jira, PR, QA Test ID and evidence mapping |
| Device POC & Hardware Validation Report | Owns hardware-sensitive Camera SDK, FGS, storage, ADB, power and recovery evidence |

Source snapshot refreshed from Confluence on 2026-07-15 with `node download-confluence.js`. Latest Build 0.1 authority includes DEC-01-DEC-07 and requires MP4 MD5 success before `BDMA_READY`.

## 3. Current source status

### Implemented or materially present

- `:app` plus `:core`; compile/target SDK 36, min SDK 26, Java source 17, CameraX 1.6.1, Room 2.8.4.
- CameraX video/image capture and serialized recording coordination.
- Temp/staging, final Media publication, capacity checks and storage-failure classification.
- Foreground recording notification/service.
- Room `dcam.db`, CSON store and `Logs/logs.txt` infrastructure.
- Media naming, selected storage-root handling, staged recovery and MD5-sidecar code/tests.
- BodyCamera Android 12 evidence for video and audio normal finalization, foreground continuation, force-stop/reboot recovery, and physical power-cut behavior.

### Present but not Build 0.1 acceptance evidence

- Password authentication/session foundation.
- Cloud identity and Remote Config no-op/local persistence.
- Kiosk controller and broad settings/menu scaffolding.
- Media encryption, update and provider logging foundations.

Keep these only when disabled or isolated from startup and recording critical path.

### Not yet proved

- Repeatable 30-second video plus image on target hardware.
- MP4 MD5 matched and used as readiness gate before BDMA import.
- Complete fixture: finalized media, `dcam.db`, `dcam_config.cson`, `Logs/logs.txt`.
- Successful BDMA detection/import through ADB.
- Long-duration thermal/storage behavior.
- Long-duration thermal/storage and repetition budgets.
- Frozen device/environment matrix and formal Device POC report.
- Complete Requirement -> Design -> Jira -> PR -> QA Test ID -> evidence mapping.

## 4. Evidence accepted now

| Evidence | Result | Limit |
|---|---|---|
| Local JVM tests | Broad capture/storage/config/auth/logging coverage | Not device or BDMA acceptance |
| Prior `test` and `assembleDebug` | Recorded pass history | Must rerun for final candidate |
| BWC force-stop recovery | Staged MP4 recovered into Media | One device/run; no BDMA import |
| BWC reboot recovery | MP4 recovered; Temp emptied | One device/run |
| BodyCamera audio finalization/lifecycle | Internal and AUTO SD finalization, screen-off and rotation passed | One device/run; no long-duration repetition |
| BodyCamera audio force-stop/reboot recovery | Staged AAC recovered into Media/Audio/yyyy-MM-dd | Reboot requires vendor USB remount handling |
| BodyCamera audio physical power cut | External SD `Temp` FAIL reproduced 2/2; app-private retest retained `558,775` bytes but tested APK entered startup crash loop. Compatibility fix later recovered exact artifact | Fresh cut on fixed APK and staging-root approval still required |
| Loggly process isolation | Persisted `JobScheduler` job and Room drain both run in `com.dvid.dcam:loggly`; emulator spawned both processes without crash | `BODYCAMERA4HHITK` marks job RUNNABLE/active but still never binds or forks `:loggly`; vendor ROM blocker confirmed |

No task reaches Done unless its Definition of Done evidence is complete.

## 5. Consolidated gap register

| Priority | Gap | Gate impact | Required close evidence |
|---:|---|---|---|
| P0 | End-to-end BDMA sample import absent | Blocks Working Recording Slice | BDMA import result, matching IDs, logs and screenshots |
| P0 | MP4 MD5 readiness not proved end to end | Blocks import and release gate | Sidecar, verified digest, mismatch negative test |
| P0 | Minimal DB/CSON/log fixture not proved together | Blocks contract acceptance | Pulled fixture, schema/content review, BDMA read result |
| P0 | Device POC report incomplete | Hardware choices provisional | Device/firmware matrix, commands, logs, media and measurements |
| P0 | Final APK build/test evidence not frozen | Candidate cannot be accepted | Clean test/build output, APK hash, install and smoke evidence |
| Closed | Startup cloud init removed from `AppComposition` | Offline-first startup path restored | Covered by compile/test evidence |
| P1 | Encryption can remain configurable | Risk unreadable BDMA sample | Build 0.1 OFF policy and filename/output evidence |
| P1 | Camera ownership Activity/lifecycle sensitive | Screen/activity-loss risk | Device POC result and approved decision |
| P0 | Audio power-cut design/evidence not accepted | External SD `Temp` loses active AAC bytes; app-private staging retains bytes but changes staging visibility/root and tested APK crashed on recovery | Approve staging root, then pass a fresh physical battery-cut end to end on fixed APK without crash |
| P1 | BodyCamera ROM does not spawn `:loggly` process | Separate-process uploader works on emulator but cannot run independently on target device | Vendor ActivityManager/process policy fix or approved same-process fallback, followed by target-device crash/outbox drain evidence |
| P1 | Traceability mapping incomplete | DoD/review failure | Requirement, Design, Jira, PR, QA Test ID and evidence links |
| P2 | Deferred surfaces visible or initialized | Scope creep/regression risk | Build-profile gate evidence; no critical-path execution |

## 6. Superseded local assumptions

- Filesystem publication alone is not `BDMA_READY`; current Data Contract requires MP4 finalization, async MD5, then readiness.
- MD5 is not deferred from Build 0.1; current Data Contract makes MP4 MD5 part of readiness.
- Eight local ADR files are useful, but not a separate release gate; write only decisions needed for current hardware and delivery path.
- Full FGS ownership, full kiosk or full authentication block Build 0.1 only if POC proves Working Recording Slice cannot pass without them.

Existing local documents remain evidence/history, not competing sources of truth.

## 7. Execution plan

### Gate A - Freeze candidate scope

**Verified on 2026-07-13:** complete.

- [x] Device connected: serial `KF5OF2126040802193`, model `BWC`, Android 12 / API 31, firmware build `877AOOAKN1_RK2_V009`.
- [x] App installed: `com.dvid.dcam`, version `1.0` / code `1`, target SDK 36.
- [x] App external root is ADB-visible at `/storage/emulated/0/Android/data/com.dvid.dcam/files` with `Config`, `Logs`, `Media` and `Temp`.
- [x] Streaming is disabled by default through `FeatureGate.VIDEO_STREAMING(false)`.
- [x] Cloud settings UI is disabled by default through `FeatureGate.CLOUD_SETTINGS(false)`.
- [x] Media encryption is disabled by both `FeatureGate.MEDIA_ENCRYPTION(false)` and `DcamConfig.DEFAULT_VIDEO_ENCRYPTED = false`; device has no encryption preference override.
- [x] Self Update, AI and PTT have no active Build 0.1 runtime implementation discovered. PTT exists only as future streaming documentation/surface scope.
- [x] Startup cloud identity/Remote Config initialization was removed from `AppComposition`; cloud settings remain disabled by default.
- [x] MD5 is enabled by default through `FeatureGate.VIDEO_MD5(true)` and `AndroidVideoMd5PreferenceStoreImpl` default `true`; it remains user/developer configurable. Existing device installs without an override adopt this default.
- [x] Current work focus remains capture, finalization, MD5, minimum contract outputs and diagnostics. BDMA import stays in Gate E after DCAM device behavior is stable.

Device contains one effective developer override: `AUDIO_CAPTURE=true`. Legacy `RECORDING_SETTINGS=true` is ignored by current code. Cloud, streaming, encryption and MD5 use current code defaults.

**Exit:** complete. Candidate rebuilt, installed and launched on BWC; startup has no cloud-init execution or crash, encryption remains OFF, and MD5 defaults ON.

### Gate B - Complete Device POC

**Status:** partial pass with audio power-cut blocker. Evidence: `evidence/build-0.1-gate-b-device-poc-2026-07-13.md` and `evidence/build-0.1-audio-device-tests-2026-07-14.md`.

- [x] Build/test and APK install on selected BWC.
- [x] Login/session precondition and camera preview.
- [x] Basic 30-second-class video recording and normal finalization.
- [x] MP4 MD5 generation and digest match for normal finalized video.
- [x] Image capture and final publication.
- [x] Foreground recording service active during recording.
- [x] Screen-off recording continues and finalizes with matching MD5.
- [x] Force-stop interrupted recording recovers from `Temp` into `Media/Video/yyyy-MM-dd` after relaunch.
- [x] Activity recreate/config-change during recording.
- [x] Reboot recovery on current candidate with matching MD5.
- [x] AUTO storage selection writes to mounted dynamic SD UUID `6162-6433`.
- [x] Repeated 40-second-class SD recording finalizes with matching MD5.
- [x] Physical power-cut recovery with matching MD5 after MediaProvider settled.
- [x] Audio normal finalization from `Temp` into `Media/Audio/yyyy-MM-dd` on internal and AUTO SD storage.
- [x] Audio foreground-service continuation through screen-off and configuration rotation.
- [ ] Audio recovery from staged AAC: force-stop and reboot PASS. External SD `Temp` physical cuts FAIL reproducibly (2/2). App-private `DurableAudioTemp` retained `558,775` bytes after a later cut, but tested APK entered a `Files.readString` `NoSuchMethodError` startup loop. One-line compatibility fix passed tests/build and recovered exact artifact to AUTO SD `Media/Audio/yyyy-MM-dd` (`recovered=1, preserved=2`). Fresh physical cut on fixed APK plus staging-root approval remain required.
- [ ] Physical removable-storage insert/remove test: not executable without device disassembly.
- [ ] Long-duration recording and thermal/storage measurements.
- [ ] Cold-boot repetition for intermittent CameraHAL/focus ANR characterization.

Recovered-MP4 MD5 gap is closed: startup and mounted-storage recovery use the active MD5 setting; enabled/disabled unit tests pass; force-stop, reboot and physical power-cut recovery on `BODYCAMERA4HHITK` produced matching MP4/MD5 pairs and emptied `Temp`. Firmware vendor app `com.bodycamera.nettysocket` can export SD as USB mass storage (`MEDIA_SHARED`); stable SD evidence requires USB function `adb`/`none`. Android MediaProvider may lag SD mount after boot; DCAM preserves staged media until a later recovery pass can publish it. Physical SD removal is not executable without camera disassembly.

Audio normal finalization and lifecycle checks pass on `BODYCAMERA4HHITK`: Internal and AUTO SD publication, foreground continuation through screen-off/rotation, force-stop recovery and reboot recovery all produced non-zero AAC under `Media/Audio/yyyy-MM-dd` with `Temp` emptied. Physical battery-cut testing on 2026-07-14 first exposed a reproducible external-SD staging blocker in two consecutive runs: active staged AAC files measured `104,625` and `141,050` bytes before power removal but both mounted as `0` bytes after boot. A later candidate moved active AAC into app-private `files/DurableAudioTemp`; its physical cut retained the exact file at `558,775` bytes, proving the byte-loss mechanism was avoided. That tested APK then entered a startup crash loop because `Files.readString` was unavailable on the Android 12 runtime. Replacing it with `Files.readAllBytes` plus UTF-8 decoding passed `test assembleDebug`; the rebuilt APK recovered the same artifact into AUTO SD `Media/Audio/yyyy-MM-dd` at `558,775` bytes and logged `recovered=1, preserved=2`. This is not full acceptance: app-private staging changes the observable `Temp` design, and no fresh physical cut has yet passed end to end on the fixed APK.

Loggly isolation implemented on 2026-07-14: normal and fatal logs first enqueue into Room; fatal direct HTTP fallback runs only when durable enqueue fails. Logging no longer uses WorkManager. A persisted native `JobScheduler` job targets `LogglyUploadJobService` in `:loggly`; that process reads Room, sends HTTP, applies retry/backoff and schedules its next Room retry deadline itself. Room multi-instance invalidation remains enabled. Emulator forced-job evidence spawned both `com.dvid.dcam` and `com.dvid.dcam:loggly` without crash. On `BODYCAMERA4HHITK`, job `u0a108/56324` is registered with the correct component, persisted/network constraints satisfied, and shown RUNNABLE/active after `cmd jobscheduler run -f`; ActivityManager still never binds or forks `com.dvid.dcam:loggly`. Vendor ROM secondary-process execution remains the acceptance blocker.

**Exit:** not reached. Finish remaining physical/repetition tests and review raw evidence.

### Gate C - Prove media readiness

- Record at least one 30-second MP4 and capture one image.
- Verify staging isolation and finalized-only publication.
- Generate MD5 after MP4 finalization.
- Verify matching MD5 permits readiness; missing/mismatch blocks readiness/import.

**Exit:** repeatable finalized media set with valid readiness evidence.

### Gate D - Prove minimum contract

- Export `dcam.db`, `dcam_config.cson` and `Logs/logs.txt` beside finalized media under approved roots.
- Validate required device/operator/build values and exclude secrets.
- Confirm BDMA access is read/import safe and Temp remains invisible.

**Exit:** one reviewed Build 0.1 fixture.

### Gate E - BDMA integration

- Install final candidate APK on selected device.
- Produce fresh video/image/MD5/DB/CSON/log fixture.
- Run BDMA detection and import through ADB.
- Record import result, identity matching, media playback, MD5 verification and cleanup behavior.

**Exit:** BDMA imports complete fixture without corruption or critical crash.

### Gate F - Release evidence and demo

- Run clean focused tests, full local tests and `assembleDebug`.
- Record APK path, size and SHA-256.
- Link every completed item to Requirement, Design, Jira, PR, QA Test ID and evidence.
- Demo full Working Recording Slice without deferred service dependency.

**Exit:** Build 0.1 candidate satisfies DoD and Working Recording Slice gate.

## 8. Immediate ordered backlog

1. Decide whether app-private `DurableAudioTemp` is acceptable; then run a fresh physical battery-cut end to end on the fixed APK without startup crash.
2. Resolve `BODYCAMERA4HHITK` secondary-process policy or approve same-process Loggly fallback; then verify crash/outbox drain on device.
3. Reconcile current MD5 implementation with Data Contract readiness and negative cases.
4. Force encryption OFF and prevent `_enc` Build 0.1 output.
5. Remove or hard-disable startup cloud/remote-config execution.
6. Produce complete Device POC report from BWC evidence; add missing physical scenarios.
7. Freeze minimum DB/CSON/log fixture with BDMA team.
8. Run fresh 30-second video plus image fixture.
9. Execute BDMA ADB import and preserve evidence.
10. Rerun tests/build; record APK hash and traceability links.

## 9. Definition of Done

An item is Done only when all applicable fields exist:

| Field | Required proof |
|---|---|
| Requirement | Approved Confluence requirement/build applicability |
| Design | Approved design or reviewed POC decision |
| Jira | Issue with Acceptance Criteria and dependencies |
| PR | Reviewable changeset mapped to Jira |
| QA Test ID | Applicable test case and expected result |
| Build evidence | Command, timestamp, result and artifact |
| Device evidence | Device/firmware, logs, screenshots/media and measured result |
| Integration evidence | BDMA result when contract/import behavior changes |

Missing required field means In Progress or Blocked, never Done.

## 10. Stop conditions and escalation

Report immediately and stop local requirement invention when any condition occurs:

- CameraX or selected Camera SDK cannot satisfy target-device behavior.
- FGS/lifecycle model loses or corrupts recording in required scenarios.
- Approved storage root is not stable, writable or visible through required ADB/BDMA flow.
- MD5/readiness behavior conflicts between DCAM and BDMA.
- DB/CSON/log schema or permission expectation is unresolved.
- Jira Acceptance Criteria conflicts with Matrix, Delivery Profile or Data Contract.

Decision owner must update Confluence/Jira before implementation changes contract behavior.

## 11. Local document disposition

| Existing local document | New role |
|---|---|
| `plans/mvp-delivery-plan.md` | Historical planning reference; this report owns current order and gate summary |
| `plans/build-0.1-critical-fix-checklist.md` | Implementation history; MD5/readiness conclusions need correction against current Data Contract |
| `current-repo/current-state.md` | Detailed code inventory appendix |
| `poc-capability-assessment.md` | Pre-implementation POC forecast; superseded where 2026-07-13 evidence exists |
| `sprint-0-action-checklist.md` | Historical risk checklist; not independent release authority |
| `evidence/confluence-refresh-gap-report-2026-07-15.md` | Current refresh snapshot; gaps merged here |
| `evidence/build-0.1-item-9-adb-recovery-2026-07-13.md` | Accepted raw device evidence appendix |

Use this report for daily Build 0.1 coordination. Update it when source status, POC evidence, BDMA contract decision or gate result changes.
