# DCAM Product Vision

**Page ID**: 41648238  
**Version**: 10  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41648238

---


# DCAM Product Vision

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Product Vision

Version

Approved 2.5

Status

Approved

Approval Scope

Long-term product direction và target outcomes; không phải build, release hoặc Production approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

01  Product Management

Target Audience

PM/BA, Product Owner, Tech Lead, Developers, QA, Stakeholders

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Project Charter, DCAM Roadmap, DCAM MVP Scope, DCAM 9-Month Development Plan, DCAM Release & Build Applicability Matrix, DCAM Requirements Home, DCAM Architecture Home, DCAM Documentation Governance

## 1. Executive Summary

DCAM là ứng dụng Android chạy trực tiếp trên thiết bị BodyCamera do DVID cung cấp. Ứng dụng dùng để ghi video, chụp ảnh và tạo dữ liệu media tại hiện trường.

DCAM đóng vai trò là **nguồn tạo dữ liệu** trong hệ sinh thái BodyCamera của DVID. Dữ liệu do DCAM tạo ra sẽ được **BDMA Desktop** xử lý ở giai đoạn sau, bao gồm ingest, index, xem lại, backup, export và báo cáo.

Mục tiêu dài hạn của DCAM là trở thành ứng dụng camera ổn định, đáng tin cậy, dễ sử dụng trên thiết bị BodyCamera chuyên dụng, đồng thời tạo ra dữ liệu chuẩn hóa để các hệ thống phía sau như BDMA có thể quản lý và khai thác hiệu quả.

## 2. Product Vision Statement

DCAM hướng tới việc trở thành ứng dụng camera chuyên dụng cho BodyCamera, giúp người dùng ghi nhận video/hình ảnh tại hiện trường một cách tin cậy, đồng thời tạo ra dữ liệu media có cấu trúc, có metadata rõ ràng và tương thích với BDMA Desktop.

Vision của DCAM không chỉ là “ghi hình được”, mà là tạo ra một nền tảng dữ liệu BodyCamera ổn định, có thể mở rộng cho các năng lực nâng cao như Live Streaming, Push-to-Talk, Remote Device Management, Advanced User Management, Advanced Encryption và Full GPS Tracking Route.

## 3. Product Positioning

Area

Positioning

Product Role

Ứng dụng tạo dữ liệu media gốc trên thiết bị BodyCamera Android.

Ecosystem Role

Nguồn dữ liệu đầu vào cho BDMA Desktop và các hệ thống xử lý dữ liệu BodyCamera sau này.

Primary Value

Ghi nhận video/hình ảnh ổn định, có metadata và cấu trúc dữ liệu rõ ràng.

Differentiation

Không chỉ là camera app thông thường; DCAM được thiết kế để vận hành trong hệ sinh thái quản lý dữ liệu BodyCamera.

Long-term Direction

Mở rộng thành nền tảng capture + streaming + communication + device management + user operation trên BodyCamera.

## 4. Business Background

Trong hệ sinh thái BodyCamera, dữ liệu video/hình ảnh cần được tạo ra một cách ổn định, có cấu trúc và có thể truy xuất được sau khi thiết bị được kết nối với hệ thống quản lý dữ liệu.

Nếu ứng dụng ghi hình trên thiết bị không chuẩn hóa file, metadata, trạng thái file và log, BDMA sẽ gặp khó khăn trong việc ingest, index, tìm kiếm, backup, export và điều tra dữ liệu.

DCAM được định vị là lớp ứng dụng trên thiết bị BodyCamera, chịu trách nhiệm tạo dữ liệu đúng chuẩn ngay từ đầu.

BodyCamera Android Device
        ↓
DCAM
        ↓ tạo video / image / metadata / logs
        ↓
BDMA Desktop
        ↓ ingest / index / view / backup / export / report
## 5. Target Users & Personas

User Group

Description

Primary Needs

Người dùng BodyCamera tại hiện trường

Người trực tiếp sử dụng thiết bị BodyCamera để ghi nhận sự kiện, công việc hoặc tình huống thực tế.

Ghi video/chụp ảnh nhanh, ổn định, ít thao tác, không mất dữ liệu.

Nhân sự vận hành/giám sát

