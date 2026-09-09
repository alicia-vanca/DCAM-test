# DCAM Device Capability & Feature Eligibility Design

**Page ID**: 48758788  
**Version**: 11  
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

Draft 0.10

Status

Draft

Approval Scope

Draft capability/eligibility design, including DDMP Hybrid coexistence eligibility; exact device support depends on approved Build Profile and Device POC evidence.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Last Updated

2026-08-25

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

DCAM is sole DPC; Headwind Client has no policy authority.

In-app console capability

Which settings can be controlled, read-only, hidden or maintenance-only.

Drives Setting hub module visibility.

Maintenance capability

Maintenance Password Gate availability, approved target enforcement, restore policy support.

Full Android unrestricted mode is not supported.

Self Update capability

BFF release authorization, manifest/APK download from R2/CDN, package validation, install path and policy restore capability.

Primary path in Hybrid profile; exact implementation remains Draft/POC-gated.

Headwind coexistence capability

Headwind Client Application-mode package presence, no HOME/launcher takeover, no Lock Task/restriction/boot conflict.

Required only when Hybrid profile is activated; does not grant policy authority.

BFF management sync capability

Outbound desired-state/ACK connectivity, identity mapping and safe retry/offline behavior.

Required only when Hybrid profile is activated.

GMS-free compliance capability

Prohibited dependency/manifest/source scan status plus target-device evidence profile.

Mandatory for production profile claim; no Play Store capability exists.

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

Update and GMS-free boundary

DCAM Self Update Design / ADR - DCAM GMS-free Android Runtime Baseline

Kiosk and maintenance policy

DCAM Android Device Owner & Kiosk Policy Design

DDMP Hybrid authority and shared contract

[DDMP 00](/wiki/spaces/DVID/pages/68845572/00+DDMP+Architecture+Overview+Reading+Guide), [DDMP 03 BFF](/wiki/spaces/DVID/pages/68812826/03+Management+API+BFF+Architecture+Baseline), [DDMP 06 Device Integration Contracts](/wiki/spaces/DVID/pages/68812848/06+Device+Integration+Contracts), [DDMP 05 APK Release & Cloudflare R2](/wiki/spaces/DVID/pages/68780056/05+APK+Release+Cloudflare+R2)

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

GMS-free compliance

Block production-profile claim if prohibited dependency/flow or target-device evidence is missing.

ADR + Release Matrix + QA/POC

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

DDMP Hybrid management

Evaluate Headwind coexistence and BFF sync only when Hybrid profile is enabled; no eligibility result may grant Android policy authority.

DCAM Android Operation Design + DDMP 06

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
management.headwind_client_coexistence
management.bff_sync
management.r2_artifact_access
bdma.adb_import
bdma.user_sync
ai.realtime_detection
## DDMP Hybrid Eligibility Boundary

Capability evaluation for Hybrid does not change Android authority. It reports whether the optional profile can be safely activated.

Capability result

Required behavior

`management.headwind_client_coexistence = ENABLED`

Headwind Client can coexist as Application mode only; DCAM DPC remains sole privileged executor.

`management.bff_sync = TEMPORARILY_UNAVAILABLE`

Defer management reconciliation and retry; local recording/policy runtime continues.

`management.r2_artifact_access = ERROR`

Do not install/update; preserve current controlled app and report actionable reason.

Any Hybrid capability unsupported

Do not activate Hybrid behavior; no fallback may hand policy authority to Headwind or another external provider.

Exact evaluator algorithm, persistence schema and POC evidence remain Draft. Authoritative DDMP sources: [DDMP 00](/wiki/spaces/DVID/pages/68845572/00+DDMP+Architecture+Overview+Reading+Guide), [DDMP 03 BFF](/wiki/spaces/DVID/pages/68812826/03+Management+API+BFF+Architecture+Baseline), [DDMP 06 Device Integration Contracts](/wiki/spaces/DVID/pages/68812848/06+Device+Integration+Contracts), [DDMP 05 APK Release & Cloudflare R2](/wiki/spaces/DVID/pages/68780056/05+APK+Release+Cloudflare+R2).

## 8. Conclusion

Device Capability Design sở hữu eligibility state name, capability category và evaluation rule.

Runtime design document sở hữu domain-specific state/behavior.

Project policy về EMM, Device Owner, update và maintenance được reference từ authoritative document; trang này chỉ chuyển policy/capability thành eligibility result.

Tất cả document và implementation phải dùng official eligibility state set nhất quán.