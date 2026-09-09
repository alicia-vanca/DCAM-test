# DCAM-6: Working Recording Slice Integration & Evidence Summary

**Page ID**: 66355221  
**Version**: 5  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/66355221

---


# DCAM-6: Working Recording Slice Integration & Evidence Summary

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Parent Task Integration Report & Evidence Summary

Status

Draft / Ready for Review

Version

0.1

Approval Scope

Báo cáo này tổng hợp WRS evidence trên thiết bị mặc định/reference device của Build 0.1: BWC / Android 12 / API 31 / firmware `877AOOAKN1_RK2_V009`.

Owner

Việt Anh

Technical Reviewer

Tech Lead / Android Lead / QA Lead / Security Reviewer / BDMA Lead / Factory Lead

Approver

PM — sau khi Tech Lead hoàn tất rà soát kỹ thuật

Target Audience

Tech Lead, Android Developers, QA, Security Reviewer, Factory/Admin Users

Related Jira

DCAM-6

Related Subtasks

DCAM-38, DCAM-39, DCAM-40, DCAM-42

Reference Configuration

BWC / Android 12 / API 31 / firmware `877AOOAKN1_RK2_V009`

Main Evidence

`EV-DCAM-38-20260815-001`

NAS Root

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-4`

Dependencies / Blockers

|  
Last Updated

2026-08-21

## 1. Tóm tắt điều hành

DCAM-6 là task cha dùng để tổng hợp bằng chứng **Working Recording Slice** trên thiết bị mặc định/reference device của Build 0.1.

Kết quả kỹ thuật hiện tại là `Pass with recorded blocked cases`.

Nói ngắn gọn: candidate APK đã chạy được trên BWC, quay được MP4 trên 30 giây, chụp được JPG, finalize artifact thành công, tạo MD5 sidecar hợp lệ và BDMA đã import thành công cả video lẫn ảnh. Hash của dữ liệu sau import khớp với source, nên luồng chính không có bằng chứng corruption.

Các ghi chú về DB backup, DCAM DB, cấu hình tham chiếu và DCAM-42 provenance là phạm vi/đặc thù evidence, không phải limitation làm giảm kết quả Pass. Phần cần giữ riêng là các case DCAM-40 đang `Blocked` và workflow review/handoff chưa ghi nhận.

## 2. DCAM-6 cần chứng minh gì

Theo scope của DCAM-6, task này không phải để phát triển thêm tính năng mới. Mục tiêu là gom các output đã được review thành một candidate runnable và chứng minh luồng WRS tối thiểu:

Record / Capture
→ Final artifacts
→ BDMA sample import

Các điểm cần chứng minh:

APK candidate cài và chạy được trên thiết bị tham chiếu.

MP4 tối thiểu 30 giây được finalize.

JPG được finalize.

Artifact tối thiểu như CSON, log và timeline được thu.

BDMA import được sample hợp lệ.

Các case `Blocked` và ghi chú phạm vi được ghi rõ để QA/Tech Lead quyết định.

## 3. Kết quả thực tế

Hạng mục

Kết quả

Ghi chú

APK candidate chạy trên BWC

`Pass`

APK cài/mở được và process hoạt động.

Quay MP4 tối thiểu 30 giây

`Pass`

MP4 dài 46,229333 giây.

Finalize MP4

`Pass`

MP4 H.264/AAC, đọc được, không có bằng chứng hỏng file.

Tạo MD5 sidecar

`Pass`

MD5 sidecar khớp với MP4.

Chụp JPG

`Pass`

JPG 1920×1080, mở được, orientation đúng.

Temp cleanup

`Pass`

`Temp` rỗng sau finalize.

Minimal artifacts

`Pass`

CSON/log/timeline đã thu. App-private DB không export được là đặc thù Android/private DB và DB không phải input BDMA Build 0.1.

BDMA sample import

`Pass`

BDMA import 2/2 artifact, 0 failed.

Payload integrity sau import

`Pass`

Hash MP4/JPG đích khớp source.

Failure/compatibility evidence

`Pass with blocked cases`

DCAM-40 có 20 Pass, 3 Blocked, 0 Fail.

## 4. QA Matrix Coverage

Các case dưới đây là phần DCAM-6 cần cover theo `DCAM-QA-Test-Strategy-&-Test-Matrix`. DCAM-38 cover WRS happy path và BDMA positive import; DCAM-40 cover failure/compatibility và negative paths.

QA Case

Nội dung cần chứng minh

Evidence ID

Result

Ghi chú

`QA-WRS-001`

APK chạy, record 30s, finalize, capture image, minimal artifacts, BDMA import

`EV-DCAM-38-20260815-001`

`Pass`

WRS happy path và BDMA positive import.

`QA-WRS-DEV-001`

Evidence trên physical reference device

`EV-DCAM-38-20260815-001`

`Pass`

BWC / Android 12 / API 31 / firmware V009.

`QA-MEDIA-NAME-001`

Filename/token contract, `DEVICE_TOKEN`, `B01OPR`, MP4/MD5 cùng basename

`EV-DCAM-38-20260815-001`

`Pass`

MP4/JPG dùng `NCC36V0112` và `B01OPR`.

`QA-BDMA-001`

BDMA import finalized MP4 có valid MD5 và finalized JPG

`EV-DCAM-38-20260815-001`

`Pass`

BDMA import 2/2, 0 failed.

`QA-BDMA-002`

MP4 thiếu MD5 phải bị block import

`EV-DCAM-40-20260813-002`; `EV-DCAM-40-20260813-004`

`Pass`

BDMA reject missing MD5; source preserved.

`QA-BDMA-003`

MP4 sai MD5 phải bị block import

`EV-DCAM-40-20260813-003`

`Pass`

BDMA reject MD5 mismatch; source preserved.

`QA-BDMA-004`

JPG không cần `.md5` vẫn import đúng

`EV-DCAM-38-20260815-001`

`Pass`

JPG imported trong positive BDMA run.

`QA-BDMA-005`

BDMA ignore `Temp`/protected artifacts; cleanup không phá source artifacts

`EV-DCAM-38-20260815-001`; `EV-DCAM-40-20260813-009`; `EV-DCAM-40-20260811-013`

`Pass`

Temp/finalization, cleanup và source preservation evidence.

`QA-BDMA-006`

ADB disconnect/permission failure xử lý controlled, không modify/delete source sai

`EV-DCAM-40-20260811-016`; `EV-DCAM-40-20260811-017`

`Pass`

Permission recovery evidence cho camera/audio path.

`QA-BDMA-007`

MD5 generation failure giữ MP4, log lỗi, không import khi chưa ready

`EV-DCAM-40-20260817-002`

`Pass`

Sidecar generation failure được queue retry và retry thành công.

`QA-DB-003`

DB missing/corrupt/unreadable phải được recovery có kiểm soát

`EV-DCAM-40-20260817-004`

`Blocked`

Trigger chưa chứng minh Room/DAO thực sự mở hoặc ghi vào `dcam.db`.

`QA-CSON-002`

Invalid/unreadable CSON phải được recovery/fallback có kiểm soát

`EV-DCAM-40-20260817-007`

`Blocked`

Không tạo được invalid CSON fixture với metadata app-owned giống file thật.

`QA-STO-001`

File đang ghi/staged không expose như final media

`EV-DCAM-40-20260811-003`; `EV-DCAM-40-20260811-004`; `EV-DCAM-40-20260812-003`

`Pass`

Staged/recovery behavior được ghi trong các manifest.

`QA-STO-002`

Final media chỉ đủ điều kiện BDMA khi valid file/readiness state

`EV-DCAM-38-20260815-001`; `EV-DCAM-40-20260813-002`; `EV-DCAM-40-20260813-003`

`Pass`

Positive import và MD5 gate negative cases.

`QA-STO-003`

Build 0.1 internal-only storage; External/Auto không active

`EV-DCAM-38-20260815-001`; `EV-DCAM-40-20260810-001`; `EV-DCAM-40-20260810-002`

`Pass with recorded blocked case`

Internal/reference run pass; storage full case `EV-DCAM-40-20260817-001` vẫn blocked.

`QA-STO-004`

Runtime storage failure safe-stop/finalize nếu có thể, không fallback sai

`EV-DCAM-40-20260817-001`

`Blocked`

Process chết trước khi recorder thực sự gặp ENOSPC.

`QA-WRS-OP-001`

Không login UI; fixed operator `B01OPR` nhất quán

`EV-DCAM-38-20260815-001`

`Pass`

Filename và operator token trong evidence là `B01OPR`.

`QA-WRS-STATUS-001`

Battery/storage/GPS basic status, không GPS route/tracking

`EV-DCAM-38-20260815-001`

`Pass`

Pin/storage/GPS basic status đã thu trong WRS evidence.

Coverage note: các case bắt buộc liên quan DCAM-6 đã được đưa vào coverage map và đã có execution attempt trong DCAM-38/DCAM-40. Các case chưa có kết luận `Pass` được ghi là `Blocked`; không đổi thành `Pass` hoặc `Fail`.

## 5. Kết quả từng subtask

Task

Vai trò

Kết quả

Nhận xét

DCAM-42

Assemble candidate / lấy thông số candidate

`Pass`

Candidate commit và APK hash được chứng minh trong DCAM-38 evidence; không yêu cầu evidence package riêng nếu phạm vi chỉ là thông số/candidate.

DCAM-38

Chạy WRS end-to-end

`Pass`

Evidence chính chứng minh APK chạy, MP4 trên 30 giây, JPG finalized, MD5 khớp và BDMA import 2/2 thành công.

DCAM-40

Kiểm tra failure/compatibility

`Pass with blocked cases`

20 case Pass, 3 case Blocked, 0 Fail. Các case Blocked phải giữ nguyên, không được gộp thành Pass.

DCAM-39

Tổng hợp blocked cases, ghi chú phạm vi và handoff QA

`Documented / handoff pending`

Tài liệu tổng hợp đã có; còn cần review, update Jira và QA acknowledgment nếu chưa được ghi nhận.

DCAM-6

Parent integration result

`Pass with recorded blocked cases`

Luồng kỹ thuật chính đã đạt; các case blocked và workflow review/handoff được ghi riêng.

## 6. Evidence chính

Evidence

Giá trị

Evidence ID

`EV-DCAM-38-20260815-001`

Path

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-4\DCAM-38\20260815-001`