Người kiểm tra tình trạng thiết bị, dữ liệu được ghi nhận và khả năng sử dụng của thiết bị.

Dữ liệu đáng tin cậy, trạng thái thiết bị rõ ràng, dễ kiểm tra lỗi.

Nhân sự xử lý dữ liệu trên BDMA

Người dùng BDMA để ingest, xem lại, tìm kiếm, backup và export dữ liệu.

File và metadata phải đọc được, mapping đúng thiết bị/thời gian/trạng thái.

Đội kỹ thuật / hỗ trợ

Người phân tích log, lỗi thiết bị, lỗi ghi hình hoặc lỗi đồng bộ với BDMA.

Log đầy đủ, trạng thái file rõ, có thể debug được.

Quản lý / khách hàng pilot

Người đánh giá khả năng triển khai thực tế của hệ thống BodyCamera.

Luồng end-to-end ổn định, có báo cáo, có khả năng mở rộng.

## 6. Problems We Solve

Problem

Meaning

Product Direction

Ghi hình chưa đủ ổn định trên thiết bị BodyCamera

Video/hình ảnh có thể lỗi, mất, không lưu đúng hoặc khó phục hồi khi có sự cố.

Ưu tiên reliability-first cho recording/capture.

Dữ liệu media thiếu cấu trúc

File không có naming/folder/status rõ ràng khiến BDMA khó ingest.

Chuẩn hóa folder structure, file naming và file status.

Metadata chưa đầy đủ

Thiếu timestamp, device id, GPS, status hoặc schema version.

Tạo metadata ngay tại nguồn dữ liệu.

Khó xử lý lỗi khi app crash/mất nguồn/storage đầy

Dễ phát sinh file corrupt hoặc dữ liệu không xác định trạng thái.

Thiết kế trạng thái completed/pending/corrupted và recovery direction.

Khó mở rộng tính năng nâng cao

Live streaming, PTT, remote management, user management, encryption, GPS route cần kiến trúc mở.

Thiết kế DCAM theo hướng modular và extensible.

BDMA phụ thuộc vào dữ liệu DCAM

Nếu DCAM không chuẩn hóa dữ liệu, BDMA phải xử lý nhiều exception.

DCAM phải BDMA-compatible by design.

## 7. Product Goals

Goal

Description

Stable Recording

Ghi video ổn định trên BodyCamera Android.

Reliable Image Capture

Chụp ảnh và lưu file chính xác.

BDMA Compatibility

Dữ liệu tạo ra phải tương thích với BDMA Desktop.

Structured Storage

File media, metadata và logs phải có cấu trúc rõ ràng.

Device-aware Behavior

Ứng dụng cần nhận biết trạng thái thiết bị như pin, storage, GPS, camera, microphone.

Operational Reliability

Hạn chế lỗi mất dữ liệu trong các tình huống bất thường.

Platform Foundation

Có nền tảng quản lý Device/User đủ sớm để hỗ trợ BDMA integration và các tính năng realtime về sau.

Extensible Platform

Kiến trúc có thể mở rộng cho live streaming, PTT, GPS route, encryption và remote management.

## 8. Product Principles

Principle

Description

Reliability First

Ưu tiên ghi hình/chụp ảnh ổn định hơn các tính năng phụ.

BDMA-compatible by Design

Thiết kế dữ liệu phải phục vụ BDMA ngay từ đầu.

Simple Field Operation

Người dùng tại hiện trường thao tác càng ít càng tốt.

Recoverable Data

Khi có lỗi, dữ liệu phải có khả năng phục hồi hoặc xác định trạng thái tối đa.

Observable System

App cần có log/trạng thái để đội kỹ thuật debug được.

Extensible Metadata

Metadata cần có version để mở rộng sau này.

Security-aware Design

Các phần media, metadata và transmission cần được thiết kế sẵn hướng bảo mật/mã hóa.

Hardware-aware Design

Thiết kế phải dựa trên giới hạn thực tế của BodyCamera: pin, storage, camera, GPS, microphone, network.

## 9. Product Capability Direction

Mục này mô tả định hướng năng lực sản phẩm ở mức tổng quan. Các chi tiết triển khai cụ thể như folder structure, metadata fields, status values, schema version, user stories và acceptance criteria được quản lý trong các tài liệu chuyên biệt như **DCAM MVP Scope**, **DCAM Requirements Home**, các Functional Requirements pages và **DCAM-BDMA Data Contract**.

