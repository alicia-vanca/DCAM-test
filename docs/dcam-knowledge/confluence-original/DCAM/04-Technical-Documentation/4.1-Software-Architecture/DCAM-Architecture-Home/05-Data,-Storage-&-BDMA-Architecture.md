# 05 - Data, Storage & BDMA Architecture

**Page ID**: 47185950  
**Version**: 13  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47185950

---


# 05 - Data, Storage & BDMA Architecture

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Data Architecture

Version

Approved 1.9

Status

Approved

Approval Scope

High-level data/storage/BDMA architecture; MD5 applicability theo Matrix/Data Contract; cryptographic algorithm, key management và decryption policy thuộc DCAM Security & Encryption Design và approved Security Profile.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Team

Last Updated

2026-07-13

Related Jira

None

Dependencies / Blockers

Approved Security Profile / Security Review cho exact cryptographic algorithm, key management và decryption behavior.

Related Documents

DCAM Architecture Home, 04 - Application & Module Architecture, 08 - DCAM-BDMA Integration Boundary, DCAM-BDMA Data Contract, DCAM Documentation Governance, DCAM Security & Encryption Design

## 1. Purpose

Trang này mô tả kiến trúc dữ liệu ở mức high-level cho **DCAM**, bao gồm media, local storage direction, SQLite database, device config, logs và relationship với **BDMA Desktop**.

Trang này phải khớp với:

**DCAM-BDMA Data Contract**.

**08 - DCAM-BDMA Integration Boundary**.

Nguyên tắc chính:

DCAM creates and exposes local data.
BDMA reads, imports, updates allowed data and performs cleanup according to Data Contract.
Chi tiết chính thức về folder structure, file naming, MD5 cho video `.mp4`, media-encryption suffix theo approved Security Profile, CSON, SQLite DB, logs và cleanup policy nằm trong **DCAM-BDMA Data Contract** và **DCAM Security & Encryption Design**.

## 2. Data Architecture Boundary

DCAM là **data producer**.

BDMA là **data consumer / importer / manager**.

Side

Responsibility

DCAM Android

Tạo media, device config, SQLite database và logs; lưu dữ liệu cục bộ trên BodyCamera; expose dữ liệu để BDMA đọc được qua ADB.

BDMA Desktop

Chủ động kết nối qua ADB, đọc dữ liệu từ thiết bị, verify/import media theo Data Contract, update config/database theo quyền được định nghĩa, và cleanup source media theo policy.

DCAM không chủ động push/upload/sync dữ liệu sang BDMA trong scope hiện tại.

BDMA không tạo media gốc trên Android, nhưng được phép update `dcam_config.cson` và `dcam.db` theo **DCAM-BDMA Data Contract**.

## 3. High-Level Data Flow

Record / Capture on BodyCamera
    ↓
DCAM generates media file
    ↓
DCAM embeds media metadata if supported by media format
    ↓
DCAM generates matching .md5 for .mp4 according to approved Build Profile; Build 0.1 requires it
    ↓
DCAM saves media in Internal or External DCAM Media Root
    ↓
DCAM maintains dcam_config.cson, dcam.db and logs.txt in Internal Storage
    ↓
User connects BodyCamera to BDMA Desktop
    ↓
BDMA detects device through ADB
    ↓
BDMA scans External then Internal DCAM Media Root
    ↓
BDMA verifies .md5 according to approved Build Profile; Build 0.1 blocks missing/mismatch
    ↓
BDMA imports media and reads allowed config/database/logs
    ↓
BDMA updates allowed CSON/DB data if needed
    ↓
BDMA performs source media cleanup according to policy
## 4. Data Categories

Data Type

Format

Location Direction

Created By

Read By

Write/Update By

Notes

Video File

`.mp4`

Internal hoặc External `Media/Video`, hoặc `Media/IMP` nếu important

DCAM

BDMA

DCAM tạo; BDMA có thể delete sau import theo policy

