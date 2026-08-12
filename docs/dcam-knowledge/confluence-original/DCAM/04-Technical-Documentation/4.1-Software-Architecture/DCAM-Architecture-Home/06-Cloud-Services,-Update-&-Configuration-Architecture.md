# 06 - Cloud Services, Update & Configuration Architecture

**Page ID**: 47120459  
**Version**: 28  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120459

---


# 06 - Cloud Services, Update & Configuration Architecture

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Cloud, Update & Configuration Architecture

Version

Approved 3.3

Status

Approved

Approval Scope

Cloud, update và configuration architecture boundaries; exact API/schema thuộc Contract, security values thuộc Security Design.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Cloud Lead / Security Reviewer / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, Web/Backend Developers, QA, Factory, Cloud Team

Last Updated

2026-07-14

Related Jira

None

Related Documents

DCAM Web Portal & Device API Contract, DCAM Device Provisioning Web Portal Design, DCAM Device Provisioning Web Portal App Design, DCAM Device Provisioning Web Portal Implementation Design, DCAM Factory Provisioning & Device Production SOP, 09 - System Settings Requirements, DCAM Android Device Owner & Kiosk Policy Design, DCAM Self Update Design, DCAM Security & Encryption Design

## 1. Purpose

Trang này định nghĩa cloud/provider boundary cho provisioning, device identity, remote config và update.

Current baseline:

Web frontend = Firebase Hosting
Web authentication = Firebase Authentication
Web backend = Firebase Cloud Functions
Cloud storage = Firebase Cloud Firestore
Firebase Realtime Database = not used
Primary update path = DCAM Self Update / approved APK artifact provider
## 2. Identity and Provisioning Baseline

serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity / primary cloud device id
serial_lookup/{serial_number} = create/restore lookup
SD Identity File = recovery cache only
No production dependency on `ANDROID_ID`, `android_id_hash` or `device_lookup/{android_id_hash}`.

Approved business flow:

DSetup verifies imported serial
    ↓
DCAM displays provisioning QR
    ↓
Factory Worker logs in
    ↓
Workspace scans QR and shows serial read-only
    ↓
Factory Worker enters owner/date and submits
    ↓
Cloud Functions creates/restores cloud identity
Device Owner setup is separate from Web business provisioning.

## 3. Current Cloud Components

Component

Responsibility

Status

Firebase Hosting

Host Web Portal frontend.

Approved

Firebase Authentication

Authenticate Factory Worker.

Approved

Firebase Cloud Functions

Backend authority for provisioning and audit.

Approved

Firebase Cloud Firestore

Worker profile, serial lookup, device and audit data.

Approved

Firebase Realtime Database

Không dùng.

Not Applicable

Android cloud adapter

Fetch/restore identity/config through approved API/provider interface.

Approved Direction

APK Artifact Provider

Manifest/APK delivery for Self Update.

Approved boundary; exact deployment TBD

Provider abstraction remains required even though current implementations are selected.

## 4. Firestore Logical Contract

Logical paths are defined in **DCAM Web Portal & Device API Contract**:

workers/{firebase_uid}
serial_lookup/{serial_number}
devices/{dcam_cloud_device_id}
audit_events/{audit_event_id}
These names are no longer generally `TBD`. Environment-specific project/database identifiers, index definitions, retention and exact Security Rules remain deployment/security details.

Frontend access rule:

Frontend authenticates with Firebase Authentication.
Frontend calls Cloud Functions/backend.
Frontend must not direct-write serial_lookup, devices or audit_events.
## 5. Provisioning API Boundary

Logical endpoint:

```
POST /v1/factory/provisioning/devices
```

Concrete Cloud Function name, Hosting rewrite and physical deployed URL remain implementation/deployment details.

Backend responsibilities:

verify Firebase identity token
verify active Factory Worker profile
validate QR-derived serial and business fields
create/restore device identity transactionally
write audit event
return stable result/reason code
## 6. QR Contract Boundary

Minimum logical fields are defined:

payload_type
payload_version
serial_number
app_package_name
app_version_name
app_version_code
device_model
firmware_version
Optional fields include nonce, timestamps, signature and contract metadata. Exact serialization/signature/expiration/replay policy remains TBD under API Contract and Security Design.

## 7. Remote Configuration

Remote Config baseline is approved:

publish target revision
    ↓
device fetches by dcam_cloud_device_id
    ↓
validate schema/version/allowed fields/capability
    ↓
cache pending config in dcam.db
    ↓
apply only when runtime guard allows
    ↓
report applied revision/result
Initial setting groups and kiosk requested-policy keys are defined in **09 - System Settings Requirements**. Exact field-level schema, rollout algorithm, wake-up/polling values and profile model remain TBD.

Remote config does not directly modify Android system policy or `dcam_config.cson` operational settings.

## 8. Device Owner / Kiosk Boundary

Current direction is not wholly TBD:

No external EMM / Android Management API / Managed Google Play
Preferred model = DCAM-as-DPC / local Device Owner when supported
Factory baseline = DSetup + ADB dpm set-device-owner when required
Maintenance entry = authorized role + Maintenance Password Gate + Controlled Mode
Exact DPC component/wrapper, OEM feasibility, restriction support and package allowlist remain Device POC/implementation details.

## 9. Update Boundary

Approved update guard:

Self Update request
    ↓
check runtime and AutoUpdate preconditions
    ↓
load manifest/download APK
    ↓
validate identity/checksum/signature/version/compatibility
    ↓
install through approved target-device path
    ↓
verify version and restore kiosk policy
Policy-safe update behavior is defined by System Settings, State Machine, Kiosk Policy and Self Update Design. Only artifact provider deployment, manifest fields, algorithms and target-device install mechanics remain TBD.

## 10. Resolved and Remaining Decisions

Item

Status

Web frontend/auth/backend/storage

Approved: Hosting/Auth/Functions/Firestore

REST/backend vs direct Firestore writes

Resolved: frontend calls backend; no direct production writes

Logical Firestore paths

Approved in API Contract

Logical provisioning endpoint

Approved

Web Portal actor/UI model

Approved: Factory Worker, Login + Workspace, QR-only

QR minimum logical fields

Approved

DCAM-as-DPC ownership direction

Approved direction; feasibility POC required

Factory Device Owner method

Approved baseline: DSetup + ADB `dpm set-device-owner`

Policy-safe update contract

Approved direction

Environment-specific Firebase Security Rules/indexes

TBD / Security + Deployment

QR cryptographic policy

TBD / API + Security

Remote config exact payload/rollout/wake-up

TBD

APK provider deployment and install mechanics

TBD / Deployment + Device POC

Owner validation/account lifecycle

TBD / Product + Factory + Security

## 11. Practical Conclusion

Cloud/Web implementation and logical provisioning contract are already selected.
The remaining TBDs are deployment, cryptographic, policy-value and target-device details—not the overall architecture.