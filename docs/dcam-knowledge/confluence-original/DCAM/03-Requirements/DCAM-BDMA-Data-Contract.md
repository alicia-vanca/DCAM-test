# DCAM-BDMA Data Contract

**Page ID**: 47743153  
**Version**: 17  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743153

---


# DCAM-BDMA Data Contract

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Data Contract / Integration Contract

Version

Approved 1.14

Status

Approved

Approval Scope

Global contract với authoritative media filename token mapping, Build 0.1 exchange profile và legacy MD5 applicability guardrail.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / BDMA Lead / Security Reviewer / Cloud Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements

Target Audience

PM/BA, Tech Lead, Android Developers, BDMA Developers, QA, Support, Cloud/WebServer Team

Last Updated

2026-08-25

Related Jira

None

Related Documents

DCAM Factory Provisioning & Device Production SOP, DCAM Web Portal & Device API Contract, DCAM Device Provisioning Web Portal Design, DCAM Device Provisioning Web Portal App Design, DCAM Device Provisioning Web Portal Implementation Design, DCAM MVP Scope, DCAM Release & Build Applicability Matrix, DCAM Architecture Home, 04 - Device Configuration Requirements, 05 - User & Device Operation Requirements, 06 - Cloud Services, Update & Configuration Architecture, 07 - Logging & Diagnostics Requirements, 07 - Logging, Diagnostics, Performance & Security, DCAM Logging & Diagnostics Design, 09 - System Settings Requirements, DCAM SQLite Database Design, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM Security & Encryption Design, 05 - Data, Storage & BDMA Architecture, 08 - DCAM-BDMA Integration Boundary, DCAM QA Test Strategy & Test Matrix, DCAM Documentation Governance, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Tài liệu này định nghĩa **Data Contract** giữa:

**DCAM Android Application** chạy trên BodyCamera.

**BDMA Desktop** chạy trên Windows/Desktop.

**BFF/PostgreSQL ddmp** quản lý device identity, Web Portal provisioning, remote config và backend metadata.

Contract thống nhất cách DCAM tạo, lưu trữ và expose dữ liệu để BDMA có thể read, verify, import, update, sync hoặc delete theo rule đã thống nhất.

Identity baseline:

serial_number = Hardware Identity / primary recovery key
dcam_cloud_device_id = Cloud Identity / primary cloud device id
SD Identity File = recovery cache on external SD card, not Hardware Identity
Do not use ANDROID_ID, android_id_hash or device_lookup/{android_id_hash}
Tài liệu này là source of truth cho:

Storage roots
Folder structure
Media naming
Important / encrypted media suffix
MD5 rule for .mp4 only
Device config CSON scope
Device identity and device information field boundary
App/data/media/encoder contract version compatibility
SQLite database file access boundary
User/operator sync boundary
BDMA-facing logs artifact
BDMA import and cleanup behavior
Logging implementation không được định nghĩa lại tại đây:

DCAM Logging & Diagnostics Design
    → owns internal structured files, rotation, local queue,
      Backend Relay → Loggly and Crashlytics boundary

This Data Contract
    → owns only the artifact and access contract exposed to BDMA
## 2. System Boundary

DCAM Android = Offline Data Producer + Runtime Owner
BDMA Desktop = Active Data Consumer / Importer / Administrative Sync Manager
BFF/PostgreSQL ddmp = Cloud Identity + Remote Config + Web Portal Provisioning Owner
DSetup = Factory helper tool up to imported serial_number verification
### 2.1 DCAM Responsibilities

Area

Responsibility

Media creation

Tạo video, image và audio media.

Fixed media encoder

Tạo media theo fixed DCAM encoder behavior; BDMA không cần dynamic decoder profile.

App/contract metadata

Expose app identity, data contract version, media contract version và encoder contract version.

Important media marking

Tạo important files với suffix `_IMP`.

Encryption suffix

Tạo encrypted media với suffix `_enc` hoặc `_IMP_enc` khi encryption enabled.

Media storage

Lưu media vào Internal hoặc External storage theo setting / Auto fallback.

MD5 generation

Chỉ tạo `.md5` cho video `.mp4` nếu checksum feature enabled.

Device config file

Tạo và maintain `dcam_config.cson` cho device information only.

Device identity local state

Lưu local mirror của `dcam_cloud_device_id`, `serial_number`, owner, manufacture date và provisioning state trong `dcam.db`.

