# DCAM Android Development Standard

**Page ID**: 47120580  
**Version**: 14  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120580

---


# DCAM Android Development Standard

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Project-specific Android Development Standard

Version

Approved 1.11

Status

Approved

Approval Scope

Project-specific Android engineering standard; không thay đổi product scope, requirement hoặc approved architecture.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / Cloud Lead / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.3 - Android Development

Target Audience

Android Developers, Java Desktop Developers, Tech Lead, QA, Cloud/WebServer Team

Last Updated

2026-07-14

Related Jira

None

Related Documents

DCAM Architecture Home, DCAM Architecture Delivery Profile, DCAM Concurrency & Threading Model Design, 04 - Device Configuration Requirements, 05 - User & Device Operation Requirements, 09 - System Settings Requirements, 10 - Android Device Operation Requirements, 06 - Cloud Services, Update & Configuration Architecture, DCAM Android Device Owner & Kiosk Policy Design, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, DCAM State Machine Design, DCAM-BDMA Data Contract, DCAM Android Training & Architecture Onboarding, 04 - Application & Module Architecture, DCAM Documentation Governance

## 1. Purpose

Tài liệu này định nghĩa tiêu chuẩn phát triển Android riêng cho dự án DCAM.

Mục tiêu:

Chuẩn hóa cách tổ chức code Android cho DCAM.

Tách business logic khỏi Android SDK, hardware SDK, cloud SDK, device policy APIs và vendor-specific SDK.

Giúp code dễ review, test, debug và bảo trì.

Đảm bảo implementation bám sát các runtime design mới: Android Operation, Android Device Owner & Kiosk Policy, Recording & Capture, Storage, SQLite Database, Security, Device Identity, Remote Config, User Management và Concurrency & Threading Model.

Áp dụng đúng **DCAM Architecture Delivery Profile** để phân biệt MVP implementation rule với full production rule.

Core clarification:

This standard defines the full direction.
MVP implementation must apply only MVP P0 rules and feature-specific rules for features actually in scope.
Do not block Working Recording Slice with future feature checklists.
Threading/concurrency implementation must follow DCAM Concurrency & Threading Model Design.
## 2. Standard Application Architecture

DCAM Android phải đi theo hướng kiến trúc sau:

Activity / Fragment
        ↓
ViewModel
        ↓
UseCase / Interactor
        ↓
Domain Controller / Repository
        ↓
Service Interface
        ↓
Platform Adapter
        ↓
Android SDK / DevicePolicyManager / Hardware SDK / File System / SQLite / Cloud-WebServer Provider
Rule quan trọng:

UI và ViewModel không được gọi trực tiếp Android hardware SDK,
vendor SDK, DevicePolicyManager, file system, SQLite runtime tables,
identity provider, credential/auth storage hoặc cloud/webserver SDK.
MVP interpretation:

Keep the same dependency direction.
But do not create every future service/interface before it has near-term usage.
No abstraction without at least one real implementation.
## 3. Architecture Delivery Profile Rule

Implementation phải theo đúng **DCAM Architecture Delivery Profile**.

Profile

Required Standard

MVP / Phase 1

Apply MVP P0 rules only; focus Recording/Capture/Storage/BDMA/minimal DB/config/log.

Phase 2

Add rules for Device/User foundation, kiosk baseline, remote config foundation, self update foundation when those features enter scope.

Phase 3+

Add rules for Live Streaming, PTT, GPS route, sensor monitoring, AI and advanced features.

Production Hardening

Apply full production checklist and feature-specific checklist.

Working Recording Slice gate:

Open app
    ↓
Check camera/storage permission
    ↓
Record 30s video
    ↓
Stop
    ↓
Finalize file
    ↓
Write minimal DB/config/log output
    ↓
BDMA detects/imports sample media
No new architecture layer/module may be added before this slice is demoable, unless it directly blocks recording, storage, BDMA ingest, device POC or release safety.

## 4. Runtime Implementation References

Sau khi các runtime design chính đã được mở rộng, implementation phải bám theo các runtime owner sau.

Runtime Area

Main Implementation Owner

Authoritative Design

MVP Status

Concurrency/threading, execution lanes, State Coordinator, MainThread boundary, Camera callback contract, File I/O and DB executor boundary

`StateCoordinator`, `RecordingCommandExecutor`, `CameraExecutor`, `FileIoExecutor`, `DbExecutor`, `MainHandler`

DCAM Concurrency & Threading Model Design

MVP Required

