# DCAM Project Charter

**Page ID**: 41156610  
**Version**: 26  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41156610

---


# DCAM Project Charter

Item

Information

Project

DCAM (Android BodyCamera Application)

Phase

Phase 2 – Android Application Development

Document Type

Project Charter

Version

Approved 1.6

Status

Approved

Approval Scope

Project objectives, governance, roles và project-direction baseline; Phase 2 Web Portal scope is limited by DEC-P2-WEB-01; active build applicability thuộc DCAM Release & Build Applicability Matrix; không phải Production approval.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

01  Product Management

Target Audience

Project Sponsor, PM/BA, Product Owner, Tech Lead, Developers, QA

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Product Vision, DCAM Roadmap, DCAM MVP Scope, DCAM Documentation Governance , [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Executive Summary

DCAM là ứng dụng Android chạy trên thiết bị BodyCamera do DVID phát triển, chịu trách nhiệm ghi video, chụp ảnh và tạo dữ liệu media phục vụ hệ sinh thái quản lý BodyCamera.

Giai đoạn 2 của dự án tập trung vào việc xây dựng ứng dụng DCAM hoàn chỉnh trên nền tảng Android, đồng thời đảm bảo dữ liệu được tạo ra có thể tích hợp liền mạch với hệ thống **BDMA Desktop**.

Kết quả mong muốn là một sản phẩm đủ ổn định để triển khai **Customer Pilot** sau 9 tháng phát triển.

## 2. Business Background

Hiện nay việc ghi nhận dữ liệu từ BodyCamera cần có một nền tảng thống nhất để phục vụ toàn bộ quy trình quản lý dữ liệu video/hình ảnh.

DCAM sẽ đóng vai trò là **nguồn tạo dữ liệu**, trong khi BDMA đóng vai trò **quản lý và khai thác dữ liệu**.

BodyCamera
      │
      ▼
DCAM Android
(Video / Image / Metadata / Logs)
      │
      ▼
BDMA Desktop
(Index / Viewer / Backup / Export / Investigation)
Hai sản phẩm sẽ cùng tạo thành hệ sinh thái quản lý dữ liệu BodyCamera của DVID.

## 3. Project Objectives

### 3.1 Business Objectives

Objective

Description

Chuẩn hóa nền tảng BodyCamera

Xây dựng ứng dụng DCAM thống nhất cho toàn bộ thiết bị BodyCamera của DVID.

Nâng cao khả năng quản lý dữ liệu

Dữ liệu được tạo ra phải tương thích với BDMA Desktop.

Hỗ trợ mở rộng sản phẩm

Thiết kế theo hướng dễ mở rộng cho các phiên bản sau.

Hỗ trợ triển khai pilot

Có bản Customer Pilot đủ ổn định sau 9 tháng.

### 3.2 Technical Objectives

Objective

Description

Video Recording

Ghi video ổn định trên thiết bị BodyCamera Android.

Image Capture

Chụp ảnh và lưu file ổn định.

Metadata

Chuẩn hóa metadata cho video/image.

Storage

Chuẩn hóa cấu trúc lưu trữ local trên thiết bị.

GPS

Hỗ trợ GPS theo file và GPS route theo recording session.

Encryption

Hỗ trợ mã hóa dữ liệu theo phạm vi đã chốt.

Live Streaming

Hỗ trợ live streaming ở mức beta/pilot.

Push-to-Talk (PTT)

Hỗ trợ giao tiếp âm thanh dạng Push-to-Talk ở mức beta/pilot.

Remote Device Management

Hỗ trợ đọc trạng thái/cấu hình thiết bị từ xa ở mức cơ bản.

User Management

Hỗ trợ user profile/operator mapping ở mức cơ bản.

## 4. Project Scope

### 4.1 In Scope

Module

Scope

Video Recording

Start/stop recording, recording state, interruption handling cơ bản.

Image Capture

Chụp ảnh, lưu ảnh, metadata cho ảnh.

Local Media Storage

Folder structure, file naming, file status.

Metadata Generation

file_id, device_id, timestamp, file_type, file_size, status, GPS nếu có.

Logging

Log app, recording, capture, storage, GPS, error.

Device Status

Pin, storage, GPS availability, app version.

GPS Tracking

GPS theo file và GPS route theo recording session.

BDMA Integration

Data Contract, folder reading, metadata mapping, BDMA ingest.

Encryption

Mã hóa dữ liệu theo từng phase.

Live Streaming

Beta feature trong Phase 3.

Push-to-Talk (PTT)

Beta feature trong Phase 3.

Remote Device Management

Basic implementation trong Phase 3.

User Management

Basic role/profile/operator mapping.

DCAM Device Provisioning Web Portal

Phase 2 / Secure Platform MVP Build 0.2: factory-focused minimum provisioning only under DEC-P2-WEB-01; active applicability and release gate remain owned by the Matrix.

### 4.2 Out of Scope

Item

Description

Cloud Video Management Platform

Không xây dựng nền tảng cloud quản lý video trong Phase 2.

AI Video Analytics

Không triển khai AI phân tích video trong Phase 2.

Facial Recognition

Không triển khai nhận diện khuôn mặt.

Vehicle ANPR

Không triển khai nhận diện biển số xe.

Third-party VMS Integration

Không tích hợp VMS bên thứ ba trong Phase 2.

Public Mobile Application

Không hỗ trợ điện thoại Android phổ thông/public app.

General-purpose / Customer / Fleet Web Portal

Không xây dựng portal customer/public, general administration hoặc fleet-management trong Phase 2. Chỉ DCAM Device Provisioning Web Portal tối thiểu theo [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence) được phép.

## 5. Deliverables

Deliverable

Description

Android Source Code

Mã nguồn DCAM đầy đủ.

APK/AAB

Gói cài đặt ứng dụng.

Technical Documentation

Bộ tài liệu kỹ thuật của ứng dụng Android.

Product Documentation

Product Vision, MVP Scope, Charter, Roadmap.

DCAM-BDMA Data Contract

Chuẩn dữ liệu giữa DCAM và BDMA.

Release Notes

Ghi chú phát hành cho từng bản release.

Installation Guide

Hướng dẫn cài đặt trên BodyCamera.

User Guide

Hướng dẫn sử dụng cơ bản.

Test Report

Báo cáo kiểm thử.

Known Issues

Danh sách lỗi đã biết và workaround nếu có.

## 6. Project Roadmap

Phase

Timeline

Target

Output

Android Training

2 weeks

Team readiness

Android camera sample/prototype.

Phase 1

Month 1–3

MVP Foundation

DCAM MVP Internal 0.1.

Phase 2

Month 4–5

Platform Foundation & BDMA Integration

Secure MVP 0.2.

Phase 3

Month 6–7

Advanced Communication & Customer Pilot Preparation

Advanced Beta 0.3.

Phase 4

Month 8–9

Hardening, QA & Release

Customer Pilot Release.

## 7. Success Criteria

Category

Target

Recording

Video recording hoạt động ổn định trên BodyCamera.

Capture

Image capture hoạt động ổn định.

Metadata

Metadata đầy đủ theo Data Contract.

BDMA Integration

BDMA ingest được dữ liệu DCAM end-to-end.

Encryption

Encryption hoạt động đúng theo phạm vi đã chốt.

GPS

GPS theo file và GPS route hoạt động đúng trong điều kiện thiết bị hỗ trợ.

Streaming

Live Streaming hoạt động ở mức beta/pilot.

PTT

Push-to-Talk hoạt động ở mức beta/pilot.

Remote Management

Có thể đọc trạng thái/cấu hình thiết bị ở mức cơ bản.

Pilot Release

Có bản Customer Pilot Release với tài liệu và test report đi kèm.

## 8. Stakeholders

Role

Responsibility

Project Sponsor

Định hướng và phê duyệt tổng thể.

Project Manager

Lập kế hoạch, quản lý tiến độ, điều phối release và tài liệu.

Product Owner

Quản lý yêu cầu nghiệp vụ và ưu tiên sản phẩm.

Android Team

Phát triển ứng dụng DCAM Android.

BDMA Team

Tích hợp, kiểm thử Data Contract và luồng ingest.

QA Team

Kiểm thử, nghiệm thu và báo cáo lỗi.

Customer / Pilot Users

Sử dụng thử, phản hồi và xác nhận tính phù hợp thực tế.

## 9. Risks

Risk

Impact

Mitigation

Camera API không ổn định trên BodyCamera

Ảnh hưởng chức năng cốt lõi

Prototype và kiểm thử sớm trên thiết bị thật.

Video corrupt khi mất nguồn/app crash

Rủi ro mất dữ liệu

Thiết kế file status và recovery mechanism.

GPS không chính xác hoặc không ổn định

Ảnh hưởng map/route

GPS validation, gps_status và logging.

Data Contract thay đổi nhiều

Rework cả DCAM và BDMA

Versioning và review chung giữa DCAM & BDMA.

Thiếu dữ liệu test

Khó kiểm thử GPS/BDMA ingest/streaming

Chuẩn bị bộ dữ liệu test chuẩn.

Live Streaming/PTT phụ thuộc network

Tính năng không ổn định trong pilot

Test network yếu, timeout, reconnect và fallback.

Encryption ảnh hưởng performance

Ghi hình chậm hoặc app không ổn định

Benchmark trên thiết bị thật trước khi chốt giải pháp.

## 10. Project Governance

Topic

Description

Development Method

Agile Scrum.

Sprint Length

4 weeks.

Weekly Team Meeting

Every Saturday.

Sprint Planning

Every Monday hoặc đầu sprint.

Documentation

Confluence.

Task Management

Jira.

Source Control

Git.

Code Review

Pull Request.

Release Management

Theo DCAM Release Plan.

Decision Tracking

Sử dụng ADR cho các quyết định kỹ thuật quan trọng nếu cần.

## 11. Project Assumptions & Constraints

### 11.1 Assumptions

ID

Assumption

Change Impact

A-01

Thiết bị BodyCamera sử dụng hệ điều hành Android được DVID hỗ trợ.

Có thể phải thay đổi kiến trúc ứng dụng.

A-02

Camera, Microphone, GPS và Storage API hoạt động ổn định trên thiết bị.

Ảnh hưởng trực tiếp tới các chức năng cốt lõi.

A-03

BDMA Desktop sẽ được phát triển song song và hỗ trợ Data Contract đã thống nhất.

Có thể phát sinh rework nếu Data Contract thay đổi.

A-04

Team dự án duy trì đủ năng lực Android/BDMA trong suốt Phase 2.

Ảnh hưởng tới tiến độ roadmap.

A-05

Thiết bị phục vụ phát triển và kiểm thử luôn sẵn sàng.

Thiếu thiết bị sẽ ảnh hưởng kiểm thử thực tế.

A-06

Dữ liệu GPS được cung cấp bởi thiết bị khi có tín hiệu hợp lệ.

Một số chức năng Map/GPS sẽ hoạt động hạn chế nếu không có tín hiệu.

### 11.2 Constraints

ID

Constraint

C-01

Ứng dụng chỉ hỗ trợ BodyCamera do DVID cung cấp.

C-02

Không hỗ trợ điện thoại Android thông thường trong Phase 2.

C-03

Dữ liệu phải tương thích với BDMA Desktop.

C-04

Ứng dụng phải ưu tiên hoạt động trong môi trường offline/local.

C-05

Thời gian thực hiện Phase 2 dự kiến 9 tháng.

C-06

Các thay đổi Data Contract phải được thống nhất giữa team DCAM và BDMA trước khi triển khai.

C-07

Các chức năng mới không được làm ảnh hưởng đến khả năng ghi hình ổn định của thiết bị.

## 12. Communication Plan

### 12.1 Communication Matrix

Activity

Frequency

Owner

Participants

Purpose

Output

Sprint Planning

Hàng tuần / đầu sprint

PM

PM, Dev Team, QA nếu cần

Chốt mục tiêu sprint, phạm vi công việc và ưu tiên triển khai.

Sprint Backlog

Daily Discussion

Hàng ngày khi cần

Dev Team

Dev Team, Tech Lead nếu cần

Cập nhật tiến độ, phát hiện blocker và xử lý nhanh vấn đề phát sinh.

Progress Update / Blocker List

Weekly Team Meeting

Hàng tuần

PM

PM, Dev Team, QA, Tech Lead nếu cần

Tổng hợp tiến độ tuần, rủi ro, issue nổi bật và kế hoạch tuần tiếp theo.

Weekly Meeting Minutes

Architecture Review

Khi có thay đổi lớn

Tech Lead

PM, Tech Lead, Android Lead, BDMA Lead nếu liên quan

Review thay đổi kiến trúc, Data Contract, module boundary hoặc technical decision quan trọng.

Technical Decision / ADR nếu cần

Milestone Review

Cuối mỗi phase

PM

PM, Sponsor, Product Owner, Tech Lead, QA Lead

Đánh giá kết quả phase, scope hoàn thành, rủi ro và readiness cho phase tiếp theo.

Phase Review Report

Release Review

Trước mỗi release

PM / QA Lead

PM, QA, Dev Team, Tech Lead

Kiểm tra release readiness, known issues, test result và tài liệu đi kèm.

Release Checklist / Release Notes

### 12.2 Communication Channels

Channel

Purpose

Jira

Quản lý task, epic và sprint.

Confluence

Quản lý tài liệu dự án.

Git Repository

Source code management.

Pull Request

Code review.

Zalo

Thảo luận nhanh.

### 12.3 Reporting

Report

Owner

Frequency

Weekly Team Meeting

PM

Weekly.

Sprint Report

PM

Every Sprint.

Risk Report

PM

Monthly.

Release Report

PM

Every Release.

Incident Report

Dev / QA

Khi phát sinh.

### 12.4 Documentation Rules

Rule

Description

Jira-first requirement

Mọi yêu cầu mới phải được ghi nhận trên Jira trước khi phát triển.

Confluence update

Các thay đổi kiến trúc phải được cập nhật trên Confluence.

Release Notes

Mỗi release phải có Release Notes.

ADR

Quyết định kỹ thuật quan trọng nên được lưu thành Architecture Decision Record nếu cần.

## 13. Change Management Process

### 13.1 Objective

Đảm bảo mọi thay đổi về phạm vi, yêu cầu hoặc thiết kế đều được đánh giá, phê duyệt và theo dõi đầy đủ trước khi triển khai.

### 13.2 Change Workflow

Requirement / Issue
        │
        ▼
Create Jira Task
        │
        ▼
Impact Analysis
        │
        ▼
PM Review
        │
        ▼
Technical Review
        │
        ▼
Approval
        │
        ▼
Implementation
        │
        ▼
Testing
        │
        ▼
Documentation Update
### 13.3 Change Classification

Type

Example

Requirement Change

Thêm chức năng mới.

Technical Change

Thay đổi kiến trúc Android.

Data Contract Change

Thay đổi metadata hoặc folder structure.

UI/UX Change

Điều chỉnh giao diện người dùng.

Security Change

Thay đổi cơ chế encryption.

Bug Fix

Sửa lỗi.

### 13.4 Approval Matrix

Change Level

Approval Required

Minor Bug Fix

Tech Lead.

Small Enhancement

PM.

Functional Change

PM + Product Owner.

Data Contract Change

PM + DCAM Lead + BDMA Lead.

Architecture Change

PM + Tech Lead.

Major Scope Change

Project Sponsor.

### 13.5 Change Principles

Principle

Description

Protect Data Contract

Không thay đổi Data Contract khi chưa đánh giá ảnh hưởng đến BDMA.

Jira Tracking

Mọi thay đổi phải có Jira task và được liên kết với tài liệu liên quan.

Documentation Update

Nếu thay đổi ảnh hưởng kiến trúc/API, tài liệu Confluence phải được cập nhật trước release.

Release Safety

Không triển khai thay đổi trực tiếp lên bản release đang phát hành nếu chưa có đánh giá rủi ro.

## 14. Related Documents

Document

Purpose

DCAM Product Vision

Định hướng sản phẩm.

DCAM MVP Scope

Phạm vi MVP và planned scope.

DCAM Roadmap

Kế hoạch 9 tháng.

DCAM-BDMA Data Contract

Chuẩn dữ liệu giữa DCAM và BDMA.

DCAM Android Architecture

Kiến trúc Android application.

DCAM Functional Requirements

Yêu cầu chức năng.

DCAM Non-functional Requirements

Yêu cầu phi chức năng.

DCAM Security & Encryption Design

Thiết kế bảo mật/mã hóa.

DCAM Live Streaming & PTT Design

Thiết kế live streaming và PTT.

DCAM Acceptance Criteria & Test Plan

Tiêu chí nghiệm thu và kế hoạch test.

DCAM Release Plan

Kế hoạch release.

## 15. Approval

Role

Name

Status

Project Sponsor

DVID

Approved

Product Owner

Hoàng Ngọc Quyền

Approved

Project Manager

Hoàng Ngọc Quyền

Approved

Technical Lead

Hoàng Ngọc Quyền

Approved

BDMA Lead

Dinh Nhan

Approved