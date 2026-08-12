# DCAM Performance Budget & Resource Constraints

**Page ID**: 50659486  
**Version**: 8  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50659486

---


# DCAM Performance Budget & Resource Constraints

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design / Performance Budget

Version

0.6

Status

Approved Pending Device POC

Approval Scope

Build 0.1 performance guardrails; numeric device evidence Pending Device POC

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead / DB Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Lead, Android Developers, QA Lead, Support

Last Updated

2026-07-20

Related Jira

None

Related Documents

[DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry), [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix), DCAM Architecture Home, DCAM Architecture Delivery Profile, DCAM Concurrency & Threading Model Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM-BDMA Data Contract, DCAM Non-functional Requirements, DCAM QA Test Strategy & Test Matrix, DCAM Device POC & Hardware Validation Report, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

Dependencies / Blockers

Device POC: obtain reference-device timing, resource, battery/thermal/storage measurements and reviewed evidence before numeric performance validation.

## 1. Purpose

Tài liệu này định nghĩa **measurable performance targets** và **resource allocation constraints** cho DCAM Android BodyCamera application.

Mục tiêu của tài liệu là biến các yêu cầu chung như “app phải nhanh”, “không ANR”, “recording ổn định”, “không corrupt file” thành các metric có thể đo được trong development, QA release và Device POC.

Phân biệt với **Non-functional Requirements**:

NFR = what quality is required.
Performance Budget = how much / how fast / measured where.
Tài liệu này không thay thế NFR. Đây là design-level budget để developer và QA biết metric nào pass/fail.

Status interpretation:

Approved Pending Device POC
    = approved provisional baseline for implementation and Build 0.1 QA
    ≠ production-validated hardware limit
## 2. Scope and Baseline Decision

Performance budget hiện tại tập trung vào các rủi ro trực tiếp của MVP và production-readiness:

Recording latency
Critical finalization latency
Memory budget
Storage I/O budget
Storage capacity / free-space budget
Startup and recovery budget
Concurrency and ANR budget
Long-running stability budget
Out of current scope:

Area

Decision

Notes

Battery target

Deferred

Chưa đặt target vì chưa có Device POC đủ tin cậy.

Thermal target

Deferred

Phụ thuộc phần cứng, firmware, ambient temperature và BodyCamera enclosure.

CPU budget

Not required for now

Chưa thêm CPU budget theo quyết định hiện tại.

AI performance

Phase 3+

Không thuộc MVP performance budget.

Cloud/network latency

Phase 2+

Chỉ thêm khi provisioning/remote config/update network flow vào implementation scope.

Important baseline:

All numeric targets are provisional until Device POC validates actual BodyCamera hardware.
Target adjustment requires measurement evidence and approved change control.
Build applicability:

Only metrics activated for the current Build Profile are release blockers.
Metric existence in this page does not automatically activate it for every build.
## 3. Assumptions and Hardware Baseline

Parameter

Assumed Value

Source / Notes

RAM

2–3 GB

Giá trị giả định cho BodyCamera mid-range; phải xác nhận bằng Device POC.

Storage type

eMMC internal + optional external microSD

Phải benchmark internal và external storage riêng.

SoC

Qualcomm / MediaTek mid-range

Phụ thuộc device model thực tế.

Android version

Android 10+ / API 29+

Theo project requirement hiện tại.

Recording resolution

1080p 30fps H.264

MVP assumption; bitrate thực tế phải đo bằng Device POC.

Typical shift duration

8–12 hours

Operational context; không đồng nghĩa continuous full-quality recording toàn thời gian.

Storage root

Internal preferred unless external storage passes POC

External microSD có thể chậm/không ổn định.

## 4. Metric Groups

### 4.1 Recording Latency Budget

Metric ID

Metric

Target

Measurement Point

Rationale

PERF-REC-001

Recording start latency

≤ 2.0s

Từ `StartRecordingCommand` enqueue đến `SdkRecordingStarted` event.

BodyCamera cần phản hồi nhanh khi officer bắt đầu incident recording.

