# DCAM Device Provisioning Web Portal App Design

**Page ID**: 50692194  
**Version**: 9  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50692194

---


# DCAM Device Provisioning Web Portal App Design

Metadata

Value

Document Type

Application Design

Status

Approved Direction

Version

1.6

Last Updated

2026-08-26

Scope

Factory Portal UI only; Secure Platform MVP Build 0.2 minimum

## 1. Purpose

The Factory Portal is a **server-rendered Thymeleaf UI inside the Spring Boot BFF**. It runs inside the ddmp-bff Docker container on Server A; it is not a separate Portal container or a WAR in Headwind Tomcat. It supports authorized factory workers to scan a DCAM-generated QR and request device identity creation or restoration. PostgreSQL ddmp remains behind the BFF; the browser has no direct database access.

The portal is not a customer portal, a DDMP fleet portal or an Android management client. It never becomes Device Owner and does not bypass DCAM.

## 2. Runtime composition

Layer

Design

Browser

Minimal HTML/JS for UX and QR scanning; no business authority, database SDK or device credential persistence.

Presentation

Thymeleaf templates rendered by Spring MVC in the BFF ddmp-bff Docker service.

Application

Factory controller, authorization/session adapter, provisioning and pending-enrollment service, QR pairing validation/conflict policy and audit service.

Data

BFF repositories access PostgreSQL ddmp hosted natively on Server B in server-side transactions only.

No Firebase/BaaS, hosted data provider or direct browser data platform is used by this application. An approved external or self-hosted IdP may be integrated by BFF only for authentication/session security.

## 3. Screens and interaction

Screen

Behaviour

Sign-in/session entry

Delegates sign-in/session establishment to the BFF-approved identity integration. Shows no account-existence detail.

Workspace selection

Displays only workspaces the server authorizes for the current actor.

QR scan / review

Requests camera permission only while scanning. The UI displays extracted serial_number read-only; public-key fingerprint and pairing nonce are not editable/displayed as identity inputs. BFF receives raw QR payload or approved scan reference and validates/extracts all pairing values server-side.

Provision result

Shows outcome, canonical cloud identity when allowed, pending-enrollment state, correlation ID and safe reason code. It never shows certificate/challenge/private-key material.

Error / retry

Shows actionable but non-sensitive errors; no stack trace, database detail or authorization configuration.

The server re-validates every form/QR field; client-side checks are usability only.

## 4. Request model

The portal submits same-origin form or XHR requests to BFF administrative endpoints. The canonical action is:

```
POST /admin/v1/factory/provisioning/devices
```

Required logical input is a raw QR pairing payload, or an approved server-side scan reference, plus authorized factory context. BFF extracts and validates serial_number, public-key fingerprint and pairing nonce/correlation; the browser must not submit these as separately editable authority fields. dcam_cloud_device_id/platformDeviceId is BFF-generated/restored output, not manually editable input. The administrative transaction creates PENDING_ENROLLMENT only; DCAM later proves Keystore key possession through the separate Device API before a certificate is issued. Exact payload field names and QR cryptographic fields are owned by the API/QR/Device PKI contract.

## 5. UI security rules

Use BFF session/authentication integration and server-side RBAC for every page and action.

Protect state-changing requests with the approved CSRF/session mechanism.

Do not put secrets, credentials, Wi-Fi password, raw token, device certificate/private-key material or long-lived identity in HTML, URL, local storage or logs.

Do not expose administrative pages to device clients; device identity restore uses a distinct device-facing BFF contract.

Accessibility, error localization and camera fallback UX are implementation concerns, provided they preserve QR-only / read-only serial policy.

## 6. Deferred decisions

The IdP provider/protocol, MFA requirement, concrete session implementation, CSRF library, camera scanning library, QR pairing format and UI visual standard remain TBD and must be selected without changing this authority model. Runtime placement is approved: Thymeleaf runs inside ddmp-bff Docker on Server A; ddmp is host-native PostgreSQL on Server B.

## 7. Acceptance criteria

An unauthorized actor cannot render or submit a provisioning action.

A worker cannot alter serial_number or submit a manual serial.

Every result is produced by BFF validation and results in a correlated provisioning/pending-enrollment audit outcome.

The browser cannot access ddmp directly.

The page and dependency tree contain no Firebase/BaaS/direct-data SDK for provisioning; any IdP integration stays server-side in the BFF security boundary.

## 8. BFF implementation mapping

Factory design boundary

DDMP BFF Bootstrap location

factory-portal-web

portal and api.admin presentation boundary

factory-authz

security and identity-provider adapter

factory-provisioning-service

application.registry and application.enrollment

pending enrollment / QR pairing

application.enrollment plus DevicePkiGateway port

factory-audit-service

application.audit and observability correlation

ddmp-repositories

infrastructure.persistence-ddmp

This mapping is logical. It preserves the approved Bootstrap dependency direction and does not freeze a Java package name.