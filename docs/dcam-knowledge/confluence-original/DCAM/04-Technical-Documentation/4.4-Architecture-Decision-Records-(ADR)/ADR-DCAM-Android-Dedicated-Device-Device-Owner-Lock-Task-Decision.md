# ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

**Page ID**: 49774787  
**Version**: 6  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49774787

---


# ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Architecture Decision Record

Version

Approved Direction 1.1

Status

Approved Direction

Approval Scope

Dedicated-device, DCAM-only Device Owner/DPC và Lock Task architecture direction, bao gồm approved DDMP hybrid boundary; exact DPC component, OEM/firmware coexistence feasibility và policy evidence còn phụ thuộc Device POC/Security Review.

Decision Date

2026-07-08

Last Updated

2026-08-24

Related Jira

None

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Security Reviewer / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.4 - Architecture Decision Records (ADR)

Target Audience

PM/BA, Tech Lead, Android Developers, QA, Security Reviewer, Factory, Support

Related Documents

DCAM Android Device Owner & Kiosk Policy Design, 10 - Android Device Operation Requirements, DCAM Android Operation Design, 03 - Android Platform & Compatibility Strategy, 09 - System Settings Requirements, DCAM Security & Encryption Design, DCAM Self Update Design, DCAM QA Test Strategy & Test Matrix, DCAM Device Provisioning Web Portal Design, DCAM Web Portal & Device API Contract, DCAM Factory Provisioning & Device Production SOP, DDMP Architecture Overview & Reading Guide, 01 Hybrid System Architecture, 03 Management API / BFF, 06 Device Integration Contracts

## 1. Context

DCAM là ứng dụng Android BodyCamera dùng cho field operation trên các thiết bị được kiểm soát.

Bộ tài liệu DCAM hiện tại đã định nghĩa device identity, Web Portal business provisioning, offline-first user/operator management, operator-authenticated recording, emergency override, runtime recovery, self update, security và QA baseline.

Current identity baseline:

serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity / primary cloud device id
serial_lookup/{serial_number} = approved recovery lookup path
ANDROID_ID / android_id_hash / device_lookup/{android_id_hash} = không dùng trong current production baseline
Tuy nhiên, production deployment cũng yêu cầu bản thân Android device phải hoạt động như một dedicated/kiosk device. Full screen UI và Home/Launcher behavior không đủ để ngăn user thoát khỏi app, mở system surfaces chưa được approve, thay đổi device settings, uninstall apps hoặc làm gián đoạn recording operation.

## 2. Decision

DCAM production deployment hướng tới Android dedicated-device operation.

DCAM deployment mode = Android Fully Managed / Dedicated Device

DCAM Android runtime phải support:
1. DCAM-only Device Owner / DPC policy enforcement. Không external DPC/EMM nào được làm policy owner trong approved DDMP profile.
2. Lock Task Mode cho normal field operation.
3. Approved User Restrictions cho kiosk hardening.
4. Controlled Admin / Maintenance Mode cho support và service workflows.
Source of truth cho detailed policy behavior là:

```
DCAM Android Device Owner & Kiosk Policy Design
```

### 2.1 Approved DDMP hybrid boundary

DCAM DPC = the only Android privileged-policy executor
Headwind Client = normal Android application (Application mode only)
BFF = management API, desired-state, audit and Headwind adapter boundary
Headwind Community = limited fleet control-plane; not a DPC or Android policy executor
Cloudflare R2/CDN = immutable APK/manifest artifact plane
Rules:

Headwind Client must not be provisioned as Device Owner or a competing DPC.
Headwind Client must not own HOME/launcher, Lock Task, user restrictions, auto-start policy or privileged APK install/rollback.
Portal and device must not call Headwind REST API or Headwind PostgreSQL directly.
Privileged policy/update commands follow BFF desired-state → DCAM DPC validation/execution → ACK.
Headwind Client push/configuration is not an authoritative privileged command bridge.
## 3. Important Boundary

ADR này tách rõ hai provisioning concepts:

