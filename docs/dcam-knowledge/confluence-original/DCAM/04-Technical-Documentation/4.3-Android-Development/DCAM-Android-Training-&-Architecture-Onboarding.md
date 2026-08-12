# DCAM Android Training & Architecture Onboarding

**Page ID**: 46825510  
**Version**: 10  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/46825510

---


# DCAM Android Training & Architecture Onboarding

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Developer Onboarding Guide

Version

Approved 1.6

Status

Approved

Approval Scope

Developer onboarding và reading guidance; không thay thế Requirements, Architecture, Technical Design hoặc Build Applicability baseline.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.3 - Android Development

Target Audience

Java Desktop Developers, Android Newcomers, QA, Tech Lead, PM/BA

Last Updated

2026-07-14

Related Jira

None

Related Documents

DCAM 9-Month Development Plan, DCAM Architecture Home, DCAM Android Development Standard, DCAM Documentation Governance, 05 - User & Device Operation Requirements, 10 - Android Device Operation Requirements, DCAM Android Device Owner & Kiosk Policy Design, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM SQLite Database Design, DCAM Storage Design, DCAM Security & Encryption Design, DCAM-BDMA Data Contract

## 1. Purpose

Tài liệu này giúp đội phát triển có nền tảng **Java / Desktop / BDMA** chuyển sang phát triển ứng dụng **Android BodyCamera** cho dự án **DCAM**.

Mục tiêu chính:

Chuẩn hóa môi trường phát triển Android.

Giúp Java Desktop Developer hiểu cách Android app hoạt động.

Giới thiệu các Android components cần biết trước khi vào dự án.

Chuẩn bị team cho Phase 1 – MVP Foundation.

Giảm rủi ro Android learning curve trong các sprint đầu tiên.

Cập nhật onboarding để developer biết DCAM hiện có offline user management, operator-authenticated recording và Android dedicated-device/kiosk policy.

Tài liệu này **không mô tả kiến trúc chính thức của DCAM**. Kiến trúc chính thức của dự án được quản lý trong bộ tài liệu **Software Architecture** dưới:

04 - Technical Documentation
└── 4.1 - Software Architecture
    └── DCAM Architecture Home
Trong đó các nội dung về architecture, module, data flow, BDMA boundary, logging, diagnostics, platform strategy, Android dedicated-device/kiosk policy và các TBD kỹ thuật sẽ được quản lý qua các trang SAD/Technical Design như:

01 - Architecture Overview.

02 - Architecture Principles.

03 - Android Platform & Compatibility Strategy.

04 - Application & Module Architecture.

05 - Data, Storage & BDMA Architecture.

06 - Cloud Services, Update & Configuration Architecture.

07 - Logging, Diagnostics, Performance & Security.

08 - DCAM-BDMA Integration Boundary.

DCAM Android Device Owner & Kiosk Policy Design.

DCAM Android Operation Design.

## 2. Scope

Area

Description

Development Environment

Android Studio, JDK, SDK, Gradle, ADB, device setup.

Java on Android

Những khác biệt chính giữa Java Desktop và Android Java.

Android Components

Activity, Service, Foreground Service, Broadcast Receiver.

Android Lifecycle

Activity lifecycle và service lifecycle.

Runtime Permission

Camera, microphone, location, storage/network/NFC-related permissions.

Android Dedicated-device / Kiosk Overview

Device Owner / approved DPC, Lock Task Mode, User Restrictions and Maintenance Mode at onboarding level only.

Login / Operator Session Overview

Login screen, no-timeout session, reboot login requirement, emergency override concept.

Camera Framework Overview

CameraX / Camera2 ở mức onboarding.

Storage Overview

Internal/external/app-specific storage ở mức onboarding.

SQLite / Local DB Overview

`dcam.db` stores settings, user/auth/session, media/session, runtime, sync, optional policy snapshot and import state.

GPS / Location Overview

Location provider, accuracy, unavailable/fallback concept.

