# DCAM Storage Design

**Page ID**: 48496699  
**Version**: 11  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496699

---


# DCAM Storage Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

0.10

Status

Approved Pending Device POC

Approval Scope

Build 0.1 Internal-only mechanics; physical path/scoped storage/ADB evidence Pending Device POC

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / BDMA Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, BDMA Team, QA, Support

Last Updated

2026-07-13

Related Jira

Not linked

Dependencies / Blockers

Active storage profile decision; Device POC cho physical path, scoped storage, ADB visibility và atomic move.

Related Documents

DCAM-BDMA Data Contract, DCAM Performance Budget & Resource Constraints, DCAM Recording & Capture Design, DCAM SQLite Database Design, DCAM Android Operation Design, DCAM Security & Encryption Design, DCAM QA Test Strategy & Test Matrix, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này định nghĩa Android-side storage mechanics: root resolution, temp/final files, storage modes, free-space guard, finalization, BDMA readiness và recovery.

Data Contract owns external file/folder/naming/checksum/import contract. Performance Budget owns measurable storage thresholds and latency targets.

## 2. Components

Component

Responsibility

`StorageRootResolver`

Resolve logical Internal/External root thành physical path.

`StorageHealthChecker`

Check mount, writable state, free space and visibility.

`MediaPathBuilder`

Build temp/staging/final path.

`TempFileManager`

Manage in-progress files.

`FinalizationManager`

Close, validate and move/rename final media.

`FileIntegrityService`

Generate checksum according to Data Contract/policy.

`BdmaReadinessMarker`

Mark `BDMA_READY` after all required conditions pass.

`StorageRecoveryScanner`

Reconcile interrupted DB/file state.

### 2.1 Logical-to-Physical Path Ownership

Concern

Authoritative Owner

Local Rule

Logical roots, folders, naming and protected artifacts

DCAM-BDMA Data Contract

Không copy hoặc tự đổi logical contract tại đây.

Android logical-root resolution mechanics

DCAM Storage Design

`StorageRootResolver` map logical root sang device path theo active profile.

BDMA logical-root-to-ADB scan mapping

DCAM BDMA Integration Technical Design

BDMA chỉ scan approved logical final-media roots.

Actual physical paths, scoped-storage behavior and ADB visibility

DCAM Device POC & Hardware Validation Report

Phải có evidence trên target device/model/firmware.

Active storage mode/profile

Approved Product/Architecture decision + Release & Build Applicability Matrix

Không tự suy ra từ tên `Internal Build 0.1`.

Không chọn hoặc publish physical path trong changeset này.

## 3. Storage Modes

Mode

Before Recording

Failure Direction

Internal

Validate internal root.

Stop/finalize safely if unavailable during session.

External

Validate external root.

No silent fallback unless approved policy allows it.

Auto

Prefer External, fallback Internal before recording.

No mid-file switch unless vendor SDK proves it safe.

## 4. Temp, Final and BDMA Readiness

State

Location

BDMA Candidate

`IN_PROGRESS`

Temp/staging

No

`FINALIZING`

Temp/staging

No

`FINALIZED`

Approved Media folder

Yes after DB/readiness checks

`RECOVERY_REQUIRED`

Preserved temp/recovery path

No

`RECOVERY_FAILED`

Preserved diagnostic area

No unless future contract defines otherwise

`BDMA_READY` requires:

final file exists
file handle closed and size stable
DB record updated
no pending recovery flag
required metadata/encryption/checksum step completed or explicitly classified
## 5. Free-space Baseline

Free-space thresholds are no longer all `TBD`. Current measurable baseline comes from **DCAM Performance Budget & Resource Constraints**.

Threshold

Current Baseline

Status

`MIN_START_FREE_SPACE`

Estimated size of 30-minute recording + `500 MB` safety margin.

Defined provisional budget

`CRITICAL_ACTIVE_FREE_SPACE`

Enough headroom to stop safely and complete critical finalization.

Defined behavior; device-specific value POC-calibrated

`RESERVED_FINALIZATION_SPACE`

At least `500 MB` or approved device-specific value.

Defined provisional budget

`WARNING_FREE_SPACE`