Main video media file. `.md5` có thể tồn tại để video verification.

Image File

`.jpg`

Internal hoặc External `Media/Image`, hoặc `Media/IMP` nếu important

DCAM

BDMA

DCAM tạo; BDMA có thể delete sau import theo policy

Image capture output. `.md5` không áp dụng.

Audio File

`.mp3`, `.aac`, `.wav`

Internal hoặc External `Media/Audio`, hoặc `Media/IMP` nếu important

DCAM

BDMA

DCAM tạo; BDMA có thể delete sau import theo policy

Audio media nếu feature được enable. `.md5` không áp dụng.

Important Media

`_IMP` suffix

`Media/IMP`

DCAM

BDMA

DCAM tạo; BDMA có thể delete sau import theo policy

Áp dụng cho video, image và audio. `.md5` chỉ áp dụng nếu important media là `.mp4`.

Encrypted Media

`_enc` suffix

Same media folders

DCAM

BDMA

DCAM tạo; BDMA import/decrypt nếu supported

Encrypted media naming phải tuân theo approved Security Profile; `.md5` chỉ áp dụng cho encrypted `.mp4` khi approved build/profile applicability yêu cầu.

MD5 Checksum

`.md5`

Same folder với `.mp4` video file

DCAM

BDMA

BDMA không được modify content; có thể delete cùng `.mp4` cleanup

Required cho mọi Build 0.1 MP4. Legacy optional/Unverified behavior chưa có approved Build Profile mapping. Không áp dụng cho `.jpg`, `.mp3`, `.aac`, `.wav`.

Device Config

`dcam_config.cson`

Internal `Config`

DCAM

BDMA

DCAM và BDMA

Chỉ chứa device information.

SQLite Database

`dcam.db`

Internal `Database`

DCAM

BDMA

DCAM và BDMA

Chứa User data, Device tracking và Operational data.

Log File

`logs.txt`

Internal `Logs`

DCAM

BDMA

Chỉ DCAM; BDMA read-only

Operational logs và diagnostics.

## 5. Storage Direction

DCAM dùng hai logical storage roots:

DCAM Internal Storage Root
DCAM External Storage Root
Physical path thực tế là device-specific và phải được validate trên BodyCamera thật.

### 5.1 Internal DCAM Storage Root

Internal DCAM Storage Root
├── Media
│   ├── Video
│   ├── Image
│   ├── Audio
│   └── IMP
├── Config
│   └── dcam_config.cson
├── Database
│   └── dcam.db
├── Logs
│   └── logs.txt
└── Temp
### 5.2 External DCAM Storage Root

External DCAM Storage Root
├── Media
│   ├── Video
│   ├── Image
│   ├── Audio
│   └── IMP
└── Temp
### 5.3 Storage Location Strategy

Data

Storage Rule

`dcam_config.cson`

Fixed trong Internal Storage.

`dcam.db`

Fixed trong Internal Storage.

`logs.txt`

Fixed trong Internal Storage.

Media files

Lưu trong Internal hoặc External theo user setting.

Auto mode

Ưu tiên External; fallback sang Internal nếu External unavailable, missing, full, not writable hoặc invalid.

## 6. Media Lifecycle Direction

New Recording / Capture
    ↓
Temporary file or in-progress file
    ↓
Media file completed
    ↓
Metadata embedded in media if supported
    ↓
Generate .md5 only for completed .mp4 when required by approved Build Profile; Build 0.1 requires it
    ↓
Media available in Media/Video, Media/Image, Media/Audio or Media/IMP
    ↓
Ready for BDMA ADB-based discovery
    ↓
BDMA imports and validates according to media type
    ↓
BDMA stores and indexes imported copy
    ↓
BDMA cleans up source media according to Data Contract policy
Final import/readiness behavior được định nghĩa bởi **DCAM-BDMA Data Contract**. Architecture page này không nên tạo source-side status model riêng gây conflict với contract.

