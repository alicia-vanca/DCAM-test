# DCAM Factory Provisioning & Device Production SOP

**Page ID**: 49545629  
**Version**: 15  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49545629

---


# DCAM Factory Provisioning & Device Production SOP

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Factory SOP / Device Production Procedure

Version

Approved 1.3

Status

Approved

Approval Scope

Factory provisioning procedure và acceptance gates; device qualification/shipment acceptance chỉ hợp lệ khi required evidence và gates hoàn tất.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead / Security Reviewer / Factory Lead / Support Lead / BDMA Lead / Web Portal Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

05 - Release Management

Target Audience

Factory Operator, Factory Worker, Factory Admin, QA, Android Developers, Tech Lead, Support, Security Reviewer, Release Manager

Last Updated

2026-07-14

Related Jira

Không có

Related Documents

DCAM Project Home, DCAM Documentation Governance, DCAM Architecture Home, DCAM QA Test Strategy & Test Matrix, DCAM Device POC & Hardware Validation Report, DCAM DSetup Factory Tool Design, DCAM Device Provisioning Web Portal Design, DCAM Device Provisioning Web Portal App Design, DCAM Device Provisioning Web Portal Implementation Design, DCAM Web Portal & Device API Contract, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Self Update Design, DCAM Security & Encryption Design, DCAM Android Operation Design, DCAM-BDMA Data Contract, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id

## 1. Purpose

Tài liệu này định nghĩa quy trình factory/admin để chuẩn bị, update hoặc rework một BodyCamera cho DCAM pilot hoặc production deployment.

Identity baseline:

serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity / primary cloud device id
serial_lookup/{serial_number} = cloud create/restore lookup
SD Identity File = recovery cache trên external SD card
Production baseline không dùng:

ANDROID_ID
android_id_hash
device_lookup/{android_id_hash}
Advertising ID as device identity
DSetup boundary:

DSetup is a PC factory helper tool.
DSetup processes exactly one Android device at a time.
DSetup completes when DCAM confirms that it imported the expected serial_number.
DSetup does not execute factory acceptance.
DSetup does not create the official production record.
DSetup does not mark PASS, FAIL, QUARANTINED or READY_TO_SHIP.
Factory acceptance, production record và shipment decision thuộc Factory/QA process trong SOP này.

## 2. Current Deployment Baseline

No external EMM.
No Android Management API.
No Managed Google Play policy-driven update.
Primary update path = DCAM Self Update / approved APK update.
Device Owner setup uses the approved factory ADB/DSetup flow when required and supported.
DSetup resolves serial_number from SD Identity File or approved barcode/manual fallback.
DSetup injects serial_number through the approved factory serial injection mechanism defined by DCAM DSetup Factory Tool Design.
DSetup launches DCAM and verifies imported serial_number.
After DSetup completion, DCAM continues application-level factory steps.
DCAM displays a provisioning QR.
Factory Worker logs in to the Web Portal and scans the QR inside Workspace.
Backend creates/restores dcam_cloud_device_id through serial_lookup/{serial_number}.
Factory/QA performs kiosk, recording, storage, BDMA, update and acceptance checks.
Factory/QA records the official result and marks READY_TO_SHIP or QUARANTINED.
### 2.1 Factory Wi-Fi Decision

Theo quyết định hiện tại của dự án:

Factory Wi-Fi SSID/password is hardcoded in the approved DCAM APK.
DCAM configures factory Wi-Fi after serial_number has been imported and validated.
Factory Operator does not enter the Wi-Fi password manually.
Security constraints vẫn bắt buộc:

Wi-Fi password must not be written to Operational Logging.
Wi-Fi password must not be sent to Firebase Crashlytics.
Wi-Fi password must not be written to production records, support exports or evidence attachments.
Wi-Fi password must not be displayed in normal factory UI.
Only release-approved APK builds may contain the current factory Wi-Fi configuration.
Thay đổi hoặc loại bỏ quyết định hardcoded factory Wi-Fi phải được xử lý như một project/security decision riêng; SOP này giữ nguyên current decision.

## 3. Identity and Recovery Model

Identity Type

Field / Artifact

Meaning

Authority

Hardware Identity

`serial_number`

Định danh vật lý ổn định và primary recovery key.

