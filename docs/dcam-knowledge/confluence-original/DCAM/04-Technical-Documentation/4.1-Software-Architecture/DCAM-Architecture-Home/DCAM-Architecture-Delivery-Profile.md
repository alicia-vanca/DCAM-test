# DCAM Architecture Delivery Profile

**Page ID**: 50626744  
**Version**: 6  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50626744

---


# DCAM Architecture Delivery Profile

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Architecture Delivery Profile / MVP Implementation Architecture Guardrail

Version

1.3

Status

Approved for Build DCAM MVP Internal Build 0.1

Approval Scope

Architecture guardrails cho Working Recording Slice; không phải Production/fleet approval

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

DCAM Architecture Home

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Team, Factory, Stakeholders

Last Updated

2026-07-13

Related Jira

None

Related Documents

DCAM MVP Scope, DCAM 9-Month Development Plan, 04 - Application & Module Architecture, DCAM Android Development Standard, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM-BDMA Data Contract, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này định nghĩa **Architecture Delivery Profile** cho DCAM để tránh hiểu nhầm giữa:

Target Architecture
        ≠
MVP Implementation Architecture
Các tài liệu kiến trúc hiện tại mô tả **Target Architecture** đủ lớn cho production và future phases. Tuy nhiên, implementation phải được triển khai theo từng phase; không được yêu cầu team implement toàn bộ **Target Architecture** trước khi có working recording.

Mục tiêu của trang này:

Bảo vệ tốc độ delivery của team nhỏ.

Tránh analysis-paralysis do tạo quá nhiều abstraction layer quá sớm.

Xác định rõ module/component/state/rule nào bắt buộc trong MVP.

Xác định rõ module nào defer sang Phase 2 / Phase 3+.

Đảm bảo mỗi sprint tạo ra APK chạy được và có vertical slice demo được.

## 2. Core Delivery Principle

Working recording trước.
Architecture phát triển từ runnable vertical slices.
Không tạo abstraction nếu chưa có ít nhất một real implementation.
MVP đầu tiên của DCAM tập trung vào luồng:

```
Record / Capture → Save Media → Expose Contract Data → BDMA Ingest
```

Do đó Phase 1 không được biến thành nỗ lực implement toàn bộ platform architecture bao gồm AI, remote config, self update, full kiosk policy, full in-app console, full feature eligibility hoặc full state machine.

## 3. MVP Implementation Architecture

### 3.1 MVP Module Set

MVP chỉ cần module set tối thiểu sau:

Module / Area

MVP Responsibility

Required in MVP

Recording / Capture

Start/stop video recording, capture image và quản lý recording/capture state.

Yes

Storage

Xử lý temp/final file, folder structure, media naming và recovery basics.

Yes

BDMA Contract Export

Expose media/config/db/log outputs tương thích với DCAM-BDMA Data Contract.

Yes

SQLite Minimal Local State

Lưu minimal media/session/device/config/log state cần cho MVP và BDMA.

Yes

Device Config / Local Identity

Duy trì `serial_number`, minimal `dcam_config.cson` device information và local identity cache khi cần.

Yes

Logging

Local diagnostics logs cho recording/capture/storage/BDMA/debug.

Yes

Basic Device Status

Battery, storage capacity, GPS availability khi thiết bị hỗ trợ.

Yes

Basic Kiosk POC

Chỉ dùng Lock Task nếu cần cho device validation; full Device Owner policy stack không bắt buộc cho first recording slice.

Conditional

### 3.2 Deferred from MVP

Các module/capability sau **không thuộc MVP implementation path** trừ khi được approve bằng explicit change control:

Deferred Area

Target Phase

Notes

Full Device Owner / Kiosk Policy stack

Phase 2+

MVP có thể chỉ chạy Lock Task POC. Full policy recovery/user restrictions/maintenance mode thuộc phase sau.

Full In-App Console advanced

Phase 2+

MVP có thể chỉ giữ Record/Settings debug screen đơn giản.

