# DCAM Concurrency & Threading Model Design

**Page ID**: 50725012  
**Version**: 6  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50725012

---


# DCAM Concurrency & Threading Model Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

Draft 0.5

Status

Draft

Approval Scope

Build 0.1 required subset; exact executor sizing/queue policy Pending Technical Review và Device POC

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / DB Reviewer / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, QA, Support, BDMA Team

Last Updated

2026-07-16

Related Jira

Not linked

Dependencies / Blockers

Tech Lead/Android Lead/DB Reviewer/QA Lead Technical Review; Jira/PR/build/test evidence; camera provider Device POC.

Related Documents

DCAM Architecture Home, DCAM Architecture Delivery Profile, DCAM Performance Budget & Resource Constraints, DCAM Android Development Standard, DCAM State Machine Design, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Self Update Design, DCAM Android Device Owner & Kiosk Policy Design, 09 - System Settings Requirements, DCAM-BDMA Data Contract, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Tài liệu này định nghĩa **Concurrency & Threading Model** cho DCAM Android.

Mục tiêu là đảm bảo các luồng quan trọng như `Recording`, `Camera callback`, `File I/O`, `SQLite write`, `State Machine coordination`, `Policy apply`, `Remote Config apply` và `Self Update` không gây **ANR**, **race condition**, **deadlock**, mất dữ liệu, corrupt media file hoặc trạng thái DB sai.

Tài liệu này là source of truth cho:

Execution lane của từng nhóm runtime.

MainThread boundary.

Recording pipeline thread model.

Single-threaded state coordination.

Camera callback contract.

File finalization và DB write boundary.

Safe window cho policy/config/update.

Deadlock/race prevention rule.

MVP threading profile và QA test matrix.

Performance budget alignment cho MainThread block, State Coordinator queue latency, Camera callback processing, DB busy retry và executor queue depth.

## 2. Technology Baseline

DCAM hiện tại theo hướng **Java-first**, vì vậy baseline threading không dùng Kotlin Coroutines hoặc StateFlow làm yêu cầu bắt buộc cho MVP.

Area

Decision

Notes

Language baseline

Java-first

Team có nền tảng Java/Desktop; tránh thêm learning curve không cần thiết trong MVP.

Async baseline

`ExecutorService` + `HandlerThread` + Android `Handler`

Dễ kiểm soát, dễ debug và phù hợp với Java-first Android implementation.

UI state emission

`LiveData` hoặc main-thread-safe callback

Không yêu cầu `StateFlow` trong MVP.

Reactive framework

Không dùng RxJava làm baseline MVP

RxJava chỉ cân nhắc nếu sau này có nhu cầu stream phức tạp và team đã đủ năng lực maintain.

State coordination

Single-threaded command/state coordinator

Mọi command/event ảnh hưởng runtime state phải đi qua một queue tuần tự.

Cross-executor call

Async event/result với timeout

Không block đồng bộ giữa các executor khi đang giữ state hoặc transaction.

Baseline thực thi:

```
Java ExecutorService + HandlerThread + Main Handler / LiveData
```

Không dùng pattern:

synchronized everywhere
blocking wait between executors
DB transaction waiting for camera callback
camera callback writing DB directly
MainThread waiting for long-running work
## 3. Execution Lane Overview

DCAM runtime chia thành các **execution lanes** rõ ràng. Mỗi lane có trách nhiệm riêng và không được tự ý mutate state của lane khác.

MainThread
    ↓ UI command
RecordingCommandExecutor / StateCoordinator
    ↓ async command
CameraExecutor / CameraHandlerThread
    ↓ callback event
RecordingCommandExecutor / StateCoordinator
    ↓ finalization command
FileIoExecutor
    ↓ DB update command
DbExecutor
    ↓ result event
RecordingCommandExecutor / StateCoordinator
    ↓ UI state
MainThread

Execution Lane

Responsibility

Not Allowed

`MainThread`

Render UI, observe state, lifecycle callback nhẹ, permission UI, navigation và hiển thị lỗi thân thiện.

