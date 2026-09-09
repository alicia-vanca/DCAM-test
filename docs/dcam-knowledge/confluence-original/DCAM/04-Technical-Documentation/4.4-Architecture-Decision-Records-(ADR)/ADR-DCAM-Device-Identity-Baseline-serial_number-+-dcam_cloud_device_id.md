# ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id

**Page ID**: 50692110  
**Version**: 7  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50692110

---


# ADR - DCAM Device Identity Baseline: `serial_number` + `dcam_cloud_device_id`

Item

Information

Project

DCAM Android BodyCamera Application

Document Type

Architecture Decision Record

Version

Approved 1.9

Status

Approved

Approval Scope

Device Identity direction: serial_number là Hardware Identity / primary recovery key; dcam_cloud_device_id là Cloud Identity and is exposed as DDMP platformDeviceId without introducing a third root identity; implementation, migration, Security/Factory/QA evidence chưa được Production-approved.

Decision Date

2026-07-09

DDMP Integration Approval Date

2026-08-24

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Backend Lead / Web Portal Lead / Security Reviewer / Factory Lead / QA Lead / BDMA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.4 - Architecture Decision Records (ADR)

Target Audience

PM/BA, Tech Lead, Android Developers, Backend/Web Portal Developers, QA, Factory, Support, Security Reviewer, BDMA Team

Last Updated

2026-08-26

Related Jira

None

Dependencies / Blockers

Security Review; downstream implementation alignment; Factory/QA evidence; authoritative GitHub/Jira linkage.

Related Documents

DCAM Project Home, DCAM Architecture Home, DCAM Documentation Governance, 04 - Device Configuration Requirements, DCAM Android Operation Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, DCAM Web Portal & Device API Contract, DCAM Device Provisioning Web Portal Design, DCAM Factory Provisioning & Device Production SOP, DCAM QA Test Strategy & Test Matrix, DDMP Architecture Overview & Reading Guide, 03 Management API / BFF, 06 Device Integration Contracts

## 1. Status

`Approved Direction`.

Identity direction trong ADR này đã được phê duyệt theo approved status taxonomy:

Item

Approval Result

serial_number

Hardware Identity / primary recovery key.

dcam_cloud_device_id

Cloud Identity / primary cloud device ID; exposed to DDMP as `platformDeviceId`.

DDMP platformDeviceId

Contract name of the same logical identifier as `dcam_cloud_device_id`; not a third root identity.

Identity mapping semantics

Approved Direction theo nội dung ADR.

Exact implementation / migration

Chưa được Production-approved; phải theo downstream Technical Review và change control.

Security / Factory / QA evidence

Chưa hoàn tất; giữ là dependency mở.

Production Approval

Not Granted.

Status này thay thế cả metadata `Accepted` và body `Proposed` trước đây. `Approved Direction` không được diễn giải là implementation complete, Device POC pass hoặc `Production Approved`.

Các tài liệu downstream cần tham chiếu ADR này và chỉ ghi local implementation impact. Nếu downstream baseline mâu thuẫn, phải mở controlled change; không copy hoặc tự tạo identity baseline mới.

## 2. Context

DCAM là Android BodyCamera Application chạy trong dedicated-device / kiosk deployment. Thiết bị cần một identity model ổn định để dùng xuyên suốt các tình huống production và support.

Identity model phải hoạt động được trong các trường hợp sau:

Android app update
app reinstall trong approved rework flow
factory reset
DSetup factory provisioning
SD card recovery
Factory Portal / BFF business provisioning
Backend identity restore
BDMA import / user sync boundary
QA release readiness
Factory READY_TO_SHIP / QUARANTINED decision
Support diagnostics
Các draft cũ từng đề cập tới Android-derived identity, bao gồm:

ANDROID_ID
android_id_hash
device_lookup/{android_id_hash}
Cách tiếp cận này không phù hợp với current production baseline vì `ANDROID_ID` và `android_id_hash` không phải Hardware Identity của BodyCamera. Chúng phụ thuộc vào Android runtime / app / user / firmware behavior và có thể gây duplicate cloud device sau factory reset, reinstall hoặc rework.

Trong current DCAM baseline, Factory SOP đã có nguồn Hardware Identity rõ ràng là `serial_number`. Vì vậy identity baseline cần thống nhất quanh `serial_number` và `dcam_cloud_device_id`.

DDMP bổ sung BFF Platform DB và Headwind Community integration, nhưng không được tạo một identity hierarchy song song. DDMP dùng tên contract `platformDeviceId` cho chính Cloud Identity hiện hữu là `dcam_cloud_device_id`. `headwindDeviceRef` chỉ là external mapping do BFF quản lý; không phải identity gốc hoặc credential.

