# 01 - Recording & Capture Requirements

**Page ID**: 47743356  
**Version**: 8  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743356

---


# 01 - Recording & Capture Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.5

Status

Approved

Approval Scope

Recording requirements và Build 0.1 checksum/finalization profile

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, QA

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Requirements Home, DCAM Requirement–Design–Test Traceability Matrix, DCAM-BDMA Data Contract, DCAM MVP Scope, 09 - System Settings Requirements, DCAM Recording & Capture Design, DCAM State Machine Design, DCAM Non-functional Requirements, DCAM Security & Encryption Design, 06 - Cloud Services, Update & Configuration Architecture, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này ghi nhận các yêu cầu chức năng liên quan đến **recording và capture** trên thiết bị BodyCamera.

Các yêu cầu trong trang này là baseline cho **DCAM Recording & Capture Design**, **DCAM State Machine Design**, **DCAM Storage Design** và các test case liên quan đến video/image/audio capture.

Exact encryption algorithm, mode, key generation/storage/rotation/recovery và BDMA decryption boundary không thuộc tài liệu Requirements này. Các quyết định đó thuộc **DCAM Security & Encryption Design** và chỉ có hiệu lực khi một approved Security Profile được ban hành.

## 2. Recording & Capture Scope

Requirement Area

Initial Description

Status

Video Recording

DCAM phải hỗ trợ quay video và lưu file `.mp4` theo Data Contract.

Approved

Image Capture

DCAM phải hỗ trợ chụp ảnh và lưu file `.jpg`.

Approved

Audio Capture

DCAM phải hỗ trợ ghi âm và lưu file `.mp3`, `.aac` hoặc `.wav` nếu feature được bật.

Approved

Important Media

DCAM phải hỗ trợ đánh dấu media quan trọng bằng suffix `_IMP`.

Approved

Pre-record

DCAM phải hỗ trợ pre-record bằng rolling cache trước khi user bắt đầu recording chính thức.

Approved

Post-record

DCAM phải hỗ trợ post-record bằng cách tiếp tục ghi thêm một khoảng thời gian sau khi user yêu cầu stop.

Approved

Emergency Situation Detection

DCAM phải hỗ trợ hướng phát hiện tình huống khẩn cấp, ví dụ người đeo BodyCamera bị té ngã, và lưu emergency video clip với pre-record + post-record quanh thời điểm detect.

Approved Direction

Future SOS Alert

Khi future WebServer capability khả dụng, DCAM có thể gửi SOS Alert tới server sau khi emergency event được phát hiện.

Future / Approved Direction

Recording State

DCAM cần quản lý trạng thái start / preparing / recording / post-recording / stop / finalizing / completed / emergency / error.

Approved

## 3. Video Recording Requirements

ID

Requirement

Status

REC-VID-001

DCAM phải hỗ trợ quay video định dạng `.mp4`.

Approved

REC-VID-002

Video file phải tuân thủ naming convention và authoritative DEVICE_TOKEN/OPERATOR_TOKEN mapping trong DCAM-BDMA Data Contract.

Approved

REC-VID-003

Video important phải dùng suffix `_IMP` và/hoặc lưu trong `Media/IMP` theo Data Contract.

Approved

REC-VID-004

Nếu media encryption được bật theo approved Security Profile, encrypted video phải dùng suffix `_enc`; important encrypted video dùng `_IMP_enc` theo DCAM-BDMA Data Contract. Exact algorithm, mode và key management thuộc DCAM Security & Encryption Design.

Approved Direction / Security Review dependent

REC-VID-005

Build 0.1 yêu cầu mọi final video MP4 có MD5 sidecar; đối với build khác, applicability theo approved build profile.

Approved for Build DCAM MVP Internal Build 0.1

REC-VID-006

Video recording không được bị gián đoạn bởi AutoUpdate, remote config apply, diagnostics hoặc future WebServer features.

Approved

## 4. Image Capture Requirements

ID

Requirement

Status

REC-IMG-001

DCAM phải hỗ trợ chụp ảnh định dạng `.jpg`.

Approved

REC-IMG-002

Image file phải tuân thủ naming convention và authoritative DEVICE_TOKEN/OPERATOR_TOKEN mapping trong DCAM-BDMA Data Contract.

Approved

REC-IMG-003

Important image phải dùng suffix `_IMP` và/hoặc lưu trong `Media/IMP`.

Approved

REC-IMG-004

DCAM không tạo `.md5` cho image `.jpg`.

Approved

REC-IMG-005

Image capture phải xử lý lỗi camera/storage/permission an toàn, không làm app crash.

Approved

## 5. Audio Capture Requirements

ID

Requirement

Status

REC-AUD-001

DCAM phải hỗ trợ audio capture nếu feature được bật.

Approved

REC-AUD-002

Audio file format được hỗ trợ gồm `.mp3`, `.aac` hoặc `.wav` theo implementation được duyệt.

