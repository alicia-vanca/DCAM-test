# DCAM-9: Image Capture POC Results & Evidence Summary

**Page ID**: 59473994  
**Version**: 43  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/59473994

---


Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Image Capture POC Results & Evidence Summary

Status

Draft

Version

0.1

Approval Scope

Tổng hợp bằng chứng kiểm thử chụp ảnh cho Build 0.1, gồm tạo JPG thành công, final path/filename, xử lý lỗi quyền camera, lỗi storage và evidence liên quan; không xác nhận Device POC pass, production qualification hoặc release readiness.

Document Owner

[phiha (Unlicensed)](https://ducviet.atlassian.net/wiki/people/557058:9faf7c54-9287-4a39-a5cd-09337b4b73b9?ref=confluence) 

Technical Reviewer

Tech Lead / QA

Approver

PM — sau khi Tech Lead hoàn tất rà soát kỹ

Parent Page

DCAM Device POC & Hardware Validation Report

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Lead, Reviewers, Approvers

Related Documents

[01 - Recording & Capture Requirements](/wiki/spaces/DVID/pages/47743356/01+-+Recording+Capture+Requirements)

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP) 

Related Jira

[https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70/backlog?selectedIssue=DCAM-9](https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70/backlog?selectedIssue=DCAM-9)

    

            

    
                [ DCAM-24](https://ducviet.atlassian.net/browse/DCAM-24)
                    -
            Getting issue details...
                                    STATUS
            

    

            

    
                [ DCAM-25](https://ducviet.atlassian.net/browse/DCAM-25)
                    -
            Getting issue details...
                                    STATUS
            
 

    

            

    
                [ DCAM-27](https://ducviet.atlassian.net/browse/DCAM-27)
                    -
            Getting issue details...
                                    STATUS
            
 
Dependencies / Blockers

TBD

Data cutoff

14 Aug 2026

Last Updated

21 Aug 2026

Branch và Commit

`(Build-and-verify-camera-capture-profiles`,`97b4f1ead6bee5eb566899a9e41bf9e5dfa41980`)(1) và (`develop`,`546fb66039d0a497ca1c6c88384972002ae981f6`)(2) và

(`DCAM-5-Storage-Temp-/-Finalization`,`941a26c907678d71a062e0bd10aab8ea50a2dff9`)(3)

Nas:

…\\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-24\

…\\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-25\

…\\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-27\

**Successful capture: final JPG exists and **`Temp`** is empty.**

**Kịch bản thành công**: Khi điều kiện bình thường (đủ quyền, đủ dung lượng), ứng dụng chụp ảnh và tạo ra file JPG cuối cùng. Đồng thời, thư mục `Temp` (nơi lưu file tạm) sau khi hoàn tất sẽ **trống rỗng** – nghĩa là các file tạm đã được dọn sạch, không để lại rác.

**Permission failure: camera readiness remains blocked; no corrupt final file is exposed.**

**Kịch bản lỗi quyền**: Khi ứng dụng bị từ chối quyền CAMERA, trạng thái sẵn sàng của camera bị chặn (blocked). Quan trọng là: **không có file JPG cuối cùng bị hỏng nào được tạo ra** – ứng dụng không cố gắng ghi ảnh khi thiếu quyền, do đó không để lại file rác hay file lỗi.

**Storage failure: capture is blocked before staging; no corrupt final file is exposed.**

**Kịch bản lỗi bộ nhớ**: Khi không thể ghi vào bộ nhớ (ví dụ thư mục `Temp` bị thay thế bằng file hoặc bị khóa), quá trình chụp bị chặn ngay từ giai đoạn chuẩn bị (staging). Một lần nữa, **không có file JPG cuối cùng bị hỏng nào được tạo ra** – ứng dụng phát hiện lỗi sớm và dừng an toàn.Hiện giờ trên branch đang dùng để test với bộ nhớ >=200M thì khi có thông báo bộ nhớ thấp thì vẫn cho lưu

Tổng hợp các subtask kết quả:

Branch và commit là lấy ở hàng “Branch và Commit“ của bảng trên đầu

Jira key

Mục đích

Branch và commit

Evidence ID

Kết quả

Lý do

DCAM-24

`Chứng minh failure được kiểm soát và không có image MD5`

(1)

`EV-DCAM-24-20260804-001`

`PASS`

Kiểm thử từ chối quyền camera **Pass** và xác nhận không có image `.md5`**Pass**.

(2)

`EV-DCAM-24-20260811-001`

`PASS`

(3)

`EV-DCAM-24-20260814-001`

`PASS`

DCAM-25

`Chứng minh camera có thể tạo prototype JPG đọc được`

(1)

`EV-DCAM-25-20260804-001`

`PASS`

Chụp thành công ảnh JPG tại final path. `ffprobe` xác nhận `mjpeg 1920x1080`; `ffmpeg` decode exit code `0`, chứng minh ảnh đọc được.

(2)

`EV-DCAM-25-20260811-001`

`PASS`

(3)

`EV-DCAM-25-20260814-001`

`PASS`

DCAM-27

`Chứng minh final path và filename đúng`

(1)

`EV-DCAM-27-20260804-001`

`Fail`

DEVICE_TOKEN contract chưa được thực thi trên tất cả nguồn serial.
Nên giá trị không khớp [A-Z0-9]{6,10} cho tất cả Room DB serial, CSON serial, factory backup và legacy account ID . Trong khi OPERATOR_TOKEN mặc định B01OPR có đúng 6 ký tự và khớp [A-Z0-9]{6}

(2)

`EV-DCAM-27-20260811-001`

`Fail`

(3)

`EV-DCAM-27-20260814-001`

`PASS`

Tạo đúng file tên và đường link lưu chúng ở bộ nhớ trong 

Tổng kết DCAM-9:

Branch và commit là lấy ở hàng “Branch và Commit“ của bảng trên đầu

Jira key

Branch và commit

Kết quả

Lý do

DCAM-9

(1)

`Fail`

Acceptance Criterion sau không được đáp ứng đầy đủ

(2)

`Fail`

(3)

`PASS`

Acceptance Criterion  hoàn thành