# DCAM State Machine Design

**Page ID**: 48496753  
**Version**: 14  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496753

---


# DCAM State Machine Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

2.1

Status

Approved Provisional Baseline

Approval Scope

Target-state model với approved Build 0.1 no-login/checksum overlay

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, QA, Support

Last Updated

2026-07-13

Related Jira

None

Related Documents

05 - User & Device Operation Requirements, DCAM Device Capability & Feature Eligibility Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Sensor & Location Monitoring Design, DCAM Realtime AI Detection Design, DCAM Self Update Design, DCAM Security & Encryption Design, 09 - System Settings Requirements, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

**DCAM State Machine Design** định nghĩa cross-runtime state-machine principle, global guard rule và state ownership boundary cho DCAM.

Project-wide Device Owner/EMM, maintenance và update baseline được reference từ:

DCAM Project Home / DCAM Architecture Home.

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision.

DCAM Android Device Owner & Kiosk Policy Design.

DCAM Self Update Design.

Trang này không restate full baseline. Local implementation impact là:

Điều phối priority và guard giữa recording, emergency, storage, DB, policy, maintenance, update và recovery.

Xác định domain nào sở hữu từng state set.

Ngăn transition không an toàn giữa các domain state machine.

## 2. State Ownership Boundary

State Group

Authoritative Document

State Machine Design Role

Feature eligibility states

DCAM Device Capability & Feature Eligibility Design

Consume official eligibility states cho runtime registration decisions.

App operating modes

DCAM Android Operation Design

Reference Android app modes như startup, policy required, login required, authenticated, recording active, safe mode và update.

Android device policy / kiosk states

DCAM Android Device Owner & Kiosk Policy Design + DCAM Android Operation Design

Reference Device Owner/DPC, Lock Task, User Restrictions, Controlled Maintenance Mode and policy recovery states.

In-app console / maintenance states

DCAM In-App Operation, Device Settings & Media Console Design

Reference Setting hub, Maintenance Password Gate, approved targets and controlled-mode behavior.

Operator login/session states

DCAM Android Operation Design + DCAM SQLite Database Design

Reference operator session guard; không own auth schema/details.

User/operator requirements

05 - User & Device Operation Requirements

Reference recording login requirement và emergency override rule.

Recording session states

DCAM Recording & Capture Design

Reference recording state machine và enforce global guard priority.

Storage/file readiness states

DCAM Storage Design

Reference storage finalization, recovery và BDMA readiness states.

Database/runtime persistence states

DCAM SQLite Database Design

Reference DB recovery, migration, external write và transaction states.

Sensor/location runtime states

DCAM Sensor & Location Monitoring Design

Reference monitoring/tracking runtime behavior nếu feature eligible.

Realtime analytics runtime states

DCAM Realtime AI Detection Design

Reference realtime runtime behavior nếu feature eligible.

Self Update states

DCAM Self Update Design and 09 - System Settings Requirements

Reference Self Update flow, AutoUpdate safety preconditions và policy-safe update guard.

Optional Play Store fallback state

DCAM In-App Operation, Device Settings & Media Console Design + DCAM Self Update Design

Reference only when fallback is enabled and capability-approved.

## 3. Official Feature Eligibility States

Feature eligibility state names được định nghĩa bởi **DCAM Device Capability & Feature Eligibility Design**.

Runtime state machines may start only for:
- ENABLED
- approved DEGRADED

Runtime state machines must not start normal runtime flow for:
- DISABLED_BY_POLICY
- DISABLED_BY_PERMISSION
- UNSUPPORTED_HARDWARE
- UNSUPPORTED_PERFORMANCE
- TEMPORARILY_UNAVAILABLE
- PRUNED
- ERROR
`SUPPORTED` không được dùng làm runtime/eligibility state. Dùng `ENABLED` khi feature thực sự có thể chạy.

## 4. Runtime State Reference Map

Runtime Area

Example State Names

Source of Truth

Android app operation