Không chạy camera start/stop blocking, DB write, checksum, file move, network request dài hoặc policy apply dài.

`RecordingCommandExecutor`

Nhận command/event, quyết định state transition, serialize recording/capture/finalization state và phát lệnh async sang lane khác.

Không thực hiện file I/O lớn, DB query lâu, checksum hoặc block vô hạn chờ callback.

`CameraExecutor` / `CameraHandlerThread`

Open camera, start/stop recording SDK, nhận callback từ CameraX/Camera2/vendor SDK.

Không ghi DB trực tiếp, không update UI trực tiếp, không mutate state machine trực tiếp.

`FileIoExecutor`

Tạo temp/staging/final path, move/rename file, checksum, CSON/log file operation và finalization I/O.

Không update UI trực tiếp, không giữ DB transaction khi đang làm file operation lớn.

`DbExecutor`

SQLite transaction, media session update, operator snapshot, identity/config cache, BDMA_READY và short DB reads/writes.

Không chạy camera/file operation, không block MainThread, không chờ camera callback khi đang trong transaction.

`PolicyExecutor`

Apply/verify Device Owner, Lock Task, User Restrictions và policy snapshot nếu feature in scope.

Không chạy khi runtime guard báo unsafe; không mutate recording state trực tiếp.

`NetworkExecutor`

Provisioning lookup, remote config fetch, update manifest/download metadata và server communication.

Không apply config/policy trực tiếp; chỉ emit result về coordinator.

`UpdateExecutor`

APK validation/install coordination theo Self Update Design khi feature in scope.

Không chạy install khi recording/finalizing/recovery unsafe.

## 4. MainThread Boundary

`MainThread` chỉ dùng cho UI và Android lifecycle work nhẹ. Mọi tác vụ có khả năng block phải được chuyển sang executor phù hợp.

Task

MainThread Rule

UI render

Được phép chạy trên MainThread.

Button click

Chỉ tạo command và enqueue vào `RecordingCommandExecutor`.

Permission dialog

Được phép vì thuộc Android UI flow.

Camera open/start/stop

Không chạy trực tiếp trên MainThread.

File move/checksum

Không chạy trên MainThread.

SQLite write/read dài

Không chạy trên MainThread.

Remote config fetch

Không chạy trên MainThread.

Policy apply dài

Không block MainThread; chỉ phần API yêu cầu main-thread-safe mới điều phối qua controller.

UI state update

Chỉ update từ result đã được post về MainThread.

Rule:

MainThread may request work.
MainThread must not perform long-running work.
MainThread must not wait synchronously for background result.
Performance target reference:

```
PERF-ANR-001 MainThread block duration < 500ms
```

## 5. Single-threaded State Coordinator

DCAM phải dùng mô hình **single-threaded state coordinator** cho các runtime state quan trọng.

Mọi command/event ảnh hưởng `Recording`, `Finalizing`, `BDMA_READY`, `Policy apply`, `Config apply`, `Update`, `Recovery` phải đi qua cùng một coordination queue hoặc một coordinator có rule tuần tự tương đương.

UI / Camera callback / Storage result / DB result / Policy result / Network result
        ↓
Command or Event
        ↓
RecordingCommandExecutor / StateCoordinator
        ↓
State transition decision
        ↓
Async work on specialized executor

Rule

Description

THR-SM-001

Chỉ State Coordinator được mutate runtime state chính.

THR-SM-002

Component khác chỉ emit command/event/result.

THR-SM-003

State transition phải chạy tuần tự trên một executor.

THR-SM-004

Không dùng nhiều thread cùng ghi `recording_state`, `finalization_state`, `bdma_readiness_state` hoặc `policy/update/config apply state`.

THR-SM-005

Command đến trong state không hợp lệ phải được reject hoặc defer bằng reason code rõ ràng.

THR-SM-006

State Coordinator không được block vô hạn khi gọi camera/file/DB/policy/network.

