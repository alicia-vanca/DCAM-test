# DCAM In-App Operation, Device Settings & Media Console Design

**Page ID**: 49840330  
**Version**: 16  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49840330

---


# DCAM In-App Operation, Device Settings & Media Console Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design / In-App Console Design

Version

Draft 1.4

Status

Draft

Approval Scope

Draft in-app console/device-settings/media-console design only, including corrected GMS-free update and maintenance boundary; không phải approved implementation hoặc release baseline.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Security Reviewer / QA Lead / BDMA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

PM/BA, Tech Lead, Android Developers, QA, Support, Security Reviewer, BDMA Team

Last Updated

2026-08-25

Related Jira

Không có

Related Documents

DCAM Android Device Owner & Kiosk Policy Design, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, 10 - Android Device Operation Requirements, 09 - System Settings Requirements, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Security & Encryption Design, DCAM State Machine Design, DCAM Device Capability & Feature Eligibility Design, DCAM Self Update Design, DCAM-BDMA Data Contract, DCAM QA Test Strategy & Test Matrix

## 1. Purpose

Khi DCAM chạy trong Android dedicated-device / kiosk deployment, field user không thể hoặc không nên thoát app để vào Android Settings hay file manager mặc định.

Vì vậy DCAM phải cung cấp một **in-app device console** để vận hành thiết bị và app trong phạm vi được phép.

Tài liệu này là source of truth cho các nhóm màn hình/tính năng bên trong DCAM app:

App Operation Settings
Device / System Settings proxy
File / Storage Manager / Media Viewer
Login Settings
User Settings (Admin only)
Admin / Maintenance with Maintenance Password Gate
No Google Play Store/Managed Google Play/Google-account update target
DCAM Self Update as primary non-EMM update path
Emergency Settings
Server Connection / Live Stream / PTT / AI Mode placeholders
Chi tiết Device Owner / Lock Task / User Restrictions vẫn thuộc **DCAM Android Device Owner & Kiosk Policy Design**. Tài liệu này chỉ định nghĩa UI/UX, permission/access level, runtime guard và apply behavior bên trong app.

## 2. Core Decision

Trong kiosk mode, DCAM không chỉ là recording app.
DCAM cũng là controlled in-app console cho các chức năng operation, device, storage, media, login, user-management và maintenance được phép.
DCAM không được phụ thuộc vào unrestricted Android Settings, external file manager, gallery app hoặc launcher app cho normal field operation.

Việc thoát normal kiosk operation phải được bảo vệ và kiểm soát:

Enter Maintenance Mode / Exit Kiosk temporarily requires Maintenance Password Gate.
Exit Kiosk temporarily means controlled maintenance access only.
Full Android unrestricted mode is not supported.
Current device baseline: No external EMM / No Android Management API / No Managed Google Play. (per ADR - Dedicated Device / Device Owner / Lock Task Decision)

Update decision for current baseline:

Primary update path = DCAM Self Update / APK update.
Google Play Store/Managed Google Play/Google-account update is not a supported maintenance flow.
Not supported = Managed Google Play / Android Management API policy-driven update cho current baseline.
No Google account is used or stored for production maintenance.
## 3. Scope

### 3.1 In Scope

Feature Group

Description

App Operation Settings

Recording và app behavior settings như video resolution, FPS, bitrate, pre-record time, post-record time, audio, file split duration và overlay options.

Device / System Settings

Controlled access tới USB mode, Wi-Fi, GPS/location, brightness, volume, device info và system-level settings cần cho kiosk operation.

File / Storage Manager

Storage usage summary, storage health, file count, available space, final media list, emergency/important media filter và read-only file/media visibility.

Media Viewer

In-app playback/viewer cho finalized media, metadata view, emergency/important markers và read-only evidence-safe viewing.

Login Settings

Self-service login settings như change password, change login method và register/enroll face authentication nếu supported.

User Settings (Admin only)

Admin-only user management như add user, edit user, delete/disable user, reset credentials, assign role và configure login method eligibility.

Admin / Maintenance

Admin-only support/maintenance entry point cho controlled kiosk exit, approved Android system access, diagnostics, recovery và policy restore.

Maintenance Password Gate

Protection cho Enter Maintenance Mode / Exit Kiosk temporarily.

Controlled temporary kiosk exit

Temporary access chỉ tới approved apps/settings screens; không có full unrestricted Android mode.

DCAM Self Update

Primary update path cho non-EMM current device baseline.

Google Play Store manual update target

Not Applicable; prohibited by GMS-free production baseline.

Emergency Settings

TBD group cho emergency trigger behavior, emergency pre/post recording, override behavior và emergency marker defaults.

Future Connectivity / Intelligence

Server connection, live stream, PTT và AI Mode placeholders cho future phases.

Access Control

Operator/Admin/Maintenance access levels cho từng screen/action.

Runtime Guard

Block/defer unsafe setting changes trong lúc recording, emergency, finalization, recovery hoặc policy transition.

Audit

Log admin/system/user-management/maintenance/update changes bằng safe reason codes.

### 3.2 Out of Scope

Area

Direction

Full Android unrestricted mode

Không được hỗ trợ bởi DCAM production design.

Unrestricted launcher / unrestricted app drawer / unrestricted Settings access

Không được hỗ trợ; chỉ approved apps/settings screens được mở trong controlled maintenance flow.

Managed Google Play / Android Management API policy-driven update

Not applicable (per ADR).

Google account usage for maintenance

Not Applicable; no Google account is used or stored on production device.

General Play Store browsing/installing unapproved apps

Không được hỗ trợ.

Exact recording state machine and camera pipeline

Managed by DCAM Recording & Capture Design.

Physical storage mechanics, temp/final movement, recovery

Managed by DCAM Storage Design.

DB schema and persisted settings/cache/user/auth data

Managed by DCAM SQLite Database Design.

Remote config requested settings

Managed by 09 - System Settings Requirements.

