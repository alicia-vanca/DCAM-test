# 03 - Media Management Requirements

**Page ID**: 47710534  
**Version**: 5  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710534

---


# 03 - Media Management Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.2

Status

Approved

Approval Scope

Media management requirement behavior; build applicability thuộc Matrix, filename/data exchange rules thuộc DCAM-BDMA Data Contract.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Developers

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Requirements Home, DCAM-BDMA Data Contract, DCAM Recording & Capture Design, DCAM Storage Design, DCAM Security & Encryption Design, 05 - Data, Storage & BDMA Architecture

## 1. Purpose

Trang này ghi nhận các yêu cầu chức năng liên quan đến **quản lý media files** do DCAM tạo ra.

Các rule chi tiết về naming, important media, encrypted media, MD5 và BDMA cleanup thuộc **DCAM-BDMA Data Contract**. Trang này chỉ giữ requirement-level direction.

## 2. Media Management Requirements

Requirement Area

Requirement Direction

Status

Media Naming

DCAM phải tạo tên file theo format trong DCAM-BDMA Data Contract.

Approved

Important Media

DCAM phải hỗ trợ suffix `_IMP` và lưu important media theo rule của Data Contract.

Approved

Encrypted Media

DCAM phải hỗ trợ suffix `_enc` và `_IMP_enc` khi encryption mode được bật và Security Design cho phép.

Approved Direction

MP4 MD5

DCAM chỉ tạo `.md5` cho video `.mp4` nếu checksum feature được bật theo Data Contract/settings.

Approved

Image/Audio MD5

DCAM không tạo `.md5` cho `.jpg`, `.mp3`, `.aac`, `.wav`.

Approved

Cleanup Readiness

DCAM phải expose source media/final media state để BDMA cleanup theo policy trong Data Contract.

Approved Direction

BDMA Readiness

Final media chỉ là BDMA candidate sau khi Recording/Storage/DB readiness đạt yêu cầu.

Approved Direction

## 3. Source of Truth

Topic

Source of Truth

Naming, `_IMP`, `_enc`, `.md5`, cleanup baseline

DCAM-BDMA Data Contract

Recording/finalization flow

DCAM Recording & Capture Design

Android-side storage mechanics

DCAM Storage Design

Encryption implementation

DCAM Security & Encryption Design

## 4. Practical Conclusion

Media management requirement statuses are now aligned with the approved Data Contract.

This page defines media management requirements.
Data Contract defines the exact media/file contract.
Storage and Recording designs define Android implementation mechanics.