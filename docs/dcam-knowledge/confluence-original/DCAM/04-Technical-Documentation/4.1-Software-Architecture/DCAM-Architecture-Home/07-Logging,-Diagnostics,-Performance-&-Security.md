# 07 - Logging, Diagnostics, Performance & Security

**Page ID**: 47185971  
**Version**: 11  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47185971

---


# 07 - Logging, Diagnostics, Performance & Security

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Operational Quality Architecture

Version

Approved 1.8

Status

Approved

Approval Scope

Operational quality architecture boundaries cho logging, diagnostics, performance và security; detailed rules thuộc Requirements/Design/QA sources.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / QA Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, Backend Developers, QA, BDMA Team, Support

Last Updated

2026-07-14

Related Jira

None

Related Documents

DCAM Architecture Home, 02 - Architecture Principles, 05 - Data, Storage & BDMA Architecture, 06 - Cloud Services, Update & Configuration Architecture, 08 - DCAM-BDMA Integration Boundary, DCAM Documentation Governance, 05 - User & Device Operation Requirements, 07 - Logging & Diagnostics Requirements, DCAM Logging & Diagnostics Design, DCAM Performance Budget & Resource Constraints, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, DCAM Self Update Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM Web Portal & Device API Contract, DCAM-BDMA Data Contract, DCAM QA Test Strategy & Test Matrix, DCAM Device POC & Hardware Validation Report

## 1. Purpose

Trang này mô tả baseline kiến trúc cho logging, diagnostics, performance, reliability và security của DCAM.

Nội dung ở trang này là high-level. Các implementation details về event schema, logging abstraction, local queue, rotation, upload, Loggly integration và Crashlytics integration thuộc **DCAM Logging & Diagnostics Design**.

Runtime/recovery/security/auth details phải reference tài liệu authoritative, không copy full rule tables.

## 2. Authoritative References

Topic

Source of Truth

Architecture Usage

Logging requirements and sensitive logging rules

07 - Logging & Diagnostics Requirements

Architecture reference logging policy và category direction.

Logging implementation, provider routing, event schema, local-first queue and diagnostics export

DCAM Logging & Diagnostics Design

Architecture chốt provider ownership; Technical Design owns implementation detail.

Measurable performance targets and resource constraints

DCAM Performance Budget & Resource Constraints

Architecture định nghĩa principle/metric categories; Performance Budget owns numeric targets và release thresholds.

User/operator login policy and emergency override

05 - User & Device Operation Requirements

Architecture reference auth/session audit và reliability constraints.

Android startup, login/session lifecycle, foreground service, safe mode and runtime recovery

DCAM Android Operation Design

Architecture reference runtime recovery behavior.

Recording/finalization/operator attribution recovery

DCAM Recording & Capture Design

Architecture reference recording evidence preservation behavior.

Storage thresholds, temp/final, BDMA readiness and storage recovery

DCAM Storage Design

Architecture reference storage reliability behavior.

DB transaction/user sync/write-back/recovery

DCAM SQLite Database Design

Architecture reference DB consistency và recovery boundary.

BDMA/DCAM data contract and user sync

DCAM-BDMA Data Contract

Architecture reference ADB sync/write-back boundary.

Credential/auth/encryption implementation

DCAM Security & Encryption Design

Architecture reference Security Design cho implementation details.

APK/update validation

DCAM Self Update Design

Architecture reference update validation direction.

Device Owner, Lock Task, User Restrictions and Maintenance Mode behavior

DCAM Android Device Owner & Kiosk Policy Design

Architecture reference policy diagnostics và reliability boundary.

Backend relay/API schema

DCAM Web Portal & Device API Contract

Architecture reference API Contract nếu operational log relay endpoint được triển khai.

## 3. Logging Architecture Decision

DCAM logging được chia thành hai logical channels với ownership riêng biệt.

Logging Type

Primary Provider

Architecture Responsibility

Operational Logging

Loggly

Kênh log vận hành chính cho lifecycle, state, recording, storage, identity, provisioning, policy, update, performance, diagnostics, expected failures và recovery.

