# DCAM Recording & Capture Design

**Page ID**: 48529484  
**Version**: 16  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529484

---


# DCAM Recording & Capture Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

1.5

Status

Approved Provisional Baseline

Approval Scope

Build 0.1 runtime direction; Camera capability và exact Camera1/Camera2 Pending Device POC

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / Android Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, QA, BDMA Team, Support

Last Updated

2026-07-20

Related Jira

None

Related Documents

[DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry), [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix), DCAM Performance Budget & Resource Constraints, DCAM QA Test Strategy & Test Matrix, 01 - Recording & Capture Requirements, DCAM-BDMA Data Contract, DCAM State Machine Design, DCAM Android Operation Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

Dependencies / Blockers

Device POC and Technical Review: confirm Camera API capability, Camera1/Camera2 decision, physical storage/path behavior and relevant execution evidence.

## 1. Purpose

Trang này định nghĩa runtime behavior cho recording, image/audio capture nếu enabled, emergency evidence, operator attribution, finalization và recovery.

Chỉ `RecordingController` được quyết định start/stop/finalize/recover session. UI, sensor và AI chỉ emit command/event.

Status interpretation:

Approved Provisional Baseline
    = approved Build 0.1 design direction for implementation and QA
    ≠ target-device SDK/OEM validation complete
    ≠ every later-phase recording capability active
## 2. Recording Flow

IDLE
→ PRECHECKING
→ PREPARING_CAMERA
→ PREPARING_STORAGE
→ STARTING
→ RECORDING
→ STOPPING
→ FINALIZING
→ BDMA_READY
→ COMPLETED
Operator policy follows the active Build Profile:

Build 0.1
    → approved no-login/basic operator placeholder is allowed by Architecture Delivery Profile

Build profile with full auth activated
    → normal recording requires active operator session

Emergency recording when activated
    → may use EMERGENCY_OVERRIDE_ADMIN when no operator is logged in
## 3. Precheck

Check

Required

Active-build operator policy or emergency override

Yes

Camera capability/permission

Yes

Storage writable and free-space threshold

Yes

DB available

Yes

Audio permission

If audio enabled

Battery/thermal

Policy-based; numeric limits remain Device POC dependent

GPS/AI

Not hard dependencies for core recording

Update/finalization/recovery not active

Yes

Current provisional precheck budget:

```
Recording precheck duration ≤ 500ms
```

## 4. Performance Baseline

Current provisional values are owned by **DCAM Performance Budget & Resource Constraints**, whose status is `Approved Pending Device POC`.

Metric

Current Target

Warm recording start

`≤ 2.0s`

Stop latency

`≤ 1.5s`

Critical finalization

`≤ 5.0s`

Image capture

`≤ 1.5s`

Emergency recording start

`≤ 3.0s` when emergency scope is active

Continuous recording stability

`≥ 4 hours` MVP provisional target

Exact SDK behavior and device-specific adjustment remain Device POC inputs.

## 5. Finalization Pipeline

stop camera SDK
close temp/staging file
validate file and stable size
complete critical metadata/encryption step only if active approved profile requires it
move/rename to final Media path
update dcam.db
mark BDMA_READY
run non-critical checksum/export asynchronously when policy allows
`BDMA_READY` means safe for BDMA scan/import, not imported.

The current provisional critical finalization target is `≤ 5.0s`. Exact retry count, retry delay and recovery escalation remain implementation TBD.

Encryption behavior is controlled by an approved Security Profile. Encrypted-media naming does not approve an encryption algorithm.

## 6. Emergency Behavior

Emergency recording is a target capability and does not block Build 0.1 unless explicitly activated by the Applicability Matrix.

Current State

Behavior when Emergency capability is active

Idle with operator

Start emergency session with operator snapshot.

Idle without operator

Use emergency override identity.

Starting/preparing

Upgrade current intent if safe.

Recording

Mark current session important and/or add emergency marker.

Stopping/finalizing

Do not interrupt finalization; queue event safely.

Failed/recovery

Preserve evidence and diagnostics.

Emergency does not grant Admin or Maintenance capability.