SD Identity File sync

Sync app-private `serial_number` ra SD Identity File làm recovery cache nếu feature enabled.

SQLite database

Tạo và maintain `dcam.db` cho user/operator, settings, runtime, media, identity, config cache và sync state.

User/operator runtime

Quản lý operator login session offline.

Operational logging

Ghi local Operational Logging theo Logging Requirements/Design và expose sanitized BDMA artifact `Logs/logs.txt`.

Cloud logging delivery

Quản lý internal upload queue/Backend Relay delivery; queue không phải BDMA artifact.

BDMA readiness

Chỉ expose final media khi safe for BDMA scan/import.

### 2.2 BDMA Responsibilities

Area

Responsibility

Device access

Kết nối và đọc dữ liệu qua ADB.

App/contract recognition

Nhận dạng DCAM bằng package/app/contract metadata và compatibility table.

Decoder profile

Không dùng `bdma_decoder_profile_id`.

Media discovery

Scan media ở External và Internal DCAM Media Roots.

MD5 verification

Verify `.md5` theo approved Build Profile; Build 0.1 block khi missing, còn legacy Unverified behavior chưa có approved profile mapping.

Import

Import media vào BDMA managed storage/index.

User/operator administration

Quản lý user/operator trong BDMA database.

User/operator sync

Đồng bộ user/operator hai chiều với DCAM qua ADB.

Device config read

Read `dcam_config.cson`.

Database write-back

Write-back vào `dcam.db` chỉ theo approved tables/fields và schema/version rules.

Logs reading

Đọc stable `Logs/logs.txt` artifact để diagnostics; không ghi, sửa, truncate hoặc xóa.

Internal logging artifacts

Không đọc/consume internal rotated files hoặc upload queue trừ khi future contract version approve.

Cleanup

Xóa source media sau import theo cleanup policy; không cleanup diagnostics/config/database artifacts.

### 2.3 BFF/PostgreSQL ddmp Responsibilities

Area

Responsibility

Device primary record

Quản lý server-side device record theo `dcam_cloud_device_id`.

Serial lookup

Quản lý mapping `serial_number` → `dcam_cloud_device_id`.

Web Portal provisioning

Create/restore identity sau authenticated Factory Worker action.

Device information management

Lưu latest serial, owner, manufacture date, device model, firmware và related metadata.

App/contract metadata

Lưu app/data/media/encoder contract metadata nếu cần.

Remote config metadata

Lưu target config revision/profile metadata.

Operational Log Relay

Nếu implemented, authenticated relay nhận sanitized operational events và forward tới Loggly theo Logging Design/API Contract.

Audit

Audit provisioning, device information change, config publish/apply result và security-relevant backend action.

## 3. Device Identity and Device Information Contract

Field

Role

Storage

`dcam_cloud_device_id`

BFF/PostgreSQL ddmp primary cloud key.

BFF/PostgreSQL ddmp + `dcam.db`.

`serial_number`

Hardware Identity / primary recovery key.

App-private storage, `dcam_config.cson`, `dcam.db` mirror, BFF/PostgreSQL ddmp, SD Identity File cache.

SD Identity File

Recovery cache; not Hardware Identity.

External SD card, approved Factory SOP path.

`owner_name`

Mutable/semi-static device information.

`dcam_config.cson`, `dcam.db` mirror, BFF/PostgreSQL ddmp.

`manufacture_date`

Semi-static manufacture date using `YYYY-MM-DD`.

`dcam_config.cson`, `dcam.db` mirror, BFF/PostgreSQL ddmp.

`serial_history`

Lịch sử serial nếu approved rework/support process thay đổi serial.

BFF/PostgreSQL ddmp; optional DB mirror.

`app_installation_id`

App-install instance metadata only.

Optional metadata; not device identity key.

Rules:

serial_number is Hardware Identity / primary recovery key.
dcam_cloud_device_id is the primary server/cloud device id.
serial_lookup/{serial_number} is used to create/restore dcam_cloud_device_id.
SD Identity File is recovery cache only.
owner_name and manufacture_date are not primary keys.
Advertising ID is not a DCAM identity key.
ANDROID_ID, android_id_hash and device_lookup/{android_id_hash} are not used.
manufacture_date uses YYYY-MM-DD.
Logical server mapping:

serial_lookup/{serial_number} = dcam_cloud_device_id

devices/{dcam_cloud_device_id}
    dcam_cloud_device_id
    serial_number
    serial_history
    owner_name
    manufacture_date
    device_model
    firmware_version
    app_code
    app_package_name
    app_version_name
    app_version_code
    dcam_data_contract_version
    media_contract_version
    encoder_contract_version
    provisioning_status
    config_revision
    status
    last_seen_at
### 3.1 App / Media / Encoder Compatibility Contract

Field

Purpose

`app_code`

Mã app nội bộ, ví dụ `DCAM_ANDROID`.

`app_package_name`

Android package name.

`app_version_name` / `app_version_code`

Version app đang chạy.

`dcam_data_contract_version`

Version tổng thể contract DCAM ↔ BDMA.

`media_contract_version`

Version folder/naming/suffix/checksum/import/cleanup rule.

`encoder_contract_version`

Version fixed encoder/media encoding behavior.

Not used:

bdma_decoder_profile_id
decoder_profile_id
dynamic_decoder_profile
## 4. Web Portal / Factory Provisioning Contract

Approved business provisioning flow:

DSetup completes imported serial_number verification
    ↓
DCAM displays provisioning QR
    ↓
Factory Worker logs in to Web Portal
    ↓
Workspace scans QR displayed by DCAM
    ↓
serial_number is displayed read-only
    ↓
Factory Worker enters/selects owner_name and manufacture_date
    ↓
Backend checks serial_lookup/{serial_number}
    ↓
Create new or restore existing dcam_cloud_device_id
    ↓
DCAM receives/restores identity and writes local state
Rules:

Web Portal serial source is the provisioning QR displayed by DCAM.
Web Portal does not allow manual serial entry.
Web Portal does not scan serial barcode from the device label.
Web Portal does not mark PASS, FAIL, QUARANTINED or READY_TO_SHIP.
QR is business provisioning only; it is not Device Owner enrollment.
QR must not contain ANDROID_ID, android_id_hash or long-lived secret.
Exact QR/API details belong to Web Portal Design and API Contract.

## 5. Storage Location Strategy (General / non-Build 0.1 where applicable)

Build 0.1 sử dụng authoritative override tại §16.

Storage Type

Purpose

Internal Storage

Lưu `dcam_config.cson`, `dcam.db`, BDMA-facing `logs.txt`, internal diagnostics artifacts và có thể lưu media khi Internal/Auto fallback được chọn.

External Storage

Ưu tiên media khi hợp lệ; có thể lưu SD Identity File recovery cache.

Fixed BDMA-visible Internal Files:

Config/dcam_config.cson
Database/dcam.db
Logs/logs.txt
Optional External Recovery Cache:

```
DCAM_FACTORY/device_identity.json
```

Internal implementation artifacts có thể tồn tại nhưng không tự động trở thành BDMA contract:

rotated operational log files
local upload queue
relay retry metadata
Crashlytics local/provider cache
Media storage rule:

User Setting

Behavior

Internal

Lưu media vào Internal DCAM Media Root.

External

Lưu media vào External DCAM Media Root nếu hợp lệ.

Auto

Ưu tiên External; fallback sang Internal trước recording nếu External unavailable/full/missing/not writable/invalid.

## 6. Logical Folder Contract

### 6.1 Internal DCAM Storage Root

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
│   └── logs.txt                 # stable BDMA-facing artifact
└── Temp
Internal rotated logs/queue may be stored in app-private/internal implementation paths that are not part of the BDMA folder contract.

### 6.2 External DCAM Storage Root

External DCAM Storage Root
├── Media
│   ├── Video
│   ├── Image
│   ├── Audio
│   └── IMP
├── DCAM_FACTORY
│   └── device_identity.json
└── Temp
Physical paths are device-specific and require real-device validation.

## 7. Media File Naming Contract

Supported media formats:

Media Type

Supported Format

Image

`.jpg`

Video

`.mp4`

Audio

`.mp3`, `.aac`, `.wav`

Normal naming:

```
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS.<ext>
```

Important/encrypted examples:

DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS_IMP.<ext>
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS_enc.<ext>
DCAM_XXXXXX_ZZZZZZ_YYYYMMDD_HHMMSS_IMP_enc.<ext>
### 7.1 Filename Components

Component

Meaning

