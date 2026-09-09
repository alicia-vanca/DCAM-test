# 02 - Architecture Principles

**Page ID**: 47120416  
**Version**: 18  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120416

---


# 02 - Architecture Principles

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Principles

Version

Approved 1.12

Status

Approved

Approval Scope

Project architecture principles and cross-cutting constraints, including approved DDMP Hybrid authority boundary and mandatory GMS-free Android runtime baseline; build applicability thuộc DCAM Release & Build Applicability Matrix.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Team, Cloud/WebServer Team

Last Updated

2026-08-25

Related Jira

None

Related Documents

DCAM Architecture Home, DCAM Release & Build Applicability Matrix, DCAM Performance Budget & Resource Constraints, DCAM Logging & Diagnostics Design, 06 - Cloud Services, Update & Configuration Architecture, DCAM Security & Encryption Design, DCAM-BDMA Data Contract, ADR - DCAM GMS-free Android Runtime Baseline

## 1. Purpose

Trang này định nghĩa các architecture principles dùng xuyên suốt DCAM. Detailed behavior thuộc authoritative Requirements, ADR, Technical Design, API/Data Contract và QA documents.

Build applicability thuộc **DCAM Release & Build Applicability Matrix**. Target Architecture không đồng nghĩa mọi module đều bắt buộc trong Build `0.1`.

## 2. Core Principles

Principle

Direction

Reliability First

Recording, finalization và evidence preservation có priority cao hơn optional features.

Offline First

Core local operation hoạt động khi cloud/provider unavailable sau provisioning.

Define Once, Reference Elsewhere

Shared rule chỉ định nghĩa tại authoritative document.

Adapter-based Architecture

Domain logic không phụ thuộc trực tiếp Android, cloud hoặc vendor SDK.

Capability-aware

Runtime chỉ start feature `ENABLED` hoặc approved `DEGRADED`.

State-driven Safety

Critical transition phải qua approved runtime guard.

Local-first Diagnostics

Operational event được persist local trước hoặc độc lập với cloud delivery.

Evidence Preservation

Khi state không chắc chắn, preserve artifact và reconcile.

MVP before Expansion

Working Recording Slice phải có trước platform expansion.

GMS-free Required

Production Android runtime không phụ thuộc Google Play services, Play Store, Google account hoặc GMS-only feature; detailed prohibition/gates thuộc ADR - DCAM GMS-free Android Runtime Baseline.

## 3. Current Provider Decisions

Domain

Current Provider / Baseline

Status

Web frontend

Spring Boot BFF + Thymeleaf

Approved

Web authentication

BFF session/identity integration

Approved

Web backend

Spring Boot BFF

Approved

Cloud provisioning/device storage

PostgreSQL ddmp

Approved

External realtime database

Không dùng trong current storage baseline

Not Applicable

Operational Logging

Loggly qua local-first queue và authenticated Backend Relay

Approved

Crash & Stability Monitoring

Firebase Crashlytics

Approved optional; must not block core operation or local diagnostics

Self Update artifact source

Approved APK artifact provider

Approved boundary; exact deployment TBD

Provider abstraction vẫn bắt buộc. Alternative-provider hoặc migration strategy chỉ cần quyết định khi deployment có yêu cầu mới.

## 4. Performance Observability

`Performance metrics provider` không còn là `TBD`.

Numeric targets
    → DCAM Performance Budget & Resource Constraints

[PERF] / [THREAD] operational events
    → Operational Logging
    → Loggly

Fatal crash / ANR / approved unexpected non-fatal
    → Firebase Crashlytics
Crashlytics custom context không thay thế Operational Logging.

## 5. Current Architecture Baselines

serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity
SD Identity File = recovery cache only
No ANDROID_ID / android_id_hash production identity
No external DPC/EMM, Android Management API or Managed Google Play owns privileged device policy
DCAM is the sole Device Owner/DPC when the dedicated-device profile is activated; feasibility remains Pending Device POC
Controlled Maintenance Mode requires Maintenance Password Gate
Primary update path = BFF-authorized DCAM Self Update from immutable Cloudflare R2/CDN artifact
GMS-free required: no Google Play services, Google Play Store, Google account, FCM, Analytics or Play Integrity dependency
Crashlytics is optional crash/stability telemetry; local-first diagnostics and Operational Logging remain mandatory
Device-specific feasibility và exact security parameters vẫn thuộc Device POC/Security Review.

## DDMP Hybrid Authority Boundary

Đây là hướng kiến trúc đã được phê duyệt; nó không tự kích hoạt Hybrid profile cho Build 0.1.

Boundary

Principle

Android privileged authority

DCAM là Device Owner/DPC duy nhất và là executor cho kiosk, restrictions, Lock Task, install/rollback.

Headwind Client

Chạy Application mode; không trở thành DPC, launcher authority, policy executor hoặc source of truth cho Android state.

Management API

BFF là boundary cho portal/device, desired-state, audit và Headwind adapter. DCAM không gọi Headwind REST API/database trực tiếp.

Artifact delivery

BFF authorize release; R2/CDN chỉ phân phối immutable manifest/APK; DCAM tự validate và quyết định thực thi theo runtime guard.

Applicability

DDMP Hybrid là Phase 2+/POC-gated; Build 0.1 không phụ thuộc Headwind/BFF/R2.

Authoritative DDMP sources: [DDMP 00](/wiki/spaces/DVID/pages/68845572/00+DDMP+Architecture+Overview+Reading+Guide), [DDMP 03 BFF](/wiki/spaces/DVID/pages/68812826/03+Management+API+BFF+Architecture+Baseline), [DDMP 06 Device Integration Contracts](/wiki/spaces/DVID/pages/68812848/06+Device+Integration+Contracts), [DDMP 05 APK Release & Cloudflare R2](/wiki/spaces/DVID/pages/68780056/05+APK+Release+Cloudflare+R2).

## 6. Remaining Decisions

Item

Current Status

CameraX vs Camera2/vendor SDK

TBD / Device POC

Target BodyCamera model/firmware

TBD / Device POC

Current domain providers

Approved in Section 3

Alternative-provider migration strategy

Future / Deployment TBD

Performance telemetry routing

Approved: Operational events → Loggly; crash/ANR → Crashlytics

Numeric performance targets

Defined provisionally; Device POC validation required

Streaming/PTT protocol

Future / TBD

Media encryption/key management

TBD / Security Review

## 7. Practical Conclusion

DCAM providers are approved by domain, not globally undefined.
Spring Boot BFF + Thymeleaf with PostgreSQL ddmp forms the Factory Portal baseline.
Loggly owns centralized Operational Logging.
Crashlytics owns Crash & Stability Monitoring.
Performance Budget owns numeric targets.
Only device-, deployment-, security- and future-feature-specific choices remain TBD.