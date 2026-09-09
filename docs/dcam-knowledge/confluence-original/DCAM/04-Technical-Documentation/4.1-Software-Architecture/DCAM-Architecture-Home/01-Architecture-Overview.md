# 01 - Architecture Overview

**Page ID**: 47120395  
**Version**: 15  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120395

---


# 01 - Architecture Overview

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Software Architecture Document / Overview

Version

Approved 2.0

Status

Approved

Approval Scope

High-level system context, component boundary và architecture overview; detailed decisions thuộc authoritative Architecture/ADR/Contract pages.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / BDMA Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

4.1 - Software Architecture

Target Audience

PM/BA, Tech Lead, Android Developers, QA

Last Updated

2026-08-25

Related Jira

None

Related Documents

DCAM Architecture Home, DCAM Project Home, DCAM Product Vision, DCAM Roadmap, DCAM MVP Scope, DCAM Documentation Governance, 05 - User & Device Operation Requirements, DCAM-BDMA Data Contract, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, ADR - DCAM GMS-free Android Runtime Baseline, DDMP Architecture Overview & Reading Guide

## 1. Purpose

Trang này mô tả tổng quan kiến trúc phần mềm của **DCAM Android BodyCamera Application**.

Mục tiêu của trang này là giúp team hiểu:

DCAM nằm ở đâu trong hệ sinh thái BodyCamera.

DCAM tạo ra dữ liệu gì.

DCAM quản lý user/operator offline như thế nào ở mức tổng quan.

BDMA sử dụng và đồng bộ dữ liệu DCAM như thế nào.

Kiến trúc tổng thể đang hướng tới điều gì.

Những phần nào đã quyết định và phần nào còn TBD thực sự.

## 2. Architecture Context

DCAM là ứng dụng Android chạy trên thiết bị BodyCamera. Ứng dụng chịu trách nhiệm tạo dữ liệu media tại nguồn, bao gồm video, image, audio nếu có, metadata, GPS nếu có, operator attribution, device context, local database và log.

DCAM hoạt động offline-first. User/operator data và operator session được lưu trong `dcam.db` trên thiết bị. BDMA Desktop cũng quản lý user/operator data trong database của BDMA và đồng bộ hai chiều với DCAM qua ADB.

BDMA Desktop là hệ thống ingest, index, xem lại, backup, export, quản lý dữ liệu và quản lý user/operator do DCAM tạo ra hoặc đồng bộ với DCAM.

Cloud Services là lớp mở rộng tùy chọn đối với core recording/capture/storage/metadata/user authentication. DCAM production Android runtime vẫn bắt buộc GMS-free; Factory Portal chạy Spring Boot BFF + Thymeleaf và PostgreSQL ddmp là server/web provisioning boundary, không phải Google Play services dependency trên device. BFF là provider factory/provisioning đã được phê duyệt trong phạm vi DCAM. Khi Hybrid profile được kích hoạt ở Phase 2+ sau POC, DDMP cung cấp control-plane: BFF là API/desired-state/audit boundary, Headwind Community là control-plane giới hạn, và Cloudflare R2/CDN là artifact plane. DCAM vẫn là Device Owner/DPC duy nhất; Headwind Client chỉ chạy Application mode.

BodyCamera Android Device
        ↓
DCAM Android Application / sole Device Owner-DPC
        ├── Local Media / Metadata / User-Operator DB / Logs
        │       ↓
        │   BDMA Desktop via ADB → Ingest / Index / View / Backup / Export / Report
        │
        └── Phase 2+ Hybrid profile (POC-gated; Deferred for Build 0.1)
                ↓ outbound sync / ACK
            DDMP BFF → Headwind Community (limited control-plane)
                ↓ verified update manifest / artifact
            Cloudflare R2/CDN
## 3. Architecture Goals

Goal

Description

Reliability First

Ưu tiên không mất dữ liệu media/metadata/operator attribution trong các luồng chính.

Offline-first

Recording, capture, metadata, user authentication, storage và logging phải hoạt động khi không có Internet.

Operator-attributed Evidence

Normal recording/capture evidence phải gắn với authenticated operator session.

Emergency Override

Emergency recording có thể dùng system operator `EMERGENCY_OVERRIDE_ADMIN` khi chưa login.

BDMA-compatible by Design

Dữ liệu DCAM tạo ra phải dễ ingest, sync và mapping với BDMA.

Modular

Các module phải tách trách nhiệm rõ để dễ phát triển và bảo trì.

Hardware-aware

Kiến trúc phải phù hợp với thực tế BodyCamera có phần cứng/firmware khác nhau.

Runtime-recoverable

App phải recover được sau crash/reboot/service kill/storage/DB/session mismatch theo runtime designs.

Cloud-ready

Có thể tích hợp cloud services nhưng không phụ thuộc bắt buộc vào một provider cụ thể.

Secure by Design

Permission, credential/auth data, metadata, media, config và update cần có hướng bảo mật rõ.

Testable

Các module cần đủ tách biệt để test và debug.

## 4. High-Level Architecture