THR-SM-007

Mọi async result phải quay lại State Coordinator trước khi update state.

Performance target reference:

```
PERF-ANR-002 State Coordinator queue latency ≤ 50ms
```

## 6. Recording Pipeline Thread Model

Recording pipeline phải được serialize ở level command/state, nhưng work nặng được phân tách sang lane chuyên trách.

StartRecordingCommand
    ↓ RecordingCommandExecutor
PRECHECKING
    ↓ CameraExecutor
Camera open / prepare
    ↓ RecordingCommandExecutor receives CameraReady
PREPARING_STORAGE
    ↓ FileIoExecutor
Temp file prepared
    ↓ RecordingCommandExecutor receives TempFileReady
STARTING
    ↓ CameraExecutor
Camera SDK start
    ↓ Camera callback emits SdkRecordingStarted
    ↓ RecordingCommandExecutor
RECORDING
Stop/finalization flow:

StopRecordingCommand
    ↓ RecordingCommandExecutor
STOPPING
    ↓ CameraExecutor
Camera SDK stop
    ↓ Camera callback emits SdkRecordingStopped
    ↓ RecordingCommandExecutor
FINALIZING
    ↓ FileIoExecutor
Close/validate/move
    ↓ RecordingCommandExecutor receives FinalizationFileReady
    ↓ DbExecutor
Update media_session / BDMA_READY
    ↓ RecordingCommandExecutor receives DbUpdateSuccess
BDMA_READY
    ↓ Async checksum may run separately on FileIoExecutor
    ↓ MainThread UI state
Important performance alignment:

Critical finalization path ends at BDMA_READY.
Checksum is outside critical finalization path and must not block BDMA_READY or new recording start.

Pipeline Step

Owner Lane

Required Rule

Start command

`MainThread` → `RecordingCommandExecutor`

UI chỉ enqueue command, không tự start camera.

Precheck

`RecordingCommandExecutor` + short delegated checks

Check nhanh; nếu cần DB/file/camera result thì dùng async result.

Camera prepare/start

`CameraExecutor`

Không mutate state trực tiếp; emit result.

Temp file prepare

`FileIoExecutor`

Không giữ DB transaction khi thao tác file.

Active recording state

`RecordingCommandExecutor`

Chỉ update sau SDK callback xác nhận.

Stop request

`RecordingCommandExecutor`

Không duplicate stop; reject/defer nếu state không hợp lệ.

Camera stop

`CameraExecutor`

Callback chỉ emit event về coordinator.

Finalization

`FileIoExecutor`

File operation hoàn tất trước DB transaction.

DB update

`DbExecutor`

Transaction ngắn; mark `BDMA_READY` sau khi file final hợp lệ.

Async checksum

`FileIoExecutor`

Không nằm trong critical finalization path.

UI update

`MainThread`

Nhận state/result đã map từ domain.

## 7. Camera Callback Contract

Camera callback là nguồn race condition phổ biến. DCAM không cho phép camera callback tự mutate state hoặc ghi DB.

Callback Source

Allowed

Not Allowed

CameraX / Camera2 callback

Tạo event như `SdkRecordingStarted`, `SdkRecordingStopped`, `CameraError`, `FileClosed`.

Ghi SQLite trực tiếp, update UI trực tiếp, gọi `StorageService` finalization trực tiếp.

Vendor SDK callback

Map vendor callback thành domain event an toàn.

Throw exception ra ngoài callback hoặc block callback thread lâu.

Camera error callback

Emit `CameraErrorEvent` về State Coordinator.

Tự cleanup media file hoặc tự đổi `recording_state`.

Camera stop callback

Emit `SdkRecordingStoppedEvent`.

Tự mark `BDMA_READY`.

Rule:

Camera callback emits event only.
State Coordinator decides what the event means.
Performance target reference:

PERF-ANR-003 Camera callback processing ≤ 100ms
PERF-ANR-006 Camera timeout ≤ 5s
## 8. File I/O and Finalization Model

