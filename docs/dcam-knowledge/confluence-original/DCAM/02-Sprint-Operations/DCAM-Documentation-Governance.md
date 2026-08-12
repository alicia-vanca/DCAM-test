# DCAM Documentation Governance

**Page ID**: 47120620  
**Version**: 21  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120620

---


# DCAM Documentation Governance

Item

Information

Project

DCAM

Document Type

Documentation Governance

Version

Approved 1.18

Status

Approved

Approval Scope

Documentation ownership, status taxonomy, approval integrity, traceability và Registry inclusion/exclusion/coverage rules.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Documentation Owner

Approver

Hoàng Ngọc Quyền

Parent Folder

02  Sprint Operations

Last Updated

2026-07-21

Related Jira

None

Related Documents

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP), DCAM Document Status Registry, DCAM Requirement–Design–Test Traceability Matrix, DCAM Release & Build Applicability Matrix, DCAM Project Home, DCAM Architecture Home, DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Document Owners, QA, Reviewers, Approvers

## 1. Authoritative Document Rule

Define once.
Reference elsewhere.
Avoid copying full rules into many documents.
A dependent document may summarize an authoritative rule only for its local audience/execution context.

### 1.1 Baseline Restatement Rule

Full cross-project baselines may be retained only in documents commonly read standalone:

DCAM Project Home
DCAM Architecture Home
DCAM-BDMA Data Contract
Authoritative ADR / Contract / SOP when the baseline is the subject of that document
Technical Design pages must use this pattern:

See <Authoritative Document> for the current baseline.
This document only defines <local implementation impact>.
Technical Design pages must not copy the complete Device Identity, Operational Logging/Crashlytics or Device Owner/EMM/Kiosk baseline. Local fields, states, guards, schemas, test conditions and implementation rules remain in the Technical Design page when required to implement that domain.

When a baseline changes, update the authoritative document first. Dependent pages should require only reference validation and local-impact review.

## 2. Rule Ownership Matrix

Topic

Authoritative Document

Documentation hierarchy/change process

DCAM Documentation Governance

Document version/status/approval summary

DCAM Document Status Registry

Requirement → Build → Design → QA → Jira → Evidence traceability

DCAM Requirement–Design–Test Traceability Matrix

Project navigation/current summary

DCAM Project Home

Build/phase feature, requirement and QA applicability

DCAM Release & Build Applicability Matrix

Product direction

DCAM Product Vision / Roadmap / MVP Scope

Execution plan

DCAM 9-Month Development Plan

Requirements navigation

DCAM Requirements Home

Architecture/Technical Design navigation

DCAM Architecture Home

MVP vs Target Architecture

DCAM Architecture Delivery Profile

Device identity

Device Identity ADR + 04 - Device Configuration Requirements

Web Portal business flow

DCAM Device Provisioning Web Portal Design

Web Portal Login/Workspace behavior

DCAM Device Provisioning Web Portal App Design

Web Portal Firebase implementation

DCAM Device Provisioning Web Portal Implementation Design

Web/API schema/path/reason code

DCAM Web Portal & Device API Contract

Security/authorization

DCAM Security & Encryption Design

Device Owner/Lock Task/Maintenance

DCAM Android Device Owner & Kiosk Policy Design

Factory production/final acceptance

DCAM Factory Provisioning & Device Production SOP

DSetup tool behavior

DCAM DSetup Factory Tool Design

Media/BDMA contract

DCAM-BDMA Data Contract

Logging provider ownership

07 - Logging, Diagnostics, Performance & Security

Logging requirements

07 - Logging & Diagnostics Requirements

Logging implementation

DCAM Logging & Diagnostics Design

QA/release validation

DCAM QA Test Strategy & Test Matrix

Performance targets

DCAM Performance Budget & Resource Constraints

Artifact evidence repository governance

DCAM Documentation Governance

Developer/QA execution procedure

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP)

Real-device evidence conclusion

DCAM Device POC & Hardware Validation Report

Raw artifact

DCAM-EVID-NAS-01, NAS nội bộ

## 3. Release / Build Applicability Rule

**DCAM Release & Build Applicability Matrix** decides when a requirement, design rule, feature or QA test group becomes mandatory.

Status

Meaning

Required

Must be implemented and pass applicable QA for the build.

Conditional

Required only when the stated condition/feature is enabled.

Deferred

Valid target capability but not a blocker for the current build.

Not Applicable

Excluded from that build profile.

POC Blocked

Waits for Device POC/decision evidence before production implementation.