## 3. Decision

DCAM current production baseline sử dụng identity model sau:

Hardware Identity:
    serial_number

Cloud Identity:
    dcam_cloud_device_id

Cloud lookup / restore path:
    serial_lookup/{serial_number}

Cloud device record:
    devices/{dcam_cloud_device_id}

Recovery cache:
    SD Identity File on external SD card
### 3.1 DDMP identity mapping

platformDeviceId = dcam_cloud_device_id
dcamInstallationId = local installation lifecycle correlation only
headwindDeviceRef = Headwind mapping only; not authentication identity
Rules:

BFF must preserve the one-to-one platformDeviceId ↔ dcam_cloud_device_id semantic.
DCAM stores and sends its resolved dcam_cloud_device_id as platformDeviceId in the BFF contract.
dcamInstallationId may rotate after approved reinstall/rework and must not create a new cloud device.
headwindDeviceRef is not a recovery key, a global identity or a device credential.
Headwind JWT is never a device credential.
DCAM current production baseline không sử dụng:

ANDROID_ID
android_id_hash
device_lookup/{android_id_hash}
Advertising ID
owner_name as identity
manufacture_date as identity
SD Identity File as authoritative identity
## 4. Identity Model

Identity

Field

Meaning

Source / Owner

Stability

Hardware Identity

`serial_number`

Định danh vật lý của BodyCamera và primary recovery key.

Factory / DSetup / DCAM runtime

Ổn định theo vòng đời production nếu được recover đúng sau factory reset hoặc rework.

Cloud Identity / DDMP Platform Identity

`dcam_cloud_device_id` / `platformDeviceId`

Một logical cloud device id. `platformDeviceId` là tên trong BFF ↔ DCAM contract cho cùng định danh này.

BFF issues or restores; DCAM persists it

Ổn định sau khi device được provisioned hoặc restored.

Installation Correlation

`dcamInstallationId`

Local installation lifecycle correlation cho reinstall/re-enrollment diagnostics; không dùng làm cloud primary key hoặc recovery key.

DCAM runtime

Có thể đổi sau approved reinstall/rework.

External Management Mapping

`headwindDeviceRef`

Reference tới Headwind-owned record; chỉ dùng reconciliation.

BFF / Headwind

Có thể thay đổi; không phải authentication identity.

Recovery Cache

SD Identity File

File trên external SD card dùng để cache `serial_number` cho factory reset recovery.

DCAM runtime + DSetup

Có ích cho recovery nhưng không phải authoritative identity.

Device Information

`owner_name`, `manufacture_date`, `device_model`, `firmware_version`

Thông tin hiển thị, support, factory và admin.

Web Portal / Factory / Backend / DCAM mirror

Mutable hoặc semi-static, không phải identity key.

## 5. Identity Rules

### 5.1 `serial_number`

`serial_number` là Hardware Identity và primary recovery key của BodyCamera.

Rules:

serial_number is Hardware Identity / primary recovery key.
serial_number is used to create or restore dcam_cloud_device_id.
serial_number is injected by DSetup or recovered from approved factory source.
serial_number is stored locally by DCAM after validation.
serial_number may be mirrored to dcam_config.cson and dcam.db according to related design documents.
serial_number may be synced to SD Identity File as recovery cache.
serial_number must not be silently overwritten after business provisioning.
serial_number change must be exceptional, permission-controlled and auditable.
### 5.2 `dcam_cloud_device_id`

`dcam_cloud_device_id` là Cloud Identity và primary cloud/backend device id.

Rules:

dcam_cloud_device_id is generated or restored by BFF.
dcam_cloud_device_id is the primary key for devices/{dcam_cloud_device_id}.
dcam_cloud_device_id is stored locally after successful provisioning or restore.
dcam_cloud_device_id is used for cloud requests after identity is resolved.
dcam_cloud_device_id must not be derived from Android system identifiers.
### 5.3 `serial_lookup/{serial_number}`

`serial_lookup/{serial_number}` là production lookup path duy nhất để create/restore Cloud Identity trong current baseline.

Rules:

If serial_lookup/{serial_number} exists:
    restore existing dcam_cloud_device_id.

If serial_lookup/{serial_number} does not exist:
    create new dcam_cloud_device_id only in an authorized factory/admin provisioning flow.

If serial_lookup/{serial_number} maps to DISABLED, REVOKED or QUARANTINED device:
    Android must not enter normal field operation.

Duplicate serial_number must not silently create another active cloud device.
### 5.4 DDMP identity mapping