Security constraints, credential storage, biometric handling and sensitive logging

Managed by DCAM Security & Encryption Design.

Self Update artifact/download/package validation detail

Managed by DCAM Self Update Design.

## 4. User Access Model

Mode / Role

Intended User

Allowed Capability Direction

Operator Mode

Field operator sử dụng BodyCamera bình thường.

Recording controls, status, limited media review, limited non-destructive settings nếu approved, own Login Settings nếu allowed.

Supervisor/Admin Mode

Authorized local admin/supervisor.

App operation settings, storage review, selected device settings, diagnostics, Login Settings, User Settings và approved maintenance actions.

Maintenance Mode

Factory/support/admin workflow.

Controlled temporary access chỉ tới approved apps/settings screens, Wi-Fi/USB/system setup, BFF/R2 or approved local/factory update/recovery, diagnostics, logs, hardware checks và approved support-level recovery. Entry yêu cầu Maintenance Password Gate.

System Mode

Chỉ dành cho internal system/runtime.

Auto recovery, policy verification, remote config apply, update guard, storage recovery, user/auth sync/recovery và emergency override.

Rules:

Rule

Description

CONSOLE-ACCESS-001

Normal operator không được nhận unrestricted Android Settings access.

CONSOLE-ACCESS-002

Admin/Maintenance actions yêu cầu explicit authorization.

CONSOLE-ACCESS-003

Maintenance Mode phải tuân theo Kiosk Policy Design và Security Design.

CONSOLE-ACCESS-004

Emergency override không phải admin login và không được unlock admin console, User Settings hoặc Maintenance Mode.

CONSOLE-ACCESS-005

Access level và action result phải auditable cho admin/system/user-management/maintenance changes.

CONSOLE-ACCESS-006

User Settings là Admin-only. Operator không được add, edit, delete/disable users hoặc assign roles.

CONSOLE-ACCESS-007

Login Settings có thể cho phép current user self-service changes chỉ trong approved security policy.

CONSOLE-ACCESS-008

Enter Maintenance Mode / Exit Kiosk temporarily phải yêu cầu Maintenance Password Gate.

CONSOLE-ACCESS-009

Exit Kiosk temporarily không được trở thành full Android unrestricted mode.

CONSOLE-ACCESS-010

Google Play Store access/navigation/install và Google-account entry bị cấm; Console không được expose package, target hoặc fallback nào cho Play Store manual update.

CONSOLE-ACCESS-011

Managed Google Play / policy-driven update: not applicable (per ADR).

## 5. Main Navigation Direction

### 5.1 Default Screen and Setting Hub

`Record / Live View` là default main screen sau startup/login.

`Setting` là in-app console hub. Màn này chứa các nút/card nhỏ để đi vào các console modules còn lại.

DCAM Runtime Navigation
├── Record / Live View                 ← Default Main Screen
└── Setting                            ← Console Hub Screen
    ├── Media / Files
    ├── Storage
    ├── App Operation Settings
    ├── Device / System Settings
    ├── Login Settings
    ├── User Settings (Admin only)
    ├── Emergency Settings (TBD)
    ├── Server / Connection (Future)
    ├── Live Stream / PTT (Future)
    ├── AI Mode (Future)
    ├── Diagnostics / Support
    └── Admin / Maintenance
        ├── Enter Maintenance Mode / Exit Kiosk temporarily
        │   └── Maintenance Password Gate
        ├── DCAM Self Update / APK update
        └── Approved DCAM Self Update or local/factory recovery action
### 5.2 Back Button Behavior

Trong kiosk mode, Android Back phải được DCAM navigation kiểm soát. Back không được exit DCAM hoặc làm lộ Android launcher/system UI.

Record / Live View
    └── Back → Setting

Setting
    └── Back → Record / Live View

Setting → Any child module
    └── Back → Setting
Detailed behavior:

Current Screen

Back Button Result

Notes

`Record / Live View`

Navigate to `Setting`.

Recording không được bị stop bởi Back.

`Setting`

Navigate to `Record / Live View`.

Back chuyển từ hub quay lại main recording screen.

`Media / Files`

Navigate to `Setting`.

File Manager vẫn read-only.

`Storage`

Navigate to `Setting`.

Không có destructive storage action.

`App Operation Settings`

Navigate to `Setting`.

Unsaved changes theo screen-specific confirm/discard rule nếu cần.

`Device / System Settings`

Navigate to `Setting`.

System setting transition không được expose unrestricted Android Settings.

`Login Settings`

Navigate to `Setting`.

Unsaved auth-method changes phải completed, cancelled hoặc safely discarded.

`User Settings (Admin only)`

Navigate to `Setting`.

Admin-only; unsaved user changes phải completed, cancelled hoặc safely discarded.

`Emergency Settings`

Navigate to `Setting`.

TBD screen không được change behavior cho đến khi approved.

`Server / Connection`

Navigate to `Setting`.

Future screen hidden/disabled cho đến khi approved.

`Live Stream / PTT`

Navigate to `Setting`.

Future screen hidden/disabled cho đến khi approved.

`AI Mode`

Navigate to `Setting`.

Future screen hidden/disabled cho đến khi approved.

`Diagnostics / Support`

Navigate to `Setting`.

Support action state có thể cần confirmation nếu đang chạy.

`Admin / Maintenance`

Navigate to `Setting`, hoặc giữ lại nếu cần safe-exit/restore.

Maintenance exit phải tuân theo Kiosk Policy/Security rules.

`Maintenance Password Gate`

Navigate to `Admin / Maintenance` hoặc `Setting` theo UX decision.

Back phải cancel entry; không được bypass gate.

`Approved update/recovery target`

Return to DCAM maintenance flow when action completes/cancels.

Must never provide Play Store browsing or Google-account entry.

Rules:

Rule

Description

CONSOLE-NAV-001

`Record / Live View` là default main screen sau successful startup/login.

CONSOLE-NAV-002

