# DCAM BDMA Integration Technical Design

**Page ID**: 48595030  
**Version**: 12  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595030

---


# DCAM BDMA Integration Technical Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

Draft 0.10

Status

Draft

Approval Scope

Build 0.1 integration profile prepared; physical path/schema and Technical Review remain open

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, BDMA Developers, QA

Last Updated

2026-07-18

Related Jira

[DCAM-8](https://ducviet.atlassian.net/browse/DCAM-8) — BDMA Sample Import; detailed mapping thuộc Traceability Matrix

Dependencies / Blockers

Technical Review; logical-to-physical ADB path mapping; Device POC; QA/Jira/PR/build/test evidence.

Related Documents

DCAM-BDMA Data Contract, 06 - BDMA Integration Requirements, 08 - DCAM-BDMA Integration Boundary, DCAM Storage Design, DCAM SQLite Database Design, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Tài liệu này mô tả implementation direction cho BDMA import/write-back flow.

Tài liệu này không copy lại Data Contract. Concrete rules cho media naming, folder structure, `_IMP`, `_enc`, `.md5` for MP4 only, app/contract compatibility, import result baseline và cleanup baseline thuộc **DCAM-BDMA Data Contract**.

## 2. Authoritative References

Topic

Authoritative Document

Local Summary

Data/file/media/app contract

DCAM-BDMA Data Contract

Source of truth cho file/folder/naming/MD5/cleanup/app-contract behavior.

Requirement boundary

06 - BDMA Integration Requirements

Requirement-level integration intent.

Architecture boundary

08 - DCAM-BDMA Integration Boundary

ADB-based và BDMA-initiated boundary.

Storage implementation

DCAM Storage Design

Implement Data Contract storage behavior.

DB schema/write-back

DCAM SQLite Database Design

Định nghĩa schema/state compatibility cho DB write-back.

## 3. BDMA Implementation Flow

Detect Android device through ADB
        ↓
Locate DCAM storage roots according to Data Contract
        ↓
Read device config / database / logs according to Data Contract
        ↓
Identify app and contract metadata:
    app_code
    app_package_name
    app_version_code
    dcam_data_contract_version
    media_contract_version
    encoder_contract_version
        ↓
Check BDMA built-in compatibility table
        ↓
Scan finalized media locations according to Data Contract
        ↓
Build import candidate list
        ↓
Apply Data Contract verification/import rules
        ↓
Write import state if allowed by schema/contract
        ↓
Apply cleanup policy if allowed by Data Contract
## 4. App / Contract Recognition Rule

BDMA must identify what Android app and contract version it is communicating with before applying import logic.

Metadata

Purpose

`app_code`

Identify DCAM app family, e.g. `DCAM_ANDROID`.

`app_package_name`

Verify Android package identity if available.

`app_version_code` / `app_version_name`

Check app compatibility.

`dcam_data_contract_version`

Check overall DCAM-BDMA data contract compatibility.

`media_contract_version`

Check media folder/naming/checksum/import/cleanup compatibility.

`encoder_contract_version`

Check fixed DCAM encoder behavior compatibility.

BDMA must not depend on a cloud-provided decoder profile.

Not used:

bdma_decoder_profile_id
decoder_profile_id
dynamic_decoder_profile
BDMA should use an internal compatibility table such as:

Supported:
- app_code = DCAM_ANDROID
- dcam_data_contract_version >= supported_minimum
- media_contract_version >= supported_minimum
- encoder_contract_version = FIXED_V1
If unsupported, BDMA should block import or show a compatibility warning and must not modify source media.

## 5. Device Information Usage

BDMA may read device information from `dcam_config.cson` and/or `dcam.db` for display, support and import context.

Field

Usage

`serial_number`

Device display/support identifier; not primary key.

`owner_name`

Owner/customer/agency display.

`manufacture_date`

Device manufacture date; expected format `YYYY-MM-DD`.

`device_model` / `firmware_version`

Device compatibility/support display.

BDMA must not treat `serial_number`, `owner_name` or `manufacture_date` as a primary identity key. Device cloud identity remains `dcam_cloud_device_id` where available.

## 6. Local Implementation Notes

Area

Direction

Scan Order

Dùng Data Contract làm source of truth.

Verification

Dùng Data Contract làm source of truth.

Cleanup

Dùng Data Contract làm source of truth.

App Recognition

Dùng app/data/media/encoder contract metadata và BDMA built-in compatibility table.

Decoder Profile

Không dùng `bdma_decoder_profile_id`.

Device Info Display

Hiển thị serial, owner name, manufacture date nếu có.

DB Write-back

Dùng Data Contract + SQLite Database Design.

Error Handling

Map implementation errors sang Data Contract result categories.

## 7. Build 0.1 Approval Scope Candidate

Candidate scope này chỉ áp dụng cho **DCAM MVP Internal Build 0.1 – Working Recording Slice** và chưa thay đổi `Status = Draft` trước Technical Review.

Area

Build 0.1 Direction

Source / Guardrail

Scan roots / path mapping

Scan logical final-media roots do Data Contract định nghĩa; BDMA Design sở hữu scan mapping, Storage Design sở hữu Android root resolution.

Không chọn physical ADB path trước Device POC.

Final-media-only

Chỉ import finalized candidate; không import partial/in-progress artifact.

Data Contract + `BDMA_READY` semantics.

Temp ignore

Bỏ qua Temp/staging/recovery candidate chưa được contract cho phép.

Data Contract; QA-BDMA-005.

MP4 MD5 pass

Khi sidecar tồn tại và match, import/cleanup chỉ theo approved cleanup policy.

QA-BDMA-001.

MP4 MD5 missing

Build 0.1 block import/evidence/release. Legacy Unverified behavior chưa có approved Build Profile mapping và không thuộc approval candidate này.

QA-BDMA-002.

MP4 MD5 mismatch

Không import, không cleanup/delete source; trả controlled integrity failure.

QA-BDMA-003.

Image no-MD5

Image final artifact không yêu cầu `.md5`; không áp dụng MP4 missing-checksum failure.

QA-BDMA-004.

Protected artifact cleanup

Không xóa `dcam_config.cson`, `dcam.db`, `logs.txt`, identity hoặc recovery artifacts.

Data Contract; QA-BDMA-005.

ADB disconnect / permission failure

Trả controlled/retryable failure; không ghi/xóa source artifact khi operation chưa xác nhận.

QA-BDMA-006.

### 7.1 Excluded from Build 0.1 Approval Candidate

full DB write-back
user/cloud synchronization
encryption/decryption implementation
physical storage/ADB path values without Device POC
production cleanup policy beyond the approved Data Contract
### 7.2 Approval Gate

`Approved Provisional Baseline` chỉ được đề xuất sau khi Tech Lead/BDMA Lead/QA Lead xác nhận scope trên, path ownership và QA-BDMA-003…006. [DucVietTech/dcam](https://github.com/DucVietTech/dcam) đã được PM-approved như repository identity; GitHub evidence chỉ được dùng khi PR/build/test evidence được linked vào Jira và Traceability Matrix.

## 8. Practical Conclusion

BDMA Technical Design owns implementation flow.
DCAM-BDMA Data Contract owns the actual data/file/import rules.
BDMA nhận dạng app bằng app/data/media/encoder contract metadata.
BDMA không dùng bdma_decoder_profile_id.
BDMA có thể hiển thị owner_name và manufacture_date như device information.
Không duplicate full contract tables tại đây.
## 13. Proposed Build 0.1 Approval Scope

Integration Rule

Build 0.1 Baseline

Scan Root

Approved logical Internal final-media root; physical/ADB mapping Pending Device POC

Path Mapping

Data Contract owns logical mapping; Storage Design owns Android physical mapping; POC validates NCC-036V/ADB

Final-media-only

Required

Temp / Cache

Ignore

MP4 MD5 Pass

Import eligible

MP4 MD5 Missing

Block import/evidence/release

MP4 MD5 Mismatch

Block import/evidence/release

MP4 MD5 Generation Failure

MP4 preserved; no BDMA_READY/import

Image no-MD5

Giữ behavior hiện có; image không bị yêu cầu MD5 bởi DEC-03

Protected Artifacts

Không cleanup dcam.db, dcam_config.cson, logs.txt, Temp/recovery, failed artifact hoặc historical legacy-unverified artifact; legacy Unverified import không active trong approved profile hiện tại

ADB Disconnect / Permission Failure

Abort/defer safely; không delete source/protected artifact

Filename Parsing

Phân tích DEVICE_TOKEN 6–10 ký tự và OPERATOR_TOKEN đúng 6 ký tự theo Data Contract; Build 0.1 dùng OPERATOR_TOKEN = B01OPR; không silent truncation hoặc underscore normalization.

Legacy Filename Compatibility

Chỉ áp dụng theo declared media_contract_version hoặc approved named profile; không suy diễn compatibility chỉ từ filename shape.

Timestamp / Same-second Collision

Pending Technical Review; page này không tự chọn timezone hoặc collision behavior.

Approval scope đề xuất chỉ cho Build 0.1. Page tiếp tục Draft đến khi Technical Review xác nhận physical path, exact error/state mapping và Data Contract consistency.