# DCAM Android Device Owner & Kiosk Policy Design

**Page ID**: 49840280  
**Version**: 12  
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

Approved Pending Device POC 1.1

Status

Approved Pending Device POC

Approval Scope

Device Owner/Kiosk implementation direction, DDMP Headwind Client coexistence boundary, runtime guards and GMS-free maintenance/update boundary. Exact OEM/firmware feasibility, DPC component, restrictions/allowlist, Headwind package behavior and install behavior remain Pending Device POC.

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

2026-08-25

Related Jira

None

Dependencies / Blockers

NCC-036V Device POC; exact DPC/Device Owner component; target-firmware feasibility; OEM restrictions/allowlist; Headwind Client coexistence POC; approved package/install behavior.

Related Documents

DCAM Device Owner ADR, DCAM Device Identity ADR, ADR - DCAM GMS-free Android Runtime Baseline, DCAM Factory Provisioning & Device Production SOP, DCAM DSetup Factory Tool Design, DCAM Android Operation Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Security & Encryption Design, DCAM Self Update Design, DCAM Device POC & Hardware Validation Report, DDMP 01 Hybrid System Architecture, DDMP 03 Management API / BFF, DDMP 06 Device Integration Contracts

## 1. Approved Direction

Project-wide Device Owner/EMM deployment direction không được restate đầy đủ tại trang này.

GMS-free kiosk rule: Google Play Store is never allowlisted as a maintenance target; no Google account is used on production device. Kiosk/Lock Task recovery, restrictions and controlled maintenance must remain functional without Google Play services. Authoritative prohibition and release gates belong to **ADR - DCAM GMS-free Android Runtime Baseline**.

Baseline Topic

Authoritative Reference

DCAM-only Device Owner/DPC and Lock Task

DCAM Device Owner ADR

Device identity

DCAM Device Identity ADR

Factory setup and acceptance

DCAM Factory Provisioning & Device Production SOP

Update flow

DCAM Self Update Design

DDMP BFF ↔ DCAM contract

DDMP 06 Device Integration Contracts

Maintenance UX

DCAM In-App Operation, Device Settings & Media Console Design

Local implementation impact của trang này:

Định nghĩa DCAM DPC policy component, Lock Task, User Restrictions, Home/Launcher policy và policy recovery.

Định nghĩa runtime verification, missing-policy behavior, Controlled Maintenance guard và approved external-app coexistence.

Exact OEM/firmware feasibility, component value, allowlist và Headwind Client package behavior cần Device POC/implementation evidence.

## 2. Boundaries

Layer

Responsibility

DSetup

Install APK, set/verify **DCAM** Device Owner when required, inject and verify serial.

Factory Web Portal

Business provisioning only; it does not create Device Owner state.

BFF

Desired-state, audit, release authorization and Headwind server-side adapter; it does not execute Android policy.

Headwind Community / Client

Limited fleet control-plane; Client runs Application mode only.

Android runtime

DCAM verifies/applies/recovers Lock Task, restrictions and Home policy.

APK installation alone does not imply Device Owner. Headwind Client installation or enrollment does not imply Device Owner, Device Admin, HOME/launcher ownership, Lock Task authority or privileged package-install authority.

## 3. Runtime Components

Component

Responsibility

`DevicePolicyStateManager`

Detect DCAM policy authority and health.

`KioskPolicyManager`

Apply/verify DCAM-owned policy profile.

`LockTaskController`

Manage DCAM-owned allowlist and lifecycle.

`UserRestrictionPolicyManager`

Apply supported restrictions.

`HomeAppPolicyManager`

Verify DCAM Home/Launcher policy if required.

`HeadwindClientCoexistencePolicy`

Verify installed/versioned Headwind Client is in Application mode, is not launcher/DPC, and has only POC-approved package allowances.

`MaintenanceAccessController`

Validate actor and maintenance gate.

`MaintenanceModeController`

Enter/exit Controlled Mode and restore policy.

`ApprovedMaintenanceTargetController`

Allow approved targets only.

UI, BFF and Headwind Client must not call Android policy APIs directly.

## 4. Headwind Client coexistence rules

