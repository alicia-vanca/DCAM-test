# DCAM MVP Scope

**Page ID**: 42532866  
**Version**: 25  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/42532866

---


# DCAM MVP Scope

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

MVP Scope

Version

Approved 2.7

Status

Approved

Approval Scope

DEC-01–DEC-07 và Working Recording Slice cho DCAM MVP Internal Build 0.1

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

01  Product Management

Target Audience

PM/BA, Product Owner, Tech Lead, Developers, QA, Stakeholders

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Product Vision, DCAM Project Charter, DCAM Roadmap, DCAM 9-Month Development Plan, DCAM Release & Build Applicability Matrix, DCAM-BDMA Data Contract, DCAM Requirements Home, DCAM Architecture Home, DCAM Documentation Governance, DCAM Architecture Delivery Profile, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Executive Summary

MVP tập trung xây dựng ứng dụng Android trên BodyCamera có khả năng ghi video, chụp ảnh, lưu media cục bộ và expose dữ liệu để **BDMA Desktop** ingest theo **DCAM-BDMA Data Contract**.

Core MVP flow:

```
Record / Capture → Save / Finalize Media → Expose Contract Data → BDMA Ingest
```

Implementation phải tuân theo:

DCAM Architecture Delivery Profile
    → giới hạn MVP architecture

DCAM Release & Build Applicability Matrix
    → xác định feature/requirement/QA nào bắt buộc cho từng build
Current active profile:

Build = DCAM MVP Internal Build 0.1
Gate = Working Recording Slice
## 2. MVP Objectives

Objective

Description

Validate Video Recording

DCAM ghi video ổn định trên selected BodyCamera.

Validate Image Capture

DCAM chụp ảnh và lưu file ổn định.

Validate Local Storage

Temp/final media và folder structure hoạt động theo Data Contract.

Validate Contract Data

DCAM tạo minimal media/CSON/DB/log artifacts được Build 0.1 support.

Validate BDMA Compatibility

BDMA detect/import được sample media qua ADB.

Validate Operational Stability

Core flow không crash khi GPS unavailable hoặc storage gần đầy.

Validate Working Recording Slice

APK chạy được end-to-end trước khi mở rộng platform architecture.

## 3. MVP Success Criteria

KPI

Target

Video Recording Success Rate

= 99%

Image Capture Success Rate

= 99%

BDMA Import Success Rate

100% for MVP sample flow

Data Contract Compliance

100% for MVP-supported fields/files

File Corruption

0 Critical Case

Application Crash

Không có Critical Crash trong core recording/capture flow.

Working Recording Slice

Pass before optional platform expansion.

## 4. Build 0.1 Required Scope

Area

Build 0.1 Decision

Recording / Capture

Required.

Storage / Finalization

Required.

Media naming/folder contract

Required for MVP-supported files.

Minimal SQLite state

Required.

Minimal `dcam_config.cson`

Required khi Data Contract/build output cần.

Sanitized `Logs/logs.txt`

Required.

Local Operational Logging

Required.

BDMA sample detect/import

Required.

Basic Device Status

Battery, storage and GPS availability where supported.

Concurrency critical path

Required from day one.

Basic Lock Task POC

Conditional only when needed for device validation.

Working Recording Slice:

Open app
    ↓
Check camera/storage permission
    ↓
Record 30s video
    ↓
Stop and finalize
    ↓
Capture sample image
    ↓
Write minimal DB/CSON/log output
    ↓
BDMA detects/imports sample media
## 5. Deferred from Build 0.1

The following are valid target capabilities but are not Build `0.1` release blockers:

Deferred Area

Target Direction

Cloud identity / Web Portal provisioning

Build 0.2

Full operator authentication / user management

Build 0.2

Full Device Owner / Kiosk Policy stack

Build 0.2+ after Device POC

Remote Config fetch/cache/apply

Build 0.2

Self Update

Build 0.2

Play Store fallback

Conditional Build 0.2+

Full In-App Console

Build 0.2+

Full Feature Eligibility engine

Build 0.2+

Full State Machine Coordinator

Build 0.2+

Sensor Monitoring advanced

Build 0.3+

Realtime AI

Future / conditional

Live Streaming / PTT / Full GPS Route

Build 0.3

```
Deferred features must not block Build 0.1 PR approval or Working Recording Slice acceptance.
```

## 6. Acceptance Criteria

ID

Criteria

AC-001

DCAM build/run được trên selected BodyCamera/test device.

AC-002

Người dùng ghi được video và chụp được ảnh.

AC-003

Media được finalize đúng MVP-supported Data Contract structure.

AC-004