File I/O có thể chậm và không ổn định trên BodyCamera, đặc biệt với external storage hoặc storage gần đầy. Tất cả file operation nặng phải chạy trong `FileIoExecutor`.

Operation

Execution Lane

Notes

Create temp/staging file

`FileIoExecutor`

Trả result về State Coordinator.

Validate file exists/size

`FileIoExecutor`

Không chạy trên MainThread.

Move/rename temp to final

`FileIoExecutor`

Phải atomic/safe theo Storage Design nếu platform cho phép.

Generate checksum

`FileIoExecutor`

Không chạy trong DB transaction; chạy async sau `BDMA_READY`.

Write CSON

`FileIoExecutor`

Dùng safe temp/write/replace pattern khi cần.

Write log file

`FileIoExecutor` hoặc logging executor

Không block recording command queue lâu.

Storage recovery scan

`FileIoExecutor`

Không block UI; result quay về coordinator/recovery manager.

Finalization order bắt buộc:

File operation outside DB transaction
    ↓
Small DB transaction
    ↓
Emit result event
    ↓
Async checksum outside critical path if enabled
Không dùng pattern:

Begin DB transaction
    ↓
Move large file
    ↓
Generate checksum
    ↓
Wait camera callback
    ↓
Commit DB transaction
## 9. SQLite / DB Write Thread Model

SQLite là single-writer sensitive. DCAM dùng một DB write boundary rõ ràng.

Rule

Description

THR-DB-001

Tất cả DB write phải chạy qua `DbExecutor` hoặc Room database executor được cấu hình tương đương.

THR-DB-002

Không DB write từ `MainThread`.

THR-DB-003

Không DB write trực tiếp từ camera callback.

THR-DB-004

Không chạy file move/checksum/network call trong DB transaction.

THR-DB-005

Transaction phải ngắn, rõ owner và có timeout/retry policy cho `SQLITE_BUSY` nếu dùng raw SQLite.

THR-DB-006

`media_session`, `operator_session`, `bdma_readiness_state`, `remote_config_apply_state`, `update_state` phải được update bằng repository/transaction boundary.

THR-DB-007

DB result phải quay về State Coordinator trước khi đổi runtime state.

THR-DB-008

DB corruption/lock timeout phải map thành domain result và recovery state, không crash trực tiếp.

Performance target reference:

PERF-IO-004 DB transaction duration ≤ 100ms
PERF-ANR-007 DB busy retry ≤ 3 retries, ≤ 1s total
## 10. Policy / Config / Update Safe Window

Các tác vụ policy/config/update có thể phá vỡ recording nếu apply sai thời điểm. DCAM phải dùng **Safe Window Guard**.

Policy / Config / Update request
    ↓
State Coordinator
    ↓
Safe Window Guard
    ├── Recording/Emergency/Finalizing active
    │       → Defer
    ├── DB/Storage/Policy recovery active
    │       → Defer
    ├── Runtime safe
    │       → Apply through owner executor/manager
    └── Invalid request
            → Reject with reason code

Incoming Event

During Recording / Finalizing

Required Behavior

Remote config affecting recording/storage

Không apply ngay.

Defer tới safe window.

Kiosk policy change

Không apply ngay nếu ảnh hưởng Lock Task/User Restrictions.

Defer và apply qua `KioskPolicyManager`.

Self update request

Không install/update.

Defer tới safe window.

User disabled by BDMA sync

Không interrupt active recording.

Block new recording sau safe window nếu user không còn hợp lệ.

Storage cleanup

Không cleanup file liên quan active/finalizing session.

Defer.

DB migration

Không chạy trong lúc recording/finalizing.

Defer tới startup/safe recovery window.

Maintenance Mode request

Không enter khi recording/finalizing/emergency.

Reject/defer theo State Machine Design.

## 11. Cross-executor Communication Rules

Cross-executor communication phải theo mô hình async event/result. Không dùng blocking wait khi đang giữ state lock hoặc DB transaction.

Rule

Description