Device label/factory source + approved factory resolution flow.

Cloud Identity

`dcam_cloud_device_id`

Primary device id trên Cloud Firestore/WebServer.

Backend create/restore bằng `serial_number`.

Recovery Cache

SD Identity File

Cache `serial_number` trên external SD card để hỗ trợ rework/factory reset.

Không phải authoritative identity khi conflict.

Recommended SD Identity File path:

```
<SD_CARD>/DCAM_FACTORY/device_identity.json
```

Serial resolution priority for DSetup:

1. Valid SD Identity File when app-private identity is unavailable after reset/rework.
2. Barcode scan from approved device/factory label.
3. Permission-controlled manual fallback when explicitly approved.
Rules:

DSetup does not generate serial_number.
Web Portal does not accept manual serial_number input.
Web Portal does not scan the serial barcode directly.
Web Portal receives serial_number only from the provisioning QR displayed by DCAM.
If SD Identity File conflicts with a trusted label/manifest/cloud mapping, factory flow must stop for resolution.
## 4. Scope

### 4.1 In Scope

Area

Description

Device intake

Kiểm tra model, firmware, physical condition, battery, USB/ADB và storage.

Clean/rework state

Đưa device về trạng thái factory/clean phù hợp trước Device Owner setup khi required.

DSetup operation

Detect exactly one ADB device, resolve serial, install approved APK, set/verify Device Owner nếu required, inject serial, launch DCAM và verify imported serial.

SD Identity File

Đọc recovery cache trong DSetup và đồng bộ từ DCAM app-private serial khi applicable.

Factory Wi-Fi

DCAM cấu hình factory Wi-Fi từ SSID/password hardcoded trong approved APK theo current project decision.

Web Portal provisioning

Factory Worker login, scan QR trong Workspace, nhập/chọn owner/manufacture date và submit create/restore request.

Cloud identity

Backend create/restore `dcam_cloud_device_id` bằng `serial_lookup/{serial_number}`.

Kiosk verification

Verify Device Owner state, Lock Task, User Restrictions, Home/Launcher behavior và controlled maintenance.

Recording/storage verification

Verify short recording, finalization, media visibility và storage behavior.

BDMA readiness

Verify ADB/media/user-sync boundary khi release baseline yêu cầu.

Update verification

Verify Self Update/approved update path và policy-safe behavior.

Factory acceptance

Factory/QA đánh giá checklist và quyết định `READY_TO_SHIP` hoặc `QUARANTINED`.

Production record

Factory/QA/backend ghi safe production metadata và acceptance result.

### 4.2 Out of Scope

Area

Reason / Owner

DSetup production acceptance decision

DSetup chỉ là helper tool và dừng sau imported serial verification.

DSetup official production record

Official record thuộc Factory/QA/backend process.

DSetup PASS/FAIL/QUARANTINED classification

Không thuộc DSetup Tool Design.

Exact serial injection command/action/component

Thuộc DCAM DSetup Factory Tool Design + Android/Security approval.

Exact Device Owner component/command wrapper

Thuộc Kiosk Policy Design, DSetup Design, release record và Device POC.

Web Portal manual serial entry

Không hỗ trợ.

Web Portal direct serial barcode scan

Không hỗ trợ.

Full BDMA UI testing

Thuộc BDMA QA scope.

Customer field operation

Thuộc Operations/Support.

External EMM / Managed Google Play

Not applicable for current baseline.

## 5. Source-of-truth Ownership

Topic

Source of Truth

Factory production procedure and acceptance

This SOP

DSetup behavior and completion boundary

DCAM DSetup Factory Tool Design

Web Portal business flow

DCAM Device Provisioning Web Portal Design

Web Portal Login/Workspace app behavior

DCAM Device Provisioning Web Portal App Design

Web Portal implementation modules

DCAM Device Provisioning Web Portal Implementation Design

API/path/schema/reason code

DCAM Web Portal & Device API Contract

Identity baseline

ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id

Device Owner / Lock Task / restrictions

DCAM Android Device Owner & Kiosk Policy Design

Android runtime and identity application

DCAM Android Operation Design

Security and sensitive-data constraints

DCAM Security & Encryption Design

QA release readiness

DCAM QA Test Strategy & Test Matrix

Hardware/firmware evidence

DCAM Device POC & Hardware Validation Report

