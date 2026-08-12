# DCAM Android Operation Design

**Page ID**: 48562239  
**Version**: 24  
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

2.2

Status

Approved Provisional Baseline

Approval Scope

Build 0.1 operation overlay; reference-device behavior Pending Device POC

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

2026-07-20

Related Jira

None

Related Documents

DCAM Release & Build Applicability Matrix, DCAM Android Device Owner & Kiosk Policy Design, DCAM Device Provisioning Web Portal Design, DCAM Web Portal & Device API Contract, DCAM SQLite Database Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM State Machine Design, DCAM Security & Encryption Design, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

Dependencies / Blockers

Device POC: confirm reference-device lifecycle, boot/background/foreground/recovery behavior and associated evidence.

## 1. Current Runtime Baseline

Project-wide Device Identity và Device Owner/Kiosk baseline không được định nghĩa lại tại đây.

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

same-boot valid session → restore
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