Network Basics

HTTP/HTTPS, WebSocket, background network concept.

Debugging Basics

Android Studio Debugger, ADB, Logcat, crash stack trace.

Coding Convention

Java naming, clean code, exception handling, basic Android style.

Git Workflow

Áp dụng Git Workflow Standard của DVID.

Onboarding Checklist

Checklist xác nhận dev đã sẵn sàng tham gia sprint.

### Out of Scope

Area

Managed In

DCAM official architecture overview

DCAM Architecture Home / 01 - Architecture Overview

DCAM architecture principles

02 - Architecture Principles

Android platform compatibility strategy

03 - Android Platform & Compatibility Strategy

DCAM module architecture

04 - Application & Module Architecture

Android Device Owner / Lock Task / User Restrictions detailed behavior

DCAM Android Device Owner & Kiosk Policy Design

Data flow, storage direction and BDMA architecture

05 - Data, Storage & BDMA Architecture

Cloud, update and configuration architecture

06 - Cloud Services, Update & Configuration Architecture

Logging/debugging strategy riêng cho BodyCamera

07 - Logging, Diagnostics, Performance & Security

DCAM-BDMA responsibility boundary and user sync boundary

08 - DCAM-BDMA Integration Boundary

User/operator requirements

05 - User & Device Operation Requirements

Android startup/login/session lifecycle

DCAM Android Operation Design under 4.2 - Technical Design

Recording operator gate and emergency override

DCAM Recording & Capture Design under 4.2 - Technical Design

Folder structure, file naming, storage state

DCAM Storage Design and DCAM-BDMA Data Contract

DB schema, user/auth/session tables and sync state

DCAM SQLite Database Design

Security/encryption/auth/kiosk exit design

DCAM Security & Encryption Design under 4.2 - Technical Design

Live Streaming / PTT design

Dedicated future technical design documents under 4.2 - Technical Design

Full functional requirements

DCAM Functional Requirements under 03 - Requirements

## 3. Target Audience

Role

Why Read This Document

Java Desktop Developer

Chuyển từ Java/JavaFX/Desktop sang Android development.

Android Developer mới vào dự án

Hiểu setup, baseline knowledge và workflow của DCAM.

Tech Lead

Dùng làm baseline để hướng dẫn team trong giai đoạn training.

QA

Hiểu cách app Android được build/deploy/debug để hỗ trợ test.

PM

Theo dõi readiness của team trước khi bắt đầu implementation.

## 4. Prerequisites

Prerequisite

Required Level

Java SE

Required

OOP

Required

Git

Required

Gradle/Maven concept

Basic

Jira workflow

Basic

Confluence documentation

Basic

Android experience

Not required

Android Enterprise / kiosk experience

Not required, but developers working on kiosk policy must read Kiosk Policy Design first.

## 5. Development Environment Setup

Tool

Purpose

Android Studio

Main IDE for Android development.

JDK

Java development runtime.

Android SDK Platform

Android API platform used by the project.

Android SDK Build Tools

Build Android project.

Android Platform Tools

ADB, device connection and debugging.

USB Driver

Connect BodyCamera to development machine.

Git

Source control.

Jira / Confluence

Task tracking and documentation.

### Device Setup

Item

Required

BodyCamera device available

Yes

USB debugging enabled

Yes for development/POC; production policy may restrict it.

Device detected by ADB

Yes for development/BDMA validation.

App install via Android Studio/ADB works

Yes for development.

Logcat can read device logs

Yes for development.

Device Owner / DPC enrollment test device

Required for developers working on kiosk policy.

Android Studio Installed
    ↓
JDK Configured
    ↓
Android SDK Installed
    ↓
Platform Tools Installed
    ↓
BodyCamera Connected
    ↓
ADB Device Detected
    ↓
Sample App Installed
    ↓
Logcat Working
    ↓
Kiosk policy POC device available if assigned to Device Owner/Lock Task work
## 6. Java on Android