Crash & Stability Monitoring

Firebase Crashlytics

Theo dõi fatal crash, ANR, unexpected non-fatal exception, release stability và bounded error context.

Approved baseline:

Operational Logging = primary operational observability channel.
Operational Logging provider = Loggly.

Crash & Stability Monitoring = application error and stability channel.
Crash & Stability provider = Firebase Crashlytics.

Crashlytics does not replace Operational Logging.
Crashlytics custom logs/keys are bounded error context only.
Operational Logging is local-first, asynchronous and provider-independent.
Production routing direction:

DCAM Android
    → local operational log / bounded queue
    → authenticated DCAM Backend Relay
    → Loggly

DCAM unexpected crash / ANR / unexpected non-fatal
    → Firebase Crashlytics
Production Android app không được hardcode Loggly customer token hoặc gửi trực tiếp tới Loggly, trừ khi một Security Review/ADR tương lai approve hướng khác.

## 4. Operational Logging Categories

Operational Logging phải đủ để debug trên thiết bị thực tế và hỗ trợ BDMA/support team.

Log Type

Purpose

Storage / Provider Direction

Application Log

Ghi nhận app lifecycle, startup, shutdown và state chung.

Local-first → Backend Relay → Loggly.

Identity / Provisioning Log

Ghi nhận identity restore, provisioning-required, serial/cloud identity result và safe reason code.

Local-first → Backend Relay → Loggly.

Auth / Session Log

Ghi nhận login success/failure, logout, session restore, reboot expiration và emergency override.

Local-first → Backend Relay → Loggly; chỉ dùng safe reason codes.

Recording Log

Ghi nhận start/stop/result/error/operator resolution/finalization/recovery.

Local-first → Backend Relay → Loggly.

Capture / Camera Log

Ghi nhận capture trigger/result/error và camera state/error/timeout.

Local-first → Backend Relay → Loggly.

Storage Log

Ghi nhận free space, storage threshold, write/read error, temp/final recovery và fallback.

Local-first → Backend Relay → Loggly.

Policy / Maintenance Log

Ghi nhận Device Owner, Lock Task, User Restrictions, Maintenance Mode và policy recovery.

Local-first → Backend Relay → Loggly.

Config / Update Log

Ghi nhận fetch/validate/apply/reject/defer và update check/download/verify/install/failure.

Local-first → Backend Relay → Loggly.

DB / User Sync / BDMA Log

Ghi nhận DB open/migration/transaction/write-back, user sync và BDMA readiness.

Local-first → Backend Relay → Loggly.

Capability / Sensor / Location / AI Log

Ghi capability detection, eligibility, degraded/pruned runtime và safe monitoring diagnostics.

Local-first → Backend Relay → Loggly.

Performance Log

Ghi latency, memory, storage, queue, thread và stability metrics theo Performance Budget.

Local-first → Backend Relay → Loggly; phải sample/rate-limit.

Network / Provider Log

Ghi connectivity, request result, retry/backoff và provider health bằng safe reason code.

Local-first; centralized upload khi provider khả dụng.

Security Log

Ghi validation failure, forbidden action và safe audit reason.

Local-first → Backend Relay → Loggly; không chứa secret.

Crash Marker

Best-effort local marker/context khi crash hoặc recovery.

Local fallback; Crashlytics owns crash/stability report.

Detailed category names, event schema và level rules thuộc **DCAM Logging & Diagnostics Design**.

## 5. Logging Rules

Rule

Description

No Sensitive Data

Không log credential, token, secret, raw private metadata hoặc sensitive media content.

Forbidden Identifiers

Không log `ANDROID_ID`, `android_id_hash` hoặc raw Android system identifier.

Include Timestamp

Log phải có timestamp.

Include Context

Log quan trọng nên có safe device/session/operation correlation context nếu phù hợp.

Structured Event

Operational log nên dùng category, event name, level, reason code và structured safe fields.

Auth Reason Codes

Auth/security logs dùng reason code an toàn, không log raw credential.

Emergency Override Audit

