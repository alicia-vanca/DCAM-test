# DCAM Device Capability & Feature Eligibility Design

**Page ID**: 48758788  
**Version**: 9  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48758788

---


# DCAM Device Capability & Feature Eligibility Design

Item

Information

Project

DCAM

Document Type

Technical Design

Version

Draft 0.8

Status

Draft

Approval Scope

Draft capability/eligibility design only; exact device support phụ thuộc approved Build Profile và Device POC evidence.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Last Updated

2026-07-20

Related Jira

None

Related Documents

DCAM Android Operation Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Self Update Design, DCAM State Machine Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Sensor & Location Monitoring Design, DCAM Realtime AI Detection Design, DCAM Device POC & Hardware Validation Report

Dependencies / Blockers

Approved Build Profile and Device POC: confirm feature eligibility against the active profile and reference-device evidence.

Target Audience

Tech Lead, Android Developers, QA, Support

## 1. Purpose

Trang này định nghĩa single canonical feature eligibility state set và runtime pruning principle cho DCAM.

Project-wide Device Owner/EMM, update và maintenance policy được reference từ DCAM Project Home, DCAM Architecture Home, Device Owner ADR, Kiosk Policy Design và Self Update Design; trang này không restate các decision đó.

Local implementation scope:

Định nghĩa eligibility state name, meaning và capability category.

Map hardware, firmware, permission, policy và runtime condition thành eligibility result.

Cung cấp input để runtime initialize/prune module và UI expose/hide capability.

Không thay đổi domain-specific state machine hoặc project policy.

## 2. Official Feature Eligibility States

Đây là official state set được dùng trên toàn bộ DCAM documents.

State

Meaning

`ENABLED`

Feature có thể run.

`DEGRADED`

Feature có thể run ở approved reduced mode.

`DISABLED_BY_POLICY`

Feature bị tắt bởi setting hoặc policy.

`DISABLED_BY_PERMISSION`

Required permission đang missing.

`UNSUPPORTED_HARDWARE`

Required hardware capability đang missing.

`UNSUPPORTED_PERFORMANCE`

Device performance thấp hơn minimum.

`TEMPORARILY_UNAVAILABLE`

Temporary device/runtime condition ngăn safe execution.

`PRUNED`

Runtime path bị exclude khỏi initialization.

`ERROR`

Detection, evaluation hoặc runtime error.

`SUPPORTED` không phải official Feature Eligibility State. Nó chỉ có thể dùng như descriptive capability wording, không dùng làm persisted/runtime state.

## 3. Runtime Rule

Setting = requested feature state
Capability = actual device ability
Eligibility = official runtime decision
Runtime starts only for ENABLED or approved DEGRADED states
Runtime modules có trạng thái `UNSUPPORTED_*`, `DISABLED_*`, `TEMPORARILY_UNAVAILABLE`, `PRUNED` hoặc `ERROR` không được start normal runtime path.

## 4. Capability Categories

Capability Category

Examples

Notes

Camera capability

Camera available, supported resolution/FPS, preview frame analysis support.

Required for recording/capture/QR/AI camera-based features.

Audio capability

Microphone available and audio permission support.

Required for audio recording/PTT future.

Sensor capability

Accelerometer, gravity, gyroscope, motion/fall detection inputs.

Optional; pruned if unsupported.

Location capability

GPS/location provider and background location support.

Optional; policy-controlled.

Storage capability

Internal/external availability, writable state, free space, write performance.

Core recording/storage readiness.

Compute capability

CPU/memory/GPU/NPU/performance class.

AI/encryption/live streaming eligibility.

Auth method capability

Password, PIN/pattern, QR, NFC, face auth availability.

Exact methods may still be Product/Security TBD.

Kiosk policy capability

Device Owner/DPC authority, Lock Task permitted, restriction support, Home/Launcher behavior.

Current baseline does not use external EMM.

In-app console capability

Which settings can be controlled, read-only, hidden or maintenance-only.

Drives Setting hub module visibility.

Maintenance capability

Maintenance Password Gate availability, approved target enforcement, restore policy support.