App startup, identity restore, provisioning state, kiosk policy verification, login screen, session lifecycle, foreground service, permission lifecycle, module registry, safe mode

`AppRuntimeOrchestrator`, `DeviceIdentityManager`, `ProvisioningManager`, `KioskPolicyManager`, `OperatorSessionManager`, `RuntimeModuleRegistry`, `ForegroundServiceHost`, `RecoveryManager`

DCAM Android Operation Design

MVP uses minimal `AppRuntimeBootstrap`; full orchestrator Phase 2+

Android Device Owner / DPC policy, Lock Task, User Restrictions, Home/Launcher policy, Maintenance Mode

`DevicePolicyStateManager`, `KioskPolicyManager`, `LockTaskController`, `UserRestrictionPolicyManager`, `HomeAppPolicyManager`, `MaintenanceModeController`, `PolicyAuditLogger`

DCAM Android Device Owner & Kiosk Policy Design

MVP Lock Task POC only if required; full stack Phase 2+

Device identity and serial/config file behavior

`DeviceIdentityManager`, `DeviceConfigRepository`, `CsonConfigStore`

04 - Device Configuration Requirements

MVP minimal local serial/config only

WebServer/Firebase identity and Web Portal provisioning provider boundary

`CloudDeviceIdentityService`, `ProvisioningService`, `RemoteConfigProvider`

06 - Cloud Services, Update & Configuration Architecture

Phase 2+

Remote config cache/apply behavior

`RemoteConfigRepository`, `SettingsRepository`, `ConfigApplyCoordinator`

09 - System Settings Requirements + SQLite Database Design

Phase 2+

User/operator management and login policy

`UserRepository`, `UserManagementUseCase`, `AuthMethodManager`, `OperatorSessionManager`

05 - User & Device Operation Requirements

Phase 2+ unless MVP explicitly enables auth

Recording/capture session lifecycle, operator gate and emergency evidence flow

`RecordingController`

DCAM Recording & Capture Design

MVP Required with MVP-supported guards

Android-side storage mechanics, temp/final move, storage recovery and BDMA readiness

`StorageService`, `StorageRootResolver`, `FinalizationManager`, `StorageRecoveryScanner`

DCAM Storage Design

MVP Required

SQLite schema, transaction, identity/config cache tables, user/auth/session tables, write-back, external change detection and DB recovery

`DatabaseService`, repositories

DCAM SQLite Database Design

MVP minimal DB; full DB Phase 2+

Device identity security, credential/auth security, provisioning security, kiosk policy security, emergency override auditability and encryption direction

`SecurityService`, `CredentialHasher`, `AuthMethodManager`, `MaintenanceCredentialService`

DCAM Security & Encryption Design

MVP safe logging/basic protection; full security Phase 2+

Feature eligibility and runtime pruning

`DeviceCapabilityManager`, `FeatureEligibilityEvaluator`

DCAM Device Capability & Feature Eligibility Design

Phase 2+ / MVP simple checks

Cross-runtime guard rules

`StateMachineCoordinator`

DCAM State Machine Design + DCAM Concurrency & Threading Model Design

Phase 2+ / MVP local recording-storage state

## 5. Service Interface Rule

Mọi external/platform capability phải được truy cập thông qua Service Interface.

Recommended interfaces:

Interface

Responsibility

Runtime Design Reference

MVP Status

`StateCoordinator` / `RecordingCommandExecutor`

Serialize command/event/state transition cho recording/capture/finalization và Safe Window Guard.

Concurrency & Threading Model Design

MVP Required

`CameraExecutor` / `CameraHandlerThread`

Isolate camera SDK operation và callback mapping.

Concurrency & Threading Model Design + Recording & Capture Design

MVP Required

`FileIoExecutor`

Isolate temp/final/checksum/log/CSON file operation.

Concurrency & Threading Model Design + Storage Design

MVP Required

`DbExecutor`

Isolate SQLite read/write/transaction boundary.

Concurrency & Threading Model Design + SQLite Database Design

MVP Required

`DevicePolicyStateService` / `DevicePolicyStateManager`

Detect Device Owner / approved DPC state and policy authority.

Kiosk Policy Design + Android Operation

Phase 2+

`KioskPolicyService` / `KioskPolicyManager`

Apply/verify production kiosk profile.

Kiosk Policy Design

Phase 2+

`LockTaskService` / `LockTaskController`

Verify allowlist, start/stop/recover Lock Task Mode.

Kiosk Policy Design