## 6. Roles and Responsibilities

Role

Responsibility

Factory Operator

Chuẩn bị device/PC, kết nối đúng một device, chạy DSetup, scan barcode khi cần và thực hiện các bước vật lý theo SOP.

Factory Worker

Login vào Web Portal, scan QR displayed by DCAM trong `Workspace`, nhập/chọn business information và submit provisioning.

Factory Admin / Factory Lead

Quản lý approved artifacts, factory process, batch information và các trường hợp cần escalation.

QA

Chạy/kiểm tra acceptance checklist, xác nhận evidence và quyết định acceptance theo release process.

Android Developer / Tech Lead

Hỗ trợ DSetup, Device Owner, serial import, SD Identity File, factory Wi-Fi, kiosk, recording/storage và runtime issue.

Web/Backend Lead

Hỗ trợ authentication, QR/API contract, create/restore, duplicate/conflict và audit.

Security Reviewer

Review credential handling, hardcoded factory Wi-Fi constraints, factory-only interfaces, logging và sensitive data.

Release Manager

Approve APK, checksum/signing metadata, DSetup version và factory release package.

Support Lead

Xử lý support-required, rework và identity conflict.

## 7. Required Inputs

Input

Required

Notes

Approved BodyCamera model/firmware

Có

Phải match Device POC/release baseline.

Approved DCAM APK

Có

Chứa current approved factory Wi-Fi configuration và required factory integration.

APK version/checksum/signing metadata

Có

Dùng để verify release artifact.

Approved DSetup build

Có

Version phải được ghi nhận trong batch/process record nếu required.

Factory PC + ADB

Có

Chạy DSetup và approved factory commands.

Barcode scanner

Conditional

Fallback khi SD Identity File không dùng được.

SD card/external storage

Conditional

Required khi batch bật recovery cache.

Approved Device Owner component/config

Conditional

Required nếu Device Owner setup thuộc production profile.

Approved serial injection mechanism

Có

Exact mechanism thuộc DSetup Design/release record.

Factory Wi-Fi/network

Có khi provisioning online

SSID/password nằm trong approved APK theo current decision.

Factory Worker account

Có

Dùng cho Web Portal login.

Owner information

Có nếu business flow yêu cầu

Nguồn/validation theo approved policy.

Manufacture date

Có

Dùng `YYYY-MM-DD`.

## 8. Device Production States

State

Meaning

`RAW_DEVICE`

Device chưa được chuẩn bị.

`INSPECTED`

Model/firmware/physical checks hoàn tất.

`FACTORY_RESET_DONE`

Device ở clean/factory state nếu required.

`DSETUP_RUNNING`

DSetup đang xử lý device hiện tại.

`SERIAL_RESOLVED`

Serial được resolve từ approved source.

`APK_INSTALLED`

Approved APK đã được install/update.

`DEVICE_OWNER_VERIFIED`

Device Owner state được verify nếu required.

`SERIAL_IMPORTED_VERIFIED`

DCAM xác nhận imported serial khớp expected serial; đây là DSetup completion point.

`FACTORY_WIFI_CONFIGURED`

DCAM đã apply factory Wi-Fi và connectivity được kiểm tra.

`PROVISIONING_QR_DISPLAYED`

DCAM đang hiển thị provisioning QR.

`BUSINESS_PROVISIONED`

Backend create/restore cloud identity thành công.

`KIOSK_POLICY_VERIFIED`

Kiosk policy checks đã pass theo production profile.

`FACTORY_ACCEPTANCE_IN_PROGRESS`

Factory/QA đang thực hiện acceptance checks.

`READY_TO_SHIP`

Factory/QA/release process đã approve shipment.

`QUARANTINED`

Required production check fail hoặc conflict chưa được resolve.

`READY_TO_SHIP` và `QUARANTINED` không phải DSetup state/decision.

## 9. End-to-end Factory Flow

Receive and inspect BodyCamera
    ↓
Factory reset / clean state if required
    ↓
Connect exactly one device to Factory PC
    ↓
Run DSetup
    ↓
DSetup checks exactly one ADB device
    ↓
DSetup resolves serial_number from SD Identity File or approved fallback
    ↓
DSetup installs approved DCAM APK
    ↓
