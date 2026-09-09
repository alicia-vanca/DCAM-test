# 03 - Android Platform & Compatibility Strategy

**Page ID**: 47120437  
**Version**: 17  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120437

---


# 03 - Android Platform & Compatibility Strategy

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Platform Compatibility Strategy

Version

Approved 1.10

Status

Approved

Approval Scope

Global platform strategy, including approved DDMP Hybrid coexistence boundary and mandatory GMS-free Android runtime baseline; Build 0.1 reference profile Pending Device POC.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, AI/ML Engineer, QA

Last Updated

2026-08-25

Related Jira

None

Related Documents

ADR - DCAM GMS-free Android Runtime Baseline, DCAM Architecture Home, 04 - Application & Module Architecture, 10 - Android Device Operation Requirements, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Self Update Design, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Android Operation Design, DCAM Device Capability & Feature Eligibility Design, DCAM Non-functional Requirements, DCAM Device POC & Hardware Validation Report, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

Dependencies / Blockers

Device POC: validate NCC-036V / Android 12 / API 31, Camera API capability and platform compatibility on the physical reference device; closure requires reviewed POC evidence.

## 1. Purpose

Trang này mô tả chiến lược tương thích Android và BodyCamera platform cho DCAM.

DCAM phải chạy trên nhiều model BodyCamera khác nhau, bao gồm thiết bị mới, thiết bị cũ, thiết bị không có Google Play services/Play Store, thiết bị có phần cứng hạn chế hoặc hiệu năng thấp, và thiết bị có khác biệt OEM/firmware trong Device Owner / Lock Task / User Restrictions / dedicated-device kiosk behavior.

Current baseline:

No external DPC/EMM, Android Management API or Managed Google Play owns privileged device policy.
Headwind Client may run only as an ordinary Application-mode app; it has no Device Owner/DPC authority.
DCAM-as-DPC / local Device Owner is preferred if target firmware supports it.
Primary update path = BFF-authorized DCAM Self Update from immutable Cloudflare R2/CDN artifact.
No Google Play Store/Managed Google Play update or maintenance fallback is permitted. Production update uses BFF-authorized DCAM Self Update from immutable R2/CDN artifact; approved local/factory APK is the separately controlled fallback.
## 2. Compatibility Principles

Principle

Description

Status

Device Capability First

Không assume mọi thiết bị đều có cùng camera, sensor, GPS, CPU/GPU/NPU, storage performance hoặc Device Owner/kiosk policy support.

Approved

Runtime Feature Pruning

Feature không đủ điều kiện phải bị loại khỏi runtime flow, không chỉ ẩn UI.

Approved Direction

Recording First

Platform compatibility phải ưu tiên bảo vệ recording/capture/emergency stability trước các feature phụ.

Approved

Dedicated-device Policy First

Production kiosk control phải dựa trên DCAM-as-DPC/local Device Owner nếu khả thi, Lock Task Mode và User Restrictions; fullscreen/Home app không đủ.

Approved Direction

No External EMM Baseline

Current device baseline không giả định external EMM, Android Management API hoặc Managed Google Play.

Approved Direction

GMS-free Required

Production DCAM runtime must not contain or require Google Play services, Play Store, Google account, FCM, Analytics, Play Integrity or any GMS-only capability.

Approved Direction

Adapter-based Platform Access

Camera, sensor, GPS, storage, AI, cloud/update và DPC/kiosk policy phải đi qua adapter/interface. No Play Store fallback adapter exists in the production profile.

Approved

Degrade When Safe

Chỉ degrade feature hoặc policy behavior nếu degrade mode đã được duyệt và không làm hỏng recording.

Approved Direction

## 3. Android / Device Capability Strategy

App Start / Boot
        ↓
Detect Android version, device hardware and local device policy state
        ↓
Build Device Capability Profile
        ↓
Build Device Policy Capability Profile if production profile requires it
        ↓
Detect update capability including approved Self Update install capability, package-installer constraints and policy-safe maintenance window
        ↓
Evaluate Feature Eligibility
        ↓
Initialize only eligible runtime modules and approved policy managers

Capability Area

Detection Direction

Runtime Impact

Camera

Detect camera availability, supported resolution/FPS và frame analysis support.

Ảnh hưởng recording/capture/AI frame analysis.

Microphone

Detect microphone availability.

Ảnh hưởng audio recording / audio-in-video.

Sensors

Detect accelerometer, gravity sensor và gyroscope.

Ảnh hưởng fall/impact/motion detection.

Location

Detect GPS/location provider và background location capability.

Ảnh hưởng device tracking / JT808 future.

Storage

Detect Internal/External availability, writable state và write performance.

Ảnh hưởng recording/media finalization/BDMA readiness.

Compute

Detect CPU/memory/GPU/NPU class nếu có thể.

Ảnh hưởng Realtime AI, encryption và live streaming future.

Android OS

Detect permission model, foreground service rules và background execution limits.