Dự án **DCAM sử dụng Java** để phù hợp với năng lực hiện tại của đội phát triển và giảm thời gian chuyển đổi từ BDMA Desktop sang Android.

Desktop Java / JavaFX

Android Java

`main()` entry point

Activity / Application lifecycle

JFrame / JavaFX Stage

Activity

JavaFX Scene / FXML

XML layout / Android View

Desktop event loop

Android main thread / looper

Thread / Executor

Handler / Executor / Service / WorkManager depending on case

Desktop file system

Android storage model and permissions

Desktop app lifecycle

Android lifecycle, background restrictions and permission model

Desktop app window control

Android Activity/Task/Lock Task behavior under device policy

### Mindset Shift

Topic

What Developer Must Remember

Main Thread

Không chạy tác vụ nặng trên UI thread.

Lifecycle

Activity có thể bị pause/stop/destroy khi user rời app hoặc hệ thống reclaim resource.

Session

DCAM operator session không timeout và không logout khi background, nhưng reboot bắt login lại.

Kiosk Policy

Production mode may require Device Owner/DPC, Lock Task and restrictions; fullscreen alone is not kiosk security.

Permission

Camera, audio, location, storage, NFC nếu dùng phải được xin quyền đúng cách.

Background Work

Tác vụ dài cần dùng Service/Foreground Service phù hợp.

Device Constraints

BodyCamera có thể giới hạn CPU, memory, battery, storage, firmware behavior and policy support.

## 7. Android Application Components

Component

Purpose

DCAM Relevance

Activity

Màn hình chính của app.

Main UI, login, user management, settings, device information.

Service

Tác vụ chạy nền hoặc không gắn trực tiếp với UI.

Recording, location update, long-running operations.

Foreground Service

Service chạy nền có notification hiển thị.

Recording, future streaming, future PTT.

Broadcast Receiver

Nhận event từ hệ thống.

Battery, network, USB/device connection, boot completed, storage state.

Device Admin / DPC Components

Android Enterprise/kiosk policy integration if selected.

Device Owner / approved DPC policy; details in Kiosk Policy Design.

Developer cần hiểu Activity lifecycle, service lifecycle, permission result, cách start/stop service, khi nào phải release resource và vì sao policy APIs không được gọi trực tiếp từ UI.

## 8. Android Lifecycle

### Activity Lifecycle

Callback

Meaning

`onCreate()`

Activity được tạo. Initialize UI and dependencies.

`onStart()`

Activity visible.

`onResume()`

Activity ready for user interaction.

`onPause()`

Activity losing focus. Save lightweight state.

`onStop()`

Activity no longer visible.

`onDestroy()`

Activity destroyed. Release resources if needed.

Training questions:

Điều gì xảy ra nếu app bị chuyển background khi đang recording?

Điều gì xảy ra nếu user tắt màn hình?

Vì sao background/foreground không được logout operator?

Vì sao device reboot phải login lại?

Vì sao production kiosk không được chỉ dựa vào fullscreen?

Lock Task nên được enter/recover ở lifecycle point nào?

Điều gì xảy ra nếu permission bị từ chối?

Khi nào phải release camera resource?

Khi nào cần foreground service?

## 9. Runtime Permission and Device Policy Overview

Permission / Policy Area

Why Needed

Camera

Video recording, image capture, preview frame analysis and QR/face login method if used.

Microphone

Video audio and Push-to-Talk.

Location

GPS metadata and GPS route.

Storage / Media Access

Save/read media and metadata depending on Android version.

Network

Remote management, streaming/PTT and future online features.

Notification

Foreground service notification on newer Android versions.

NFC

NFC login method if enabled and supported.

Device Owner / approved DPC

Production dedicated-device/kiosk control.

Lock Task Mode

Keep field user inside approved app/task.

User Restrictions

Harden device against reset, safe boot, app control or unapproved settings changes.