DSetup sets/verifies Device Owner if required
    ↓
DSetup injects serial_number through approved factory mechanism
    ↓
DSetup launches DCAM
    ↓
DSetup verifies DCAM imported expected serial_number
    ↓
DSetup scope ends
    ↓
DCAM syncs SD Identity File if applicable
    ↓
DCAM configures factory Wi-Fi using credentials hardcoded in approved APK
    ↓
Verify Wi-Fi / Firebase connectivity
    ↓
DCAM displays provisioning QR
    ↓
Factory Worker logs in to Web Portal
    ↓
Factory Worker scans QR inside Workspace
    ↓
Factory Worker reviews read-only device information
    ↓
Factory Worker enters/selects owner_name and manufacture_date
    ↓
Factory Worker submits provisioning from Workspace
    ↓
Backend creates/restores dcam_cloud_device_id by serial_number
    ↓
Verify cloud/local identity application
    ↓
Verify kiosk policy and controlled maintenance
    ↓
Verify recording, storage, BDMA if required and update capability
    ↓
Factory/QA completes production record
    ↓
Factory/QA marks READY_TO_SHIP or QUARANTINED
## 10. Detailed SOP Steps

### 10.1 Receive and Inspect Device

Step

Expected Result

Check model and firmware.

Match approved release/POC baseline.

Check physical condition, screen, camera lens, buttons, USB and charging.

Không có defect rõ ràng.

Check storage/SD card when required.

Storage available cho current batch profile.

Failure thuộc Factory/QA process và có thể dẫn đến `QUARANTINED`.

### 10.2 Clean Device State

Step

Expected Result

Factory reset/clean device when provisioning profile requires it.

Device ở state phù hợp để set Device Owner.

Confirm no conflicting Device Owner/account/management state.

Không có conflict.

Confirm approved ADB factory access.

DSetup có thể giao tiếp với device.

DSetup không bypass hoặc remove third-party Device Owner.

### 10.3 Run DSetup

DSetup responsibilities:

Detect exactly one ADB device
Check basic connection state
Read serial_number from SD Identity File if available
Use approved barcode/manual fallback if needed
Install approved DCAM APK
Set or verify Device Owner if required
Inject serial_number through approved factory mechanism
Launch DCAM
Verify DCAM imported expected serial_number
DSetup completion rule:

```
DSetup completes when imported_serial_number == expected_serial_number.
```

DSetup UI có thể hiển thị transient operation status/error để operator biết bước hiện tại, nhưng không được tạo official production acceptance result.

### 10.4 Resolve Serial Number

Case

Action

Valid SD Identity File

DSetup dùng recovered `serial_number` sau validation.

Missing/invalid SD Identity File

Operator scan approved barcode/device label.

Barcode unavailable and manual fallback approved

Permission-controlled manual input theo factory procedure.

Conflict with label/manifest/cloud

Stop DSetup/factory flow và escalate.

### 10.5 Install APK and Verify Device Owner

Step

Expected Result

Verify approved APK version/checksum/signing metadata.

Match release package.

Install/update approved APK.

Package install thành công.

Run approved Device Owner setup when required and state permits.

Android accepts approved setup.

Verify Device Owner state.

DCAM/approved DPC is Device Owner when required.

Exact command wrapper và component name không được định nghĩa trong SOP; chúng thuộc DSetup Design, Kiosk Policy Design, Device POC và release record.

### 10.6 Inject and Verify Serial Number

Step

Expected Result

DSetup uses resolved expected serial.

Serial source đã được validate.

DSetup calls approved factory serial injection mechanism.

DCAM nhận request trong approved factory context.

DCAM validates and imports serial.

App-private identity được cập nhật an toàn.

DSetup reads/receives verification result.

Actual serial khớp expected serial.

SOP không chốt ADB broadcast/action/component cụ thể. Exact mechanism thuộc `DCAM DSetup Factory Tool Design`.

### 10.7 Sync SD Identity File

Sau DSetup completion, DCAM có thể tạo/cập nhật SD Identity File từ app-private `serial_number`.

App-private serial_number wins during normal operation.
SD Identity File is recovery cache only.
If the file conflicts with app-private serial, DCAM must not replace app-private identity from the file.
### 10.8 Configure Factory Wi-Fi

Step

Expected Result