Document priority is not build applicability.
A document-level P0 does not automatically mean P0 for every build.
A QA P0 is a release blocker only when its feature/test group is applicable to the active build.
Current baseline:

Active Build = DCAM MVP Internal Build 0.1
Active Gate = Working Recording Slice
When build scope changes:

Update Applicability Matrix first
    ↓
Update Roadmap / MVP Scope / Development Plan if phase scope changes
    ↓
Update Requirements/Design/API only when behavior/contract changes
    ↓
Update QA applicability
    ↓
Update Traceability Matrix
    ↓
Update Project Home only when navigation/current summary changes
An isolated Jira task must not promote a feature from `Deferred` to `Required` without a Matrix update or approved exception.

## 4. Domain Governance Baselines

### 4.1 Web Portal

Business Flow → Web Portal Design
Login/Workspace behavior → App Design
Firebase modules → Implementation Design
API/schema → API Contract
Security constraints → Security Design
Current user-facing baseline:

Factory Worker only
Login + Workspace only
QR displayed by DCAM is the serial source
serial_number is read-only
### 4.2 Device Identity

serial_number = Hardware Identity / recovery key
dcam_cloud_device_id = Cloud Identity
SD Identity File = recovery cache
serial_lookup/{serial_number} = create/restore lookup
### 4.3 Logging

Loggly = Operational Logging provider
Crashlytics = Crash & Stability provider
Logging Design owns internal files/queue/routing
Data Contract owns Logs/logs.txt
QA owns validation
### 4.4 Factory

DSetup ends at imported serial verification.
DSetup completion does not mean production acceptance.
Factory/QA owns official record and READY_TO_SHIP / QUARANTINED.
## 5. Approval, Status and Reference Rules

A downstream document must not be approved against an upstream Draft unless an exception is documented.

When an approved dependency changes materially, downstream content must be reapproved, marked Draft, assigned a qualified approval status, or documented as unaffected.

### 5.1 Approval Integrity Rule

Rule ID

Rule

APR-001

A page must not show `Status = Approved` while any required row in its Approval table remains `Pending`.

APR-002

Status, version label, approval table and revision history must describe the same approval state.

APR-003

A page whose numeric values depend on hardware evidence must not use unqualified `Approved` as a production claim.

APR-004

A page whose algorithm, key management or security policy is unresolved must not use unqualified `Approved` as a production-security claim.

APR-005

Approval scope and remaining dependency must be recorded in page metadata or DCAM Document Status Registry.

### 5.2 Status Taxonomy

The complete operational taxonomy is maintained in **DCAM Document Status Registry**.

Status

Usage Summary

Draft

Content/review incomplete.

Approved Direction

Direction accepted; exact implementation/evidence incomplete.

Approved Provisional Baseline

Usable current baseline, adjustable by evidence.

Approved Pending Device POC

Production validity depends on target-device evidence.

Approved Pending Security Review

Security algorithm/key/policy values remain open.

Approved for Build `<profile>`

Release baseline only for the named build.

Production Approved

Required evidence and production approval gates completed.

Superseded / Archived

Historical, not current source of truth.

`Approved` without qualifier is allowed only when the decision is complete and remaining POC/Security/Deployment details cannot materially change the meaning of the baseline.

### 5.3 Version and Status Registry Rule

Each page owns its own current metadata.
DCAM Document Status Registry is the only cross-document version/status summary.
Project Home, Architecture Home and Requirements Home must not copy mutable version/status tables.
When page metadata and registry differ, page metadata is the immediate source; the registry discrepancy must be corrected in the same change window.

### 5.3.1 Registry Coverage Scope

Rule ID

Rule

REG-SCOPE-001

Mọi current Confluence page trong space DVID có page metadata `Project` bắt đầu bằng `DCAM` phải có đúng một row trong **Current Document Register**.

REG-SCOPE-002

Scope bao gồm Home/navigation page, Governance, Registry, Requirements, Architecture, ADR, Technical Design, Contract, Standard/SOP, QA và report; áp dụng cho cả `Draft`, qualified status và `Approved`.

REG-SCOPE-003

Scope không bao gồm folder/container không có DCAM document metadata, template/meeting note, non-DCAM page, hoặc content ở trạng thái archived/deleted/trashed. Historical `Superseded / Archived` entry phải được quản lý tách khỏi current coverage.

REG-SCOPE-004

Page metadata sở hữu `Version`, `Status` và `Approval Scope` của chính page; Registry chỉ tổng hợp và không được nâng status hoặc mở rộng approval scope.