Full Android unrestricted mode is not supported.

Self Update capability

Manifest access, APK download, package validation, install path and policy restore capability.

Primary update path for current baseline.

Play Store fallback capability

GMS available, Play Store available, controlled fallback process approved.

Optional only; not required for core DCAM.

BDMA capability

ADB/user sync/import path availability under approved restrictions.

Must be validated by POC.

## 5. Baseline References and Local Eligibility Impact

Project-wide EMM, Device Owner, maintenance và update decision không được copy lại tại trang này.

Topic

Authoritative Reference

Current project/architecture baseline

DCAM Project Home / DCAM Architecture Home

Device Owner / EMM / Managed Google Play

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

Update and Play Store fallback

DCAM Self Update Design / DCAM In-App Operation, Device Settings & Media Console Design

Kiosk and maintenance policy

DCAM Android Device Owner & Kiosk Policy Design

Local eligibility impact:

Capability evaluator chỉ report capability/eligibility state; không thay đổi project policy.

Feature không có trên target device/profile phải map sang official state như `UNSUPPORTED_HARDWARE`, `DISABLED_BY_POLICY` hoặc `PRUNED`.

Optional fallback chỉ được expose khi capability tồn tại và authoritative policy cho phép.

Runtime module phải consume eligibility result trước khi initialize hoặc expose UI.

## 6. Downstream Runtime Design Alignment

Runtime Area

How It Uses This Page

Detailed Runtime Owner

Android runtime startup

Dùng eligibility result để initialize/prune modules trong `RuntimeModuleRegistry`.

DCAM Android Operation Design

Kiosk policy

Detect policy authority/restriction/Lock Task support before normal field operation.

DCAM Android Device Owner & Kiosk Policy Design

In-app console

Hide/disable/read-only console modules based on capability, role and policy.

DCAM In-App Operation, Device Settings & Media Console Design

Self Update

Enable update only when update capability and runtime guard allow.

DCAM Self Update Design

Play Store fallback

Hide/disable if GMS/Play Store unavailable or fallback not approved.

In-App Console + Self Update + Security Design

Recording/capture

Consume camera/audio/storage eligibility trước session start; detailed recording states nằm ở tài liệu riêng.

DCAM Recording & Capture Design

Storage

Consume storage capability trước root/path selection; detailed file states nằm ở tài liệu riêng.

DCAM Storage Design

SQLite DB

Persist `feature_eligibility_state` values bằng official state set này.

DCAM SQLite Database Design

Sensor/location

Chỉ start nếu feature eligibility allows; chỉ emit events.

DCAM Sensor & Location Monitoring Design

Realtime AI / analytics

Chỉ start nếu feature eligibility allows; chỉ emit events.

DCAM Realtime AI Detection Design

State machine

Reference state set này cho runtime registration decisions.

DCAM State Machine Design

## 7. Database Direction

`feature_eligibility_state.eligibility_state` chỉ được dùng official state set dưới đây.

ENABLED
DEGRADED
DISABLED_BY_POLICY
DISABLED_BY_PERMISSION
UNSUPPORTED_HARDWARE
UNSUPPORTED_PERFORMANCE
TEMPORARILY_UNAVAILABLE
PRUNED
ERROR
Recommended feature/capability keys include but are not limited to:

camera.recording
camera.preview_analysis
audio.recording
sensor.motion
location.gps
storage.internal
storage.external
policy.device_owner
policy.lock_task
policy.user_restrictions
policy.home_launcher
console.setting_hub
console.file_manager_read_only
console.media_viewer_read_only
maintenance.password_gate
maintenance.approved_targets
update.self_update
update.play_store_fallback
bdma.adb_import
bdma.user_sync
ai.realtime_detection
## 8. Conclusion

Device Capability Design sở hữu eligibility state name, capability category và evaluation rule.

Runtime design document sở hữu domain-specific state/behavior.

Project policy về EMM, Device Owner, update và maintenance được reference từ authoritative document; trang này chỉ chuyển policy/capability thành eligibility result.

Tất cả document và implementation phải dùng official eligibility state set nhất quán.