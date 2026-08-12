# DCAM 9-Month Development Plan

**Page ID**: 46759955  
**Version**: 11  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/46759955

---


# DCAM 9-Month Development Plan

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Development Plan

Version

Approved 1.6

Status

Approved

Approval Scope

Execution sequencing và delivery planning; active scope thuộc DCAM Release & Build Applicability Matrix, delivery status thuộc Jira.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

02  Sprint Operations

Target Audience

PM/BA, Product Owner, Tech Lead, Developers, QA, Stakeholders

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Project Home, DCAM Documentation Governance, DCAM Product Vision, DCAM Project Charter, DCAM Roadmap, DCAM MVP Scope, DCAM Release & Build Applicability Matrix, DCAM Architecture Delivery Profile, DCAM Requirements Home, DCAM Architecture Home

Duration

9 Months

Sprint Length

2–4 Weeks

## 1. Purpose

Tài liệu này mô tả cách triển khai DCAM trong 9 tháng, bao gồm Android onboarding, sprint allocation, phase delivery, buffer, risk và release preparation.

Roadmap → What & When
Development Plan → How
Release & Build Applicability Matrix → What is mandatory for each build
Implementation phải tuân theo **DCAM Architecture Delivery Profile** và **DCAM Release & Build Applicability Matrix**.

## 2. Project Assumptions

Item

Value

Development Duration

9 months

Sprint Length

2–4 weeks

Estimated Total Sprints

18

Development Team

4 Developers

QA

Shared QA / assigned per phase

Platform

Android BodyCamera

Desktop Integration

BDMA Desktop

Development Language

Java-first

Main Delivery Target

Customer Pilot / Production Candidate

Current Active Build

DCAM MVP Internal Build 0.1

Current Delivery Gate

Working Recording Slice

## 3. Development Strategy

Principle

Description

Foundation First

Recording, capture, storage and BDMA contract output đi trước.

Working Recording First

Pass Working Recording Slice trước khi mở rộng platform architecture.

Build Applicability

Chỉ implement/test feature được Matrix mark Required/Conditional cho build hiện tại.

Integration Early

BDMA sample import được đưa vào Build 0.1.

Incremental Delivery

Mỗi phase có runnable build và measurable acceptance.

Architecture by Delivery Profile

Không tạo future module/layer trước khi có real implementation need.

Stabilize Before Pilot

Phase cuối tập trung regression, performance, security and release readiness.

```
No future Target Architecture feature may block Build 0.1 unless the Applicability Matrix activates it or an approved exception exists.
```

## 4. Overall Timeline

Stage

Timeline

Main Goal

Main Deliverable

Android Training

Week 1–2

Team Android readiness.

Android training prototype.

Phase 1 – MVP Foundation

Month 1–3

Working Recording Slice and stable core capture/storage.

DCAM MVP Internal Build 0.1.

Phase 2 – Platform Foundation & BDMA Integration

Month 4–5

Identity, provisioning, Device/User, BDMA E2E and basic security/platform foundation.

Secure Platform MVP Build 0.2.

Phase 3 – Advanced Communication

Month 6–7

Live Streaming, PTT, GPS route and advanced encryption beta.

Advanced Communication Beta Build 0.3.

Phase 4 – Hardening & Customer Pilot

Month 8–9

Full applicable regression and pilot readiness.

Customer Pilot / Production Candidate.

## 5. Phase 1 – MVP Foundation

### 5.1 Required Scope

Recording / Capture
Storage / Finalization
Minimal DB / CSON / logs.txt
Local Operational Logging
MVP Data Contract output
BDMA sample detect/import
Critical-path concurrency
Basic device status where supported
### 5.2 Deferred Scope

Web Portal provisioning
Cloud identity
Full operator auth/user management
Full Device Owner / Kiosk Policy
Remote Config
Self Update
Advanced In-App Console
AI
Live Streaming
PTT
Full GPS route
### 5.3 Deliverables

Deliverable

Description

Camera Prototype

Recording/capture sample on selected device.

Working Recording Slice

30-second recording, image capture, finalization, minimal artifacts and BDMA sample import.

DCAM MVP Internal Build 0.1

Stable core MVP build.

MVP Test Checklist / Report

Applicable Build 0.1 QA groups only.

Known Issues

Device/firmware limitations and workarounds.

## 6. Phase 2 – Platform Foundation & BDMA Integration

Area

Direction

DCAM-BDMA

Full active contract integration and write-back scope.

Device Identity

`serial_number`, `dcam_cloud_device_id`, restore/provisioning foundation.

