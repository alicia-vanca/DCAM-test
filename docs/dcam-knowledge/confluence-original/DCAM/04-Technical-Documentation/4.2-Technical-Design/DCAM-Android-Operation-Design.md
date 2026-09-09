# DCAM Android Operation Design

**Page ID**: 48562239  
**Version**: 28  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48562239

---


# DCAM Android Operation Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

2.6

Status

Approved Provisional Baseline

Approval Scope

Build 0.1 operation overlay plus DDMP Hybrid runtime boundary and GMS-free Android runtime guard; reference-device behavior and Hybrid coexistence remain Pending Device POC.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / Cloud Lead / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, QA, Support, Cloud/WebServer Team

Last Updated

2026-08-26

Related Jira

None

Related Documents

DCAM Release & Build Applicability Matrix, DCAM Android Device Owner & Kiosk Policy Design, DCAM Device Provisioning Web Portal Design, DCAM Web Portal & Device API Contract, DCAM SQLite Database Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM State Machine Design, DCAM Security & Encryption Design, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

Dependencies / Blockers

Device POC: confirm reference-device lifecycle, boot/background/foreground/recovery behavior and associated evidence.

## 1. Current Runtime Baseline

Project-wide Device Identity, Device Owner/Kiosk và GMS-free runtime baseline không được định nghĩa lại tại đây.

Production runtime must not require Google Play services, Play Store, Google account, FCM, Analytics or Play Integrity. Runtime recovery, recording, diagnostics, BFF sync and safe update deferral remain functional without them; authoritative gates belong to **ADR - DCAM GMS-free Android Runtime Baseline**.

Baseline Topic

Authoritative Reference

Current project and architecture baseline

DCAM Project Home / DCAM Architecture Home

Device Identity

ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id

Device Owner / EMM / Lock Task direction

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

Kiosk and maintenance policy

DCAM Android Device Owner & Kiosk Policy Design

Provisioning paths and schema

DCAM Web Portal & Device API Contract

Update direction

DCAM Self Update Design

Local implementation impact của trang này:

Runtime verify required device policy trước normal field operation.

Runtime resolve local/cloud identity và chuyển sang recovery/provisioning state khi thiếu identity.

Runtime điều phối Login, session, Lock Task, maintenance, update và recovery theo state/guard của Android application.

Các giá trị baseline thay đổi phải được cập nhật tại authoritative document; trang này chỉ cập nhật khi runtime orchestration thay đổi.

## 2. Startup Flow

initialize logging
→ resolve runtime profile
→ verify required device policy
→ verify restrictions/Home/Lock Task allowlist
→ open DB/config
→ resolve identity
→ run DB/storage/recovery
→ load settings/config/update cache
→ evaluate capability
→ prepare modules
→ enter Lock Task when lifecycle is safe
→ restore same-boot session or show Login
## 3. Missing Policy Behavior

This behavior is no longer wholly `TBD`.

If production profile requires Device Owner/kiosk authority and it is missing:
    enter DEVICE_POLICY_REQUIRED or POLICY_DEGRADED
    log safe reason
    block normal field operation by default
A limited degraded exception requires an explicit Product/Security decision and build-profile entry.

## 4. Identity and Provisioning

local serial + cloud ID → use local identity
serial only → lookup serial_lookup/{serial_number}
lookup found → restore devices/{dcam_cloud_device_id}
lookup missing → PROVISIONING_REQUIRED
serial missing → DSetup/rework flow
Web provisioning uses Factory Worker, Login/Workspace and QR-derived read-only serial. It does not create Device Owner state.

## 5. Login and Operation Gate

same-boot valid session �� restore
reboot → require login
normal recording without operator → OPERATOR_AUTH_REQUIRED
emergency without operator → EMERGENCY_OVERRIDE_ADMIN when approved
Maintenance and policy transitions are blocked during recording, emergency, finalization, recovery and unsafe update state.

## 6. Runtime Modes

BOOTING
DEVICE_POLICY_REQUIRED
DEVICE_POLICY_APPLIED
LOCK_TASK_ACTIVE
POLICY_DEGRADED
POLICY_RECOVERY_REQUIRED
MAINTENANCE_AUTH_REQUIRED
MAINTENANCE_MODE
SERIAL_REQUIRED
PROVISIONING_REQUIRED
LOGIN_REQUIRED
OPERATOR_AUTHENTICATED
READY
RECORDING_ACTIVE
EMERGENCY_ACTIVE
DEGRADED_OPERATION
SAFE_MODE
UPDATING
FATAL_ERROR
## 7. Performance Baseline

Metric

Target

Cold boot to recording-ready

`≤ 8s`

App restart to recording-ready

`≤ 4s`

Login screen after READY

`≤ 1s`

Targets are owned by Performance Budget and validated by Device POC.

## DDMP Hybrid Runtime Boundary

Hybrid activation is Phase 2+/POC-gated. DCAM continues to start, record, recover and enforce local policy with no Headwind/BFF/R2 dependency in Build 0.1.

DCAM boot / local policy + recovery
    ↓
DCAM starts its own runtime and enters safe lifecycle state
    ↓
Headwind Client, if installed, runs as a normal Application-mode app
    ↓
DCAM outbound sync to BFF reads desired state / release authorization
    ↓
DCAM validates identity + schema + runtime guards
    ↓