THR-CROSS-001

Executor A không được chờ đồng bộ executor B khi đang giữ DB transaction.

THR-CROSS-002

State Coordinator không được block vô hạn chờ camera/file/DB result.

THR-CROSS-003

Camera callback không được chờ DB write hoàn tất.

THR-CROSS-004

FileIoExecutor không được chờ MainThread để hoàn tất file operation.

THR-CROSS-005

MainThread không được chờ `Future.get()` từ background executor.

THR-CROSS-006

Mọi request async cần timeout hoặc cancellation path nếu thao tác có thể treo.

THR-CROSS-007

Result từ executor phụ phải quay về State Coordinator để quyết định transition kế tiếp.

THR-CROSS-008

UI chỉ nhận final UI state hoặc progress state đã được map, không nhận raw SDK exception.

## 12. Deadlock and Race Prevention Rules

Rule

Description

THR-DEAD-001

Không thread nào được chờ đồng bộ thread khác khi đang giữ runtime state mutation lock.

THR-DEAD-002

Không DB transaction nào được chờ camera callback.

THR-DEAD-003

Không camera callback nào được chờ DB transaction.

THR-DEAD-004

Không MainThread nào được chờ `RecordingCommandExecutor`.

THR-DEAD-005

Không `FileIoExecutor` nào được chờ MainThread để complete.

THR-DEAD-006

Không giữ lock khi gọi vendor SDK nếu SDK có thể callback lại vào app.

THR-DEAD-007

Không start duplicate recording session từ hai event source khác nhau.

THR-DEAD-008

Không mark `BDMA_READY` trước khi final file và DB transaction đều thành công.

THR-DEAD-009

Không apply remote config/policy/update trực tiếp từ network callback.

THR-DEAD-010

Không mutate `recording_state` từ nhiều executor.

THR-DEAD-011

Không delay `BDMA_READY` vì checksum file lớn; checksum chạy async ngoài critical path.

## 13. MVP Threading Profile

Execution Lane

MVP Required

Notes

`MainThread`

Yes

UI/lifecycle only.

`RecordingCommandExecutor`

Yes

Single-threaded command/state queue cho recording/capture/finalization.

`CameraExecutor` / `CameraHandlerThread`

Yes

Camera SDK operation và callback mapping.

`FileIoExecutor`

Yes

Temp/final/log/CSON operation; checksum async if enabled.

`DbExecutor`

Yes

SQLite write/read boundary.

`PolicyExecutor`

Conditional

Chỉ cần nếu MVP có Lock Task POC.

`NetworkExecutor`

No

Phase 2+ nếu provisioning/remote config/update check bắt đầu.

`UpdateExecutor`

No

Phase 2+ theo Self Update Design.

MVP required rules:

Camera callback emits event only.
Recording state is mutated only on RecordingCommandExecutor.
File finalization runs on FileIoExecutor.
DB writes run on DbExecutor.
MainThread never blocks on recording/file/DB work.
BDMA_READY is marked only after final file and DB update succeed.
For Build 0.1, checksum success gates BDMA_READY.
### 13.1 Build 0.1 Review Candidate

Execution Boundary

Build 0.1

Review Guardrail

`MainThread`

Required

UI/lifecycle work nhẹ; không camera/file/DB blocking.

State Coordinator / `RecordingCommandExecutor`

Required

Single-writer runtime state; command/event serialization.

`CameraExecutor` / `CameraHandlerThread`

Required

Camera calls/callback mapping; không mutate DB/UI/state trực tiếp.

`FileIoExecutor`

Required

Temp/final/CSON/log/checksum I/O; không giữ DB transaction.

`DbExecutor`

Required

Short DB reads/writes, finalization/`BDMA_READY` state; bounded retry theo `PERF-ANR-007`.

`PolicyExecutor`

Conditional

Chỉ active nếu Build 0.1 có approved Lock Task POC exception.

`NetworkExecutor` / `UpdateExecutor`

Deferred

Không thuộc Working Recording Slice.

