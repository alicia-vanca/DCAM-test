# 04 - Device Configuration Requirements

**Page ID**: 47710554  
**Version**: 10  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710554

---


# 04 - Device Configuration Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.7

Status

Approved

Approval Scope

Device configuration requirements với narrow Build 0.1 operator exception

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Cloud Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, BDMA Developers, QA, Cloud/WebServer Team

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Release & Build Applicability Matrix, DCAM Factory Provisioning & Device Production SOP, DCAM Device Provisioning Web Portal Design, DCAM Device Provisioning Web Portal App Design, DCAM Web Portal & Device API Contract, DCAM Requirements Home, DCAM-BDMA Data Contract, 09 - System Settings Requirements, 06 - Cloud Services, Update & Configuration Architecture, DCAM SQLite Database Design, DCAM Android Operation Design, DCAM Security & Encryption Design, 08 - DCAM-BDMA Integration Boundary, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này ghi nhận các yêu cầu chức năng liên quan đến **device configuration** của DCAM.

Các rule chi tiết về `dcam_config.cson`, device information, device identity, Firebase/WebServer identity và remote config boundary thuộc các tài liệu authoritative tương ứng.

Trang này áp dụng requirement-level baseline từ **DCAM Factory Provisioning & Device Production SOP**, **ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id** và **DCAM Web Portal & Device API Contract**. Không hardcode version của dependent document trong requirement text; current approved/draft version được quản lý bởi Project Home và Governance.

serial_number is Hardware Identity / primary recovery key.
dcam_cloud_device_id is the Firebase/WebServer Cloud Identity / primary key.
SD Identity File is a recovery cache on external SD card, not Hardware Identity.
owner_name is device information and may change.
manufacture_date is device information and should use ISO date format YYYY-MM-DD.
serial_lookup/{serial_number} is used to create/restore dcam_cloud_device_id.
ANDROID_ID must not be used for production identity.
android_id_hash must not be used as recovery lookup key in the current baseline.
device_lookup/{android_id_hash} must not be used in the current baseline.
Build applicability is defined by **DCAM Release & Build Applicability Matrix**. Build `0.1` only requires the minimal local identity/config subset; cloud identity and Web Portal provisioning become required from Build `0.2`.

## 2. Device Configuration Requirements

Requirement Area

Requirement Direction

Status

Device Config File

DCAM phải tạo và maintain `dcam_config.cson` trong Internal Storage.

Approved

Device Information

`dcam_config.cson` chỉ lưu static/semi-static device information và identity mirror cần thiết cho BDMA/support display.

Approved

Serial Number

`serial_number` là Hardware Identity / primary recovery key; lưu trong app-private storage, mirror vào `dcam_config.cson`, `dcam.db` và Firebase/WebServer.

Approved

Cloud Device ID

`dcam_cloud_device_id` là Firebase/WebServer primary cloud device id.

Approved

SD Identity File

SD Identity File trên thẻ nhớ ngoài là recovery cache chứa `serial_number`; không phải Hardware Identity.

Approved

Owner Name

`owner_name` là thông tin chủ sở hữu/đơn vị sở hữu thiết bị, lưu trong `dcam_config.cson`, mirror vào `dcam.db` và Firebase/WebServer.

Approved

Manufacture Date

`manufacture_date` là ngày sản xuất thiết bị, lưu trong `dcam_config.cson`, mirror vào `dcam.db` và Firebase/WebServer; format chuẩn là `YYYY-MM-DD`.

Approved

Serial Mutability

Serial không được tự ý thay đổi trong normal operation; thay đổi chỉ qua approved factory/rework/admin flow.

Approved

Device Information Mutability

`owner_name` và `manufacture_date` là device information; có thể được cập nhật qua approved provisioning/admin/config flow.

Approved

Device Identity Key

Firebase/WebServer primary key là `dcam_cloud_device_id`; recovery/create/restore dùng `serial_lookup/{serial_number}`.

Approved

No Android ID Dependency

DCAM không dùng `ANDROID_ID`, `android_id_hash` hoặc `device_lookup/{android_id_hash}` trong current production baseline.

