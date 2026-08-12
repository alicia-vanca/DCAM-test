# Decision Brief – DCAM Phase 2 Web Portal Scope Precedence

**Page ID**: 54296681  
**Version**: 2  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/54296681

---


# Decision Brief – DCAM Phase 2 Web Portal Scope Precedence

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Decision Brief / Scope Precedence

Version

1.0

Status

Approved

Approval Scope

DEC-P2-WEB-01 product-scope precedence for Phase 2 / Secure Platform MVP Build 0.2 onward; not implementation, Security Review, Factory acceptance or release approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Web Portal Lead / Backend Lead / Security Reviewer / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

02  Sprint Operations

Target Audience

PM/BA, Product Owner, Tech Lead, Web/Backend Developers, QA, Security Reviewer, Factory Lead

Last Updated

2026-07-21

Related Jira

None

Related Documents

[DCAM Project Charter](/wiki/spaces/DVID/pages/41156610/DCAM+Project+Charter), [DCAM Roadmap](/wiki/spaces/DVID/pages/41615474/DCAM+Roadmap), [DCAM Release & Build Applicability Matrix](/wiki/spaces/DVID/pages/51020012/DCAM+Release+Build+Applicability+Matrix), [DCAM Device Provisioning Web Portal Design](/wiki/spaces/DVID/pages/49315858/DCAM+Device+Provisioning+Web+Portal+Design), [DCAM Device Provisioning Web Portal App Design](/wiki/spaces/DVID/pages/50692194/DCAM+Device+Provisioning+Web+Portal+App+Design), [DCAM Device Provisioning Web Portal Implementation Design](/wiki/spaces/DVID/pages/51019802/DCAM+Device+Provisioning+Web+Portal+Implementation+Design), [DCAM Web Portal & Device API Contract](/wiki/spaces/DVID/pages/49873154/DCAM+Web+Portal+Device+API+Contract), [DCAM QA Test Strategy & Test Matrix](/wiki/spaces/DVID/pages/49545345/DCAM+QA+Test+Strategy+Test+Matrix), [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix)

Dependencies / Blockers

Firebase Security Rules, IAM, QR signature/replay policy, worker-account lifecycle, owner validation, retention/reconciliation, environment separation review and Build 0.2 implementation/test evidence remain pending.

## 1. Decision Summary

Field

Decision

Decision ID

`DEC-P2-WEB-01`

Decision Required

Yes — resolve a scope conflict between the Project Charter and Roadmap.

Selected Option

**Option B — Roadmap precedence**

Decision Status

Approved

Approver

Hoàng Ngọc Quyền

Effective Policy Date

2026-07-20

Affected Phase / Build

Phase 2 / `Secure Platform MVP Build 0.2` onward

Retroactive Effect

None for `DCAM MVP Internal Build 0.1 – Working Recording Slice`.

## 2. Conflict and Precedence

The prior [DCAM Project Charter](/wiki/spaces/DVID/pages/41156610/DCAM+Project+Charter) stated that a Web Portal was out of scope for Phase 2, while the [DCAM Roadmap](/wiki/spaces/DVID/pages/41615474/DCAM+Roadmap) listed Device identity / Web Portal provisioning in Phase 2.

For the specific Phase 2 Web Portal scope, this decision adopts the Roadmap direction and requires the Charter wording to be narrowed accordingly.

Roadmap direction → accepted for the defined minimum factory provisioning scope
This Decision Brief → governs the scope boundary and precedence
Applicability Matrix → governs build applicability and release-blocker interpretation
The superseded Charter statement does not authorize a general-purpose portal. It is replaced only by the minimum scope in this record.

## 3. Approved Minimum Scope

The `DCAM Device Provisioning Web Portal` is a factory-focused Web Portal that supports only:

Capability

Approved Boundary

User-facing actor

Authenticated `Factory Worker` only.

Screens

Login and Workspace provisioning.

Serial source

Scan a provisioning QR displayed by DCAM; `serial_number` is read-only.

Business input

`owner_name` and `manufacture_date` under approved policy.

Identity result

Backend create or restore `dcam_cloud_device_id`.

Safe result

`CREATED`, `RESTORED`, `REJECTED`, `FAILED`, or `SUPPORT_REQUIRED`.

Audit

Provisioning request and result are auditable.

Write authority

Backend-authorized access only; frontend does not direct-write provisioning collections.

## 4. Approved Security Boundary

Area

Approved Boundary

Authentication / authorization

Backend verifies token, active `Factory Worker` profile and authorization.

Request trust

Do not trust worker identity or role from the request body.

Restricted operations

A Factory Worker cannot override duplicate, rebind or restricted state.

Sensitive data

Do not log or display token, password, factory Wi-Fi value, maintenance secret or raw provider error.

QR and camera privacy

QR contains no long-lived secret; scan frames are not uploaded or stored.

Environment

Production and non-production environments must be separated.

## 5. Explicit Non-Scope

This decision does not include:

Customer/public portal, general administration or fleet-management portal.

Cloud video management, video viewer, reporting or general user management.

Frontend direct-write Firestore.

Manual serial entry.

Device Owner, Android serial injection, `READY_TO_SHIP`, factory acceptance or shipment decision.

Remote Config, Self Update, remote device management, live streaming, PTT or AI.

## 6. Build Applicability and Build 0.1 Guardrail

Build 0.1 = Web Portal provisioning Deferred and non-blocking.
Build 0.2 = minimum factory provisioning Required, as owned by the Applicability Matrix.
This decision does not change the `DCAM MVP Internal Build 0.1 – Working Recording Slice`, its QA applicability, traceability coverage, or release gate. It creates no implementation evidence, no Jira backlog and no release approval.

## 7. Pending Security and Operations Decisions

The following remain Pending and are not decided by DEC-P2-WEB-01:

Firebase Security Rules, IAM and concrete deployment/environment configuration.

QR signature, nonce, expiry and replay policy.

Factory Worker account lifecycle and individual/shared-station model.

`owner_name` source and validation policy.

Duplicate/rebind support workflow.

Audit retention, idempotency/timeout reconciliation and operational retention policy.

Exact Cloud Function name, Hosting rewrite and physical endpoint mapping.

## 8. Affected Documents and Required Follow-up

Document

Required Follow-up

Project Charter

Replace the generic Phase 2 Web Portal exclusion with the narrow approved scope; retain general portal non-scope.

Roadmap

Retain Phase 2 provisioning and state the factory-focused minimum boundary.

Applicability Matrix

Preserve Build 0.1 Deferred and Build 0.2 Required; add this scope boundary.

Web Portal Design / App / Implementation / API Contract

Reference this decision and align local terminology, non-scope and Pending decisions.

QA / Traceability

Record Phase 2 planning boundary only; do not create evidence, Jira mappings or coverage uplift.

Factory SOP

Clarify that this scope decision does not activate factory acceptance or shipment.

Project Home / Registry

Add the decision to physical hierarchy/navigation and synchronize metadata.

## 9. Handoff Condition

Work 03 may create a Build 0.2 backlog only after this controlled documentation changeset has been verified. This decision alone is not implementation approval, Security Review pass, factory acceptance or release approval.