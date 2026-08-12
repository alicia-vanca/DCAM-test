# 06 - BDMA Integration Requirements

**Page ID**: 47743376  
**Version**: 9  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743376

---


# 06 - BDMA Integration Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.6

Status

Approved

Approval Scope

Stable requirements và Build 0.1 final-media/checksum import boundary

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, BDMA Developers, QA

Last Updated

2026-07-21

Related Jira

[DCAM-8](https://ducviet.atlassian.net/browse/DCAM-8) — BDMA Sample Import; detailed mapping thuộc Traceability Matrix

Dependencies / Blockers

BDMA Technical Review; ADB/Device POC evidence; implementation PR/build/test evidence cho mapped Jira items.

Related Documents

DCAM-BDMA Data Contract, 08 - DCAM-BDMA Integration Boundary, DCAM BDMA Integration Technical Design, DCAM Storage Design, DCAM SQLite Database Design, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này ghi nhận requirement-level boundary giữa DCAM Android và BDMA Desktop.

Trang này không định nghĩa lại folder, naming, MD5, important media, encrypted media, cleanup matrix hoặc DB/write-back contract. Các rule đó thuộc **DCAM-BDMA Data Contract**.

## 2. Authoritative References

Topic

Authoritative Document

Local Summary

Media contract, MD5 for MP4 only, folder/naming, cleanup baseline

DCAM-BDMA Data Contract

BDMA implementation phải follow Data Contract.

Integration boundary

08 - DCAM-BDMA Integration Boundary

BDMA remains ADB-based and BDMA-initiated.

BDMA implementation flow

DCAM BDMA Integration Technical Design

Technical design describes scan/import/write-back implementation notes.

DB schema/write-back details

DCAM SQLite Database Design

DB-level fields and schema compatibility are handled there.

## 3. Requirement Scope

Requirement ID

Requirement

Direction

Build 0.1

Status

BDMA-INT-001

ADB-based Import

BDMA import boundary remains ADB-based.

Required.

Approved

BDMA-INT-002

Data Contract Compliance

BDMA must comply with DCAM-BDMA Data Contract.

Required.

Approved

BDMA-INT-003

Final Media Only

BDMA imports finalized media candidates only.

Required.

Approved

BDMA-INT-004

Temp/Cache Handling

Temp/cache handling follows Data Contract and Storage Design.

Required.

Approved

BDMA-INT-005

DB Write-back

BDMA write-back must follow Data Contract and DB schema compatibility.

Deferred; không block sample import.

Approved Direction

BDMA-INT-006

Logs

BDMA log access follows Data Contract.

Required read-only artifact access.

Approved

## 4. Practical Conclusion

BDMA Requirements defines requirement-level integration intent.
DCAM-BDMA Data Contract owns concrete media/file/MD5/cleanup rules.
BDMA Technical Design owns implementation flow.
## 5. Build 0.1 Import Eligibility Requirements

Requirement ID

Requirement

Status

BDMA-B01-001

BDMA chỉ scan logical Internal final-media root của approved Build 0.1 mapping; Temp/Cache phải bị ignore.

Approved for Build DCAM MVP Internal Build 0.1

BDMA-B01-002

MP4 chỉ đủ điều kiện import khi finalized và có valid MD5 sidecar.

Approved for Build DCAM MVP Internal Build 0.1

BDMA-B01-003

Missing hoặc mismatch MD5 phải block import và Build 0.1 release evidence.

Approved for Build DCAM MVP Internal Build 0.1

BDMA-B01-004

ADB disconnect, permission failure hoặc checksum failure không được làm cleanup protected artifacts.

Approved for Build DCAM MVP Internal Build 0.1

BDMA-B01-005

Image no-MD5 behavior giữ theo Data Contract; DEC-03 không mở rộng MD5 sang image.

Approved for Build DCAM MVP Internal Build 0.1

Physical scan root/path mapping vẫn Pending Device POC và Data Contract/Storage Design review.