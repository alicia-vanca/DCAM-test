# DCAM Device Provisioning Web Portal App Design

**Page ID**: 50692194  
**Version**: 6  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50692194

---


# DCAM Device Provisioning Web Portal App Design

Item

Information

Project

DCAM Android BodyCamera Application

Document Type

Web App Design

Version

Approved 1.4

Status

Approved

Approval Scope

Approved Phase 2 factory provisioning application flow/UI baseline under DEC-P2-WEB-01; implementation/deployment details thuộc Implementation Design và approved Security/API contracts; not a Build 0.1 or release approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Web Portal Lead / Backend Lead / Android Lead / Security Reviewer / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Page

DCAM Device Provisioning Web Portal Design

Target Audience

Web Developers, Backend Developers, Android Developers, Factory Worker, Factory Lead, QA, Security Reviewer

Last Updated

2026-07-20

Related Jira

None

Related Documents

DCAM Device Provisioning Web Portal Design, DCAM Device Provisioning Web Portal Implementation Design, DCAM Web Portal & Device API Contract, DCAM Security & Encryption Design, DCAM Factory Provisioning & Device Production SOP , [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Web Portal App là single-purpose factory application với hai screen:

Login
Workspace
Sau login, mọi QR scan, device review, form, inline review, submit, result và error state nằm trong `Workspace`.

### 1.1 DEC-P2-WEB-01 Applicability

[Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence) limits this app to Phase 2 / `Secure Platform MVP Build 0.2` factory provisioning. The app supports only Factory Worker, Login/Workspace, DCAM QR-derived read-only serial, owner/date input and safe backend results. It does not activate Build 0.1, manual serial entry, frontend direct writes, a general/customer/fleet portal, Device Owner, Android serial injection or factory acceptance/shipment.

## 2. Approved App Baseline

User-facing account = Factory Worker only
Authentication = Firebase Authentication
Frontend hosting = Firebase Hosting
Backend = Firebase Cloud Functions
Storage = Firebase Cloud Firestore
Serial source = QR displayed by DCAM
serial_number = read-only
Logical endpoint = POST /v1/factory/provisioning/devices
Không có manual serial entry, direct serial barcode scan, separate Confirmation screen hoặc worker override.

## 3. Screen Model

Screen

Responsibility

Login

Xác thực Factory Worker và hiển thị safe authentication error.

Workspace

Chứa toàn bộ provisioning operation.

Workspace areas:

Area

Responsibility

Session Header

Worker/session/environment/logout.

QR Scanner Panel

Scan QR displayed by DCAM.

Device Review Panel

Hiển thị device context và serial read-only.

Device Information Panel

Nhập/chọn owner và manufacture date.

Inline Review / Submit

Review và submit trong cùng screen.

Result / Error Panel

`CREATED`, `RESTORED`, `REJECTED`, `FAILED` hoặc `SUPPORT_REQUIRED`.

## 4. Workspace State Model

IDLE
SCANNING_QR
VALIDATING_QR
DEVICE_REVIEW_READY
EDITING_DEVICE_INFO
REVIEW_READY
SUBMITTING
SUCCEEDED_CREATED
SUCCEEDED_RESTORED
FAILED
SUPPORT_REQUIRED
SESSION_EXPIRED
State transition không tạo route/screen mới.

## 5. QR Payload Baseline

Minimum logical schema đã được chốt trong API Contract:

payload_type
payload_version
serial_number
app_package_name
app_version_name
app_version_code
device_model
firmware_version
Optional fields:

serial_source
generated_at
expires_at
provisioning_nonce
signature
dcam_data_contract_version
media_contract_version
encoder_contract_version
Exact JSON/URI/JWS serialization và signature/nonce/expiration/replay policy vẫn TBD.

Rules:

Rule

Description

APP-QR-001

Chỉ accept supported DCAM provisioning payload.

APP-QR-002

Missing serial bị reject.

APP-QR-003

Serial không editable.

APP-QR-004

Camera stream/frame không lưu hoặc upload.

APP-QR-005

QR không chứa long-lived secret hoặc factory Wi-Fi credential.

## 6. Form and Validation

Field

Direction

`serial_number`

Required, read-only, source từ QR.

`owner_name`

Required; exact source/length/charset TBD.

`manufacture_date`

Required, default factory local date, format `YYYY-MM-DD`; correction policy TBD.

Device/app metadata

Read-only nếu QR cung cấp.

Submit chỉ enabled khi QR valid, session valid và required fields hợp lệ.

## 7. API Integration

Logical endpoint:

```
POST /v1/factory/provisioning/devices
```

Concrete Cloud Function deployment name, Firebase Hosting rewrite và physical URL mapping là implementation/deployment detail còn TBD.

Request source:

Data

Source

Worker identity

Verified Firebase Authentication context.

`serial_number`

QR payload.

`owner_name`

Workspace form.

`manufacture_date`

Workspace form.

Device/app metadata

QR payload if accepted by API Contract.

Frontend không direct-write `serial_lookup`, `devices` hoặc `audit_events`.

## 8. Security and Privacy

Area

Rule

Authentication

Firebase Authentication.

Authorization

Backend validates active Factory Worker profile.

Browser storage

Không lưu password, identity token, raw QR payload hoặc factory Wi-Fi password.

Camera

Không persist/upload scan frame.

Error

Không expose raw Firebase/backend stack trace.

Conflict

Worker không override duplicate/rebind/restricted state.

Logout/session expiry

Clear current Workspace provisioning state.

## 9. Error Behavior

Case

Behavior

Camera permission denied

Hướng dẫn cấp quyền.

Invalid/unsupported QR

Reject and rescan.

Session expired

Return/re-authenticate at Login.

Validation error

Inline field error.

Backend timeout

Unknown outcome; reconciliation/idempotency policy.

Duplicate/rebind

Support Required.

Backend unavailable

Safe retry; do not assume success.

## 10. Resolved and Remaining Decisions

Item

Status

Web stack

Approved: Hosting/Auth/Functions/Firestore

Firebase Security Rules / IAM / environment separation

Pending / Backend + Security + Operations

Screens

Approved: Login + Workspace

User role

Approved: Factory Worker

Serial source

Approved: DCAM QR only

QR minimum logical schema

Approved in API Contract

Logical provisioning endpoint

Approved: `POST /v1/factory/provisioning/devices`

Concrete Cloud Function name/URL mapping

TBD / Deployment

QR serialization and cryptographic policy

TBD / API + Security

Supported browser/station profile

TBD / Factory + Implementation

Worker account model/lifecycle

TBD / Factory + Security

Owner source and exact validation

TBD / Product + Factory

Duplicate/rebind support process

TBD / Support + Backend

Printable/downloadable receipt

TBD / Product

Offline queue

Not planned; future decision if factory network requires it

## 11. QA Acceptance

Only Login and Workspace exist.
Valid worker can open Workspace.
QR serial is read-only.
No manual/barcode serial flow exists.
Invalid QR does not enable submit.
Created/Restored result shows dcam_cloud_device_id.
Conflict shows Support Required without override.
Session/camera/secret data is not persisted in browser storage.
## 12. Practical Conclusion

The app design is implementation-ready for UI structure, Firebase stack, QR minimum data and logical endpoint.
Only cryptographic QR details, deployment mapping, factory account policy and business master-data rules remain TBD.