Pressing Back trên `Record / Live View` mở `Setting`.

CONSOLE-NAV-003

Pressing Back trên `Setting` quay lại `Record / Live View`.

CONSOLE-NAV-004

Pressing Back trong bất kỳ child module nào quay lại `Setting`.

CONSOLE-NAV-005

Back không được exit DCAM khi kiosk mode đang active.

CONSOLE-NAV-006

Back không được stop active recording, emergency recording, finalization, recovery hoặc policy transition.

CONSOLE-NAV-007

Child module access vẫn phải tôn trọng role, capability, policy và runtime guard.

CONSOLE-NAV-008

Future modules phải hidden, disabled hoặc unavailable cho đến khi có approved design.

CONSOLE-NAV-009

Back không được bypass Maintenance Password Gate.

CONSOLE-NAV-010

Không có Play Store manual-update target. Mọi request/navigation tới Play Store hoặc Google-account flow phải bị reject và quay lại controlled DCAM maintenance flow.

## 6. App Operation Settings

App Operation Settings điều khiển DCAM application behavior và recording-related runtime settings.

Setting

Example Values / Direction

Access

Apply Rule

Status

Video resolution

720p / 1080p / device-supported profiles.

Admin / Supervisor

Apply trước next recording; không apply trong active recording.

Approved Direction

Video FPS

25 / 30 / 60 nếu supported.

Admin / Supervisor

Apply trước next recording; validate capability.

Approved Direction

Video bitrate / quality

Low / Medium / High hoặc numeric profile.

Admin / Supervisor

Apply trước next recording; validate storage/performance.

Approved Direction

Audio recording

On / Off nếu product policy cho phép.

Admin / Supervisor

Apply trước next recording.

Approved Direction

Pre-record time

Ví dụ: 0 / 15 / 30 / 60 seconds.

Admin / Supervisor

Apply khi buffer engine safe; không apply trong unstable runtime.

Approved Direction

Post-record time

Ví dụ: 0 / 10 / 30 seconds.

Admin / Supervisor

Apply trước next session hoặc safe window.

Approved Direction

File split duration

Ví dụ: 5 / 10 / 15 / 30 minutes.

Admin / Supervisor

Apply trước next recording.

Approved Direction

Timestamp overlay

On / Off / format TBD.

Admin / Supervisor

Apply trước next recording; validate encoder support.

TBD

GPS overlay / metadata

Metadata only / overlay / disabled.

Admin / Supervisor

Phụ thuộc GPS eligibility và privacy policy.

TBD

Storage target

Internal / External / Auto.

Admin / Maintenance

Chỉ apply khi không có active recording/finalization.

Approved Direction

Auto-delete / retention

Disabled / policy-defined.

Admin / Future

Không được delete evidence ngoài dự kiến; cần BDMA/import policy.

TBD

Rules:

Rule

Description

CONSOLE-APP-001

Recording-affecting settings không được apply trong active recording, emergency recording, finalization hoặc recording recovery.

CONSOLE-APP-002

Settings phải được validate theo device capability và feature eligibility.

CONSOLE-APP-003

Remote config, local admin setting và default setting phải resolve bằng deterministic priority rule.

CONSOLE-APP-004

Invalid setting phải bị reject và last valid applied setting vẫn active.

CONSOLE-APP-005

Applied setting changes phải được persist trong `dcam.db` và auditable.

## 7. Device / System Settings

Device/System Settings là các controlled device functions cần thiết vì user không thể rời DCAM trong kiosk mode.

Important boundary:

Device/System Settings UI là controlled proxy.
Điều này không có nghĩa DCAM expose unrestricted Android Settings.
Một số settings yêu cầu Device Owner / DPC authority hoặc Maintenance Mode.
Một số settings chỉ được mở restricted system panel nếu approved bởi policy.

Setting / Function

Direction

Access

Policy Dependency

Status

USB mode

Charging only / Data transfer / ADB/support mode direction.

Admin / Maintenance

User Restrictions và BDMA boundary.

Approved Direction / Values TBD

Wi-Fi

View/connect configured networks hoặc enter setup flow.

Admin / Maintenance

Có thể cần Device Owner/DPC hoặc restricted system panel.

Approved Direction

GPS / Location

Enable/disable DCAM location feature; system-level toggle có thể cần policy/admin.

Admin / Supervisor

Device capability + Android policy.

Approved Direction

Bluetooth

Future/support only nếu cần.

Maintenance

User Restrictions profile.

TBD

Brightness / screen timeout

Điều chỉnh in-app/device display behavior nếu allowed.

Operator limited / Admin

Android API/policy support.

TBD

Volume / audio prompt level

Điều chỉnh allowed app/system volume.

Operator limited / Admin

Android version/OEM behavior.

TBD

Date/time/timezone

View only hoặc admin-controlled nếu product cho phép.

Maintenance

Security/audit required.

TBD

Device information

View serial, owner, manufacture date, model, firmware, app version, contract version.

Operator view / Admin edit limited

Identity/provisioning rules.

Approved Direction

Network status

View connectivity, IP, server reachability.

Operator view / Admin

None hoặc network permission.

Approved Direction

Kiosk policy status

View policy state, Lock Task status, restriction profile.

Admin / Support

Kiosk Policy Design.

Approved Direction

Rules:

Rule

Description

CONSOLE-SYS-001

Device/System Settings chỉ được expose approved controls, không expose unrestricted Android Settings.

CONSOLE-SYS-002

System-level changes cần Device Owner/DPC phải đi qua policy managers.

CONSOLE-SYS-003

Maintenance-only settings không được available trong normal Operator Mode.

CONSOLE-SYS-004

USB mode và ADB behavior không được silently làm hỏng BDMA import/user sync.

CONSOLE-SYS-005

System setting changes phải auditable khi ảnh hưởng security, network, storage hoặc policy behavior.

## 8. File / Storage Manager