Approved

REC-AUD-003

Important audio phải dùng suffix `_IMP` và/hoặc lưu trong `Media/IMP`.

Approved

REC-AUD-004

DCAM không tạo `.md5` cho `.mp3`, `.aac`, `.wav`.

Approved

REC-AUD-005

Audio capture phải phụ thuộc vào microphone permission và audio setting đã được validate.

Approved

## 6. Pre-record Requirements

Pre-record là chức năng cho phép DCAM duy trì một rolling video cache trước khi user bắt đầu recording chính thức hoặc trước thời điểm emergency event được detect.

ID

Requirement

Status

REC-PRE-001

DCAM phải hỗ trợ bật/tắt pre-record bằng system setting.

Approved

REC-PRE-002

Pre-record duration phải cấu hình được trong khoảng `5–60` giây.

Approved

REC-PRE-003

Khi pre-record được bật và DCAM ở trạng thái sẵn sàng, camera phải duy trì rolling cache theo duration đã cấu hình.

Approved

REC-PRE-004

Khi user bắt đầu recording, DCAM phải đưa phần pre-record cache hợp lệ vào video recording chính thức nếu cache available.

Approved

REC-PRE-005

Pre-record cache không được xem là media hoàn chỉnh trước khi recording chính thức bắt đầu.

Approved

REC-PRE-006

Pre-record cache không được BDMA import trực tiếp.

Approved

REC-PRE-007

Pre-record phải tuân thủ storage, battery, permission và performance constraints trong Non-functional Requirements.

Approved

REC-PRE-008

Nếu pre-record cache bị lỗi hoặc không đủ duration, DCAM vẫn phải cho phép recording chính thức bắt đầu nếu camera/storage điều kiện chính vẫn hợp lệ.

Approved

REC-PRE-009

Emergency event có thể sử dụng pre-record cache hợp lệ để tạo emergency clip quanh thời điểm detect.

Approved Direction

## 7. Post-record Requirements

Post-record là chức năng cho phép DCAM tiếp tục ghi thêm một khoảng thời gian sau khi user yêu cầu stop recording hoặc sau khi emergency event được detect.

ID

Requirement

Status

REC-POST-001

DCAM phải hỗ trợ bật/tắt post-record bằng system setting.

Approved

REC-POST-002

Post-record duration phải cấu hình được trong khoảng `5–60` giây.

Approved

REC-POST-003

Khi user yêu cầu stop recording và post-record được bật, DCAM phải tiếp tục ghi thêm theo duration đã cấu hình trước khi finalize file.

Approved

REC-POST-004

Trong post-record period, UI phải thể hiện rõ recording đang ở trạng thái stopping/post-recording hoặc equivalent state.

Approved

REC-POST-005

Sau khi post-record hoàn tất, DCAM phải chuyển sang stop/finalize flow bình thường.

Approved

REC-POST-006

Nếu có lỗi trong post-record period, DCAM phải stop/finalize an toàn và ghi log lỗi.

Approved

REC-POST-007

AutoUpdate, remote config apply và cleanup vẫn không được interrupt trong post-record period.

Approved

REC-POST-008

Emergency event có thể trigger post-record để ghi tiếp sau thời điểm detect trước khi finalize emergency clip.

Approved Direction

## 8. Emergency Situation Detection Requirements

Emergency Situation Detection là capability phát hiện hoặc nhận tín hiệu tình huống khẩn cấp, ví dụ người đeo BodyCamera bị té ngã. Capability này phải được thiết kế theo hướng local-first: việc lưu emergency clip không phụ thuộc Internet/server.

ID

Requirement

Status

REC-EMG-001

DCAM phải hỗ trợ direction cho Emergency Situation Detection như fall detection, manual SOS button, impact detection hoặc future AI detection.

Approved Direction

REC-EMG-002

Khi emergency event được detect, DCAM phải tạo emergency event trong state machine.

Approved Direction

REC-EMG-003

DCAM phải lưu emergency video clip gồm pre-record segment + detection time + post-record segment nếu điều kiện camera/storage cho phép.

Approved Direction

REC-EMG-004

Emergency clip phải được đánh dấu Important Media bằng `_IMP` và/hoặc lưu trong `Media/IMP`.

Approved Direction

REC-EMG-005

Nếu media encryption được bật theo approved Security Profile, emergency encrypted clip phải dùng suffix `_IMP_enc` theo Data Contract. Exact algorithm, mode và key management thuộc Security Design.

Approved Direction / Security Review dependent

REC-EMG-006

`.md5` chỉ tạo cho final emergency `.mp4` nếu MD5 policy enabled.

Approved Direction

REC-EMG-007

Nếu đang không recording, emergency event phải tạo emergency recording session dựa trên pre-record/post-record.

Approved Direction

REC-EMG-008

Nếu đang recording, emergency event không được làm mất recording hiện tại; direction ưu tiên là split emergency segment khi khả thi.