Advanced User Management

Phase 2+

MVP có thể dùng no-login/basic operator placeholder nếu test scope chấp nhận.

Remote Config fetch/cache/apply

Phase 2+

Không được block recording slice.

Self Update / APK update

Phase 2+

Không được block recording slice.

Play Store fallback

Phase 2+ / Conditional

Chỉ áp dụng nếu device có GMS/Play Store và approved process.

Device Capability & Feature Eligibility full engine

Phase 2+

MVP có thể dùng static capability flags hoặc simple checks.

Full State Machine Coordinator

Phase 2+

MVP có thể dùng local recording/storage state machine nhỏ.

Sensor Monitoring advanced

Phase 3+

Không bắt buộc cho first MVP.

Realtime AI Detection

Phase 3+ / Future

Không bắt buộc cho first MVP.

Live Streaming / PTT

Phase 3+

Không bắt buộc cho first MVP.

Full GPS route tracking

Phase 3+

MVP chỉ ghi nhận GPS availability/basic location nếu có.

## 4. Gradle / Code Organization Profile

### 4.1 MVP Gradle Module Limit

MVP không nên bắt đầu với khoảng 35 Gradle modules.

Recommended MVP Gradle structure:

:app
:core
:media
:storage
:bdma-contract
Initial structure nhỏ hơn cũng được chấp nhận:

:app
:core
Bên trong modules, ưu tiên dùng packages trước:

com.dcam.recording
com.dcam.capture
com.dcam.storage
com.dcam.database
com.dcam.bdma
com.dcam.identity
com.dcam.logging
com.dcam.device
### 4.2 Rule for Splitting a Gradle Module

Một package chỉ nên tách thành Gradle module riêng khi có ít nhất một điều kiện đúng:

Split Trigger

Description

Lifecycle ownership

Area có lifecycle rõ ràng và độc lập với app shell.

Dependency isolation

Area có platform/vendor/cloud dependencies cần được isolate.

Parallel team ownership

Nhiều developers cần ownership boundaries rõ ràng.

Testing boundary

Area cần được test độc lập và lặp lại nhiều lần.

Dependency-cycle prevention

Giữ trong cùng module tạo nguy cơ dependency cycle.

Stable interface exists

Đã có ít nhất một real implementation phía sau interface.

Không tạo Gradle module chỉ vì một future document có liệt kê một future capability.

## 5. Runtime Component Profile

### 5.1 MVP Runtime Components

MVP runtime nên giữ dưới khoảng 10 core runtime components:

AppRuntimeBootstrap
RecordingController
CameraService
StorageService
DatabaseService
BdmaExportService
DeviceConfigStore
DeviceStatusProvider
LogService
Optional chỉ dùng khi current device POC cần:

```
BasicLockTaskController
```

### 5.2 Not Required for MVP

Các components sau là target architecture hoặc later-phase components, không phải MVP blockers:

RuntimeModuleRegistry
FeatureEligibilityEvaluator
ConfigApplyCoordinator
SelfUpdateCoordinator
MaintenanceModeController
ApprovedMaintenanceTargetController
RealtimeAnalyticsService
SensorEventRouter
Full StateMachineCoordinator
Full KioskPolicyManager recovery stack
PlayStoreFallbackService
## 6. Runtime State Profile

### 6.1 MVP State Set

MVP nên dùng state set nhỏ:

BOOTSTRAPPING
READY
RECORDING
CAPTURING
FINALIZING
STORAGE_BLOCKED
ERROR_RECOVERY
### 6.2 Later-phase States

Các states sau vẫn là target states hợp lệ nhưng không được block first MVP implementation:

PROVISIONING_REQUIRED
POLICY_REQUIRED
MAINTENANCE_MODE
UPDATE_PENDING
REMOTE_CONFIG_PENDING
CAPABILITY_DEGRADED
KIOSK_RECOVERY
## 7. Dependency Rule Profile

### 7.1 MVP P0 Rules

