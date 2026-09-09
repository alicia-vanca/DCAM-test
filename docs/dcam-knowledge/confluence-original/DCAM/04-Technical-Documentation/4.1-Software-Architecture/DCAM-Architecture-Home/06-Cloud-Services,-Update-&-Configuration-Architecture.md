# 06 - Cloud Services, Update & Configuration Architecture

**Page ID**: 47120459  
**Version**: 33  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120459

---


# 06 - Cloud Services, Update & Configuration Architecture

Metadata

Value

Document Type

Architecture

Status

Approved

Version

3.7

Last Updated

2026-08-26

## 1. Purpose

This page defines the cloud/provider boundaries for factory provisioning, device identity, desired-state configuration and artifact/update delivery. DCAM remains the only Android Device Owner/DPC and privileged executor. The production Android runtime is GMS-free under the approved baseline.

## 2. Approved provider model

Concern

Approved authority

Notes

Factory provisioning UI

Spring Boot BFF + Thymeleaf Factory Portal

Server-rendered factory-only interface; Build 0.2 minimum.

Factory authentication/authorization

BFF session/identity integration + server-side RBAC

Exact IdP/protocol/MFA remains Security Review gated.

Factory provisioning and identity

Spring Boot BFF

Validation, create/restore, conflict policy and audit boundary.

Factory authoritative data

PostgreSQL ddmp

BFF-only access; separate DB/role/pool/migrations from Headwind hmdm.

Fleet desired-state and audit

Spring Boot BFF

DDMP Phase 2+/POC-gated control plane.

MDM control plane

Headwind Community self-host

Limited application-mode control plane; not identity or Device Owner authority.

APK artifacts

Cloudflare R2/CDN

Artifact plane; signed/versioned release policy applies.

Android configuration provider

BFF desired-state/config contract

No Android remote-config SDK baseline.

Device API authentication

BFF-controlled Device PKI + per-device mTLS

DCAM Keystore proof-of-possession; identity fields are not credentials.

No Firebase web/auth/data component is used in the factory provisioning or device-management line. Firebase Crashlytics, if retained, is bounded optional telemetry only and is not a cloud authority.

## 3. Factory provisioning / identity flow

flowchart TD
  W["Factory Worker"] --> P["BFF Thymeleaf Factory Portal"]
  P --> B["Spring Boot BFF"]
  B --> D["PostgreSQL ddmp"]
  B --> A["Device-facing BFF identity contract"]
  A --> C["DCAM Device Owner/DPC"]

Worker scans a DCAM-generated QR. serial_number is read-only, QR-originated recovery key.

BFF validates, authorizes and creates/restores dcam_cloud_device_id; in the device contract this same ID is named platformDeviceId.

BFF writes provisioning audit with the result. Browser clients never write database records directly.

DCAM uses device-facing identity credentials only; it never uses Factory Worker session material.

## 4. Desired-state and configuration

Factory provisioning establishes identity; it does not independently control fleet configuration. In the Hybrid DDMP profile, BFF owns desired-state, capability-aware resolution, audit and device-facing configuration contract. DCAM validates and applies only behavior within its Device Owner authority. Headwind can deliver limited application-mode commands/profile information but cannot replace BFF desired-state authority or DCAM enforcement.

Remote configuration must not use Google Play services, FCM or any SDK/provider that changes the GMS-free baseline without an ADR.

## 5. Artifact and update plane

Cloudflare R2/CDN hosts immutable/versioned APK artifacts. DCAM Self Update verifies trusted release metadata and artifact integrity, obeys kiosk/safe-window policy and remains the update executor. Headwind Client may coexist as an application but does not become Device Owner or override DCAM's update policy.

## 6. Security and deployment controls

Use BFF administrative routes for Factory Portal and separate device-facing routes/scopes for DCAM.

ddmp and Headwind hmdm are logical databases with separate roles, pools, migrations, backups and access controls; no cross-database business query.

Provider/protocol selection for factory worker authentication, MFA, QR cryptographic details, database DDL, retention, network deployment and production environment separation remain designated review gates.

Factory provisioning remains Phase 2 / Build 0.2 minimum; DDMP fleet profile remains POC-gated Phase 2+. Neither activates Build 0.1.

## 7. Related documents

DCAM Factory Provisioning Portal & BFF API Contract

DCAM Device Provisioning Web Portal Design / App Design / Implementation Design

ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id

03 Management API / BFF Architecture Baseline

05 APK Release & Cloudflare R2

ADR – DCAM GMS-free Android Runtime Baseline

## Device API trust boundary

Device API authentication follows [ADR – DCAM Device API Credential & mTLS Baseline](/wiki/spaces/DVID/pages/70287362/ADR+DCAM+Device+API+Credential+mTLS+Baseline). DCAM connects outbound through the approved public edge and authenticates only to BFF using its per-device mTLS certificate. BFF validates active/revoked certificate status and maps credential server-side to platformDeviceId. Factory Portal worker sessions and Headwind credentials are separate and never reach DCAM.

Exact Device PKI, certificate profile, public hostname/TLS termination and revocation mechanics remain Security/Operations/POC decisions.