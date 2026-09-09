# DCAM Device Provisioning Web Portal Implementation Design

**Page ID**: 51019802  
**Version**: 9  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51019802

---


# DCAM Device Provisioning Web Portal Implementation Design

Metadata

Value

Document Type

Implementation Design

Status

Approved Direction

Version

1.5

Last Updated

2026-08-26

Scope

Factory Portal; implementation begins no earlier than Build 0.2

## 1. Implementation baseline

Implement Factory Portal as a bounded module of the Spring Boot BFF using Spring MVC and Thymeleaf. It runs inside the ddmp-bff Docker container on Server A, independently of the headwind-tomcat container. It writes authoritative provisioning data in host-native PostgreSQL database ddmp on Server B. The implementation must preserve: DCAM as sole Device Owner/DPC, BFF as API/audit boundary, serial_number as QR-only recovery key and dcam_cloud_device_id/platformDeviceId as canonical cloud identity.

## 2. Logical module structure

Module

Responsibility

factory-portal-web

Spring MVC controllers, Thymeleaf templates, view models and safe error rendering.

factory-authz

Authenticated-subject/session integration and server-side Factory Worker/workspace authorization.

factory-provisioning-service

Create/restore orchestration, QR pairing validation, idempotency/conflict policy and transaction boundary. It maps to application.registry/application.enrollment in the DDMP BFF Bootstrap.

device-identity-service

Canonical identity resolution exposed through separate admin and device-facing use cases. It must not authenticate a device by serial alone.

factory-enrollment-service

Creates/rejects/expires PENDING_ENROLLMENT bound to serial, public-key fingerprint, pairing nonce/correlation, platformDeviceId and expiry. It never issues a certificate merely from a browser request.

factory-audit-service

Append-oriented provisioning and pending-enrollment audit with correlation and actor context.

ddmp-repositories

Server-side PostgreSQL access. No repository or DB credential is reachable from browser code.

These are logical boundaries, not a mandatory package layout.

## 3. Request flow

sequenceDiagram
  participant W as Factory Worker
  participant P as Thymeleaf Portal
  participant B as Spring Boot BFF
  participant D as PostgreSQL ddmp
  W->>P: Scan DCAM QR pairing payload and submit
  P->>B: POST admin provisioning request
  B->>B: Authenticate, authorize, validate QR pairing
  B->>D: Resolve/create/restore + pending enrollment + audit
  D-->>B: Transaction result
  B-->>P: Safe outcome and correlation ID
The BFF performs create/restore, pending-enrollment creation, conflict handling and audit in one controlled transaction boundary. If transaction semantics cannot include an audit write, the implementation must use a documented durable outbox/equivalent before production approval.

## 4. Persistence model

The final DDL is pending, but the BFF data model must represent:

Factory Worker subject and workspace authorization.

Serial-to-canonical-device identity lookup.

Device provisioning state and create/restore idempotency key.

Pending-enrollment record: public-key fingerprint, pairing nonce/correlation, target platformDeviceId, expiry and lifecycle outcome. It contains no device private key.

Append-oriented provisioning audit event.

Schema version/migration ownership for ddmp.

Use a dedicated BFF database role/pool and migration path. Do not query Headwind database hmdm, use shared tables, or grant browser clients SQL access.

## 5. Endpoint and error behavior

Administrative action:

```
POST /admin/v1/factory/provisioning/devices
```

The controller must require authenticated Factory Worker authorization, apply CSRF/session controls, accept raw QR payload or approved scan reference, validate/extract pairing fields server-side, pass only validated input to service code and map outcomes to stable reason codes. It must never return stack traces, raw SQL errors, token material, authorization configuration or secrets.

The Android/device-facing enrollment/identity endpoint is separate. After factory BFF creates PENDING_ENROLLMENT, DCAM proves Android Keystore key possession against BFF challenge before certificate issue/binding. This path must not accept, mint or replay a Factory Worker browser session.

## 6. Test and release gates

Unit test QR pairing parsing/validation, workspace RBAC, serial conflict, create/restore idempotency, pending-enrollment expiry/fingerprint mismatch and audit failures.

Integration test BFF transaction rollback/outbox behavior, PostgreSQL migrations, CSRF/session enforcement and reason-code stability.

E2E test scan-to-provision-to-pending-enrollment-to-DCAM proof-of-possession/certificate binding using an isolated non-production environment.

Dependency/release check verifies no external web, authentication or data SDK is in Factory Portal.

Security review approves IdP/protocol, session/MFA, QR pairing cryptographic policy, Device PKI/nonce/expiry contract, database role, retention and production environment separation.

## 7. Non-goals

No direct device database writes, manual serial entry, device owner policy, Headwind provisioning authority, fleet portal features or Build 0.1 activation are introduced by this implementation.