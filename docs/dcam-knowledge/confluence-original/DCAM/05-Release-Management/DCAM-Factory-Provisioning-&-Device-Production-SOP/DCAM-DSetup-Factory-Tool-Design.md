# DCAM DSetup Factory Tool Design

**Page ID**: 50626624  
**Version**: 5  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50626624

---


# DCAM DSetup Factory Tool Design

Item

Information

Project

DCAM Android BodyCamera Application

Document Type

Factory Tool Design

Version

Approved 1.3

Status

Approved

Approval Scope

Approved DSetup factory-tool behavior; không phải Device POC pass, device qualification hoặc Production shipment approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Factory Lead / QA Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Page

DCAM Factory Provisioning & Device Production SOP

Target Audience

Android Developers, Factory Tool Developers, Factory Operator, Factory Admin, QA, Support

Last Updated

2026-07-14

Related Jira

None

Related Documents

DCAM Factory Provisioning & Device Production SOP, ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id, DCAM Android Device Owner & Kiosk Policy Design, DCAM Android Operation Design, DCAM Device POC & Hardware Validation Report

## 1. Purpose and Completion Boundary

DSetup is a PC factory helper using ADB and exactly one connected Android device.

DSetup completes only when:
actual_imported_serial_number == expected_serial_number
DSetup does not own official production record, factory acceptance, `PASS / FAIL / QUARANTINED / READY_TO_SHIP`, Web Portal provisioning, recording/storage/BDMA acceptance or full kiosk verification.

## 2. Main Flow

detect exactly one ADB device
→ read serial from valid SD Identity File or approved barcode/manual fallback
→ validate serial
→ install approved APK
→ set/verify Device Owner when required
→ inject serial through approved factory interface
→ launch DCAM
→ verify imported serial
→ complete
## 3. Device Owner Reference and Local Tool Impact

Device Owner deployment baseline được reference từ:

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision.

DCAM Factory Provisioning & Device Production SOP.

DCAM Android Device Owner & Kiosk Policy Design.

DSetup không định nghĩa lại baseline. Local tool impact:

Khi approved production profile yêu cầu Device Owner, DSetup thực thi approved factory command/wrapper trên eligible clean device.

APK install không đồng nghĩa Device Owner đã được thiết lập.

Existing non-DCAM Device Owner conflict phải dừng flow.

Exact DPC component, command wrapper/UI và OEM/firmware feasibility vẫn cần implementation/Device POC evidence.

## 4. Serial Resolution and Injection

Serial resolution priority:

valid SD Identity File
approved barcode scan
permission-controlled manual fallback
DSetup never generates serial and never substitutes Android ID, IMEI, MAC or Advertising ID.

The exact serial injection transport remains a genuine implementation/security decision. Allowed direction includes an approved factory-only ADB command/broadcast/intent interface with explicit import verification.

## 5. APK and Device Validation

Area

Requirement

ADB device count

Exactly one.

ADB state

Must be authorized/usable.

APK

Release-approved file only.

Checksum/signing metadata

Verify when included in release package.

Device Owner

Set/verify when required by profile.

Serial import

Must match expected serial.

## 6. Security Rules

No maintenance credential
No cloud/auth token
No APK signing private material
No Android system identifier as identity
No full SD/media upload
No bypass of runtime security or maintenance gate
Local DSetup diagnostics, if enabled, must use bounded retention and sanitized values.

## 7. UI Direction

Recommended sequential views:

Welcome/Checklist
Device Detection
Serial Resolution/Confirmation
APK Install
Device Owner Setup if applicable
Serial Injection
Launch DCAM
Verify Imported Serial
Completed or Error
The UI may show operation status, not production acceptance.

## 8. Resolved and Remaining Decisions

Item

Status

Single-device ADB model

Approved

Completion boundary

Approved: imported serial verification

Device Owner setup method

Approved factory baseline: ADB `dpm set-device-owner`

Exact Device Owner component/wrapper

TBD / Android + Device POC

Device Owner required in every run

Conditional by approved production profile

PC technology stack

TBD

Supported factory PC OS

TBD

ADB packaging/distribution

TBD

APK selection/config mechanism

TBD / Release

Serial format rule

TBD / Product + Factory

SD Identity File validation/signature

TBD / Android + Security

Exact SD read command/path mapping

TBD / Device POC

Serial injection mechanism

TBD / Android + Security

Imported-serial verification transport

TBD / Android + Tool Dev

Factory-only build/profile flag

TBD / Security + Android

Local log retention

TBD / Security + Factory

## 9. Practical Conclusion

DSetup là factory helper tool; Factory SOP sở hữu production procedure và acceptance decision.

Trang này chỉ sở hữu PC tool flow, single-device validation, approved Device Owner command integration, serial resolution/injection và imported-serial verification. Exact component/interface và target-device feasibility vẫn cần implementation/Device POC evidence.