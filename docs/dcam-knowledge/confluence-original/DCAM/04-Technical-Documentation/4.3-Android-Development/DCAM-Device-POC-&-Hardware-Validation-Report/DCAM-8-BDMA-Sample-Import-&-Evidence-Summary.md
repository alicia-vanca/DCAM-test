# DCAM-8: BDMA Sample Import & Evidence Summary

**Page ID**: 60030996  
**Version**: 67  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/60030996

---


Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Test/Verification Items for MP4/JPG File Detection and Import Functionality & Evidence Summary

Version

0.1

Status

Draft

Approval Scope

Tổng hợp bằng chứng kiểm thử import mẫu BDMA cho Build 0.1, gồm sample, scan path, import MP4/JPG, kiểm tra MD5 gating, log/parser và limitation; không xác nhận BDMA import pass, Device POC pass hoặc release readiness.

Document Owner

[phiha (Unlicensed)](https://ducviet.atlassian.net/wiki/people/557058:9faf7c54-9287-4a39-a5cd-09337b4b73b9?ref=confluence) 

Related Documents

[https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743356?xpis=eyJicmlkZ2UiOiJzbWFydExpbmtzIiwiaWQiOiIxNzg1NzIwMjI1NDkzIiwic291cmNlIjoiY29uZmx1ZW5jZSJ9](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743356?xpis=eyJicmlkZ2UiOiJzbWFydExpbmtzIiwiaWQiOiIxNzg1NzIwMjI1NDkzIiwic291cmNlIjoiY29uZmx1ZW5jZSJ9)

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP) 

Technical Reviewer

Tech Lead / QA

Approver

PM — sau khi Tech Lead hoàn tất rà soát kỹ 

Parent Page

DCAM Device POC & Hardware Validation Report

Target Audience

PM/BA, Tech Lead, Android Developers, QA, BDMA Lead, Reviewers, Approvers

Related Jira

