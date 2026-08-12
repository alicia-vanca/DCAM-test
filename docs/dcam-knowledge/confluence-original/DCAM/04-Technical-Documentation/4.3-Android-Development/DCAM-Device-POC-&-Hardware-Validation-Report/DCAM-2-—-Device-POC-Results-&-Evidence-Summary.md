# DCAM-2 — Device POC Results & Evidence Summary

**Page ID**: 57344040  
**Version**: 14  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/57344040

---


# DCAM-2 — Device POC Results & Evidence Summary

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Device POC Results & Evidence Summary

Version

1.3

Status

**Draft**

Approval Scope

Tổng hợp kết quả POC, cơ sở evidence, giới hạn và dữ liệu đề xuất cho tài liệu cha; không thay đổi yêu cầu, mã nguồn, phạm vi phê duyệt hoặc kết luận phát hành.

Document Owner

[duchm](https://ducviet.atlassian.net/wiki/people/712020:39572dd4-e781-49ef-935e-7fbb19511d4a?ref=confluence), [Việt Anh](https://ducviet.atlassian.net/wiki/people/70121:291a3abc-1d00-4cc3-84e8-af1e4491aeb1?ref=confluence) 

Technical Reviewer

Tech Lead / QA

Approver

PM — sau khi Tech Lead hoàn tất rà soát kỹ thuật

Parent Page

DCAM Device POC & Hardware Validation Report — §16/§16.1

Target Audience

`PM/BA, Tech Lead, Document Owners, QA, Reviewers, Approvers, Android Developers`

Related Documents

[DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix); [DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry); [DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP).

Related Jira

[https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70?selectedIssue=DCAM-2](https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70?selectedIssue=DCAM-2) ; [DCAM-11](https://ducviet.atlassian.net/browse/DCAM-11), [DCAM-12](https://ducviet.atlassian.net/browse/DCAM-12), [DCAM-13](https://ducviet.atlassian.net/browse/DCAM-13), [DCAM-15](https://ducviet.atlassian.net/browse/DCAM-15) ([duchm](https://ducviet.atlassian.net/wiki/people/712020:39572dd4-e781-49ef-935e-7fbb19511d4a?ref=confluence) ); [DCAM-14](https://ducviet.atlassian.net/browse/DCAM-14), [DCAM-49](https://ducviet.atlassian.net/browse/DCAM-49) ([Việt Anh](https://ducviet.atlassian.net/wiki/people/70121:291a3abc-1d00-4cc3-84e8-af1e4491aeb1?ref=confluence) ).

Dependencies / Blockers

WRS-004: **Fail** do GAP requirement ↔ code (ngưỡng tĩnh 2 GiB chưa đáp ứng PERF-STOR-001, 200 MiB chưa đáp ứng PERF-STOR-003); WRS-009: **Blocked**, chờ PM xác nhận nới độ dài 6–10 → 6–11 ký tự và sửa code (chặn chữ thường)

Last Updated

07 Aug 2026

Data cutoff

2026-07-31 15:30:15 ICT

## 1. Mục đích và cách sử dụng

Tài liệu này tổng hợp kết quả kỹ thuật, Evidence ID và giới hạn của các POC thuộc phạm vi trực tiếp của DCAM-2; đồng thời cung cấp dữ liệu đối chiếu cho tài liệu cha §16/§16.1.

Đây là bản tổng hợp thực thi đang làm việc (working execution summary). Kết luận POC chính thức thuộc **DCAM Device POC & Hardware Validation Report**; quy trình thực hiện và bàn giao được quản lý trong Jira; mức độ bao phủ được quản lý trong Traceability Matrix.

Evidence gốc, vị trí lưu trữ chi tiết, thông tin định danh thiết bị và checksum được lưu trong kho evidence nội bộ trên NAS (máy chủ lưu trữ tệp nội bộ dùng chung của dự án). Trang này chỉ nêu Evidence ID, kết quả và giới hạn; người có quyền truy cập dùng Evidence ID cùng controlled reference trong Jira hoặc manifest để tra cứu gói evidence tương ứng.

Kết quả POC chỉ phản ánh phạm vi đã kiểm chứng; không tự động đồng nghĩa với việc đã đáp ứng đầy đủ yêu cầu, được chấp thuận phát hành hoặc sẵn sàng triển khai diện rộng. Kết quả chỉ áp dụng cho build và phạm vi đã kiểm chứng; khi mã nguồn hoặc dependency liên quan thay đổi, các WRS bị ảnh hưởng cần được kiểm tra lại trước khi sử dụng kết quả.

DCAM-14 sử dụng kết quả POC-WRS-001 làm exact reference device baseline và POC-WRS-002 làm Camera API/recording-capture capability baseline để xây dựng camera lifecycle/failure observation harness, runner và evidence structure.

DCAM-49 kế thừa qualified source từ DCAM-14 để disposition sáu camera lifecycle/failure cases, kiểm tra restore và đóng gói active evidence. DCAM-14 và DCAM-49 là supporting engineering evidence tasks, không tạo POC ID mới và không tự thay đổi kết quả trong POC Result Matrix.

## 2. POC Result Matrix

Test ID

Jira

Result

Evidence ID

Kết quả chính

Phạm vi / giới hạn

POC-WRS-001

DCAM-11

**Pass**

`EV-DCAM-DCAM-11-20260724-04`

Ghi nhận model thương mại `NCC-036V`, model Android `BWC`, Android 12/API 31 và firmware.

Không khẳng định hai nhãn model tương đương trong mọi trường hợp; không suy rộng thành qualification nhiều thiết bị.

POC-WRS-002

DCAM-12

**Pass**

`EV-DCAM-DCAM-12-20260724-04`

Đã kiểm chứng Camera API, camera inventory/lens, FPS, preview, JPG, MP4 và permission.

Áp dụng cho thiết bị/build tham chiếu; duration/container metadata và codec/profile chưa được xác minh bằng `ffprobe`.

POC-WRS-003

DCAM-13

**Pass**

`EV-DCAM-DCAM-13-20260724-02`

Đã kiểm chứng Internal storage mapping, scoped-storage behavior, ADB visibility và việc từ chối External/Auto.

Chỉ áp dụng cho Internal-active của Build 0.1.

POC-WRS-004

DCAM-15

**Fail**

`EV-DCAM-DCAM-15-20260805-01`

Fail cũ do phương pháp test (fill/rollback lặp trên state không fresh → LMK kill do thrashing), không phải lỗi code; retest 05/8 fresh install trên cùng commit đạt.

Giữ **Fail** vì ngưỡng tĩnh 2 GiB/200 MiB chưa đáp ứng PERF-STOR-001/003; cần khắc phục và kiểm tra lại trên các cấu hình.

POC-WRS-005

—

**Not Executed** trong phạm vi DCAM-2

**Not Evidenced**

Chưa ghi nhận kết quả trong tài liệu này.

Ngoài phạm vi thực thi trực tiếp của DCAM-2; chỉ cập nhật sau khi nhận evidence handoff đã được rà soát từ phạm vi liên quan. Không được hiểu là **Pass** hoặc **Fail**.

POC-WRS-006

—

**Not Executed** trong phạm vi DCAM-2

**Not Evidenced**

Chưa ghi nhận kết quả trong tài liệu này.

Ngoài phạm vi thực thi trực tiếp của DCAM-2; chỉ cập nhật sau khi nhận evidence handoff đã được rà soát từ phạm vi liên quan. Không được hiểu là **Pass** hoặc **Fail**.

POC-WRS-007

DCAM-15

**Pass**

`EV-DCAM-DCAM-15-20260805-01`

Đã xác nhận GPS, Internal free storage và Battery trên thiết bị đã cấu hình.

Free storage được xác định bằng `Total − Used`; Battery dùng phần trăm pin trên Android status bar và cần bật cấu hình hiển thị.

POC-WRS-008

—

**Not Executed** trong phạm vi DCAM-2

**Not Evidenced**

Chưa ghi nhận kết quả trong tài liệu này.

Ngoài phạm vi thực thi trực tiếp của DCAM-2; chỉ cập nhật sau khi nhận evidence handoff đã được rà soát từ phạm vi liên quan. Không được hiểu là **Pass** hoặc **Fail**.

POC-WRS-009

DCAM-15

**Blocked**

`EV-DCAM-DCAM-15-20260805-01`

Token đạt định dạng `[A-Z0-9]{6,10}`; recovery đã được kiểm chứng bằng một file MP4 thực tế.

Thứ tự ưu tiên serial đã rõ: DB → `dcam_config.cson` → backup SD → `configs.cson` (vendor) → nhập tay; blocker còn lại là PM xác nhận nới độ dài 6–10 → 6–11 và sửa code chặn chữ thường.

## 3. Kết quả theo Jira

Phần này trình bày kết quả POC theo từng Jira task thuộc phạm vi trực tiếp của DCAM-2. Mỗi hồ sơ gồm kết quả thực thi, kết quả kỹ thuật chính, giới hạn và Evidence ID.

Subtask

POC liên quan

Mục tiêu

DCAM-11 

POC-WRS-001

Xác định chính xác cấu hình thiết bị được dùng làm mẫu tham chiếu, gồm model, Android/API và firmware.

DCAM-12

POC-WRS-002

Xác minh Camera API và khả năng preview, chụp ảnh, quay video trên thiết bị tham chiếu.

DCAM-13

POC-WRS-003

Xác minh cách ứng dụng sử dụng Internal storage, đường dẫn lưu file, scoped storage, khả năng truy cập bằng ADB và hành vi của External/Auto.

DCAM-15

POC-WRS-004, POC-WRS-007, POC-WRS-009

Kiểm tra độ ổn định khi dung lượng thấp; khả năng báo Battery, Internal free storage và GPS; đồng thời xác minh `DEVICE_TOKEN` và định dạng filename.

DCAM-14

POC-WRS-001 và POC-WRS-002

Sử dụng exact device baseline và camera capability baseline để tạo lifecycle/failure observation harness, runner và evidence structure.

DCAM-49

Supporting observation; không tạo POC ID

Disposition sáu camera lifecycle/failure cases, kiểm tra evidence/restore/integrity và cung cấp closure input cho DCAM-2 cùng Camera Recording Prototype.

Các hồ sơ DCAM-11/12/13/15 giữ Evidence ID tương ứng. DCAM-14 dùng NAS README làm execution instruction. DCAM-49 dùng NAS Procedure làm review authority để kiểm tra selected case, evidence, Blocked rationale và closure decision.

### 3.1 — DCAM-11 / POC-WRS-001

Field

Value

Execution result

**Pass**

Jira Worklog

[Worklog #10001 — DCAM-11](https://ducviet.atlassian.net/browse/DCAM-11?focusedWorklogId=10001)

Evidence ID

`EV-DCAM-DCAM-11-20260724-04`

Key outcome

Commercial model `NCC-036V`; Android-reported model `BWC`; Android 12/API 31; firmware được ghi nhận.

Limitation

Không khẳng định `NCC-036V == BWC` trong mọi trường hợp; không suy rộng thành qualification nhiều thiết bị.

### 3.2 — DCAM-12 / POC-WRS-002

Field

Value

Execution result

**Pass**

Jira Worklog

[Worklog #10002 — DCAM-12](https://ducviet.atlassian.net/browse/DCAM-12?focusedWorklogId=10002)

Evidence ID

`EV-DCAM-DCAM-12-20260724-04`

Key outcome

Đã kiểm chứng Camera API, camera inventory/lens, FPS, preview, JPG, MP4 và permission trên thiết bị/build tham chiếu.

Limitation

Duration/container metadata và codec/profile của MP4 chưa được xác minh bằng `ffprobe`.

### 3.3 — DCAM-13 / POC-WRS-003

Field

Value

Execution result

**Pass**

Jira Worklog

[Worklog #10003 — DCAM-13](https://ducviet.atlassian.net/browse/DCAM-13?focusedWorklogId=10003)

Evidence ID

`EV-DCAM-DCAM-13-20260724-02`

Key outcome

Đã kiểm chứng Internal storage mapping, scoped-storage behavior, ADB visibility và việc từ chối External/Auto trong Build 0.1.

Limitation

External/Auto có thể hợp lệ ở build hoặc release sau; kết quả hiện tại không áp dụng ngoài Build 0.1.

### 3.4 — DCAM-15 / POC-WRS-004, POC-WRS-007 và POC-WRS-009

Field

Value

Execution result

WRS-004: **Fail**; WRS-007: **Pass**; WRS-009: **Blocked**.

Evidence ID

`EV-DCAM-DCAM-15-20260805-01`

Key outcome

**WRS-004:** Fail cũ do phương pháp test (lặp fill/rollback trên state không fresh → dirty pages tích tụ → LMK kill do thrashing 63–65%, không riêng app), không phải lỗi code. Hành vi guard 2 GiB chỉ kiểm tra lúc start là logic nghiệp vụ đúng; khi đang ghi, dừng theo file-size-limit `(free − 200 MiB) / 2`, không dùng lại guard 2 GiB. Retest 05/8 fresh install (cùng commit c4da859): auto-stop/finalize/pre-start rejection đúng, PID giữ nguyên, không ANR, MP4 hợp lệ. 

**WRS-007:** Đã xác nhận GPS, dung lượng bộ nhớ Internal và mức pin trên thiết bị tham chiếu đã cấu hình. 

**WRS-009:** Token đạt định dạng `[A-Z0-9]{6,10}`; ứng dụng tạo và liệt kê được file local; các unit test mục tiêu cho filename, recovery và serial đạt. Gói evidence hiện hành xác nhận recovery thành công bằng một file MP4 thực tế và file sau recovery giữ nguyên nội dung.

Jira Worklog

[Worklog #10004 — DCAM-15](https://ducviet.atlassian.net/browse/DCAM-15?focusedWorklogId=10004)

Limitation

**WRS-004:** Giữ **Fail** do GAP requirement ↔ code: PERF-STOR-001 yêu cầu ngưỡng start tính động = bitrate × 30 phút + 500 MB (FHD 12 Mbps → ~3.2 GB, code dùng hằng số tĩnh 2.0 GB nên chỉ đảm bảo ~10 phút), PERF-STOR-003 yêu cầu 500 MB cho finalization trong khi code dùng 200 MiB. Cần khắc phục và retest trên các cấu hình SD/HD/FHD. 

**WRS-007:** Free storage được xác định bằng `Total − Used`;

**WRS-009:** Thứ tự ưu tiên lấy serial đã rõ: DB → `dcam_config.cson` → backup SD → `configs.cson` (vendor) → nhập tay khi cài app. WRS-009 giữ **Blocked** cho đến khi PM xác nhận nới độ dài 6–10 → 6–11 ký tự (để khớp serial 11 ký tự trên nhãn thiết bị trong `configs.cson`) và dev sửa code (thứ tự đọc serial + chặn chữ thường `[A-Z0-9]` + độ dài theo xác nhận) rồi retest. Vẫn cần phân biệt serial trên nhãn thiết bị dài 11 ký tự và serial hệ thống `ro.serialno` đọc qua Android/ADB dài 18 ký tự.

### 3.5 — DCAM-14 / Camera Lifecycle/Failure Observation Harness and Evidence Structure

Field

Value

Execution result

Harness/procedure: **Pass**.

Execution instruction

NAS `README.md`: `\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-14\README.md`.

Input baselines

POC-WRS-001 cung cấp exact reference device configuration; POC-WRS-002 cung cấp Android platform Camera API và recording/capture capability baseline.

Key output

Runner/procedure có thể build/cài APK từ source hiện tại, thực hiện normal lifecycle observation và sinh evidence package gồm log, media, screenshot, manifest và checksum.

Limitation

Không chứng minh exact Activity finish/reopen, các failure observation chưa có controlled injection/recovery method hoặc Production/fleet/multi-firmware readiness.

### 3.6 — DCAM-49 / Camera Lifecycle and Failure Observation Matrix

Field

Value

Execution result

**4 Pass / 2 Blocked**; 
Final normal baseline **Pass**. 
Evidence đã sẵn sàng cho review; closure chưa hoàn tất.

Evidence ID

Active closure set `EV-DCAM-49-20260806-002/003/004/005/006/007`.

Review procedure

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-49\DCAM-49-Observation-Matrix-Procedure.md`.

Key outcome

Permission denial, camera unavailable/recovery và background/foreground return đã Pass trên qualified device/build; final normal baseline được tái lập sau observation matrix.

Limitation

`CAM-FL-003` và `CAM-FL-004` giữ **Blocked** vì chưa có deterministic safe injection cho recording-start và stop/finalize failure. Kết quả không chứng minh actual behavior của hai case này hoặc Production/fleet/multi-firmware readiness.

## 4. Các vấn đề và quyết định còn mở

Nội dung

Trạng thái hiện tại

Điều cần làm / lưu ý

WRS-004 / độ ổn định khi dung lượng thấp

**Fail**

Fail cũ đã xác định do phương pháp test, không phải lỗi code; retest fresh install đạt. Mở implementation issue sửa `CaptureStorageCapacityPolicy`: ngưỡng start tính động = bitrate × 30 phút + 500 MB (PERF-STOR-001) và nâng `MIN_CAPTURE_FREE_BYTES` từ 200 MiB lên 500 MB (PERF-STOR-003), sau đó retest trên các cấu hình SD/HD/FHD. Giữ **Fail** cho đến khi lượt kiểm tra lại đạt yêu cầu và có evidence mới đã được rà soát.

WRS-009 / `DEVICE_TOKEN`

**Blocked**

Cần PM xác nhận nới độ dài 6–10 → 6–11 ký tự (khớp serial 11 ký tự trong `configs.cson`)