MVP conditional POC / Phase 2+ full

`UserRestrictionService` / `UserRestrictionPolicyManager`

Apply/verify/remove approved User Restrictions.

Kiosk Policy Design

Phase 2+

`MaintenanceModeService` / `MaintenanceModeController`

Enter/exit authorized Maintenance Mode.

Kiosk Policy Design + Security Design

Phase 2+

`DeviceIdentityService` / `DeviceIdentityManager`

Resolve local identity, restore cloud identity by `serial_number` / `serial_lookup/{serial_number}`, manage SD Identity File sync state and provisioning state.

Android Operation + Cloud Architecture + SQLite Design

MVP local minimal / Phase 2+ full

`ProvisioningService` / `ProvisioningManager`

Quản lý provisioning-required state, QR display data, polling và provisioning result.

Android Operation + Cloud Architecture

Phase 2+

`RemoteConfigService` / `RemoteConfigProvider`

Fetch effective config theo `dcam_cloud_device_id`.

Cloud Architecture + System Settings

Phase 2+

`ConfigApplyCoordinator`

Validate và apply/defer config theo runtime guard.

System Settings + State Machine Design

Phase 2+

`DeviceConfigStore` / `CsonConfigStore`

Read/write `dcam_config.cson` chỉ cho device information.

Device Configuration Requirements + Data Contract

MVP Required if CSON is generated

`CameraService`

Camera open/close/preview/recording adapter.

Recording & Capture Design

MVP Required

`RecordingController`

Owner quyết định authoritative cho recording session.

Recording & Capture Design

MVP Required

`OperatorSessionService` / `OperatorSessionManager`

Create/restore/invalidate/read active operator session.

Android Operation + SQLite Database Design

Phase 2+ unless MVP auth enabled

`AuthMethodService` / `AuthMethodManager`

Dispatch Password, pattern, face, QR và NFC auth method.

Security & Encryption Design

Phase 2+

`StorageService`

Quản lý storage root/temp/final/recovery mechanics.

Storage Design

MVP Required

`DatabaseService`

Boundary cho SQLite transaction, query và migration.

SQLite Database Design

MVP Required minimal

`DeviceCapabilityService`

Detect hardware/platform/performance/policy capability.

Device Capability Design

MVP simple checks / Phase 2+ full

`LocationService`

GPS/location adapter.

Sensor & Location Monitoring Design

MVP availability/basic / Phase 3 route

`RealtimeAnalyticsService`

Realtime analytics runtime adapter.

Realtime AI Detection Design

Phase 3+ / Future

`UpdateService`

Thực thi Play Store / Self Update.

Self Update Design

Phase 2+

`LogService`

Ghi local diagnostics logging.

Logging Requirements

MVP Required

`SecurityService`

Xử lý credential, identity, encryption/signature/key-related functions.

Security & Encryption Design

Phase 2+ full

`FileIntegrityService`

Checksum/hash/validation theo Data Contract.

Storage Design / Data Contract

MVP if `.md5` supported

Not allowed:

Activity -> DevicePolicyManager
ViewModel -> DevicePolicyManager
UI -> startLockTask/stopLockTask directly
UI -> setLockTaskPackages/addUserRestriction/clearUserRestriction directly
Activity -> VendorCameraSdk
ViewModel -> SQLiteDatabase
ViewModel -> cloud/webserver SDK
ViewModel -> credential table / auth storage
UseCase -> Android file path manipulation
UseCase -> original Android system identifier logging
UseCase -> auth values in logs
Camera callback -> SQLiteDatabase
Camera callback -> UI update directly
Camera callback -> runtime state mutation directly
Sensor Runtime -> Camera SDK
Realtime AI Runtime -> StorageService final media write
BDMA helper -> active operator_session lifecycle fields without approved contract
RecordingController bypassing OperatorSessionProvider for normal recording when auth is enabled
Remote config code writing operational settings or kiosk settings into dcam_config.cson
Allowed direction:

UI/ViewModel
    ↓
UseCase
    ↓
Domain Controller / Repository
    ↓
Service Interface
    ↓
Adapter Implementation
## 6. MVP P0 Implementation Rules

MVP PRs must satisfy these rules. These are mandatory even before full platform architecture starts.

Rule ID

Rule

MVP-DEV-001

UI/ViewModel must not call Camera SDK, SQLite, physical file path operations or Android policy APIs directly.

MVP-DEV-002

Recording and capture commands must go through `RecordingController` or equivalent MVP controller.

