# DCAM Self Update Design

**Page ID**: 48529439  
**Version**: 12  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529439

---


# DCAM Self Update Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

Draft 1.1

Status

Draft

Approval Scope

Draft self-update design only; không phải approved implementation, release hoặc Production baseline.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, QA, Support, Factory/Admin Users

Last Updated

2026-07-14

Related Jira

None

Related Documents

09 - System Settings Requirements, 06 - Cloud Services, Update & Configuration Architecture, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Android Operation Design, DCAM State Machine Design, DCAM Device Capability & Feature Eligibility Design, DCAM Security & Encryption Design, DCAM QA Test Strategy & Test Matrix, DCAM Device POC & Hardware Validation Report

## 1. Purpose

Tài liệu này mô tả technical design cho **Self Update / APK update** của DCAM.

Project-wide EMM/Device Owner và update-direction baseline được reference từ:

DCAM Project Home / DCAM Architecture Home.

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision.

DCAM Android Device Owner & Kiosk Policy Design.

Local implementation impact: **DCAM Self Update Design** sở hữu artifact discovery, download, validation, install, result, recovery và rollback direction cho primary APK update flow.

Tài liệu này không định nghĩa lại:

AutoUpdate precondition — thuộc **09 - System Settings Requirements**.

Device Owner / Lock Task / User Restrictions policy — thuộc **DCAM Android Device Owner & Kiosk Policy Design**.

Controlled Mode, Maintenance Password Gate và optional Play Store fallback UX — thuộc **DCAM In-App Operation, Device Settings & Media Console Design**.

## 2. Authoritative References

Topic

Authoritative Document

Local Summary

AutoUpdate preconditions

09 - System Settings Requirements

Self Update phải check đủ approved preconditions trước download/install, bao gồm device policy state safe.

App operating modes and runtime safety

DCAM Android Operation Design

Self Update consume Android app modes như `READY`, `RECORDING_ACTIVE`, `EMERGENCY_ACTIVE`, `SAFE_MODE`, `POLICY_RECOVERY_REQUIRED`, `UPDATING`.

No-EMM / No-AMAPI / No-Managed-GP decision

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

Self Update references the ADR instead of repeating the full decision block.

Kiosk policy / controlled maintenance

DCAM Android Device Owner & Kiosk Policy Design

Update must preserve Device Owner/DPC policy, Lock Task recovery and User Restrictions baseline.

In-app controlled maintenance UX

DCAM In-App Operation, Device Settings & Media Console Design

Manual Play Store fallback, if enabled, must run only through Controlled Mode and Maintenance Password Gate.

Update provider architecture

06 - Cloud Services, Update & Configuration Architecture

Current baseline should treat APK artifact provider as primary; Managed Google Play not applicable.

Update priority / blocked state guard

DCAM State Machine Design

Update có priority thấp hơn emergency, recording, finalizing, storage/DB recovery, policy recovery và unsafe runtime states.

Capability/runtime safety

DCAM Device Capability & Feature Eligibility Design

Update không chạy khi capability evaluation hoặc unsafe runtime initialization đang active; Play Store fallback depends on GMS/Play Store capability.

Package/update security

DCAM Security & Encryption Design

APK identity, integrity, trusted source, signature and policy-safe update security.

## 3. Design Decision

Primary current update path:
1. DCAM checks version manifest from approved artifact provider.
2. DCAM downloads APK artifact when newer approved version exists.
3. DCAM validates package identity, signature, checksum, version and compatibility.
4. DCAM installs through approved Android/package policy path only when runtime guard is safe.
5. DCAM verifies update result and restores kiosk policy.

Optional fallback:
Manual Google Play Store update may be used only from Controlled Maintenance Mode
if the device has GMS/Play Store and an approved maintenance/factory Google account/process exists.

Not applicable for current baseline (per ADR):
Managed Google Play / Android Management API / External EMM-driven update.
Self Update không được interrupt field operation. Exact safety preconditions được reference từ **09 - System Settings Requirements**.