DCAM = only Device Owner/DPC and only HOME/Lock Task policy owner
Headwind Client = installed normal application, if the DDMP profile is enabled
BFF = desired-state/API boundary
Headwind Community = status/configuration control-plane only

Rule

Requirement

KSK-HW-001

Headwind Client must not be provisioned as Device Owner or competing DPC.

KSK-HW-002

Headwind Client must not become default HOME/launcher or alter DCAM Home/Launcher policy.

KSK-HW-003

Headwind Client must not enter/exit Lock Task, modify Lock Task allowlist, apply User Restrictions or relax policy.

KSK-HW-004

Headwind Client must not install, rollback or authorize DCAM APK update.

KSK-HW-005

Its package may be installed and, only where justified by target firmware/operation, granted a POC-approved allowlist/foreground/background exception. It is not a normal user-facing maintenance target.

KSK-HW-006

DCAM must re-verify DPC, HOME, Lock Task and restrictions after Headwind install/update, reboot, process kill or reconnect.

KSK-HW-007

Headwind configuration/push is not a privileged command bridge; only BFF desired-state accepted and locally validated by DCAM may request policy/update work.

KSK-HW-008

Any coexistence failure enters DCAM policy recovery/degraded support state; it must not silently relax kiosk policy.

Exact Headwind package name, Android permissions, allowlist behavior and background execution treatment remain TBD/Pending Device POC.

## 5. Maintenance Baseline

authorized actor
    ↓
Maintenance Password Gate
    ↓
runtime safe-state check
    ↓
relax only approved DCAM policy
    ↓
open approved target only
    ↓
return and restore restrictions/Lock Task
Entry is blocked during recording, emergency, finalization, recovery, unsafe update or policy recovery. Headwind Client is not an unrestricted maintenance target and must not be used to escape kiosk.

## 6. POC Boundary

Device POC must validate:

DCAM Device Owner setup on target firmware
Lock Task recovery
Home/Recents/Back behavior
supported User Restrictions
approved maintenance targets
DCAM Self Update and policy restore
BDMA ADB and SD Identity File compatibility
Headwind Client install/Application-mode coexistence
Headwind process restart, boot/reboot, Doze, Wi-Fi reconnect and offline recovery
Headwind package allowance does not expose launcher, Recents, Settings, package install or kiosk escape
## 7. Resolved and Remaining Decisions

Item

Status

DPC ownership direction

Approved: DCAM-only local Device Owner / DPC

Headwind Client role

Approved direction: Application mode only; limited control-plane client

Factory Device Owner setup

Approved baseline: DSetup + ADB `dpm set-device-owner` for DCAM

Maintenance entry

Approved: authorized role + gate + Controlled Mode

Full unrestricted Android

Not Supported

Headwind privileged command authority

Not Supported

Exact Device Owner component/wrapper

TBD / Android + Device POC

Target-firmware feasibility

TBD / Device POC

Lock Task flags and package allowlist

TBD / Product + Security + POC

Headwind package name/permissions/allowance

TBD / Headwind version + Device POC

User Restrictions by OEM

TBD / Device POC

Exact maintenance policy values

TBD / Security + Product

Approved apps/settings targets

TBD / Product + Security + POC

Silent install behavior

TBD / Device POC

Broken-policy support procedure

TBD / Support + Factory

## 8. Practical Conclusion

DCAM owns DPC, kiosk, launcher, restrictions and recovery.
Headwind Client may coexist only as a POC-approved Application-mode package.
BFF desired-state is the only remote management input accepted by DCAM for privileged work.
Only implementation values and target-device evidence remain TBD.
## 9. Status Interpretation

`Approved Pending Device POC` có nghĩa:

Device Owner/Kiosk architecture direction, DDMP coexistence boundary, runtime guards và policy boundaries đã được phê duyệt.

Exact Device Owner/DPC component, target-firmware feasibility, OEM-specific User Restrictions, allowlist, Headwind Client behavior và silent-install behavior chưa được xác nhận.

Các mục chưa xác nhận phải giữ TBD hoặc Pending Device POC và không được dùng làm production claim.

Chỉ Device POC evidence trên approved reference configuration mới có thể đóng dependency; status này không phải hardware certification hoặc Production approval.