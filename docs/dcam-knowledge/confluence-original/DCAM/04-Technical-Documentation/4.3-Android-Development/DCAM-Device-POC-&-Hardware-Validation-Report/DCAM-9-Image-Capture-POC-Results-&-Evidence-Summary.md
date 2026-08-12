# DCAM-9: Image Capture POC Results & Evidence Summary

**Page ID**: 59473994  
**Version**: 33  
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

Related Documents

[01 - Recording & Capture Requirements](/wiki/spaces/DVID/pages/47743356/01+-+Recording+Capture+Requirements)

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP) 

Document Owner

[phiha](https://ducviet.atlassian.net/wiki/people/557058:9faf7c54-9287-4a39-a5cd-09337b4b73b9?ref=confluence) 

Technical Reviewer

Tech Lead / QA

Branch

`Build-and-verify-camera-capture-profiles`

Commit

`97b4f1ead6bee5eb566899a9e41bf9e5dfa41980`

Approver

PM — sau khi Tech Lead hoàn tất rà soát kỹ

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

Tiến độ đang bị chặn do sự không thống nhất với quy tắc xác thực DEVICE_TOKEN. Cơ chế hiện tại chấp nhận ký tự viết thường, trong khi quy định về tên tệp yêu cầu định dạng [A-Z0-9]{6,10}. DCAM-9 vẫn chưa hoàn tất kiểm định cho đến khi quy tắc xác thực hoặc quy định được phê duyệt được điều chỉnh cho đồng bộ.

Parent Page

DCAM Device POC & Hardware Validation Report

Last Updated

05 Aug 2026

Nas:

…\\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-24\20260804_01

…\\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-25\20260804_01

…\\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-27\20260804_01

**Successful capture: final JPG exists and **`Temp`** is empty.**

**Kịch bản thành công**: Khi điều kiện bình thường (đủ quyền, đủ dung lượng), ứng dụng chụp ảnh và tạo ra file JPG cuối cùng. Đồng thời, thư mục `Temp` (nơi lưu file tạm) sau khi hoàn tất sẽ **trống rỗng** – nghĩa là các file tạm đã được dọn sạch, không để lại rác.

**Permission failure: camera readiness remains blocked; no corrupt final file is exposed.**

**Kịch bản lỗi quyền**: Khi ứng dụng bị từ chối quyền CAMERA, trạng thái sẵn sàng của camera bị chặn (blocked). Quan trọng là: **không có file JPG cuối cùng bị hỏng nào được tạo ra** – ứng dụng không cố gắng ghi ảnh khi thiếu quyền, do đó không để lại file rác hay file lỗi.

**Storage failure: capture is blocked before staging; no corrupt final file is exposed.**

**Kịch bản lỗi bộ nhớ**: Khi không thể ghi vào bộ nhớ (ví dụ thư mục `Temp` bị thay thế bằng file hoặc bị khóa), quá trình chụp bị chặn ngay từ giai đoạn chuẩn bị (staging). Một lần nữa, **không có file JPG cuối cùng bị hỏng nào được tạo ra** – ứng dụng phát hiện lỗi sớm và dừng an toàn.Hiện giờ trên branch đang dùng để test với bộ nhớ >=200M thì khi có thông báo bộ nhớ thấp thì vẫn cho lưu

Tổng hợp các subtask kết quả:

Evidence ID

Jira key

Mục đích

Kết quả

Lý do

`EV-DCAM-24-20260804-001`

DCAM-24

`Chứng minh failure được kiểm soát và không có image MD5`

`PASS`

Kiểm thử từ chối quyền camera **Pass** và xác nhận không có image `.md5` **Pass**.

`EV-DCAM-25-20260804-001`

DCAM-25

`Chứng minh camera có thể tạo prototype JPG đọc được`

`PASS`

Chụp thành công ảnh JPG tại final path. `ffprobe` xác nhận `mjpeg 1920x1080`; `ffmpeg` decode exit code `0`, chứng minh ảnh đọc được.

`EV-DCAM-27-20260804-001`

DCAM-27

`Chứng minh final path và filename đúng`

`Fail`

Tên tệp JPG (trường hợp hợp lệ) và đường dẫn cuối cùng đã được ghi nhận thành công. Tuy nhiên, việc xác thực DEVICE_TOKEN không tuân thủ quy tắc định dạng tên tệp (contract) hiện hành. Quy tắc này yêu cầu định dạng [A-Z0-9]{6,10}, trong khi mã nguồn hiện tại lại chấp nhận [A-Za-z0-9]{6,10}, tức là cho phép cả ký tự viết thường. Giá trị mặc định OPERATOR_TOKEN B01OPR tuân thủ định dạng [A-Z0-9]{6}. Do đó, lỗi xảy ra là do việc thực thi quy tắc đối với DEVICE_TOKEN chưa đầy đủ.

Jira key

Kết quả

Lý do

DCAM-9

`Fail`

Acceptance Criterion sau không được đáp ứng đầy đủ