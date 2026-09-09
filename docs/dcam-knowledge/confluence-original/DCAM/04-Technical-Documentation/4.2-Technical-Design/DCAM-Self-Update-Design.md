# DCAM Self Update Design

**Page ID**: 48529439  
**Version**: 14  
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

Draft 1.3

Status

Draft

Approval Scope

Draft self-update design, aligned with approved DDMP authority boundary and GMS-free Android Runtime ADR: BFF authorizes release, R2/CDN distributes immutable artifact, DCAM DPC validates and installs. Exact schema, algorithm, install mechanic and Production rollout evidence remain Draft.

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

2026-08-25

Related Jira

None

Related Documents

09 - System Settings Requirements, 06 - Cloud Services, Update & Configuration Architecture, DCAM Device Owner ADR, DCAM Device Identity ADR, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Android Operation Design, DCAM State Machine Design, DCAM Device Capability & Feature Eligibility Design, DCAM Security & Encryption Design, DCAM QA Test Strategy & Test Matrix, DCAM Device POC & Hardware Validation Report, DDMP 01 Hybrid System Architecture, DDMP 03 Management API / BFF, DDMP 05 APK Release & Cloudflare R2, DDMP 06 Device Integration Contracts

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

Controlled Mode và Maintenance Password Gate UX — thuộc **DCAM In-App Operation, Device Settings & Media Console Design**; không có Play Store fallback.

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

Controlled Maintenance supports approved DCAM/local-factory update and recovery only; no Play Store target exists.

Update provider architecture

06 - Cloud Services, Update & Configuration Architecture

BFF authorizes release, Cloudflare R2/CDN distributes immutable artifact, DCAM DPC executes; Managed Google Play not applicable.

DDMP release/command contract

DDMP 03 Management API / BFF + DDMP 06 Device Integration Contracts

BFF desired-state/release reference and DCAM ACK; Headwind is not a privileged update transport.

Update priority / blocked state guard

DCAM State Machine Design

Update có priority thấp hơn emergency, recording, finalizing, storage/DB recovery, policy recovery và unsafe runtime states.

Capability/runtime safety

DCAM Device Capability & Feature Eligibility Design

Update không chạy khi capability evaluation hoặc unsafe runtime initialization đang active; GMS-free compliance is a mandatory release gate.

Package/update security

DCAM Security & Encryption Design

APK identity, integrity, trusted source, signature and policy-safe update security.

## 3. Design Decision

Primary DDMP update path:
1. BFF authorizes a release and publishes versioned release desired-state for platformDeviceId (= dcam_cloud_device_id).
2. DCAM retrieves the release instruction through outbound BFF sync.
3. DCAM obtains immutable manifest/APK artifact from Cloudflare R2/CDN.
4. DCAM validates release authorization, package identity, signature, checksum, version and compatibility.
5. DCAM DPC installs through approved Android/package policy path only when runtime guard is safe.
6. DCAM verifies update result, restores kiosk policy and ACKs BFF.

Headwind Client/Community may provide fleet status/configuration only. They do not authorize, download, install or rollback DCAM APK.

No Play Store/Managed Google Play/Android Management API or Google-account update fallback is permitted. If remote update is unavailable, DCAM safely defers or uses only an explicitly approved local/factory APK package.
Self Update không được interrupt field operation. Exact safety preconditions được reference từ **09 - System Settings Requirements**.

## 4. Self Update Flow

Scheduled/manual update check or BFF release desired-state
    ↓
Confirm current device baseline uses DCAM Self Update path
    ↓
DCAM outbound BFF sync retrieves authorized release/manifest reference
    ↓
Load immutable version manifest from Cloudflare R2/CDN
    ↓
If newer authorized version exists
    ↓
Check AutoUpdate preconditions from System Settings
    ↓
Check Android Operation mode and State Machine guard
    ↓
Check Kiosk Policy state is safe for update
    ↓
Check storage/network/power/package constraints
    ↓
Download APK from R2/CDN
    ↓
