# DCAM Release & Build Applicability Matrix

**Page ID**: 51020012  
**Version**: 11  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51020012

---


DCAM Release & Build Applicability Matrix

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Release / Build Applicability Matrix

Version

Approved 1.8

Status

Approved

Approval Scope

Build applicability for DCAM MVP Internal Build 0.1, DEC-01–DEC-07, Important Media Conditional — Not Activated, legacy MD5 exclusion guardrail, DEC-P2-WEB-01 Phase 2 Web Portal minimum-scope boundary and mandatory GMS-free Android runtime guard.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead / BDMA Lead / Cloud Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

02  Sprint Operations

Target Audience

PM/BA, Product Owner, Tech Lead, Developers, BDMA Team, QA, Factory, Security Reviewer

Last Updated

2026-08-25

Related Jira

None

Related Documents

DCAM MVP Scope, DCAM Roadmap, DCAM 9-Month Development Plan, DCAM Architecture Delivery Profile, DCAM Requirements Home, DCAM Architecture Home, DCAM QA Test Strategy & Test Matrix, DCAM Documentation Governance, ADR - DCAM GMS-free Android Runtime Baseline, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice , [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Tài liệu này là source of truth để xác định requirement, design và QA test group nào áp dụng cho từng build của DCAM.

Requirements and designs define what DCAM must support.
This matrix defines when each rule becomes applicable to a build.
## 2. Current Active Build Baseline

Active Build Profile = DCAM MVP Internal Build 0.1
Active Delivery Gate = Working Recording Slice
Active Phase = Phase 1 - MVP Foundation
Build `0.1` tập trung vào:

```
Record / Capture → Save / Finalize Media → Minimal DB / CSON / logs.txt → BDMA sample import
```

## 3. Applicability Status

Status

Meaning

Required

Phải implement và pass applicable QA trước khi accept build.

Conditional

Chỉ bắt buộc khi điều kiện/feature được explicitly enabled.

Deferred

Thuộc phase sau và không block build hiện tại.

Not Applicable

Không thuộc mục tiêu của build.

POC Blocked

Chờ Device POC hoặc decision trước khi freeze implementation.

A document-level P0 does not automatically mean P0 for every build.
Build-level applicability in this matrix controls release planning and QA execution.
## 4. Build Profiles

Build Profile

Phase

Primary Goal

DCAM MVP Internal Build 0.1

Phase 1

Working recording/capture/storage/BDMA vertical slice.

Secure Platform MVP Build 0.2

Phase 2

Device/User, provisioning, platform and security foundation.

Advanced Communication Beta Build 0.3

Phase 3

Live Streaming, PTT, GPS route and advanced communication.

Customer Pilot / Production Candidate

Phase 4

Full applicable regression and production readiness.

## 5. Core Feature Applicability

Feature / Area

Build 0.1

Build 0.2

Build 0.3

Pilot

Recording / Capture

Required

Required

Required

Required

Storage / Finalization

Required

Required

Required

Required

Minimal DB / CSON / logs.txt

Required

Required

Required

Required

BDMA sample import

Required

Required E2E

Required

Required

Local Operational Logging

Required

Required

Required

Required

Loggly / Crashlytics cloud providers

Conditional / Deferred

Required when enabled

Required

Required or approved fallback; Crashlytics remains optional telemetry and never a GMS dependency

Local `serial_number`

Required minimal

Required

Required

Required

Cloud identity / Web Portal provisioning

Deferred

Required

Required

Required

User/Auth foundation

Deferred unless explicitly enabled

Required

Required

Required

Full Device Owner / Kiosk

Deferred; POC only if needed

Conditional / POC Blocked

Required if active

Required for production profile

Remote Config / Self Update

Deferred

Required foundation

Required

Required or documented exception

AI / Live Streaming / PTT / Full GPS route

Not Applicable

Deferred

Required according to approved scope

Required only if included in pilot scope

Factory `READY_TO_SHIP` acceptance

Not Applicable

Conditional

Conditional

Required

### 5.1 Phase 2 Web Portal Provisioning Boundary

[Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence) applies only to `Secure Platform MVP Build 0.2` and later.

Boundary

Phase 2 / Build 0.2 Interpretation

Required scope

Factory Worker Login/Workspace, DCAM provisioning QR with read-only `serial_number`, approved `owner_name`/`manufacture_date`, backend create/restore `dcam_cloud_device_id`, safe result and audit.

Excluded scope

Customer/public/general administration/fleet portal, video viewer/reporting/general user management, frontend direct-write, manual serial entry, Device Owner, Android serial injection, factory acceptance/shipment decision, Remote Config, Self Update, remote management, Live Streaming, PTT and AI.

Pending boundary

BFF identity/session/RBAC security, QR signature/replay, worker lifecycle, owner validation, retention/reconciliation and deployment/environment detail remain Pending.

### 5.2 GMS-free Android Runtime Guard

[ADR - DCAM GMS-free Android Runtime Baseline](/wiki/spaces/DVID/pages/69730306/ADR+-+DCAM+GMS-free+Android+Runtime+Baseline) is a mandatory cross-build Android guard. It does **not** activate deferred Phase 2 features for Build 0.1.

Guard

Build 0.1

Build 0.2

Build 0.3

Pilot / Production Candidate

No prohibited Android dependency/flow (`com.google.android.gms:*`, Play Store, FCM, Analytics, Play Integrity, Google account maintenance)

Required — no new dependency/flow

Required

Required

Required

Firebase Crashlytics boundary

Conditional when explicitly enabled; local diagnostics remain required

Optional telemetry only; no GMS dependency

Optional telemetry only; no GMS dependency

Optional telemetry only; no GMS dependency

Target firmware operation without GMS/Play Store

POC Blocked / evidence when target profile is exercised

POC Blocked until target-device evidence

Required before profile acceptance

Required production gate

Remote APK update source

Deferred

BFF-authorized R2/CDN foundation; no Play Store fallback

BFF-authorized R2/CDN

BFF-authorized R2/CDN; approved local/factory package only as controlled fallback

## 6. Build 0.1 Release Gate

Build `0.1` must implement:

Runnable APK
RecordingController / CameraService boundary
Single-threaded state coordination
StorageService with temp/final flow
Minimal dcam.db and dcam_config.cson where required
Sanitized Logs/logs.txt
MVP Data Contract output
Media filename dùng DEVICE_TOKEN 6–10 ký tự và OPERATOR_TOKEN đúng 6 ký tự
BDMA detects/imports sample media
Build `0.1` must not be blocked by:

Web Portal provisioning
Cloud device identity
Full operator authentication
Full Device Owner / kiosk policy
Remote Config
Self Update
Advanced User Management
Realtime AI
Live Streaming
PTT
Full GPS route
DEC-P2-WEB-01 has no retroactive effect: Web Portal provisioning remains `Deferred` and non-blocking for Build 0.1.

## 7. QA Interpretation

```
A QA test marked P0 is a release blocker only when its feature/test group is applicable to the current build profile.
```

For Build `0.1`, mandatory QA groups are Working Recording Slice, Recording/Capture, Storage/Finalization, minimal DB/CSON/logs, media filename token validation, BDMA sample import, critical-path concurrency, MVP performance and sensitive-data sanitization.

`QA-MEDIA-IMP-001` giữ `Priority = P0`, nhưng không yêu cầu execution evidence và không chặn Build 0.1 khi Important Media applicability là `Conditional — Not Activated`.

## 8. Change Control

Update this Matrix first when a feature moves between builds.
Update Roadmap / MVP Scope / Development Plan if phase scope changes.
Update QA applicability and release gates.
Update Project Home navigation/status summary.
A feature may not move from `Deferred` to `Required` through an isolated Jira task without updating this Matrix or documenting an approved temporary exception.

## 9. Practical Conclusion

Current active build = DCAM MVP Internal Build 0.1.
Current gate = Working Recording Slice.
Build 0.1 requires recording, capture, storage, minimal DB/CSON/logs and BDMA sample import.
Build 0.1 is not blocked by Web Portal, full auth, full kiosk, Remote Config, Self Update, AI, Live Streaming or PTT.
Build 0.2 activates identity/provisioning/user/platform foundations.
Build 0.3 activates advanced communication.
Pilot applies all features and tests included in approved pilot scope.
## 10. Build 0.1 Approved Decision Applicability

Applicability dưới đây được phê duyệt bởi [Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice](/wiki/spaces/DVID/pages/51642452/Decision+Brief+DCAM+MVP+Internal+Build+0.1+Working+Recording+Slice) và override mọi generic/target-state row mâu thuẫn đối với Build 0.1.

Capability / Requirement

Build 0.1 Applicability

Condition / Evidence

NCC-036V / Android 12 / API 31 / 877AOOAKN1_RK2_V009

Required

Qualification giữ Pending Device POC đến khi có physical-device evidence.

Android platform Camera API

Required

Camera1/Camera2 và capability chờ Device POC.

Vendor SDK

Not Applicable

Không thuộc Build 0.1.

Internal storage

Required

DEC-02.

External storage

Not Applicable

Không fallback.

Auto storage mode

Not Applicable

Không fallback.

MP4 MD5 sidecar

Required

Mọi MP4; integrity-only.

BDMA_READY after valid MD5

Required

Missing/mismatch/failure chặn import và release evidence.

Login UI / Authentication / Roles

Not Applicable

Target-state requirements giữ cho build sau.

Static Build 0.1 Operator

Required

B01OPR / Build 0.1 Operator; không phải authenticated identity.

Media Filename Tokens

Required

DEVICE_TOKEN = validated serial_number snapshot, 6–10 ký tự; OPERATOR_TOKEN = B01OPR, đúng 6 ký tự; chỉ [A-Z0-9], không underscore và không silent truncation. Device POC phải xác nhận serial_number thực tế.

Important Media creation/marking (`_IMP` generation hoặc move vào `Media/IMP`)

Conditional — Not Activated

Chỉ trở thành Required khi Release & Build Applicability Matrix hoặc approved temporary exception kích hoạt rõ ràng. Không chặn Working Recording Slice hoặc Build 0.1 release gate khi chưa được kích hoạt.

Basic Device Status

Required

Battery level, Internal free storage, GPS Available/Unavailable/Unsupported.

GPS coordinates / route / continuous tracking

Not Applicable

Không thuộc WRS.

Multi-model / multi-firmware / fleet qualification

Not Applicable

WRS chỉ pass trên một reference configuration.

Contract recognition đối với `_IMP`, `_IMP_enc` và `Media/IMP` vẫn được giữ để bảo toàn DCAM-BDMA Data Contract. Việc parser/BDMA nhận diện các contract values này không tự kích hoạt Important Media creation/marking workflow.

Applicability này áp dụng cho REC-VID-003, REC-IMG-003, REC-AUD-003 và phần Important Media của REC-EMG-004/005/009. Encrypted-media implementation vẫn phụ thuộc approved Security Profile và applicability riêng.

## 11. Legacy MD5 Policy Applicability

Legacy `missing MD5 → Unverified import` và `BDMA_READY-before-checksum` không phải là một Build Profile. Các behavior này hiện không được gán cho bất kỳ approved Build Profile nào: Build 0.1 explicitly blocks, còn Build 0.2, Build 0.3 và Pilot/Shipment chưa có approved mapping.

Legacy Policy

Build 0.1

Build 0.2

Build 0.3

Pilot / Shipment

Missing MP4 MD5 → Unverified import

Not Applicable

Not Applicable — no approved mapping

Not Applicable — no approved mapping

Not Applicable — no approved mapping

BDMA_READY before required checksum completes

Not Applicable

Not Applicable — no approved mapping

Not Applicable — no approved mapping

Not Applicable — no approved mapping

Future activation chỉ hợp lệ khi PM phê duyệt một named Build Profile và cập nhật đồng thời Matrix, Data Contract, QA Matrix, Traceability Matrix và release gate. Đây là exclusion guardrail, không tạo Build Profile hoặc product behavior mới.

`MD5 mismatch` không thuộc legacy exception: Data Contract tiếp tục yêu cầu không import và không cleanup source khi verification fail.