Web Portal

Factory Worker QR-based provisioning.

User/Auth

Basic operator/profile/session foundation.

Kiosk / Device Owner

Begin after required Device POC evidence.

Remote Config

Fetch/cache/apply foundation according to approved scope.

Self Update

Basic APK update foundation after recording/storage stability.

Security

Basic approved protection/encryption foundation.

Main deliverable: **Secure Platform MVP Build 0.2**.

## 7. Phase 3 – Advanced Communication

Area

Direction

Live Streaming

Beta/basic implementation.

Push-to-Talk

Beta/basic implementation.

Full GPS Route

Version 1 route tracking.

Advanced Encryption

Extended approved scope.

Reconnect / Timeout

Weak-network handling and operational logging.

Main deliverable: **Advanced Communication Beta Build 0.3**.

## 8. Phase 4 – Hardening & Customer Pilot

Activity

Description

Regression

Run all test groups activated for pilot scope.

Stability

Long-running recording/device tests.

Performance

CPU, memory, battery, storage and latency budgets.

Security Review

Active authentication, provisioning, kiosk, update and encryption scope.

Factory Validation

Applicable SOP and `READY_TO_SHIP` gates.

Release Documentation

Release notes, installation guide, test report and known issues.

## 9. Sprint Allocation

Sprint Range

Main Focus

Sprint 1

Android training, environment and delivery-profile onboarding.

Sprint 2

Camera prototype.

Sprint 3

Record/capture/finalize vertical slice.

Sprint 4

Minimal DB/CSON/log output and BDMA sample import.

Sprint 5–6

Build 0.1 hardening and applicable QA.

Sprint 7–10

Phase 2 identity, provisioning, BDMA and platform foundation.

Sprint 11

Build 0.2 stabilization.

Sprint 12–14

Live Streaming, PTT, GPS route and advanced encryption.

Sprint 15–16

Build 0.3 hardening.

Sprint 17

Regression, performance, stability and security review.

Sprint 18

Release candidate and customer pilot preparation.

## 10. Jira / Sprint Applicability Rule

Every implementation Jira issue should identify:

Build Profile
Applicability Status
Requirement Inputs
Design / Contract Inputs
QA Group
POC / Security / API blocker
Deferred Dependencies
A future feature may not be added to Build `0.1` by an isolated Jira task without updating **DCAM Release & Build Applicability Matrix** or recording an approved temporary exception.

## 11. Risk Management

Risk

Mitigation

Android learning curve

Training, prototypes and code review.

Camera/firmware limitation

Early Device POC and provider abstraction.

Analysis paralysis

Enforce Working Recording Slice and Matrix.

Too many modules early

Follow Architecture Delivery Profile.

BDMA contract changes

Versioned Data Contract and early sample import.

Device/User model changes

Activate only in Build 0.2 and review API/schema first.

Scope creep

Matrix change control and Jira traceability.

## 12. Documentation Deliverables by Phase

Phase

Required Documents

Current Baseline

DCAM Project Home, Documentation Governance, Release & Build Applicability Matrix, Architecture Home, Architecture Delivery Profile.

Android Training

DCAM Android Training & Architecture Onboarding and prototype notes.

Phase 1

DCAM Requirements Home, MVP Scope, Data Contract, Working Recording Slice evidence, applicable QA report, release notes.

Phase 2

Updated API/Technical Designs, integration test report and platform foundation notes.

Phase 3

Approved feature-specific Technical Designs and beta test reports.

Phase 4

QA Test Report, Factory SOP evidence where applicable, Release Notes, Installation Guide and Known Issues.

## 13. Related Documents

Document

Purpose

DCAM Roadmap

Product stages and milestones.

DCAM MVP Scope

MVP acceptance and boundaries.

DCAM Release & Build Applicability Matrix

Active feature/requirement/test applicability.

DCAM Requirements Home

Functional Requirements 01–10 navigation.

DCAM Architecture Home

Architecture, Technical Design and ADR navigation.

DCAM Architecture Delivery Profile

MVP implementation guardrails.

DCAM-BDMA Data Contract

Android–BDMA interoperability.

DCAM QA Test Strategy & Test Matrix

Test coverage and release validation.

DCAM Factory Provisioning & Device Production SOP

Factory production and final acceptance.

## 14. Practical Conclusion

Current active build = DCAM MVP Internal Build 0.1.
Current gate = Working Recording Slice.
Development Plan defines execution.
Applicability Matrix defines mandatory scope per build.
Requirements Home and Architecture Home replace obsolete generic document names.