PERF-REC-002

Recording stop latency

≤ 1.5s

Từ `StopRecordingCommand` đến `SdkRecordingStopped` event.

Tránh user confusion về việc recording đã stop hay chưa.

PERF-REC-003

Critical finalization latency

≤ 5.0s

Từ `SdkRecordingStopped` đến `BDMA_READY`.

Critical path chỉ gồm file close/validate/move, DB media_session update và final media safe cho BDMA scan.

PERF-REC-004

Image capture latency

≤ 1.5s

Từ `CaptureImageCommand` đến image file finalized.

Image capture phải nhanh và không block video recording path.

PERF-REC-005

Emergency recording start latency

≤ 3.0s

Từ emergency event đến `SdkRecordingStarted`.

Cho phép thêm thời gian cho operator resolution hoặc emergency override.

PERF-REC-006

Precheck duration

≤ 500ms

Từ `PRECHECKING` enter đến `PrecheckPassed`.

Precheck không được trở thành bottleneck trước camera start.

PERF-REC-007

Async checksum completion

Measure only / Device POC baseline

Từ `BDMA_READY` đến `CHECKSUM_READY`.

Checksum nằm ngoài critical finalization path; target cuối cùng phụ thuộc storage speed.

Notes:

Targets trên áp dụng cho warm camera path.
Cold camera start có thể chậm hơn và phải được đo riêng trong Device POC.
### 4.2 Critical Finalization and Checksum Rule

DCAM chọn baseline sau:

```
Checksum is outside the critical finalization path.
```

Critical finalization path:

SDK stop
    ↓
File closed / validated / moved to final path
    ↓
DB media_session updated
    ↓
Final media safe for BDMA scan/import
    ↓
BDMA_READY
Async checksum path:

BDMA_READY
    ↓
Checksum generated asynchronously on FileIoExecutor
    ↓
Checksum persisted / checksum file ready
    ↓
CHECKSUM_READY

State

Meaning

Rule

`BDMA_READY`

Final media tồn tại, DB đã update và mọi integrity gate bắt buộc của approved Build Profile đã pass.

Build 0.1 yêu cầu valid MD5 trước state này.

`CHECKSUM_PENDING`

MP4 đã finalized nhưng checksum chưa hoàn tất.

Không BDMA_READY/import trong Build 0.1; legacy Unverified behavior chưa có approved Build Profile mapping.

`CHECKSUM_READY`

Checksum đã tạo xong và sẵn sàng cho verification.

BDMA verify integrity trước readiness/import khi Build Profile yêu cầu.

`CHECKSUM_FAILED`

Checksum generation fail.

Không được làm mất final media; log reason code và giữ file.

Rule:

Do not delay BDMA_READY for large-file checksum generation.
Do not block new recording start because checksum is still running.
Do not run checksum inside DB transaction.
### 4.3 Memory Budget

Metric ID

Metric

Target

Condition

Rationale

PERF-MEM-001

App resident memory idle

≤ 80 MB

App running, no recording, minimal kiosk/record UI active.

Giữ headroom cho Android system và vendor services.

PERF-MEM-002

App resident memory recording

≤ 200 MB

Active video recording + basic logging + minimal DB state.

Tránh OOM/LMK khi camera HAL và encoder đang chạy.

PERF-MEM-003

Peak memory spike

≤ 400 MB

Finalization + async checksum + DB update near-concurrent.

Spike ngắn có thể chấp nhận nhưng phải bounded.

PERF-MEM-004

Memory leak threshold

≤ 5 MB/hour growth

Continuous recording hoặc long-running kiosk test.

BodyCamera chạy lâu trong ca trực; leak nhỏ cũng tích tụ thành crash.

PERF-MEM-005

Low memory survival

Foreground service priority and safe recovery

Recording active.

Recording service không được bị kill dễ dàng; nếu bị kill phải recover evidence.

Notes:

Nếu target BodyCamera chỉ có 2 GB RAM, PERF-MEM-002 cần review lại sau Device POC.
Resident memory phải đo bằng adb shell dumpsys meminfo hoặc Android Profiler theo cùng một phương pháp đo.
### 4.4 Storage I/O Budget

Metric ID

Metric

Target

Condition

Rationale

PERF-IO-001

Sustained write speed for recording

≥ 15 MB/s

1080p 30fps H.264 recording target.

Cần headroom cho burst, metadata và device variability.

PERF-IO-002

File move/rename latency

≤ 500ms

Same filesystem move.

Finalization critical path cần nhanh.

PERF-IO-003

MD5/SHA-256 checksum generation

Measure only / Device POC baseline

FileIoExecutor; after `BDMA_READY`.

Checksum không nằm trong critical finalization path.

PERF-IO-004

DB transaction duration

≤ 100ms

Single `media_session` update.

Align với Concurrency & Threading Model: transaction ngắn, không giữ lock lâu.

PERF-IO-005

CSON write duration

≤ 200ms

`dcam_config.cson` update.

Không được block recording path.

PERF-IO-006

Storage health check

≤ 300ms

Startup validation.

Không delay READY state quá lâu.

Notes:

External microSD phải benchmark riêng.
Nếu external storage không đạt sustained write speed, policy nên chuyển recording sang internal storage hoặc giảm bitrate.
### 4.5 Storage Capacity / Free-space Budget

Storage capacity budget là bắt buộc vì recording có thể fail hoặc corrupt nếu app start recording khi dung lượng không đủ.

Metric ID

Metric

Target

Condition

Rationale

PERF-STOR-001

Minimum free space to start recording

Estimated 30-min recording size + 500 MB safety margin

Before recording start.

Tránh start recording rồi fail giữa chừng vì thiếu dung lượng.

PERF-STOR-002

Critical free space during recording

Enough for safe stop/finalization

Active recording.

Khi gần hết dung lượng phải stop/finalize an toàn, không corrupt media.

PERF-STOR-003

Minimum free space for finalization

≥ 500 MB or device-specific value

Before moving temp/staging to final.

Tránh finalization fail vì không đủ headroom.

PERF-STOR-004

Per-hour storage estimate

Calculated from actual bitrate

Device POC / QA.

Dùng để hiển thị/ước lượng recording duration còn lại.

PERF-STOR-005

Storage full handling

No file corruption

Storage full / near-full test.

Preserve evidence candidate và log reason code.

PERF-STOR-006

Internal vs external storage policy

Device POC decision

Internal eMMC vs external microSD benchmark.

Nếu external SD chậm/không ổn định thì recording dùng internal only.

Formula:

estimated_file_size_mb = bitrate_mbps * duration_seconds / 8
minimum_start_free_space_mb = estimated_30min_recording_size_mb + 500MB safety_margin
Example:

1080p 30fps at 12 Mbps:
12 * 1800 / 8 = 2700 MB

Minimum free space to start 30-min recording:
2700 MB + 500 MB = 3200 MB
Rule:

Recording precheck must verify free-space budget before camera start.
Storage low event during recording must route through State Coordinator.
Storage full must trigger safe stop/finalization or recovery path.
### 4.6 Startup and Recovery Budget

Metric ID

Metric

Target

Condition

Rationale

PERF-BOOT-001

Cold boot to recording ready

≤ 8s

Boot complete → app `READY`.

Officer cần có thể record nhanh sau khi bật thiết bị.

PERF-BOOT-002

App restart to recording ready

≤ 4s

Process kill/restart → `READY`.

Crash/process recovery phải nhanh.

PERF-BOOT-003

Recovery scan duration

≤ 3s

Scan temp/staging/DB cho interrupted sessions.

Không block startup quá lâu.

PERF-BOOT-004

Login screen display

≤ 1s after READY

UI render after required checks.

UX phải phản hồi nhanh.

PERF-BOOT-005

Identity restore local path

≤ 500ms

Read serial/config/local identity cache.

Identity local restore không nên block runtime lâu.

Notes:

```
Kiosk policy verification, remote identity restore, provisioning polling và update check không được block MVP recording readiness nếu không phải P0 dependency.
```

### 4.7 Concurrency and ANR Budget

Metric ID

Metric

Target

Condition

Rationale

PERF-ANR-001

MainThread block duration

< 500ms

Any single operation.

Giữ safety margin lớn so với Android ANR threshold.

PERF-ANR-002

State Coordinator queue latency

≤ 50ms

Command enqueue → processing start.

Single-threaded coordinator phải responsive.

PERF-ANR-003

Camera callback processing

≤ 100ms

Callback → event emitted to coordinator.

Không block camera callback thread.

PERF-ANR-004

UI state update latency

≤ 100ms

State change → UI reflects.

User thấy recording state thay đổi kịp thời.

PERF-ANR-005

Executor queue depth

≤ 10 pending tasks

Any single executor.

Phát hiện backpressure hoặc bottleneck.

PERF-ANR-006

Camera timeout

≤ 5s

Camera open/start/stop.

Map thành `CAMERA_TIMEOUT` nếu exceed.

PERF-ANR-007

DB busy retry

≤ 3 retries, ≤ 1s total

`SQLITE_BUSY` scenario.

Không loop vô hạn và không block state machine lâu.

Rule:

Concurrency metrics must be logged through safe [PERF] or [THREAD] logs.
No MainThread disk/network/DB/camera blocking operation is allowed in release candidate.
### 4.8 Long-running Stability Budget

Metric ID

Metric

Target

Condition

Rationale

PERF-STAB-001

Continuous recording stability

≥ 4 hours without crash

1080p, logging on, approved degradation allowed if implemented.

BodyCamera cần chịu được session dài mà không crash/corrupt file.

PERF-STAB-002

App uptime kiosk

≥ 24 hours without restart

Idle + intermittent recording.

Thiết bị có thể bật lâu giữa các ca trực.

PERF-STAB-003

Recording sessions per day

≥ 50 sessions without degradation

Start/stop cycles.

Mô phỏng usage tuần tra thực tế.

PERF-STAB-004

File descriptor leak

0 leaked FDs after finalization

Per completed session.

FD leak sẽ gây crash sau nhiều phiên recording.

PERF-STAB-005

App-owned steady-state thread count

≤ 15 threads

After warmup.

Chỉ tính thread do DCAM tạo/quản lý.

PERF-STAB-006

Total process thread count

Measure and document

Device POC.

CameraX/vendor SDK/Android runtime có thể tạo thread riêng; cần ghi nhận thực tế.

PERF-STAB-007

DB size growth rate

≤ 1 MB/day excluding media references

Normal operation.

DB không được grow unbounded.

Notes:

PERF-STAB-001 cho phép approved degradation nếu sau này có degradation policy.
Metric này không có nghĩa phải duy trì full quality 1080p trong mọi điều kiện môi trường/phần cứng.
## 5. MVP vs Full Performance Budget

Metric Group

MVP Required / Phase 1

Phase 2+ / Production Candidate

Recording Latency

PERF-REC-001, 002, 003, 004, 006

All

Critical Finalization / Checksum

BDMA_READY critical path only

Add checksum completion and integrity reporting

Memory

PERF-MEM-001, 002, 004

All

Storage I/O

PERF-IO-001, 002, 004

All

Storage Capacity / Free-space

PERF-STOR-001, 002, 005

All

Startup / Recovery

PERF-BOOT-001, 002, 004

All

Concurrency / ANR

PERF-ANR-001, 002, 003, 006, 007

All

Long-running Stability

PERF-STAB-001, 003, 004, 005

All

MVP enforcement rule:

MVP PR/release must pass metrics activated for Build 0.1 by the Applicability Matrix and QA Matrix.
Full metric enforcement begins when the corresponding feature enters Phase 2+ or Production Candidate scope.
Device POC may adjust provisional values through approved evidence-based change control.
## 6. Measurement and Validation Approach

### 6.1 Logging Format

Use safe `[PERF]` logs:

[PERF] recording_start_latency_ms=1850
[PERF] recording_stop_latency_ms=900
[PERF] critical_finalization_latency_ms=3200
[PERF] checksum_completion_latency_ms=8200
[PERF] memory_resident_mb=145
[PERF] db_transaction_ms=45
[PERF] boot_to_ready_ms=6500
[PERF] camera_open_ms=1200
[PERF] executor_queue_depth_state=3
[PERF] app_owned_thread_count=9
[PERF] total_process_thread_count=31
[PERF] free_space_mb=12800
[PERF] estimated_30min_file_size_mb=2700
[PERF] minimum_start_free_space_mb=3200
Do not log:

credentials
tokens
maintenance password
raw Android system identifier
raw media content
raw AI frame
biometric samples
encryption keys
sensitive config payload
### 6.2 Measurement Phases

Phase

Method

Owner

Frequency

Device POC

Manual profiling, Android Profiler, adb tools, custom timing logs and storage benchmark.

Android Lead

Once per device model/firmware baseline.

MVP Sprint

Automated `[PERF]` log category and manual spot check.

Dev + QA

Every sprint.

QA Release

Performance test suite for applicable MVP subset.

QA Lead

Every release candidate.

Production Pilot

Field telemetry if approved.

Tech Lead

Continuous / pilot-only.

### 6.3 Tooling

Tool

Purpose

Android Profiler

Memory and runtime profiling during development.

`adb shell dumpsys meminfo`

Resident memory measurement.

`adb shell top` / `pidstat` if available

Runtime process observation during Device POC.

Custom `[PERF]` log parser

Latency and budget measurement automation.

StrictMode debug build

Detect disk/network on MainThread.

LeakCanary debug build only

Memory leak detection.

`lsof` / `/proc/<pid>/fd` if available

File descriptor leak check.

Storage benchmark script

Internal vs external sustained write/read measurement.

## 7. Failure Response Matrix

A violation blocks a release only when the metric is applicable to the active Build Profile. Device POC adjustment must be approved before it changes a pass/fail target.

Metric Violation

Severity

Response

PERF-REC-001 > 2.0s

Critical

Block applicable release; investigate camera init, precheck or command queue bottleneck.

PERF-REC-003 > 5.0s

Critical

Block applicable release if critical finalization path exceeds target; checksum is excluded.

PERF-MEM-004 > 5 MB/hour

Critical

Block applicable release; run leak analysis and long-running test.

PERF-ANR-001 > 500ms

High

Block applicable release candidate; identify MainThread violation.

PERF-ANR-007 exceeds retry/timeout

High

Fix DB busy handling; no infinite loop allowed.

PERF-STOR-001 not enforced

Critical

Block recording release; start recording without free-space budget is unsafe.

PERF-STOR-005 fails

Critical

Block recording release; storage full must not corrupt evidence.

PERF-STAB-001 < 4 hours

Critical

Block applicable release candidate unless an approved Device POC adjustment exists.

PERF-STAB-004 > 0 FD leak

High

Fix before next applicable release; add regression test.

PERF-STAB-005 > 15 app-owned threads

Medium

Investigate thread ownership; fix thread leak/unbounded executor creation.

PERF-BOOT-001 > 8s

Medium

Investigate startup sequence; defer non-critical init or submit evidence-based adjustment.

PERF-IO-001 < 15 MB/s

Medium

Treat as hardware/storage limitation; adjust bitrate or storage policy after approval.

## 8. Device POC Dependency and Adjustment Rules

After Device POC completes, all numeric targets must be reviewed against actual hardware measurements.

Rule

Description

POC-ADJ-001

Nếu hardware underperforms, target có thể adjust nhưng phải có measurement evidence.

POC-ADJ-002

Nếu camera open/start chậm hơn target trên firmware cụ thể, thêm device-specific note và mitigation.

POC-ADJ-003

Nếu external SD không đạt sustained write speed, quyết định policy `internal storage only for recording` hoặc giảm bitrate.

POC-ADJ-004