MVP-DEV-003

Camera SDK calls must go through `CameraService` or equivalent adapter.

MVP-DEV-004

Temp/final media write must go through `StorageService` or equivalent storage boundary.

MVP-DEV-005

DB writes must go through `DatabaseService` or repository boundary.

MVP-DEV-006

BDMA output must follow DCAM-BDMA Data Contract for MVP-supported files/fields.

MVP-DEV-007

Logs must not include credentials, secrets, tokens, raw sensitive identifiers or sensitive media data.

MVP-DEV-008

Unsupported optional features must be disabled clearly; do not fake support through UI-only hiding.

MVP-DEV-009

Gradle/module split must follow Architecture Delivery Profile split trigger.

MVP-DEV-010

Every sprint must keep the APK runnable unless the sprint task explicitly fixes a blocker.

MVP-DEV-011

MainThread must not run long camera/file/DB work or wait synchronously for background result.

MVP-DEV-012

Camera callback must emit event only; it must not write DB, update UI or mutate runtime state directly.

MVP-DEV-013

Recording state must be mutated only through single-threaded command/state coordinator.

MVP-DEV-014

File finalization must run through `FileIoExecutor` or equivalent boundary.

MVP-DEV-015

DB writes must run through `DbExecutor` or equivalent Room/raw SQLite executor boundary.

MVP-DEV-016

`BDMA_READY` must be marked only after final file and DB transaction both succeed.

## 7. Feature-specific Implementation Rules

Full rules below become mandatory only when the corresponding feature is in implementation scope.

### 7.1 Device Owner / Kiosk Implementation Rule

Implementation phải tuân thủ **DCAM Android Device Owner & Kiosk Policy Design** when kiosk/Device Owner implementation starts.

Rule

Description

DEV-KIOSK-001

DevicePolicyManager APIs must be isolated behind policy services/managers.

DEV-KIOSK-002

DCAM must detect Device Owner / approved DPC state before normal production field operation.

DEV-KIOSK-003

DCAM must not assume Device Owner state because APK is installed.

DEV-KIOSK-004

Lock Task must not start until package allowlist is verified.

DEV-KIOSK-005

Lock Task entry must be lifecycle-safe and recoverable after reboot/crash/update.

DEV-KIOSK-006

User Restrictions apply/remove must go through `UserRestrictionPolicyManager`, not UI or random use cases.

DEV-KIOSK-007

Maintenance Mode must go through `MaintenanceModeController` and security/audit guard.

DEV-KIOSK-008

Policy failure must return safe domain result/reason code; do not silently continue unrestricted in production profile.

DEV-KIOSK-009

Policy logs must not include maintenance credential, enrollment secret, raw Android identifier or full sensitive config payload.

DEV-KIOSK-010

Update flow must verify policy-safe state and restore Lock Task after restart if required.

DEV-KIOSK-011

Policy/config/update apply must pass Safe Window Guard from Concurrency & Threading Model.

### 7.2 Device Identity / Provisioning Implementation Rule

Implementation phải tuân thủ approved identity và provisioning design when identity/provisioning implementation is in scope.

Rule

Description

DEV-ID-001

Server-side primary device id là `dcam_cloud_device_id`.

DEV-ID-002

`serial_number` là Hardware Identity / primary recovery key.

DEV-ID-003

`serial_lookup/{serial_number}` là approved create/restore lookup path cho cloud identity.

DEV-ID-004

Advertising ID không được dùng làm DCAM identity key.

DEV-ID-005

Original Android system identifier không được ghi vào logs.

DEV-ID-006

Identity resolution phải được implement trong `DeviceIdentityManager`, không nằm trong Activity/ViewModel.

DEV-ID-007

Khi local DB/CSON bị mất nhưng còn app-private serial, app phải restore `dcam_cloud_device_id` bằng `serial_lookup/{serial_number}` trước provisioning.

DEV-ID-008

Nếu server lookup không tìm thấy, app enters `PROVISIONING_REQUIRED`.

DEV-ID-009

Web Portal QR Flow, nếu dùng, là DCAM business provisioning path, không phải Device Owner setup.

DEV-ID-010

Android Enterprise / Device Owner provisioning is separate from Web Portal business provisioning.

DEV-ID-011

`dcam_config.cson` chỉ được update cho device information fields như serial.

DEV-ID-012

`dcam_cloud_device_id`, `serial_number`, SD Identity File sync state và provisioning state phải được persist qua approved DB repository/transaction.