Approved

Advertising ID

DCAM không dùng Advertising ID làm primary key hoặc recovery key.

Approved

CSON Update

DCAM/WebServer may update `dcam_config.cson` only for device information fields such as serial, owner name, manufacture date, device model and firmware information.

Approved Direction

Config Validation

DCAM cần xử lý trường hợp config missing, invalid hoặc unreadable bằng fallback/diagnostics an toàn.

Approved Direction

Operational Settings Boundary

Operational/runtime settings không lưu trong `dcam_config.cson`; phải lưu trong `dcam.db`.

Approved

Provisioning State

Nếu local `dcam_cloud_device_id` thiếu, DCAM phải dùng `serial_number` để restore cloud identity; nếu lookup không có mapping thì vào `PROVISIONING_REQUIRED`. Nếu local serial thiếu, DSetup thực hiện approved recovery/injection flow.

Approved

## 3. Device Identity and Device Information Rule

Field

Storage

Mutability

Purpose

`dcam_cloud_device_id`

`dcam.db`, Firebase/WebServer

Stable after provisioning

Primary device key on Firebase/WebServer.

`serial_number`

App-private storage, `dcam_config.cson`, `dcam.db` mirror, Firebase/WebServer, SD Identity File cache

Stable Hardware Identity; changed only by approved rework/admin flow

Hardware Identity and primary recovery key.

SD Identity File

External SD card, e.g. `<SD_CARD>/DCAM_FACTORY/device_identity.json`

Recreated/synced from app-private serial; may be lost if SD is formatted/replaced

Recovery cache used by DSetup after factory reset.

`owner_name`

`dcam_config.cson`, `dcam.db` mirror, Firebase/WebServer

Mutable / semi-static

Owner, customer, agency or organization name for admin/support/BDMA display.

`manufacture_date`

`dcam_config.cson`, `dcam.db` mirror, Firebase/WebServer

Semi-static; corrected only through approved admin flow

Device manufacture date in ISO format `YYYY-MM-DD`.

`serial_history`

Firebase/WebServer, optional DB mirror

Append-only direction

Tracks previous serial values if approved rework/admin flow changes serial.

`firebase_installation_id`

`dcam.db`, Firebase/WebServer metadata

May change after reinstall

Current app-install metadata only; not a device identity key.

Rules:

serial_number is Hardware Identity / recovery key.
dcam_cloud_device_id is Cloud Identity / primary server key.
SD Identity File is recovery cache only.
owner_name must not be used as primary key.
manufacture_date must not be used as primary key.
Advertising ID must not be used as primary key.
ANDROID_ID must not be used as production identity.
android_id_hash must not be used as recovery lookup key in the current baseline.
manufacture_date should use ISO date format YYYY-MM-DD.
## 4. First Install / Missing Local Identity Requirement

When DCAM starts and local identity is missing:

Open dcam.db and read dcam_config.cson
    ↓
If local dcam_cloud_device_id and serial_number exist:
    use local identity
    sync SD Identity File if available
    ↓
If local dcam_cloud_device_id is missing but serial_number exists:
    lookup Firebase/WebServer:
        serial_lookup/{serial_number}
    ↓
    If found:
        fetch device record
        restore dcam_cloud_device_id
        restore owner_name if available
        restore manufacture_date if available
        recreate dcam.db / dcam_config.cson as needed
        sync SD Identity File if available
    ↓
    If not found:
        enter PROVISIONING_REQUIRED
        display provisioning QR according to approved Web Portal flow
    ↓
If local serial_number is missing:
    require DSetup serial recovery/injection from SD Identity File or approved barcode-assisted factory flow
Factory reset recovery:

Factory reset clears app-private serial and cloud identity.
DSetup runs again.
DSetup reads SD Identity File if present and valid.
If SD Identity File is valid:
    DSetup recovers serial_number without barcode scan.
If SD Identity File is missing/invalid:
    Factory Operator scans barcode through DSetup.