Nếu total process thread count cao do vendor SDK/CameraX, ghi nhận trong Device POC; chỉ enforce app-owned thread count.

POC-ADJ-005

Nếu checksum quá chậm, giữ checksum async và không block `BDMA_READY`.

POC-ADJ-006

Target adjustment cần Technical Note hoặc ADR nếu thay đổi ảnh hưởng release acceptance.

POC-ADJ-007

Sau khi POC chốt target model/firmware, đổi status sang `Approved for Build <profile>` hoặc `Production Approved` chỉ khi approval gate tương ứng hoàn tất.

## 9. Cross-reference Integration

Document

Integration Action

DCAM Architecture Home

Include in reading order and reference registry-qualified status.

DCAM Non-functional Requirements

Detailed measurable metrics are defined in this document.

DCAM Concurrency & Threading Model Design

Align PERF-ANR metrics with executor/queue/threading rules.

DCAM Recording & Capture Design

Reference recording latency, critical finalization latency and async checksum rule.

DCAM Storage Design

Reference storage I/O and storage capacity/free-space budget.

DCAM SQLite Database Design

Reference DB transaction duration, DB busy retry and no file/checksum inside transaction.

DCAM QA Test Strategy & Test Matrix

Test only the metric subset applicable to the active Build Profile.

DCAM Device POC & Hardware Validation Report

Validate or adjust these targets using target hardware evidence.

DCAM Requirement–Design–Test Traceability Matrix

Map metric IDs to QA IDs, Jira items and evidence.

DCAM Document Status Registry

Maintain qualified approval status and approval scope.

## 10. Revision History

Version

Date

Status

Changes

0.1

2026-07-09

Draft

Initial proposed performance and resource budget.

0.2

2026-07-09

Draft / Review Baseline

Async checksum outside critical finalization; defer battery/thermal; add thread and free-space budgets.

0.3

2026-07-10

Approved Pending Device POC

Align metadata, approval scope, build-scoped enforcement and evidence-based adjustment rules.

0.4

2026-07-13

Approved Pending Device POC

Đồng bộ Build 0.1 checksum/readiness override theo DEC-03.

0.5

2026-07-13

Approved Pending Device POC

Làm rõ legacy Unverified behavior chưa có approved Build Profile mapping.

## 11. Practical Conclusion

DCAM cần performance budget để tránh tranh luận cảm tính về “nhanh/chậm/ổn định”.

Current baseline:

Status = Approved Pending Device POC.
Checksum is async and outside critical finalization path.
Battery and thermal targets are deferred until Device POC.
App-owned steady-state threads ≤ 15.
Total process thread count must be measured during Device POC.
CPU budget is not required for now.
Storage capacity / free-space budget is required.
Only build-applicable metrics are release blockers.
MVP phải đo và enforce các metric liên quan trực tiếp tới:

Recording start/stop
Critical finalization to BDMA_READY
MainThread / State Coordinator / Camera callback latency
DB transaction and DB busy retry
Storage sustained write and free-space safety
Memory leak and long-running recording stability
Production interpretation requires target-model/firmware Device POC evidence and the appropriate qualified approval status.

## 17. Build 0.1 Checksum and Storage Performance Override

PERF-REC-003 đo stop-to-finalized/checksum-pending; không dùng BDMA_READY làm endpoint trước checksum.

PERF-REC-007 đo async MD5 completion đến BDMA_READY.

MP4 MD5 success là release requirement; không được bỏ qua để đạt readiness latency.

Build 0.1 chỉ dùng MD5, không đổi sang SHA-256.

Build 0.1 sử dụng Internal storage only.

Không tự đặt MD5 latency/resource threshold; số liệu phải lấy từ NCC-036V Device POC.

Mọi statement cũ cho phép checksum không chặn BDMA_READY không áp dụng cho Build 0.1. Legacy `missing MD5 → Unverified import` hiện không được gán cho Build 0.2, Build 0.3 hoặc Pilot/Shipment và chỉ có thể được kích hoạt bằng approved named-profile change.