Provisioning Type

Meaning

Source of Truth

Android Enterprise / Device Owner provisioning

Enroll Android device vào Device Owner / fully managed / dedicated-device mode.

DCAM Android Device Owner & Kiosk Policy Design

DCAM business provisioning

Create/restore `dcam_cloud_device_id` và device information bằng `serial_number` và `serial_lookup/{serial_number}`. Không dùng `ANDROID_ID`, `android_id_hash` hoặc `device_lookup/{android_id_hash}` trong current production baseline.

DCAM Device Provisioning Web Portal Design + DCAM Web Portal & Device API Contract

Web Portal QR Flow, nếu dùng, không được hiểu lại thành Android Enterprise Device Owner enrollment.

## 4. Consequences

Area

Consequence

Requirements

Android Device Operation Requirements phải bao gồm requirements cho dedicated-device, Device Owner, Lock Task và User Restrictions.

Architecture

Architecture Home phải reference kiosk policy design mới như authoritative source cho device policy behavior.

Android Operation

Startup/reboot/recovery flow phải verify policy state, apply hoặc validate restrictions và enter Lock Task Mode an toàn.

Platform Compatibility

Device POC phải validate Device Owner, Lock Task và User Restriction behavior trên target BodyCamera models và firmware.

System Settings

Remote/admin policy settings có thể request kiosk policy changes, nhưng Android phải validate và chỉ apply khi safe.

Security

Kiosk exit, Maintenance Mode, policy removal, identity restore và restriction changes phải auditable và protected.

Self Update

Update flow phải preserve Device Owner state và không làm hỏng kiosk/Lock Task policy.

QA

QA Test Matrix phải bao gồm dedicated-device/kiosk policy tests, serial lookup identity restore tests và POC coexistence DCAM DPC + Headwind Client qua boot/reboot/Doze/offline.

## 5. Accepted Risks and Mitigations

Risk

Mitigation

OEM-specific behavior khác nhau giữa các BodyCamera models.

Validate trong DCAM Device POC & Hardware Validation Report.

Device Owner không thể activate bằng normal APK install.

Document Android Enterprise provisioning tách biệt với DCAM business provisioning.

Lock Task có thể trap support users nếu không có exit path.

Define Controlled Admin/Maintenance Mode.

User Restrictions có thể block legitimate maintenance.

Dùng approved restriction profile và temporary maintenance policy.

Update failure có thể khiến device ở inconsistent kiosk state.

Self Update phải verify policy-safe update preconditions và rollback/recovery behavior.

Identity baseline drift trở lại Android ID lookup.

Treat `serial_number` / `serial_lookup/{serial_number}` as current production baseline và reject `ANDROID_ID`, `android_id_hash`, `device_lookup/{android_id_hash}` trong implementation và QA.

Headwind Client conflicts with kiosk/HOME or is incorrectly treated as a policy channel.

DCAM remains the only DPC; allowlist/coexistence, boot/reboot, Doze, Lock Task and recovery behavior require Device POC evidence before Headwind push becomes a functional dependency.

## 6. Decision Summary

DCAM không chỉ là fullscreen Android application.
DCAM production deployment là dedicated-device/kiosk deployment.
Device Owner / DPC policy, Lock Task Mode và User Restrictions là platform-level controls do DCAM thực thi.
DCAM là Android Device Owner / DPC duy nhất trong approved DDMP profile; Headwind Client chỉ chạy Application mode.
DCAM business provisioning tách biệt với Android Enterprise Device Owner enrollment.
DCAM business provisioning dùng serial_number và serial_lookup/{serial_number} để create/restore dcam_cloud_device_id.
Detailed policy behavior thuộc DCAM Android Device Owner & Kiosk Policy Design.
Các tài liệu khác phải reference design đó và chỉ mô tả local impact.
No external EMM / Android Management API / Managed Google Play owns Android privileged policy in this profile. DDMP BFF/Headwind may manage fleet workflow and telemetry, but cannot replace DCAM DPC authority.