REG-SCOPE-005

Khi page metadata thiếu `Version` hoặc `Approval Scope`, Registry phải ghi rõ `Missing in page metadata`; không tự suy diễn giá trị và phải mở metadata-cleanup finding.

REG-SCOPE-006

Mỗi current page chỉ có một Registry row, được nhận diện bằng current page title và link trực tiếp tới page; duplicate title phải được xử lý trước khi coi coverage là complete.

REG-SCOPE-007

Page metadata change và Registry synchronization phải hoàn tất trong cùng controlled change window.

REG-SCOPE-008

Current coverage = số unique Registry rows khớp current in-scope pages / tổng số current in-scope pages tại Data cut-off.

### 5.3.2 Dangling Hierarchy Entry Rule

Rule ID

Rule

REG-DANGLING-001

Một hierarchy entry được coi là `Dangling` khi live descendants/tree vẫn trả entry nhưng direct page fetch trả `404` và CQL không tìm thấy current content.

REG-DANGLING-002

`Dangling` entry không được tính vào Registry coverage của named, retrievable DCAM pages và không được dùng làm navigation destination.

REG-DANGLING-003

Project Home hoặc Registry phải ghi page ID, observed parent và validation evidence để audit cho đến khi entry được restore hoặc xóa khỏi live tree.

REG-DANGLING-004

Không rename, move, archive hoặc delete bằng blind write khi page object không retrievable; Confluence admin phải xác nhận physical cleanup hoặc access restoration.

REG-DANGLING-005

Chỉ bỏ documented exclusion khi page có title, retrievable current content, required metadata và Registry row hợp lệ, hoặc khi entry không còn xuất hiện trong live tree.

Current documented exclusion:

Không có `Dangling` hierarchy entry tại data cut-off 2026-07-16. Page ID `49774716` không còn xuất hiện trong live descendants; documented exclusion đã đóng.

### 5.4 Reference Naming Rule

Use current page names:

DCAM Requirements Home
DCAM Architecture Home
DCAM Release & Build Applicability Matrix
DCAM QA Test Strategy & Test Matrix
DCAM Factory Provisioning & Device Production SOP
DCAM Document Status Registry
DCAM Requirement–Design–Test Traceability Matrix
Do not use obsolete/non-existing names:

DCAM Functional Requirements
DCAM System Architecture & Technical Notes
DCAM Android Architecture
DCAM Release Plan
Dependent documents should not pin a mutable version such as `Factory SOP Draft 1.0` unless an immutable audit/release baseline explicitly requires it.

Recommended wording:

```
Aligned with the current authoritative DCAM Factory Provisioning & Device Production SOP.
```

## 6. Traceability Governance

An active implementation requirement is considered fully traceable only when the following chain is visible:

Requirement ID
    → Build Applicability
    → Architecture / Technical Design / Contract section
    → QA Test ID
    → Jira implementation item
    → Verification evidence

Rule ID

Rule

TRACE-GOV-001

Every requirement activated for a build must have Design/Contract and QA mappings.

TRACE-GOV-002

Document-level related links alone do not count as complete traceability.

TRACE-GOV-003

Jira items must identify Build Profile, Requirement Inputs, Design Inputs and QA Group.

TRACE-GOV-004

`Covered` requires evidence or an explicit evidence owner/location.

TRACE-GOV-005

POC/Security blockers must be visible in the traceability row.

TRACE-GOV-006

Requirement behavior changes update Requirements first; applicability-only changes update the Matrix first.

## 7. Navigation Synchronization Rule

After navigation, ownership or build-summary changes, update:

DCAM Project Home
DCAM Architecture Home when technical ownership/reading order changes
DCAM Requirements Home when requirement structure/reading order changes
After version or status changes, update:

Authoritative page metadata
DCAM Document Status Registry
Do not update navigation pages only to copy a changed version/status value.

Navigation pages summarize and do not replace authoritative detail.

## 8. Practical Conclusion

Requirements own what DCAM must do.
Architecture/ADR own decisions and boundaries.
Technical Design owns how DCAM works.
Contracts own interoperability.
Release & Build Applicability Matrix owns when scope becomes mandatory.
Traceability Matrix owns Requirement → Build → Design → QA → Jira → Evidence mapping.
Document Status Registry owns the cross-document version/status summary.
QA owns how applicable behavior is verified.
Device POC owns real-device evidence.
Factory SOP owns physical production/final acceptance.
Project Home, Architecture Home and Requirements Home own navigation only.