## 7. Media Metadata Direction

Media metadata được DCAM tạo và BDMA consume.

Theo **DCAM-BDMA Data Contract**:

Media metadata is embedded in media files if supported by the media format.
Separate media metadata JSON is not part of the current contract.
Implications:

Topic

Direction

Media metadata exchange

Embedded trong media file nếu supported.

Separate media metadata JSON

Không thuộc current contract.

Device information

Lưu trong `dcam_config.cson`.

User data / device tracking / operational data

Lưu trong `dcam.db`.

Logs

Lưu trong `logs.txt`.

## 8. Media Naming Direction

Media naming được định nghĩa bởi **DCAM-BDMA Data Contract**.

Normal media:

```
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS.<ext>
```

Important media:

```
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS_IMP.<ext>
```

Encrypted media:

```
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS_enc.<ext>
```

Important encrypted media:

```
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS_IMP_enc.<ext>
```

Supported extensions:

Media Type

Format

Video

`.mp4`

Image

`.jpg`

Audio

`.mp3`, `.aac`, `.wav`

Token mapping dưới đây chỉ là architecture summary; **DCAM-BDMA Data Contract** sở hữu authoritative rule:

Token

Build 0.1 Mapping

Constraint

XXXXXX / DEVICE_TOKEN

Snapshot của validated `serial_number`

6–10 ký tự, `[A-Z0-9]`; không underscore hoặc silent truncation.

ZZZZZZ / OPERATOR_TOKEN

`B01OPR` từ `operator_id`

Đúng 6 ký tự, `[A-Z0-9]`.

Reference-device `serial_number` thực tế cần Device POC evidence. Timestamp timezone và same-second collision handling vẫn Pending Technical Review.

MD5 naming là video-only rule. Nếu DCAM tạo `.md5`, DCAM chỉ tạo `.md5` cho `.mp4` video files, bao gồm normal, important, encrypted và important encrypted `.mp4` variants.

## 9. BDMA Compatibility Principles

Principle

Description

DCAM Source Data is Local

BDMA đọc source data từ local Android storage/database thông qua ADB.

BDMA Initiates Read/Sync

DCAM không chủ động push/upload/sync dữ liệu sang BDMA trong scope hiện tại.

Deterministic Data Structure

BDMA không nên phải đoán ý nghĩa của file; cấu trúc dữ liệu cần rõ ràng và nhất quán.

Data Contract Required

Hành vi của BDMA phải tuân theo DCAM-BDMA Data Contract.

Legacy Missing-MD5 Import

Unverified/warning chỉ là compatibility behavior chưa có approved Build Profile mapping; không áp dụng cho Build 0.1, Build 0.2, Build 0.3 hoặc Pilot/Shipment theo current Matrix. Image/audio không áp dụng `.md5`.

Verified `.mp4` Import

Nếu video `.mp4` có `.md5` và verification pass, video có thể được xem là Verified.

Cleanup Policy

BDMA có thể delete source media sau import theo Data Contract. Với `.mp4`, cleanup phụ thuộc trạng thái MD5; với image/audio, `.md5` không áp dụng.

Write-back Scope

BDMA có thể update `dcam_config.cson` và `dcam.db`, nhưng `logs.txt` là read-only.

## 10. Source of Truth Direction

Information

Source of Truth Before Import

Source of Truth After Import

Media file

DCAM local storage

BDMA managed storage copy

Embedded media metadata

DCAM media file

BDMA imported metadata copy nếu parsed/imported

Device information

`dcam_config.cson`

BDMA imported/mapped device record nếu áp dụng

User data

`dcam.db`

BDMA imported/mapped user record nếu áp dụng

Device tracking

`dcam.db`

BDMA imported/mapped tracking record nếu áp dụng

Operational data

`dcam.db`

BDMA imported/mapped operational record nếu áp dụng

Logs

`logs.txt`

BDMA có thể copy/read logs để support, nhưng source log vẫn DCAM-owned