`BOOTING`, `DEVICE_POLICY_REQUIRED`, `DEVICE_POLICY_APPLIED`, `LOGIN_REQUIRED`, `OPERATOR_AUTHENTICATED`, `READY`, `RECORDING_ACTIVE`, `EMERGENCY_ACTIVE`, `DEGRADED_OPERATION`, `SAFE_MODE`, `UPDATING`, `FATAL_ERROR`

DCAM Android Operation Design

Android kiosk policy

`DEVICE_POLICY_UNKNOWN`, `DEVICE_POLICY_REQUIRED`, `DEVICE_POLICY_APPLIED`, `LOCK_TASK_READY`, `LOCK_TASK_ACTIVE`, `LOCK_TASK_FAILED`, `MAINTENANCE_AUTH_REQUIRED`, `MAINTENANCE_AUTH_FAILED`, `MAINTENANCE_MODE`, `POLICY_DEGRADED`, `POLICY_RECOVERY_REQUIRED`

DCAM Android Device Owner & Kiosk Policy Design

In-app console

`RECORD_LIVE_VIEW`, `SETTING_HUB`, `CONSOLE_CHILD_MODULE`, `CONSOLE_ACCESS_DENIED`, `CONSOLE_UNAVAILABLE`

DCAM In-App Operation, Device Settings & Media Console Design

Operator session

`ACTIVE`, `LOGGED_OUT`, `EXPIRED_BY_REBOOT`, `REVOKED`, `INVALID`

DCAM SQLite Database Design / Android Operation Design

Recording session

`IDLE`, `PRECHECKING`, `RECORDING`, `STOPPING`, `FINALIZING`, `BDMA_READY`, `OPERATOR_AUTH_REQUIRED`, `RECOVERY_REQUIRED`

DCAM Recording & Capture Design

Storage/file state

`IN_PROGRESS`, `FINALIZING`, `FINALIZED`, `BDMA_READY`, `RECOVERY_REQUIRED`, `RECOVERY_FAILED`

DCAM Storage Design

Database state

`MIGRATION_REQUIRED`, `MIGRATING`, `RECOVERY_REQUIRED`, `SAFE_MODE`, external write states

DCAM SQLite Database Design

Feature eligibility

`ENABLED`, `DEGRADED`, `PRUNED`, `TEMPORARILY_UNAVAILABLE`, etc.

DCAM Device Capability & Feature Eligibility Design

Self Update flow

`UPDATE_CHECKING`, `UPDATE_AVAILABLE`, `UPDATE_DEFERRED`, `APK_DOWNLOADING`, `APK_VALIDATING`, `INSTALLING`, `UPDATE_FAILED`, `UPDATE_VERIFIED`

DCAM Self Update Design

Play Store fallback

`PLAY_FALLBACK_UNAVAILABLE`, `PLAY_FALLBACK_AUTH_REQUIRED`, `PLAY_FALLBACK_ACTIVE`, `PLAY_FALLBACK_RETURN_REQUIRED`

In-App Console + Self Update

The examples above are references only. Detailed meaning, transitions and persistence requirements stay in the authoritative document for each runtime area.

## 5. Global Priority and Guard Rules

Priority

Runtime Area

Guard Direction

P0

Emergency evidence preservation

Không được bị interrupt bởi update, cleanup, policy transition hoặc optional workloads. Emergency có thể dùng override operator nếu chưa login.

P1

Recording / Finalizing

Không được bị interrupt bởi update, destructive DB writes, unsafe storage cleanup hoặc unsafe kiosk policy changes.

P2

Storage / DB / Policy Recovery

Phải complete hoặc enter safe/degraded mode trước khi normal runtime resume.

P3

Required Device Policy for Production Field Operation

Nếu production profile yêu cầu DCAM-as-DPC/Lock Task/User Restrictions mà state missing/invalid, normal field operation phải blocked/degraded theo Kiosk Policy Design.

P4

Operator authentication for normal recording

Normal recording/capture evidence yêu cầu active operator session.

P5

Capability evaluation and runtime registration

Phải complete trước khi optional modules start.

P6

Controlled Maintenance