DCAM loads hardcoded factory Wi-Fi configuration from approved APK.

Current approved SSID/password available to app.

DCAM configures/connects to factory Wi-Fi.

Device connected.

Verify network/Firebase connectivity.

Web provisioning backend reachable.

Do not display, log or record the Wi-Fi password.

### 10.9 Web Portal Business Provisioning

Approved Web Portal flow:

DCAM displays provisioning QR
Factory Worker opens Web Portal
Factory Worker logs in
Workspace opens
Factory Worker scans QR displayed by DCAM
Workspace displays serial_number as read-only
Factory Worker enters/selects owner_name
Factory Worker confirms manufacture_date in Workspace
Factory Worker submits provisioning
Backend creates/restores dcam_cloud_device_id
Workspace displays result
Rules:

Only Factory Worker is user-facing.
Web Portal has only Login and Workspace.
Web Portal does not accept manual serial_number.
Web Portal does not scan the serial barcode directly.
Web Portal does not mark production PASS/FAIL/QUARANTINED/READY_TO_SHIP.
### 10.10 Apply and Verify Kiosk Policy

Factory/QA verifies:

Device Owner state when required
Lock Task allowlist and active behavior
Home/Recents/Back restrictions
Approved User Restrictions
Policy recovery after reboot/process restart
Maintenance Password Gate
No unrestricted Android escape
### 10.11 Recording and Storage Verification

Factory/QA performs a short production smoke test:

Login as approved test operator
Start short recording
Stop recording
Verify safe finalization
Verify finalized media visibility
Verify temp media is not exposed as final
Verify storage state
### 10.12 BDMA and Update Verification

Run only according to release profile:

BDMA ADB/media discovery and import readiness
User sync readiness if required
Self Update capability and safe defer behavior
Post-update kiosk/policy restore when update test is executed
Optional Play Store fallback only when separately approved
### 10.13 Factory Acceptance

Factory/QA reviews all required evidence and decides:

READY_TO_SHIP
or
QUARANTINED
DSetup completion is one input to acceptance; it is not the acceptance decision.

## 11. Factory Acceptance Checklist

Check

Required

Approved model/firmware

Có

Exactly one ADB device during DSetup

Có

Approved APK version/checksum/signing metadata

Có

Device Owner verified when required

Conditional

Serial resolved from approved source

Có

DCAM imported expected serial

Có

SD Identity File sync/recovery cache

Conditional

Approved APK contains current hardcoded factory Wi-Fi configuration

Có

Factory Wi-Fi/Firebase connectivity

Có khi online provisioning required

Factory Worker Web Portal login

Có

QR scanned from DCAM in Workspace

Có

Web Portal serial is read-only

Có

Backend create/restore by `serial_number`

Có

`dcam_cloud_device_id` available and mapping correct

Có

Lock Task/User Restrictions/maintenance protections

Có theo production profile

Recording/finalization/storage smoke test

Có

BDMA readiness

Conditional

Self Update capability

Có/Conditional theo release profile

Official production record completed by Factory/QA/backend process

Có

Final acceptance decision recorded

Có

## 12. Failure Handling

Failure

Required Action

Zero or multiple ADB devices

Stop DSetup until exactly one device is connected.

Unauthorized/offline ADB device

Stop and correct factory connection state.

Serial missing/invalid/conflicting

Stop and resolve through approved factory/support process.

APK identity/checksum/signature mismatch

Stop and quarantine through Factory/QA process.

Device Owner conflict or setup failure when required

Stop; require rework/factory reset/support decision.

Serial injection/import verification failure

DSetup stops; Factory/QA decides retry, rework or quarantine.

Factory Wi-Fi configuration/connectivity failure

Retry or quarantine according to approved factory process.

Invalid QR or missing QR serial

Rescan refreshed DCAM QR; do not enter serial manually in Web Portal.

Web Portal login/auth failure

Stop provisioning until valid Factory Worker session exists.

Duplicate/rebind conflict

Show Support Required; Factory Worker cannot override.

Missing cloud device id after success path

Stop acceptance and escalate.

Kiosk escape or Maintenance Gate failure

Quarantine.

Recording/finalization/storage failure

Quarantine.

Required BDMA/update check failure

Quarantine or approved limitation decision.

## 13. Production Record

