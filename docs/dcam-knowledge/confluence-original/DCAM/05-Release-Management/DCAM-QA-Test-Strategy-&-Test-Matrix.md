# DCAM QA Test Strategy & Test Matrix

**Page ID**: 49545345  
**Version**: 22  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49545345

---


# DCAM QA Test Strategy & Test Matrix

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

QA Strategy / Test Matrix

Version

Approved 2.7

Status

Approved

Approval Scope

Build 0.1 test definitions and release gates; Important Media Conditional — Not Activated; DEC-P2-WEB-01 Phase 2 Web Portal planning boundary recorded without Jira mapping, execution evidence or coverage uplift.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / QA Lead / BDMA Lead / Security Reviewer / Cloud Lead / Android Lead / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

05 Release Management

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Team, Cloud/WebServer Team, Security Reviewer, Factory, Support, Stakeholders

Last Updated

2026-08-25

Related Jira

[DCAM-10](https://ducviet.atlassian.net/browse/DCAM-10) — Build 0.1 QA & Evidence Package; detailed mapping thuộc Traceability Matrix

Dependencies / Blockers

Technical Review cho minimal DB/CSON physical assertions; PR/build/test execution evidence chưa được link.

Related Documents

[DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix), [DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry), DCAM Release & Build Applicability Matrix, DCAM Requirements Home, DCAM Non-functional Requirements, 07 - Logging & Diagnostics Requirements, 07 - Logging, Diagnostics, Performance & Security, DCAM Logging & Diagnostics Design, DCAM Performance Budget & Resource Constraints, DCAM-BDMA Data Contract, DCAM Web Portal & Device API Contract, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, DCAM State Machine Design, DCAM Device Capability & Feature Eligibility Design, DCAM Device Provisioning Web Portal Design, DCAM Device Provisioning Web Portal App Design, DCAM Device Provisioning Web Portal Implementation Design, 09 - System Settings Requirements, DCAM Self Update Design, DCAM Android Development Standard, DCAM Device POC & Hardware Validation Report, DCAM Factory Provisioning & Device Production SOP, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice , [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Tài liệu này định nghĩa QA strategy và test matrix cho **DCAM Android BodyCamera Application**.

Tài liệu bao phủ cả Target System, nhưng release blocking luôn được quyết định theo Active Build Profile.

Priority answers: How severe is failure when the test is applicable?
Build Applicability answers: Must this test run and pass for this build?

Release Blocker = Priority P0 AND Applicability Required
Conditional P0 becomes blocker only when its activation condition is true.
Deferred / Not Applicable P0 does not block the active build.
Source of truth cho `Required / Conditional / Deferred / Not Applicable / POC Blocked` là **DCAM Release & Build Applicability Matrix**.

Current baseline:

Active Build = DCAM MVP Internal Build 0.1
Active Gate = Working Recording Slice
### 1.1 Build 0.1 Mandatory QA Scope

Working Recording Slice
Recording and image capture
Storage and critical finalization
Minimal DB / CSON / logs.txt
BDMA sample detect/import
Critical-path concurrency and MainThread safety
MVP performance subset
Sensitive-data sanitization
Core offline/provider-degradation behavior when implemented
The following do not block Build 0.1 unless explicitly activated by Matrix exception:

Web Portal provisioning
Cloud identity
Full User/Auth
Full Device Owner / Kiosk / Maintenance
Remote Config
Self Update
Factory READY_TO_SHIP
Live Streaming / PTT / Full GPS Route / AI
Important Media creation/marking
## 2. Scope

Area

Target-system QA Scope

Build 0.1 Interpretation

Recording / Capture

Normal/emergency recording, image/audio capture, operator attribution.

Normal recording + image capture required; emergency/audio conditional or deferred unless activated.

Storage

Temp/final, `BDMA_READY`, low/full storage, recovery.

Required.

SQLite / CSON / Logs

Runtime state, contract artifacts, migration/write-back.

Minimal Build 0.1 subset required; full migration/write-back deferred.

BDMA Integration

ADB discovery, import, checksum, logs and cleanup.

Sample detect/import and applicable contract behavior required.

Performance / Concurrency

Latency, ANR, DB/I/O, memory and stability.

MVP subset required.

Logging & Diagnostics

Local-first logging, sanitization, provider relay/Crashlytics.

Local-first and sanitization required; cloud provider tests conditional when enabled.

User / Auth

Login/session/emergency override.

Deferred except approved Build 0.1 placeholder/operator policy.

Provisioning

Web Portal QR and cloud identity.

Deferred for Build 0.1; Phase 2 / Build 0.2 minimum factory provisioning is planned under DEC-P2-WEB-01, with no execution evidence or Build 0.1 gate change.

Kiosk / Maintenance

Device Owner, Lock Task, restrictions and controlled maintenance.

Deferred / POC only if explicitly activated.

Remote Config / Self Update

Fetch/apply/reject/update/recovery.

Deferred.

Factory Production

READY_TO_SHIP / QUARANTINED.

Not Applicable for Build 0.1; required for shipment build.

Security

Sensitive logging, credentials, identity, update and encryption.

Sanitization required; full cryptographic profile pending Security Review.

### 2.1 DEC-P2-WEB-01 Phase 2 Planning Boundary

[Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence) confirms the minimum factory provisioning scope for Build 0.2. Existing `QA-PROV-001…003` remain planned and Deferred for Build 0.1; this update does not create a new test ID, Jira mapping, execution evidence, `Covered` result or Build 0.1 release blocker.

## 3. Out of Scope

Area

Reason

Final production security certification

Cần Security Review riêng.

Full BDMA UI test

Thuộc BDMA QA scope; tài liệu này chỉ cover DCAM-facing integration.

Cloud provider production SLA

Thuộc Cloud/WebServer operation; QA chỉ verify application behavior khi provider available/unavailable.

Final hardware certification

Thuộc Device POC / Hardware Validation Report.

External EMM / Android Management API / Managed Google Play policy-driven update

Không áp dụng cho current device baseline.

Future Live Stream / PTT / AI Mode full behavior

Future requirements/designs chưa approved/activated.

Battery/thermal numeric budget

Deferred until Device POC theo Performance Budget.

## 4. Authoritative References

Test Area

Source of Truth

Build Applicability

DCAM Release & Build Applicability Matrix

Requirement-to-test mapping

DCAM Requirement–Design–Test Traceability Matrix

Functional Requirements

DCAM Requirements Home

Non-functional Requirements

DCAM Non-functional Requirements

Logging Requirements

07 - Logging & Diagnostics Requirements

Logging Provider Ownership

07 - Logging, Diagnostics, Performance & Security

Logging Implementation

DCAM Logging & Diagnostics Design

Performance Budget

DCAM Performance Budget & Resource Constraints

Data Contract

DCAM-BDMA Data Contract

API Contract

DCAM Web Portal & Device API Contract

Android Runtime

DCAM Android Operation Design

Kiosk Policy

DCAM Android Device Owner & Kiosk Policy Design

In-app Console

DCAM In-App Operation, Device Settings & Media Console Design

Recording

DCAM Recording & Capture Design

Storage

DCAM Storage Design

SQLite

DCAM SQLite Database Design

Security

DCAM Security & Encryption Design

State Guards

DCAM State Machine Design

Device POC Evidence

DCAM Device POC & Hardware Validation Report

Factory Acceptance

DCAM Factory Provisioning & Device Production SOP

## 5. Test Strategy

Test Level

Purpose

Owner

Unit Test

Domain logic, validators, state transitions, naming, sanitization and eligibility.

Android Dev

Integration Test

DB, StorageService, RecordingController, logging and BDMA boundaries.

Android Dev / QA

Contract Test

Data Contract, API Contract, file naming, checksum, DB/schema and access boundaries.

QA / BDMA / Backend

Performance Test

Active-build metrics from Performance Budget.

QA / Android Dev

Device Test

Camera, storage, boot, lifecycle, ADB and active device-policy scope on BodyCamera.

QA / Android Dev

Recovery Test

Crash, reboot, service kill, DB/storage/provider failure and interrupted finalization.

QA

Security Test

Sensitive-data protection and active security profile.

Security Reviewer / QA

Regression Test

Only groups activated for the release Build Profile.

QA

Acceptance Test

Build-specific end-to-end gate; Factory path only for shipment builds.

PM / QA / Stakeholders

## 6. Test Environment Matrix

Environment

Purpose

Build 0.1

Later Build / Condition

Android Emulator

Early UI/domain/unit integration.

Optional

Optional

Android Phone

Development smoke test.

Optional

Optional

BodyCamera Device - NCC-036V / Android 12 / API 31 / 877AOOAKN1_RK2_V009

Recording/storage/performance/ADB baseline trên identifiable physical device.

Required

Configuration khác cần impact review/regression và không mặc nhiên pass.

Windows PC with BDMA

Sample detect/import and logs artifact.

Required

Required

Offline Environment

Core offline operation.

Required

Required

Near-full / Full Storage Environment

Finalization and recovery safety.

Required

Required

Provider Failure Environment

Ensure diagnostics provider failure does not block core.

Conditional when cloud provider integrated

Required when provider enabled

GMS-free Android Runtime

No prohibited GMS/Play Store/account dependency or flow; target-device evidence required for production profile.

Required dependency/source guard

Required production gate

Web Portal Test Backend

Provisioning and remote config.

Deferred

Required for Build 0.2 provisioning scope

DCAM-as-DPC / Device Owner Device

Kiosk behavior.

Deferred / POC exception

Required when kiosk profile active

Artifact Provider Backend

Self Update.

Deferred

Required when Self Update active

Factory-reset Device

Factory SOP.

Not Applicable

Required before shipment

Loggly / Crashlytics test projects

Cloud observability.

Conditional when enabled

Required when enabled for release profile

## 7. Master Test Matrix

`Priority` and `Build 0.1` are independent columns. Later-build applicability remains controlled by the Release & Build Applicability Matrix.

Test ID

Area

Scenario

Priority

Build 0.1

Source

Status

QA-WRS-001

Working Recording Slice

APK runs; record 30s; finalize; capture image; write minimal DB/CSON/logs; BDMA detects/imports sample artifacts.

P0

Required

MVP Scope + Architecture Delivery Profile

Draft

QA-LOG-001

Operational Logging

Operational event persists locally or in durable queue independently of cloud delivery.

P0

Required

Logging Requirements + Design

Draft

QA-LOG-002

Operational Logging

Local files/queue are bounded and do not fill storage.

P0

Required

Logging Design + Storage

Draft

QA-LOG-003

Backend Relay / Loggly

Authenticated relay forwards structured event to Loggly.

P0

Deferred

Logging Design + API Contract

Draft

QA-LOG-004

Provider Failure

Provider outage does not block recording/finalization/core offline operation.

P0

Conditional when provider client exists

Logging Requirements + Design

Draft

QA-LOG-005

Crashlytics

Fatal crash captured with safe bounded context.

P0

Conditional when enabled

Logging Design

Draft

QA-LOG-006

Crashlytics

Unexpected non-fatal recorded; expected failures do not create spam.

P0

Conditional when enabled

Logging Design

Draft

QA-LOG-007

Sensitive Logging

Secrets and sensitive payloads are absent from every active logging channel.

P0

Required

Security + Logging Requirements

Draft

QA-LOG-008

GMS-free / Provider Independence

Local logs and BDMA artifact work without GMS/provider; provider outage does not affect core diagnostics.

P0

Required for production profile

Logging Requirements + Device POC + ADR

Draft

QA-LOG-009

BDMA Logs Contract

BDMA reads `logs.txt` read-only and does not modify/delete it.

P1

Required

Data Contract + Logging Design

Draft

QA-LOG-010

Correlation / Quality

Events contain safe timestamp/category/name/reason/correlation fields.

P1

Required

Logging Requirements + Design

Draft

QA-PERF-001

Performance

Recording start meets `PERF-REC-001`.

P0

Required

Performance Budget

Draft

QA-PERF-002

Performance

Recording stop meets `PERF-REC-002`.

P0

Required

Performance Budget

Draft

QA-PERF-003

Performance

Critical finalization đạt PERF-REC-003 đến finalized/checksum-pending; async MD5 hoàn tất trước BDMA_READY.

P0

Required

Performance Budget + Concurrency

Draft

QA-PERF-004

Performance

MainThread block meets `PERF-ANR-001`; no disk/network on MainThread critical path.

P0

Required

Performance Budget + Dev Standard

Draft

QA-PERF-005

Performance

State Coordinator and camera callback meet `PERF-ANR-002/003`.

P0

Required

Performance Budget + Concurrency

Draft

QA-PERF-006

Performance

DB transaction and busy retry meet `PERF-IO-004/PERF-ANR-007`.

P0

Required

Performance Budget + SQLite

Draft

QA-PERF-007

Performance

Recording precheck enforces `PERF-STOR-001`.

P0

Required

Performance Budget + Storage

Draft

QA-PERF-008

Performance

Near-full/full storage does not corrupt media and triggers safe handling.

P0

Required

Performance Budget + Storage

Draft

QA-PERF-009

Performance

App-owned thread count measured; total count recorded during POC.

P1

Recommended / POC

Performance Budget + Device POC

Draft

QA-PERF-010

Performance

Continuous recording meets active long-running stability target.

P0

Required

Performance Budget

Draft

QA-IMG-001

Image Capture

Capture and finalize `.jpg` successfully on selected BodyCamera.

P0

Required

Recording Requirements + Design

Draft

QA-IMG-002

Image Contract

Image naming/folder follows Data Contract; image không yêu cầu `.md5`; BDMA imports it without MP4 checksum enforcement.

P0

Required

Recording Requirements + Data Contract

Draft

QA-IMG-003

Image Performance

Image capture meets `PERF-REC-004`.

P0

Required

Performance Budget

Draft

QA-IMG-004

Image Recovery

Camera/storage/permission failure is controlled and does not crash app.

P0

Required

REC-IMG-005 + Recording Design

Draft

QA-MEDIA-NAME-001

Media Filename Tokens

Tên file video/image dùng DEVICE_TOKEN từ validated serial_number với 6–10 ký tự [A-Z0-9] và OPERATOR_TOKEN = B01OPR với đúng 6 ký tự [A-Z0-9]; MP4/MD5 dùng cùng base name.

P0

Required

DEC-07 + REC-VID-002 + REC-IMG-002 + Data Contract §7/§16.4

Draft

QA-MEDIA-NAME-002

Media Filename Validation

Token sai length/charset hoặc chứa underscore phải bị reject có kiểm soát; không silent truncation và artifact không hợp lệ không được trở thành BDMA_READY/import evidence.

P0

Required

DEC-07 + Data Contract §7/§16.4

Draft

QA-MEDIA-IMP-001

Important Media

Khi Important Media được explicitly activated: kiểm tra marking behavior, `_IMP` filename suffix, placement trong `Media/IMP` theo Data Contract, MD5 bắt buộc nếu artifact là MP4, chỉ `BDMA_READY` sau MD5 success và BDMA recognition/import đúng.

P0

Conditional — Not Activated

REC-VID-003 + Release & Build Applicability Matrix §10 + DCAM-BDMA Data Contract §6–§9/§16

Draft

QA-BOOT-001

Android Operation

Startup reaches the state required by the active Build Profile.

P0

Required

Android Operation + Delivery Profile

Draft

QA-KIOSK-001

Kiosk Policy

Production boot verifies required Device Owner state.

P0

Deferred

Kiosk Design

Draft

QA-KIOSK-002

Kiosk Policy

Missing required policy enters controlled required/degraded state.

P0

Deferred

Kiosk Design

Draft

QA-KIOSK-003

Lock Task

Package is allowlisted and enters Lock Task.

P0

Deferred

Kiosk Design

Draft

QA-KIOSK-004

Lock Task

Home/Recents/Back cannot escape approved kiosk experience.

P0

Deferred

Kiosk Design

Draft

QA-KIOSK-005

User Restrictions

Required restrictions apply or unsupported reason is recorded.

P0

Deferred

Kiosk Design

Draft

QA-KIOSK-006

Maintenance

Authorized maintenance enter/exit restores policy and records audit.

P0

Deferred

Kiosk + Security + Console

Draft

QA-KIOSK-007

Policy Recovery

Crash/process kill restores Lock Task or controlled recovery.

P0

Deferred

Android Operation + Kiosk

Draft

QA-KIOSK-008

No External EMM

Active profile has no external EMM/AMA/Managed Google Play dependency.

P0

Deferred / POC

Kiosk + Self Update

Draft

QA-CONSOLE-001

Console

Kiosk user accesses only approved in-app functions.

P0

Deferred

In-App Console

Draft

QA-CONSOLE-002

Navigation

Record/Setting/Back behavior matches design.

P0

Deferred

In-App Console

Draft

QA-CONSOLE-003

Navigation

Child Back returns to Setting and never exits DCAM.

P0

Deferred

In-App Console

Draft

QA-CONSOLE-004

Settings

Idle setting change validates/persists/applies next recording.

P0

Deferred

Console + System Settings + Recording

Draft

QA-CONSOLE-005

Settings Guard

Recording-time setting change is deferred/rejected safely.

P0

Deferred

Console + State Machine

Draft

QA-CONSOLE-006

Device Settings

USB/Wi-Fi/GPS control requires approved authorization.

P0

Deferred

Console + Kiosk

Draft

QA-CONSOLE-007

File/Media

File Manager and Media Viewer are read-only.

P0

Deferred

Console + Security + Storage

Draft

QA-PROV-001

Provisioning

Device without restorable identity enters `PROVISIONING_REQUIRED`.

P0

Deferred

Web Portal + API Contract

Draft

QA-PROV-002

API Contract

Restore uses `serial_lookup/{serial_number}` and not Android system ID.

P0

Deferred

API Contract

Draft

QA-PROV-003

Web Portal

Factory Worker QR-only read-only serial Workspace flow works.

P0

Deferred

Web Portal Designs

Draft

QA-AUTH-001

User/Auth

Recording follows operator policy of active build; Build 0.1 may use approved no-login/basic placeholder.

P0

Required with Build 0.1 policy

User Requirements + Delivery Profile

Draft

QA-AUTH-002

User/Auth

Background/foreground does not unexpectedly logout active session.

P0

Deferred

Android Operation

Draft

QA-AUTH-003

User/Auth

Reboot expires prior full-auth session when full auth is active.

P0

Deferred

Android Operation

Draft

QA-REC-001

Recording

Start/stop normal recording and preserve active-build operator attribution policy.

P0

Required

Recording Design

Draft

QA-REC-002

Emergency Recording

Emergency override records safely without granting admin/maintenance capability.

P0

Deferred / Conditional

Recording Design

Draft

QA-STO-001

Storage

In-progress file is not exposed as final media.

P0

Required

Storage Design

Draft

QA-STO-002

Storage

Final media becomes `BDMA_READY` only after valid file and DB state.

P0

Required

Storage Design

Draft

QA-BDMA-001

BDMA

BDMA chỉ import finalized MP4 có valid MD5 và finalized image theo image contract.

P0

Required

Data Contract

Draft

QA-BDMA-002

BDMA Integrity

Missing MP4 MD5 blocks import, evidence và Build 0.1 release gate; source MP4 được giữ nguyên.

P0

Required

Data Contract + Decision Brief

Draft

QA-BDMA-003

BDMA Integrity

MP4 MD5 mismatch blocks import và cleanup; source evidence giữ nguyên.

P0

Required

Data Contract + BDMA Design

Draft

QA-BDMA-004

BDMA Image Contract

Final image without `.md5` is imported according to image contract and is not treated as missing-checksum failure.

P0

Required

Data Contract + BDMA Design

Draft

QA-BDMA-005

BDMA Scan/Cleanup

Scan ignores Temp and protected artifacts; cleanup never deletes CSON, DB, logs, identity or recovery artifacts.

P0

Required

Data Contract + BDMA Design

Draft

QA-BDMA-006

ADB Failure

ADB disconnect or permission failure returns controlled/retryable failure and does not modify/delete source artifacts.

P0

Required

BDMA Design

Draft

QA-DB-001

SQLite

Unsupported schema write-back is rejected.

P0

Deferred

SQLite Design

Draft

QA-DB-002

SQLite Minimal Artifact

Create/open minimal `dcam.db`; validate `schema_version` and Build 0.1 media/session/finalization/`BDMA_READY`/recovery semantics against the reviewed schema.

P0

Required; exact physical assertions Technical Review required

SQLite Design + Delivery Profile

Draft

QA-DB-003

SQLite Failure/Recovery

Missing, corrupt, unreadable or write-failed DB enters controlled recovery, preserves media/evidence and does not mark invalid media `BDMA_READY`.

P0

Required

SQLite Design + Storage Design

Draft

QA-CSON-001

CSON Minimal Artifact

Create/read minimal `dcam_config.cson`; validate approved required/allowed fields and reject forbidden fields.

P0

Required; exact minimum field set Technical Review required

Device Configuration Requirements + Data Contract

Draft

QA-CSON-002

CSON Failure/Recovery

Missing, invalid or unreadable CSON follows controlled recovery/fallback without secret leakage or source corruption.

P0

Required

Device Configuration Requirements + Data Contract

Draft

QA-RCFG-001

Remote Config

Invalid config rejected and last valid applied config remains active.

P0

Deferred

System Settings

Draft

QA-UPD-001

Self Update

Self Update is primary update path for active no-external-EMM profile.

P0

Deferred

Self Update Design

Draft

QA-UPD-002

Self Update

Update is deferred during recording/finalization.

P0

Deferred

Self Update Design

Draft

QA-FACTORY-001

Factory SOP

Factory-reset device reaches expected production decision.

P0

Not Applicable

Factory SOP

Draft

QA-FACTORY-002

Factory SOP

APK version/checksum/signing metadata verified before install.

P0

Not Applicable

Factory + Update + Security

Draft

QA-FACTORY-003

Factory SOP

Device Owner/Lock Task/restrictions verified before shipment.

P0

Not Applicable

Factory + Kiosk

Draft

QA-SEC-001

Security

Raw Android system identifiers are not logged or used as production lookup.

P0

Required for sanitization

Security Design

Draft

QA-SEC-002

Security

Maintenance secrets are not logged or hardcoded.

P0

Deferred / Conditional

Security Design

Draft

QA-SEC-003

Security

Factory Wi-Fi exception is not exposed through logs/API/QR/records/evidence.

P0

Conditional if present in build

Security + Factory

Draft

QA-CAP-001

Capability

Unsupported GPS feature is pruned and does not crash app.

P1

Conditional if GPS path present

Capability Design

Draft

### 7.1 Build 0.1 Artifact and BDMA Assertions

Test ID

Guardrail / Closure Condition

QA-DB-002

`schema_version` và semantic subset phải được Technical Review trước khi chốt table/column/enum assertions; Room/raw SQLite không làm thay đổi test intent.

QA-DB-003

Recovery phải preserve media/evidence; exact recovery representation giữ `TBD` đến Technical Review.

QA-CSON-001

Allowed/forbidden boundary lấy từ Data Contract; exact minimal required-field list phải được Technical Review.

QA-CSON-002

Fallback/recovery không được tự tạo identity/config value không có source of truth.

QA-BDMA-003…006

Result phải ghi scan root, source artifact, MD5 state, ADB failure category và cleanup decision; không đánh dấu `Passed` nếu thiếu evidence.

## 8. Performance Test Group

Test Group

Build 0.1

Evidence

Recording latency

Required

`[PERF] recording_start_latency_ms`, `recording_stop_latency_ms`.

Image latency

Required

Image command-to-final timestamp.

Critical finalization

Required

`critical_finalization_latency_ms`, `BDMA_READY` timestamp.

MainThread / ANR

Required

StrictMode and timing evidence.

State Coordinator / camera callback

Required

Queue/callback timing.

DB transaction / busy retry

Required

DB timing/retry logs.

Storage I/O / free-space

Required

Benchmark and near-full/full test.

Memory leak / stability

Required

Long-running report, memory growth, FD checks.

App-owned thread count

Recommended / POC

Thread ownership list and process measurement.

Battery / Thermal

Deferred

Device POC measurement only.

Performance targets are `Approved Pending Device POC`; target-device evidence may adjust values only through approved change control.

## 9. Logging & Diagnostics Test Group

Test Group

Build 0.1

Evidence

Local-first persistence

Required

Local file/queue evidence.

Rotation and bounded storage

Required

Size/rotation/retention result.

Sensitive-data sanitization

Required

Scan of every active logging channel.

BDMA logs artifact

Required

Read-only `logs.txt` access.

Provider outage

Conditional when provider integration exists

Offline/unavailable simulation.

Backend Relay / Loggly delivery

Deferred unless explicitly enabled

Relay + Loggly event.

Crashlytics fatal/non-fatal

Conditional when enabled

Safe report and classification evidence.

GMS-free/provider independence

Required for production profile

Local logs and BDMA artifact without GMS/provider; prohibited dependency/source tests pass.

Logging provider failure is a diagnostics degradation,
not a recording/storage/emergency failure.
## 10. Factory Acceptance Test Direction

Factory SOP acceptance is not applicable to Build 0.1 and becomes mandatory only for a shipment build.

Release-approved shipment APK
    ↓
Factory SOP execution
    ↓
Factory acceptance checklist
    ↓
QA review
    ↓
READY_TO_SHIP or QUARANTINED
Factory P0 tests do not block an internal non-shipment build.

## 11. Regression Test Set by Build Profile

Regression Group

Build 0.1

Build 0.2

Build 0.3

Pilot / Shipment

Working Recording Slice

Required

Required

Required

Required

Normal Recording / Image Capture

Required

Required

Required

Required

Storage / Finalization / Recovery

Required

Required

Required

Required

Minimal DB/CSON/logs

Required

Required

Required

Required

BDMA sample import

Required

Required E2E

Required

Required

MVP Performance / Concurrency

Required

Required + active platform metrics

Required

Full applicable budget

Sensitive Logging sanitization

Required

Required

Required

Required

Local-first logging / bounded storage

Required

Required

Required

Required

Loggly / Crashlytics

Conditional when enabled

Required when enabled

Required

Required or approved fallback

Full User/Auth

Deferred

Required

Required

Required

Web Portal / Cloud Identity

Deferred

Required

Required

Required

Remote Config

Deferred

Required foundation

Required

Required if active

Self Update

Deferred

Required foundation

Required

Required or exception

Device Owner / Kiosk / Maintenance

Deferred / POC exception

Conditional / POC Blocked

Required if active

Required for production kiosk profile

Factory SOP / READY_TO_SHIP

Not Applicable

Conditional

Conditional

Required for shipment

Live Streaming / PTT / Full GPS Route

Not Applicable

Deferred

Required according to scope

Required only if included in pilot scope

AI

Not Applicable

Deferred

Conditional/Future

Only if pilot scope activates it

## 12. Exit Criteria

### 12.1 Universal Rule

All P0 tests with Applicability = Required must pass.
All P0 tests with Applicability = Conditional must pass when the activation condition is true.
Deferred and Not Applicable tests do not block the build.
POC Blocked tests block only the build/profile that requires the unresolved capability.

Criteria

Requirement

Applicable P0 tests passed

Required

No open applicable P0 bug

Required

Applicable P1 bugs reviewed and accepted

Required

Required evidence linked in Traceability Matrix or release report

Required

Release & Build Applicability Matrix matches tested scope

Required

Release checklist completed for the active Build Profile

Required

### 12.2 Build 0.1 Exit Criteria

Criteria

Build 0.1

QA-WRS-001 passes

Required

Normal recording and image capture pass

Required

Storage/finalization/recovery subset passes

Required

Minimal DB/CSON/logs artifacts produced

Required

BDMA detects/imports sample media

Required

Applicable MVP Performance Budget passes or has approved Device POC adjustment

Required

MainThread/concurrency critical path passes

Required

Sensitive values absent from active logs/artifacts

Required

Local-first logging and bounded storage pass

Required

QA-MEDIA-IMP-001 execution evidence

Not Required khi Important Media activation condition là false; không chặn Build 0.1 release gate.

Web Portal / full auth / kiosk / Remote Config / Self Update / Factory tests

Deferred or Not Applicable; must not block Build 0.1

### 12.3 Pilot / Shipment Additional Exit Criteria

Criteria

Condition

Kiosk policy and no unrestricted escape

Required when production kiosk profile active

Web Portal / provisioning / identity restore

Required when activated

Self Update primary path

Required when activated

Factory SOP acceptance

Required for shipment

Safe production record

Required for shipment

Security Review and active encryption profile tests

Required when encryption active

Device POC target model/firmware evidence

Required for production hardware baseline

## 13. Traceability and Evidence Rule

QA coverage is complete only when the following chain is visible:

Requirement ID
    → Build Applicability
    → Design / Contract section
    → QA Test ID
    → Jira item
    → Test evidence
The authoritative mapping is **DCAM Requirement–Design–Test Traceability Matrix**.

A test row marked `Draft` may define intended coverage, but it does not count as executed evidence.

## 14. Practical Conclusion

Priority and Build Applicability are separate.
Only applicable P0 tests block a build.
Build 0.1 is gated by Working Recording Slice, recording/image capture, storage/finalization, minimal artifacts, BDMA sample import, performance/concurrency and sanitization.
Web Portal, full auth, full kiosk, Remote Config, Self Update and Factory acceptance do not block Build 0.1 unless explicitly activated.
Traceability Matrix owns Requirement → Build → Design → QA → Jira → Evidence coverage.
## 15. Build 0.1 Decision Validation

Test ID

Area

Scenario

Priority

Build 0.1

Source

Status

QA-STO-003

Storage Policy

Internal-only; External/Auto disabled; failed pre-check không start recording.

P0

Required

DEC-02 + Storage Requirements/Design

Draft

QA-STO-004

Storage Failure

Runtime storage failure safe-stop và finalize MP4 nếu còn khả năng; không fallback.

P0

Required

DEC-02 + Storage Design

Draft

QA-BDMA-007

Checksum Failure

MD5 generation failure giữ MP4, ghi log, persist Checksum Pending/Failed và không BDMA_READY/import.

P0

Required

DEC-03 + Data Contract

Draft

QA-WRS-OP-001

Operator

Không login UI; B01OPR / Build 0.1 Operator immutable và nhất quán trong SQLite/CSON/log; không auth claim.

P0

Required

DEC-04 + User/Config/SQLite/Logging

Draft

QA-WRS-STATUS-001

Device Status

Battery level, Internal free storage và GPS state được report đúng; không coordinates/route/tracking.

P0

Required

DEC-05 + Device POC

Draft

QA-WRS-DEV-001

Device Coverage

Evidence chứa physical device ID, NCC-036V, Android 12/API 31 và firmware 877AOOAKN1_RK2_V009.

P0

Required

DEC-01/06 + Device POC

Draft

### 15.1 Release Gate Override

Missing, mismatch hoặc failed MP4 MD5 là hard failure cho Build 0.1.

BDMA_READY timestamp phải sau checksum success timestamp.

WRS evidence phải đến từ ít nhất một identifiable physical reference device.

Filename evidence phải chứa input serial_number/operator_id, generated filename, parsed tokens và media_contract_version; POC xác nhận actual serial_number.

GPS Unavailable/Unsupported không làm fail nếu reporting chính xác.

Build 0.1 pass không phải Device POC qualification cho model khác hoặc Production/fleet approval.

Jira mapping đã được ghi trong Traceability Matrix. PR/build/test links và execution result vẫn phải được bổ sung trước khi đóng implementation Definition of Done; [DucVietTech/dcam](https://github.com/DucVietTech/dcam) chỉ là PM-approved repository identity, không phải execution evidence.