DEV-ID-013

`ANDROID_ID`, `android_id_hash` và `device_lookup/{android_id_hash}` không được dùng trong current production identity/recovery baseline.

### 7.3 Remote Config Implementation Rule

Remote config identity/fetch/cache/apply baseline và initial setting groups đã được approved. Exact field-level payload schema và field names vẫn TBD cho implementation/API design.

Rule

Description

DEV-CONFIG-001

Remote config chỉ được fetch sau khi device identity đã được resolve.

DEV-CONFIG-002

Fetch dùng `dcam_cloud_device_id`, không dùng serial.

DEV-CONFIG-003

Fetched config phải được cache trong `dcam.db` dưới dạng pending/effective config state.

DEV-CONFIG-004

Config phải pass schema/version/allowed-field/capability/policy validation trước khi apply.

DEV-CONFIG-005

Config apply phải đi qua `ConfigApplyCoordinator`; không apply ad-hoc từ UI/network callback.

DEV-CONFIG-006

Config apply phải deferred trong lúc recording, emergency, finalization, DB/storage recovery, policy recovery hoặc unsafe update state.

DEV-CONFIG-007

Invalid config không được replace last valid applied config.

DEV-CONFIG-008

Operational settings và kiosk settings phải lưu trong `dcam.db`, không lưu trong `dcam_config.cson`.

DEV-CONFIG-009

Serial/device-information update chỉ được update `dcam_config.cson` qua `CsonConfigStore`.

DEV-CONFIG-010

Config fetch/apply/reject/defer result phải được log bằng safe reason codes.

DEV-CONFIG-011

Kiosk remote config is requested policy; actual apply belongs to policy managers.

DEV-CONFIG-012

Network callback must not apply config directly; it must emit result to State Coordinator / apply coordinator.

### 7.4 User/Auth Implementation Rule

Implementation phải tuân thủ offline-first user management design when auth/user management is in scope.

Rule

Description

DEV-AUTH-001

Startup login screen và session lifecycle phải được implement qua Android Operation components, không dùng ad-hoc Activity state.

DEV-AUTH-002

Active operator session phải được read qua `OperatorSessionManager` hoặc approved repository/service.

DEV-AUTH-003

Session không có time-based timeout; background/foreground không được logout.

DEV-AUTH-004

Device reboot phải invalidate previous active session và yêu cầu login lại.

DEV-AUTH-005

Normal recording/capture evidence phải yêu cầu active operator session.

DEV-AUTH-006

Emergency recording without login phải dùng system operator `EMERGENCY_OVERRIDE_ADMIN`.

DEV-AUTH-007

Emergency override không được cấp interactive Admin UI access hoặc Maintenance Mode access.

DEV-AUTH-008

User/auth changes từ BDMA sync phải được validate và apply theo DB/Data Contract rules.

DEV-AUTH-009

User management UI phải dùng UseCase/Repository; không mutate SQLite table trực tiếp từ UI.

DEV-AUTH-010

Auth method capability phải được check trước khi enable face, QR hoặc NFC login methods.

DEV-AUTH-011

Operator login must not override missing required Device Owner / kiosk policy in production profile.

DEV-AUTH-012

BDMA user disable/change must not interrupt active recording; apply through safe window rule.

### 7.5 Recording Implementation Rule

Implementation phải tuân thủ **RecordingController authority** và **Concurrency & Threading Model**.

UI / Sensor / Realtime AI / Emergency Event Manager
        ↓ command or event
RecordingController / State Coordinator
        ↓ state-machine + policy guard + operator-auth guard
CameraService + StorageService + DatabaseService
For MVP, only MVP-supported guards are required. Full policy/auth/emergency guards become mandatory when those features enter scope.

Rule

Description

DEV-REC-001

Chỉ `RecordingController` được start/stop/finalize/recover recording session.

DEV-REC-002

UI, Sensor và Realtime AI modules chỉ emit commands/events.

DEV-REC-003

Camera SDK calls phải đi qua `CameraService`/adapter.

DEV-REC-004

Final media write/finalization phải đi qua `StorageService` and `FileIoExecutor` boundary.

DEV-REC-005

DB state changes phải đi qua repository/`DatabaseService` transaction boundary and `DbExecutor`.

DEV-REC-006

Normal recording phải fail fast với `OPERATOR_AUTH_REQUIRED` nếu auth is enabled and không có active operator session.

DEV-REC-007

