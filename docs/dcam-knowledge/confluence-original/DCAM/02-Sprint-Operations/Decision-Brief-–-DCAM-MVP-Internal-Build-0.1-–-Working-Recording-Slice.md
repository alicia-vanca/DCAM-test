# Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

**Page ID**: 51642452  
**Version**: 4  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51642452

---


# Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Decision Brief / Build Baseline

Version

1.2

Status

Approved for Build DCAM MVP Internal Build 0.1

Approval Scope

DEC-01–DEC-07 cho Working Recording Slice; không phải Production approval hoặc Device POC pass

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead / BDMA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

02  Sprint Operations

Target Audience

PM/BA, Product Owner, Tech Lead, Android Developers, QA, BDMA Team

Last Updated

2026-07-21

Related Jira

[DCAM-1](https://ducviet.atlassian.net/browse/DCAM-1) — Build 0.1 Epic; detailed work mapping thuộc Traceability Matrix

Related Documents

DCAM MVP Scope, DCAM Release & Build Applicability Matrix, DCAM Architecture Delivery Profile, DCAM Device POC & Hardware Validation Report, DCAM-BDMA Data Contract, DCAM QA Test Strategy & Test Matrix, DCAM Requirement–Design–Test Traceability Matrix, DCAM Document Status Registry

Dependencies / Blockers

Device POC evidence; Camera1/Camera2 Technical Review; physical Internal storage/ADB path; SQLite/CSON schema placement; implementation PR/build/test evidence.

## 1. Objective

Đồng bộ các quyết định PM-approved cho **DCAM MVP Internal Build 0.1 – Working Recording Slice** thành baseline đủ rõ để chuyển sang **03 — Jira Planning** mà không mở rộng product scope, requirement hoặc approved architecture ngoài bảy quyết định dưới đây.

## 2. Authority and Applicability

Tài liệu này sở hữu quyết định Build 0.1 đối với DEC-01–DEC-07.

Nếu nội dung generic hoặc target-state ở tài liệu downstream mâu thuẫn với quyết định dưới đây, Build 0.1 phải tuân theo Decision Brief này. Behavior của build khác không tự động bị thay đổi.

PM approval of the reference configuration ≠ Device POC pass
Approved for Build DCAM MVP Internal Build 0.1 ≠ Production Approved
## 3. Approved Decisions

### DEC-01 — Reference Device

Field

Approved Baseline

Reference Model

NCC-036V

Android Version

Android 12

API Level

31

Firmware / Build

877AOOAKN1_RK2_V009

Camera Integration

Android platform Camera API

Vendor SDK

Not Applicable for Build 0.1

Applicability

Device POC và Working Recording Slice

Qualification Status

Pending Device POC

Không được suy diễn Camera1, Camera2 hoặc capability chưa được Device POC xác nhận.

### DEC-02 — Active Storage Policy

Rule

Approved Baseline

Active Storage

Internal storage

External Fallback

Not Allowed

Pre-check Failure

Không bắt đầu recording

Runtime Storage Failure

Safe-stop và finalize MP4 nếu còn khả năng

External / Auto

Not Applicable for Build 0.1 acceptance scope

Logical-to-physical path phải chờ Device POC và Storage Design review; quyết định này không tự chọn physical path.

### DEC-03 — MP4 Checksum

Rule

Approved Baseline

Algorithm

MD5

Scope

Mọi MP4

Purpose

Integrity check; không phải encryption, authentication hoặc security signature

Sequence

Finalize MP4 trước, sau đó tạo MD5 bất đồng bộ

BDMA Readiness

Chỉ chuyển sang BDMA_READY sau khi MD5 thành công

MD5 Failure

Giữ MP4, ghi log, đặt Checksum Pending/Failed và không cho BDMA import

Missing / Mismatch

Chặn Build 0.1 evidence và release gate

Image MD5

Không thay đổi behavior hiện có

Baseline cũ cho phép missing MD5 → Unverified import hoặc cho phép BDMA_READY không chờ checksum không áp dụng cho Build 0.1.

Exact enum, schema và checksum performance threshold phải qua Technical Review hoặc Device POC; không được suy diễn từ decision này.

### DEC-04 — Operator Policy

Field

Approved Baseline

Login UI

Not Applicable for Build 0.1

operator_id

B01OPR

operator_name

Build 0.1 Operator

Mutability

Không thể sửa từ UI hoặc runtime configuration

Persistence

Ghi nhất quán vào SQLite, CSON và log tại nơi schema yêu cầu

Identity Meaning

Technical traceability only; không phải authenticated identity

Authentication / Authorization / Roles / Operator Management

Out of Scope for Build 0.1

Target-state authentication requirements vẫn giữ nguyên cho build sau; Build 0.1 sử dụng build-specific exception này.

### DEC-05 — Basic Device Status

Status

Approved Baseline

Battery

Battery level; chưa có low-battery policy

Storage

Internal free storage theo DEC-02

GPS

Available, Unavailable hoặc Unsupported

GPS Exclusions

Không coordinates, route hoặc continuous tracking

WRS Result

GPS Unavailable/Unsupported không làm fail nếu trạng thái được báo chính xác

Delivery

Fold vào Device POC & Hardware Validation và Working Recording Slice Integration; không tạo Jira issue riêng

### DEC-06 — WRS Device Coverage

WRS chỉ phải pass trên một reference configuration:

```
NCC-036V / Android 12 / API 31 / 877AOOAKN1_RK2_V009
```

Evidence phải lấy từ ít nhất một physical device có định danh rõ ràng. Model hoặc firmware khác không mặc nhiên được coi là pass. Thay reference configuration phải có impact review và regression phù hợp.

Kết quả không đại diện cho multi-model, multi-firmware, production hoặc fleet readiness.

### DEC-07 — Media Filename Token Mapping

Build 0.1 giữ format tên file media sau:

```
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS.<ext>
```

Token

Source

Approved Rule

XXXXXX / DEVICE_TOKEN

Snapshot của validated `serial_number`

6–10 ký tự, chỉ `[A-Z0-9]`; không có underscore; không silent truncation.

ZZZZZZ / OPERATOR_TOKEN

Snapshot của `operator_id`

Đúng 6 ký tự, chỉ `[A-Z0-9]`; Build 0.1 dùng `B01OPR`.

operator_name

Build 0.1 Operator

Giữ nguyên; không tham gia filename.

Important / Encrypted suffix

`_IMP`, `_enc`, `_IMP_enc`

Giữ nguyên contract hiện có.

Giá trị `serial_number` thực tế của reference device phải được Device POC xác nhận trước khi qualification. Không được cắt ngắn, thêm underscore hoặc tự tạo token thay thế để làm cho giá trị không hợp lệ trở thành hợp lệ.

Timezone của `YYYYMMDD_HHMMSS` và collision handling khi tạo nhiều artifact trong cùng một giây vẫn là Technical Review items; DEC-07 không tự chốt hai behavior này.

## 4. Technical Review Items

Item

Owner / Closure Gate

Camera1 hoặc Camera2 và capability thực tế

Android Lead; Device POC evidence

Physical Internal storage path và ADB mapping

Android Lead / BDMA Lead; Device POC + Storage Design review

Checksum enum và database migration

Tech Lead / Android Lead; SQLite/Data Contract Technical Review

SQLite table/column, ownership và transaction boundary

Tech Lead; SQLite Design review

CSON placement của operator fields

Android Lead / BDMA Lead; Data Contract review

Timestamp timezone cho media filename

Tech Lead / Android Lead / BDMA Lead; Data Contract Technical Review

Same-second filename collision handling

Tech Lead / Android Lead; Storage/Recording Technical Review

Reference-device serial_number charset và length

Android Lead / QA Lead; Device POC evidence

MD5 execution time và resource threshold

QA / Android Lead; Device POC evidence

Authoritative GitHub repository mapping

PM-approved identity record below; implementation PR/build/test evidence remains open.

Các mục này không được coi là đã phê duyệt bởi Decision Brief.

### 4.1 Implementation Repository Mapping

Item

Controlled Record

Repository Identity

[DucVietTech/dcam](https://github.com/DucVietTech/dcam)

Default Branch

`main`

Approval

PM Approved

Scope

Authoritative implementation repository identity/linkage for DCAM.

Evidence Boundary

Mapping không xác nhận source behavior, commit, PR, CI, build, test, Device POC hoặc release acceptance. Những evidence đó phải được linked vào Jira và Traceability row tương ứng.

## 5. Release Guardrails

Pending Device POC được giữ cho NCC-036V cho đến khi có test evidence.

Missing, mismatch hoặc failed MP4 MD5 chặn Build 0.1 evidence/release gate.

External storage và Auto mode không thuộc Build 0.1 acceptance scope.

GPS Unavailable/Unsupported không làm fail WRS nếu reporting chính xác.

GitHub implementation không được dùng để thay đổi product scope hoặc requirement. ${gh} chỉ xác nhận repository identity; implementation evidence phải được đánh giá riêng theo Jira/Traceability.

Không được diễn giải Build 0.1 result thành Production, fleet hoặc multi-device readiness.

## 6. Approval Record

Item

Result

DEC-01–DEC-07

PM Approved

Controlled Documentation Changeset

PM Approved

Publication Order

PM Approved

Device Qualification

Pending Device POC

Production Approval

Not Granted

Technical Review Decisions

Not Granted

GitHub Repository Mapping

Approved — [DucVietTech/dcam](https://github.com/DucVietTech/dcam) (repository identity only; not implementation/release evidence)

Effective Date

2026-07-13

Repository Mapping Effective Date

2026-07-18

## 7. Publication Order

Decision Brief.

MVP Scope và Release & Build Applicability Matrix.

Requirements và DCAM-BDMA Data Contract.

Architecture Delivery Profile và downstream designs.

Device POC và QA Matrix.

Requirement–Design–Test Traceability Matrix.

Document Status Registry.

## 8. Practical Conclusion

Bảy decisions đã đủ để chuyển thành Build 0.1 Acceptance Criteria sau khi documentation synchronization hoàn tất. Device qualification, Technical Review items và implementation PR/build/test evidence vẫn là dependency mở; repository identity đã được ghi nhận riêng.