+------------------------------------------------+
|                BodyCamera Device               |
|                                                |
|  +------------------------------------------+  |
|  |          DCAM Android Application         |  |
|  |                                          |  |
|  |  UI / Login / Runtime Orchestrator       |  |
|  |  Recording / Capture / Storage / DB      |  |
|  |  User / Auth / GPS / Logs                |  |
|  |  Device Capability / Update / Config     |  |
|  |  Cloud Service Adapter (optional)        |  |
|  +------------------------------------------+  |
|                                                |
|  Local Media + Metadata + User DB + Logs       |
+------------------------------------------------+
                     ↓
              BDMA Desktop via ADB
                     ↓
     Import + User Sync + Optional Cloud extension (no Android GMS/Analytics dependency)
## 5. Primary Responsibilities of DCAM

Responsibility

Description

Android Runtime Operation

Startup, boot behavior, login screen, operator session lifecycle, foreground service, permission lifecycle, module registry và recovery orchestration.

User / Operator Runtime

Authenticate operator offline, maintain active session, expire session after reboot và support emergency override attribution.

Media Capture

Ghi video, chụp ảnh và audio capture nếu được bật/eligible.

Recording Finalization

Finalize media an toàn và chỉ mark BDMA-ready khi conditions pass.

Operator Attribution

Lưu operator snapshot hoặc emergency override attribution trong media/session state.

Metadata Generation

Tạo metadata gắn với media file hoặc DB record theo Data Contract/design.

Local Storage

Lưu media, metadata, local database/file records và logs theo cấu trúc ổn định.

SQLite Runtime State

Lưu settings, user/auth/session, media session, feature eligibility, write-back và recovery state.

Device Context

Ghi nhận device id, device status, storage, battery và GPS availability.

BDMA Compatibility

Đảm bảo BDMA có thể ingest, sync và mapping dữ liệu qua ADB-based flow.

Observability

Tạo log giúp debug và vận hành.

Cloud Service Readiness

Spring Boot BFF + Thymeleaf Factory Portal and PostgreSQL ddmp provisioning boundary plus optional DDMP Hybrid profile: BFF management boundary, limited Headwind control-plane and R2 artifact plane; no change to offline core or GMS-free Android runtime.

Advanced Communication Readiness

Chuẩn bị kiến trúc cho Live Streaming và PTT ở future phase.

## 6. Out of Scope for This Overview

Area

Managed In

Detailed Functional Requirements

DCAM Requirements Home and Functional Requirements 01–10

User/operator requirements

05 - User & Device Operation Requirements

Data contract, media naming, MD5, BDMA behavior, user sync

DCAM-BDMA Data Contract

Android runtime orchestration/login/session lifecycle

DCAM Android Operation Design

Recording/capture/finalization/operator attribution details

DCAM Recording & Capture Design

SQLite schema/user-auth-session/write-back/recovery details

DCAM SQLite Database Design

Storage mechanics and recovery

DCAM Storage Design

Cloud provider implementation detail

06 - Cloud Services, Update & Configuration Architecture

Streaming protocol

Future Live Streaming Design / TBD

PTT protocol

Future Push-to-Talk Design / TBD

Credential/auth/encryption detail

DCAM Security & Encryption Design

Implementation standards

DCAM Android Development Standard

## 7. Architecture Status

Area

Status

Core architecture direction

Approved baseline

Offline-first

Decided

Offline user authentication

Approved requirement / Draft technical baseline defined

BDMA/DCAM user sync

Approved contract direction / Draft technical baseline defined

Operator-authenticated recording

Approved requirement / Draft technical baseline defined

Emergency override operator

Decided: `EMERGENCY_OVERRIDE_ADMIN`

Capability-based design

Decided

Runtime orchestration design

Draft baseline defined

Recording/capture runtime design

Draft baseline defined

SQLite runtime DB boundary

Draft baseline defined

Storage mechanics design

Draft baseline defined

Security/auth design

Draft baseline defined

Cloud Service abstraction

Decided direction

Spring Boot BFF + Thymeleaf Factory Portal provider

Approved in DCAM factory boundary; DDMP Hybrid control-plane is Phase 2+/POC-gated.

Java-first

Decided

CameraX vs Camera2/vendor SDK

Proposed, needs BodyCamera POC

Metadata / Data Contract

Data Contract baseline defined

Local database

SQLite / `dcam.db`

Update direction

In Hybrid profile, BFF-authorized DCAM Self Update from immutable R2/CDN artifact is the only remote production update path; approved local/factory APK is separately controlled support fallback. Google Play Store/Managed Google Play/Google-account update is not applicable.

Streaming protocol

TBD

PTT protocol

TBD

## 8. Practical Conclusion

DCAM được thiết kế như một ứng dụng Android **offline-first**, **modular**, **hardware-aware**, **cloud-provider-aware**, **runtime-recoverable**, **operator-attributed** và **BDMA-compatible by design**.

Các TBD còn lại trên trang này là open decisions thật sự cần POC, thiết bị thật, ADR hoặc future design.

Overview định nghĩa architecture context.
Runtime design documents định nghĩa implementation baseline.
Data Contract định nghĩa BDMA-facing và user-sync behavior.
Security Design định nghĩa auth/encryption constraints.
DDMP documents define the optional Phase 2+ control-plane; DCAM remains the sole Android Device Owner/DPC.