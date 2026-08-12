# 08 - Security & Encryption Requirements

**Page ID**: 47710594  
**Version**: 8  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710594

---


# 08 - Security & Encryption Requirements

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

Security requirement direction; exact algorithm, key management và policy values thuộc DCAM Security & Encryption Design và Security Review.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / BDMA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, QA, Security Reviewer, BDMA Team

Last Updated

2026-07-21

Related Jira

None

Related Documents

05 - User & Device Operation Requirements, DCAM Security & Encryption Design, DCAM SQLite Database Design, DCAM-BDMA Data Contract, DCAM Recording & Capture Design, 07 - Logging & Diagnostics Requirements, DCAM Non-functional Requirements

## 1. Purpose

Trang này định nghĩa requirement-level security, authentication và encryption cho DCAM.

Trang này không copy lại full sensitive logging list, user/auth DB schema, media naming rules hoặc encryption implementation details. Các rule đó thuộc tài liệu authoritative tương ứng.

## 2. Authoritative References

Topic

Authoritative Document

Local Summary

User/operator login policy and emergency override

05 - User & Device Operation Requirements

Security requirements reference approved login/session behavior.

User/auth/session persistence

DCAM SQLite Database Design

DB design là source of truth cho user/auth/session tables và transaction boundary.

Credential/auth security direction

DCAM Security & Encryption Design

Chi tiết technical implementation thuộc Security Design.

BDMA/DCAM user sync and write-back boundary

DCAM-BDMA Data Contract

Data Contract là source of truth cho sync/write-back boundary.

Recording operator attribution

DCAM Recording & Capture Design

Recording design là source of truth cho media/session attribution behavior.

Sensitive logging rules

07 - Logging & Diagnostics Requirements

Security requirements chỉ reference logging policy, không copy full list.

Encrypted media naming and BDMA contract

DCAM-BDMA Data Contract

Data Contract là source of truth cho `_enc`, `_IMP_enc` và import-facing naming rules.

NFR security/privacy constraints

DCAM Non-functional Requirements

NFR page là source of truth cho quality-level constraints.

## 3. Requirement Scope

Requirement

Direction

Status

Offline Authentication

DCAM phải authenticate operator offline bằng local data trong `dcam.db`.

Approved

Login Session Policy

Session không timeout, background/foreground không logout, reboot yêu cầu login lại.

Approved

Normal Recording Auth

Normal recording/capture evidence yêu cầu authenticated operator session.

Approved

Emergency Override

Emergency recording có thể dùng system operator `EMERGENCY_OVERRIDE_ADMIN` khi chưa có operator logged in.

Approved

Credential Safety

Authentication data không được lưu hoặc log ở dạng unsafe raw form.

Approved

Auth Method Support

Password, pattern, face authentication, QR code và NFC tag được hỗ trợ theo capability/security policy.

Approved Direction

BDMA User Sync Safety

User/auth data do BDMA provision phải được validate trước khi runtime use.

Approved Direction

Media Protection

DCAM phải protect media và emergency evidence theo Security Design và Data Contract.

Approved Direction

Secure Logging

DCAM phải tuân thủ Logging Requirements khi xử lý sensitive data.

Approved

Permission Safety

Missing permission phải fail safely cho affected feature hoặc auth method only.

Approved

Update Security

APK/package phải được validate trước khi install.

Approved

Runtime Package Security

Optional runtime/model packages phải được validate trước khi use nếu được distributed riêng.

Approved Direction / Implementation TBD

Location and Analytics Privacy

Sensitive operational data phải tuân thủ approved storage/logging/privacy policy.

Approved Direction

## 4. Authentication Method Requirements

Login Method

Requirement

Status

Password

Offline password login phải được hỗ trợ nếu policy enable.

Approved Direction

Pattern

Offline pattern login phải được hỗ trợ nếu policy enable.

Approved Direction

Face Authentication

Chỉ enable nếu device capability và security policy cho phép.

Capability-based Direction

QR Code

QR credential/token mapping phải được validate offline.

Approved Direction / Format TBD

NFC Tag

NFC credential/card mapping phải được validate offline.

Approved Direction / Format TBD

Emergency Override

Dùng protected system identity chỉ cho emergency recording without login.

Approved

Security implementation details như exact hash parameters, QR/NFC credential format và face authentication model vẫn thuộc **DCAM Security & Encryption Design**.

## 5. Emergency Override Security Requirement

Emergency override không được attribute vào real Admin user.

Required system operator:

operator_code = EMERGENCY_OVERRIDE_ADMIN
user_id = SYSTEM_EMERGENCY_OVERRIDE
user_type = SYSTEM
BDMA và DCAM phải audit được recording được tạo bởi real authenticated operator hay bởi emergency override.

## 6. Practical Conclusion

Security Requirements xác định những gì phải được protect.
User Requirements xác định login/session policy.
Security & Encryption Design xác định cách implementation protection.
SQLite Database Design xác định user/auth/session persistence.
Data Contract là source of truth cho external sync và file/import rules.
Logging Requirements là source of truth cho sensitive logging rules.