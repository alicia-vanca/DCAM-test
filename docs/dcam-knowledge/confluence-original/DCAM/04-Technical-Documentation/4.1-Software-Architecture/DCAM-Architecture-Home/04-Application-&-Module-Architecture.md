# 04 - Application & Module Architecture

**Page ID**: 47218698  
**Version**: 20  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47218698

---


# 04 - Application & Module Architecture

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Application Architecture

Version

Approved 1.15

Status

Approved

Approval Scope

Module boundaries with approved Build 0.1 reference/camera/checksum constraints, approved DDMP integration direction and mandatory GMS-free Android runtime baseline; DDMP implementation remains Phase 2+/POC-gated.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, AI/ML Engineer, QA

Last Updated

2026-08-26

Related Jira

None

Related Documents

DCAM Architecture Home, DCAM Architecture Delivery Profile, 05 - User & Device Operation Requirements, 03 - Android Platform & Compatibility Strategy, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Self Update Design, 05 - Data, Storage & BDMA Architecture, 06 - Cloud Services, Update & Configuration Architecture, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, DCAM State Machine Design, DCAM Device Capability & Feature Eligibility Design, DCAM Sensor & Location Monitoring Design, DCAM Realtime AI Detection Design, DCAM Android Development Standard, DCAM Device Owner ADR, DCAM Device Identity ADR, DDMP 01 Hybrid System Architecture, DDMP 03 Management API / BFF, DDMP 06 Device Integration Contracts, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này mô tả kiến trúc ứng dụng và module direction của DCAM.

DCAM dùng kiến trúc layered/module-based để các capability như recording, emergency, user/operator authentication, dedicated-device/kiosk policy, in-app console, controlled maintenance, Self Update, monitoring, realtime analytics, storage và BDMA có thể bật/tắt/degrade theo capability từng thiết bị BodyCamera.

Current baseline:

No external DPC/EMM, Android Management API or Managed Google Play owns Android privileged policy.
DCAM = the only local Device Owner / DPC if target firmware supports it.
Headwind Client = Application mode only; it does not own DPC, HOME/launcher, Lock Task or privileged update policy.
BFF = desired-state/API boundary; Headwind Community = limited fleet control-plane.
Primary update path = BFF-authorized DCAM Self Update from Cloudflare R2/CDN.
No Google Play Store/Managed Google Play update or maintenance fallback is permitted in the production profile.
Trang này là **target architecture-level map**. Nó không được hiểu là danh sách bắt buộc phải implement toàn bộ trong MVP.

Implementation phải tuân theo **DCAM Architecture Delivery Profile**:

Target Architecture
        ≠
MVP Implementation Architecture

Working recording/storage/BDMA slice must come first.
Additional modules/managers/coordinators become mandatory only when their feature enters scope.
Runtime details thuộc các technical design authoritative tương ứng.

## 2. Target Architecture vs Delivery Profile

Trang này giữ target architecture để đảm bảo DCAM có hướng mở rộng dài hạn. Tuy nhiên implementation được chia theo phase.

Profile

Purpose

Implementation Meaning

Target Architecture

Mô tả kiến trúc đầy đủ cho production/future phases.

Không bắt buộc implement toàn bộ trong MVP.

MVP Implementation Architecture

Subset tối thiểu để có working recording/capture/storage/BDMA.

Bắt buộc cho Phase 1.

Phase 2 Platform Architecture

Mở rộng Device/User, BDMA, security, kiosk, remote config/self-update foundation.

Chỉ bật sau khi working recording slice pass.

Phase 3+ Advanced Architecture

Live Streaming, PTT, GPS route, sensor/AI, advanced analytics.

Future/advanced scope.

Architecture delivery guardrail:

No new architecture layer/module may be added before Working Recording Slice is demoable,
unless it directly blocks recording, storage, BDMA ingest, device POC or release safety.
### 2.1 MVP Module Set

MVP implementation should focus on:

Recording / Capture
Storage
BDMA Contract Export
SQLite minimal local state
Device Config / Local Identity
Logging
Basic Device Status
Basic Lock Task POC only if required
Deferred from MVP:

AI Detection
Sensor Monitoring advanced
Remote Config apply
Self Update
Play Store fallback
Full In-App Console advanced
Full Kiosk Policy stack
Advanced User Management
Full Feature Eligibility engine
Full Runtime Module Registry
Full State Machine Coordinator
### 2.2 Phase Mapping

Module / Area

MVP / Phase 1

Phase 2

Phase 3+

recording

Required

Hardened

Hardened

capture

Required

Hardened

Hardened

storage

Required

Recovery improved

Hardened

database

Minimal

Full identity/user/settings/media tables

Hardened

bdma-integration

Basic import compatibility

Full contract coverage

Hardened

device identity

Local serial/config minimal

Cloud identity/provisioning restore

Hardened

auth-session

No-login/basic placeholder or minimal operator if required

Basic offline user/operator

Advanced auth methods

user-management

Not required

Basic

Advanced

kiosk-policy

Lock Task POC if needed

Device Owner/DPC baseline

Hardened recovery

in-app-console

Minimal debug/settings screen

Setting hub/basic modules

Advanced console

remote-config

Not required

Fetch/cache

Validate/apply policy

ddmp-device-management-sync

Not required

BFF enrollment/sync/desired-state/ACK; Headwind coexistence POC

Hardened reconnect, reconciliation and release rollout

self-update

Not required

Basic APK update

Hardened update

play-store-fallback

Not required

Conditional POC

Conditional

device-capability

Simple checks/static flags

Capability profile

Dynamic pruning

feature-eligibility

Not required as full engine

Basic evaluator

Full runtime pruning

state-machine

Small recording/storage state

Cross-runtime guard

Full coordination

sensor-monitoring

Not required

Optional basic

Advanced

realtime-ai-detection

Not required

Not required

Optional/future

live streaming / PTT

Not required

Not required

Phase 3+

## 3. Recommended Architecture

UI Layer
    ↓
ViewModel / Presentation State
    ↓
Use Case / Interactor
    ↓
Domain Controller / Repository
    ↓
Service Interface
    ↓
Platform Adapter / Data Source Interface
    ↓
Android SDK / DevicePolicyManager / Camera / Sensors / GPS / Storage / SQLite / Auth Hardware / Cloud / Runtime Package
Core rule:

Business logic must depend on interfaces.
Platform-specific, device-policy-specific, auth-specific and hardware-specific access must be isolated behind adapters.
Runtime orchestration must follow Android Operation Design.
Detailed Device Owner / Lock Task / User Restrictions behavior must follow Kiosk Policy Design.
In-app console and controlled maintenance UX must follow In-App Console Design.
Self Update / APK update must follow Self Update Design.
MVP interpretation:

Use the same direction, but do not create every target service/manager upfront.
A service/interface should exist only when it has near-term usage and at least one real implementation.
## 4. Module Direction

Module

Purpose

Status

`ui`

Screens và UI state, bao gồm Record/Live View, Setting hub, login, policy-required/degraded, user management and maintenance screens.

Target Architecture / MVP minimal UI only

`domain`

Use cases, domain models và interfaces.

Draft Implementation

`android-operation`

Runtime orchestrator, policy verification, login screen, session lifecycle, foreground service, permission lifecycle, module registry, Self Update runtime và recovery.

Target Architecture / Phase 2+ except MVP bootstrap

`android-kiosk-policy`

DCAM-as-DPC/local Device Owner state, Lock Task, User Restrictions, Home/Launcher policy, Controlled Maintenance Mode and policy recovery.

Phase 2+ / MVP Lock Task POC only if required

`in-app-console`

Record/Live View default navigation, Setting hub, child module registry, back behavior and role/capability/policy-based console visibility.

Phase 2+ / MVP minimal screen only

`app-operation-settings`

