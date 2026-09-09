# DCAM-23 — Build 0.1 Recording & Recovery Evidence Summary

**Page ID**: 63078416  
**Version**: 1  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/63078416

---


# DCAM-23 — Recording & Recovery Evidence Summary

Item

Information

Project

DCAM

Document Type

Evidence Summary (Build 0.1)

Status

Draft

Version

1.0

Document Owner

[Dinh Nhan](https://ducviet.atlassian.net/wiki/people/70121:52166504-9fa8-44ba-969f-16f03f3dc4d6?ref=confluence) 

Technical Reviewer

Tech Lead / QA

Approver

PM

Related Jira

[DCAM-23](https://ducviet.atlassian.net/browse/DCAM-23); parent [DCAM-4](https://ducviet.atlassian.net/browse/DCAM-4); [DCAM-2](https://ducviet.atlassian.net/browse/DCAM-2)

Related Documents

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP); [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix);[DCAM Recording & Capture Design](/wiki/spaces/DVID/pages/48529484/DCAM+Recording+Capture+Design) 

Last Updated

2026-08-08

NAS Evidence

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-23\20260808_001`

## 1. Mục đích và cách sử dụng

Trang này tổng hợp kết quả và Evidence ID của DCAM-23 (Build 0.1): normal 30-second recording (QA-WRS-001) và force-stop staged-MP4 recovery (REC-STATE-005 / recovery test group). Evidence gốc, device identity và checksum nằm trên NAS; trang này chỉ nêu kết quả, Evidence ID và giới hạn.

## 2. Evidence Result Matrix

Test ID

Jira

Result

Evidence ID

Kết quả chính

QA-WRS-001

[DCAM-23](https://ducviet.atlassian.net/browse/DCAM-23)

**Pass**

`EV-DCAM-23-20260808-001`

Recording 30 s: MP4 30.439 s, h264 1920x1088, avg 29.567 FPS (expected 30, ±1 FPS), decode + MD5 Pass.

REC-STATE-005 / recovery test group

[DCAM-23](https://ducviet.atlassian.net/browse/DCAM-23)

**Pass**

`EV-DCAM-23-20260808-001`

Force-stop trong lúc ghi, relaunch, recovered MP4 13.176 s + MD5, Temp trống.

## 3. Chi tiết kết quả

### 3.1 — DCAM-23 / QA-WRS-001 Video Subset

Field

Value

Execution result

**Pass**

Evidence ID

`EV-DCAM-23-20260808-001`

Device / model / serial

Android BWC (k69v1_64_k419) / KF5OF2126040802193 — Android 12 / API 31

Build / commit

`DCAM-4-Camera-recording-prototype` / `8ec030bb717d3d926887b4187ce59158f426109e`

Configured video

camera 0 — FHD / h264 / 1920x1088@30 — a-camera2-native-surface-sharing-v1

Key outcome

Request→active 463 ms; stop→finalized 941 ms; MP4 30.439 s; codec/resolution/FPS Pass (±1 FPS); decode + MD5 Pass.

### 3.2 — DCAM-23 / REC-STATE-005 Recovery Group

Field

Value

Execution result

**Pass**

Evidence ID

`EV-DCAM-23-20260808-001`

Scenario

Start video thứ hai, observe 12 s video content, force-stop `com.dvid.dcam`, relaunch, verify staged MP4 recovery.

Key outcome

Force-stop→process stopped 382 ms; relaunch→recovered publication 2955 ms; recovered MP4 13.176 s + MD5; staged 7,126,309 bytes before force-stop; Temp trống.

## 5. Links

Jira: [DCAM-23](https://ducviet.atlassian.net/browse/DCAM-23); [DCAM-4](https://ducviet.atlassian.net/browse/DCAM-4);

Confluence: [DCAM Device POC & Hardware Validation Report](/wiki/spaces/DVID/pages/49545399/DCAM+Device+POC+Hardware+Validation+Report); [DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP)

NAS: `\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-23\20260808_001`