Chỉ các dependency rules sau là mandatory cho MVP:

Rule ID

Rule

MVP-DEP-001

UI/ViewModel không được gọi trực tiếp Camera SDK, SQLite, physical file path operations hoặc Android policy APIs.

MVP-DEP-002

Recording và capture commands phải đi qua `RecordingController` hoặc MVP controller tương đương.

MVP-DEP-003

Camera SDK calls phải đi qua `CameraService` hoặc adapter tương đương.

MVP-DEP-004

Temp/final media write phải đi qua `StorageService` hoặc storage boundary tương đương.

MVP-DEP-005

DB writes phải đi qua `DatabaseService` hoặc repository boundary.

MVP-DEP-006

BDMA output phải tuân theo DCAM-BDMA Data Contract cho files/fields được MVP support.

MVP-DEP-007

Logs không được chứa credentials, secrets, tokens, raw sensitive identifiers hoặc sensitive media data.

MVP-DEP-008

Unsupported optional features phải được disabled rõ ràng; không fake support bằng cách chỉ hide UI.

### 7.2 Phase 2+ Rules

Full dependency rule set trong **04 - Application & Module Architecture** và **DCAM Android Development Standard** chỉ trở thành mandatory khi feature tương ứng đi vào implementation scope.

Examples:

Rule Area

Becomes Mandatory When

DevicePolicyManager isolation

Khi Kiosk/Device Owner implementation bắt đầu.

Maintenance Mode controller

Khi Controlled Maintenance Mode implementation bắt đầu.

SelfUpdateCoordinator

Khi APK self-update implementation bắt đầu.

ConfigApplyCoordinator

Khi Remote config apply implementation bắt đầu.

FeatureEligibilityEvaluator

Khi Dynamic feature pruning implementation bắt đầu.

RuntimeModuleRegistry

Khi Runtime module lifecycle trở nên cần thiết.

## 8. Working Recording Slice Gate

Trước khi mở rộng platform architecture, DCAM phải pass một working vertical slice:

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
### 8.1 Working Recording Slice Acceptance

ID

Acceptance Criteria

WRS-001

APK build và chạy được trên selected BodyCamera.

WRS-002

App record được ít nhất 30 giây video.

WRS-003

App capture được image.

WRS-004

Media file được finalize dưới expected DCAM media path.

WRS-005

File naming tuân theo DCAM-BDMA Data Contract rule được MVP support.

WRS-006

Minimal `dcam_config.cson`, `dcam.db` và `logs.txt` được tạo hoặc cập nhật theo MVP scope.

WRS-007

BDMA detect và import được sample media từ device.

WRS-008

App không crash trong happy-path recording/capture flow.

WRS-009

Known limitations được document rõ ràng thay vì bị che sau future abstractions.

### 8.2 Expansion Gate

Không thêm architecture layer/module mới trước khi Working Recording Slice demo được,
trừ khi layer/module đó trực tiếp block recording, storage, BDMA ingest, device POC hoặc release safety.
## 9. Phase Delivery Profile

Area

MVP / Phase 1

Phase 2

Phase 3+

Recording

Required

Hardened

Hardened

Capture

Required

Hardened

Hardened

Storage

Required

Recovery improved

Hardened

BDMA Contract

Basic ingest compatibility

Full contract coverage

Hardened

SQLite

Minimal tables

Full identity/user/settings/media tables

Hardened

Device Identity

Local serial/config minimal

Cloud identity/provisioning restore

Hardened

User/Auth

No-login/basic placeholder hoặc minimal operator nếu required

Basic offline user/operator

Advanced methods

Kiosk

Lock Task POC nếu cần

Device Owner/DPC baseline

Hardened recovery

In-App Console

Minimal debug/settings screen

Setting hub/basic modules

Advanced console

Remote Config

Not required

Fetch/cache

Validate/apply policy

Self Update

Not required

Basic APK update

Hardened update

Play Store fallback

Not required

