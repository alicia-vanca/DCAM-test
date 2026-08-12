# DCAM Web Portal & Device API Contract

**Page ID**: 49873154  
**Version**: 11  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49873154

---


# DCAM Web Portal & Device API Contract

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design / API Contract

Version

Approved 0.9

Status

Approved

Approval Scope

Approved Phase 2 factory provisioning web/device API and data-contract boundary under DEC-P2-WEB-01; deployment and security enforcement remain subject to approved Implementation/Security sources; not a Build 0.1 or release approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Backend Lead / Web Portal Lead / Security Reviewer / QA Lead / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Android Developers, Web Developers, Backend Team, QA, Security Reviewer, Factory, Support

Last Updated

2026-07-20

Related Jira

None

Related Documents

DCAM Device Provisioning Web Portal Design, DCAM Device Provisioning Web Portal App Design, DCAM Device Provisioning Web Portal Implementation Design, DCAM Security & Encryption Design, DCAM Android Operation Design, DCAM Factory Provisioning & Device Production SOP, ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id , [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Trang này owns the logical API, request/response, Firestore path, result/reason-code and QR payload contract for factory business provisioning.

Current implementation baseline:

Frontend = Firebase Hosting
Authentication = Firebase Authentication
Backend = Firebase Cloud Functions
Storage = Firebase Cloud Firestore
### 1.1 DEC-P2-WEB-01 Applicability

[Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence) applies this logical contract to Phase 2 / `Secure Platform MVP Build 0.2` minimum factory provisioning only. It does not authorize a customer/general/fleet portal, frontend direct writes, manual serial entry, Device Owner, Android serial injection, factory acceptance/shipment, Remote Config, Self Update, remote management, Live Streaming, PTT or AI.

## 2. Authentication and Authorization

Factory Worker logs in through Firebase Authentication.
Frontend sends Firebase identity context to backend.
Backend verifies token and active workers/{firebase_uid} profile.
Backend derives worker identity and role from verified context.
Request body must not be trusted for worker identity, role or authorization.

Worker account lifecycle and individual/shared-station policy remain Factory/Security decisions.

## 3. Provisioning Endpoint

Logical contract:

```
POST /v1/factory/provisioning/devices
```

This logical endpoint is approved. Only the concrete Cloud Function deployment name, Hosting rewrite and physical environment URL remain TBD.

Frontend must call backend. It must not direct-write production provisioning collections.

## 4. Request Contract

{
  "request_id": "client-generated-correlation-id",
  "qr": {
    "payload_type": "DCAM_DEVICE_PROVISIONING",
    "payload_version": "1",
    "serial_number": "...",
    "app_package_name": "...",
    "app_version_name": "...",
    "app_version_code": 1,
    "device_model": "...",
    "firmware_version": "...",
    "serial_source": "optional",
    "generated_at": "optional",
    "expires_at": "optional",
    "provisioning_nonce": "optional",
    "signature": "optional"
  },
  "device_information": {
    "owner_name": "...",
    "manufacture_date": "YYYY-MM-DD"
  }
}
Minimum QR logical fields are approved. Exact serialization/cryptographic format remains TBD.

## 5. Response Contract

Success direction:

{
  "request_id": "...",
  "result": "CREATED",
  "dcam_cloud_device_id": "...",
  "serial_number": "...",
  "device_state": "ACTIVE",
  "support_required": false
}
Allowed high-level results:

CREATED
RESTORED
REJECTED
FAILED
SUPPORT_REQUIRED
Backend must return stable safe reason codes and must not expose raw stack traces or provider internals.

## 6. Firestore Logical Paths

Logical collection/document names are defined:

workers/{firebase_uid}
serial_lookup/{serial_number}
devices/{dcam_cloud_device_id}
audit_events/{audit_event_id}
They are not a general `TBD` anymore.

Environment/project/database identifiers, indexes, retention, IAM and exact Firebase Security Rules remain deployment/security details.

## 7. Core Document Direction

### 7.1 Worker Profile

firebase_uid
status
role = FACTORY_WORKER
factory/site reference if applicable
created_at / updated_at
### 7.2 Serial Lookup

serial_number
dcam_cloud_device_id
state
created_at / updated_at
### 7.3 Device Record

dcam_cloud_device_id
serial_number
owner_name
manufacture_date
device_model
firmware_version
app_package_name
app_version_name
app_version_code
state
created_at / updated_at
### 7.4 Audit Event

audit_event_id
request_id
worker_uid
serial_number
dcam_cloud_device_id
action
result
safe_reason_code
changed_fields
timestamp
Exact optional fields and retention remain open; the logical ownership is defined.

## 8. Create / Restore Logic

validate authentication and worker profile
validate QR-derived serial and device information
check serial_lookup/{serial_number}

not found:
    create new dcam_cloud_device_id
    create lookup + device record transactionally
    result = CREATED

found and restorable:
    return same dcam_cloud_device_id
    apply allowed device-information updates
    result = RESTORED

conflict/rebind/restricted state:
    do not allow worker override
    result = SUPPORT_REQUIRED or REJECTED

write audit event
Idempotency uses `request_id` and/or backend transaction/deduplication policy. Exact retention window and timeout reconciliation remain TBD.

## 9. Security Rules

QR serial is read-only in UI.
Backend revalidates all input.
Frontend cannot write provisioning collections directly.
Factory Worker cannot override duplicate/rebind/state restriction.
QR and API must not contain factory Wi-Fi value, maintenance secret or Android system identifier.
Camera scan frames are not uploaded.
Production and non-production environments must be separated; concrete project/database identifiers, Firebase Security Rules and IAM remain Pending Security/Operations decisions.
Audit uses verified worker context.
## 10. Android Boundary

DSetup does not call this provisioning endpoint and does not create official production records.

Android may use approved device-facing identity/config endpoints or Firestore/backend adapters to restore identity after business provisioning. Exact Android device authentication remains TBD and must not reuse Factory Worker credentials.

## 11. Resolved and Remaining Decisions

Item

Status

Web implementation stack

Approved

Factory user-facing role

Approved: Factory Worker

Logical provisioning endpoint

Approved

Logical Firestore paths

Approved

Frontend direct-write policy

Resolved: not allowed

QR minimum logical schema

Approved

Create/restore identity model

Approved

Concrete function name/URL rewrite

TBD / Deployment

Firebase Security Rules/IAM/indexes

TBD / Backend + Security

Worker account lifecycle/model

TBD / Factory + Security

Android device authentication

TBD / Backend + Security

QR serialization/signature/nonce/expiration/replay

TBD / Security

Owner master-data source and validation

TBD / Product + Factory

Idempotency retention and unknown-outcome reconciliation

TBD / Backend

Duplicate/rebind support workflow

TBD / Support + Product

Audit retention

TBD / Security + Backend

## 12. Practical Conclusion

The logical API, Firestore paths, QR minimum fields and backend authority are defined.
Remaining TBDs are physical deployment, IAM/security, lifecycle and operational-policy details.