Official production record được ghi bởi Factory/QA/backend process, không phải DSetup.

Recommended safe fields:

Field

Description

`factory_batch_id`

Batch identifier.

`factory_run_id`

Factory process run identifier.

`dsetup_version`

DSetup build used.

`dsetup_completion_result`

`SERIAL_IMPORTED_VERIFIED` hoặc safe operation error classification; không dùng làm final production PASS.

`serial_source`

`SD_IDENTITY_FILE`, `BARCODE_SCAN` hoặc approved fallback.

`serial_number`

Verified Hardware Identity.

`dcam_cloud_device_id`

Cloud Identity sau create/restore.

`owner_name`

Business owner/customer information nếu required.

`manufacture_date`

`YYYY-MM-DD`.

`bodycamera_model` / `firmware_version`

Hardware baseline.

`apk_version_name` / `apk_version_code`

Installed app version.

`apk_checksum` / signing fingerprint

Release verification metadata.

`device_owner_verify_result`

Safe verification result nếu required.

`factory_wifi_result`

Connectivity result; không lưu password.

`web_provisioning_result`

Created/Restored/Failed/Support Required.

`serial_lookup_result`

Mapping verification.

`kiosk_policy_result`

Factory/QA result.

`recording_storage_result`

Factory/QA smoke-test result.

`bdma_result`

Conditional result.

`self_update_result`

Conditional/capability result.

`factory_operator` / `factory_worker` / `qa_approver`

Safe actor identifiers.

`factory_acceptance_result`

`READY_TO_SHIP` hoặc `QUARANTINED`.

`ready_to_ship_at`

Timestamp nếu approved.

Forbidden fields:

factory Wi-Fi password
maintenance password
Google account password/token
authentication token
APK signing private key
cloud/provisioning secret
raw Android system identifier
unrelated SD card/media contents
## 14. Shipment Handover

Step

Expected Result

Review official production record.

Required metadata/evidence đầy đủ.

Confirm DSetup completion input.

DCAM imported expected serial.

Confirm cloud mapping.

`serial_number` map đúng `dcam_cloud_device_id`.

Confirm kiosk/runtime state.

Device locked down và ready.

Confirm required smoke tests.

Recording/storage/BDMA/update results đạt release profile.

Record final decision.

Factory/QA marks `READY_TO_SHIP` hoặc `QUARANTINED`.

Prepare shipment.

Device/accessories/label/batch reference đúng.

## 15. Open Questions / TBD

Item

Owner / Source

Exact DSetup technology stack and packaging

DSetup Design + Release Manager

Exact approved serial injection mechanism

DSetup Design + Android + Security

Exact Device Owner component/command wrapper

Kiosk Policy Design + DSetup Design + Device POC

Exact SD Identity File signature/checksum policy

Android + Security + Factory

Exact serial format and conflict resolution

Product + Factory + Backend

Factory Worker account model

Factory + Security + Web/Backend

QR signature/nonce/expiration/replay policy

API Contract + Security

Owner source and validation

Product + Factory

Request idempotency/timeout reconciliation

API Contract + Backend

Production record storage and retention

Factory + QA + Security + Backend

Credential rotation process for hardcoded factory Wi-Fi

Release Manager + Security + Android

## 16. Practical Conclusion

Factory SOP owns the complete production procedure and final acceptance decision.
DSetup is a single-device PC factory helper tool.
DSetup ends when DCAM confirms imported_serial_number == expected serial_number.
DSetup does not create the official production record.
DSetup does not mark PASS, FAIL, QUARANTINED or READY_TO_SHIP.
Exact serial injection mechanism belongs to DCAM DSetup Factory Tool Design.
The current project decision keeps factory Wi-Fi SSID/password hardcoded in the approved DCAM APK.
The Wi-Fi password must never appear in logs, Crashlytics, production records or evidence.
DCAM displays the provisioning QR after imported serial is available.
Factory Worker uses the two-screen Web Portal: Login and Workspace.
Factory Worker scans the QR displayed by DCAM; serial_number is read-only.
Backend creates/restores dcam_cloud_device_id through serial_lookup/{serial_number}.
Factory/QA performs kiosk, recording, storage, BDMA and update checks according to release profile.
Factory/QA records the official result and marks READY_TO_SHIP or QUARANTINED.