## 4. Self Update Flow

Scheduled/manual update check
    ↓
Confirm current device baseline uses DCAM Self Update path
    ↓
Load version manifest from approved artifact provider
    ↓
If newer approved version exists
    ↓
Check AutoUpdate preconditions from System Settings
    ↓
Check Android Operation mode and State Machine guard
    ↓
Check Kiosk Policy state is safe for update
    ↓
Check storage/network/power/package constraints
    ↓
Download APK
    ↓
Validate APK package identity, signature, checksum, version and compatibility
    ↓
Install according to approved Android/device policy path
    ↓
After restart/resume, verify app version
    ↓
Verify Device Owner/DPC state and re-enter Lock Task if required
    ↓
Report/audit update result
Else
    ↓
Continue current version
Manual Play Store fallback is separate:

Admin / Maintenance
    ↓
Maintenance Password Gate
    ↓
Controlled Mode
    ↓
Open Google Play Store only if approved and available
    ↓
Update DCAM/approved apps only
    ↓
Return to DCAM
    ↓
Verify update result where applicable
    ↓
Restore kiosk policy
## 5. Update Source Priority

Priority

Source

Direction

Status

1

DCAM Self Update / APK artifact provider

Primary update path for current no-external-EMM baseline.

Approved Direction

2

Local/factory APK package

Optional factory/support fallback if approved and validated.

Approved Direction / Process TBD

3

Manual Google Play Store update

Optional controlled fallback only if device has GMS/Play Store and approved maintenance/factory account.

Optional / POC Required

Not applicable

Managed Google Play / Android Management API policy-driven update

Not available for current baseline.

Not Applicable

Not allowed

Personal Google account Play Store update

Not allowed for production maintenance.

Not Supported

## 6. App Operating Mode and Policy Alignment

Self Update does not own app operating modes or kiosk policy. It references **DCAM Android Operation Design**, **DCAM State Machine Design** and **DCAM Android Device Owner & Kiosk Policy Design** for runtime safety.

Android Operation Mode / Runtime State

Update Behavior

`READY`

Update check/download may run if all System Settings preconditions pass and policy state is safe.

`BOOTING`

Defer until startup, recovery, policy verification and capability evaluation complete.

`DEVICE_POLICY_REQUIRED`

Do not install; require policy remediation/support first.

`POLICY_DEGRADED`

Allow only if degradation is approved for update path.

`POLICY_RECOVERY_REQUIRED`

Defer update.

`LOCK_TASK_ACTIVE`

Update may proceed only if install path does not leave device unrestricted or approved update/maintenance window exists.

`MAINTENANCE_AUTH_REQUIRED`

Update path requiring maintenance must wait for authorized Maintenance Password Gate.

`MAINTENANCE_MODE`

Update may run if Security/Kiosk policy allows and no critical runtime state active.

`RECORDING_ACTIVE`

Defer update.

`EMERGENCY_ACTIVE`

Defer update.

`FINALIZING` / storage finalization active

Defer update.

`RECOVERY_MODE` / storage or DB recovery active

Defer update.

`SAFE_MODE`

Do not auto-install; allow diagnostics/support only unless maintenance policy explicitly allows.

`DEGRADED_OPERATION`

Allow only if degradation does not make update unsafe.

`UPDATING`

Continue current update flow; block duplicate update command.

`FATAL_ERROR`

Do not auto-update.

## 7. Policy-safe Update Rules

Detailed kiosk rules belong to **DCAM Android Device Owner & Kiosk Policy Design**. Self Update must follow these rules.

Rule

Description

UPD-KIOSK-001

Update must preserve package identity expected by Device Owner/DPC policy.

UPD-KIOSK-002

Update must not remove DCAM from Lock Task allowlist before safe transition.

UPD-KIOSK-003

Update must not leave device unrestricted if install fails.

UPD-KIOSK-004

Device policy state must be safe before install.

UPD-KIOSK-005

If update path requires temporary Lock Task exit or allowlist expansion, it must happen only in approved update/maintenance window.