Developer cần biết kiểm tra permission, xin permission đúng thời điểm, xử lý khi user từ chối permission và không để app crash khi permission unavailable.

Ghi chú: Device Owner / Lock Task / User Restrictions không được implement ad-hoc trong Activity/ViewModel. Xem **DCAM Android Device Owner & Kiosk Policy Design** và **DCAM Android Development Standard**.

Ghi chú: trong kiến trúc hiện tại, BDMA đọc dữ liệu DCAM và đồng bộ user/operator qua **ADB** ở phía Desktop. DCAM Android không chủ động push/upload media sang BDMA trong scope hiện tại. Chi tiết ranh giới này nằm trong **08 - DCAM-BDMA Integration Boundary**.

## 10. Login / Operator Session Overview

Developer cần nắm các decision sau trước khi implement UI/logic liên quan recording:

DCAM startup shows login screen if no valid same-boot operator session exists.
Normal recording/capture evidence requires active operator session.
Emergency recording can use EMERGENCY_OVERRIDE_ADMIN when no operator is logged in.
Session has no timeout.
Background/foreground does not logout operator.
Device reboot requires login again.
System tracking, monitoring, logging, recovery, policy verification and capability detection can run before login.
Implementation detail thuộc **DCAM Android Operation Design**, **DCAM Android Device Owner & Kiosk Policy Design**, **DCAM Recording & Capture Design**, **DCAM SQLite Database Design** và **DCAM Security & Encryption Design**.

## 11. Camera Framework Overview

API

Notes

CameraX

Ưu tiên nghiên cứu trước vì dễ dùng hơn và lifecycle-aware.

Camera2

Dùng khi cần kiểm soát sâu hơn hoặc CameraX không phù hợp với BodyCamera.

Developer cần hiểu preview, image capture, video recording, camera state, error callback, resource release và device-specific camera behavior.

Quyết định CameraX first / Camera2 fallback đang là hướng đề xuất và cần POC trên thiết bị thật. Chi tiết xem **03 - Android Platform & Compatibility Strategy** và **04 - Application & Module Architecture**.

## 12. Storage and SQLite Overview

Area

Description

Internal Storage

Fixed files such as `dcam_config.cson`, `dcam.db`, `logs.txt`.

External Storage

Preferred media storage when available and policy allows.

Media Files

Video/image/audio files generated by DCAM.

Temporary vs Final File

File đang ghi và file đã hoàn tất phải được phân biệt.

SQLite DB

`dcam.db` stores settings, user/auth/session, media/session, tracking, runtime, optional policy snapshot, sync and import state.

BDMA_READY

Means safe for BDMA scan/import, not already imported.

Chi tiết chính thức nằm trong:

**05 - Data, Storage & BDMA Architecture** ở mức architecture direction.

**08 - DCAM-BDMA Integration Boundary** ở mức responsibility/ownership boundary.

**DCAM Storage Design** ở mức technical design chi tiết.

**DCAM SQLite Database Design** ở mức schema/runtime DB boundary.

**DCAM-BDMA Data Contract** ở mức contract giữa Android và BDMA.

## 13. GPS / Location Overview

Topic

Description

Location Provider

GPS/network provider or fused provider depending on implementation.

Accuracy

GPS data có thể sai lệch hoặc chưa đủ chính xác.

Availability

GPS có thể unavailable trong nhà hoặc khi thiết bị chưa lock được tín hiệu.

Timestamp

Location cần gắn với thời điểm capture/recording.

Fallback

App không được crash nếu GPS unavailable.

System tracking/GPS có thể chạy trước login nếu eligible và policy cho phép. Chi tiết xem **DCAM Android Operation Design** và **DCAM Sensor & Location Monitoring Design**.

## 14. Network Basics

Topic

Used For

HTTP / HTTPS

Remote API, device management, configuration nếu scope yêu cầu.

WebSocket

Realtime command/status or streaming support if selected.

Timeout

Streaming/PTT/network operations.

Retry

Network instability.

Offline Handling