Log rõ khi emergency override được dùng, nhưng không gán vào real Admin user.

User Sync Audit

Sync conflict/reject/apply result phải có log để phục vụ Support/BDMA.

Local First

Operational log phải có local fallback cho offline/non-GMS/provider-unavailable devices.

Asynchronous

Logging/upload không được block MainThread, recording, capture, finalization hoặc DB transaction.

Provider Isolation

Loggly/Backend Relay/Crashlytics failure không được làm operation chính fail.

Log Rotation

Log retention/rotation là bắt buộc; exact values thuộc DCAM Logging & Diagnostics Design / Device POC.

Crashlytics Boundary

Không gửi routine operational event hoặc expected failure vào Crashlytics như một thay thế cho Loggly.

Sanitization

Sanitize trước local persistence, trước upload và trước Crashlytics custom key/log/non-fatal report.

Forbidden content summary:

password / maintenance password / credential input
access token / session secret / cloud token
Google account password/token
APK signing private key
provisioning secret / enrollment secret
ANDROID_ID / android_id_hash / raw Android system identifier
raw media / raw sensor stream / raw AI frame / biometric material
full sensitive config payload
stack trace fragments containing credential input
See **07 - Logging & Diagnostics Requirements** and **DCAM Security & Encryption Design** for the full rule.

## 6. Crash & Stability Monitoring Direction

Firebase Crashlytics owns:

Unhandled fatal crash
ANR
Unexpected non-fatal exception
Unexpected invariant violation
Repeated unexpected SDK/framework exception requiring stability investigation
Bounded breadcrumbs/custom keys leading to an error
Release/app-version stability visibility
Firebase Crashlytics does not own:

Routine app lifecycle
Every recording start/stop
Expected validation failure
Expected network timeout/retry
Normal storage threshold warning
Complete performance metric stream
Full operational log upload
Factory production record
Non-GMS/target-device compatibility phải được xác nhận qua **DCAM Device POC & Hardware Validation Report**. Local operational diagnostics vẫn là fallback bắt buộc nếu Crashlytics không khả dụng.

Detailed classification và SDK abstraction thuộc **DCAM Logging & Diagnostics Design**.

## 7. Diagnostics Direction

Diagnostics cần hỗ trợ:

Debug trong development.

Điều tra issue trong pilot.

Customer support.

Troubleshooting cho BDMA ingest.

Troubleshooting cho user/operator sync.

Điều tra compatibility của hardware/auth-method.

Điều tra runtime recovery.

Điều tra operational log upload/provider health.

Điều tra crash/stability theo app version và target device.

Recommended diagnostics package:

Item

Status

App version

Required

Device model

Required

Android version

Required

GMS availability

Required

Operational Logging provider mode

Required

Crashlytics availability/mode

Recommended

Operational log queue/upload status

Recommended

Storage status

Required

Battery status

Required

GPS availability

Recommended

Last recording result

Recommended

Last operator resolution state

Recommended

Last emergency override usage summary

Recommended

Last user sync result

Recommended

Last capture result

Recommended

Last recovery result

Recommended

Last DB migration/write-back result

Recommended

Last policy/config/update result

Recommended

Performance summary

Recommended

Crash summary marker

Recommended

Final export package format vẫn TBD trong **DCAM Logging & Diagnostics Design**.

## 8. Performance Principles

Principle

Description

Do Not Block UI Thread

File IO, camera operation, DB transaction, auth validation, log serialization và network không được block UI thread.

Recording Has Priority

Logging, cloud, update, AI, user sync và background tasks không được làm giảm recording stability.

Async IO

File write/read, checksum, DB, logging upload, user sync và recovery scan nên chạy trong controlled background executors.

Controlled Logging

Logging phải bounded bởi retention/rotation/sampling/rate-limit policy.

Measure on Real Device

Performance phải được test trên BodyCamera hardware thật, không chỉ emulator/phone.

Offline Safe

App vẫn usable khi network/cloud/provider operations timeout hoặc fail.

Offline Auth Ready