## 11. Relationship with DCAM-BDMA Data Contract

**DCAM-BDMA Data Contract** hiện là official source of truth cho:

Topic

Source of Truth

Logical storage roots

DCAM-BDMA Data Contract

Internal / External storage strategy

DCAM-BDMA Data Contract

Folder structure

DCAM-BDMA Data Contract

Media naming

DCAM-BDMA Data Contract

`_IMP`, `_enc`, `_IMP_enc` suffix rules

DCAM-BDMA Data Contract

MD5 naming and missing MD5 behavior for `.mp4` video only

DCAM-BDMA Data Contract

`dcam_config.cson` purpose and permission

DCAM-BDMA Data Contract

`dcam.db` purpose and permission

DCAM-BDMA Data Contract

`logs.txt` purpose and read-only permission

DCAM-BDMA Data Contract

BDMA import and cleanup rules

DCAM-BDMA Data Contract

Trang này phải giữ high-level và không được redefine conflicting details.

## 12. Relationship with 08 - DCAM-BDMA Integration Boundary

Topic

05 - Data, Storage & BDMA Architecture

08 - DCAM-BDMA Integration Boundary

Purpose

Định hướng high-level về data/storage architecture.

Ranh giới trách nhiệm hệ thống và ownership.

DCAM role

Bên tạo dữ liệu và owner của local source data.

Data Producer trên BodyCamera.

BDMA role

Bên đọc/import/manage dữ liệu, với write-back permissions được định nghĩa bởi Data Contract.

Active Data Consumer / Importer / Manager.

Transfer mechanism

Tham chiếu ADB như discovery/read path hiện tại.

Định nghĩa ADB là integration boundary hiện tại.

File/DB formats

Tóm tắt contract baseline hiện tại, bao gồm `.md5` chỉ áp dụng cho `.mp4`.

Định nghĩa ownership/access responsibility xung quanh các format đó.

Final contract

DCAM-BDMA Data Contract.

DCAM-BDMA Data Contract.

Hai tài liệu này phải luôn align với nhau. Nếu một tài liệu thay đổi ownership hoặc transfer responsibility, tài liệu còn lại cũng phải được cập nhật.

## 13. Remaining Technical Design Items

Các implementation details dưới đây vẫn thuộc các tài liệu **4.2 - Technical Design** trong tương lai hoặc tài liệu technical design tương ứng:

Item

Related Document

Exact physical Internal DCAM Storage Root path

DCAM Storage Design

Exact physical External DCAM Storage Root path

DCAM Storage Design

SQLite database schema

DCAM SQLite Database Design

Encryption key storage and decryption process theo approved Security Profile

DCAM Security & Encryption Design

Temp file recovery behavior

DCAM Storage Design

Duplicate import handling

BDMA Technical Documentation / Data Contract update if needed

Partial import handling

BDMA Technical Documentation / Data Contract update if needed

## 14. Practical Conclusion

Kiến trúc dữ liệu của DCAM cần được thiết kế ngay từ đầu để phục vụ BDMA ingest, nhưng boundary về ownership phải luôn rõ ràng:

DCAM chịu trách nhiệm tạo dữ liệu và đảm bảo source data sẵn sàng ở local storage.
BDMA chịu trách nhiệm đọc qua ADB, import và verify `.mp4` theo approved Build Profile; Build 0.1 yêu cầu valid `.md5` trước BDMA_READY/import. BDMA lưu trữ, index, quản lý và hiển thị dữ liệu.
Contract baseline cuối cùng hiện đã được định nghĩa trong **DCAM-BDMA Data Contract**. Tất cả tài liệu Storage Design, SQLite Database Design, Security & Encryption Design và BDMA import implementation sau này phải align với contract này, đặc biệt rule mới: `.md5` chỉ áp dụng cho video `.mp4`, không áp dụng cho `.jpg`, `.mp3`, `.aac` hoặc `.wav`.