# DCAM Logging & Diagnostics Design

**Page ID**: 51019937  
**Version**: 10  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51019937

---


# DCAM Logging & Diagnostics Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design / Logging & Diagnostics

Version

Approved 0.6

Status

Approved

Approval Scope

Build 0.1 operational logging cho approved decisions; không thay Security Profile

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Backend Lead / QA Lead / Security Reviewer / Support Lead / BDMA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, Backend Developers, QA, Security Reviewer, Support, BDMA Team

Last Updated

2026-07-16

Related Jira

None

Related Documents

07 - Logging, Diagnostics, Performance & Security, 07 - Logging & Diagnostics Requirements, DCAM-BDMA Data Contract, DCAM Security & Encryption Design, DCAM Performance Budget & Resource Constraints, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Self Update Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM Web Portal & Device API Contract, DCAM QA Test Strategy & Test Matrix, DCAM Device POC & Hardware Validation Report, DCAM Documentation Governance, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Tài liệu này là source of truth cho implementation design của logging và diagnostics trong DCAM.

Provider ownership và cross-project logging baseline được reference từ:

DCAM Project Home / DCAM Architecture Home.

07 - Logging, Diagnostics, Performance & Security.

07 - Logging & Diagnostics Requirements.

DCAM-BDMA Data Contract.

Trang này không restate full provider baseline. Local implementation scope:

Logging abstraction and ownership
Operational event schema
Log category and level rules
Internal local-first files and rotation
Persistent upload queue
Backend Relay delivery integration
Firebase Crashlytics integration boundary
Error classification
Sensitive data sanitization
Correlation context
Provider failure behavior
Performance/reliability constraints
QA acceptance direction
## 2. Authoritative References

Topic

Authoritative Document

Local Usage

Provider ownership

07 - Logging, Diagnostics, Performance & Security

Tài liệu này implement mô hình hai channel đã được architecture approve.

Required behavior/events

07 - Logging & Diagnostics Requirements

Tài liệu này không giảm requirement coverage.

BDMA-facing log artifact

DCAM-BDMA Data Contract

`Logs/logs.txt` location, encoding/record boundary và read-only access phải tuân theo Data Contract.

Security

DCAM Security & Encryption Design

Forbidden fields, credential protection và provider-secret rules.

Performance metrics

DCAM Performance Budget & Resource Constraints

`[PERF]` events hỗ trợ measurement mà không ảnh hưởng critical path.

Runtime and recovery

DCAM Android Operation Design

Startup/session/service/recovery events.

Recording/evidence

DCAM Recording & Capture Design

Logging không block recording/finalization hoặc làm mất evidence.

Storage

DCAM Storage Design

Internal files/queue/rotation tuân theo storage safety.

SQLite

DCAM SQLite Database Design

Không network/file-heavy logging trong DB transaction.

Backend Relay API

DCAM Web Portal & Device API Contract

Relay path/schema/auth nếu implemented.

Release validation

DCAM QA Test Strategy & Test Matrix

End-to-end test coverage.

## 3. Core Decisions

Decision

Status

Hai logical channels: `Operational Logging` và `Crash & Stability Monitoring`.

Approved

Operational Logging là kênh vận hành chính.

Approved

Loggly là centralized Operational Logging provider.

Approved

Firebase Crashlytics là crash/stability provider.

Approved

Crashlytics custom logs/keys chỉ là bounded error context.

Approved

Operational Logging phải local-first và hoạt động khi offline/non-GMS/provider unavailable.

Approved

Android production app không nhúng static Loggly customer token.

Approved Direction

Preferred route: local queue → authenticated Backend Relay → Loggly.

Approved Direction

Domain modules không gọi Loggly/Crashlytics trực tiếp.

Approved

`Logs/logs.txt` là stable sanitized BDMA-facing artifact.

Approved Contract

Internal active/rotated files và upload queue không phải BDMA contract artifacts.

Approved Contract

Logging không block recording, emergency, finalization, MainThread hoặc DB transaction.

Approved

Exact retention, rotation count/size, batching và relay endpoint còn TBD.

TBD

## 4. Logging Model

Logging Type

Provider

Responsibility

Operational Logging

Loggly through Backend Relay

Lifecycle, state, business/runtime events, expected failures, recovery, performance và support diagnostics.

