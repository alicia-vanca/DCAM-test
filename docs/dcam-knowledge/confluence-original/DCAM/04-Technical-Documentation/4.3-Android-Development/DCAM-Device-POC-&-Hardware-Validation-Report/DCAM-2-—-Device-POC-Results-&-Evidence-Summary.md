# DCAM-2 — Device POC Results & Evidence Summary

**Page ID**: 57344040  
**Version**: 22  
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

1.5

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

[DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix); [DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry);

Related Jira

[https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70?selectedIssue=DCAM-2](https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70?selectedIssue=DCAM-2) ; [DCAM-11](https://ducviet.atlassian.net/browse/DCAM-11), [DCAM-12](https://ducviet.atlassian.net/browse/DCAM-12), [DCAM-13](https://ducviet.atlassian.net/browse/DCAM-13), [DCAM-15](https://ducviet.atlassian.net/browse/DCAM-15) ([duchm](https://ducviet.atlassian.net/wiki/people/712020:39572dd4-e781-49ef-935e-7fbb19511d4a?ref=confluence) ); [DCAM-14](https://ducviet.atlassian.net/browse/DCAM-14), [DCAM-49](https://ducviet.atlassian.net/browse/DCAM-49) ([Việt Anh](https://ducviet.atlassian.net/wiki/people/70121:291a3abc-1d00-4cc3-84e8-af1e4491aeb1?ref=confluence) ).

Dependencies / Blockers

Không có

Last Updated

20 Aug 2026

Data cutoff

2026-08-19 15:30:15 ICT

## 1. Mục đích và cách sử dụng

Tài liệu tổng hợp kết quả kỹ thuật, Evidence ID và giới hạn của các POC trong phạm vi DCAM-2, làm dữ liệu đối chiếu cho tài liệu cha **DCAM Device POC & Hardware Validation Report** (§16/§16.1). Đây là bản tổng hợp thực thi đang làm việc (working execution summary): kết luận POC chính thức thuộc tài liệu cha; quy trình thực hiện và bàn giao quản lý trong Jira; mức độ bao phủ quản lý trong Traceability Matrix.

Kết quả POC chỉ áp dụng cho build và phạm vi đã kiểm chứng; không tự động đồng nghĩa với việc đã đáp ứng đầy đủ yêu cầu, được chấp thuận phát hành hoặc sẵn sàng triển khai diện rộng. Khi mã nguồn hoặc dependency liên quan thay đổi, các WRS bị ảnh hưởng phải được kiểm tra lại trước khi sử dụng kết quả.

DCAM-14 dùng POC-WRS-001 (exact reference device baseline) và POC-WRS-002 (Camera API/recording-capture capability baseline) để xây camera lifecycle/failure observation harness, runner và evidence structure; DCAM-49 kế thừa qualified source từ DCAM-14 để disposition sáu camera lifecycle/failure cases, kiểm tra restore và đóng gói active evidence. Hai task này là supporting engineering evidence, không tạo POC ID mới và không tự thay đổi kết quả trong POC Result Matrix.

## 2. POC Result Matrix

Test ID

Jira

Result

Evidence ID

Kết quả chính

POC-WRS-001

DCAM-11

**Pass**

`EV-DCAM-DCAM-11-20260724-04`

Ghi nhận model thương mại `NCC-036V`, model Android `BWC`, Android 12/API 31 và firmware.

POC-WRS-002

DCAM-12

**Pass**

`EV-DCAM-DCAM-12-20260724-04`

Đã kiểm chứng Camera API, camera inventory/lens, FPS, preview, JPG, MP4 và permission.

POC-WRS-003

DCAM-13

**Pass**

`EV-DCAM-DCAM-13-20260724-02`

Đã kiểm chứng Internal storage mapping, scoped-storage behavior, ADB visibility và việc từ chối External/Auto.

POC-WRS-004

DCAM-15

**Pass**

`EV-DCAM-DCAM-15-20260819-04`

Ứng dụng xử lý đúng khi sắp hết dung lượng ở cả 3 chất lượng SD/HD/FHD: (1) chặn bắt đầu ghi khi không đủ chỗ; (2) tự dừng an toàn sau khoảng 30 phút và luôn giữ lại reserve 500 MiB; (3) từ chối ghi lại sau khi đã dừng; file MP4 phát lại và kiểm tra hash hợp lệ.

POC-WRS-005

—

—

—

Không thuộc phạm vi DCAM-2

POC-WRS-006

—

—

—

Không thuộc phạm vi DCAM-2

POC-WRS-007

DCAM-15

**Pass**

`EV-DCAM-DCAM-15-20260819-04`

Ứng dụng báo đúng trạng thái pin, dung lượng bộ nhớ trong và GPS trên thiết bị tham chiếu, với các giới hạn đã được phê duyệt.

POC-WRS-008

—

—

—

Không thuộc phạm vi DCAM-2

POC-WRS-009

DCAM-15

**Pass**

`EV-DCAM-DCAM-15-20260819-04`

Token trong filename đúng định dạng `[A-Z0-9]{6,10}` lấy từ serial đã xác nhận (khôi phục từ thẻ SD, lưu đúng một lần); file JPG/MP4 tạo ra có đúng token và BDMA import đ��ợc — có kiểm tra MD5, chặn file thiếu/sai hash, báo lỗi rõ ràng.

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

### 3.1 — DCAM-11 / POC-WRS-001

Field

Value

Execution result

**Pass**

Jira Worklog

[Worklog #10001 — DCAM-11](https://ducviet.atlassian.net/browse/DCAM-11?focusedWorklogId=10001)

Evidence ID

`EV-DCAM-DCAM-11-20260724-04`

NAS location

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-11\20260724_04`

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

NAS location

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-12\20260724_04`

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

NAS location

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-13\20260724_02`

Key outcome

Đã kiểm chứng Internal storage mapping, scoped-storage behavior, ADB visibility và việc từ chối External/Auto trong Build 0.1.

Limitation

External/Auto có thể hợp lệ ở build hoặc release sau; kết quả hiện tại không áp dụng ngoài Build 0.1.

### 3.4 — DCAM-15 / POC-WRS-004, POC-WRS-007 và POC-WRS-009

Field

Value

Execution result

**Pass**

Jira Worklog

    

            

    
                [ DCAM-15](https://ducviet.atlassian.net/browse/DCAM-15)
                    -
            Getting issue details...
                                    STATUS
            
 
Evidence ID

`EV-DCAM-DCAM-15-20260819-04`

NAS location

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-15\20260819_04`

Key outcome

**WRS-004 Pass **— SD/HD/FHD đều giữ reserve 500 MiB, tự dừng/finalize, từ chối ghi sau khi dừng và đạt kiểm tra playback/hash. 

**WRS-007** **Pass ** — Battery, Internal storage và GPS được xác minh theo các rule đã phê duyệt. 

**WRS-009** **Pass** — DEVICE_TOKEN remediation Pass, applicability trên qualification target Accepted carry-forward, và parser/import Pass cho JPG hợp lệ, MP4 có MD5 đúng, đồng thời chặn MP4 thiếu hoặc sai MD5.

Limitation

|  

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