Chỉ chạy sau Maintenance Password Gate và khi runtime safe. Không có full Android unrestricted mode.

P7

Sensor, location and realtime analytics

Optional workloads; có thể chạy without operator login nếu eligible và policy cho phép.

P8

Self Update / remote config apply

Chỉ chạy khi authoritative safety preconditions, policy-safe update conditions và guards cho phép.

P9

Diagnostics / support operation

Được phép nếu không mutate unsafe runtime state; Maintenance Mode phải auditable.

## 6. Device Policy / Kiosk Guard

Production field operation có thể yêu cầu DCAM-as-DPC / local Device Owner policy, Lock Task Mode và User Restrictions.

App Startup / Resume
    ↓
DevicePolicyGuard
    ├── Required policy present and valid
    │       → allow startup to continue
    ├── Required policy partially valid
    │       → enter POLICY_DEGRADED; allow only approved degraded path
    └── Required policy missing or invalid
            → enter DEVICE_POLICY_REQUIRED; block normal field operation
Rules:

Rule

Description

SM-POLICY-001

Device policy state must be checked before normal field operation when production profile requires kiosk.

SM-POLICY-002

Lock Task Mode must not start until package allowlist is verified.

SM-POLICY-003

User Restrictions apply/remove must be blocked during emergency, recording, finalizing, DB/storage recovery or unsafe update/install.

SM-POLICY-004

Maintenance Mode transitions require Maintenance Password Gate, explicit authorization and audit.

SM-POLICY-005

Policy recovery must not start duplicate recording or clear evidence.

SM-POLICY-006

Missing required kiosk policy must not silently allow unrestricted production operation.

SM-POLICY-007

Unsupported OEM restriction must be represented as policy/capability limitation and routed through approved degraded/release decision.

SM-POLICY-008

External EMM / Android Management API / Managed Google Play state is not a current baseline dependency.

## 7. Controlled Maintenance Guard

Admin / Maintenance request
    ↓
MaintenanceGuard
    ├── Runtime unsafe
    │       → reject/defer with reason
    ├── Role not allowed
    │       → reject CONSOLE_ACCESS_DENIED
    ├── Maintenance Password Gate failed/locked
    │       → reject MAINTENANCE_AUTH_FAILED
    └── Authenticated and safe
            → enter MAINTENANCE_MODE with approved targets only
Rules:

Rule

Description

SM-MAINT-001

Maintenance Mode requires Maintenance Password Gate.

SM-MAINT-002

Emergency override must not satisfy Maintenance Password Gate.

SM-MAINT-003

Maintenance must be blocked during recording, emergency, finalization, DB/storage recovery, policy recovery or update/install unsafe state.

SM-MAINT-004

Maintenance Mode may open only approved apps/settings screens.

SM-MAINT-005

Full Android unrestricted mode is not supported.

SM-MAINT-006

On timeout/exit/resume/reboot/crash recovery, policy restore must be attempted.

## 8. Operator Authentication Guard

Normal recording/capture evidence phải được guard bằng authenticated operator session.

Normal Start Recording
    ↓
DevicePolicyGuard if production profile requires it
    ↓
OperatorAuthenticatedRecordingGuard
    ├── Required policy state is invalid
    │       → reject POLICY_REQUIRED or POLICY_DEGRADED_BLOCKED
    ├── Active operator session exists
    │       → allow Recording Precheck
    └── No active operator session
            → reject OPERATOR_AUTH_REQUIRED
Emergency recording có override path riêng:

Emergency Start Recording
    ↓
EmergencyRecordingGuard
    ├── Active operator session exists
    │       → use active operator snapshot
    └── No active operator session
            → use EMERGENCY_OVERRIDE_ADMIN system operator
Rules:

Rule

Description

SM-AUTH-001

Normal recording/capture evidence phải yêu cầu active operator session.

SM-AUTH-002

Emergency recording có thể bypass login và dùng `EMERGENCY_OVERRIDE_ADMIN`.

SM-AUTH-003

Emergency override không được map vào real Admin user.

