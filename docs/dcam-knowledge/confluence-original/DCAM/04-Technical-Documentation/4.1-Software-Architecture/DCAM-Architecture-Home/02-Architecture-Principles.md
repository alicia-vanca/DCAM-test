# 02 - Architecture Principles

**Page ID**: 47120416  
**Version**: 14  
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

Approved 1.8

Status

Approved

Approval Scope

Project architecture principles và cross-cutting constraints; build applicability thuộc DCAM Release & Build Applicability Matrix.

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

2026-07-14

Related Jira

None

Related Documents

DCAM Architecture Home, DCAM Release & Build Applicability Matrix, DCAM Performance Budget & Resource Constraints, DCAM Logging & Diagnostics Design, 06 - Cloud Services, Update & Configuration Architecture, DCAM Security & Encryption Design, DCAM-BDMA Data Contract

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

## 3. Current Provider Decisions

Domain

Current Provider / Baseline

Status

Web frontend

Firebase Hosting

Approved

Web authentication

Firebase Authentication

Approved

Web backend

Firebase Cloud Functions

Approved

Cloud provisioning/device storage

Firebase Cloud Firestore

Approved

Firebase Realtime Database

Không dùng trong current storage baseline

Not Applicable

Operational Logging

Loggly qua local-first queue và authenticated Backend Relay

Approved

Crash & Stability Monitoring

Firebase Crashlytics

Approved

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
No external EMM / Android Management API / Managed Google Play
DCAM-as-DPC / local Device Owner is preferred when supported
Controlled Maintenance Mode requires Maintenance Password Gate
Primary update path = DCAM Self Update / approved APK update
Device-specific feasibility và exact security parameters vẫn thuộc Device POC/Security Review.

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
Firebase Hosting/Auth/Functions/Firestore form the Web Portal baseline.
Loggly owns centralized Operational Logging.
Crashlytics owns Crash & Stability Monitoring.
Performance Budget owns numeric targets.
Only device-, deployment-, security- and future-feature-specific choices remain TBD.