Build/phase applicability không được suy luận từ Product Vision. **DCAM Release & Build Applicability Matrix** là source of truth để xác định capability nào đang required, conditional, deferred hoặc not applicable cho từng build.

### 9.1 Core Capture

DCAM cần cung cấp năng lực ghi video và chụp ảnh ổn định trên thiết bị BodyCamera, với thao tác đơn giản, phù hợp cho người dùng tại hiện trường.

### 9.2 Structured Media Data

DCAM cần tạo dữ liệu media có cấu trúc, có metadata và có trạng thái rõ ràng để các hệ thống phía sau có thể ingest, index, tìm kiếm, xem lại, backup và export dữ liệu một cách ổn định.

### 9.3 BDMA Compatibility

DCAM phải được thiết kế để tương thích với BDMA Desktop ngay từ đầu. Mục tiêu là giúp BDMA có thể xử lý dữ liệu DCAM theo luồng end-to-end mà không cần xử lý nhiều ngoại lệ hoặc rework lớn.

### 9.4 Operational Reliability

DCAM cần ưu tiên khả năng vận hành ổn định trong các tình huống thực tế như storage hạn chế, GPS không ổn định, app bị gián đoạn, thiết bị mất nguồn hoặc camera/microphone hoạt động không ổn định.

### 9.5 Platform Foundation

DCAM cần có nền tảng quản lý thiết bị và người dùng đủ sớm, bao gồm Device Information, Device Status, basic Remote Device Management, User Profile, Operator, Role và Permission foundation. Các năng lực này được định hướng ở **Phase 2 – Platform Foundation & BDMA Integration** để giảm rework cho BDMA, Live Streaming và PTT.

### 9.6 Advanced Communication Capabilities

Sau khi nền tảng Device/User đã có, DCAM sẽ mở rộng sang các năng lực giao tiếp/thời gian thực như Live Streaming, Push-to-Talk, Full GPS Tracking Route và Advanced Encryption ở **Phase 3 – Advanced Communication**.

## 10. Product Evolution Roadmap

Stage

Product Focus

Expected Outcome

Android Training

Android environment, Java on Android, lifecycle, permission, camera sample, storage sample, GPS sample and BodyCamera debugging basics.

Team Java/Desktop đủ năng lực bắt đầu phát triển Android trên BodyCamera.

Phase 1 – MVP Foundation

Video recording, image capture, local storage, metadata, logs and basic device status.

DCAM MVP Internal Build 0.1 có thể tạo dữ liệu media cơ bản trên BodyCamera.

Phase 2 – Platform Foundation & BDMA Integration

DCAM-BDMA Data Contract, BDMA ingest, metadata mapping, file recovery, GPS per media file, basic encryption, Remote Device Management and Advanced User Management foundation.

Secure Platform MVP Build 0.2: DCAM và BDMA chạy end-to-end, đồng thời có nền tảng Device/User để giảm rework cho các tính năng sau.

Phase 3 – Advanced Communication

Live Streaming, Push-to-Talk, Full GPS Tracking Route, Advanced Encryption, reconnect/timeout handling and streaming/PTT state logging.

Advanced Communication Beta Build 0.3 đạt mức beta/basic implementation cho các năng lực realtime.

Phase 4 – Hardening & Customer Pilot

Regression, stability, performance, security review, release notes, installation guide, test report and known issues.

Customer Pilot Release / Production Candidate sẵn sàng triển khai thử nghiệm với khách hàng chọn lọc.

Future Platform

Fleet management, cloud integration, analytics, advanced monitoring, OTA and extended operations.

DCAM mở rộng thành nền tảng capture/communication/device management cho BodyCamera.

Applicability của từng stage/build được quản lý tại **DCAM Release & Build Applicability Matrix**.

## 11. Relationship with BDMA Ecosystem

Area

Relationship

Media Files

DCAM tạo video/image; BDMA ingest, index và hiển thị.

Metadata

DCAM tạo metadata; BDMA sử dụng để search/filter/report.

Device ID

DCAM gắn device id; BDMA dùng để mapping dữ liệu với thiết bị.

User / Operator