`platformDeviceId` là tên contract của cùng Cloud Identity `dcam_cloud_device_id`, không phải một global ID mới.

Rules:

BFF issues/restores and binds platformDeviceId using the existing dcam_cloud_device_id identity semantics.
DCAM uses resolved dcam_cloud_device_id as platformDeviceId for BFF sync/enrollment.
serial_lookup/{serial_number} remains the only current production create/restore lookup path.
headwindDeviceRef is held by BFF as an external mapping and must not be trusted for device authentication.
dcamInstallationId is local correlation only; it never replaces serial_number or dcam_cloud_device_id.
### 5.5 SD Identity File

SD Identity File là recovery cache, không phải Hardware Identity.

Recommended path:

```
<SD_CARD>/DCAM_FACTORY/device_identity.json
```

Recommended payload:

{
  "schema_version": 1,
  "identity_type": "BODYCAMERA_SD_FACTORY_IDENTITY",
  "serial_number": "BC240001",
  "created_by": "DSetup|DCAM",
  "created_at": "2026-07-09T00:00:00Z",
  "updated_at": "2026-07-09T00:00:00Z",
  "signature": "optional-approved-signature-or-checksum"
}
Rules:

DCAM app-private serial_number is source of truth while DCAM is running.
If SD Identity File is missing, DCAM may recreate it from app-private serial_number.
If SD card is replaced, DCAM may create SD Identity File on the new card.
If SD Identity File conflicts with app-private serial_number, app-private serial_number wins.
DSetup may use SD Identity File after factory reset only if the file is valid.
If SD Identity File is missing, invalid or conflicting, DSetup must request barcode scan or quarantine according to Factory SOP.
## 6. Explicit Rejections

Mechanism

Decision

Reason

`ANDROID_ID`

Rejected

Không phải Hardware Identity của BodyCamera; phụ thuộc Android runtime / app / user / OEM behavior.

`android_id_hash`

Rejected

Hashing không giải quyết được vấn đề lifecycle và vẫn phụ thuộc Android system identifier.

`device_lookup/{android_id_hash}`

Rejected

Có thể tạo duplicate device sau factory reset hoặc rework và không align với Factory SOP.

Advertising ID

Rejected

Không phù hợp làm device identity hoặc recovery key.

IMEI / Wi-Fi MAC / Bluetooth MAC

Not baseline

App-level access có thể bị hạn chế, privacy-sensitive và không ổn định trên mọi Android/OEM build.

`owner_name` as identity

Rejected

Đây là mutable device information.

`manufacture_date` as identity

Rejected

Đây là semi-static metadata, không phải unique identity.

SD Identity File as primary identity

Rejected

File này có thể bị xóa, copy, tráo SD card hoặc conflict; chỉ được xem là recovery cache.

Separate DDMP root device ID

Rejected

`platformDeviceId` must represent the existing `dcam_cloud_device_id`, avoiding a competing logical identity.

`headwindDeviceRef` as device identity or authentication

Rejected

Đây là external mapping state và do Headwind lifecycle chi phối; không đủ cho recovery hoặc trust decision.

## 7. Rationale

Quyết định này được chọn vì các lý do sau:

`serial_number` là Hardware Identity gần nhất với thiết bị vật lý BodyCamera.

`dcam_cloud_device_id` giúp BFF có một primary cloud device id ổn định, không phụ thuộc trực tiếp vào Android runtime identifier.

`serial_lookup/{serial_number}` giúp factory reset hoặc rework restore lại đúng cloud identity cũ.

DSetup có thể recover `serial_number` từ SD Identity File hoặc barcode scan mà không cần phụ thuộc `ANDROID_ID`.

Factory, QA, Support, Backend và BDMA có cùng một baseline để trace physical device ↔ cloud device.

Rủi ro duplicate cloud device sau factory reset được giảm đáng kể.

## 8. Consequences

### 8.1 Positive Consequences

Factory reset recovery trở nên deterministic hơn.
Backend identity restore đơn giản hơn.
Cloud device duplication risk giảm.
DCAM không phụ thuộc Android ID behavior.
Factory SOP, API Contract, Android runtime và QA có thể dùng cùng một identity model.
Support có thể mapping physical device với cloud device thông qua serial_number.
### 8.2 Negative / Trade-off Consequences

serial_number phải được bảo vệ khỏi accidental overwrite.
serial_number conflict handling phải rõ ràng.
SD Identity File có thể sai nếu SD card bị tráo giữa hai BodyCamera.
Barcode label / factory record quality trở nên quan trọng hơn.
Backend phải enforce duplicate active serial mapping prevention.
Support flow cần xử lý serial conflict, rebind hoặc exceptional serial change.
### 8.3 Security Consequences