Crash & Stability Monitoring

Firebase Crashlytics

Fatal crash, ANR, unexpected non-fatal exception, release stability và bounded context.

Classification boundary:

Expected event / expected failure / state transition / metric
    → Operational Logging

Unhandled fatal crash / ANR / unexpected exception
    → Crashlytics
    → safe Operational Logging marker/context when possible

Crashlytics breadcrumb/custom key
    → bounded context only
    → not complete operational stream
## 5. High-level Architecture

DCAM Modules
Recording / Storage / Identity / Auth / Policy / Update / Config / BDMA / Performance
                                  ↓
                         DCAM Logging Facade
                                  ↓
          ┌───────────────────────┴───────────────────────┐
          ↓                                               ↓
OperationalLogger                                 StabilityReporter
          ↓                                               ↓
SensitiveDataSanitizer                            CrashlyticsAdapter
          ↓
Internal Local Log Writer
          ↓
Active / Rotated Structured Logs
          ├──────────────→ BdmaLogArtifactWriter → Logs/logs.txt
          ↓
Persistent Upload Queue
          ↓
Authenticated Backend Relay
          ↓
Loggly
Failure behavior:

Provider/network unavailable
    ↓
Internal local logging continues
    ↓
Logs/logs.txt remains maintainable/readable
    ↓
Upload retries asynchronously when safe
## 6. Component Responsibilities

Component

Responsibility

`OperationalLogger`

API chính để ghi structured operational event.

`StabilityReporter`

Report fatal/non-fatal context qua provider abstraction.

`DiagnosticContextProvider`

Cung cấp safe app/device/session/runtime context.

`SensitiveDataSanitizer`

Redact/drop forbidden fields before every sink.

`LocalLogWriter`

Ghi local-first asynchronous event.

`LogRotationManager`

Enforce size/time/file-count/retention.

`BdmaLogArtifactWriter`

Maintain hoặc atomically regenerate sanitized `Logs/logs.txt` artifact.

`OperationalLogQueue`

Persist pending/uploaded/retry state.

`OperationalLogUploader`

Upload bounded batch khi network/runtime safe.

`LogglyRelayService`

Backend auth, validate, sanitize, rate-limit và forward tới Loggly.

`CrashlyticsAdapter`

Wrap Firebase SDK và isolate provider failure.

`DiagnosticsExporter`

Future support package export; không thay thế `logs.txt` contract.

## 7. Logging Facade Contract

Recommended direction:

interface OperationalLogger {
    void debug(String category, String eventName, DiagnosticFields fields);
    void info(String category, String eventName, DiagnosticFields fields);
    void warn(String category, String eventName, String reasonCode, DiagnosticFields fields);
    void error(String category, String eventName, String reasonCode,
               ThrowableSummary throwable, DiagnosticFields fields);
}

interface StabilityReporter {
    void setContext(String key, String safeValue);
    void addBreadcrumb(String message);
    void recordNonFatal(Throwable throwable, DiagnosticFields safeContext);
}
Rules:

Rule

Description

LOG-API-001

Domain module chỉ phụ thuộc abstraction.

LOG-API-002

Provider SDK/API nằm trong infrastructure adapter.

LOG-API-003

Logging call không throw ngược vào business flow.

LOG-API-004

Logging failure không làm operation chính fail.

LOG-API-005

Throwable/context phải sanitize trước mọi sink.

## 8. Operational Event Schema

Logical schema direction:

{
  "schema_version": 1,
  "timestamp_utc": "2026-07-10T08:30:00.000Z",
  "level": "INFO",
  "category": "RECORDING",
  "event_name": "recording_started",
  "reason_code": null,
  "message": "Recording started",
  "dcam_cloud_device_id": "safe-id-if-allowed",
  "serial_number_masked": "BC-****-0001",
  "app_version_name": "1.0.0",
  "app_version_code": 100,
  "device_model": "MODEL-A",
  "session_id": "safe-session-id",
  "recording_session_id": "safe-recording-id",
  "correlation_id": "safe-correlation-id",
  "runtime_state": "RECORDING",
  "fields": {
    "latency_ms": 1250
  }
}
Rules:

Rule

Description

LOG-SCHEMA-001

Required: schema version, timestamp, level, category, event name.

LOG-SCHEMA-002

