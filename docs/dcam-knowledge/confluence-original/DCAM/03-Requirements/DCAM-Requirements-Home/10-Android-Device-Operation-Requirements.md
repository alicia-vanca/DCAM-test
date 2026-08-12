# 10 - Android Device Operation Requirements

**Page ID**: 48496661  
**Version**: 11  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496661

---


# 10 - Android Device Operation Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.9

Status

Approved

Approval Scope

Android device-operation target requirements; active subset thuộc Matrix, hardware behavior phụ thuộc Device POC khi được nêu.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Security Reviewer / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, AI/ML Engineer, QA

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Requirements Home, 05 - User & Device Operation Requirements, 03 - Android Platform & Compatibility Strategy, 04 - Application & Module Architecture, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Self Update Design, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Device Capability & Feature Eligibility Design, DCAM Sensor & Location Monitoring Design, DCAM Realtime AI Detection Design, DCAM State Machine Design, DCAM Android Development Standard, DCAM Security & Encryption Design, 09 - System Settings Requirements

## 1. Purpose

Tài liệu này mô tả các **Functional Requirements** liên quan đến cách ứng dụng **DCAM** hoạt động trên hệ điều hành Android của thiết bị BodyCamera.

Phạm vi bao gồm startup, login screen, full screen, Home/Launcher, dedicated-device/kiosk mode, DCAM-as-DPC / local Device Owner capability nếu firmware hỗ trợ, Lock Task Mode, User Restrictions, in-app operation/device/media console, foreground service, lifecycle, permission, power, recovery, continuous monitoring, realtime AI detection, Self Update và **device capability / feature eligibility**.

Current baseline:

No external EMM.
No Android Management API.
No Managed Google Play policy-driven update.
DCAM-as-DPC / local Device Owner is preferred if target firmware supports it.
Primary update path is DCAM Self Update / APK update.
Manual Google Play Store update is optional controlled maintenance fallback only if device capability and approved process allow it.
User/operator requirement chi tiết thuộc **05 - User & Device Operation Requirements**. Chi tiết kiosk policy thuộc **DCAM Android Device Owner & Kiosk Policy Design**. Chi tiết các màn hình/tính năng bên trong app khi chạy kiosk thuộc **DCAM In-App Operation, Device Settings & Media Console Design**. Chi tiết update package/download/install thuộc **DCAM Self Update Design**. Trang này chỉ mô tả tác động ở mức requirement.

## 2. Scope

Area

Requirement Direction

Status

Dedicated-device / Kiosk Mode

DCAM production deployment phải support dedicated-device/kiosk operation trên BodyCamera.

Approved Direction

DCAM-as-DPC / local Device Owner

DCAM phải support Device Owner / DPC-capable policy behavior nếu target firmware và factory process cho phép.

Approved Direction / POC Required

No External EMM Baseline

Current baseline không giả định external EMM, Android Management API hoặc Managed Google Play policy-driven update.

Approved Direction

Lock Task Mode

DCAM phải chạy trong Lock Task Mode cho normal field operation khi deployment profile yêu cầu.

Approved Direction

User Restrictions

DCAM phải apply hoặc verify approved User Restrictions để harden kiosk device khi có policy authority.

Approved Direction

In-app Device Console

Khi user bị hạn chế thoát app, DCAM phải cung cấp các màn hình in-app cho allowed operation settings, device/system settings proxy, storage/file/media review và support/admin workflows.

Approved Direction

App Operation Settings

DCAM phải support cấu hình recording/app behavior như video resolution, FPS/quality, pre-record time, post-record time, audio và file split profile theo capability/policy.

Approved Direction

Device/System Settings Proxy

DCAM phải cung cấp controlled UI cho USB mode, Wi-Fi, GPS/location, device info và các system settings được approve.

Approved Direction

File/Storage Manager/Media Viewer

DCAM phải cung cấp storage summary, media list/viewer và evidence-safe file visibility vì user không dùng external file manager trong kiosk. File/media actions are read-only unless future approved design changes this.

Approved Direction

Login Settings

DCAM phải support approved current-user login settings như đổi mật khẩu, đổi kiểu đăng nhập hoặc đăng ký face auth nếu capability/security policy cho phép.

Approved Direction

User Settings

DCAM phải support Admin-only user management như thêm/sửa/xóa hoặc disable user theo security/audit rules.