BodyCamera có thể hoạt động khi không có mạng.

Background Network

Network task while app background/foreground service.

Ghi chú quan trọng: network không phải điều kiện bắt buộc cho core recording/capture/metadata/storage/operator login. Kiến trúc DCAM hiện theo hướng **offline-first**; xem **02 - Architecture Principles**.

## 15. Debugging Basics

Tool / Concept

Purpose

Android Studio Debugger

Breakpoint, inspect variable, step through code.

ADB

Device connection, install APK, shell command, BDMA-side access/sync investigation.

Logcat

Read runtime logs.

Crash Stack Trace

Identify crash location.

ANR

Understand app not responding issue.

Memory Profiler

Basic memory observation.

Device Policy Test Logs

Validate policy state, Lock Task and restriction behavior during POC.

Các quy ước logging/debugging riêng cho BodyCamera và DCAM nằm trong **07 - Logging, Diagnostics, Performance & Security** và các tài liệu technical design liên quan.

## 16. Coding Convention

Area

Rule

Language

Java-first for DCAM.

Naming

Follow Java naming convention.

Class Responsibility

Mỗi class nên có trách nhiệm rõ ràng.

Exception Handling

Không swallow exception; log và xử lý fallback nếu cần.

Null Handling

Tránh NullPointerException bằng validation rõ ràng.

Threading

Không block UI thread.

Logging

Log đủ để debug, không log dữ liệu nhạy cảm.

Policy APIs

Không gọi trực tiếp DevicePolicyManager/Lock Task APIs từ UI/ViewModel; dùng policy managers.

Code Review

Pull request cần review trước khi merge.

Chi tiết về dependency direction, module boundary và layer responsibility nằm trong **04 - Application & Module Architecture** và **DCAM Android Development Standard**.

## 17. Training Roadmap

Week

Focus

Expected Output

Week 1

Android Studio, SDK, ADB, project structure

Dev setup được environment.

Week 1

Java on Android, Activity, lifecycle

Dev hiểu app entry point và lifecycle.

Week 1

Runtime permission, storage overview

Dev xử lý được permission/storage sample.

Week 1

Login/session overview

Dev hiểu login screen, no-timeout session, reboot login required.

Week 1

Dedicated-device/kiosk overview

Dev hiểu Device Owner/DPC, Lock Task, User Restrictions là policy layer riêng.

Week 2

CameraX/Camera2 overview

Dev chạy được camera preview/capture sample.

Week 2

Video recording sample

Dev record được video sample.

Week 2

SQLite/user/session sample

Dev thao tác DB sample qua repository pattern.

Week 2

GPS/location sample

Dev lấy được location sample nếu device hỗ trợ.

Week 2

Debugging basics

Dev đọc được Logcat và debug được app.

Week 2

BodyCamera test

Dev deploy và test sample app trên BodyCamera.

Week 2+

Kiosk policy POC if assigned

Dev test được policy state/Lock Task/restriction sample trên thiết bị POC.

## 18. Onboarding Exit Criteria

Criteria

Required

Android Studio installed and configured

Yes

JDK and Android SDK configured

Yes

BodyCamera connected and detected by ADB

Yes

Sample app can be built

Yes

Sample app can be installed on BodyCamera

Yes

Image capture sample works

Yes

Video recording sample works

Yes

Runtime permission flow understood

Yes

Basic storage write/read sample works

Yes

Basic login/session sample understood

Yes

Basic SQLite repository sample understood

Yes

Basic Device Owner / Lock Task / User Restrictions concept understood

Yes

Logcat can be used for debugging

Yes

Android Studio Debugger works

Yes

Developer understands Activity lifecycle

Yes

Developer understands Service / Foreground Service concept

Yes

Developer can follow Git Workflow Standard

Yes

## 19. Android Development Checklist

Checklist Item

Status

Android Studio installed

Not started

JDK configured

Not started

Android SDK installed

Not started

Android Build Tools installed

Not started