Warn/error/rejection dùng stable safe reason code.

LOG-SCHEMA-003

Machine analysis dựa vào structured fields, không dựa vào free-text-only message.

LOG-SCHEMA-004

Dynamic fields qua allowlist/sanitizer.

LOG-SCHEMA-005

Raw serial không log mặc định; masked representation chỉ khi approved.

LOG-SCHEMA-006

Schema changes phải versioned/backward-compatible.

LOG-SCHEMA-007

Event phải serialize thành one logical record cho `logs.txt`; multi-line content phải escape/normalize.

## 9. Categories and Levels

Categories:

APP
IDENTITY
AUTH
PROVISIONING
RECORDING
CAPTURE
CAMERA
STORAGE
DB
POLICY
CONFIG
UPDATE
BDMA
SENSOR
LOCATION
AI
CAPABILITY
PERF
NETWORK
FACTORY
SECURITY
Levels:

Level

Usage

`DEBUG`

Development diagnostics; production may filter/sample.

`INFO`

Important successful state/operation.

`WARN`

Degraded state, expected rejection, retryable failure, near-threshold.

`ERROR`

Operation failure requiring recovery/support while app may continue safely.

`FATAL`

Crash marker only; Crashlytics owns fatal crash reporting.

Rules:

Do not send every ERROR to Crashlytics.
Do not use ERROR for normal expected branch when WARN + reason_code is sufficient.
DEBUG must still obey sensitive-data rules.
High-frequency events must be sampled/aggregated/rate-limited.
## 10. Operational Logging Flow

Module creates event
    ↓
Facade validates category/event/level
    ↓
Sanitizer filters fields
    ↓
LocalLogWriter appends asynchronously
    ↓
Rotation manager enforces limits
    ↓
BdmaLogArtifactWriter updates safe Logs/logs.txt representation
    ↓
Upload queue marks event/batch pending
    ↓
Uploader sends authenticated bounded batch when safe
    ↓
Backend validates/authenticates/sanitizes/rate-limits
    ↓
Backend forwards to Loggly
    ↓
Queue marks uploaded or schedules retry
Runtime guards:

No network on MainThread.
No upload inside DB transaction.
No heavy serialization/file work on critical camera/finalization path.
Defer batch work during recording stress, storage recovery or low-resource state.
## 11. Local-first Internal Storage

Area

Direction

Internal format

Structured JSON Lines preferred; exact implementation may use equivalent single-record format.

Internal location

App-private/internal path; exact path TBD and not part of BDMA contract.

Write model

Append-oriented, asynchronous, bounded.

Rotation

Required by size/time/file-count; exact values TBD.

Retention

Bounded; exact duration/count TBD.

Queue persistence

Survive process restart/reboot where practical.

Retry

Exponential backoff + jitter or approved equivalent.

Batch

Bounded size/count; no large memory spike.

Drop policy

Drop oldest low-priority events first; emit `log_drop_summary` if possible.

Priority

Security/crash/recovery/recording failures > errors/warnings > important info > routine info > debug.

Internal active/rotated log files remain implementation-private unless a future Data Contract version explicitly exposes them.

## 12. BDMA-facing `logs.txt` Artifact

Path fixed by Data Contract:

```
Internal DCAM Storage Root/Logs/logs.txt
```

Contract:

Property

Implementation Direction

Encoding

UTF-8.

Records

Newline-delimited; one complete sanitized event per line.

Structured format

JSON Lines or equivalent structured single-line export.

Write mode

Append-safe or atomically regenerated/replaced.

Availability

Stable logical name must remain available except short atomic replace window.

Reader

BDMA read-only; should copy/read a safe snapshot.

Sensitive data

Sanitized before export.

Relationship:

Internal active/rotated logs
        ↓ export/sanitize/normalize
Logs/logs.txt
        ↓ ADB read-only
BDMA
Rules:

Rule

Description

BDMA-LOG-001

`logs.txt` is stable BDMA-facing name independent of internal rotation implementation.

BDMA-LOG-002

BDMA does not depend on internal rotated file names/paths.

BDMA-LOG-003

Upload queue and retry metadata are not exported.

BDMA-LOG-004

Crashlytics report/cache is not exported.

BDMA-LOG-005

Provider outage must not make `logs.txt` unavailable when local logging works.