Technology choice, queue capacity và concrete class names không được tự suy ra từ subset này. `Approved Provisional Baseline` chỉ được đề xuất sau required Technical Review.

## 14. Performance Budget Alignment

Detailed numeric targets are owned by **DCAM Performance Budget & Resource Constraints**. This page owns the execution model; the performance page owns measurable targets.

Performance Metric

Threading / Concurrency Owner Rule

PERF-ANR-001 MainThread block duration `< 500ms`

MainThread chỉ chạy UI/lifecycle work nhẹ.

PERF-ANR-002 State Coordinator queue latency `≤ 50ms`

State Coordinator không chạy file/DB/camera work nặng.

PERF-ANR-003 Camera callback processing `≤ 100ms`

Camera callback chỉ map event và enqueue về coordinator.

PERF-ANR-005 Executor queue depth `≤ 10 pending tasks`

Executors phải có monitoring/logging để detect backpressure.

PERF-ANR-006 Camera timeout `≤ 5s`

Camera operation cần timeout và recovery path.

PERF-ANR-007 DB busy retry `≤ 3 retries, ≤ 1s total`

DB executor không được loop vô hạn.

PERF-STAB-005 App-owned steady-state thread count `≤ 15`

DCAM-owned executors/threads phải bounded; không tạo thread per session.

PERF-REC-003 Critical finalization latency `≤ 5s`

File close/validate/move + DB update nằm trong critical path; checksum không nằm trong path này.

## 15. Phase 2+ Threading Expansion

Khi Phase 2+ mở rộng platform features, thêm các execution lanes và guard tương ứng.

Feature

Additional Threading Requirement

Device Owner / Kiosk Policy

Policy apply/verify chạy qua policy manager và serialized executor; apply chỉ trong safe window.

Remote Config

Fetch trên network executor; validate/apply qua coordinator; không apply từ callback.

Self Update

Download/validate/install coordination riêng; install bị defer khi recording/finalizing/recovery.

User Sync / BDMA write-back

Apply qua DB executor và external change detection; không mutate active recording lifecycle.

Sensor Monitoring

Sensor callback chỉ emit event; không điều khiển recording trực tiếp.

Realtime AI Detection

AI thread chỉ emit detection event; không ghi final media hoặc điều khiển camera trực tiếp.

Live Streaming / PTT

Network/audio/video lanes riêng; không block core recording/file finalization.

## 16. Logging and Diagnostics

Concurrency log phải đủ để QA/support tìm race condition mà không log sensitive data.

Required log examples:

[THREAD] Command enqueued: StartRecordingCommand
[THREAD] State transition: IDLE -> PRECHECKING
[THREAD] Camera callback received: SdkRecordingStarted
[THREAD] File finalization started
[THREAD] File finalization completed
[THREAD] DB transaction started: media_session_update
[THREAD] DB transaction completed: media_session_update
[THREAD] Safe window blocked: RECORDING_ACTIVE
[THREAD] Remote config deferred: SAFE_WINDOW_BLOCKED_BY_RECORDING
[THREAD] Self update deferred: FINALIZING
[THREAD] Camera stop timeout; recovery required
[THREAD] DB busy retry: <attempt>
[PERF] state_coordinator_queue_latency_ms=<value>
[PERF] executor_queue_depth_state=<value>
Không log:

credentials
maintenance password
tokens
raw Android system identifier
raw media content
raw AI frame
biometric samples
encryption keys
sensitive config payload
## 17. QA Test Matrix for Concurrency

Test ID

Scenario

Expected Result

THR-QA-001

Start → Stop nhanh liên tục nhiều lần.

Không crash, không duplicate session, không corrupt file.

THR-QA-002

Stop khi camera callback đến chậm.

State không deadlock; timeout/recovery nếu cần.

THR-QA-003

App crash hoặc kill trong `FINALIZING`.

Recovery preserve temp/final candidate và reconcile DB/file.

THR-QA-004

Storage gần đầy khi đang recording.

