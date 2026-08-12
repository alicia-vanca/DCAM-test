# 08 - DCAM-BDMA Integration Boundary

**Page ID**: 47153235  
**Version**: 15  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47153235

---


# 08 - DCAM-BDMA Integration Boundary

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Integration Boundary

Version

Approved 1.12

Status

Approved

Approval Scope

DCAM–BDMA boundary với approved Build 0.1 import eligibility overlay và legacy MD5 no-profile-mapping guardrail; cryptographic algorithm/key/decryption policy thuộc approved Security Profile.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / BDMA Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Team, Support

Last Updated

2026-07-18

Related Jira

[DCAM-8](https://ducviet.atlassian.net/browse/DCAM-8) — BDMA Sample Import; detailed mapping thuộc Traceability Matrix

Dependencies / Blockers

Approved Security Profile / Security Review cho exact cryptographic algorithm, key management và BDMA decryption behavior.

Related Documents

DCAM Architecture Home, 05 - Data, Storage & BDMA Architecture, DCAM-BDMA Data Contract, 05 - User & Device Operation Requirements, DCAM Storage Design, DCAM SQLite Database Design, DCAM Recording & Capture Design, DCAM Security & Encryption Design, DCAM BDMA Integration Technical Design, DCAM Documentation Governance, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Tài liệu này định nghĩa **ranh giới tích hợp và trách nhiệm hệ thống** giữa:

**DCAM Android Application** chạy trên thiết bị BodyCamera.

**BDMA Desktop** chạy trên máy tính Windows/Desktop.

Tài liệu này không thay thế **DCAM-BDMA Data Contract**. Tài liệu này chỉ định nghĩa **boundary**, còn Data Contract định nghĩa chi tiết folder structure, file naming, MD5 behavior cho video `.mp4`, CSON, SQLite DB, logs, user/operator sync và ingest/cleanup rules.

## 2. Core Architectural Decision

DCAM được thiết kế theo mô hình:

DCAM Android = Offline Data Producer + Runtime Owner
BDMA Desktop = Active Data Consumer / Importer / Administrative Sync Manager
Điều này có nghĩa:

DCAM tạo dữ liệu trên BodyCamera.

DCAM lưu dữ liệu cục bộ trên thiết bị BodyCamera.

DCAM quản lý user/operator và operator session offline trong `dcam.db`.

DCAM expose media files và `.md5` cho video `.mp4` theo approved Build Profile; Build 0.1 yêu cầu `.md5` cho mọi MP4. CSON, SQLite DB và logs được expose để BDMA đọc qua ADB.

BDMA chủ động kết nối thiết bị qua ADB.

BDMA chủ động đọc và verify `.mp4` theo approved Build Profile; Build 0.1 chỉ import sau valid `.md5`. BDMA lưu trữ, index, quản lý và hiển thị dữ liệu.

BDMA quản lý user/operator trong BDMA database và sync hai chiều với DCAM qua ADB.

BDMA được phép write-back một số dữ liệu theo **DCAM-BDMA Data Contract** và **DCAM SQLite Database Design** ownership rules.

BDMA có thể cleanup source media sau import theo policy trong **DCAM-BDMA Data Contract**.

DCAM **không chủ động push/upload/sync media** sang BDMA trong scope hiện tại.

## 3. High-Level Boundary

BodyCamera Android Device
    ↓
DCAM Android Application
    ├── Record / Capture / Finalize Media
    ├── Maintain local user/operator/auth/session data in dcam.db
    ├── Maintain dcam_config.cson
    ├── Maintain dcam.db
    ├── Write logs.txt
    └── Expose BDMA-ready source data
            ↓ ADB Boundary, BDMA initiates access
BDMA Desktop
    ├── Scan Internal / External Media Roots
    ├── Read Media / MP4 MD5 / CSON / DB / Logs
    ├── Sync user/operator data with DCAM
    ├── Verify / Import / Store / Index
    ├── Write-back only allowed data
    └── Cleanup source media according to Data Contract
## 4. Integration Principle Summary

Principle

Description

Status

DCAM is Data Producer

DCAM tạo media và `.md5` cho video `.mp4` theo approved Build Profile; Build 0.1 yêu cầu `.md5` cho mọi MP4. DCAM cũng tạo device config, SQLite DB và logs trên thiết bị.

Decided

DCAM owns Android runtime state

DCAM owns active operator session, active media session và runtime invariants.

Decided

BDMA is Data Consumer / Importer / Manager

BDMA đọc và verify video `.mp4` theo approved Build Profile; Build 0.1 không import khi MD5 missing/mismatch/failed. BDMA index, quản lý và hiển thị dữ liệu do DCAM tạo.

Decided

BDMA is Administrative Sync Manager

BDMA là administrative source of truth mặc định cho user/operator sync.

Decided

ADB is Current Transfer Boundary

Giao tiếp giữa BDMA và Android device được thực hiện qua ADB.

Decided

BDMA Initiates Synchronization

BDMA chủ động đọc/sync dữ liệu qua ADB; DCAM không push media sang BDMA.

Decided

User Sync is Two-way

User/operator data sync hai chiều giữa BDMA database và DCAM `dcam.db`.

Decided

Local Files are Source Data

Dữ liệu gốc gồm media files, `.md5` cho `.mp4` theo approved Build Profile, CSON, SQLite DB và logs; Build 0.1 yêu cầu `.md5` cho mọi MP4.

Decided

BDMA Write-back is Contract-based

BDMA được write-back vào `dcam_config.cson` và `dcam.db` theo Data Contract và SQLite ownership rules.

Decided

Logs are Read-only for BDMA

BDMA chỉ được đọc `logs.txt`, không được ghi/sửa/xóa/truncate.

Decided

Media Cleanup is Policy-based

BDMA có thể xóa source media sau import theo cleanup policy trong Data Contract.

Decided

Error Responsibility Must Be Clear

Lỗi phát sinh ở đâu thì system owner tương ứng chịu trách nhiệm xử lý.

Decided

## 5. Access Boundary Summary

Dữ liệu do DCAM tạo ra sẽ được lưu trên thiết bị BodyCamera dưới dạng file/database theo **DCAM-BDMA Data Contract**.

Data Category

Created / Owned By

Read By

Write/Update/Delete Rule

Video/Image/Audio Media

DCAM

BDMA

BDMA có thể import và cleanup theo Data Contract.

MD5 Checksum

DCAM

BDMA

Chỉ áp dụng cho `.mp4`; behavior theo Data Contract và Matrix. Build 0.1 yêu cầu valid MD5; legacy Unverified behavior chưa có approved profile mapping.

Device Config `dcam_config.cson`

DCAM

BDMA

BDMA có thể read/write/update device information only.

User/operator profile data

DCAM + BDMA

DCAM + BDMA

Sync hai chiều theo Data Contract và SQLite ownership.

User auth method data

DCAM + BDMA

DCAM + BDMA

Chỉ sync approved protected references; Android validate trước khi use.

Active operator session

DCAM

BDMA diagnostics/read only

BDMA không được trực tiếp modify active runtime session.

SQLite Database `dcam.db`

DCAM

BDMA

BDMA chỉ được write approved fields/tables theo Data Contract và SQLite Database Design.

Log File `logs.txt`

DCAM

BDMA

BDMA read-only.

Temp / staging files

DCAM

Normally not BDMA

BDMA không được process trừ khi contract tương lai định nghĩa rõ.

## 6. SQLite and User Sync Boundary

Detailed DB ownership và write-back rules được định nghĩa bởi **DCAM SQLite Database Design** và **DCAM-BDMA Data Contract**.

Architecture boundary summary:

Android owns schema and runtime invariants.
BDMA may write only approved fields/tables.
BDMA must check schema_version and supported versions.
BDMA write-back must be revisioned/auditable.
Android must detect external writes and apply/defer/reject safely.
User/operator sync boundary:

BDMA connects through ADB
    ↓
BDMA reads DCAM user sync state/revision
    ↓
BDMA pushes approved BDMA-side user changes
    ↓
BDMA pulls DCAM local user changes
    ↓
Both sides update sync checkpoint/history
BDMA không được write active Android runtime lifecycle fields trừ khi được Data Contract và SQLite ownership rules phê duyệt rõ ràng.

## 7. Recording and Operator Attribution Boundary

Normal recording/capture evidence yêu cầu active operator session trên DCAM.

Emergency recording có thể dùng system operator khi chưa có operator logged in:

```
EMERGENCY_OVERRIDE_ADMIN
```

Boundary summary:

Area

Source of Truth

Login/session policy

05 - User & Device Operation Requirements + DCAM Android Operation Design

Operator session DB state

DCAM SQLite Database Design

Recording operator gate and media attribution

DCAM Recording & Capture Design

Emergency override security

DCAM Security & Encryption Design

BDMA display/sync contract

DCAM-BDMA Data Contract

BDMA phải preserve sự khác biệt giữa real authenticated operator recording và emergency override recording.

## 8. BDMA Readiness Boundary

BDMA chỉ nên import final/ready source data.

`BDMA_READY` được định nghĩa bởi các runtime technical designs:

Area

Source of Truth

Recording session finalization and BDMA_READY meaning

DCAM Recording & Capture Design

Storage final file readiness and temp/final distinction

DCAM Storage Design

DB media session/import state

DCAM SQLite Database Design

External file naming/import/cleanup contract

DCAM-BDMA Data Contract

`BDMA_READY` nghĩa là media safe for BDMA scan/import. Nó không có nghĩa là BDMA đã import media.

## 9. Error Responsibility Matrix

Scenario

Primary Owner

Expected Handling

Recording/capture fails

DCAM

Log error, update operational state nếu có thể, preserve evidence nếu cần.

Normal recording without operator login

DCAM

Reject theo operator-auth-required behavior.

Emergency recording without operator login

DCAM

Dùng `EMERGENCY_OVERRIDE_ADMIN` và persist auditable attribution.

User sync conflict

BDMA default / shared

Apply agreed conflict policy và log conflict.

User disabled during active recording

DCAM + BDMA

Không interrupt current recording; block new recording sau safe window.

Invalid auth data from sync

DCAM

Reject và giữ last valid local state.

Media naming invalid

DCAM

Sửa media naming; BDMA report skipped/unknown file.

`.mp4` MD5 missing

DCAM / BDMA handling

Block đối với mọi currently approved profile. Legacy Unverified import chỉ được phép sau future named-profile approval.

`.jpg`, `.mp3`, `.aac`, `.wav` MD5 missing

Not applicable

BDMA imports normally; MD5 không áp dụng cho image/audio.

`.mp4` MD5 mismatch

Shared investigation

BDMA không import/delete source; DCAM investigates source integrity.

Storage full on BodyCamera

DCAM

Apply Storage Design behavior và log diagnostics.

ADB device not detected

BDMA

Show connection error và retry guidance.

ADB disconnect during sync

BDMA

Retry/resume behavior vẫn là BDMA implementation TBD.

DB locked/corrupted

Shared

Android follows SQLite recovery; BDMA không được unsafe write.

Logs unreadable

DCAM / BDMA handling

BDMA có thể continue import nếu media valid và report warning.

Cleanup failed

BDMA

Import vẫn valid nhưng cleanup issue phải được report.

Data Contract mismatch

Shared

Yêu cầu contract review và version handling.

## 10. Relationship with Other Documents

Document

Relationship

DCAM-BDMA Data Contract

Source of truth cho file/folder/naming/MD5/CSON/DB/log/import/cleanup/user sync behavior.

05 - User & Device Operation Requirements

Source of truth cho user/operator requirement, login policy và emergency override.

DCAM SQLite Database Design

Source of truth cho SQLite schema ownership, user/auth/session tables, transaction, external write detection và recovery.

DCAM Recording & Capture Design

Source of truth cho recording/capture/finalization, operator gate và emergency evidence flow.

DCAM Storage Design

Source of truth cho Android-side storage mechanics, temp/final và BDMA readiness.

DCAM BDMA Integration Technical Design

BDMA implementation flow reference Data Contract và boundary này.

DCAM Security & Encryption Design

Source of truth cho credential/auth/encryption implementation direction.

ADR

Boundary changes và major responsibility changes nên được ghi nhận bằng ADR.

## 11. Remaining Technical Design Items

Item

Status

Owner Direction

Exact physical Internal DCAM Storage Root path

TBD

DCAM / Storage Design POC

Exact physical External DCAM Storage Root path

TBD

DCAM / Storage Design POC

Exact SQLite implementation fields

Draft baseline defined; exact implementation fields TBD during implementation

DCAM + BDMA

Exact user sync conflict UI

TBD

BDMA

QR/NFC credential formats

TBD

DCAM + BDMA / Security Design

Encryption key storage and BDMA decryption process theo approved Security Profile

TBD

DCAM + BDMA / Security Design

ADB retry/resume behavior

TBD

BDMA

Import validation error UX

TBD

BDMA

Duplicate import handling

TBD

BDMA

Partial import handling

TBD

BDMA

## 12. Practical Conclusion

DCAM tạo và expose local source data.
DCAM owns active runtime/session/media state.
BDMA reads/imports/verifies/manages data through ADB.
BDMA syncs user/operator data with DCAM through ADB.
Data Contract định nghĩa file/import/user-sync rules.
SQLite Design định nghĩa DB write-back rules.
Storage/Recording designs định nghĩa BDMA readiness và operator attribution.
Remaining TBD items trên boundary page này là các implementation/BDMA-side open decisions thật sự, không phải các source-of-truth issues đã được resolve.

## 13. Build 0.1 Boundary Override

DCAM sở hữu recording, Internal storage, finalization, MD5 generation, SQLite/CSON/log state và readiness publication.

BDMA chỉ scan approved logical Internal final-media root.

MP4 phải finalized và có valid MD5 trước khi đủ điều kiện import.

Missing/mismatch/failed MD5 không được import Unverified trong Build 0.1. Legacy Unverified behavior hiện không được gán cho Build 0.2, Build 0.3 hoặc Pilot/Shipment.

Image no-MD5 behavior không đổi.

BDMA không cleanup protected artifacts hoặc source data khi ADB/permission/import/checksum chưa success.

Physical ADB path phải chờ Device POC; [DucVietTech/dcam](https://github.com/DucVietTech/dcam) đã được PM-approved như repository identity, nhưng chưa thay thế PR/build/test evidence.