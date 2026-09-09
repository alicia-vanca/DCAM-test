# DCAM Security & Encryption Design

**Page ID**: 48496720  
**Version**: 22  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496720

---


# DCAM Security & Encryption Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

1.11

Status

Approved Pending Security Review

Approval Scope

Authentication, authorization, credential-protection, DDMP Hybrid trust boundary and sensitive-data constraints are approved directions; exact cryptographic and policy values remain open.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / BDMA Lead / Cloud Lead / Android Lead / Web Portal Lead / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Developers, QA, Security Reviewer, Factory, Support

Last Updated

2026-08-26

Related Jira

None

Related Documents

[DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry), [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix), DCAM Web Portal & Device API Contract, DCAM Device Provisioning Web Portal Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Logging & Diagnostics Design, DCAM Factory Provisioning & Device Production SOP, DCAM Self Update Design

Dependencies / Blockers

Security Review: approve exact cryptographic algorithm, key-management, QR/maintenance policy values and security-test evidence; no Production security claim before closure.

## 1. Current Security Baselines

### 1.1 GMS-free Android Runtime Security Guard

**ADR - DCAM GMS-free Android Runtime Baseline** is authoritative.

Prohibited: com.google.android.gms:*, Google Play Store/com.android.vending,
Google account device maintenance, FCM, Firebase Analytics, Play Integrity,
Google Sign-In, Google Maps Android SDK and Android Firebase Remote Config baseline.
Security/release evidence must inspect resolved release runtime dependencies, manifest/source flows and target-device POC. Crashlytics is permitted only as optional bounded crash/stability telemetry; it is not an authentication, integrity or production-control dependency.

Project-wide Device Identity, Web Portal và Device Owner/Kiosk baseline không được copy lại tại trang này.

Baseline Topic

Authoritative Reference

Current project and architecture baseline

DCAM Project Home / DCAM Architecture Home

Document status and approval scope

DCAM Document Status Registry

Requirement/test coverage

DCAM Requirement–Design–Test Traceability Matrix

Device Identity

ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id

Web provisioning contract

DCAM Web Portal & Device API Contract

Device Owner / EMM / Lock Task direction

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

Kiosk and maintenance behavior

DCAM Android Device Owner & Kiosk Policy Design

Operational Logging and Crashlytics ownership

07 - Logging, Diagnostics, Performance & Security / DCAM Logging & Diagnostics Design

Local implementation impact của Security Design:

Định nghĩa authentication, authorization, credential protection, sensitive-data sanitization và secret-handling constraint.

Định nghĩa security guard cho QR, provisioning, maintenance, logging, update và policy recovery.

Không thay đổi identity semantics, provider ownership hoặc Device Owner direction của authoritative document.

Status interpretation:

Approved Pending Security Review
    = approved security direction and constraints
    ≠ approved production cryptographic profile
    ≠ production security certification
## 2. Credential and Sensitive-data Direction

The protection direction is already defined and is not wholly `TBD`:

No plaintext credential storage
No hardcoded default maintenance credential
Use protected representation
Prefer Android Keystore / hardware-backed protection when available
Never log credential input or protected secret material
Exact complexity, rotation, reset/recovery, failed-attempt and session-timeout values remain TBD.

Factory Wi-Fi is an explicit project exception. It must not appear in Operational Logging, Crashlytics, QR, API payloads, production records, support evidence or normal UI.

## 3. Web Portal Security

Area

Baseline

Authentication

BFF session/identity integration.

Authorization

Backend validates active Factory Worker profile.

Backend authority

Spring Boot BFF owns provisioning writes and audit.

Serial

QR-derived and read-only.

Camera privacy

Scan frames/images are not stored or uploaded.

Browser storage

No long-lived credential, raw QR payload or factory Wi-Fi value.

Conflict

Worker cannot override duplicate/rebind/restricted device state.

QR minimum logical fields are defined. Exact serialization, signature algorithm, nonce, expiration and replay policy remain TBD.

## 4. Kiosk and Maintenance Security

Approved entry model:

Admin or approved Maintenance role
    ↓
Maintenance Password Gate
    ↓
runtime safe-state validation
    ↓
Controlled Mode with approved targets only
    ↓
policy restore
Exact approved target list and credential-policy values remain TBD. Emergency override does not satisfy the maintenance gate.

## 5. Logging Security

Forbidden across local logs, queue, Loggly, Crashlytics and `logs.txt`:

credential values
auth/access tokens
maintenance secret
factory Wi-Fi value
APK signing private material
provisioning secret
raw Android system identifier
raw sensitive media/sensor/AI/biometric content
Operational Logging and Crashlytics must use bounded safe context and stable reason codes.

## 6. Update Security

Approved direction:

trusted artifact source
package identity validation
checksum validation
signature validation
version/compatibility validation
runtime and kiosk safety guard
post-update policy restore
Exact checksum algorithm/field names, signature comparison method and target-device install mechanism remain TBD.

## 7. Encryption Boundary

Encryption is still a genuine open implementation area.

Item

Status

Media encryption requirement by release/profile

TBD / Product + Security

Exact algorithm/mode

TBD / Security Review

Key generation/storage/rotation/recovery

TBD / Security Review

SQLite encryption requirement