Approved Direction

Emergency Settings

DCAM phải reserve emergency settings group; exact behavior TBD.

TBD

Server Connection / Live Stream / PTT / AI Mode

DCAM phải reserve future groups nhưng không enable production controls khi chưa có approved design.

Future / TBD

Maintenance Mode

DCAM phải có controlled Admin/Maintenance Mode để support/factory thao tác an toàn.

Approved Direction

Maintenance Password Gate

Enter Maintenance Mode / Exit Kiosk temporarily phải yêu cầu maintenance password/credential theo Security Design.

Approved Direction

Self Update

DCAM Self Update / APK update là primary update path cho current no-EMM baseline.

Approved Direction

Manual Play Store Fallback

Optional only if GMS/Play Store exists and approved maintenance process allows it.

Conditional / POC Required

Full Screen Operation

DCAM phải chạy full screen để tối ưu trải nghiệm vận hành trên BodyCamera.

Approved

Home / Launcher App

DCAM có thể là Home App / Launcher App nếu deployment policy yêu cầu; Home/Launcher không thay thế Lock Task Mode.

Approved

Auto Start on Boot

DCAM phải có khả năng auto-start sau boot nếu permission/policy cho phép.

Approved

Startup Login Screen

DCAM startup phải hiển thị login screen sau khi hoàn tất safe startup/recovery/policy checks.

Approved

Operator Session Lifecycle

Session không timeout; background/foreground không logout; reboot yêu cầu login lại.

Approved

Foreground Operation

Recording, location, monitoring, AI hoặc device operation cần chạy bằng foreground/background mechanism phù hợp.

Approved

Device Capability Detection

DCAM phải detect hardware/platform/performance/permission/policy/update capability khi startup/boot.

Approved Direction

Feature Eligibility

DCAM phải evaluate feature eligibility trước khi initialize optional runtime modules.

Approved Direction

Runtime Feature Pruning

Feature không đủ điều kiện phải bị loại khỏi runtime flow.

Approved Direction

Continuous Monitoring

Sensor/GPS monitoring có thể chạy liên tục theo policy nếu device eligible và không cần operator login.

Approved Direction

Realtime AI Detection

AI frame analysis có thể chạy trong preview/recording nếu device eligible; AI không được tự điều khiển recording.

Approved Direction

Permission Handling

DCAM phải xử lý Camera, Microphone, Location, Storage, Notification, NFC nếu dùng login NFC, install package nếu dùng Self Update và device policy permissions nếu áp dụng.

Approved

Power / Thermal Management

DCAM phải tính đến battery, thermal, background limits và workload.

Approved Direction

Recovery Behavior

DCAM cần phục hồi an toàn sau crash, reboot, service killed, policy failure, update failure hoặc model/auth/storage/DB failure.

Approved

## 3. Startup Operating Model

Android Device boots / App starts
    ↓
DCAM initializes logging and policy state detection
    ↓
DCAM verifies Device Owner / DCAM DPC state if production profile requires it
    ↓
DCAM applies/verifies User Restrictions and Lock Task allowlist when policy authority exists
    ↓
DCAM initializes local config and dcam.db
    ↓
DCAM runs safe DB/storage/recovery checks
    ↓
DCAM loads settings, remote config cache and update metadata cache
    ↓
DCAM detects device capability including GMS/Play Store availability and update capability
    ↓
DCAM evaluates feature eligibility
    ↓
DCAM starts system modules that do not require login
    ↓
DCAM prepares allowed in-app console modules based on role/capability/policy
    ↓
DCAM enters or restores Lock Task Mode when UI lifecycle is safe
    ↓
If same boot and active operator session is valid
    → enter app directly and show Record / Live View
Else
    → show login screen
    ↓
After login, enable normal recording/capture commands and allowed console actions
Detailed startup orchestration được định nghĩa trong **DCAM Android Operation Design**. Detailed Device Owner / Lock Task / User Restrictions behavior thuộc **DCAM Android Device Owner & Kiosk Policy Design**. Detailed in-app console behavior thuộc **DCAM In-App Operation, Device Settings & Media Console Design**. Detailed update flow thuộc **DCAM Self Update Design**.

## 4. Login and Session Requirements

Scenario

Required Behavior

Status

