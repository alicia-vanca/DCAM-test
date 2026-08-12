# DCAM Device Provisioning Web Portal Implementation Design

**Page ID**: 51019802  
**Version**: 6  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51019802

---


# DCAM Device Provisioning Web Portal Implementation Design

Item

Information

Project

DCAM Android BodyCamera Application

Document Type

Web Application Implementation Design

Version

Approved 1.3

Status

Approved

Approval Scope

Approved Phase 2 factory provisioning implementation direction under DEC-P2-WEB-01; production deployment and security evidence still require approved gates; not Build 0.1 or release approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Web Portal Lead / Backend Lead / Security Reviewer / QA Lead / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Page

DCAM Device Provisioning Web Portal Design

Target Audience

Web Developers, Backend Developers, QA, Security Reviewer, Factory Lead

Last Updated

2026-07-20

Related Jira

None

Related Documents

[DCAM Device Provisioning Web Portal Design](/wiki/spaces/DVID/pages/49315858/DCAM+Device+Provisioning+Web+Portal+Design), [DCAM Device Provisioning Web Portal App Design](/wiki/spaces/DVID/pages/50692194/DCAM+Device+Provisioning+Web+Portal+App+Design), [DCAM Web Portal & Device API Contract](/wiki/spaces/DVID/pages/49873154/DCAM+Web+Portal+Device+API+Contract), [DCAM Security & Encryption Design](/wiki/spaces/DVID/pages/48496720/DCAM+Security+Encryption+Design), [DCAM Factory Provisioning & Device Production SOP](/wiki/spaces/DVID/pages/49545629/DCAM+Factory+Provisioning+Device+Production+SOP), [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Tài liệu này mô tả implementation architecture của DCAM Device Provisioning Web Portal dùng trong nhà máy.

Authoritative dependencies:

DCAM Device Provisioning Web Portal Design
    → approved business flow: Factory Worker, QR-only, Login + Workspace

DCAM Device Provisioning Web Portal App Design
    → approved app model, Workspace panels và app-side behavior

DCAM Web Portal & Device API Contract
    → request, response, logical endpoint, schema, reason code và Firestore contract

DCAM Security & Encryption Design
    → authentication, authorization, camera privacy, secret handling và audit constraints

DCAM Factory Provisioning & Device Production SOP
    → physical factory process và production acceptance boundary
Tài liệu này tập trung vào frontend/backend modules, application state, QR scan, form/validation, Firebase integration, error behavior và QA acceptance.

Tài liệu không định nghĩa lại API schema, Device Owner setup, serial injection, factory Wi-Fi setup hoặc production acceptance.

### 1.1 DEC-P2-WEB-01 Applicability

[Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence) limits this local implementation direction to Phase 2 / `Secure Platform MVP Build 0.2` factory provisioning. It does not create an implementation backlog, change the API contract beyond the approved scope, close Security/Operations Pending decisions or activate Build 0.1/factory acceptance/shipment.

## 2. Approved Implementation Baseline

Deployment = Firebase Hosting
Authentication = Firebase Authentication
Backend API = Firebase Cloud Functions
Storage = Firebase Cloud Firestore
User-facing account type = Factory Worker only
Screens = Login and Workspace only
Web serial source = provisioning QR displayed by DCAM only
serial_number = read-only; no manual input; no direct barcode scan
owner_name = required
manufacture_date = required; defaults to factory browser local date; may select another valid date
Backend = authority for authentication verification, authorization, create/restore, duplicate policy and audit
Frontend = no direct write to Serial Lookup, Devices or Audit Events
Web App không:

Set hoặc verify Android Device Owner.

Inject serial vào Android.

Scan serial barcode trên tem thiết bị.

Cho nhập serial thủ công.

Verify Lock Task/kiosk policy.

Mark `PASS`, `FAIL`, `QUARANTINED` hoặc `READY_TO_SHIP`.

Cho Factory Worker override duplicate/rebind/conflict.

Gửi hoặc hiển thị factory Wi-Fi password.

## 3. High-level Architecture

Firebase Hosting
    ↓
DCAM Web Portal Frontend
    ↓
Firebase Authentication
    ↓
Firebase Cloud Functions
    ↓
Firebase Cloud Firestore

Component

Responsibility

Firebase Hosting

Phân phối SPA qua HTTPS và route request theo deployment config.

Web Portal Frontend

Hiển thị Login/Workspace, scan QR, validate UI, submit authenticated request và display result.

Firebase Authentication

Xác thực Factory Worker và cấp identity token.

Cloud Functions

Verify token/worker profile, validate request, create/restore identity, enforce policy và audit.

Cloud Firestore

Lưu worker profile, serial lookup, device record và audit data.

Frontend không được direct-write production provisioning collections.

## 4. Screen Model

Screen

Purpose

Login

Xác thực Factory Worker.

Workspace

Chứa QR scan, device review, owner/date input, inline review, submit, result và error behavior.

Scanner, Device Review, Device Information, Inline Review và Result/Error là panel/state trong `Workspace`, không phải route/screen riêng.

## 5. Frontend Module Architecture

### 5.1 Application Shell Module

Khởi tạo app.

Theo dõi authentication state.

Chọn `Login` hoặc `Workspace`.

Quản lý global loading/session expiration.

Reset Workspace state khi logout.

UNAUTHENTICATED → Login
AUTHENTICATED → Workspace
### 5.2 Authentication Module

Nhận worker credential.

Login bằng Firebase Authentication.

Theo dõi session và lấy identity token.

Logout và xử lý session hết hạn.

Out of scope: Sign Up, user management, role selection và password administration UI.

### 5.3 Workspace Module

Điều phối Workspace state machine.

Giữ QR/form state hiện tại.

Điều phối QR Scanner, Device Review, Device Information, Inline Review và Result/Error panels.

Block submit nếu session/data/state chưa hợp lệ.

Reset flow cho Rescan hoặc Provision Next Device.

### 5.4 QR Scanner Module

Yêu cầu camera permission.

Hiển thị preview/scan frame.

Đọc QR từ màn hình DCAM.

Dừng camera sau scan thành công.

Cho retry/rescan và hiển thị permission guidance.

Không được lưu/upload camera stream hoặc image; không có manual serial/barcode fallback.

### 5.5 QR Payload Module

Parse raw QR.

Validate `payload_type`, `payload_version` và `serial_number`.

Validate expiration/signature/replay khi policy enable.

Reject forbidden fields/secret.

Chuẩn hóa read-only device context.

Backend phải validate lại toàn bộ QR-derived data.

### 5.6 Device Review Module

Hiển thị read-only:

serial_number
device_model
firmware_version
app_package_name
app_version_name
app_version_code
serial_source
payload_version
generated_at / target environment nếu available
### 5.7 Device Information Module

Thu thập:

owner_name = required
manufacture_date = required
Không chứa serial input, cloud ID input, credential hoặc secret field.

### 5.8 Manufacture Date Module

Approved UX:

Manufacture Date

● Use today’s date
  Current date: YYYY-MM-DD

○ Select another date

[Date Picker]
Rules:

Today được chọn mặc định.

Dùng local calendar date của factory browser.

Manual date được enable khi chọn `Select another date`.

Giá trị gửi backend là `YYYY-MM-DD`, không phải timestamp.

Không được lệch ngày do UTC conversion.

Future/minimum-date policy theo API Contract/Product/Factory decision.

States:

TODAY_SELECTED
MANUAL_SELECTED_EMPTY
MANUAL_SELECTED_VALID
MANUAL_SELECTED_INVALID
### 5.9 Inline Review Module

Hiển thị serial, device context, owner, effective manufacture date, date source và environment. Worker có thể sửa owner/date hoặc rescan, nhưng không sửa serial.

### 5.10 Provisioning API Module

Lấy Firebase identity token.

Tạo `request_id`/idempotency context.

Gọi Cloud Function mapping với logical API contract.

Quản lý timeout/unknown outcome.

Parse stable response/reason code.

Logical API:

```
POST /v1/factory/provisioning/devices
```

Cloud Function có thể dùng HTTPS hoặc callable mapping, nhưng semantics phải theo DCAM Web Portal & Device API Contract.

### 5.11 Result / Error Module

Result handling maps the approved stable outcomes: `CREATED`, `RESTORED`, `REJECTED`, `FAILED` and `SUPPORT_REQUIRED`. Successful results may show `dcam_cloud_device_id`, serial, owner, manufacture date, device state and a safe next step.

Rejected/failed/support-required outcomes must have safe title/description, reason code where applicable and only valid actions: retry, rescan, login or support required.

## 6. Backend Module Architecture

### 6.1 Authentication Guard

Verify Firebase identity token.

Lấy authenticated UID.

Load worker profile.

Check active status và `Factory Worker` authorization.

Không tin role/worker id từ request body.

### 6.2 Request Validation

Validate contract version/request structure.

Validate QR-derived serial/device metadata.

Validate owner/date.

Reject forbidden/unexpected fields.

Reject manual replacement serial semantics.

### 6.3 QR Security Validation

Khi enabled:

nonce
generated_at
expires_at
signature
replay prevention
request-to-QR correlation
QR không được chứa factory Wi-Fi password, auth token, maintenance credential, Google credential hoặc signing secret.

### 6.4 Device Provisioning Service

Nhận authenticated/validated request.

Check `serial_lookup/{serial_number}`.

Restore existing identity nếu mapping/state cho phép.

Tạo new `dcam_cloud_device_id`, mapping và device record nếu serial mới.

Update allowed business information.

Ghi audit.

Trả Created/Restored hoặc support-required result.

Create/restore/mapping/device/audit phải dùng transaction hoặc atomic boundary phù hợp.

### 6.5 Repository Modules

Module

Responsibility

Device Repository

Read/create/update allowed device record và state.

Serial Lookup Repository

Resolve/create mapping; ngăn một serial map âm thầm tới nhiều active devices.

Worker Profile Repository

Read worker role/active status và safe audit metadata.

Audit Repository

Persist provisioning/security-relevant audit event.

Repository không tự quyết định support rebind policy ngoài approved service rule.

## 7. Logical Firestore Areas

Logical Area

Purpose

Worker Profiles

Worker identity, role và active status.

Serial Lookup

`serial_number` → `dcam_cloud_device_id`.

Devices

Identity, business information, metadata và device state.

Audit Events

Worker action, request id, result, reason code và changed fields.

Exact naming thuộc API Contract. Frontend không được direct-write các area này.

## 8. Application State Model

### 8.1 Authentication States

AUTH_LOADING
UNAUTHENTICATED
LOGIN_SUBMITTING
AUTHENTICATED
AUTH_ERROR
SESSION_EXPIRED
LOGGING_OUT
### 8.2 Workspace States

WORKSPACE_READY
CAMERA_PERMISSION_REQUIRED
CAMERA_OPENING
QR_SCANNING
QR_PROCESSING
QR_INVALID
DEVICE_REVIEW_READY
FORM_INCOMPLETE
REVIEW_READY
PROVISIONING_SUBMITTING
PROVISIONING_SUCCESS
PROVISIONING_FAILED
SUPPORT_REQUIRED
SESSION_EXPIRED
## 9. Login Flow

Open Web App
    ↓
Check Firebase authentication state
    ↓
If unauthenticated: show Login
    ↓
Factory Worker submits credential
    ↓
Firebase Authentication validates
    ↓
Backend/application verifies worker profile and active status
    ↓
If authorized: open Workspace
Else: remain Login and show safe error
Login UI không reveal account existence, raw Firebase error hoặc authorization config.

## 10. Main Provisioning Flow

Factory Worker opens Workspace
    ↓
Start QR scan
    ↓
Scan provisioning QR displayed by DCAM
    ↓
Parse and validate QR
    ↓
Show read-only device review
    ↓
Enter/select owner_name
    ↓
Use today's manufacture_date or select another valid date
    ↓
Validate form and show inline review
    ↓
Submit authenticated provisioning request
    ↓
Cloud Function verifies worker and request
    ↓
Backend checks serial lookup
    ↓
New serial → CREATED
Existing restorable serial → RESTORED
Conflict/restricted state → SUPPORT_REQUIRED
    ↓
Workspace displays result
Không có navigation sang Confirmation/Success/Failure screen riêng.

## 11. Validation Rules

### QR

Correct payload type/version.

Contains serial.

Serial format valid.

No forbidden identity/secret fields.

Expiration/signature/replay valid khi enabled.

### Serial

QR-derived only.

Read-only.

No manual/barcode input.

Backend validation required.

### Owner Name

Required.

Không chỉ whitespace.

Pass approved length/charset/source rule.

Backend validation required.

### Manufacture Date

Required.

Default local today hoặc selected valid date.

`YYYY-MM-DD`.

Không timestamp/date-shift.

Backend validation required.

## 12. UI Design

### Login

Product identity/title.

Email hoặc Worker ID field.

Password field.

Login button/progress/safe error.

Environment label cho non-production.

Không Sign Up hoặc role selection.

### Workspace Header

Product/Workspace title.

Worker display name/code.

Environment.

Logout.

### QR Scanner Panel

Instruction, preview, scan frame, start/retry/cancel và permission guidance.

Camera chỉ active trong scan state.

Invalid QR không mở form.

### Device Review Panel

Prominent read-only serial.

Model/firmware/app/serial source/QR metadata.

Rescan resets QR/form state.

### Device Information Panel

Required owner field.

Today/select-another manufacture-date control.

### Inline Review / Submit

Serial, owner, manufacture date/source, device context, environment.

Edit allowed info, Rescan, Submit Provisioning.

### Result / Error

Created/Restored details hoặc safe error/support-required state.

Actions: Provision Next Device, Return to Scanner, Logout, Retry, Rescan hoặc Login tùy state.

## 13. Error Handling Principles

Không display stack trace/raw Firebase internal error.

Không expose token, service credential hoặc factory Wi-Fi password.

Dùng stable reason code.

Chỉ retry khi safe.

Timeout = unknown outcome; không assume failed/success.

Backend phải support idempotency/reconciliation direction.

Duplicate/rebind/restricted state chuyển support process; Factory Worker không override.

## 14. Security Requirements

Factory Worker login required before Workspace.

Backend verifies token, worker profile, active status and authorization.

Frontend cannot direct-write provisioning collections.

Serial is QR-derived and read-only.

Password/token is not stored as business data or logged.

QR contains no long-lived secret.

Camera stream/image is not stored/uploaded.

Factory Worker cannot override duplicate/rebind/restricted states.

Audit records authenticated worker identity and request context.

Production/non-production environments are separated.

Logout/session expiration clears current provisioning state.

Firestore rules/backend authorization reject unauthorized access.

Factory Wi-Fi password is outside Web Portal/API payload and must never be displayed/logged.

## 15. Responsive and Factory Usability

Tablet/desktop station support.

Mobile/tablet single-column flow; desktop may use two columns without changing business order.

Serial prominent.

Submit/Rescan separated.

Loading prevents duplicate submit.

Errors not color-only.

Success/failure uses icon + text.

Current local date displayed explicitly.

Recommended Workspace order:

Header
QR Scanner
Device Review
Device Information
Inline Review
Result / Error
## 16. QA Acceptance Checklist

### Authentication

Valid active Factory Worker opens Workspace.

Invalid/inactive worker remains Login or is blocked safely.

Session expiration blocks submit.

Logout resets state.

### Screen Model

Only Login and Workspace are screens.

Scanner/review/inline review/result are Workspace panels/states.

### QR and Serial

Valid DCAM QR parses.

Wrong type/version/missing serial rejected.

Serial read-only.

No manual or direct barcode serial flow.

Camera stream/image not stored/uploaded.

### Owner / Date

Owner required and validated.

Today default works using local calendar date.

Manual selected date works.

Request uses `YYYY-MM-DD` without timezone shift.

### Provisioning

New serial → Created.

Existing allowed serial → Restored.

Conflict/restricted state → Support Required.

Backend unavailable/timeout shows safe behavior.

Success displays cloud device ID.

Provision Next Device resets flow/date mode.

### Security

Frontend does not direct-write provisioning collections.

Token/password/factory Wi-Fi credential not displayed or logged.

Forbidden Android identity fields rejected.

Worker cannot override conflict.

Audit contains worker/request context.

## 17. Approved Decisions and Remaining Inputs

Approved:

Firebase Hosting, Firebase Authentication, Cloud Functions and Firestore.

Factory Worker only.

Login and Workspace only.

QR-only serial source; read-only serial.

Required owner and manufacture date.

Today/select-another date UX.

Backend authority for create/restore/duplicate/audit.

Logical provisioning API maps to `/v1/factory/provisioning/devices` semantics.

Remaining inputs:

Owner source/validation exact rule.

Supported browser/factory station profile.

Individual vs shared worker account.

QR signing/expiration/replay policy.

Firebase Security Rules, IAM and production/non-production environment separation.

Duplicate/rebind support process.

Exact Cloud Function name/path mapping.

Idempotency/unknown-outcome reconciliation detail.

Manufacture-date minimum/future-date policy.

Audit retention.

## 18. Dependency Review Result

Dependency

Reviewed Status

Result

Web Portal Business Flow Design

Approved 1.0

Match: Factory Worker, QR-only, Login/Workspace, no production acceptance.

Web Portal App Design

Approved 1.1

Match: panels/states inside Workspace, read-only serial, required owner/date.

API Contract

Draft 0.6

Match: Firebase auth context, Cloud Functions authority, QR-only serial, factory provisioning endpoint semantics.

Security Design

Draft 1.2

Match: Factory Worker authorization, camera privacy, no override, secret restrictions.

Factory SOP

Draft 1.2

Match: DSetup stops after serial import verification; Factory/QA owns acceptance.

## 19. Practical Conclusion

DCAM Device Provisioning Web Portal is a single-purpose factory Web App.
Application has Login and Workspace only.
User-facing account type is Factory Worker only.
Workspace contains QR scan, device review, required owner/date input, inline review, submit and result/error behavior.
serial_number comes only from DCAM provisioning QR and remains read-only.
Firebase Hosting serves the frontend.
Firebase Authentication authenticates Factory Worker.
Cloud Functions verify authorization, validate request, create/restore identity, enforce conflict policy and write audit.
Firestore stores worker profile, serial lookup, device record and audit according to API Contract.
Frontend does not direct-write provisioning collections.
Factory Worker cannot override duplicate/rebind/restricted state.
Web Portal does not set Device Owner, inject serial, run factory acceptance or decide READY_TO_SHIP.