Login phải hoạt động without Internet bằng local DB-backed data.

Capability-aware Runtime

Optional workloads và auth methods phải được pruned/degraded nếu device capability không đủ.

## 9. Performance Metrics Direction

Metric categories được approve ở architecture level. Numeric target, measurement point và release response thuộc **DCAM Performance Budget & Resource Constraints**.

Metric

Purpose

Target Ownership

App startup time

Đánh giá UX và readiness.

Performance Budget

Login latency

Đánh giá operator readiness.

Performance Budget / future measured target nếu chưa chốt.

Session restore latency

Đánh giá background/process recovery UX.

Performance Budget

User sync duration

Đánh giá BDMA sync supportability.

Performance Budget / ADB sync POC

Recording start/stop latency

Đánh giá field operation readiness.

Performance Budget

Capture latency

Đánh giá image capture responsiveness.

Performance Budget

Storage write latency

Đánh giá media reliability.

Performance Budget

Finalization latency

Đánh giá BDMA readiness và evidence flow.

Performance Budget

DB transaction latency

Đánh giá runtime reliability.

Performance Budget

Memory usage/leak

Đánh giá stability.

Performance Budget

Storage capacity/free-space

Đánh giá recording safety.

Performance Budget

MainThread/ANR/concurrency

Đánh giá responsiveness.

Performance Budget

Long-running stability

Đánh giá kiosk/recording endurance.

Performance Budget

Battery / thermal impact

Đánh giá operational duration và hardware behavior.

Device POC / deferred target

Crash rate / ANR count

Đánh giá release stability.

Crashlytics + QA/Performance Budget direction

Operational `[PERF]` event implementation thuộc **DCAM Logging & Diagnostics Design**.

## 10. Reliability Direction

Scenario

Expected Direction

Source of Truth

Storage nearly full

Warn/block/stop/finalize theo threshold policy.

DCAM Storage Design

GPS unavailable

Tiếp tục recording/capture; mark GPS/location unavailable.

Sensor & Location Monitoring Design

Network unavailable

Tiếp tục local operation; defer cloud/update/log upload; user auth vẫn offline.

Cloud Services Architecture + Security Design + Logging Design

Loggly/Backend Relay unavailable

Tiếp tục local-first logging; retry bounded khi safe.

DCAM Logging & Diagnostics Design

Crashlytics unavailable

Tiếp tục app operation; local operational diagnostics vẫn hoạt động.

DCAM Logging & Diagnostics Design

App backgrounded

Tiếp tục approved tasks và không logout operator.

DCAM Android Operation Design

Device reboot

Expire previous operator session và yêu cầu login.

Android Operation + SQLite Design

User sync conflict

Log conflict và apply agreed policy hoặc manual BDMA handling.

Data Contract + SQLite Design

User disabled while recording

Không interrupt current evidence; block new recording sau safe window.

SQLite + Recording Design

Emergency recording without login

Dùng `EMERGENCY_OVERRIDE_ADMIN` và preserve audit trail.

Recording + Security Design

Device power issue

Degrade optional modules và preserve recording/evidence.

Android Operation + Device Capability Design

App crash during recording

Preserve temp/final candidate và recover/mark failed an toàn.

Recording & Capture + Storage + DB Design

DB locked/corrupted

Retry/defer hoặc enter safe mode theo DB recovery behavior.

DCAM SQLite Database Design

Local log queue/file corrupted

Recover/drop bounded queue an toàn; không crash primary operation.

DCAM Logging & Diagnostics Design

## 11. Security Baseline

Area

Direction

Status

Secret Management

Không hardcode secrets, bao gồm Loggly token và provider credentials.

Approved

Credential Handling

Auth data phải được protect theo Security Design.

Approved Direction

Token Handling

Không log token/password/session secret.

Approved

Identifier Handling

Không log `ANDROID_ID`, `android_id_hash` hoặc raw Android system identifier.

Approved

Session Policy

No-timeout session và reboot-login-required behavior là approved product decisions.

Approved

Emergency Override