App startup without active session

Hiển thị login screen sau khi startup/recovery/policy checks an toàn.

Approved

App startup with valid same-boot session

Vào app trực tiếp nếu runtime guard và policy state cho phép.

Approved

App backgrounded by another app

Không logout. Trong kiosk mode, background by unapproved app should not occur in normal field operation.

Approved

App foreground again

Reuse active session nếu session còn valid.

Approved

Screen off/on

Không logout.

Approved

Device reboot

Expire previous session và yêu cầu login lại.

Approved

Normal recording without login

Reject theo operator-auth-required behavior.

Approved

Emergency recording without login

Cho phép emergency override bằng `EMERGENCY_OVERRIDE_ADMIN`.

Approved

Device policy missing in production profile

Normal field operation phải bị blocked/degraded theo policy-required behavior.

Approved Direction

Admin console access

Requires authorized Admin/Supervisor/Maintenance access; emergency override does not unlock admin console.

Approved Direction

## 5. System Modules Before Login

Module / Operation

Login Required?

Status

Logging and diagnostics

No

Approved

Device policy state detection

No

Approved Direction

User Restrictions / Lock Task verification

No

Approved Direction

DB recovery

No

Approved

Storage recovery

No

Approved

Capability detection

No

Approved

Feature eligibility evaluation

No

Approved

Update metadata check

No, only if safe and non-disruptive

Approved Direction

Sensor monitoring

No, nếu eligible và policy cho phép

Approved Direction

GPS/system tracking

No, nếu eligible và policy cho phép

Approved Direction

BDMA user sync readiness

Không yêu cầu DCAM operator login

Approved Direction

Storage dashboard basic health

May be visible before login only if product/security allows; otherwise after login/admin.

TBD

Normal recording/capture evidence

Yes

Approved

Emergency recording

No, nếu emergency override policy áp dụng

Approved

Admin/device settings console

Yes, Admin/Maintenance authorization required

Approved Direction

## 6. Android Dedicated Device / Kiosk Requirements

Requirement

Description

Status

Dedicated-device deployment support

DCAM production profile phải support dedicated-device/kiosk runtime.

Approved Direction

Device Owner state detection

DCAM phải detect whether required Device Owner / DCAM DPC policy is active.

Approved Direction

Policy authority validation

DCAM phải biết policy authority có tồn tại hay không và degradation behavior là gì.

Approved Direction

No external EMM assumption

DCAM current baseline không được phụ thuộc external EMM, Android Management API hoặc Managed Google Play.

Approved Direction

Lock Task allowlist validation

DCAM phải verify package được allowlisted trước khi start Lock Task.

Approved Direction

Lock Task runtime

DCAM phải enter/re-enter Lock Task trong normal field operation khi policy yêu cầu.

Approved Direction

User Restrictions baseline

DCAM phải apply/verify approved restriction profile khi có policy authority.

Approved Direction

Home/Launcher policy

DCAM có thể được set preferred Home/Launcher nhưng không được coi đây là kiosk control duy nhất.

Approved

In-app replacement for blocked system surfaces

DCAM phải cung cấp controlled in-app console cho các thao tác cần thiết thay vì cho user thoát sang Android Settings/File Manager/Gallery không kiểm soát.

Approved Direction

Maintenance Mode

DCAM phải có controlled support/factory flow để tạm thoát hoặc nới kiosk policy.

Approved Direction

Maintenance Password Gate

Maintenance entry phải yêu cầu password/credential và audit theo Security Design.

Approved Direction

Policy failure state

Nếu Device Owner/Lock Task/User Restrictions không đạt production baseline, app phải vào `DEVICE_POLICY_REQUIRED`, `POLICY_DEGRADED` hoặc safe controlled state.

Approved Direction

Policy audit

Policy apply/remove/failure và Maintenance Mode entry/exit phải được log/audit bằng safe reason codes.

Approved Direction

## 7. In-app Operation / Device / Media Console Requirements

Khi DCAM chạy kiosk, recording vẫn là tính năng chính, nhưng app phải có console nội bộ để thay thế các bề mặt hệ thống bị chặn.

Feature Group

Requirement

Access Direction

Status

Main Navigation

`Record / Live View` là default screen; `Setting` là console hub; Back behavior do DCAM kiểm soát.

All users, role-based modules.