[https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70/backlog?selectedIssue=DCAM-8](https://ducviet.atlassian.net/jira/software/projects/DCAM/boards/70/backlog?selectedIssue=DCAM-8) 

    

            

    
                [ DCAM-35](https://ducviet.atlassian.net/browse/DCAM-35)
                    -
            Getting issue details...
                                    STATUS
            

    

            

    
                [ DCAM-36](https://ducviet.atlassian.net/browse/DCAM-36)
                    -
            Getting issue details...
                                    STATUS
            

    

            

    
                [ DCAM-37](https://ducviet.atlassian.net/browse/DCAM-37)
                    -
            Getting issue details...
                                    STATUS
            

    

            

    
                [ DCAM-41](https://ducviet.atlassian.net/browse/DCAM-41)
                    -
            Getting issue details...
                                    STATUS
            
 
Related Documents

[DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix);

Dependencies / Blockers

TBD

Data cutoff

14 Aug 2026

Last Updated

21 Aug 2026

BDMA,DCAM with Branch,Commit

(BDMA with version: `1.0.150` ,commit: `678fc06924113eeeb164b3f2e3fefde98ee8f665`

and DCAM with branch: `Build-and-verify-camera-capture-profiles` ,commit: `97b4f1ead6bee5eb566899a9e41bf9e5dfa41980`)(1)

(BDMA with branch : `BDMA-164-Allow-sync-from-DCAM` ,commit: `7bda3f9a773db50be04c42cd834562ffca4dd9b2` and DCAM with branch : `DCAM-5-Storage-Temp-/-Finalization` , commit: `941a26c907678d71a062e0bd10aab8ea50a2dff9`)(2)

Cách đặt tên file và nơi lưu file Build 0.1 quy định

Item

Information

Vị trí lưu ảnh,audio,image

Được lưu ở Internal Storage trong Media/Audio/yyyy-mm-dd,

Media/Image/yyyy-mm-dd,Media/Video/yyyy-mm-dd,Media/IMP/yyyy-mm-dd. yyyy-mm-dd là thời gian mà file dữ liệu được lưu.

Cách đặt tên cho file

DCAM_<DEVICE_TOKEN>_<OPERATOR_TOKEN>_<YYYYMMDD>_<HHMMSS>.ext. Trong đó DEVICE_TOKEN: `[A-Z0-9]{6,10}`. OPERATOR_TOKEN: `[A-Z0-9]{6} nhưng với Build 0.1 đang đặt là B010PR`. ext có thể là định dạng .**jpg **cho image, .**mp4 **cho video và ** .mp3/.aac/ .wav **cho audio. Chú ý là sẽ thêm _IMP cho video quan trọng và sẽ có file .md5 cho các file video để kiểm tra sự toàn vẹn của dữ liệu khi bên BDMA dùng trước khi đồng bộ.

Nas

..\\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-8

**Các yêu cầu chi tiết mà Dcam-8 và các subtask của nó cần phải làm**

DCAM-8 — Xác minh luồng phát hiện/import mẫu và lỗi hiển thị

DCAM-8 là hạng mục kiểm thử/xác minh cho chức năng phát hiện và import file MP4/JPG theo hợp đồng áp dụng của Build 0.1. Trọng tâm là chứng minh luồng import hoạt động đúng trên đường scan đã được review, đặc biệt là cơ chế kiểm tra MD5 của MP4.

Yêu cầu chính

Xác định hợp đồng Build 0.1 áp dụng

Ghi rõ tài liệu hoặc phiên bản contract được dùng làm tiêu chuẩn.

Đối chiếu quy tắc đặt tên file, cấu trúc thư mục, định dạng mẫu, điều kiện import và hành vi lỗi.

Nếu phát hiện cần thay đổi contract, chuyển vấn đề sang DCAM-02.

Xác định đường scan thực tế

Xác định thư mục vật lý mà ứng dụng hoặc tiến trình import quét.

Ghi lại đường dẫn đầu vào, đường dẫn đầu ra và cách tái tạo lần scan.

Nếu chưa thống nhất vị trí repository/storage, chuyển quyết định sang DCAM-00.

Kiểm tra import MP4 hợp lệ

Chuẩn bị file MP4 tuân thủ quy tắc tên và cấu trúc của parser.

Chuẩn bị file MD5 tương ứng theo đúng định dạng mà hệ thống yêu cầu.

Chạy qua đúng scan path đã được review.

Xác nhận parser nhận diện file, kiểm tra MD5 thành công và import hoàn tất.

Xác nhận các output Camera, Storage và Artifact liên quan được tạo hoặc liên kết đúng.

Kiểm tra import JPG hợp lệ

Chuẩn bị JPG đúng định dạng và quy tắc đặt tên.

Xác nhận parser nhận diện và import thành công.

Xác nhận kết quả được liên kết với sample và artifact/log tương ứng.

Kiểm tra MD5 gating cho MP4
MP4 không được import trong các trường hợp:

Thiếu file hoặc giá trị MD5.

MD5 không khớp nội dung MP4.

Quá trình đọc hoặc xác minh MD5 thất bại.

File MD5 sai định dạng nếu contract quy định đây là lỗi.

Cần chứng minh không có bản ghi hoặc artifact import thành công bị tạo ngoài ý muốn.

Kiểm tra tương thích filename/parser

Xác nhận tên file mẫu phù hợp với parser hiện tại.

Ghi lại dữ liệu parser trích xuất từ tên hoặc đường dẫn.

Kiểm tra các giả định về dấu phân cách, phần mở rộng, chữ hoa/chữ thường, vị trí MD5 và cấu trúc thư mục.

Nếu parser và scan path không tương thích, ghi rõ đây là limitation hoặc lỗi cần xử lý.

Kiểm tra hành vi lỗi nhìn thấy được

Khi MP4 bị chặn, phải có dấu hiệu lỗi đủ rõ để QA xác minh.

Ghi lại lỗi xuất hiện ở UI, trạng thái import, log hoặc output vận hành tương ứng.

Thông báo không được khiến người dùng hiểu nhầm rằng import đã thành công.

Thu thập log và bằng chứng truy vết

Lưu log phát hiện file, parse filename/path, kiểm tra MD5 và kết quả import.

Mỗi kết quả phải liên kết được với sample đầu vào.

Ghi Evidence ID và đường dẫn UNC khi NAS hoạt động.

Ghi rõ contract version, scan path, thời gian chạy và kết quả Pass, Fail hoặc Blocked.

Ngoài phạm vi

Không triển khai luồng write-back đầy đủ.

Không kiểm thử import ở quy mô production hoặc tải lớn.

Không tự ý thay đổi contract hoặc quyết định cấu trúc repository trong hạng mục này.

Các subtask

DCAM-35 — Chạy detect/import qua scan path đã review

Mục tiêu là thực thi luồng end-to-end có kiểm soát:

Đưa MP4 hợp lệ kèm MD5 và JPG hợp lệ vào scan path.

Chạy cơ chế scan/detect/import đúng như Build 0.1 quy định.

Xác nhận các file được phát hiện và parser xử lý đúng.

Xác nhận cả MP4 và JPG được import thành công.

Kiểm tra đầu ra Camera, Image, Storage và Artifact nếu áp dụng.

Ghi sample ID, đường dẫn nguồn, đường dẫn kết quả, log và trạng thái.

Nêu rõ mọi giới hạn nếu luồng chỉ chạy được một phần.

Kết quả mong đợi: MP4 hợp lệ và JPG hợp lệ đều được detect/import qua đường scan đã được phê duyệt.

DCAM-36 — Xác minh MP4 có MD5 không hợp lệ bị chặn

Cần chuẩn bị và chạy ít nhất các trường hợp:

MP4 không có MD5.

MP4 có MD5 nhưng checksum không khớp.

Quá trình đọc hoặc xác minh MD5 thất bại.

Có thể bổ sung MD5 sai định dạng nếu contract phân biệt trường hợp này.

Với mỗi trường hợp cần xác nhận:

MP4 không được import.

Không có kết quả thành công hoặc artifact không hợp lệ còn sót lại.

Có log nêu nguyên nhân bị chặn.

Lỗi hoặc trạng thái thất bại có thể quan sát được theo contract.

Kết quả được liên kết với đúng negative sample.

Kết quả mong đợi: mọi MP4 thiếu, sai hoặc không xác minh được MD5 đều bị chặn trước import.

DCAM-37 — Chuẩn bị bộ sample hợp lệ

Chuẩn bị tối thiểu:

Một MP4 hợp lệ.

File hoặc giá trị MD5 chính xác của MP4.

Một JPG hợp lệ.

Tên file và cấu trúc thư mục tương thích với parser Build 0.1.

Metadata hoặc manifest giúp xác định sample, checksum và kết quả kỳ vọng.

Bộ sample nên có:

Sample ID duy nhất.

Tên file và kích thước.

MD5 kỳ vọng của MP4.

Nguồn tạo hoặc provenance của sample.

Đường dẫn lưu trữ local và UNC khi NAS hoạt động.

Contract version mà sample tuân thủ.

Ngoài bộ positive sample, nên tạo các biến thể negative phục vụ DCAM-36 nhưng không làm thay đổi mẫu gốc.

Kết quả mong đợi: bộ mẫu có thể tái sử dụng và tái chạy, không phụ thuộc vào dữ liệu không được kiểm soát.

DCAM-41 — Thu thập parser/import/log và các giới hạn

Subtask này tổng hợp bằng chứng từ các lần chạy:

Output phát hiện file.

Kết quả parser đối với filename và path.

Kết quả kiểm tra MD5.

Kết quả import hoặc lý do bị chặn.

Log liên quan.

Đường dẫn input/output và artifact.

Contract version.

Các giới hạn hoặc khác biệt đã biết.

Cần phân biệt rõ:

Hành vi đúng theo contract.

Lỗi triển khai.

Sai lệch do sample hoặc scan path.

Vấn đề repository cần chuyển DCAM-00.

Vấn đề contract cần chuyển DCAM-02.

Kết quả mong đợi: BDMA Lead và QA có đủ bằng chứng để tái hiện, review và quyết định Pass, Fail hoặc Blocked.

Điều kiện hoàn thành DCAM-8

DCAM-8 chỉ được coi là hoàn tất khi:

Scan path và sample có thể tái tạo.

MP4 hợp lệ có MD5 và JPG hợp lệ import thành công.

MP4 thiếu/sai/lỗi MD5 không được import.

Filename và path tương thích với parser, hoặc sai lệch đã được ghi nhận.

Kết quả import liên kết được với sample, log và artifact.

Có Evidence ID/UNC path khi NAS khả dụng.

Mọi limitation đều được ghi rõ.

Kết quả đã sẵn sàng cho BDMA Lead và QA review.

Traceability giữa DCAM-8, DCAM-35, DCAM-36, DCAM-37, DCAM-41, sample và evidence được duy trì đầy đủ.

**Tổng kết trách nhiệm ,kết quả , nguyên nhân của task **(với (1) và (2) là ở hàng** “**BDMA,DCAM with Branch,Commit” bảng trên đầu)

Task

Responsibility

Branch và Commit

Result

Reason

DCAM-37

chứng minh sample đã được chuẩn bị đúng.

(1)

`Pass`

Sample, MD5 và sidecar đã capture đầy đủ.

(2)

`Pass`

DCAM-35

chứng minh positive samples import thành công.

(1)

`Blocked`

Chưa thể chứng minh positive MP4/JPG import thành công vì BDMA chưa quét đúng scan path/folder của DCAM.

(2)

`Pass`

MP4/JPG import thành công và BDMA quét đúng scan path/folder của DCAM.

DCAM-36

chứng minh các trường hợp MD5 không hợp lệ bị chặn.

(1)

`Blocked`

MP4 thiếu/sai MD5 chưa import, nhưng chưa chứng minh được đây là do checksum gate của BDMA vì BDMA chưa quét đúng dữ liệu DCAM.

(2)

`Pass`

MP4 thiếu/sai MD5 không được import và chứng minh được đây là do checksum gate của BDMA

DCAM-41

tập hợp log, parser output và limitation.

(1)

`Blocked`

Chưa có parser output, MD5 decision, import decision và visible error từ luồng BDMA thực tế.

(2)

`Pass`

Có đầy đủ log,parser output và limitation

DCAM-8

tổng hợp traceability và đưa ra kết luận cuối cùng.

(1)

`Blocked`

Các bằng chứng chính phụ thuộc vào việc BDMA quét đúng dữ liệu DCAM, hiện chưa đủ điều kiện kết luận Pass/Fail.

(2)

`Pass`

Đã có đầy đủ thông tin và đưa ra được kết luận cuối cùng `Pass`

**Tổng hợp**

Evidence ID

Branch và Commit

Result

Reason

`EV-DCAM-8-20260804-001`

(1)

`Blocked`

DCAM-8 hiện chưa đủ điều kiện kết luận Pass hoặc Fail cho luồng BDMA import. DCAM side đã chuẩn bị sample, naming rule, folder structure và evidence ban đầu. Tuy nhiên, do BDMA chưa quét đúng scan path/folder của DCAM nên các bằng chứng bắt buộc về detect/import, parser output, MD5 gate và DataSync/DataBackup chưa được xác minh

`EV-DCAM-8-20260814-001`

(2)

`Pass`

Acceptance Criterion  hoàn thành