Emergency recording phải persist emergency override attribution nếu emergency override is in scope.

DEV-REC-008

Media session phải persist operator snapshot tại recording/capture start when auth/operator model is in scope.

DEV-REC-009

Production policy-required failure must block/defer normal recording before camera start when production kiosk policy is in scope.

DEV-REC-010

Camera callback must emit event only; state transition must happen on State Coordinator.

DEV-REC-011

`BDMA_READY` must be marked only after final file and DB transaction both succeed.

## 8. Storage and Database Implementation Rule

Storage và DB code phải tuân thủ runtime design boundaries tương ứng.

Area

Standard

Storage path handling

ViewModel/UseCase không được build physical paths trực tiếp. Dùng `StorageService` / `MediaPathBuilder`.

Temp/final handling

Chỉ Storage layer xử lý temp/staging/final file movement. File I/O lớn chạy trên `FileIoExecutor`.

CSON handling

Chỉ approved config store được update `dcam_config.cson`, và chỉ cho device information.

BDMA_READY

Chỉ set sau khi Storage + DB readiness conditions pass.

DB write

Dùng repositories và transaction helpers; tránh raw SQL rải rác trong app. DB write chạy trên `DbExecutor`.

DB transaction

Không chứa file move/checksum/network/camera wait; transaction phải ngắn.

Identity/provisioning DB write

Dùng `DeviceIdentityRepository` / `ProvisioningRepository` và transaction helpers when identity/provisioning is in scope.

Kiosk policy state DB write

Nếu persisted, dùng approved policy repository/table boundary; không write ad-hoc từ UI.

Remote config DB write

Dùng `RemoteConfigRepository` và setting repositories when remote config is in scope.

User/auth/session DB write

Dùng `UserRepository`, `AuthMethodRepository`, `OperatorSessionRepository` và transaction helpers when auth is in scope.

BDMA write-back

Phải validate schema/version/revision và table ownership.

External change detection

User/auth/settings/import writes từ BDMA phải đi qua external change detection/reload/apply logic.

Recovery

RecoveryManager / StorageRecoveryScanner / DB recovery code phải preserve evidence-like files khi chưa chắc chắn.

## 9. Credential, Identity, Policy and Logging Standard

Area

Standard

Credential storage

Chỉ store approved protected representation/reference theo Security Design.

Maintenance credential storage

Chỉ store approved protected representation; không hardcode/plaintext.

Identity storage

Store `dcam_cloud_device_id` và `serial_number` chỉ qua approved repository.

Android system identifier

Không dùng làm production identity/recovery lookup và không log original value.

Advertising ID

Không dùng làm DCAM identity key.

Auth logs

Chỉ log safe reason codes.

Policy logs

Chỉ log safe policy event/reason codes; không log secrets/credentials.

Config logs

Log revision/result/reason code; không dump sensitive config content.

Threading logs

Log command/event/state transition/defer/timeout bằng safe reason code.

Sensitive values

Không log credentials, tokens, biometric samples, encryption keys, original Android system identifier, maintenance credential, enrollment secret hoặc sensitive media data.

Emergency override log

Log việc emergency override được dùng, kèm safe session/media identifiers.

User sync log

Log sync result/conflict reason, không log sensitive auth values.

Crash/debug logs

Không dump DB rows chứa auth/identity/policy-sensitive data.

## 10. Threading Standard

Authoritative threading rules nằm trong **DCAM Concurrency & Threading Model Design**. Section này chỉ tóm tắt baseline implementation.

Java-first
ExecutorService / HandlerThread
Single-threaded State Coordinator
Camera callback emits event only
DB writes through DbExecutor only
File finalization through FileIoExecutor only
Policy/config/update apply only in Safe Window
MainThread only for UI

Task Type

Standard Threading

UI render / lifecycle light work

MainThread only.

Recording command/state transition

Single-threaded `RecordingCommandExecutor` / `StateCoordinator`.

Camera open/close/start/stop

Dedicated `CameraExecutor` hoặc SDK-required `HandlerThread`.

Camera callback

Chỉ emit event về `StateCoordinator`; không DB write/update UI/mutate state trực tiếp.

File read/write/move/checksum

`FileIoExecutor` hoặc dedicated IO executor.

Finalization pipeline

File operation trên `FileIoExecutor`, DB transaction ngắn trên `DbExecutor`, result quay về `StateCoordinator`.

SQLite operations

`DbExecutor` / Room executor / controlled SQLite executor.

Device policy check/apply