Contract Rule

`DCAM`

Fixed Prefix

Luôn là `DCAM`.

`XXXXXX` / `DEVICE_TOKEN`

Snapshot của validated `serial_number`

6–10 ký tự, chỉ `[A-Z0-9]`; không underscore; không silent truncation.

`ZZZZZZ` / `OPERATOR_TOKEN`

Snapshot của `operator_id`

Đúng 6 ký tự, chỉ `[A-Z0-9]`; Build 0.1 dùng `B01OPR`.

`YYYYMMDD`

Date

Ngày gắn với capture/recording; timezone phải được Technical Review chốt.

`HHMMSS`

Time

Thời gian gắn với capture/recording; same-second collision handling phải được Technical Review chốt.

`<ext>`

File Extension

Dùng format được hỗ trợ trong bảng trên.

Format-only example; không phải Device POC evidence:

```
DCAM_BC240001_B01OPR_20260713_103000.mp4
```

Guardrails:

Giá trị `serial_number` thực tế phải được validation trước khi tạo `DEVICE_TOKEN`; reference-device value cần Device POC evidence.

Không được cắt ngắn, thêm underscore hoặc tự tạo token thay thế để biến input không hợp lệ thành hợp lệ.

`operator_name` không tham gia filename; Build 0.1 giữ `operator_name = Build 0.1 Operator`.

Artifact có token không hợp lệ không được đánh dấu `BDMA_READY` hoặc đưa vào Build 0.1 import evidence.

Filename legacy không được tự reinterpret theo mapping mới; compatibility chỉ được áp dụng khi có `media_contract_version` hoặc profile rule được phê duyệt và truy xuất được.

## 8. MD5 Checksum Contract (General / non-Build 0.1 where applicable)

Build 0.1 sử dụng authoritative override tại §16.

Legacy `missing MD5 → Unverified import` là generic compatibility behavior chưa được gán cho approved Build Profile nào. Build 0.2, Build 0.3 và Pilot/Shipment không được tự động áp dụng behavior này; future activation phải được phê duyệt và ghi rõ trong Release & Build Applicability Matrix.

`.md5` chỉ áp dụng cho video `.mp4`.

Rule

Description

Applies to `.mp4` only

Áp dụng cho normal, important, encrypted và important encrypted video.

Same folder

`.md5` lưu cùng folder với `.mp4`.

Same base name

`.md5` dùng cùng base name với `.mp4`.

Legacy compatibility only

Thiếu `.md5` chỉ có thể import dạng Unverified/warning khi một named Build Profile được phê duyệt rõ ràng; hiện không có approved profile mapping.

Not applicable for image/audio

Không tạo/tìm/warning cho image/audio.

## 9. BDMA Import and Cleanup Rules

BDMA scans:

Media/Video
Media/Image
Media/Audio
Media/IMP
BDMA must ignore as media:

Temp
DCAM_FACTORY/device_identity.json
Config/dcam_config.cson
Database/dcam.db
Logs/logs.txt
`logs.txt` được đọc qua diagnostics flow, không phải media import flow.

Case

BDMA Import

Delete Source

`.mp4` has `.md5` and verify pass

Yes

Cho phép auto delete nếu cleanup enabled.

`.mp4` has `.md5` and verify fail

No

No.

`.mp4` missing `.md5`

No for every currently approved profile

Legacy Unverified import chỉ được phép sau future named-profile approval; Build 0.1 hard-block.

Image/audio supported

Yes nếu import succeeds.

Cho phép auto delete nếu cleanup enabled.

Unsupported encrypted media

No hoặc deferred.

No.

File in `Temp`

No.

No.

SD Identity File

No media import.

No media cleanup.

BDMA không được cleanup `dcam_config.cson`, `dcam.db`, `logs.txt`, internal rotated logs, upload queue hoặc SD Identity File.

## 10. Device Config CSON Contract (General / non-Build 0.1 where applicable)

Build 0.1 sử dụng narrow operator exception tại §16.

Path:

```
Internal DCAM Storage Root/Config/dcam_config.cson
```

Purpose:

```
dcam_config.cson = static/semi-static device information only
```

Allowed examples:

Area

Example

Device display identity

CameraID / device display code.

Device name

BodyCamera name.

Device model

Model.

Serial number

Hardware Identity mirror.

Owner name

Owner/customer/agency display.

