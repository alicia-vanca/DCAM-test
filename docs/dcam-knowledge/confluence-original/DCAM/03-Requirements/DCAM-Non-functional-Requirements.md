# DCAM Non-functional Requirements

**Page ID**: 48595009  
**Version**: 16  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595009

---


# DCAM Non-functional Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Non-functional Requirements

Version

Approved 1.14

Status

Approved

Approval Scope

System quality direction; numeric performance budgets và hardware-dependent behavior thuộc Performance Budget và Device POC.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / QA Lead / Security Reviewer / BDMA Lead / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements

Target Audience

PM/BA, Tech Lead, Developers, QA, Security Reviewer, Support, Factory

Last Updated

2026-08-25

Related Jira

None

Related Documents

DCAM Release & Build Applicability Matrix, DCAM Performance Budget & Resource Constraints, DCAM Concurrency & Threading Model Design, 07 - Logging & Diagnostics Requirements, DCAM Logging & Diagnostics Design, DCAM Security & Encryption Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM QA Test Strategy & Test Matrix, DCAM Device POC & Hardware Validation Report

## 1. Purpose

GMS-free quality guard: resolved runtime dependency graph, manifest/source flows and target-device operation must meet ADR - DCAM GMS-free Android Runtime Baseline. Crashlytics remains optional and provider degradation must never affect core operation.

Trang này định nghĩa quality baseline cho reliability, performance, resource use, security, diagnostics, compatibility, maintainability và production operation.

Numeric targets thuộc **DCAM Performance Budget & Resource Constraints**. Build-level applicability thuộc **DCAM Release & Build Applicability Matrix**.

## 2. Reliability Requirements

Requirement

Direction

Status

Recording safety

Crash/provider/config failure không được silently corrupt active/final media.

Approved

Evidence preservation

Uncertain file/DB state phải preserve artifact và enter recovery.

Approved

Offline operation

Core recording/storage/local diagnostics hoạt động không phụ thuộc cloud.

Approved

Recovery

App phải recover safely sau crash, reboot, process/service kill và interrupted finalization.

Approved

Provider degradation

Loggly/Crashlytics/backend outage không tự động làm recording fail.

Approved

Kiosk fail closed

Missing required production policy không được mở unrestricted field operation.

Approved Direction

Update fail safe

Update failure không để device unrestricted hoặc mất current usable version khi có thể.

Approved Direction

## 3. Performance and Resource Requirements

Measurable values không còn `TBD` toàn bộ. Provisional targets đã được định nghĩa trong Performance Budget và phải được Device POC validate.

Key MVP targets:

Metric

Current Target

Warm recording start

`≤ 2.0s`

Recording stop

`≤ 1.5s`

Critical finalization

`≤ 5.0s`

Image capture

`≤ 1.5s`

Emergency recording start

`≤ 3.0s`

Recording precheck

`≤ 500ms`

MainThread block

`< 500ms`

Cold boot to recording ready

`≤ 8s`

App restart to recording ready

`≤ 4s`

Continuous recording stability

`≥ 4 hours` MVP target

Minimum start free space

Estimated 30-minute recording + `500 MB` safety margin

Critical finalization headroom

At least `500 MB` or approved device-specific value

Battery/thermal numeric budgets and device-specific adjustment remain POC-dependent.

## 4. Logging and Diagnostics Requirements

Operational Logging → Loggly through local-first queue and Backend Relay
Crash & Stability Monitoring → Firebase Crashlytics (optional; no GMS dependency permitted)
BDMA diagnostic artifact → Logs/logs.txt
Performance operational events use `[PERF]` / `[THREAD]` through Operational Logging. Crash/ANR and approved unexpected non-fatal context use Crashlytics.

Sensitive values must not appear in local logs, upload queue, Loggly, Crashlytics or `logs.txt`.

## 5. Security Requirements

Area

Direction

Identity

Use `serial_number` and `dcam_cloud_device_id`; no Android system identifier dependency.

Credential storage

Protected representation only; no plaintext.

Factory Wi-Fi exception

Hardcoded in approved APK by current decision, but excluded from logs/API/QR/records/evidence.

Maintenance

Authorized Admin/Maintenance role + Maintenance Password Gate + Controlled Mode only.

Backend authority

Web frontend cannot directly write production provisioning data.

Package update

Validate package identity, checksum, signature, version and compatibility.

Encryption

Exact media/DB algorithm and key management remain Security Review decisions.

## 6. Dedicated-device and Maintenance Requirements

The entry model is no longer wholly `TBD`:

Admin or approved Maintenance role
    ↓
Maintenance Password Gate
    ↓
Runtime safe-state validation
    ↓
Controlled Maintenance Mode
    ↓
Approved targets only
    ↓
Restore Lock Task and User Restrictions
No full Android unrestricted mode.

Exact credential complexity, rotation, reset, failed-attempt values and maintenance timeout remain TBD.

DPC ownership direction is also established:

Current preferred model = DCAM-as-DPC / local Device Owner
Factory setup baseline = DSetup + ADB dpm set-device-owner when required
External EMM / Android Management API = not applicable
Exact component/wrapper and target-device feasibility remain Device POC items.

## 7. Compatibility Requirements

Area

Requirement

Android/OEM

Validate on approved BodyCamera model/firmware.

Camera

Isolate CameraX/Camera2/vendor SDK behind adapter.

Storage

Validate physical paths, scoped storage and ADB visibility.

Kiosk

Validate Device Owner, Lock Task, restrictions and Home behavior on target firmware.

GMS-free Android Runtime

Core operation, local diagnostics, kiosk recovery, BFF sync and safe update deferral must not require GMS/Play Store/account; mandatory production profile guard.

BDMA

Follow approved Data Contract and read/write boundary.

## 8. Maintainability Requirements

Use authoritative ownership.
Use stable reason codes and structured events.
Keep platform/provider logic behind adapters.
Avoid copying rule tables across documents.
Trace implementation-blocking TBDs to Device POC, Security Review, Product decision or deployment owner.
## 9. Resolved and Remaining Decisions

Item

Status

Performance numeric baseline

Defined in Performance Budget; POC validation required

Performance telemetry provider

Approved: Operational events → Loggly; crash/ANR → Crashlytics

Maintenance entry method

Approved baseline: role + Maintenance Password Gate + Controlled Mode

Maintenance exact policy values

TBD / Security + Product

DPC ownership model

Approved direction: DCAM-as-DPC / local Device Owner

Factory Device Owner setup

Approved baseline: DSetup + ADB `dpm set-device-owner` when required

Exact DPC component/OEM feasibility

TBD / Device POC

Battery/thermal numeric limits

TBD / Device POC

Media/DB encryption algorithm and keys

TBD / Security Review

Target model/firmware

TBD / Device POC

## 10. Practical Conclusion

The quality baseline now distinguishes approved direction from device/security-specific values.
Performance, telemetry, maintenance entry and DPC ownership are no longer treated as fully undefined.
Only real POC, security and deployment details remain TBD.