Approved Direction

REC-EMG-009

Nếu split segment không khả thi, DCAM phải ghi emergency marker hoặc đánh dấu current recording là Important theo rule được thiết kế.

Approved Direction

REC-EMG-010

Khi Future WebServer khả dụng, DCAM có thể gửi SOS Alert tới server sau khi emergency event được detect hoặc sau khi local emergency clip được lưu.

Future / Approved Direction

REC-EMG-011

Nếu gửi SOS thất bại hoặc không có Internet, local emergency clip vẫn phải được giữ.

Approved Direction

REC-EMG-012

Emergency event và state transition phải được log nhưng không log raw media hoặc sensitive content.

Approved Direction

## 9. Recording State Requirements

Recording state machine phải hỗ trợ flow có/không có pre-record/post-record và flow emergency.

Baseline flow khi pre-record và post-record được bật:

Preview / Pre-record Buffering
    ↓ user starts recording
Preparing
    ↓
Recording
    ↓ user stops recording
Post-recording
    ↓
Stopping
    ↓
Finalizing
    ↓
Completed
    ↓
Preview / Pre-record Buffering
Emergency flow khi không recording:

Preview / Pre-record Buffering
    ↓ emergency detected
Emergency Recording
    ↓ post-record completed
Emergency Finalizing
    ↓
Emergency Saved
Emergency flow khi đang recording:

Recording
    ↓ emergency detected
Emergency Marker / Emergency Segment Decision
    ↓
Continue Recording or Split Emergency Segment

ID

Requirement

Status

REC-STATE-001

DCAM phải có controlled state cho preparing, recording, stopping, finalizing, completed và error.

Approved

REC-STATE-002

Nếu pre-record bật, state machine phải thể hiện trạng thái buffering/cache trước khi recording chính thức.

Approved

REC-STATE-003

Nếu post-record bật, state machine phải thể hiện trạng thái post-recording trước stopping/finalizing.

Approved

REC-STATE-004

Nếu emergency event xảy ra, state machine phải thể hiện Emergency Detected / Emergency Recording / Emergency Finalizing / Emergency Saved hoặc equivalent state.

Approved Direction

REC-STATE-005

Lỗi permission/camera/microphone/storage phải đi vào controlled Error/Recovery state.

Approved

REC-STATE-006

Mỗi transition quan trọng nên được ghi log để phục vụ QA và diagnostics.

Approved

## 10. Relationship with Other Documents

Document

Relationship

DCAM Requirement–Design–Test Traceability Matrix

Mapping từng Requirement ID active sang Build, Design/Contract, QA, Jira và evidence.

09 - System Settings Requirements

Định nghĩa settings cho pre-record/post-record, emergency detection và future SOS.

DCAM Recording & Capture Design

Chi tiết hóa rolling cache, recording flow, emergency clip, post-recording, finalize và interaction với storage.

DCAM State Machine Design

Mô tả state machine chính thức cho pre-record, recording, post-record, emergency, error/recovery.

DCAM Storage Design

Định nghĩa vị trí cache/temp và rule BDMA ignore cache/temp.

DCAM Non-functional Requirements

Định nghĩa performance, battery, storage, emergency reliability và recording stability constraints.

DCAM-BDMA Data Contract

Đảm bảo chỉ final media được expose cho BDMA import; emergency clip là Important Media và encrypted-media naming nếu feature active.

DCAM Security & Encryption Design

Sở hữu exact algorithm, mode, key management, encryption activation profile và BDMA decryption boundary.

06 - Cloud Services, Update & Configuration Architecture

Định hướng Future SOS Alert tới WebServer nhưng không phụ thuộc server để lưu local clip.

## 11. Notes

Chi tiết kỹ thuật về rolling cache, temp/cache storage, merge/append cache vào final video, emergency event source abstraction, SOS retry policy, error recovery và performance impact sẽ được bổ sung trong **DCAM Recording & Capture Design**, **DCAM State Machine Design** và các future WebServer/SOS technical design pages.

Encrypted naming requirement does not approve a cryptographic algorithm.
Encryption becomes implementation/release mandatory only when the Applicability Matrix activates an approved Security Profile.
## 14. Build 0.1 Controlled Requirements

Requirement ID

Requirement

Status

REC-VID-007

MP4 phải được finalize trước, sau đó MD5 được tạo bất đồng bộ; artifact chỉ được chuyển sang BDMA_READY sau khi MD5 thành công.

Approved for Build DCAM MVP Internal Build 0.1

REC-VID-008

Nếu MD5 generation failed, missing hoặc mismatch, DCAM phải giữ MP4, ghi log, duy trì Checksum Pending/Failed và không cho BDMA import.

Approved for Build DCAM MVP Internal Build 0.1

MD5 chỉ dùng cho integrity check; không phải encryption, authentication hoặc security signature. Exact state enum và performance threshold cần Technical Review/Device POC.