Manufacture date

`YYYY-MM-DD`.

Firmware/hardware version

Version information.

App/contract information

App and contract version metadata.

Not allowed:

user/operator data
auth credentials
operational settings
storage mode
feature flags
MD5/encryption enable flags
cleanup policy
BDMA import records
BDMA decoder profile id
application logs
logging provider token
factory Wi-Fi password
ANDROID_ID
android_id_hash
## 11. SQLite Database Contract

Path:

```
Internal DCAM Storage Root/Database/dcam.db
```

Data Group

Description

Device identity/provisioning

Cloud ID, serial, source, owner, manufacture date, contract metadata and provisioning state.

SD Identity File sync

Recovery cache availability/sync result.

Remote config cache

Target/pending/applied revision and apply status.

User/operator data

Profiles, auth references, session history and sync state.

Settings

Operational settings and applied setting state.

Runtime state

App/module/session state.

Media/session state

Recording/capture, operator snapshot, BDMA readiness and recovery state.

BDMA import/write-back

Import status, history, external change log and sync checkpoint.

Diagnostics

Diagnostic metadata/events nếu approved; không chứa provider credential hoặc forbidden secret.

DB ownership:

Android owns schema and runtime invariants.
BDMA writes only approved tables/fields.
BFF/PostgreSQL ddmp does not write directly into local DB.
BDMA checks schema_version before write-back.
Android safely applies/defers/rejects external writes.
## 12. User / Operator Sync Contract

DCAM stores user/operator data in dcam.db.
BDMA stores user/operator data in BDMA database.
BDMA syncs through ADB.
Sync is two-way, versioned and auditable.
Required emergency system identity:

user_id = SYSTEM_EMERGENCY_OVERRIDE
operator_code = EMERGENCY_OVERRIDE_ADMIN
display_name = Emergency Override Admin
user_type = SYSTEM
role = SYSTEM_ADMIN
## 13. Logs Artifact Contract

### 13.1 Stable BDMA-facing Artifact

`logs.txt` is stored at:

```
Internal DCAM Storage Root/Logs/logs.txt
```

`logs.txt` là stable logical artifact cho BDMA/support diagnostics. Nó không bắt buộc phải là internal source file duy nhất của logging implementation.

Property

Contract

Purpose

Sanitized Operational Logging artifact cho BDMA/support.

Encoding

UTF-8.

Record boundary

Newline-delimited; mỗi logical event phải là một complete record.

Structured direction

JSON Lines hoặc equivalent structured single-line records theo Logging Design.

Sensitive fields

Phải được sanitize trước khi xuất.

Availability

DCAM maintain file hoặc safe snapshot để BDMA có thể đọc khi connected.

Active write behavior

BDMA phải coi file có thể đang được DCAM append/replace; nên copy/read snapshot an toàn.

### 13.2 Relation to Internal Rotated Logs

Internal structured active/rotated logs
        ↓ sanitize/normalize/export
Logs/logs.txt
        ↓ read-only ADB access
BDMA diagnostics
Rules:

Rule

Description

LOG-CONTRACT-001

`logs.txt` là stable BDMA-facing name bất kể internal logger dùng một hay nhiều rotated files.

LOG-CONTRACT-002

Internal rotated file names/paths không thuộc BDMA contract mặc định.

LOG-CONTRACT-003

BDMA không scan hoặc phụ thuộc vào internal rotated files.

LOG-CONTRACT-004

DCAM có thể regenerate/replace `logs.txt` atomically hoặc maintain append-only export theo Logging Design.

LOG-CONTRACT-005

Rotation không được để `logs.txt` biến mất lâu dài hoặc trở thành partial/corrupt artifact.

LOG-CONTRACT-006

Multi-line exception/context phải được escaped/normalized thành one logical record nếu exported.

### 13.3 Relation to Upload Queue and Providers

Local upload queue ≠ logs.txt
Loggly delivery state ≠ BDMA artifact
Crashlytics report/cache ≠ logs.txt
Rules:

Rule

Description

LOG-CONTRACT-007

Upload queue và relay retry metadata là internal implementation state, không expose cho BDMA.

LOG-CONTRACT-008

Loggly delivery success/failure có thể xuất hiện như safe Operational Logging event nhưng provider token/secret không được expose.

LOG-CONTRACT-009