## 7. Capture Direction

Capture Type

Direction

Build 0.1

Image

Finalize and make BDMA-ready when feature eligible.

Required

Audio-only

Optional; exact format/policy TBD.

Deferred / Conditional

Capture during recording

Allowed only when target SDK supports it and policy approves.

POC-dependent

Capture during finalization

Rejected/deferred.

Required guard

## 8. Recovery

When state is uncertain:
preserve temp/staging/final candidate
preserve operator snapshot or Build 0.1 placeholder attribution
reconcile DB and files
mark recovery result
never delete evidence automatically
Recovery covers crash, reboot, service kill, interrupted stop/finalization, storage removal and DB/file mismatch.

## 9. QA Coverage

**DCAM QA Test Strategy & Test Matrix** owns release validation, while **DCAM Release & Build Applicability Matrix** decides which QA groups block each build.

Build 0.1 required coverage includes:

QA-WRS-001
QA-REC-001
QA-IMG-001…004
QA-STO-001…002
QA-BDMA-001 and applicable checksum behavior
QA-PERF-001…008 and QA-PERF-010 applicable subset
recovery and sensitive-data sanitization
Emergency, full operator-auth, kiosk, update and provisioning tests are deferred/conditional unless activated.

Requirement-ID-level coverage and gaps are maintained in **DCAM Requirement–Design–Test Traceability Matrix**.

## 10. Resolved and Remaining Decisions

Item

Status

Build 0.1 recording flow

Approved Provisional Baseline

Image capture Build 0.1 scope

Approved Provisional Baseline

Recording performance targets

Approved Pending Device POC via Performance Budget

Critical finalization target

Provisional `≤ 5.0s`

Build-scoped recording/image QA group

Defined in QA Matrix

CameraX / Camera2 / vendor SDK

TBD / Device POC

Pause/resume support

TBD / Device POC

Image capture during active recording

TBD / SDK + Product

Audio-only format/policy

TBD / Product + Device POC

Pre-record physical buffer format

TBD / Device POC

Pre-record before login

TBD / Product + Security

Post-record exact duration

TBD / Product

Metadata embedded vs sidecar/DB

TBD / Data Contract + Implementation

Finalization retry count/backoff

TBD / Implementation

Recovery marker exact format

TBD / Implementation

`media_session` exact fields

TBD / SQLite Design

Emergency split segment vs marker

TBD / Product + Device POC

## 11. Promotion Gate

This document may move from `Approved Provisional Baseline` to `Approved for Build <profile>` or `Production Approved` only when the applicable gates are complete:

Gate

Evidence

Target camera provider

CameraX/Camera2/vendor SDK decision and device evidence.

Target model/firmware

Device POC baseline recorded.

Recording/image QA

Applicable QA tests executed with evidence.

Performance

Applicable provisional targets pass or approved adjustment exists.

Storage/finalization

Recovery and near-full/full tests pass.

Operator/security policy

Active-build auth/emergency/encryption policies approved.

Traceability

Requirement → Build → Design → QA → Jira → Evidence complete for active scope.

## 12. Practical Conclusion

Build 0.1 recording, image capture, finalization and recovery direction is approved as a provisional baseline.
Performance values and provider behavior remain Device POC dependent.
Later-phase emergency, audio, full auth and encryption behavior does not automatically block Build 0.1.
Current status is Approved Provisional Baseline, not Production Approved.
## 13. Build 0.1 Camera and Finalization Overlay

Camera provider phải dùng Android platform Camera API phía sau CameraService.

Vendor SDK là Not Applicable cho Build 0.1.

Không lựa chọn Camera1 hoặc Camera2 trước khi Device POC xác nhận capability.

MP4 được finalize trước khi MD5 work được enqueue bất đồng bộ.

Recording critical path không chờ hash computation, nhưng artifact không được publish BDMA_READY trước MD5 success.

MD5 failure/missing/mismatch phải giữ MP4, ghi log và block BDMA import/release evidence.

Image no-MD5 behavior không đổi.

Reference configuration vẫn Pending Device POC; section này không xác nhận recording capability hoặc performance.