Approved Direction

App Operation Settings

Cho phép cấu hình video resolution, FPS/quality, bitrate/profile, audio, pre-record time, post-record time, file split duration và các setting recording/app behavior khác.

Admin/Supervisor; operator limited if approved.

Approved Direction

Device/System Settings Proxy

Cho phép thao tác hoặc hiển thị USB mode, Wi-Fi, GPS/location, device information, network status, brightness/volume nếu approved.

Admin/Maintenance; selected read-only operator view.

Approved Direction

Login Settings

Current user có thể đổi mật khẩu/kiểu đăng nhập/đăng ký face auth nếu capability/security policy cho phép.

Current user / Admin recovery.

Approved Direction

User Settings

Admin-only thêm/sửa/xóa hoặc disable user, reset credential, role/login method eligibility.

Admin only.

Approved Direction

File/Storage Manager

Hiển thị storage summary và danh sách media finalized.

Operator view / Admin / Support depending on data.

Approved Direction

Media Viewer

Cho phép xem finalized media trong app.

Operator limited / Admin; visibility TBD.

Approved Direction

File/Media action policy

File Manager and Media Viewer are read-only/view-only; delete/edit/mark/export/share không thuộc MVP baseline.

All roles.

Approved Direction

Emergency Settings

Reserve settings group for emergency trigger/pre/post/override behavior; exact values TBD.

Admin/Security review.

TBD

Server Connection

Reserve group for future server profile/connection status.

Future.

Future / TBD

Live Stream / PTT

Reserve group for future live stream and push-to-talk.

Future.

Future / TBD

AI Mode

Reserve group for future AI mode/model/profile/threshold controls; AI must remain capability-gated.

Future/Admin.

Future / TBD

Rules:

Rule

Description

OP-CONSOLE-001

In-app console must not expose unrestricted Android Settings.

OP-CONSOLE-002

System-level settings requiring Device Owner/DPC must route through approved policy managers.

OP-CONSOLE-003

Recording-affecting settings must be applied only when recording/finalization/emergency/recovery guard allows.

OP-CONSOLE-004

Storage/media UI must use StorageService/SQLite state, not direct raw UI file operations.

OP-CONSOLE-005

Media Viewer must only open finalized media and must not modify evidence content.

OP-CONSOLE-006

Future features must remain hidden/disabled until approved requirements/design exist.

OP-CONSOLE-007

Admin/system/user/update setting changes must be auditable using safe reason codes.

## 8. Update Requirements

Requirement

Description

Status

Self Update primary path

DCAM Self Update / APK update là primary update path cho current baseline.

Approved Direction

No Managed Google Play baseline

Managed Google Play / Android Management API policy-driven update is not applicable.

Approved Direction

Update runtime guard

Update must defer during recording, emergency, finalization, DB/storage recovery, policy recovery or unsafe maintenance transition.

Approved Direction

Package validation

Update package must pass package identity, checksum, signature, version and compatibility validation.

Approved Direction

Policy restore

After update/restart, DCAM must verify app version where possible and restore kiosk policy.

Approved Direction

Failure handling

Update failure must preserve controlled runtime state and must not corrupt evidence.

Approved Direction

Manual Play Store fallback

Optional only if GMS/Play Store exists and approved maintenance/factory process allows it.

Conditional / POC Required

Play Store fallback control

Play Store fallback must run only through approved maintenance flow and approved target list.

Conditional / POC Required

## 9. Device Capability Requirements

Requirement

Description

Status

Hardware Detection

Detect camera, microphone, sensors, GPS/location, storage, compute capability và optional auth method hardware.

Approved Direction

Android Capability Detection

Detect Android version, foreground service support, permission model, background limits, Lock Task support và Device Owner/DPC policy state nếu applicable.

Approved Direction

Login Method Capability

Detect face authentication, QR scan và NFC login methods có available/safe hay không.

Approved Direction

Kiosk Policy Capability

Detect Device Owner active state, Lock Task permitted state, Home/Launcher policy behavior và supported/unsupported User Restrictions.

Approved Direction

Update Capability

Detect Self Update install capability, package installer constraints, GMS/Play Store availability and fallback availability.

Approved Direction

In-app Console Capability

Detect which device/system settings can be controlled in app, which require Maintenance Mode, and which are read-only/TBD.