Crashlytics reports không thuộc BDMA Data Contract; selected safe error event có thể đồng thời tồn tại trong Operational Logging.

LOG-CONTRACT-010

Provider unavailable không làm `logs.txt` unavailable nếu local logging vẫn hoạt động.

### 13.4 Permissions

Actor

Permission

DCAM

Read / Write / Replace / Rotate export.

BDMA

Read-only.

BFF/PostgreSQL ddmp

Không direct-access file; nhận operational events qua approved relay/API nếu implemented.

BDMA không được write, modify, truncate, rename hoặc delete `logs.txt`.

### 13.5 Forbidden Content

`logs.txt` không được chứa:

password or credential
BFF device access token
maintenance password
factory Wi-Fi password
Google account password/token
APK signing private key
raw Android system identifier
ANDROID_ID
android_id_hash
provisioning secret / long-lived QR secret
raw media/sensor/location/AI/biometric payload
full sensitive config payload
## 14. BDMA Write-back Rules

BDMA may write-back to:

Target

Permission

`dcam_config.cson`

Device information only nếu approved path.

`dcam.db` user/profile/auth/sync tables

Theo User Sync Contract.

`dcam.db` approved operational/import tables

Theo SQLite Design.

Source media

Delete after successful import theo cleanup policy.

Matching `.md5`

Delete after matching `.mp4` cleanup.

BDMA must not write-back to:

Target

Rule

`logs.txt`

Read-only.

Internal rotated logs / upload queue

Không thuộc BDMA contract.

Media content before import

Không modify.

Embedded media metadata

Không modify.

`.md5` content

Không modify.

`Temp`

Không process trừ future contract.

SD Identity File

Không modify/delete trừ future approved support/factory contract.

Active runtime/session lifecycle fields

Android runtime-owned.

Device identity primary mapping

BFF/PostgreSQL ddmp provisioning-owned.

`bdma_decoder_profile_id`

Not applicable.

## 15. Error and Warning Cases

Case

Type

Expected Handling

Missing `.md5` for `.mp4`

Blocking for every currently approved profile

Build 0.1 không import; legacy Unverified handling chưa được gán cho approved profile nào.

Missing `.md5` for image/audio

Not applicable

Import normally.

MD5 mismatch for `.mp4`

Error

Không import/delete source.

DB schema unsupported

Error

Không write; report compatibility error.

App/contract version unsupported

Error

Block hoặc compatibility warning.

Invalid user/auth sync data

Error

Reject, preserve last valid state và log.

Serial lookup not found

Provisioning

DCAM enters `PROVISIONING_REQUIRED`.

Server unavailable with local identity

Offline fallback

Continue using last valid local identity/config.

Server unavailable without local identity

Provisioning wait

Wait for network/Factory Worker action.

SD Identity File missing with app-private serial

Warning

Recreate if SD available.

SD Identity File conflict

Warning / policy

App-private serial wins.

`logs.txt` missing temporarily during atomic replace

Retryable warning

BDMA retries; media import may continue.

`logs.txt` malformed/partial

Warning

BDMA reports diagnostics warning; does not modify source.

Internal rotated logs unavailable

Not applicable to BDMA

BDMA depends only on `logs.txt`.

Loggly/Crashlytics unavailable

Diagnostics degradation

Local logs and `logs.txt` remain available; core flow continues.

External storage missing

Warning

Scan Internal fallback.

Cleanup failed

Warning/Error

Import remains valid; report cleanup issue.

## 16. Versioning

Versioned Area

Rule

Data Contract

Document version is contract baseline.

DB schema

`dcam.db` exposes schema/version metadata.

App contract metadata

App/package/version and contract metadata identify compatibility.

Media contract

Versions folder/naming/suffix/checksum/import/cleanup.

Encoder contract

Versions fixed encoder behavior.

Device identity

Server identity/provisioning contract is versioned.

SD Identity File

Path/schema/signature/checksum policy is versioned if enabled.

Remote config

Config schema/revision is versioned.

User sync

Records use revision/change tokens.

Logs artifact

Breaking change to `logs.txt` encoding, record format, location or BDMA access requires Data Contract + Logging Design + BDMA compatibility review.

Internal rotation/queue

May evolve within Logging Design without Data Contract change if `logs.txt` contract remains compatible.

## 17. Practical Conclusion