Ảnh hưởng services/lifecycle/recovery strategy.

Device Owner / DPC

Detect whether DCAM or local DPC component has required policy authority.

Ảnh hưởng dedicated-device/kiosk readiness.

Lock Task

Detect whether DCAM package is allowlisted and Lock Task Mode is permitted.

Ảnh hưởng kiosk runtime entry/recovery.

User Restrictions

Detect supported/unsupported restrictions on Android version/OEM firmware.

Ảnh hưởng restriction profile apply/degrade behavior.

Home / Launcher Policy

Detect whether persistent preferred activity / launcher behavior works on target firmware.

Ảnh hưởng boot/user escape behavior và recovery strategy.

Self Update under policy

Detect whether approved APK update/install path works under DCAM policy constraints.

Ảnh hưởng Self Update and maintenance/update window.

GMS-free compliance

Verify Google Play services/Play Store are not a runtime requirement and prohibited dependency/flow scan passes.

Mandatory production release gate.

## 4. Dedicated-device / Kiosk Compatibility Matrix

Chi tiết policy behavior thuộc **DCAM Android Device Owner & Kiosk Policy Design**. Trang này định nghĩa compatibility dimensions cần validate.

Compatibility Item

Detection / Validation Direction

Fallback / Impact

DCAM-as-DPC / Device Owner setup

Validate local Device Owner setup path on target device/factory process.

Nếu không thể activate required policy authority, production kiosk profile không đạt cho đến khi có fallback/ADR.

`isDeviceOwnerApp` / DPC authority

Runtime detects local policy authority state.

Enter `DEVICE_POLICY_REQUIRED` hoặc `POLICY_DEGRADED` nếu required profile thiếu authority.

No external EMM

Confirm platform strategy does not require EMM/Android Management API/Managed Google Play.

Must use local DCAM policy + Self Update baseline.

Lock Task allowlist

Validate package allowlist and `isLockTaskPermitted`.

Không start Lock Task; block/degrade field operation.

Lock Task UI feature behavior

Validate Home/Back/Recents/notifications/system info behavior.

Adjust lock task feature profile or document OEM limitation.

Preferred Home/Launcher

Validate DCAM as preferred Home/Launcher if required.

Fall back to Lock Task-only behavior if approved; otherwise policy degraded.

User Restrictions

Validate each approved restriction by Android version/OEM.

Unsupported restrictions logged as capability/policy limitation.

Safe boot/factory reset blocking

Validate on real hardware/firmware.

If unsupported, production risk must be accepted or mitigated operationally.

App uninstall/control blocking

Validate DCAM/DPC cannot be removed by field user.

If unsupported, release blocker for production kiosk profile.

External media / USB / ADB behavior

Validate against BDMA import/user sync boundary.

Adjust restriction profile to preserve approved BDMA flow.

Controlled Maintenance Mode

Validate Maintenance Password Gate, approved targets only and policy restore.

Full Android unrestricted mode is not supported; if controlled target enforcement fails, block release or disable affected maintenance target.

Self Update while locked

Validate Self Update can preserve policy and return to kiosk.

Use approved maintenance/update window or defer update.

GMS-free release gate

Validate target firmware and APK operate without Google Play services/Play Store, including DPC, recording, local diagnostics, BFF sync and R2 update deferral.

Required before production interpretation.

OEM crash/reboot recovery

Validate app relaunch and Lock Task recovery.

Define fallback in Android Operation and QA.

## 5. Feature Eligibility Rule

Settings, capability and device policy must be evaluated separately.

```
Final runtime decision = Setting + Device Capability + Permission + Device Policy Capability + Safety Policy
```

Remote config hoặc user setting có thể request một feature/policy, nhưng không được force một hardware path hoặc Android policy path bị thiếu hoặc không an toàn.

Examples:

Requested Feature / Policy

Device Condition

Result

GPS Tracking enabled

Không có GPS/location provider.

`UNSUPPORTED_HARDWARE`, không start location service.

Weapon Detection enabled

CPU class thấp, không có acceleration.

`UNSUPPORTED_PERFORMANCE`, không load model/frame analyzer.

Fall Detection enabled

Có accelerometer, thiếu gravity sensor.

`DEGRADED` nếu đã approved; nếu không thì unsupported.

Video Recording enabled

Camera unavailable hoặc storage not writable.

Block recording bằng controlled message.

Lock Task enabled

Package chưa được allowlisted hoặc không có DPC authority.

`DEVICE_POLICY_REQUIRED` hoặc `POLICY_DEGRADED`, không silently continue unrestricted.

User Restriction profile enabled

OEM không support một restriction cụ thể.

Apply supported restrictions, log unsupported restriction, evaluate release risk.

Maintenance Mode enabled

Credential/audit policy chưa approved hoặc Maintenance Password Gate unavailable.

Maintenance Mode disabled until Security Review passes.

Self Update enabled