BDMA-LOG-006

Rotation/export failure must not leave a long-lived partial/corrupt artifact.

BDMA-LOG-007

BDMA cannot modify, truncate, rename or delete the artifact.

## 13. Loggly Integration Boundary

Production route:

DCAM Android
    → authenticated Backend Relay
    → Loggly HTTP ingestion
Rules:

Rule

Description

LOGGLY-001

No hardcoded Loggly token in Android APK.

LOGGLY-002

No direct production Android → Loggly delivery without separate Security Review/ADR.

LOGGLY-003

Backend owns token, environment routing, rate limiting and provider retry.

LOGGLY-004

Backend validates schema and sanitizes again.

LOGGLY-005

Dev/staging/prod use distinct source/tags/environment.

LOGGLY-006

Provider outage does not fail Android operation.

LOGGLY-007

Exact endpoint/auth/schema belongs to API Contract.

## 14. Crashlytics Boundary

Use Crashlytics for:

Unhandled fatal crash
ANR
Unexpected non-fatal exception
Unexpected invariant violation
Repeated unexpected SDK/framework exception requiring stability analysis
Bounded breadcrumbs/custom keys directly related to an error
Do not use Crashlytics for:

Routine lifecycle
Every recording start/stop
Expected validation failure
Expected timeout/retry
Normal storage warning
Full performance stream
Full operational event upload
Factory production record
Rules:

Rule

Description

CRASH-001

Crashlytics does not replace OperationalLogger.

CRASH-002

`recordNonFatal` only for unexpected/actionable exception.

CRASH-003

Custom keys/breadcrumbs are bounded and sanitized.

CRASH-004

No credential/token/raw identifier/sensitive payload.

CRASH-005

Provider unavailable fails silently.

CRASH-006

Non-GMS/target compatibility requires Device POC; local diagnostics remain mandatory.

## 15. Error Classification

Error Class

Operational Logging

Crashlytics

Expected validation rejection

`WARN` + reason code

No

Expected offline/timeout

`WARN`/sampled `INFO` + retry context

No

Storage near full

`WARN`

No

Known recording SDK failure

`ERROR` + reason code

Only if exception unexpected/actionable

Handled unexpected exception

`ERROR` + sanitized summary

Non-fatal if actionable

Unhandled exception

Best-effort crash marker

Fatal/provider automatic

ANR

Optional local watchdog marker

Stability monitoring

Provider delivery failure

Sampled `WARN` + queue state

No

Security validation failure

`WARN/ERROR` + safe reason

Only for unexpected exception

## 16. Sensitive Data and Sanitization

Forbidden in all sinks:

raw media/pre-record content
raw sensor/location/AI/biometric payload
password/pattern/maintenance credential
credential hash input
access/session/Firebase/cloud token
factory Wi-Fi password
Google account password/token
APK signing private key
provisioning/enrollment/long-lived QR secret
ANDROID_ID
android_id_hash
raw Android system identifier
full sensitive config payload
stack trace fragments containing credential input
Sanitization sequence:

sanitize before internal persistence
sanitize before Logs/logs.txt export
sanitize again before Backend Relay upload
sanitize before Crashlytics keys/logs/non-fatal report
If sanitizer fails, drop unsafe field/event rather than persist raw data.

## 17. Correlation Context

Allowed safe context when applicable:

correlation_id
request_id
session_id
recording_session_id
device_boot_id
app_version
contract_version
runtime_state
recording_state
storage_mode
last_safe_reason_code
feature_eligibility state
provider mode
Rules:

IDs are bounded and validated.
Context lookup does not perform blocking I/O for every log call.
Missing context does not fail logging.
Context does not contain credential or raw personal data.
## 18. Performance and Reliability Constraints

Constraint

Direction

MainThread

No file/network/heavy serialization.

Recording priority

Logging/upload must not compete with camera/encoder/finalization.

Memory

Buffers/batches bounded.

Storage

Rotation/retention required; low-storage drops low priority first.

Network

Async, bounded timeout, retry/backoff.

DB

No network or large serialization in transaction.

Failure isolation

Sink/provider/file failure does not crash app or main operation.

Recursion guard

Internal logging failure cannot create infinite loop.

Sampling

High-frequency events/metrics sampled or aggregated.