UPD-KIOSK-006

If update path requires Maintenance Mode, Maintenance Password Gate is required.

UPD-KIOSK-007

After update/restart, DCAM must verify policy and re-enter Lock Task.

UPD-KIOSK-008

Policy restore failure after update must enter policy recovery/degraded state and log reason.

UPD-KIOSK-009

Update must not assume external EMM/Managed Google Play (per ADR).

## 8. Play Store Fallback Boundary

Manual Google Play Store update is not the primary path. It exists only as optional fallback.

Rule

Description

UPD-PLAY-001

Manual Play Store update is allowed only through Admin / Maintenance Controlled Mode.

UPD-PLAY-002

Maintenance Password Gate is required before Play Store fallback.

UPD-PLAY-003

Device must have GMS/Google Play Store available and validated by Device POC.

UPD-PLAY-004

Only DCAM/approved apps may be updated.

UPD-PLAY-005

Personal Google account is not allowed for production maintenance.

UPD-PLAY-006

Approved maintenance/factory Google account handling is TBD and must pass Security/Product review.

UPD-PLAY-007

Play Store fallback must not allow unrestricted Play Store browsing or unapproved app install.

UPD-PLAY-008

After Play Store update, DCAM must return to app, verify update result where applicable and restore kiosk policy.

## 9. Blocked / Deferred Update Reasons

Self Update should return clear reason codes instead of duplicating full precondition list.

Reason Code

Meaning

`DEFERRED_EMERGENCY_ACTIVE`

Emergency evidence flow đang active.

`DEFERRED_RECORDING_ACTIVE`

Recording đang active.

`DEFERRED_FINALIZING`

Post-record/finalization/storage final move đang active.

`DEFERRED_CAPABILITY_EVALUATION`

Capability/eligibility evaluation chưa complete.

`DEFERRED_MONITORING_AI_UNSAFE`

Monitoring/realtime runtime state unsafe theo policy.

`DEFERRED_DB_OR_STORAGE_RECOVERY`

DB/storage recovery đang active.

`DEFERRED_POLICY_RECOVERY`

Device Owner/Lock Task/User Restrictions state đang recovery hoặc chưa xác định.

`DEFERRED_POLICY_UNSAFE`

Kiosk policy state không safe cho update.

`DEFERRED_MAINTENANCE_WINDOW_REQUIRED`

Update path cần Maintenance/update window nhưng chưa được authorize.

`DEFERRED_MAINTENANCE_AUTH_REQUIRED`

Maintenance Password Gate required before update path can continue.

`DEFERRED_NOT_CHARGING`

Charging precondition chưa đạt.

`DEFERRED_NO_INTERNET`

Network precondition chưa đạt.

`DEFERRED_PLAY_STORE_NOT_AVAILABLE`

Play Store fallback requested but GMS/Play Store unavailable.

`BLOCKED_INVALID_PACKAGE`

APK/manifest/checksum/signature validation failed.

`BLOCKED_PACKAGE_IDENTITY_MISMATCH`

APK identity không match expected DCAM package identity.

`BLOCKED_MANAGED_GOOGLE_PLAY_NOT_APPLICABLE`

Managed Google Play / policy-driven update requested on no-EMM baseline.

`BLOCKED_PERSONAL_GOOGLE_ACCOUNT`

Personal Google account attempted for production maintenance.

## 10. Artifact Direction

Artifact

Purpose

Status

APK File

Installation package cho DCAM version mới.

Approved Direction

Version Manifest

latest version, versionCode, versionName, minimum supported version, download URL.

Approved Direction / Exact Schema TBD

Checksum Field/File

Verify downloaded APK.

Approved Direction / Algorithm/Field TBD

Signature Metadata

Support package identity/signature validation.

Approved Direction / Exact Metadata TBD

Policy Compatibility Metadata

Optional metadata indicating update requires maintenance window, minimum Android version, policy constraints or Device Owner compatibility.

Approved Direction / Exact Schema TBD

Rollback/previous version metadata