serial_number is not a secret, but it is security-sensitive operational identity.
serial_number must not be used as an authentication secret.
Knowing serial_number alone must not grant device control or admin access.
Cloud APIs still require device/admin authentication and authorization.
SD Identity File should have validation, checksum or signature if supported.
Logs should avoid unnecessary exposure of serial_number and must not include secrets.
## 9. Implementation Direction

### 9.1 Android Runtime

Android must:

Read local app-private serial_number if available.
Read local dcam_cloud_device_id if available.
Use dcam_cloud_device_id for cloud requests after identity is resolved.
If dcam_cloud_device_id is missing but serial_number exists, restore through serial_lookup/{serial_number}.
If serial_number is missing, enter SERIAL_REQUIRED / PROVISIONING_REQUIRED / approved factory state.
Never use ANDROID_ID as production identity.
Never compute or send android_id_hash as production recovery identity.
Never call device_lookup/{android_id_hash} in current production baseline.
Use resolved dcam_cloud_device_id as platformDeviceId when enrolling or syncing with DDMP BFF.
Never use headwindDeviceRef or Headwind JWT as a device identity/credential.
### 9.2 DSetup / Factory SOP

DSetup must:

Recover serial_number from SD Identity File if valid.
Fallback to barcode scan if SD Identity File is missing, invalid or conflicting.
Inject serial_number into DCAM after Device Owner verification.
Record serial source in production record.
Use serial_number for BFF Factory Portal business provisioning or restore flow.
Bind the restored dcam_cloud_device_id to DDMP BFF as platformDeviceId; persist any headwindDeviceRef only as external mapping state.
Never use ANDROID_ID or android_id_hash for production provisioning.
Never treat Web Portal QR Flow as Android Device Owner setup.
### 9.3 BFF / Factory Portal

Backend must:

Use serial_lookup/{serial_number} to resolve dcam_cloud_device_id.
Store device records under devices/{dcam_cloud_device_id}.
Prevent duplicate active serial_number mapping.
Audit serial create, restore, conflict and exceptional change actions.
Reject or deprecate device_lookup/{android_id_hash} for current production baseline.
### 9.4 SQLite / Local DB

`dcam.db` should support local persistence of:

dcam_cloud_device_id
serial_number
serial_source
identity_state
last_identity_lookup_at
last_identity_restore_at
sd_identity_sync_state
device_identity_history
provisioning_state
dcam_installation_id
headwind_device_ref_mapping_state
`dcam.db` must not store:

raw ANDROID_ID
android_id_hash as production recovery key
Advertising ID as device identity
### 9.5 Security

Security Design must align with this statement:

serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity / primary cloud device id
SD Identity File = recovery cache only
ANDROID_ID / android_id_hash / device_lookup are not used in current production baseline
## 10. Migration / Documentation Cleanup

The following old wording must be removed from current-baseline documents:

Recovery lookup key = android_id_hash
serial_number = mutable device information
device_lookup/{android_id_hash}
Local QR provisioning / device_lookup
Android only checks device_lookup/{android_id_hash}
Replace with:

Recovery lookup key = serial_number
serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity / primary cloud device id
serial_lookup/{serial_number}
Factory Portal/BFF creates or restores devices/{dcam_cloud_device_id} by serial_number

Document

Required Change

DCAM Security & Encryption Design

Sửa identity/security/recovery model từ `android_id_hash` sang `serial_number`.

DCAM QA Test Strategy & Test Matrix

Sửa các test `device_lookup/{android_id_hash}` sang `serial_lookup/{serial_number}`.

DCAM Architecture Overview

Kiểm tra lại identity và update baseline để không còn nội dung cũ.

DCAM Device Provisioning Web Portal Design

Đảm bảo QR nếu dùng chỉ là business provisioning và dùng `serial_number`.

DCAM Web Portal & Device API Contract

Giữ làm source of truth cho API/data contract của `serial_lookup`.

DCAM Factory Provisioning & Device Production SOP

Giữ làm source of truth cho DSetup, barcode scan, SD Identity File và ready-to-ship flow.

DDMP 06 Device Integration Contracts

`platformDeviceId = dcam_cloud_device_id`, `dcamInstallationId` local-only và `headwindDeviceRef` mapping-only phải giữ cùng semantics.

## 11. QA Acceptance Criteria

QA must verify:

A clean factory device can be provisioned with barcode-scanned serial_number.
A factory-reset device can recover serial_number from valid SD Identity File.
A factory-reset device without valid SD Identity File requires barcode scan.
serial_lookup/{serial_number} restores the same dcam_cloud_device_id.
Duplicate serial_number does not create a second active cloud device.
ANDROID_ID is not sent, stored or logged.
android_id_hash is not sent, stored or logged as production identity.
device_lookup/{android_id_hash} is not used.
SD Identity File conflict does not override app-private serial_number during normal operation.
Device with DISABLED / REVOKED / QUARANTINED cloud state cannot enter normal field operation.
BFF receives dcam_cloud_device_id as platformDeviceId without creating a second root device identity.
headwindDeviceRef cannot authenticate a device or alter its resolved cloud identity.
Production record includes safe serial source metadata but no Android ID/hash.
Suggested QA cases:

Test ID

Scenario

Expected Result

QA-ID-001

New device provisioned by barcode serial.

Backend creates or restores `dcam_cloud_device_id` through `serial_lookup/{serial_number}`.

QA-ID-002

Factory reset with valid SD Identity File.

DSetup recovers same `serial_number`; Backend restores same `dcam_cloud_device_id`.

QA-ID-003

Factory reset without valid SD Identity File.

DSetup requires barcode scan; Backend restores by scanned `serial_number`.

QA-ID-004

SD Identity File conflicts with app-private serial.

App-private serial wins; conflict is logged/audited safely.

QA-ID-005

Duplicate serial provisioning attempt.

Backend blocks duplicate active device or requires admin conflict resolution.

QA-ID-006

Android ID/hash inspection.

No `ANDROID_ID`, `android_id_hash` or `device_lookup` is used in current production flow.

QA-ID-007

Disabled/revoked/quarantined device.

Android does not enter normal field operation.

QA-ID-008

DDMP enrollment/sync identity.

BFF receives the resolved `dcam_cloud_device_id` as `platformDeviceId`; no third root ID is created.

QA-ID-009

Headwind mapping change or re-enrollment.

`headwindDeviceRef` may reconcile but cannot authenticate, replace or restore cloud identity.

## 12. Alternatives Considered

Alternative

Decision

Reason

Use `ANDROID_ID`

Rejected

Không phải physical BodyCamera identity và có lifecycle không phù hợp với factory reset / rework.

Use `android_id_hash`

Rejected

Che raw identifier nhưng không giải quyết được vấn đề stability và vẫn lệch Factory SOP.

Use `device_lookup/{android_id_hash}`

Rejected

Có thể tạo duplicate device và làm Web Portal/API/QA lệch identity baseline.

Use IMEI / MAC / hardware radio identifiers

Not selected

Có thể không accessible, privacy-sensitive và phụ thuộc Android/OEM build.

Use only `serial_number` as cloud primary key

Rejected

Backend vẫn cần `dcam_cloud_device_id` làm Cloud Identity ổn định để hỗ trợ migration, audit, rebind và internal reference.

Create a separate DDMP `platformDeviceId` root identity

Rejected

Tạo competing identity/migration burden; DDMP contract uses existing `dcam_cloud_device_id` semantic instead.

Use `headwindDeviceRef` as primary device identity

Rejected

Headwind reference is integration-owned mapping, not a stable recovery or trust identity.

## 13. Final Decision Summary

DCAM current production identity baseline là:

serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity / primary cloud device id
SD Identity File = recovery cache on external SD card
serial_lookup/{serial_number} = cloud create/restore lookup
devices/{dcam_cloud_device_id} = cloud device record
platformDeviceId = DDMP contract name for the same dcam_cloud_device_id
headwindDeviceRef = external Headwind mapping only
dcamInstallationId = local installation lifecycle correlation only
Current production baseline explicitly does not use:

ANDROID_ID
android_id_hash
device_lookup/{android_id_hash}
Advertising ID
owner_name as identity
manufacture_date as identity
SD Identity File as authoritative identity
ADR này thống nhất Android runtime, DSetup, Factory SOP, Web Portal, BFF, PostgreSQL ddmp, SQLite, Security, QA và BDMA-facing behavior quanh một identity model duy nhất.

## Device credential boundary

Credential and identity are separate by decision. The authoritative device credential model is [ADR – DCAM Device API Credential & mTLS Baseline](/wiki/spaces/DVID/pages/70287362/ADR+DCAM+Device+API+Credential+mTLS+Baseline). serial_number and dcam_cloud_device_id/platformDeviceId retain their approved identity semantics but must never independently authenticate a device. A certificate/key binding is managed by BFF and does not create a third root identity.