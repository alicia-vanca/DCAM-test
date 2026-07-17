# DCAM Engineering Evidence & NAS Artifact SOP

**Page ID**: 53608471  
**Version**: 3  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/53608471

---


# DCAM Engineering Evidence & NAS Artifact SOP

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Engineering Evidence & NAS Artifact SOP

Version

Approved 1.0

Status

Approved

Approval Scope

Quy trình kiểm soát raw technical evidence cho DCAM Build 0.1; không xác nhận NAS readiness, Device POC pass hoặc Build 0.1 readiness.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Hoàng Ngọc Quyền

Approver

Hoàng Ngọc Quyền

Parent Folder

02 - Sprint Operations

Target Audience

Dev, QA, Tech Lead, PM, IT

Last Updated

2026-07-16

Related Jira

None

Related Documents

[DCAM Documentation Governance](/wiki/spaces/DVID/pages/47120620/DCAM+Documentation+Governance), [DCAM Device POC & Hardware Validation Report](/wiki/spaces/DVID/pages/49545399/DCAM+Device+POC+Hardware+Validation+Report), [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix), [DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry)

Dependencies / Blockers

IT provision UNC path ổn định, Access Group, Backup/Retention; PM/Tech Lead xác nhận Owner; Tech Lead xác nhận truy cập thực tế.

## 1. Purpose, Scope and Users

SOP này quy định cách Dev/QA/Tech Lead lưu và tham chiếu **raw technical evidence** của DCAM Build 0.1 trong mạng nội bộ. Áp dụng cho APK/build package, MP4/JPG, logcat, ADB output, screenshot, report và manifest của từng lần chạy.

Không đưa raw APK, MP4/JPG, logcat đầy đủ hoặc dữ liệu thiết bị lên Confluence, Jira, Slack hoặc cloud storage. Confluence chỉ lưu quy định, SOP, metadata, kết luận và link/UNC path; Jira chỉ lưu index, trạng thái và liên kết evidence.

## 2. Evidence Ownership Model

Thành phần

Vai trò

NAS / DCAM-EVID-NAS-01

Kho raw artifact gốc trong mạng nội bộ.

Jira

Execution/evidence index: Jira key, Evidence ID, UNC path và kết quả. Không phải nơi lưu raw artifact.

DCAM Device POC & Hardware Validation Report

Báo cáo tổng hợp và kết luận POC thiết bị; link tới evidence set liên quan.

DCAM Requirement–Design–Test Traceability Matrix

Mapping Requirement → Jira → evidence; không thay thế raw artifact.

Confluence

Lưu governance/SOP/metadata/kết luận và link/UNC path; không lưu raw artifact.

GitHub repository mapping DCAM chưa được phê duyệt và không được coi là authoritative evidence source trong SOP này.

## 3. Repository Record

Repository ID: DCAM-EVID-NAS-01
Name: DCAM Internal Artifact Evidence Store
Classification: Internal Only
Root UNC Path: \\<dvid-snt-server>\2. DCAM\Artifacts\
Owner: DVID
Access Group: SnT
Backup/Retention: Active
Operational Status: Active
Chỉ chuyển trạng thái repository sang **Active** khi IT đã cấp UNC path ổn định, nhóm quyền và Backup/Retention; đồng thời Tech Lead xác nhận đã truy cập thực tế. Trước thời điểm đó, SOP này vẫn là **Draft**.

## 4. Standard Folder Structure

\\<dvid-snt-server>\2. DCAM\Artifacts\
  _templates\
  Build-0.1\
    Sprint-2\
      DCAM-<Jira-key>\
        YYYYMMDD_<run-id>\
          manifest.md
          build\
          adb\
          logs\
          media\
            recordings\
            images\
          screenshots\
          reports\
  Archive\
Mỗi run dùng một folder riêng. Không ghi đè run đã được review; nếu chạy lại, tạo run ID mới.

## 5. Run and Evidence Identification

Mỗi evidence set dùng ID:

```
EV-DCAM-<Jira-key>-<YYYYMMDD>-<sequence>
```

Quy ước:

**<Jira-key>**: Jira item của lần thực thi; nếu chưa có Jira item, không tự tạo item bằng SOP này và phải đánh dấu linkage là **TBD**.

**<YYYYMMDD>**: ngày thực thi.

**<sequence>**: số thứ tự tăng dần trong ngày cho cùng Jira key.

Run folder dùng **YYYYMMDD_<run-id>**; **run-id** phải nhất quán với Evidence ID.

## 6. Manifest Requirements

Mỗi run folder phải có manifest file tối thiểu gồm:

Evidence ID và Jira key;

Người chạy và thời điểm chạy;

Device/model/firmware;

Build/commit ID nếu có;

Lệnh ADB chính;

Kết quả **Pass** hoặc **Fail**;

Danh sách file và checksum khi áp dụng.

Nếu thiếu thông tin, ghi rõ **TBD** hoặc **Not captured**, không suy diễn. Không sửa manifest của run đã review; tạo run mới cho lần chạy lại.

## 7. Dev/QA Procedure

Tạo Jira-linked run folder theo cấu trúc chuẩn và tạo manifest file.

Lưu raw build, ADB output, logs, media, screenshots và reports vào đúng subfolder.

Hoàn tất manifest, xác nhận Evidence ID và UNC path.

Ghi vào Jira: Evidence ID, UNC path đầy đủ tới run folder, kết quả **Pass/Fail**, thời điểm và ghi chú thiếu evidence nếu có.

Cập nhật row liên quan trong Traceability Matrix bằng Jira/evidence linkage theo quy tắc hiện hành; không đánh dấu **Covered** nếu thiếu Jira hoặc execution evidence.

Khi evidence hỗ trợ Device POC, Device POC Report chỉ link Evidence ID và UNC path tới evidence set, rồi ghi kết luận POC dựa trên evidence đã review.

## 8. Data and Storage Rules

NAS là nơi lưu chuẩn cho raw artifact; không dùng ổ cá nhân, USB cá nhân hoặc Slack làm nơi lưu chuẩn.

Không đẩy raw artifact lên cloud, Confluence, Jira hoặc Slack.

Jira/Confluence chỉ tham chiếu Evidence ID, UNC path, trạng thái, checksum khi cần và kết luận.

Phân quyền truy cập chỉ qua Access Group do IT provision; không chia sẻ bằng bản sao không kiểm soát.

## 9. Exception and Access Handling

Tình huống

Hành động

Thiếu evidence

Ghi **Missing evidence** trong Jira và Traceability row liên quan; không đánh dấu Covered. Tạo run mới khi có thể thu thập lại.

Run lỗi / fail

Lưu evidence thực tế cùng manifest, ghi **Fail**; không xóa hoặc ghi đè run.

Cần quyền NAS

Yêu cầu IT provision/quyền Access Group. Giữ Operational Status là **Provisioning Pending IT** cho đến khi xác nhận.

UNC path, backup hoặc retention chưa xác nhận

Giữ giá trị **TBD**; không tự điền hoặc suy diễn.

## 10. Readiness Boundary

SOP này không phê duyệt NAS readiness, Device POC, Build 0.1 hay Production release. NAS chỉ có thể được coi là **Active** sau khi toàn bộ điều kiện tại §3 được xác nhận.