Video resolution, FPS/quality, bitrate/profile, audio, pre/post-record and file split settings with safe apply guards.

Phase 2+ / MVP fixed or minimal settings

`device-system-settings`

Controlled proxy for approved USB/Wi-Fi/GPS/device/system settings.

Phase 2+

`file-media-console`

Read-only Storage Dashboard, File Manager and Media Viewer for finalized media visibility.

Phase 2+

`maintenance-access`

Maintenance Password Gate, Admin/Maintenance authorization, approved target enforcement and session timeout.

Phase 2+

`user-management`

Quản lý user/operator profile, Admin-only user settings and local admin flows.

Phase 2+

`auth-session`

Operator login methods, Login Settings, session restore/invalidate và emergency override identity access.

Phase 2+ / MVP minimal operator placeholder if required

`state-machine`

Cross-runtime guard và coordination rules, bao gồm policy guard, operator auth guard, maintenance guard and update guard.

Target Architecture / MVP small recording-storage state only

`device-capability`

Detect hardware/platform/performance/auth-method/policy/update capability và tạo Device Capability Profile.

Phase 2+ / MVP simple checks

`feature-eligibility`

Evaluate feature runtime eligibility và prune unsupported flows.

Phase 2+ / not full MVP engine

`camera`

Camera access abstraction; lựa chọn cụ thể CameraX/Camera2/vendor vẫn cần POC.

MVP Required

`recording`

RecordingController, policy guard, operator gate, recording/capture session và emergency evidence flow.

MVP Required, with only MVP-supported guards

`capture`

Image/audio capture workflow; evidence capture dùng operator gate.

MVP Required

`emergency`

Emergency Event Manager, event routing và emergency override path.

Phase 2+ unless required by MVP acceptance

`sensor-monitoring`

Accelerometer/gravity monitoring và event candidate creation; có thể chạy before login nếu eligible and policy allows.

Phase 3+ / Optional

`location-tracking`

GPS/location tracking và offline buffer direction; có thể chạy before login nếu eligible and policy allows.

Phase 3+ / MVP GPS availability/basic location only

`realtime-ai-detection`

Frame/sample analysis và event candidate creation.

Phase 3+ / Future

`ai-model`

AI model provider và model validation nếu tách khỏi APK.

Future / TBD

`storage`

Android-side path/temp/final/BDMA readiness/recovery mechanics.

MVP Required

`database`

SQLite `dcam.db`, schema ownership, user/auth/session tables, console/update state, transaction/write-back/recovery boundary, optional policy state snapshot nếu required.

MVP minimal / Phase 2 full

`bdma-integration`

Hỗ trợ BDMA readiness/import state/write-back/user sync.

MVP basic ingest compatibility / Phase 2 full

`security-encryption`

Credential/auth security, kiosk exit security, emergency override auditability, encryption, update/model validation direction.

Phase 2+ / MVP safe logging and basic data protection only

`self-update`

Primary update path: BFF release desired-state, immutable R2/CDN manifest/APK download, validation, DCAM DPC install, result ACK and kiosk policy restore.

Phase 2+

`play-store-fallback`

Not part of the approved production module graph; prohibited by ADR - DCAM GMS-free Android Runtime Baseline.

Not Applicable

`cloud-config`

Remote config provider, validation và apply flow, including kiosk requested-policy settings.

Phase 2+ / no MVP apply

`ddmp-device-management-sync`

BFF enrollment, outbound sync, desired-state/release retrieval, ACK and local cache. Does not call Headwind REST API directly.

Phase 2+ / POC-gated; not required for Build 0.1

`webserver-integration`

Future Live Streaming, PTT, SOS, JT808 và server analytics adapters.

Future / TBD

`logging`

Recording, auth, emergency, policy, capability, monitoring, DB, storage, maintenance, update và diagnostics logs.

MVP Required for core logs; full categories later

`device`

Device status, battery, thermal, device info and device policy state summary.

MVP basic / Phase 2 full

`common`

Shared constants và utilities.