DCAM phải cung cấp in-app storage visibility vì kiosk mode ngăn user dùng arbitrary file managers.

### 8.1 Storage Dashboard

Item

Description

Access

Status

Total storage

Tổng dung lượng DCAM storage root.

Operator view / Admin

Approved Direction

Used storage

Dung lượng DCAM media, logs, DB, temp/recovery folders đang dùng.

Operator view / Admin

Approved Direction

Free storage

Dung lượng còn lại an toàn cho recording.

Operator view / Admin

Approved Direction

Estimated remaining record time

Ước tính dựa trên current quality setting.

Operator view / Admin

Approved Direction

Storage health

Writable, mounted, low-space, unavailable, recovery required.

Operator view / Admin

Approved Direction

Internal/external status

Current storage root và external card state.

Operator view / Admin

Approved Direction

BDMA readiness summary

Count/size của files ready for BDMA.

Admin / BDMA / Support

Approved Direction

Temp/recovery summary

Count/size của files cần recovery.

Admin / Support

Approved Direction

### 8.2 File Manager

File Manager là **read-only / view-only**. Không được cung cấp delete, edit, mark, export hoặc share actions.

Function

Direction

Access

Status

List final media

Hiển thị finalized recordings/images/audio theo date/type/status.

Operator limited / Admin

Approved Direction

Filter emergency/important media

Hiển thị nhanh emergency hoặc important evidence khi metadata đã tồn tại.

Operator limited / Admin

Approved Direction

View metadata

Hiển thị time, operator snapshot, duration, size, GPS/AI/event markers nếu available.

Operator limited / Admin

Approved Direction

Open finalized media in viewer

Mở selected finalized media trong read-only Media Viewer.

Operator limited / Admin

Approved Direction

Rules:

Rule

Description

CONSOLE-FILE-001

In-progress/temp files không được hiển thị như final playable media.

CONSOLE-FILE-002

File Manager là read-only: không delete, không edit/metadata mutation, không mark-important action, không export/share action.

CONSOLE-FILE-003

File Manager và Media Viewer không được modify media file content hoặc media metadata.

CONSOLE-FILE-004

Storage/File Manager phải dùng StorageService/SQLite state, không dùng raw ad-hoc file scanning từ UI.

CONSOLE-FILE-005

Evidence-like files phải được preserve trong trường hợp uncertainty/recovery.

## 9. Media Viewer

Media Viewer cung cấp safe in-app review cho finalized media.

Feature

Direction

Access

Status

Video playback

Chỉ play finalized MP4.

Operator limited / Admin

Approved Direction

Image viewer

View finalized image captures nếu feature tồn tại.

Operator limited / Admin

TBD

Audio playback

Play finalized audio nếu feature tồn tại.

Operator limited / Admin

TBD

Metadata panel

Hiển thị safe metadata và operator snapshot.

Operator limited / Admin

Approved Direction

Timeline markers

Hiển thị emergency/event/AI markers nếu available.

Operator/Admin

TBD

Playback during recording

Thường disabled hoặc limited để tránh resource conflict.

Operator/Admin

TBD

Full file path display

Tránh expose raw sensitive/internal paths cho normal operator.

Admin/Support only

Approved Direction

Rules:

Rule

Description

CONSOLE-MEDIA-001

Media Viewer chỉ được mở finalized media.

CONSOLE-MEDIA-002

Playback không được interfere với recording, emergency, finalization hoặc storage recovery.

CONSOLE-MEDIA-003

Operator visibility rules phải được định nghĩa trước khi show all files cho mọi user.

CONSOLE-MEDIA-004

Media Viewer không được bypass encryption/access policy nếu media encryption enabled.

CONSOLE-MEDIA-005

Media Viewer là read-only và không được delete, edit, mark, export hoặc share media.

## 10. Login Settings and User Settings

### 10.1 Login Settings

Login Settings là self-service authentication settings module cho currently authenticated user.

Function

Direction

Access

Status

Change password

Current user có thể đổi own password sau current-password verification hoặc approved re-authentication.

Current user / Admin recovery

Approved Direction

Change login method

User có thể chọn hoặc enable allowed login methods theo role/security/capability policy.

Current user / Admin approval if required

Approved Direction

Register face authentication

Enroll/register face auth nếu device capability, security policy và user role cho phép.

Current user / Admin approval if required

Approved Direction / Values TBD

Re-enroll face authentication

Replace existing face enrollment sau re-authentication hoặc admin-approved recovery.

Current user / Admin recovery

TBD

Remove own face auth

Remove current user's face auth nếu product/security policy cho phép fallback login.

Current user

TBD

Login method status

Hiển thị enabled/disabled/unsupported login methods như password, PIN, QR, NFC, face auth.

Current user / Admin

Approved Direction

Rules:

Rule

Description

CONSOLE-LOGIN-001

Login Settings chỉ được cho current-user self-service changes trừ khi Admin recovery flow được explicitly dùng.

CONSOLE-LOGIN-002

Change password phải yêu cầu current password hoặc approved re-authentication.

CONSOLE-LOGIN-003

Face auth enrollment phải pass device capability, security policy và liveness/quality requirements nếu applicable.

CONSOLE-LOGIN-004

Login method changes không được làm current user bị lock out nếu không có approved fallback method.

CONSOLE-LOGIN-005

Login credential, biometric template, face embedding hoặc sensitive auth material không được log.

CONSOLE-LOGIN-006

Login Settings không được available từ emergency override như admin capability.

CONSOLE-LOGIN-007

Tất cả sensitive login-setting changes phải được audit bằng safe reason codes.

### 10.2 User Settings (Admin only)

User Settings là Admin-only module cho local user management trên DCAM device.

Function

Direction

Access

Status

Add user

Tạo local user/operator/admin account theo approved role policy.

Admin only

Approved Direction

Edit user

Update user display name, role, status, login method eligibility hoặc non-sensitive profile fields.

Admin only

Approved Direction

Delete / disable user