DCAM applies only safe local actions and sends ACK/result to BFF

Runtime rule

Behavior

Authority

Only DCAM DPC executes DevicePolicyManager, kiosk/Lock Task/restrictions and package install/rollback. Headwind Client has no privileged path.

Command source

BFF is the device-facing boundary. Headwind is reached through BFF adapter only; no direct Headwind REST/database call and no assumed Headwind-to-DCAM command bridge.

Identity

`platformDeviceId = dcam_cloud_device_id`; `serial_number` remains local/factory recovery key; Headwind reference is mapping-only.

Delivery semantics

Desired-state is versioned/idempotent; DCAM separates received, accepted/deferred/rejected, executed and acknowledged outcomes.

Update

BFF authorizes an immutable R2/CDN artifact. DCAM download/validate/install/health-check/rollback follows Self Update and local safety guards.

Coexistence gate

OEM/firmware validation must prove Headwind package does not disturb DCAM boot, HOME, Lock Task, auto-start, restrictions or policy recovery.

Diagnostics delivery

Local DiagnosticsOutbox is written before remote delivery. Upload is opportunistic after startup and only when network/runtime guards allow; BFF batch ACK, not provider receipt, confirms remote delivery.

### Offline diagnostics scheduler

boot / network opportunity
    → verify local runtime is not in critical recording/finalization/recovery pressure
    → select bounded pending DiagnosticsOutbox batch by priority
    → upload to BFF with backoff+jitter and stable event_id
    → apply BFF ACK or retry/rate-limit hint
Diagnostics upload must not run on the main thread, block startup, compete with protected recording/finalization or require FCM/GMS. Prolonged offline only increases bounded queue age; it does not change DCAM safe runtime state.

Authoritative DDMP sources: [DDMP 00](/wiki/spaces/DVID/pages/68845572/00+DDMP+Architecture+Overview+Reading+Guide), [DDMP 03 BFF](/wiki/spaces/DVID/pages/68812826/03+Management+API+BFF+Architecture+Baseline), [DDMP 06 Device Integration Contracts](/wiki/spaces/DVID/pages/68812848/06+Device+Integration+Contracts), [DDMP 05 APK Release & Cloudflare R2](/wiki/spaces/DVID/pages/68780056/05+APK+Release+Cloudflare+R2), [DDMP 08 Operations](/wiki/spaces/DVID/pages/68812869/08+Operations+SLO+Runbooks).

## 8. Resolved and Remaining Decisions

Item

Status

Missing required policy behavior

Approved baseline: required/degraded state and block by default

Identity restore path

Approved

Web provisioning model

Approved

Startup performance targets

Defined

Foreground service type mapping

TBD / Android POC

Notification wording/actions

TBD / Product

Boot/OEM startup requirements

TBD / Device POC

Battery exemption policy

TBD / Device POC

Service restart/heartbeat values

TBD / Implementation + POC

Provisioning polling in service

TBD / Android + Backend

Exact Lock Task re-entry point

TBD / Kiosk POC

Policy-missing degraded exception

TBD only if requested

Target-device update install mechanism

TBD / Device POC

SD identity sync schedule/error policy

TBD / Android + Security + QA

## 9. Practical Conclusion

Policy-missing behavior, identity restore and startup budgets are defined.
Remaining TBDs are OEM, lifecycle scheduling and explicit exception details.
## 12. Build 0.1 Operation Overlay

Operation Area

Build 0.1 Behavior

Startup

Không hiển thị login UI; dùng static Build 0.1 operator context.

Recording Pre-check

Camera/permission và Internal storage pre-check phải đạt.

Storage Failure

Safe-stop và finalize MP4 nếu còn khả năng.

Finalization

Finalize MP4, tạo MD5 bất đồng bộ, chỉ BDMA_READY sau success.

Device Status

Hiển thị/report battery level, Internal free storage, GPS Available/Unavailable/Unsupported.

GPS Failure

Unavailable/Unsupported không làm fail WRS nếu reporting chính xác.

Device Coverage

Chỉ NCC-036V / Android 12 / API 31 / 877AOOAKN1_RK2_V009; Pending Device POC.

Target-state login/operator behavior không áp dụng cho Build 0.1.

## Device credential operational states

Device credential lifecycle inherits [ADR – DCAM Device API Credential & mTLS Baseline](/wiki/spaces/DVID/pages/70287362/ADR+DCAM+Device+API+Credential+mTLS+Baseline). This is a network-management state overlay and must not replace the recording/evidence state machine.

State

Entry condition

Required DCAM behaviour

Credential absent

First boot, protected key/certificate unavailable

Generate/prepare Keystore key and enter controlled enrollment path; no Device API mutation.

Enrollment pending

BFF factory/support pairing exists

Prove key possession, obtain certificate only after BFF approval; bounded retry while online.

Credential active

Valid mTLS certificate is present

Use Device API only; attach requestId/eventId to replay-sensitive operations.

Rotation due

BFF/local policy indicates replacement needed

Rotate through active credential/proof-of-possession; preserve approved grace behaviour.

Credential rejected/revoked

BFF rejects certificate or local trust fails

Reject/defer cloud control, persist safe reason and audit when possible; do not interrupt local recording/evidence.

Re-enrolment required

Reset/reinstall/key loss/rework

Do not authenticate by serial alone; require authorized factory/support flow.