Policy manager serialized executor or main-thread-safe Android API boundary as required; never block UI with long policy work.

Lock Task enter/exit

Lifecycle-safe UI/main thread coordination through `LockTaskController`, not ad-hoc UI calls.

User Restrictions apply/remove

Policy manager serialized execution; defer if runtime guard unsafe.

CSON read/write

IO executor; dùng safe temp/write/replace pattern khi cần.

Identity lookup / remote config fetch

Network executor / provider-managed async mechanism.

Provisioning polling

Background worker; không được block UI.

Remote config validation/apply

Background executor + State Coordinator / ConfigApplyCoordinator + main-thread UI result only.

User sync through ADB / DB write-back apply

Background executor + `DbExecutor`; không được block UI hoặc recording/finalization.

Auth method validation

Không được block UI lâu hơn mức acceptable UX; heavy work nên chạy off main thread.

Recovery scan

Background executor; không được block UI thread.

Network/update download

Network executor / library-managed executor.

UI update

Main thread qua ViewModel/LiveData.

For MVP, mandatory threading focus is camera, file IO, SQLite, single-threaded recording state and UI responsiveness. Other rows apply when the related feature is in scope.

## 11. Error Handling Standard

Errors phải được map theo từng layer.

SDK Exception / DevicePolicy Exception / SQLite Exception / IO Exception / Network Exception / Auth Exception / Timeout
        ↓
Adapter/ServiceError
        ↓
DomainResult
        ↓
ViewModel UI State
        ↓
User-friendly message + diagnostic log
Common categories:

Category

Example

Device Policy Error

Device Owner missing, Lock Task not permitted, restriction unsupported, Maintenance Mode denied.

Identity Error

Local identity missing, lookup failed, server mapping not found.

Provisioning Error

QR expired, provisioning pending timeout, server provisioning rejected.

Remote Config Error

Fetch failed, schema invalid, apply deferred, apply rejected.

Auth Error

Login failed, operator session missing, user disabled, auth method unavailable.

Permission Error

Camera/location/notification/NFC permission missing.

Capability Error

Device cannot run feature, login method or policy path.

Recording Error

Start/stop/finalize failed, operator auth required, policy required.

Storage Error

Root unavailable, full disk, final move failed.

Database Error

DB locked/corrupted/migration failed.

Threading Error

Camera timeout, DB busy timeout, finalization timeout, executor rejected task, unsafe state mutation attempt.

User Sync Error

Conflict, unsupported schema, invalid write-back data.

Update Error

Preconditions not met, invalid APK, policy unsafe.

Runtime Error

Service killed, process death, safe mode.

## 12. Pull Request Review Checklist

### 12.1 MVP PR Checklist

Use this checklist for Phase 1 / Working Recording Slice PRs.

Check

Required

APK remains runnable or PR explicitly fixes a blocker.

Yes

UI/ViewModel does not call Camera SDK, file system path operations, SQLite or DevicePolicyManager directly.

Yes

Recording/capture goes through `RecordingController` or approved MVP equivalent.

Yes

Camera SDK calls go through `CameraService` or approved adapter.

Yes

Camera callback only emits event; it does not write DB, update UI or mutate state directly.

Yes

Recording state mutation happens only on single-threaded coordinator.

Yes

Storage/temp/final handling goes through `StorageService` or approved storage boundary.

Yes

File finalization runs through `FileIoExecutor` or equivalent boundary.

Yes

DB writes go through `DatabaseService` / repository boundary and `DbExecutor`.

Yes

DB transaction does not contain file move/checksum/network/camera wait.

Yes

`BDMA_READY` is marked only after final file and DB update succeed.

Yes

BDMA output follows MVP-supported DCAM-BDMA Data Contract.

Yes

Logs exist for important recording/capture/storage/BDMA/threading success/error paths.

Yes

Sensitive data is not logged.

Yes

No unused future abstraction/module is added without a real implementation and near-term usage.

Yes

Optional/deferred features are clearly disabled or out of scope.

Yes

Long-running camera/file/DB work does not block UI thread.

Yes

Raw SDK/DB/IO errors are mapped before reaching UI.

Yes

Code links to Jira issue where applicable.

Recommended

### 12.2 Full Production PR Checklist

Use this checklist when the related platform/production features are in scope.

Check

Required

UI/ViewModel does not call SDK/file system/SQLite/identity/cloud/credential storage/DevicePolicyManager directly.

Yes

Runtime code uses authoritative controller/service owner.

Yes