TBD

BDMA decryption/key exchange

TBD / Data Contract + Security

Encrypted-media naming does not approve a cryptographic algorithm.
No requirement, design, QA or release document may claim AES-256 or another algorithm is production-approved until Security Review closes this section.
## DDMP Hybrid Security Boundary

Hybrid security preserves the approved authority model: DCAM is the sole Device Owner/DPC and privileged executor; Headwind Client is Application mode only; BFF is the device-facing management and audit boundary.

Boundary

Approved security direction

Device to BFF

Device authenticates only to BFF using a protected device credential/certificate representation. Exact protocol, issuance, rotation and revocation values require Security Review.

BFF to Headwind

BFF holds a least-privilege internal Headwind service credential. Device, portal/browser and Headwind Client must not receive it.

Release authorization

BFF authorizes a release; R2/CDN distributes an immutable artifact. R2 write credentials and APK signing private material never reach device/portal/browser. Exact URL/token/signing mechanics remain Security Review items.

Local enforcement

DCAM validates package identity, integrity/signature, compatibility and safe runtime state before installation. No Headwind status/configuration can directly execute DevicePolicyManager, Lock Task, restriction or install action.

Identity/audit

`dcam_cloud_device_id` is the platform device association; `serial_number` is recovery key; Headwind reference is mapping-only. Audit/log correlation must use bounded, access-controlled non-secret context and never credential material.

Failure/revocation

Credential/release/replay anomaly must cause BFF/DCAM rejection or deferral with safe reason; it must not unlock or interrupt recording/evidence preservation.

Security and observability counterparts: [DDMP 07 Security, RBAC & Audit](/wiki/spaces/DVID/pages/68747311/07+Security+RBAC+Audit), [DDMP 08 Operations, SLO & Runbooks](/wiki/spaces/DVID/pages/68812869/08+Operations+SLO+Runbooks) and [DDMP 03 BFF](/wiki/spaces/DVID/pages/68812826/03+Management+API+BFF+Architecture+Baseline), [DDMP 05 R2 Release](/wiki/spaces/DVID/pages/68780056/05+APK+Release+Cloudflare+R2), [DDMP 06 Device Integration Contracts](/wiki/spaces/DVID/pages/68812848/06+Device+Integration+Contracts). Hybrid is Phase 2+/POC-gated; Build 0.1 has no DDMP dependency.

## 8. Resolved and Remaining Decisions

Item

Status

Web auth provider

Approved Direction: BFF session/identity integration

Worker authorization

Approved Direction: backend profile validation

QR serial behavior

Approved Direction: QR-only and read-only

Maintenance entry model

Approved Direction

Credential representation direction

Approved Direction: protected, non-plaintext, Keystore preferred

Factory Wi-Fi handling

Approved Direction with non-disclosure constraints

Exact maintenance policy values

TBD / Security + Product

QR cryptographic format

TBD / Security Review

Exact BFF authorization/claims/session model

TBD / Backend + Security

Update checksum/signature details

TBD / Security Review

DDMP device credential, release-authorization and R2 access mechanics

TBD / Security Review + DDMP Contract

Media/DB encryption and key management

TBD / Security Review

Retention for security/audit events

TBD / Security + Deployment

## 9. Approval Gate to Production

The page may move to `Production Approved` only when all applicable items below are complete:

Gate

Required Evidence

Active encryption profile

Approved algorithm/mode and activation scope.

Key lifecycle

Generation, storage, rotation, recovery and revocation decision.

BDMA boundary

Approved decryption/key-exchange behavior where encryption is active.

QR security

Serialization, integrity/authenticity, nonce/expiry/replay decision.

Maintenance policy

Credential complexity, failed attempts, timeout, reset/recovery and audit.

Update validation

Checksum/signature/package identity verification detail.

DDMP trust boundary

Device/BFF credential lifecycle, BFF–Headwind least privilege, release authorization and R2 access mechanics.

Security QA

Applicable QA IDs executed with evidence.

Device/deployment evidence

Target environment validates required protection mechanisms.

## 10. Practical Conclusion

Authentication, authorization, maintenance entry, protected credential direction, sensitive logging rules and DDMP Hybrid trust-boundary constraints are approved directions.
Exact cryptographic, policy-value, retention and target-device security details remain open.
Current status is Approved Pending Security Review, not Production Approved.
## Device API credential and mTLS direction

The authoritative credential decision is [ADR – DCAM Device API Credential & mTLS Baseline](/wiki/spaces/DVID/pages/70287362/ADR+DCAM+Device+API+Credential+mTLS+Baseline). DCAM Device API authentication uses one active per-device mTLS client certificate bound to an asymmetric Android Keystore key. Private key material must not be exported, placed in app-private files, QR/configuration, logs or BFF storage.

DCAM must distinguish serial_number/platformDeviceId identity from credential. Factory Worker sessions, Headwind JWT, artifact credentials and diagnostics-provider credentials are prohibited as Device API authentication. Credential loss, revoke, rotation and re-enrolment must reject or defer online control safely without unlocking or interrupting recording, kiosk or evidence preservation.

Exact Device PKI, certificate profile/lifetime, revocation distribution, attestation verification and supported-device policy remain Security Review and POC gates.