Draft Implementation

## 5. MVP Gradle and Component Limit

MVP should not start with ~35 Gradle modules.

Recommended MVP Gradle structure:

:app
:core
:media
:storage
:bdma-contract
Even smaller initial structure is acceptable:

:app
:core
Inside modules, prefer packages first:

com.dcam.recording
com.dcam.capture
com.dcam.storage
com.dcam.database
com.dcam.bdma
com.dcam.identity
com.dcam.logging
com.dcam.device
MVP runtime component limit:

AppRuntimeBootstrap
RecordingController
CameraService
StorageService
DatabaseService
BdmaExportService
DeviceConfigStore
DeviceStatusProvider
LogService
Optional only when needed for current device POC:

```
BasicLockTaskController
```

## 6. Capability, Policy and Update-aware Dependency Direction

Target architecture direction:

AppRuntimeOrchestrator
        ↓
DevicePolicyStateManager if production profile requires kiosk policy
        ↓
KioskPolicyManager / LockTaskController / UserRestrictionPolicyManager
        ↓
DeviceCapabilityManager
        ↓
FeatureEligibilityEvaluator
        ↓
ConsoleModuleRegistry + RuntimeModuleRegistry
        ↓
Initialize eligible modules only
        ↓
Schedule non-blocking DDMP DeviceManagementSyncCoordinator when the DDMP profile is enabled
        ↓
Operator login gate controls recording/capture evidence commands
        ↓
SelfUpdateCoordinator can run only when State Machine guard allows
MVP interpretation:

AppRuntimeBootstrap
        ↓
Basic permission/device/storage checks
        ↓
RecordingController
        ↓
CameraService + StorageService + DatabaseService
        ↓
BdmaExportService / Contract outputs
Rules:

Rule

Description

MVP Requirement

DEP-001

UI phụ thuộc vào ViewModel, không phụ thuộc trực tiếp Repository hoặc Android hardware/policy APIs.

Required

DEP-002

ViewModel gọi use cases, không gọi trực tiếp Camera/GPS/AI/Storage/SQLite/auth hardware/DevicePolicyManager/PackageInstaller.

Required for Camera/Storage/SQLite; full scope later

DEP-003

Use Case phụ thuộc vào interfaces và domain services.

Required where interface exists

DEP-004

State Machine nhận events và quyết định cross-runtime guard decisions.

Phase 2+; MVP may use local recording state

DEP-005

Sensor Monitoring và Realtime Analytics chỉ tạo events; không điều khiển recording trực tiếp.

Phase 3+

DEP-006

Device Capability / Feature Eligibility phải chạy trước khi optional runtime modules được initialize.

Phase 2+; MVP simple checks only

DEP-007

Unsupported modules hoặc login methods phải được pruned/disabled khỏi runtime, không chỉ hidden trong UI.

Required for MVP-supported optional features

DEP-008

Remote config không được override missing hardware, unsafe performance capability hoặc missing required policy authority.

Phase 2+

DEP-009

Recording behavior phải đi qua RecordingController.

Required

DEP-010

Storage behavior phải đi qua StorageService / Storage Design mechanics.

Required

DEP-011

DB writes phải đi qua repository/DatabaseService transaction boundary.

Required

DEP-012

Active operator session phải được truy cập qua OperatorSessionManager/approved repository.

Phase 2+ / if auth enabled in MVP

DEP-013

Normal recording/capture evidence không được bypass operator-auth guard.

Phase 2+ / if auth enabled in MVP

DEP-014

Emergency override phải dùng system operator `EMERGENCY_OVERRIDE_ADMIN`, không dùng real Admin user.

Phase 2+

DEP-015

DevicePolicyManager, Lock Task and User Restrictions APIs must be isolated behind kiosk policy services/managers.

Required only if kiosk POC/implementation exists

DEP-016

Maintenance Mode must be controlled by approved controller and security guard.

Phase 2+

DEP-017

Maintenance Password Gate must be enforced before controlled kiosk exit.