`EMERGENCY_OVERRIDE_ADMIN` là auditable system identity, không phải real Admin user.

Approved

Media Protection

Apply encryption/security behavior theo Security & Encryption Design.

Approved Direction

Metadata Protection

Sensitive fields phải được protect nếu requirement/security policy yêu cầu.

Approved Direction

Permission

Chỉ request needed permissions và handle denial an toàn.

Approved

Update Security

APK/package validation là bắt buộc trước khi install.

Approved Direction

Operational Logging Security

Android dùng authenticated Backend Relay; không embed Loggly customer token trong APK.

Approved Direction

Crashlytics Security

Custom keys/logs/non-fatal context phải bounded và sanitized.

Approved

Cloud Security

Provider access control/API auth phải theo provider/backend design.

Approved Direction / Detail TBD

## 12. Encryption Direction

Encryption implementation details thuộc **DCAM Security & Encryption Design**.

Area

Direction

Status

Encryption scope

Protect media/database/metadata theo approved security policy.

Draft Direction

Encryption algorithm

Chốt trong Security Design/ADR.

TBD

Key storage

Chốt trong Security Design/ADR.

TBD

Key rotation

Chốt trong Security Design/ADR.

TBD

Diagnostics package protection

Chốt trong Logging Design + Security Design nếu export chứa protected diagnostics.

TBD

BDMA compatibility

Phối hợp với Data Contract và Security Design.

Direction Approved / Detail TBD

## 13. Provider and Fallback Direction

Provider / Mode

Ownership

Notes

Loggly

Operational Logging centralized provider

Nhận operational events thông qua authenticated DCAM Backend Relay trong production direction.

Firebase Crashlytics

Crash & Stability Monitoring provider

Theo dõi fatal, ANR, unexpected non-fatal và stability; compatibility cần Device POC.

DCAM Backend Relay

Operational log security/routing boundary

Own provider credential, schema validation, rate limiting và Loggly forwarding. Exact API thuộc API Contract.

BDMA Desktop import

Optional diagnostics fallback

BDMA có thể import local logs/diagnostics nếu Data Contract support.

Local-only mode

Mandatory fallback

Bắt buộc cho offline, non-GMS hoặc provider-unavailable environment.

Provider operations không được block recording/capture/login-critical operation.

## 14. Remaining TBD Items

Item

Status / Owner

Log retention/rotation exact values

TBD / Logging Design + Device POC + Support

Operational event local format

TBD / Logging Design

Operational queue persistence model

TBD / Logging Design

Backend Relay endpoint/schema/auth

TBD / API Contract + Security

Loggly tag/source/environment convention

TBD / Logging Design + Backend + Support

Operational batching/sampling/rate-limit values

TBD / Logging Design + Device POC

Crash report/local crash marker export format

TBD / Logging Design

Diagnostics export package format/encryption

TBD / Logging Design + Security + BDMA

Crashlytics build/environment enablement

TBD / Logging Design + Product + Security

Crashlytics compatibility on target firmware/device

TBD / Device POC

Battery and thermal performance targets

Deferred / Device POC

Encryption algorithm and key management

TBD / Security Design / ADR

Cloud/provider security rules and API access control detail

TBD / API Contract + Security + Backend

## 15. Practical Conclusion

DCAM phải có đủ observability, reliability và security để vận hành ngoài hiện trường.

Architecture page defines two-channel logging ownership.
Operational Logging is the primary operational observability channel.
Loggly is the centralized Operational Logging provider.
Firebase Crashlytics is the Crash & Stability Monitoring provider.
Crashlytics does not replace Operational Logging.
Operational Logging is local-first, asynchronous and provider-independent.
Production Android does not embed a Loggly customer token; preferred routing uses authenticated DCAM Backend Relay.
DCAM Logging & Diagnostics Design owns implementation detail.
Logging Requirements own required events and sensitive logging rules.
Performance Budget owns measurable targets and release thresholds.
Runtime design pages define recovery behavior.
Security Design defines auth/encryption/provider security constraints.
Data Contract defines BDMA diagnostics/export boundary.