# ADR – DCAM Device API Credential & mTLS Baseline

**Page ID**: 70287362  
**Version**: 1  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/70287362

---


# ADR – DCAM Device API Credential & mTLS Baseline

Item

Information

Project

DCAM Android BodyCamera Application

Document Type

Architecture Decision Record

Version

Approved Direction 1.0

Status

Approved Direction

Approval Scope

Device-facing BFF credential/trust baseline: per-device mTLS, Android Keystore proof-of-possession, controlled enrollment/rotation/revocation and authority separation. Exact PKI, algorithms, validity values and production evidence remain Security Review gated.

Decision Date

2026-08-26

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Backend Lead / Security Reviewer / Factory Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.4 - Architecture Decision Records (ADR)

Target Audience

Android Developers, Backend/BFF Developers, Security, QA, Factory, Operations

Last Updated

2026-08-26

Dependencies / Blockers

Security Review; supported-device Keystore/PKI POC; OEM attestation trust evaluation; certificate lifecycle/runbook; BFF/API/Factory/QA implementation evidence.

Related Documents

ADR – DCAM Device Identity Baseline; DCAM Security & Encryption Design; DCAM Android Operation Design; 06 - Cloud Services, Update & Configuration Architecture; DDMP 03 Management API / BFF Architecture Baseline; DDMP 06 Device Integration Contracts; DDMP 07 Security, RBAC & Audit; DDMP 10 Physical Deployment & Network Connectivity Architecture

## 1. Status and intent

This ADR is an Approved Direction, not a production-security conclusion. It establishes the only approved credential model for DCAM device-facing BFF APIs. Detailed protocol values, PKI deployment, cryptographic algorithms, certificate validity, revocation implementation and supported-device evidence remain Security Review and POC gates.

This ADR does not change the approved authority model:

DCAM = only Android Device Owner/DPC and privileged executor
BFF = device API, desired-state, certificate and audit boundary
Headwind Client = Application mode only
Headwind credential = server-side BFF integration only
Factory Portal = human administrative BFF/Thymeleaf interface
## 2. Context

DCAM devices are usually offline and connect outbound when Wi-Fi/IP is available. Device APIs are reachable through a controlled public edge, so an identifier alone must never authenticate a device.

The existing identity baseline remains unchanged:

serial_number = Hardware Identity / recovery key
dcam_cloud_device_id = Cloud Identity
platformDeviceId = BFF contract name for dcam_cloud_device_id
headwindDeviceRef = external mapping only
None of these values is a credential. A Factory Worker session, Headwind JWT, artifact credential, portal cookie and diagnostics-provider credential are also not device credentials.

## 3. Decision

### 3.1 Device credential

A DCAM device authenticates to BFF Device API through mutual TLS using one active, BFF-controlled client certificate bound to a device-generated asymmetric key pair.

private key      = generated and retained by DCAM in Android Keystore
public key       = registered to BFF during controlled enrollment
client certificate = issued/bound by BFF-controlled Device PKI
certificate fingerprint/key ID = BFF lookup input
platformDeviceId = authorization mapping result, not client-supplied authority
The private key must not be exported, serialized, copied to local files, sent to BFF, logged or placed in QR/configuration data. Android Keystore hardware-backed/StrongBox protection is preferred when available.

### 3.2 Request authorization

For every device-facing request, BFF must:

complete TLS and validate the client certificate against approved Device PKI trust;

check certificate fingerprint/key ID is active, unexpired and not revoked;

map that credential server-side to exactly one permitted platformDeviceId;

authorize the route and requested resource against that mapping;

validate request schema, capability and command/version semantics;

write the required security/business audit result.

A request-supplied serial_number, platformDeviceId, Headwind reference or installation ID must never override this server-side mapping.

### 3.3 Scope separation

Caller

Credential class

Permitted surface

DCAM device

Per-device mTLS client certificate