Remove user khỏi active use hoặc mark user là deleted/disabled. Historical media/log attribution phải được preserve.

Admin only

Approved Direction

Reset password / credential

Reset password/PIN hoặc trigger re-enrollment flow theo security policy.

Admin only

Approved Direction

Manage role

Assign role như Operator, Supervisor/Admin hoặc Maintenance-support role nếu approved.

Admin only

Approved Direction / Values TBD

Manage login method eligibility

Allow/disable password, QR, NFC, face auth hoặc methods khác cho user dựa trên capability/security policy.

Admin only

Approved Direction

View user list

Hiển thị local user list và status.

Admin only

Approved Direction

View user detail

Hiển thị user profile, role, status và allowed login methods.

Admin only

Approved Direction

Rules:

Rule

Description

CONSOLE-USER-001

User Settings là Admin-only. Operator không được access module này.

CONSOLE-USER-002

Add/edit/delete/disable user actions phải được audit.

CONSOLE-USER-003

Delete user không được làm hỏng historical operator attribution trên existing media, logs, DB records hoặc BDMA import history.

CONSOLE-USER-004

Nếu user có historical media/log references, implementation nên ưu tiên disabled/deleted status thay vì physical hard delete.

CONSOLE-USER-005

Admin không được delete/disable last available admin account trừ khi có recovery path.

CONSOLE-USER-006

User credential reset không được expose plaintext password/PIN hoặc sensitive biometric material.

CONSOLE-USER-007

User Settings phải tuân theo DCAM Security & Encryption Design và SQLite schema constraints.

CONSOLE-USER-008

User changes phải compatible với BDMA user sync direction và không được silently corrupt local/cloud user mapping.

## 11. Admin / Maintenance and Maintenance Password Gate

Admin / Maintenance là in-app path duy nhất được approved để tạm thời rời normal kiosk operation.

Flow này phải được bảo vệ bằng **Maintenance Password Gate**.

DCAM chỉ hỗ trợ một maintenance exit model:

```
Exit Kiosk temporarily — Controlled Mode only
```

DCAM **không** hỗ trợ:

Full Android unrestricted mode
Unrestricted launcher access
Unrestricted app drawer access
Unrestricted Android Settings access
User-driven free navigation outside approved maintenance scope
### 11.1 Controlled Maintenance Entry Flow

Setting
    ↓
Admin / Maintenance
    ↓
Select Enter Maintenance Mode / Exit Kiosk temporarily
    ↓
Validate current user is Admin or approved Maintenance role
    ↓
Maintenance Password Gate
    ↓
Validate runtime guard is safe
    ↓
Stop Lock Task temporarily and/or relax only approved restrictions
    ↓
Open only approved Android Settings/system screen or approved maintenance app
    ↓
Return to DCAM
    ↓
Restore User Restrictions / Lock Task policy
    ↓
Return to normal kiosk operation
### 11.2 Controlled Mode Boundary

Area

Allowed in Controlled Mode

Not Allowed

Android Settings

Chỉ approved settings screens/panels cần cho maintenance, như Wi-Fi, Location/GPS, USB, display/volume hoặc device info nếu approved.

Free browsing toàn bộ Android Settings nếu không approved.

Apps

Chỉ approved maintenance/support/DPC/system apps được temporarily allowlisted; no Play Store package is allowlisted.

General app drawer, games, browser, personal apps, unapproved apps, Play Store browsing/install hoặc Google-account entry.

Google account / Play Store account

Not Applicable.

Any Google-account sign-in, persistence or maintenance procedure.

User Restrictions

Chỉ selected restrictions có thể temporarily relaxed cho một maintenance task cụ thể.

Clear toàn bộ restrictions hoặc để restrictions disabled sau maintenance.

Lock Task

Có thể stopped temporarily chỉ sau khi password gate success và runtime guard safe.

Stop Lock Task không có password, trong lúc recording/emergency, hoặc để stopped sau maintenance.

System changes

Chỉ approved changes cần cho support/factory/maintenance.

Arbitrary system customization, account changes ngoài approved Play update flow, factory reset, unapproved app install/uninstall hoặc security downgrade.

Navigation

Admin phải return qua approved DCAM/maintenance flow.

Free user-driven unrestricted Android navigation.

### 11.3 Maintenance Password Requirements

Requirement

Direction

Status

Dedicated maintenance password

Enter Maintenance Mode / Exit Kiosk temporarily yêu cầu dedicated maintenance password hoặc approved maintenance credential.

Approved Direction

Not operator password

Normal operator password không đủ để exit kiosk.

Approved Direction

Admin authorization

Caller phải là Admin hoặc approved Maintenance role trước password verification.

Approved Direction

Offline support

Maintenance password phải hoạt động offline nếu product yêu cầu field/factory support không có network.

Approved Direction

No hardcoded default

App không được ship với hardcoded/default production maintenance password.

Approved Direction

Secure storage

Bảo vệ maintenance credential theo Security Design; plaintext không được phép.

Approved Direction

No logging

Maintenance credential và các sensitive auth materials không được log.

Approved Direction

Failed attempt protection

Repeated failed attempts phải trigger delay, cooldown, temporary lockout hoặc admin/security alert theo policy.

Approved Direction

Audit

Success, failure, lockout, entry, exit và policy restore result phải được audit bằng safe reason codes.

Approved Direction

Session timeout

Maintenance session phải expire sau configured timeout hoặc inactivity và restore kiosk policy.

Approved Direction

Restore policy

DCAM phải re-enter Lock Task và restore approved User Restrictions sau maintenance.

Approved Direction

Reset/recovery

Maintenance password reset/recovery path là TBD và phải được Security/Product approve.

TBD

### 11.4 Allowed Maintenance Actions

Action

Password Required?

Runtime Guard

Status

Enter Maintenance Mode

Yes

Phải safe.

Approved Direction

Exit Lock Task temporarily

Yes

Phải safe.

