# DCAM Roadmap

**Page ID**: 41615474  
**Version**: 16  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41615474

---


# DCAM Roadmap

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Product Roadmap

Version

Approved 1.7

Status

Approved

Approval Scope

Phase và milestone direction; DEC-P2-WEB-01 defines the narrow Phase 2 Web Portal provisioning scope; active build applicability và release gate thuộc DCAM Release & Build Applicability Matrix.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

01  Product Management

Target Audience

PM/BA, Product Owner, Tech Lead, Developers, QA, Stakeholders

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Project Charter, DCAM Product Vision, DCAM MVP Scope, DCAM 9-Month Development Plan, DCAM Release & Build Applicability Matrix, DCAM Requirements Home, DCAM Architecture Home, DCAM-BDMA Data Contract, DCAM Documentation Governance , [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Tài liệu này mô tả lộ trình phát triển, milestone và deliverable của DCAM.

Roadmap → What & When
DCAM 9-Month Development Plan → How
DCAM Release & Build Applicability Matrix → What is mandatory for each build
Các phase liên quan BDMA phải tuân thủ **DCAM-BDMA Data Contract** cho storage, media naming, MD5, encrypted-media naming, CSON, SQLite DB, logs và cleanup behavior.

## 2. Roadmap Overview

Stage

Timeline

Primary Goal

Main Deliverable

Android Training

Week 1–2

Android/BodyCamera readiness.

Camera training prototype.

Phase 1 – MVP Foundation

Week 3–12

Recording, capture, storage, minimal contract data and logs.

DCAM MVP Internal Build 0.1.

Phase 2 – Platform Foundation & BDMA Integration

Week 13–22

BDMA E2E, Device/User, identity/provisioning and basic security foundation.

Secure Platform MVP Build 0.2.

Phase 3 – Advanced Communication

Week 23–32

Live Streaming, PTT, GPS route and advanced encryption at beta level.

Advanced Communication Beta Build 0.3.

Phase 4 – Hardening & Pilot

Week 33–40

Regression, performance, security and production readiness.

Customer Pilot / Production Candidate.

## 3. Feature Roadmap

Feature / Area

Target Phase

Expected Level

Video Recording / Image Capture

Phase 1

MVP then hardened.

Local Storage / Finalization

Phase 1

MVP aligned with Data Contract.

Minimal DB / CSON / `logs.txt`

Phase 1

Working Recording Slice baseline.

BDMA sample import

Phase 1

Required Working Recording Slice evidence.

Full BDMA integration / write-back

Phase 2

End-to-end platform integration.

Device identity / Web Portal provisioning

Phase 2

Factory-focused minimum provisioning only under DEC-P2-WEB-01; not a general/customer/fleet portal.

Remote Device Management

Phase 2

Basic status/config foundation.

User/Auth foundation

Phase 2

Basic operator/profile/role foundation.

Basic encryption

Phase 2

Approved Security Design scope.

Kiosk / Device Owner foundation

Phase 2

POC-dependent.

Remote Config / Self Update foundation

Phase 2

Activated only after recording/storage stability.

Live Streaming / PTT / Full GPS Route

Phase 3

Beta/basic implementation.

Full regression / stability / security review

Phase 4

Pilot release gate.

Feature applicability and QA release-blocker interpretation are owned by **DCAM Release & Build Applicability Matrix**.

## 4. Milestones

Milestone

Target Time

Success Condition

M0 – Android Training Complete

End Week 2

Team builds/runs/debugs camera sample on BodyCamera.

M1 – Camera Prototype

End Week 6

Recording and capture work on selected device.

M1.5 – Working Recording Slice

End Week 8

Record/capture/finalize, write minimal DB/CSON/log and BDMA imports sample media.

M2 – MVP Internal 0.1

End Week 12

Applicable Build 0.1 QA groups pass.

M3 – DCAM-BDMA E2E Demo

End Week 16

BDMA ingests and displays DCAM data.

M4 – Secure Platform MVP 0.2

End Week 22

Device/User/provisioning/basic security foundation available.

M5 – Advanced Communication Beta 0.3

End Week 32

Streaming/PTT/GPS route beta available.

M6 – Release Candidate

End Week 39

Critical blockers closed.

M7 – Customer Pilot Release

End Week 40

Pilot build and release/test documents available.

## 5. Roadmap Boundaries

Area

Boundary

Build Applicability

Matrix decides `Required / Conditional / Deferred / Not Applicable` for each build.

Target Architecture

A future design page does not automatically make a feature mandatory for Build 0.1.

Platform Foundation

Phase 2 is foundation/basic implementation, not full fleet management or enterprise IAM.

Web Portal provisioning

Factory-focused minimum scope only: authenticated Factory Worker, Login/Workspace, DCAM QR read-only serial, owner/date, backend create/restore and audit. No customer, general administration or fleet-management portal.

Advanced Communication

Phase 3 targets beta/basic capability, not full production hardening.

Pilot

Pilot scope includes only capabilities explicitly activated by the Matrix.

## 6. Related Documents

Document

Purpose

DCAM Product Vision

Long-term product direction.

DCAM MVP Scope

MVP scope and acceptance.

DCAM 9-Month Development Plan

Detailed execution plan.

DCAM Release & Build Applicability Matrix

Active build scope and release applicability.

DCAM Requirements Home

Functional Requirements 01–10 navigation.

DCAM Architecture Home

Architecture, Technical Design and ADR navigation.

DCAM-BDMA Data Contract

Android–BDMA interoperability contract.

DCAM QA Test Strategy & Test Matrix

Test coverage and release validation.

DCAM Factory Provisioning & Device Production SOP

Factory production and final acceptance.

## 7. Practical Conclusion

Current active build = DCAM MVP Internal Build 0.1.
Current delivery gate = Working Recording Slice.
Roadmap defines intended stages.
Applicability Matrix defines what is mandatory now.