Device API only, for example /device/v1/**

Factory Worker

BFF human session/identity

Factory Portal / administrative provisioning only

Platform administrator

IdP/SSO/MFA session

Approved BFF administrative surface

BFF service

Least-privilege internal service credential

Headwind/release/diagnostics-provider server integration

Headwind Client

Headwind application credential

Headwind service only

A DCAM device must never call administrative or Factory Portal endpoints, and no worker/browser session may be used as device authentication.

## 4. Enrollment and first certificate

Enrollment requires a controlled factory/support transaction, not knowledge of a serial number.

DCAM generates an asymmetric key pair in Android Keystore and exposes a QR containing only non-secret pairing data: serial_number, public-key fingerprint and a fresh correlation/nonce value.

Authorized Factory Portal flow validates the QR, worker/workspace authority and identity create/restore policy. BFF records a short-lived pending enrollment bound to serial_number, public-key fingerprint and correlation ID.

DCAM establishes an outbound connection and proves possession by signing a BFF challenge with the Keystore private key.

BFF verifies the proof against the pending enrollment public key, binds credential to platformDeviceId and issues/returns the client certificate chain according to Device PKI design.

BFF writes success, rejection, conflict and expiry audit events. DCAM stores only certificate/public metadata needed for use; it does not export the private key.

The QR must not contain a long-lived secret, API key, password or private key. Exact QR signature, nonce/freshness, challenge and transfer encoding are defined in the Device Integration Contract and Security Review.

## 5. Lifecycle, recovery and offline behaviour

Event

Required behaviour

Normal operation

Device uses active certificate for BFF Device API; local recording/evidence work remains independent of network availability.

Rotation due

Authenticated device requests a replacement credential using the existing key/certificate and proof of possession. BFF applies approved overlap/grace policy.

Certificate revoked

BFF rejects future online API calls and audits the reason. It must not unlock/interrupt local recording, kiosk or evidence preservation.

Key/certificate loss after reset/reinstall

Device does not recover authentication from serial_number alone. It enters restricted recovery/enrollment state and requires authorized re-enrolment.

Rework / ownership/security incident

BFF revokes old credential, invalidates pending actions as required and permits a new factory/support enrollment only with audit.

Prolonged offline

DCAM retains bounded local queues and applies safe cached behavior. Credential expiry/rotation is reconciled when network returns according to approved policy.

Certificate validity, rotation timing, grace duration, certificate revocation distribution and emergency recovery values are deliberately not fixed by this ADR.

## 6. Attestation posture

Keystore/hardware-backed status is a device-security posture signal. BFF may collect and verify key-attestation evidence only when an approved verifier and OEM trust chain are available.

GMS-free deployment must not assume a Google attestation root is available or trusted. Devices without a validated hardware-attestation chain may be admitted only under an explicitly approved risk policy; they must not self-assert a trusted hardware level.

## 7. Developer rules

DEV-CRED-001  Generate one asymmetric device key pair in Android Keystore.
DEV-CRED-002  Do not export, log, serialize or copy the private key.
DEV-CRED-003  Use per-device mTLS for every Device API request.
DEV-CRED-004  Do not use serial_number, platformDeviceId, QR data, Headwind JWT or a static API key as device authentication.
DEV-CRED-005  BFF maps certificate fingerprint/key ID to platformDeviceId server-side; client values cannot select another device.
DEV-CRED-006  Device code may call only Device API routes and never Factory Portal/admin routes.
DEV-CRED-007  Enrollment requires factory/support authorization plus proof of possession; serial number alone is insufficient.
DEV-CRED-008  Rotate/revoke/re-enrolment must be auditable and use stable correlation/reason codes.
DEV-CRED-009  Device mutations retain requestId/eventId idempotency; mTLS does not replace replay-safe business semantics.
DEV-CRED-010  Credential failure must reject/defer network control safely and must not compromise local recording/evidence preservation.
DEV-CRED-011  Do not add a GMS-only dependency for credential, attestation or device authentication without a controlled ADR/security review.
## 8. Explicit non-goals

This ADR does not approve an external IdP, MFA policy, exact CA/vendor, certificate algorithm, certificate lifetime, remote key provisioning service, database schema, Headwind protocol, device hardware attestation guarantee or production rollout. Those are downstream design/POC decisions.

## 9. Consequences

Android implementation needs a credential lifecycle component and explicit operational states. BFF needs Device PKI integration, mTLS validation, certificate-to-device mapping, revocation/rotation services and audit. Factory Portal needs pending enrollment approval but never receives device private key. QA needs positive, negative, clone, revoke, rotation, reinstall/re-enrolment and offline recovery tests.