DCAM creates media, device config, dcam.db and operational diagnostics.
BFF/PostgreSQL ddmp owns dcam_cloud_device_id and serial_lookup mapping.
serial_number is Hardware Identity / primary recovery key.
dcam_cloud_device_id is Cloud Identity / primary cloud device id.
SD Identity File is recovery cache, not Hardware Identity.
Factory Worker Web Portal provisioning uses QR displayed by DCAM.
BDMA reads/imports media through ADB.
BDMA identifies compatibility through app/data/media/encoder contract versions.
BDMA does not use bdma_decoder_profile_id.
BDMA syncs user/operator data through dcam.db.
dcam_config.cson is device information only.
dcam.db stores operational/user/runtime/import/identity/config-cache state.
Logs/logs.txt is the stable sanitized BDMA-facing Operational Logging artifact.
Internal rotated logs, upload queue, Loggly delivery state and Crashlytics reports are not BDMA contract artifacts.
BDMA reads logs.txt in read-only mode and must not modify or delete it.
MD5 applies only to .mp4 video files.
BDMA_READY means safe for BDMA scan/import, not already imported.
## 18. Build 0.1 Authoritative Contract Profile

Section này override các generic/legacy rules mâu thuẫn đối với DCAM MVP Internal Build 0.1. Generic/legacy behavior ngoài Build 0.1 chỉ có hiệu lực khi Release & Build Applicability Matrix gán rõ cho một named Build Profile; hiện chưa có mapping đó.

### 18.1 Storage and Scan Root

Contract Item

Build 0.1 Rule

Active Storage

Internal storage only

External / Auto

Not Applicable

Fallback

Không fallback sang External

Pre-check Failure

Không bắt đầu recording

Runtime Failure

Safe-stop và finalize MP4 nếu còn khả năng

BDMA Scan

Chỉ scan approved logical Internal final-media root; ignore Temp/Cache

Physical Internal/ADB path vẫn Pending Device POC.

### 18.2 MP4 MD5 and Readiness

Contract Item

Build 0.1 Rule

Algorithm

MD5

Applicability

Mọi MP4

Purpose

Integrity check only

Sequence

Finalize MP4 → async MD5 → BDMA_READY

MD5 Failure

Giữ MP4, log, Checksum Pending/Failed, không import

Missing / Mismatch

Block import, evidence và release gate

Image

Không yêu cầu MD5; behavior hiện có giữ nguyên

Cleanup

Không xóa MP4/MD5/protected artifact khi verification/import chưa success

Legacy `missing MD5 → Unverified import` và `BDMA_READY-before-checksum` không áp dụng cho Build 0.1 và hiện không được gán cho Build 0.2, Build 0.3 hoặc Pilot/Shipment. `MD5 mismatch` luôn là verification error, không thuộc legacy exception.

### 18.3 Operator Attribution

Field

Build 0.1 Value

operator_id

B01OPR

operator_name

Build 0.1 Operator

Mutability

Không thể sửa từ UI/runtime configuration

Meaning

Technical traceability; không phải authenticated identity

Hai fields được phép trong SQLite, CSON và log tại nơi approved schema yêu cầu. Exact placement/serialization vẫn cần Technical Review.

### 18.4 Build 0.1 Filename Token Profile

Rule

Build 0.1 Baseline

Pattern

`DCAM_<DEVICE_TOKEN>_<OPERATOR_TOKEN>_YYYYMMDD_HHMMSS.<ext>`

DEVICE_TOKEN

Validated `serial_number` snapshot, 6–10 ký tự, `[A-Z0-9]`.

OPERATOR_TOKEN

`B01OPR`, đúng 6 ký tự, `[A-Z0-9]`.

Validation Failure

Không silent truncation; không `BDMA_READY`; ghi evidence lỗi theo approved Logging baseline.

Qualification

Device POC phải xác nhận `serial_number` thực tế và filename evidence trên reference device.

Open Technical Review

Timestamp timezone và same-second collision handling.

### 18.5 Evidence Boundary

GitHub code, PR, build hoặc test chỉ được dùng làm authoritative evidence khi repository mapping được ghi trong Decision Brief/Registry **và** evidence được linked vào Jira cùng Traceability row tương ứng. [DucVietTech/dcam](https://github.com/DucVietTech/dcam) là PM-approved repository identity; mapping này không tự cung cấp execution evidence hoặc thay đổi contract behavior.