Approved Direction

Open approved Android Settings screen/panel

Yes

Phải safe và policy-approved.

Approved Direction

Open approved maintenance/support app

Yes

Phải safe và policy-approved.

Approved Direction

Run DCAM Self Update / APK update

Yes nếu launched từ Maintenance; runtime guard required.

Phải safe; BFF/R2 authorization or approved local/factory package validation required.

Primary Current Baseline

Temporarily relax selected User Restrictions

Yes

Phải safe và policy-approved.

Approved Direction

Configure Wi-Fi/USB/system setting requiring Android screen

Yes

Phải safe và policy-approved.

Approved Direction

Run support diagnostics

Conditional; phụ thuộc sensitivity.

Không được disrupt recording/emergency/finalization.

Approved Direction

Restore normal kiosk mode

Không cần additional password nếu returning từ active maintenance session.

Luôn phải attempted.

Approved Direction

### 11.5 App Update Rules for GMS-free Baseline

Rule

Description

CONSOLE-UPD-001

Update applicability tuân theo ADR và Release & Build Applicability Matrix; console không được tự giả định policy-driven update capability.

CONSOLE-UPD-002

Primary remote update path là BFF-authorized DCAM Self Update / immutable R2/CDN APK như định nghĩa trong DCAM Self Update Design.

CONSOLE-UPD-003

Google Play Store, Managed Google Play, Android Management API và Google-account update flow không có maintenance target trong DCAM production profile.

CONSOLE-UPD-004

Approved local/factory APK package là support fallback duy nhất khi được authorize, validate và audit; nếu không có, DCAM safely defers update.

CONSOLE-UPD-005

Console phải không expose GMS/Play Store capability, target, navigation hoặc account entry.

CONSOLE-UPD-006

Sau update, DCAM phải verify app version/signature/update result nếu applicable và restore kiosk policy.

CONSOLE-UPD-007

App update không được chạy trong lúc recording, emergency, finalization, storage recovery, DB recovery, update/install unsafe state hoặc policy recovery.

CONSOLE-UPD-008

Dependency/manifest/source compliance và target-device GMS-free evidence phải pass theo ADR trước production claim.

### 11.6 Maintenance Password and Controlled Mode Rules

Rule

Description

CONSOLE-MAINT-001

Admin / Maintenance là approved path duy nhất để Enter Maintenance Mode / Exit Kiosk temporarily.

CONSOLE-MAINT-002

Enter Maintenance Mode / Exit Kiosk temporarily yêu cầu Maintenance Password Gate.

CONSOLE-MAINT-003

Maintenance Password Gate yêu cầu Admin hoặc approved Maintenance role trước password verification.

CONSOLE-MAINT-004

Emergency override không được satisfy Maintenance Password Gate.

CONSOLE-MAINT-005

Maintenance credential không được hardcoded, stored plaintext hoặc logged.

CONSOLE-MAINT-006

Failed maintenance attempts phải rate-limited và audited.

CONSOLE-MAINT-007

Maintenance Mode phải bị block trong active recording, emergency, post-record/finalizing, storage recovery, DB recovery, update/install hoặc policy recovery.

CONSOLE-MAINT-008

Back/cancel trên gate screen phải cancel entry và return to Admin / Maintenance hoặc Setting; không được bypass authentication.

CONSOLE-MAINT-009

Temporary kiosk exit chỉ được mở approved Android system surfaces hoặc approved maintenance apps.

CONSOLE-MAINT-010

DCAM phải restore User Restrictions và re-enter Lock Task sau maintenance timeout, manual exit, app resume, reboot hoặc process recovery khi có thể.

CONSOLE-MAINT-011

Maintenance entry/exit phải auditable mà không expose secret values.

CONSOLE-MAINT-012

Maintenance reset/recovery phải yêu cầu approved Admin/Security process.

CONSOLE-MAINT-013

Full Android unrestricted mode is not supported.

CONSOLE-MAINT-014

Maintenance không bao giờ được để device ở unrestricted Android state sau timeout, exit, reboot hoặc crash recovery.

## 12. Emergency Settings

Emergency Settings là TBD nhưng phải được giữ như first-class settings group.

Candidate settings:

Setting

Direction

Status

Emergency trigger source

Physical button / UI button / sensor / future server command.

TBD

Emergency pre-record time

Có thể khác normal pre-record time.

TBD

Emergency post-record time

Có thể khác normal post-record time.

TBD

Emergency auto-mark important

Mark emergency media là important/evidence.

TBD

Emergency audio/video profile

Dùng high priority profile nếu safe.

TBD

Emergency override behavior

Dùng `EMERGENCY_OVERRIDE_ADMIN` nếu không có operator session.

Approved Direction

Emergency notification/live action

Future server/live stream integration.

Future / TBD

Rules:

Rule

Description

CONSOLE-EMG-001

Emergency settings không được làm giảm evidence preservation nếu chưa explicit approval.

CONSOLE-EMG-002

Emergency override không phải admin login và không được unlock admin/settings console hoặc Maintenance Password Gate.

CONSOLE-EMG-003

Emergency-related changes phải auditable.

CONSOLE-EMG-004

Emergency behavior phải có priority hơn update, optional AI và non-critical settings apply.

## 13. Future: Server Connection / Live Stream / PTT / AI Mode

Các group này là placeholders cho future features và phải hidden, disabled hoặc marked unavailable cho đến khi design được approved.

Feature Group

Direction

Status

Server Connection

Server URL/profile, device online status, connection health, retry state.

Future / TBD

Live Stream

Start/stop live stream, stream quality, server target, emergency live behavior.

Future / TBD

PTT

Push-to-talk channel, audio route, server connection và permission model.

Future / TBD

AI Mode

AI detection on/off, model/profile, event type, performance mode, confidence threshold.

Future / TBD

JT808 / Tracking

Vehicle/bodycam tracking protocol nếu required.

Future / TBD

Rules:

Rule

Description

