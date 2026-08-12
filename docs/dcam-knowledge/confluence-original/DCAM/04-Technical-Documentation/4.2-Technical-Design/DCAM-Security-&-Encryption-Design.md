# DCAM Security & Encryption Design

**Page ID**: 48496720  
**Version**: 18  
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

1.7

Status

Approved Pending Security Review

Approval Scope

Authentication, authorization, credential-protection direction, maintenance-entry model and sensitive-data constraints are approved; exact cryptographic and policy values remain open.

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

2026-07-20

Related Jira

None

Related Documents

[DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry), [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix), DCAM Web Portal & Device API Contract, DCAM Device Provisioning Web Portal Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Logging & Diagnostics Design, DCAM Factory Provisioning & Device Production SOP, DCAM Self Update Design

Dependencies / Blockers

Security Review: approve exact cryptographic algorithm, key-management, QR/maintenance policy values and security-test evidence; no Production security claim before closure.

## 1. Current Security Baselines

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

Firebase Authentication.

Authorization

Backend validates active Factory Worker profile.

Backend authority

Cloud Functions owns provisioning writes and audit.

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
## 8. Resolved and Remaining Decisions

Item

Status

Web auth provider

Approved Direction: Firebase Authentication

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

Exact Firebase Security Rules/claims model

TBD / Backend + Security

Update checksum/signature details

TBD / Security Review

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

Security QA

Applicable QA IDs executed with evidence.

Device/deployment evidence

Target environment validates required protection mechanisms.

## 10. Practical Conclusion

Authentication, authorization, maintenance entry, protected credential direction and sensitive logging rules are approved directions.
Exact cryptographic, policy-value, retention and target-device security details remain open.
Current status is Approved Pending Security Review, not Production Approved.