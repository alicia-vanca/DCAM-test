# 07 - Logging & Diagnostics Requirements

**Page ID**: 47776094  
**Version**: 12  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47776094

---


# 07 - Logging & Diagnostics Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.9

Status

Approved

Approval Scope

Stable Build 0.1 logging requirements cho storage, checksum, operator và Device Status

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / QA Lead / Cloud Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, AI/ML Engineer, QA, Support, Cloud/WebServer Team

Last Updated

2026-08-25

Related Jira

Not linked

Dependencies / Blockers

Jira/PR/build/test evidence; provider-specific activation decision; logs.txt implementation exception closure.

Related Documents

07 - Logging, Diagnostics, Performance & Security, DCAM Logging & Diagnostics Design, DCAM-BDMA Data Contract, DCAM Security & Encryption Design, DCAM Performance Budget & Resource Constraints, DCAM QA Test Strategy & Test Matrix, DCAM Device Capability & Feature Eligibility Design, DCAM State Machine Design, DCAM Sensor & Location Monitoring Design, DCAM Realtime AI Detection Design, DCAM Recording & Capture Design, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

GMS-free logging rule: no FCM, Analytics, Play Integrity, Google account or other GMS dependency may be introduced for logging/diagnostics. Crashlytics is optional bounded telemetry; local-first logs and BDMA diagnostics are required independently. See ADR - DCAM GMS-free Android Runtime Baseline.

Trang này là source of truth cho các yêu cầu chức năng về logging và diagnostics của DCAM.

Logs phải hỗ trợ debug, support, audit và troubleshooting cho recording, storage, emergency, sensor monitoring, location tracking, realtime AI, update, remote config, provisioning, kiosk/policy, BDMA và device capability / feature eligibility.

Provider ownership và implementation detail không được định nghĩa lại tại đây:

07 - Logging, Diagnostics, Performance & Security
    → owns two-channel architecture and provider ownership

DCAM Logging & Diagnostics Design
    → owns logging facade, event schema, local-first files/queue,
      Backend Relay → Loggly, Crashlytics classification and sanitization

DCAM-BDMA Data Contract
    → owns the logs artifact that BDMA may read

DCAM Security & Encryption Design
    → owns forbidden sensitive fields and credential protection
## 2. Logging Channel Requirements

DCAM phải hỗ trợ hai logical logging channels:

Logging Type

Provider Direction

Requirement

Operational Logging

Loggly

Là kênh observability vận hành chính cho lifecycle, business/runtime events, expected failures, recovery, diagnostics và performance events.

Crash & Stability Monitoring

Firebase Crashlytics

Theo dõi fatal crash, ANR, unexpected non-fatal exception và bounded error context phục vụ đánh giá application stability.

Rules:

Rule

Requirement

LOG-CH-001

Crashlytics không được thay thế Operational Logging.

LOG-CH-002

Operational events không được gửi hàng loạt vào Crashlytics như một operational event stream.

LOG-CH-003

Crashlytics custom logs/keys chỉ được dùng làm bounded context liên quan trực tiếp tới crash hoặc unexpected exception.

LOG-CH-004

Provider-specific integration phải nằm sau approved logging abstraction; business/runtime modules không gọi provider trực tiếp.

LOG-CH-005

Provider unavailable không được block recording, emergency, media finalization hoặc core offline operation.

## 3. Local-first Requirements

Operational Logging phải là local-first.

Requirement

Description

Status

Local Persistence

Operational event phải được ghi local trước hoặc được đưa vào durable local queue trước khi cloud delivery được coi là hoàn tất.

Approved

Asynchronous Delivery

Upload/relay không được chạy trên MainThread hoặc block critical recording/storage path.

Approved

Offline Queue

DiagnosticsOutbox phải persist event/crash summary đã sanitize trước delivery; khi network, BFF Diagnostics Ingestion hoặc provider unavailable, queue/retry theo bounded policy.

Approved

Bounded Storage

Local log files và DiagnosticsOutbox phải có rotation/retention/size limit, priority/drop policy và drop summary để không làm đầy storage hoặc ảnh hưởng recording/evidence.

Approved

Provider Recovery