CONSOLE-FUT-001

Future settings không được xuất hiện như production controls cho đến khi requirement/design được approved.

CONSOLE-FUT-002

Future feature toggles vẫn phải pass capability/eligibility/runtime guard.

CONSOLE-FUT-003

AI mode không được trực tiếp điều khiển recording; nó chỉ emit events trừ khi future ADR thay đổi điều này.

CONSOLE-FUT-004

Live Stream/PTT không được interrupt emergency/recording/finalization.

## 14. Runtime Apply Guard

Tất cả console setting changes phải pass runtime guard.

User/Admin changes setting
    ↓
Validate access level
    ↓
Validate schema/value range
    ↓
Validate device capability and policy support
    ↓
Check runtime guard
    ↓
If safe: persist requested/applied state and apply
If unsafe: defer or reject with reason code
    ↓
Audit result
Blocked/deferred states:

recording active
emergency active
post-record/finalizing active
storage recovery active
DB recovery/migration active
policy recovery active
update/install active
unsafe low storage/thermal state
Additional auth/user-management guard:

Login/User setting changes
    ↓
Validate caller role and re-authentication requirement
    ↓
Validate account safety rule
    ↓
Validate credential/biometric security rule
    ↓
Persist only safe non-sensitive state
    ↓
Audit safe reason/result
Additional maintenance guard:

Enter Maintenance Mode / Exit Kiosk temporarily
    ↓
Validate Admin or approved Maintenance role
    ↓
Validate Maintenance Password Gate
    ↓
Validate runtime safe state
    ↓
Temporarily stop Lock Task / relax only approved restrictions
    ↓
Open only approved apps/settings screens
    ↓
Audit maintenance entry result
Additional update guard for current no-EMM baseline:

App update request
    ↓
Prefer DCAM Self Update / APK update
    ↓
Validate package identity, signature, checksum and version
    ↓
Validate runtime guard
    ↓
Install/update only through approved update path
    ↓
Verify update result where applicable
    ↓
Restore kiosk policy
GMS-free update-source guard:

Play Store / Managed Google Play / Android Management API /
Google-account update request
    ↓
Reject; do not expose an account, package, target or fallback
    ↓
Use only BFF-authorized R2/CDN Self Update
or separately approved local/factory package recovery
    ↓
DCAM validates artifact and safe-state
    ↓
Install/defer, record result and restore kiosk policy
## 15. Persistence Direction

Data Type

Storage Direction

Source of Truth

App operation settings

`dcam.db` requested/applied settings.

System Settings + this document + Recording Design

Device/system requested settings

`dcam.db` requested/applied settings hoặc policy manager state.

This document + Kiosk Policy Design

Kiosk policy settings

`dcam.db` requested settings / optional policy snapshot; actual Android policy state lấy từ policy managers.

Kiosk Policy Design

Storage/media view state

`dcam.db` media/storage state + StorageService.

Storage + SQLite + Data Contract

Login settings

`dcam.db` user/auth state cộng với secure credential/biometric storage như Security Design định nghĩa.

This document + Security + SQLite

User settings

`dcam.db` user table, role/status/auth-method eligibility và audit history.

This document + Security + SQLite + User Requirements

Face auth enrollment state

Secure storage / local auth store / DB reference only; exact implementation TBD bởi Security Design.

Security + Device Capability + SQLite

Maintenance credential state

Secure credential storage hoặc protected state; không có plaintext trong `dcam.db`. DB có thể lưu non-sensitive policy/audit/lockout metadata only.

This document + Security + SQLite

Maintenance session/audit state

`dcam.db` non-sensitive audit/session metadata và policy restore result.

This document + Kiosk Policy + Security

Approved maintenance target list

`dcam.db` hoặc config cache chỉ lưu approved DCAM/support/DPC/system package hoặc screen identifiers. Play Store, Managed Google Play, Android Management API và Google-account targets không được lưu/allowlist; không có unrestricted wildcard target.

This document + Kiosk Policy + Security

DCAM Self Update audit

`dcam.db` non-sensitive audit metadata: actor/source, artifact/version, validation result, install result, reason code.

This document + Self Update + Security

Prohibited external-store request audit

`dcam.db` non-sensitive audit metadata: actor/source, prohibited request class, rejection result và reason code. Không lưu Google account/token values.

This document + Security + Self Update

Emergency settings

`dcam.db`; schema TBD.

Future requirement/design

Future server/live/PTT/AI settings

`dcam.db`; schema TBD.

Future designs

Device information

`dcam_config.cson` + DB mirror cho serial/owner/manufacture info.

Device Configuration + Provisioning Design

## 16. QA Acceptance Direction

Test ID

Scenario

Expected Result

Priority

QA-CONSOLE-001

Kiosk user không thể rời DCAM nhưng có thể access allowed in-app console functions.

Approved functions available; Android Settings vẫn restricted.

P0

QA-CONSOLE-002

Admin đổi video resolution khi idle.

Setting validates, persists và applies to next recording.

P0

QA-CONSOLE-003

Admin đổi video resolution khi đang recording.

Change bị deferred/rejected; current recording không bị disrupted.

P0

QA-CONSOLE-004

Admin mở Wi-Fi/USB setting trong normal mode.

Yêu cầu admin/maintenance authorization.

P0

QA-CONSOLE-005

Storage dashboard hiển thị used/free space và estimated remaining record time.

Values được hiển thị và match storage state.

P1

QA-CONSOLE-006

Media viewer chỉ mở finalized file.

Temp/in-progress file bị hidden hoặc blocked.

P0

QA-CONSOLE-007

File Manager và Media Viewer là read-only.

Delete, edit, mark important, export và share actions không available.

P0

QA-CONSOLE-008

Future Live/PTT/AI settings hidden/disabled cho đến approved design.

Không có unsupported future control active trong MVP.

P1

QA-CONSOLE-009

Emergency setting placeholder không change runtime behavior cho đến khi approved.