SM-AUTH-004

System tracking, GPS tracking, sensor monitoring, logging, DB/storage recovery, kiosk policy verification, update metadata check và capability evaluation không yêu cầu operator login.

SM-AUTH-005

Login session không có timeout và không được clear bởi background/foreground transition.

SM-AUTH-006

Device reboot invalidates previous operator session và yêu cầu login lại.

SM-AUTH-007

User disabled by BDMA sync không được interrupt active recording; new recording bị blocked sau safe window.

## 9. Self Update Guard

Current baseline uses Self Update / APK update as primary path.

Update request
    ↓
UpdateGuard
    ├── Managed Google Play requested
    │       → reject/not applicable for current baseline
    ├── Recording/Emergency/Finalizing active
    │       → defer update
    ├── DB/Storage/Policy recovery active
    │       → defer update
    ├── APK validation failed
    │       → reject package
    └── Preconditions valid and package safe
            → enter UPDATING
Rules:

Rule

Description

SM-UPD-001

Self Update is the primary update path for current no-external-EMM baseline.

SM-UPD-002

Managed Google Play / Android Management API policy-driven update is not applicable.

SM-UPD-003

Update must be deferred during recording, emergency, finalization, DB/storage recovery, policy recovery or unsafe maintenance transition.

SM-UPD-004

Update must validate package identity, checksum, signature, version and compatibility before install.

SM-UPD-005

Update failure must not leave device unrestricted.

SM-UPD-006

After update/restart, policy restore and Lock Task recovery must be checked.

SM-UPD-007

Manual Play Store fallback requires Controlled Maintenance Mode and capability-approved target.

## 10. Cross-runtime Coordination Rules

Rule

Description

SM-CROSS-001

Domain runtime state machines không được bypass global guard rules.

SM-CROSS-002

Recording/finalizing state blocks Self Update, unsafe policy changes và unsafe cleanup.

SM-CROSS-003

Emergency active state có highest local runtime priority.

SM-CROSS-004

Storage hoặc DB recovery phải preserve evidence-like artifacts trước cleanup.

SM-CROSS-005

Optional feature failure không được crash hoặc block core recording trừ khi đó là direct dependency.

SM-CROSS-006

Runtime modules chỉ được register sau capability/eligibility evaluation.

SM-CROSS-007

External DB write-back không được override active runtime guards.

SM-CROSS-008

`BDMA_READY` nghĩa là safe for BDMA scan/import, không phải already imported.

SM-CROSS-009

Detailed state transitions vẫn nằm trong domain design page sở hữu runtime area đó.

SM-CROSS-010

Operator/session/user sync state không được mutate trực tiếp active recording lifecycle.

SM-CROSS-011

Auth/session changes từ BDMA sync chỉ apply khi runtime guard cho phép.

SM-CROSS-012

Kiosk policy change requests từ remote config chỉ apply khi Kiosk Policy Design và runtime guards cho phép.

SM-CROSS-013

Lock Task failure must route to policy recovery/degraded state, not unrestricted runtime.

SM-CROSS-014

File Manager / Media Viewer are read-only and must not mutate recording/storage state.

SM-CROSS-015

Console setting changes must not bypass recording, policy or update guards.

## 11. Capability Evaluation and Runtime Registration

App Start / Boot
        ↓
Android Operation Startup
        ↓
Device Policy State Detection if required
        ↓
Device Capability Evaluation including update and Play Store fallback capability
        ↓
Feature Eligibility Result
        ↓
State Machine Registration
        ↓
Only ENABLED or approved DEGRADED runtime modules start
        ↓
Login gate controls only features that require operator identity
Runtime registration decision:

Eligibility State

Registration Behavior

`ENABLED`

Register normal runtime path.

`DEGRADED`

Register approved degraded runtime path.

`DISABLED_BY_POLICY`

Không register.

`DISABLED_BY_PERMISSION`

Không register cho đến khi permission thay đổi và eligibility được re-evaluate.

`UNSUPPORTED_HARDWARE`