DCAM cần tạo hoặc truyền ngữ cảnh user/operator để BDMA có thể mapping dữ liệu với người vận hành khi scope Phase 2 được triển khai.

Timestamp

DCAM gắn timestamp; BDMA hiển thị và dùng để lọc dữ liệu.

GPS

DCAM ghi GPS nếu có; BDMA có thể hiển thị vị trí hoặc route.

Logs

DCAM tạo log; BDMA hoặc đội kỹ thuật sử dụng để debug.

Data Contract

DCAM và BDMA phải thống nhất cấu trúc dữ liệu để tránh rework.

## 12. Success Metrics

Metric

Meaning

Recording Success Rate

Tỷ lệ ghi video thành công trên BodyCamera.

Image Capture Success Rate

Tỷ lệ chụp ảnh và lưu file thành công.

File Corruption Rate

Tỷ lệ file lỗi hoặc không đọc được.

Metadata Completeness

Tỷ lệ file có đủ metadata bắt buộc.

BDMA Import Success Rate

Tỷ lệ dữ liệu DCAM được BDMA ingest thành công.

Device/User Mapping Readiness

Mức độ sẵn sàng của nền tảng device/user để phục vụ BDMA và các tính năng nâng cao.

GPS Availability Rate

Tỷ lệ file/session có GPS hợp lệ khi thiết bị hỗ trợ.

App Crash Rate

Tỷ lệ app crash trong quá trình recording/capture.

Storage Handling Reliability

Khả năng xử lý khi storage gần đầy hoặc đầy.

Pilot Feedback Score

Mức độ đáp ứng nhu cầu thực tế trong giai đoạn pilot.

## 13. Vision Boundaries

Area

Boundary

MVP vs Future

MVP tập trung vào capture, storage, metadata, logs và BDMA compatibility; advanced capabilities được triển khai theo roadmap và applicability matrix.

BodyCamera Scope

DCAM chỉ định hướng cho thiết bị BodyCamera do DVID cung cấp, không phải public Android camera app.

BDMA Dependency

DCAM không thay thế BDMA; DCAM tạo dữ liệu, BDMA quản lý và khai thác dữ liệu.

Platform Foundation

Remote Device Management và Advanced User Management được đưa vào Phase 2 ở mức foundation/basic implementation, không phải full fleet management hoặc enterprise IAM.

Advanced Communication

Live Streaming, PTT, Full GPS Route và Advanced Encryption thuộc Phase 3 ở mức beta/basic implementation.

Production Readiness

Customer Pilot / Production Candidate không đồng nghĩa tất cả advanced capabilities đã production-hardened đầy đủ.

## 14. Related Documents

Document

Purpose

DCAM Project Charter

Tài liệu khởi động dự án, scope, governance và approval baseline.

DCAM Roadmap

Lộ trình phát triển 9 tháng.

DCAM MVP Scope

Phạm vi MVP và planned scope.

DCAM 9-Month Development Plan

Kế hoạch triển khai roadmap thành sprint, phase, buffer và deliverables.

DCAM Release & Build Applicability Matrix

Source of truth cho feature/requirement/test applicability của từng build.

DCAM Requirements Home

Trang điều hướng Functional Requirements 01–10 và Requirements-level documents.

DCAM Architecture Home

Kiến trúc chính thức, module, data flow, Technical Design và ADR navigation.

DCAM Android Training & Architecture Onboarding

Tài liệu onboarding Android cho team Java/Desktop.

DCAM-BDMA Data Contract

Chuẩn dữ liệu giữa DCAM Android và BDMA Desktop.

DCAM Non-functional Requirements

Yêu cầu hiệu năng, bảo mật, độ ổn định, storage, battery và GPS.

DCAM Security & Encryption Design

Thiết kế bảo mật và mã hóa.

DCAM QA Test Strategy & Test Matrix

QA strategy, release validation và applicable test groups.

## 15. Practical Conclusion

Product Vision defines long-term product direction.
DCAM Requirements Home owns requirement navigation.
DCAM Architecture Home owns architecture/technical navigation.
DCAM Release & Build Applicability Matrix decides when a capability becomes mandatory for a build.
Current active build is DCAM MVP Internal Build 0.1 with Working Recording Slice as the delivery gate.