# DCAM Device POC & Hardware Validation Report

**Page ID**: 49545399  
**Version**: 18  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49545399

---


# DCAM Device POC & Hardware Validation Report

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Device POC / Hardware Validation Report

Version

Approved Pending Device POC 1.1

Status

Approved Pending Device POC

Approval Scope

Reference configuration và scoped Build 0.1 POC results đã được ghi nhận; full device qualification, fleet/production readiness và các POC gate còn lại vẫn pending

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead / Security Reviewer / BDMA Lead / Factory Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.3 - Android Development

Target Audience

Tech Lead, Android Developers, QA, Security Reviewer, Support, Factory/Admin Users

Last Updated

2026-08-21

Related Jira

[DCAM-2](https://ducviet.atlassian.net/browse/DCAM-2)

Related Documents

DCAM Project Home, DCAM Architecture Home, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Android Operation Design, DCAM Self Update Design, DCAM QA Test Strategy & Test Matrix, DCAM Factory Provisioning & Device Production SOP, DCAM Security & Encryption Design, DCAM Device Capability & Feature Eligibility Design, DCAM Storage Design, DCAM Recording & Capture Design, DCAM-BDMA Data Contract, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

Dependencies / Blockers

|  

## 1. Purpose

Tài liệu này ghi nhận kết quả **POC / hardware validation** trên BodyCamera thật để chốt các quyết định phụ thuộc vào firmware, OEM behavior và Android version của thiết bị mục tiêu.

Mục tiêu của tài liệu là biến các giả định trong architecture/design thành bằng chứng kiểm chứng được trên thiết bị thật. Các kết quả trong tài liệu này sẽ quyết định implementation path cho `Device Owner`, `Lock Task`, `User Restrictions`, `Self Update`, `Recording`, `Storage`, `BDMA`, `Factory SOP` và các fallback được phép.

Current validation baseline:

Không dùng external EMM.
Không dùng Android Management API.
Không dùng Managed Google Play policy-driven update.
Tính khả thi của DCAM-as-DPC / local Device Owner phải được validate trên thiết bị thật.
Primary update path = DCAM Self Update / APK update.
Google Play Store/Managed Google Play/Google-account update is not a production path; POC validates mandatory GMS-free behavior.
Chỉ hỗ trợ Controlled Maintenance Mode; không hỗ trợ full Android unrestricted mode.
Factory SOP chỉ được approve cho một device model sau khi các POC blockers bắt buộc đã được xử lý hoặc được accept rõ ràng.
## 2. POC Scope

Area

Validation Purpose

Priority

Android version / API behavior

Xác nhận các Android APIs cần thiết có sẵn và hoạt động đúng trên target BodyCamera.

P0

DCAM-as-DPC / Device Owner setup

Xác nhận DCAM có thể trở thành Device Owner/DPC-capable mà không phụ thuộc external EMM.

P0

Lock Task Mode

Xác nhận DCAM có thể enter và recover `Lock Task`.

P0

User Restrictions

Xác nhận restriction nào được hỗ trợ, không hỗ trợ hoặc có OEM behavior đặc biệt.

P0

Home/Launcher policy

Xác nhận DCAM có thể hoạt động như Home/Launcher hoặc preferred home nếu production profile yêu cầu.

P0

Controlled Maintenance Mode

Xác nhận temporary kiosk exit chỉ mở approved targets và có thể restore policy sau khi kết thúc.

P0

Maintenance Password Gate

Xác nhận password gate chặn kiosk exit không hợp lệ và logging an toàn.

P0

No full unrestricted Android

Xác nhận user không truy cập được unrestricted launcher, app drawer hoặc Android Settings ngoài approved flow.

P0

Self Update / APK update

Xác nhận APK download, validation, install, restart và policy restore hoạt động trên device thật.

P0

Silent install feasibility

Xác nhận Device Owner/local policy có thể silent install APK hay bắt buộc phải có approved UX/session flow.

P0

Factory SOP feasibility

Xác nhận các bước SOP có thể chạy lặp lại ổn định trên target model/firmware.

P0

Production record data

Xác nhận có thể thu thập required safe production metadata mà không lưu forbidden secrets.

P0

GMS-free target profile

Xác nhận target firmware/core operation không yêu cầu Google Play services hoặc Play Store.

P1

GMS-free dependency/source gate

Validate prohibited dependencies and Play Store/account flows are absent.

P1

Google-account prohibition

Validate no production provisioning, maintenance or update step requires/stores Google account.

P1

Camera/recording

Xác nhận camera pipeline, resolution profiles, FPS và pre-record feasibility.

P0

Storage / BDMA

Xác nhận storage path, final media visibility, ADB import và BDMA readiness.

P0

Foreground service / boot

Xác nhận boot receiver, screen-off behavior, process kill và restart behavior.

P0

GPS/sensor/AI capability

Xác nhận optional capability và runtime pruning cho GPS, sensor và AI features.

P1

## 3. Device Baseline Information

Field

Value

BodyCamera Model

`NCC-036V` theo màn hình thông tin thiết bị; Android-reported model là `BWC`

Manufacturer

N & N Electric Motor Specialists Limited o/a NTE (Nguyen Technologies & Electrical Equipment)

Android Version

Android 12 / API 31

Build/Firmware Version

`877AOOAKN1_RK2_V009`

GMS-free dependency graph pass

TBD

No Play Store/Google-account flow

TBD

USB/ADB Mode Behavior

Một endpoint ADB ở trạng thái `device` đã được quan sát

Camera API / Vendor SDK

Android platform Camera API; một camera ID `0`, hướng `Back`; preview, JPG và MP4 đã được kiểm chứng trên thiết bị tham chiếu. Vendor SDK không áp dụng cho baseline đã quan sát.

Storage Layout

Build 0.1 kích hoạt Internal storage; scoped-storage behavior và ADB visibility đã được kiểm chứng; External/Auto bị từ chối và không có fallback ngoài ý muốn.

Battery Optimization Behavior

TBD

Boot Auto-start Support

TBD

Device Owner Setup Support

TBD

Lock Task Support

TBD

User Restrictions Support

TBD

Silent APK Install Support

TBD

Factory SOP Repeatability

TBD

External EMM Availability

Không có trong current baseline.

## 4. Kiosk / Device Owner POC Matrix

Test ID

Scenario

Expected Evidence

Status

POC-KIOSK-001

Set DCAM hoặc local DCAM DPC component làm Device Owner trên fresh/factory device.

Device Owner state được xác nhận bằng Android/device policy inspection.

TBD

POC-KIOSK-002

Thử normal APK install mà không có Device Owner setup.

Xác nhận APK install đơn thuần không được xem là Device Owner.

TBD

POC-KIOSK-003

Verify Lock Task allowlist và enter Lock Task.

DCAM enter Lock Task và user không thể escape khỏi kiosk experience.

TBD

POC-KIOSK-004

Reboot device và verify Lock Task recovery.

DCAM verify policy và re-enter Lock Task sau boot.

TBD

POC-KIOSK-005

Process kill/crash recovery.

DCAM restore Lock Task hoặc enter policy recovery state.

TBD

POC-KIOSK-006

Home/Recents/Back behavior.

Không có đường escape khỏi approved kiosk experience.

TBD

POC-KIOSK-007

Apply User Restrictions baseline.

Các supported/unsupported restrictions được ghi nhận rõ.

TBD

POC-KIOSK-008

Attempt factory reset/safe boot/app uninstall/app control.

Bị block nếu device hỗ trợ; nếu không hỗ trợ thì ghi rõ unsupported reason.

TBD

POC-KIOSK-009

Enter Controlled Maintenance bằng password.

Chỉ approved targets được mở và có audit event.

TBD

POC-KIOSK-010

Invalid maintenance password.

Kiosk vẫn active; không restriction nào bị relax.

TBD

POC-KIOSK-011

Maintenance timeout/reboot/crash recovery.

DCAM restore User Restrictions và Lock Task.

TBD

POC-KIOSK-012

Thử unrestricted launcher/app drawer/settings.

Access unavailable hoặc bị block theo approved policy.

TBD

POC-KIOSK-013

Verify no external EMM dependency.

Device pass kiosk baseline mà không cần Android Management API/Managed Google Play.

TBD

## 5. Self Update / APK Update POC Matrix

Test ID

Scenario

Expected Evidence

Status

POC-UPD-001

DCAM load update manifest từ approved artifact provider.

Manifest được load và version được parse đúng.

TBD

POC-UPD-002

Download APK package.

APK được download với expected size/checksum.

TBD

POC-UPD-003

Validate package identity/signature/checksum/version.

Valid APK được accept; invalid APK bị reject.

TBD

POC-UPD-004

Install APK khi device idle và runtime safe.

Update thành công hoặc approved install UX được ghi nhận rõ.

TBD

POC-UPD-005

Install path yêu cầu temporary maintenance.

Maintenance Password Gate bắt buộc và policy được restore sau update.

TBD

POC-UPD-006

Update trong lúc recording/emergency/finalization.

Update bị defer.

TBD

POC-UPD-007

Update failure.

Current app/policy không bị rơi vào unrestricted state; recovery behavior được ghi nhận.

TBD

POC-UPD-008

Verify app version sau update/restart.

New version được detect hoặc failure reason được log.

TBD

POC-UPD-009

Confirm Managed Google Play path is not used.

Được mark là not applicable cho current baseline.

TBD

POC-UPD-010

Confirm silent install feasibility.

Silent install supported/not supported được ghi nhận bằng firmware evidence.

TBD

## 6. GMS-free Android Runtime POC Matrix

This matrix validates the mandatory production guard. It does not test a Play Store fallback.

POC ID

Test

Expected Result

Status

POC-GMS-001

Inspect resolved release runtime dependency graph.

No `com.google.android.gms:*`, FCM, Analytics, Play Integrity/App Check Play Integrity, Google Sign-In, Maps or other prohibited dependency.

TBD

POC-GMS-002

Inspect manifest/source/update/maintenance paths.

No Play Store/`com.android.vending`, Google account, FCM registration or GMS availability path.

TBD

POC-GMS-003

Run provisioning, kiosk, recording, evidence finalization, recovery and local diagnostics on target firmware without GMS/Play Store.

Core operation passes without GMS/Play Store.

TBD

POC-GMS-004

Exercise BFF sync and safe R2 update defer/update behavior.

No FCM, Play Store or Google account is required; kiosk/DPC remains controlled.

TBD

POC-GMS-005

Attempt prohibited update source request.

Request is rejected; device stays controlled and current trusted APK remains.

TBD

## 7. In-app Console POC Matrix

Test ID

Scenario

Expected Evidence

Status

POC-CONSOLE-001

App start vào Record / Live View sau login.

Default screen được xác nhận.

TBD

POC-CONSOLE-002

Back trên Record mở Setting; Back trên Setting quay lại Record.

Navigation behavior được xác nhận.

TBD

POC-CONSOLE-003

Back từ child module quay lại Setting.

Navigation behavior được xác nhận.

TBD

POC-CONSOLE-004

Operator không thể truy cập Admin-only User Settings.

Access bị denied hoặc UI bị hidden.

TBD

POC-CONSOLE-005

Login Settings self-service chỉ hoạt động cho current user.

Password/login method behavior được xác nhận.

TBD

POC-CONSOLE-006

File Manager và Media Viewer read-only.

Không có delete/edit/mark/export/share actions.

TBD

POC-CONSOLE-007

Future modules hidden/disabled.

Server/Live/PTT/AI disabled cho đến khi design được approve.

TBD

## 8. Storage / BDMA / ADB POC Matrix

Test ID

Scenario

Expected Evidence

Status

POC-BDMA-001

Record finalized MP4 và mark BDMA readiness.

File và DB state consistent.

TBD

POC-BDMA-002

BDMA ADB import dưới production restrictions.

ADB/import path hoạt động hoặc approved maintenance path được ghi nhận.

TBD

POC-BDMA-003

User sync/write-back dưới restrictions.

Sync hoạt động hoặc restriction adjustment được ghi nhận.

TBD

POC-BDMA-004

External media / USB restrictions.

Không silently break BDMA boundary.

TBD

POC-BDMA-005

Storage recovery sau interrupted finalization.

Evidence được preserve và recovery result được ghi nhận.

TBD

## 9. Factory SOP POC Matrix

Test ID

Scenario

Expected Evidence

Status

POC-FACTORY-001

Run SOP từ raw/factory-reset device đến `READY_TO_SHIP`.

Các bước có thể chạy lặp lại và thu thập đủ required evidence.

TBD

POC-FACTORY-002

Execute SOP trên device có failed Device Owner setup.

Device được mark `QUARANTINED` trừ khi có approved fallback.

TBD

POC-FACTORY-003

Execute SOP trên device có failed provisioning.

Device được mark `QUARANTINED`.

TBD

POC-FACTORY-004

Execute SOP trên device có recording/storage failure.

Device được mark `QUARANTINED`.

TBD

POC-FACTORY-005

Verify factory production record.

Chỉ chứa safe metadata; loại trừ raw Android ID, maintenance password, Google token/password và signing secrets.

TBD

POC-FACTORY-006

Verify SOP có thể chạy bởi Factory Operator/Admin với expected tools.

Required factory roles, tools, accounts và network assumptions được ghi nhận.

TBD

## 10. Decision Rules Based on POC Result

Result

Decision Direction

DCAM-as-DPC / Device Owner supported

Tiếp tục triển khai DCAM-owned kiosk policy implementation.

DCAM-as-DPC unsupported

Tạo ADR / fallback design; không được silently assume robust kiosk.

Lock Task unsupported/unreliable

Block production kiosk release cho đến khi có approved alternative.

Required restrictions unsupported

Mark policy degraded và để Product/Security quyết định acceptance.

Self Update silent install supported

Dùng silent/policy update path nếu security validation pass.

Self Update silent install unsupported

Dùng approved maintenance/update UX và document limitation.

Prohibited dependency/source detected

Block production GMS-free profile claim and record evidence.

GMS-free target POC passes

Eligible for downstream production gate; does not itself grant production approval.

BDMA blocked by restrictions

Điều chỉnh restriction profile hoặc support procedure; không release cho đến khi BDMA path được validate.

Factory SOP cannot be completed repeatably

Không approve production shipment cho đến khi SOP/tooling/fallback được fix.

Factory production record cannot be captured safely

Không approve production shipment cho đến khi record format/tooling được fix.

## 11. Factory SOP Approval Gate

Factory SOP chỉ được dùng cho pilot/production shipment khi các POC gates dưới đây đã pass hoặc được explicitly accepted.

Gate

Required Result

Device model/firmware baseline

Model/firmware đã biết và được ghi nhận.

Device Owner / Kiosk behavior

Supported hoặc approved fallback đã được document.

Lock Task recovery

Đã verify sau boot/crash/update khi applicable.

No unrestricted Android escape

Đã verify không có unrestricted Android escape.

Web Portal business provisioning

Đã verify.

Recording/storage finalization

Đã verify.

BDMA boundary

Đã verify nếu release baseline yêu cầu.

Self Update capability

Đã verify hoặc limitation được accept rõ ràng.

Production record

Safe fields được confirm.

Quarantine behavior

Critical failures dẫn tới `QUARANTINED`.

## 12. Practical Conclusion

Device POC là bắt buộc trước release vì firmware/OEM behavior của BodyCamera quyết định implementation path thật.
Current baseline không dùng external EMM, Android Management API hoặc Managed Google Play policy-driven update.
Tính khả thi của DCAM-as-DPC / local Device Owner phải được validate.
Lock Task, User Restrictions, Home/Launcher và recovery phải được validate trên thiết bị thật.
Controlled Maintenance Mode không được expose full Android unrestricted mode.
Maintenance Password Gate phải bảo vệ kiosk exit.
Primary update path là DCAM Self Update / APK update.
Production baseline has no Play Store fallback. GMS-free dependency/manifest/source and target-device evidence are mandatory before a production profile claim.
BDMA ADB import/user sync phải hoạt động dưới approved restriction profile.
Factory SOP chỉ được approve sau khi required POC gates đã closed hoặc được accept rõ ràng.
Tất cả TBD từ POC này phải feed back vào Technical Design, QA Matrix, Factory SOP và ADR nếu cần.
## 16. Build 0.1 Reference Configuration and Test Plan

Field

Approved Test Configuration

Reference Model

NCC-036V

Android Version

Android 12

API Level

31

Firmware / Build

877AOOAKN1_RK2_V009

Camera Integration

Android platform Camera API

Vendor SDK

Not Applicable

Physical Device Identifier

`36NCC0901` (serial; single reference device)

Qualification Status

Scoped Build 0.1 POC results recorded; not fleet/production qualification

Execution Status

POC-WRS-003 **Pass** via `EV-DCAM-DCAM-13-20260724-02`; POC-WRS-004/007/009 **Pass** via `EV-DCAM-DCAM-15-20260819-04`; parser/import support via `EV-DCAM-8-20260814-001`

PM approval xác nhận test target, không xác nhận device qualification hoặc production readiness.

### 16.1 Working Recording Slice POC Cases

Chi tiết kết quả thực thi, cơ sở evidence và limitation được tổng hợp tại: [DCAM-2 — Device POC Results & Evidence Summary](/wiki/spaces/DVID/pages/57344040/DCAM-2+Device+POC+Results+Evidence+Summary)

Test ID

Validation

Expected Result

Status

POC-WRS-001

Ghi model, OS/API, firmware và physical device identifier.

Evidence xác định rõ một exact reference configuration.

Pass

POC-WRS-002

Xác nhận Android platform Camera API và capability thực tế.

Recording/capture capability được ghi bằng evidence; Camera1/Camera2 không suy diễn.

Pass

POC-WRS-003

Xác nhận physical Internal storage path, scoped-storage behavior và ADB visibility.

Logical-to-physical mapping được review; không External fallback.

Pass

POC-WRS-004

Failed Internal pre-check và runtime storage failure.

Không start khi pre-check fail; safe-stop/finalize nếu runtime failure và còn khả năng.

Pass

POC-WRS-005

MP4 finalize và async MD5.

Chỉ BDMA_READY sau valid MD5; missing/mismatch/failure block import/evidence.

Not Executed

POC-WRS-006

Static operator artifacts.

B01OPR / Build 0.1 Operator nhất quán tại nơi DB/CSON/log schema yêu cầu.

Not Executed

POC-WRS-007

Basic Device Status.

Battery level, Internal free storage và GPS Available/Unavailable/Unsupported được report chính xác.

Pass

POC-WRS-008

End-to-end WRS và BDMA sample import.

Pass/fail kèm APK, media, DB/CSON/log, MD5 và import evidence.

Not Executed

POC-WRS-009

Media filename token validation.

Xác nhận serial_number thực tế đáp ứng [A-Z0-9]{6,10}; filename dùng DEVICE_TOKEN từ validated serial_number và OPERATOR_TOKEN = B01OPR; không truncation/underscore; lưu filename + parser/import evidence.

Pass

GPS Unavailable/Unsupported không làm fail POC-WRS-007 nếu trạng thái được báo đúng. Kết quả trên configuration này không đại diện multi-model, multi-firmware, Production hoặc fleet readiness.

‌