Device

BWC / `KF5OF2126040800171`

Android/API

Android 12 / API 31

Firmware

`877AOOAKN1_RK2_V009`

Candidate commit

`8928ab7d6806e815247ac5def51953bd27fbc3a8`

APK SHA-256

`42CA4F3244EB843AD6C9DC7227350F8694B71A025BBD337B638ADF239B4CEF77`

BDMA commit

`e728e498b8c37610b88fb7254ac46bce1f6a3dff`

## 7. BDMA import result

BDMA positive import đạt `Pass`.

Item

Result

Device validation

`Pass`

Queue result

`total=2, passed=2, failed=0`

Image imported

`DCAM_NCC36V0112_B01OPR_20260815_083057.jpg`

Video imported

`DCAM_NCC36V0112_B01OPR_20260815_082900.mp4`

Video playback

`Pass`

Hash comparison

Imported MP4/JPG khớp source

Auto-delete

OFF; source trên thiết bị còn nguyên

Ý nghĩa: BDMA đã đọc/import được sample WRS hợp lệ và không có bằng chứng mất dữ liệu hoặc corruption trong luồng positive import.

## 8. Failure/compatibility summary

DCAM-40 được dùng để bổ sung bằng chứng failure behavior và compatibility quanh WRS candidate.

### 8.1 Kết quả DCAM-40