Package install path/validation/runtime guard chưa đạt.

Defer/block update and report reason.

GMS-free release gate

Prohibited dependency/flow, target firmware non-compliance or missing required evidence.

Block production profile claim and report reason.

## 6. GMS-free Required Direction

Area

Direction

Status

Core Recording

Phải hoạt động without GMS.

Approved

Local Storage / BDMA

Phải hoạt động without GMS/cloud.

Approved

Device configuration

BFF desired-state/config plus validated local defaults; Android remote-config SDK is not in the approved production baseline.

Approved Direction

Update

BFF-authorized DCAM Self Update from immutable R2/CDN artifact is the only remote production update path; approved local/factory APK is separately controlled support fallback.

Approved Direction

Device Owner / DPC

Không assume external EMM/Android Management API/Managed Google Play. DCAM-as-DPC/local Device Owner feasibility must be validated by Device POC.

Approved Direction

Future WebServer

Là optional capability, phải adapter-based.

Future / Approved Direction

## DDMP Hybrid Compatibility Boundary

Hybrid capability is an additive, Phase 2+/POC-gated compatibility dimension. It must not change local DPC authority or make core recording dependent on server availability.

Compatibility item

Validation direction

Failure impact

Headwind Client coexistence

Verify Application mode does not take HOME/launcher ownership, disturb boot recovery, Lock Task, restrictions or DCAM auto-start.

Block Hybrid profile; DCAM local baseline remains safe.

BFF device sync

Verify outbound desired-state fetch, duplicate/idempotent handling, offline recovery and DCAM ACK without direct Headwind API/database access.

Defer command/update; never bypass local runtime guards.

R2 update path

Verify BFF authorization, immutable manifest/APK, checksum/signer/package/version compatibility and kiosk recovery.

Defer update; preserve controlled runtime.

Device identity mapping

Verify `platformDeviceId = dcam_cloud_device_id`; `serial_number` remains recovery key; Headwind reference is mapping-only.

Block Hybrid enrollment/mapping, not local identity.

Authoritative DDMP sources: [DDMP 00](/wiki/spaces/DVID/pages/68845572/00+DDMP+Architecture+Overview+Reading+Guide), [DDMP 03 BFF](/wiki/spaces/DVID/pages/68812826/03+Management+API+BFF+Architecture+Baseline), [DDMP 06 Device Integration Contracts](/wiki/spaces/DVID/pages/68812848/06+Device+Integration+Contracts), [DDMP 05 APK Release & Cloudflare R2](/wiki/spaces/DVID/pages/68780056/05+APK+Release+Cloudflare+R2).

## 7. POC / Validation Direction

Device capability và kiosk policy compatibility phải được xác nhận bằng **DCAM Device POC & Hardware Validation Report**.

Minimum kiosk/platform POC evidence:

POC Area

Evidence Needed

DCAM-as-DPC / Device Owner setup

Local setup method, factory process requirement and repeatability.

Lock Task Mode

Start/recovery after boot, crash, process kill and app update.

System UI behavior

Home/Back/Recents/notification shade behavior.

User Restrictions

Per-restriction support result and OEM deviations.

Controlled Maintenance Mode

Maintenance Password Gate, approved target enforcement, audit and policy restore.

BDMA boundary

ADB/media import/user sync still works under approved production/factory restrictions.

Self Update path

APK validation/install behavior under Device Owner/Lock Task policy.

GMS-free production profile

No Google Play services/Play Store/Google account/GMS-only SDK dependency; dependency, manifest/source and target-device evidence must pass ADR release gates.

## 8. Practical Conclusion

Android platform compatibility không chỉ là Android version support. Nó còn bao gồm hardware, sensor, storage, compute, permission capability, local Device Owner/DPC feasibility, Lock Task behavior, User Restrictions, update install behavior and mandatory GMS-free runtime compliance.

Detect capability early
Detect device policy state early
Do not assume external EMM / Android Management API / Managed Google Play
Prefer DCAM-as-DPC / local Device Owner if target firmware supports it
Use Self Update / APK update as primary update path
Do not provide or invoke a Play Store fallback in the production profile
Do not initialize unsupported modules
Do not enter unrestricted production mode when required kiosk policy is missing
Degrade only when approved
Keep recording and emergency evidence stable
Use adapter abstractions for platform-specific and policy-specific access
Validate Device Owner / Lock Task / User Restrictions / Self Update on real BodyCamera firmware
## 11. Build 0.1 Reference Platform Profile

Item

Build 0.1 Baseline

Model

NCC-036V

Android

Android 12

API Level

31

Firmware / Build

877AOOAKN1_RK2_V009

Camera Integration

Android platform Camera API

Vendor SDK

Not Applicable

Qualification

Pending Device POC

Profile này không thay đổi global Android compatibility strategy. Camera1/Camera2 và capability thực tế phải được xác nhận bằng Device POC. Kết quả không đại diện cho model/firmware khác.