DCAM batch tới BFF với stable event_id; chỉ ACK BFF mới xác nhận delivery. Retry dùng backoff+jitter và retry hint/rate limit để không tạo duplicate hoặc reconnect storm. Provider downstream recovery không đổi Android delivery semantics.

Approved Direction

BDMA Diagnostics Artifact

DCAM phải expose artifact tương thích `logs.txt` theo DCAM-BDMA Data Contract; exact active/rotation implementation thuộc Logging Design và Data Contract.

Approved

GMS-free / Provider Independence

Core operational diagnostics must operate locally without Google Play services, Crashlytics, Loggly or network; this is mandatory for production profile.

Approved

## 4. Logging Scope

Area

Requirement Direction

Status

Application Lifecycle Logs

Log app boot, startup, shutdown, process/service restart và recovery.

Approved

Identity / Provisioning Logs

Log identity restore, QR/business provisioning result và safe reason code; không log secret.

Approved

Capability Logs

Log capability detection, feature eligibility và runtime pruning.

Approved Direction

Recording Logs

Log start, stop, post-record, finalize, error và recovery.

Approved

Storage Logs

Log storage full, external removed, fallback, write failure và finalization state.

Approved

Emergency Logs

Log candidate, detected event, recording started, clip saved và SOS state.

Approved Direction

Sensor / Location Logs

Log monitoring/tracking lifecycle, unavailable capability và degraded state.

Approved Direction

Realtime AI Logs

Log model state, runtime state, event candidate và degraded/pruned state.

Approved Direction

Remote Config Logs

Log fetch, validate, apply, defer và reject result without secrets.

Approved

Update Logs

Log check, download, verify, install, failure, defer và precondition block.

Approved

Kiosk / Policy Logs

Log Device Owner/Lock Task/User Restrictions/Maintenance lifecycle bằng safe state và reason code.

Approved

Performance Logs

Log các measurable events được Performance Budget yêu cầu.

Approved

BDMA Logs

Hỗ trợ diagnose import readiness, DB/write-back và contract compatibility issues.

Approved

## 5. Required Operational Events

Event Group

Required Events

Application/System

App boot/startup, service killed/restarted, permission denied, safe-mode/recovery entry.

Identity/Provisioning

Identity restore started/completed/failed, provisioning QR state, provisioning submitted/completed/failed, serial lookup result.

Recording

Start, stop, post-record, finalize, error, recovery.

Emergency

Event candidate, event created, emergency recording started, clip saved, marker created, SOS pending/sent/failed.

Storage

Storage selected, fallback, low/full, write failure, finalization result, `BDMA_READY`.

Sensor

Monitoring started/stopped/error/degraded, sensor unavailable, motion event candidate.

Location

Tracking started/stopped, permission missing, sample buffered, sync result.

Realtime AI

AI started/stopped, model loaded/failed, event candidate, event created, degraded/pruned.

Config/Update

Config fetched/applied/deferred/rejected, update checked/blocked/verified/failed.

Kiosk/Policy

Policy verification, Lock Task enter/restore/failure, restriction apply/failure, Maintenance Mode enter/exit/failure.

Performance

Metrics required by DCAM Performance Budget & Resource Constraints.

BDMA

Import readiness issue, contract mismatch, DB/write-back issue if applicable.

## 6. Capability Logging Requirements

Event

Description

Status

Capability Detection Started / Completed

Device capability detection lifecycle.

Approved Direction

Hardware Capability Missing

Missing optional hoặc core hardware.

Approved Direction

Performance Class Evaluated

Memory/storage/AI performance class result.

Approved Direction

Feature Eligibility Evaluated

Supported/degraded/unsupported state per feature.

Approved Direction

Runtime Flow Pruned

Unsupported feature runtime không được initialized.

Approved Direction

Degraded Mode Applied

Feature chạy reduced mode.

Approved Direction

Capability Detection Error

Detection failure và fallback profile.

Approved Direction

Example direction:

[CAPABILITY] Detection started
[CAPABILITY] GPS Tracking disabled: provider unavailable
[CAPABILITY] Realtime AI disabled: performance below minimum
[CAPABILITY] Fall Detection degraded: gravity sensor unavailable
[CAPABILITY] Feature runtime pruned: realtime_ai
[CAPABILITY] Detection completed
## 7. Event Quality Requirements