Android Platform Tools installed

Not started

USB driver installed

Not started

BodyCamera connected via USB

Not started

ADB detects BodyCamera

Not started

Sample project opened successfully

Not started

Sample project builds successfully

Not started

Sample APK installed on BodyCamera

Not started

App launches on BodyCamera

Not started

Camera permission request works

Not started

Microphone permission request works

Not started

Location permission request works

Not started

Camera preview works

Not started

Image capture works

Not started

Video recording works

Not started

Basic storage write works

Not started

Basic storage read works

Not started

Basic SQLite read/write works

Not started

Basic login/session flow understood

Not started

Basic Device Owner / Lock Task / User Restrictions concepts understood

Not started

GPS sample works or fallback behavior is understood

Not started

Logcat works

Not started

Android Studio Debugger works

Not started

Developer understands Activity lifecycle

Not started

Developer understands Service / Foreground Service concept

Not started

Developer can create feature branch

Not started

Developer can create pull request

Not started

Developer understands Jira workflow

Not started

Developer knows where to find DCAM documents

Not started

## 20. Related Documents

Document

Purpose

DVID Read Guide

Hướng dẫn onboarding chung cho team DVID.

Git Workflow Standard

Quy trình Git branching, PR, review và merge.

Jira Workflow Standard

Quy trình trạng thái Jira và xử lý task.

DCAM Project Home

Trang trung tâm điều hướng tài liệu DCAM.

DCAM Roadmap

Product roadmap, các phase và milestones.

DCAM 9-Month Development Plan

Kế hoạch execution, sprint allocation, buffer và delivery plan.

DCAM Architecture Home

Trang chủ bộ tài liệu Software Architecture của DCAM.

01 - Architecture Overview

Bối cảnh kiến trúc high-level và architecture goals.

02 - Architecture Principles

Offline-first, capability-based design và các architecture principles.

03 - Android Platform & Compatibility Strategy

Chiến lược tương thích Android version, GMS/non-GMS, BodyCamera and kiosk policy behavior.

04 - Application & Module Architecture

Application layers, module direction và dependency rules.

05 - Data, Storage & BDMA Architecture

Định hướng high-level về data, storage và BDMA architecture.

07 - Logging, Diagnostics, Performance & Security

Baseline về logging, diagnostics, performance, reliability và security.

08 - DCAM-BDMA Integration Boundary

Ranh giới trách nhiệm giữa DCAM Android và BDMA Desktop.

DCAM Android Device Owner & Kiosk Policy Design

Device Owner/DPC, Lock Task, User Restrictions, Home/Launcher policy and Maintenance Mode.

05 - User & Device Operation Requirements

User/operator requirements, login policy and emergency override.

DCAM Android Operation Design

Startup, policy verification, login/session lifecycle and runtime orchestration.

DCAM Recording & Capture Design

RecordingController, operator gate and emergency override.

DCAM SQLite Database Design

`dcam.db` schema/runtime DB boundary.

DCAM Storage Design

Android-side storage mechanics and BDMA readiness.

DCAM Security & Encryption Design

Security/auth/encryption/kiosk security technical design.

DCAM-BDMA Data Contract

Data contract between DCAM Android and BDMA Desktop.

## 21. Practical Conclusion

Mục tiêu của tài liệu này là giúp team Java/Desktop chuyển sang Android đủ nhanh để bắt đầu DCAM Phase 1.

Tài liệu này chỉ nên giữ ở mức **training and onboarding**. Các quyết định kiến trúc và lưu ý kỹ thuật riêng của DCAM cần được quản lý trong **4.1 - Software Architecture** và **4.2 - Technical Design** để tránh trộn nội dung training với architecture chính thức.

Developer được giao việc liên quan Device Owner / Lock Task / User Restrictions phải đọc:

DCAM Android Device Owner & Kiosk Policy Design
DCAM Android Operation Design
DCAM Android Development Standard
DCAM Device POC & Hardware Validation Report