Phase 2+

DEP-018

Self Update must go through `SelfUpdateCoordinator` and must not assume Managed Google Play/EMM.

Phase 2+

DEP-019

No module may launch, browse or update through Google Play Store/Managed Google Play, or require Google account maintenance.

Mandatory production guard

DEP-020

DDMP device sync must use BFF only; DCAM domain/runtime modules must not call Headwind REST API, Headwind database or use Headwind JWT.

Phase 2+ / POC-gated

DEP-021

`platformDeviceId` in DDMP sync is the resolved `dcam_cloud_device_id`; `dcamInstallationId` is local correlation and `headwindDeviceRef` is mapping-only.

Phase 2+ / POC-gated

DEP-022

Desired-state is validated and executed only by DCAM’s local policy/update adapters; it cannot bypass Device Owner, capability, safe-state or recording guards.

Phase 2+ / POC-gated

DEP-023

DDMP sync, retry, telemetry and Headwind coexistence must not block startup-to-recording-ready, recording, finalization, DB transaction or MainThread work.

Phase 2+ / POC-gated

## 7. Platform Adapter Examples

Platform Service

Interface Example

Implementation Example

Status

Device Policy State

`DevicePolicyStateService`

Android `DevicePolicyManager` / local DCAM DPC state adapter.

Phase 2+

Kiosk Policy

`KioskPolicyService`

Policy orchestrator for Device Owner/DPC, Lock Task, restrictions and maintenance.

Phase 2+

Lock Task

`LockTaskService`

Android Lock Task API adapter behind `LockTaskController`.

MVP conditional / Phase 2+ full

User Restrictions

`UserRestrictionService`

Android UserManager/DevicePolicyManager restriction adapter.

Phase 2+

Home/Launcher Policy

`HomeAppPolicyService`

Preferred Home/Launcher policy adapter if firmware supports it.

Phase 2+

Console Navigation

`ConsoleNavigationService`

Record/Live View + Setting hub + child module navigation coordinator.

Phase 2+ / MVP minimal UI

Console Settings

`ConsoleSettingService`

App operation/device settings validation and safe apply coordinator.

Phase 2+

Maintenance Access

`MaintenanceAccessService`

Admin role + Maintenance Password Gate + timeout/lockout state.

Phase 2+

Approved Maintenance Target

`ApprovedMaintenanceTargetService`

Approved package/settings target launch control.

Phase 2+

Device Capability

`DeviceCapabilityService`

Android hardware/platform/policy and GMS-free compliance detector; no Play Store capability is exposed.

Phase 2+ / MVP simple checks

Feature Eligibility

`FeatureEligibilityService`

Policy + settings + capability evaluator.

Phase 2+

Operator Session

`OperatorSessionService`

DB-backed session manager.

Phase 2+ / if auth enabled in MVP

Auth Methods

`AuthMethodService`

Password / pattern / face / QR / NFC dispatch.

Phase 2+

User Management

`UserRepository`

SQLite-backed user/operator profile repository.

Phase 2+

Camera

`CameraService`

CameraX / Camera2 / vendor SDK adapter.

MVP Required

Motion Sensor

`MotionSensorService`

Android SensorManager.

Phase 3+

Location

`LocationService`

Android Location Provider.

MVP basic availability / Phase 3+ route

Realtime Analytics

`RealtimeAnalyticsService`

On-device runtime/model adapter.

Phase 3+ / Future

AI Model

`AIModelProvider`

Bundled hoặc future remote model provider.

Future / TBD

Storage

`StorageService`

Android file storage implementation.

MVP Required

Database

`DatabaseService`

Room / SQLite wrapper.

MVP minimal / Phase 2 full

Configuration

`DeviceConfigurationService`

BFF desired-state/config + validated local defaults; no cloud-config SDK may change the GMS-free baseline.

Phase 2+

DDMP Device Management

`DeviceManagementSyncService`

