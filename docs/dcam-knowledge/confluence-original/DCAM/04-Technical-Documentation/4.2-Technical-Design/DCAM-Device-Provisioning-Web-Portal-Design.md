# DCAM Device Provisioning Web Portal Design

**Page ID**: 49315858  
**Version**: 13  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49315858

---


# DCAM Device Provisioning Web Portal Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design / Business Flow Design

Version

Approved 1.3

Status

Approved

Approval Scope

Approved Phase 2 factory provisioning business-flow baseline under DEC-P2-WEB-01; UI behavior thuộc App Design, implementation thuộc Implementation Design, API/schema thuộc API Contract; not a Build 0.1 or release approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Cloud Lead / Security Reviewer / Android Lead / Backend Lead / Web Portal Lead / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

PM/BA, Tech Lead, Android Developers, Web Developers, Backend Team, QA, Factory Worker, Factory Lead

Last Updated

2026-07-20

Related Jira

None

Related Documents

DCAM Device Provisioning Web Portal App Design, DCAM Device Provisioning Web Portal Implementation Design, DCAM Web Portal & Device API Contract, DCAM Factory Provisioning & Device Production SOP, DCAM DSetup Factory Tool Design, DCAM Android Operation Design, DCAM Security & Encryption Design, ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id , [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Trang này là source of truth cho Web Portal business provisioning flow.

User-facing account = Factory Worker
Screens = Login and Workspace
Serial source = provisioning QR displayed by DCAM
serial_number = read-only
Backend create/restore = serial_lookup/{serial_number}
App UI behavior thuộc **DCAM Device Provisioning Web Portal App Design**. Firebase/frontend/backend implementation thuộc **Implementation Design**. Request/response/schema/path thuộc **API Contract**.

### 1.1 DEC-P2-WEB-01 Applicability

[Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence) governs this business flow only for Phase 2 / `Secure Platform MVP Build 0.2` onward. Local scope is Factory Worker Login/Workspace, DCAM QR-derived read-only `serial_number`, approved owner/date input, backend create/restore and audit. It does not activate Build 0.1, a general/customer/fleet portal, Device Owner, Android serial injection, factory acceptance or shipment.

## 2. Approved Business Flow

DSetup verifies DCAM imported expected serial_number
    ↓
DCAM displays provisioning QR
    ↓
Factory Worker logs in
    ↓
Workspace opens
    ↓
Worker scans QR displayed by DCAM
    ↓
Workspace validates QR and shows serial_number read-only
    ↓
Worker enters/selects owner_name and manufacture_date
    ↓
Worker reviews and submits inside Workspace
    ↓
Backend validates worker and request
    ↓
Backend checks serial_lookup/{serial_number}
    ↓
Create new dcam_cloud_device_id OR restore existing identity
    ↓
Workspace shows `CREATED` / `RESTORED` / `REJECTED` / `FAILED` / `SUPPORT_REQUIRED`
Web Portal does not:

set Device Owner
inject serial_number into Android
accept manual serial_number
scan serial barcode directly
allow worker override of duplicate/rebind/conflict
mark PASS / FAIL / QUARANTINED / READY_TO_SHIP
## 3. Actors

Actor

Responsibility

DCAM App

Hiển thị QR và apply/restore cloud identity after provisioning.

DSetup

Dừng sau imported serial verification.

Factory Worker

Login, scan QR, review device data, enter business information và submit.

Web Frontend

Login/Workspace UI, QR parsing, field validation và safe result display.

Cloud Functions / Backend

Authentication, authorization, validation, create/restore, conflict policy và audit.

Cloud Firestore

Worker profile, serial lookup, device record và audit data.

## 4. Screen Model

Screen

Purpose

Login

Xác thực Factory Worker.

Workspace

Chứa QR scan, device review, form, inline review, submit, result và error states.

Workspace panels không phải screen/route riêng. Không có separate Confirmation, Success hoặc Failed screen.

## 5. QR Contract Baseline

QR schema không còn là `TBD` toàn bộ. Minimum logical payload đã được định nghĩa trong **DCAM Web Portal & Device API Contract**.

Minimum fields:

payload_type = DCAM_DEVICE_PROVISIONING
payload_version
serial_number
app_package_name
app_version_name
app_version_code
device_model
firmware_version
Optional contract/security context:

serial_source
generated_at
expires_at
provisioning_nonce
signature
dcam_data_contract_version
media_contract_version
encoder_contract_version
Rules:

Rule

Description

QR-001

QR phải do DCAM hiển thị và có supported type/version.

QR-002

QR phải có `serial_number`.

QR-003

`serial_number` luôn read-only.

QR-004

QR không chứa credential, factory Wi-Fi password, maintenance secret hoặc Android system identifier.

QR-005

Camera stream/image không được lưu hoặc upload.

QR-006

Invalid/unsupported QR bị reject.

Exact serialization format, signature algorithm, nonce, expiration và replay policy vẫn thuộc API Contract + Security Review.

## 6. Device Information

Field

Source

Behavior

`serial_number`

QR

Required, read-only.

`device_model`

QR

Read-only nếu có.

`firmware_version`

QR

Read-only nếu có.

App version

QR

Read-only.

`owner_name`

Factory Worker

Required theo approved validation policy.

`manufacture_date`

Factory Worker / factory local date

Required, `YYYY-MM-DD`.

`dcam_cloud_device_id`

Backend result

Hiển thị sau Created/Restored.

## 7. Logical Backend Contract

Logical endpoint đã được chốt:

```
POST /v1/factory/provisioning/devices
```

Concrete Firebase Cloud Function deployment name và hosting rewrite/path mapping vẫn là implementation detail.

Backend behavior:

verify Firebase identity token
verify active Factory Worker profile
validate request and QR-derived serial
check serial_lookup/{serial_number}
create or restore devices/{dcam_cloud_device_id}
write audit event
return stable result and safe reason code
Frontend không được direct-write production provisioning collections.

## 8. Error Handling

Case

Expected Handling

Login/session failure

Giữ ở Login hoặc yêu cầu login lại.

Invalid QR

Reject và rescan.

Missing serial

Reject; không mở manual serial input.

Existing restorable serial

Restore same cloud identity.

Duplicate/rebind conflict

Support Required; worker không override.

Backend unavailable/timeout

Không assume success; retry/reconciliation theo API Contract.

Restricted device state

Show safe state and support instruction.

## 9. Security and Audit

Area

Requirement

Authentication

Firebase Authentication.

Authorization

Backend verifies active Factory Worker profile.

Backend authority

Cloud Functions owns create/restore/audit.

Serial integrity

QR-derived and read-only.

Camera privacy

No frame/image storage or upload.

Sensitive data

No password/token/secret/internal stack trace in UI/logs.

Audit

Worker, request id, serial, cloud id, timestamp, result, safe reason and changed fields.

## 10. Resolved and Remaining Decisions

Item

Status

User-facing actor

Approved: Factory Worker

Screen model

Approved: Login + Workspace

Serial source

Approved: QR displayed by DCAM

QR minimum logical fields

Approved in API Contract

Logical provisioning endpoint

Approved: `POST /v1/factory/provisioning/devices`

Backend stack

Approved: Firebase Cloud Functions + Firestore

Firebase Security Rules / IAM / environment separation

Pending / Backend + Security + Operations

QR serialization/signature/nonce/expiration/replay

TBD / API Contract + Security

Owner source and exact validation

TBD / Product + Factory

Worker account model

TBD / Factory + Security

Duplicate/rebind support process

TBD / Product + Support + Backend

Exact audit retention

TBD / Security + Backend

Supported browser/factory station profile

TBD / Implementation + Factory

## 11. Practical Conclusion

The Web Portal business flow is no longer ambiguous about actor, screens, serial source, QR minimum data or logical endpoint.
Factory Worker uses Login and Workspace only.
serial_number comes from the QR displayed by DCAM and remains read-only.
Cloud Functions/backend owns create/restore and audit.
Only QR security encoding, account/process policy and deployment-specific details remain TBD.