Stop/fail an toàn, preserve file nếu có thể, log reason code.

THR-QA-005

DB busy khi finalization update.

Retry/timeout rõ ràng; không treo UI; không mark BDMA_READY sai.

THR-QA-006

BDMA scan trong lúc app đang finalize file.

BDMA không thấy partial file như final media; chỉ import khi BDMA_READY.

THR-QA-007

Background/foreground khi đang recording.

Recording không bị state mismatch; UI restore đúng state.

THR-QA-008

Policy/config/update request trong lúc recording.

Request bị defer hoặc reject đúng reason; recording không bị interrupt.

THR-QA-009

Camera error callback trong lúc DB write.

Không deadlock; state chuyển sang failure/recovery an toàn.

THR-QA-010

Multiple event sources gửi command cùng lúc.

State Coordinator xử lý tuần tự; không race condition.

THR-QA-011

File move chậm trên external storage.

Không block MainThread; finalization timeout/recovery nếu cần.

THR-QA-012

Device reboot sau active session/recording.

Session cũ hết hạn theo auth rule; recording recovery preserve evidence.

THR-QA-013

Checksum file lớn đang chạy và user start recording mới.

Recording mới không bị block bởi checksum async nếu storage policy cho phép.

## 18. PR Checklist

Check

Required

Code không chạy camera/file/DB operation dài trên MainThread.

Yes

Camera callback chỉ emit event, không ghi DB/update UI/mutate state trực tiếp.

Yes

Recording state chỉ mutate trên single-threaded coordinator.

Yes

File finalization chạy trên `FileIoExecutor` hoặc boundary tương đương.

Yes

DB write chạy trên `DbExecutor` hoặc Room executor tương đương.

Yes

DB transaction không chứa file move/checksum/network/camera wait.

Yes

Checksum không nằm trong critical finalization path.

Yes

Cross-executor call dùng async event/result, không blocking wait khi giữ lock/transaction.

Yes

Policy/config/update apply kiểm tra Safe Window Guard.

Feature-specific

Timeout/recovery path tồn tại cho camera stop/finalization/DB busy nếu có nguy cơ treo.

Yes

Logs có reason code cho defer/reject/timeout/recovery/performance.

Yes

Sensitive data không bị log.

Yes

QA scenario concurrency/performance liên quan đã được cập nhật nếu PR thay đổi recording/storage/DB/policy/update.

Recommended

## 19. Practical Conclusion

DCAM phải có concurrency model rõ ràng trước khi implementation recording/storage/DB đi sâu.

Baseline thực thi hiện tại là:

Java-first
ExecutorService / HandlerThread
Single-threaded state coordinator
Camera callback emits event only
DB writes through DbExecutor only
File finalization through FileIoExecutor only
Checksum is async and outside critical finalization path
Policy/config/update apply only in safe window
MainThread only for UI
Nguyên tắc quan trọng nhất:

Only the coordinator mutates runtime state.
Specialized executors do work and return results.
No executor blocks another executor while holding state or DB transaction.
Performance targets are defined by DCAM Performance Budget & Resource Constraints.
## 20. Build 0.1 Required Concurrency Subset

Component

Required Responsibility

MainThread

UI/state rendering only; không camera/file/DB/hash blocking work.

State Coordinator

Serialize recording/storage/finalization/checksum/readiness transitions.

CameraExecutor

Android platform Camera API operations; vendor SDK Not Applicable.

FileIoExecutor

Temp/final file I/O và async MD5 sau MP4 finalization.

DbExecutor

Persist media/session/finalization/checksum/recovery/operator state.

Build 0.1 sequence:

Camera/File finalization
  → persist finalized/checksum-pending
  → async MD5 on FileIoExecutor
  → persist checksum success
  → publish BDMA_READY
Nếu MD5 fail, persist failed/pending state, giữ MP4, log và không publish BDMA_READY. Exact executor count, queue capacity, timeout và state enum vẫn Pending Technical Review; page giữ Draft.