Kết quả

Số lượng

`PASS`

20

`BLOCKED`

3

`FAIL`

0

### 8.2 Các case `BLOCKED`

DCAM-40 Case

QA Matrix Case

Nội dung kiểm thử

Kết quả execution

Lý do

`D6-FAIL-001-03`

`QA-STO-004`

Storage failure phải safe-stop/finalize đúng và không fallback sai.

`BLOCKED`

Process DCAM chết trước khi recorder thực sự gặp `ENOSPC`.

`D6-FAIL-004-02`

`QA-DB-003`

DB corrupt/unreadable phải được recovery có kiểm soát.

`BLOCKED`

Trigger chưa chứng minh Room/DAO thực sự mở hoặc ghi vào `dcam.db`.

`D6-FAIL-004-05`

`QA-CSON-002`

Invalid/unreadable CSON phải được recovery/fallback có kiểm soát.

`BLOCKED`

Không tạo được invalid CSON fixture với metadata app-owned giống file thật.

### 8.3 Chi tiết Evidence

#### `D6-FAIL-001-03`

Evidence ID: `EV-DCAM-40-20260817-001`.

Storage đạt `0 KB available` và `100% usage`.

Process DCAM đã dừng trước thời điểm recorder gặp `ENOSPC`.

Video recovery và video hậu kiểm vẫn có MD5 hợp lệ.

Chưa có execution result cho hành vi recorder khi nhận `ENOSPC`.

#### `D6-FAIL-004-02`

Evidence ID: `EV-DCAM-40-20260817-004`.

Corrupt `dcam.db` 55 byte được inject đúng hash.

Navigation trigger được xác nhận.

Chưa có bằng chứng Room/SQLite/DAO thực sự mở fixture corrupt.

DB gốc được restore đúng hash.

#### `D6-FAIL-004-05`

Evidence ID: `EV-DCAM-40-20260817-007`.

Invalid CSON fixture được tạo đúng nội dung/hash.

Metadata fixture khác metadata app-owned của file gốc.

`chown` sang app owner trả về `Operation not permitted`.

App không được launch với invalid fixture.