Threading/concurrency follows DCAM Concurrency & Threading Model Design.

Yes

Device policy APIs go through `KioskPolicyManager` / policy services.

Feature-specific

Lock Task entry/exit goes through `LockTaskController`.

Feature-specific

User Restrictions go through `UserRestrictionPolicyManager`.

Feature-specific

Maintenance Mode goes through `MaintenanceModeController` and security guard.

Feature-specific

Device identity resolution goes through `DeviceIdentityManager`.

Feature-specific

Provisioning flow goes through `ProvisioningManager` / approved service.

Feature-specific

Code uses `dcam_cloud_device_id` as server device id, not serial.

Feature-specific

Code uses `serial_number` only as Hardware Identity / recovery key, not cloud primary key.

Feature-specific

Code does not use `ANDROID_ID`, `android_id_hash` or `device_lookup/{android_id_hash}` for current production identity/recovery baseline.

Yes

Code does not use Advertising ID as DCAM identity key.

Yes

Original Android system identifier is not logged.

Yes

Remote config fetch/apply goes through approved provider/repository/coordinator.

Feature-specific

Remote config apply checks runtime guard and Safe Window Guard.

Feature-specific

Kiosk requested-policy config does not directly mutate Android policy outside policy manager boundary.

Feature-specific

Operational settings are not written into `dcam_config.cson`.

Yes

Login/session behavior goes through `OperatorSessionManager`.

Feature-specific

Device reboot invalidates previous active session.

Feature-specific

Background/foreground does not logout operator.

Feature-specific

Recording behavior goes through `RecordingController`.

Yes

Normal recording checks active operator session and required production policy state.

Feature-specific

Emergency override uses `EMERGENCY_OVERRIDE_ADMIN`, not real Admin user.

Feature-specific

Media session stores operator snapshot.

Feature-specific

Storage behavior goes through `StorageService`.

Yes

DB writes go through repository/transaction boundary.

Yes

BDMA user sync/write-back validates schema/version/revision and table ownership.

Feature-specific

Optional modules use Feature Eligibility before runtime start.

Feature-specific

Sensor/AI modules do not control recording directly.

Feature-specific

Hardware SDK calls are isolated in adapters and serialized when needed.

Yes

Long-running work does not block UI thread.

Yes

Raw SDK/DB/IO/auth/config/policy errors are mapped before reaching UI.

Yes

Logs exist for important success/error/recovery/identity/provisioning/config/auth/sync/policy/threading paths.

Yes

Sensitive data is not logged.

Yes

Code links to Jira issue where applicable.

Recommended

### 12.3 Feature-specific Checklist Rule

Do not fail a Phase 1 recording/storage PR because it does not implement Self Update, Remote Config, Maintenance Mode, Play Store fallback, AI Detection or full Kiosk Policy.
Fail it only if it violates MVP P0 rules, Concurrency & Threading Model rules, or creates a blocker for the working recording/storage/BDMA slice.
## 13. Practical Conclusion

Rule quan trọng nhất khi phát triển DCAM Android là:

UI và business logic không bao giờ được depend trực tiếp vào hardware SDK,
Android platform APIs, DevicePolicyManager, SQLite internals, file paths,
identity provider, credential storage hoặc cloud/webserver SDKs.
Rule quan trọng nhất về concurrency/threading là:

Only the coordinator mutates runtime state.
Specialized executors do work and return results.
No executor blocks another executor while holding state or DB transaction.
Runtime implementation phải tuân thủ các source-of-truth documents đã được mở rộng:

DCAM Architecture Delivery Profile
DCAM Concurrency & Threading Model Design
Android Operation Design
Android Device Owner & Kiosk Policy Design
Recording & Capture Design
Storage Design
SQLite Database Design
Security & Encryption Design
State Machine Design
Device Capability Design
Cloud Services / Update / Configuration Architecture
System Settings Requirements
DCAM-BDMA Data Contract
MVP implementation rule:

Apply MVP P0 rules first.
Apply Concurrency & Threading Model from day one.
Deliver Working Recording Slice first.
Add full production rules only when the relevant feature enters scope.
Cách tổ chức này giúp DCAM ổn định, dễ test và dễ maintain trên nhiều BodyCamera hardware models khác nhau, đồng thời hỗ trợ device identity recovery, Web Portal provisioning, Android dedicated-device/kiosk policy, remote config baseline, offline user management và operator-authenticated recording mà không làm chậm MVP recording/storage/BDMA delivery.