Approved Direction

Performance Detection

Estimate CPU/memory/storage write class và AI feasibility nếu cần.

Approved Direction

Capability Persistence

Persist capability/eligibility result trong `dcam.db`.

Approved Direction

Capability Notification

User/admin/support cần thấy unsupported/degraded feature or policy reason.

Approved Direction

Safe Startup

Thiếu optional hardware hoặc unsupported optional restriction không được làm app crash.

Approved

Core Feature Block

Thiếu core capability như camera/storage hoặc required policy authority phải block affected core operation với message rõ ràng.

Approved Direction

## 10. Runtime Pruning Requirements

Feature

Unsupported Runtime Behavior

Status

GPS Tracking

Không start LocationMonitoringService hoặc register location listener.

Approved Direction

Fall Detection

Không register unavailable sensor listeners; degrade/disable nếu approved.

Approved Direction

Realtime AI

Không load model, attach frame analyzer hoặc start AI worker.

Approved Direction

Face Authentication

Không enable face login nếu thiếu hardware/security policy.

Approved Direction

QR Login

Không enable QR login nếu thiếu camera/decoder/security policy.

Approved Direction

NFC Login

Không enable NFC login nếu thiếu NFC hardware/permission/security policy.

Approved Direction

Weapon Detection

Không load weapon model nếu performance/capability không đủ.

Approved Direction

Audio Recording

Không start audio pipeline nếu microphone unsupported/permission missing.

Approved Direction

Encryption

Không chạy unsafe encryption path nếu thiếu performance/security prerequisite; áp dụng Security & Encryption Design.

Approved Direction

Lock Task Mode

Không start Lock Task nếu package chưa được allowlisted; enter policy-required/degraded state.

Approved Direction

Maintenance Mode

Không enable Maintenance Mode nếu auth/audit/security prerequisite chưa đạt.

Approved Direction

Self Update

Không chạy install nếu package validation, runtime guard hoặc policy state không đạt.

Approved Direction

Manual Play Store Fallback

Không show/enable nếu GMS/Play Store unavailable hoặc process chưa approved.

Conditional

Device/System Settings Control

Không show active control nếu Android/OEM/policy không support; show read-only/status or unsupported reason.

Approved Direction

Media Viewer

Không enable playback nếu media not finalized, encrypted without access, or playback would disrupt active recording/finalization.

Approved Direction

Future Live/PTT/AI Mode

Không show as production enabled until future requirement/design is approved and eligibility passes.

Approved Direction

## 11. Permission and Policy Requirements

Permission / Policy Area

Requirement

Status

Camera

Required cho recording/capture/preview frame analysis và camera-based login method nếu dùng.

Approved

Microphone

Required cho audio-enabled recording.

Approved

Location / GPS

Required only khi location tracking được enable và eligible.

Approved

Storage

Required hoặc adapted theo Android version và device policy.

Approved

Notification

Required khi foreground service/status notification cần dùng.

Approved

NFC

Required only khi NFC login được enable và device supports it.

Approved Direction

Install Package

Required only cho approved Self Update path.

Approved Direction

Device Owner / DPC Policy

Required cho production dedicated-device policy nếu deployment profile yêu cầu.

Approved Direction

Lock Task Allowlist

Required trước khi start Lock Task.

Approved Direction

User Restrictions

Required/conditional theo approved restriction profile.

Approved Direction

System Setting Control

May require Device Owner/DPC authority, restricted Android panel, OEM API or Maintenance Mode depending on setting.

Approved Direction

Permission/policy failure rule:

Missing optional permission or optional policy capability must not crash DCAM.
Only the affected feature, login method, console control or policy-controlled behavior is blocked, degraded, read-only or pruned.
Missing required production kiosk policy must enter controlled policy-required/degraded behavior.
## 12. Power, Battery and Thermal Requirements

Requirement

Description

Status

Low Battery Behavior

DCAM có thể degrade monitoring/AI theo policy, nhưng không được làm hỏng recording.

Approved Direction

Thermal Behavior

DCAM có thể throttle/degrade AI hoặc monitoring khi device quá nóng.

Approved Direction

Continuous Monitoring Cost

Sensor/GPS monitoring phải được policy-controlled.

Approved Direction

AI Inference Cost

