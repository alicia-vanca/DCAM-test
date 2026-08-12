# 02 - Media Storage Requirements

**Page ID**: 47808901  
**Version**: 7  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47808901

---


# 02 - Media Storage Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.4

Status

Approved

Approval Scope

Stable requirements và Build 0.1 Internal-only storage policy

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Team

Last Updated

2026-07-21

Related Jira

Not linked

Dependencies / Blockers

Active storage profile decision; Device POC cho physical path, scoped storage và ADB visibility.

Related Documents

DCAM Requirements Home, DCAM-BDMA Data Contract, DCAM Storage Design, DCAM Recording & Capture Design, DCAM SQLite Database Design, 05 - Data, Storage & BDMA Architecture, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này ghi nhận các yêu cầu chức năng liên quan đến **media storage** của DCAM.

Yêu cầu storage hiện đã có technical design chi tiết trong **DCAM Storage Design**. Trang này chỉ giữ requirement-level direction; physical path, temp/final mechanics, fallback, BDMA readiness và recovery behavior thuộc Storage Design.

## 2. Storage Requirements

Requirement ID

Requirement Area

Requirement Direction

Build 0.1

Status

STO-LOC-001

Internal Storage

DCAM hỗ trợ Internal DCAM Media Root; Build 0.1 bắt buộc sử dụng Internal storage.

Required.

Approved for Build DCAM MVP Internal Build 0.1

STO-LOC-002

External Storage

DCAM có thể hỗ trợ External ở build khác khi approved profile và Device POC cho phép.

Not Applicable.

Approved for Build DCAM MVP Internal Build 0.1

STO-MODE-001

Auto Storage

Auto/fallback có thể thuộc future approved profile; Build 0.1 không dùng Auto hoặc External fallback.

Not Applicable.

Approved for Build DCAM MVP Internal Build 0.1

STO-ART-001

Fixed Internal Files

`dcam_config.cson`, `dcam.db` và `logs.txt` phải nằm trong Internal Storage.

Required.

Approved Direction

STO-TEMP-001

Temp Handling

DCAM phải phân biệt temporary/in-progress files với completed media files; in-progress files không được là BDMA import candidate.

Required.

Approved Direction

STO-FINAL-001

Final Media Readiness

Build 0.1 chỉ xem MP4 là BDMA-ready sau khi final file, DB/storage state và valid MD5 đạt yêu cầu.

Required.

Approved for Build DCAM MVP Internal Build 0.1

STO-REC-001

Storage Recovery

DCAM phải có cơ chế recovery khi temp/final file và DB state không khớp sau crash/reboot/storage failure.

Required.

Approved Direction

## 3. Source of Truth

Topic

Source of Truth

Folder/naming/MD5/BDMA cleanup contract

DCAM-BDMA Data Contract

Android-side storage mechanics

DCAM Storage Design

Recording/capture finalization flow

DCAM Recording & Capture Design

Media session DB state

DCAM SQLite Database Design

## 4. Practical Conclusion

Storage requirements are now no longer TBD at requirement level.

Requirements define what DCAM must support.
Storage Design defines how Android implements storage mechanics.
Data Contract defines external media/file/BDMA contract.
## 5. Build 0.1 Storage Failure Requirements

Requirement ID

Requirement

Build 0.1

Status

STO-PRE-001

Nếu Internal storage pre-check không đạt, DCAM không được bắt đầu recording.

Required

Approved

STO-FAIL-001

Nếu storage lỗi trong recording, DCAM phải safe-stop và finalize MP4 nếu còn khả năng.

Required

Approved

STO-FAIL-002

Build 0.1 không được fallback sang External hoặc Auto.

Required

Approved

Physical Internal path, scoped-storage behavior và ADB visibility vẫn Pending Device POC.