DSetup injects serial_number into DCAM using the approved mechanism.
DSetup verifies DCAM imported serial_number.
DCAM restores dcam_cloud_device_id through serial_lookup/{serial_number} when mapping exists.
If mapping does not exist, DCAM displays provisioning QR for Web Portal provisioning.
## 5. Web Portal Provisioning Requirement

Default business provisioning method for a serial without an existing cloud mapping is the approved **DCAM QR-based Web Portal flow**.

DSetup completes imported serial_number verification
    ↓
DCAM attempts serial_lookup/{serial_number}
    ↓
If existing mapping is found:
        DCAM restores dcam_cloud_device_id and device information
Else:
        DCAM enters PROVISIONING_REQUIRED
        DCAM displays provisioning QR containing serial_number and approved device context
    ↓
Factory Worker opens Web Portal
    ↓
Factory Worker logs in
    ↓
Workspace scans QR displayed by DCAM
    ↓
Workspace displays serial_number as read-only
    ↓
Factory Worker enters/selects owner_name
    ↓
Factory Worker confirms manufacture_date
    ↓
Factory Worker submits provisioning inside Workspace
    ↓
Backend creates/restores:
        devices/{dcam_cloud_device_id}
        serial_lookup/{serial_number}
    ↓
Workspace displays Created / Restored / Error / Support Required
    ↓
DCAM receives/restores identity + device information + initial config metadata
    ↓
DCAM writes dcam.db and dcam_config.cson
Forbidden in normal Web Portal flow:

manual serial_number entry
direct serial barcode scan
editable serial_number
Factory Worker duplicate/rebind override
Web Portal PASS / FAIL / QUARANTINED / READY_TO_SHIP decision
BDMA provisioning is not required for normal factory provisioning.

## 6. Source of Truth

Topic

Source of Truth

Build/phase applicability

DCAM Release & Build Applicability Matrix

Factory provisioning, DSetup and SD Identity File recovery execution

DCAM Factory Provisioning & Device Production SOP + DCAM DSetup Factory Tool Design

Web Portal business flow and Workspace behavior

DCAM Device Provisioning Web Portal Design + DCAM Device Provisioning Web Portal App Design

API/data schema and Firestore contract

DCAM Web Portal & Device API Contract

`dcam_config.cson` scope and external contract

DCAM-BDMA Data Contract

Firebase/WebServer identity and provisioning architecture

06 - Cloud Services, Update & Configuration Architecture

Runtime/operational settings and remote config apply policy

09 - System Settings Requirements

SQLite persistence, identity cache and remote config cache

DCAM SQLite Database Design

Startup restore/provisioning runtime flow

DCAM Android Operation Design

Identity/provisioning security constraints

DCAM Security & Encryption Design

## 7. Practical Conclusion

Device configuration requirements are aligned with the current authoritative Factory SOP, identity ADR and approved QR-based Web Portal flow.

dcam_config.cson = device information and identity mirror only
dcam.db = operational/runtime settings + identity/cache mirror
serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Firebase/WebServer Cloud Identity / primary key
SD Identity File = recovery cache on external SD card, not Hardware Identity
serial_lookup/{serial_number} = create/restore path for dcam_cloud_device_id
owner_name = mutable/semi-static device information
manufacture_date = semi-static device information using YYYY-MM-DD
Web Portal provisioning = Factory Worker + Login/Workspace + QR displayed by DCAM
serial_number = read-only in Web Portal
Do not use ANDROID_ID, android_id_hash or device_lookup/{android_id_hash} in the current baseline
Build applicability is controlled by DCAM Release & Build Applicability Matrix
## 10. Build 0.1 CSON Exception

Build 0.1 cho phép đúng hai technical attribution fields trong dcam_config.cson tại nơi schema yêu cầu:

Requirement ID

Field

Value

Mutability

Meaning

CFG-B01-001

operator_id

B01OPR

Immutable trong Build 0.1

Technical traceability only

CFG-B01-002

operator_name

Build 0.1 Operator

Immutable trong Build 0.1

Technical traceability only

Exception này không cho phép credential, authentication token, authorization role, user profile hoặc runtime operator management trong CSON. Exact field placement/serialization cần Data Contract Technical Review.