Operational events phải đủ structured context để correlate và troubleshoot nhưng không chứa sensitive payload.

Requirement

Direction

Timestamp

Mỗi event có timestamp thống nhất.

Category

Có category/domain ổn định.

Level

Có approved severity/level.

Event Name

Dùng stable event name thay vì free-text-only log.

Result / Reason Code

Failure/recovery dùng stable result và safe reason code.

Correlation

Có request/session/recording/correlation identifier khi applicable.

Application Context

Có safe app/device/contract version context khi cần.

Bounded Payload

Event payload phải có size limit và không chứa raw large payload.

Exact field names, JSON schema và level mapping thuộc **DCAM Logging & Diagnostics Design**.

## 8. Crash & Stability Requirements

Requirement

Description

Status

Fatal Crash Reporting

Unhandled fatal crash phải được Crashlytics capture khi optional provider available; sanitized local crash summary phải vào DiagnosticsOutbox khi practical. Crashlytics receipt không phải authoritative delivery ACK.

Approved

ANR Monitoring

ANR phải được theo dõi khi platform/provider hỗ trợ.

Approved

Unexpected Non-fatal

Unexpected handled exception có ảnh hưởng stability có thể được record như non-fatal theo classification policy.

Approved

Expected Failure Exclusion

Expected timeout, validation rejection, retryable network error hoặc supported degraded state không được tạo Crashlytics noise theo mặc định.

Approved

Bounded Context

Custom keys/logs chỉ chứa safe state, version, category và reason context cần thiết.

Approved

Local Fallback

Prolonged offline, BFF/provider unavailable hoặc Crashlytics delayed không làm mất Operational Logging local, bounded crash summary hoặc làm core flow fail.

Approved

Exact classification thuộc **DCAM Logging & Diagnostics Design**.

## 9. Sensitive Logging Rules

DCAM không được log hoặc gửi tới Loggly/Crashlytics:

raw media content
pre-record cache content
continuous raw sensor stream
raw location history unless explicitly approved
raw AI frames
face crops
face embeddings / identity data unless explicitly approved
encryption keys
access tokens
Firebase identity token
credentials / passwords / secrets
maintenance password value or protected credential material
factory Wi-Fi password
Google account password/token
APK signing private key
provisioning secret or long-lived QR secret
raw Android system identifier
ANDROID_ID
android_id_hash
full sensitive config payload
sensitive user data beyond necessary diagnostic metadata
Allowed diagnostic metadata examples:

Metadata

Allowed Direction

Event type

Allowed.

State transition

Allowed.

Timestamp

Allowed.

Error code/category

Allowed.

Capability reason code

Allowed.

Runtime state

Allowed.

Safe request/session/correlation ID

Allowed.

App/contract version

Allowed nếu cần support.

Detector/model version

Allowed nếu safe.

Confidence score

Allowed nếu cần diagnostics và policy cho phép.

## 10. Diagnostics Requirements

Requirement

Description

Status

Capability Diagnostics

Giải thích vì sao feature supported, degraded, unsupported hoặc pruned.

Approved Direction

Recording Diagnostics

Hỗ trợ investigation recording/finalization issues.

Approved

Emergency Diagnostics

Hỗ trợ event-source-to-evidence trace.

Approved Direction

Monitoring Diagnostics

Hỗ trợ sensor/GPS issue investigation.

Approved Direction

AI Diagnostics

Hỗ trợ model/runtime/performance issue investigation.

Approved Direction

Provisioning Diagnostics

Hỗ trợ identity/QR/create/restore investigation bằng safe metadata.

Approved

Kiosk Diagnostics

Hỗ trợ policy apply/restore/failure investigation without secrets.

Approved

BDMA Diagnostics

Hỗ trợ import readiness và source data issues.

Approved

Security Diagnostics

Ghi validation/security failure bằng safe reason code.

Approved

Provider Diagnostics

Hỗ trợ phát hiện local queue pressure, relay failure, upload retry và provider unavailable state.

Approved

## 11. Acceptance Direction

QA phải verify tối thiểu:

Operational event is available locally before/independent of cloud delivery.
DiagnosticsOutbox is bounded, retryable and supports late delivery after prolonged offline; batch acknowledgement and idempotency are BFF-owned.
Loggly/Backend Relay outage does not block core operation.
Crashlytics receives fatal and approved unexpected non-fatal events.
Expected operational failures do not create Crashlytics spam.
Sensitive values are absent from local logs, relay payload, Loggly and Crashlytics.
Non-GMS/provider-unavailable device still preserves local diagnostics.
BDMA can read the approved logs artifact without modifying it.
Detailed test IDs thuộc **DCAM QA Test Strategy & Test Matrix**.

## 12. Build 0.1 Stable Requirement IDs

Các ID dưới đây gắn vào behavior hiện có; changeset này không thay đổi logging behavior.

Requirement ID

Existing Requirement

Build 0.1

LOG-LOCAL-001

Local Persistence

Required

LOG-LOCAL-002

Asynchronous Delivery

Required

LOG-LOCAL-003

Offline Queue

Required cho local/provider-degradation flow được implement

LOG-LOCAL-004

Bounded Storage

Required

LOG-LOCAL-005

BFF Delivery Recovery

Conditional khi BFF Diagnostics Ingestion được enable; stable event_id, ACK, retry/backoff+jitter and rate-limit handling required.

LOG-LOCAL-006

BDMA Diagnostics Artifact (`logs.txt`)

Required

LOG-LOCAL-007

GMS-free / Provider Independence

Required for production profile

LOG-EVT-APP-001

Application/System operational events

Required subset

LOG-EVT-REC-001

Recording operational events

Required

LOG-EVT-STO-001

Storage operational events

Required

LOG-EVT-PERF-001

Performance events thuộc active Performance Budget

Required

LOG-EVT-BDMA-001

BDMA readiness/contract issues

Required

LOG-QUAL-001

Timestamp

Required

LOG-QUAL-002

Category

Required

LOG-QUAL-003

Level

Required

LOG-QUAL-004

Event Name

Required

LOG-QUAL-005

Result / Reason Code

Required

LOG-QUAL-006

Correlation

Required khi applicable

LOG-QUAL-007

Application Context

Required khi support cần

LOG-QUAL-008

Bounded Payload

Required

LOG-SEC-001

Sensitive Logging Rules

Required

LOG-DIAG-REC-001

Recording/finalization diagnostics

Required

LOG-DIAG-STO-001

Storage/finalization/recovery diagnostics

Required

LOG-DIAG-BDMA-001

BDMA import-readiness/contract diagnostics

Required

## 13. Practical Conclusion

Operational Logging is the primary operational observability channel.
Loggly is the centralized Operational Logging provider.
Firebase Crashlytics is the optional Crash & Stability Monitoring provider; it must not introduce Google Play services or a dependency on GMS-only Firebase features. BFF Diagnostics Ingestion is the authoritative remote delivery boundary for local operational events and crash summaries; the downstream operational provider remains replaceable.
Crashlytics does not replace Operational Logging.
Operational Logging is local-first, asynchronous and bounded.
Cloud/provider failure must not block core DCAM operation.
logs.txt remains the BDMA-facing diagnostics artifact defined by DCAM-BDMA Data Contract.
Exact schema, queue, rotation, relay and provider integration belong to DCAM Logging & Diagnostics Design.
Logging phải làm capability-aware runtime behavior có thể giải thích được:

If a feature does not run,
logs must show whether it was disabled by setting,
unsupported by hardware,
unsupported by performance,
missing permission,
degraded,
or pruned from runtime.
## 14. Build 0.1 Decision Logging Requirements

Requirement ID

Required Event / Context

Status

LOG-B01-STO-001

Internal storage pre-check failure, runtime storage failure, safe-stop và finalization result.

Approved

LOG-B01-MD5-001

MD5 start/success/pending/failed/missing/mismatch và BDMA readiness result.

Approved

LOG-B01-OP-001

B01OPR / Build 0.1 Operator tại nơi log schema yêu cầu; không được diễn giải là authenticated identity.

Approved

LOG-B01-STATUS-001

Battery level, Internal free storage và GPS Available/Unavailable/Unsupported khi WRS thu evidence.

Approved

Log phải sanitized và không được thêm credential, token, coordinate hoặc route data từ các decisions này.