Warning above start/critical thresholds.

Exact value TBD / Product + POC

`MIN_RECOVERY_SPACE`

Enough for recovery/finalization support operations.

Exact value TBD / POC

Formula direction:

estimated_recording_bytes = bitrate_bits_per_second / 8 × duration_seconds
MIN_START_FREE_SPACE = estimated_30_minute_bytes + 500 MB
Device POC may adjust values with recorded evidence; it must not remove the safety-margin principle.

## 6. Finalization Budget

Current performance baseline:

Metric

Target

Critical finalization latency

`≤ 5.0s`

Recording stop latency

`≤ 1.5s`

Checksum

Must not block critical `BDMA_READY` path when asynchronous policy is allowed.

Finalization flow:

close temp file
validate stable file
complete critical metadata steps
move/rename to final path
update DB
mark BDMA_READY
run non-critical checksum/export work asynchronously when policy allows
Exact retry count/backoff and device/storage-specific atomic-move behavior remain TBD.

## 7. Recovery

Scenario

Behavior

Temp file + active DB state after crash

Validate and finalize if safe; otherwise preserve and mark recovery.

Final file exists but DB missing

Reconcile DB using recovered flag.

DB says ready but file missing

Mark missing source and log diagnostics.

Missing checksum

Build 0.1 giữ blocked/recoverable, tạo MD5 nếu policy cho phép và không publish BDMA_READY/import trước valid MD5. Legacy Unverified behavior chưa có approved Build Profile mapping.

External removed

Preserve candidate and stop/finalize safely.

Interrupted move/duplicate

Apply deterministic recovery rule after implementation decision.

When uncertain, preserve evidence-like artifacts.

## 8. Performance and QA References

Storage QA coverage is no longer an unspecified future matrix. **DCAM QA Test Strategy & Test Matrix** already covers:

minimum free-space precheck
near-full/full storage handling
safe stop/finalization
critical finalization latency
storage I/O
external removal
reboot/crash recovery
BDMA readiness
Detailed device/model cases remain part of Device POC and execution test plans.

## 9. Resolved and Remaining Decisions

Item

Status

Minimum start free-space formula

Defined in Performance Budget

Safety margin

Defined: `500 MB` provisional baseline

Critical finalization headroom

Defined provisional baseline

Critical finalization latency

Defined: `≤ 5.0s`

Storage full behavior

Defined: safe stop/finalize, no corruption

Physical internal/external paths

TBD / Device POC

Scoped storage and ADB visibility

TBD / Device POC

External-mode fallback policy

TBD / Product + Storage Design

Mid-recording path switch

Unsupported by default; POC needed for any exception

Warning/recovery exact thresholds

TBD / POC

Atomic move support

TBD / target storage POC

Temp/recovery naming and retention

TBD / Implementation + Support

Duplicate move recovery rule

TBD / Implementation

Recovered-media BDMA flag

TBD / Data Contract decision

## 10. Practical Conclusion

Storage thresholds and finalization budget are no longer fully undefined.
Performance Budget supplies the current measurable baseline.
Storage Design retains only physical-path, device-specific and recovery-policy decisions as TBD.
## 11. Build 0.1 Authoritative Storage Profile

Section này override Auto/External/fallback mechanics đối với Build 0.1.

Design Item

Build 0.1 Behavior

Active Root

Logical Internal DCAM Media Root

External / Auto

Not Applicable

Pre-check

Nếu không đạt thì không start recording

Runtime Failure

Safe-stop và finalize MP4 nếu còn khả năng

Finalization

Close/finalize MP4 trước khi bắt đầu async MD5

Readiness

Chỉ publish BDMA_READY sau valid MD5

MD5 Failure

Giữ MP4; persist Checksum Pending/Failed; log; không import

Recovery

Reconcile file/DB/checksum state; không cleanup protected artifact

Logical-to-physical Internal path, scoped-storage behavior và ADB visibility vẫn Pending Device POC. Exact checksum state names và recovery schema cần Technical Review.

Legacy `missing MD5 → Unverified import` không thuộc Build 0.1 và hiện không được gán cho Build 0.2, Build 0.3 hoặc Pilot/Shipment.