## 19. Provider Failure Behavior

Scenario

Expected Behavior

Loggly unavailable

Backend/Android retry bounded; core operation continues.

Backend Relay unavailable

Events remain queued within capacity.

Network unavailable

No upload; local logging and `logs.txt` continue.

Crashlytics unavailable

Ignore provider safely; local operational context remains.

Internal log write fail

Best-effort fallback marker/health warning; no business-flow crash.

`logs.txt` export fail

Preserve internal source; retry export; do not expose partial artifact.

Queue corrupted

Rebuild/drop queue safely; preserve internal logs when possible.

Storage critically low

Drop low-priority logs; preserve recording/evidence and critical markers.

Sanitizer failure

Drop unsafe data.

## 20. QA / Acceptance Checklist

Test Case

Expected Result

Operational event

Persisted local before/independent of upload.

Offline device

Local logs and `logs.txt` continue; queue remains bounded.

Network restored

Pending batch retries when safe.

Relay/Loggly unavailable

Recording/login/runtime not blocked.

Crashlytics unavailable

App remains stable; Operational Logging works.

Expected timeout

Operational warn/retry event only; no Crashlytics spam.

Unexpected handled exception

Operational error + approved non-fatal.

Fatal crash

Crashlytics report when available + best-effort local marker.

Sensitive injection

Removed from internal log, `logs.txt`, relay and Crashlytics.

High-frequency event

Sampling/rate limit prevents log storm.

Low storage

Low-priority logs reduced before critical evidence path.

Rotation

Internal files and queue remain bounded.

`logs.txt` contract

UTF-8, one logical record per line, stable path and BDMA read-only.

Internal archive isolation

BDMA does not depend on internal rotated files/queue.

MainThread

No file/network blocking from logging.

Relay auth failure

No Loggly forward; safe handling/retry policy.

Detailed test IDs belong to **DCAM QA Test Strategy & Test Matrix**.

## 21. Open Questions / TBD

Item

Status

Exact internal app-private active/rotated log path

TBD / Storage Design

Exact internal rotation size/time/file-count

TBD / Device POC + Support

Retention duration

TBD / Product + Support + Security

Queue persistence implementation

TBD / Android Design

Batch size and upload interval

TBD / Device POC + Backend

Backend Relay endpoint/schema/auth

TBD / API Contract + Security

Loggly tags/source convention

TBD / Backend + Support

Production sampling/rate-limit values

TBD

`logs.txt` append vs atomic regeneration strategy

TBD / Android + BDMA testing

Maximum `logs.txt` size/snapshot window

TBD / BDMA + Support + Storage

Diagnostics package format/encryption

TBD / Security + BDMA

Crashlytics enablement by build/environment

TBD / Product + Security

Crashlytics compatibility per target firmware

TBD / Device POC

Whether masked serial is allowed in centralized logs

TBD / Security

## 22. Practical Conclusion

DCAM Logging & Diagnostics Design sở hữu implementation detail, không sở hữu lại cross-project provider decision.

Trang này định nghĩa logging abstraction, event schema, local file/rotation, persistent queue, Backend Relay integration và Crashlytics adapter boundary.

Internal active/rotated log và upload queue là implementation-private.

`Logs/logs.txt` là sanitized BDMA-facing artifact theo DCAM-BDMA Data Contract.

Mỗi sink phải sanitize độc lập; logging/provider failure không được block critical DCAM operation.

Provider ownership hoặc baseline thay đổi được cập nhật tại Architecture Home/Logging Architecture trước; trang này chỉ cập nhật local integration impact.

## 23. Build 0.1 Decision Event Mapping

Event Group

Required Context

Storage Pre-check

Internal free storage, result và non-start reason

Storage Runtime Failure

Failure reason, safe-stop result và finalization result

MP4 MD5

Start, success, pending, failed, missing hoặc mismatch

BDMA Readiness

Readiness transition hoặc blocked reason

Operator

B01OPR / Build 0.1 Operator tại nơi schema yêu cầu; technical traceability only

Device Status

Battery level, Internal free storage, GPS Available/Unavailable/Unsupported

POC Evidence

Reference model, OS/API, firmware và physical device identifier

Không log credential, token, coordinates hoặc route. MD5 không được mô tả như encryption, authentication hoặc security signature.