Optional metadata to support recovery/rollback decision.

Optional / TBD

Artifact storage/provider can be WebServer, R2, local factory source or another approved artifact source. DCAM owns version check, safety check, download, validation and install decision.

## 11. APK Validation Direction

Validation Area

Description

Status

Version Validation

APK must be newer or policy-approved.

Approved Direction

Package Identity

APK must match DCAM application identity.

Approved Direction

Checksum Validation

APK bytes must match manifest/checksum metadata.

Approved Direction / Algorithm TBD

Signature Validation

APK signature must be trusted by Android/device policy.

Approved Direction / Exact Check TBD

Device Compatibility

APK must be compatible with Android version and capability profile.

Approved Direction

Policy Compatibility

APK/update flow must be compatible with Device Owner/DPC, Lock Task, update/maintenance window and package identity requirements.

Approved Direction

Storage Validation

Device must have enough storage for download/install.

Approved Direction / Threshold TBD

Source Validation

Artifact source must be trusted and not user-provided arbitrary APK.

Approved Direction

## 12. Logging Requirements

Event

Description

Update Check Started

Bắt đầu update check.

Manifest Loaded

Manifest được load từ artifact provider.

New Version Available

Tìm thấy new version.

Preconditions Not Met

AutoUpdate deferred; reason code reference System Settings / Android Operation state.

Update Deferred

Store reason code such as recording active, finalizing, DB recovery, policy recovery, maintenance auth required.

Policy State Checked

Device Owner/Lock Task/User Restrictions state checked before install.

Policy-safe Window Required

Update requires Maintenance/update window.

APK Download Started / Completed / Failed

Download lifecycle.

APK Validation Passed / Failed

Validation lifecycle.

Install Started / Succeeded / Failed

Install lifecycle.

Managed Google Play Not Applicable

Policy-driven update requested but no-EMM baseline applies.

Play Store Fallback Started / Completed / Failed

Manual fallback lifecycle if enabled.

Post-update Version Verified

App version checked after update/resume.

Post-update Policy Verified

Device policy verified after restart/update.

Post-update Lock Task Restored

Lock Task restored after update restart.

Post-update Policy Restore Failed

Policy restore failed; enter policy recovery/degraded state.

Forbidden log content:

Google account password/token
maintenance password/credential
APK signing private key or secret
cloud token
full sensitive config payload
raw Android identifier
## 13. QA / POC Requirements

Test Area

Expected Evidence

Self Update available

Device can check manifest and detect update.

APK validation

Invalid package/checksum/signature is rejected.

Runtime guard

Update is deferred during recording/emergency/finalization/recovery.

Policy restore

Lock Task/User Restrictions restored after update/restart.

No-EMM behavior

Managed Google Play/policy-driven update is marked not applicable.

Play Store fallback

Tested only if GMS/Play Store exists and approved process exists.

Personal account block

Personal Google account update path is not accepted for production maintenance.

Failure handling

Failed install does not leave device unrestricted.

## 14. Remaining TBD Items

Item

Why Still TBD

Exact manifest JSON schema

Implementation/API design decision.

Artifact provider exact implementation: WebServer/R2/local factory source

Deployment/infrastructure decision.

Checksum algorithm and manifest field names

Security/implementation decision.

Exact Android install mechanism on target BodyCamera firmware

Device POC dependent.

Whether rollback is supported

Product/Security/release decision.

Manual Play Store fallback availability

Device POC + Product/Security decision.

## 15. Practical Conclusion

Self Update Design sở hữu primary APK update flow: artifact, manifest, validation, install, result, recovery và rollout/rollback direction.

Project-wide EMM/Device Owner/update baseline được reference từ ADR và Architecture Home. System Settings sở hữu AutoUpdate precondition; Kiosk Policy sở hữu policy constraint; In-App Console sở hữu controlled fallback UX; Security Design sở hữu package/update credential constraint. Các exact schema, algorithm, install mechanic và rollout value còn lại phải được quyết định tại đúng authoritative owner.