BFF HTTPS client for enrollment/sync/desired-state/ACK, local cache and backoff. No direct Headwind adapter in DCAM.

Phase 2+ / POC-gated

Self Update

`SelfUpdateService`

Manifest/APK provider, validator and install coordinator.

Phase 2+

Play Store Fallback

No service is present in the approved production module graph.

Not Applicable

BDMA

`BDMAReadinessService`, `BdmaUserSyncService`

Local DB/file readiness và user sync implementation.

MVP basic / Phase 2 full

Logging

`LogService`

Local file log service.

MVP Required

## 8. Core Flow Direction

### 8.1 MVP Working Recording Slice

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
### 8.2 Capability and Policy-aware Startup and Login

Target flow for later phases:

App Start / Boot
    ↓
Android Operation startup
    ↓
Detect/verify required DCAM-as-DPC / Device Owner state if production profile requires it
    ↓
Verify Lock Task allowlist and User Restrictions when policy authority exists
    ↓
Load validated settings from dcam.db
    ↓
Detect Device Capability including self-update install constraints, policy-safe maintenance window and GMS-free compliance
    ↓
Evaluate Feature Eligibility
    ↓
Persist capability/eligibility result
    ↓
Initialize system modules that do not require login
    ↓
Prepare console modules and approved maintenance targets
    ↓
Enter/recover Lock Task Mode when lifecycle is safe
    ↓
Schedule DDMP BFF sync asynchronously when enabled; it must not delay recording-ready, login or local recovery
    ↓
Restore same-boot operator session or show login screen
    ↓
Enable normal recording/capture commands only after operator auth and policy guard pass
### 8.3 Emergency Event Flow

Sensor / Realtime Analytics / Manual SOS / Future Source
    ↓
Event Detector / Event Router
    ↓
Emergency Event Manager
    ↓
State Machine guard
    ↓
RecordingController command
    ↓
Active operator snapshot OR EMERGENCY_OVERRIDE_ADMIN
    ↓
Emergency Clip / Marker / Important Media
### 8.4 Kiosk and Maintenance Flow

Startup policy check OR admin maintenance request
    ↓
KioskPolicyManager
    ↓
DevicePolicyStateManager verifies authority
    ↓
UserRestrictionPolicyManager applies/verifies restriction profile
    ↓
HomeAppPolicyManager verifies Home/Launcher policy if required
    ↓
LockTaskController verifies allowlist and enters Lock Task when safe
    ↓
Maintenance request requires MaintenanceAccessController
    ↓
ApprovedMaintenanceTargetController opens only approved targets
    ↓
PolicyAuditLogger emits safe reason codes
### 8.5 Self Update Flow

Update requested / scheduled through BFF release desired-state
    ↓
SelfUpdateCoordinator
    ↓
Check System Settings preconditions and State Machine guard
    ↓
Load immutable manifest and download APK from Cloudflare R2/CDN
    ↓
Validate package identity, checksum, signature, version and compatibility
    ↓
Install through approved path
    ↓
Verify app version and restore kiosk policy
## 9. Source-of-truth Alignment

Runtime/Architecture Area

Source of Truth

MVP vs Target Architecture delivery scope

DCAM Architecture Delivery Profile

User/operator requirements

05 - User & Device Operation Requirements

Android dedicated-device/kiosk policy

DCAM Android Device Owner & Kiosk Policy Design

In-app console and controlled maintenance UX

DCAM In-App Operation, Device Settings & Media Console Design

Self Update / APK update

DCAM Self Update Design

DDMP BFF ↔ DCAM shared contract

DDMP 06 Device Integration Contracts

DDMP hybrid boundary and BFF role

DDMP 01 Hybrid System Architecture + DDMP 03 Management API / BFF

Runtime startup/module registry/session lifecycle

DCAM Android Operation Design

Recording/capture session behavior and operator attribution

DCAM Recording & Capture Design

Storage mechanics and BDMA readiness

DCAM Storage Design

SQLite transaction/user auth/session/write-back/recovery