Không register; runtime path bị pruned.

`UNSUPPORTED_PERFORMANCE`

Không register trừ khi approved degraded mode tồn tại.

`TEMPORARILY_UNAVAILABLE`

Chưa register; có thể re-evaluate later.

`PRUNED`

Runtime path bị exclude.

`ERROR`

Dùng safe fallback hoặc error handling.

## 12. Relationship with Domain State Machines

Domain

What State Machine Design Requires

Detailed State Owner

Android Operation

Phải coordinate startup, device policy verification, login screen, session restore, foreground service, safe mode, update runtime và module registry.

DCAM Android Operation Design

Android Kiosk Policy

Phải coordinate Device Owner/DPC state, Lock Task, User Restrictions, Maintenance Mode và policy recovery với global guards.

DCAM Android Device Owner & Kiosk Policy Design

In-app Console

Phải coordinate Setting hub, read-only file/media, Login/User settings, Maintenance Password Gate and controlled targets with global guards.

DCAM In-App Operation, Device Settings & Media Console Design

Self Update

Phải defer nếu recording, emergency, finalizing, recovery, policy recovery hoặc unsafe runtime state đang active.

DCAM Self Update Design and 09 - System Settings Requirements

User / Operator

Normal recording yêu cầu authenticated operator; emergency override dùng system operator.

05 - User & Device Operation Requirements

Recording

Phải tuân thủ policy/auth guard, global priority và không duplicate/unsafe transitions.

DCAM Recording & Capture Design

Storage

Không được expose partial files như final media và phải tuân thủ recovery guard.

DCAM Storage Design

SQLite DB

Không được allow unsafe write-back/migration/user sync trong critical runtime states.

DCAM SQLite Database Design

Realtime AI / Analytics

Phải optional, capability-gated và non-blocking cho recording; chỉ emit events.

DCAM Realtime AI Detection Design

Sensor / Location

Phải optional/capability-gated và không điều khiển recording trực tiếp; có thể chạy before login.

DCAM Sensor & Location Monitoring Design

## 13. Logging Direction

State-machine coordination logs nên reference domain logs thay vì duplicate.

Required examples:

[STATE] Runtime registration started
[STATE] Feature pruned by eligibility result
[STATE] Device policy guard rejected normal operation
[STATE] Lock task failure routed to policy recovery
[STATE] Maintenance auth required
[STATE] Maintenance target blocked
[STATE] Operator auth guard rejected recording
[STATE] Emergency override guard accepted recording
[STATE] Global guard rejected command: RECORDING_ACTIVE
[STATE] Self update deferred by guard: FINALIZING
[STATE] Self update rejected: MANAGED_GOOGLE_PLAY_NOT_APPLICABLE
[STATE] Emergency priority active
[STATE] Recovery mode required before normal startup
Detailed recording, storage, DB, Android operation, kiosk policy, auth/security, update và analytics logs được định nghĩa trong các authoritative design pages tương ứng và Logging Requirements.

## 14. Practical Conclusion

State Machine Design sở hữu cross-runtime coordination và guard rule.

Domain-specific state/behavior vẫn thuộc Android Operation, Kiosk Policy, In-App Console, Self Update, Recording, SQLite, Storage và Device Capability Design. Project baseline được reference từ Project Home, Architecture Home và ADR; trang này không restate baseline đó.

## 16. Build 0.1 State Overlay

Login/Auth/Operator Session gate là Not Applicable cho Build 0.1 normal recording/capture.

State Coordinator sử dụng static operator B01OPR / Build 0.1 Operator.

Placeholder không tạo authenticated session hoặc authorization claim.

Storage pre-check failure giữ flow ngoài Recording state.

Runtime storage failure chuyển sang controlled safe-stop/finalization/recovery.

MP4 không chuyển sang BDMA_READY trước MD5 success; missing/mismatch/failure giữ trạng thái blocked/recoverable.

Exact enum/state name cần Technical Review; behavior boundary đã được PM approve.

Target-state authentication flow phía trên vẫn áp dụng cho build sau.