Emergency behavior vẫn được control bởi current Recording/Emergency design.

P1

QA-CONSOLE-010

App mở `Record / Live View` sau successful startup/login.

Default visible screen là `Record / Live View`.

P0

QA-CONSOLE-011

Press Back trên `Record / Live View`.

App navigate tới `Setting`; app không exit; recording không bị stopped.

P0

QA-CONSOLE-012

Press Back trên `Setting`.

App return tới `Record / Live View`.

P0

QA-CONSOLE-013

Enter any child module từ `Setting` và press Back.

App return tới `Setting`.

P0

QA-CONSOLE-014

Current user đổi credential từ Login Settings.

Change yêu cầu approved re-auth, persists safely và không log secret values.

P0

QA-CONSOLE-015

User enroll face auth từ Login Settings.

Enrollment chỉ xuất hiện khi device capability/security policy cho phép; sensitive biometric material không bị log.

P0

QA-CONSOLE-016

Operator thử mở User Settings.

Access denied hoặc module hidden.

P0

QA-CONSOLE-017

Admin add/edit/delete/disable user từ User Settings.

Action success theo role/security rules, được audit và preserve historical attribution.

P0

QA-CONSOLE-018

Admin thử delete/disable last admin account.

Action bị block trừ khi approved recovery path tồn tại.

P0

QA-CONSOLE-019

Admin thử Enter Maintenance Mode với valid Maintenance Password Gate.

Maintenance entry chỉ success khi runtime guard safe; có audit; chỉ approved apps/settings screens có thể mở.

P0

QA-CONSOLE-020

Admin thử Enter Maintenance Mode với invalid Maintenance Password Gate.

Entry denied; Lock Task vẫn active; failure được audit mà không log secret.

P0

QA-CONSOLE-021

Repeated invalid Maintenance Password Gate attempts.

Rate limit/cooldown/lockout được apply theo policy.

P0

QA-CONSOLE-022

Emergency override thử enter Maintenance Mode.

Access denied; emergency override không satisfy maintenance gate.

P0

QA-CONSOLE-023

Maintenance session timeout hoặc app resume sau maintenance.

DCAM restore restrictions và re-enter Lock Task khi có thể.

P0

QA-CONSOLE-024

Admin thử mở unapproved app hoặc unapproved Android Settings area trong controlled maintenance.

Access bị blocked hoặc return to approved maintenance flow; device không enter full unrestricted mode.

P0

QA-CONSOLE-025

Maintenance flow thử unrestricted launcher/app drawer access.

Access không available.

P0

QA-CONSOLE-026

DCAM Self Update chạy trên no-EMM baseline.

Update package được validate, chỉ apply khi safe, và kiosk policy được restore.

P0

QA-CONSOLE-027

Admin performs approved BFF/R2 or local/factory update action.

Source validation, safe failure/defer and kiosk policy restore are verified.

P1

QA-CONSOLE-028

Admin attempts to launch Play Store or Google-account maintenance flow.

No target/navigation exists; action is rejected and device remains controlled.

P1

QA-CONSOLE-029

Google Play Store/Managed Google Play/Android Management API update is requested.

Flow is rejected; system uses Self Update or approved local/factory fallback only.

P0

## 17. Open Questions / TBD

Item

Status

Exact App Operation Settings list and allowed values

TBD

Exact video profiles by BodyCamera model

TBD / Device POC

Exact pre-record buffer implementation and memory/storage cost

TBD

Whether operator can access Media Viewer and which files are visible

TBD / Product + Security

Exact read-only file visibility and filter rules

TBD / Product + Security

Exact USB mode values and implementation path

TBD / Kiosk Policy + Device POC

Exact Wi-Fi setup flow under kiosk

TBD / Kiosk Policy + Device POC

Exact GPS system toggle vs app feature setting boundary

TBD

Exact Setting hub layout and button/card order

TBD / UI Design

Exact supported login methods: password, PIN, QR, NFC, face auth

TBD / Security + Product

Exact face auth enrollment implementation and storage model

TBD / Security + Device Capability

Exact user delete semantics: hard delete vs disabled/deleted status

TBD / Security + SQLite + BDMA

Exact admin role model and last-admin recovery policy

TBD / Security + Product

Exact Maintenance Password Gate policy: complexity, rotation and reset process

TBD / Security + Product

Exact failed-attempt lockout and recovery policy

TBD / Security + Product

Exact maintenance session timeout

TBD / Security + Product

Exact approved Android system screens for controlled temporary kiosk exit

TBD / Kiosk Policy + Security + Product

Exact approved maintenance/support app package list

TBD / Kiosk Policy + Security + Product

GMS-free dependency/manifest/source and target-device evidence

Required / ADR release gate

Exact DCAM Self Update transport: WebServer/R2/local package/BDMA-assisted

TBD / Self Update Design

Exact silent install feasibility on selected BodyCamera firmware/profile

TBD / Device POC

Emergency Settings requirements

TBD

Server connection / Live Stream / PTT / AI Mode requirements

Future / TBD

Whether all admin settings require Maintenance Mode or only selected settings

TBD

## 18. Practical Conclusion

Trang này sở hữu local UI/UX và runtime guard của in-app console:

`Record / Live View` là default main screen; `Setting` là console hub.

Back navigation luôn ở trong DCAM khi kiosk policy active.

Login Settings hỗ trợ approved current-user auth change; User Settings là Admin-only và phải bảo toàn historical attribution.

Admin/Maintenance flow chỉ expose approved target thông qua role, authentication, capability, policy, security và runtime guard.

File Manager và Media Viewer là read-only/view-only.

App Operation Settings và Device/System proxy chỉ thay đổi các setting được phép.

Project-wide Device Owner/EMM, maintenance và update baseline được reference từ ADR, Kiosk Policy Design và Self Update Design; trang này không restate các baseline đó. Các rule chi tiết trong body chỉ mô tả local console behavior.