DCAM SQLite Database Design

Credential/auth/kiosk/update security

DCAM Security & Encryption Design

Feature eligibility states

DCAM Device Capability & Feature Eligibility Design

Cross-runtime guards

DCAM State Machine Design

BDMA/user sync contract

DCAM-BDMA Data Contract

Implementation standard

DCAM Android Development Standard

## 10. Practical Conclusion

DCAM architecture phải support heterogeneous BodyCamera hardware, dedicated-device/kiosk deployment, in-app console operation, Self Update primary path và offline operator authentication.

Target architecture direction:

Do not assume external EMM / Android Management API / Managed Google Play
Detect policy state first when production profile requires kiosk
Detect capability early including update install/policy constraints and GMS-free compliance
Evaluate feature eligibility
Prune unsupported runtime modules and auth methods
Degrade only where approved
Use KioskPolicyManager for Device Owner / Lock Task / User Restrictions
Use ConsoleModuleRegistry and ConsoleSettingCoordinator for in-app console modules
Use MaintenanceAccessController for Maintenance Password Gate
Use ApprovedMaintenanceTargetController for controlled maintenance targets
Use DeviceManagementSyncCoordinator for non-blocking BFF enrollment/sync/desired-state/ACK when the DDMP profile is enabled
Use SelfUpdateCoordinator for BFF-authorized R2/CDN APK update path
Do not let Headwind Client/API own Android policy, privileged execution or direct DCAM domain integration
Use optional PlayStoreFallbackService only when approved and available
Use OperatorSessionManager for active session
Use RecordingController for recording behavior
Use StorageService for storage mechanics
Use DatabaseService/repositories for DB transaction boundary
Keep platform, device policy, auth hardware and provider logic behind adapters
MVP delivery direction:

Keep target architecture valid.
Do not implement all target modules upfront.
Use small MVP module/component/state set.
Deliver Working Recording Slice first.
Expand platform architecture only after recording/storage/BDMA slice is demoable.
TBD statuses trên trang này hiện đại diện cho implementation choices thực tế, Device POC outcomes hoặc future features, không phải các architecture decisions đã có source of truth.

## 17. Build 0.1 Module Constraints

CameraService phải bao Android platform Camera API; không tích hợp vendor SDK cho Build 0.1.

State coordination phải enforce Internal storage pre-check trước recording.

File/storage layer finalize MP4 trước khi enqueue async MD5.

BDMA readiness publisher chỉ nhận artifact sau valid MD5.

SQLite/CSON/log adapters phải sử dụng static Build 0.1 operator tại nơi schema yêu cầu.

Device Status integration chỉ gồm battery level, Internal free storage và GPS availability state.

DDMP device-management sync, Headwind Client coexistence, R2 update flow and BFF integration are not Build 0.1 implementation requirements; they remain Phase 2+/POC-gated.

Exact Camera API generation, physical path và schema placement không được suy diễn tại module level.

## Device credential lifecycle module

The DCAM module graph includes a logical device-credential lifecycle component, governed by [ADR – DCAM Device API Credential & mTLS Baseline](/wiki/spaces/DVID/pages/70287362/ADR+DCAM+Device+API+Credential+mTLS+Baseline).

Component

Responsibility

Forbidden behaviour

DeviceCredentialManager

Generate/use Keystore-backed asymmetric key, select active certificate and expose lifecycle state.

Export/copy private key or store it in SQLite/config/SD.

DeviceEnrollmentCoordinator

Coordinate pending enrollment, proof-of-possession, certificate receipt and re-enrolment state.

Treat serial_number or platformDeviceId as authentication.

DeviceApiClient

Use mTLS for device routes, requestId/eventId and safe retry.

Call Factory Portal/admin routes or use worker/Headwind credentials.

CredentialStateRepository

Persist non-secret lifecycle metadata/reason/correlation only.

Persist private key, raw token or certificate secret material.

Exact TLS stack and certificate storage API are implementation details subject to Android/Device POC and Security Review.