Conditional POC

Conditional

Device Capability

Simple checks/static flags

Capability profile

Dynamic pruning

State Machine

Small recording/storage state

Cross-runtime guard

Full coordination

Sensor Monitoring

Not required

Optional basic

Advanced

AI Detection

Not required

Not required

Optional/future

Live Streaming/PTT

Not required

Not required

Phase 3+

## 10. Sprint Delivery Rules

Rule

Description

DEL-001

Mỗi sprint phải tạo ra một runnable APK.

DEL-002

Mỗi architecture task phải support một demoable vertical slice.

DEL-003

Không tạo abstraction nếu chưa có ít nhất một real implementation.

DEL-004

Không split module nếu chưa có ownership rõ ràng và near-term usage.

DEL-005

Không future feature dependency nào được block critical recording path.

DEL-006

Deferred modules phải được mark deferred rõ ràng, không half-implemented âm thầm.

DEL-007

Design pages có thể mô tả Target Architecture, nhưng Jira implementation tasks phải đi theo current delivery profile.

## 11. Relationship to Other Documents

Document

Relationship

DCAM MVP Scope

Owns MVP product scope và acceptance; trang này map scope đó sang implementation architecture.

DCAM 9-Month Development Plan

Owns schedule và phase plan; trang này giới hạn architecture size theo từng phase.

04 - Application & Module Architecture

Owns Target Architecture và module direction; trang này định nghĩa subset nào active theo từng phase.

DCAM Android Development Standard

Owns implementation rules; trang này định nghĩa rule nào mandatory trong MVP và rule nào dành cho later phases.

DCAM Recording & Capture Design

Owns recording behavior; MVP phải implement phần này trước bằng một vertical slice nhỏ.

DCAM Storage Design

Owns storage mechanics; MVP chỉ implement storage behavior cần cho recording/capture/BDMA.

DCAM SQLite Database Design

Owns DB direction; MVP implement minimal tables/state trước.

DCAM-BDMA Data Contract

Owns contract behavior; MVP phải tuân thủ fields/files được MVP support trong contract.

## 12. Practical Conclusion

DCAM nên giữ **Target Architecture**, nhưng implementation phải triển khai theo phase.

Target Architecture vẫn hợp lệ.
MVP Implementation Architecture được cố ý giữ nhỏ hơn.
Working recording/storage/BDMA slice phải đi trước.
Các managers/coordinators/modules bổ sung chỉ trở thành mandatory khi feature tương ứng đi vào scope.
Recommended Phase 1 limits:

≤ 5 Gradle modules
≤ 8 logical modules
≤ 10 runtime components
≤ 7 runtime states
≤ 8 P0 dependency rules
1 working recording/storage/BDMA vertical slice trước khi platform expansion
## 13. Build 0.1 Controlled Delivery Profile

Area

Required Build 0.1 Subset

Reference Device

NCC-036V / Android 12 / API 31 / 877AOOAKN1_RK2_V009; Pending Device POC.

Camera

Android platform Camera API qua CameraService; vendor SDK Not Applicable; Camera1/Camera2 chờ POC.

Storage

Internal only; no fallback; failed pre-check blocks start; runtime failure safe-stop/finalize if possible.

Finalization

MP4 finalized trước; async MD5; BDMA_READY chỉ sau valid MD5.

Operator

No login UI; B01OPR / Build 0.1 Operator; technical traceability only.

Media Filename

Data Contract-owned mapping: DEVICE_TOKEN là validated serial_number snapshot 6–10 ký tự; OPERATOR_TOKEN là B01OPR đúng 6 ký tự; không underscore hoặc silent truncation.

Device Status

Battery level, Internal free storage, GPS Available/Unavailable/Unsupported.

Coverage

Ít nhất một identifiable physical reference device; không đại diện Production/fleet/multi-model readiness.

Build 0.1 phải tuân theo Decision Brief và Release & Build Applicability Matrix khi target-state design rộng hơn profile này.