Minimal DB/CSON/log artifacts được tạo theo Build 0.1 scope.

AC-005

`Logs/logs.txt` tồn tại, sanitized và BDMA read-only.

AC-006

BDMA detect/import được sample media qua ADB.

AC-007

App không crash trong happy path và các expected fallback đã define.

AC-008

Critical camera/file/DB work không block MainThread.

AC-009

Working Recording Slice pass.

AC-010

Deferred feature absence không làm fail Build 0.1.

## 7. Exit Criteria

Condition

Required

Recording / Capture Pass

Yes

Storage / Finalization Pass

Yes

Working Recording Slice Pass

Yes

MVP Data Contract Pass

Yes

BDMA Sample Import Pass

Yes

Applicable Build 0.1 QA Groups Pass

Yes

No Critical Bug

Yes

PM & QA Approval

Yes

QA applicability is defined by **DCAM Release & Build Applicability Matrix** and verified by **DCAM QA Test Strategy & Test Matrix**.

## 8. Related Documents

Document

Purpose

DCAM Product Vision

Product direction.

DCAM Roadmap

Phase and milestone direction.

DCAM 9-Month Development Plan

Execution and sprint plan.

DCAM Release & Build Applicability Matrix

Active build scope and release applicability.

DCAM Requirements Home

Functional Requirements 01–10 navigation.

DCAM Architecture Home

Architecture, Technical Design and ADR navigation.

DCAM Architecture Delivery Profile

MVP architecture guardrails.

DCAM-BDMA Data Contract

Android–BDMA data contract.

DCAM QA Test Strategy & Test Matrix

Test coverage and release validation.

DCAM Android Training & Architecture Onboarding

Android onboarding.

## 9. Practical Conclusion

MVP product direction remains Record/Capture → Storage → Contract Data → BDMA.
Current active implementation profile is Build 0.1.
Working Recording Slice is the current gate.
Build applicability is owned by DCAM Release & Build Applicability Matrix.
## 10. Build 0.1 Controlled Baseline

Tài liệu [Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice](/wiki/spaces/DVID/pages/51642452/Decision+Brief+DCAM+MVP+Internal+Build+0.1+Working+Recording+Slice) sở hữu DEC-01–DEC-07. Baseline dưới đây chỉ áp dụng cho Build 0.1 và không phải Production approval.

Decision

Build 0.1 Baseline

Reference Device

NCC-036V / Android 12 / API 31 / 877AOOAKN1_RK2_V009; qualification Pending Device POC.

Camera Integration

Android platform Camera API; vendor SDK Not Applicable; Camera1/Camera2 chưa được suy diễn.

Active Storage

Internal storage only; không External fallback hoặc Auto mode.

MP4 Checksum

Mọi MP4 phải có MD5; chỉ BDMA_READY sau MD5 success; missing/mismatch/failure chặn evidence và release gate.

Operator Policy

Không login UI; operator_id = B01OPR; operator_name = Build 0.1 Operator; không phải authenticated identity.

Media Filename Tokens

DEVICE_TOKEN = validated serial_number snapshot, 6–10 ký tự; OPERATOR_TOKEN = B01OPR, đúng 6 ký tự; chỉ [A-Z0-9], không underscore hoặc silent truncation.

Basic Device Status

Battery level, Internal free storage và GPS Available/Unavailable/Unsupported.

Device Coverage

WRS pass trên ít nhất một physical reference device có định danh rõ ràng; không đại diện fleet/multi-model/Production readiness.

### Build 0.1 Acceptance Criteria

ID

Criteria

WRS-AC-001

APK chạy trên exact reference configuration; evidence ghi physical device identifier, OS/API và firmware.

WRS-AC-002

Failed Internal storage pre-check không bắt đầu recording; runtime failure safe-stop và finalize MP4 nếu còn khả năng.

WRS-AC-003

MP4 được finalize trước, tạo MD5 bất đồng bộ và chỉ trở thành BDMA_READY sau MD5 success.

WRS-AC-004

Static operator values xuất hiện nhất quán tại nơi SQLite/CSON/log schema yêu cầu và không thể sửa từ UI/runtime configuration.

WRS-AC-005

Battery level, Internal free storage và GPS state được báo đúng; GPS Unavailable/Unsupported không làm fail WRS.

WRS-AC-006

Kết quả chỉ áp dụng cho NCC-036V / Android 12 / API 31 / 877AOOAKN1_RK2_V009; thay configuration cần impact review và regression.

WRS-AC-007

Media filename tuân thủ DEC-07 và DCAM-BDMA Data Contract; Device POC/QA evidence xác nhận token length/charset, không truncation và BDMA parsing/import boundary.