Validate release authorization, APK package identity, signature, checksum, version and compatibility
    ↓
DCAM DPC installs according to approved Android/device policy path
    ↓
After restart/resume, verify app version
    ↓
Verify Device Owner/DPC state and re-enter Lock Task if required
    ↓
ACK/result/audit to BFF
Else
    ↓
Continue current version
No Google Play Store fallback exists. Controlled Maintenance Mode is used only for approved DCAM/local-factory update and recovery actions.

## 5. Update Source Priority

Priority

Source

Direction

Status

1

BFF-authorized DCAM Self Update from Cloudflare R2/CDN

Primary update path; BFF publishes release desired-state, R2/CDN serves immutable artifact, DCAM DPC validates/installs/ACKs.

Approved Direction

2

Local/factory APK package

Optional factory/support fallback if approved and validated.

Approved Direction / Process TBD

Not applicable

Google Play Store / Managed Google Play / Android Management API

Not a production update source.

Not Applicable

Not allowed

Google account maintenance update

Not supported.

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

UPD-DDMP-001

Only BFF release desired-state/manifest reference may request automated update; Headwind push/status is not authorization.

UPD-DDMP-002

DCAM must not call Headwind REST API/database or use Headwind JWT for update discovery, authorization or execution.

UPD-DDMP-003

Post-update outcome must be ACKed to BFF with release/command correlation; Headwind telemetry is supplementary only.

UPD-DDMP-004

If Headwind Client is present, its update/coexistence is a separate POC-approved package policy and must not disrupt DCAM kiosk recovery.

## 8. GMS-free Update Boundary

Google Play Store, Managed Google Play, Android Management API and Google-account-based maintenance are not DCAM update paths.

Rule

Description

UPD-GMS-001

DCAM must not launch, browse, install or update through Google Play Store / `com.android.vending`.

UPD-GMS-002

DCAM must not require Google Play services, FCM, Play Integrity, Analytics or a Google account for update discovery, authorization, download, install or ACK.

UPD-GMS-003

BFF-authorized immutable R2/CDN manifest/APK is the only remote production update path.

UPD-GMS-004

Approved local/factory APK package is the only separately controlled support fallback.

UPD-GMS-005

If BFF/R2 is unavailable, DCAM safely defers update and retains the current trusted APK; it must not switch to Play Store.

UPD-GMS-006

Resolved dependency graph, manifest/source scan and target-device POC must pass the ADR GMS-free release gates.

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

`DEFERRED_RELEASE_NOT_AUTHORIZED`

BFF desired-state/release authorization missing, expired or superseded.

`DEFERRED_R2_ARTIFACT_UNAVAILABLE`

Authorized artifact/manifest is temporarily unavailable from R2/CDN.

`BLOCKED_GMS_FREE_COMPLIANCE`

Prohibited runtime dependency, manifest/source flow or required GMS-free evidence is missing.

`BLOCKED_INVALID_PACKAGE`

APK/manifest/checksum/signature validation failed.

`BLOCKED_PACKAGE_IDENTITY_MISMATCH`

APK identity không match expected DCAM package identity.

`BLOCKED_PROHIBITED_UPDATE_SOURCE`

Google Play Store, Managed Google Play, Android Management API or Google-account update path was requested.

## 10. Artifact Direction

Artifact

Purpose

Status

Immutable APK object

DCAM installation package stored in Cloudflare R2 and delivered through CDN. Object must not be overwritten for the same release.

Approved Direction

Release Manifest

BFF-authorized reference to release ID, versionCode/name, immutable artifact URL/object reference, SHA-256/checksum, signing certificate digest, compatibility, expiry and rollback metadata.

Approved Direction / Exact Schema TBD

Checksum Field

Verify downloaded APK bytes against the immutable release manifest.

Approved Direction / Algorithm/Field TBD

Signature Metadata

Support package identity/signature validation.

Approved Direction / Exact Metadata TBD

Policy Compatibility Metadata

Indicates maintenance window, minimum Android version, policy constraints or Device Owner compatibility.

Approved Direction / Exact Schema TBD

