# DCAM Android Device Owner & Kiosk Policy Design

**Page ID**: 49840280  
**Version**: 10  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49840280

---


# DCAM Android Device Owner & Kiosk Policy Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design / Device Owner / Kiosk Policy Design

Version

Approved Pending Device POC 0.9

Status

Approved Pending Device POC

Approval Scope

Device Owner/Kiosk implementation direction và runtime guards; exact OEM/firmware feasibility, Device Owner component, restrictions/allowlist và install behavior Pending Device POC.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Security Reviewer / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, QA, Security Reviewer, Support, Factory

Last Updated

2026-07-13

Related Jira

None

Dependencies / Blockers

NCC-036V Device POC; exact DPC/Device Owner component; target-firmware feasibility; OEM restrictions/allowlist; Play Store/silent-install evidence.

Related Documents

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Factory Provisioning & Device Production SOP, DCAM DSetup Factory Tool Design, DCAM Android Operation Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Security & Encryption Design, DCAM Self Update Design, DCAM Device POC & Hardware Validation Report

## 1. Approved Direction

Project-wide Device Owner/EMM deployment direction không được restate tại trang này.

Baseline Topic

Authoritative Reference

Current project and architecture baseline

DCAM Project Home / DCAM Architecture Home

Device Owner / EMM / Managed Google Play decision

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

Factory setup and acceptance

DCAM Factory Provisioning & Device Production SOP

Update flow

DCAM Self Update Design

Maintenance UX

DCAM In-App Operation, Device Settings & Media Console Design

Local implementation impact của trang này:

Định nghĩa DPC/Device Owner policy component, Lock Task, User Restrictions, Home/Launcher policy và policy recovery.

Định nghĩa runtime verification, missing-policy behavior và Controlled Maintenance guard.

Exact OEM/firmware feasibility, component value và allowlist cần Device POC/implementation evidence.

## 2. Boundaries

Layer

Responsibility

DSetup

Install APK, set/verify Device Owner when required, inject and verify serial.

Web Portal

Business provisioning only; it does not create Device Owner state.

Android runtime

Verify/apply/recover Lock Task, restrictions and Home policy.

APK installation alone does not imply Device Owner.

## 3. Runtime Components

Component

Responsibility

`DevicePolicyStateManager`

Detect policy authority and health.

`KioskPolicyManager`

Apply/verify policy profile.

`LockTaskController`

Manage allowlist and lifecycle.

`UserRestrictionPolicyManager`

Apply supported restrictions.

`HomeAppPolicyManager`

Verify Home/Launcher if required.

`MaintenanceAccessController`

Validate actor and maintenance gate.

`MaintenanceModeController`

Enter/exit Controlled Mode and restore policy.

`ApprovedMaintenanceTargetController`

Allow approved targets only.

UI must not call Android policy APIs directly.

## 4. Maintenance Baseline

authorized actor
    ↓
Maintenance Password Gate
    ↓
runtime safe-state check
    ↓
relax only approved policy
    ↓
open approved target only
    ↓
return and restore restrictions/Lock Task
Entry is blocked during recording, emergency, finalization, recovery, unsafe update or policy recovery.

Exact credential complexity, rotation, recovery, failed-attempt values and session timeout remain Security/Product decisions.

## 5. POC Boundary

Device POC must validate:

Device Owner setup on target firmware
Lock Task recovery
Home/Recents/Back behavior
supported User Restrictions
approved maintenance targets
Self Update and policy restore
BDMA ADB and SD Identity File compatibility
## 6. Resolved and Remaining Decisions

Item

Status

DPC ownership direction

Approved: DCAM-as-DPC / local Device Owner

Factory Device Owner setup

Approved baseline: DSetup + ADB `dpm set-device-owner`

Maintenance entry

Approved: authorized role + gate + Controlled Mode

Full unrestricted Android

Not Supported

Exact Device Owner component/wrapper

TBD / Android + Device POC

Target-firmware feasibility

TBD / Device POC

Lock Task flags and package allowlist

TBD / Product + Security + POC

User Restrictions by OEM

TBD / Device POC

Exact maintenance policy values

TBD / Security + Product

Approved apps/settings targets

TBD / Product + Security + POC

Play Store fallback and silent install

TBD / Device POC

Broken-policy support procedure

TBD / Support + Factory

## 7. Practical Conclusion

The kiosk architecture is defined.
Only implementation values and target-device evidence remain TBD.
## 8. Status Interpretation

`Approved Pending Device POC` có nghĩa:

Device Owner/Kiosk architecture direction, runtime guards và policy boundaries đã được phê duyệt.

Exact Device Owner/DPC component, target-firmware feasibility, OEM-specific User Restrictions, allowlist và Play Store/silent-install behavior chưa được xác nhận.

Các mục chưa xác nhận phải giữ TBD hoặc Pending Device POC và không được dùng làm production claim.

Chỉ Device POC evidence trên approved reference configuration mới có thể đóng dependency; status này không phải hardware certification hoặc Production approval.