AI frame analysis phải được giới hạn theo FPS/resolution/model/device capability.

Approved Direction

Auth Method Cost

Heavy auth method không được block startup/recording-critical work.

Approved Direction

Media Viewer Cost

Playback/media review must not disrupt active recording/finalization or emergency flow.

Approved Direction

Storage Dashboard Cost

Storage statistics must avoid expensive scans during recording/finalization; use cached DB/state when possible.

Approved Direction

Kiosk Policy Recovery Cost

Policy recovery không được làm chậm hoặc phá recording/finalization; nếu unsafe thì defer policy change và log reason.

Approved Direction

Self Update Cost

Download/validation/install must respect battery/charging/storage/network preconditions.

Approved Direction

Capability Re-evaluation

DCAM có thể re-evaluate temporary capability state sau thay đổi battery/thermal/storage, permission, auth capability, update capability hoặc policy state.

Approved Direction

## 13. Relationship with Other Documents

Related Document

Relationship

05 - User & Device Operation Requirements

Source of truth cho user/operator requirement, login policy và emergency override.

DCAM Android Device Owner & Kiosk Policy Design

Source of truth cho Device Owner/DPC policy, Lock Task Mode, User Restrictions, Home/Launcher policy, Maintenance Mode và policy recovery.

DCAM In-App Operation, Device Settings & Media Console Design

Source of truth cho in-app operation settings, device/system settings proxy, file/storage manager, media viewer, Login/User settings, Maintenance UX, Emergency Settings TBD và future placeholders.

DCAM Self Update Design

Source of truth cho Self Update / APK artifact / validation / install flow.

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision

Source of truth cho long-term architecture decision và rationale.

DCAM Android Operation Design

Source of truth cho runtime initialization, policy verification, login screen, session lifecycle, foreground service, update runtime và recovery.

DCAM Recording & Capture Design

Source of truth cho recording operator gate, emergency override attribution và recording setting application.

DCAM Storage Design

Source of truth cho storage mechanics, storage state and media finalization.

DCAM SQLite Database Design

Source of truth cho persisted user/auth/session/runtime/settings/media/update/optional policy snapshot data.

DCAM Device Capability & Feature Eligibility Design

Source of truth cho capability detection, eligibility states và runtime pruning.

DCAM State Machine Design

Source of truth cho cross-runtime guards, kiosk policy states, update states và runtime registration boundaries.

DCAM Realtime AI Detection Design

AI chỉ start nếu device eligible và chỉ emit events.

DCAM Sensor & Location Monitoring Design

Sensor/GPS monitoring chỉ start nếu eligible và chỉ emit events.

DCAM Security & Encryption Design

Source of truth cho auth/security/encryption/kiosk exit/admin console/update security implementation direction.

09 - System Settings Requirements

Settings không được override capability limits; kiosk config là requested policy, actual apply thuộc Kiosk Policy Design; update settings align với Self Update Design.

## 14. Practical Conclusion

DCAM Android operation phải capability-aware, login-aware, kiosk-policy-aware, console-aware và update-aware.

Detect device policy state early
Do not assume external EMM / Android Management API / Managed Google Play
Prefer DCAM-as-DPC / local Device Owner if firmware supports it
Apply/verify approved User Restrictions
Verify Lock Task allowlist
Enter/re-enter Lock Task Mode for normal field operation
Provide controlled in-app console because user cannot leave app freely
Record / Live View is default screen
Setting is console hub
Expose App Operation Settings through safe setting/apply guards
Expose Device/System Settings only as approved controlled proxy
Expose File/Storage Manager and Media Viewer as read-only/view-only
Support Login Settings and Admin-only User Settings
Require Maintenance Password Gate for controlled maintenance
Use DCAM Self Update / APK update as primary update path
Treat manual Play Store update as optional controlled fallback only
Keep Emergency Settings, Server Connection, Live Stream, PTT and AI Mode as TBD/future groups until approved
Detect capability early
Evaluate feature eligibility
Start system modules that do not require login
Show login when operator session is missing or rebooted
Initialize only eligible runtime modules
Prune unsupported optional features
Block normal recording/capture until operator is authenticated
Allow emergency override with EMERGENCY_OVERRIDE_ADMIN
Keep recording and emergency evidence stable
Use controlled degraded/policy-required state when required kiosk policy is missing