Rollback/previous release metadata

Approved fallback reference used only through BFF release decision and DCAM safe-state guard.

Optional / TBD

BFF authorizes release and returns only the manifest/artifact reference needed by DCAM. R2/CDN stores and serves immutable artifacts; it does not authorize release or install. DCAM must not contain R2 S3 write credentials, Headwind JWT or arbitrary artifact URL trust.

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

Artifact source must be the BFF-authorized immutable R2/CDN manifest/artifact reference, not a user-provided arbitrary APK.

Approved Direction

Release Authorization

Release desired-state/manifest reference must be current and authorized by BFF for platformDeviceId.

Approved Direction

## 12. Logging Requirements

Event

Description

Update Check Started

Bắt đầu update check.

BFF Release Instruction Received

BFF desired-state/release reference received and correlated.

Manifest Loaded

Immutable manifest được load từ authorized R2/CDN artifact reference.

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

GMS-free source rejected

Prohibited update source/dependency was detected and update was blocked.

Post-update Version Verified

App version checked after update/resume.

Post-update Policy Verified

Device policy verified after restart/update.

Post-update Lock Task Restored

Lock Task restored after update restart.

Post-update Policy Restore Failed

Policy restore failed; enter policy recovery/degraded state.

BFF Update ACK Sent / Failed

DCAM outcome sent to BFF; retry safely if transport unavailable.

Forbidden log content:

Google-account credential (not supported in production device flow)
maintenance password/credential
APK signing private key or secret
cloud token
R2 S3 credential or signed administrative URL
Headwind JWT
full sensitive config payload
raw Android identifier
## 13. QA / POC Requirements

Test Area

Expected Evidence

BFF release contract

DCAM receives only authorized versioned release desired-state/manifest reference and uses `platformDeviceId = dcam_cloud_device_id`.

R2 immutable artifact

Artifact/manifest is downloaded from R2/CDN, integrity/signature is verified and an overwritten/replayed artifact is rejected.

Self Update available

Device can check manifest and detect update.

APK validation

Invalid package/checksum/signature is rejected.

Runtime guard

Update is deferred during recording/emergency/finalization/recovery.

Policy restore

Lock Task/User Restrictions restored after update/restart.

GMS-free source behavior

Google Play Store/Managed Google Play/Android Management API/Google-account update path is rejected.

GMS-free release gate

Dependency, manifest/source and target-device evidence pass before production profile claim.

Failure handling

Failed install does not leave device unrestricted.

ACK/recovery

DCAM sends idempotent outcome ACK to BFF after success/failure/defer; Headwind status alone is not treated as execution evidence.

Headwind coexistence

Headwind Client install/update/reboot does not disturb DCAM DPC, HOME, Lock Task or policy recovery.

## 14. Remaining TBD Items

Item

Why Still TBD

Exact manifest JSON schema

Implementation/API design decision.

Artifact provider exact implementation: WebServer/R2/local factory source

Resolved for DDMP path: Cloudflare R2/CDN. Local factory source remains a separately approved fallback process.

Checksum algorithm and manifest field names

Security/implementation decision.

Exact Android install mechanism on target BodyCamera firmware

Device POC dependent.

Whether rollback is supported

Product/Security/release decision.

GMS-free dependency/manifest/source gate

CI/release review evidence required.

## 15. Practical Conclusion

Self Update Design sở hữu primary APK update flow: BFF-authorized release desired-state, immutable R2/CDN artifact/manifest, validation, DCAM DPC install, result ACK, recovery và rollout/rollback direction.

Project-wide EMM/Device Owner/update baseline được reference từ ADR và Architecture Home. System Settings sở hữu AutoUpdate precondition; Kiosk Policy sở hữu policy constraint và Headwind Client coexistence; In-App Console sở hữu controlled fallback UX; Security Design sở hữu package/update credential constraint. Headwind Client/Community không phải update authority hoặc privileged execution path. Các exact schema, algorithm, install mechanic và rollout value còn lại phải được quyết định tại đúng authoritative owner.